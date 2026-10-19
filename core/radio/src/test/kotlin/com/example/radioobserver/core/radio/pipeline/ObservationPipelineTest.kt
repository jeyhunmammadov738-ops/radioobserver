package com.example.radioobserver.core.radio.pipeline

import com.example.radioobserver.core.radio.boundary.ObservationEvent
import com.example.radioobserver.core.radio.boundary.ObservationSessionId
import com.example.radioobserver.core.radio.boundary.SessionEndReason
import com.example.radioobserver.core.radio.model.ManufacturerInfo
import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.DeviceTypeCandidate
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2.7 acceptance tests (device-free JVM, injected clock — no sleeping). These validate
 * pipeline policy: retention, freshness honesty, privacy masking, dedup, and disposal.
 * They do NOT validate real radio reception or Android runtime behavior.
 */
class ObservationPipelineTest {

    private fun bleDevice(
        key: String = "ble:aa:bb:cc:dd:ee:ff",
        address: String? = "aa:bb:cc:dd:ee:ff",
        observedAt: Long = 1_000L,
        rssi: Int? = -55,
        displayName: String? = "Tag",
        confidence: ObservationConfidence = ObservationConfidence.CANDIDATE,
        locallyAdmin: Boolean = false,
        parseIssues: List<String> = emptyList(),
    ) = ObservedRadioDevice(
        observationKey = key,
        radioType = RadioType.BLE,
        observedAtEpochMs = observedAt,
        displayName = displayName,
        address = address,
        manufacturerCandidate = ManufacturerInfo(
            companyId = -1, companyName = "Unknown vendor", evidence = listOf("evidence")),
        deviceTypeCandidate = DeviceTypeCandidate(
            label = "iBeacon format", confidence = confidence, evidence = listOf("layout matched")),
        rssiDbm = rssi,
        locallyAdministeredAddress = locallyAdmin,
        parseIssues = parseIssues,
    )

    private fun wifiDevice(
        key: String = "wifi:11:22:33:44:55:66",
        observedAt: Long = 1_000L,
        resultAgeMs: String? = "500",
        updatedFlag: String? = "true",
        rssi: Int? = -67,
    ) = ObservedRadioDevice(
        observationKey = key,
        radioType = RadioType.WIFI,
        observedAtEpochMs = observedAt,
        displayName = "Cafe",
        address = "11:22:33:44:55:66",
        rssiDbm = rssi,
        sourceFields = buildMap {
            if (resultAgeMs != null) put("resultAgeMs", resultAgeMs)
            if (updatedFlag != null) put("resultsUpdatedFlag", updatedFlag)
        },
    )

    // ---- 1) privacy-safe projection ----

    @Test
    fun `projection masks addresses and never exposes raw identifiers or issue texts`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        val raw = "RareRawDeviceIdentifierZZ"
        pipeline.submit(bleDevice(address = "aa:bb:cc:dd:ee:ff",
            parseIssues = listOf("BSSID '$raw' is malformed")))

