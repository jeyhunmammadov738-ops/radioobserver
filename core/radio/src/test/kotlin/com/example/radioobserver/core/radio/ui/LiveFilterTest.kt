package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 02 — behavioral regressions for advanced live filtering. The prompt's
 * lettered matrix maps: A default, B sources, C buckets, D freshness+unknown,
 * E manufacturer, F Turkish text, G OR/AND semantics, H determinism/no-mutation,
 * I identical membership across Liste/Radar/Timeline, J radar accounting after
 * filtering, K timeline ordering, L reset, R session disposal with active filter.
 */
class LiveFilterTest {

    private fun row(
        id: String,
        radio: RadioType,
        bucket: SignalBucket = SignalBucket.NEAR,
        freshness: FreshnessState = FreshnessState.FRESH,
        name: String? = null,
        manufacturer: String? = null,
        seen: Long = 100,
    ) = ObservationRowUi(
        rowId = id,
        radio = radio,
        name = name,
        manufacturerHint = manufacturer,
        typeHint = null,
        confidence = com.example.radioobserver.core.radio.model.ObservationConfidence.UNKNOWN,
        signalBucket = bucket,
        rssiDbm = null,
        locallyAdministered = false,
        freshness = freshness,
        ageMs = null,
        lastSeenAtEpochMs = seen,
        parseIssueCount = 0,
    )

    private fun stateOf(vararg rows: ObservationRowUi) = ObservationUiState(rows = rows.toList())

    // A. Default unrestricted filtering: everything visible, in pipeline order.
    @Test
    fun `A - default criteria and blank query keep every row in authoritative order`() {
        val rows = listOf(
            row("a", RadioType.BLE), row("b", RadioType.WIFI), row("c", RadioType.BLE),
        )
        val state = stateOf(*rows.toTypedArray())
        val out = LiveFilter.rowsFor(state, FeedFilter.ALL)
        assertEquals(rows, out)
    }

    // B. Source filters via the existing chips contract.
    @Test
    fun `B - source filters select only the chosen radio`() {
        val state = stateOf(
            row("a", RadioType.BLE), row("b", RadioType.WIFI),
        )
        assertEquals(listOf("a"), LiveFilter.rowsFor(state, FeedFilter.BLUETOOTH).map { it.rowId })
        assertEquals(listOf("b"), LiveFilter.rowsFor(state, FeedFilter.WIFI).map { it.rowId })
        assertEquals(2, LiveFilter.rowsFor(state, FeedFilter.ALL).size)
    }

    // C. Single and multiple signal categories (OR within the category).
    @Test
    fun `C - bucket filtering with single and multi select`() {
        val state = stateOf(
            row("imm", RadioType.BLE, bucket = SignalBucket.IMMEDIATE),
            row("near", RadioType.BLE, bucket = SignalBucket.NEAR),
            row("far", RadioType.BLE, bucket = SignalBucket.FAR),
            row("un", RadioType.BLE, bucket = SignalBucket.UNAVAILABLE),
        )
        val c1 = LiveFilter.AdvancedCriteria(signalBuckets = setOf(SignalBucket.IMMEDIATE))
        assertEquals(listOf("imm"), LiveFilter.rowsFor(state, FeedFilter.ALL, c1).map { it.rowId })
        val c2 = LiveFilter.AdvancedCriteria(
            signalBuckets = setOf(SignalBucket.NEAR, SignalBucket.FAR),
        )
        assertEquals(setOf("near", "far"), LiveFilter.rowsFor(state, FeedFilter.ALL, c2)
            .map { it.rowId }.toSet())
    }

    // D. Freshness: unknown never silently matches known selections.
    @Test
    fun `D - freshness excludes UNKNOWN unless explicitly selected`() {
        val state = stateOf(
            row("f", RadioType.BLE, freshness = FreshnessState.FRESH),
            row("s", RadioType.BLE, freshness = FreshnessState.STALE),
            row("u", RadioType.BLE, freshness = FreshnessState.UNKNOWN),
        )
        val knownOnly = LiveFilter.AdvancedCriteria(
            freshness = setOf(FreshnessState.FRESH, FreshnessState.STALE),
        )
        assertEquals(setOf("f", "s"), LiveFilter.rowsFor(state, FeedFilter.ALL, knownOnly)
            .map { it.rowId }.toSet())
        val withUnknown = LiveFilter.AdvancedCriteria(freshness = setOf(FreshnessState.UNKNOWN))
        assertEquals(listOf("u"), LiveFilter.rowsFor(state, FeedFilter.ALL, withUnknown)
            .map { it.rowId })
    }

