package com.example.radioobserver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.history.HistoryController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * OPTIONAL LOCAL HISTORY — browsing surface (post-roadmap feature; professionally
 * restyled 2026-10-23). Geçmiş is now a clean READ/BROWSE screen: count, source
 * filters, saved records, per-record delete, confirmed Clear All. ALL preference
 * controls (recording consent, Wi-Fi-names consent, retention) moved to the Ayarlar
 * tab, which drives the SAME [HistoryController] state — no duplicate settings, no
 * behavior change. Default recording stays OFF; nothing here starts/stops sessions.
 */
enum class HistoryFilter { ALL, BLUETOOTH, WIFI }

@Composable
fun HistoryScreen(
    state: HistoryController.UiState,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    onDeleteOne: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismissNotices: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var clearAllConfirm by remember { mutableStateOf(false) }
    val historyClearAllA11y = stringResource(R.string.history_clear_all_a11y)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        if (state.failedClosedNotice) {
            HistoryNoticeCard(stringResource(R.string.history_failed_closed_notice), onDismissNotices)
        } else if (state.droppedNotice) {
            HistoryNoticeCard(stringResource(R.string.history_dropped_notice), onDismissNotices)
        }

        // Count + source filters.
        Text(
            text = stringResource(R.string.history_count, state.records.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HistoryFilterRow(filter, onFilterChange)
        val visible = state.records.filter {
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.BLUETOOTH -> it.radio == RadioType.BLE
                HistoryFilter.WIFI -> it.radio == RadioType.WIFI
            }
        }
        if (visible.isEmpty()) {
            // Honest empty state + a contextual pointer to the preferences in Ayarlar.
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
            if (!state.consent.recordingEnabled) {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.history_open_settings)) }
            }
        } else {
            visible.forEach { record ->
                HistoryRecordCard(
                    record = record,
                    onOpenDetail = { onOpenDetail(record.id) },
                    onDelete = { onDeleteOne(record.id) },
                )
            }
        }
        if (state.records.isNotEmpty()) {
            OutlinedButton(
                onClick = { clearAllConfirm = true },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = historyClearAllA11y },
            ) { Text(stringResource(R.string.history_clear_all)) }
        }
    }

    if (clearAllConfirm) {
        AlertDialog(
            onDismissRequest = { clearAllConfirm = false },
            title = { Text(stringResource(R.string.history_clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_all_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearAllConfirm = false
                        onClearAll()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.history_clear_all_confirm_yes)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { clearAllConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.history_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryFilterRow(filter: HistoryFilter, onFilterChange: (HistoryFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = filter == HistoryFilter.ALL,
            onClick = { onFilterChange(HistoryFilter.ALL) },
            label = { Text(stringResource(R.string.filter_all)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = filter == HistoryFilter.BLUETOOTH,
            onClick = { onFilterChange(HistoryFilter.BLUETOOTH) },
            label = { Text(stringResource(R.string.filter_bluetooth)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        FilterChip(
            selected = filter == HistoryFilter.WIFI,
            onClick = { onFilterChange(HistoryFilter.WIFI) },
            label = { Text(stringResource(R.string.filter_wifi)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun HistoryNoticeCard(text: String, onDismiss: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_notice_dismiss))
            }
        }
    }
}

@Composable
private fun HistoryRecordCard(
    record: HistoryRecord,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = record.displayName ?: stringResource(R.string.history_unnamed),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.history_row_line,
                    radioLabel(record.radio),
                    formatTime(record.savedAtEpochMs),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenDetail, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.history_open_detail))
                }
                TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.history_delete_one))
                }
            }
        }
    }
}

@Composable
fun HistoryDetailScreen(
    record: HistoryRecord?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.history_detail_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.feed_back))
            }
        }
        if (record == null) {
            // Honest unavailable state (record deleted/expired while open).
            Text(stringResource(R.string.history_detail_missing), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = record.displayName ?: stringResource(R.string.history_unnamed),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                DetailSectionTitle(R.string.detail_section_source_time)
                HistoryDetailRow(stringResource(R.string.history_detail_radio), radioLabel(record.radio))
                HistoryDetailRow(
                    stringResource(R.string.history_detail_name),
                    record.displayName ?: stringResource(R.string.history_unnamed),
                )
                HistoryDetailRow(
                    stringResource(R.string.history_detail_saved_at),
                    formatTime(record.savedAtEpochMs),
                )
                HistoryDetailRow(
                    stringResource(R.string.history_detail_observed_at),
                    formatTime(record.observedAtEpochMs),
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSectionTitle(R.string.detail_section_signal)
                HistoryDetailRow(
                    stringResource(R.string.history_detail_signal),
                    signalBucketLabel(record.signalBucket) +
                        " (" + stringResource(R.string.signal_qualitative_note) + ")",
                )
                HistoryDetailRow(
                    stringResource(R.string.history_detail_freshness),
                    freshnessLabel(record.freshness),
                )
                HistoryDetailRow(
                    stringResource(R.string.history_detail_age),
                    record.ageMs?.let { stringResource(R.string.history_detail_age_ms, it) }
                        ?: stringResource(R.string.history_detail_age_unknown),
                )
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailSectionTitle(R.string.detail_section_hints)
                record.manufacturerHint?.let {
                    HistoryDetailRow(stringResource(R.string.history_detail_manufacturer), it)
                }
                record.typeHint?.let {
                    HistoryDetailRow(stringResource(R.string.history_detail_type), it)
                }
                Text(stringResource(R.string.history_privacy_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun HistoryDetailRow(label: String, value: String) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun radioLabel(radio: RadioType): String = when (radio) {
    RadioType.BLE -> stringResource(R.string.filter_bluetooth)
    RadioType.WIFI -> stringResource(R.string.filter_wifi)
}

@Composable
private fun signalBucketLabel(bucket: SignalBucket): String = when (bucket) {
    SignalBucket.IMMEDIATE -> stringResource(R.string.signal_immediate)
    SignalBucket.NEAR -> stringResource(R.string.signal_near)
    SignalBucket.FAR -> stringResource(R.string.signal_far)
    SignalBucket.UNAVAILABLE -> stringResource(R.string.signal_unavailable)
}

@Composable
private fun freshnessLabel(freshness: FreshnessState): String = when (freshness) {
    FreshnessState.FRESH -> stringResource(R.string.freshness_fresh)
    FreshnessState.STALE -> stringResource(R.string.freshness_stale)
    FreshnessState.UNKNOWN -> stringResource(R.string.freshness_unknown)
}

private fun formatTime(epochMs: Long): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
