package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.boundary.ObservationSessionSpec
import com.example.radioobserver.core.radio.permissions.AndroidApiBucket
import com.example.radioobserver.core.radio.permissions.CapabilitySnapshot
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.PermissionId
import com.example.radioobserver.core.radio.permissions.PermissionState
import com.example.radioobserver.core.radio.permissions.RadioHardwareStatus
import com.example.radioobserver.core.radio.permissions.ReadinessBlocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.4 acceptance tests (device-free JVM, all fakes): gates before registration/reading,
 * broadcast handling, freshness honesty, defensive records, fail-closed permission loss,
 * single-session + exactly-once cleanup, and no-scan-injection proof by construction.
 * These tests do NOT claim real-device broadcast delivery, OEM behavior, or permission UX.
 */
class WifiSessionCoordinatorTest {

    // ---- test doubles ----

    private class FakeWifiResultEngine : WifiResultEngine {
        var startCount = 0; private set
        var stopCount = 0; private set
        var startFailure: WifiEngineStartOutcome.Failed? = null
        var stopSucceeds = true
        var callback: WifiResultEngineCallback? = null; private set

        override fun start(callback: WifiResultEngineCallback): WifiEngineStartOutcome {
            startCount += 1
            startFailure?.let { return it }
            this.callback = callback
            return WifiEngineStartOutcome.Started
        }

        override fun stop(): Boolean {
            if (startCount == 0 || stopCount >= startCount) return false
            stopCount += 1
            return stopSucceeds
        }
    }

    private class FakeFetcher : WifiResultFetcher {
        var fetchCount = 0; private set
        var outcome: WifiFetchOutcome = WifiFetchOutcome.Results(emptyList())

        override fun fetch(): WifiFetchOutcome {
            fetchCount += 1
            return outcome
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

    private val ready = ObservationReadiness.Ready(RadioType.WIFI, emptySet())

    @Test
    fun `W3 - shared live foreground holder gates the Wi-Fi source independently`() {
        // Root-cause regression (FieldDiag-04): the Wi-Fi source must read the SAME live
        // holder the supervisor sink sets — independently of BLE. Not resumed ->
        // ForegroundIneligible; after resume -> accepted (passive registration).
        val holder = java.util.concurrent.atomic.AtomicBoolean(false)
        val sys = system(foreground = { holder.get() })
        assertTrue(sys.source.startObservation(
            ObservationSessionSpec(30_000),
            object : ObservationSessionListener {
                override fun onObservationEvent(event: ObservationEvent) {}
            }) is SourceStartResult.Refused)
        holder.set(true)
        val started = sys.source.startObservation(
            ObservationSessionSpec(30_000),
            object : ObservationSessionListener {
                override fun onObservationEvent(event: ObservationEvent) {}
            })
        assertTrue(started is SourceStartResult.Started)
    }

    private fun blocked(vararg blockers: ReadinessBlocker) =
        ObservationReadiness.Blocked(RadioType.WIFI, blockers.toSet())

    private fun system(
        engine: FakeWifiResultEngine = FakeWifiResultEngine(),
        fetcher: FakeFetcher = FakeFetcher(),
        scheduler: ManualScheduler = ManualScheduler(),
        readiness: () -> ObservationReadiness = { ready },
        foreground: () -> Boolean = { true },
        clock: () -> Long = { 1_000L },
    ) = System(fixtureEngine = engine, fetcher = fetcher, scheduler = scheduler,
        source = WifiSessionCoordinator(engine, fetcher, readiness, foreground, scheduler, clock))

    private data class System(
        val fixtureEngine: FakeWifiResultEngine,
        val fetcher: FakeFetcher,
        val scheduler: ManualScheduler,
        val source: WifiObservationSource,
    )

    private fun record(
        bssid: String? = "aa:bb:cc:dd:ee:ff",
        ssid: String? = "Cafe",
        rssi: Int? = -67,
        bootTimestampUs: Long? = 500_000_000,
        ageMs: Long? = 1_500,
    ) = WifiAccessPointRecord(bssid, ssid, rssi, bootTimestampUs, ageMs)

    // ---- 1) gates: nothing registers or reads before they pass ----

    @Test
    fun `foreground ineligible refuses without registering or fetching`() {
        val sys = system(foreground = { false })
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener())
        assertTrue((result as SourceStartResult.Refused).error is ObservationError.ForegroundIneligible)
        assertEquals(0, sys.fixtureEngine.startCount)
        assertEquals(0, sys.fetcher.fetchCount)
    }

