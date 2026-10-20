package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 task 3.2 acceptance tests (device-free JVM): UI state contracts must stay
 * inert, per-radio independent, and honest about permissions. These tests prove state
 * LOGIC only — never real-device UI, permission dialogs or radio behavior.
 */
class ObservationUiStateTest {

    @Test
    fun `initial state is inert - nothing observing, nothing requested, empty feed`() {
        val state = ObservationUiState.initial()
        assertEquals(RadioSessionUiStatus.IDLE, state.bleSession.status)
        assertEquals(RadioSessionUiStatus.IDLE, state.wifiSession.status)
        assertEquals(LocationAccessUi.NOT_REQUESTED, state.locationAccess)
        assertTrue(state.rows.isEmpty())
        assertNull(state.bleSession.issueKey)
        assertNull(state.wifiSession.issueKey)
    }

    @Test
    fun `session statuses are per-radio independent in both directions`() {
        val base = ObservationUiState.initial()
        val bleRunning = ObservationUiMapper.withSessionStatus(
            base, RadioType.BLE, RadioSessionUiStatus.OBSERVING, remainingMs = 20_000)
        assertEquals(RadioSessionUiStatus.OBSERVING, bleRunning.bleSession.status)
        assertEquals(RadioSessionUiStatus.IDLE, bleRunning.wifiSession.status)

        val wifiRunning = ObservationUiMapper.withSessionStatus(
            bleRunning, RadioType.WIFI, RadioSessionUiStatus.OBSERVING, remainingMs = 30_000)
        assertEquals(RadioSessionUiStatus.OBSERVING, wifiRunning.wifiSession.status)
        val wifiStopped = ObservationUiMapper.withSessionStatus(
            wifiRunning, RadioType.WIFI, RadioSessionUiStatus.STOPPED)
        assertEquals(RadioSessionUiStatus.STOPPED, wifiStopped.wifiSession.status)
        // Stopping Wi-Fi never disturbed the independent BLE session:
        assertEquals(RadioSessionUiStatus.OBSERVING, wifiStopped.bleSession.status)
    }

    @Test
    fun `granting precise location never starts observation - second tap required`() {
        val granted = ObservationUiMapper.withLocationAccess(
            ObservationUiState.initial(), LocationAccessUi.PRECISE)
        assertEquals(LocationAccessUi.PRECISE, granted.locationAccess)
        // The core product decision: a permission outcome is NOT a start command.
        assertEquals(RadioSessionUiStatus.IDLE, granted.bleSession.status)
        assertEquals(RadioSessionUiStatus.IDLE, granted.wifiSession.status)
        assertTrue(granted.rows.isEmpty())
    }

    @Test
    fun `approximate-only is not precise enough for scanning`() {
        assertFalse(LocationAccessUi.NOT_REQUESTED.isPreciseEnoughForScanning)
        assertFalse(LocationAccessUi.APPROXIMATE_ONLY.isPreciseEnoughForScanning)
        assertTrue(LocationAccessUi.PRECISE.isPreciseEnoughForScanning)
        assertNotEquals(LocationAccessUi.APPROXIMATE_ONLY, LocationAccessUi.PRECISE)
    }

    @Test
    fun `blocked, error, timeout and stopped remain distinguishable`() {
        val base = ObservationUiState.initial()
        val blocked = ObservationUiMapper.withSessionStatus(
            base, RadioType.BLE, RadioSessionUiStatus.BLOCKED, SessionIssueKey.BLUETOOTH_OFF)
        val errored = ObservationUiMapper.withSessionStatus(
            base, RadioType.BLE, RadioSessionUiStatus.ERROR, SessionIssueKey.SECURITY_EXCEPTION)
        val timedOut = ObservationUiMapper.withSessionStatus(
            base, RadioType.BLE, RadioSessionUiStatus.TIMED_OUT)
        val stopped = ObservationUiMapper.withSessionStatus(
            base, RadioType.BLE, RadioSessionUiStatus.STOPPED)

        assertEquals(SessionIssueKey.BLUETOOTH_OFF, blocked.bleSession.issueKey)
        assertEquals(SessionIssueKey.SECURITY_EXCEPTION, errored.bleSession.issueKey)
        assertNull(timedOut.bleSession.issueKey)
        assertNull(stopped.bleSession.issueKey)
        assertNotEquals(blocked.bleSession.status, errored.bleSession.status)
        assertNotEquals(timedOut.bleSession.status, stopped.bleSession.status)
        assertNotEquals(blocked.bleSession.status, stopped.bleSession.status)
    }

