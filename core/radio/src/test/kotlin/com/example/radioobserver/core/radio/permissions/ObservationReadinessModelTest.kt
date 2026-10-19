package com.example.radioobserver.core.radio.permissions

import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.5 acceptance tests: table-driven readiness decisions across API buckets,
 * radios, permission outcomes, and hardware/toggle states. Pure JVM; no Android.
 */
class ObservationReadinessModelTest {

    private fun snapshot(
        radio: RadioType,
        bucket: AndroidApiBucket,
        hardwarePresent: Boolean = true,
        radioEnabled: Boolean = true,
        locationOn: Boolean = true,
        permissions: Map<PermissionId, PermissionState> = emptyMap(),
    ) = CapabilitySnapshot(
        apiBucket = bucket,
        radio = radio,
        hardware = RadioHardwareStatus(hardwarePresent, radioEnabled, locationOn),
        permissions = permissions,
    )

    private fun allGranted(radio: RadioType, bucket: AndroidApiBucket): Map<PermissionId, PermissionState> =
        ObservationReadinessModel.requiredPermissions(radio, bucket)
            .associateWith { PermissionState.GRANTED }

    // ---- required-permission table (matrix 2.1 encoded) ----

    @Test
    fun `required permissions per radio and bucket match the approved matrix`() {
        val table = mapOf(
            (RadioType.BLE to AndroidApiBucket.API_29_30) to setOf(
                PermissionId.BLUETOOTH, PermissionId.BLUETOOTH_ADMIN, PermissionId.ACCESS_FINE_LOCATION),
            (RadioType.BLE to AndroidApiBucket.API_31_32) to setOf(
                PermissionId.BLUETOOTH_SCAN, PermissionId.ACCESS_FINE_LOCATION),
            (RadioType.BLE to AndroidApiBucket.API_33_PLUS) to setOf(
                PermissionId.BLUETOOTH_SCAN, PermissionId.ACCESS_FINE_LOCATION),
            (RadioType.WIFI to AndroidApiBucket.API_29_30) to setOf(
                PermissionId.ACCESS_WIFI_STATE, PermissionId.ACCESS_FINE_LOCATION),
            (RadioType.WIFI to AndroidApiBucket.API_31_32) to setOf(
                PermissionId.ACCESS_WIFI_STATE, PermissionId.ACCESS_FINE_LOCATION),
            (RadioType.WIFI to AndroidApiBucket.API_33_PLUS) to setOf(
                PermissionId.ACCESS_WIFI_STATE, PermissionId.ACCESS_FINE_LOCATION),
        )
        for ((key, expected) in table) {
            assertEquals("mismatch for $key", expected, ObservationReadinessModel.requiredPermissions(key.first, key.second))
        }
    }

    // ---- READY paths across every bucket and radio ----

    @Test
    fun `all-granted states are READY across every bucket and radio`() {
        for (bucket in AndroidApiBucket.values()) {
            for (radio in RadioType.values()) {
                val readiness =
                    ObservationReadinessModel.evaluate(
                        snapshot(radio, bucket, permissions = allGranted(radio, bucket)))
                assertTrue("expected READY for $radio/$bucket, got $readiness", readiness is ObservationReadiness.Ready)
                val ready = readiness as ObservationReadiness.Ready
                assertTrue(ready.disclosures.contains(Disclosure.NO_BACKGROUND_OBSERVATION))
            }
        }
    }

    @Test
    fun `ready ble disclosures reveal proximity promise without background claim`() {
        val ready = ObservationReadinessModel.evaluate(
            snapshot(RadioType.BLE, AndroidApiBucket.API_33_PLUS, permissions = allGranted(RadioType.BLE, AndroidApiBucket.API_33_PLUS))
        ) as ObservationReadiness.Ready
        assertEquals(
            setOf(Disclosure.BLE_RESULTS_MAY_REVEAL_PROXIMITY, Disclosure.NO_BACKGROUND_OBSERVATION),
            ready.disclosures,
        )
    }

    @Test
    fun `ready wifi disclosures include location and stale-results warnings`() {
        val ready = ObservationReadinessModel.evaluate(
            snapshot(RadioType.WIFI, AndroidApiBucket.API_29_30, permissions = allGranted(RadioType.WIFI, AndroidApiBucket.API_29_30))
        ) as ObservationReadiness.Ready
        assertTrue(ready.disclosures.contains(Disclosure.WIFI_RESULTS_MAY_REVEAL_LOCATION))
        assertTrue(ready.disclosures.contains(Disclosure.WIFI_RESULTS_MAY_BE_CACHED_STALE))
    }

    // ---- permission denial states, table-driven ----

    @Test
    fun `denied vs permanently denied produce distinct blockers`() {
        val deniedCase = ObservationReadinessModel.evaluate(
            snapshot(RadioType.BLE, AndroidApiBucket.API_33_PLUS, permissions = mapOf(
                PermissionId.BLUETOOTH_SCAN to PermissionState.DENIED,
                PermissionId.ACCESS_FINE_LOCATION to PermissionState.GRANTED)))
        val permanentCase = ObservationReadinessModel.evaluate(
            snapshot(RadioType.BLE, AndroidApiBucket.API_33_PLUS, permissions = mapOf(
                PermissionId.BLUETOOTH_SCAN to PermissionState.PERMANENTLY_DENIED,
                PermissionId.ACCESS_FINE_LOCATION to PermissionState.GRANTED)))

        val deniedBlocker = (deniedCase as ObservationReadiness.Blocked).blockers.single() as ReadinessBlocker.PermissionMissing
        val permanentBlocker = (permanentCase as ObservationReadiness.Blocked).blockers.single() as ReadinessBlocker.PermissionMissing
        assertEquals(PermissionId.BLUETOOTH_SCAN, deniedBlocker.permission)
        assertFalse(deniedBlocker.permanent)
        assertTrue(permanentBlocker.permanent)
    }

