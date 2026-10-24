package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * WORKSTREAM 02 — saved filter presets: pure Kotlin/JVM model, codec and repository
 * (zero android.* imports). Presets are CONFIGURATION, never observation history:
 * a preset stores ONLY its id, user-supplied name, the restored source filter and
 * versioned categorical criteria. It MUST NOT contain MAC addresses, BSSIDs, raw
 * advertising data, device session ids, observation snapshots, coordinates,
 * persistent device fingerprints or any search text — the codec structurally has no
 * field for any of those.
 *
 * Preset ids are repository-generated bookkeeping ("fp-<epoch>-<ordinal>"),
 * completely unrelated to radio device identity; they never track devices.
 */
object FilterPresets {

    /** One saved profile. [name] is user-supplied, validated by the repository. */
    data class FilterPreset(
        val id: String,
        val name: String,
        val version: Int = VERSION,
        val sourceFilter: ObservationFeedProjection.FeedFilter =
            ObservationFeedProjection.FeedFilter.ALL,
        val criteria: LiveFilter.AdvancedCriteria = LiveFilter.AdvancedCriteria(),
    )

    interface Persistence {
        fun read(): String?
        fun write(text: String)
    }

    const val VERSION = 1

    /**
     * Compact, corruption-tolerant line codec (same style as the history codec):
     * one header line, then one line per preset, all ending in \n. Field separator
     * '|'; text fields escape '\', '|' and newlines so user names can never break
     * the structure. A malformed line is DROPPED and counted, never crashed on,
     * never guessed; an unknown header/version fails closed to an empty list
     * WITHOUT rewriting or deleting the stored file (no unrelated user data loss).
     */
    object Codec {
        const val HEADER = "#radioobserver-presets-v1"
        private const val SEP = '|'

        data class DecodeResult(
            val presets: List<FilterPreset>,
            /** Lines skipped as corrupt/unknown — surfaced honestly. */
            val droppedLines: Int,
            /** True when nothing was understandable at all (fail closed, file untouched). */
            val failedClosed: Boolean,
        )

        fun encode(presets: List<FilterPreset>): String = buildString {
            append(HEADER).append('\n')
            for (p in presets) append(encodePreset(p)).append('\n')
        }

        fun decode(text: String?): DecodeResult {
            if (text.isNullOrBlank()) return DecodeResult(emptyList(), 0, false)
            val lines = text.lines()
            if (lines.firstOrNull()?.trim() != HEADER) {
                return DecodeResult(emptyList(), lines.size, true)
            }
            var dropped = 0
            val presets = mutableListOf<FilterPreset>()
            for (line in lines.drop(1)) {
                if (line.isBlank()) continue
                val preset = decodePreset(line)
                if (preset != null) presets += preset else dropped += 1
            }
            return DecodeResult(presets, dropped, false)
        }

        private fun encodePreset(p: FilterPreset): String = listOf(
            escape(p.id),
            escape(p.name),
            p.version.toString(),
            p.sourceFilter.name,
            p.criteria.signalBuckets.joinToString(",") { it.name },
            p.criteria.freshness.joinToString(",") { it.name },
            when (p.criteria.manufacturerKnownOnly) {
                true -> "K"
                false -> "U"
                null -> "-"
            },
            p.criteria.manufacturerValues.joinToString(",") { escape(it) },
        ).joinToString(SEP.toString())

        private fun decodePreset(line: String): FilterPreset? {
            val fields = line.split(SEP)
            if (fields.size != 8) return null
            val id = unescapeOrNull(fields[0]) ?: return null
            val name = unescapeOrNull(fields[1]) ?: return null
            val version = fields[2].toIntOrNull() ?: return null
            if (version != VERSION) return null
            val source = when (fields[3]) {
                ObservationFeedProjection.FeedFilter.ALL.name ->
                    ObservationFeedProjection.FeedFilter.ALL
                ObservationFeedProjection.FeedFilter.BLUETOOTH.name ->
                    ObservationFeedProjection.FeedFilter.BLUETOOTH
                ObservationFeedProjection.FeedFilter.WIFI.name ->
                    ObservationFeedProjection.FeedFilter.WIFI
                else -> return null
            }
            val buckets = decodeEnumSet<SignalBucket>(fields[4]) ?: return null
            val freshness = decodeEnumSet<FreshnessState>(fields[5]) ?: return null
            val known = when (fields[6]) {
                "K" -> true
                "U" -> false
                "-" -> null
                else -> return null
            }
            val values = fields[7].split(",")
                .filter { it.isNotBlank() }
                .mapNotNull { unescapeOrNull(it) }
                .toSet()
            if (name.isBlank() || name.length > Repository.MAX_NAME_LENGTH) return null
            return FilterPreset(
                id = id,
                name = name,
                version = version,
                sourceFilter = source,
                criteria = LiveFilter.AdvancedCriteria(
                    signalBuckets = buckets,
                    freshness = freshness,
                    manufacturerKnownOnly = known,
                    manufacturerValues = values,
                ),
            )
        }

