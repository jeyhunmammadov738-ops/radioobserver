package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.AndroidApiBucket
import com.example.radioobserver.core.radio.permissions.CapabilitySnapshot
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.PermissionId
import com.example.radioobserver.core.radio.permissions.PermissionState
import com.example.radioobserver.core.radio.permissions.RadioHardwareStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.3 acceptance tests (device-free JVM): gating, lifecycle, typed errors, defensive
 * callbacks, dedup, and idempotent cleanup — all against fakes, no Android, no radio.
 * These tests do NOT claim real-device BLE reception, permission dialogs, or OS throttling.
 */
class BleSessionCoordinatorTest {

    // ---- test doubles ----

    private class FakeBleScanEngine : BleScanEngine {
        var startCount = 0; private set
        var stopCount = 0; private set
        var startFailure: EngineStartOutcome.Failed? = null
        var stopSucceeds = true
        var callback: EngineCallback? = null; private set

        override fun rawStart(callback: EngineCallback): EngineStartOutcome {
            startCount += 1
            startFailure?.let { return it }
            this.callback = callback
            return EngineStartOutcome.Started
        }

        override fun rawStop(): Boolean {
            if (stopCount > 0 && startCount == 0) return false
            if (startCount == 0 || stopCount >= startCount) return false
            stopCount += 1
            return stopSucceeds
        }
    }

    private class ManualScheduler : SessionScheduler {
        val tasks = mutableListOf<Pair<Long, () -> Unit>>()

        override fun schedule(delayMs: Long, action: () -> Unit): ScheduledTask =
            object : ScheduledTask {
                var cancelled = false
                override fun cancel() { cancelled = true }
            }.also { tasks += delayMs to action }
    }

    private class Listener : ObservationSessionListener {
        val events = mutableListOf<ObservationEvent>()
        override fun onObservationEvent(event: ObservationEvent) { events += event }
    }

    // ---- fixtures ----

    private val ready = ObservationReadiness.Ready(RadioType.BLE, emptySet())

    private fun blocked(vararg blockers: com.example.radioobserver.core.radio.permissions.ReadinessBlocker) =
        ObservationReadiness.Blocked(RadioType.BLE, blockers.toSet())

    private fun snapshot(
        permissions: Map<PermissionId, PermissionState>,
        hardwarePresent: Boolean = true,
        radioEnabled: Boolean = true,
        locationOn: Boolean = true,
    ) = CapabilitySnapshot(
        apiBucket = AndroidApiBucket.API_33_PLUS,
        radio = RadioType.BLE,
        hardware = RadioHardwareStatus(hardwarePresent, radioEnabled, locationOn),
        permissions = permissions,
    )

    private fun coordinator(
        engine: FakeBleScanEngine = FakeBleScanEngine(),
        scheduler: ManualScheduler = ManualScheduler(),
        readiness: () -> ObservationReadiness = { ready },
        foreground: () -> Boolean = { true },
    ) = Triple(engine, scheduler, BleSessionCoordinator(engine, readiness, foreground, scheduler, clock = { 1_000L }))

    private fun ibeaconAdvertisement(): ByteArray {
        // AD structure: len, type 0xFF, Apple 0x004C (LE), iBeacon prefix 02 15 + 16B uuid +
        // 2B major + 2B minor + 1B txPower.
        val uuid = ByteArray(16) { (it + 1).toByte() }
        val manufacturer = byteArrayOf(0x02, 0x15) + uuid + byteArrayOf(0x00, 0x01, 0x00, 0x02, 0x59.toByte())
        val ad = byteArrayOf((manufacturer.size + 3).toByte(), 0xFF.toByte(), 0x4C, 0x00) + manufacturer
        return ad
    }

    // ---- 1) gating: no scan without verified gates ----

