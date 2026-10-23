package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * WORKSTREAM 01 (2026-10-23, developer-approved): pure Kotlin/JVM projections for the
 * three switchable live-observation views (Liste / Radar / Zaman tüneli).
 *
 * Contracts — identical to [ObservationFeedProjection]:
 *  - Input is ONLY the CURRENT sanitized [ObservationUiState]. There is NO second
 *    retention, no cache, no snapshot: every projection derives from the state it is
 *    handed, so disposal, TTL eviction, the 200-row pipeline cap and a new session
 *    generation can never leave a stale item in ANY view.
 *  - [ObservationRowUi.rowId] is the pipeline's session-local ordinal (never
 *    address-derived) and is the ONLY identity used here.
 *  - Switching a view, filter or projection is a PURE function call: no scanning,
 *    no permission request, no session action, no history write. Side effects are
 *    impossible by construction.
 *  - HONESTY: the radar is an ABSTRACT signal-category scheme. Rings are the
 *    verified [SignalBucket] taxonomy (IMMEDIATE/NEAR/FAR/UNAVAILABLE), never
 *    meters; angles are deterministic visual decoration derived from rowId and
 *    convey NO direction; the timestamp surfaced by the timeline is the pipeline's
 *    last-seen wall clock ONLY — the model has no first-seen field and none is
 *    invented. STALE cached Wi-Fi keeps its honest provenance in every view.
 */
/** Which live presentation is selected (Liste / Radar / Zaman tüneli). A UI
 * preference only — never persisted, dispatches nothing. */
enum class LiveViewKind { LIST, RADAR, TIMELINE }

object LiveViewProjection {

    /** Radar ring order, innermost first. UNAVAILABLE is its own outermost zone — never a fake proximity. */
    val RING_ORDER: List<SignalBucket> = listOf(
        SignalBucket.IMMEDIATE, SignalBucket.NEAR, SignalBucket.FAR, SignalBucket.UNAVAILABLE,
    )

    const val RING_UNAVAILABLE_INDEX: Int = 3

    /**
     * Bounded schematic canvas: at most [RADAR_MAX_DISPLAYED_DOTS] dots are drawn;
     * anything beyond that is reported HONESTLY in [RadarProjection.overflowCount]
     * (and remains reachable in the adjacent list), never silently dropped.
     */
    const val RADAR_MAX_DISPLAYED_DOTS = 40

    /**
     * One abstract, deterministic dot. [angleDegrees] and [radiusFraction] are
     * VISUAL ONLY (rowId-derived); they are NOT antenna measurements and the UI must
     * present them under the fixed honesty copy.
     */
    data class RadarDot(
        val rowId: String,
        val radio: RadioType,
        val name: String?,
        val bucket: SignalBucket,
        val freshness: FreshnessState,
        val ringIndex: Int,
        val angleDegrees: Double,
        val radiusFraction: Double, // 0.0..1.0 within the whole canvas radius
    )

    data class RadarProjection(
        val dots: List<RadarDot>,
        val overflowCount: Int,
        /** True when NO row carries a usable signal category — drives an honest empty-ish state. */
        val allSignalUnavailable: Boolean,
    )

    /**
     * One timeline entry. ONLY real model fields: the pipeline's last-seen wall clock
     * and its provenance. There is no first-seen field anywhere — [TimelineEntry]
     * deliberately has none.
     */
    data class TimelineEntry(
        val rowId: String,
        val radio: RadioType,
        val name: String?,
        val bucket: SignalBucket,
        val freshness: FreshnessState,
        val lastSeenAtEpochMs: Long,
    )

    /**
     * Abstract radar projection over the CURRENT filtered rows. Deterministic for a
     * given (rowId, ring) pair and stable across recompositions: the angle and the
     * in-band jitter derive ONLY from the rowId's stable String hash, never from
     * list position, so adding/removing OTHER rows never moves an existing dot.
     */
    fun radarProjection(state: ObservationUiState, filter: ObservationFeedProjection.FeedFilter): RadarProjection {
        val rows = ObservationFeedProjection.rowsFor(state, filter)
        val displayed = rows.take(RADAR_MAX_DISPLAYED_DOTS)
        val dots = displayed.map { row ->
            val ringIndex = RING_ORDER.indexOf(row.signalBucket).let {
                if (it < 0) RING_UNAVAILABLE_INDEX else it
            }
            val h = stableHash(row.rowId)
            RadarDot(
                rowId = row.rowId,
                radio = row.radio,
                name = row.name,
                bucket = row.signalBucket,
                freshness = row.freshness,
                ringIndex = ringIndex,
                angleDegrees = (h % 360_00) / 100.0,
                radiusFraction = radiusFractionFor(ringIndex, h),
            )
        }
        return RadarProjection(
            dots = dots,
            overflowCount = (rows.size - displayed.size).coerceAtLeast(0),
            allSignalUnavailable = rows.isNotEmpty() &&
                rows.all { it.signalBucket == SignalBucket.UNAVAILABLE },
        )
    }

    /**
     * Session-local chronology, newest last-seen first, with a STABLE tie-breaker:
     * equal timestamps order by rowId (deterministic; the pipeline's canonical
     * newest-first order is never mutated, this is a derived read-only view).
     * No first-seen events exist or are fabricated — ordering uses ONLY
     * lastSeenAtEpochMs, exactly as proven by the sanitized model.
     */
    fun timelineProjection(state: ObservationUiState, filter: ObservationFeedProjection.FeedFilter): List<TimelineEntry> =
        ObservationFeedProjection.rowsFor(state, filter)
            .map { row ->
                TimelineEntry(
                    rowId = row.rowId,
                    radio = row.radio,
                    name = row.name,
                    bucket = row.signalBucket,
                    freshness = row.freshness,
                    lastSeenAtEpochMs = row.lastSeenAtEpochMs,
                )
            }
            .sortedWith(
                compareByDescending<TimelineEntry> { it.lastSeenAtEpochMs }
                    .thenBy { it.rowId },
            )

    /**
     * Membership truth: all three views render EXACTLY the same sanitized,
     * filtered row set. This function IS that contract, testable on the host.
     */
    fun sameMembership(state: ObservationUiState, filter: ObservationFeedProjection.FeedFilter): Boolean {
        val rows = ObservationFeedProjection.rowsFor(state, filter)
        val radar = radarProjection(state, filter)
        val timeline = timelineProjection(state, filter)
        return rows.map { it.rowId }.toSet() == radar.dots.map { it.rowId }.toSet() &&
            rows.size == radar.dots.size + radar.overflowCount &&
            rows.map { it.rowId }.toSet() == timeline.map { it.rowId }.toSet()
    }

    // --- deterministic geometry helpers (pure, host-tested) ---

    private fun stableHash(rowId: String): Long {
        // String.hashCode() is specified and stable across JVM versions; mask to a
        // non-negative long so modulo arithmetic below is total.
        return (rowId.hashCode().toLong() and 0xFFFFFFFFL)
    }

    /** Deterministic radius: each ring owns its own band; jitter stays inside the band. */
    internal fun radiusFractionFor(ringIndex: Int, h: Long): Double {
        val bands = RING_ORDER.size
        val band = 1.0 / bands
        val inner = ringIndex * band
        // First 5% of every band is reserved as a visual gap between rings.
        val usable = band * 0.95
        val jitter = ((h / 360_00) % 1000) / 1000.0
        return (inner + 0.05 * band + usable * jitter).coerceIn(0.0, 1.0)
    }
}
