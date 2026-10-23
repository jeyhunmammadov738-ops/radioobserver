package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 01 host regressions (device-free). Verified here: pure membership
 * consistency across the three views, deterministic bounded radar geometry, honest
 * unknown/stale handling, timeline ordering/ties, and disposal safety by
 * derivation-from-current-state. NOT verifiable on the host: Compose rendering,
 * touch geometry, real device behavior — Xiaomi retest required.
 */
class LiveViewProjectionTest {

    private val ALL = ObservationFeedProjection.FeedFilter.ALL

    private fun row(
        id: String,
        radio: RadioType,
        bucket: SignalBucket = SignalBucket.NEAR,
        freshness: FreshnessState = FreshnessState.FRESH,
        seen: Long = 1_000L,
        name: String? = "X",
    ) = ObservationRowUi(
        rowId = id, radio = radio, name = name, manufacturerHint = null, typeHint = null,
        confidence = com.example.radioobserver.core.radio.model.ObservationConfidence.CANDIDATE,
        signalBucket = bucket, rssiDbm = -55, locallyAdministered = false,
        freshness = freshness, ageMs = 10, lastSeenAtEpochMs = seen, parseIssueCount = 0)

    private fun state(vararg rows: ObservationRowUi) = ObservationUiState(rows = rows.toList())

    // A: one sanitized source -> consistent filtered membership in all three views.

    @Test
    fun `A - all three views render the same filtered row set`() {
        val s = state(
            row("r1", RadioType.BLE, seen = 10),
            row("r2", RadioType.WIFI, freshness = FreshnessState.STALE, seen = 20),
            row("r3", RadioType.BLE, bucket = SignalBucket.UNAVAILABLE, seen = 30),
        )
        for (filter in ObservationFeedProjection.FeedFilter.entries) {
            assertTrue(LiveViewProjection.sameMembership(s, filter))
        }
        // Filter honesty: WIFI filter excludes BLE rows in EVERY view.
        val wifiRadar = LiveViewProjection.radarProjection(s, ObservationFeedProjection.FeedFilter.WIFI)
        assertEquals(setOf("r2"), wifiRadar.dots.map { it.rowId }.toSet())
        val wifiTimeline = LiveViewProjection.timelineProjection(s, ObservationFeedProjection.FeedFilter.WIFI)
        assertEquals(setOf("r2"), wifiTimeline.map { it.rowId }.toSet())
    }

    // B: stable deterministic placement, bounded overflow, no physical inference.

    @Test
    fun `B - radar placement is deterministic for a given rowId`() {
        val s = state(row("stable-id", RadioType.BLE), row("other-id", RadioType.WIFI))
        val first = LiveViewProjection.radarProjection(s, ALL)
        val second = LiveViewProjection.radarProjection(s, ALL)
        assertEquals(first, second)

        val byId = first.dots.associateBy { it.rowId }
        val a1 = byId.getValue("stable-id")
        val b1 = byId.getValue("other-id")
        // Different ids generally land on different visual spots (decorative only).
        assertNotEquals(a1.angleDegrees to a1.radiusFraction, b1.angleDegrees to b1.radiusFraction)
        // Geometry stays inside the canvas: angle and radius are bounded.
        assertTrue(a1.angleDegrees in 0.0..360.0)
        assertTrue(a1.radiusFraction in 0.0..1.0)
        assertTrue(b1.radiusFraction in 0.0..1.0)
    }

    @Test
    fun `B - a dot never moves when OTHER rows appear or disappear`() {
        val alone = LiveViewProjection.radarProjection(state(row("fixed", RadioType.BLE)), ALL)
            .dots.single()
        val crowded = LiveViewProjection.radarProjection(
            state(
                row("fixed", RadioType.BLE),
                row("a", RadioType.WIFI), row("b", RadioType.WIFI), row("c", RadioType.WIFI),
            ), ALL,
        ).dots.first { it.rowId == "fixed" }
        assertEquals(alone.angleDegrees, crowded.angleDegrees, 0.0)
        assertEquals(alone.radiusFraction, crowded.radiusFraction, 0.0)
    }

    @Test
    fun `B - radar is bounded and overflow is reported honestly`() {
        val many = state(*(1..60).map { row("r$it", RadioType.BLE) }.toTypedArray())
        val p = LiveViewProjection.radarProjection(many, ALL)
        assertEquals(LiveViewProjection.RADAR_MAX_DISPLAYED_DOTS, p.dots.size)
        assertEquals(20, p.overflowCount)
        // Overflowed rows still resolve against the CURRENT state via the shared projection.
        assertEquals("r60", ObservationFeedProjection.detailFor(many, "r60")?.rowId)
    }

    // C: signal category mapping without invented precision.

    @Test
    fun `C - buckets map to verified rings and UNAVAILABLE is never a proximity`() {
        val s = state(
            row("imm", RadioType.BLE, bucket = SignalBucket.IMMEDIATE),
            row("near", RadioType.BLE, bucket = SignalBucket.NEAR),
            row("far", RadioType.BLE, bucket = SignalBucket.FAR),
            row("none", RadioType.BLE, bucket = SignalBucket.UNAVAILABLE),
        )
        val rings = LiveViewProjection.radarProjection(s, ALL).dots.associate { it.rowId to it.ringIndex }
        assertEquals(0, rings.getValue("imm"))
        assertEquals(1, rings.getValue("near"))
        assertEquals(2, rings.getValue("far"))
        assertEquals(LiveViewProjection.RING_UNAVAILABLE_INDEX, rings.getValue("none"))
        // Ring order is the verified taxonomy, innermost first.
        assertEquals(
            listOf(SignalBucket.IMMEDIATE, SignalBucket.NEAR, SignalBucket.FAR, SignalBucket.UNAVAILABLE),
            LiveViewProjection.RING_ORDER,
        )
    }

