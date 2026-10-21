package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Phase 3 task 3.6 acceptance tests (pure JVM): combined feed + All/BLE/Wi-Fi filter,
 * honest empty taxonomy, detail resolution against CURRENT state and its invalidation,
 * ordering preservation, and sanitized-only surface. Compose click/navigation runtime,
 * TalkBack and real rendering are UNVERIFIED (device-free protocol).
 */
class ObservationFeedProjectionTest {

    private fun row(
        id: String,
        radio: RadioType,
        freshness: FreshnessState = FreshnessState.FRESH,
        seen: Long = 1_000L,
        confidence: ObservationConfidence = ObservationConfidence.CANDIDATE,
    ) = ObservationRowUi(
        rowId = id, radio = radio, name = "X", manufacturerHint = null, typeHint = null,
        confidence = confidence, signalBucket = SignalBucket.NEAR, rssiDbm = -55,
        locallyAdministered = false, freshness = freshness, ageMs = 10,
        lastSeenAtEpochMs = seen, parseIssueCount = 0)

    private fun state(
        rows: List<ObservationRowUi> = emptyList(),
        ble: RadioSessionUiStatus = RadioSessionUiStatus.IDLE,
        wifi: RadioSessionUiStatus = RadioSessionUiStatus.IDLE,
    ) = ObservationUiState(
        bleSession = RadioSessionUiState(RadioType.BLE, ble),
        wifiSession = RadioSessionUiState(RadioType.WIFI, wifi),
        rows = rows)

    // A: combined feed + filter correctness, scanner-action-free by construction.

    @Test
    fun `A - combined feed and All-BLE-WiFi filters select the right rows`() {
        val s = state(rows = listOf(
            row("obs-1", RadioType.BLE), row("obs-2", RadioType.WIFI), row("obs-3", RadioType.BLE)))
        assertEquals(3, ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.ALL).size)
        assertEquals(listOf("obs-1", "obs-3"),
            ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.BLUETOOTH).map { it.rowId })
        assertEquals(listOf("obs-2"),
            ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.WIFI).map { it.rowId })
        val c = ObservationFeedProjection.counts(s)
        assertEquals(3, c.total); assertEquals(2, c.bluetooth); assertEquals(1, c.wifi)
    }

    // B: pipeline order is preserved verbatim (never re-sorted by proximity or identity).

    @Test
    fun `B - filtering never reorders rows and pipeline newest-first order wins`() {
        val s = state(rows = listOf(
            row("obs-9", RadioType.BLE, seen = 900),
            row("obs-4", RadioType.WIFI, seen = 400)))
        val filtered = ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.WIFI)
        assertEquals(listOf("obs-4"), filtered.map { it.rowId })
        val all = ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.ALL)
        assertEquals(listOf("obs-9", "obs-4"), all.map { it.rowId })
    }

    // F: honest empty taxonomy — active session, no session, filter mismatch.

    @Test
    fun `F - empty taxonomy distinguishes active session, ended session and filter mismatch`() {
        val active = state(ble = RadioSessionUiStatus.OBSERVING)
        assertEquals(ObservationFeedProjection.FeedEmptyKind.SESSION_ACTIVE_NO_OBSERVATIONS,
            ObservationFeedProjection.emptyKind(active, ObservationFeedProjection.FeedFilter.ALL))
        val ended = state(ble = RadioSessionUiStatus.TIMED_OUT)
        assertEquals(ObservationFeedProjection.FeedEmptyKind.NO_SESSION_NO_OBSERVATIONS,
            ObservationFeedProjection.emptyKind(ended, ObservationFeedProjection.FeedFilter.ALL))
        val mismatch = state(rows = listOf(row("obs-1", RadioType.BLE)))
        assertEquals(ObservationFeedProjection.FeedEmptyKind.NO_MATCHES_FOR_FILTER,
            ObservationFeedProjection.emptyKind(mismatch, ObservationFeedProjection.FeedFilter.WIFI))
        assertNull(ObservationFeedProjection.emptyKind(mismatch, ObservationFeedProjection.FeedFilter.ALL))
    }

    // C + D: detail resolves ONLY from current state; disposal/replacement -> unavailable.

    @Test
    fun `C and D - detail resolves from current state and vanishes after disposal`() {
        val live = state(rows = listOf(row("obs-1", RadioType.BLE), row("obs-2", RadioType.WIFI)))
        val d = ObservationFeedProjection.detailFor(live, "obs-2")
        assertNotNull(d)
        assertEquals(RadioType.WIFI, d!!.radio)
        // Session ends -> orchestrator publishes empty rows (canonical disposal):
        val disposed = state(ble = RadioSessionUiStatus.STOPPED)
        assertNull(ObservationFeedProjection.detailFor(disposed, "obs-2"))
        // New session reuses ordinal ids, but resolution is against the NEW state:
        val nextSession = state(rows = listOf(row("obs-1", RadioType.WIFI)), ble = RadioSessionUiStatus.OBSERVING)
        val resolved = ObservationFeedProjection.detailFor(nextSession, "obs-1")
        assertEquals(RadioType.WIFI, resolved!!.radio)
        // Blank selection never resolves:
        assertNull(ObservationFeedProjection.detailFor(live, " "))
    }

    // E + H: freshness carried verbatim; invalid values fail closed at the source contracts.

    @Test
    fun `E - FRESH STALE UNKNOWN stay distinct and are never promoted by the projection`() {
        val s = state(rows = listOf(
            row("obs-1", RadioType.BLE, freshness = FreshnessState.FRESH),
            row("obs-2", RadioType.BLE, freshness = FreshnessState.STALE),
            row("obs-3", RadioType.WIFI, freshness = FreshnessState.UNKNOWN)))
        val rows = ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.ALL)
        assertEquals(
            listOf(FreshnessState.FRESH, FreshnessState.STALE, FreshnessState.UNKNOWN),
            rows.map { it.freshness })
        assertEquals(FreshnessState.UNKNOWN,
            ObservationFeedProjection.detailFor(s, "obs-3")!!.freshness)
    }

    // I: only sanitized public fields exist on the projection surface (structural).

    @Test
    fun `I - detail surface exposes only sanitized public fields, never raw data`() {
        val s = state(rows = listOf(row("obs-1", RadioType.BLE)))
        val fields = ObservationFeedProjection.detailFor(s, "obs-1")!!
        // Every field is from ObservationRowUi; there is no address/BSSID/payload member
        // anywhere on the type — verified structurally by compiling this test against it.
        assertEquals("obs-1", fields.rowId)
        assertEquals(RadioType.BLE, fields.radio)
        assertEquals(0, fields.parseIssueCount)
    }

    // J + K: entering/feed operations dispatch nothing; no retained rows after disposal.

    @Test
    fun `J and K - projection is total and side-effect-free over current state only`() {
        // rowsFor/emptyKind/detailFor are pure functions of the state argument: no
        // session, scanner or permission surface exists on this type (structural).
        val s = state(rows = listOf(row("obs-1", RadioType.BLE)))
        assertEquals(1, ObservationFeedProjection.rowsFor(s, ObservationFeedProjection.FeedFilter.ALL).size)
        assertEquals(0, ObservationFeedProjection.rowsFor(
            s.copy(rows = emptyList()), ObservationFeedProjection.FeedFilter.ALL).size)
    }
}
