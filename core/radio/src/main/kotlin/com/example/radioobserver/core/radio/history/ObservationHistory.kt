package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import java.util.UUID

/**
 * The pure-JVM history brain (post-roadmap feature; docs/optional-local-history-design.md).
 * Holds the current consent + records in memory and rewrites the WHOLE store on every
 * mutation through [HistoryPersistence] (atomic whole-file writes; a 500-record store is
 * tiny). The adapter serializes all access on one background executor, so concurrent
 * write/delete races cannot interleave.
 *
 * Consent is re-read AT EXECUTION TIME for every operation: a queued write that was
 * requested while recording was enabled is dropped if the user disabled recording
 * before the write runs (no disabled-era resurrection).
 *
 * History NEVER touches the live observation pipeline: the direction is strictly
 * live → history. Pruning is deterministic (every save/load), NOT scheduled background
 * work. Retention: default 7 days, hard max 30; capacity: 500, oldest-saved dropped first.
 */
class ObservationHistory(
    private val persistence: HistoryPersistence,
    private val clock: () -> Long,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    var consent: HistoryConsent = HistoryConsent()
        private set
    private var records: MutableList<HistoryRecord> = mutableListOf()
    private var loaded = false

    /** Corrupt lines dropped by the last load (honest, one-time UI notice). */
    var lastLoadDroppedLines: Int = 0
        private set

    /** True when the previous store was unreadable and everything was dropped (fail closed). */
    var lastLoadFailedClosed: Boolean = false
        private set

    // ---- load / snapshot ----

    @Synchronized
    fun loadIfNeeded() {
        if (loaded) return
        val decoded = HistoryCodec.decode(persistence.read())
        consent = decoded.consent
        records = decoded.records.sortedByDescending { it.savedAtEpochMs }.toMutableList()
        lastLoadDroppedLines = decoded.droppedLines
        lastLoadFailedClosed = decoded.failedClosed
        if (decoded.droppedLines > 0 || decoded.failedClosed) {
            // Rewrite the store WITHOUT the corrupt content immediately (bounded, no data left broken).
            persistNow()
        }
        pruneAndPersist(clock(), force = false)
        loaded = true
    }

    /** Newest-first snapshot, optionally filtered by radio (UI filter). Reads PRUNE too:
     *  retention is enforced deterministically at every load AND every read, never by
     *  background scheduling. */
    @Synchronized
    fun snapshot(filter: RadioType? = null): List<HistoryRecord> {
        loadIfNeeded()
        pruneAndPersist(clock(), force = false)
        return records.filter { filter == null || it.radio == filter }
    }

    @Synchronized
    fun count(): Int {
        loadIfNeeded()
        pruneAndPersist(clock(), force = false)
        return records.size
    }

    // ---- consent ----

    @Synchronized
    fun setRecordingEnabled(enabled: Boolean) {
        loadIfNeeded()
        consent = consent.copy(recordingEnabled = enabled)
        persistNow()
    }

    @Synchronized
    fun setWifiNamesEnabled(enabled: Boolean) {
        loadIfNeeded()
        consent = consent.copy(wifiNamesEnabled = enabled)
        persistNow()
    }

    @Synchronized
    fun setRetentionDays(days: Int) {
        loadIfNeeded()
        if (days !in HistoryLimits.ALLOWED_RETENTION_DAYS) return // hard max 30, fixed choices
        consent = consent.copy(retentionDays = days)
        pruneAndPersist(clock(), force = true)
    }

    // ---- recording ----

    /**
     * Persists a bounded sanitized snapshot for one eligible session-local observation.
     * Checks consent AT EXECUTION TIME. Returns the saved record or null (not enabled).
     * Wi-Fi display names are stored ONLY under the separate Wi-Fi-names consent;
     * BSSIDs never exist at this layer (rows are already sanitized).
     */
    @Synchronized
    fun record(row: ObservationRowUi): HistoryRecord? {
        loadIfNeeded()
        if (!consent.recordingEnabled) return null
        val displayName = when (row.radio) {
            RadioType.WIFI -> row.name.takeIf { consent.wifiNamesEnabled }
            RadioType.BLE -> row.name
        }
        val record = HistoryRecord(
            id = idGenerator(),
            radio = row.radio,
            savedAtEpochMs = clock(),
            observedAtEpochMs = row.lastSeenAtEpochMs,
            displayName = displayName,
            manufacturerHint = row.manufacturerHint,
            typeHint = row.typeHint,
            signalBucket = row.signalBucket,
            freshness = row.freshness,
            ageMs = row.ageMs,
        )
        records.add(0, record)
        pruneAndPersist(clock(), force = true)
        return record
    }

    // ---- deletion ----

    /** Deletes one saved record by id. Never touches any live session. */
    @Synchronized
    fun deleteOne(id: String): Boolean {
        loadIfNeeded()
        val removed = records.removeAll { it.id == id }
        if (removed) persistNow()
        return removed
    }

    /** Confirmed Clear All. The live-feed dedup (seen ids) is managed by [LiveRowTracker]. */
    @Synchronized
    fun clearAll(): Int {
        loadIfNeeded()
        val removed = records.size
        records.clear()
        persistNow()
        return removed
    }

    // ---- retention / capacity ----

    /** Deterministic prune on EVERY save and load: no background scheduling. */
    private fun pruneAndPersist(now: Long, force: Boolean) {
        val before = records.size
        val ttlMs = consent.retentionDays * 24L * 60 * 60 * 1000
        records = records.filter { now - it.savedAtEpochMs <= ttlMs }.toMutableList()
        if (records.size > HistoryLimits.MAX_RECORDS) {
            // Oldest-SAVED dropped first: keep the newest MAX_RECORDS.
            records = records.sortedByDescending { it.savedAtEpochMs }
                .take(HistoryLimits.MAX_RECORDS).toMutableList()
        }
        if (force || records.size != before) persistNow()
    }

    private fun persistNow() {
        persistence.write(HistoryCodec.encode(consent, records))
    }
}
