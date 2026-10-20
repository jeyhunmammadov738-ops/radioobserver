package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.PublicObservation

/**
 * Phase 3 task 3.2 — pure presentation mapping. NO side effects: no permission calls,
 * no Android APIs, no receivers, no timers, no IO, no logging of identifiers. Every
 * function is a total function over its inputs; invalid values are mapped defensively
 * to "unknown" rather than invented.
 *
 * The ONLY accepted observation input is the sanitized [PublicObservation] projection,
 * so raw addresses/BSSIDs/payloads/parse-issue texts are unreachable here by design.
 */
object ObservationUiMapper {

    /**
     * Maps one sanitized public observation to a presentation row.
     * [index] is used only as a session-local fallback identity when the pipeline
     * ordinal is blank; it is never derived from an address or its hash.
     */
    fun toRow(observation: PublicObservation, index: Int = 0): ObservationRowUi {
        require(index >= 0) { "index must not be negative" }
        return ObservationRowUi(
            rowId = observation.observationId.ifBlank { "row-$index" },
            radio = observation.radio,
            name = observation.displayName?.takeIf { it.isNotBlank() },
            manufacturerHint = observation.manufacturerName?.takeIf { it.isNotBlank() },
            typeHint = observation.deviceTypeLabel?.takeIf { it.isNotBlank() },
            confidence = observation.confidence,
            signalBucket = observation.signalBucket,
            rssiDbm = observation.rssiDbm,
            locallyAdministered = observation.locallyAdministeredAddress,
            // Freshness is carried VERBATIM from the pipeline: the UI never recomputes,
            // upgrades or re-labels it. Cached Wi-Fi can therefore never become FRESH here.
            freshness = observation.freshness,
            ageMs = observation.ageMs?.takeIf { it >= 0 },
            lastSeenAtEpochMs = observation.lastSeenAtEpochMs,
            parseIssueCount = observation.parseIssueCount.coerceAtLeast(0),
        )
    }

    fun toRows(observations: List<PublicObservation>): List<ObservationRowUi> =
        observations.mapIndexed { index, observation -> toRow(observation, index) }

    /**
     * Applies a session status update for EXACTLY ONE radio, leaving the other radio's
     * state untouched (independence is structural, not conventional).
     */
    fun withSessionStatus(
        state: ObservationUiState,
        radio: RadioType,
        status: RadioSessionUiStatus,
        issueKey: SessionIssueKey? = null,
        remainingMs: Long? = null,
    ): ObservationUiState {
        require(status != RadioSessionUiStatus.OBSERVING || remainingMs == null || remainingMs > 0) {
            "an observing session must not report zero remaining time"
        }
        val updated = RadioSessionUiState(radio, status, issueKey, remainingMs)
        return when (radio) {
            RadioType.BLE -> state.copy(bleSession = updated)
            RadioType.WIFI -> state.copy(wifiSession = updated)
        }
    }

    /**
     * Records a location-permission outcome. DELIBERATELY session-status neutral: even a
     * PRECISE grant must NOT put a radio into CHECKING/OBSERVING — a NEW explicit Start
     * tap is required afterwards (developer-approved product decision).
     */
    fun withLocationAccess(state: ObservationUiState, access: LocationAccessUi): ObservationUiState =
        state.copy(locationAccess = access)

    /**
     * Foreground loss terminates BOTH radios' sessions (Phase 2 RESUMED-only policy) and
     * never leaves anything that could auto-resume: both end in STOPPED/FOREGROUND_LOST.
     */
    fun withForegroundLost(state: ObservationUiState): ObservationUiState = state.copy(
        bleSession = RadioSessionUiState(
            RadioType.BLE, RadioSessionUiStatus.STOPPED, SessionIssueKey.FOREGROUND_LOST),
        wifiSession = RadioSessionUiState(
            RadioType.WIFI, RadioSessionUiStatus.STOPPED, SessionIssueKey.FOREGROUND_LOST),
    )

    /**
     * Replaces the visible feed (bounded upstream by the pipeline's 200/TTL policy).
     * Callers pass the CURRENT session snapshot only; no history is retained here.
     */
    fun withRows(state: ObservationUiState, rows: List<ObservationRowUi>): ObservationUiState =
        state.copy(rows = rows)

    /** Clears the feed, e.g. on session teardown; session statuses are not modified. */
    fun withRowsCleared(state: ObservationUiState): ObservationUiState = state.copy(rows = emptyList())
}
