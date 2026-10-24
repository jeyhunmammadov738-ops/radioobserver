package com.example.radioobserver.core.radio.history

/**
 * WORKSTREAM 05 — NAMED ANALYTICAL GROUPS over saved History records.
 *
 * IDENTITY ANALYSIS (mandatory, documented): an analytical group is a USER-CREATED
 * analytical grouping of ALREADY-SAVED History record ids. It is NOT a new radio
 * scanning session, NOT a live UI observation session, and NOT a physical-device
 * identity. Group ids are application-generated metadata ids ("G-0001", ...), never
 * derived from addresses, names, payloads or timestamps. A group references ONLY
 * existing saved History record ids — raw radio data is NEVER duplicated into the
 * group store, and two records in one group are NEVER claimed to be the same
 * physical device (no cross-session device matching exists here or anywhere else).
 *
 * LEGACY HONESTY: existing records have NO trustworthy session link, so none is
 * fabricated; membership is always EXPLICIT user selection. A group of legacy
 * records is an analytical selection, never a "historically verified scanning
 * session" — the UI must say exactly that.
 *
 * CONSENT: creating/renaming/deleting a group NEVER starts recording, never saves
 * live observations, never dispatches a radio action, and never exempts records
 * from retention/deletion. Group metadata about lawfully saved records follows the
 * existing app-private storage policy.
 */
data class AnalyticalGroup(
    /** App-generated metadata id ("G-0001", ...) — never a device identifier. */
    val id: String,
    /** User-entered bounded name (<= 40 chars, trimmed, non-blank). */
    val name: String,
    /** EXPLICIT saved History record references only (sorted, deduplicated). */
    val recordIds: List<String>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

object SessionGroupLimits {
    /** Bounded group count (verified against the 500-record convention). */
    const val MAX_GROUPS = 50
    const val MAX_NAME_LENGTH = 40
    const val SCHEMA_VERSION = "ws05-groups-v1"
}

/** Honest typed results for explicit group actions (no swallowing). */
sealed interface GroupResult {
    data class Success(val groups: List<AnalyticalGroup>) : GroupResult
    data class Error(val reason: GroupError) : GroupResult
}

enum class GroupError {
    NAME_BLANK,
    NAME_TOO_LONG,
    GROUP_LIMIT_REACHED,
    NOT_FOUND,
    RECORD_NOT_SAVED,
    ALREADY_MEMBER,
}

/** Minimal persistence seam (same conventions as annotations and presets). */
interface SessionGroupPersistence {
    fun read(): String?
    fun write(text: String)
}

/**
 * Pure repository over the versioned codec. All mutations are explicit user
 * actions; [syncWithRecords] keeps membership exactly aligned with the live
 * History record set (dangling references are dropped, never resurrected).
 */
class SessionGroupRepository(
    private val persistence: SessionGroupPersistence,
    private val clock: () -> Long = { 0L },
) {
    private var cached: MutableList<AnalyticalGroup> = decode()

    /** Corrupt/duplicate/oversized entries dropped at load (honest diagnostic). */
    val lastLoadIssues: Int = run {
        val text = persistence.read() ?: return@run 0
        GroupCodec.decode(text).second
    }

    fun list(): List<AnalyticalGroup> = cached.sortedBy { it.id }.toList()

    fun get(id: String): AnalyticalGroup? = cached.firstOrNull { it.id == id }

    fun create(name: String): GroupResult {
        val validName = validateName(name) ?: return GroupResult.Error(GroupError.NAME_BLANK)
        if (cached.size >= SessionGroupLimits.MAX_GROUPS) {
            return GroupResult.Error(GroupError.GROUP_LIMIT_REACHED)
        }
        val group = AnalyticalGroup(
            id = nextId(),
            name = validName,
            recordIds = emptyList(),
            createdAtEpochMs = clock(),
            updatedAtEpochMs = clock(),
        )
        cached.add(group)
        return persist()
    }

    fun rename(id: String, name: String): GroupResult {
        val validName = validateName(name) ?: return GroupResult.Error(GroupError.NAME_BLANK)
        val index = indexOf(id) ?: return GroupResult.Error(GroupError.NOT_FOUND)
        cached[index] = cached[index].copy(name = validName, updatedAtEpochMs = clock())
        return persist()
    }

    /**
     * EXPLICIT membership: adds ONE already-saved record id. [knownRecordIds] is
     * the current History snapshot; unknown ids are rejected honestly (no
     * fabricated references, no silent acceptance).
     */
    fun addRecord(id: String, recordId: String, knownRecordIds: Set<String>): GroupResult {
        val index = indexOf(id) ?: return GroupResult.Error(GroupError.NOT_FOUND)
        if (recordId !in knownRecordIds) return GroupResult.Error(GroupError.RECORD_NOT_SAVED)
        val group = cached[index]
        if (recordId in group.recordIds) return GroupResult.Error(GroupError.ALREADY_MEMBER)
        cached[index] = group.copy(
            recordIds = (group.recordIds + recordId).sorted(),
            updatedAtEpochMs = clock(),
        )
        return persist()
    }

    /** Explicit removal of ONE record reference (does NOT delete the record). */
    fun removeRecord(id: String, recordId: String): GroupResult {
        val index = indexOf(id) ?: return GroupResult.Error(GroupError.NOT_FOUND)
        val group = cached[index]
        if (recordId !in group.recordIds) return GroupResult.Error(GroupError.NOT_FOUND)
        cached[index] = group.copy(
            recordIds = group.recordIds - recordId,
            updatedAtEpochMs = clock(),
        )
        return persist()
    }

    /**
     * Delete the GROUP metadata only (never the underlying History records — that
     * combined destructive action does not exist and would need separate
     * authorization and confirmation).
     */
    fun delete(id: String): GroupResult {
        val index = indexOf(id) ?: return GroupResult.Error(GroupError.NOT_FOUND)
        cached.removeAt(index)
        return persist()
    }

    /**
     * Retention/deletion reconciliation: drop every membership reference whose
     * record no longer exists in [knownRecordIds]. Groups may become empty (honest
     * empty state; user metadata preserved). Returns removed-reference count.
     */
    fun syncWithRecords(knownRecordIds: Set<String>): Int {
        var removed = 0
        var changed = false
        val updated = cached.map { g ->
            val kept = g.recordIds.filter { it in knownRecordIds }
            if (kept.size != g.recordIds.size) {
                removed += g.recordIds.size - kept.size
                changed = true
                g.copy(recordIds = kept, updatedAtEpochMs = clock())
            } else g
        }
        if (!changed) return 0
        cached = updated.toMutableList()
        persist()
        return removed
    }

    private fun indexOf(id: String): Int? {
        val i = cached.indexOfFirst { it.id == id }
        return if (i < 0) null else i
    }

    private fun nextId(): String {
        var n = 1
        val existing = cached.map { it.id }.toSet()
        while ("G-" + n.toString().padStart(4, '0') in existing) n++
        return "G-" + n.toString().padStart(4, '0')
    }

    private fun validateName(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > SessionGroupLimits.MAX_NAME_LENGTH) return null
        return trimmed
    }

    private fun persist(): GroupResult {
        persistence.write(GroupCodec.encode(cached))
        return GroupResult.Success(list())
    }

    private fun decode(): MutableList<AnalyticalGroup> {
        val text = persistence.read() ?: return mutableListOf()
        return GroupCodec.decode(text).first.toMutableList()
    }
}

