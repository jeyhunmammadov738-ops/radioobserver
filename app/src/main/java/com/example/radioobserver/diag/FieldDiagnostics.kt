package com.example.radioobserver.diag

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * DEBUG-ONLY, privacy-safe field diagnostics (third field iteration, 2026-10-23).
 *
 * TWO outputs, ONE instrumentation path:
 *  1. RadioObserverDiag Logcat events (requires ADB);
 *  2. an on-screen, bounded chronology (FieldDiagnosticsPanel) so the next phone
 *     retest needs NO ADB/PC — the decisive non-ADB evidence path.
 *
 * GUARANTEES: no raw MAC/BSSID, no advertising payload, no observed identifiers,
 * no location data, no observation content, no network calls, no disk/file
 * storage, no analytics/telemetry; only typed booleans/class names. Release
 * builds are NOT debuggable: the panel is not rendered and NOTHING is emitted.
 * Diagnostics NEVER change control flow, never start/request/recheck anything.
 *
 * The ring is pure in-RAM state, bounded to [CAPACITY] rows, cleared by process
 * death. Gated by the application debuggable flag (set from MainActivity.onCreate)
 * instead of BuildConfig, which newer AGP versions do not generate by default.
 */
object FieldDiagnostics {
    const val BUILD_MARKER = "FieldDiag-04"
    private const val TAG = "RadioObserverDiag"
    const val CAPACITY = 40

    @Volatile
    private var debuggable = false

    private val startNanos = System.nanoTime()

    /** Bounded in-memory chronology (main-thread appends; synchronized defensively). */
    private val rows = ArrayList<String>(CAPACITY)

    /** Strictly increasing process-local sequence: makes timeline order explicit
     *  across resume/recreation and audit the transcribed nonmonotonic offsets. */
    private var sequence = 0L

    /** Call once from MainActivity.onCreate; release builds stay silent. */
    fun initialize(appContext: Context) {
        debuggable =
            (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /** Whether DEBUG outputs (panel + Logcat) are enabled for this build. */
    val isDebugBuild: Boolean get() = debuggable

    fun log(event: String, vararg facts: Pair<String, Any?>) {
        if (!debuggable) return
        val sb = StringBuilder(event)
        for ((k, v) in facts) sb.append(' ').append(k).append('=').append(v)
        Log.d(TAG, sb.toString())
        appendRow(formatRow(event, facts.toList()))
    }

    /** Elapsed-milliseconds-since-diag-start + typed event line (pure, testable). */
    fun formatRow(event: String, facts: List<Pair<String, Any?>>): String {
        val sb = StringBuilder()
        for ((k, v) in facts) sb.append(' ').append(k).append('=').append(v)
        return "${event}${sb}"
    }

    /** Pure, testable row-line format: sequence + monotonic elapsed seconds. */
    fun rowLine(seq: Long, elapsedMs: Long, text: String): String =
        "#$seq +${"%.3f".format(elapsedMs / 1000.0)}s $text"

    private fun appendRow(text: String) {
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000
        val line = synchronized(this) {
            sequence += 1
            rowLine(sequence, elapsedMs, text).also { rows.add(it) }
        }
        if (rows.size > CAPACITY) rows.removeAt(0)
    }

    /** Copy of the bounded chronology, oldest first (for the on-screen panel). */
    fun snapshotRows(): List<String> = synchronized(this) { ArrayList(rows) }

    /** In-RAM only; clears on teardown/process death. Never persisted anywhere. */
    fun clear() = synchronized(this) { rows.clear() }
}
