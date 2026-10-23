package com.example.radioobserver.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * MICRO-POLISH (2026-10-23): the live detail previously rendered the pair
 * "Son görülme" / "Son görülme: 20:45:23" — a duplicated label inside the value.
 * [DetailTime.plainClockText] is the pure, host-tested presentation of the bare
 * authoritative pipeline timestamp (no label prefix, no invented time).
 */
class DetailTimeTest {

    @Test
    fun `value contains ONLY the clock time, never a duplicated label prefix`() {
        val value = DetailTime.plainClockText(1_761_200_000_000L)
        assertFalse(value.contains("Son görülme"))
        assertFalse(value.contains("görülme"))
    }

    @Test
    fun `formatting is a stable HH-mm-ss clock shape`() {
        // Locale-independent shape check: exactly three two-digit groups.
        // The default locale is RESTORED afterwards so sibling tests (e.g. the
        // diagnostics ring's decimal separator) are never affected by this test.
        val original = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale("tr", "TR"))) {
                Locale.setDefault(locale)
                val value = DetailTime.plainClockText(0L)
                assertEquals(8, value.length)
                assertTrue(value.substring(2, 3) == ":")
                assertTrue(value.substring(5, 6) == ":")
            }
        } finally {
            Locale.setDefault(original)
        }
    }
}
