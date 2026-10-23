package com.example.radioobserver.core.radio.history

/**
 * Persistence seam for the history store. The Android adapter implements this with an
 * app-private file (allowBackup is already false in the manifest, so nothing leaves the
 * device). All calls must be serialized by the adapter (single background executor):
 * the engine treats them as its private, in-order storage.
 */
interface HistoryPersistence {
    /** Current store text; null when no readable store exists yet. */
    fun read(): String?

    /** Atomically replace the whole store with [text]. */
    fun write(text: String)
}
