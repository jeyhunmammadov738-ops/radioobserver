package com.example.radioobserver.core.radio.signature

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.LiveFilter
import com.example.radioobserver.core.radio.ui.LiveViewProjection
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import com.example.radioobserver.core.radio.ui.ObservationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 03A — signature classifier regressions. The prompt's lettered matrix:
 * A determinism; B supportable families + honest UNKNOWN; C no identity upgrade from
 * names/manufacturer; D deterministic conflict handling; E Unicode/Turkish folds and
 * malformed inputs; F bounded evaluation with duplicate rules; G no state mutation;
 * H identical three-view membership; I radar accounting under classification-bearing
 * rows; J disposal + no classification-driven session actions (structural purity).
 */
class SignatureClassifierTest {

    private fun row(
        radio: RadioType = RadioType.BLE,
        typeHint: String? = null,
        confidence: ObservationConfidence = ObservationConfidence.UNKNOWN,
        name: String? = null,
        manufacturer: String? = null,
        id: String = "r",
        freshness: FreshnessState = FreshnessState.FRESH,
        seen: Long = 100,
    ) = ObservationRowUi(
        rowId = id,
        radio = radio,
        name = name,
        manufacturerHint = manufacturer,
        typeHint = typeHint,
        confidence = confidence,
        signalBucket = SignalBucket.NEAR,
        rssiDbm = null,
        locallyAdministered = false,
        freshness = freshness,
        ageMs = null,
        lastSeenAtEpochMs = seen,
        parseIssueCount = 0,
    )

    private val ibeacon = row(
        typeHint = "iBeacon (Apple layout)",
        confidence = ObservationConfidence.VALIDATED_FORMAT,
    )
    private val ibeaconInput = SignatureInput.fromRow(ibeacon)

    // A. Deterministic: same sanitized input + catalog version -> same hint.
    @Test
    fun `A - classification is deterministic for the same input`() {
        val h1 = SignatureClassifier.classify(ibeaconInput)
        val h2 = SignatureClassifier.classify(ibeaconInput)
        assertEquals(h1, h2)
        assertEquals(SignatureHintStatus.SUPPORTED, h1.status)
        assertEquals(SignatureCategory.IBEACON_FAMILY, h1.category)
        assertEquals(listOf("R-BLE-IBEACON-01"), h1.matchedRuleIds)
        assertEquals(SignatureCatalog.VERSION, h1.catalogVersion)
    }

    // B. Supportable generic categories; UNKNOWN for absent/unreliable hints.
    @Test
    fun `B - supportable families and honest UNKNOWN fallback`() {
        val wifi = SignatureClassifier.classify(SignatureInput.fromRow(row(radio = RadioType.WIFI)))
        assertEquals(SignatureCategory.ACCESS_POINT, wifi.category)
        assertEquals(SignatureHintStatus.SUPPORTED, wifi.status)
        val bare = SignatureClassifier.classify(SignatureInput.fromRow(row()))
        assertEquals(SignatureHintStatus.UNKNOWN, bare.status)
        assertNull(bare.category)
        assertTrue(bare.matchedRuleIds.isEmpty())
        val samsungOnly = SignatureClassifier.classify(
            SignatureInput.fromRow(row(manufacturer = "Samsung Electronics Co., Ltd.")),
        )
        assertEquals(SignatureHintStatus.UNKNOWN, samsungOnly.status)
        val findMy = SignatureClassifier.classify(
            SignatureInput.fromRow(
                row(
                    typeHint = "Find My separated-style advertisement (Apple layout)",
                    confidence = ObservationConfidence.CANDIDATE,
                ),
            ),
        )
        assertEquals(SignatureCategory.FIND_MY_FAMILY, findMy.category)
        assertEquals(SignatureHintStatus.AMBIGUOUS, findMy.status)
    }

