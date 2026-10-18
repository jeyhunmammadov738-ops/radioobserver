package com.example.radioobserver.core.radio.state

import com.example.radioobserver.core.radio.model.ObservedRadioDevice

sealed interface UpsertOutcome {
    /** Observation stored (first insert or newest-wins replacement). */
    data class Accepted(val replacedExisting: Boolean) : UpsertOutcome

    /** Structurally invalid input. */
    data class Rejected(val reason: String) : UpsertOutcome

    /** An older sample arrived for a key that already has a newer observation. */
    data class IgnoredStale(
        val keptObservedAtEpochMs: Long,
        val ignoredObservedAtEpochMs: Long,
    ) : UpsertOutcome
}

/**
 * Minimal, pure, in-memory observation aggregation. Phase 1 semantics:
 * - Dedup by [ObservedRadioDevice.observationKey]: a convenience key, NOT physical identity.
 * - Within a key, only the NEWEST observation wins; older/out-of-order samples are ignored.
 * - Devices are NEVER merged across keys: same manufacturer code or SSID text is not identity.
 * - Everything is process-local; nothing is persisted or uploaded.
 */
class ObservationStore {

    private val byKey = LinkedHashMap<String, ObservedRadioDevice>()

    fun upsert(device: ObservedRadioDevice): UpsertOutcome {
        if (device.observationKey.isBlank()) {
            return UpsertOutcome.Rejected("observationKey must not be blank")
        }
        val existing = byKey[device.observationKey]
        if (existing != null && device.observedAtEpochMs < existing.observedAtEpochMs) {
            return UpsertOutcome.IgnoredStale(
                keptObservedAtEpochMs = existing.observedAtEpochMs,
                ignoredObservedAtEpochMs = device.observedAtEpochMs,
            )
        }
        byKey[device.observationKey] = device
        return UpsertOutcome.Accepted(replacedExisting = existing != null)
    }

    fun find(key: String): ObservedRadioDevice? = byKey[key]

    /** Newest-first snapshot. */
    fun snapshot(): List<ObservedRadioDevice> =
        byKey.values.sortedByDescending { it.observedAtEpochMs }

    fun size(): Int = byKey.size

    fun clear() = byKey.clear()
}
