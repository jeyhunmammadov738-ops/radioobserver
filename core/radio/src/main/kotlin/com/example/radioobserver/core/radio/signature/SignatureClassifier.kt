package com.example.radioobserver.core.radio.signature

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.ui.LiveFilter
import com.example.radioobserver.core.radio.ui.ObservationRowUi

/**
 * WORKSTREAM 03A — privacy-safe signature classification (pure JVM).
 *
 * TRUST BOUNDARY (deliberately enforced by the TYPE of [SignatureInput]):
 * the ONLY inputs are already-sanitized fields that exist on [ObservationRowUi]:
 * radio source (BLE/WIFI), the pipeline's own type-hint label and its format
 * confidence. There is deliberately NO field for a name, SSID, manufacturer hint,
 * RSSI, freshness, row id or address — so no caller can accidentally upgrade an
 * arbitrary user-visible name into a device-type claim, and no identifier can
 * enter classification. The engine is a pure function: deterministic, bounded
 * (single linear pass over a finite catalog), no regex, no recursion, no network.
 *
 * Honesty contract: a [SignatureHint] is at best a broad device FAMILY hint derived
 * from a publicly documented advertisement format the project itself validated in
 * Phase 1. It is NEVER proof of a make, model, owner, direction or distance, and
 * never proof that two observations are the same physical device. Classification
 * never persists anything and never generates fingerprints.
 */
object SignatureClassifier {

    /**
     * Classifies one sanitized observation input against a rule catalog.
     * Deterministic for the same input + catalog: matched rules are collected in
     * one bounded pass, ranked by specificity (then by rule id for total order),
     * and the outcome is assembled from the winners.
     */
    fun classify(
        input: SignatureInput,
        catalog: List<SignatureRule> = SignatureCatalog.RULES,
    ): SignatureHint = classifyWithUserRules(input, catalog, emptyList())

    /**
     * WORKSTREAM 03B: classification with the user-defined catalog attached.
     *
     * PRECEDENCE (deterministic, JVM-tested): the built-in evaluation is EXACTLY
     * [classify] — its status, category, rule ids and rationale are never changed,
     * upgraded or suppressed by user rules. Enabled user rules that match the same
     * sanitized input are reported SEPARATELY as unverified suggestions
     * ([SignatureHint.userSuggestions], sorted by rule id); contradictory user
     * matches are all listed (no arbitrary winner, no false verified identity).
     * The whole user pass is one additional bounded scan over a finite (<= 30)
     * rule list using the same exact categorical matching — no regex, no
     * recursion, no network.
     */
    fun classifyWithUserRules(
        input: SignatureInput,
        catalog: List<SignatureRule> = SignatureCatalog.RULES,
        userRules: List<UserSignatureRule>,
    ): SignatureHint {
        val builtIn = classifyInternal(input, catalog)
        if (userRules.isEmpty()) return builtIn
        val suggestions = userRules
            .asSequence()
            .filter { it.enabled }
            .filter { rule -> matchesSelector(rule.selector, input) }
            .sortedBy { it.id }
            .map { rule ->
                SignatureUserSuggestion(
                    ruleId = rule.id,
                    title = rule.title,
                    category = rule.category,
                    schemaVersion = UserSignatureRules.SCHEMA_VERSION,
                )
            }
            .toList()
        return builtIn.copy(userSuggestions = suggestions)
    }

    /** Exact categorical matching for user selectors ('null' selector part = any). */
    private fun matchesSelector(
        selector: UserRuleSelector,
        input: SignatureInput,
    ): Boolean {
        val radioOk = selector.radio == null || selector.radio == input.radio
        val tokenOk = selector.typeToken == null ||
            com.example.radioobserver.core.radio.ui.LiveFilter.fold(selector.typeToken.label) ==
            com.example.radioobserver.core.radio.ui.LiveFilter.fold(input.typeHint.orEmpty())
        val confidenceOk = selector.confidence == null || selector.confidence == input.confidence
        return radioOk && tokenOk && confidenceOk
    }