        val public = pipeline.publicSnapshot().single()
        val rendered = public.toString()
        assertFalse(rendered.contains("aa:bb:cc:dd:ee:ff")) // raw address unreachable
        assertFalse(rendered.contains(raw))                  // parse-issue texts unreachable
        assertEquals(1, public.parseIssueCount)              // count only
        assertTrue(public.observationId.startsWith("obs-"))  // ordinal, not address-derived
    }

    @Test
    fun `observationIds are per-pipeline ordinals and carry no address-derived material`() {
        val pipeline = ObservationPipeline(clock = { 1_000L })
        val id1 = pipelineStoredId(pipeline, bleDevice())
        val id2 = pipelineStoredId(pipeline, bleDevice(key = "ble:ff:ee:dd:cc:bb:aa", address = "ff:ee:dd:cc:bb:aa"))
        assertNotEquals(id1, id2) // distinct devices get distinct ordinals
        assertTrue(id1.matches(Regex("obs-\\d+")))
        // No address material inside the id (nothing hash- or string-derived from it):
        for (address in listOf("aa:bb:cc:dd:ee:ff", "ff:ee:dd:cc:bb:aa")) {
            assertFalse(id1.contains(address.replace(":", "")))
            assertFalse(id2.contains(address.replace(":", "")))
        }
        // Ordinals are session-local by design: a new pipeline restarts at obs-1, which
        // refers to unrelated data - correlation across sessions via ids is impossible.
        val freshPipeline = ObservationPipeline(clock = { 1_000L })
        assertEquals("obs-1", pipelineStoredId(freshPipeline, bleDevice()))
    }

    private fun pipelineStoredId(p: ObservationPipeline, d: ObservedRadioDevice): String =
        (p.submit(d) as PipelineOutcome.Stored).observationId

    // ---- 2) confidence conservation ----

    @Test
    fun `confidence and type labels are carried verbatim - never promoted`() {
        val pipeline = ObservationPipeline(clock = { 1_500L })
        pipeline.submit(bleDevice(confidence = ObservationConfidence.POSSIBLE))
        val public = pipeline.publicSnapshot().single()
        assertEquals(ObservationConfidence.POSSIBLE, public.confidence)
        assertEquals("iBeacon format", public.deviceTypeLabel)
    }

    @Test
    fun `projection contains no threat or product-identity claims`() {
        val pipeline = ObservationPipeline(clock = { 1_500L })
        pipeline.submit(bleDevice())
        pipeline.submit(wifiDevice())
        val text = pipeline.publicSnapshot().joinToString { it.toString() }
        assertFalse(text.contains("airtag", ignoreCase = true))
        assertFalse(text.contains("smarttag", ignoreCase = true))
        assertFalse(text.contains("tracker", ignoreCase = true))
        assertFalse(text.contains("person", ignoreCase = true))
        assertFalse(text.contains("threat", ignoreCase = true))
    }

    // ---- 3) freshness honesty (clock bases kept separate) ----

    @Test
    fun `updated wifi result with fresh boot-relative age is FRESH with that age`() {
        val pipeline = ObservationPipeline(clock = { 500_000L })
        pipeline.submit(wifiDevice(resultAgeMs = "1500", updatedFlag = "true"))
        val public = pipeline.publicSnapshot().single()
        assertEquals(FreshnessState.FRESH, public.freshness)
        assertEquals(1500L, public.ageMs) // boot-relative age passed through as plain age
    }

    @Test
    fun `cached wifi result updated=false never claims FRESH discovery`() {
        val pipeline = ObservationPipeline(clock = { 500_000L })
        pipeline.submit(wifiDevice(resultAgeMs = "1000", updatedFlag = "false"))
        val public = pipeline.publicSnapshot().single()
        assertEquals(FreshnessState.STALE, public.freshness) // never FRESH, even with small age
    }

    @Test
    fun `wifi result with unknown age is UNKNOWN freshness`() {
        val pipeline = ObservationPipeline(clock = { 500_000L })
        pipeline.submit(wifiDevice(resultAgeMs = "unknown", updatedFlag = "true"))
        val public = pipeline.publicSnapshot().single()
        assertEquals(FreshnessState.UNKNOWN, public.freshness)
        assertNull(public.ageMs)
    }

    @Test
    fun `future and overflow ages are UNKNOWN - never clamped into freshness`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        // BLE receipt timestamp in the future relative to the injected clock:
        pipeline.submit(bleDevice(observedAt = 9_000L, key = "ble:01:02:03:04:05:06", address = "01:02:03:04:05:06"))
        // BLE receipt age beyond the tracked maximum:
        pipeline.submit(bleDevice(observedAt = -50_000_000L, key = "ble:0a:0b:0c:0d:0e:0f", address = "0a:0b:0c:0d:0e:0f"))
        val states = pipeline.publicSnapshot().map { it.freshness }
        assertTrue(states.all { it == FreshnessState.UNKNOWN })
    }

    @Test
    fun `ble receipt within the fresh window is FRESH and older receipt is STALE`() {
        val pipeline = ObservationPipeline(clock = { 100_000L })
        pipeline.submit(bleDevice(observedAt = 80_000L, key = "ble:aa:bb:cc:dd:ee:ff", address = "aa:bb:cc:dd:ee:ff"))
        pipeline.submit(bleDevice(observedAt = 10_000L, key = "ble:ff:ee:dd:cc:bb:aa", address = "ff:ee:dd:cc:bb:aa"))
        val byTime = pipeline.publicSnapshot().sortedBy { it.lastSeenAtEpochMs }
        assertEquals(FreshnessState.STALE, byTime[0].freshness) // age 90s > fresh window 30s
        assertEquals(FreshnessState.FRESH, byTime[1].freshness)
    }

    // ---- 4) dedup: newest-wins, no revival ----

    @Test
    fun `duplicate identity newest-wins and older samples cannot resurrect`() {
        val pipeline = ObservationPipeline(clock = { 5_000L })
        pipeline.submit(bleDevice(observedAt = 3_000L))
        val older = pipeline.submit(bleDevice(observedAt = 2_000L))
        assertTrue(older is PipelineOutcome.IgnoredStale)
        val public = pipeline.publicSnapshot().single()
        assertEquals(3_000L, public.lastSeenAtEpochMs)
    }

    @Test
    fun `post-expiry late event cannot revive a swept entry`() {
        // TTL 60s, clock at 100_000: an entry observed at 1_000 is long expired.
        val pipeline = ObservationPipeline(
            retention = RetentionConfig(maxEntries = 10, entryTtlMs = 60_000),
            clock = { 100_000L },
        )
        pipeline.submit(bleDevice(observedAt = 1_000L))
        assertTrue(pipeline.publicSnapshot().isEmpty()) // swept by TTL

        // a late OLD event for the same key arrives after expiry:
        val outcome = pipeline.submit(bleDevice(observedAt = 2_000L))
        assertTrue(outcome is PipelineOutcome.Stored) // stored transiently...
        assertTrue(pipeline.publicSnapshot().isEmpty()) // ...but immediately swept: never revived
    }

    @Test
    fun `closed pipeline rejects late events and stays empty`() {
        val pipeline = ObservationPipeline(clock = { 1_000L })
        pipeline.submit(bleDevice())
        pipeline.closeAndDispose()

        val late = pipeline.submit(bleDevice(observedAt = 2_000L))
        assertEquals(PipelineOutcome.IgnoredClosed, late)
        assertTrue(pipeline.publicSnapshot().isEmpty())
        assertFalse(pipeline.remove("ble:aa:bb:cc:dd:ee:ff"))
    }

    // ---- 5) bounded retention ----

    @Test
    fun `burst capacity is bounded with deterministic oldest-first eviction`() {
        val pipeline = ObservationPipeline(
            retention = RetentionConfig(maxEntries = 5, entryTtlMs = Long.MAX_VALUE),
            clock = { 0L },
        )
        // 8 devices with increasing observedAt; capacity 5 => the 3 oldest must go.
        for (i in 0 until 8) {
            pipeline.submit(bleDevice(
                key = "ble:%02x:00:00:00:00:01".format(i),
                address = "%02x:00:00:00:00:01".format(i),
                observedAt = 100L + i))
        }
        assertEquals(5, pipeline.size())
        val keptTimes = pipeline.publicSnapshot().map { it.lastSeenAtEpochMs }.sorted()
        assertEquals(listOf(103L, 104L, 105L, 106L, 107L), keptTimes) // oldest 3 evicted
    }

    @Test
    fun `TTL expiry is deterministic with the injected clock - no sleeping`() {
        val pipeline = ObservationPipeline(
            retention = RetentionConfig(maxEntries = 10, entryTtlMs = 10_000),
            clock = { 20_000L },
        )
        pipeline.submit(bleDevice(observedAt = 5_000L))   // expired (age 15s > 10s)
        pipeline.submit(bleDevice(observedAt = 15_000L, key = "ble:0f:0e:0d:0c:0b:0a", address = "0f:0e:0d:0c:0b:0a")) // alive
        val times = pipeline.publicSnapshot().map { it.lastSeenAtEpochMs }
        assertEquals(listOf(15_000L), times)
    }

    // ---- 6) defensive inputs ----

    @Test
    fun `blank key is rejected and null or malformed identifiers never crash`() {
        val pipeline = ObservationPipeline(clock = { 1_000L })
        assertEquals(
            PipelineOutcome.Rejected("observationKey must not be blank"),
            pipeline.submit(bleDevice(key = "   ", address = null)),
        )
        // no address, unknown-identity path:
        val stored = pipeline.submit(bleDevice(key = "ble:unknown:123", address = null, displayName = null))
        assertTrue(stored is PipelineOutcome.Stored)
        val public = pipeline.publicSnapshot().single()
        assertEquals(ObservationConfidence.CANDIDATE, public.confidence)
    }

    @Test
    fun `implausible RSSI maps to UNAVAILABLE bucket without false certainty`() {
        val pipeline = ObservationPipeline(clock = { 1_000L })
        pipeline.submit(bleDevice(rssi = 999, key = "ble:0a:0a:0a:0a:0a:0a", address = "0a:0a:0a:0a:0a:0a"))
        pipeline.submit(bleDevice(rssi = null, key = "ble:0b:0b:0b:0b:0b:0b", address = "0b:0b:0b:0b:0b:0b"))
        val buckets = pipeline.publicSnapshot().map { it.signalBucket }
        assertTrue(buckets.all { it == SignalBucket.UNAVAILABLE })
    }

    // ---- 7) listener bridge + radio independence ----

    @Test
    fun `listener feeds data events and one radio's session end does not corrupt the other`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        val listener = PipelineObservationListener(pipeline)

        listener.onObservationEvent(ObservationEvent.DeviceObserved(bleDevice(observedAt = 1_000L)))
        listener.onObservationEvent(
            ObservationEvent.SessionEnded(ObservationSessionId("ble-1"), SessionEndReason.FOREGROUND_LOST))
        listener.onObservationEvent(ObservationEvent.WifiResultsAvailable(
            listOf(wifiDevice(observedAt = 1_500L)), updatedSinceLastDelivery = true, resultsAgeMs = 500L))

        // BLE session ended: its DATA is deliberately retained (aggregation is passive),
        // and the Wi-Fi observation was processed independently — no corruption.
        assertEquals(2, pipeline.publicSnapshot().size)
    }

    @Test
    fun `clear is deterministic and explicit disposal removes volatile identifiers`() {
        val pipeline = ObservationPipeline(clock = { 1_000L })
        pipeline.submit(bleDevice())
        assertTrue(pipeline.remove("ble:aa:bb:cc:dd:ee:ff"))
        assertFalse(pipeline.remove("ble:aa:bb:cc:dd:ee:ff"))
        pipeline.submit(bleDevice())
        pipeline.clear()
        assertEquals(0, pipeline.size())
    }
}
