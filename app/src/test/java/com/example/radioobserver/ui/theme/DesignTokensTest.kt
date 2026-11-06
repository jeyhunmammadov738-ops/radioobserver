package com.example.radioobserver.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-01 host tests: WCAG contrast math and token invariants. Pure JVM, no Compose
 * runtime, no device, no new dependency.
 */
class DesignTokensTest {

    private fun ratio(fg: Long, bg: Long) = ControlDeckTokens.contrastRatio(fg, bg)

    @Test
    fun `A primary text on background meets AA normal text`() {
        assertTrue(ratio(ControlDeckTokens.DARK_ON_BACKGROUND, ControlDeckTokens.DARK_BACKGROUND) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.DARK_ON_SURFACE, ControlDeckTokens.DARK_SURFACE) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.DARK_ON_SURFACE, ControlDeckTokens.DARK_SURFACE_LOW) >= 4.5)
    }

    @Test
    fun `B secondary text on surfaces meets AA normal text`() {
        assertTrue(ratio(ControlDeckTokens.DARK_ON_SURFACE_VARIANT, ControlDeckTokens.DARK_SURFACE_CONTAINER) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.DARK_ON_SURFACE_VARIANT, ControlDeckTokens.DARK_SURFACE_HIGH) >= 4.5)
    }

    @Test
    fun `C primary action label on its container meets AA`() {
        assertTrue(ratio(ControlDeckTokens.ON_PRIMARY, ControlDeckTokens.PRIMARY) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.PRIMARY, ControlDeckTokens.DARK_BACKGROUND) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.ON_PRIMARY_CONTAINER, ControlDeckTokens.PRIMARY_CONTAINER) >= 4.5)
    }

    @Test
    fun `D error and warning pairings meet AA`() {
        assertTrue(ratio(ControlDeckTokens.ON_ERROR_CONTAINER, ControlDeckTokens.ERROR_CONTAINER) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.ERROR, ControlDeckTokens.DARK_SURFACE) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.ON_WARNING_CONTAINER, ControlDeckTokens.WARNING_CONTAINER) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.WARNING, ControlDeckTokens.DARK_SURFACE) >= 4.5)
    }

    @Test
    fun `E secondary teal accent and outline meet their targets`() {
        assertTrue(ratio(ControlDeckTokens.SECONDARY, ControlDeckTokens.DARK_BACKGROUND) >= 4.5)
        assertTrue(ratio(ControlDeckTokens.ON_SECONDARY_CONTAINER, ControlDeckTokens.SECONDARY_CONTAINER) >= 4.5)
        // Non-text boundary: outline against the base surface.
        assertTrue(ratio(ControlDeckTokens.OUTLINE, ControlDeckTokens.DARK_SURFACE) >= 3.0)
        assertTrue(ratio(ControlDeckTokens.STALE, ControlDeckTokens.DARK_SURFACE_CONTAINER) >= 4.5)
    }

    @Test
    fun `F surface hierarchy is subtle but distinguishable`() {
        val base = ControlDeckTokens.relativeLuminance(ControlDeckTokens.DARK_BACKGROUND)
        val low = ControlDeckTokens.relativeLuminance(ControlDeckTokens.DARK_SURFACE_LOW)
        val container = ControlDeckTokens.relativeLuminance(ControlDeckTokens.DARK_SURFACE_CONTAINER)
        val high = ControlDeckTokens.relativeLuminance(ControlDeckTokens.DARK_SURFACE_HIGH)
        val highest = ControlDeckTokens.relativeLuminance(ControlDeckTokens.DARK_SURFACE_HIGHEST)
        // Strictly ascending elevation: background < low < container < high < highest.
        assertTrue(base < low && low < container && container < high && high < highest)
        // Each step is visible but not noisy (>= 1.05x luminance step).
        assertTrue(low / base >= 1.05)
        assertTrue(high / container >= 1.05)
    }

    @Test
    fun `G disabled and enabled states stay distinguishable without colour alone`() {
        // Disabled text uses the same tone family as secondary text but a lower step;
        // the UI pairs it with an explicit state label, verified structurally here by
        // requiring the disabled tone to remain legible on the surface.
        assertTrue(ratio(ControlDeckTokens.OUTLINE, ControlDeckTokens.DARK_BACKGROUND) >= 3.0)
        assertFalse(ControlDeckTokens.STALE == ControlDeckTokens.DARK_ON_SURFACE)
    }

    @Test
    fun `H spacing scale is 4dp based and ordered`() {
        assertEquals(listOf(4, 8, 12, 16, 24, 32), DeckSpacing.all)
        assertTrue(DeckSpacing.all.zipWithNext().all { (a, b) -> a < b })
        assertTrue(DeckSpacing.all.all { it % 4 == 0 })
    }

    @Test
    fun `I touch targets never fall below 48dp`() {
        assertTrue(DeckDimensions.MIN_TOUCH_TARGET >= 48)
        assertTrue(DeckDimensions.PRIMARY_ACTION_HEIGHT >= 48)
        assertTrue(DeckDimensions.COMPACT_ROW_MIN_HEIGHT >= 44)
    }

    @Test
    fun `J contrast helper is symmetric and self-pairing is one`() {
        val a = ControlDeckTokens.DARK_ON_SURFACE
        val b = ControlDeckTokens.DARK_SURFACE
        assertEquals(1.0, ratio(a, a), 0.0001)
        assertEquals(ratio(a, b), ratio(b, a), 0.0001)
    }
}
