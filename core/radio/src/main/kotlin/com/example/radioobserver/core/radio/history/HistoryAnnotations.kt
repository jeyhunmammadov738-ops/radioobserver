package com.example.radioobserver.core.radio.history

/**
 * WORKSTREAM 04 — RECORD-BOUND annotations for SAVED History records.
 *
 * IDENTITY ANALYSIS (the reason this exists separately from HistoryRecord): a live
 * observation id identifies a row WITHIN one session; a History record id identifies
 * ONE stored record; a real physical device is NEITHER. An annotation is therefore
 * bound to a HISTORY RECORD ID only — the random UUID generated at save time, which
 * is deliberately NOT derived from any address (see [HistoryRecord]). An annotation
 * can NEVER become a persistent device identity: there is no address, name-pattern
 * or fingerprint field to key on, no matching of annotations to observations, and
 * no cross-session correlation of any kind. When its record expires or is deleted,
 * [HistoryAnnotationRepository.retainAll] drops the annotation; orphaned metadata
 * can never recreate a deleted record (it is simply not consulted by History).
 *
 * CONTENT: optional label (<= 40 chars), optional note (<= 200 chars) — both
 * user-entered local text treated as sensitive local data — plus a favorite flag.
 * No automatic labeling from radio data, no background updates, no cloud sync, no
 * export. Writes happen ONLY on explicit user actions.
 */
data class HistoryAnnotation(
    val recordId: String,
    val label: String?,
    val note: String?,
    val favorite: Boolean,
)

object HistoryAnnotationLimits {
    const val MAX_LABEL_LENGTH = 40
    const val MAX_NOTE_LENGTH = 200
    const val SCHEMA_VERSION = "ws04-annotations-v1"
    private const val MAX_LINE = 1000

    /** Validates a label: trimmed non-blank or null; at most 40 chars. */
    fun validateLabel(raw: String): String? = validateText(raw, MAX_LABEL_LENGTH)

    /** Validates a note: trimmed non-blank or null; at most 200 chars. */
    fun validateNote(raw: String): String? = validateText(raw, MAX_NOTE_LENGTH)

    /** Returns the trimmed text if valid (non-blank, within bound), else null. */
    fun validateText(raw: String, maxLength: Int): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > maxLength) return null
        return trimmed
    }

    internal fun isLineWithinBound(line: String): Boolean = line.length <= MAX_LINE
}

/** Result of an explicit annotation action (honest typed errors, no swallowing). */
sealed interface AnnotationResult {
    data class Success(val annotations: Map<String, HistoryAnnotation>) : AnnotationResult
    data class Error(val reason: AnnotationError) : AnnotationResult
}

enum class AnnotationError {
    /** A field exceeded its bound (label <= 40, note <= 200). */
    TOO_LONG,
    /** The target record id does not exist in the current History snapshot. */
    RECORD_NOT_FOUND,
}

/** Minimal persistence seam (same convention as presets and the user catalog). */
interface HistoryAnnotationPersistence {
    fun read(): String?
    fun write(text: String)
}

/**
 * Pure repository over the versioned codec. All mutations are EXPLICIT user actions
 * and persist atomically through the injected seam; [retainAll] keeps the store
 * exactly in sync with the live History record set (retention sweeps, capacity cap,
 * single deletes and Clear All all flow through it).
 */
