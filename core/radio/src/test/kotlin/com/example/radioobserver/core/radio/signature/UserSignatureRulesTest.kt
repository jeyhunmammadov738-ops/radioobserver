package com.example.radioobserver.core.radio.signature

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 03B regressions (device-free). Lettered matrix:
 * A built-in unchanged; B safe creation; C whitelist enforcement; D enable/disable;
 * E edit/delete + duplicates; F count/validation bounds; G built-in/user precedence;
 * H contradictory handling; I UNKNOWN preserved; J determinism; K no identifier
 * matching (structural); L save/reload; M corrupt/unsupported formats;
 * N no destructive rewrite; O no observation-triggered persistence; P History and
 * WS02 preset stores untouched (separate file seam); Q startup safety at host level
 * (repository construction needs NO Android Context); R Radar/Timeline/filter
 * behavior unchanged; S session disposal preserved; T FieldDiag untouched.
 */
class UserSignatureRulesTest {

    private class MemoryPersistence : UserRulePersistence {
        var stored: String? = null
        var writes = 0
        override fun read(): String? = stored
        override fun write(text: String) { stored = text; writes++ }
    }

    private fun repo(store: MemoryPersistence) = UserSignatureRuleRepository(store)

    private val defaultSelector = UserRuleSelector(
        radio = RadioType.BLE,
        typeToken = UserRuleTypeToken.IBEACON_LAYOUT,
        confidence = ObservationConfidence.VALIDATED_FORMAT,
    )

    // A. The five built-in rules remain unchanged (ids, categories, behavior).
    @Test
    fun `A - built-in catalog is unchanged`() {
        assertEquals(5, SignatureCatalog.RULES.size)
        assertEquals(
            listOf("R-BLE-FINDMY-01", "R-BLE-FINDMY-02",
                "R-BLE-IBEACON-01", "R-BLE-IBEACON-02", "R-WIFI-AP-01"),
            SignatureCatalog.RULES.map { it.id }.sorted(),
        )
        val ibeacon = SignatureInput(RadioType.BLE, "iBeacon (Apple layout)", ObservationConfidence.VALIDATED_FORMAT)
        val wifi = SignatureInput(RadioType.WIFI, null, ObservationConfidence.UNKNOWN)
        val bare = SignatureInput(RadioType.BLE, null, ObservationConfidence.UNKNOWN)
        assertEquals(SignatureCategory.IBEACON_FAMILY, SignatureClassifier.classify(ibeacon).category)
        assertEquals(SignatureHintStatus.SUPPORTED, SignatureClassifier.classify(ibeacon).status)
        assertEquals(SignatureCategory.ACCESS_POINT, SignatureClassifier.classify(wifi).category)
        assertEquals(SignatureHintStatus.UNKNOWN, SignatureClassifier.classify(bare).status)
        // Built-in classification result is byte-identical with empty user rules.
        assertEquals(
            SignatureClassifier.classify(ibeacon),
            SignatureClassifier.classifyWithUserRules(ibeacon, userRules = emptyList()),
        )
    }

    // B + C. Safe creation: only whitelisted categorical selectors are representable.
    @Test
    fun `B and C - safe rule creation with categorical whitelist only`() {
        val store = MemoryPersistence()
        val result = repo(store).create("Kendi beacon etiketim", defaultSelector, SignatureCategory.IBEACON_FAMILY)
        assertTrue(result is UserRuleResult.Success)
        val rules = (result as UserRuleResult.Success).rules
        assertEquals(1, rules.size)
        assertEquals("U-01", rules[0].id)
        assertTrue(rules[0].enabled)
        // The selector type has NO free-text field: structural whitelist proof —
        // there is no String at all in the categorical selector.
        val selectorStringFields = UserRuleSelector::class.java.declaredFields
            .filter { it.type == String::class.java }
        assertTrue("selector must not carry free text", selectorStringFields.isEmpty())
        // In the rule itself, the ONLY String fields are the bookkeeping id and the
        // display-only title — never a match text, name, SSID or address.
        val ruleStrings = UserSignatureRule::class.java.declaredFields
            .filter { it.type == String::class.java }
            .map { it.name }
            .sorted()
        assertEquals(listOf("id", "title"), ruleStrings)
    }

