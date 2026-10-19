package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.factory.BleObservationFactory
import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.ReadinessBlocker
import com.example.radioobserver.core.radio.state.ObservationStore
import com.example.radioobserver.core.radio.state.UpsertOutcome

/**
 * Narrow scan engine boundary (task 2.3). The ONLY things an implementation may do:
 * start the platform scan with a callback, and stop it. There is deliberately no pairing,
 * connecting, advertising, GATT, Wi-Fi, or retry surface.
 */
interface BleScanEngine {
    fun rawStart(callback: EngineCallback): EngineStartOutcome
    /** @return true if stop was issued cleanly; false = cleanup failure (reported). */
    fun rawStop(): Boolean
}

sealed interface EngineStartOutcome {
    data object Started : EngineStartOutcome
    data class Failed(val reason: ScanFailureReason, val platformCode: Int? = null) : EngineStartOutcome
}

/** Raw platform scan events, before any validation. Timestamps are arrival wall-clock ms. */
interface EngineCallback {
    fun onAdvertisement(
        address: String?,
        rssiDbm: Int?,
        bytes: ByteArray?,
        timestampMs: Long,
        displayName: String?,
    )

    fun onBatch(advertisements: List<EngineAdvertisement>)

    fun onEngineFailure(reason: ScanFailureReason, platformCode: Int?)
}

/** One raw advertisement inside a batch. */
data class EngineAdvertisement(
    val address: String?,
    val rssiDbm: Int?,
    val bytes: ByteArray?,
    val timestampMs: Long,
    val displayName: String?,
)

/** Time source + single-shot delayed task scheduling, injected for deterministic tests. */
interface SessionScheduler {
    fun schedule(delayMs: Long, action: () -> Unit): ScheduledTask
}

interface ScheduledTask {
    fun cancel()
}

/**
 * Deterministic BLE session coordinator (task 2.3): everything except the platform scan
 * itself lives here, pure JVM, fully host-testable. The Android adapter (in :app) is a
 * thin shell that feeds [BleScanEngine]/readiness/foreground inputs in.
 *
 * Gating order (each step refuses with a typed error; a scan NEVER begins otherwise):
 * 1. single active session guard;
 * 2. caller-supplied foreground eligibility (UI wiring intentionally not implemented —
 *    the default provider returns false, so nothing can start scanning silently);
 * 3. real permission/capability readiness from the provider (evaluated at the Android
 *    boundary by the adapter; a pure-model Ready is NEVER treated as proof of an actual
 *    runtime grant — the adapter must use real checks to produce the snapshot);
 * 4. engine start outcome.
 *
 * Session guarantees: bounded by spec (hard ceiling 10 min; recommended short default
 * [bleDefault] = 20 s), idempotent cancel/stop, stop on cancel/timeout/failure/foreground
 * loss, no restart loop, late callbacks ignored (never revive a closed session), dedup via
 * Phase 1 ObservationStore newest-wins semantics. No raw addresses or payload bytes are
 * logged, persisted, or exported — devices stay in process memory for the session only.
 */