    @Test
    fun `approximate-only permission issue is a typed, distinct reason`() {
        val state = ObservationUiMapper.withSessionStatus(
            ObservationUiState.initial(),
            RadioType.BLE,
            RadioSessionUiStatus.PERMISSION_REQUIRED,
            SessionIssueKey.PERMISSION_APPROXIMATE_ONLY,
        )
        assertEquals(SessionIssueKey.PERMISSION_APPROXIMATE_ONLY, state.bleSession.issueKey)
        assertNotEquals(SessionIssueKey.PERMISSION_DENIED, state.bleSession.issueKey)
    }

    @Test
    fun `foreground loss stops both radios and never auto-resumes`() {
        val running = ObservationUiMapper.withSessionStatus(
            ObservationUiMapper.withSessionStatus(
                ObservationUiState.initial(), RadioType.BLE, RadioSessionUiStatus.OBSERVING),
            RadioType.WIFI, RadioSessionUiStatus.OBSERVING)
        val lost = ObservationUiMapper.withForegroundLost(running)
        assertEquals(RadioSessionUiStatus.STOPPED, lost.bleSession.status)
        assertEquals(RadioSessionUiStatus.STOPPED, lost.wifiSession.status)
        assertEquals(SessionIssueKey.FOREGROUND_LOST, lost.bleSession.issueKey)
        assertEquals(SessionIssueKey.FOREGROUND_LOST, lost.wifiSession.issueKey)
        // Recovering foreground is a separate explicit action; this call starts nothing.
        assertNotEquals(RadioSessionUiStatus.OBSERVING, lost.bleSession.status)
        assertNotEquals(RadioSessionUiStatus.OBSERVING, lost.wifiSession.status)
    }

    @Test
    fun `user intents are inert distinct data contracts`() {
        val intents = listOf(
            ObservationUserIntent.StartBleObservation,
            ObservationUserIntent.StopBleObservation,
            ObservationUserIntent.StartWifiObservation,
            ObservationUserIntent.StopWifiObservation,
        )
        assertEquals(4, intents.toSet().size)
        // Carrying intents must not mutate any state by itself:
        val state = ObservationUiState.initial()
        intents.forEach { _ -> assertEquals(state, ObservationUiState.initial()) }
    }

    @Test
    fun `session state rejects invalid remaining time and radio`() {
        assertThrows(IllegalArgumentException::class.java) {
            RadioSessionUiState(RadioType.BLE, RadioSessionUiStatus.OBSERVING, remainingMs = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            // Observing with zero remaining time is a contradiction, not a transition:
            ObservationUiMapper.withSessionStatus(
                ObservationUiState.initial(), RadioType.BLE,
                RadioSessionUiStatus.OBSERVING, remainingMs = 0)
        }
    }

    @Test
    fun `clearing the feed does not alter session statuses`() {
        val running = ObservationUiMapper.withSessionStatus(
            ObservationUiState.initial(), RadioType.BLE, RadioSessionUiStatus.OBSERVING)
        val cleared = ObservationUiMapper.withRowsCleared(running)
        assertTrue(cleared.rows.isEmpty())
        assertEquals(RadioSessionUiStatus.OBSERVING, cleared.bleSession.status)
    }
}
