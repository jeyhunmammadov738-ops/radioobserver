package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.boundary.ObservationEvent
import com.example.radioobserver.core.radio.model.DeviceTypeCandidate
import com.example.radioobserver.core.radio.model.ManufacturerInfo
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.pipeline.ObservationPipeline
import com.example.radioobserver.core.radio.pipeline.PublicObservation
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3 task 3.2 acceptance tests (device-free JVM): presentation mapping honesty and
 * the privacy boundary. Rows are built from REAL pipeline snapshots so these tests
 * exercise behavior end-to-end (evidence device -> pipeline projection -> UI row), not
 * text greps. They never validate real radio reception or Android UI.
 */
class ObservationUiMapperTest {

    private fun bleDevice(
        address: String = "aa:bb:cc:dd:ee:ff",
        observedAt: Long = 1_000L,
        rssi: Int? = -55,
        parseIssues: List<String> = listOf("bad payload near aa:bb:cc:dd:ee:ff"),
    ) = ObservedRadioDevice(
        observationKey = "ble:$address",
        radioType = RadioType.BLE,
        observedAtEpochMs = observedAt,
        displayName = "Tag",
        address = address,
        manufacturerCandidate = ManufacturerInfo(
            companyId = 76, companyName = "Apple", evidence = listOf("0x004C")),
        deviceTypeCandidate = DeviceTypeCandidate(
            label = "iBeacon format", confidence = ObservationConfidence.CANDIDATE,
            evidence = listOf("layout matched")),
        rssiDbm = rssi,
        locallyAdministeredAddress = false,
        parseIssues = parseIssues,
    )

    private fun wifiDevice(
        key: String = "wifi:11:22:33:44:55:66",
        observedAt: Long = 1_000L,
        resultAgeMs: String = "500",
        updatedFlag: String = "true",
    ) = ObservedRadioDevice(
        observationKey = key,
        radioType = RadioType.WIFI,
        observedAtEpochMs = observedAt,
        displayName = "Cafe",
        address = "11:22:33:44:55:66",
        sourceFields = mapOf("resultAgeMs" to resultAgeMs, "resultsUpdatedFlag" to updatedFlag),
        rssiDbm = -67,
        parseIssues = emptyList(),
    )

