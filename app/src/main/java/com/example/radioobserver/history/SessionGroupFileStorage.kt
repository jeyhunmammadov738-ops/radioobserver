package com.example.radioobserver.history

import android.content.Context
import com.example.radioobserver.core.radio.history.SessionGroupPersistence
import java.io.File
import java.io.IOException

/**
 * WORKSTREAM 05: app-private storage for NAMED ANALYTICAL GROUPS.
 *
 * STARTUP SAFETY (WS02 hotfix contract): constructed ONLY inside
 * MainActivity.onCreate AFTER super.onCreate — never in a property initializer.
 *
 * Atomic write: complete next state -> temp file -> atomic rename (OEM fallback
 * copy+delete). Unreadable content fails closed to an empty group set WITHOUT
 * rewriting the original. This file (session_groups_v1.txt) is COMPLETELY
 * SEPARATE from the History store, the WS02 presets, the WS03B signature catalog
 * and the WS04 annotation store — none is read, written or migrated here.
 * Partial-write failure leaves the original file intact (temp discarded).
 */
class SessionGroupFileStorage(context: Context) : SessionGroupPersistence {

    private val file = File(context.filesDir, FILE_NAME)
    private val temp = File(context.filesDir, "$FILE_NAME.tmp")

    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (io: IOException) {
            null // unreadable: fail closed to no groups, original preserved
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
        const val FILE_NAME = "session_groups_v1.txt"
    }
}
