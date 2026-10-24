package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * WORKSTREAM 02 — pure Kotlin/JVM advanced live filtering (zero android.* imports,
 * no side effects). Operates ONLY on the sanitized [ObservationUiState]: filtering is
 * a read-only derivation and NEVER mutates source observations, timestamps,
 * freshness, source identity, session state or History.
 *
 * PRIVACY CONTRACT: no criterion here can become a hidden device-watch rule. Every
 * input is sanitized UI data (safe labels, verified bucket taxonomy, sanitized
 * manufacturer HINTS that are never proof of identity). No raw MAC/BSSID/advertising
 * bytes are ever reachable from this layer, so they can never be searched.
 */
object LiveFilter {

    /**
     * Typed advanced criteria. Every set is a MULTI-SELECT with OR-within-category
     * semantics; different categories combine with AND. An EMPTY selection means
     * UNRESTRICTED for that category (never "match nothing").
     *
     * Source filtering stays in the established Tümü/Bluetooth/Wi-Fi chips
     * ([ObservationFeedProjection.FeedFilter]) — the single, primary source control;
     * presets may restore it but the advanced panel does not duplicate it.
     *
     * [manufacturerValues] matches specific sanitized manufacturer hints
     * (case-folded equality). Hints are NOT verified identity; a value match only
     * groups rows that carry the same hint.
     */
    data class AdvancedCriteria(
        val signalBuckets: Set<SignalBucket> = emptySet(),
        val freshness: Set<FreshnessState> = emptySet(),
        /** null = unrestricted; true = known hints only; false = unknown hints only. */
        val manufacturerKnownOnly: Boolean? = null,
        val manufacturerValues: Set<String> = emptySet(),
    ) {
        val isUnrestricted: Boolean
            get() = signalBuckets.isEmpty() && freshness.isEmpty() &&
                manufacturerKnownOnly == null && manufacturerValues.isEmpty()

        fun activeCriteriaCount(): Int = signalBuckets.size +
            freshness.size +
            (if (manufacturerKnownOnly != null) 1 else 0) +
            manufacturerValues.size
    }

    /**
     * THE single shared projection boundary for ALL THREE live views: the exact same
     * eligible membership feeds Liste, Radar and Zaman tüneli. Order stays the
     * pipeline's authoritative newest-first (filtering never reorders).
     *
     * [query] is the TEMPORARY text search: sanitized display names only, Turkish
     * folded, case-insensitive. It is UI state, never persisted in presets.
     */
    fun rowsFor(
        state: ObservationUiState,
        source: ObservationFeedProjection.FeedFilter,
        criteria: AdvancedCriteria = AdvancedCriteria(),
        query: String = "",
    ): List<ObservationRowUi> =
        ObservationFeedProjection.rowsFor(state, source)
            .filter { matches(it, criteria) }
            .filter { textMatches(it, query) }

    /** AND across categories; OR within a category; unknown never silently matches. */
    fun matches(row: ObservationRowUi, criteria: AdvancedCriteria): Boolean {
        if (criteria.signalBuckets.isNotEmpty() && row.signalBucket !in criteria.signalBuckets) {
            return false
        }
        if (criteria.freshness.isNotEmpty() && row.freshness !in criteria.freshness) {
            return false
        }
        // Manufacturer is ONE category with OR semantics inside it: a row matches
        // when its known/unknown STATUS matches the selected status OR its sanitized
        // hint equals one of the selected values. Values-only criteria therefore
        // never degenerate into "everything" (status alone matches nothing).
        if (criteria.manufacturerKnownOnly != null || criteria.manufacturerValues.isNotEmpty()) {
            val knownMatch = when (criteria.manufacturerKnownOnly) {
                true -> row.manufacturerHint != null
                false -> row.manufacturerHint == null
                null -> false
            }
            val valueMatch = criteria.manufacturerValues.isNotEmpty() &&
                row.manufacturerHint != null &&
                criteria.manufacturerValues.any { fold(it) == fold(row.manufacturerHint) }
            if (!knownMatch && !valueMatch) return false
        }
        return true
    }

    /**
     * Case-insensitive search over sanitized display names ONLY (never raw MAC,
     * BSSID, advertising bytes or any hidden identifier — those never reach this
     * layer). Rows WITHOUT a visible name never match a non-blank query.
     */
    fun textMatches(row: ObservationRowUi, query: String): Boolean {
        if (query.isBlank()) return true
        val name = row.name ?: return false
        return fold(name).contains(fold(query.trim()))
    }

    /**
     * Minimal deterministic Turkish fold: İ/I/ı all fold to 'i' so "ISPARTA",
     * "ısparta" and "İsparta" match each other; everything else uses the standard
     * per-character lowercase. Locale-independent (no Locale parameter — stable on
     * every host and device, JVM-testable).
     */
    fun fold(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    'İ', 'I', 'ı' -> 'i'
                    else -> c.lowercaseChar()
                },
            )
        }
    }

    /** Badge/summary helper: advanced criteria + temporary search, both counted. */
    fun activeCriteriaCount(criteria: AdvancedCriteria, query: String): Int =
        criteria.activeCriteriaCount() + if (query.isNotBlank()) 1 else 0
}
