package com.example.radioobserver.core.radio.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiObservationClassifierTest {

    @Test
    fun infrastructureBssidProducesCanonicalObservation() {
        val device = WifiObservationClassifier.classify(
            WifiObservationInput(
                observedAtEpochMs = 1_000L,
                ssid = "HomeNet",
                bssid = "AC:BB:CC:DD:EE:FF",
                rssiDbm = -55,
            ),
        )

        assertEquals("wifi:ac:bb:cc:dd:ee:ff", device.observationKey)
        assertEquals("ac:bb:cc:dd:ee:ff", device.address)
        assertEquals("HomeNet", device.displayName)
        assertEquals(-55, device.rssiDbm)
        assertEquals("false", device.sourceFields["scanInitiatedByApp"])
        assertEquals(false, device.locallyAdministeredAddress) // 0xAC: locally administered bit clear
        assertEquals("Unknown vendor", device.manufacturerCandidate?.companyName)
        assertEquals("infrastructure_access_point", device.sourceFields["observationKind"])
        assertTrue(device.parseIssues.isEmpty())
    }

    @Test
    fun locallyAdministeredBssidIsFlaggedRandomizedAndVendorStaysUnknown() {
        val device = WifiObservationClassifier.classify(
            WifiObservationInput(
                observedAtEpochMs = 2_000L,
                ssid = "Mystery",
                bssid = "02:11:22:33:44:55",
            ),
            vendorOuiHints = mapOf("02:11:22" to "ShouldNeverMatchRandomized"),
        )

        assertTrue(device.locallyAdministeredAddress)
        assertTrue(device.manufacturerCandidate?.companyName?.contains("locally administered") == true)
        // OUI hints must NOT be applied to a randomized BSSID.
        assertFalse(device.manufacturerCandidate?.companyName == "ShouldNeverMatchRandomized")
    }

    @Test
    fun knownOuiHintIsAppliedOnlyWhenSupplied() {
        val device = WifiObservationClassifier.classify(
            WifiObservationInput(observedAtEpochMs = 3_000L, bssid = "AC:BB:CC:11:22:33"),
            vendorOuiHints = mapOf("ac:bb:cc" to "AcmeCorp"),
        )
        assertEquals("AcmeCorp", device.manufacturerCandidate?.companyName)

        val withoutHint = WifiObservationClassifier.classify(
            WifiObservationInput(observedAtEpochMs = 3_001L, bssid = "AC:BB:CC:11:22:33"),
        )
        assertEquals("Unknown vendor", withoutHint.manufacturerCandidate?.companyName)
    }

    @Test
    fun ssidTextNeverProvesVendorOrModel() {
        // A suggestive SSID must not produce any vendor inference.
        val device = WifiObservationClassifier.classify(
            WifiObservationInput(observedAtEpochMs = 4_000L, ssid = "Apple TV Guest"),
        )
        assertEquals("wifi:ssid:Apple TV Guest", device.observationKey)
        assertEquals("Apple TV Guest", device.displayName)
        assertNull(device.manufacturerCandidate) // no BSSID -> no vendor claim at all
        assertNull(device.address)
    }

    @Test
    fun malformedBssidFallsBackToSsidKey() {
        val device = WifiObservationClassifier.classify(
            WifiObservationInput(observedAtEpochMs = 5_000L, ssid = "Cafe", bssid = "zz:99:xx"),
        )
        assertNull(device.address)
        assertEquals("wifi:ssid:Cafe", device.observationKey)
        assertTrue(device.parseIssues.any { it.contains("malformed") })
    }

    @Test
    fun noBssidAndNoSsidIsExplicitlyMarked() {
        val device = WifiObservationClassifier.classify(
            WifiObservationInput(observedAtEpochMs = 6_000L),
        )
        assertTrue(device.observationKey.startsWith("wifi:unknown:"))
        assertTrue(device.parseIssues.any { it.contains("no stable identity key") })
    }

    @Test
    fun bssidNormalizationIsLenientOnSeparatorsAndCase() {
        assertEquals("aa:bb:cc:dd:ee:ff", WifiObservationClassifier.normalizeBssid("aabbcc-ddeeff"))
        assertEquals("aa:bb:cc:dd:ee:ff", WifiObservationClassifier.normalizeBssid("AABBCC.DD.EE.FF"))
        assertEquals("aa:bb:cc:dd:ee:ff", WifiObservationClassifier.normalizeBssid(" aa:bb:cc dd:ee:ff "))
        assertNull(WifiObservationClassifier.normalizeBssid("aa:bb"))
        assertNull(WifiObservationClassifier.normalizeBssid(null))
        assertNull(WifiObservationClassifier.normalizeBssid("gg:bb:cc:dd:ee:ff"))
    }

    @Test
    fun locallyAdministeredBitDetection() {
        assertTrue(WifiObservationClassifier.isLocallyAdministered("02:00:00:00:00:00"))
        assertTrue(WifiObservationClassifier.isLocallyAdministered("06:00:00:00:00:00"))
        assertFalse(WifiObservationClassifier.isLocallyAdministered("00:00:00:00:00:00"))
        assertFalse(WifiObservationClassifier.isLocallyAdministered("04:00:00:00:00:00"))
    }
}