    @Test
    fun `missing map entry is treated as denied not granted`() {
        val readiness = ObservationReadinessModel.evaluate(
            snapshot(RadioType.WIFI, AndroidApiBucket.API_31_32, permissions = emptyMap()))
        val blockers = (readiness as ObservationReadiness.Blocked).blockers
        assertEquals(2, blockers.size) // ACCESS_WIFI_STATE + ACCESS_FINE_LOCATION both missing
        assertTrue(blockers.all { it is ReadinessBlocker.PermissionMissing })
    }

    @Test
    fun `bucket 29-30 ble checks legacy permissions not BLUETOOTH_SCAN`() {
        val legacyGranted = mapOf(
            PermissionId.BLUETOOTH to PermissionState.GRANTED,
            PermissionId.BLUETOOTH_ADMIN to PermissionState.GRANTED,
            PermissionId.ACCESS_FINE_LOCATION to PermissionState.GRANTED)
        val readiness = ObservationReadinessModel.evaluate(
            snapshot(RadioType.BLE, AndroidApiBucket.API_29_30, permissions = legacyGranted))
        assertTrue("legacy granted set must be READY", readiness is ObservationReadiness.Ready)
    }

    @Test
    fun `bucket 31+ does not require legacy permissions`() {
        val modernGranted = allGranted(RadioType.BLE, AndroidApiBucket.API_31_32)
        assertTrue(!modernGranted.containsKey(PermissionId.BLUETOOTH_ADMIN))
        val readiness = ObservationReadinessModel.evaluate(
            snapshot(RadioType.BLE, AndroidApiBucket.API_31_32, permissions = modernGranted))
        assertTrue(readiness is ObservationReadiness.Ready)
    }

    // ---- hardware and toggle states ----

    @Test
    fun `hardware absence blocks readiness`() {
        val readiness = ObservationReadinessModel.evaluate(
            snapshot(RadioType.BLE, AndroidApiBucket.API_31_32, hardwarePresent = false, radioEnabled = false,
                permissions = allGranted(RadioType.BLE, AndroidApiBucket.API_31_32)))
        val blockers = (readiness as ObservationReadiness.Blocked).blockers
        assertTrue(blockers.any { it is ReadinessBlocker.HardwareUnavailable })
    }

    @Test
    fun `radio off blocks even with all permissions granted`() {
        val readiness = ObservationReadinessModel.evaluate(
            snapshot(RadioType.WIFI, AndroidApiBucket.API_33_PLUS, radioEnabled = false,
                permissions = allGranted(RadioType.WIFI, AndroidApiBucket.API_33_PLUS)))
        val blockers = (readiness as ObservationReadiness.Blocked).blockers
        assertEquals(1, blockers.size)
        assertTrue(blockers.single() is ReadinessBlocker.RadioDisabled)
    }

    @Test
    fun `location services off is a conservative blocker for both radios`() {
        for (radio in RadioType.values()) {
            val bucket = AndroidApiBucket.API_33_PLUS
            val readiness = ObservationReadinessModel.evaluate(
                snapshot(radio, bucket, locationOn = false, permissions = allGranted(radio, bucket)))
            val blockers = (readiness as ObservationReadiness.Blocked).blockers
            assertEquals("$radio should be blocked by location-off", 1, blockers.size)
            assertTrue(blockers.single() is ReadinessBlocker.LocationServicesDisabled)
        }
    }

    // ---- combined failures accumulate ----

    @Test
    fun `multiple blockers accumulate in one decision`() {
        val readiness = ObservationReadinessModel.evaluate(
            snapshot(RadioType.WIFI, AndroidApiBucket.API_31_32,
                radioEnabled = false, locationOn = false,
                permissions = mapOf(
                    PermissionId.ACCESS_WIFI_STATE to PermissionState.GRANTED,
                    PermissionId.ACCESS_FINE_LOCATION to PermissionState.DENIED)))
        val blockers = (readiness as ObservationReadiness.Blocked).blockers
        assertEquals(3, blockers.size)
        assertTrue(blockers.any { it is ReadinessBlocker.PermissionMissing && !it.permanent })
        assertTrue(blockers.any { it is ReadinessBlocker.RadioDisabled })
        assertTrue(blockers.any { it is ReadinessBlocker.LocationServicesDisabled })
    }

    @Test
    fun `inconsistent snapshot with enabled radio but no hardware is rejected`() {
        val ex = runCatching {
            snapshot(RadioType.BLE, AndroidApiBucket.API_29_30, hardwarePresent = false, radioEnabled = true)
        }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException", ex is IllegalArgumentException)
    }

    // ---- bucket mapping ----

    @Test
    fun `sdk level maps to the documented buckets`() {
        assertEquals(AndroidApiBucket.API_29_30, AndroidApiBucket.fromSdkLevel(29))
        assertEquals(AndroidApiBucket.API_29_30, AndroidApiBucket.fromSdkLevel(30))
        assertEquals(AndroidApiBucket.API_31_32, AndroidApiBucket.fromSdkLevel(31))
        assertEquals(AndroidApiBucket.API_31_32, AndroidApiBucket.fromSdkLevel(32))
        assertEquals(AndroidApiBucket.API_33_PLUS, AndroidApiBucket.fromSdkLevel(33))
        assertEquals(AndroidApiBucket.API_33_PLUS, AndroidApiBucket.fromSdkLevel(35))
    }
}
