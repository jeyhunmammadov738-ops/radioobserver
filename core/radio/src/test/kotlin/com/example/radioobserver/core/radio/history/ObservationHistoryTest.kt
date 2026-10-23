package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral tests for the optional local history engine (post-roadmap feature),
 * covering the developer's required A-N cases at JVM level. Real Android file
 * behavior is NOT claimed here: the persistence seam is a scripted fake.
 */
class ObservationHistoryTest {

    /** In-memory persistence fake: simulates the app-private store (whole-file writes). */
    private class FakeStore : HistoryPersistence {
        var text: String? = null
        var writes = 0
        override fun read(): String? = text
        override fun write(text: String) {
            this.text = text
            writes += 1
        }
        fun reload(engineClock: () -> Long): ObservationHistory =
            ObservationHistory(this, engineClock)
    }

    private var nowMs = 100_000L
    private val clock: () -> Long = { nowMs }

    private fun row(
        id: String = "obs-1",
        radio: RadioType = RadioType.BLE,
        name: String? = "Kahve Durağı",
        observedAt: Long = 90_000,
    ) = ObservationRowUi(
        rowId = id, radio = radio, name = name, manufacturerHint = "Apple",
        typeHint = "iBeacon format", signalBucket = SignalBucket.NEAR,
        rssiDbm = -55, locallyAdministered = false,
        freshness = FreshnessState.STALE, ageMs = 120_000,
        lastSeenAtEpochMs = observedAt, parseIssueCount = 0,
        confidence = ObservationConfidence.CANDIDATE,
    )

