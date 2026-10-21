package com.example.radioobserver.core.radio.session

import com.example.radioobserver.core.radio.boundary.FakeBleObservationSource
import com.example.radioobserver.core.radio.boundary.FakeWifiObservationSource
import com.example.radioobserver.core.radio.boundary.ObservationError
import com.example.radioobserver.core.radio.lifecycle.RadioObservationSupervisor
import com.example.radioobserver.core.radio.model.DeviceTypeCandidate
import com.example.radioobserver.core.radio.model.ManufacturerInfo
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.pipeline.ObservationPipeline
import com.example.radioobserver.core.radio.session.ObservationSessionOrchestrator.StartAuthorization
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.core.radio.ui.SessionIssueKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 task 3.4 acceptance tests (device-free JVM, scripted fakes, injected clock).
 * They validate the ORCHESTRATOR's decision/state logic only: real Android callback
 * delivery, radio reception, broadcast timing and lifecycle behavior remain UNVERIFIED.
 * The Wi-Fi fake deliberately exposes NO scan-trigger API: passive-only is structural.
 */
class ObservationSessionOrchestratorTest {

    private val ble = FakeBleObservationSource()
    private val wifi = FakeWifiObservationSource()
    private val supervisor = RadioObservationSupervisor(ble, wifi)
    private var nowMs = 10_000L
    private val states = mutableListOf<ObservationUiState>()

    private fun orchestrator() = ObservationSessionOrchestrator(
        supervisor = supervisor,
        pipelineFactory = { ObservationPipeline(clock = { nowMs }) },
        onUiState = { states += it },
    )

    private val fullAuth = StartAuthorization(
        foregroundEligible = true, preciseLocation = true, capabilityReady = true)

    private fun bleDevice(address: String = "aa:bb:cc:dd:ee:ff") = ObservedRadioDevice(
        observationKey = "ble:$address",
        radioType = RadioType.BLE,
        observedAtEpochMs = nowMs,
        displayName = "Tag",
        address = address,
        manufacturerCandidate = ManufacturerInfo(76, "Apple", listOf("0x004C")),
        deviceTypeCandidate = DeviceTypeCandidate("iBeacon format", ObservationConfidence.CANDIDATE, listOf("layout")),
        rssiDbm = -55,
        parseIssues = listOf("bad payload near $address"),
    )

    private fun wifiDevice(key: String) = ObservedRadioDevice(
        observationKey = key,
        radioType = RadioType.WIFI,
        observedAtEpochMs = nowMs,
        displayName = "Cafe",
        address = "11:22:33:44:55:66",
        sourceFields = mapOf("resultAgeMs" to "100", "resultsUpdatedFlag" to "true"),
        rssiDbm = -67,
    )

    // A -----------------------------------------------------------------------

    @Test
    fun `A - cold start and resume never trigger any source start`() {
        val o = orchestrator()
        supervisor.onHostResumed()
        assertEquals(0, ble.startedSessionCount)
        assertEquals(0, wifi.startedSessionCount)
        assertEquals(RadioSessionUiStatus.IDLE, o.uiSnapshot().bleSession.status)
        assertEquals(RadioSessionUiStatus.IDLE, o.uiSnapshot().wifiSession.status)
    }

    // B -----------------------------------------------------------------------

