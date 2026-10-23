package com.example.radioobserver.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
