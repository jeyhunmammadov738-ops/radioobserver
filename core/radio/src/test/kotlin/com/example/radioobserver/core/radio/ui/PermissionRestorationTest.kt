package com.example.radioobserver.core.radio.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase 3.8 focused regression tests: honest state restoration across recreation,
 * Settings returns and process-fresh starts. A denial may only be reported when a
 * genuine runtime request happened; nothing ever starts observation.
 */
class PermissionRestorationTest {

    private val none = RuntimeGrantSnapshot(
        fineLocation = false, coarseLocation = false, bluetoothScan = true)

    // Root-cause regression: a process-fresh Settings/resume refresh with no grant and
    // NO genuine runtime request must read NOT_REQUESTED, never a fabricated DENIED.

    @Test
    fun `fresh process Settings return with no grant and no prior request is NOT_REQUESTED`() {
        val state = PermissionFlowState() // fresh process: nothing previously requested
        val (next, effect) = PermissionFlowReducer.reduce(
            state, PermissionFlowEvent.SettingsReturned(none), apiLevel = 34)
        assertEquals(LocationAccessUi.NOT_REQUESTED, next.locationAccess)
        assertEquals(PermissionFlowEffect.None, effect)
    }

    @Test
    fun `Settings return after a genuine denied request stays honestly DENIED`() {
        val state = PermissionFlowState(previouslyRequested = setOf(PermissionFlowScope.BLE))
        val (next, _) = PermissionFlowReducer.reduce(
            state, PermissionFlowEvent.SettingsReturned(none), apiLevel = 34)
        assertEquals(LocationAccessUi.DENIED, next.locationAccess)
    }

    @Test
    fun `runtime result after a real dialog maps ungranted to DENIED and consumes the pending tap`() {
        val mid = PermissionFlowState(
            pendingRequestScope = PermissionFlowScope.BLE,
            pendingStartTap = PermissionFlowScope.BLE,
            previouslyRequested = setOf(PermissionFlowScope.BLE))
        val (next, effect) = PermissionFlowReducer.reduce(
            mid, PermissionFlowEvent.PermissionResult(PermissionFlowScope.BLE, none), apiLevel = 34)
        assertEquals(LocationAccessUi.DENIED, next.locationAccess)
        assertNull(next.pendingStartTap) // consumed, never executed
        assertNull(next.pendingRequestScope) // in-flight guard released
        assertEquals(PermissionFlowEffect.None, effect) // NO start effect exists at all
    }

    @Test
    fun `Activity recreation drops the transient Start tap but keeps the in-flight guard`() {
        val mid = PermissionFlowState(
            pendingRequestScope = PermissionFlowScope.BLE,
            pendingStartTap = PermissionFlowScope.BLE)
        val (next, _) = PermissionFlowReducer.reduce(
            mid, PermissionFlowEvent.ActivityRecreated, apiLevel = 34)
        assertNull(next.pendingStartTap) // never replayed on the new Activity
        assertEquals(PermissionFlowScope.BLE, next.pendingRequestScope) // late result still consumable
    }

    @Test
    fun `resolver never fabricates denial - grantless mapping needs request evidence`() {
        assertEquals(LocationAccessUi.NOT_REQUESTED, LocationAccessResolver.resolve(none, false))
        assertEquals(LocationAccessUi.DENIED, LocationAccessResolver.resolve(none, true))
        val coarse = RuntimeGrantSnapshot(false, true, true)
        assertEquals(LocationAccessUi.APPROXIMATE_ONLY, LocationAccessResolver.resolve(coarse, false))
    }
}
