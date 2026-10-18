package com.example.radioobserver.core.radio.parser

import com.example.radioobserver.core.radio.model.ObservationConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleDeviceClassifierTest {

    private fun iBeaconPayload(): ByteArray =
        byteArrayOf(0x02, 0x15) + ByteArray(16) { (it + 1).toByte() } +
            byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x59)

    private fun findMyPayload(): ByteArray =
        byteArrayOf(0x05, 0x12, 0x00) + ByteArray(22) { (it + 1).toByte() } + byteArrayOf(0xAA.toByte(), 0xBB.toByte())

    @Test
    fun appleIbeaconWithExactLayoutIsValidated() {
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x004C, iBeaconPayload())

        assertEquals("Apple Inc.", manufacturer?.companyName)
        assertEquals("iBeacon (Apple layout)", deviceType?.label)
        assertEquals(ObservationConfidence.VALIDATED_FORMAT, deviceType?.confidence)
        assertTrue(deviceType?.evidence?.any { it.contains("UUID") } == true)
        assertTrue(manufacturer?.evidence?.any { it.contains("does NOT prove") } == true)
    }

    @Test
    fun appleIbeaconPrefixWithWrongLengthIsOnlyPossible() {
        val payload = byteArrayOf(0x02, 0x15) + ByteArray(10)
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x004C, payload)

        assertEquals("Apple Inc.", manufacturer?.companyName)
        assertEquals(ObservationConfidence.POSSIBLE, deviceType?.confidence)
        assertTrue(deviceType?.evidence?.any { it.contains("does NOT match") } == true)
    }

    @Test
    fun findMySeparatedLayoutIsCandidateNotConfirmed() {
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x004C, findMyPayload())

        assertEquals("Apple Inc.", manufacturer?.companyName)
        assertTrue(deviceType?.label?.contains("Find My") == true)
        assertEquals(ObservationConfidence.CANDIDATE, deviceType?.confidence)
        assertTrue(deviceType?.evidence?.any { it.contains("does NOT prove") } == true)
    }

    @Test
    fun findMyPrefixWithWrongLengthIsOnlyPossible() {
        val payload = byteArrayOf(0x05, 0x12) + ByteArray(5)
        val (_, deviceType) = BleDeviceClassifier.classify(0x004C, payload)

        assertEquals(ObservationConfidence.POSSIBLE, deviceType?.confidence)
        assertTrue(deviceType?.label?.contains("length mismatch") == true)
    }

    @Test
    fun bogusAppleLookalikeGetsNoDeviceTypeClaim() {
        // Apple company id, but an unrecognized prefix (06 12): manufacturer only.
        val payload = byteArrayOf(0x06, 0x12) + ByteArray(20)
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x004C, payload)

        assertEquals("Apple Inc.", manufacturer?.companyName)
        assertNull(deviceType)
        assertTrue(manufacturer?.evidence?.any { it.contains("does NOT prove an AirTag") } == true)
    }

    @Test
    fun samsungNeverProducesSmartTagClaim() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x0075, payload)

        assertEquals("Samsung Electronics Co., Ltd.", manufacturer?.companyName)
        assertNull(deviceType) // no validated public SmartTag format -> no claim
        assertTrue(manufacturer?.evidence?.any { it.contains("no device-type claim") } == true)
    }

    @Test
    fun unknownVendorIdFallsBackToUnknown() {
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x0157, byteArrayOf(0x01))

        assertTrue(manufacturer?.companyName?.startsWith("Unknown manufacturer") == true)
        assertEquals(0x0157, manufacturer?.companyId)
        assertNull(deviceType)
    }

    @Test
    fun emptyApplePayloadMeansManufacturerOnly() {
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(0x004C, ByteArray(0))
        assertEquals("Apple Inc.", manufacturer?.companyName)
        assertNull(deviceType)
    }

    @Test
    fun nullCompanyIdMeansNothing() {
        val (manufacturer, deviceType) = BleDeviceClassifier.classify(null, ByteArray(5))
        assertNull(manufacturer)
        assertNull(deviceType)
    }
}
