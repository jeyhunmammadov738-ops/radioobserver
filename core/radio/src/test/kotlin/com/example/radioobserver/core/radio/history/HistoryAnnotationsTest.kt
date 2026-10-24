package com.example.radioobserver.core.radio.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 04 regressions (device-free): RECORD-BOUND History annotations.
 * D. save/reload · E. favorites filtering (map semantics) · F. create/edit/remove ·
 * G. codec round-trip incl. escaping · H. corrupt + unsupported schema fail closed
 * without destructive rewrite · I. deletion/retention cleanup, orphans cannot
 * recreate records · J/K. record-id binding only, no radio data, no matching.
 */
class HistoryAnnotationsTest {

    private class MemoryPersistence : HistoryAnnotationPersistence {
        var stored: String? = null
        var writes = 0
        override fun read(): String? = stored
        override fun write(text: String) { stored = text; writes++ }
    }

    private val ids = setOf("rec-1", "rec-2", "rec-3")

    private fun repo(store: MemoryPersistence) = HistoryAnnotationRepository(store)

    @Test
    fun `D - save and reload persist annotations`() {
        val store = MemoryPersistence()
        val r = repo(store)
        assertTrue(r.set("rec-1", "Ev cihazı", "Mutfağın yanı", true, ids) is AnnotationResult.Success)
        assertTrue(r.set("rec-2", null, null, true, ids) is AnnotationResult.Success)
        assertTrue(r.set("rec-3", "Sadece etiket", null, false, ids) is AnnotationResult.Success)
        val text = store.stored ?: ""
        assertTrue(text.startsWith("ws04-annotations-v1"))
        // A fresh repository over the SAME store sees the same annotations.
        val reloaded = repo(store)
        assertEquals(true, reloaded.get("rec-1")?.favorite)
        assertEquals("Ev cihazı", reloaded.get("rec-1")?.label)
        assertEquals("Mutfağın yanı", reloaded.get("rec-1")?.note)
        assertEquals(true, reloaded.get("rec-2")?.favorite)
        assertNull(reloaded.get("rec-2")?.label)
        assertEquals("Sadece etiket", reloaded.get("rec-3")?.label)
        assertEquals(false, reloaded.get("rec-3")?.favorite)
    }

