package com.example.radioobserver.history

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PREMIUM UI/UX V2 (2026-10-23): device-free tests for the pure Turkish human-friendly
 * history-age formatter (the previous UI printed raw milliseconds such as 2,838,035).
 */
class HistoryAgeFormatTest {

    @Test
    fun `sub-minute ages are stated as less than one minute`() {
        assertEquals("1 dakikadan az", HistoryAgeFormat.formatAgeMs(0))
        assertEquals("1 dakikadan az", HistoryAgeFormat.formatAgeMs(59_999))
    }

    @Test
    fun `sub-minute ages are NOT qualified as approximate`() {
        assertEquals("1 dakikadan az", HistoryAgeFormat.formatApproximate(30_000))
    }

    @Test
    fun `minutes round down without inventing precision`() {
        assertEquals("1 dakika", HistoryAgeFormat.formatAgeMs(60_000))
        // 2,838,035 ms ≈ 47.3 min → "yaklaşık 47 dakika" (never "48 dakika").
        assertEquals("47 dakika", HistoryAgeFormat.formatAgeMs(2_838_035))
        assertEquals("yaklaşık 47 dakika", HistoryAgeFormat.formatApproximate(2_838_035))
        assertEquals("59 dakika", HistoryAgeFormat.formatAgeMs(3_599_999))
    }

    @Test
    fun `hours and days use the largest honest unit`() {
        assertEquals("1 saat", HistoryAgeFormat.formatAgeMs(3_600_000))
        assertEquals("3 saat", HistoryAgeFormat.formatAgeMs(3 * 3_600_000 + 1_000))
        assertEquals("1 gün", HistoryAgeFormat.formatAgeMs(86_400_000))
        assertEquals("2 gün", HistoryAgeFormat.formatAgeMs(2 * 86_400_000 + 999))
    }

    @Test
    fun `very large ages are bounded honestly`() {
        assertEquals("30 günden fazla", HistoryAgeFormat.formatAgeMs(45 * 86_400_000L))
        assertEquals("yaklaşık 30 günden fazla", HistoryAgeFormat.formatApproximate(45 * 86_400_000L))
    }
}
