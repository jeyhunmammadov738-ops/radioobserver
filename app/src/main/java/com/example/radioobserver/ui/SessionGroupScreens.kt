package com.example.radioobserver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.analytics.CategoryCount
import com.example.radioobserver.core.radio.analytics.GroupStats
import com.example.radioobserver.core.radio.analytics.SessionAnalytics
import com.example.radioobserver.core.radio.history.AnalyticalGroup
import com.example.radioobserver.core.radio.history.GroupError
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.history.SessionGroupController
import com.example.radioobserver.ui.components.EmptyState
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WORKSTREAM 05 — FUNCTIONAL-ONLY Compose surfaces for named analytical groups
 * (developer decision: NO visual redesign in this iteration; these are the
 * minimum interfaces needed for create/rename/delete, membership, per-group
 * statistics, time charts and two-group comparison).
 *
 * Every screen is a pure reader over ALREADY-SAVED History records plus explicit
 * user callbacks: opening any of them starts NO scan, saves NO observation and
 * touches NO recording consent.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionGroupsScreen(
    state: SessionGroupController.UiState,
    records: List<HistoryRecord>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenCompare: () -> Unit,
    onDismissNotices: () -> Unit,
    onBack: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.group_list_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feed_back))
                }
            }
            Text(
                text = stringResource(R.string.group_list_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.droppedNotice) {
                Card {
                    Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.group_dropped_notice),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissNotices, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.alert_dismiss))
                        }
                    }
                }
            }
            state.lastError?.let { error ->
                Text(
                    text = stringResource(errorText(error)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(
                onClick = { showCreate = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.group_create_button)) }
            OutlinedButton(
                onClick = onOpenCompare,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.group_compare)) }
            if (state.groups.isEmpty()) {
                EmptyState(stringResource(R.string.group_empty))
            } else {
                state.groups.forEach { group ->
                    val memberCount = group.recordIds.size
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(group.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(R.string.group_member_count_format, memberCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                TextButton(
                                    onClick = { onOpenDetail(group.id) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.history_open_detail)) }
                                TextButton(
                                    onClick = { renameTarget = group.id },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.group_rename)) }
                                TextButton(
                                    onClick = { deleteTarget = group.id },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.group_delete)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        GroupNameDialog(
            title = stringResource(R.string.group_create_button),
            initial = "",
            onConfirm = { name -> onCreate(name); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { target ->
        GroupNameDialog(
            title = stringResource(R.string.group_rename),
            initial = state.groups.firstOrNull { it.id == target }?.name ?: "",
            onConfirm = { name -> onRename(target, name); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.group_delete_confirm_title)) },
            text = { Text(stringResource(R.string.group_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(target); deleteTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.group_delete_confirm_yes)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.group_cancel)) }
            },
        )
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.group_create_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.group_cancel))
            }
        },
    )
}

@Composable
fun SessionGroupDetailScreen(
    group: AnalyticalGroup?,
    records: List<HistoryRecord>,
    onAddRecord: (String, String) -> Unit,
    onRemoveRecord: (String, String) -> Unit,
    onOpenHistoryDetail: (String) -> Unit,
    onOpenReport: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.group_detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feed_back))
                }
            }
            if (group == null) {
                EmptyState(stringResource(R.string.group_empty))
                return@Column
            }
            Text(group.name, style = MaterialTheme.typography.titleLarge)
            // Honest analytical-selection disclosure (never a "verified session").
            Text(
                text = stringResource(R.string.group_list_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val memberRecords = group.recordIds.mapNotNull { id -> records.firstOrNull { it.id == id } }
            OutlinedButton(
                onClick = onOpenReport,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.report_open)) }
            GroupStatsCard(SessionAnalytics.stats(memberRecords))
            SectionHeader(stringResource(R.string.group_members))
            if (memberRecords.isEmpty()) {
                EmptyState(stringResource(R.string.group_members_empty))
            } else {
                memberRecords.forEach { record ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.group_record_label_format,
                                    record.displayName ?: stringResource(R.string.row_unnamed),
                                    radioLabel(record.radio),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = { onOpenHistoryDetail(record.id) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.group_member_open_detail)) }
                            TextButton(
                                onClick = { onRemoveRecord(group.id, record.id) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.group_remove_record)) }
                        }
                    }
                }
            }
            AddRecordSection(group = group, records = records, onAddRecord = onAddRecord)
        }
    }
}

@Composable
private fun AddRecordSection(
    group: AnalyticalGroup,
    records: List<HistoryRecord>,
    onAddRecord: (String, String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = !open },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.group_add_record)) }
    if (open) {
        val available = records.filter { it.id !in group.recordIds }
        if (available.isEmpty()) {
            Text(
                text = stringResource(R.string.group_no_available_records),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.group_add_record_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            available.forEach { record ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(
                            R.string.group_record_label_format,
                            record.displayName ?: stringResource(R.string.row_unnamed),
                            radioLabel(record.radio),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onAddRecord(group.id, record.id)
                            if (available.all { it.id == record.id }) open = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.group_add_record)) }
                }
            }
        }
    }
}

