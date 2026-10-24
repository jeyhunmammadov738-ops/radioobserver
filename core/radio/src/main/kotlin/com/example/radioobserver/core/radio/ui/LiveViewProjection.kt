package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import kotlin.math.abs
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

    /**
     * One drawn radar symbol: a single observation OR an explicit CLUSTER of dots
     * whose base geometry visually merged (centers closer than
     * [MARKER_COLLISION_MAXR_FRACTION] of the canvas radius — two ~20dp markers on a
     * ~170dp-radius canvas become indistinguishable below ~20dp). Nothing is ever
     * silently dropped: cluster members are counted exactly in [members] and remain
     * individually reachable in the adjoining list. The representative is the FIRST
     * member in the authoritative pipeline order; geometry comes from it.
     */
    data class RadarMarker(
        val members: List<RadarDot>,
        val isCluster: Boolean,
        val count: Int,
        val rowId: String,
        val radio: RadioType,
        val name: String?,
        val bucket: SignalBucket,
        val freshness: FreshnessState,
        val ringIndex: Int,
        val angleDegrees: Double,
        val radiusFraction: Double,
        /** Honest hollow (Eski/cached) styling only when EVERY member is stale. */
        val allMembersStale: Boolean,
    )

    data class RadarProjection(
        val markers: List<RadarMarker>,
        val overflowCount: Int,
        /** True when NO row carries a usable signal category — drives an honest empty-ish state. */
        val allSignalUnavailable: Boolean,
        /** Sanitized rows handed in (post-filter, authoritative order). */
        val totalRows: Int,
    ) {
        /** Accounting invariant: singles + cluster counts + documented overflow == totalRows. */
        val representedRecords: Int get() = markers.sumOf { it.count }
        val clusterCount: Int get() = markers.count { it.isCluster }
        val individualCount: Int get() = markers.count { !it.isCluster }
    }

    /**
     * Visual-merge threshold in canvas-radius fractions. Two markers whose centers
     * are closer than this draw as one to the eye; the projection therefore groups
     * them into an explicit count-bearing cluster instead of an ambiguous overdraw.
     */
    const val MARKER_COLLISION_MAXR_FRACTION = 0.12

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
     * given snapshot and stable across recompositions: a dot's angle and in-band
     * jitter derive ONLY from its rowId (golden-ratio + avalanche mixes below), never
     * from list position, so a NON-colliding dot never moves when other rows appear
     * or disappear. Dots whose base geometry visually merges are grouped into
     * explicit count-bearing clusters — never silently overdrawn or dropped.
     *
     * QA 2026-10-23 root cause fixed here: the previous linear (h % 36000)/100 angle
     * degenerated for the REAL session ordinals ("obs-1", "obs-2", …) because their
     * String hashes differ by exactly 1 — every consecutive id mapped to the SAME
     * 0.01° angle step, stacking 11 records at effectively one point (Xiaomi
     * screenshot: 11 observations, ~3 distinguishable markers). The golden-ratio mix
     * spreads consecutive ordinals ~132.7° apart and the jitter is decorrelated via
     * an independent avalanche constant.
     */
    fun radarProjection(
        state: ObservationUiState,
        filter: ObservationFeedProjection.FeedFilter,
        criteria: LiveFilter.AdvancedCriteria = LiveFilter.AdvancedCriteria(),
        query: String = "",
    ): RadarProjection {
        // WORKSTREAM 02: advanced filtering happens BEFORE marker layout and
        // cluster accounting — the radar invariant counts the SAME eligible set.
        val rows = LiveFilter.rowsFor(state, filter, criteria, query)
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
                angleDegrees = goldenAngleFor(h),
                radiusFraction = radiusFractionFor(ringIndex, h),
            )
        }
        return RadarProjection(
            markers = clusterMergedMarkers(dots),
            overflowCount = (rows.size - displayed.size).coerceAtLeast(0),
            allSignalUnavailable = rows.isNotEmpty() &&
                rows.all { it.signalBucket == SignalBucket.UNAVAILABLE },
            totalRows = rows.size,
        )
    }

    /**
     * Groups visually merged dots into explicit clusters (union-find over pairwise
     * chord distance in canvas-radius units). Deterministic and total: groups keep
     * first-occurrence order; the representative is each group's FIRST member in
     * the authoritative pipeline order.
     */
    internal fun clusterMergedMarkers(dots: List<RadarDot>): List<RadarMarker> {
        if (dots.isEmpty()) return emptyList()
        val parent = IntArray(dots.size) { it }
        fun find(i: Int): Int {
            var r = i
            while (parent[r] != r) { parent[r] = parent[parent[r]]; r = parent[r] }
            return r
        }
        fun union(i: Int, j: Int) {
            val ri = find(i); val rj = find(j)
            if (ri != rj) parent[maxOf(ri, rj)] = minOf(ri, rj)
        }
        for (i in dots.indices) {
            for (j in i + 1 until dots.size) {
                if (chordDistance(dots[i], dots[j]) < MARKER_COLLISION_MAXR_FRACTION) union(i, j)
            }
        }
        val grouped = LinkedHashMap<Int, MutableList<RadarDot>>()
        for (i in dots.indices) grouped.getOrPut(find(i)) { mutableListOf() }.add(dots[i])
        return grouped.values.map { members ->
            val rep = members.first()
            RadarMarker(
                members = members,
                isCluster = members.size > 1,
                count = members.size,
                rowId = rep.rowId,
                radio = rep.radio,
                name = rep.name,
                bucket = rep.bucket,
                freshness = rep.freshness,
                ringIndex = rep.ringIndex,
                angleDegrees = rep.angleDegrees,
                radiusFraction = rep.radiusFraction,
                allMembersStale = members.all { it.freshness == FreshnessState.STALE },
            )
        }
    }

    /** Deterministic chord distance between two dots in canvas-radius units. */
    internal fun chordDistance(a: RadarDot, b: RadarDot): Double {
        val dr = Math.toRadians(abs(a.angleDegrees - b.angleDegrees))
        val r1 = a.radiusFraction
        val r2 = b.radiusFraction
        return Math.sqrt(r1 * r1 + r2 * r2 - 2.0 * r1 * r2 * Math.cos(dr))
    }

    /**
     * Golden-ratio angle mix: consecutive ordinals ("obs-1", "obs-2", …) whose String
     * hashes differ by 1 land ~132.7° apart instead of 0.01° apart. Purely visual.
     */
    internal fun goldenAngleFor(h: Long): Double =
        ((h * 0x9E3779B9L) and 0xFFFFFFFFL) * 360.0 / 4_294_967_296.0

    /**
     * Session-local chronology, newest last-seen first, with a STABLE tie-breaker:
     * equal timestamps order by rowId (deterministic; the pipeline's canonical
     * newest-first order is never mutated, this is a derived read-only view).
     * No first-seen events exist or are fabricated — ordering uses ONLY
     * lastSeenAtEpochMs, exactly as proven by the sanitized model.
     */
    fun timelineProjection(
        state: ObservationUiState,
        filter: ObservationFeedProjection.FeedFilter,
        criteria: LiveFilter.AdvancedCriteria = LiveFilter.AdvancedCriteria(),
        query: String = "",
    ): List<TimelineEntry> =
        LiveFilter.rowsFor(state, filter, criteria, query)
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
    fun sameMembership(
        state: ObservationUiState,
        filter: ObservationFeedProjection.FeedFilter,
        criteria: LiveFilter.AdvancedCriteria = LiveFilter.AdvancedCriteria(),
        query: String = "",
    ): Boolean {
        val rows = LiveFilter.rowsFor(state, filter, criteria, query)
        val radar = radarProjection(state, filter, criteria, query)
        val timeline = timelineProjection(state, filter, criteria, query)
        val radarIds = radar.markers.flatMap { marker -> marker.members.map { it.rowId } }.toSet()
        return rows.map { it.rowId }.toSet() == radarIds &&
            rows.size == radar.representedRecords + radar.overflowCount &&
            rows.map { it.rowId }.toSet() == timeline.map { it.rowId }.toSet()
    }

    // --- deterministic geometry helpers (pure, host-tested) ---

    private fun stableHash(rowId: String): Long {
        // String.hashCode() is specified and stable across JVM versions; mask to a
        // non-negative long so modulo arithmetic below is total.
        return (rowId.hashCode().toLong() and 0xFFFFFFFFL)
    }

    /**
     * Deterministic radius: each ring owns its own band; jitter stays inside the band
     * and is decorrelated from the angle via an independent avalanche constant (the
     * old h/36000-derived jitter was perfectly correlated with the degenerate angle
     * for consecutive ordinals — same screen point for every "obs-N" in one ring).
     */
    internal fun radiusFractionFor(ringIndex: Int, h: Long): Double {
        val bands = RING_ORDER.size
        val band = 1.0 / bands
        val inner = ringIndex * band
        // First 5% of every band is reserved as a visual gap between rings.
        val usable = band * 0.95
        val h2 = (h * 0x85EBCA6BL) and 0xFFFFFFFFL
        val jitter = ((h2 ushr 16) % 1000) / 1000.0
        return (inner + 0.05 * band + usable * jitter).coerceIn(0.0, 1.0)
    }
}