    @Test
    fun `B - one fully authorized start causes exactly one source session`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        val decision = o.startBleSession(fullAuth)
        assertTrue(decision is ObservationSessionOrchestrator.StartAttempt.Started)
        assertEquals(1, ble.startedSessionCount)
        assertEquals(RadioSessionUiStatus.OBSERVING, o.uiSnapshot().bleSession.status)
        assertEquals(20_000L, o.uiSnapshot().bleSession.remainingMs)
        // Wi-Fi untouched:
        assertEquals(0, wifi.startedSessionCount)
    }

    // C + F -------------------------------------------------------------------

    @Test
    fun `C - missing or approximate-only location yields a permission need, never a scan`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        val coarseOnly = fullAuth.copy(preciseLocation = false)
        val decision = o.startBleSession(coarseOnly)
        assertTrue(decision is ObservationSessionOrchestrator.StartAttempt.NeedsRuntimePermission)
        assertEquals(0, ble.startedSessionCount)
        assertEquals(
            RadioSessionUiStatus.PERMISSION_REQUIRED, o.uiSnapshot().bleSession.status)
        assertEquals(
            SessionIssueKey.PERMISSION_APPROXIMATE_ONLY, o.uiSnapshot().bleSession.issueKey)
        // Wi-Fi side fails identically closed:
        val wifiDecision = o.startWifiObservation(coarseOnly)
        assertTrue(wifiDecision is ObservationSessionOrchestrator.StartAttempt.NeedsRuntimePermission)
        assertEquals(0, wifi.startedSessionCount)
    }

    // D + E -------------------------------------------------------------------

    @Test
    fun `D and E - a grant never starts anything and the SECOND explicit tap starts`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth.copy(preciseLocation = false)) // dialog path happened
        // The user grants in the system dialog. NOTHING may auto-start here:
        assertEquals(0, ble.startedSessionCount)
        // A new explicit tap with fresh authorization then starts:
        val second = o.startBleSession(fullAuth)
        assertTrue(second is ObservationSessionOrchestrator.StartAttempt.Started)
        assertEquals(1, ble.startedSessionCount)
    }

    // G -----------------------------------------------------------------------

    @Test
    fun `G - BLE and Wi-Fi sessions are independent and can coexist`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth)
        o.startWifiObservation(fullAuth)
        ble.emit(bleDevice())
        wifi.deliverResults(listOf(wifiDevice("wifi:a1")), updated = true, resultsAgeMs = 100)
        var snap = o.uiSnapshot()
        assertEquals(RadioSessionUiStatus.OBSERVING, snap.bleSession.status)
        assertEquals(RadioSessionUiStatus.OBSERVING, snap.wifiSession.status)
        assertEquals(2, snap.rows.size)

        // Stopping BLE leaves Wi-Fi and its rows intact (the stopped radio's records stay
        // in the shared session feed until full teardown; they never block Wi-Fi):
        o.stopBleSession()
        snap = o.uiSnapshot()
        assertEquals(RadioSessionUiStatus.STOPPED, snap.bleSession.status)
        assertEquals(RadioSessionUiStatus.OBSERVING, snap.wifiSession.status)
        assertEquals(1, snap.rows.count { it.radio == RadioType.WIFI })
        // Wi-Fi session is still live and accepting new deliveries:
        wifi.deliverResults(listOf(wifiDevice("wifi:a2")), updated = true, resultsAgeMs = 100)
        assertEquals(2, o.uiSnapshot().rows.count { it.radio == RadioType.WIFI })
    }

    // H -----------------------------------------------------------------------

    @Test
    fun `H - duplicate start and repeated stop are idempotent`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth)
        val duplicate = o.startBleSession(fullAuth)
        assertTrue(duplicate is ObservationSessionOrchestrator.StartAttempt.Started)
        assertEquals(1, ble.startedSessionCount) // no second registration/deadline

        assertTrue(o.stopBleSession())
        assertFalse(o.stopBleSession()) // repeated stop is a safe no-op
        assertEquals(1, ble.startedSessionCount)
        assertEquals(RadioSessionUiStatus.STOPPED, o.uiSnapshot().bleSession.status)
    }

    // I -----------------------------------------------------------------------

    @Test
    fun `I - foreground loss stops BOTH sessions and resuming never restarts them`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth)
        o.startWifiObservation(fullAuth)
        supervisor.onHostPaused()
        var snap = o.uiSnapshot()
        assertEquals(RadioSessionUiStatus.STOPPED, snap.bleSession.status)
        assertEquals(SessionIssueKey.FOREGROUND_LOST, snap.bleSession.issueKey)
        assertEquals(RadioSessionUiStatus.STOPPED, snap.wifiSession.status)
        assertEquals(SessionIssueKey.FOREGROUND_LOST, snap.wifiSession.issueKey)
        assertTrue(snap.rows.isEmpty()) // pipeline disposed with the sessions

        // Re-entering foreground resurrects nothing:
        supervisor.onHostResumed()
        snap = o.uiSnapshot()
        assertEquals(RadioSessionUiStatus.STOPPED, snap.bleSession.status)
        assertEquals(RadioSessionUiStatus.STOPPED, snap.wifiSession.status)
        assertEquals(1, ble.startedSessionCount)
        assertEquals(1, wifi.startedSessionCount)
    }

    // J -----------------------------------------------------------------------

    @Test
    fun `J - a new owner after recreation starts from zero with no resurrected data`() {
        supervisor.onHostResumed()
        val old = orchestrator()
        old.startBleSession(fullAuth)
        ble.emit(bleDevice())
        assertEquals(1, old.uiSnapshot().rows.size)
        supervisor.onHostPaused() // recreation path: lifecycle teardown first

        // New Activity/process: a fresh orchestrator starts completely inactive:
        val fresh = orchestrator()
        val snap = fresh.uiSnapshot()
        assertEquals(RadioSessionUiStatus.IDLE, snap.bleSession.status)
        assertTrue(snap.rows.isEmpty())
        // And the old instance cannot resurrect anything either:
        assertTrue(old.uiSnapshot().rows.isEmpty())
        assertEquals(1, ble.startedSessionCount)
    }

    // K -----------------------------------------------------------------------

    @Test
    fun `K - timeout, source failure and late callbacks are handled safely`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth)
        ble.simulateDurationElapsed()
        assertEquals(RadioSessionUiStatus.TIMED_OUT, o.uiSnapshot().bleSession.status)

        // A late advertisement after the timeout can neither add rows nor revive:
        val rowsBefore = o.uiSnapshot().rows.size
        ble.emit(bleDevice())
        assertEquals(rowsBefore, o.uiSnapshot().rows.size)
        assertEquals(RadioSessionUiStatus.TIMED_OUT, o.uiSnapshot().bleSession.status)

        // Source failure with a typed cause, then its terminal event:
        o.startWifiObservation(fullAuth)
        wifi.failWith(ObservationError.RadioDisabled(RadioType.WIFI))
        val snap = o.uiSnapshot()
        assertEquals(RadioSessionUiStatus.ERROR, snap.wifiSession.status)
        assertEquals(SessionIssueKey.WIFI_OFF, snap.wifiSession.issueKey)
        // Late events after the failure are dropped; nothing auto-retries:
        wifi.deliverResults(listOf(wifiDevice("wifi:late")), updated = true, resultsAgeMs = 100)
        assertEquals(0, o.uiSnapshot().rows.size) // pipeline disposed at terminal end
    }

    // L + M -------------------------------------------------------------------

    @Test
    fun `L and M - sanitized rows reach UI state and raw identifiers never leak`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth)
        ble.emit(bleDevice("aa:bb:cc:dd:ee:ff"))
        val snap = o.uiSnapshot()
        assertEquals(1, snap.rows.size)
        val row = snap.rows.single()
        assertEquals(RadioType.BLE, row.radio)
        assertEquals(1, row.parseIssueCount)
        val rendered = snap.toString() + row.toString()
        assertFalse(rendered.contains("aa:bb:cc:dd:ee:ff"))
        assertFalse(rendered.contains("dd:ee:ff"))
        assertFalse(rendered.contains("bad payload")) // parse-issue text never surfaced
        assertTrue(row.rowId.startsWith("obs-"))
    }

    // N -----------------------------------------------------------------------

    @Test
    fun `N - Wi-Fi cached, stale, unknown freshness is preserved honestly`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startWifiObservation(fullAuth)
        wifi.deliverResults(
            listOf(wifiDevice("wifi:f1")), updated = true, resultsAgeMs = 100)
        wifi.deliverResults(
            listOf(wifiDevice("wifi:s1").copy(
                sourceFields = mapOf("resultAgeMs" to "600000", "resultsUpdatedFlag" to "true"))),
            updated = true, resultsAgeMs = 600_000)
        wifi.deliverResults(
            listOf(wifiDevice("wifi:c1").copy(
                sourceFields = mapOf("resultAgeMs" to "500", "resultsUpdatedFlag" to "false"))),
            updated = false, resultsAgeMs = 500)
        wifi.deliverResults(
            listOf(wifiDevice("wifi:u1").copy(
                sourceFields = mapOf("resultAgeMs" to "notparseable", "resultsUpdatedFlag" to "true"))),
            updated = true, resultsAgeMs = null)
        val rows = o.uiSnapshot().rows
        assertEquals(4, rows.size)
        val freshnessById = rows.associate { it.rowId to it.freshness }
        assertEquals(FreshnessState.FRESH, freshnessById["obs-1"])
        assertEquals(FreshnessState.STALE, freshnessById["obs-2"])
        // Cached (updated=false) delivery is never presented as fresh:
        assertNotEquals(FreshnessState.FRESH, freshnessById["obs-3"])
        // Invalid/unparseable age keeps UNKNOWN semantics:
        assertEquals(FreshnessState.UNKNOWN, freshnessById["obs-4"])
    }

    // O -----------------------------------------------------------------------

    @Test
    fun `O - Wi-Fi observation stays passive - one session, no scan side effects`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startWifiObservation(fullAuth)
        // FakeWifiObservationSource exposes only startObservation/deliver/fail/stop seams;
        // the WifiObservationSource contract has NO scan-trigger member. The orchestrator
        // itself only consumes events (structural guarantee; fake has no scan API).
        assertEquals(1, wifi.startedSessionCount)
        o.stopWifiObservation()
        assertEquals(1, wifi.startedSessionCount)
        assertFalse(wifi.hasActiveSession)
    }

    // P -----------------------------------------------------------------------

    @Test
    fun `P - pipeline disposal respects radio independence and resets session identity`() {
        supervisor.onHostResumed()
        val o = orchestrator()
        o.startBleSession(fullAuth)
        o.startWifiObservation(fullAuth)
        ble.emit(bleDevice())
        wifi.deliverResults(listOf(wifiDevice("wifi:a1")), updated = true, resultsAgeMs = 100)
        assertEquals(2, o.uiSnapshot().rows.size)

        o.stopBleSession() // BLE ends; Wi-Fi still live: its data must survive
        val afterBleStop = o.uiSnapshot()
        assertEquals(1, afterBleStop.rows.count { it.radio == RadioType.WIFI })

        o.stopWifiObservation() // last session: volatile pipeline is disposed
        assertTrue(o.uiSnapshot().rows.isEmpty())

        // A late Wi-Fi delivery after disposal cannot resurrect anything:
        wifi.deliverResults(listOf(wifiDevice("wifi:late")), updated = true, resultsAgeMs = 100)
        assertTrue(o.uiSnapshot().rows.isEmpty())

        // A fresh session gets a fresh pipeline with session-local identities restarted:
        o.startBleSession(fullAuth)
        ble.emit(bleDevice("11:22:33:44:55:66"))
        val freshRows = o.uiSnapshot().rows
        assertEquals(1, freshRows.size)
        assertTrue(freshRows.single().rowId.startsWith("obs-1"))
    }
}