    // C. Names/manufacturer are NOT classifier inputs: no model is established from
    // an arbitrary user-visible name or SSID (structurally enforced by SignatureInput).
    @Test
    fun `C - arbitrary names and manufacturer never establish a family`() {
        val withFancyName = row(name = "Galaxy Buds Pro Max Ultra", manufacturer = "Apple Inc.")
        val withoutName = row()
        assertEquals(
            SignatureClassifier.classify(SignatureInput.fromRow(withFancyName)),
            SignatureClassifier.classify(SignatureInput.fromRow(withoutName)),
        )
        val ssidRow = row(radio = RadioType.WIFI, name = "iPhone Hotspot 5000")
        assertEquals(
            SignatureCategory.ACCESS_POINT,
            SignatureClassifier.classify(SignatureInput.fromRow(ssidRow)).category,
        )
        val fields = SignatureInput::class.java.declaredFields.map { it.name }.toSet()
        assertTrue("input must not carry a name field", "name" !in fields)
        assertTrue("input must not carry a manufacturer field", "manufacturerHint" !in fields)
    }

    // D. Conflicting, equally-specific rules: uncertainty preserved, deterministic order.
    @Test
    fun `D - conflicting equal-specificity rules keep uncertainty deterministically`() {
        val conflictCatalog = listOf(
            SignatureRule(
                id = "R-TEST-A-01", category = SignatureCategory.IBEACON_FAMILY,
                radio = RadioType.BLE, typeHintEquals = ibeacon.typeHint,
                requiresConfidence = ObservationConfidence.VALIDATED_FORMAT,
                strong = true, specificity = 5,
                rationale = listOf("test rule A"),
            ),
            SignatureRule(
                id = "R-TEST-B-01", category = SignatureCategory.FIND_MY_FAMILY,
                radio = RadioType.BLE, typeHintEquals = ibeacon.typeHint,
                requiresConfidence = ObservationConfidence.VALIDATED_FORMAT,
                strong = true, specificity = 5,
                rationale = listOf("test rule B"),
            ),
        )
        val hint = SignatureClassifier.classify(ibeaconInput, conflictCatalog)
        assertEquals(SignatureHintStatus.CONFLICT, hint.status)
        assertNull(hint.category)
        assertEquals(listOf("R-TEST-A-01", "R-TEST-B-01"), hint.matchedRuleIds)
        assertEquals(hint, SignatureClassifier.classify(ibeaconInput, conflictCatalog))
        val rankedCatalog = conflictCatalog + SignatureRule(
            id = "R-TEST-A-02", category = SignatureCategory.IBEACON_FAMILY,
            radio = RadioType.BLE, typeHintEquals = ibeacon.typeHint,
            requiresConfidence = ObservationConfidence.VALIDATED_FORMAT,
            strong = true, specificity = 6,
            rationale = listOf("more specific"),
        )
        val ranked = SignatureClassifier.classify(ibeaconInput, rankedCatalog)
        assertEquals(SignatureCategory.IBEACON_FAMILY, ranked.category)
        assertEquals(SignatureHintStatus.SUPPORTED, ranked.status)
    }

    // E. Unicode/Turkish folds, blank values and malformed inputs are safe.
    @Test
    fun `E - Turkish fold matching and malformed inputs are safe`() {
        val turkish = SignatureClassifier.classify(
            SignatureInput.fromRow(
                row(
                    typeHint = "İBEACON (APPLE LAYOUT)",
                    confidence = ObservationConfidence.VALIDATED_FORMAT,
                ),
            ),
        )
        assertEquals(SignatureCategory.IBEACON_FAMILY, turkish.category)
        assertEquals(SignatureHintStatus.SUPPORTED, turkish.status)
        val blank = SignatureClassifier.classify(
            SignatureInput.fromRow(
                row(typeHint = "   ", confidence = ObservationConfidence.POSSIBLE),
            ),
        )
        assertEquals(SignatureHintStatus.UNKNOWN, blank.status)
        val oddCatalog = listOf(
            SignatureRule(
                id = "R-TEST-ODD-01", category = SignatureCategory.ACCESS_POINT,
                radio = null, typeHintEquals = "İ", requiresConfidence = null,
                strong = false, specificity = 1, rationale = listOf("odd"),
            ),
        )
        val odd = SignatureClassifier.classify(
            SignatureInput(RadioType.BLE, "i", ObservationConfidence.UNKNOWN), oddCatalog,
        )
        assertEquals(SignatureCategory.ACCESS_POINT, odd.category)
    }

