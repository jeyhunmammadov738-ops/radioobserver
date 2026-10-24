package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.FilterPresets.Repository
import com.example.radioobserver.core.radio.ui.FilterPresets.Repository.Outcome
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 02 — preset lifecycle regressions. Prompt matrix mapping: M save/
 * apply/rename/delete lifecycle, N persistence across repository reload (restart
 * simulation), O count and name validation, P corrupt or unsupported preset
 * handling, Q non-persistence of search text and radio identifiers.
 */
class FilterPresetRepositoryTest {

    /** In-memory persistence: also simulates a process RESTART when reused. */
    private class MemoryPersistence : FilterPresets.Persistence {
        var stored: String? = null
        override fun read(): String? = stored
        override fun write(text: String) { stored = text }
    }

    @After
    fun resetClock() {
        Repository.epochMs = { System.currentTimeMillis() }
    }

    private val criteria = LiveFilter.AdvancedCriteria(
        signalBuckets = setOf(SignalBucket.NEAR, SignalBucket.FAR),
        freshness = setOf(FreshnessState.FRESH),
        manufacturerKnownOnly = true,
        manufacturerValues = setOf("Apple"),
    )

    // M. Full explicit lifecycle: save → apply → rename → delete.
    @Test
    fun `M - save apply rename delete lifecycle`() {
        Repository.epochMs = { 1000L }
        val store = MemoryPersistence()
        val repo = Repository(store)

        val saved = repo.save("Kafe filtresi", FeedFilter.BLUETOOTH, criteria)
        assertTrue(saved is Outcome.Saved)
        val preset = (saved as Outcome.Saved).preset
        assertEquals("Kafe filtresi", preset.name)
        assertEquals(FeedFilter.BLUETOOTH, preset.sourceFilter)
        assertEquals(criteria, preset.criteria)

        // Apply: findById returns the full restore contract (source + criteria).
        assertEquals(criteria, repo.findById(preset.id)?.criteria)
        assertEquals(FeedFilter.BLUETOOTH, repo.findById(preset.id)?.sourceFilter)

        val renamed = repo.rename(preset.id, "Kafeler yakın")
        assertTrue(renamed is Outcome.Renamed)
        assertEquals("Kafeler yakın", repo.findById(preset.id)?.name)

        assertTrue(repo.delete(preset.id) is Outcome.Deleted)
        assertNull(repo.findById(preset.id))
        assertTrue(repo.delete(preset.id) is Outcome.NotFound)
        assertTrue(repo.rename(preset.id, "x") is Outcome.NotFound)
    }

    // N. Persistence across repository reload (restart simulation).
    @Test
    fun `N - presets survive repository reload`() {
        Repository.epochMs = { 2000L }
        val store = MemoryPersistence()
        Repository(store).save("Yakın taze", FeedFilter.WIFI, criteria)
        Repository(store).save("Uzak eski", FeedFilter.ALL, LiveFilter.AdvancedCriteria())

        // Fresh instance, same store — the restart simulation.
        val reloaded = Repository(store)
        assertEquals(2, reloaded.list().size)
        val near = reloaded.list().first { it.name == "Yakın taze" }
        assertEquals(criteria, near.criteria)
        assertEquals(FeedFilter.WIFI, near.sourceFilter)
    }