class HistoryAnnotationRepository(
    private val persistence: HistoryAnnotationPersistence,
) {
    private var cached: MutableMap<String, HistoryAnnotation> = decode()

    /** Malformed/duplicate/oversized records dropped at load (honest diagnostic). */
    val lastLoadIssues: Int = run {
        val text = persistence.read() ?: return@run 0
        AnnotationCodec.decode(text).second
    }

    fun get(recordId: String): HistoryAnnotation? = cached[recordId]

    fun list(): Map<String, HistoryAnnotation> = cached.toMap()

    /**
     * Creates or updates the annotation of ONE record. [knownRecordIds] must be the
     * current History record snapshot; a missing record id is rejected honestly.
     */
    fun set(
        recordId: String,
        label: String?,
        note: String?,
        favorite: Boolean,
        knownRecordIds: Set<String>,
    ): AnnotationResult {
        if (recordId !in knownRecordIds) return AnnotationResult.Error(AnnotationError.RECORD_NOT_FOUND)
        val validLabel = label?.let { HistoryAnnotationLimits.validateLabel(it) }
            ?: label?.takeIf { it.isBlank() } // blank clears the field
        val validNote = note?.let { HistoryAnnotationLimits.validateNote(it) }
            ?: note?.takeIf { it.isBlank() }
        if (label != null && validLabel == null && label.isNotBlank()) {
            return AnnotationResult.Error(AnnotationError.TOO_LONG)
        }
        if (note != null && validNote == null && note.isNotBlank()) {
            return AnnotationResult.Error(AnnotationError.TOO_LONG)
        }
        val annotation = HistoryAnnotation(
            recordId = recordId,
            label = validLabel?.takeIf { it.isNotBlank() },
            note = validNote?.takeIf { it.isNotBlank() },
            favorite = favorite,
        )
        cached[recordId] = annotation
        return persist()
    }

    /** Explicitly removes one record's annotation (single History delete path). */
    fun delete(recordId: String): AnnotationResult {
        cached.remove(recordId)
        return persist()
    }

    /**
     * Retention/expiration/deletion sync: drop every annotation whose record is no
     * longer in [knownRecordIds]. Returns how many were dropped (0 = no write).
     */
    fun retainAll(knownRecordIds: Set<String>): Int {
        val stale = cached.keys - knownRecordIds
        if (stale.isEmpty()) return 0
        stale.forEach { cached.remove(it) }
        persist()
        return stale.size
    }

    private fun persist(): AnnotationResult {
        persistence.write(AnnotationCodec.encode(cached))
        return AnnotationResult.Success(cached.toMap())
    }

    private fun decode(): MutableMap<String, HistoryAnnotation> {
        val text = persistence.read() ?: return mutableMapOf()
        val entries = AnnotationCodec.decode(text).first
        return mutableMapOf(*entries.map { it.recordId to it }.toTypedArray())
    }
}

/**
 * Versioned, line-oriented codec (same safety conventions as the WS03B catalog).
 * File layout:
 *   line 1: ws04-annotations-v1
 *   record: recordId|label|note|favorite   ('-' = null; text escaped for '|', '\n')
 * Unsupported version -> EMPTY, and the caller must NOT delete or rewrite the
 * original file. Malformed/oversized lines are dropped AND counted; duplicates keep
 * the first; nothing can throw.
 */
object AnnotationCodec {

    fun encode(annotations: Map<String, HistoryAnnotation>): String = buildString {
        append(HistoryAnnotationLimits.SCHEMA_VERSION).append('\n')
        annotations.values.sortedBy { it.recordId }.forEach { a ->
            append(a.recordId).append('|')
            append(escape(a.label ?: "-")).append('|')
            append(escape(a.note ?: "-")).append('|')
            append(if (a.favorite) "1" else "0").append('\n')
        }
    }

    /** Returns (annotations, droppedCount) — never throws, never rewrites input. */
    fun decode(text: String): Pair<List<HistoryAnnotation>, Int> {
        val lines = text.split('\n')
        if (lines.isEmpty() || lines[0].trim() != HistoryAnnotationLimits.SCHEMA_VERSION) {
            return emptyList<HistoryAnnotation>() to 0
        }
        val out = ArrayList<HistoryAnnotation>()
        val seen = HashSet<String>()
        var dropped = 0
        for (i in 1 until lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            if (!HistoryAnnotationLimits.isLineWithinBound(raw)) { dropped++; continue }
            val parts = raw.split('|')
            if (parts.size != 4) { dropped++; continue }
            val recordId = parts[0]
            val label = unescape(parts[1])
            val note = unescape(parts[2])
            val favorite = parts[3] == "1"
            if (recordId.isBlank() || parts[3] != "1" && parts[3] != "0" ||
                (label != "-" && label.length > HistoryAnnotationLimits.MAX_LABEL_LENGTH) ||
                (note != "-" && note.length > HistoryAnnotationLimits.MAX_NOTE_LENGTH)
            ) { dropped++; continue }
            if (!seen.add(recordId)) { dropped++; continue }
            out.add(
                HistoryAnnotation(
                    recordId = recordId,
                    label = if (label == "-") null else label,
                    note = if (note == "-") null else note,
                    favorite = favorite,
                ),
            )
        }
        return out to dropped
    }

    private fun escape(s: String) = s
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun unescape(s: String): String {
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> out.append('\\')
                    'p' -> out.append('|')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    else -> { out.append(c); out.append(s[i + 1]) }
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
