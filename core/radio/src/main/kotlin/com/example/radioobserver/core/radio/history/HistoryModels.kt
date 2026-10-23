package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * Post-roadmap feature (2026-10-23, developer-approved): OPTIONAL, LOCAL, PERSISTENT
 * AND DELETABLE observation history (docs/optional-local-history-design.md).
 *
 * PRIVACY CONTRACT — the ONLY fields a history record may carry. By construction this
 * model CANNOT hold: raw BLE MAC addresses, Wi-Fi BSSIDs, SSIDs without separate
 * consent, advertising payloads, raw ScanResult objects, exact device-location
 * information, stable cross-session radio identifiers, debug diagnostic events,
 * tracking fingerprints or private exception details. RSSI is stored ONLY as a
 * conservative [SignalBucket] category — never as an exact physical distance.
 */
data class HistoryRecord(
    /** Random UUID generated at save time — deliberately NOT derived from any address. */
    val id: String,
    val radio: RadioType,
    /** When the record was saved (wall clock, ms). */
    val savedAtEpochMs: Long,
    /** Honest receipt time of the observation (wall clock, ms). */
    val observedAtEpochMs: Long,
    /** Safe display label. For Wi-Fi this is ONLY non-null under the separate Wi-Fi-names consent. */
    val displayName: String?,
    val manufacturerHint: String?,
    val typeHint: String?,
    /** Conservative signal category; never a distance claim. */
    val signalBucket: SignalBucket,
    /** Freshness provenance carried VERBATIM at save time — a saved row never becomes "fresh". */
    val freshness: FreshnessState,
    /** Age provenance at save time; null = unknown. */
    val ageMs: Long?,
)

/**
 * User consent + retention policy. Persisted with the history so the choice survives
 * process recreation. Recording is OFF by default; Wi-Fi names need a SEPARATE consent
 * (an SSID can reveal location context) and are OFF by default too.
 */
data class HistoryConsent(
    val recordingEnabled: Boolean = false,
    val wifiNamesEnabled: Boolean = false,
    /** Retention in days: default 7, hard maximum 30 (see [HistoryLimits]). */
    val retentionDays: Int = HistoryLimits.DEFAULT_RETENTION_DAYS,
)

/** Bounded, deterministic limits — no unlimited retention, no unbounded growth. */
object HistoryLimits {
    const val DEFAULT_RETENTION_DAYS = 7
    const val MAX_RETENTION_DAYS = 30
    val ALLOWED_RETENTION_DAYS = setOf(7, 14, 30)
    const val MAX_RECORDS = 500
}
