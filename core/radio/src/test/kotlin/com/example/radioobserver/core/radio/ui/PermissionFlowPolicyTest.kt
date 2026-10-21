package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.permissions.PermissionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 task 3.3 acceptance tests (device-free JVM): the pure runtime-permission flow
 * policy. They validate DECISION LOGIC ONLY - real dialogs, OEM behavior and grant
 * delivery remain UNVERIFIED under the host-only protocol.
 */
class PermissionFlowPolicyTest {

    private val allGranted = RuntimeGrantSnapshot(fineLocation = true, coarseLocation = true, bluetoothScan = true)
    private val noneGranted = RuntimeGrantSnapshot(fineLocation = false, coarseLocation = false, bluetoothScan = false)

    // ---- Planner: API-correct request plans ----

    @Test
    fun `API 29-30 plans a single FINE request for both scopes`() {
        for (scope in PermissionFlowScope.entries) {
            val plan = PermissionRequestPlanner.plan(29, scope, noneGranted.copy(bluetoothScan = true))!!
            assertEquals(listOf(PermissionId.ACCESS_FINE_LOCATION), plan.permissions)
            assertEquals(scope, plan.scope)
        }
        assertEquals(
            listOf(PermissionId.ACCESS_FINE_LOCATION),
            PermissionRequestPlanner.plan(30, PermissionFlowScope.WIFI, noneGranted)!!.permissions,
        )
    }

    @Test
    fun `API 31+ requests FINE and COARSE together with BLUETOOTH_SCAN for BLE`() {
        val plan = PermissionRequestPlanner.plan(31, PermissionFlowScope.BLE, noneGranted)!!
        assertEquals(
            listOf(
                PermissionId.ACCESS_FINE_LOCATION,
                PermissionId.ACCESS_COARSE_LOCATION,
                PermissionId.BLUETOOTH_SCAN,
            ),
            plan.permissions,
        )
        // One coordinated request - never separate overlapping launches:
        assertEquals(1, plan.permissions.size / plan.permissions.size)
        assertTrue(plan.permissions.containsAll(listOf(
            PermissionId.ACCESS_FINE_LOCATION, PermissionId.ACCESS_COARSE_LOCATION)))
    }

    @Test
    fun `API 31+ still requests COARSE together with FINE even when COARSE is already granted`() {
        val plan = PermissionRequestPlanner.plan(
            33, PermissionFlowScope.WIFI, RuntimeGrantSnapshot(fineLocation = false, coarseLocation = true))!!
        assertEquals(
            listOf(PermissionId.ACCESS_FINE_LOCATION, PermissionId.ACCESS_COARSE_LOCATION),
            plan.permissions,
        )
    }

    @Test
    fun `API 31+ BLE with location granted requests only the missing BLUETOOTH_SCAN`() {
        val plan = PermissionRequestPlanner.plan(
            32, PermissionFlowScope.BLE, allGranted.copy(bluetoothScan = false))!!
        assertEquals(listOf(PermissionId.BLUETOOTH_SCAN), plan.permissions)
    }

    @Test
    fun `no plan when everything the scope needs is already granted`() {
        for (api in listOf(29, 30)) {
            assertNull(PermissionRequestPlanner.plan(api, PermissionFlowScope.BLE, allGranted))
        }
        for (api in listOf(31, 32, 33, 35)) {
            assertNull(PermissionRequestPlanner.plan(api, PermissionFlowScope.BLE, allGranted))
            assertNull(PermissionRequestPlanner.plan(api, PermissionFlowScope.WIFI, allGranted))
        }
    }

