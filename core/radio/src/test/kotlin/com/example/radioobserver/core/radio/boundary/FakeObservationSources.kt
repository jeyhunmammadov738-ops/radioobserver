package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM fakes for the boundary contracts (task 2.2 test doubles). No Android involved.
 * They record events and let tests script: refusal at start, device emission, Wi-Fi result
 * delivery, duration elapsed, and cancellation.
 */
class FakeObservationHandle(
    override val sessionId: ObservationSessionId,
    private val onFirstCancel: (ObservationSessionId) -> Unit,
) : ObservationHandle {
    @Volatile
    private var active = true

    override val isActive: Boolean get() = active

    override fun cancel(): Boolean {
        if (!active) return false
        active = false
        onFirstCancel(sessionId)
        return true
    }

    fun markInactive() {
        active = false
    }
}

class RecordingListener : ObservationSessionListener {
    val events = mutableListOf<ObservationEvent>()

    override fun onObservationEvent(event: ObservationEvent) {
        events += event
    }
}

/** Shared single-session plumbing; enforces the one-active-session guard per source. */
abstract class FakeObservationSource(override val radio: RadioType) : RadioObservationSource {
    /** If set, start calls refuse with this typed error (e.g. permission denied, no hardware). */
    var configuredRefusal: ObservationError? = null

    private var activeHandle: FakeObservationHandle? = null
    private val sessionCounter = AtomicLong(0)
    protected var activeListener: ObservationSessionListener? = null

    val startedSessionCount: Int get() = sessionCounter.get().toInt()
    val hasActiveSession: Boolean get() = activeHandle?.isActive == true

    protected fun doStart(listener: ObservationSessionListener): SourceStartResult {
        val active = activeHandle
        if (active != null && active.isActive) {
            return SourceStartResult.Refused(ObservationError.SessionAlreadyActive(radio))
        }
        configuredRefusal?.let { return SourceStartResult.Refused(it) }

        val handle = FakeObservationHandle(ObservationSessionId("session-${sessionCounter.incrementAndGet()}")) { id ->
            activeListener?.onObservationEvent(ObservationEvent.SessionEnded(id, SessionEndReason.CANCELLED_BY_USER))
            activeListener = null
        }
        activeHandle = handle
        activeListener = listener
        listener.onObservationEvent(ObservationEvent.SessionStarted(handle.sessionId))
        return SourceStartResult.Started(handle)
    }

    protected fun endActive(reason: SessionEndReason) {
        val handle = activeHandle ?: return
        handle.markInactive()
        activeListener?.onObservationEvent(ObservationEvent.SessionEnded(handle.sessionId, reason))
        activeListener = null
    }

    protected fun send(event: ObservationEvent) {
        activeListener?.onObservationEvent(event)
    }
}

class FakeBleObservationSource : FakeObservationSource(RadioType.BLE), BleObservationSource {
    override fun startSession(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult = doStart(listener)

    /** Test seam: simulate the scanner delivering one advertisement. */
    fun emit(device: ObservedRadioDevice) = send(ObservationEvent.DeviceObserved(device))

    /** Test seam: simulate the bounded duration expiring. */
    fun simulateDurationElapsed() = endActive(SessionEndReason.DURATION_ELAPSED)

    /** Test seam: simulate the adapter stopping cleanly. */
    fun simulateNormalCompletion() = endActive(SessionEndReason.COMPLETED_NORMALLY)
}

class FakeWifiObservationSource : FakeObservationSource(RadioType.WIFI), WifiObservationSource {
    override fun startObservation(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult = doStart(listener)

    /**
     * Test seam: simulate a platform/other-app scan completion delivering cached results.
     * There is intentionally NO method that triggers a scan.
     */
    fun deliverResults(
        observations: List<ObservedRadioDevice>,
        updated: Boolean,
        resultsAgeMs: Long?,
    ) = send(ObservationEvent.WifiResultsAvailable(observations, updated, resultsAgeMs))

    /** Test seam: simulate a source-level failure after start. */
    fun failWith(error: ObservationError) {
        send(ObservationEvent.SourceFailed(error))
        endActive(SessionEndReason.SOURCE_ERROR)
    }

    fun simulateDurationElapsed() = endActive(SessionEndReason.DURATION_ELAPSED)
}
