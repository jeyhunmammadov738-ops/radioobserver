package com.example.radioobserver.core.radio.history

import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * Compact, corruption-tolerant line codec for the history store (design doc: fail closed
 * to privacy — a fully unreadable store is dropped, individual corrupt LINES are
 * skipped and counted, and the store is rewritten without them on the next write).
 *
 * Format: one header line, one consent line, then one line per record, all ending in \n.
 * Field separator is '|'; text fields escape '\', '|' and newlines so user-visible
 * strings can never break the structure. Unknown values stay unknown; a line with a
 * wrong field count or unparsable token is DROPPED, never crashed on, never guessed.
 */
object HistoryCodec {

    const val HEADER = "#radioobserver-history-v1"
    private const val SEP = '|'

    data class DecodeResult(
        val consent: HistoryConsent,
        val records: List<HistoryRecord>,
        /** Lines skipped as corrupt/unknown — surfaced honestly, never silently ignored. */
        val droppedLines: Int,
        /** True when the store was so unreadable that everything was dropped (fail closed). */
        val failedClosed: Boolean,
    )

    fun encode(consent: HistoryConsent, records: List<HistoryRecord>): String = buildString {
        append(HEADER).append('\n')
        append(encodeConsent(consent)).append('\n')
        for (r in records) append(encodeRecord(r)).append('\n')
    }

    fun decode(text: String?): DecodeResult {
        if (text.isNullOrBlank()) return DecodeResult(HistoryConsent(), emptyList(), 0, false)
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != HEADER) {
            // Not a store we can understand: fail closed to privacy — nothing is kept.
            return DecodeResult(HistoryConsent(), emptyList(), lines.size, true)
        }
        var dropped = 0
        var consent: HistoryConsent? = null
        val records = mutableListOf<HistoryRecord>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val decodedConsent = decodeConsent(line)
            if (decodedConsent != null) {
                consent = decodedConsent
                continue
            }
            val record = decodeRecord(line)
            if (record != null) records += record else dropped += 1
        }
        return DecodeResult(consent ?: HistoryConsent(), records, dropped, false)
    }

    private fun encodeConsent(c: HistoryConsent) = listOf(
        "consent",
        c.recordingEnabled.toString(),
        c.wifiNamesEnabled.toString(),
        normalizeRetention(c.retentionDays).toString(),
    ).joinToString(SEP.toString())

    private fun decodeConsent(line: String): HistoryConsent? {
        val parts = split(line)
        if (parts.size != 4 || parts[0] != "consent") return null
        val enabled = parts[1].toBooleanStrictOrNull() ?: return null
        val wifi = parts[2].toBooleanStrictOrNull() ?: return null
        val days = parts[3].toIntOrNull()?.let(::normalizeRetention) ?: return null
        return HistoryConsent(enabled, wifi, days)
    }

    private fun encodeRecord(r: HistoryRecord) = listOf(
        "rec",
        escape(r.id),
        r.radio.name,
        r.savedAtEpochMs.toString(),
        r.observedAtEpochMs.toString(),
        escape(r.displayName.orEmpty()),
        escape(r.manufacturerHint.orEmpty()),
        escape(r.typeHint.orEmpty()),
        r.signalBucket.name,
        r.freshness.name,
        r.ageMs?.toString() ?: "",
    ).joinToString(SEP.toString())

    private fun decodeRecord(line: String): HistoryRecord? {
        val parts = split(line)
        if (parts.size != 11 || parts[0] != "rec") return null
        val id = unescape(parts[1])
        val radio = runCatching { RadioType.valueOf(parts[2]) }.getOrNull() ?: return null
        val savedAt = parts[3].toLongOrNull() ?: return null
        val observedAt = parts[4].toLongOrNull() ?: return null
        val bucket = runCatching { SignalBucket.valueOf(parts[8]) }.getOrNull() ?: return null
        val freshness = runCatching { FreshnessState.valueOf(parts[9]) }.getOrNull() ?: return null
        val age = parts[10].takeIf { it.isNotEmpty() }?.toLongOrNull()
        return HistoryRecord(
            id = id,
            radio = radio,
            savedAtEpochMs = savedAt,
            observedAtEpochMs = observedAt,
            displayName = unescape(parts[5]).takeIf { it.isNotEmpty() },
            manufacturerHint = unescape(parts[6]).takeIf { it.isNotEmpty() },
            typeHint = unescape(parts[7]).takeIf { it.isNotEmpty() },
            signalBucket = bucket,
            freshness = freshness,
            ageMs = age,
        )
    }

    private fun normalizeRetention(days: Int) =
        if (days in HistoryLimits.ALLOWED_RETENTION_DAYS) days else HistoryLimits.DEFAULT_RETENTION_DAYS

    /**
     * RAW split on the separator only: escape pairs are kept VERBATIM so the field
     * values can be unescaped exactly once later. Real newlines cannot appear (they
     * are escaped); a trailing lone backslash marks a truncated/corrupt line.
     */
    private fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '\\' -> {
                    if (i + 1 >= line.length) return emptyList() // truncated escape: corrupt
                    sb.append(ch).append(line[i + 1]) // keep the escape pair verbatim
                    i += 2
                }
                ch == SEP -> {
                    out += sb.toString(); sb.setLength(0); i += 1
                }
                else -> {
                    if (ch == '\n' || ch == '\r') return emptyList()
                    sb.append(ch); i += 1
                }
            }
        }
        out += sb.toString()
        return out
    }

    private fun escape(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                SEP -> append("\\p")
                '\n' -> append("\\n")
                '\r' -> append("\\n") // normalized to one newline class
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String): String = buildString {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    '\\' -> append('\\')
                    'p' -> append(SEP)
                    'n' -> append('\n')
                    else -> append(value[i + 1])
                }
                i += 2
            } else {
                append(ch); i += 1
            }
        }
    }
}
