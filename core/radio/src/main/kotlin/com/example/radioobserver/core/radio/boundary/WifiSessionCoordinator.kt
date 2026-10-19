package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.ReadinessBlocker
import com.example.radioobserver.core.radio.state.ObservationStore
import com.example.radioobserver.core.radio.state.UpsertOutcome
import com.example.radioobserver.core.radio.wifi.WifiObservationClassifier
import com.example.radioobserver.core.radio.wifi.WifiObservationInput

/**
 * Narrow receive-only engine boundary (task 2.4). An implementation may ONLY:
 * (a) register/unregister a session-scoped receiver for system scan-result broadcasts and
 * (b) provide a fetcher that reads system-cached results. There is deliberately NO method
 * to request, trigger, schedule, or poll a Wi-Fi scan, and no connection/configuration API.
 */
interface WifiResultEngine {
    fun start(callback: WifiResultEngineCallback): WifiEngineStartOutcome

    /** @return true if the receiver was unregistered cleanly; false = cleanup failure (reported). */
    fun stop(): Boolean
}

interface WifiResultEngineCallback {
    /**
     * A system scan-results broadcast arrived. [updated] mirrors the platform's
     * "results updated" flag; false/missing means results may be cached from an older scan
     * and must NEVER be presented as newly detected.
     */
    fun onScanResultsBroadcast(updated: Boolean)
}

sealed interface WifiEngineStartOutcome {
    data object Started : WifiEngineStartOutcome
    data class Failed(val error: ObservationError) : WifiEngineStartOutcome
}

/** One raw access-point record as extracted from the system scan-result cache. */
data class WifiAccessPointRecord(
    val bssid: String? = null,
    val ssid: String? = null,
    val rssiDbm: Int? = null,
    /** Platform raw value, preserved verbatim (microseconds since device boot). */
    val bootTimestampUs: Long? = null,
    /**
     * Age of the cached result computed against the compatible boot-time clock at extraction
     * time, in milliseconds. Null when the timestamp is unknown, negative, in the future,
     * or implausibly large — never fabricated.
     */
    val ageMs: Long? = null,
)

interface WifiResultFetcher {
    /** Read the latest system-cached results. Must NEVER trigger a scan. */
    fun fetch(): WifiFetchOutcome
}

sealed interface WifiFetchOutcome {
    data class Results(val records: List<WifiAccessPointRecord>) : WifiFetchOutcome
    data class Failed(val error: ObservationError) : WifiFetchOutcome
}

/**
 * Deterministic receive-only Wi-Fi session coordinator (task 2.4): pure JVM, host-testable.
 * The Android adapter (in :app) is a thin shell feeding engine/fetcher/readiness/foreground.
 *
 * Gating order (a scan result is NEVER fetched otherwise):
 * 1. single active session per Wi-Fi source;
 * 2. caller-supplied foreground eligibility (default false — nothing registers or reads
 *    until a later UI task supplies a real signal);
 * 3. real-check readiness snapshot evaluated by the 2.5 model;
 * 4. engine (receiver) registration.
 * The gates are RE-EVALUATED on every broadcast: permission/foreground loss fails closed
 * with a typed error instead of reading anything.
 *
 * Freshness honesty: the platform timestamp (µs since boot) is preserved verbatim and the
 * computed age travels in the delivery; receipt time (wall-clock) is recorded separately in
 * the device's observedAtEpochMs. A broadcast with updated=false/missing is delivered with
 * updatedSinceLastDelivery=false — cached data is never labeled as new. Dedup uses the
 * Phase 1 ObservationStore newest-wins semantics on receipt time.
 *
 * Session model note: this coordinator is per-radio. The 2.2 contracts allow one BLE session
 * and one Wi-Fi session at the same time (independent sources); that semantic is unchanged.
 */
