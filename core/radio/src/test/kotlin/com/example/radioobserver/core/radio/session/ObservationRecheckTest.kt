package com.example.radioobserver.core.radio.session

import com.example.radioobserver.core.radio.boundary.FakeBleObservationSource
import com.example.radioobserver.core.radio.boundary.FakeWifiObservationSource
import com.example.radioobserver.core.radio.lifecycle.RadioObservationSupervisor
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.ObservationPipeline
import com.example.radioobserver.core.radio.session.ObservationSessionOrchestrator.StartAuthorization
import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.core.radio.ui.SessionIssueKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 3.7 focused regression tests: the orchestrator's user-invoked readiness
 * RECHECK path. It must refresh a non-running status honestly, never start or stop a
 * session, never fabricate activity, never flicker on unchanged facts, and always
 * defer to the authoritative live status of an OBSERVING/STOPPING radio.
 */
class ObservationRecheckTest {

    private val ble = FakeBleObservationSource()
    private val wifi = FakeWifiObservationSource()
    private val supervisor = RadioObservationSupervisor(ble, wifi)
    private var nowMs = 10_000L
    private val states = mutableListOf<com.example.radioobserver.core.radio.ui.ObservationUiState>()

    private fun orchestrator() = ObservationSessionOrchestrator(
        supervisor = supervisor,
        pipelineFactory = { ObservationPipeline(clock = { nowMs }) },
        onUiState = { states += it },
    )

    private val fullAuth = StartAuthorization(
        foregroundEligible = true, preciseLocation = true, capabilityReady = true)

    // D/F/H: a recheck refreshes blocked permission/capability status without starting.

    @Test
    fun `recheck refreshes a blocked status and never starts a source`() {
        val o = orchestrator()
        states.clear()
        o.refreshReadinessStatus(RadioType.BLE, RadioSessionUiStatus.PERMISSION_REQUIRED,
            SessionIssueKey.PERMISSION_APPROXIMATE_ONLY)
        assertEquals(1, states.size)
        assertEquals(RadioSessionUiStatus.PERMISSION_REQUIRED,
            states.last().bleSession.status)
        assertEquals(SessionIssueKey.PERMISSION_APPROXIMATE_ONLY, states.last().bleSession.issueKey)
        assertEquals(0, ble.startedSessionCount)
        assertEquals(0, wifi.startedSessionCount)
    }

    // G: recovery refresh is NOT a Start; a blocked-to-ready recheck lands on IDLE.

    @Test
    fun `blocked-to-ready recheck lands on IDLE without touching the other radio`() {
        val o = orchestrator()
        // Arrange: BLE blocked via a failed Start (capability not ready).
        o.startBleSession(fullAuth.copy(capabilityReady = false))
        assertEquals(RadioSessionUiStatus.BLOCKED, o.uiSnapshot().bleSession.status)
        states.clear()
        o.refreshReadinessStatus(RadioType.BLE, RadioSessionUiStatus.IDLE, null)
        assertEquals(RadioSessionUiStatus.IDLE, states.last().bleSession.status)
        assertEquals(null, states.last().bleSession.issueKey)
        assertEquals(RadioSessionUiStatus.IDLE, states.last().wifiSession.status)
        assertEquals(0, ble.startedSessionCount)
    }

    // H/I: live session status always wins; unchanged facts cause no flicker.

    @Test
    fun `recheck is a no-op while the radio is OBSERVING`() {
        val o = orchestrator()
        supervisor.onHostResumed()
        o.startBleSession(fullAuth)
        assertEquals(RadioSessionUiStatus.OBSERVING, o.uiSnapshot().bleSession.status)
        states.clear()
        o.refreshReadinessStatus(RadioType.BLE, RadioSessionUiStatus.IDLE, null)
        assertEquals(0, states.size)
        assertEquals(RadioSessionUiStatus.OBSERVING, o.uiSnapshot().bleSession.status)
        assertEquals(1, ble.startedSessionCount)
    }

    @Test
    fun `unchanged recheck facts publish nothing - repeated clicks are idempotent`() {
        val o = orchestrator()
        o.refreshReadinessStatus(RadioType.WIFI, RadioSessionUiStatus.BLOCKED,
            SessionIssueKey.WIFI_OFF)
        states.clear()
        o.refreshReadinessStatus(RadioType.WIFI, RadioSessionUiStatus.BLOCKED,
            SessionIssueKey.WIFI_OFF)
        assertEquals(0, states.size)
    }

    // K: a recheck can never resurrect a terminated session as active.

    @Test
    fun `recheck cannot fabricate an active session`() {
        val o = orchestrator()
        var rejected = false
        try {
            o.refreshReadinessStatus(RadioType.BLE, RadioSessionUiStatus.OBSERVING, null)
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertEquals(true, rejected)
        assertEquals(0, ble.startedSessionCount)
    }
}
