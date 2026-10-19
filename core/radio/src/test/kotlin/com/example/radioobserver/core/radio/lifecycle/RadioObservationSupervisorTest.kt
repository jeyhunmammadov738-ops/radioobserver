package com.example.radioobserver.core.radio.lifecycle

import com.example.radioobserver.core.radio.boundary.BleObservationSource
import com.example.radioobserver.core.radio.boundary.ForegroundLossAwareSource
import com.example.radioobserver.core.radio.boundary.ObservationError
import com.example.radioobserver.core.radio.boundary.ObservationEvent
import com.example.radioobserver.core.radio.boundary.ObservationHandle
import com.example.radioobserver.core.radio.boundary.ObservationSessionId
import com.example.radioobserver.core.radio.boundary.ObservationSessionListener
import com.example.radioobserver.core.radio.boundary.ObservationSessionSpec
import com.example.radioobserver.core.radio.boundary.SessionEndReason
import com.example.radioobserver.core.radio.boundary.SourceStartResult
import com.example.radioobserver.core.radio.boundary.WifiObservationSource
import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.6 acceptance tests (device-free JVM, all fakes). These validate the SUPERVISOR's
 * policy reactions only: actual Android lifecycle callback delivery on a real device is
 * UNVERIFIED and cannot be proven here.
 */
class RadioObservationSupervisorTest {

    // ---- fakes ----

    private open class FakeRadioSource : ObservationSessionListener {
        var startCount = 0
        var activeSessionTerminatedCount = 0 // only REAL active-session terminations
        var refusal: ObservationError? = null
        var active = false
        val events = mutableListOf<ObservationEvent>()

        override fun onObservationEvent(event: ObservationEvent) { events += event }

        fun startCore(listener: ObservationSessionListener): SourceStartResult {
            startCount += 1
            refusal?.let { return SourceStartResult.Refused(it) }
            active = true
            val handle = object : ObservationHandle {
                override val sessionId = ObservationSessionId("fake-${startCount}")
                override val isActive get() = active
                override fun cancel(): Boolean {
                    if (!active) return false
                    active = false
                    listener.onObservationEvent(ObservationEvent.SessionEnded(sessionId, SessionEndReason.CANCELLED_BY_USER))
                    return true
                }
            }
            listener.onObservationEvent(ObservationEvent.SessionStarted(handle.sessionId))
            return SourceStartResult.Started(handle)
        }
    }

    private class FakeAwareBleSource(val core: FakeRadioSource = FakeRadioSource()) :
        BleObservationSource, ForegroundLossAwareSource {
        override val radio = RadioType.BLE
        override fun startSession(spec: ObservationSessionSpec, listener: ObservationSessionListener) =
            core.startCore(listener)
        override fun notifyForegroundLost() {
            if (core.active) {
                core.active = false
                core.activeSessionTerminatedCount += 1
            }
        }
    }

    private class FakeAwareWifiSource(val core: FakeRadioSource = FakeRadioSource()) :
        WifiObservationSource, ForegroundLossAwareSource {
        override val radio = RadioType.WIFI
        override fun startObservation(spec: ObservationSessionSpec, listener: ObservationSessionListener) =
            core.startCore(listener)
        override fun notifyForegroundLost() {
            if (core.active) {
                core.active = false
                core.activeSessionTerminatedCount += 1
            }
        }
    }

    /** Legacy source WITHOUT ForegroundLossAwareSource: must fall back to handle.cancel(). */
    private class FakePlainBleSource(val core: FakeRadioSource = FakeRadioSource()) :
        BleObservationSource {
        override val radio = RadioType.BLE
        override fun startSession(spec: ObservationSessionSpec, listener: ObservationSessionListener) =
            core.startCore(listener)
    }

    private fun spec() = ObservationSessionSpec(20_000)