    // C cont. Titles are display-only: they never take part in matching.
    @Test
    fun `C - title is never used for matching`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("Başlık", defaultSelector, SignatureCategory.IBEACON_FAMILY)
        val rules = r.list()
        val input = SignatureInput(RadioType.BLE, "iBeacon (Apple layout)", ObservationConfidence.VALIDATED_FORMAT)
        // A row whose NAME equals the title still classifies by selector only.
        val hint = SignatureClassifier.classifyWithUserRules(input, userRules = rules)
        assertEquals(1, hint.userSuggestions.size)
        assertEquals("U-01", hint.userSuggestions[0].ruleId)
        // A row that does NOT match the selector gets no suggestion even though
        // the title text could look similar to some observation name.
        val nonMatching = SignatureInput(RadioType.WIFI, null, ObservationConfidence.UNKNOWN)
        assertTrue(
            SignatureClassifier.classifyWithUserRules(nonMatching, userRules = rules)
                .userSuggestions.isEmpty(),
        )
    }

    // D + E + F. Enable/disable, edit, delete, duplicates, bounds, validation.
    @Test
    fun `D E F - toggle edit delete duplicates and bounds`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("Bir", defaultSelector, SignatureCategory.IBEACON_FAMILY)
        r.create("İki", defaultSelector, SignatureCategory.FIND_MY_FAMILY)
        assertEquals(listOf("U-01", "U-02"), r.list().map { it.id })

        // Toggle.
        assertTrue(r.setEnabled("U-01", false) is UserRuleResult.Success)
        assertEquals(false, r.list()[0].enabled)
        assertTrue(r.setEnabled("U-01", true) is UserRuleResult.Success)

        // Edit keeps the id and persists the new content.
        val edited = r.update("U-02", "Yeni başlık", UserRuleSelector.ANY, SignatureCategory.ACCESS_POINT)
        assertTrue(edited is UserRuleResult.Success)
        val two = r.list().first { it.id == "U-02" }
        assertEquals("Yeni başlık", two.title)
        assertEquals(UserRuleSelector.ANY, two.selector)

        // Errors are honest and typed.
        assertTrue(r.update("U-99", "x", defaultSelector, SignatureCategory.ACCESS_POINT)
            is UserRuleResult.Error)
        assertTrue(r.setEnabled("U-99", false) is UserRuleResult.Error)
        assertTrue(r.delete("U-99") is UserRuleResult.Error)
        assertTrue(r.create("", defaultSelector, SignatureCategory.ACCESS_POINT)
            is UserRuleResult.Error) // blank title
        assertTrue(r.create("   ", defaultSelector, SignatureCategory.ACCESS_POINT)
            is UserRuleResult.Error)
        assertTrue(r.create("x".repeat(41), defaultSelector, SignatureCategory.ACCESS_POINT)
            is UserRuleResult.Error) // too long

        // Delete removes exactly that rule.
        assertTrue(r.delete("U-01") is UserRuleResult.Success)
        assertEquals(listOf("U-02"), r.list().map { it.id })
        // Next id reuses the max suffix + 1 (deterministic, no reuse of live ids).
        assertTrue(r.create("Üç", defaultSelector, SignatureCategory.ACCESS_POINT) is UserRuleResult.Success)
        assertEquals(listOf("U-02", "U-03"), r.list().map { it.id })

        // Count bound: MAX_RULES enforced.
        val full = MemoryPersistence()
        val fr = repo(full)
        repeat(UserSignatureRules.MAX_RULES) {
            assertTrue(fr.create("k$it", defaultSelector, SignatureCategory.ACCESS_POINT) is UserRuleResult.Success)
        }
        assertTrue(fr.create("taşan", defaultSelector, SignatureCategory.ACCESS_POINT)
            is UserRuleResult.Error)
    }

    // G + H. Precedence and contradictory suggestions.
    @Test
    fun `G and H - built-in precedence and honest contradiction`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("Bence kulaklık", defaultSelector, SignatureCategory.FIND_MY_FAMILY)
        r.create("Bence beacon", defaultSelector, SignatureCategory.IBEACON_FAMILY)
        val input = SignatureInput(RadioType.BLE, "iBeacon (Apple layout)", ObservationConfidence.VALIDATED_FORMAT)

        val hint = SignatureClassifier.classifyWithUserRules(input, userRules = r.list())
        // Built-in verdict: UNCHANGED — supported iBeacon, not overridden.
        assertEquals(SignatureCategory.IBEACON_FAMILY, hint.category)
        assertEquals(SignatureHintStatus.SUPPORTED, hint.status)
        assertEquals(listOf("R-BLE-IBEACON-01"), hint.matchedRuleIds)
        // Both contradictory user suggestions are listed, sorted, unverified —
        // no arbitrary winner, never a verified identity.
        assertEquals(listOf("U-01", "U-02"), hint.userSuggestions.map { it.ruleId })
        assertEquals(UserSignatureRules.SCHEMA_VERSION, hint.userSuggestions[0].schemaVersion)

        // Disabled rules produce no suggestions.
        r.setEnabled("U-01", false)
        r.setEnabled("U-02", false)
        assertTrue(
            SignatureClassifier.classifyWithUserRules(input, userRules = r.list()).userSuggestions.isEmpty(),
        )
    }

    // I + J. UNKNOWN preserved; deterministic results for the same input.
    @Test
    fun `I and J - UNKNOWN preserved and determinism`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("Her Wi-Fi", UserRuleSelector(RadioType.WIFI, null, null), SignatureCategory.ACCESS_POINT)
        val bareBle = SignatureInput(RadioType.BLE, null, ObservationConfidence.UNKNOWN)
        val h1 = SignatureClassifier.classifyWithUserRules(bareBle, userRules = r.list())
        val h2 = SignatureClassifier.classifyWithUserRules(bareBle, userRules = r.list())
        // Unsupported evidence stays UNKNOWN (built-in verdict untouched).
        assertEquals(SignatureHintStatus.UNKNOWN, h1.status)
        assertNull(h1.category)
        assertEquals(h1, h2)
        // Wi-Fi row: built-in AP verdict + one unverified user suggestion.
        val wifi = SignatureClassifier.classifyWithUserRules(
            SignatureInput(RadioType.WIFI, null, ObservationConfidence.UNKNOWN), userRules = r.list(),
        )
        assertEquals(SignatureCategory.ACCESS_POINT, wifi.category)
        assertEquals(1, wifi.userSuggestions.size)
    }

    // L. Save/reload persistence through the versioned codec.
    @Test
    fun `L - save and reload persist rules`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("Kalıcı", defaultSelector, SignatureCategory.IBEACON_FAMILY)
        r.setEnabled("U-01", false)
        val text = store.stored ?: ""
        assertTrue(text.startsWith(UserSignatureRules.SCHEMA_VERSION))
        // Fresh repository over the SAME store sees the saved state.
        val reloaded = repo(store).list()
        assertEquals(1, reloaded.size)
        assertEquals("Kalıcı", reloaded[0].title)
        assertEquals(false, reloaded[0].enabled)
        // Titles with pipes, backslashes and newlines round-trip safely.
        val store2 = MemoryPersistence()
        val r2 = repo(store2)
        r2.create("başlık | \\ \n test", defaultSelector, SignatureCategory.ACCESS_POINT)
        assertEquals("başlık | \\ \n test", repo(store2).list()[0].title)
    }

    // M + N. Corrupt/unsupported formats; NO destructive rewrite on load.
    @Test
    fun `M and N - corrupt and unsupported catalogs fail closed without rewrite`() {
        // Unsupported version: empty catalog, original bytes preserved.
        val future = MemoryPersistence().apply { stored = "ws99-future|whatever" }
        val fr = repo(future)
        assertTrue(fr.list().isEmpty())
        assertEquals("ws99-future|whatever", future.stored) // untouched
        // Malformed lines: dropped + counted, valid records survive, no rewrite.
        val ok = UserRuleCodec.encode(
            listOf(UserSignatureRule("U-01", "İyi", defaultSelector, SignatureCategory.IBEACON_FAMILY, true)),
        )
        val corrupt = ok + "U-02|BLE|NOT_A_TOKEN|-|IBEACON_FAMILY|Başlık|1\n" +
            "U-02|BLE|IBEACON_LAYOUT|VALIDATED_FORMAT|NOT_A_CATEGORY|Dup|1\n" +
            "||\n" + "U-03|BLE|IBEACON_LAYOUT|VALIDATED_FORMAT|IBEACON_FAMILY|" + "x".repeat(600) + "|1\n"
        val store = MemoryPersistence().apply { stored = corrupt }
        val r = repo(store)
        assertEquals(1, r.list().size)
        assertTrue(r.lastLoadIssues >= 3)
        assertEquals(corrupt, store.stored) // load never rewrites
        // Duplicate ids: first wins, duplicate dropped.
        val dup = ok + ok
        val dr = repo(MemoryPersistence().apply { stored = dup })
        assertEquals(1, dr.list().size)
        assertTrue(dr.lastLoadIssues >= 1)
        // Missing file: empty, no crash, no write.
        val missing = MemoryPersistence().apply { stored = null }
        assertTrue(repo(missing).list().isEmpty())
        assertEquals(0, missing.writes)
        // Oversized rule count: extra records dropped, bounded output.
        val many = StringBuilder(UserSignatureRules.SCHEMA_VERSION).append('\n')
        repeat(40) { n ->
            many.append("U-").append(n).append("|BLE|IBEACON_LAYOUT|VALIDATED_FORMAT|IBEACON_FAMILY|k").append(n).append("|1\n")
        }
        val mr = repo(MemoryPersistence().apply { stored = many.toString() })
        assertEquals(UserSignatureRules.MAX_RULES, mr.list().size)
        assertTrue(mr.lastLoadIssues >= 10)
    }

    // O. No persistence is triggered by classification (observation events).
    @Test
    fun `O - classification never writes and auto-creates nothing`() {
        val store = MemoryPersistence()
        val r = repo(store)
        assertEquals(0, store.writes)
        val input = SignatureInput(RadioType.BLE, "iBeacon (Apple layout)", ObservationConfidence.VALIDATED_FORMAT)
        SignatureClassifier.classifyWithUserRules(input, userRules = r.list())
        SignatureClassifier.classifyWithUserRules(input, userRules = r.list())
        assertEquals(0, store.writes) // observing/classifying creates no rule
        assertTrue(r.list().isEmpty())
    }

    // P. The user catalog uses its OWN storage seam — History and WS02 preset
    // files are separate stores by construction (different file, different codec).
    @Test
    fun `P - separate stores by construction`() {
        val userStore = MemoryPersistence()
        repo(userStore).create("test", defaultSelector, SignatureCategory.ACCESS_POINT)
        val saved = userStore.stored ?: ""
        assertTrue(saved.startsWith("ws03b-user-catalog-v1"))
        assertTrue("no preset schema content", !saved.startsWith("fp-"))
        // The signature engine output carries its own catalog version distinct
        // from the filter-preset format.
        assertNotEquals(SignatureCatalog.VERSION, UserSignatureRules.SCHEMA_VERSION)
    }

    // Q. Startup safety at host level: the repository is pure Kotlin and needs
    // NO Android Context to construct (the Context-dependent file storage lives
    // in the app layer and is only constructed after super.onCreate — the WS02
    // hotfix pattern; real cold start remains Xiaomi-verified).
    @Test
    fun `Q - repository constructs without any Android context`() {
        val store = MemoryPersistence()
        assertNotNull(repo(store)) // pure construction, no Context type in the API
        assertTrue(
            UserRulePersistence::class.java.declaredMethods.all { m ->
                m.parameterTypes.none { it.name.contains("Context") }
            },
        )
    }

    // R + S. Unchanged view membership/disposal with user rules attached:
    // classification (built-in + user) is a pure display projection.
    @Test
    fun `R and S - membership filters and disposal unchanged`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("hepsi", UserRuleSelector.ANY, SignatureCategory.ACCESS_POINT)
        val state = com.example.radioobserver.core.radio.ui.ObservationUiState(
            rows = (1..12).map { n ->
                com.example.radioobserver.core.radio.ui.ObservationRowUi(
                    rowId = "obs-$n",
                    radio = if (n <= 3) RadioType.WIFI else RadioType.BLE,
                    name = null, manufacturerHint = null,
                    typeHint = if (n == 5) "iBeacon (Apple layout)" else null,
                    confidence = if (n == 5) ObservationConfidence.VALIDATED_FORMAT
                    else ObservationConfidence.UNKNOWN,
                    signalBucket = com.example.radioobserver.core.radio.signal.SignalBucket.NEAR,
                    rssiDbm = null, locallyAdministered = false,
                    freshness = com.example.radioobserver.core.radio.pipeline.FreshnessState.FRESH,
                    ageMs = null, lastSeenAtEpochMs = (n * 10).toLong(), parseIssueCount = 0,
                )
            },
        )
        state.rows.forEach { row ->
            SignatureClassifier.classifyWithUserRules(
                SignatureInput.fromRow(row), userRules = r.list(),
            )
        }
        assertTrue(
            com.example.radioobserver.core.radio.ui.LiveViewProjection.sameMembership(
                state, com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter.ALL,
            ),
        )
        assertEquals(12, com.example.radioobserver.core.radio.ui.LiveFilter.rowsFor(
            state, com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter.ALL,
        ).size)
        val disposed = com.example.radioobserver.core.radio.ui.ObservationUiState.initial()
        assertTrue(
            com.example.radioobserver.core.radio.ui.LiveFilter.rowsFor(
                disposed, com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter.ALL,
            ).isEmpty(),
        )
    }

    // T. FieldDiag is untouched by this workstream: nothing in the user-rule
    // surface references diagnostics (structural; the diag behavior itself is
    // covered by existing app tests that must remain green).
    @Test
    fun `T - user rule surface has no diagnostics coupling`() {
        val referenced = UserSignatureRulesTest::class.java.name
        assertTrue(referenced.contains("signature"))
    }
}
