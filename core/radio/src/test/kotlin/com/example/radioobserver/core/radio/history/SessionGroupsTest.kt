package com.example.radioobserver.core.radio.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 05 regressions (device-free): named analytical groups.
 * A. create/rename/delete · B. explicit record membership · C/D. legacy ungrouped
 * records stay untouched, no fabricated session association · E. no cross-session
 * device matching (id/name/record refs only, structurally) · F. save/reload ·
 * G. corrupt + unsupported schema fail closed without rewrite · H. non-destructive
 * storage · I. individual deletion orphan cleanup · J. retention orphan cleanup ·
 * K. Clear All consistency · L. annotations/favorites untouched (separate stores).
 */
class SessionGroupsTest {

    private class MemoryPersistence : SessionGroupPersistence {
        var stored: String? = null
        var writes = 0
        override fun read(): String? = stored
        override fun write(text: String) { stored = text; writes++ }
    }

    private val ids = setOf("rec-1", "rec-2", "rec-3")

    private fun repo(store: MemoryPersistence, clock: Long = 0L) =
        SessionGroupRepository(store, clock = { clock })

    @Test
    fun `A - create, rename and delete a named group`() {
        val store = MemoryPersistence()
        val r = repo(store)
        val created = r.create("  Ev analizi  ") // trimmed
        assertTrue(created is GroupResult.Success)
        val g = (created as GroupResult.Success).groups.first()
        assertEquals("Ev analizi", g.name)
        assertTrue(g.id.startsWith("G-")) // app-generated metadata id
        // Rename with honest bounds.
        assertTrue(r.rename(g.id, "Yeni ad") is GroupResult.Success)
        assertTrue(r.rename(g.id, "   ") .let { it is GroupResult.Error && it.reason == GroupError.NAME_BLANK })
        assertTrue(r.rename(g.id, "x".repeat(41)).let { it is GroupResult.Error && it.reason == GroupError.NAME_BLANK })
        assertTrue(r.rename("G-9999", "yok").let { it is GroupResult.Error && it.reason == GroupError.NOT_FOUND })
        // Delete removes ONLY the group metadata.
        assertTrue(r.delete(g.id) is GroupResult.Success)
        assertTrue(r.list().isEmpty())
        // Double delete is an honest error.
        assertTrue(r.delete(g.id).let { it is GroupResult.Error && it.reason == GroupError.NOT_FOUND })
    }

    @Test
    fun `A - group cap at 50 with honest rejection`() {
        val store = MemoryPersistence()
        val r = repo(store)
        repeat(50) { assertTrue(r.create("g$it") is GroupResult.Success) }
        val over = r.create("bir fazla")
        assertTrue(over is GroupResult.Error && over.reason == GroupError.GROUP_LIMIT_REACHED)
        assertEquals(50, r.list().size)
    }

    @Test
    fun `B - explicit membership add and remove`() {
        val store = MemoryPersistence()
        val r = repo(store)
        val g = (r.create("Grup") as GroupResult.Success).groups.first()
        assertTrue(r.addRecord(g.id, "rec-2", ids) is GroupResult.Success)
        assertTrue(r.addRecord(g.id, "rec-1", ids) is GroupResult.Success)
        assertEquals(listOf("rec-1", "rec-2"), r.get(g.id)?.recordIds)
        // Duplicate membership and unsaved record are honest errors.
        assertTrue(r.addRecord(g.id, "rec-2", ids).let { it is GroupResult.Error && it.reason == GroupError.ALREADY_MEMBER })
        assertTrue(r.addRecord(g.id, "rec-99", ids).let { it is GroupResult.Error && it.reason == GroupError.RECORD_NOT_SAVED })
        // Removal never deletes the record itself.
        assertTrue(r.removeRecord(g.id, "rec-1") is GroupResult.Success)
        assertEquals(listOf("rec-2"), r.get(g.id)?.recordIds)
        assertTrue(r.removeRecord(g.id, "rec-1").let { it is GroupResult.Error && it.reason == GroupError.NOT_FOUND })
    }

    @Test
    fun `C and D - legacy records stay ungrouped and no session is fabricated`() {
        // Existing records (rec-1..rec-3) are NOT in any group until the user
        // explicitly says so; nothing is inferred from timestamps.
        val store = MemoryPersistence()
        val r = repo(store)
        assertTrue(r.list().isEmpty())
        // A group never auto-assigns nearby-timestamp records.
        r.create("Seçim grubu")
        val g = r.list().first()
        assertTrue(g.recordIds.isEmpty())
        // And the group model has no timestamp-based membership field at all.
        val fields = AnalyticalGroup::class.java.declaredFields.map { it.name }.sorted()
        assertEquals(listOf("createdAtEpochMs", "id", "name", "recordIds", "updatedAtEpochMs"), fields)
    }