    @Test
    fun `E - favorites filtering over the annotation map`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.set("rec-1", null, null, true, ids)
        r.set("rec-2", null, null, false, ids)
        r.set("rec-3", null, null, true, ids)
        val favorites = r.list().filterValues { it.favorite }.keys
        assertEquals(setOf("rec-1", "rec-3"), favorites)
    }

    @Test
    fun `F - create edit remove with honest validation`() {
        val store = MemoryPersistence()
        val r = repo(store)
        // Unknown record id: honest rejection, no write.
        assertTrue(r.set("rec-99", "x", null, true, ids) is AnnotationResult.Error)
        assertEquals(0, store.writes)
        // Bound violations are rejected.
        assertTrue(r.set("rec-1", "e".repeat(41), null, false, ids)
            .let { it is AnnotationResult.Error && it.reason == AnnotationError.TOO_LONG })
        assertTrue(r.set("rec-1", null, "n".repeat(201), false, ids)
            .let { it is AnnotationResult.Error && it.reason == AnnotationError.TOO_LONG })
        // Valid creation then edit.
        assertTrue(r.set("rec-1", "Bir", "Not", true, ids) is AnnotationResult.Success)
        assertTrue(r.set("rec-1", "İki", "Başka not", false, ids) is AnnotationResult.Success)
        val a = r.get("rec-1")
        assertNotNull(a)
        assertEquals("İki", a!!.label)
        assertEquals("Başka not", a.note)
        assertEquals(false, a.favorite)
        // Blank text clears the field (removing label/note without deleting favorite).
        assertTrue(r.set("rec-1", "   ", null, true, ids) is AnnotationResult.Success)
        assertNull(r.get("rec-1")?.label)
        assertEquals(true, r.get("rec-1")?.favorite)
        // Explicit removal deletes the whole annotation.
        assertTrue(r.delete("rec-1") is AnnotationResult.Success)
        assertNull(r.get("rec-1"))
    }

    @Test
    fun `G - escaping round-trip for pipes backslashes and newlines`() {
        val store = MemoryPersistence()
        val r = repo(store)
        val tricky = "etiket | \\ \n son"
        r.set("rec-1", tricky, "not | \\ \n iki", true, ids)
        assertEquals(tricky, repo(store).get("rec-1")?.label)
        assertEquals("not | \\ \n iki", repo(store).get("rec-1")?.note)
    }

    @Test
    fun `H - corrupt and unsupported files fail closed without rewrite`() {
        // Unsupported future version: empty, original bytes preserved.
        val future = MemoryPersistence().apply { stored = "ws99-future|junk" }
        val fr = repo(future)
        assertTrue(fr.list().isEmpty())
        assertEquals("ws99-future|junk", future.stored)
        // Corrupt lines: dropped + counted; valid records survive; no rewrite on load.
        val good = AnnotationCodec.encode(
            mapOf("rec-1" to HistoryAnnotation("rec-1", "İyi", "Not", true)),
        )
        val corrupt = good +
            "rec-2|label|note|maybe\n" +             // bad favorite token
            "||\n" +                                  // malformed
            "rec-2|" + "x".repeat(1500) + "|-|1\n" +  // oversized line
            "rec-1|dup|dup|0\n"                       // duplicate recordId
        val store = MemoryPersistence().apply { stored = corrupt }
        val r = repo(store)
        assertEquals(1, r.list().size)
        assertTrue(r.lastLoadIssues >= 3)
        assertEquals(corrupt, store.stored) // load never rewrites
        // Missing file: empty, no crash, no write.
        val missing = MemoryPersistence().apply { stored = null }
        assertTrue(repo(missing).list().isEmpty())
        assertEquals(0, missing.writes)
    }

    @Test
    fun `I - retention and deletion cleanup, orphans cannot recreate records`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.set("rec-1", null, null, true, ids)
        r.set("rec-2", null, null, true, ids)
        // rec-3 expired via retention sweep: annotations dropped, 0 write on no-op.
        assertEquals(0, r.retainAll(setOf("rec-1", "rec-2", "rec-3")))
        // rec-2 deleted: dropped.
        assertEquals(1, r.retainAll(setOf("rec-1")))
        assertNull(r.get("rec-2"))
        // Clear All: everything dropped.
        assertEquals(1, r.retainAll(emptySet()))
        assertTrue(r.list().isEmpty())
        // Orphaned metadata can NEVER recreate a record: the annotation map is not
        // consulted by History at all (structural), and the store holds only ids.
        val saved = store.stored ?: ""
        assertTrue(!saved.contains("HistoryRecord"))
        assertTrue(saved.lines().first() == "ws04-annotations-v1" || saved.isEmpty())
    }

    @Test
    fun `J and K - record-id binding only, no radio data or matching`() {
        val store = MemoryPersistence()
        val r = repo(store)
        r.set("rec-1", "Etiket", "Not", true, ids)
        val a = r.get("rec-1")!!
        // The annotation carries ONLY record id + user text + flag: no radio type,
        // no address, no name, no fingerprint fields exist on the type.
        val fields = HistoryAnnotation::class.java.declaredFields.map { it.name }.sorted()
        assertEquals(listOf("favorite", "label", "note", "recordId"), fields)
        // No matching API exists either — the repository has no search/match method.
        val methods = HistoryAnnotationRepository::class.java.declaredMethods.map { it.name }
        assertTrue(methods.none { it.contains("match", ignoreCase = true) || it.contains("search", ignoreCase = true) })
        assertEquals("rec-1", a.recordId)
    }
}