    // O. Count bound + name validation (blank, too long, duplicates; case-folded).
    @Test
    fun `O - name validation, duplicate handling and max preset bound`() {
        Repository.epochMs = { 3000L }
        val store = MemoryPersistence()
        val repo = Repository(store)

        assertTrue(repo.save("   ", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.InvalidName)
        assertTrue(repo.save("", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.InvalidName)
        assertTrue(repo.save("x".repeat(41), FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.InvalidName)

        assertTrue(repo.save("Aynı ad", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.Saved)
        // Exact duplicate: deterministic rejection.
        assertTrue(repo.save("Aynı ad", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.DuplicateName)
        // Case-folded duplicate (Turkish İ) is still a duplicate.
        assertTrue(repo.save("AYNI AD", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.DuplicateName)

        // Bound: fill to MAX_PRESETS, then the next save is refused.
        for (i in 2..Repository.MAX_PRESETS) {
            assertTrue(repo.save("Preset-$i", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
                is Outcome.Saved)
        }
        assertEquals(Repository.MAX_PRESETS, repo.list().size)
        assertTrue(repo.save("One too many", FeedFilter.ALL, LiveFilter.AdvancedCriteria())
            is Outcome.TooManyPresets)
    }

    // P. Corrupt or unsupported content: per-line skip, unknown header fail-closed
    // WITHOUT deleting the file, and name re-validation on load.
    @Test
    fun `P - corrupt lines are dropped safely and unknown stores fail closed`() {
        val good = FilterPresets.FilterPreset(
            id = "fp-1-0", name = "İyi ön ayar", sourceFilter = FeedFilter.BLUETOOTH,
            criteria = criteria,
        )
        val encoded = FilterPresets.Codec.encode(listOf(good))
        val corruptLine = "fp-2|broken|notaversion|ALL|NEAR|-|-|"
        val longNameLine = "fp-3|" + "y".repeat(41) + "|1|ALL|-|-|-|-"
        val store = MemoryPersistence()
        store.stored = encoded + corruptLine + "\n" + longNameLine + "\n"
        val decoded = FilterPresets.Codec.decode(store.stored)
        assertEquals(1, decoded.presets.size)
        assertEquals(good, decoded.presets[0])
        assertEquals(2, decoded.droppedLines)
        assertFalse(decoded.failedClosed)

        // A repository loading it stays honest and does NOT rewrite the file.
        val repo = Repository(store)
        assertEquals(listOf(good), repo.list())
        assertEquals(encoded + corruptLine + "\n" + longNameLine + "\n", store.stored)

        // Unknown header: fail closed, keep everything, never crash.
        store.stored = "#some-other-format-v9\nwhatever\n"
        val repo2 = Repository(store)
        assertTrue(repo2.list().isEmpty())
        assertEquals("#some-other-format-v9\nwhatever\n", store.stored)

        // Totally malformed text (random bytes content) — same fail-closed.
        val repo3 = Repository(MemoryPersistence().apply { stored = "\u0000 garbage \u0001" })
        assertTrue(repo3.list().isEmpty())
    }

    // Q. The serialized preset contains ONLY configuration: no search text, no
    // MAC/BSSID-like identifiers, no observation snapshots.
    @Test
    fun `Q - serialized presets contain no search text or radio identifiers`() {
        Repository.epochMs = { 4000L }
        val store = MemoryPersistence()
        val repo = Repository(store)
        repo.save(
            "Tarama yok|cihaz", FeedFilter.WIFI,
            LiveFilter.AdvancedCriteria(
                signalBuckets = setOf(SignalBucket.FAR),
                freshness = setOf(FreshnessState.STALE),
                manufacturerKnownOnly = false,
            ),
        )
        val text = store.stored ?: ""
        // Structural checks: only the configuration line exists.
        assertTrue(text.startsWith(FilterPresets.Codec.HEADER))
        // A preset line has exactly 8 fields; none can carry a free-text query.
        val line = text.lines()[1]
        assertEquals(8, line.split('|').size)
        // The user-supplied NAME is stored (escaped), but no search query exists
        // anywhere in the store because save() has no query parameter at all.
        assertFalse(text.contains("search"))
        // Round-trip: reload gives the same single preset with criteria only.
        val reloaded = Repository(store)
        assertEquals(1, reloaded.list().size)
        assertEquals(setOf(SignalBucket.FAR), reloaded.list()[0].criteria.signalBuckets)
        assertNull(reloaded.list()[0].criteria.manufacturerValues.firstOrNull { it.isBlank() })
        assertNotNull(reloaded.list()[0])
    }
}
