package com.example.radioobserver.ui.report

/**
 * UX-07: PURE review of one export request, derived ONLY from values the screen already
 * has. It never generates a report, never touches the file system and never changes the
 * WS06 output: it answers what the user is about to export so the UI can say it honestly.
 *
 * [selectedCount] is the number of distinct saved records the chosen scope covers.
 * [sensitiveFieldCount] counts the opt-in field groups currently INCLUDED (SSID names,
 * user labels/notes). Zero means every sensitive field is excluded, which is the default.
 */
data class ExportReview(
    val selectedCount: Int,
    val totalCount: Int,
    val sensitiveFieldCount: Int,
    val scopeResolved: Boolean,
) {
    /** True only when the scope cannot produce a report at all (e.g. same group twice). */
    val blocked: Boolean get() = !scopeResolved

    /** True when the selection covers no saved records; the export stays user-initiated. */
    val empty: Boolean get() = scopeResolved && selectedCount == 0

    /** True when the user has consciously opted into at least one sensitive field group. */
    val includesSensitive: Boolean get() = sensitiveFieldCount > 0

    /** Honest, non-fabricated sentence keys the UI maps to resources. */
    val summaryKey: SummaryKey
        get() = when {
            blocked -> SummaryKey.SCOPE_UNRESOLVED
            empty -> SummaryKey.NO_RECORDS
            includesSensitive -> SummaryKey.INCLUDES_SENSITIVE
            else -> SummaryKey.EXCLUDED_BY_DEFAULT
        }

    enum class SummaryKey { SCOPE_UNRESOLVED, NO_RECORDS, INCLUDES_SENSITIVE, EXCLUDED_BY_DEFAULT }
}

object ExportReviewer {
    /**
     * [includeNames] and [includeAnnotations] are the two existing opt-ins; each counts as
     * one sensitive field group when enabled.
     */
    fun review(
        selectedCount: Int,
        totalCount: Int,
        includeNames: Boolean,
        includeAnnotations: Boolean,
        scopeResolved: Boolean,
    ): ExportReview = ExportReview(
        selectedCount = selectedCount.coerceAtLeast(0),
        totalCount = totalCount.coerceAtLeast(0),
        sensitiveFieldCount = (if (includeNames) 1 else 0) + (if (includeAnnotations) 1 else 0),
        scopeResolved = scopeResolved,
    )
}