@Composable
fun SessionGroupCompareScreen(
    groups: List<AnalyticalGroup>,
    records: List<HistoryRecord>,
    onBack: () -> Unit,
) {
    var leftId by remember { mutableStateOf(groups.firstOrNull()?.id ?: "") }
    var rightId by remember { mutableStateOf(groups.getOrNull(1)?.id ?: "") }
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.group_compare_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.feed_back))
                }
            }
            if (groups.size < 2) {
                EmptyState(stringResource(R.string.group_compare_need_two))
                return@Column
            }
            GroupPickerRow(stringResource(R.string.group_compare_pick_left), groups, leftId) { leftId = it }
            GroupPickerRow(stringResource(R.string.group_compare_pick_right), groups, rightId) { rightId = it }
            if (leftId != rightId) {
                val left = groups.first { it.id == leftId }
                val right = groups.first { it.id == rightId }
                val leftStats = SessionAnalytics.stats(left.recordIds.mapNotNull { id -> records.firstOrNull { it.id == id } })
                val rightStats = SessionAnalytics.stats(right.recordIds.mapNotNull { id -> records.firstOrNull { it.id == id } })
                Text(left.name, style = MaterialTheme.typography.titleMedium)
                GroupStatsCard(leftStats)
                Text(right.name, style = MaterialTheme.typography.titleMedium)
                GroupStatsCard(rightStats)
                Text(
                    text = stringResource(R.string.group_stats_honesty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupPickerRow(label: String, groups: List<AnalyticalGroup>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            groups.forEach { g ->
                androidx.compose.material3.FilterChip(
                    selected = g.id == selected,
                    onClick = { onSelect(g.id) },
                    label = { Text(g.name) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/** Compact statistics card + deterministic bar visuals + textual summary. */
@Composable
private fun GroupStatsCard(stats: GroupStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatLine(stringResource(R.string.group_stats_records), stats.recordCount.toString())
            StatLine(stringResource(R.string.group_stats_ble), stats.bleCount.toString())
            StatLine(stringResource(R.string.group_stats_wifi), stats.wifiCount.toString())
            DistributionRows(stringResource(R.string.group_stats_source), stats.sourceDistribution)
            DistributionRows(stringResource(R.string.group_stats_signal), stats.signalDistribution)
            DistributionRows(stringResource(R.string.group_stats_freshness), stats.freshnessDistribution)
            StatLine(
                stringResource(R.string.group_stats_manufacturer),
                "${stats.manufacturerKnownCount} / ${stats.manufacturerUnknownCount}",
            )
            StatLine(
                stringResource(R.string.group_stats_coverage),
                coverageText(stats),
            )
            if (stats.invalidTimestampCount > 0) {
                Text(
                    text = stringResource(R.string.group_stats_invalid_times_format, stats.invalidTimestampCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (stats.timeBuckets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.group_stats_time_buckets),
                    style = MaterialTheme.typography.labelMedium,
                )
                TimeBucketBars(stats.timeBuckets.map { it.count })
            }
            Text(
                text = stringResource(R.string.group_stats_honesty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Deterministic width bars: width fraction = count / max count (no animation). */
@Composable
private fun DistributionRows(title: String, categories: List<CategoryCount>) {
    val maxCount = categories.maxOfOrNull { it.count } ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        categories.forEach { c ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${c.label}: ${c.count} (%${c.percent})",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .width(120.dp * (if (maxCount == 0) 0f else c.count.toFloat() / maxCount))
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** Deterministic height bars for bounded time buckets; zero buckets stay zero. */
@Composable
private fun TimeBucketBars(counts: List<Int>) {
    val max = counts.maxOrNull() ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
        counts.forEach { count ->
            val heightDp = if (max == 0) 4.dp else (4 + (36 * count / max)).dp
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(heightDp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun coverageText(stats: GroupStats): String {
    val pattern = stringResource(R.string.group_date_format)
    val fmt = remember { SimpleDateFormat(pattern, Locale.ROOT) }
    val earliest = stats.earliestObservedAtEpochMs
    val latest = stats.latestObservedAtEpochMs
    return if (earliest == null || latest == null) {
        stringResource(R.string.group_stats_unknown)
    } else {
        "${fmt.format(Date(earliest))} — ${fmt.format(Date(latest))}"
    }
}

@Composable
private fun radioLabel(radio: RadioType): String = when (radio) {
    RadioType.BLE -> stringResource(R.string.alert_radio_ble)
    RadioType.WIFI -> stringResource(R.string.alert_radio_wifi)
}

private fun errorText(error: GroupError): Int = when (error) {
    GroupError.NAME_BLANK, GroupError.NAME_TOO_LONG -> R.string.group_name_blank
    GroupError.GROUP_LIMIT_REACHED -> R.string.group_limit_reached
    GroupError.NOT_FOUND, GroupError.RECORD_NOT_SAVED, GroupError.ALREADY_MEMBER -> R.string.group_dropped_notice
}
