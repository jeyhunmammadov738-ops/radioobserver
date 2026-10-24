package com.example.radioobserver.core.radio.analytics

import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 05 regressions (device-free): pure local analytics.
 * N. source/freshness distributions · O. invalid/missing timestamp exclusion ·
 * P. deterministic time buckets · Q. honest cached Wi-Fi handling (STALE saved
 * verbatim, never "fresh at save") · R. two-group comparison · S. same metrics in
 * detail and comparison (same engine) · T. no fabricated distinct-device counts
 * (counts are records, structurally) · U. no radio side effects (pure object).
 */
class SessionAnalyticsTest {

    private fun rec(
        id: String,
        radio: RadioType = RadioType.BLE,
        observedAt: Long = 1_000L,
        savedAt: Long = 2_000L,
        bucket: SignalBucket = SignalBucket.NEAR,
        fresh: FreshnessState = FreshnessState.FRESH,
        manufacturer: String? = null,
    ) = HistoryRecord(
        id = id, radio = radio, savedAtEpochMs = savedAt, observedAtEpochMs = observedAt,
        displayName = null, manufacturerHint = manufacturer, typeHint = null,
        signalBucket = bucket, freshness = fresh, ageMs = null,
    )

    @Test
    fun `N - source, signal and freshness distributions are exact counts and percents`() {
        val records = listOf(
            rec("a", RadioType.BLE, bucket = SignalBucket.IMMEDIATE),
            rec("b", RadioType.BLE, bucket = SignalBucket.NEAR),
            rec("c", RadioType.WIFI, fresh = FreshnessState.STALE),
            rec("d", RadioType.WIFI, fresh = FreshnessState.STALE),
        )
        val s = SessionAnalytics.stats(records)
        assertEquals(4, s.recordCount)
        assertEquals(2, s.bleCount)
        assertEquals(2, s.wifiCount)
        assertEquals(50, s.sourceDistribution.first { it.label == "Bluetooth" }.percent)
        // Freshness provenance is carried VERBATIM: 2 STALE saved records remain STALE.
        assertEquals(2, s.freshnessDistribution.first { it.label == "STALE" }.count)
        assertEquals(1, s.signalDistribution.first { it.label == "IMMEDIATE" }.count)
        // Manufacturer known/unknown distribution.
        assertEquals(4, s.manufacturerUnknownCount)
    }

    @Test
    fun `O - invalid timestamps are excluded and reported, never repaired`() {
        val records = listOf(
            rec("a", observedAt = 100L),
            rec("bad1", observedAt = 0L),
            rec("bad2", observedAt = -5L),
            rec("b", observedAt = 900L),
        )
        val s = SessionAnalytics.stats(records)
        assertEquals(2, s.invalidTimestampCount)
        assertEquals(100L, s.earliestObservedAtEpochMs)
        assertEquals(900L, s.latestObservedAtEpochMs)
        // Excluded points never enter bucket arithmetic.
        assertEquals(2, s.timeBuckets.sumOf { it.count })
    }

    @Test
    fun `P - time buckets are deterministic and bounded`() {
        // 6 valid records spread over a 60ms span with 6 buckets -> one per bucket.
        val records = (0 until 6).map { i -> rec("r$i", observedAt = 1_000L + i * 10) }
        val s1 = SessionAnalytics.stats(records)
        val s2 = SessionAnalytics.stats(records)
        assertEquals(SessionAnalyticsLimits.TIME_BUCKET_COUNT, s1.timeBuckets.size)
        assertTrue(s1.timeBuckets.all { it.count == 1 })
        assertEquals(s1, s2) // pure function: identical input -> identical output
        // Degenerate single-point span: everything in bucket 0, still bounded.
        val one = SessionAnalytics.stats(listOf(rec("only", observedAt = 5_000L)))
        assertEquals(SessionAnalyticsLimits.TIME_BUCKET_COUNT, one.timeBuckets.size)
        assertEquals(1, one.timeBuckets.first { it.index == 0 }.count)
        assertTrue(one.timeBuckets.drop(1).all { it.count == 0 })
    }

    @Test
    fun `Q - cached STALE Wi-Fi records stay old and are never presented as new discoveries`() {
        val cached = listOf(
            rec("w1", RadioType.WIFI, fresh = FreshnessState.STALE, observedAt = 100L, savedAt = 50_000L),
        )
        val s = SessionAnalytics.stats(cached)
        // Distribution keeps the ORIGINAL freshness, not the save-time state.
        assertEquals(1, s.freshnessDistribution.first { it.label == "STALE" }.count)
        assertEquals(0, s.freshnessDistribution.first { it.label == "FRESH" }.count)
        // Saved time and observation time remain DISTINCT provenance.
        assertEquals(100L, s.earliestObservedAtEpochMs)
        assertEquals(50_000L, s.earliestSavedAtEpochMs)
    }

    @Test
    fun `R and S - two-group comparison reuses the exact same engine and metrics`() {
        val left = listOf(
            rec("a", RadioType.BLE), rec("b", RadioType.BLE),
        )
        val right = listOf(
            rec("x", RadioType.WIFI, fresh = FreshnessState.STALE),
        )
        val cmp = SessionAnalytics.compare(left, right)
        // Comparison is stats(left) vs stats(right) — SAME metrics, verbatim.
        assertEquals(SessionAnalytics.stats(left), cmp.left)
        assertEquals(SessionAnalytics.stats(right), cmp.right)
        assertEquals(2, cmp.left.recordCount)
        assertEquals(1, cmp.right.recordCount)
        assertEquals(0, cmp.left.wifiCount)
        assertEquals(1, cmp.right.wifiCount)
    }

    @Test
    fun `T - record counts are counts, never distinct-device claims`() {
        // Two records from "the same area" still count as TWO records; the model
        // has no device-identity field, so no metric could claim distinct devices.
        val records = listOf(rec("a"), rec("b"))
        val s = SessionAnalytics.stats(records)
        assertEquals(2, s.recordCount)
        // Structural honesty: GroupStats carries counts and percentages only —
        // no distinct-device, distance, direction or movement field exists.
        val fields = GroupStats::class.java.declaredFields.map { it.name }.sorted()
        assertEquals(
            listOf(
                "bleCount", "earliestObservedAtEpochMs", "earliestSavedAtEpochMs",
                "freshnessDistribution", "invalidTimestampCount", "latestObservedAtEpochMs",
                "latestSavedAtEpochMs", "manufacturerKnownCount", "manufacturerUnknownCount",
                "recordCount", "signalDistribution", "sourceDistribution", "timeBuckets",
                "wifiCount",
            ),
            fields,
        )
    }

    @Test
    fun `U - pure engine with identical input identical output and no side channel`() {
        // SessionAnalytics is an object with no state and no write channel of any
        // kind (structural) and behaviorally the same records give the same stats.
        val records = listOf(rec("a", RadioType.WIFI))
        assertEquals(SessionAnalytics.stats(records), SessionAnalytics.stats(records))
        assertEquals(0, SessionAnalytics.stats(emptyList()).recordCount)
        assertNull(SessionAnalytics.stats(emptyList()).earliestObservedAtEpochMs)
        assertTrue(SessionAnalytics.stats(emptyList()).timeBuckets.isEmpty())
    }
}
