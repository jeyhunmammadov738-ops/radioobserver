package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType

/**
 * Phase 3 task 3.6 — pure Kotlin/JVM feed/detail view projection (zero android.*
 * imports, no side effects). Operates ONLY on the sanitized [ObservationUiState]
 * produced by the 3.4 orchestrator: no second retention, no cache, no history. The
 * pipeline's 200-row bound and 15-minute lazy TTL remain authoritative — this layer
 * derives everything from the CURRENT state, so a disposed/evicted/replaced row can
 * never be resurrected from here.
 *
 * Selection safety: [ObservationRowUi.rowId] is the pipeline's session-local ordinal
 * (never address/hash-derived). Detail resolution is ALWAYS against the current state
 * snapshot: an expired, evicted, disposed or new-session row simply does not resolve,
 * and the caller must render the honest "no longer available" state.
 */
object ObservationFeedProjection {

    /** Read-only source filter for the combined ephemeral feed. */
    enum class FeedFilter { ALL, BLUETOOTH, WIFI }

    /**
     * Honest empty-feed taxonomy. Null means the feed HAS rows to show (no empty state).
     * NONE of these states may imply that no radios are nearby.
     */
    enum class FeedEmptyKind {
        /** A session is (still) active but produced no observations yet. */
        SESSION_ACTIVE_NO_OBSERVATIONS,
        /** No session is active (never started or ended); nothing was retained. */
        NO_SESSION_NO_OBSERVATIONS,
        /** Observations exist, but the chosen source filter matches none. */
        NO_MATCHES_FOR_FILTER,
    }

    /** Counts per source from the sanitized rows only. */
    data class FeedCounts(val total: Int, val bluetooth: Int, val wifi: Int)

    /** Filtering NEVER reorders: the pipeline's newest-first order stays authoritative. */
    fun rowsFor(state: ObservationUiState, filter: FeedFilter): List<ObservationRowUi> =
        when (filter) {
            FeedFilter.ALL -> state.rows
            FeedFilter.BLUETOOTH -> state.rows.filter { it.radio == RadioType.BLE }
            FeedFilter.WIFI -> state.rows.filter { it.radio == RadioType.WIFI }
        }

    fun counts(state: ObservationUiState): FeedCounts = FeedCounts(
        total = state.rows.size,
        bluetooth = state.rows.count { it.radio == RadioType.BLE },
        wifi = state.rows.count { it.radio == RadioType.WIFI },
    )

    /**
     * Which honest empty state (if any) applies. Order of decisions:
     * 1. rows match the filter -> null (feed renders).
     * 2. rows exist overall but not under this filter -> NO_MATCHES_FOR_FILTER.
     * 3. no rows at all: an active (OBSERVING/STOPPING) session ->
     *    SESSION_ACTIVE_NO_OBSERVATIONS; otherwise -> NO_SESSION_NO_OBSERVATIONS.
     */
    fun emptyKind(state: ObservationUiState, filter: FeedFilter): FeedEmptyKind? {
        if (state.rows.isNotEmpty()) {
            return if (rowsFor(state, filter).isEmpty()) FeedEmptyKind.NO_MATCHES_FOR_FILTER else null
        }
        val active = state.bleSession.status == RadioSessionUiStatus.OBSERVING ||
            state.bleSession.status == RadioSessionUiStatus.STOPPING ||
            state.wifiSession.status == RadioSessionUiStatus.OBSERVING ||
            state.wifiSession.status == RadioSessionUiStatus.STOPPING
        return if (active) FeedEmptyKind.SESSION_ACTIVE_NO_OBSERVATIONS
        else FeedEmptyKind.NO_SESSION_NO_OBSERVATIONS
    }

    /**
     * Resolves a selected row against the CURRENT sanitized state ONLY. No snapshot is
     * cached here: disposal, eviction, TTL sweep, session replacement or a new
     * generation yields null, and the UI must show the unavailable state — never a
     * ghost row, never a restart to "recover" it.
     *
     * Duplicate rowIds cannot map to two rows within one state; the first match wins
     * deterministically (rowIds are unique per session by pipeline construction; this
     * is a defensive total function, not a reliance on duplication).
     */
    fun detailFor(state: ObservationUiState, rowId: String): ObservationRowUi? =
        if (rowId.isBlank()) null else state.rows.firstOrNull { it.rowId == rowId }
}
