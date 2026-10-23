package com.example.radioobserver.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * FINAL POLISH (2026-10-23): device-free tests for the presentation-boundary
 * localization of the Wi-Fi classifier's English vendor sentinels.
 *
 * The classifier (untouched, its own tests still assert the English sentinels)
 * emits "Unknown vendor (locally administered BSSID)" / "Unknown vendor"; the UI
 * must render natural Turkish instead. A real OUI-derived vendor hint must NEVER be
 * mistaken for a sentinel, and no vendor is ever invented.
 */
class ManufacturerPresentationTest {

    @Test
    fun `classifier sentinels are recognized`() {
        assertTrue(ManufacturerPresentation.isKnownFallback("Unknown vendor"))
        assertTrue(ManufacturerPresentation.isKnownFallback("Unknown vendor (locally administered BSSID)"))
        assertTrue(ManufacturerPresentation.isLocalizedFallback("Unknown vendor"))
    }

    @Test
    fun `locally administered sentinel is distinguishable`() {
        assertTrue(ManufacturerPresentation.isLocallyAdministeredFallback("Unknown vendor (locally administered BSSID)"))
        assertFalse(ManufacturerPresentation.isLocallyAdministeredFallback("Unknown vendor"))
    }

    @Test
    fun `null and real vendor hints are NEVER treated as sentinels`() {
        assertFalse(ManufacturerPresentation.isKnownFallback(null))
        assertFalse(ManufacturerPresentation.isKnownFallback(""))
        assertFalse(ManufacturerPresentation.isKnownFallback("Texas Instruments"))
        assertFalse(ManufacturerPresentation.isLocallyAdministeredFallback("Intel"))
        assertFalse(ManufacturerPresentation.isKnownFallback("Unknown vendorry")) // no prefix sloppiness
        assertFalse(ManufacturerPresentation.isKnownFallback("unknown vendor")) // case-sensitive by contract
    }
}

class ManufacturerBleSentinelTest {

    @Test
    fun `BLE unknown-manufacturer sentinel yields the honest company id`() {
        assertEquals(0x038F, ManufacturerPresentation.bleUnknownManufacturerId("Unknown manufacturer (id 0x038F)"))
        assertEquals(0x004C, ManufacturerPresentation.bleUnknownManufacturerId("Unknown manufacturer (id 0x004C)"))
        // Case-insensitive hex, exact-match only, surrounding whitespace tolerated.
        assertEquals(911, ManufacturerPresentation.bleUnknownManufacturerId("  Unknown manufacturer (id 0x038f) "))
    }

    @Test
    fun `known manufacturer hints are NEVER treated as BLE sentinels`() {
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Apple Inc."))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Samsung Electronics Co., Ltd."))
        assertFalse(ManufacturerPresentation.isLocalizedFallback("Apple Inc."))
        assertTrue(ManufacturerPresentation.isKnownManufacturerHint("Apple Inc."))
    }

    @Test
    fun `null, empty, malformed and Wi-Fi sentinels are not BLE sentinels`() {
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId(null))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId(""))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Unknown manufacturer"))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Unknown manufacturer (id )"))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Unknown manufacturer (id 0xGGGG)"))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Unknown vendor (locally administered BSSID)"))
        assertNull(ManufacturerPresentation.bleUnknownManufacturerId("Unknown vendor"))
        // The previously fixed Wi-Fi fallbacks keep working unchanged.
        assertTrue(ManufacturerPresentation.isKnownFallback("Unknown vendor (locally administered BSSID)"))
        assertTrue(ManufacturerPresentation.isKnownFallback("Unknown vendor"))
    }

    @Test
    fun `company id is displayed honestly as uppercase 0x-prefixed code`() {
        assertEquals("0x038F", ManufacturerPresentation.formatBleCompanyId(0x038F))
        assertEquals("0x004C", ManufacturerPresentation.formatBleCompanyId(76))
        assertEquals("0xFFFF", ManufacturerPresentation.formatBleCompanyId(0xFFFF))
    }
}
