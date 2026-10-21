package com.example.radioobserver.core.radio.session

import com.example.radioobserver.core.radio.boundary.ObservationError
import com.example.radioobserver.core.radio.boundary.ObservationEvent
import com.example.radioobserver.core.radio.boundary.ObservationSessionId
import com.example.radioobserver.core.radio.boundary.ObservationSessionListener
import com.example.radioobserver.core.radio.boundary.ObservationSessionSpec
import com.example.radioobserver.core.radio.boundary.SessionEndReason
import com.example.radioobserver.core.radio.boundary.SourceStartResult
import com.example.radioobserver.core.radio.lifecycle.RadioObservationSupervisor
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.ObservationPipeline
import com.example.radioobserver.core.radio.ui.ObservationUiMapper
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.core.radio.ui.SessionIssueKey

/**
 * Phase 3 task 3.4 — foreground observation session ORCHESTRATOR (pure Kotlin/JVM).
 *
 * THE one production call path from a future 3.5 user Start/Stop control to the existing
 * engines (no competing coordinator, no second foreground policy):
 *
 *   explicit user tap (future 3.5 UI)
 *     -> :app ObservationSessionPresenter (fresh permission + capability facts at tap time)
 *     -> [startBleSession]/[startWifiObservation] (foreground/permission/capability re-checks)
 *     -> [RadioObservationSupervisor] (RESUMED-only eligibility, Phase 2.6 policy)
 *     -> existing BLE / passive Wi-Fi source (own security/readiness gates inside)
 *     -> events -> [ObservationPipeline] (privacy boundary, 200 entries / 15-min lazy TTL)
 *     -> PublicObservation -> 3.2 mapper -> [ObservationUiState] (inert until the 3.5 UI)
 *
 * Developer-approved rules enforced here:
 * - NOTHING starts without an explicit start call: no cold-start, resume, permission
 *   callback, Settings return or recreation path reaches the sources from this class.
 * - One BLE and one Wi-Fi session, independently; both may coexist; duplicate starts are
 *   idempotent (no duplicate scans, receivers or deadlines). Stopping one radio never
 *   stops the other; foreground loss (via the supervisor) stops BOTH.
 * - Generation guard: after cancel, timeout, foreground loss or a newer session, late
 *   source events can no longer mutate state or resurrect disposed data.
 * - When the LAST session ends, the volatile pipeline is disposed (closeAndDispose):
 *   sanitized rows and raw session-local evidence die with the session. A fresh session
 *   gets a fresh pipeline with fresh session-local identities. Nothing auto-restarts.
 * - Wi-Fi observation is strictly passive: this class only consumes source events; it
 *   has no API (and no ability) to trigger any Wi-Fi scan.
 * - Single-threaded like the existing components (main-thread callbacks assumed).
 */
