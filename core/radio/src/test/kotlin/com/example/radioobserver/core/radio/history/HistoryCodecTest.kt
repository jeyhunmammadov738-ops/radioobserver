package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Codec round-trip, escaping and corruption behavior (fail-closed to privacy). */
class HistoryCodecTest {

    private fun record(
        id: String = "id-1",
        radio: RadioType = RadioType.BLE,
        name: String? = "Cafe",
        age: Long? = 120_000,
        savedAt: Long = 1_000,
        observedAt: Long = 900,
    ) = HistoryRecord(
        id = id, radio = radio, savedAtEpochMs = savedAt, observedAtEpochMs = observedAt,
        displayName = name, manufacturerHint = "Apple", typeHint = "iBeacon format",
        signalBucket = SignalBucket.NEAR, freshness = FreshnessState.STALE, ageMs = age,
    )

    @Test
    fun `round-trips consent and records including wifi-name consent`() {
        val consent = HistoryConsent(recordingEnabled = true, wifiNamesEnabled = true, retentionDays = 30)
        val recs = listOf(record(), record(id = "id-2", radio = RadioType.WIFI, name = "Home|Net\\2\nX"))
        val decoded = HistoryCodec.decode(HistoryCodec.encode(consent, recs))
        assertEquals(consent, decoded.consent)
        assertEquals(2, decoded.records.size)
        assertEquals(recs[0], decoded.records[0])
        assertEquals(recs[1], decoded.records[1]) // separator/escape hostile name survives verbatim
        assertEquals(0, decoded.droppedLines)
        assertFalse(decoded.failedClosed)
    }

    @Test
    fun `corrupt lines are skipped and counted, never crash`() {
        val good = record()
        val text = HistoryCodec.encode(HistoryConsent(), listOf(good)) +
            "rec|only|three|fields\n" +
            "rec|id|NOT_A_RADIO|1|1|n|m|t|NEAR|STALE|100\n" +
            "rec|id|BLE|not_a_number|1|n|m|t|NEAR|STALE|100\n" +
            "garbage line\n"
        val decoded = HistoryCodec.decode(text)
        assertEquals(1, decoded.records.size)
        assertEquals(good, decoded.records.single())
        assertEquals(4, decoded.droppedLines)
        assertFalse(decoded.failedClosed)
    }

    @Test
    fun `unrecognized store fails closed to privacy`() {
        val decoded = HistoryCodec.decode("some random file\nwith lines")
        assertTrue(decoded.records.isEmpty())
        assertEquals(HistoryConsent(), decoded.consent)
        assertTrue(decoded.failedClosed)
    }

    @Test
    fun `missing consent line keeps safe defaults`() {
        val text = HistoryCodec.HEADER + "\n" + HistoryCodec.encode(HistoryConsent(), listOf(record()))
            .lines().drop(2).joinToString("\n")
        val decoded = HistoryCodec.decode(text)
        assertEquals(HistoryConsent(), decoded.consent)
        assertEquals(1, decoded.records.size)
    }

    @Test
    fun `unknown retention falls back to default and limits are as designed`() {
        val consent = HistoryCodec.decode("consent|true|false|99\n").consent
        assertEquals(HistoryLimits.DEFAULT_RETENTION_DAYS, consent.retentionDays)
        assertEquals(30, HistoryLimits.MAX_RETENTION_DAYS)
        assertEquals(500, HistoryLimits.MAX_RECORDS)
        assertEquals(setOf(7, 14, 30), HistoryLimits.ALLOWED_RETENTION_DAYS)
        assertNotEquals(31, HistoryLimits.MAX_RETENTION_DAYS)
    }

    @Test
    fun `null and blank stores are fresh-install safe`() {
        val decoded = HistoryCodec.decode(null)
        assertEquals(0, decoded.records.size)
        assertFalse(decoded.failedClosed)
        assertEquals(HistoryConsent(), decoded.consent)
    }
}
