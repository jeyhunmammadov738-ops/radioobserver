package com.example.radioobserver.core.radio.export

import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * WORKSTREAM 06 — LOCAL REPORTING AND EXPLICIT EXPORT (pure JVM, zero android.*).
 *
 * PRIVACY / TRUST CONTRACT
 * - Only ALREADY-SAVED, sanitized [HistoryRecord] instances the user explicitly
 *   selects may be exported. Transient live rows, alerts and FieldDiag data are
 *   structurally unreachable: this package never sees them.
 * - No raw MAC/BSSID, payload, fingerprint, storage path or record ID is ever
 *   written. Row ordinals are REPORT-LOCAL counters, not identities.
 * - Names/SSIDs and user labels/notes are EXCLUDED by default and require an
 *   explicit opt-in; a withheld name can never be reconstructed here.
 * - Record COUNT is never a device count; every payload carries that statement.
 * - Generation is deterministic for the same inputs (stable ordering, fixed
 *   headers, UTC ISO-8601 timestamps, Locale.ROOT formatting only).
 */
enum class ExportFormat(val id: String, val mimeType: String, val extension: String) {
    CSV("csv", "text/csv", "csv"),
    JSON("json", "application/json", "json"),
    ;

    companion object {
        fun fromId(value: String): ExportFormat? = entries.firstOrNull { it.id == value }
    }
}

enum class ExportReportType(val id: String) {
    RECORDS("records"),
    GROUP_SUMMARY("group-summary"),
    GROUP_COMPARISON("group-comparison"),
}

/** Sensitive-field opt-ins. BOTH default to false (documented export policy). */
data class ExportOptions(
    /** Network names (recorded ONLY under the separate Wi-Fi-name consent). */
    val includeNames: Boolean = false,
    /** User-created labels/notes (user text, never radio data). */
    val includeUserAnnotations: Boolean = false,
)

/** Sanitized, export-safe projection of ONE saved record. */
data class ExportRecord(
    val radio: RadioType,
    val savedAtEpochMs: Long,
    val observedAtEpochMs: Long,
    val displayName: String?,
    val manufacturerHint: String?,
    val typeHint: String?,
    val signalBucket: SignalBucket,
    val freshness: FreshnessState,
    val ageMs: Long?,
    val userLabel: String?,
    val userNote: String?,
)

/** The exact result of a report build. Content is UTF-8 text for both formats. */
data class ExportResult(
    val format: ExportFormat,
    val reportType: ExportReportType,
    val content: String,
    val recordCount: Int,
    val suggestedFileName: String,
)

object ExportPolicy {

    const val SCHEMA_VERSION = 1

    /** Explicit unknown token used in CSV cells (JSON uses null). */
    const val UNKNOWN = "unknown"

    /**
     * Spreadsheet-formula defense: after skipping leading whitespace/control
     * characters, a value that starts with = + - @ TAB CR is prefixed with a
     * single apostrophe. Applied to EVERY text cell (user and pipeline text),
     * applied AFTER quoting decisions, documented in the WS06 doc. Empty input
     * stays empty (nothing to protect).
     */
    fun protectSpreadsheet(value: String): String {
        if (value.isEmpty()) return value
        val firstEffective = value.firstOrNull { !it.isWhitespace() && it.code >= 0x20 }
            ?: return value
        return if (firstEffective == '=' || firstEffective == '+' ||
            firstEffective == '-' || firstEffective == '@' ||
            firstEffective == '\t' || firstEffective == '\r'
        ) "'$value" else value
    }

    fun isValidTimestamp(epochMs: Long?): Boolean = epochMs != null && epochMs > 0L

    /** ISO-8601 UTC, locale-independent. Invalid/absent time -> null (never invented). */
    fun isoUtc(epochMs: Long?): String? {
        if (!isValidTimestamp(epochMs)) return null
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(epochMs!!))
    }

    /** CSV cell: RFC 4180 quoting (comma, quote, CR, LF) + formula protection. */
    fun csvCell(value: String): String {
        val safe = protectSpreadsheet(value)
        val needsQuotes = safe.contains(',') || safe.contains('"') ||
            safe.contains('\n') || safe.contains('\r')
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun jsonEscape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) {
                    sb.append("\\u").append(String.format(Locale.ROOT, "%04x", ch.code))
                } else sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun jsonString(value: String?): String =
        if (value == null) "null" else "\"" + jsonEscape(value) + "\""

    /**
     * Deterministic record ordering: newest SAVED first, then newest observed,
     * then radio name, then type hint (nulls last). No identity-based ordering.
     */
    fun ordered(records: List<HistoryRecord>): List<HistoryRecord> =
        records.sortedWith(
            compareByDescending<HistoryRecord> { it.savedAtEpochMs }
                .thenByDescending { it.observedAtEpochMs }
                .thenBy { it.radio.name }
                .thenBy(nullsLast()) { it.typeHint },
        )

    fun projected(record: HistoryRecord, options: ExportOptions): ExportRecord = ExportRecord(
        radio = record.radio,
        savedAtEpochMs = record.savedAtEpochMs,
        observedAtEpochMs = record.observedAtEpochMs,
        // A withheld name stays withheld: the field is null unless explicitly included.
        displayName = record.displayName.takeIf { options.includeNames },
        manufacturerHint = record.manufacturerHint,
        typeHint = record.typeHint,
        signalBucket = record.signalBucket,
        freshness = record.freshness,
        ageMs = record.ageMs,
        userLabel = null,
        userNote = null,
    )
}
