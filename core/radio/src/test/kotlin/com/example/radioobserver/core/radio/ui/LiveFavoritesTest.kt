package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 04 regressions (device-free): session-local LIVE favorites.
 * A. toggle · B. disposal cleanup · C. no History write (structural: pure object,
 * no persistence type) · J. no cross-session identity matching (rowId ordinal
 * only) · K. no raw identifiers stored (String set of session ordinals only) ·
 * R. favorites-only derivation never mutates or reorders source rows.
 */
class LiveFavoritesTest {

    private fun row(id: String, radio: RadioType = RadioType.BLE) = ObservationRowUi(
        rowId = id, radio = radio, name = null, manufacturerHint = null,
        typeHint = null, confidence = ObservationConfidence.UNKNOWN,
        signalBucket = SignalBucket.NEAR, rssiDbm = null, locallyAdministered = false,
        freshness = FreshnessState.FRESH, ageMs = null, lastSeenAtEpochMs = 0,
        parseIssueCount = 0,
    )

    @Test
    fun `A - toggle add and remove`() {
        var ids = emptySet<String>()
        ids = LiveFavorites.toggle(ids, "obs-1")
        assertTrue(LiveFavorites.isFavorite(ids, "obs-1"))
        ids = LiveFavorites.toggle(ids, "obs-2")
        assertEquals(setOf("obs-1", "obs-2"), ids)
        ids = LiveFavorites.toggle(ids, "obs-1")
        assertEquals(setOf("obs-2"), ids)
        // Toggle targets ONLY its own row — an unrelated favorite survives.
        assertTrue(LiveFavorites.isFavorite(ids, "obs-2"))
    }

    @Test
    fun `B - disposal cleanup drops gone ids and clears on empty session`() {
        var ids = LiveFavorites.toggle(emptySet(), "obs-1")
        ids = LiveFavorites.toggle(ids, "obs-2")
        // Session rows evicted obs-2 (TTL/disposal): only obs-1 survives.
        ids = LiveFavorites.retain(ids, listOf("obs-1"))
        assertEquals(setOf("obs-1"), ids)
        // Session disposed (empty rows): favorites die with the session.
        ids = LiveFavorites.retain(ids, emptyList())
        assertTrue(ids.isEmpty())
    }

    @Test
    fun `C and K - pure in-memory UI state, no persistence type reachable`() {
        // The API is Set<String> in / Set<String> out: there is no write channel at
        // all, so favoriting CANNOT write History (structural, plus behavioral
        // purity below). Only session-local ordinal strings are stored — no name,
        // address, or payload field exists on this surface.
        val ids = LiveFavorites.toggle(emptySet(), "obs-1")
        assertEquals(setOf("obs-1"), ids)
        // Purity: the input set is never mutated.
        val original = setOf("obs-1")
        LiveFavorites.retain(original, emptyList())
        assertEquals(setOf("obs-1"), original)
    }

    @Test
    fun `J - row id ordinals only, never cross-session device identity`() {
        val session1 = LiveFavorites.toggle(emptySet(), "obs-1")
        // A DIFFERENT session reuses the ordinal "obs-1" for a different physical
        // radio: LiveFavorites does not compare anything across sessions — a fresh
        // Activity simply starts from an empty set (no persistence, no matching).
        val session2 = emptySet<String>()
        assertFalse(session1 == session2 || LiveFavorites.isFavorite(session2, "obs-1"))
        // And retaining against a different session's rows drops everything.
        assertTrue(LiveFavorites.retain(session1, listOf("row-x")).isEmpty())
    }

    @Test
    fun `R - favorites-only view is a read-only derivation`() {
        val rows = listOf(row("obs-1"), row("obs-2", RadioType.WIFI), row("obs-3"))
        val ids = LiveFavorites.toggle(LiveFavorites.toggle(emptySet(), "obs-3"), "obs-2")
        val view = LiveFavorites.rowsFor(rows, ids)
        // Authoritative pipeline order (newest-first) is preserved; nothing is
        // reordered, nothing is mutated, membership is a strict subset.
        assertEquals(listOf("obs-2", "obs-3"), view.map { it.rowId })
        assertEquals(3, rows.size) // source list untouched
        // Empty favorite set: empty view (never "everything").
        assertTrue(LiveFavorites.rowsFor(rows, emptySet()).isEmpty())
    }
}