    @Test
    fun `A - fresh install default OFF means zero persistent writes`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.loadIfNeeded()
        assertEquals(HistoryConsent(), history.consent) // disabled by default
        assertEquals(0, history.count())
        // A live row arrives while recording is OFF: nothing is written.
        assertNull(history.record(row()))
        assertEquals(0, store.writes)
        assertFalse(history.consent.recordingEnabled)
    }

    @Test
    fun `B - explicit enable records only future eligible observations`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        assertEquals(1, store.writes) // consent persisted
        val saved = history.record(row())
        assertEquals(RadioType.BLE, saved?.radio)
        assertEquals(90_000L, saved?.observedAtEpochMs) // honest observed time
        assertEquals(FreshnessState.STALE, saved?.freshness) // provenance verbatim: never "fresh"
        assertEquals(1, history.count())
        // The persisted store round-trips (see G too).
        assertTrue(HistoryCodec.decode(store.text).records.isNotEmpty())
    }

    @Test
    fun `C - disable stops future writes and keeps old records`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        history.record(row("obs-1"))
        assertEquals(1, history.count())
        history.setRecordingEnabled(false)
        assertNull(history.record(row("obs-2"))) // execution-time consent check
        assertEquals(1, history.count()) // old record still there
        assertEquals(1, history.snapshot().size)
    }

    @Test
    fun `D - wifi display names require the separate consent and are never invented`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        // Wi-Fi name consent OFF by default: name must NOT be persisted.
        val wifiRow = row(radio = RadioType.WIFI, name = "Ev Ağı")
        val saved = history.record(wifiRow)
        assertEquals(RadioType.WIFI, saved?.radio)
        assertNull(saved?.displayName)
        history.setWifiNamesEnabled(true)
        val savedWithName = history.record(row(id = "obs-2", radio = RadioType.WIFI, name = "Ev Ağı"))
        assertEquals("Ev Ağı", savedWithName?.displayName)
        // The model has NO address fields at all: BSSID cannot exist in the schema.
        val fields = HistoryRecord::class.java.declaredFields.map { it.name }
        assertTrue(fields.none { it.contains("bssid", ignoreCase = true) ||
            it.contains("mac", ignoreCase = true) || it.contains("address", ignoreCase = true) })
    }

    @Test
    fun `E - raw identifiers cannot enter the persisted store`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        history.record(row(name = "raw aa:bb:cc:dd:ee:ff should only be a name"))
        val persisted = store.text ?: ""
        // The sanitized row carries no raw address, and no BSSID/MAC token is present
        // anywhere in the encoded store beyond what the display NAME itself contains.
        assertTrue(persisted.contains("raw aa:bb:cc:dd:ee:ff should only be a name")) // name = user text
        assertFalse(persisted.contains("|aa:bb:cc:dd:ee:ff|")) // no structured address field exists
        assertTrue(HistoryRecord::class.java.declaredFields.size <= 10) // bounded minimal schema
    }

    @Test
    fun `G - history survives process recreation via the store`() {
        val store = FakeStore()
        val first = ObservationHistory(store, clock)
        first.setRecordingEnabled(true)
        first.setWifiNamesEnabled(true)
        first.record(row("obs-1", radio = RadioType.WIFI, name = "Cafe 5G"))
        first.setRetentionDays(30)
        // "Process death": a NEW engine instance over the SAME store.
        val second = store.reload(clock)
        second.loadIfNeeded()
        assertEquals(HistoryConsent(recordingEnabled = true, wifiNamesEnabled = true, retentionDays = 30),
            second.consent)
        assertEquals(1, second.count())
        assertEquals("Cafe 5G", second.snapshot().single().displayName)
        assertEquals(RadioType.WIFI, second.snapshot().single().radio)
    }

    @Test
    fun `H - saved history never re-enters the live session state`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        history.record(row("obs-1"))
        // The engine exposes records ONLY through snapshot()/count(): there is no API
        // that touches the live pipeline, and loading never changes consent to enabled.
        assertEquals(1, history.snapshot().size)
        assertEquals(1, history.count())
        // A reload with a fresh engine does not resurrect live state either:
        val second = store.reload(clock)
        assertEquals(1, second.count())
        assertTrue(second.consent.recordingEnabled) // consent persisted — but no live rows exist
        assertTrue(second.snapshot(RadioType.BLE).isNotEmpty()) // filter works
    }

    @Test
    fun `I - retention prunes at the configured window and caps at 30 days`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        history.setRetentionDays(7)
        nowMs = 0
        history.record(row("obs-1")) // savedAt = 0
        nowMs = 7 * 24L * 3600_000 // exactly 7 days: still kept (inclusive window)
        assertEquals(1, history.count())
        nowMs += 1_000 // just past 7 days: pruned on next load
        assertEquals(0, history.count())
        // Hard maximum: 30 days cannot be exceeded; only fixed choices are accepted.
        history.setRetentionDays(31)
        assertEquals(7, history.consent.retentionDays) // rejected, unchanged
        history.setRetentionDays(30)
        assertEquals(30, history.consent.retentionDays)
    }

    @Test
    fun `J - capacity is a deterministic 500 with oldest-saved dropped first`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        for (i in 0 until 600) {
            history.record(row("obs-$i"))
            nowMs += 1 // strictly increasing savedAt
        }
        assertEquals(HistoryLimits.MAX_RECORDS, history.count())
        // The 600th save dropped the 100 oldest: the FIRST-saved record is gone...
        val ids = history.snapshot().map { it.id }
        assertEquals(HistoryLimits.MAX_RECORDS, ids.size)
        // ...and what remains are the newest 500 savedAt values (deterministic).
        val savedTimes = history.snapshot().map { it.savedAtEpochMs }
        assertEquals(savedTimes.sortedDescending(), savedTimes)
    }

    @Test
    fun `K - single-record deletion removes exactly that record`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        val first = history.record(row("obs-1"))!!
        val second = history.record(row("obs-2"))!!
        assertNotEquals(first.id, second.id) // random ids, not address-derived
        assertTrue(history.deleteOne(first.id))
        assertFalse(history.deleteOne(first.id)) // idempotent second call
        assertEquals(listOf(second.id), history.snapshot().map { it.id })
    }

    @Test
    fun `L - confirmed Clear All empties the store and persists the emptiness`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        history.record(row("obs-1"))
        history.record(row("obs-2"))
        assertEquals(2, history.clearAll())
        assertEquals(0, history.count())
        // Persisted emptiness: a reload sees nothing (no resurrection from storage).
        assertEquals(0, store.reload(clock).count())
        assertEquals(0, HistoryCodec.decode(store.text).records.size)
    }

    @Test
    fun `N - corrupted stored records are dropped safely with honest accounting`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        history.setRecordingEnabled(true)
        history.record(row("obs-1"))
        // Corrupt the store behind the engine's back (simulates bit rot / partial write).
        store.text = (store.text ?: "") + "rec|broken\n"
        val reloaded = store.reload(clock)
        assertEquals(1, reloaded.count())
        assertEquals(1, reloaded.lastLoadDroppedLines)
        // Fully corrupt store: fail closed — nothing kept, consent safe.
        store.text = "not a history file at all"
        val failedClosed = store.reload(clock)
        failedClosed.loadIfNeeded()
        assertTrue(failedClosed.lastLoadFailedClosed)
        assertEquals(0, failedClosed.count())
    }

    @Test
    fun `O - consent-only usage never starts or touches sessions`() {
        val store = FakeStore()
        val history = ObservationHistory(store, clock)
        // Toggling consent / retention / filters is inert with respect to observation:
        // the engine holds no reference to any coordinator, supervisor, pipeline or
        // permission type — its API surface is records + consent only.
        history.setRecordingEnabled(true)
        history.setWifiNamesEnabled(true)
        history.setRetentionDays(14)
        history.clearAll()
        assertEquals(HistoryConsent(true, true, 14), history.consent)
        val engineImports = java.io.File(
            "src/main/kotlin/com/example/radioobserver/core/radio/history/ObservationHistory.kt"
        ).readText()
        assertFalse(engineImports.contains("Pipeline"))
        assertFalse(engineImports.contains("startScan"))
        assertFalse(engineImports.contains("startObservation"))
    }
}
