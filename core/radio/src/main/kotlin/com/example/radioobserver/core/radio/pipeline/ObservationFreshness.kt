package com.example.radioobserver.core.radio.pipeline

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType

/**
 * Freshness of an observation (task 2.7). FRESH means "arrived/reported recently"; it is
 * NOT a discovery or presence claim. FRESH is deliberately withheld from cached Wi-Fi
 * results (updated=false or unknown result age) so stale system-cached data can never be
 * presented as a fresh observation.
 */
enum class FreshnessState {
    /** Known age, within the fresh window, and not flagged as cached. */
    FRESH,

    /** Age is known but older than the fresh window (includes cached updated=false results). */
    STALE,

    /** Age cannot be established (unknown timestamp, future/negative/overflow values). */
    UNKNOWN,
}

/** Freshness thresholds; ages are plain elapsed milliseconds regardless of clock base. */
data class FreshnessConfig(
    /** Known age at or below this is FRESH (when not cached-flagged). */
    val freshWindowMs: Long = 30_000,
)

/**
 * Conservative, clock-base-honest age/freshness computation (task 2.7).
 *
 * Clock-base rules (never conflated):
 * - Wi-Fi RESULT age comes ONLY from the platform boot-relative timestamp, preserved in
 *   sourceFields["resultAgeMs"] as a plain age. If it cannot be established, the age is
 *   UNKNOWN - the receipt wall-clock is deliberately NOT substituted, because "when the
 *   cache was read" would be conflated with "how old the result is".
 * - BLE age is computed from the RECEIPT wall-clock: now - observedAtEpochMs. Negative
 *   results (future timestamps / clock jumps) are UNKNOWN, never clamped into fake freshness.
 * - The pipeline RETENTION TTL is a separate concept measured from observedAtEpochMs.
 */
object ObservationFreshness {

    /** Beyond this a computed age is treated as implausible/overflow: unknown. */
    const val MAX_TRACKED_AGE_MS: Long = 24L * 60 * 60 * 1000

    fun ageMs(device: ObservedRadioDevice, nowMs: Long): Long? {
        if (device.radioType == RadioType.WIFI) {
            // RESULT age only: never substitute receipt time for data age (honest UNKNOWN).
            val resultAge = device.sourceFields["resultAgeMs"]?.toLongOrNull()
            return if (resultAge != null && resultAge >= 0 && resultAge <= MAX_TRACKED_AGE_MS) {
                resultAge // plain elapsed age; base already accounted for upstream
            } else {
                null
            }
        }
        val receiptAge = nowMs - device.observedAtEpochMs
        if (receiptAge < 0) return null // future/implausible: conservative UNKNOWN
        return if (receiptAge > MAX_TRACKED_AGE_MS) null else receiptAge
    }

    fun freshness(device: ObservedRadioDevice, nowMs: Long, config: FreshnessConfig = FreshnessConfig()): FreshnessState {
        val age = ageMs(device, nowMs) ?: return FreshnessState.UNKNOWN
        if (age > config.freshWindowMs) return FreshnessState.STALE
        // Age is within the fresh window: cached Wi-Fi results still never claim FRESH.
        val updatedFlag = device.sourceFields["resultsUpdatedFlag"]
        if (device.radioType == RadioType.WIFI && updatedFlag != "true") return FreshnessState.STALE
        return FreshnessState.FRESH
    }
}