    private fun supervisor(
        ble: BleObservationSource = FakeAwareBleSource(),
        wifi: WifiObservationSource = FakeAwareWifiSource(),
        sink: MutableList<Boolean> = mutableListOf(),
    ) = Triple(
        ble, wifi,
        RadioObservationSupervisor(ble, wifi) { sink += it },
    )

    // ---- 1) eligibility defaults ----

    @Test
    fun `eligibility starts FALSE - start fails closed before the source is touched`() {
        val (ble, wifi, sup) = supervisor()
        assertFalse(sup.isForegroundEligible)
        val bleResult = sup.startBleSession(spec(), FakeRadioSource())
        val wifiResult = sup.startWifiObservation(spec(), FakeRadioSource())
        assertTrue((bleResult as SourceStartResult.Refused).error is ObservationError.ForegroundIneligible)
        assertTrue((wifiResult as SourceStartResult.Refused).error is ObservationError.ForegroundIneligible)
        assertEquals(0, (ble as FakeAwareBleSource).core.startCount) // source never touched
        assertEquals(0, (wifi as FakeAwareWifiSource).core.startCount)
    }

    @Test
    fun `RESUMED alone never auto-starts either radio`() {
        val (ble, wifi, sup) = supervisor()
        sup.onHostResumed()
        assertTrue(sup.isForegroundEligible)
        assertEquals(0, (ble as FakeAwareBleSource).core.startCount)
        assertEquals(0, (wifi as FakeAwareWifiSource).core.startCount)
    }

    // ---- 2) explicit start while eligible ----

    @Test
    fun `explicit start while eligible starts the underlying source once`() {
        val (ble, _, sup) = supervisor()
        sup.onHostResumed()
        val result = sup.startBleSession(spec(), FakeRadioSource())
        assertTrue(result is SourceStartResult.Started)
        assertEquals(1, (ble as FakeAwareBleSource).core.startCount)
    }

    @Test
    fun `underlying refusal passes through and no handle is tracked`() {
        val core = FakeRadioSource().apply {
            refusal = ObservationError.PermissionDenied(
                com.example.radioobserver.core.radio.permissions.PermissionId.ACCESS_FINE_LOCATION, false, RadioType.BLE) }
        val sup = RadioObservationSupervisor(FakeAwareBleSource(core), FakeAwareWifiSource())
        sup.onHostResumed()
        val result = sup.startBleSession(spec(), FakeRadioSource())
        assertTrue((result as SourceStartResult.Refused).error is ObservationError.PermissionDenied)
        assertFalse(sup.cancelBleSession()) // nothing tracked to cancel
    }

    // ---- 3) foreground loss stops both; idempotent repeat ----

    @Test
    fun `onHostPaused stops BOTH active sessions and eligibility goes false`() {
        val (ble, wifi, sup) = supervisor()
        sup.onHostResumed()
        sup.startBleSession(spec(), ble.let { it as FakeAwareBleSource }.core)
        sup.startWifiObservation(spec(), (wifi as FakeAwareWifiSource).core)

        sup.onHostPaused()

        assertFalse(sup.isForegroundEligible)
        assertEquals(1, (ble as FakeAwareBleSource).core.activeSessionTerminatedCount)
        assertEquals(1, (wifi as FakeAwareWifiSource).core.activeSessionTerminatedCount)
    }

    @Test
    fun `onStop and onDestroy repeat cleanup safely without duplicate terminations`() {
        val (ble, wifi, sup) = supervisor()
        sup.onHostResumed()
        sup.startBleSession(spec(), (ble as FakeAwareBleSource).core)
        sup.startWifiObservation(spec(), (wifi as FakeAwareWifiSource).core)
        sup.onHostPaused()
        val bleEnded = ble.core.activeSessionTerminatedCount
        val wifiEnded = wifi.core.activeSessionTerminatedCount

        sup.onHostStopped()
        sup.onHostDestroyed()

        assertEquals(bleEnded, ble.core.activeSessionTerminatedCount)
        assertEquals(wifiEnded, wifi.core.activeSessionTerminatedCount)
    }

