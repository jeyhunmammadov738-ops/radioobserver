package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 task 3.5 acceptance tests (pure JVM): the control-screen projection rules.
 * They cover the STATE->PRESENTATION logic only; real Compose click delivery, layout,
 * TalkBack semantics and device rendering are UNVERIFIED without a device.
 */
class ControlScreenProjectionTest {

    private fun session(
        status: RadioSessionUiStatus,
        radio: RadioType = RadioType.BLE,
        remainingMs: Long? = null,
        issue: SessionIssueKey? = null,
    ) = RadioSessionUiState(radio, status, issue, remainingMs)

    // G: every status maps to distinct, non-conflated enable/disable combinations.

    @Test
    fun `G - status rules are distinct and non-conflated for every status`() {
        val rules = listOf(
            RadioSessionUiStatus.IDLE to (true to false),
            RadioSessionUiStatus.CHECKING to (true to false),
            RadioSessionUiStatus.PERMISSION_REQUIRED to (true to false),
            RadioSessionUiStatus.BLOCKED to (true to false),
            RadioSessionUiStatus.OBSERVING to (false to true),
            RadioSessionUiStatus.STOPPING to (false to true),
            RadioSessionUiStatus.STOPPED to (true to false),
            RadioSessionUiStatus.TIMED_OUT to (true to false),
            RadioSessionUiStatus.ERROR to (true to false),
        )
        rules.forEach { (status, expected) ->
            assertEquals("startEnabled($status)", expected.first, ControlScreenProjection.startEnabled(status))
            assertEquals("stopEnabled($status)", expected.second, ControlScreenProjection.stopEnabled(status))
        }
    }

    // D: the permission flow stays reachable; Start never auto-runs anything.

    @Test
    fun `D - PERMISSION_REQUIRED keeps Start enabled so the two-tap flow stays reachable`() {
        assertTrue(ControlScreenProjection.startEnabled(RadioSessionUiStatus.PERMISSION_REQUIRED))
        // The projection itself dispatches nothing (pure function): after a grant there is
        // no queued action; a NEW user Start is the only path back into gating.
    }

    // F: repeated taps cannot double-start: Start is disabled while active/stopping.

    @Test
    fun `F - Start is disabled while OBSERVING and STOPPING to prevent duplicate sessions`() {
        assertFalse(ControlScreenProjection.startEnabled(RadioSessionUiStatus.OBSERVING))
        assertFalse(ControlScreenProjection.startEnabled(RadioSessionUiStatus.STOPPING))
    }

    // E: blocked/failed states are never presented as observing.

    @Test
    fun `E - blocked, denied and error states are not observing`() {
        for (status in listOf(
            RadioSessionUiStatus.BLOCKED, RadioSessionUiStatus.PERMISSION_REQUIRED,
            RadioSessionUiStatus.ERROR, RadioSessionUiStatus.TIMED_OUT,
        )) {
            assertFalse("stopEnabled($status)", ControlScreenProjection.stopEnabled(status))
            assertTrue("retryable($status)", ControlScreenProjection.startEnabled(status))
        }
    }

    // K: session limit seconds come ONLY from real state; otherwise nothing is shown.

    @Test
    fun `K - session limit derives only from authoritative remainingMs, never invented`() {
        val live = session(RadioSessionUiStatus.OBSERVING, remainingMs = 20_000L)
        assertEquals(20L, ControlScreenProjection.sessionLimitSeconds(live))
        val idle = session(RadioSessionUiStatus.IDLE, remainingMs = 20_000L)
        assertNull(ControlScreenProjection.sessionLimitSeconds(idle))
        val liveNoValue = session(RadioSessionUiStatus.OBSERVING, remainingMs = null)
        assertNull(ControlScreenProjection.sessionLimitSeconds(liveNoValue))
    }

    // I + J: only sanitized public data is counted; honest empty-feed note placement.

    @Test
    fun `I - observation count is the sanitized row count only`() {
        val state = ObservationUiState(rows = listOf(
            ObservationRowUi("obs-1", RadioType.BLE, "Tag", "Apple", "iBeacon format",
                com.example.radioobserver.core.radio.model.ObservationConfidence.CANDIDATE,
                com.example.radioobserver.core.radio.signal.SignalBucket.NEAR,
                -55, false,
                com.example.radioobserver.core.radio.pipeline.FreshnessState.FRESH,
                0, 0, 1),
        ))
        assertEquals(1, ControlScreenProjection.observationCount(state.rows))
    }

    @Test
    fun `J and A - cold IDLE screen shows no empty-feed claim only after real observation`() {
        val cold = ObservationUiState.initial()
        assertFalse(ControlScreenProjection.showEmptyFeedNote(cold))
        val blockedOnly = ObservationUiState(
            wifiSession = session(RadioSessionUiStatus.BLOCKED, radio = RadioType.WIFI))
        assertFalse(ControlScreenProjection.showEmptyFeedNote(blockedOnly))
        val observing = ObservationUiState(
            bleSession = session(RadioSessionUiStatus.OBSERVING, remainingMs = 20_000L))
        assertTrue(ControlScreenProjection.showEmptyFeedNote(observing))
        val timedOut = ObservationUiState(
            bleSession = session(RadioSessionUiStatus.TIMED_OUT))
        assertTrue(ControlScreenProjection.showEmptyFeedNote(timedOut))
        val withRows = ObservationUiState(rows = listOf(ObservationRowUi(
            "obs-1", RadioType.WIFI, "Cafe", null, null,
            com.example.radioobserver.core.radio.model.ObservationConfidence.CANDIDATE,
            com.example.radioobserver.core.radio.signal.SignalBucket.NEAR,
            -67, false,
            com.example.radioobserver.core.radio.pipeline.FreshnessState.STALE,
            100, 0, 0)))
        assertFalse(ControlScreenProjection.showEmptyFeedNote(withRows))
    }

    // G/C: independence at the projection level: BLE and Wi-Fi rules never cross.

    @Test
    fun `C - BLE and Wi-Fi control rules are computed independently`() {
        val state = ObservationUiState(
            bleSession = session(RadioSessionUiStatus.OBSERVING),
            wifiSession = session(RadioSessionUiStatus.IDLE, radio = RadioType.WIFI),
        )
        assertFalse(ControlScreenProjection.startEnabled(state.bleSession.status))
        assertTrue(ControlScreenProjection.stopEnabled(state.bleSession.status))
        assertTrue(ControlScreenProjection.startEnabled(state.wifiSession.status))
        assertFalse(ControlScreenProjection.stopEnabled(state.wifiSession.status))
        assertEquals(RadioType.BLE, ControlScreenProjection.radioFor(state.bleSession))
        assertEquals(RadioType.WIFI, ControlScreenProjection.radioFor(state.wifiSession))
    }
}
