package com.example.radioobserver.core.radio.parser

import com.example.radioobserver.core.radio.model.DeviceTypeCandidate
import com.example.radioobserver.core.radio.model.ManufacturerInfo
import com.example.radioobserver.core.radio.model.ObservationConfidence

/**
 * Conservative BLE classification from manufacturer-specific data.
 *
 * Binding rules:
 * - A company id proves ecosystem membership only. It is NEVER proof of a product
 *   (AirTag, SmartTag, ...) and NEVER proof that someone is being followed.
 * - 0x004C = Apple Inc., 0x0075 = Samsung Electronics (Bluetooth SIG assigned numbers).
 * - Samsung: no validated public SmartTag advertisement format is known, so NO
 *   device-type candidate is ever produced for 0x0075 in Phase 1.
 */
object BleDeviceClassifier {

    const val COMPANY_ID_APPLE = 0x004C
    const val COMPANY_ID_SAMSUNG = 0x0075

    /** iBeacon payload AFTER the 2 company-id bytes: [02, 15, UUID(16), major(2), minor(2), tx(1)]. */
    const val IBEACON_PAYLOAD_SIZE = 23

    /** Find My separated payload AFTER the 2 company-id bytes: [05, 12, status(1), key(22), crc(2)]. */
    const val FIND_MY_PAYLOAD_SIZE = 27

    /**
     * Maps a parsed manufacturer record to conservative manufacturer + device-type candidates.
     */
    fun classify(companyId: Int?, payload: ByteArray): Pair<ManufacturerInfo?, DeviceTypeCandidate?> {
        if (companyId == null) return null to null
        return when (companyId) {
            COMPANY_ID_APPLE -> appleManufacturer() to classifyApplePayload(payload)
            COMPANY_ID_SAMSUNG -> samsungManufacturer() to null
            else -> unknownManufacturer(companyId) to null
        }
    }

    private fun appleManufacturer() = ManufacturerInfo(
        companyId = COMPANY_ID_APPLE,
        companyName = "Apple Inc.",
        evidence = listOf(
            "Bluetooth SIG company id 0x004C (little-endian bytes 4C 00)",
            "Company id indicates Apple ecosystem membership only; it does NOT prove an AirTag or any tracker",
        ),
    )

    private fun samsungManufacturer() = ManufacturerInfo(
        companyId = COMPANY_ID_SAMSUNG,
        companyName = "Samsung Electronics Co., Ltd.",
        evidence = listOf(
            "Bluetooth SIG company id 0x0075 (little-endian bytes 75 00)",
            "Company id indicates Samsung ecosystem membership only; no validated public SmartTag " +
                "advertisement format is known, so no device-type claim is made",
        ),
    )

    private fun unknownManufacturer(companyId: Int) = ManufacturerInfo(
        companyId = companyId,
        companyName = "Unknown manufacturer (id 0x%04X)".format(companyId),
        evidence = listOf("Company id 0x%04X is not in the Phase 1 lookup table".format(companyId)),
    )

    /**
     * Classifies an Apple payload (AFTER the 2 company-id bytes) into, at most, a candidate:
     *
     *  - iBeacon: [02, 15, UUID(16), major(2), minor(2), txPower(1)] — fully published format.
     *    Exact length -> VALIDATED_FORMAT; prefix with wrong length -> POSSIBLE only.
     *
     *  - Find My separated layout (publicly documented in OpenHaystack-style tooling):
     *    [05, 12, status(1), key(22), crc(2)].
     *    Exact length -> CANDIDATE; wrong length -> POSSIBLE.
     *    A layout match is a formatting observation; it does NOT prove an AirTag, that a
     *    device is lost, or that anyone is being followed, and compatibility with all
     *    modern Find My versions is NOT claimed.
     */
    fun classifyApplePayload(payload: ByteArray): DeviceTypeCandidate? {
        if (payload.size < 2) return null
        val first = payload[0].toInt() and 0xFF
        val second = payload[1].toInt() and 0xFF

        if (first == 0x02 && second == 0x15) {
            return if (payload.size == IBEACON_PAYLOAD_SIZE) {
                val uuidHex = payload.copyOfRange(2, 18).joinToString("") { "%02x".format(it) }
                DeviceTypeCandidate(
                    label = "iBeacon (Apple layout)",
                    confidence = ObservationConfidence.VALIDATED_FORMAT,
                    evidence = listOf(
                        "prefix 0x02 0x15 matches the published iBeacon layout",
                        "payload length ${payload.size} matches UUID(16) + major(2) + minor(2) + txPower(1)",
                        "beacon UUID bytes: $uuidHex",
                    ),
                )
            } else {
                DeviceTypeCandidate(
                    label = "Apple iBeacon-like prefix (length mismatch)",
                    confidence = ObservationConfidence.POSSIBLE,
                    evidence = listOf(
                        "prefix 0x02 0x15 matches the published iBeacon layout",
                        "payload length ${payload.size} does NOT match the required $IBEACON_PAYLOAD_SIZE bytes; " +
                            "not treated as an iBeacon",
                    ),
                )
            }
        }

        if (first == 0x05 && second == 0x12) {
            return if (payload.size == FIND_MY_PAYLOAD_SIZE) {
                DeviceTypeCandidate(
                    label = "Find My separated-style advertisement (Apple layout)",
                    confidence = ObservationConfidence.CANDIDATE,
                    evidence = listOf(
                        "prefix 0x05 0x12 matches the publicly documented Find My separated layout",
                        "payload length ${payload.size} matches status(1) + key(22) + crc(2)",
                        "layout match is a formatting observation only; it does NOT prove this is an " +
                            "AirTag, that it is lost, or that it follows anyone",
                    ),
                )
            } else {
                DeviceTypeCandidate(
                    label = "Apple Find My-like prefix (length mismatch)",
                    confidence = ObservationConfidence.POSSIBLE,
                    evidence = listOf(
                        "prefix 0x05 0x12 matches the Find My separated prefix",
                        "payload length ${payload.size} does NOT match the documented $FIND_MY_PAYLOAD_SIZE bytes",
                    ),
                )
            }
        }

        return null
    }
}
