package com.example.radioobserver.core.radio.factory

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.parser.BleAdvertisementParser
import com.example.radioobserver.core.radio.parser.BleDeviceClassifier
import com.example.radioobserver.core.radio.parser.BleParseInput
import com.example.radioobserver.core.radio.parser.BleParseResult

/**
 * Builds a canonical [ObservedRadioDevice] from either raw advertisement bytes or an
 * Android-extracted manufacturer record. Pure JVM; no android.* imports.
 */
object BleObservationFactory {

    fun fromRawAdvertisement(
        bytes: ByteArray?,
        address: String?,
        rssiDbm: Int?,
        observedAtEpochMs: Long,
    ): ObservedRadioDevice {
        val parse = BleAdvertisementParser.parse(BleParseInput.RawAdvertisement(bytes ?: ByteArray(0)))
        return build(parse, address, rssiDbm, observedAtEpochMs)
    }

    fun fromAndroidManufacturerData(
        companyId: Int,
        payload: ByteArray,
        address: String?,
        rssiDbm: Int?,
        observedAtEpochMs: Long,
    ): ObservedRadioDevice {
        val parse = BleAdvertisementParser.parse(BleParseInput.ManufacturerData(companyId, payload))
        return build(parse, address, rssiDbm, observedAtEpochMs)
    }

    private fun build(
        parse: BleParseResult,
        address: String?,
        rssiDbm: Int?,
        observedAtEpochMs: Long,
    ): ObservedRadioDevice {
        val (manufacturer, deviceType) =
            BleDeviceClassifier.classify(parse.companyId, parse.manufacturerPayload)
        val issues = parse.issues.toMutableList()

        val key = if (!address.isNullOrBlank()) {
            "ble:" + address.lowercase()
        } else {
            issues += "no BLE address available; address-less observations share a single dedup key (conservative)"
            "ble:no-address"
        }

        return ObservedRadioDevice(
            observationKey = key,
            radioType = RadioType.BLE,
            observedAtEpochMs = observedAtEpochMs,
            displayName = parse.localName,
            address = address,
            manufacturerCandidate = manufacturer,
            deviceTypeCandidate = deviceType,
            rssiDbm = rssiDbm,
            locallyAdministeredAddress = address != null && isLocallyAdministeredMac(address),
            sourceFields = mapOf("scanInitiatedByApp" to "false"),
            parseIssues = issues,
        )
    }

    /** Bit 1 (0x02) of the first octet set => locally administered (likely randomized) BLE address. */
    fun isLocallyAdministeredMac(address: String): Boolean {
        val firstOctet = address.trim().lowercase().substringBefore(':').toIntOrNull(16)
        return firstOctet != null && (firstOctet and 0x02) != 0
    }
}