    private fun classifyInternal(
        input: SignatureInput,
        catalog: List<SignatureRule>,
    ): SignatureHint {
        // Bounded single pass: a finite catalog is scanned exactly once per call.
        val matched = catalog.filter { rule ->
            val radioOk = rule.radio == null || rule.radio == input.radio
            val typeOk = rule.typeHintEquals == null ||
                LiveFilter.fold(rule.typeHintEquals) == LiveFilter.fold(input.typeHint.orEmpty())
            val confidenceOk = rule.requiresConfidence == null ||
                rule.requiresConfidence == input.confidence
            radioOk && typeOk && confidenceOk
        }

        if (matched.isEmpty()) return SignatureHint.unknown(SignatureCatalog.VERSION)

        val winners = matched
            .groupBy { it.category }
            .maxWithOrNull(compareBy { (_, rules) -> rules.maxOf { it.specificity } })

        // Deterministic conflict handling: if several DIFFERENT categories tie on
        // the highest specificity, no family wins. Uncertainty is preserved (the
        // hint stays UNKNOWN) and the conflicting rule ids are reported, sorted.
        val topSpecificity = matched.maxOf { it.specificity }
        val topRules = matched.filter { it.specificity == topSpecificity }
        if (topRules.map { it.category }.distinct().size > 1) {
            return SignatureHint(
                category = null,
                status = SignatureHintStatus.CONFLICT,
                matchedRuleIds = topRules.map { it.id }.sorted(),
                catalogVersion = SignatureCatalog.VERSION,
                rationale = topRules.sortedBy { it.id }.flatMap { it.rationale },
            )
        }

        // Bounded output: duplicate catalog entries (or accidental copies) are
        // deduplicated so matched ids and rationale stay finite and readable.
        val winningRuleIds = topRules.map { it.id }.distinct().sorted()
        // A family is only SUPPORTED when every winning rule rests on a fully
        // validated/structural evidence basis; length-mismatch "possible" rules
        // and candidate layouts stay AMBIGUOUS (never upgraded).
        val strong = topRules.all { it.strong }
        return SignatureHint(
            category = winners?.key,
            status = if (strong) SignatureHintStatus.SUPPORTED else SignatureHintStatus.AMBIGUOUS,
            matchedRuleIds = winningRuleIds,
            catalogVersion = SignatureCatalog.VERSION,
            rationale = topRules.sortedBy { it.id }.flatMap { it.rationale }.distinct(),
        )
    }
}

/**
 * The complete, typed input of the classifier. Every field already exists on the
 * sanitized [ObservationRowUi]; nothing new is collected and nothing raw can be
 * passed (there is no field for it).
 */
data class SignatureInput(
    val radio: RadioType,
    val typeHint: String?,
    val confidence: ObservationConfidence,
) {
    companion object {
        /** Projects the sanitized row into the typed input; nothing else is read. */
        fun fromRow(row: ObservationRowUi) = SignatureInput(
            radio = row.radio,
            typeHint = row.typeHint,
            confidence = row.confidence,
        )
    }
}

/** Broad device family categories the current sanitized data can honestly support. */
enum class SignatureCategory {
    /** Passive Wi-Fi scan-result rows are access-point observations by construction. */
    ACCESS_POINT,

    /** iBeacon-family beacon (published iBeacon layout, validated by the project's own parser). */
    IBEACON_FAMILY,

    /** Advertisement matching the publicly documented Find My separated layout (NOT AirTag proof). */
    FIND_MY_FAMILY,
}

enum class SignatureHintStatus {
    /** Structural/validated evidence for the family hint. */
    SUPPORTED,

    /** Only partial evidence (e.g. right prefix, wrong payload length) — never definitive. */
    AMBIGUOUS,

    /** Conflicting equally-specific rules — no family claim is made. */
    CONFLICT,

    /** No supportable rule: the honest fallback. */
    UNKNOWN,
}

/**
 * An UNVERIFIED, user-defined classification suggestion (WS03B). Displayed with the
 * explicit provenance label "Kullanıcı tanımlı ipucu — doğrulanmamış"; never merged
 * into the built-in verdict, never presented as verified identity.
 */
data class SignatureUserSuggestion(
    val ruleId: String,
    val title: String,
    val category: SignatureCategory,
    val schemaVersion: String,
)

/**
 * The outcome. [category] is null exactly when the status is UNKNOWN or CONFLICT.
 * [matchedRuleIds], [catalogVersion] and [rationale] provide auditable, non-sensitive
 * provenance for UI display ("WHY was this suggested").
 */