    @Test
    fun `W1 - shared live foreground holder gates the BLE source correctly after resume`() {
        // Root-cause regression (FieldDiag-04): ONE live holder owned by the supervisor
        // sink must gate the source. Not resumed yet -> typed ForegroundIneligible;
        // after resume -> accepted start. This is the corrected production wiring.
        val holder = java.util.concurrent.atomic.AtomicBoolean(false)
        val (engine, _, source) = coordinator(foreground = { holder.get() })
        val listener = Listener()
        assertTrue(source.startSession(BleSessionCoordinator.bleDefault(), listener)
            is SourceStartResult.Refused)
        assertEquals(0, engine.startCount) // no scan attempt before eligibility
        holder.set(true) // supervisor resume: the sink sets the SAME holder
        assertTrue(source.startSession(BleSessionCoordinator.bleDefault(), listener)
            is SourceStartResult.Started)
        assertEquals(1, engine.startCount)
    }

    @Test
    fun `W2 - detached gate holder reproduces the FieldDiag-04 phone signature`() {
        // Documentation of the DEFECT: the sink's holder (read by the debug header)
        // is true, but the source's gate provider was misbound to a different holder
        // that stays false -> ForegroundIneligible refusal with the panel claiming
        // source=true. The Android-layer misbinding itself is NOT host-testable; this
        // pins the observable decision signature it produced on the phone.
        val sinkHolder = java.util.concurrent.atomic.AtomicBoolean(true)
        val misboundGateHolder = java.util.concurrent.atomic.AtomicBoolean(false)
        val (engine, _, source) = coordinator(foreground = { misboundGateHolder.get() })
        assertTrue(sinkHolder.get()) // what the FieldDiag-04 header displayed
        val result = source.startSession(BleSessionCoordinator.bleDefault(), Listener())
        assertTrue((result as SourceStartResult.Refused).error
            is ObservationError.ForegroundIneligible)
        assertEquals(0, engine.startCount)
    }

    @Test
    fun `foreground ineligible refuses without touching the engine`() {
        val (engine, _, source) = coordinator(foreground = { false })
        val result = source.startSession(BleSessionCoordinator.bleDefault(), Listener())
        assertTrue(result is SourceStartResult.Refused)
        assertTrue((result as SourceStartResult.Refused).error is ObservationError.ForegroundIneligible)
        assertEquals(0, engine.startCount)
    }

    @Test
    fun `denied permission refuses with typed non-permanent error`() {
        val (engine, _, source) = coordinator(
            readiness = { blocked(com.example.radioobserver.core.radio.permissions.ReadinessBlocker.PermissionMissing(
                PermissionId.BLUETOOTH_SCAN, permanent = false)) })
        val result = source.startSession(BleSessionCoordinator.bleDefault(), Listener())
        val error = (result as SourceStartResult.Refused).error as ObservationError.PermissionDenied
        assertEquals(PermissionId.BLUETOOTH_SCAN, error.permission)
        assertFalse(error.permanent)
        assertEquals(0, engine.startCount)
    }

    @Test
    fun `permanently denied and hardware states map to their typed errors`() {
        val (engineHw, _, hwSource) = coordinator(
            readiness = { blocked(com.example.radioobserver.core.radio.permissions.ReadinessBlocker.HardwareUnavailable(RadioType.BLE)) })
        assertTrue((hwSource.startSession(BleSessionCoordinator.bleDefault(), Listener()) as SourceStartResult.Refused)
            .error is ObservationError.HardwareUnavailable)

        val (engineLoc, _, locSource) = coordinator(
            readiness = { blocked(com.example.radioobserver.core.radio.permissions.ReadinessBlocker.LocationServicesDisabled(RadioType.BLE)) })
        assertTrue((locSource.startSession(BleSessionCoordinator.bleDefault(), Listener()) as SourceStartResult.Refused)
            .error is ObservationError.LocationServicesDisabled)
        assertEquals(0, engineHw.startCount); assertEquals(0, engineLoc.startCount)
    }

    // ---- 2) start once; concurrent start refused ----

