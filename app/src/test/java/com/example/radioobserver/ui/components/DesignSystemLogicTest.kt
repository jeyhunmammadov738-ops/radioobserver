package com.example.radioobserver.ui.components

import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PREMIUM UI/UX V2 (2026-10-23): device-free tests for the pure status→pill mapping
 * (tone NEVER carries meaning alone — the mapping is only presentation support) and
 * the truthful dashboard summary projection.
 */
class DesignSystemLogicTest {

    @Test
    fun `observing is the only ACTIVE pill kind`() {
        assertEquals(PillKind.ACTIVE, PillStyle.kindFor(RadioSessionUiStatus.OBSERVING))
    }

    @Test
    fun `blocked, error and timed-out are ERROR pills`() {
        assertEquals(PillKind.ERROR, PillStyle.kindFor(RadioSessionUiStatus.BLOCKED))
        assertEquals(PillKind.ERROR, PillStyle.kindFor(RadioSessionUiStatus.ERROR))
        assertEquals(PillKind.ERROR, PillStyle.kindFor(RadioSessionUiStatus.TIMED_OUT))
    }

    @Test
    fun `checking, stopping and permission-required are ATTENTION pills`() {
        assertEquals(PillKind.ATTENTION, PillStyle.kindFor(RadioSessionUiStatus.CHECKING))
        assertEquals(PillKind.ATTENTION, PillStyle.kindFor(RadioSessionUiStatus.STOPPING))
        assertEquals(PillKind.ATTENTION, PillStyle.kindFor(RadioSessionUiStatus.PERMISSION_REQUIRED))
    }

    @Test
    fun `idle and stopped are NEUTRAL pills`() {
        assertEquals(PillKind.NEUTRAL, PillStyle.kindFor(RadioSessionUiStatus.IDLE))
        assertEquals(PillKind.NEUTRAL, PillStyle.kindFor(RadioSessionUiStatus.STOPPED))
    }
}

class DashboardSummaryTest {

    @Test
    fun `zero active sources when both idle`() {
        assertEquals(
            0,
            DashboardSummary.activeSourceCount(
                RadioSessionUiStatus.IDLE, RadioSessionUiStatus.STOPPED))
        assertFalse(
            DashboardSummary.anyActive(RadioSessionUiStatus.IDLE, RadioSessionUiStatus.BLOCKED))
    }

    @Test
    fun `observing counts exactly the observing sources`() {
        assertEquals(
            1,
            DashboardSummary.activeSourceCount(
                RadioSessionUiStatus.OBSERVING, RadioSessionUiStatus.IDLE))
        assertEquals(
            2,
            DashboardSummary.activeSourceCount(
                RadioSessionUiStatus.OBSERVING, RadioSessionUiStatus.OBSERVING))
        assertTrue(
            DashboardSummary.anyActive(RadioSessionUiStatus.OBSERVING, RadioSessionUiStatus.IDLE))
    }

    @Test
    fun `non-observing states never count as active`() {
        // CHECKING/STOPPING are honest progress words but NOT an active session.
        assertEquals(
            0,
            DashboardSummary.activeSourceCount(
                RadioSessionUiStatus.CHECKING, RadioSessionUiStatus.STOPPING))
    }
}