    @Test
    fun `resuming after loss does not revive the old session - fresh start required`() {
        val (ble, _, sup) = supervisor()
        sup.onHostResumed()
        val listener = (ble as FakeAwareBleSource).core
        sup.startBleSession(spec(), listener)
        sup.onHostPaused()

        sup.onHostResumed()

        assertEquals(1, ble.core.startCount) // no new session, nothing revived
        assertTrue(sup.isForegroundEligible)
        // a NEW explicit start is allowed and requires the fresh eligibility
        val second = sup.startBleSession(spec(), listener)
        assertTrue(second is SourceStartResult.Started)
        assertEquals(2, ble.core.startCount)
    }

    // ---- 4) independence of the two sessions ----

    @Test
    fun `cancelling one session does not stop the other`() {
        val (ble, wifi, sup) = supervisor()
        sup.onHostResumed()
        sup.startBleSession(spec(), (ble as FakeAwareBleSource).core)
        sup.startWifiObservation(spec(), (wifi as FakeAwareWifiSource).core)

        assertTrue(sup.cancelBleSession())

        assertTrue(wifi.core.active) // Wi-Fi untouched
        assertFalse(ble.core.active)
        assertEquals(0, wifi.core.activeSessionTerminatedCount)
    }

    @Test
    fun `cancel is idempotent`() {
        val (ble, _, sup) = supervisor()
        sup.onHostResumed()
        sup.startBleSession(spec(), (ble as FakeAwareBleSource).core)
        assertTrue(sup.cancelBleSession())
        assertFalse(sup.cancelBleSession())
        assertEquals(1, ble.core.startCount)
    }

    // ---- 5) races: timeout vs pause, plain fallback ----

    @Test
    fun `timeout before pause leaves nothing to stop - pause is still safe`() {
        val (ble, wifi, sup) = supervisor()
        sup.onHostResumed()
        sup.startBleSession(spec(), (ble as FakeAwareBleSource).core)
        // simulate the session having ended by timeout inside the source
        ble.core.active = false
        sup.onHostPaused()
        // no crash, no duplicate termination, both sources consulted
        assertEquals(0, ble.core.activeSessionTerminatedCount)
    }

    @Test
    fun `source without ForegroundLossAware falls back to handle cancel`() {
        val core = FakeRadioSource()
        val sup = RadioObservationSupervisor(FakePlainBleSource(core), FakeAwareWifiSource())
        sup.onHostResumed()
        val listener = FakeRadioSource()
        sup.startBleSession(spec(), listener)

        sup.onHostPaused()

        assertFalse(core.active) // stopped via handle.cancel()
        assertTrue(listener.events.any { it is ObservationEvent.SessionEnded })
    }

    // ---- 6) eligibility sink and activity recreation ----

    @Test
    fun `eligibility sink receives the transitions`() {
        val sink = mutableListOf<Boolean>()
        val sup = RadioObservationSupervisor(FakeAwareBleSource(), FakeAwareWifiSource()) { sink += it }
        sup.onHostResumed()
        sup.onHostPaused()
        assertEquals(listOf(true, false), sink)
    }

    @Test
    fun `a new supervisor instance begins ineligible (activity recreation)`() {
        val (_, _, sup1) = supervisor()
        sup1.onHostResumed()
        val (_, _, sup2) = supervisor() // recreated Activity -> fresh supervisor
        assertFalse(sup2.isForegroundEligible)
    }

    @Test
    fun `start attempt while ineligible after pause fails closed`() {
        val (ble, _, sup) = supervisor()
        sup.onHostResumed()
        sup.onHostPaused()
        val result = sup.startBleSession(spec(), FakeRadioSource())
        assertTrue((result as SourceStartResult.Refused).error is ObservationError.ForegroundIneligible)
        assertEquals(0, (ble as FakeAwareBleSource).core.startCount)
    }
}
