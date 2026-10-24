package com.example.radioobserver.core.radio.ui

/**
 * WORKSTREAM 04 — pure, SESSION-LOCAL live favorites (zero android.* imports).
 *
 * PRIVACY CONTRACT: a favorite is bound to the session-local [ObservationRowUi.rowId]
 * ordinal ONLY. It is UI state in memory: nothing is persisted, nothing is derived
 * from addresses, payloads or names, and a starred row can never match "the same
 * device" in another session (rowIds are session ordinals, and this object never
 * compares anything else). Toggling a favorite writes NO History record and starts
 * or stops NOTHING — callers apply the returned set to UI state only.
 *
 * DISPOSAL: favorites die with their observations — [retain] drops ids whose rows
 * are gone (an emptied/disposed session therefore clears the set completely).
 */
object LiveFavorites {

    /** Pure toggle; returns the new immutable set (add if absent, remove if present). */
    fun toggle(ids: Set<String>, rowId: String): Set<String> =
        if (rowId in ids) ids - rowId else ids + rowId

    fun isFavorite(ids: Set<String>, rowId: String): Boolean = rowId in ids

    /**
     * Disposal/eviction cleanup: keep only favorites whose observation row still
     * exists. An empty row list (expired TTL, cleared session, disposal) yields an
     * EMPTY favorite set — no stale session-local ids survive their session.
     */
    fun retain(ids: Set<String>, rowIds: Collection<String>): Set<String> {
        if (ids.isEmpty()) return ids
        val present = rowIds.toSet()
        val kept = ids.intersect(present)
        // Session-local ids that reference NO current row are gone with the session.
        return if (rowIds.isEmpty()) emptySet() else kept
    }

    /**
     * Favorites-only DERIVATION for the Liste view (a read-only filter — the
     * authoritative observation list is never mutated). The same shared projection
     * boundary feeds the rows in; this only restricts the display. Radar and Zaman
     * tüneli keep their unchanged membership.
     */
    fun rowsFor(rows: List<ObservationRowUi>, ids: Set<String>): List<ObservationRowUi> =
        if (ids.isEmpty()) emptyList() else rows.filter { it.rowId in ids }
}
