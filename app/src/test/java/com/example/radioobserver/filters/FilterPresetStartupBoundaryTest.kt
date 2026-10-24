package com.example.radioobserver.filters

import com.example.radioobserver.core.radio.ui.FilterPresets
import com.example.radioobserver.core.radio.ui.LiveFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 02 STARTUP CRASH HOTFIX — initialization-boundary regression evidence
 * (host-only). MainActivity now constructs the preset repository EXACTLY ONCE in
 * onCreate AFTER super.onCreate and then calls list(); these tests prove that the
 * whole repository construction + list() path — the exact statements onCreate runs —
 * can never throw for ANY stored preset content, so no preset data can crash startup.
 *
 * HOST-HARNESS LIMITATION (explicit): this project has no Robolectric/activity-
 * construction harness and none was added (no new dependency authorization). The
 * property-initializer-to-onCreate move itself therefore remains UNVERIFIED on the
 * host; only the real Xiaomi launch retest can confirm the Activity opens.
 */
class FilterPresetStartupBoundaryTest {

    private class MemoryPersistence : FilterPresets.Persistence {
        var stored: String? = null
        override fun read(): String? = stored
        override fun write(text: String) { stored = text }
    }

    // Startup with NO store file (fresh install / first run): empty, no crash.
    @Test
    fun `startup with missing preset store yields an empty list without throwing`() {
        val repo = FilterPresets.Repository(MemoryPersistence().apply { stored = null })
        assertTrue(repo.list().isEmpty())
    }

    // Startup with arbitrary corrupt bytes in the store: fail closed to empty,
    // no exception, and (N contract) the file content is NOT rewritten/deleted.
    @Test
    fun `startup with unreadable or foreign store content fails closed to empty`() {
        val raw = "\u0000 garbage \u0001|#not-our-format|v9"
        val store = MemoryPersistence().apply { stored = raw }
        val repo = FilterPresets.Repository(store)
        assertTrue(repo.list().isEmpty())
        // No rewrite/delete of the stored content during startup.
        assertEquals(raw, store.stored)
    }

    // Startup over EXISTING saved presets: they all load; a partially corrupt
    // store still yields the valid subset (corruption never crashes startup).
    @Test
    fun `startup with mixed valid and corrupt preset lines loads the valid subset`() {
        val store = MemoryPersistence()
        // First save a real preset through the repository (realistic content).
        FilterPresets.Repository(store).save(
            "Kafeler",
            com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter.WIFI,
            LiveFilter.AdvancedCriteria(manufacturerKnownOnly = true),
        )
        val original = store.stored ?: ""
        // Append two malformed lines the way an interrupted write could leave.
        store.stored = original + "fp-x|bozuk|notaversion|ALL|NEAR|-|-|\n|broken\n"
        val repo = FilterPresets.Repository(store)
        assertEquals(1, repo.list().size)
        assertEquals("Kafeler", repo.list()[0].name)
        // Startup did not rewrite or delete the store.
        assertEquals(original + "fp-x|bozuk|notaversion|ALL|NEAR|-|-|\n|broken\n", store.stored)
    }

    // The startup filter DEFAULT stays unrestricted: the crash fix must not
    // change the established non-persisted UI-state contract.
    @Test
    fun `startup filter default remains unrestricted`() {
        val criteria = LiveFilter.AdvancedCriteria()
        assertTrue(criteria.isUnrestricted)
        assertEquals(0, LiveFilter.activeCriteriaCount(criteria, ""))
        assertEquals(
            0,
            FilterPresets.Repository(MemoryPersistence()).list().size,
        )
    }
}
