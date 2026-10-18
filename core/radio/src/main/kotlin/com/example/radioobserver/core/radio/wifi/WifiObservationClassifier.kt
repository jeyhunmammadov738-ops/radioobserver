package com.example.radioobserver.core.radio.wifi

import com.example.radioobserver.core.radio.model.ManufacturerInfo
import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType

data class WifiObservationInput(
    val observedAtEpochMs: Long,
    val ssid: String? = null,
    val bssid: String? = null,
    val rssiDbm: Int? = null,
)

/**
 * Conservative Wi-Fi ACCESS-POINT observation classifier.
 *
 * Phase 1 boundaries (enforced by design, not just documentation):
 * - Infrastructure access points only. Client devices are NOT visible to Android's
 *   standard Wi-Fi APIs, and no monitor-mode packet capture is used or claimed.
 * - SSID text NEVER proves a device make/model; it is treated as a display name only.
 * - A BSSID prefix may be locally administered (randomized); vendor stays Unknown then.
 * - Phase 1 does NOT initiate Wi-Fi scanning; sourceFields record that explicitly.
 * - Unknown remains unknown: vendor inference only happens via caller-supplied OUI hints.
 */
object WifiObservationClassifier {

    fun classify(
        input: WifiObservationInput,
        /** Optional OUI prefix hints, e.g. "aa:bb:cc" -> "ExampleCorp". Hint accuracy is the caller's responsibility. */
        vendorOuiHints: Map<String, String> = emptyMap(),
    ): ObservedRadioDevice {
        val issues = mutableListOf<String>()
        val normalizedBssid = normalizeBssid(input.bssid)
        var locallyAdministered = false
        var manufacturer: ManufacturerInfo? = null

        if (normalizedBssid == null) {
            if (!input.bssid.isNullOrBlank()) {
                issues += "BSSID '${input.bssid}' is malformed; treating as absent"
            }
        } else {
            locallyAdministered = isLocallyAdministered(normalizedBssid)
            val oui = normalizedBssid.take(8) // "aa:bb:cc"
            manufacturer = when {
                locallyAdministered -> ManufacturerInfo(
                    companyId = -1,
                    companyName = "Unknown vendor (locally administered BSSID)",
                    evidence = listOf(
                        "BSSID $normalizedBssid has the locally-administered bit set",
                        "Locally administered BSSIDs are frequently randomized; no vendor inference is made",
                    ),
                )
                vendorOuiHints[oui] != null -> ManufacturerInfo(
                    companyId = -1,
                    companyName = vendorOuiHints.getValue(oui),
                    evidence = listOf(
                        "BSSID OUI prefix $oui matched a caller-supplied hint",
                        "OUI hints are supplied by the caller; the classifier does not validate them",
                    ),
                )
                else -> ManufacturerInfo(
                    companyId = -1,
                    companyName = "Unknown vendor",
                    evidence = listOf(
                        "BSSID OUI prefix $oui has no entry in the supplied vendor hint table; unknown remains unknown",
                    ),
                )
            }
        }

        val trimmedSsid = input.ssid?.trim()?.takeIf { it.isNotEmpty() }
        val key = when {
            normalizedBssid != null -> "wifi:$normalizedBssid"
            trimmedSsid != null -> "wifi:ssid:$trimmedSsid"
            else -> {
                issues += "no BSSID or SSID available; observation has no stable identity key"
                "wifi:unknown:${input.observedAtEpochMs}"
            }
        }

        return ObservedRadioDevice(
            observationKey = key,
            radioType = RadioType.WIFI,
            observedAtEpochMs = input.observedAtEpochMs,
            displayName = trimmedSsid,
            address = normalizedBssid,
            manufacturerCandidate = manufacturer,
            rssiDbm = input.rssiDbm,
            locallyAdministeredAddress = locallyAdministered,
            sourceFields = mapOf(
                "observationKind" to "infrastructure_access_point",
                "scanInitiatedByApp" to "false",
            ),
            parseIssues = issues,
        )
    }

    /** Lowercases and canonicalizes a BSSID to "aa:bb:cc:dd:ee:ff"; null when absent or malformed. */
    fun normalizeBssid(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim().replace(Regex("[.:\\s-]"), "").lowercase()
        if (cleaned.length != 12 || !cleaned.all { it.isDigit() || it in 'a'..'f' }) return null
        return cleaned.chunked(2).joinToString(":")
    }

    /** Bit 1 (0x02) of the first octet set => locally administered. */
    fun isLocallyAdministered(normalizedBssid: String): Boolean {
        val firstOctet = normalizedBssid.substringBefore(':').toInt(16)
        return (firstOctet and 0x02) != 0
    }
}