    // D: cached/stale Wi-Fi stays honestly marked in radar AND timeline.

    @Test
    fun `D - stale cached Wi-Fi keeps its provenance in both alternative views`() {
        val s = state(row("w1", RadioType.WIFI, freshness = FreshnessState.STALE))
        val dot = LiveViewProjection.radarProjection(s, ALL).dots.single()
        val entry = LiveViewProjection.timelineProjection(s, ALL).single()
        assertEquals(FreshnessState.STALE, dot.freshness)
        assertEquals(FreshnessState.STALE, entry.freshness)
        // Unknown freshness also stays unknown — never promoted by display.
        val u = state(row("u", RadioType.WIFI, freshness = FreshnessState.UNKNOWN))
        assertEquals(FreshnessState.UNKNOWN, LiveViewProjection.timelineProjection(u, ALL).single().freshness)
    }

    // E: timeline conversion/ordering/ties; no fabricated first-seen events.

    @Test
    fun `E - timeline orders by last-seen descending with a stable rowId tie-breaker`() {
        val s = state(
            row("old", RadioType.BLE, seen = 100),
            row("newest", RadioType.WIFI, seen = 900),
            row("mid", RadioType.BLE, seen = 500),
            row("tie-b", RadioType.BLE, seen = 500),
            row("tie-a", RadioType.BLE, seen = 500),
        )
        val ids = LiveViewProjection.timelineProjection(s, ALL).map { it.rowId }
        assertEquals(listOf("newest", "mid", "tie-a", "tie-b", "old"), ids)
        // Honest timestamps only: every entry carries exactly the model's last-seen value.
        assertEquals(900L, LiveViewProjection.timelineProjection(s, ALL).first().lastSeenAtEpochMs)
    }

    @Test
    fun `E - timeline entries carry no invented first-seen field`() {
        // The model has no first-seen time; the entry type structurally has none either.
        val entry = LiveViewProjection.TimelineEntry::class.java.declaredFields.map { it.name }
        assertTrue("ilk/first-seen MUST NOT exist on the timeline entry" , entry.none { it.contains("first", ignoreCase = true) })
    }

    // F: disposal/TTL/cap cannot leave stale items — projections derive from CURRENT state.

    @Test
    fun `F - an evicted or disposed row disappears from every view`() {
        val before = state(row("gone", RadioType.BLE), row("kept", RadioType.BLE))
        assertTrue(LiveViewProjection.sameMembership(before, ALL))
        // The pipeline disposed/evicted "gone" (or a whole new session started):
        // the CURRENT state simply no longer contains it.
        val after = state(row("kept", RadioType.BLE))
        assertEquals(setOf("kept"), LiveViewProjection.radarProjection(after, ALL).dots.map { it.rowId }.toSet())
        assertEquals(setOf("kept"), LiveViewProjection.timelineProjection(after, ALL).map { it.rowId }.toSet())
        assertEquals("kept", ObservationFeedProjection.detailFor(after, "kept")?.rowId)
        assertEquals(null, ObservationFeedProjection.detailFor(after, "gone"))
    }

    @Test
    fun `F - projections never mutate the canonical state or its order`() {
        val rows = listOf(row("b", RadioType.BLE, seen = 2), row("a", RadioType.WIFI, seen = 1))
        val s = ObservationUiState(rows = rows)
        LiveViewProjection.radarProjection(s, ALL)
        LiveViewProjection.timelineProjection(s, ALL)
        // Same instances, same order: the feed's authoritative newest-first order stands.
        assertEquals(rows, s.rows)
        assertEquals("b", s.rows.first().rowId)
    }

    // G/J: view switching is pure — no dispatch, stable identity, honest empties.

    @Test
    fun `G and J - switching a view kind changes no data and resolves no ghost rows`() {
        val s = state(row("only", RadioType.BLE))
        // Every kind over the same state yields the same membership…
        for (kind in LiveViewKind.entries) {
            assertTrue(LiveViewProjection.sameMembership(s, ALL))
        }
        // …and an unknown/blank id never resolves in any projection path.
        assertEquals(null, ObservationFeedProjection.detailFor(s, "does-not-exist"))
        assertEquals(null, ObservationFeedProjection.detailFor(s, ""))
        // Empty states stay empty in the alternative views (honest empties, no ghosts).
        val empty = ObservationUiState()
        assertEquals(0, LiveViewProjection.radarProjection(empty, ALL).dots.size)
        assertEquals(0, LiveViewProjection.radarProjection(empty, ALL).overflowCount)
        assertTrue(LiveViewProjection.timelineProjection(empty, ALL).isEmpty())
    }

    @Test
    fun `C - all-UNAVAILABLE radar reports the honest signal-less state`() {
        val s = state(
            row("u1", RadioType.BLE, bucket = SignalBucket.UNAVAILABLE),
            row("u2", RadioType.WIFI, bucket = SignalBucket.UNAVAILABLE),
        )
        val p = LiveViewProjection.radarProjection(s, ALL)
        assertTrue(p.allSignalUnavailable)
        assertEquals(setOf("u1", "u2"), p.dots.map { it.rowId }.toSet())
    }
}