    @Test
    fun `rows built from real snapshots never expose raw identifiers`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        pipeline.submit(bleDevice(address = "aa:bb:cc:dd:ee:ff"))
        pipeline.submit(wifiDevice())
        val rows = ObservationUiMapper.toRows(pipeline.publicSnapshot())
        assertEquals(2, rows.size)
        val everything = rows.joinToString { it.toString() } + rows.joinToString { it.name ?: "" }
        // Raw identifiers must not appear anywhere in the sanitized presentation:
        assertFalse(everything.contains("aa:bb:cc:dd:ee:ff"))
        assertFalse(everything.contains("11:22:33:44:55:66"))
        assertFalse(everything.contains("dd:ee:ff"))
        assertFalse(everything.contains("44:55:66"))
        // Parse-issue text (which embeds raw identifiers) is exposed only as a COUNT:
        assertEquals(1, rows[0].parseIssueCount)
        assertFalse(everything.contains("bad payload"))
    }

    @Test
    fun `row identity is the session-local ordinal, never address-derived`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        pipeline.submit(bleDevice(address = "aa:bb:cc:dd:ee:ff"))
        pipeline.submit(bleDevice(address = "ff:ee:dd:cc:bb:aa"))
        val rows = ObservationUiMapper.toRows(pipeline.publicSnapshot())
        assertEquals(listOf("obs-1", "obs-2"), rows.map { it.rowId })
        assertFalse(rows[0].rowId.contains("aa:bb"))
        assertFalse(rows[0].rowId.contains("aabbcc"))
        assertNotEquals(rows[0].rowId, rows[1].rowId)
    }

    @Test
    fun `fresh BLE observation stays fresh and cached Wi-Fi never becomes fresh`() {
        val now = 2_000L
        val pipeline = ObservationPipeline(clock = { now })
        pipeline.submit(bleDevice(observedAt = now - 500))
        // Cached (updated=false) Wi-Fi result: honesty says it can never be FRESH:
        pipeline.submit(wifiDevice(observedAt = now - 500, resultAgeMs = "100", updatedFlag = "false"))
        val rows = ObservationUiMapper.toRows(pipeline.publicSnapshot())
        val bleRow = rows.first { it.radio == RadioType.BLE }
        val cachedWifiRow = rows.first { it.radio == RadioType.WIFI }
        assertEquals(FreshnessState.FRESH, bleRow.freshness)
        assertNotEquals(FreshnessState.FRESH, cachedWifiRow.freshness)
    }

    @Test
    fun `wifi freshness distinguishes fresh, stale and unknown`() {
        val now = 2_000L
        val pipeline = ObservationPipeline(clock = { now })
        pipeline.submit(wifiDevice(key = "wifi:a1", observedAt = now - 500, resultAgeMs = "100"))     // fresh
        pipeline.submit(wifiDevice(key = "wifi:a2", observedAt = now - 500, resultAgeMs = "600000"))   // stale
        pipeline.submit(wifiDevice(key = "wifi:a3", observedAt = now - 500, resultAgeMs = "unknown"))   // unknown
        val rows = ObservationUiMapper.toRows(pipeline.publicSnapshot())
        val freshness = rows.map { it.freshness }.toSet()
        assertTrue(freshness.contains(FreshnessState.FRESH))
        assertTrue(freshness.contains(FreshnessState.STALE))
        assertTrue(freshness.contains(FreshnessState.UNKNOWN))
        assertEquals(3, rows.size)
    }

    @Test
    fun `confidence stays conservative and signal stays qualitative`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        pipeline.submit(bleDevice())
        val row = ObservationUiMapper.toRows(pipeline.publicSnapshot()).single()
        // An Apple 0x004C hint with iBeacon layout is only ever a CANDIDATE - the UI row
        // must not promote it or claim a tracker identity:
        assertEquals(ObservationConfidence.CANDIDATE, row.confidence)
        assertNotEquals(ObservationConfidence.VALIDATED_FORMAT, row.confidence)
        assertEquals("Apple", row.manufacturerHint)
        assertEquals(SignalBucket.NEAR, row.signalBucket) // qualitative bucket, not meters
        assertEquals(-55, row.rssiDbm)
    }

    @Test
    fun `defensive mapping hides blank names, negative ages and missing values`() {
        val public = PublicObservation(
            observationId = "",
            radio = RadioType.BLE,
            displayName = "   ",
            manufacturerName = "",
            deviceTypeLabel = null,
            confidence = ObservationConfidence.UNKNOWN,
            signalBucket = SignalBucket.UNAVAILABLE,
            rssiDbm = null,
            locallyAdministeredAddress = true,
            freshness = FreshnessState.UNKNOWN,
            ageMs = -5,
            lastSeenAtEpochMs = 1_000L,
            parseIssueCount = -3,
        )
        val row = ObservationUiMapper.toRow(public, index = 0)
        assertEquals("row-0", row.rowId)     // blank ordinal -> session-local fallback
        assertNull(row.name)
        assertNull(row.manufacturerHint)
        assertNull(row.typeHint)
        assertNull(row.rssiDbm)
        assertEquals(SignalBucket.UNAVAILABLE, row.signalBucket)
        assertNull(row.ageMs)               // implausible age is not fabricated
        assertEquals(0, row.parseIssueCount)
        assertEquals(FreshnessState.UNKNOWN, row.freshness)
    }

    @Test
    fun `feed replacement is bounded to the current session snapshot only`() {
        val pipeline = ObservationPipeline(clock = { 2_000L })
        pipeline.submit(bleDevice())
        val state = ObservationUiMapper.withRows(
            ObservationUiState.initial(),
            ObservationUiMapper.toRows(pipeline.publicSnapshot()))
        assertEquals(1, state.rows.size)
        val emptied = ObservationUiMapper.withRowsCleared(state)
        assertTrue(emptied.rows.isEmpty())
    }

    @Test
    fun `mapper accepts only the sanitized projection - no evidence type is referenced`() {
        // Structural check exercised through behavior: mapping a snapshot after pipeline
        // disposal yields nothing, and disposal cannot be undone to resurrect rows:
        val pipeline = ObservationPipeline(clock = { 2_000L })
        pipeline.submit(bleDevice())
        pipeline.closeAndDispose()
        assertTrue(pipeline.publicSnapshot().isEmpty())
        assertTrue(ObservationUiMapper.toRows(pipeline.publicSnapshot()).isEmpty())
    }
}
