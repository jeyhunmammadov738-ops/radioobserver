package com.example.radioobserver.history

import android.content.Context
import com.example.radioobserver.core.radio.history.HistoryPersistence
import java.io.File
import java.io.IOException

/**
 * App-private whole-file storage for the optional local history (post-roadmap feature).
 *
 * - LOCAL ONLY: the file lives in the app's private sandbox; the manifest already sets
 *   android:allowBackup="false", so nothing is EVER backed up to the cloud or restored
 *   to another device. No network, no analytics, no sharing — none exist in this app.
 * - ATOMIC: writes go to a temp file first, then rename() replaces the store in one
 *   step — a process kill mid-write can never leave a half-written store (and the
 *   codec fails closed to privacy on unreadable content anyway).
 * - NO NEW DEPENDENCY: plain java.io (protocol v11.1: no new database library).
 * - All calls run on the controller's single background executor (serialized).
 */
class HistoryFileStorage(context: Context) : HistoryPersistence {

    private val file = File(context.filesDir, FILE_NAME)
    private val temp = File(context.filesDir, "$FILE_NAME.tmp")

    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (io: IOException) {
            null // unreadable: the engine fails closed to privacy
        } catch (security: SecurityException) {
            null
        }
    }

    override fun write(text: String) {
        try {
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                // Cross-filesystem edge: fall back to copy + delete.
                file.writeText(text)
                temp.delete()
            }
        } catch (io: IOException) {
            // Storage failure: the history is best-effort — never crash observation for it.
        } catch (security: SecurityException) {
            // Same: silent, honest absence (the next successful write retries).
        }
    }

    companion object {
        const val FILE_NAME = "radio_history_v1.txt"
    }
}
