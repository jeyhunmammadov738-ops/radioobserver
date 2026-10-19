package com.example.radioobserver.core.radio.pipeline

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.signal.SignalBuckets

/**
 * PRIVACY-SAFE projection (task 2.7) for any future UI/diagnostics. By construction it
 * CANNOT expose: raw BLE addresses, raw Wi-Fi BSSIDs, raw advertisement bytes, or
 * parse-issue strings (which may embed raw identifiers). Raw identifiers remain volatile
 * inside the pipeline for session-local dedup only and are disposed with it.
 *
 * [observationId] is a per-pipeline session-local ordinal — deliberately NOT derived from
 * any address (no long-lived, silently linkable hash identifiers).
 *
 * Confidence/type are carried VERBATIM from the evidence models: nothing here promotes a
 * candidate to a proven product, person, position, distance, or threat.
 */
data class PublicObservation(
    val observationId: String,
    val radio: RadioType,
    val displayName: String?,
    val manufacturerName: String?,
    val deviceTypeLabel: String?,
    val confidence: ObservationConfidence,
    val signalBucket: SignalBucket,
    val rssiDbm: Int?,
    val locallyAdministeredAddress: Boolean,
    val freshness: FreshnessState,
    val ageMs: Long?,
    /** Receipt time (wall-clock). Freshness clock bases are kept separate (see [ObservationFreshness]). */
    val lastSeenAtEpochMs: Long,
    /** Count only; the texts may embed raw identifiers and are never surfaced. */
    val parseIssueCount: Int,
)

/** OUI prefix visible, remainder masked; malformed/absent addresses are fully withheld. */
internal fun maskAddress(address: String?): String? {
    if (address.isNullOrBlank()) return null
    val octets = address.split(":")
    if (octets.size != 6) return "(withheld)"
    return octets.take(3).joinToString(":") + ":**:**:**"
}

/** Internal mapping from the volatile evidence model to the privacy-safe projection. */
internal fun toPublicObservation(
    device: ObservedRadioDevice,
    observationId: String,
    nowMs: Long,
    freshnessConfig: FreshnessConfig,
): PublicObservation = PublicObservation(
    observationId = observationId,
    radio = device.radioType,
    displayName = device.displayName,
    manufacturerName = device.manufacturerCandidate?.companyName,
    deviceTypeLabel = device.deviceTypeCandidate?.label,
    confidence = device.deviceTypeCandidate?.confidence ?: ObservationConfidence.UNKNOWN,
    signalBucket = SignalBuckets.bucketFor(device.rssiDbm),
    rssiDbm = device.rssiDbm,
    locallyAdministeredAddress = device.locallyAdministeredAddress,
    freshness = ObservationFreshness.freshness(device, nowMs, freshnessConfig),
    ageMs = ObservationFreshness.ageMs(device, nowMs),
    lastSeenAtEpochMs = device.observedAtEpochMs,
    parseIssueCount = device.parseIssues.size,
)
