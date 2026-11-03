package com.example.radioobserver.core.radio.export

import com.example.radioobserver.core.radio.analytics.CategoryCount
import com.example.radioobserver.core.radio.analytics.GroupStats
import com.example.radioobserver.core.radio.analytics.SessionAnalytics
import com.example.radioobserver.core.radio.analytics.TimeBucket
import com.example.radioobserver.core.radio.history.HistoryAnnotation
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * WORKSTREAM 06 — deterministic CSV/JSON report generation.
 *
 * All totals come from the EXACT surviving record list passed in (deleted or
 * expired records simply do not exist in the History snapshot, so they can never
 * appear). Group membership is the user's explicit WS05 selection; dangling ids
 * are counted as [missing] and never substituted with other records.
 */
object ReportBuilder {

    const val NOTE_COUNT_NOT_DEVICES =
        "Record counts are saved observation records, not unique physical devices."
    const val NOTE_NO_IDENTITIES =
        "No device identities, distances, directions or session memberships are inferred."
    const val NOTE_FRESHNESS =
        "Freshness is the provenance captured at save time; cached Wi-Fi rows remain old."
    const val NOTE_EXTERNAL_COPY =
        "Once saved outside the app, the destination app/provider controls that copy."

    private fun utcStamp(nowMs: Long): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(nowMs))
    }

    private fun fileName(type: ExportReportType, format: ExportFormat, nowMs: Long): String =
        "radioobserver-${type.id}-${utcStamp(nowMs)}.${format.extension}"

    // ---------- CSV ----------

    fun csv(records: List<HistoryRecord>, annotations: Map<String, HistoryAnnotation>,
            options: ExportOptions): String {
        val ordered = ExportPolicy.ordered(records)
        val headers = mutableListOf(
            "row", "source", "saved_at_utc", "observed_at_utc", "freshness",
            "signal_category", "type_hint", "manufacturer_hint",
        )
        if (options.includeNames) headers.add("network_name")
        if (options.includeUserAnnotations) {
            headers.add("user_label")
            headers.add("user_note")
        }
        val sb = StringBuilder()
        sb.append(headers.joinToString(",")).append("\r\n")
        ordered.forEachIndexed { index, record ->
            val annotation = annotations[record.id]
            val cells = mutableListOf(
                (index + 1).toString(),
                record.radio.name,
                ExportPolicy.isoUtc(record.savedAtEpochMs) ?: ExportPolicy.UNKNOWN,
                ExportPolicy.isoUtc(record.observedAtEpochMs) ?: ExportPolicy.UNKNOWN,
                record.freshness.name,
                record.signalBucket.name,
                record.typeHint ?: ExportPolicy.UNKNOWN,
                record.manufacturerHint ?: ExportPolicy.UNKNOWN,
            )
            if (options.includeNames) {
                // Withheld names stay unknown: only a recorded, consented name can appear.
                cells.add(record.displayName ?: ExportPolicy.UNKNOWN)
            }
            if (options.includeUserAnnotations) {
                cells.add(annotation?.label ?: ExportPolicy.UNKNOWN)
                cells.add(annotation?.note ?: ExportPolicy.UNKNOWN)
            }
            sb.append(cells.joinToString(",") { ExportPolicy.csvCell(it) }).append("\r\n")
        }
        return sb.toString()
    }

    // ---------- JSON ----------

    private fun jsonDistribution(list: List<CategoryCount>): String =
        list.joinToString(",", "[", "]") { item ->
            "{\"category\":${ExportPolicy.jsonString(item.label)}," +
                "\"count\":${item.count},\"percent\":${item.percent}}"
        }

    private fun jsonBuckets(buckets: List<TimeBucket>): String =
        buckets.joinToString(",", "[", "]") { bucket ->
            "{\"index\":${bucket.index}," +
                "\"start_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(bucket.startEpochMs))}," +
                "\"end_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(bucket.endEpochMs))}," +
                "\"count\":${bucket.count}}"
        }

    private fun jsonStats(stats: GroupStats): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"record_count\":${stats.recordCount},")
        sb.append("\"ble_count\":${stats.bleCount},")
        sb.append("\"wifi_count\":${stats.wifiCount},")
        sb.append("\"source_distribution\":${jsonDistribution(stats.sourceDistribution)},")
        sb.append("\"signal_distribution\":${jsonDistribution(stats.signalDistribution)},")
        sb.append("\"freshness_distribution\":${jsonDistribution(stats.freshnessDistribution)},")
        sb.append("\"manufacturer_known_count\":${stats.manufacturerKnownCount},")
        sb.append("\"manufacturer_unknown_count\":${stats.manufacturerUnknownCount},")
        sb.append("\"earliest_observed_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(stats.earliestObservedAtEpochMs))},")
        sb.append("\"latest_observed_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(stats.latestObservedAtEpochMs))},")
        sb.append("\"earliest_saved_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(stats.earliestSavedAtEpochMs))},")
        sb.append("\"latest_saved_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(stats.latestSavedAtEpochMs))},")
        sb.append("\"invalid_timestamp_count\":${stats.invalidTimestampCount},")
        sb.append("\"time_buckets\":${jsonBuckets(stats.timeBuckets)}")
        sb.append("}")
        return sb.toString()
    }

    private fun jsonRecords(records: List<HistoryRecord>,
                            annotations: Map<String, HistoryAnnotation>,
                            options: ExportOptions): String {
        val ordered = ExportPolicy.ordered(records)
        return ordered.joinToString(",", "[", "]") { record ->
            val annotation = annotations[record.id]
            val parts = mutableListOf<String>()
            parts.add("\"source\":${ExportPolicy.jsonString(record.radio.name)}")
            parts.add("\"saved_at_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(record.savedAtEpochMs))}")
            parts.add("\"observed_at_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(record.observedAtEpochMs))}")
            parts.add("\"freshness\":${ExportPolicy.jsonString(record.freshness.name)}")
            parts.add("\"signal_category\":${ExportPolicy.jsonString(record.signalBucket.name)}")
            parts.add("\"age_ms\":${record.ageMs ?: "null"}")
            parts.add("\"type_hint\":${ExportPolicy.jsonString(record.typeHint)}")
            parts.add("\"manufacturer_hint\":${ExportPolicy.jsonString(record.manufacturerHint)}")
            if (options.includeNames) {
                parts.add("\"network_name\":${ExportPolicy.jsonString(record.displayName)}")
            }
            if (options.includeUserAnnotations) {
                parts.add("\"user_label\":${ExportPolicy.jsonString(annotation?.label)}")
                parts.add("\"user_note\":${ExportPolicy.jsonString(annotation?.note)}")
            }
            parts.joinToString(",", "{", "}")
        }
    }

    private fun commonJson(
        type: ExportReportType,
        nowMs: Long,
        options: ExportOptions,
        scopeJson: String,
        bodyParts: List<String>,
    ): String {
        val parts = mutableListOf(
            "\"schema_version\":${ExportPolicy.SCHEMA_VERSION}",
            "\"report_type\":${ExportPolicy.jsonString(type.id)}",
            "\"generated_at_utc\":${ExportPolicy.jsonString(ExportPolicy.isoUtc(nowMs))}",
            "\"field_inclusion\":{\"names\":${options.includeNames}," +
                "\"user_annotations\":${options.includeUserAnnotations}}",
            "\"scope\":$scopeJson",
        )
        parts.addAll(bodyParts)
        parts.add(
            "\"notes\":[${listOf(
                NOTE_COUNT_NOT_DEVICES, NOTE_NO_IDENTITIES, NOTE_FRESHNESS, NOTE_EXTERNAL_COPY,
            ).joinToString(",") { ExportPolicy.jsonString(it) }}]",
        )
        return parts.joinToString(",", "{", "}\n")
    }

    // ---------- public builders ----------

    fun recordsReport(
        records: List<HistoryRecord>,
        annotations: Map<String, HistoryAnnotation> = emptyMap(),
        options: ExportOptions = ExportOptions(),
        format: ExportFormat = ExportFormat.CSV,
        nowMs: Long,
    ): ExportResult {
        val content = when (format) {
            ExportFormat.CSV -> csv(records, annotations, options)
            ExportFormat.JSON -> commonJson(
                ExportReportType.RECORDS, nowMs, options,
                "{\"kind\":\"records\"}",
                listOf(
                    "\"totals\":{\"record_count\":${records.size}}",
                    "\"records\":${jsonRecords(records, annotations, options)}",
                ),
            )
        }
        return ExportResult(format, ExportReportType.RECORDS, content, records.size,
            fileName(ExportReportType.RECORDS, format, nowMs))
    }

    fun groupSummaryReport(
        groupName: String,
        groupRecordIds: List<String>,
        allRecords: List<HistoryRecord>,
        annotations: Map<String, HistoryAnnotation> = emptyMap(),
        options: ExportOptions = ExportOptions(),
        format: ExportFormat = ExportFormat.CSV,
        nowMs: Long,
    ): ExportResult {
        val idSet = groupRecordIds.toSet()
        val members = allRecords.filter { it.id in idSet }
        val missing = idSet.size - members.size
        val content = when (format) {
            ExportFormat.CSV -> csv(members, annotations, options)
            ExportFormat.JSON -> commonJson(
                ExportReportType.GROUP_SUMMARY, nowMs, options,
                "{\"kind\":\"group\",\"group_name\":${ExportPolicy.jsonString(groupName)}," +
                    "\"selected_record_ids\":${idSet.size},\"missing_record_count\":$missing}",
                listOf(
                    "\"totals\":{\"record_count\":${members.size}," +
                        "\"missing_record_count\":$missing}",
                    "\"analytics\":${jsonStats(SessionAnalytics.stats(members))}",
                    "\"records\":${jsonRecords(members, annotations, options)}",
                ),
            )
        }
        return ExportResult(format, ExportReportType.GROUP_SUMMARY, content, members.size,
            fileName(ExportReportType.GROUP_SUMMARY, format, nowMs))
    }

    fun comparisonReport(
        leftName: String,
        leftRecordIds: List<String>,
        rightName: String,
        rightRecordIds: List<String>,
        allRecords: List<HistoryRecord>,
        options: ExportOptions = ExportOptions(),
        format: ExportFormat = ExportFormat.CSV,
        nowMs: Long,
    ): ExportResult {
        val leftSet = leftRecordIds.toSet()
        val rightSet = rightRecordIds.toSet()
        val left = allRecords.filter { it.id in leftSet }
        val right = allRecords.filter { it.id in rightSet }
        val leftStats = SessionAnalytics.stats(left)
        val rightStats = SessionAnalytics.stats(right)
        val content = when (format) {
            ExportFormat.CSV -> {
                // Stable long-format table: one metric per row, both groups as columns.
                val sb = StringBuilder()
                sb.append("metric,left,right\r\n")
                fun row(metric: String, l: String, r: String) {
                    sb.append(ExportPolicy.csvCell(metric)).append(',')
                        .append(ExportPolicy.csvCell(l)).append(',')
                        .append(ExportPolicy.csvCell(r)).append("\r\n")
                }
                row("group_name", leftName, rightName)
                row("selected_record_ids", leftSet.size.toString(), rightSet.size.toString())
                row("missing_record_count",
                    (leftSet.size - left.size).toString(),
                    (rightSet.size - right.size).toString())
                row("record_count", leftStats.recordCount.toString(), rightStats.recordCount.toString())
                row("ble_count", leftStats.bleCount.toString(), rightStats.bleCount.toString())
                row("wifi_count", leftStats.wifiCount.toString(), rightStats.wifiCount.toString())
                row("manufacturer_known_count",
                    leftStats.manufacturerKnownCount.toString(),
                    rightStats.manufacturerKnownCount.toString())
                row("manufacturer_unknown_count",
                    leftStats.manufacturerUnknownCount.toString(),
                    rightStats.manufacturerUnknownCount.toString())
                row("invalid_timestamp_count",
                    leftStats.invalidTimestampCount.toString(),
                    rightStats.invalidTimestampCount.toString())
                row("earliest_observed_utc",
                    ExportPolicy.isoUtc(leftStats.earliestObservedAtEpochMs) ?: ExportPolicy.UNKNOWN,
                    ExportPolicy.isoUtc(rightStats.earliestObservedAtEpochMs) ?: ExportPolicy.UNKNOWN)
                row("latest_observed_utc",
                    ExportPolicy.isoUtc(leftStats.latestObservedAtEpochMs) ?: ExportPolicy.UNKNOWN,
                    ExportPolicy.isoUtc(rightStats.latestObservedAtEpochMs) ?: ExportPolicy.UNKNOWN)
                leftStats.signalDistribution.forEach { lc ->
                    val rc = rightStats.signalDistribution.firstOrNull { it.label == lc.label }
                    row("signal_${lc.label}", lc.count.toString(), (rc?.count ?: 0).toString())
                }
                leftStats.freshnessDistribution.forEach { lc ->
                    val rc = rightStats.freshnessDistribution.firstOrNull { it.label == lc.label }
                    row("freshness_${lc.label}", lc.count.toString(), (rc?.count ?: 0).toString())
                }
                sb.toString()
            }
            ExportFormat.JSON -> commonJson(
                ExportReportType.GROUP_COMPARISON, nowMs, options,
                "{\"kind\":\"comparison\",\"left\":{\"group_name\":${ExportPolicy.jsonString(leftName)}," +
                    "\"selected_record_ids\":${leftSet.size}}," +
                    "\"right\":{\"group_name\":${ExportPolicy.jsonString(rightName)}," +
                    "\"selected_record_ids\":${rightSet.size}}}",
                listOf(
                    "\"comparison\":{\"left\":${jsonStats(leftStats)}," +
                        "\"right\":${jsonStats(rightStats)}}",
                ),
            )
        }
        return ExportResult(format, ExportReportType.GROUP_COMPARISON, content,
            left.size + right.size,
            fileName(ExportReportType.GROUP_COMPARISON, format, nowMs))
    }
}