    // E. Manufacturer hints: known vs unknown vs specific values.
    @Test
    fun `E - manufacturer known, unknown and specific value matching`() {
        val state = stateOf(
            row("known", RadioType.BLE, manufacturer = "Apple"),
            row("other", RadioType.BLE, manufacturer = "Google"),
            row("none", RadioType.BLE, manufacturer = null),
        )
        val known = LiveFilter.AdvancedCriteria(manufacturerKnownOnly = true)
        assertEquals(setOf("known", "other"), LiveFilter.rowsFor(state, FeedFilter.ALL, known)
            .map { it.rowId }.toSet())
        val unknown = LiveFilter.AdvancedCriteria(manufacturerKnownOnly = false)
        assertEquals(listOf("none"), LiveFilter.rowsFor(state, FeedFilter.ALL, unknown)
            .map { it.rowId })
        val specific = LiveFilter.AdvancedCriteria(manufacturerValues = setOf("apple"))
        assertEquals(listOf("known"), LiveFilter.rowsFor(state, FeedFilter.ALL, specific)
            .map { it.rowId })
    }

    // F. Turkish text search: İ/I/ı fold both ways, sanitized names only.
    @Test
    fun `F - Turkish fold search over sanitized display names only`() {
        val state = stateOf(
            row("i1", RadioType.WIFI, name = "İSPARTA-5G"),
            row("i2", RadioType.WIFI, name = "ısparta misafir"),
            row("i3", RadioType.BLE, name = "Island Beats"),
            row("i4", RadioType.BLE, name = null),
        )
        val q = "isparta"
        assertEquals(setOf("i1", "i2"), LiveFilter.rowsFor(state, FeedFilter.ALL, query = q)
            .map { it.rowId }.toSet())
        // BLE address-like raw data is structurally unreachable: only names are
        // searched; a row WITHOUT a name can never match a non-blank query.
        assertFalse(LiveFilter.textMatches(row("i4", RadioType.BLE), q))
        // Case-fold equivalence both directions.
        assertTrue(LiveFilter.textMatches(row("x", RadioType.BLE, name = "Kafe"), "KAFE"))
        assertTrue(LiveFilter.textMatches(row("x", RadioType.BLE, name = "KAFE"), "kafe"))
    }

    // G. OR within a category, AND across categories.
    @Test
    fun `G - OR within group AND across groups`() {
        val state = stateOf(
            row("bt-fresh", RadioType.BLE, bucket = SignalBucket.NEAR,
                freshness = FreshnessState.FRESH),
            row("bt-stale", RadioType.BLE, bucket = SignalBucket.NEAR,
                freshness = FreshnessState.STALE),
            row("wf-fresh", RadioType.WIFI, bucket = SignalBucket.NEAR,
                freshness = FreshnessState.FRESH),
            row("wf-fresh-far", RadioType.WIFI, bucket = SignalBucket.FAR,
                freshness = FreshnessState.FRESH),
        )
        val criteria = LiveFilter.AdvancedCriteria(
            signalBuckets = setOf(SignalBucket.NEAR, SignalBucket.FAR), // OR inside
            freshness = setOf(FreshnessState.FRESH),                    // AND across
        )
        val out = LiveFilter.rowsFor(state, FeedFilter.ALL, criteria).map { it.rowId }
        // NEAR|FAR OR-group crossed with FRESH AND-group.
        assertEquals(setOf("bt-fresh", "wf-fresh", "wf-fresh-far"), out.toSet())
        assertFalse(out.contains("bt-stale"))
    }

    // H. Deterministic, side-effect-free: inputs never mutated, same input → same output.
    @Test
    fun `H - deterministic result and source list never mutated`() {
        val rows = listOf(
            row("a", RadioType.BLE), row("b", RadioType.WIFI),
        )
        val state = stateOf(*rows.toTypedArray())
        val criteria = LiveFilter.AdvancedCriteria(freshness = setOf(FreshnessState.FRESH))
        val out1 = LiveFilter.rowsFor(state, FeedFilter.ALL, criteria)
        val out2 = LiveFilter.rowsFor(state, FeedFilter.ALL, criteria)
        assertEquals(out1, out2)
        // The authoritative state is untouched: same rows, same freshness values.
        assertEquals(rows, state.rows)
        assertEquals(FreshnessState.FRESH, state.rows[0].freshness)
        assertEquals(2, state.rows.size)
    }

