package com.example.radioobserver.core.radio.parser

/** Bluetooth AD structure type codes used by Phase 1 parsing. */
object AdType {
    const val SHORT_LOCAL_NAME = 0x08
    const val COMPLETE_LOCAL_NAME = 0x09
    const val MANUFACTURER_SPECIFIC = 0xFF
}

/** Input variants for the pure-JVM BLE parser (no android.* imports anywhere in this module). */
sealed interface BleParseInput {
    /** Full raw advertisement bytes: a sequence of AD structures [length, type, data...]. */
    class RawAdvertisement(val bytes: ByteArray) : BleParseInput

    /**
     * Android-extracted manufacturer record: the OS has already stripped the AD length/type
     * and parsed the little-endian company id. The parser does NOT re-parse the company id
     * from [payload]; it consumes [companyId] directly.
     */
    class ManufacturerData(val companyId: Int, val payload: ByteArray) : BleParseInput
}

/** Raw outcome of byte-level AD-structure parsing. */
data class BleParseResult(
    val companyId: Int? = null,
    /** Manufacturer payload EXCLUDING the two little-endian company-id bytes. */
    val manufacturerPayload: ByteArray = ByteArray(0),
    val localName: String? = null,
    val adStructuresParsed: Int = 0,
    val issues: List<String> = emptyList(),
)

/**
 * Pure Kotlin BLE advertisement byte parser.
 *
 * Handles: empty/null input, truncated AD structures, zero-length padding terminators,
 * oversized manufacturer payloads, duplicate manufacturer structures — without throwing.
 */
object BleAdvertisementParser {

    /** Defensive sanity cap; a single AD structure can hold at most 30 data bytes. */
    const val MAX_MANUFACTURER_PAYLOAD_BYTES = 62

    fun parse(input: BleParseInput): BleParseResult = when (input) {
        is BleParseInput.RawAdvertisement -> parseAdvertisement(input.bytes)
        is BleParseInput.ManufacturerData -> parseManufacturerData(input.companyId, input.payload)
    }

    /** Parses raw BLE advertisement bytes (AD structures). Never throws on malformed input. */
    fun parseAdvertisement(bytes: ByteArray?): BleParseResult {
        if (bytes == null || bytes.isEmpty()) {
            return BleParseResult(issues = listOf("empty or null advertisement"))
        }

        var index = 0
        var structures = 0
        var companyId: Int? = null
        var manufacturerPayload = ByteArray(0)
        var localName: String? = null
        val issues = mutableListOf<String>()

        while (index < bytes.size) {
            val length = bytes[index].toInt() and 0xFF
            if (length == 0) break // zero length = legal padding terminator

            val structureEndExclusive = index + 1 + length
            if (structureEndExclusive > bytes.size) {
                issues += "truncated AD structure at offset $index: declared length $length exceeds " +
                    "remaining ${bytes.size - index - 1} byte(s)"
                break
            }

            val type = bytes[index + 1].toInt() and 0xFF
            val data = bytes.copyOfRange(index + 2, structureEndExclusive)

            when (type) {
                AdType.MANUFACTURER_SPECIFIC -> {
                    if (data.size < 2) {
                        issues += "manufacturer-specific structure too short (${data.size} byte(s)); " +
                            "a company id needs 2 bytes"
                    } else if (companyId == null) {
                        // Company id: 16-bit little-endian in the AD data.
                        companyId = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)
                        manufacturerPayload = data.copyOfRange(2, data.size)
                    } else {
                        issues += "multiple manufacturer-specific structures present; keeping the first"
                    }
                }
                AdType.COMPLETE_LOCAL_NAME, AdType.SHORT_LOCAL_NAME -> {
                    if (localName == null) {
                        localName = String(data, Charsets.UTF_8)
                    }
                }
            }

            structures++
            index = structureEndExclusive
        }

        return BleParseResult(
            companyId = companyId,
            manufacturerPayload = manufacturerPayload,
            localName = localName,
            adStructuresParsed = structures,
            issues = issues,
        )
    }

    /** Android-extracted path: company id consumed as-is, payload taken verbatim. */
    private fun parseManufacturerData(companyId: Int, payload: ByteArray): BleParseResult {
        val issues = mutableListOf<String>()
        if (payload.size > MAX_MANUFACTURER_PAYLOAD_BYTES) {
            issues += "manufacturer payload ${payload.size} bytes exceeds sanity cap " +
                MAX_MANUFACTURER_PAYLOAD_BYTES
        }
        return BleParseResult(
            companyId = companyId,
            manufacturerPayload = payload,
            adStructuresParsed = 1,
            issues = issues,
        )
    }
}
