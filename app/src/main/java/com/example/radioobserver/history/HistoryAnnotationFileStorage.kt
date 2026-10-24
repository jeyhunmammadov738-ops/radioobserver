package com.example.radioobserver.history

import android.content.Context
import com.example.radioobserver.core.radio.history.HistoryAnnotationPersistence
import java.io.File
import java.io.IOException

/**
 * WORKSTREAM 04: app-private storage for RECORD-BOUND History annotations.
 *
 * STARTUP SAFETY (WS02 hotfix contract): constructed ONLY inside
 * MainActivity.onCreate AFTER super.onCreate — never in an Activity property
 * initializer. The Context is used solely for filesDir.
 *
 * Atomic write: complete next state -> temp file -> atomic rename (OEM fallback
 * copy+delete). Unreadable content fails closed to an empty annotation set WITHOUT
 * rewriting the original. This file (history_annotations_v1.txt) is COMPLETELY
 * SEPARATE from the History store, the WS02 filter presets and the WS03B signature
 * catalog — none of those files is read, written or migrated here.
 */
class HistoryAnnotationFileStorage(context: Context) : HistoryAnnotationPersistence {

    private val file = File(context.filesDir, FILE_NAME)
    private val temp = File(context.filesDir, "$FILE_NAME.tmp")

    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (io: IOException) {
            null // unreadable: fail closed to no annotations
        } catch (security: SecurityException) {
            null
        }
    }

    override fun write(text: String) {
        try {
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                file.writeText(text)
                temp.delete()
            }
        } catch (io: IOException) {
            temp.delete() // never leave a partial temp file behind
        }
    }

    companion object {
        const val FILE_NAME = "history_annotations_v1.txt"
    }
}
