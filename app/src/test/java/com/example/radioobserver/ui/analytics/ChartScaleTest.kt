package com.example.radioobserver.ui.analytics

import com.example.radioobserver.core.radio.analytics.SessionAnalytics
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {

    @Test
    fun `zero count stays exactly zero`() {
        assertEquals(0f, ChartScale.fraction(0, 5), 0f)
    }

    @Test
    fun `all zero maximum never divides by zero`() {
        assertEquals(0f, ChartScale.fraction(0, 0), 0f)
    }

    @Test
    fun `largest count fills the track`() {
        assertEquals(1f, ChartScale.fraction(7, 7), 0f)
    }

    @Test
    fun `counts stay proportional to the largest`() {
        assertEquals(0.5f, ChartScale.fraction(3, 6), 0.0001f)
    }

    @Test
    fun `negative inputs are not drawn`() {
        assertEquals(0f, ChartScale.fraction(-2, 5), 0f)
        assertEquals(0f, ChartScale.fraction(2, -5), 0f)
    }

    @Test
    fun `single source distribution keeps the other side at zero`() {
        val records = listOf(
            record("a", RadioType.BLE),
            record("b", RadioType.BLE),
        )
        val stats = SessionAnalytics.stats(records)
        assertEquals(2, stats.bleCount)
        assertEquals(0, stats.wifiCount)
        val ble = stats.sourceDistribution.first { it.count == 2 }
        val wifi = stats.sourceDistribution.firstOrNull { it.count == 0 }
        assertEquals(100, ble.percent)
        if (wifi != null) assertEquals(0, wifi.percent)
        assertEquals(1f, ChartScale.fraction(ble.count, 2), 0f)
        assertEquals(0f, ChartScale.fraction(0, 2), 0f)
    }

    @Test
    fun `percent uses the group total as denominator and half up rounding`() {
        // 1 of 3 -> 33, 2 of 3 -> 67; the engine owns both values.
        val records = listOf(
            record("a", RadioType.BLE),
            record("b", RadioType.WIFI),
            record("c", RadioType.WIFI),
        )
        val stats = SessionAnalytics.stats(records)
        val percents = stats.sourceDistribution.associate { it.label to it.percent }
        assertTrue(percents.values.sum() in 99..101)
        assertEquals(3, stats.recordCount)
    }

    @Test
    fun `empty group reports zero everywhere without inventing buckets`() {
        val stats = SessionAnalytics.stats(emptyList())
        assertEquals(0, stats.recordCount)
        assertEquals(0, stats.bleCount)
        assertEquals(0, stats.wifiCount)
        assertTrue(stats.sourceDistribution.all { it.count == 0 && it.percent == 0 })
        assertEquals(0f, ChartScale.fraction(0, 0), 0f)
    }

    private fun record(id: String, radio: RadioType): HistoryRecord =
        HistoryRecord(
            id = id,
            radio = radio,
            savedAtEpochMs = 1_700_000_000_000,
            observedAtEpochMs = 1_700_000_000_000,
            displayName = null,
            manufacturerHint = null,
            typeHint = null,
            signalBucket = SignalBucket.UNAVAILABLE,
            freshness = FreshnessState.UNKNOWN,
            ageMs = null,
        )
}
