package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 3.7 acceptance tests (pure JVM): honest per-radio recovery mapping. Android
 * Settings launch, permission dialogs and Compose runtime are UNVERIFIED (device-free).
 */
class RecoveryStatusProjectionTest {

    private fun session(
        status: RadioSessionUiStatus,
        issue: SessionIssueKey? = null,
        radio: RadioType = RadioType.BLE,
    ) = RadioSessionUiState(radio, status, issue, null)

    private fun recovery(status: RadioSessionUiStatus, issue: SessionIssueKey? = null) =
        RecoveryStatusProjection.recoveryFor(session(status, issue), LocationAccessUi.DENIED)

    // A: missing/denied/approximate permission map to truthful settings action, never auto-grant.

    @Test
    fun `A - permission denied and approximate-only map to permission Settings action`() {
        val denied = recovery(RadioSessionUiStatus.PERMISSION_REQUIRED, SessionIssueKey.PERMISSION_DENIED)
        assertEquals(RecoveryStatusProjection.RecoveryAction.OPEN_PERMISSION_SETTINGS, denied!!.action)
        val approx = recovery(RadioSessionUiStatus.PERMISSION_REQUIRED, SessionIssueKey.PERMISSION_APPROXIMATE_ONLY)
        assertEquals(RecoveryStatusProjection.RecoveryAction.OPEN_PERMISSION_SETTINGS, approx!!.action)
        assertEquals(RecoveryStatusProjection.RecoveryMessageKey.APPROXIMATE_ONLY_NEEDS_SETTING, approx.messageKey)
    }

    // B: no "permanently denied" concept exists in the projection vocabulary (structural).

    @Test
    fun `B - denial strength is never overstated - no permanent-denied vocabulary exists`() {
        val values = RecoveryStatusProjection.RecoveryMessageKey.entries.map { it.name }
        assert(values.none { it.contains("PERMANENT", ignoreCase = true) })
        // DENIED maps to the SAME honest settings path regardless of rationale history:
        assertEquals(
            RecoveryStatusProjection.recoveryFor(
                session(RadioSessionUiStatus.PERMISSION_REQUIRED, SessionIssueKey.PERMISSION_DENIED),
                LocationAccessUi.DENIED)!!,
            RecoveryStatusProjection.recoveryFor(
                session(RadioSessionUiStatus.PERMISSION_REQUIRED, SessionIssueKey.PERMISSION_DENIED),
                LocationAccessUi.NOT_REQUESTED)!!,
        )
    }

    // C + D: radios map independently; location-services is a Settings problem, not a permission one.

    @Test
    fun `C and D - radio-off rechecks independently and location-services opens location settings`() {
        val bleOff = recovery(RadioSessionUiStatus.BLOCKED, SessionIssueKey.BLUETOOTH_OFF)
        assertEquals(RecoveryStatusProjection.RecoveryAction.RECHECK, bleOff!!.action)
        assertEquals(RadioType.BLE, bleOff.radio)
        val locOff = recovery(RadioSessionUiStatus.BLOCKED, SessionIssueKey.LOCATION_SERVICES_OFF)
        assertEquals(RecoveryStatusProjection.RecoveryAction.OPEN_LOCATION_SETTINGS, locOff!!.action)
        assertEquals(
            RecoveryStatusProjection.RecoveryMessageKey.LOCATION_SERVICES_NEED_SETTING, locOff.messageKey)
    }

    // E: one failing radio never imposes a recovery banner on the other's live state.

    @Test
    fun `E - live BLE gets no banner while Wi-Fi is blocked and vice versa`() {
        val state = ObservationUiState(
            bleSession = session(RadioSessionUiStatus.OBSERVING, radio = RadioType.BLE),
            wifiSession = session(RadioSessionUiStatus.BLOCKED,
                SessionIssueKey.WIFI_OFF, radio = RadioType.WIFI))
        assertNull(RecoveryStatusProjection.recoveryFor(state.bleSession, LocationAccessUi.PRECISE))
        assertEquals(RecoveryStatusProjection.RecoveryAction.RECHECK,
            RecoveryStatusProjection.recoveryFor(state.wifiSession, LocationAccessUi.PRECISE)!!.action)
    }

    // F: source error / timeout / foreground loss / idle never claim recovery, never claim active.

    @Test
    fun `F - terminal and idle states map to no recovery action`() {
        for (status in listOf(
            RadioSessionUiStatus.IDLE, RadioSessionUiStatus.CHECKING,
            RadioSessionUiStatus.STOPPED, RadioSessionUiStatus.TIMED_OUT,
            RadioSessionUiStatus.ERROR, RadioSessionUiStatus.OBSERVING,
            RadioSessionUiStatus.STOPPING,
        )) {
            assertNull("unexpected banner for $status", recovery(status))
        }
        assertNull(recovery(RadioSessionUiStatus.BLOCKED, SessionIssueKey.HARDWARE_UNSUPPORTED))
        assertNull(recovery(RadioSessionUiStatus.ERROR, SessionIssueKey.INTERNAL_ERROR))
    }

    // G + J: action types are distinct; unsupported hardware has NO fabricated action.

    @Test
    fun `G - distinct states map to distinct action semantics`() {
        assertEquals(RecoveryStatusProjection.RecoveryAction.OPEN_PERMISSION_SETTINGS,
            recovery(RadioSessionUiStatus.BLOCKED, SessionIssueKey.PERMISSION_APPROXIMATE_ONLY)!!.action)
        assertEquals(RecoveryStatusProjection.RecoveryAction.RECHECK,
            recovery(RadioSessionUiStatus.BLOCKED, SessionIssueKey.SCAN_THROTTLED)!!.action)
        assertEquals(RecoveryStatusProjection.RecoveryAction.OPEN_LOCATION_SETTINGS,
            recovery(RadioSessionUiStatus.BLOCKED, SessionIssueKey.LOCATION_SERVICES_OFF)!!.action)
    }

    // L: the projection carries semantic keys only — no raw text, identifiers or claims.

    @Test
    fun `L - message vocabulary contains no identity threat or distance claims`() {
        val names = RecoveryStatusProjection.RecoveryMessageKey.entries.map { it.name }
        assert(names.none { it.contains("TRACKER") || it.contains("AIRTAG") || it.contains("DISTANCE") })
    }
}