    // I + J. Identical membership across all three views AND radar accounting after
    // advanced filtering (the invariant singles + clusters + overflow == eligible).
    @Test
    fun `I and J - identical membership and radar accounting under advanced filters`() {
        val rows = (1..11).map { n ->
            row(
                "obs-$n",
                if (n <= 2) RadioType.BLE else RadioType.WIFI,
                bucket = if (n % 3 == 0) SignalBucket.FAR else SignalBucket.UNAVAILABLE,
                freshness = if (n % 2 == 0) FreshnessState.STALE else FreshnessState.FRESH,
                name = if (n % 4 == 0) "Cafe-$n" else null,
            )
        }
        val state = stateOf(*rows.toTypedArray())
        val criteria = LiveFilter.AdvancedCriteria(
            signalBuckets = setOf(SignalBucket.FAR),
            freshness = setOf(FreshnessState.FRESH, FreshnessState.STALE),
        )
        val query = ""
        val eligible = LiveFilter.rowsFor(state, FeedFilter.ALL, criteria, query)
        assertTrue(eligible.isNotEmpty())
        // The sameMembership contract now covers the advanced boundary.
        assertTrue(LiveViewProjection.sameMembership(state, FeedFilter.ALL, criteria, query))
        val radar = LiveViewProjection.radarProjection(state, FeedFilter.ALL, criteria, query)
        assertEquals(eligible.size, radar.representedRecords + radar.overflowCount)
        // Membership across views.
        val timeline = LiveViewProjection.timelineProjection(state, FeedFilter.ALL, criteria, query)
        assertEquals(eligible.map { it.rowId }.toSet(), timeline.map { it.rowId }.toSet())
    }

    // K. Timeline ordering after filtering stays the honest last-seen chronology.
    @Test
    fun `K - timeline ordering preserved under filters`() {
        val rows = listOf(
            row("old", RadioType.BLE, freshness = FreshnessState.STALE, seen = 10),
            row("new", RadioType.BLE, freshness = FreshnessState.FRESH, seen = 50),
            row("mid", RadioType.BLE, freshness = FreshnessState.FRESH, seen = 30),
        )
        val state = stateOf(*rows.toTypedArray())
        val criteria = LiveFilter.AdvancedCriteria(freshness = setOf(FreshnessState.FRESH))
        val ids = LiveViewProjection.timelineProjection(state, FeedFilter.ALL, criteria)
            .map { it.rowId }
        // Newest first by REAL last-seen; cached/stale row is filtered out honestly.
        assertEquals(listOf("new", "mid"), ids)
    }

    // L. Reset restores unrestricted: empty criteria + blank query = everything.
    @Test
    fun `L - reset restores unrestricted defaults`() {
        val state = stateOf(
            row("a", RadioType.BLE), row("b", RadioType.WIFI, name = "Cafe"),
        )
        val active = LiveFilter.AdvancedCriteria(
            signalBuckets = setOf(SignalBucket.FAR),
            freshness = setOf(FreshnessState.STALE),
            manufacturerKnownOnly = true,
        )
        assertTrue(LiveFilter.rowsFor(state, FeedFilter.ALL, active, "x").isEmpty())
        // Reset contract: unrestricted criteria + blank query.
        val reset = LiveFilter.AdvancedCriteria()
        assertTrue(reset.isUnrestricted)
        assertEquals(2, LiveFilter.rowsFor(state, FeedFilter.ALL, reset, "").size)
        assertEquals(0, LiveFilter.activeCriteriaCount(reset, ""))
    }

    // R. Session disposal with an ACTIVE filter: the emptied authoritative state
    // empties every filtered view — filters never keep disposed rows alive.
    @Test
    fun `R - disposal with active filter empties all views`() {
        val active = LiveFilter.AdvancedCriteria(freshness = setOf(FreshnessState.FRESH))
        val live = stateOf(
            row("a", RadioType.BLE, freshness = FreshnessState.FRESH, seen = 5),
        )
        assertEquals(1, LiveFilter.rowsFor(live, FeedFilter.ALL, active).size)
        val disposed = ObservationUiState.initial()
        assertTrue(LiveFilter.rowsFor(disposed, FeedFilter.ALL, active).isEmpty())
        assertTrue(LiveViewProjection.sameMembership(disposed, FeedFilter.ALL, active, ""))
    }
}
