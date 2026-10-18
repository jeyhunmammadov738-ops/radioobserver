package com.example.radioobserver.core.radio.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleAdvertisementParserTest {

    /** Full valid iBeacon AD structure: [0x1A, 0xFF, 4C 00, 02 15, UUID(16), major(2), minor(2), tx(1)] */
    private fun iBeaconAdvertisement(): ByteArray {
        val uuid = ByteArray(16) { (it + 1).toByte() }
        val payload = byteArrayOf(0x02, 0x15) + uuid + byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x59)
        val manufacturer = byteArrayOf(0x4C, 0x00) + payload // 25 bytes
        val manufacturerStructure = byteArrayOf(0x1A.toByte(), 0xFF.toByte()) + manufacturer
        val name = "Watch".toByteArray(Charsets.UTF_8)
        val nameStructure = byteArrayOf((1 + name.size).toByte(), 0x09) + name
        return manufacturerStructure + nameStructure
    }

    @Test
    fun parsesValidIbeaconAdvertisement() {
        val result = BleAdvertisementParser.parseAdvertisement(iBeaconAdvertisement())

        assertEquals(0x004C, result.companyId)
        assertEquals(23, result.manufacturerPayload.size)
        assertEquals("Watch", result.localName)
        assertEquals(2, result.adStructuresParsed)
        assertTrue(result.issues.isEmpty())
        assertEquals(0x02, result.manufacturerPayload[0].toInt() and 0xFF)
        assertEquals(0x15, result.manufacturerPayload[1].toInt() and 0xFF)
    }

    @Test
    fun companyIdIsLittleEndian() {
        // Samsung 0x0075 must appear as bytes 75 00 in the AD data.
        val adv = byteArrayOf(
            0x03, 0xFF.toByte(), 0x75, 0x00,
        )
        val result = BleAdvertisementParser.parseAdvertisement(adv)
        assertEquals(0x0075, result.companyId)
    }

    @Test
    fun nullAndEmptyInputAreSafe() {
        val nullResult = BleAdvertisementParser.parseAdvertisement(null)
        assertNull(nullResult.companyId)
        assertTrue(nullResult.issues.isNotEmpty())

        val emptyResult = BleAdvertisementParser.parseAdvertisement(ByteArray(0))
        assertNull(emptyResult.companyId)
        assertTrue(emptyResult.issues.isNotEmpty())
    }

    @Test
    fun truncatedAdStructureIsReportedWithoutCrash() {
        // Declares length 0x0A (needs 9 data bytes) but only 3 remain.
        val adv = byteArrayOf(0x0A, 0xFF.toByte(), 0x4C, 0x00, 0x05)
        val result = BleAdvertisementParser.parseAdvertisement(adv)
        assertNull(result.companyId)
        assertEquals(0, result.adStructuresParsed)
        assertTrue(result.issues.any { it.contains("truncated") })
    }

    @Test
    fun zeroLengthPaddingTerminatesParsing() {
        val adv = iBeaconAdvertisement() + byteArrayOf(0x00, 0x03, 0xAB.toByte())
        val result = BleAdvertisementParser.parseAdvertisement(adv)
        assertEquals(0x004C, result.companyId)
        assertEquals(2, result.adStructuresParsed)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun manufacturerStructureWithoutCompanyIdIsReported() {
        // length 1: only the type byte, no data at all.
        val adv = byteArrayOf(0x01, 0xFF.toByte())
        val result = BleAdvertisementParser.parseAdvertisement(adv)
        assertNull(result.companyId)
        assertTrue(result.issues.any { it.contains("too short") })
        assertEquals(1, result.adStructuresParsed)
    }

    @Test
    fun duplicateManufacturerStructuresKeepTheFirst() {
        val adv = byteArrayOf(
            0x03, 0xFF.toByte(), 0x4C, 0x00,
            0x03, 0xFF.toByte(), 0x75, 0x00,
        )
        val result = BleAdvertisementParser.parseAdvertisement(adv)
        assertEquals(0x004C, result.companyId)
        assertTrue(result.issues.any { it.contains("multiple manufacturer-specific") })
    }

    @Test
    fun androidExtractedManufacturerDataDoesNotReparseCompanyId() {
        val payload = byteArrayOf(0x02, 0x15) + ByteArray(21)
        val result = BleAdvertisementParser.parse(
            BleParseInput.ManufacturerData(companyId = 0x004C, payload = payload),
        )
        assertEquals(0x004C, result.companyId)
        assertEquals(payload.size, result.manufacturerPayload.size)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun oversizedAndroidPayloadIsFlagged() {
        val payload = ByteArray(200)
        val result = BleAdvertisementParser.parse(
            BleParseInput.ManufacturerData(companyId = 0x004C, payload = payload),
        )
        assertEquals(0x004C, result.companyId)
        assertTrue(result.issues.any { it.contains("exceeds sanity cap") })
    }
}