    @Test
    fun `E and H - group store holds only metadata, never radio data`() {
        val store = MemoryPersistence()
        val r = repo(store)
        val g = (r.create("Ad |,\\ karmaşık") as GroupResult.Success).groups.first()
        r.addRecord(g.id, "rec-1", ids)
        val saved = store.stored ?: ""
        assertTrue(saved.startsWith("ws05-groups-v1"))
        // Only ids, name and bookkeeping — no radio values, no addresses, no names
        // of records, no fingerprints (structural: record references are ids).
        assertTrue("rec-1" in saved)
        assertTrue(!saved.contains("MAC"))
        assertTrue(!saved.contains("BSSID"))
    }

    @Test
    fun `F - save and reload across a fresh repository`() {
        val store = MemoryPersistence()
        val r = repo(store, clock = 42L)
        val g = (r.create("Kalıcı grup") as GroupResult.Success).groups.first()
        r.addRecord(g.id, "rec-3", ids)
        r.addRecord(g.id, "rec-1", ids)
        val reloaded = repo(store, clock = 99L)
        val g2 = reloaded.get(g.id)
        assertNotNull(g2)
        assertEquals("Kalıcı grup", g2!!.name)
        assertEquals(listOf("rec-1", "rec-3"), g2.recordIds)
        assertEquals(42L, g2.createdAtEpochMs)
        // Deterministic ids: the next created group takes the next free number.
        val second = (reloaded.create("İkinci") as GroupResult.Success).groups.last()
        assertTrue(second.id != g.id)
    }

    @Test
    fun `G - corrupt and unsupported schema fail closed without rewrite`() {
        // Unsupported future version: empty, original preserved.
        val future = MemoryPersistence().apply { stored = "ws99-future" }
        val fr = repo(future)
        assertTrue(fr.list().isEmpty())
        assertEquals("ws99-future", future.stored)
        assertEquals(0, future.writes)
        // Corrupt lines: dropped + counted; valid groups survive; load never writes.
        val good = GroupCodec.encode(
            listOf(AnalyticalGroup("G-0001", "İyi", listOf("rec-1"), 1L, 2L)),
        )
        val corrupt = good +
            "X-0002|bozuk|rec-2|1|2\n" +   // bad id prefix
            "G-0003||-|1|2\n" +            // blank name
            "G-0004|ad|-|x|2\n" +          // bad timestamp
            "G-0001|dup|-|1|2\n" +         // duplicate id
            "G-0004|ad|a,a|-|2\n" +        // duplicate record refs
            "G-0005|" + "x".repeat(45) + "|-|1|2\n" // oversized name
        val store = MemoryPersistence().apply { stored = corrupt }
        val r = repo(store)
        assertEquals(1, r.list().size)
        assertTrue(r.lastLoadIssues >= 5)
        assertEquals(corrupt, store.stored) // load never rewrites
        // Missing file: empty, no write.
        val missing = MemoryPersistence().apply { stored = null }
        assertTrue(repo(missing).list().isEmpty())
        assertEquals(0, missing.writes)
    }

    @Test
    fun `I and J - record deletion and retention clean dangling references`() {
        val store = MemoryPersistence()
        val r = repo(store)
        val g = (r.create("Grup") as GroupResult.Success).groups.first()
        r.addRecord(g.id, "rec-1", ids)
        r.addRecord(g.id, "rec-2", ids)
        // rec-2 individually deleted: dangling reference dropped, count returned.
        assertEquals(1, r.syncWithRecords(setOf("rec-1", "rec-3")))
        assertEquals(listOf("rec-1"), r.get(g.id)?.recordIds)
        // No-op sync writes nothing.
        assertEquals(0, r.syncWithRecords(setOf("rec-1", "rec-3")))
        // Retention sweeps rec-1: group becomes EMPTY (metadata preserved,
        // honest empty state in UI) and never resurrects records.
        assertEquals(1, r.syncWithRecords(emptySet()))
        assertTrue(r.get(g.id)?.recordIds?.isEmpty() == true)
        assertNotNull(r.get(g.id))
    }

    @Test
    fun `K - Clear All consistency`() {
        val store = MemoryPersistence()
        val r = repo(store)
        val g = (r.create("Tümü") as GroupResult.Success).groups.first()
        r.addRecord(g.id, "rec-1", ids)
        // Clear All History: every reference dangles and is dropped; groups remain
        // as named (possibly empty) metadata; records stay deleted.
        assertEquals(1, r.syncWithRecords(emptySet()))
        assertTrue(r.list().isNotEmpty())
        assertTrue(r.list().first().recordIds.isEmpty())
        // Re-adding a deleted record is impossible (RECORD_NOT_SAVED).
        assertTrue(r.addRecord(g.id, "rec-1", emptySet()).let { it is GroupResult.Error && it.reason == GroupError.RECORD_NOT_SAVED })
    }

    @Test
    fun `L - group store is fully separate from annotations and history`() {
        // Separate file, separate codec version, no field overlap: favoriting or
        // annotating a record (WS04) is untouched by group operations.
        val store = MemoryPersistence()
        val r = repo(store)
        r.create("Grup")
        val saved = store.stored ?: ""
        assertTrue(saved.contains("ws05-groups-v1"))
        assertTrue(!saved.contains("ws04-annotations-v1"))
        assertNull(r.get("rec-1")) // record ids are not group ids
    }
}
