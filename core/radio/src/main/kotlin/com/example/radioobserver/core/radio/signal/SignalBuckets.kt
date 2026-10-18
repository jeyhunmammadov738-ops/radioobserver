package com.example.radioobserver.core.radio.signal

/** Coarse radio-signal category. NEVER a distance, proximity, or threat measurement. */
enum class SignalBucket { IMMEDIATE, NEAR, FAR, UNAVAILABLE }

/**
 * Configurable RSSI thresholds. Boundary behavior is INCLUSIVE at the threshold:
 *  - rssi >= [immediateThresholdDbm] -> IMMEDIATE
 *  - [nearThresholdDbm] <= rssi < [immediateThresholdDbm] -> NEAR
 *  - rssi < [nearThresholdDbm] -> FAR
 */
data class RssiThresholds(
    val immediateThresholdDbm: Int = -50,
    val nearThresholdDbm: Int = -70,
) {
    init {
        require(immediateThresholdDbm >= nearThresholdDbm) {
            "immediateThresholdDbm ($immediateThresholdDbm) must be >= nearThresholdDbm ($nearThresholdDbm)"
        }
    }
}

/**
 * Deterministic RSSI-to-signal-bucket mapping.
 *
 * RSSI alone cannot prove distance, physical proximity, or any tracking relationship.
 * Invalid or unavailable RSSI is mapped to UNAVAILABLE, never to a fabricated value.
 */
object SignalBuckets {

    /** RSSI plausibility window; values outside are treated as invalid, not as signal evidence. */
    const val MIN_VALID_DBM = -128
    const val MAX_VALID_DBM = 0

    fun isRssiPlausible(rssiDbm: Int): Boolean = rssiDbm in MIN_VALID_DBM..MAX_VALID_DBM

    fun bucketFor(rssiDbm: Int?, thresholds: RssiThresholds = RssiThresholds()): SignalBucket {
        if (rssiDbm == null || !isRssiPlausible(rssiDbm)) return SignalBucket.UNAVAILABLE
        return when {
            rssiDbm >= thresholds.immediateThresholdDbm -> SignalBucket.IMMEDIATE
            rssiDbm >= thresholds.nearThresholdDbm -> SignalBucket.NEAR
            else -> SignalBucket.FAR
        }
    }
}
