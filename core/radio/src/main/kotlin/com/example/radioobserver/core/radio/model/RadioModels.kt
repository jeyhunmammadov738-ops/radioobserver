package com.example.radioobserver.core.radio.model

/** Radio technology of an observation. Phase 1 models observations only; no scanning exists yet. */
enum class RadioType { BLE, WIFI }

/**
 * Confidence levels for any candidate claim made about an observed device.
 * NOTHING in Phase 1 asserts physical identity, product identity, or tracker status.
 */
enum class ObservationConfidence {
    /** No usable format evidence. */
    UNKNOWN,

    /** Partial format evidence only (e.g. right vendor prefix, wrong payload length). */
    POSSIBLE,

    /**
     * Byte layout matches a publicly documented format. Still a formatting observation:
     * identity of the physical device is NOT proven.
     */
    CANDIDATE,

    /** Fully specified, widely published format (e.g. the iBeacon layout) matched byte-for-byte. */
    VALIDATED_FORMAT,
}

/** Bluetooth SIG assigned company identifier observed in manufacturer-specific data. */
data class ManufacturerInfo(
    val companyId: Int,
    val companyName: String,
    val evidence: List<String> = emptyList(),
)

/**
 * A conservative claim about what a device MIGHT be. Always paired with human-readable
 * evidence so callers can show WHY a label was applied.
 */
data class DeviceTypeCandidate(
    val label: String,
    val confidence: ObservationConfidence,
    val evidence: List<String>,
)

/**
 * Canonical observed-device model for both BLE and Wi-Fi observations.
 *
 * Identity caveats (by design, not just docs):
 * - BLE addresses and Wi-Fi BSSIDs may be randomized; [observationKey] is a dedup
 *   convenience, NOT proof of stable physical identity.
 * - [rssiDbm] is a radio signal level; it cannot prove distance or proximity.
 */
data class ObservedRadioDevice(
    /** Stable dedup key within one session (e.g. "ble:aa:bb:cc:dd:ee:ff", "wifi:aa:bb:cc:dd:ee:ff"). */
    val observationKey: String,
    val radioType: RadioType,
    /** Wall-clock epoch milliseconds at observation time (documented persistence unit). */
    val observedAtEpochMs: Long,
    /** Human-facing name: advertised local name (BLE) or SSID (Wi-Fi). May be absent. */
    val displayName: String? = null,
    /** Raw BLE address or BSSID as reported, if available. May be randomized. */
    val address: String? = null,
    val manufacturerCandidate: ManufacturerInfo? = null,
    val deviceTypeCandidate: DeviceTypeCandidate? = null,
    /** RSSI in dBm, or null when unavailable/invalid. Not a distance measurement. */
    val rssiDbm: Int? = null,
    /** True when the address carries the locally-administered bit (likely randomized). */
    val locallyAdministeredAddress: Boolean = false,
    /** Free-form, source-specific extras (e.g. "channel=6"). */
    val sourceFields: Map<String, String> = emptyMap(),
    /** Non-fatal problems found while parsing this observation. */
    val parseIssues: List<String> = emptyList(),
)
