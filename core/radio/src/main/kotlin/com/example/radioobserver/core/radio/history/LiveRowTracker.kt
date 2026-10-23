package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.ui.ObservationRowUi

/**
 * Pure dedup tracker for the LIVE feed → history boundary. Guarantees:
 * - ONE bounded snapshot per eligible session-local observation: a rowId is saved at
 *   most once per live session (no one-row-per-advertisement, no recomposition writes).
 * - NO BACKFILL: enabling recording SEEDS the tracker with the CURRENT live rows, so
 *   rows that existed before consent are never saved afterwards — only rows that
 *   appear AFTER the user enables are recorded.
 * - NO RESURRECTION after Clear All: the tracker keeps already-saved rowIds in the
 *   seen set, so feeding the still-live rows after a clear produces nothing new.
 * - Session-local rowIds restart at obs-1 for a new session; the seen set resets when
 *   the live feed empties (session disposal), so a fresh session records its rows.
 *   Rows are only EVER fed from sanitized orchestrator state — history never injects
 *   anything into the live pipeline (the direction is one-way).
 */
class LiveRowTracker {

    private var seen = mutableSetOf<String>()
    private var recordingEnabled = false

    /** Current consent state; MUST be applied before feeding rows. */
    fun onConsentChanged(enabled: Boolean) {
        if (enabled && !recordingEnabled) pendingSeed = true
        if (!enabled) pendingSeed = false
        recordingEnabled = enabled
    }

    private var pendingSeed = false
    private var currentRows: List<ObservationRowUi> = emptyList()

    /**
     * Feed the CURRENT live rows; returns the rows eligible to be saved NOW (never
     * seen before in this live session, recording enabled, and appearing after the
     * enable — seeded rows on the enable transition are excluded).
     */
    fun feed(rows: List<ObservationRowUi>): List<ObservationRowUi> {
        if (rows.isEmpty()) {
            // Live feed disposed: session-local ordinals will restart; reset the seen
            // set so a NEW session's rows are not mistaken for duplicates. An enable
            // transition with an EMPTY screen has nothing to seed: every future row
            // is a post-consent row, so the pending seed is consumed here.
            seen = mutableSetOf()
            currentRows = emptyList()
            pendingSeed = false
            return emptyList()
        }
        if (!recordingEnabled) {
            currentRows = rows
            return emptyList()
        }
        if (pendingSeed) {
            // Consent moment: everything that was ON SCREEN BEFORE consent (the last fed
            // rows) predates it and must NOT be backfilled. Rows that appear in this
            // same feed but were NOT live before consent are post-consent rows.
            seen = mutableSetOf<String>().also { s -> currentRows.forEach { s += it.rowId } }
            pendingSeed = false
        }
        val fresh = rows.filter { it.rowId !in seen }
        seen.addAll(rows.map { it.rowId })
        currentRows = rows
        return fresh
    }

    /** In-memory only: after Clear All, currently-live rows stay seen (no resurrection). */
    fun keepSeen() = Unit
}