        private inline fun <reified T : Enum<T>> decodeEnumSet(field: String): Set<T>? {
            if (field.isBlank()) return emptySet()
            val out = mutableSetOf<T>()
            for (token in field.split(",")) {
                val value = enumValues<T>().firstOrNull { it.name == token } ?: return null
                out += value
            }
            return out
        }

        private fun escape(s: String): String = s
            .replace("\\", "\\\\")
            .replace("|", "\\p")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

        private fun unescapeOrNull(s: String): String? {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\') {
                    if (i + 1 >= s.length) return null
                    when (val next = s[i + 1]) {
                        '\\' -> sb.append('\\')
                        'p' -> sb.append('|')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        else -> return null
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i += 1
                }
            }
            return sb.toString()
        }
    }

    /**
     * Owner of the preset list. All mutations are explicit user actions; filters are
     * never auto-saved. Bound: [MAX_PRESETS]. Names: trimmed, non-blank, at most
     * [MAX_NAME_LENGTH] characters; duplicate names are rejected deterministically
     * (same outcome every time, for both save and rename).
     */
    class Repository(private val persistence: Persistence) {

        private var presets: List<FilterPreset> = load()

        private fun load(): List<FilterPreset> {
            val decoded = Codec.decode(persistence.read())
            // Deterministic defenses on load: duplicate ids keep the FIRST; a
            // duplicate name also keeps the FIRST. Corrupt lines were already
            // dropped and counted by the codec — the stored file is NOT touched
            // here (it is rewritten only on the next successful user mutation).
            val seenIds = mutableSetOf<String>()
            val seenNames = mutableSetOf<String>()
            return decoded.presets.filter { p ->
                seenIds.add(p.id) && seenNames.add(LiveFilter.fold(p.name))
            }
        }

        sealed class Outcome {
            data class Saved(val preset: FilterPreset) : Outcome()
            data class Renamed(val preset: FilterPreset) : Outcome()
            object Deleted : Outcome()
            object NotFound : Outcome()
            object InvalidName : Outcome()
            object DuplicateName : Outcome()
            object TooManyPresets : Outcome()
        }

        fun list(): List<FilterPreset> = presets.toList()

        fun findById(id: String): FilterPreset? = presets.firstOrNull { it.id == id }

        /**
         * Save the CURRENTLY selected eligible criteria under [name]. Search text is
         * deliberately NOT a parameter — it can never be persisted into a preset.
         */
        fun save(
            name: String,
            sourceFilter: ObservationFeedProjection.FeedFilter,
            criteria: LiveFilter.AdvancedCriteria,
        ): Outcome {
            val trimmed = name.trim()
            if (trimmed.isBlank() || trimmed.length > MAX_NAME_LENGTH) return Outcome.InvalidName
            if (presets.any { LiveFilter.fold(it.name) == LiveFilter.fold(trimmed) }) {
                return Outcome.DuplicateName
            }
            if (presets.size >= MAX_PRESETS) return Outcome.TooManyPresets
            val preset = FilterPreset(
                id = "fp-$epochMs-${presets.size}",
                name = trimmed,
                version = VERSION,
                sourceFilter = sourceFilter,
                criteria = criteria,
            )
            presets = presets + preset
            persist()
            return Outcome.Saved(preset)
        }

        fun rename(id: String, newName: String): Outcome {
            val target = presets.firstOrNull { it.id == id } ?: return Outcome.NotFound
            val trimmed = newName.trim()
            if (trimmed.isBlank() || trimmed.length > MAX_NAME_LENGTH) return Outcome.InvalidName
            if (presets.any { it.id != id && LiveFilter.fold(it.name) == LiveFilter.fold(trimmed) }) {
                return Outcome.DuplicateName
            }
            val renamed = target.copy(name = trimmed)
            presets = presets.map { if (it.id == id) renamed else it }
            persist()
            return Outcome.Renamed(renamed)
        }

        fun delete(id: String): Outcome {
            val target = presets.firstOrNull { it.id == id } ?: return Outcome.NotFound
            presets = presets.filter { it.id != id }
            persist()
            return Outcome.Deleted
        }

        private fun persist() {
            persistence.write(Codec.encode(presets))
        }

        companion object {
            const val MAX_PRESETS = 20
            const val MAX_NAME_LENGTH = 40

            /** Injectable epoch for deterministic tests; wall clock by default. */
            var epochMs: () -> Long = { System.currentTimeMillis() }
        }
    }
}