class BleSessionCoordinator(
    private val engine: BleScanEngine,
    private val readinessProvider: () -> ObservationReadiness,
    private val foregroundEligible: () -> Boolean,
    private val scheduler: SessionScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : BleObservationSource {

    override val radio: RadioType = RadioType.BLE

    private var activeHandle: FakeBleHandle? = null
    private var activeListener: ObservationSessionListener? = null
    private var activeStore: ObservationStore? = null
    private var timeoutTask: ScheduledTask? = null
    private var sessionCounter = 0L

    /** Conservative short window recommended for user-initiated observation bursts. */
    companion object {
        fun bleDefault(): ObservationSessionSpec = ObservationSessionSpec(20_000)
    }

    override fun startSession(
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
            return SourceStartResult.Refused(primaryBlockerAsError(readiness))
        }

        when (val start = engine.rawStart(engineCallback)) {
            is EngineStartOutcome.Failed ->
                return SourceStartResult.Refused(
                    ObservationError.ScanStartFailed(start.reason, start.platformCode, radio))
            EngineStartOutcome.Started -> Unit
        }

        sessionCounter += 1
        val handle = FakeBleHandle(sessionCounter)
        activeHandle = handle
        activeListener = listener
        activeStore = ObservationStore()
        timeoutTask = scheduler.schedule(spec.maxDurationMs) { stopInternal(SessionEndReason.DURATION_ELAPSED) }
        listener.onObservationEvent(ObservationEvent.SessionStarted(handle.sessionId))
        return SourceStartResult.Started(handle)
    }

    /** Explicit foreground-loss signal (task 2.6 will wire real lifecycle; never background-safe). */
    fun notifyForegroundLost() {
        stopInternal(SessionEndReason.FOREGROUND_LOST)
    }

    private val engineCallback = object : EngineCallback {
        override fun onAdvertisement(
            address: String?,
            rssiDbm: Int?,
            bytes: ByteArray?,
            timestampMs: Long,
            displayName: String?,
        ) = deliver(address, rssiDbm, bytes, timestampMs, displayName)

        override fun onBatch(advertisements: List<EngineAdvertisement>) {
            for (ad in advertisements) deliver(ad.address, ad.rssiDbm, ad.bytes, ad.timestampMs, ad.displayName)
        }

        override fun onEngineFailure(reason: ScanFailureReason, platformCode: Int?) {
            val listener = activeListener ?: return // late failure after stop: ignored
            listener.onObservationEvent(ObservationEvent.SourceFailed(
                ObservationError.ScanStartFailed(reason, platformCode, radio)))
            stopInternal(SessionEndReason.SOURCE_ERROR)
        }
    }

    private fun deliver(
        address: String?,
        rssiDbm: Int?,
        bytes: ByteArray?,
        timestampMs: Long,
        displayName: String?,
    ) {
        val listener = activeListener ?: return // late callback after stop: ignored, never revives
        val store = activeStore ?: return
        // Defensive parse at the boundary: the factory never throws on null/empty/malformed input.
        val device = BleObservationFactory.fromRawAdvertisement(bytes, address, rssiDbm, timestampMs)
            .let { if (displayName.isNullOrBlank()) it else it.copy(displayName = displayName) }
        when (store.upsert(device)) {
            is UpsertOutcome.Accepted ->
                listener.onObservationEvent(ObservationEvent.DeviceObserved(device))
            is UpsertOutcome.IgnoredStale -> Unit // older/out-of-order sample: newest wins, no event
            is UpsertOutcome.Rejected -> Unit // structurally invalid: dropped, never crashed
        }
    }

    /** Idempotent end. Cancels the timer, stops the engine exactly once, reports cleanup failure. */
    private fun stopInternal(reason: SessionEndReason) {
        val handle = activeHandle ?: return
        activeHandle = null
        val listener = activeListener
        activeListener = null
        val store = activeStore
        activeStore = null
        timeoutTask?.cancel()
        timeoutTask = null

        // Engine is stopped exactly once per session (stopInternal itself is idempotent).
        val stopFailed = !engine.rawStop()
        handle.markInactive()

        if (listener != null) {
            if (stopFailed) {
                listener.onObservationEvent(ObservationEvent.SourceFailed(ObservationError.ScanStopFailed(radio)))
            }
            listener.onObservationEvent(ObservationEvent.SessionEnded(handle.sessionId, reason))
        }
        // store/session objects are dropped: strictly per-session, never persisted.
    }

    /**
     * Deterministic primary blocker for refusal mapping:
     * hardware > radio > location services > permissions (ties by permission ordinal).
     */
    private fun primaryBlockerAsError(readiness: ObservationReadiness.Blocked): ObservationError {
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
            // Unrecognized blocker: still refused, conservatively unknown-but-blocked.
            ObservationError.HardwareUnavailable(radio)
        }
    }

    /** Internal handle: cancel is the single user-facing control; coordinator does the rest. */
    private inner class FakeBleHandle(
        private val id: Long,
    ) : ObservationHandle {
        @Volatile
        private var active = true

        override val sessionId: ObservationSessionId = ObservationSessionId("ble-session-$id")

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
