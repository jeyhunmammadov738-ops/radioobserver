package com.example.radioobserver.core.radio.signature

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType

/**
 * WORKSTREAM 03B — privacy-safe USER-DEFINED classification rules.
 *
 * Trust boundary (structural, same philosophy as WS03A): a user rule can only be
 * built from a FINITE, CODE-OWNED whitelist of categorical selectors — a radio
 * source (or any), one of the parser's own public-format type-hint tokens (or
 * any), and a format confidence (or any) — mapping to one fixed broad family
 * category. There is deliberately NO free-text matching value, NO substring or
 * regex field, and NO way to key a rule to an observed device, name, SSID, address,
 * session id, RSSI, location or fingerprint: the types make such a rule
 * unrepresentable. The optional [title] exists for RULE MANAGEMENT display only and
 * is never used in matching (JVM-tested).
 *
 * Persistence: versioned app-private plain text (see [UserRuleCodec]); writes
 * happen ONLY on explicit user actions (create/edit/toggle/delete); loading is
 * fail-closed (corrupt lines are dropped+counted, an unsupported version yields
 * an empty catalog WITHOUT deleting or rewriting the original file).
 */
object UserSignatureRules {
    /** Bounded user-rule count (project convention: presets use 20; rules 30). */
    const val MAX_RULES = 30

    /** Max management-title length (titles are display-only, never identifiers). */
    const val MAX_TITLE_LENGTH = 40

    /** Codec/file schema version shipped with every stored file. */
    const val SCHEMA_VERSION = "ws03b-user-catalog-v1"

    /** Stable generated-id prefix; ids are rule bookkeeping, NOT device identity. */
    const val ID_PREFIX = "U-"

    /**
     * Validates a management title: trimmed, non-blank, at most 40 chars.
     * Returns the trimmed valid title or null (invalid).
     */
    fun validateTitle(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.length > MAX_TITLE_LENGTH) return null
        return trimmed
    }

    /** Next stable sequence id given the existing rules (deterministic). */
    fun nextId(existing: List<UserSignatureRule>): String {
        val max = existing
            .mapNotNull { r -> r.id.removePrefix(ID_PREFIX).toIntOrNull() }
            .maxOrNull() ?: 0
        return ID_PREFIX + (max + 1).toString().padStart(2, '0')
    }
}

/**
 * The FINITE, code-owned whitelist of type-hint tokens a user rule may select.
 * Every token is an exact label Radio Observer's own Phase-1 parser can emit from
 * publicly documented formats — never user-typed text, never a name, never a
 * substring pattern.
 */
enum class UserRuleTypeToken(val label: String) {
    IBEACON_LAYOUT("iBeacon (Apple layout)"),
    IBEACON_LIKE_MISMATCH("Apple iBeacon-like prefix (length mismatch)"),
    FIND_MY_SEPARATED("Find My separated-style advertisement (Apple layout)"),
    FIND_MY_LIKE_MISMATCH("Apple Find My-like prefix (length mismatch)"),
}

/**
 * Categorical selector of a user rule. Null means "any"; matching is exact
 * enum/equality based (Unicode-folded only for the code-owned label), never
 * substring or regex.
 */
data class UserRuleSelector(
    val radio: RadioType?,
    val typeToken: UserRuleTypeToken?,
    val confidence: ObservationConfidence?,
) {
    companion object {
        val ANY = UserRuleSelector(null, null, null)
    }
}

/** One user-defined rule. [title] is management display only — never matched. */
data class UserSignatureRule(
    val id: String,
    val title: String,
    val selector: UserRuleSelector,
    val category: SignatureCategory,
    val enabled: Boolean,
)

/** Outcome of an explicit user action on the repository (honest errors). */
sealed interface UserRuleResult {
    data class Success(val rules: List<UserSignatureRule>) : UserRuleResult
    data class Error(val messageKey: UserRuleError) : UserRuleResult
}

enum class UserRuleError {
    INVALID_TITLE,
    LIMIT_REACHED,
    NOT_FOUND,
}

/** Minimal persistence seam (same convention as WS02 filter presets). */
interface UserRulePersistence {
    fun read(): String?
    fun write(text: String)
}

/**
 * Pure repository over the versioned codec. Every mutating method is an EXPLICIT
 * user action; nothing observes the live feed and no rule is ever auto-created.
 * All writes persist atomically through the injected persistence.
 */
