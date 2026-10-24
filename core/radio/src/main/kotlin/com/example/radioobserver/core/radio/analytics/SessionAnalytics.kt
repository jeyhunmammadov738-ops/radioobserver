package com.example.radioobserver.core.radio.analytics

import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * WORKSTREAM 05 — pure Kotlin/JVM LOCAL ANALYTICS over SAVED sanitized History
 * records (zero android.* imports, no side effects, never touches the live
 * observation pipeline and never dispatches a radio action).
 *
 * HONESTY CONTRACT:
 *  - A RECORD COUNT IS NOT A DISTINCT-PHYSICAL-DEVICE COUNT. Two saved records may
 *    originate from the same physical radio; the History store deliberately keeps
 *    no cross-session device identity, so no metric here may imply one.
 *  - Manufacturer hints and signature classifications are NOT identity proof.
 *  - No metric implies precise distance, direction, geographic coverage or device
 *    movement. [SignalBucket] is a conservative category, never a distance.
 *  - Gaps between saved records are NOT interpolated: time buckets count records
 *    per bounded interval only; empty buckets render as zero, never as trends.
 *  - [FreshnessState] provenance is carried VERBATIM from save time: a STALE
 *    (cached "Eski") record is never presented as newly discovered at save time.
 *  - Saved-record timestamp ([HistoryRecord.savedAtEpochMs]) vs source-observation
 *    timestamp ([HistoryRecord.observedAtEpochMs]) are DISTINCT concepts; bucket
 *    arithmetic uses the observation receipt time and reports every excluded
 *    (invalid) point, never inventing values.
 */
object SessionAnalyticsLimits {
    /** Deterministic bucket count for the time distribution (bounded, stable). */
    const val TIME_BUCKET_COUNT = 6
}

/** Distribution of one categorical dimension (counts + honest percentages). */
data class CategoryCount(
    val label: String,
    val count: Int,
    /** Percentage of the TOTAL records (0..100, rounded); never a probability claim. */
    val percent: Int,
)

/** One deterministic time bucket: [startEpochMs, endEpochMs) counting saved records. */
data class TimeBucket(
    val index: Int,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val count: Int,
)

/** Aggregate analytics over a bounded set of saved records. */
data class GroupStats(
    val recordCount: Int,
    val bleCount: Int,
    val wifiCount: Int,
    val sourceDistribution: List<CategoryCount>,
    val signalDistribution: List<CategoryCount>,
    val freshnessDistribution: List<CategoryCount>,
    val manufacturerKnownCount: Int,
    val manufacturerUnknownCount: Int,
    /** Earliest/latest OBSERVATION receipt times; null = unknown (no valid points). */
    val earliestObservedAtEpochMs: Long?,
    val latestObservedAtEpochMs: Long?,
    /** Records excluded from time arithmetic because their timestamps are invalid. */
    val invalidTimestampCount: Int,
    /** Earliest/latest SAVE times (bookkeeping provenance, distinct from observed). */
    val earliestSavedAtEpochMs: Long?,
    val latestSavedAtEpochMs: Long?,
    val timeBuckets: List<TimeBucket>,
)

/** Side-by-side comparison of two analytic group stats (no correlation claims). */
data class GroupComparison(
    val left: GroupStats,
    val right: GroupStats,
)

object SessionAnalytics {

    /** Deterministic stats over the given saved records. Empty input -> empty stats. */
    fun stats(records: List<HistoryRecord>): GroupStats {
        val total = records.size
        val ble = records.count { it.radio == RadioType.BLE }
        val wifi = records.count { it.radio == RadioType.WIFI }
        return GroupStats(
            recordCount = total,
            bleCount = ble,
            wifiCount = wifi,
            sourceDistribution = listOf(
                category("Bluetooth", ble, total),
                category("Wi-Fi", wifi, total),
            ),
            signalDistribution = SignalBucket.entries
                .filter { it != SignalBucket.UNAVAILABLE }
                .map { bucket -> category(bucket.name, records.count { it.signalBucket == bucket }, total) },
            freshnessDistribution = FreshnessState.entries
                .map { fresh -> category(fresh.name, records.count { it.freshness == fresh }, total) },
            manufacturerKnownCount = records.count { it.manufacturerHint != null },
            manufacturerUnknownCount = records.count { it.manufacturerHint == null },
            earliestObservedAtEpochMs = records.map { it.observedAtEpochMs }
                .filter { isValidTimestamp(it) }.minOrNull(),
            latestObservedAtEpochMs = records.map { it.observedAtEpochMs }
                .filter { isValidTimestamp(it) }.maxOrNull(),
            invalidTimestampCount = records.count { !isValidTimestamp(it.observedAtEpochMs) },
            earliestSavedAtEpochMs = records.map { it.savedAtEpochMs }.minOrNull(),
            latestSavedAtEpochMs = records.map { it.savedAtEpochMs }.maxOrNull(),
            timeBuckets = buckets(records),
        )
    }

    fun compare(left: List<HistoryRecord>, right: List<HistoryRecord>): GroupComparison =
        GroupComparison(left = stats(left), right = stats(right))

    /**
     * Deterministic time buckets over the OBSERVATION receipt times: the span
     * [earliest, latest] is divided into [SessionAnalyticsLimits.TIME_BUCKET_COUNT]
     * equal intervals. Invalid timestamps (<= 0) are EXCLUDED and counted, never
     * repaired. An empty/single-point span yields degenerate buckets (still
     * deterministic). Nothing is interpolated: counts are per-bucket only.
     */
    fun buckets(records: List<HistoryRecord>): List<TimeBucket> {
        val valid = records.filter { isValidTimestamp(it.observedAtEpochMs) }
        if (valid.isEmpty()) return emptyList()
        val times = valid.map { it.observedAtEpochMs }
        val min = times.min()
        val max = times.max()
        val n = SessionAnalyticsLimits.TIME_BUCKET_COUNT
        // Degenerate span (one point / zero width): everything falls in bucket 0.
        val width = if (max <= min) 1 else (max - min + 1)
        val bucketWidth = (width + n - 1) / n // ceil division, always >= 1
        val result = (0 until n).map { i ->
            TimeBucket(index = i, startEpochMs = min + i * bucketWidth, endEpochMs = min + (i + 1) * bucketWidth, count = 0)
        }.toMutableList()
        for (t in times) {
            val idx = ((t - min) / bucketWidth).toInt().coerceIn(0, n - 1)
            result[idx] = result[idx].copy(count = result[idx].count + 1)
        }
        return result
    }

    /** Timestamps <= 0 are treated as unknown/invalid (never repaired, never used). */
    fun isValidTimestamp(epochMs: Long): Boolean = epochMs > 0

    private fun category(label: String, count: Int, total: Int): CategoryCount =
        CategoryCount(
            label = label,
            count = count,
            percent = if (total == 0) 0 else (count * 100 + total / 2) / total,
        )
}