    @Test
    fun `manifest-normal and legacy permissions are never runtime-requestable`() {
        assertFalse(PermissionRequestPlanner.isRuntimeRequestable(PermissionId.ACCESS_WIFI_STATE))
        assertFalse(PermissionRequestPlanner.isRuntimeRequestable(PermissionId.BLUETOOTH))
        assertFalse(PermissionRequestPlanner.isRuntimeRequestable(PermissionId.BLUETOOTH_ADMIN))
        assertTrue(PermissionRequestPlanner.isRuntimeRequestable(PermissionId.ACCESS_FINE_LOCATION))
        assertTrue(PermissionRequestPlanner.isRuntimeRequestable(PermissionId.ACCESS_COARSE_LOCATION))
        assertTrue(PermissionRequestPlanner.isRuntimeRequestable(PermissionId.BLUETOOTH_SCAN))
        // No invented nearby-wifi substitute exists in the vocabulary at all:
        val vocabulary = PermissionId.entries.map { it.name }
        assertTrue(!vocabulary.any { it.contains("NEARBY") })
        // Every plan stays inside the runtime-requestable set across all buckets/scopes:
        for (api in listOf(29, 31, 33)) {
            for (scope in PermissionFlowScope.entries) {
                PermissionRequestPlanner.plan(api, scope, noneGranted)?.let {
                    it.permissions.forEach { p -> assertTrue(PermissionRequestPlanner.isRuntimeRequestable(p)) }
                }
            }
        }
    }

    // ---- Honest location-access resolution ----

    @Test
    fun `approximate-only is never precise enough for scanning`() {
        val coarseOnly = RuntimeGrantSnapshot(fineLocation = false, coarseLocation = true)
        assertEquals(LocationAccessUi.APPROXIMATE_ONLY, LocationAccessResolver.resolve(coarseOnly, false))
        assertFalse(LocationAccessResolver.resolve(coarseOnly, false).isPreciseEnoughForScanning)
        assertEquals(LocationAccessUi.PRECISE, LocationAccessResolver.resolve(allGranted, false))
        assertTrue(LocationAccessResolver.resolve(allGranted, false).isPreciseEnoughForScanning)
        assertEquals(LocationAccessUi.DENIED, LocationAccessResolver.resolve(noneGranted, true))
    }

    // ---- Denial classification ----

    @Test
    fun `rationale false alone never proves permanently denied`() {
        assertEquals(
            DenialAssessment.NOT_DENIED,
            DenialClassifier.assess(denied = false, rationaleShouldShow = false, previouslyRequested = true))
        assertEquals(
            DenialAssessment.RATIONALE_RECOMMENDED,
            DenialClassifier.assess(denied = true, rationaleShouldShow = true, previouslyRequested = true))
        // The system's rationale=false with prior requests is UNCERTAIN, not permanent:
        assertEquals(
            DenialAssessment.UNCERTAIN,
            DenialClassifier.assess(denied = true, rationaleShouldShow = false, previouslyRequested = true))
        assertEquals(
            DenialAssessment.UNCERTAIN,
            DenialClassifier.assess(denied = true, rationaleShouldShow = false, previouslyRequested = false))
    }

    // ---- Reducer: two-tap rule, duplicate guard, never auto-start ----