    @Test
    fun `valid gated start starts the engine exactly once and emits SessionStarted`() {
        val (engine, scheduler, source) = coordinator()
        val listener = Listener()
        val result = source.startSession(BleSessionCoordinator.bleDefault(), listener)
        assertTrue(result is SourceStartResult.Started)
        assertEquals(1, engine.startCount)
        assertEquals(1, scheduler.tasks.size) // timeout scheduled
        assertTrue(listener.events.single() is ObservationEvent.SessionStarted)
    }

    @Test
    fun `concurrent start is refused and does not restart the engine`() {
        val (engine, _, source) = coordinator()
        source.startSession(BleSessionCoordinator.bleDefault(), Listener())
        val second = source.startSession(BleSessionCoordinator.bleDefault(), Listener())
        assertTrue((second as SourceStartResult.Refused).error is ObservationError.SessionAlreadyActive)
        assertEquals(1, engine.startCount)
    }

    // ---- 3) defensive callback handling ----

    @Test
    fun `advertisement is parsed through the Phase 1 factory with evidence preserved`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        engine.callback!!.onAdvertisement("AC:BB:CC:DD:EE:FF", -55, ibeaconAdvertisement(), 100L, "Lounge beacon")

        val device = (listener.events.last() as ObservationEvent.DeviceObserved).device
        assertEquals("ble:ac:bb:cc:dd:ee:ff", device.observationKey)
        assertEquals("Lounge beacon", device.displayName)
        assertNotNull(device.manufacturerCandidate)
        assertNotNull(device.deviceTypeCandidate) // iBeacon layout = validated FORMAT evidence
    }

    @Test
    fun `null bytes null rssi and malformed payloads never crash and still deliver`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        engine.callback!!.onAdvertisement("AA:BB:CC:DD:EE:FF", null, null, 200L, null)
        engine.callback!!.onAdvertisement("AA:BB:CC:DD:EE:FF", 999, byteArrayOf(0x02, 0x01), 300L, null) // implausible RSSI, truncated frame
        engine.callback!!.onAdvertisement(null, -60, ibeaconAdvertisement(), 400L, null) // no address: fallback key path

        val devices = listener.events.filterIsInstance<ObservationEvent.DeviceObserved>()
        assertEquals(3, devices.size) // nothing crashed; nothing fabricated; all delivered
        assertEquals(null, devices[0].device.rssiDbm)
    }

    @Test
    fun `batch callbacks deliver each entry defensively`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        engine.callback!!.onBatch(
            listOf(
                EngineAdvertisement("11:11:11:11:11:11", -40, ibeaconAdvertisement(), 500L, "A"),
                EngineAdvertisement("22:22:22:22:22:22", null, null, 501L, null),
            ))
        assertEquals(2, listener.events.filterIsInstance<ObservationEvent.DeviceObserved>().size)
    }

    // ---- 6) dedup / newest-wins / late callbacks ----

    @Test
    fun `duplicate observations use store semantics - newest wins, stale ignored`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        engine.callback!!.onAdvertisement("AC:BB:CC:DD:EE:FF", -55, null, 1000L, null)
        engine.callback!!.onAdvertisement("AC:BB:CC:DD:EE:FF", -50, null, 2000L, null) // newer: accepted
        engine.callback!!.onAdvertisement("AC:BB:CC:DD:EE:FF", -60, null, 1500L, null) // older: ignored

        val deliveries = listener.events.filterIsInstance<ObservationEvent.DeviceObserved>()
        assertEquals(2, deliveries.size)
        assertEquals(-50, deliveries[1].device.rssiDbm)
    }

    @Test
    fun `late callback after cancel is ignored and never revives the session`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        val result = source.startSession(BleSessionCoordinator.bleDefault(), listener) as SourceStartResult.Started
        result.handle.cancel()
        val eventsAtEnd = listener.events.size

        engine.callback!!.onAdvertisement("AC:BB:CC:DD:EE:FF", -55, null, 5000L, null)

        assertEquals(eventsAtEnd, listener.events.size)
        assertFalse(result.handle.isActive)
    }

    // ---- 4) failure mapping and cleanup ----

    @Test
    fun `engine start failure refuses with typed ScanStartFailed and platform code`() {
        val engine = FakeBleScanEngine().apply { startFailure = EngineStartOutcome.Failed(ScanFailureReason.SECURITY_EXCEPTION) }
        val (_, _, source) = coordinator(engine = engine)
        val result = source.startSession(BleSessionCoordinator.bleDefault(), Listener())
        val error = (result as SourceStartResult.Refused).error as ObservationError.ScanStartFailed
        assertEquals(ScanFailureReason.SECURITY_EXCEPTION, error.reason)
    }

    @Test
    fun `asynchronous onScanFailed maps to typed error and ends the session without retry`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        engine.callback!!.onEngineFailure(ScanFailureReason.fromPlatformCode(6), 6) // too frequent

        val failed = listener.events.filterIsInstance<ObservationEvent.SourceFailed>().single()
        assertTrue((failed as ObservationEvent.SourceFailed).error is ObservationError.ScanStartFailed)
        val ended = listener.events.last() as ObservationEvent.SessionEnded
        assertEquals(SessionEndReason.SOURCE_ERROR, ended.reason)
        assertEquals(1, engine.startCount) // NO restart loop
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `unknown platform codes stay UNKNOWN - never success or fabricated reasons`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        engine.callback!!.onEngineFailure(ScanFailureReason.fromPlatformCode(99), 99)

        val failed = listener.events.filterIsInstance<ObservationEvent.SourceFailed>().single()
        assertEquals(ScanFailureReason.UNKNOWN, (failed.error as ObservationError.ScanStartFailed).reason)
        assertEquals(99, failed.error.platformCode)
    }

    // ---- 5) idempotent stop paths ----

    @Test
    fun `cancel stops the engine exactly once and is idempotent`() {
        val (engine, _, source) = coordinator()
        val result = source.startSession(BleSessionCoordinator.bleDefault(), Listener()) as SourceStartResult.Started

        assertTrue(result.handle.cancel())
        assertFalse(result.handle.cancel())

        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `duration timeout ends the session with DURATION_ELAPSED and stops the engine once`() {
        val (engine, scheduler, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        scheduler.tasks.single().second() // fire the timeout

        assertEquals(SessionEndReason.DURATION_ELAPSED, (listener.events.last() as ObservationEvent.SessionEnded).reason)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `foreground-loss signal stops the session with FOREGROUND_LOST`() {
        val (engine, _, source) = coordinator()
        val listener = Listener()
        source.startSession(BleSessionCoordinator.bleDefault(), listener)

        source.notifyForegroundLost()

        assertEquals(SessionEndReason.FOREGROUND_LOST, (listener.events.last() as ObservationEvent.SessionEnded).reason)
        assertEquals(1, engine.stopCount)
    }

    @Test
    fun `cleanup failure is reported not hidden`() {
        val engine = FakeBleScanEngine().apply { stopSucceeds = false }
        val (_, _, source) = coordinator(engine = engine)
        val listener = Listener()
        val result = source.startSession(BleSessionCoordinator.bleDefault(), listener) as SourceStartResult.Started

        result.handle.cancel()

        assertTrue(listener.events.any { it is ObservationEvent.SourceFailed &&
            (it as ObservationEvent.SourceFailed).error is ObservationError.ScanStopFailed })
    }

    @Test
    fun `default session window is short and within the hard ceiling`() {
        val spec = BleSessionCoordinator.bleDefault()
        assertTrue(spec.maxDurationMs in 10_000..30_000)
        assertTrue(spec.maxDurationMs <= ObservationSessionSpec.MAX_SESSION_DURATION_MS)
    }
}
