package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType

/**
 * Phase 3 task 3.5 — pure Kotlin/JVM projection from the sanitized [ObservationUiState]
 * to control-screen presentation facts (task 3.2's contract, applied). Zero android.*
 * imports: the Compose layer only renders what this pure function derives, so the
 * button enable/disable rules, honest notes and summaries are host-testable.
 *
 * Developer-approved rules encoded here:
 * - Start stays ENABLED for every non-active status (including PERMISSION_REQUIRED, so
 *   the two-tap permission flow is always reachable, and BLOCKED, so a fresh tap can
 *   re-gate honestly). It is disabled ONLY while a session is active or stopping,
 *   preventing duplicate sessions from repeated taps.
 * - Stop is ENABLED only while a session is active or stopping; repeated Stop elsewhere
 *   is disabled (the orchestrator would no-op anyway).
 * - The empty-feed honesty note is shown only after observation has actually happened:
 *   a cold-start IDLE screen must not claim "no observations" as a session result.
 * - The session limit label uses ONLY the authoritative [RadioSessionUiState.remainingMs]
 *   (set from the real bounded spec at session start); no invented countdown is derived.
 * - The observation count is the sanitized row count only; no identity is exposed.
 */
object ControlScreenProjection {

    /** Start is callable unless this radio already has an active/being-stopped session. */
    fun startEnabled(status: RadioSessionUiStatus): Boolean =
        status != RadioSessionUiStatus.OBSERVING && status != RadioSessionUiStatus.STOPPING

    /** Stop is callable only while a session is actually active or stopping. */
    fun stopEnabled(status: RadioSessionUiStatus): Boolean =
        status == RadioSessionUiStatus.OBSERVING || status == RadioSessionUiStatus.STOPPING

    /**
     * The bounded-session limit in seconds, derived ONLY from the authoritative
     * [RadioSessionUiState.remainingMs] of a live session (the real spec bound written at
     * session start). Null when no real value exists: the UI must then show NO timer.
     */
    fun sessionLimitSeconds(session: RadioSessionUiState): Long? =
        if (session.status == RadioSessionUiStatus.OBSERVING || session.status == RadioSessionUiStatus.STOPPING) {
            session.remainingMs?.div(1000)
        } else null

    /** Sanitized observation count for the small honest summary. Never identity data. */
    fun observationCount(rows: List<ObservationRowUi>): Int = rows.size

    /**
     * The EMPTY_FEED_HONEST_NOTE is shown only when observation has actually run in this
     * session-yet-terminated window: an empty feed must never be presented on a cold,
     * never-started screen (and never implies absence of nearby radios when shown).
     */
    fun showEmptyFeedNote(state: ObservationUiState): Boolean {
        if (state.rows.isNotEmpty()) return false
        val statuses = listOf(state.bleSession.status, state.wifiSession.status)
        return statuses.any {
            it == RadioSessionUiStatus.OBSERVING || it == RadioSessionUiStatus.STOPPING ||
                it == RadioSessionUiStatus.STOPPED || it == RadioSessionUiStatus.TIMED_OUT ||
                it == RadioSessionUiStatus.ERROR
        }
    }

    /** The two radios are independent by construction; helper for card-level reasoning. */
    fun radioFor(session: RadioSessionUiState): RadioType = session.radio
}