    @Test
    fun `missing fine location refuses typed without registration`() {
        val sys = system(
            readiness = { blocked(ReadinessBlocker.PermissionMissing(PermissionId.ACCESS_FINE_LOCATION, permanent = false)) })
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener())
        val denied = (result as SourceStartResult.Refused).error as ObservationError.PermissionDenied
        assertEquals(PermissionId.ACCESS_FINE_LOCATION, denied.permission)
        assertFalse(denied.permanent)
        assertEquals(0, sys.fixtureEngine.startCount)
        assertEquals(0, sys.fetcher.fetchCount)
    }

    @Test
    fun `wifi unavailable and location off map to their typed errors`() {
        val hw = system(readiness = { blocked(ReadinessBlocker.HardwareUnavailable(RadioType.WIFI)) })
        assertTrue((hw.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener()) as SourceStartResult.Refused)
            .error is ObservationError.HardwareUnavailable)
        val loc = system(readiness = { blocked(ReadinessBlocker.LocationServicesDisabled(RadioType.WIFI)) })
        assertTrue((loc.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener()) as SourceStartResult.Refused)
            .error is ObservationError.LocationServicesDisabled)
    }

    // ---- 2) start once; contention refused ----

    @Test
    fun `valid gated start registers the receiver once and schedules the timeout`() {
        val sys = system()
        val listener = Listener()
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)
        assertTrue(result is SourceStartResult.Started)
        assertEquals(1, sys.fixtureEngine.startCount)
        assertEquals(1, sys.scheduler.tasks.size)
        assertTrue(listener.events.single() is ObservationEvent.SessionStarted)
    }

    @Test
    fun `concurrent start is refused without re-registering`() {
        val sys = system()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener())
        val second = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener())
        assertTrue((second as SourceStartResult.Refused).error is ObservationError.SessionAlreadyActive)
        assertEquals(1, sys.fixtureEngine.startCount)
    }

    // ---- 3) broadcast handling + freshness honesty ----

    @Test
    fun `successful updated broadcast fetches once and delivers classified observations`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Results(listOf(record())) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val delivery = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single()
        assertEquals(1, delivery.observations.size)
        assertTrue(delivery.updatedSinceLastDelivery)
        assertEquals("wifi:aa:bb:cc:dd:ee:ff", delivery.observations.single().observationKey)
        assertEquals("Cafe", delivery.observations.single().displayName) // display hint only
        assertNotNull(delivery.observations.single().manufacturerCandidate)
        assertEquals(1, sys.fetcher.fetchCount)
    }

    @Test
    fun `false or missing update flag delivers results marked NOT new`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Results(listOf(record(ageMs = 60_000))) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = false)

        val delivery = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single()
        assertFalse(delivery.updatedSinceLastDelivery) // cached data never labeled as new
        assertEquals(60_000L, delivery.resultsAgeMs)
    }

    @Test
    fun `unknown age and boot timestamp travel honestly in source fields`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Results(listOf(record(bootTimestampUs = null, ageMs = null))) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val device = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>()
            .single().observations.single()
        assertEquals("unknown", device.sourceFields["resultAgeMs"])
        assertEquals("unknown", device.sourceFields["bootTimestampUs"])
        assertEquals("true", device.sourceFields["resultsUpdatedFlag"])
        assertEquals("false", device.sourceFields["scanInitiatedByApp"])
    }

    // ---- 4) defensive records ----

    @Test
    fun `malformed empty fields and invalid rssis never crash`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Results(listOf(
                record(bssid = null, ssid = "  ", rssi = null),
                record(bssid = "zz::malformed", ssid = null, rssi = 999),
                record(ssid = "\u0000ctrl", bssid = "11:22:33:44:55:66"),
            )) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val devices = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single().observations
        assertEquals(3, devices.size) // all delivered, none fabricated, none crashed
        assertEquals(null, devices[0].rssiDbm)
        assertTrue(devices[1].parseIssues.isNotEmpty()) // malformed BSSID treated as absent
    }

    @Test
    fun `future and negative boot timestamps stay unknown ages`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Results(listOf(
                record(bootTimestampUs = -5, ageMs = null),
                record(bootTimestampUs = 9_000_000_000_000, ageMs = null),
            )) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val devices = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single().observations
        assertTrue(devices.all { it.sourceFields["resultAgeMs"] == "unknown" })
        assertEquals(null, listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single().resultsAgeMs)
    }

    @Test
    fun `duplicate and out-of-order deliveries use newest-wins receipt semantics`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Results(listOf(record(ssid = "First"))) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)
        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)
        sys.fetcher.outcome = WifiFetchOutcome.Results(listOf(record(ssid = "Second")))
        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val deliveries = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>()
        assertEquals(2, deliveries.size)
        assertEquals("Second", deliveries[1].observations.single().displayName)
    }

    // ---- 5) fail-closed permission/foreground loss at broadcast ----

    @Test
    fun `permission loss at broadcast fails closed without reading results`() {
        val sys = system(fetcher = FakeFetcher().apply {
            outcome = WifiFetchOutcome.Failed(
                ObservationError.PermissionDenied(PermissionId.ACCESS_FINE_LOCATION, false, RadioType.WIFI)) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val failed = listener.events.filterIsInstance<ObservationEvent.SourceFailed>().single()
        assertTrue((failed as ObservationEvent.SourceFailed).error is ObservationError.PermissionDenied)
        assertEquals(SessionEndReason.SOURCE_ERROR, (listener.events.last() as ObservationEvent.SessionEnded).reason)
        assertEquals(1, sys.fixtureEngine.stopCount) // fail closed, unregistered once, no retry
    }

    @Test
    fun `readiness loss at broadcast ends the session typed`() {
        var blockNow = false
        val sys = system(readiness = {
            if (blockNow) blocked(ReadinessBlocker.RadioDisabled(RadioType.WIFI)) else ready })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)
        blockNow = true

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        assertTrue(listener.events.filterIsInstance<ObservationEvent.SourceFailed>()
            .single().error is ObservationError.RadioDisabled)
        assertEquals(0, sys.fetcher.fetchCount) // never read after loss
    }

    @Test
    fun `foreground loss at broadcast stops with FOREGROUND_LOST`() {
        var fg = true
        val sys = system(foreground = { fg })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)
        fg = false

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        assertEquals(SessionEndReason.FOREGROUND_LOST, (listener.events.last() as ObservationEvent.SessionEnded).reason)
        assertEquals(0, sys.fetcher.fetchCount)
    }

    // ---- 6) cleanup exactly once; late/duplicate broadcasts ----

    @Test
    fun `late broadcast after cancel is ignored and never revives the session`() {
        val sys = system()
        val listener = Listener()
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener) as SourceStartResult.Started
        result.handle.cancel()
        val eventsAtEnd = listener.events.size

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        assertEquals(eventsAtEnd, listener.events.size)
        assertEquals(0, sys.fetcher.fetchCount)
    }

    @Test
    fun `cancel is idempotent and unregisters exactly once`() {
        val sys = system()
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener()) as SourceStartResult.Started
        assertTrue(result.handle.cancel())
        assertFalse(result.handle.cancel())
        assertEquals(1, sys.fixtureEngine.stopCount)
    }

    @Test
    fun `timeout ends with DURATION_ELAPSED and unregisters once`() {
        val sys = system()
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.scheduler.tasks.single().second()

        assertEquals(SessionEndReason.DURATION_ELAPSED, (listener.events.last() as ObservationEvent.SessionEnded).reason)
        assertEquals(1, sys.fixtureEngine.stopCount)
    }

    @Test
    fun `explicit foreground-loss signal stops with FOREGROUND_LOST`() {
        val sys = system()
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        (sys.source as WifiSessionCoordinator).notifyForegroundLost()

        assertEquals(SessionEndReason.FOREGROUND_LOST, (listener.events.last() as ObservationEvent.SessionEnded).reason)
        assertEquals(1, sys.fixtureEngine.stopCount)
    }

    @Test
    fun `registration failure at start refuses typed and nothing else happens`() {
        val engine = FakeWifiResultEngine().apply {
            startFailure = WifiEngineStartOutcome.Failed(
                ObservationError.ScanStartFailed(ScanFailureReason.REGISTRATION_FAILED, null, RadioType.WIFI)) }
        val sys = system(engine = engine)
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), Listener())
        assertTrue((result as SourceStartResult.Refused).error is ObservationError.ScanStartFailed)
        assertEquals(0, sys.fetcher.fetchCount)
    }

    @Test
    fun `unregister failure is reported not hidden`() {
        val engine = FakeWifiResultEngine().apply { stopSucceeds = false }
        val sys = system(engine = engine)
        val listener = Listener()
        val result = sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener) as SourceStartResult.Started

        result.handle.cancel()

        assertTrue(listener.events.any { it is ObservationEvent.SourceFailed &&
            (it as ObservationEvent.SourceFailed).error is ObservationError.ScanStopFailed })
    }

    @Test
    fun `empty result cache delivers an empty honest delivery`() {
        val sys = system(fetcher = FakeFetcher().apply { outcome = WifiFetchOutcome.Results(emptyList()) })
        val listener = Listener()
        sys.source.startObservation(WifiSessionCoordinator.wifiDefault(), listener)

        sys.fixtureEngine.callback!!.onScanResultsBroadcast(updated = true)

        val delivery = listener.events.filterIsInstance<ObservationEvent.WifiResultsAvailable>().single()
        assertTrue(delivery.observations.isEmpty())
        assertEquals(null, delivery.resultsAgeMs)
    }
}