class UserSignatureRuleRepository(
    private val persistence: UserRulePersistence,
) {
    private var cached: List<UserSignatureRule> = decode()

    /** Number of malformed/duplicate/overflow records dropped at load (diagnostic). */
    val lastLoadIssues: Int = run {
        val text = persistence.read() ?: return@run 0
        UserRuleCodec.decode(text).second
    }

    fun list(): List<UserSignatureRule> = cached

    fun create(title: String, selector: UserRuleSelector, category: SignatureCategory): UserRuleResult {
        val validTitle = UserSignatureRules.validateTitle(title)
            ?: return UserRuleResult.Error(UserRuleError.INVALID_TITLE)
        if (cached.size >= UserSignatureRules.MAX_RULES) {
            return UserRuleResult.Error(UserRuleError.LIMIT_REACHED)
        }
        val rule = UserSignatureRule(
            id = UserSignatureRules.nextId(cached),
            title = validTitle,
            selector = selector,
            category = category,
            enabled = true,
        )
        return persist(cached + rule)
    }

    fun update(id: String, title: String, selector: UserRuleSelector, category: SignatureCategory): UserRuleResult {
        val validTitle = UserSignatureRules.validateTitle(title)
            ?: return UserRuleResult.Error(UserRuleError.INVALID_TITLE)
        val index = cached.indexOfFirst { it.id == id }
        if (index < 0) return UserRuleResult.Error(UserRuleError.NOT_FOUND)
        val updated = cached[index].copy(
            title = validTitle, selector = selector, category = category,
        )
        return persist(cached.toMutableList().apply { set(index, updated) })
    }

    fun setEnabled(id: String, enabled: Boolean): UserRuleResult {
        val index = cached.indexOfFirst { it.id == id }
        if (index < 0) return UserRuleResult.Error(UserRuleError.NOT_FOUND)
        val updated = cached[index].copy(enabled = enabled)
        return persist(cached.toMutableList().apply { set(index, updated) })
    }

    fun delete(id: String): UserRuleResult {
        if (cached.none { it.id == id }) return UserRuleResult.Error(UserRuleError.NOT_FOUND)
        return persist(cached.filter { it.id != id })
    }

    private fun persist(next: List<UserSignatureRule>): UserRuleResult {
        // Atomic write: encode the COMPLETE next state, single write seam.
        persistence.write(UserRuleCodec.encode(next))
        cached = next
        return UserRuleResult.Success(next)
    }

    private fun decode(): List<UserSignatureRule> {
        val text = persistence.read() ?: return emptyList()
        return UserRuleCodec.decode(text).first
    }
}

/**
 * Versioned, line-oriented codec. File layout:
 *   line 1: version header (must equal [UserSignatureRules.SCHEMA_VERSION])
 *   rule lines: id|radio|token|confidence|category|title|enabled  ('-' = any/null)
 * Titles are escaped for '|' and newlines. Corrupt lines are dropped and COUNTED
 * without touching the rest; duplicate ids keep the FIRST record; records beyond
 * MAX_RULES are dropped; an unsupported version fails CLOSED (empty result) and
 * the caller must NOT delete or rewrite the original file.
 */
object UserRuleCodec {

    private val RADIO_TOKENS = RadioType.entries.associateBy { it.name }
    private val CONF_TOKENS = ObservationConfidence.entries.associateBy { it.name }
    private val CAT_TOKENS = SignatureCategory.entries.associateBy { it.name }
    private val TYPE_TOKENS = UserRuleTypeToken.entries.associateBy { it.name }
    private const val MAX_LINE = 500

    fun encode(rules: List<UserSignatureRule>): String = buildString {
        append(UserSignatureRules.SCHEMA_VERSION).append('\n')
        rules.forEach { r ->
            append(r.id).append('|')
            append(r.selector.radio?.name ?: "-").append('|')
            append(r.selector.typeToken?.name ?: "-").append('|')
            append(r.selector.confidence?.name ?: "-").append('|')
            append(r.category.name).append('|')
            append(escape(r.title)).append('|')
            append(if (r.enabled) "1" else "0").append('\n')
        }
    }

    /** Returns (rules, droppedCount) — never throws, never rewrites input. */
    fun decode(text: String): Pair<List<UserSignatureRule>, Int> {
        val lines = text.split('\n')
        if (lines.isEmpty() || lines[0].trim() != UserSignatureRules.SCHEMA_VERSION) {
            return emptyList<UserSignatureRule>() to 0
        }
        val rules = ArrayList<UserSignatureRule>()
        var dropped = 0
        val seenIds = HashSet<String>()
        for (i in 1 until lines.size) {
            val raw = lines[i]
            if (raw.isBlank()) continue
            if (raw.length > MAX_LINE) { dropped++; continue }
            val parts = raw.split('|')
            if (parts.size != 7) { dropped++; continue }
            val id = parts[0]
            val radio = if (parts[1] == "-") null else RADIO_TOKENS[parts[1]]
            val token = if (parts[2] == "-") null else TYPE_TOKENS[parts[2]]
            val confidence = if (parts[3] == "-") null else CONF_TOKENS[parts[3]]
            val category = CAT_TOKENS[parts[4]]
            val title = unescape(parts[5])
            val enabled = parts[6] == "1"
            if (id.isBlank() || !id.startsWith(UserSignatureRules.ID_PREFIX) || category == null ||
                UserSignatureRules.validateTitle(title) == null ||
                (parts[1] != "-" && radio == null) ||
                (parts[2] != "-" && token == null) ||
                (parts[3] != "-" && confidence == null)
            ) { dropped++; continue }
            if (!seenIds.add(id)) { dropped++; continue } // duplicate id: first wins
            if (rules.size >= UserSignatureRules.MAX_RULES) { dropped++; continue }
            rules.add(
                UserSignatureRule(
                    id = id,
                    title = title.trim(),
                    selector = UserRuleSelector(radio, token, confidence),
                    category = category,
                    enabled = enabled,
                ),
            )
        }
        return rules to dropped
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