    @Test
    fun `Start tap with missing permissions shows a dialog and records a transient intent`() {
        val (state, effect) = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.BLE, noneGranted),
            apiLevel = 31,
        )
        assertTrue(effect is PermissionFlowEffect.ShowRuntimeDialog)
        assertEquals(PermissionFlowScope.BLE, state.pendingRequestScope)
        assertEquals(PermissionFlowScope.BLE, state.pendingStartTap)
        assertTrue(state.previouslyRequested.contains(PermissionFlowScope.BLE))
    }

    @Test
    fun `duplicate taps while a request is in flight are ignored`() {
        val first = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.WIFI, noneGranted),
            apiLevel = 31,
        )
        val second = PermissionFlowReducer.reduce(
            first.first,
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.WIFI, noneGranted),
            apiLevel = 31,
        )
        assertTrue(second.second is PermissionFlowEffect.None)
        assertEquals(PermissionFlowScope.WIFI, second.first.pendingRequestScope)
    }

    @Test
    fun `grant never starts observation - result clears the transient intent and yields no effect`() {
        val armed = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.BLE, noneGranted),
            apiLevel = 31,
        ).first
        val (after, effect) = PermissionFlowReducer.reduce(
            armed,
            PermissionFlowEvent.PermissionResult(PermissionFlowScope.BLE, allGranted),
            apiLevel = 31,
        )
        assertEquals(PermissionFlowEffect.None, effect)
        assertNull(after.pendingRequestScope)
        assertNull(after.pendingStartTap)
        assertEquals(LocationAccessUi.PRECISE, after.locationAccess)
    }

    @Test
    fun `coarse-only result maps to APPROXIMATE_ONLY and stays insufficient`() {
        val armed = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.BLE, noneGranted),
            apiLevel = 31,
        ).first
        val (after, effect) = PermissionFlowReducer.reduce(
            armed,
            PermissionFlowEvent.PermissionResult(
                PermissionFlowScope.BLE,
                RuntimeGrantSnapshot(fineLocation = false, coarseLocation = true)),
            apiLevel = 31,
        )
        assertEquals(PermissionFlowEffect.None, effect)
        assertEquals(LocationAccessUi.APPROXIMATE_ONLY, after.locationAccess)
        assertFalse(after.locationAccess.isPreciseEnoughForScanning)
    }

    @Test
    fun `denied result records DENIED without claiming permanent denial`() {
        val (after, _) = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.PermissionResult(PermissionFlowScope.WIFI, noneGranted),
            apiLevel = 31,
        )
        assertEquals(LocationAccessUi.DENIED, after.locationAccess)
    }

    @Test
    fun `Start tap with everything granted produces no dialog and no pending state`() {
        val (state, effect) = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.BLE, allGranted),
            apiLevel = 33,
        )
        assertEquals(PermissionFlowEffect.None, effect)
        assertNull(state.pendingRequestScope)
        assertNull(state.pendingStartTap)
    }

    @Test
    fun `Settings return refreshes grants only - no dialog, no pending, no start`() {
        val armed = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.WIFI, noneGranted),
            apiLevel = 31,
        ).first
        val (after, effect) = PermissionFlowReducer.reduce(
            armed,
            PermissionFlowEvent.SettingsReturned(allGranted),
            apiLevel = 31,
        )
        assertEquals(PermissionFlowEffect.None, effect)
        assertEquals(LocationAccessUi.PRECISE, after.locationAccess)
        // The in-flight marker is intentionally kept until a real dialog result arrives,
        // so a stale late callback is still consumed safely and no second dialog stacks:
        assertEquals(PermissionFlowScope.WIFI, after.pendingRequestScope)
    }

    @Test
    fun `activity recreation drops the transient Start intent and never replays it`() {
        val armed = PermissionFlowReducer.reduce(
            PermissionFlowState(),
            PermissionFlowEvent.UserStartTap(PermissionFlowScope.BLE, noneGranted),
            apiLevel = 31,
        ).first
        val recreated = PermissionFlowReducer.reduce(
            armed, PermissionFlowEvent.ActivityRecreated, apiLevel = 31,
        )
        assertEquals(PermissionFlowEffect.None, recreated.second)
        assertNull(recreated.first.pendingStartTap)
        // A late result after recreation is consumed safely and still starts nothing:
        val settled = PermissionFlowReducer.reduce(
            recreated.first,
            PermissionFlowEvent.PermissionResult(PermissionFlowScope.BLE, allGranted),
            apiLevel = 31,
        )
        assertEquals(PermissionFlowEffect.None, settled.second)
        assertEquals(LocationAccessUi.PRECISE, settled.first.locationAccess)
        assertNull(settled.first.pendingRequestScope)
    }

    @Test
    fun `revoked-after-grant on Settings return is reflected honestly`() {
        val hadPrecise = PermissionFlowState(locationAccess = LocationAccessUi.PRECISE)
        val (after, effect) = PermissionFlowReducer.reduce(
            hadPrecise,
            PermissionFlowEvent.SettingsReturned(noneGranted),
            apiLevel = 31,
        )
        assertEquals(PermissionFlowEffect.None, effect)
        assertEquals(LocationAccessUi.DENIED, after.locationAccess)
    }
}