data class SignatureHint(
    val category: SignatureCategory?,
    val status: SignatureHintStatus,
    val matchedRuleIds: List<String>,
    val catalogVersion: String,
    val rationale: List<String>,
    /** WS03B: unverified user suggestions, SEPARATE from the built-in verdict. */
    val userSuggestions: List<SignatureUserSuggestion> = emptyList(),
) {
    companion object {
        fun unknown(catalogVersion: String) = SignatureHint(
            category = null,
            status = SignatureHintStatus.UNKNOWN,
            matchedRuleIds = emptyList(),
            catalogVersion = catalogVersion,
            rationale = emptyList(),
        )
    }
}

/**
 * One original, app-owned catalog rule. Rules reference ONLY type-hint labels that
 * Radio Observer's own Phase-1 parser produces from publicly documented formats
 * (iBeacon prefix/length, Find My separated layout). No third-party catalog, OUI
 * table, BT-SIG export, Fast Pair dataset or Fieldwatch asset is copied; matching
 * uses exact (Unicode-folded) label equality, not regex.
 */
data class SignatureRule(
    val id: String,
    val category: SignatureCategory,
    /** Required radio source, or null for any. */
    val radio: RadioType?,
    /** Exact type-hint label this rule applies to (compared Unicode-folded), or null for any. */
    val typeHintEquals: String?,
    /** Required format confidence, or null for any. */
    val requiresConfidence: ObservationConfidence?,
    /** True when the evidence basis is structural or a fully validated format. */
    val strong: Boolean,
    /** Higher specificity wins; ties are broken deterministically (rule id order). */
    val specificity: Int,
    /** Non-sensitive technical provenance for UI display. */
    val rationale: List<String>,
)

/**
 * The built-in catalog. Entirely original, authored for this project, and versioned:
 * the version string ships with every hint so any stored/diplayed provenance stays
 * auditable. The catalog is immutable and intentionally SMALL: every entry must be
 * justifiable from already-sanitized signals alone (see the trust-boundary table in
 * docs/workstream-03a-signature-core.md).
 */
object SignatureCatalog {

    const val VERSION = "radioobserver-signature-catalog-v1"

    val RULES: List<SignatureRule> = listOf(
        SignatureRule(
            id = "R-WIFI-AP-01",
            category = SignatureCategory.ACCESS_POINT,
            radio = RadioType.WIFI,
            typeHintEquals = null,
            requiresConfidence = null,
            strong = true,
            specificity = 1,
            rationale = listOf(
                "Passive Wi-Fi observations come from system scan results; an AP " +
                    "observation is structural by construction (no format guesswork)",
            ),
        ),
        SignatureRule(
            id = "R-BLE-IBEACON-01",
            category = SignatureCategory.IBEACON_FAMILY,
            radio = RadioType.BLE,
            typeHintEquals = "iBeacon (Apple layout)",
            requiresConfidence = ObservationConfidence.VALIDATED_FORMAT,
            strong = true,
            specificity = 3,
            rationale = listOf(
                "Type hint matches the published iBeacon layout with the exact " +
                    "required payload length (validated in Phase 1 by this project)",
            ),
        ),
        SignatureRule(
            id = "R-BLE-IBEACON-02",
            category = SignatureCategory.IBEACON_FAMILY,
            radio = RadioType.BLE,
            typeHintEquals = "Apple iBeacon-like prefix (length mismatch)",
            requiresConfidence = ObservationConfidence.POSSIBLE,
            strong = false,
            specificity = 2,
            rationale = listOf(
                "iBeacon prefix matched but the payload length did not; family is " +
                    "possible only, never treated as a validated iBeacon",
            ),
        ),
        SignatureRule(
            id = "R-BLE-FINDMY-01",
            category = SignatureCategory.FIND_MY_FAMILY,
            radio = RadioType.BLE,
            typeHintEquals = "Find My separated-style advertisement (Apple layout)",
            requiresConfidence = ObservationConfidence.CANDIDATE,
            strong = false,
            specificity = 2,
            rationale = listOf(
                "Type hint matches the publicly documented Find My separated layout; " +
                    "a layout match does NOT prove an AirTag, an owner or that anyone " +
                    "is being followed",
            ),
        ),
        SignatureRule(
            id = "R-BLE-FINDMY-02",
            category = SignatureCategory.FIND_MY_FAMILY,
            radio = RadioType.BLE,
            typeHintEquals = "Apple Find My-like prefix (length mismatch)",
            requiresConfidence = ObservationConfidence.POSSIBLE,
            strong = false,
            specificity = 2,
            rationale = listOf(
                "Find My separated prefix matched but the payload length did not; " +
                    "possible family only, never definitive",
            ),
        ),
    )
}
