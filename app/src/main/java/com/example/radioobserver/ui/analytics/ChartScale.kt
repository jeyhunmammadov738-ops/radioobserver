package com.example.radioobserver.ui.analytics

/**
 * UX-06: PURE chart scale used by the analytics bars. It converts an exact engine count
 * into a 0f..1f drawing fraction and never invents values:
 * - a zero count is always exactly 0f (no baseline pretending activity),
 * - an all-zero or negative maximum yields 0f instead of a division by zero,
 * - the largest count fills the track (1f),
 * - every other count is proportional to the largest one.
 *
 * The numeric text next to a bar always comes from the engine, never from this fraction.
 */
object ChartScale {
    fun fraction(count: Int, maxCount: Int): Float {
        if (count <= 0 || maxCount <= 0) return 0f
        if (count >= maxCount) return 1f
        return count.toFloat() / maxCount.toFloat()
    }
}
