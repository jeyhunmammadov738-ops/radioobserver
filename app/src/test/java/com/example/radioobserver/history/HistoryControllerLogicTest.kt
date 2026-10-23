package com.example.radioobserver.history

import com.example.radioobserver.core.radio.history.HistoryConsent
import com.example.radioobserver.core.radio.history.HistoryLimits
import com.example.radioobserver.core.radio.history.HistoryPersistence
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.history.LiveRowTracker
import com.example.radioobserver.core.radio.history.ObservationHistory
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App-level JVM tests for the optional local history wiring (post-roadmap feature):
 * the controller's building blocks (engine + tracker) behave as documented, the
 * controller itself being Android-typed (Handler/executor) stays untested on JVM —
 * real-device persistence is NOT claimed here.
 */
class HistoryControllerLogicTest {

    private class FakePersistence : HistoryPersistence {
        var text: String? = null
        var writes = 0
        override fun read(): String? = text
        override fun write(text: String) {
            this.text = text
            writes += 1
        }
    }

    private fun row(id: String, radio: RadioType = RadioType.BLE, name: String? = "Tag") =
        ObservationRowUi(
            rowId = id, radio = radio, name = name, manufacturerHint = "Apple",
            typeHint = "iBeacon format", signalBucket = SignalBucket.NEAR, rssiDbm = -55,
            locallyAdministered = false, freshness = FreshnessState.FRESH, ageMs = 5,
            lastSeenAtEpochMs = 10_000, parseIssueCount = 0,
            confidence = ObservationConfidence.CANDIDATE,
        )

    @Test
    fun `the full live-to-history bridge path never starts observation and saves once`() {
        val persistence = FakePersistence()
        val history = ObservationHistory(persistence = persistence, clock = { 1_000L })
        val tracker = LiveRowTracker()

        // Controller flow: initialize -> OFF by default -> live rows produce NO saves.
        history.loadIfNeeded()
        tracker.onConsentChanged(history.consent.recordingEnabled)
        assertTrue(tracker.feed(listOf(row("obs-1"))).isEmpty())
        assertNull(history.record(row("obs-1"))) // not enabled: nothing recorded
        assertEquals(0, persistence.writes) // a fresh empty store needs NO write

        // User enables: rows on screen are seeded (no backfill), new rows save once.
        history.setRecordingEnabled(true)
        tracker.onConsentChanged(true)
        assertTrue(tracker.feed(listOf(row("obs-1"))).isEmpty())
        val fresh = tracker.feed(listOf(row("obs-1"), row("obs-2")))
        assertEquals(listOf("obs-2"), fresh.map { it.rowId })
        val saved = history.record(fresh.single()) ?: error("record must be saved")
        assertEquals(RadioType.BLE, saved.radio)
        assertEquals(10_000L, saved.observedAtEpochMs)
        assertEquals(1, history.count())

        // Clear All + still-live rows: tracker keeps them seen — no resurrection.
        history.clearAll()
        assertTrue(tracker.feed(listOf(row("obs-1"), row("obs-2"))).isEmpty())
        assertEquals(0, history.count())
    }

    @Test
    fun `consent copy stays within the documented policy boundaries`() {
        val consent = HistoryConsent()
        assertFalse(consent.recordingEnabled) // OFF by default
        assertFalse(consent.wifiNamesEnabled) // separate Wi-Fi consent OFF by default
        assertEquals(7, consent.retentionDays) // 7-day default retention
        assertEquals(30, HistoryLimits.MAX_RETENTION_DAYS) // hard maximum
        assertEquals(500, HistoryLimits.MAX_RECORDS) // hard capacity
    }
}