class ObservationSessionOrchestrator(
    private val supervisor: RadioObservationSupervisor,
    private val pipelineFactory: () -> ObservationPipeline,
    private val bleSessionSpec: ObservationSessionSpec = ObservationSessionSpec(
        DEFAULT_BLE_SESSION_MS),
    private val wifiSessionSpec: ObservationSessionSpec = ObservationSessionSpec(
        DEFAULT_WIFI_SESSION_MS),
    private val onUiState: (ObservationUiState) -> Unit = {},
) {
    private var uiState = ObservationUiState.initial()
    private var pipeline: ObservationPipeline? = null
    private var bleGeneration = 0L
    private var wifiGeneration = 0L

    /** Fresh authorization facts, gathered by the caller at real tap time. */
    data class StartAuthorization(
        /** Caller-observed foreground eligibility (Activity RESUMED). */
        val foregroundEligible: Boolean,
        /** FINE location currently granted. Approximate-only must be false here. */
        val preciseLocation: Boolean,
        /** Radio/hardware/location-service readiness from the existing probe. */
        val capabilityReady: Boolean,
        /** Typed issue to show when [capabilityReady] is false. */
        val capabilityIssue: SessionIssueKey? = null,
    )

    /** Typed outcome of an explicit start attempt. Never a side effect by itself. */
    sealed interface StartAttempt {
        data class Started(val radio: RadioType) : StartAttempt
        data class NeedsRuntimePermission(val radio: RadioType) : StartAttempt
        data class Blocked(val radio: RadioType, val issue: SessionIssueKey) : StartAttempt
    }

    companion object {
        /** Existing Phase 2 defaults, re-declared for the production call path. */
        const val DEFAULT_BLE_SESSION_MS: Long = 20_000L
        const val DEFAULT_WIFI_SESSION_MS: Long = 30_000L
    }

    // ------------------------------------------------------------------ start

    fun startBleSession(authorization: StartAuthorization): StartAttempt =
        start(RadioType.BLE, authorization, bleSessionSpec, supervisor::startBleSession)

    fun startWifiObservation(authorization: StartAuthorization): StartAttempt =
        start(RadioType.WIFI, authorization, wifiSessionSpec, supervisor::startWifiObservation)

    private fun start(
        radio: RadioType,
        authorization: StartAuthorization,
        spec: ObservationSessionSpec,
        starter: (ObservationSessionSpec, ObservationSessionListener) -> SourceStartResult,
    ): StartAttempt {
        val session = sessionOf(radio)
        // Idempotent duplicate Start: never a second source call, registration or deadline.
        if (session.status == RadioSessionUiStatus.OBSERVING ||
            session.status == RadioSessionUiStatus.STOPPING
        ) {
            return StartAttempt.Started(radio)
        }
        // Permission gate: FINE required; approximate-only fails closed. No scan.
        if (!authorization.preciseLocation) {
            publish(withStatus(radio, RadioSessionUiStatus.PERMISSION_REQUIRED,
                SessionIssueKey.PERMISSION_APPROXIMATE_ONLY))
            return StartAttempt.NeedsRuntimePermission(radio)
        }
        // Capability gate: hardware/radio/location-service readiness at tap time.
        if (!authorization.capabilityReady) {
            val issue = authorization.capabilityIssue ?: SessionIssueKey.HARDWARE_UNSUPPORTED
            publish(withStatus(radio, RadioSessionUiStatus.BLOCKED, issue))
            return StartAttempt.Blocked(radio, issue)
        }
        // Foreground gate, re-checked at the actual start boundary (time-of-use).
        if (!authorization.foregroundEligible || !supervisor.isForegroundEligible) {
            publish(withStatus(radio, RadioSessionUiStatus.BLOCKED,
                SessionIssueKey.FOREGROUND_LOST))
            return StartAttempt.Blocked(radio, SessionIssueKey.FOREGROUND_LOST)
        }

        val generation = if (radio == RadioType.BLE) ++bleGeneration else ++wifiGeneration
        val listener = ObservationSessionListener { event ->
            onEvent(radio, generation, event)
        }
        return when (val result = starter(spec, listener)) {
            is SourceStartResult.Started -> {
                ensurePipeline()
                publish(withStatus(radio, RadioSessionUiStatus.OBSERVING,
                    remainingMs = spec.maxDurationMs))
                StartAttempt.Started(radio)
            }
            is SourceStartResult.Refused -> {
                val (status, issue) = mapRefusal(result.error)
                publish(withStatus(radio, status, issue))
                if (status == RadioSessionUiStatus.PERMISSION_REQUIRED) {
                    StartAttempt.NeedsRuntimePermission(radio)
                } else {
                    StartAttempt.Blocked(radio, issue ?: SessionIssueKey.INTERNAL_ERROR)
                }
            }
        }
    }

    // ------------------------------------------------------------------ stop

    /**
     * Phase 3.7 — user-invoked READINESS RECHECK ONLY. Publishes a fresh non-running
     * session status derived from the caller's CURRENT permission/capability facts.
     * Never starts, stops, registers a source or touches a generation; while the radio
     * is OBSERVING/STOPPING the authoritative live status always wins (no-op); an
     * unchanged derivation is a no-op too (no flicker, no duplicate callbacks).
     */
    fun refreshReadinessStatus(
        radio: RadioType,
        status: RadioSessionUiStatus,
        issue: SessionIssueKey? = null,
    ) {
        require(status != RadioSessionUiStatus.OBSERVING && status != RadioSessionUiStatus.STOPPING) {
            "a recheck must never fabricate an active session"
        }
        val current = sessionOf(radio)
        if (current.status == RadioSessionUiStatus.OBSERVING ||
            current.status == RadioSessionUiStatus.STOPPING
        ) return
        if (current.status == status && current.issueKey == issue) return
        publish(withStatus(radio, status, issue))
    }

    /** Explicit user Stop for BLE only. The Wi-Fi session is untouched. Idempotent. */
    fun stopBleSession(): Boolean = stop(RadioType.BLE, supervisor::cancelBleSession)

    /** Explicit user Stop for Wi-Fi only. The BLE session is untouched. Idempotent. */
    fun stopWifiObservation(): Boolean = stop(RadioType.WIFI, supervisor::cancelWifiSession)

    private fun stop(radio: RadioType, canceller: () -> Boolean): Boolean {
        val session = sessionOf(radio)
        if (session.status != RadioSessionUiStatus.OBSERVING &&
            session.status != RadioSessionUiStatus.STOPPING
        ) {
            return false // already inactive: repeated Stop is a safe no-op
        }
        val cancelled = canceller()
        // Synchronous sources deliver SessionEnded inside cancel(); only mark STOPPING if
        // the event has not already settled the session (async adapters catch up later).
        if (sessionOf(radio).status == RadioSessionUiStatus.OBSERVING) {
            publish(withStatus(radio, RadioSessionUiStatus.STOPPING))
        }
        return cancelled
    }

    // ------------------------------------------------------------------ events

    private fun onEvent(radio: RadioType, generation: Long, event: ObservationEvent) {
        // Generation guard: late events from an ended/older session never mutate state.
        if (generation != currentGeneration(radio)) return
        val session = sessionOf(radio)
        val live = session.status == RadioSessionUiStatus.OBSERVING ||
            session.status == RadioSessionUiStatus.STOPPING
        when (event) {
            is ObservationEvent.SessionStarted -> if (live) {
                publish(withStatus(radio, RadioSessionUiStatus.OBSERVING))
            }
            is ObservationEvent.DeviceObserved -> if (live) {
                pipeline?.submit(event.device)
                publishRows()
            }
            is ObservationEvent.WifiResultsAvailable -> if (live) {
                event.observations.forEach { pipeline?.submit(it) }
                publishRows()
            }
            is ObservationEvent.SourceFailed -> if (live) {
                // Record the typed failure cause; the terminal SessionEnded(SOURCE_ERROR)
                // that follows (or never arrives) still ends the session safely.
                val (_, issue) = mapRefusal(event.error)
                publish(withStatus(radio, RadioSessionUiStatus.ERROR,
                    issue ?: SessionIssueKey.INTERNAL_ERROR))
            }
            is ObservationEvent.SessionEnded -> if (live || session.status == RadioSessionUiStatus.ERROR) {
                publish(applySessionEnd(radio, event.reason))
                invalidateGeneration(radio)
            }
        }
    }

    /** Terminal mapping; disposes the pipeline when the LAST session ends. */
    private fun applySessionEnd(
        radio: RadioType,
        reason: SessionEndReason,
    ): ObservationUiState {
        val (status, issue) = when (reason) {
            SessionEndReason.CANCELLED_BY_USER ->
                // A cancel arriving while the host is no longer RESUMED was the
                // supervisor's foreground-loss stop (non-aware sources use cancel()):
                if (!supervisor.isForegroundEligible) {
                    RadioSessionUiStatus.STOPPED to SessionIssueKey.FOREGROUND_LOST
                } else {
                    RadioSessionUiStatus.STOPPED to null
                }
            SessionEndReason.DURATION_ELAPSED -> RadioSessionUiStatus.TIMED_OUT to null
            SessionEndReason.FOREGROUND_LOST ->
                RadioSessionUiStatus.STOPPED to SessionIssueKey.FOREGROUND_LOST
            SessionEndReason.SOURCE_ERROR ->
                // A SourceFailed already recorded the typed cause; keep it. Otherwise the
                // failure arrived without detail: honest generic internal error.
                if (sessionOf(radio).status == RadioSessionUiStatus.ERROR &&
                    sessionOf(radio).issueKey != null
                ) {
                    RadioSessionUiStatus.ERROR to sessionOf(radio).issueKey
                } else {
                    RadioSessionUiStatus.ERROR to SessionIssueKey.INTERNAL_ERROR
                }
            SessionEndReason.COMPLETED_NORMALLY -> RadioSessionUiStatus.STOPPED to null
        }
        var next = withStatus(radio, status, issue)
        // Privacy teardown: when NO session remains active, the volatile pipeline and its
        // rows die (both radios' data — the sessions they belong to are gone). Per-radio
        // independence holds while the other session is still live: stopping ONE radio
        // leaves the pipeline and the other radio's rows intact.
        val other = otherRadio(radio)
        val otherLive = sessionOf(other).status == RadioSessionUiStatus.OBSERVING ||
            sessionOf(other).status == RadioSessionUiStatus.STOPPING
        if (!otherLive) {
            pipeline?.closeAndDispose()
            pipeline = null
            next = ObservationUiMapper.withRowsCleared(next)
        }
        return next
    }

    private fun invalidateGeneration(radio: RadioType) {
        if (radio == RadioType.BLE) bleGeneration++ else wifiGeneration++
    }

    private fun currentGeneration(radio: RadioType): Long =
        if (radio == RadioType.BLE) bleGeneration else wifiGeneration

    // ------------------------------------------------------------------ state

    /** Current sanitized presentation snapshot (rows from the live pipeline only). */
    fun uiSnapshot(): ObservationUiState =
        uiState.copy(rows = pipeline?.let { ObservationUiMapper.toRows(it.publicSnapshot()) }
            ?: emptyList())

    private fun ensurePipeline() {
        if (pipeline == null) pipeline = pipelineFactory()
    }

    private fun publishRows() {
        val p = pipeline ?: return
        publish(uiState.copy(rows = ObservationUiMapper.toRows(p.publicSnapshot())))
    }

    private fun publish(state: ObservationUiState) {
        uiState = state
        // Emitted snapshot always carries the CURRENT sanitized rows (or none if disposed):
        val rows = pipeline?.let { ObservationUiMapper.toRows(it.publicSnapshot()) } ?: emptyList()
        onUiState(state.copy(rows = rows))
    }

    private fun withStatus(
        radio: RadioType,
        status: RadioSessionUiStatus,
        issue: SessionIssueKey? = null,
        remainingMs: Long? = null,
    ): ObservationUiState = when (radio) {
        RadioType.BLE -> uiState.copy(
            bleSession = uiState.bleSession.copy(status = status, issueKey = issue, remainingMs = remainingMs))
        RadioType.WIFI -> uiState.copy(
            wifiSession = uiState.wifiSession.copy(status = status, issueKey = issue, remainingMs = remainingMs))
    }

    private fun sessionOf(radio: RadioType) = when (radio) {
        RadioType.BLE -> uiState.bleSession
        RadioType.WIFI -> uiState.wifiSession
    }

    private fun otherRadio(radio: RadioType) = when (radio) {
        RadioType.BLE -> RadioType.WIFI
        RadioType.WIFI -> RadioType.BLE
    }

    private fun mapRefusal(error: ObservationError): Pair<RadioSessionUiStatus, SessionIssueKey?> =
        when (error) {
            is ObservationError.PermissionDenied ->
                RadioSessionUiStatus.PERMISSION_REQUIRED to SessionIssueKey.PERMISSION_DENIED
            is ObservationError.RadioDisabled ->
                RadioSessionUiStatus.BLOCKED to when (error.radio) {
                    RadioType.BLE -> SessionIssueKey.BLUETOOTH_OFF
                    RadioType.WIFI -> SessionIssueKey.WIFI_OFF
                }
            is ObservationError.LocationServicesDisabled ->
                RadioSessionUiStatus.BLOCKED to SessionIssueKey.LOCATION_SERVICES_OFF
            is ObservationError.HardwareUnavailable ->
                RadioSessionUiStatus.BLOCKED to SessionIssueKey.HARDWARE_UNSUPPORTED
            is ObservationError.SessionAlreadyActive ->
                RadioSessionUiStatus.OBSERVING to null
            is ObservationError.ForegroundIneligible ->
                RadioSessionUiStatus.BLOCKED to SessionIssueKey.FOREGROUND_LOST
            is ObservationError.ScanStartFailed,
            is ObservationError.ScanStopFailed ->
                RadioSessionUiStatus.ERROR to SessionIssueKey.SECURITY_EXCEPTION
        }
}
