package com.example.radioobserver.ui.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UX-07 host tests: the review must describe the request honestly, never invent data. */
class ExportReviewerTest {

    @Test
    fun defaultRequestExcludesEverySensitiveField() {
        val review = ExportReviewer.review(
            selectedCount = 12, totalCount = 40,
            includeNames = false, includeAnnotations = false, scopeResolved = true,
        )
        assertEquals(0, review.sensitiveFieldCount)
        assertFalse(review.includesSensitive)
        assertEquals(ExportReview.SummaryKey.EXCLUDED_BY_DEFAULT, review.summaryKey)
    }

    @Test
    fun namesOptInAloneCountsAsOneSensitiveGroup() {
        val review = ExportReviewer.review(
            selectedCount = 5, totalCount = 5,
            includeNames = true, includeAnnotations = false, scopeResolved = true,
        )
        assertEquals(1, review.sensitiveFieldCount)
        assertTrue(review.includesSensitive)
        assertEquals(ExportReview.SummaryKey.INCLUDES_SENSITIVE, review.summaryKey)
    }

    @Test
    fun bothOptInsCountAsTwoGroups() {
        val review = ExportReviewer.review(
            selectedCount = 3, totalCount = 9,
            includeNames = true, includeAnnotations = true, scopeResolved = true,
        )
        assertEquals(2, review.sensitiveFieldCount)
    }

    @Test
    fun unresolvedScopeBlocksAndOutranksEverythingElse() {
        val review = ExportReviewer.review(
            selectedCount = 0, totalCount = 9,
            includeNames = true, includeAnnotations = true, scopeResolved = false,
        )
        assertTrue(review.blocked)
        assertEquals(ExportReview.SummaryKey.SCOPE_UNRESOLVED, review.summaryKey)
    }

    @Test
    fun emptySelectionIsReportedAsNoRecords() {
        val review = ExportReviewer.review(
            selectedCount = 0, totalCount = 25,
            includeNames = false, includeAnnotations = false, scopeResolved = true,
        )
        assertTrue(review.empty)
        assertEquals(ExportReview.SummaryKey.NO_RECORDS, review.summaryKey)
    }

    @Test
    fun countsAreNeverNegativeAndNeverInflated() {
        val review = ExportReviewer.review(
            selectedCount = -4, totalCount = -9,
            includeNames = false, includeAnnotations = false, scopeResolved = true,
        )
        assertEquals(0, review.selectedCount)
        assertEquals(0, review.totalCount)
    }

    @Test
    fun selectionNeverExceedsTheReportedTotal() {
        val review = ExportReviewer.review(
            selectedCount = 40, totalCount = 40,
            includeNames = false, includeAnnotations = false, scopeResolved = true,
        )
        assertTrue(review.selectedCount <= review.totalCount)
    }
}
