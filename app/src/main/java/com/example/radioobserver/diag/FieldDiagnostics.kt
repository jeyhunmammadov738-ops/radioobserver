package com.example.radioobserver.diag

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * DEBUG-ONLY, privacy-safe field diagnostics (post-roadmap field iteration, 2026-10-23).
 * Structured, typed, identifier-free breadcrumbs so a bounded, package-filtered
 * logcat can pinpoint the Start/permission/eligibility mechanism on real devices.
 *
 * GUARANTEES: no raw MAC/BSSID, no advertising payload, no observed identifiers,
 * no location data, no observation content, no network calls or telemetry; only
 * typed booleans/class names. Release builds are NOT debuggable, so they emit
 * NOTHING. Logging NEVER changes control flow.
 *
 * Gated by the application's debuggable flag (set from MainActivity.onCreate)
 * instead of BuildConfig, which newer AGP versions do not generate by default.
 */
object FieldDiagnostics {
    private const val TAG = "RadioObserverDiag"

    @Volatile
    private var debuggable = false

    /** Call once from MainActivity.onCreate; release builds stay silent. */
    fun initialize(appContext: Context) {
        debuggable =
            (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun log(event: String, vararg facts: Pair<String, Any?>) {
        if (!debuggable) return
        val sb = StringBuilder(event)
        for ((k, v) in facts) sb.append(' ').append(k).append('=').append(v)
        Log.d(TAG, sb.toString())
    }
}
