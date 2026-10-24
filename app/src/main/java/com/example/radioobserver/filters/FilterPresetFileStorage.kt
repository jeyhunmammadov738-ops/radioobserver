package com.example.radioobserver.filters

import android.content.Context
import com.example.radioobserver.core.radio.ui.FilterPresets
import java.io.File
import java.io.IOException

/**
 * WORKSTREAM 02 — app-private whole-file storage for saved filter presets.
 * Mirrors the established HistoryFileStorage pattern exactly:
 *
 * - LOCAL ONLY: the file lives in the app's private sandbox; the manifest already
 *   sets android:allowBackup="false", so presets are never backed up to the cloud
 *   or restored to another device. No network, no analytics, no sharing.
 * - ATOMIC: writes go to a temp file first, then rename() replaces the store in
 *   one step — a process kill mid-write can never leave a half-written file (and
 *   the codec fails closed on unreadable content anyway).
 * - NO NEW DEPENDENCY: plain java.io.
 * - Presets are CONFIGURATION, not observation history: this file is completely
 *   separate from the history store; neither read nor write ever touches it.
 */
class FilterPresetFileStorage(context: Context) : FilterPresets.Persistence {

    private val file = File(context.filesDir, FILE_NAME)
    private val temp = File(context.filesDir, "$FILE_NAME.tmp")

    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (io: IOException) {
            null // unreadable: fail closed to no presets
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
            // Best-effort configuration store: never crash the app for it.
        } catch (security: SecurityException) {
            // Same: silent, honest absence (the next successful write retries).
        }
    }

    companion object {
        const val FILE_NAME = "filter_presets_v1.txt"
    }
}
