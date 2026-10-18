package com.example.radioobserver.core.radio.factory

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleObservationFactoryTest {

    private fun iBeaconAdvertisement(): ByteArray {
        val payload = byteArrayOf(0x02, 0x15) + ByteArray(16) { (it + 1).toByte() } +
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x59)
        val manufacturer = byteArrayOf(0x4C, 0x00) + payload
        return byteArrayOf(0x1A.toByte(), 0xFF.toByte()) + manufacturer
    }

    @Test
    fun rawAdvertisementBecomesCanonicalDevice() {
        val device = BleObservationFactory.fromRawAdvertisement(
            bytes = iBeaconAdvertisement(),
            address = "AA:BB:CC:DD:EE:FF",
            rssiDbm = -63,
            observedAtEpochMs = 1_000L,
        )

        assertEquals(RadioType.BLE, device.radioType)
        assertEquals("ble:aa:bb:cc:dd:ee:ff", device.observationKey)
        assertEquals("Apple Inc.", device.manufacturerCandidate?.companyName)
        assertEquals(ObservationConfidence.VALIDATED_FORMAT, device.deviceTypeCandidate?.confidence)
        assertEquals(-63, device.rssiDbm)
        assertEquals(1_000L, device.observedAtEpochMs)
    }

    @Test
    fun randomizedBleAddressIsFlagged() {
        val device = BleObservationFactory.fromRawAdvertisement(
            bytes = iBeaconAdvertisement(),
            address = "62:BB:CC:DD:EE:FF", // 0x62 has bit 0x02 set
            rssiDbm = null,
            observedAtEpochMs = 2_000L,
        )
        assertTrue(device.locallyAdministeredAddress)
        assertEquals("ble:62:bb:cc:dd:ee:ff", device.observationKey)
    }

    @Test
    fun missingAddressProducesConservativeSharedKey() {
        val device = BleObservationFactory.fromRawAdvertisement(
            bytes = iBeaconAdvertisement(),
            address = null,
            rssiDbm = -70,
            observedAtEpochMs = 3_000L,
        )
        assertEquals("ble:no-address", device.observationKey)
        assertTrue(device.parseIssues.any { it.contains("no BLE address") })
    }

    @Test
    fun androidManufacturerPathBuildsDeviceWithoutReparse() {
        val payload = byteArrayOf(0x05, 0x12, 0x00) + ByteArray(22) + byteArrayOf(0x01, 0x02)
        val device = BleObservationFactory.fromAndroidManufacturerData(
            companyId = 0x004C,
            payload = payload,
            address = "11:22:33:44:55:66",
            rssiDbm = -80,
            observedAtEpochMs = 4_000L,
        )

        assertEquals(ObservationConfidence.CANDIDATE, device.deviceTypeCandidate?.confidence)
        assertEquals("ble:11:22:33:44:55:66", device.observationKey)
    }

    @Test
    fun emptyInputYieldsUnknownDeviceWithoutCrash() {
        val device = BleObservationFactory.fromRawAdvertisement(
            bytes = null,
            address = "11:22:33:44:55:66",
            rssiDbm = null,
            observedAtEpochMs = 5_000L,
        )
        assertEquals(null, device.manufacturerCandidate)
        assertEquals(null, device.deviceTypeCandidate)
        assertTrue(device.parseIssues.isNotEmpty())
    }
}
