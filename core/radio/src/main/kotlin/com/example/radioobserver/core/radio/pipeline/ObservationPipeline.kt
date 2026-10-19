package com.example.radioobserver.core.radio.pipeline

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.state.ObservationStore
import com.example.radioobserver.core.radio.state.UpsertOutcome

/** Bounded volatile retention for the pipeline (task 2.7). */
data class RetentionConfig(
    /** Hard capacity; deterministic oldest-first eviction under bursts. */
    val maxEntries: Int = 200,
    /** Entries older than this (by receipt wall-clock) are swept lazily; no timers. */
    val entryTtlMs: Long = 15L * 60 * 1000,
)

sealed interface PipelineOutcome {
    data class Stored(val observationId: String, val replacedExisting: Boolean) : PipelineOutcome

    /** Older sample for a key that already holds a newer observation (newest-wins). */
    data class IgnoredStale(val observationKey: String) : PipelineOutcome

    /** Structurally invalid (e.g. blank dedup key). */
    data class Rejected(val reason: String) : PipelineOutcome

    /** The pipeline was closed/disposed: late events can never revive data. */
    data object IgnoredClosed : PipelineOutcome
}

/**
 * Device-free observation pipeline (task 2.7): pure JVM, zero android.* imports, built on
 * the Phase 1 models and [ObservationStore] newest-wins semantics.
 *
 * LOCAL-FIRST, NO EXPORT BY DEFAULT: everything is in-memory volatile. There is no file,
 * database, preference, network, telemetry, share, or logging path anywhere in this class —
 * and none may be added without new authorization.
 *
 * Freshness is honest (see [ObservationFreshness]); retention is bounded
 * ([RetentionConfig]); expiry is a lazy sweep on access (no background processing);
 * eviction is deterministic oldest-first. Raw identifiers are kept only for session-local
 * dedup and are removed with the entry or with [closeAndDispose]. The public projection
 * ([publicSnapshot]) can never surface raw addresses or payload bytes.
 */
class ObservationPipeline(
    private val retention: RetentionConfig = RetentionConfig(),
    private val freshnessConfig: FreshnessConfig = FreshnessConfig(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val store = ObservationStore()
    private val keyToId = HashMap<String, String>()
    private var nextOrdinal = 0L

    @Volatile
    var isClosed: Boolean = false
        private set

    /**
     * Submit one observation. Reuses the store's newest-wins dedup; a randomized BLE address
     * or locally administered BSSID is only ever a session-local dedup key, never a durable
     * identity claim.
     */
    fun submit(device: ObservedRadioDevice): PipelineOutcome {
        if (isClosed) return PipelineOutcome.IgnoredClosed
        return when (val outcome = store.upsert(device)) {
            is UpsertOutcome.Accepted -> {
                val id = keyToId.getOrPut(device.observationKey) {
                    nextOrdinal += 1
                    "obs-$nextOrdinal"
                }
                evictOverCapacity()
                PipelineOutcome.Stored(id, outcome.replacedExisting)
            }
            is UpsertOutcome.IgnoredStale ->
                PipelineOutcome.IgnoredStale(device.observationKey)
            is UpsertOutcome.Rejected ->
                PipelineOutcome.Rejected(outcome.reason)
        }
    }

    /**
     * Privacy-safe projection, newest-first. Expired entries are swept here (lazy, no
     * timers). Raw addresses, payloads, and parse-issue texts are not reachable from it.
     */
    fun publicSnapshot(): List<PublicObservation> {
        sweepExpired()
        val now = clock()
        return store.snapshot().mapNotNull { device ->
            val id = keyToId[device.observationKey] ?: return@mapNotNull null
            toPublicObservation(device, id, now, freshnessConfig)
        }
    }

    /** Raw entries are intentionally NOT exposed publicly; this is for host tests only of
     *  capacity/eviction behavior via [size]. */
    fun size(): Int {
        sweepExpired()
        return store.size()
    }

    /** Explicit deterministic disposal of a single entry. */
    fun remove(observationKey: String): Boolean {
        val removed = store.remove(observationKey) != null
        if (removed) keyToId.remove(observationKey)
        return removed
    }

    /** Explicit deterministic disposal of everything currently held. */
    fun clear() {
        store.clear()
        keyToId.clear()
    }

    /**
     * Terminal disposal (e.g. on foreground loss / session teardown): after this call no
     * event can be stored or revive data (late callbacks fail closed), and all volatile
     * identifiers are dropped. There is deliberately NO reset/reopen: a new observation
     * window needs a NEW pipeline instance.
     */
    fun closeAndDispose() {
        isClosed = true
        clear()
    }

    private fun sweepExpired() {
        if (isClosed) return
        val now = clock()
        val expired = store.keys().filter { key ->
            val device = store.find(key)
            device != null && now - device.observedAtEpochMs > retention.entryTtlMs
        }
        for (key in expired) {
            store.remove(key)
            keyToId.remove(key)
        }
    }

    /** Deterministic oldest-first eviction to respect the hard capacity. */
    private fun evictOverCapacity() {
        while (store.size() > retention.maxEntries) {
            val oldestKey = store.keys().minByOrNull { key ->
                store.find(key)?.observedAtEpochMs ?: Long.MIN_VALUE
            } ?: return
            store.remove(oldestKey)
            keyToId.remove(oldestKey)
        }
    }
}