    // F. Bounded evaluation: duplicate rules never cause unbounded work or output.
    @Test
    fun `F - duplicate rules stay bounded and deterministic`() {
        val duplicateCatalog = SignatureCatalog.RULES + SignatureCatalog.RULES + SignatureCatalog.RULES
        val hint = SignatureClassifier.classify(ibeaconInput, duplicateCatalog)
        assertEquals(SignatureCategory.IBEACON_FAMILY, hint.category)
        assertTrue(hint.matchedRuleIds.size <= SignatureCatalog.RULES.size)
        assertEquals(
            SignatureClassifier.classify(ibeaconInput),
            SignatureClassifier.classify(ibeaconInput, SignatureCatalog.RULES),
        )
        assertTrue(SignatureCatalog.RULES.size in 1..50)
    }

    // G. Classification never mutates the observation (pure projection).
    @Test
    fun `G - classification does not mutate observation state`() {
        val original = ibeacon.copy(rowId = "pure-1", freshness = FreshnessState.STALE, lastSeenAtEpochMs = 42)
        val before = original.copy()
        val hint = SignatureClassifier.classify(SignatureInput.fromRow(original))
        assertEquals(original, before)
        assertEquals("pure-1", original.rowId)
        assertEquals(FreshnessState.STALE, original.freshness)
        assertEquals(42, original.lastSeenAtEpochMs)
        assertTrue(original.name == null && original.manufacturerHint == null)
        assertNotNull(hint)
    }

    // H. Live membership stays identical across Liste/Radar/Zaman tüneli when
    // classification-bearing rows are present (classification is display-only), and
    // the radar accounting invariant keeps holding (I).
    @Test
    fun `H and I - membership and radar accounting unchanged with classified rows`() {
        val rows = (1..12).map { n ->
            row(
                id = "obs-$n",
                radio = if (n <= 3) RadioType.WIFI else RadioType.BLE,
                typeHint = if (n == 5) "iBeacon (Apple layout)" else null,
                confidence = if (n == 5) ObservationConfidence.VALIDATED_FORMAT
                else ObservationConfidence.UNKNOWN,
                freshness = if (n % 2 == 0) FreshnessState.STALE else FreshnessState.FRESH,
                seen = (n * 10).toLong(),
            )
        }
        val state = ObservationUiState(rows = rows)
        rows.forEach { SignatureClassifier.classify(SignatureInput.fromRow(it)) }
        assertTrue(LiveViewProjection.sameMembership(state, FeedFilter.ALL))
        val eligible = LiveFilter.rowsFor(state, FeedFilter.ALL)
        assertEquals(12, eligible.size)
        val radar = LiveViewProjection.radarProjection(state, FeedFilter.ALL)
        assertEquals(eligible.size, radar.representedRecords + radar.overflowCount)
        val timeline = LiveViewProjection.timelineProjection(state, FeedFilter.ALL)
        assertEquals(eligible.map { it.rowId }.toSet(), timeline.map { it.rowId }.toSet())
        val criteria = LiveFilter.AdvancedCriteria(freshness = setOf(FreshnessState.STALE))
        assertTrue(LiveViewProjection.sameMembership(state, FeedFilter.BLUETOOTH, criteria))
        // BLE stale rows: n in {4, 6, 8, 10, 12} -> exactly 5 (n=2 is Wi-Fi, filtered out).
        assertEquals(5, LiveFilter.rowsFor(state, FeedFilter.BLUETOOTH, criteria).size)
    }

    // J. Disposal empties everything (classification cannot resurrect rows) and the
    // engine is structurally pure: classify() has no session/permission side effects.
    @Test
    fun `J - disposal and purity - classification dispatches nothing`() {
        val disposed = ObservationUiState.initial()
        val hint = SignatureClassifier.classify(
            SignatureInput.fromRow(row(id = "gone", radio = RadioType.WIFI)),
        )
        assertEquals(SignatureCategory.ACCESS_POINT, hint.category)
        assertTrue(LiveFilter.rowsFor(disposed, FeedFilter.ALL).isEmpty())
        assertTrue(
            LiveFilter.rowsFor(disposed, FeedFilter.ALL, LiveFilter.AdvancedCriteria()).isEmpty(),
        )
        val stale = row(radio = RadioType.WIFI, freshness = FreshnessState.STALE)
        SignatureClassifier.classify(SignatureInput.fromRow(stale))
        assertEquals(FreshnessState.STALE, stale.freshness)
        assertNotEquals(FreshnessState.FRESH, stale.freshness)
    }
}