/**
 * Versioned line-oriented codec (identical safety conventions to the WS04
 * annotation codec). Layout:
 *   line 1: ws05-groups-v1
 *   group: id|name|recordIds(,)|createdAt|updatedAt   ('-' = empty list; text
 * escaped for '|', ',', '\n'). Unsupported version -> EMPTY, original NEVER
 * rewritten. Corrupt/duplicate/oversized lines dropped AND counted. A load that
 * exceeds [SessionGroupLimits.MAX_GROUPS] keeps the first 50 (by id) and counts
 * the rest as dropped. Missing file -> empty.
 */
object GroupCodec {

    fun encode(groups: List<AnalyticalGroup>): String = buildString {
        append(SessionGroupLimits.SCHEMA_VERSION).append('\n')
        groups.sortedBy { it.id }.forEach { g ->
            append(g.id).append('|')
            append(escape(g.name)).append('|')
            append(if (g.recordIds.isEmpty()) "-" else g.recordIds.joinToString(",") { escape(it) })
                .append('|')
            append(g.createdAtEpochMs).append('|')
            append(g.updatedAtEpochMs).append('\n')
        }
    }

    /** Returns (groups, droppedCount) — never throws, never rewrites input. */
    fun decode(text: String): Pair<List<AnalyticalGroup>, Int> {
        val lines = text.split('\n')
        if (lines.isEmpty() || lines[0].trim() != SessionGroupLimits.SCHEMA_VERSION) {
            return emptyList<AnalyticalGroup>() to 0
        }
        val out = ArrayList<AnalyticalGroup>()
        val seenIds = HashSet<String>()
        var dropped = 0
        for (i in 1 until lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            if (raw.length > 2000) { dropped++; continue }
            val parts = raw.split('|')
            if (parts.size != 5) { dropped++; continue }
            val id = parts[0]
            val name = unescape(parts[1])
            val createdAt = parts[3].toLongOrNull()
            val updatedAt = parts[4].toLongOrNull()
            val recordIds = if (parts[2] == "-") emptyList() else parts[2].split(',').map { unescape(it) }
            if (id.isBlank() || !id.startsWith("G-") || name.isBlank() ||
                name.length > SessionGroupLimits.MAX_NAME_LENGTH ||
                createdAt == null || updatedAt == null || createdAt < 0 || updatedAt < 0
            ) { dropped++; continue }
            if (recordIds.size != recordIds.distinct().size) { dropped++; continue }
            if (!seenIds.add(id)) { dropped++; continue }
            if (out.size >= SessionGroupLimits.MAX_GROUPS) { dropped++; continue }
            out.add(
                AnalyticalGroup(
                    id = id,
                    name = name,
                    recordIds = recordIds.distinct().sorted(),
                    createdAtEpochMs = createdAt,
                    updatedAtEpochMs = updatedAt,
                ),
            )
        }
        return out to dropped
    }

    private fun escape(s: String) = s
        .replace("\\", "\\\\")
        .replace("|", "\\p")
        .replace(",", "\\c")
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
                    'c' -> out.append(',')
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
