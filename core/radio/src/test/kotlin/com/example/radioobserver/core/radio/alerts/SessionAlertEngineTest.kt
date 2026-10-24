package com.example.radioobserver.core.radio.alerts

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 04 regressions (device-free): controlled IN-APP session alerts.
 * L. defaults OFF · M. requires an active session · N. cached/STALE Wi-Fi can
 * never alert · O. dedup per (rule, observation) + bounded queue + deterministic
 * eviction · P. foreground-loss/expiry reset · Q. same snapshot re-feed emits
 * nothing (recomposition safety) · R. engine dispatches nothing (pure).
 */
class SessionAlertEngineTest {

    private fun row(
        id: String,
        radio: RadioType = RadioType.BLE,
        fresh: FreshnessState = FreshnessState.FRESH,
        bucket: SignalBucket = SignalBucket.NEAR,
        manufacturer: String? = null,
    ) = ObservationRowUi(
        rowId = id, radio = radio, name = null, manufacturerHint = manufacturer,
        typeHint = null, confidence = ObservationConfidence.UNKNOWN,
        signalBucket = bucket, rssiDbm = null, locallyAdministered = false,
        freshness = fresh, ageMs = null, lastSeenAtEpochMs = 0, parseIssueCount = 0,
    )

    private val allTriggers = AlertTrigger.entries.toSet()

    @Test
    fun `L - disabled by default and cold start never re-enables`() {
        val e = SessionAlertEngine()
        assertTrue(!e.enabled)
        assertTrue(e.triggers.isEmpty())
        val out = e.onRows(listOf(row("obs-1")), sessionActive = true)
        assertTrue(out.isEmpty())
        assertTrue(e.alerts().isEmpty())
    }

    @Test
    fun `M - alerts require an active session`() {
        val e = SessionAlertEngine()
        e.setEnabled(true)
        e.setTriggers(setOf(AlertTrigger.NEW_FRESH_BLE))
        val out = e.onRows(listOf(row("obs-1")), sessionActive = false)
        assertTrue(out.isEmpty())
        assertTrue(e.alerts().isEmpty())
        // The inactive pass reset the engine, so no stale alerts exist. When a NEW
        // session becomes active and genuinely emits its own rows, they alert once
        // (fresh session-local semantics; the engine never invents backfill).
        val out2 = e.onRows(listOf(row("obs-1")), sessionActive = true)
        assertEquals(1, out2.size)
        assertEquals(AlertTrigger.NEW_FRESH_BLE, out2[0].trigger)
    }

    @Test
    fun `N - cached STALE Wi-Fi never produces a new-device alert`() {
        val e = SessionAlertEngine()
        e.setEnabled(true)
        e.setTriggers(setOf(AlertTrigger.NEW_FRESH_WIFI, AlertTrigger.NEW_FRESH_BLE))
        val cachedWifi = listOf(
            row("w-1", RadioType.WIFI, FreshnessState.STALE),
            row("w-2", RadioType.WIFI, FreshnessState.UNKNOWN),
        )
        assertTrue(e.onRows(cachedWifi, sessionActive = true).isEmpty())
        // A genuinely FRESH Wi-Fi result does alert.
        val fresh = e.onRows(listOf(row("w-3", RadioType.WIFI, FreshnessState.FRESH)), true)
        assertEquals(listOf(AlertTrigger.NEW_FRESH_WIFI), fresh.map { it.trigger })
        // FRESH BLE alerts its own trigger only.
        val ble = e.onRows(listOf(row("b-1", RadioType.BLE, FreshnessState.FRESH)), true)
        assertEquals(listOf(AlertTrigger.NEW_FRESH_BLE), ble.map { it.trigger })
    }

    @Test
    fun `O - dedup per rule and observation, bounded queue, FIFO eviction`() {
        val e = SessionAlertEngine()
        e.setEnabled(true)
        e.setTriggers(setOf(AlertTrigger.MANUFACTURER_UNKNOWN))
        // One row: exactly one alert.
        val first = e.onRows(listOf(row("obs-1")), true)
        assertEquals(1, first.size)
        // Same row again (recomposition): nothing.
        assertTrue(e.onRows(listOf(row("obs-1")), true).isEmpty())
        // Queue bound: 12 distinct rows -> only the newest 10 stay visible.
        (2..12).forEach { n -> e.onRows(listOf(row("obs-$n")), true) }
        assertEquals(SessionAlertLimits.MAX_ALERTS, e.alerts().size)
        assertEquals("A-0003", e.alerts().first().alertId) // oldest surviving
        assertEquals("A-0012", e.alerts().last().alertId)   // deterministic eviction
        // Two triggers on the SAME row: two distinct alerts (per-rule dedup).
        val e2 = SessionAlertEngine()
        e2.setEnabled(true)
        e2.setTriggers(setOf(AlertTrigger.NEW_FRESH_BLE, AlertTrigger.SIGNAL_NEAR))
        val two = e2.onRows(listOf(row("x-1")), true)
        assertEquals(2, two.size)
        assertTrue(two.map { it.trigger }.containsAll(setOf(AlertTrigger.NEW_FRESH_BLE, AlertTrigger.SIGNAL_NEAR)))
        // ...but repeating the row produces no third alert.
        assertTrue(e2.onRows(listOf(row("x-1")), true).isEmpty())
    }

    @Test
    fun `P - foreground loss and session expiry reset everything`() {
        val e = SessionAlertEngine()
        e.setEnabled(true)
        e.setTriggers(allTriggers)
        e.onRows(listOf(row("obs-1")), true)
        assertTrue(e.alerts().isNotEmpty())
        // Foreground loss / disposal: caller reports it via resetSession.
        e.resetSession()
        assertTrue(e.alerts().isEmpty())
        // The new session re-sees obs-1: with reset memory it is genuinely new
        // again and CAN alert (correct: session-local semantics), sequence restarts.
        val out = e.onRows(listOf(row("obs-1")), true)
        assertEquals("A-0001", out.first().alertId)
        // Disabling clears too and stays off (no auto re-enablement).
        e.setEnabled(false)
        assertTrue(e.alerts().isEmpty())
        assertTrue(!e.enabled)
    }

    @Test
    fun `Q - same snapshot re-feed emits nothing`() {
        val e = SessionAlertEngine()
        e.setEnabled(true)
        e.setTriggers(allTriggers)
        val snapshot = listOf(row("a"), row("b", RadioType.WIFI), row("c"))
        val first = e.onRows(snapshot, true)
        val second = e.onRows(snapshot, true)
        val third = e.onRows(snapshot, true)
        assertTrue(second.isEmpty() && third.isEmpty())
        assertEquals(first.size, e.alerts().size)
    }

    @Test
    fun `R - dismiss removes one alert and dismissAll clears`() {
        val e = SessionAlertEngine()
        e.setEnabled(true)
        e.setTriggers(setOf(AlertTrigger.MANUFACTURER_UNKNOWN))
        e.onRows(listOf(row("obs-1"), row("obs-2")), true)
        assertEquals(2, e.alerts().size)
        e.dismiss("A-0001")
        assertEquals(listOf("A-0002"), e.alerts().map { it.alertId })
        // Dismissing an unknown id is a harmless no-op.
        e.dismiss("A-9999")
        assertEquals(1, e.alerts().size)
        e.dismissAll()
        assertTrue(e.alerts().isEmpty())
    }
}
