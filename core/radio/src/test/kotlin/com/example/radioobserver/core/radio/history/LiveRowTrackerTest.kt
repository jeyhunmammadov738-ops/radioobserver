package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-feed → history dedup boundary: no backfill on enable, no per-advertisement
 * duplicates, no resurrection after Clear All, reset on session disposal.
 */
class LiveRowTrackerTest {

    private fun row(id: String) = ObservationRowUi(
        rowId = id, radio = RadioType.BLE, name = "Tag", manufacturerHint = "Apple",
        typeHint = "iBeacon format", signalBucket = SignalBucket.NEAR,
        rssiDbm = -55, locallyAdministered = false,
        freshness = FreshnessState.FRESH,
        ageMs = 10, lastSeenAtEpochMs = 1_000, parseIssueCount = 0,
        confidence = ObservationConfidence.CANDIDATE,
    )

    @Test
    fun `B - enable permits only rows appearing after consent, no backfill`() {
        val t = LiveRowTracker()
        t.onConsentChanged(enabled = false)
        assertTrue(t.feed(listOf(row("obs-1"), row("obs-2"))).isEmpty()) // live before consent
        t.onConsentChanged(enabled = true)
        assertTrue(t.feed(listOf(row("obs-1"), row("obs-2"))).isEmpty()) // seeded: NOT backfilled
        val fresh = t.feed(listOf(row("obs-1"), row("obs-2"), row("obs-3")))
        assertEquals(listOf("obs-3"), fresh.map { it.rowId }) // only the NEW row
    }

    @Test
    fun `F - duplicate callbacks and recomposition-style repeats save once`() {
        val t = LiveRowTracker()
        t.onConsentChanged(enabled = true)
        assertTrue(t.feed(emptyList()).isEmpty())
        assertEquals(1, t.feed(listOf(row("obs-1"))).size)
        assertTrue(t.feed(listOf(row("obs-1"), row("obs-1"))).isEmpty()) // duplicate feed
        assertEquals(1, t.feed(listOf(row("obs-1"), row("obs-2"))).size)
    }

    @Test
    fun `M - no resurrection of live rows after Clear All`() {
        val t = LiveRowTracker()
        t.onConsentChanged(enabled = true)
        assertEquals(1, t.feed(listOf(row("obs-1"))).size) // saved once
        t.keepSeen() // clear-all marker: the live row stays seen
        assertTrue(t.feed(listOf(row("obs-1"))).isEmpty()) // NOT re-recorded after clear
    }

    @Test
    fun `feed disposal resets seen ids so a NEW session records again`() {
        val t = LiveRowTracker()
        t.onConsentChanged(enabled = true)
        assertEquals(1, t.feed(listOf(row("obs-1"))).size)
        assertTrue(t.feed(emptyList()).isEmpty()) // session disposed
        assertEquals(1, t.feed(listOf(row("obs-1"))).size) // new session, same ordinal: new record
    }

    @Test
    fun `disable transitions never seed or record`() {
        val t = LiveRowTracker()
        t.onConsentChanged(enabled = true)
        t.onConsentChanged(enabled = false)
        assertTrue(t.feed(listOf(row("obs-1"))).isEmpty())
        t.onConsentChanged(enabled = true)
        // Enable with rows already on screen: seeded, no backfill
        assertTrue(t.feed(listOf(row("obs-1"))).isEmpty())
    }
}
