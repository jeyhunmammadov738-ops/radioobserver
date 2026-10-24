package com.example.radioobserver.core.radio.alerts

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ObservationRowUi

/**
 * WORKSTREAM 04 — CONTROLLED IN-APP SESSION ALERTS, pure Kotlin/JVM engine.
 *
 * These are NOT Android system notifications: no permission, no service, no sound,
 * no vibration, no wake lock, no background anything. The engine is a deterministic
 * in-memory reducer over the SANITIZED public observation rows only; nothing here
 * can reach addresses, BSSIDs, payloads or names — and no alert ever dispatches a
 * session action (Start/Stop) or a History write.
 *
 * LIFECYCLE: disabled BY DEFAULT; enabled only by an explicit user action; active
 * only while a user-started foreground observation session is alive. Session
 * disposal or foreground loss MUST be reported via [resetSession] (alerts, dedup
 * memory and queue are cleared — nothing survives, nothing is persisted, and a
 * cold start never re-enables the engine).
 *
 * FRESHNESS HONESTY: "genuinely new, fresh" triggers require
 * [FreshnessState.FRESH]. A cached Wi-Fi result (STALE) can NEVER produce a
 * new-device alert; if reliable freshness evidence is unavailable the trigger
 * simply does not fire. Signal-category and manufacturer-status triggers are
 * categorical only — no identity claims.
 *
 * RECOMPOSITION SAFETY: [onRows] is a pure reducer over row snapshots. Feeding the
 * SAME snapshot again emits NOTHING (seen ids and (trigger, row) pairs are
 * remembered), so Compose recomposition cannot generate repeated alerts.
 */
enum class AlertTrigger {
    /** A genuinely NEW, FRESH BLE observation appeared this session. */
    NEW_FRESH_BLE,
    /** A genuinely NEW, FRESH Wi-Fi observation appeared (never a cached reappearance). */
    NEW_FRESH_WIFI,
    /** A new observation arrived in the IMMEDIATE signal category. */
    SIGNAL_IMMEDIATE,
    /** A new observation arrived in the NEAR signal category. */
    SIGNAL_NEAR,
    /** A new observation arrived in the FAR signal category. */
    SIGNAL_FAR,
    /** A new observation carries a KNOWN sanitized manufacturer hint (not identity). */
    MANUFACTURER_KNOWN,
    /** A new observation carries NO manufacturer hint. */
    MANUFACTURER_UNKNOWN,
}

/** One session-local alert. [rowId] is the session-local ordinal — never a device id. */
data class SessionAlert(
    /** Stable session-local event id ("A-0001", "A-0002", ...) — deterministic. */
    val alertId: String,
    val trigger: AlertTrigger,
    val rowId: String,
    val radio: RadioType,
    /** Monotonic per-session sequence starting at 1. */
    val sequence: Int,
)

/** Bounded, deterministic limits (no unbounded growth, no persistence). */
object SessionAlertLimits {
    /** Max alerts kept visible in the queue; oldest evicted first (FIFO). */
    const val MAX_ALERTS = 10
}

/**
 * The reducer. All state is session-local and in-memory; constructing a new engine
 * (every cold start) yields a DISABLED, EMPTY engine by design.
 */
class SessionAlertEngine {

    var enabled: Boolean = false
        private set

    var triggers: Set<AlertTrigger> = emptySet()
        private set

    private var seenRowIds = HashSet<String>()
    private val emitted = HashSet<Pair<AlertTrigger, String>>()
    private val queue = ArrayDeque<SessionAlert>()
    private var nextSequence = 1

    /** Explicit user activation/deactivation ONLY. Disabling clears everything. */
    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) resetSession()
    }

    /** Explicit user trigger selection (independent of enablement). */
    fun setTriggers(value: Set<AlertTrigger>) {
        triggers = value.toSortedSet()
    }

    /**
     * Feed ONE sanitized rows snapshot. Returns the NEWLY emitted alerts (possibly
     * empty). [sessionActive] must reflect a live user-started foreground session:
     * when false the session memory is reset and nothing is ever emitted.
     */
    fun onRows(rows: List<ObservationRowUi>, sessionActive: Boolean): List<SessionAlert> {
        if (!enabled || !sessionActive) {
            resetSession()
            return emptyList()
        }
        val fresh = ArrayList<SessionAlert>(0)
        // Pipeline order is authoritative; iterate newest-first rows deterministically.
        for (row in rows) {
            if (row.rowId in seenRowIds) continue
            seenRowIds.add(row.rowId)
            for (trigger in triggers) {
                if (!matches(trigger, row)) continue
                val key = trigger to row.rowId
                if (!emitted.add(key)) continue // at most one alert per (rule, observation)
                val alert = SessionAlert(
                    alertId = "A-" + nextSequence.toString().padStart(4, '0'),
                    trigger = trigger,
                    rowId = row.rowId,
                    radio = row.radio,
                    sequence = nextSequence,
                )
                nextSequence++
                queue.addLast(alert)
                if (queue.size > SessionAlertLimits.MAX_ALERTS) queue.removeFirst()
                fresh.add(alert)
            }
        }
        return fresh
    }

    /** Categorical, sanitized matching only — freshness is respected, never invented. */
    private fun matches(trigger: AlertTrigger, row: ObservationRowUi): Boolean = when (trigger) {
        AlertTrigger.NEW_FRESH_BLE ->
            row.radio == RadioType.BLE && row.freshness == FreshnessState.FRESH
        AlertTrigger.NEW_FRESH_WIFI ->
            row.radio == RadioType.WIFI && row.freshness == FreshnessState.FRESH
        AlertTrigger.SIGNAL_IMMEDIATE -> row.signalBucket == SignalBucket.IMMEDIATE
        AlertTrigger.SIGNAL_NEAR -> row.signalBucket == SignalBucket.NEAR
        AlertTrigger.SIGNAL_FAR -> row.signalBucket == SignalBucket.FAR
        AlertTrigger.MANUFACTURER_KNOWN -> row.manufacturerHint != null
        AlertTrigger.MANUFACTURER_UNKNOWN -> row.manufacturerHint == null
    }

    fun dismiss(alertId: String) {
        queue.removeAll { it.alertId == alertId }
    }

    fun dismissAll() {
        queue.clear()
    }

    /** Current visible alerts, newest-last (stable order). */
    fun alerts(): List<SessionAlert> = queue.toList()

    /**
     * Session disposal / foreground loss / disablement: clears seen ids, dedup
     * pairs, the queue AND the sequence counter — the next session starts clean.
     * This is also called from [onRows] when the session is no longer active.
     */
    fun resetSession() {
        seenRowIds.clear()
        emitted.clear()
        queue.clear()
        nextSequence = 1
    }
}
