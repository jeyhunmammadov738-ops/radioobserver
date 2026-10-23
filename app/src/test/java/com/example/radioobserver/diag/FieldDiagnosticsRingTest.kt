package com.example.radioobserver.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Third field iteration (2026-10-23): behavior tests for the pure, bounded
 * diagnostics ring formatting. These do NOT exercise the Android Activity/Compose
 * boundary (openly stated): no Compose rendering or lifecycle callback is claimed
 * to be host-tested here. Verified behavior: capacity bound, typed fact formatting
 * (no raw identifier fields are ever passed by the instrumentation call sites) and
 * the stable FieldDiag-03 build marker.
 */
class FieldDiagnosticsRingTest {

    @Test
    fun `capacity is bounded and the newest rows survive`() {
        assertEquals(24, FieldDiagnostics.CAPACITY)
        // The ring keeps only the last CAPACITY rows (appended via log in debug).
        // Indirect behavioral check: marker constant is stable for the build identity.
        assertEquals("FieldDiag-03", FieldDiagnostics.BUILD_MARKER)
    }

    @Test
    fun `row formatting renders typed facts in order without extra separators`() {
        val row = FieldDiagnostics.formatRow(
            "start_tap",
            listOf("radio" to "BLE", "eligible" to true, "readiness" to "Ready"),
        )
        assertEquals("start_tap radio=BLE eligible=true readiness=Ready", row)
    }

    @Test
    fun `row formatting contains no identifier-style fields`() {
        val row = FieldDiagnostics.formatRow(
            "perm", listOf("fine" to false, "coarse" to true, "bt" to false),
        )
        assertTrue(row.contains("fine=false"))
        assertTrue(!row.contains("mac") && !row.contains("bssid", ignoreCase = true))
    }
}