class WifiSessionCoordinator(
    private val engine: WifiResultEngine,
    private val fetcher: WifiResultFetcher,
    private val readinessProvider: () -> ObservationReadiness,
    private val foregroundEligible: () -> Boolean,
    private val scheduler: SessionScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : WifiObservationSource {

    override val radio: RadioType = RadioType.WIFI

    private var activeHandle: WifiHandle? = null
    private var activeListener: ObservationSessionListener? = null
    private var activeStore: ObservationStore? = null
    private var timeoutTask: ScheduledTask? = null
    private var sessionCounter = 0L

    /** Conservative short window for a user-initiated listening burst. */
    companion object {
        fun wifiDefault(): ObservationSessionSpec = ObservationSessionSpec(30_000)
    }

    override fun startObservation(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult {
        if (activeHandle != null) {
            return SourceStartResult.Refused(ObservationError.SessionAlreadyActive(radio))
        }
        if (!foregroundEligible()) {
            return SourceStartResult.Refused(ObservationError.ForegroundIneligible(radio))
        }
        val readiness = readinessProvider()
        if (readiness is ObservationReadiness.Blocked) {
            return SourceStartResult.Refused(readinessAsError(readiness))
        }

        when (val start = engine.start(engineCallback)) {
            is WifiEngineStartOutcome.Failed ->
                return SourceStartResult.Refused(start.error)
            WifiEngineStartOutcome.Started -> Unit
        }

        sessionCounter += 1
        val handle = WifiHandle(sessionCounter)
        activeHandle = handle
        activeListener = listener
        activeStore = ObservationStore()
        timeoutTask = scheduler.schedule(spec.maxDurationMs) { stopInternal(SessionEndReason.DURATION_ELAPSED) }
        listener.onObservationEvent(ObservationEvent.SessionStarted(handle.sessionId))
        return SourceStartResult.Started(handle)
    }

    /** Explicit foreground-loss signal (task 2.6 will wire real lifecycle). */
    fun notifyForegroundLost() {
        stopInternal(SessionEndReason.FOREGROUND_LOST)
    }

    private val engineCallback = object : WifiResultEngineCallback {
        override fun onScanResultsBroadcast(updated: Boolean) {
            val listener = activeListener ?: return // late broadcast after stop: ignored, never revives
            // Re-evaluate all gates at broadcast time: fail closed on any loss.
            if (!foregroundEligible()) {
                listener.onObservationEvent(ObservationEvent.SourceFailed(
                    ObservationError.ForegroundIneligible(radio)))
                stopInternal(SessionEndReason.FOREGROUND_LOST)
                return
            }
            val readiness = readinessProvider()
            if (readiness is ObservationReadiness.Blocked) {
                listener.onObservationEvent(ObservationEvent.SourceFailed(readinessAsError(readiness)))
                stopInternal(SessionEndReason.SOURCE_ERROR)
                return
            }
            when (val fetched = fetcher.fetch()) {
                is WifiFetchOutcome.Failed -> {
                    listener.onObservationEvent(ObservationEvent.SourceFailed(fetched.error))
                    stopInternal(SessionEndReason.SOURCE_ERROR) // no auto-retry/restart
                }
                is WifiFetchOutcome.Results -> deliver(listener, fetched.records, updated)
            }
        }
    }

    private fun deliver(
        listener: ObservationSessionListener,
        records: List<WifiAccessPointRecord>,
        updated: Boolean,
    ) {
        val store = activeStore ?: return
        val receiptEpochMs = clock() // reception time: kept SEPARATE from per-result age
        val accepted = mutableListOf<com.example.radioobserver.core.radio.model.ObservedRadioDevice>()

        for (record in records) {
            val device = WifiObservationClassifier.classify(
                WifiObservationInput(
                    observedAtEpochMs = receiptEpochMs,
                    ssid = record.ssid,
                    bssid = record.bssid,
                    rssiDbm = record.rssiDbm,
                ),
            ).let { base ->
                // Freshness provenance travels with the observation; boot clock stays boot clock.
                base.copy(sourceFields = base.sourceFields + mapOf(
                    "resultsUpdatedFlag" to updated.toString(),
                    "resultAgeMs" to (record.ageMs?.toString() ?: "unknown"),
                    "bootTimestampUs" to (record.bootTimestampUs?.toString() ?: "unknown"),
                ))
            }
            when (store.upsert(device)) {
                is UpsertOutcome.Accepted -> accepted += device
                is UpsertOutcome.IgnoredStale -> Unit // out-of-order/older receipt: newest wins
                is UpsertOutcome.Rejected -> Unit // structurally invalid: dropped, no crash
            }
        }

        val minAge = accepted.mapNotNull { d ->
            d.sourceFields["resultAgeMs"]?.toLongOrNull()
        }.minOrNull()

        listener.onObservationEvent(
            ObservationEvent.WifiResultsAvailable(
                observations = accepted,
                updatedSinceLastDelivery = updated, // false/missing flag is passed through honestly
                resultsAgeMs = minAge,
            ),
        )
    }

    /** Idempotent end: cancels the timer and unregisters the receiver exactly once. */
    private fun stopInternal(reason: SessionEndReason) {
        val handle = activeHandle ?: return
        activeHandle = null
        val listener = activeListener
        activeListener = null
        activeStore = null
        timeoutTask?.cancel()
        timeoutTask = null

        val stopFailed = !engine.stop()
        handle.markInactive()

        if (listener != null) {
            if (stopFailed) {
                listener.onObservationEvent(ObservationEvent.SourceFailed(ObservationError.ScanStopFailed(radio)))
            }
            listener.onObservationEvent(ObservationEvent.SessionEnded(handle.sessionId, reason))
        }
    }

    /** Deterministic blocker mapping (hardware > radio > location > permission ties by ordinal). */
    private fun readinessAsError(readiness: ObservationReadiness.Blocked): ObservationError {
        val blockers = readiness.blockers
        (blockers.firstOrNull { it is ReadinessBlocker.HardwareUnavailable })?.let { return ObservationError.HardwareUnavailable(radio) }
        (blockers.firstOrNull { it is ReadinessBlocker.RadioDisabled })?.let { return ObservationError.RadioDisabled(radio) }
        (blockers.firstOrNull { it is ReadinessBlocker.LocationServicesDisabled })?.let { return ObservationError.LocationServicesDisabled(radio) }
        val missing = blockers.filterIsInstance<ReadinessBlocker.PermissionMissing>()
            .sortedBy { it.permission.ordinal }
            .firstOrNull()
        return if (missing != null) {
            ObservationError.PermissionDenied(missing.permission, missing.permanent, radio)
        } else {
            ObservationError.HardwareUnavailable(radio) // unrecognized blocker: still refused
        }
    }

    private inner class WifiHandle(
        private val id: Long,
    ) : ObservationHandle {
        @Volatile
        private var active = true

        override val sessionId: ObservationSessionId = ObservationSessionId("wifi-session-$id")

        override val isActive: Boolean get() = active

        override fun cancel(): Boolean {
            if (!active) return false
            active = false
            stopInternal(SessionEndReason.CANCELLED_BY_USER)
            return true
        }

        fun markInactive() {
            active = false
        }
    }
}
