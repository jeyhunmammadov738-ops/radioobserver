package com.example.radioobserver.signature

import android.content.Context
import com.example.radioobserver.core.radio.signature.UserRulePersistence
import java.io.File
import java.io.IOException

/**
 * WORKSTREAM 03B: app-private storage for the USER classification catalog.
 *
 * STARTUP SAFETY (WS02 hotfix contract): this class is constructed ONLY inside
 * MainActivity.onCreate AFTER super.onCreate — never in an Activity property
 * initializer. The Context is used solely for filesDir; nothing else is touched.
 *
 * Atomic write: the complete next state is written to a temp file and atomically
 * renamed over the original, so an interrupted write can never truncate the user's
 * catalog. Unreadable content fails closed to an empty catalog WITHOUT rewriting
 * the original file.
 *
 * This file (signature_user_catalog_v1.txt) is COMPLETELY SEPARATE from the WS02
 * filter-preset store and from History storage; neither of those files is read,
 * written or migrated here.
 */
class SignatureUserRuleFileStorage(context: Context) : UserRulePersistence {

    private val file = File(context.filesDir, FILE_NAME)
    private val temp = File(context.filesDir, "$FILE_NAME.tmp")

    override fun read(): String? {
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (io: IOException) {
            null // unreadable: fail closed to no user rules
        } catch (security: SecurityException) {
            null
        }
    }

    override fun write(text: String) {
        try {
            temp.writeText(text)
            if (!temp.renameTo(file)) {
                // Some OEM filesystems reject rename-over-existing: fall back to
                // copy+delete, still atomic from the reader's perspective.
                file.writeText(text)
                temp.delete()
            }
        } catch (io: IOException) {
            temp.delete() // never leave a partial temp file behind
        }
    }

    companion object {
        const val FILE_NAME = "signature_user_catalog_v1.txt"
    }
}
