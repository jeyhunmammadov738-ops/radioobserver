package com.example.radioobserver.core.radio.state

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationStoreTest {

    private fun device(key: String, ts: Long, name: String = "dev-$key") = ObservedRadioDevice(
        observationKey = key,
        radioType = RadioType.BLE,
        observedAtEpochMs = ts,
        displayName = name,
    )

    @Test
    fun newestObservationWinsForSameKey() {
        val store = ObservationStore()
        assertEquals(UpsertOutcome.Accepted(replacedExisting = false), store.upsert(device("ble:aa", 1_000L)))
        assertEquals(UpsertOutcome.Accepted(replacedExisting = true), store.upsert(device("ble:aa", 2_000L)))

        assertEquals(1, store.size())
        assertEquals(2_000L, store.find("ble:aa")?.observedAtEpochMs)
    }

    @Test
    fun staleSampleIsIgnoredWithEvidence() {
        val store = ObservationStore()
        store.upsert(device("ble:aa", 2_000L))
        val outcome = store.upsert(device("ble:aa", 1_000L))

        assertTrue(outcome is UpsertOutcome.IgnoredStale)
        outcome as UpsertOutcome.IgnoredStale
        assertEquals(2_000L, outcome.keptObservedAtEpochMs)
        assertEquals(1_000L, outcome.ignoredObservedAtEpochMs)
        assertEquals(2_000L, store.find("ble:aa")?.observedAtEpochMs)
    }

    @Test
    fun equalTimestampReplacesLastWriteWins() {
        val store = ObservationStore()
        store.upsert(device("ble:aa", 2_000L, name = "first"))
        val outcome = store.upsert(device("ble:aa", 2_000L, name = "second"))

        assertEquals(UpsertOutcome.Accepted(replacedExisting = true), outcome)
        assertEquals("second", store.find("ble:aa")?.displayName)
    }

    @Test
    fun blankKeyIsRejected() {
        val store = ObservationStore()
        val outcome = store.upsert(device("  ", 1_000L))
        assertTrue(outcome is UpsertOutcome.Rejected)
        assertEquals(0, store.size())
    }

    @Test
    fun devicesWithSameManufacturerOrSsidAreNeverMerged() {
        val store = ObservationStore()
        val wifi1 = device("wifi:ssid:Cafe", 1_000L)
        val wifi2 = device("wifi:ssid:Cafe2", 1_100L)
        store.upsert(wifi1)
        store.upsert(wifi2)

        assertEquals(2, store.size()) // distinct keys stay distinct records
    }

    @Test
    fun unknownIdentifierKeysStayDistinct() {
        val store = ObservationStore()
        store.upsert(device("wifi:unknown:100", 100L))
        store.upsert(device("wifi:unknown:200", 200L))
        assertEquals(2, store.size())
    }

    @Test
    fun snapshotIsSortedNewestFirst() {
        val store = ObservationStore()
        store.upsert(device("k1", 100L))
        store.upsert(device("k2", 300L))
        store.upsert(device("k3", 200L))

        val keys = store.snapshot().map { it.observationKey }
        assertEquals(listOf("k2", "k3", "k1"), keys)
    }

    @Test
    fun clearResetsState() {
        val store = ObservationStore()
        store.upsert(device("k1", 100L))
        store.clear()
        assertEquals(0, store.size())
    }
}
