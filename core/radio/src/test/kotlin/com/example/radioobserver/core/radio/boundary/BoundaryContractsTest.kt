package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.PermissionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task 2.2 acceptance tests: session bounds, start/stop/cancel, refusals, stale Wi-Fi results. */
class BoundaryContractsTest {

    private fun device(radio: RadioType = RadioType.BLE, key: String = "ble:ac:bb:cc:dd:ee:ff") =
        ObservedRadioDevice(
            observationKey = key,
            radioType = radio,
            observedAtEpochMs = 1_768_000_000_000,
            address = "AC:BB:CC:DD:EE:FF",
            rssiDbm = -55,
        )

    // ---- ObservationSessionSpec bounds ----

    @Test
    fun `spec rejects non-positive duration`() {
        val ex = runCatching { ObservationSessionSpec(0) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
    }

    @Test
    fun `spec rejects duration above the hard ceiling`() {
        val tooBig = ObservationSessionSpec.MAX_SESSION_DURATION_MS + 1
        val ex = runCatching { ObservationSessionSpec(tooBig) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
    }

    @Test
    fun `spec accepts the exact ceiling and default is bounded`() {
        ObservationSessionSpec(ObservationSessionSpec.MAX_SESSION_DURATION_MS)
        assertTrue(ObservationSessionSpec.default().maxDurationMs <= ObservationSessionSpec.MAX_SESSION_DURATION_MS)
    }

    // ---- BLE source lifecycle ----

    @Test
    fun `ble start emits SessionStarted and emits devices to the listener`() {
        val source = FakeBleObservationSource()
        val listener = RecordingListener()

        val result = source.startSession(ObservationSessionSpec.default(), listener)
        assertTrue(result is SourceStartResult.Started)
        source.emit(device())

        assertEquals(2, listener.events.size)
        assertTrue(listener.events[0] is ObservationEvent.SessionStarted)
        val observed = listener.events[1] as ObservationEvent.DeviceObserved
        assertEquals("ble:ac:bb:cc:dd:ee:ff", observed.device.observationKey)
    }

    @Test
    fun `cancel ends the session exactly once and reports CANCELLED_BY_USER`() {
        val source = FakeBleObservationSource()
        val listener = RecordingListener()
        val result = source.startSession(ObservationSessionSpec.default(), listener) as SourceStartResult.Started

        assertTrue(result.handle.cancel())
        assertFalse(result.handle.cancel()) // second cancel is a no-op
        assertTrue(listener.events.last() is ObservationEvent.SessionEnded)
        assertEquals(
            SessionEndReason.CANCELLED_BY_USER,
            (listener.events.last() as ObservationEvent.SessionEnded).reason,
        )
        assertFalse(source.hasActiveSession)
    }

    @Test
    fun `emissions after cancellation are ignored`() {
        val source = FakeBleObservationSource()
        val listener = RecordingListener()
        val result = source.startSession(ObservationSessionSpec.default(), listener) as SourceStartResult.Started
        result.handle.cancel()

        source.emit(device())

        assertEquals(2, listener.events.size) // Started + Ended only
    }

    @Test
    fun `single-session guard refuses a second concurrent session`() {
        val source = FakeBleObservationSource()
        source.startSession(ObservationSessionSpec.default(), RecordingListener())

        val second = source.startSession(ObservationSessionSpec.default(), RecordingListener())

        assertTrue(second is SourceStartResult.Refused)
        val refused = (second as SourceStartResult.Refused).error
        assertTrue(refused is ObservationError.SessionAlreadyActive)
        assertEquals(1, source.startedSessionCount)
    }

    @Test
    fun `a new session may start after the previous one ended normally`() {
        val source = FakeBleObservationSource()
        val first = source.startSession(ObservationSessionSpec.default(), RecordingListener())
        source.simulateNormalCompletion()

        val second = source.startSession(ObservationSessionSpec.default(), RecordingListener())

        assertTrue(first is SourceStartResult.Started && second is SourceStartResult.Started)
        assertEquals(2, source.startedSessionCount)
    }

    @Test
    fun `duration elapsed ends the session with DURATION_ELAPSED`() {
        val source = FakeBleObservationSource()
        val listener = RecordingListener()
        source.startSession(ObservationSessionSpec.default(), listener)

        source.simulateDurationElapsed()

        assertEquals(
            SessionEndReason.DURATION_ELAPSED,
            (listener.events.last() as ObservationEvent.SessionEnded).reason,
        )
    }

    // ---- refusals at start ----

    @Test
    fun `ble source refuses with a typed permission error when capability is denied`() {
        val source = FakeBleObservationSource()
        source.configuredRefusal =
            ObservationError.PermissionDenied(PermissionId.BLUETOOTH_SCAN, permanent = true, radio = RadioType.BLE)

        val result = source.startSession(ObservationSessionSpec.default(), RecordingListener())

        assertTrue(result is SourceStartResult.Refused)
        val denied = (result as SourceStartResult.Refused).error as ObservationError.PermissionDenied
        assertEquals(PermissionId.BLUETOOTH_SCAN, denied.permission)
        assertTrue(denied.permanent)
        assertEquals(0, source.startedSessionCount)
    }

    @Test
    fun `wifi source refuses when hardware is unavailable`() {
        val source = FakeWifiObservationSource()
        source.configuredRefusal = ObservationError.HardwareUnavailable(RadioType.WIFI)

        val result = source.startObservation(ObservationSessionSpec.default(), RecordingListener())

        assertTrue(result is SourceStartResult.Refused)
        assertTrue((result as SourceStartResult.Refused).error is ObservationError.HardwareUnavailable)
    }

    // ---- Wi-Fi passive result semantics ----

    @Test
    fun `wifi results carry updated flag, age marker, and observations`() {
        val source = FakeWifiObservationSource()
        val listener = RecordingListener()
        source.startObservation(ObservationSessionSpec.default(), listener)

        source.deliverResults(listOf(device(RadioType.WIFI, "wifi:ac:bb:cc:dd:ee:ff")), updated = true, resultsAgeMs = 1200)

        val delivery = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single()
        assertEquals(1, delivery.observations.size)
        assertTrue(delivery.updatedSinceLastDelivery)
        assertEquals(1200L, delivery.resultsAgeMs)
    }

    @Test
    fun `stale wifi results are delivered with updated=false and unknown age`() {
        val source = FakeWifiObservationSource()
        val listener = RecordingListener()
        source.startObservation(ObservationSessionSpec.default(), listener)

        source.deliverResults(emptyList(), updated = false, resultsAgeMs = null)

        val delivery = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single()
        assertFalse(delivery.updatedSinceLastDelivery)
        assertNull(delivery.resultsAgeMs) // unknown age is modeled, not invented
    }

    @Test
    fun `wifi source failure reports the typed error and ends the session`() {
        val source = FakeWifiObservationSource()
        val listener = RecordingListener()
        source.startObservation(ObservationSessionSpec.default(), listener)

        source.failWith(ObservationError.RadioDisabled(RadioType.WIFI))

        assertTrue(listener.events.any { it is ObservationEvent.SourceFailed })
        assertEquals(
            SessionEndReason.SOURCE_ERROR,
            (listener.events.last() as ObservationEvent.SessionEnded).reason,
        )
    }
}
