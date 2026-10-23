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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.radioobserver.core.radio.history.HistoryConsent
import com.example.radioobserver.core.radio.history.HistoryLimits
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.history.HistoryController
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * OPTIONAL LOCAL HISTORY (post-roadmap feature, developer-approved 2026-10-23):
 * Turkish Material 3 screens. Recording is OFF by default and needs explicit user
 * consent; everything here is inert with respect to live observation — toggles,
 * deletions and the clear-all never start, stop or schedule sessions.
 */
enum class HistoryFilter { ALL, BLUETOOTH, WIFI }

@Composable
fun HistoryScreen(
    state: HistoryController.UiState,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    onConsentChange: (HistoryConsent) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onDeleteOne: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismissNotices: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    var clearAllConfirm by remember { mutableStateOf(false) }
    val historyToggleA11y = stringResource(R.string.history_recording_toggle_a11y)
    val historyWifiNamesA11y = stringResource(R.string.history_wifi_names_toggle_a11y)
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
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.feed_back))
        }

        if (state.failedClosedNotice) {
            HistoryNoticeCard(stringResource(R.string.history_failed_closed_notice), onDismissNotices)
        } else if (state.droppedNotice) {
            HistoryNoticeCard(stringResource(R.string.history_dropped_notice), onDismissNotices)
        }

        // ---- Consent: recording ON/OFF ----
        HistoryCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.history_recording_toggle),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                Switch(
                    checked = state.consent.recordingEnabled,
                    onCheckedChange = { onConsentChange(state.consent.copy(recordingEnabled = it)) },
                    modifier = Modifier.semantics {
                        contentDescription = historyToggleA11y
                    },
                )
            }
            Text(stringResource(R.string.history_consent_body), style = MaterialTheme.typography.bodySmall)
        }

        // ---- Separate Wi-Fi names consent (OFF by default) ----
        if (state.consent.recordingEnabled) {
            HistoryCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.history_wifi_names_toggle),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.consent.wifiNamesEnabled,
                        onCheckedChange = {
                            onConsentChange(state.consent.copy(wifiNamesEnabled = it))
                        },
                        modifier = Modifier.semantics {
                            contentDescription = historyWifiNamesA11y
                        },
                    )
                }
                Text(stringResource(R.string.history_wifi_names_body), style = MaterialTheme.typography.bodySmall)
            }
            // ---- Retention (7/14/30 days, hard max 30) ----
            HistoryCard {
                Text(
                    stringResource(R.string.history_retention_label),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryLimits.ALLOWED_RETENTION_DAYS.forEach { days ->
                        FilterChip(
                            selected = state.consent.retentionDays == days,
                            onClick = { onRetentionChange(days) },
                            label = {
                                Text(stringResource(R.string.history_retention_days, days))
                            },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
            }
        }

        // ---- Count + filters + list ----
        Text(
            text = stringResource(R.string.history_count, state.records.size),
            style = MaterialTheme.typography.bodyMedium,
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
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
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
private fun HistoryCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
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
        Text(
            stringResource(R.string.history_detail_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.feed_back))
        }
        if (record == null) {
            // Honest unavailable state (record deleted/expired while open).
            Text(stringResource(R.string.history_detail_missing), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
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
        record.manufacturerHint?.let {
            HistoryDetailRow(stringResource(R.string.history_detail_manufacturer), it)
        }
        record.typeHint?.let {
            HistoryDetailRow(stringResource(R.string.history_detail_type), it)
        }
        HistoryDetailRow(
            stringResource(R.string.history_detail_signal),
            signalBucketLabel(record.signalBucket) +
                " (" + stringResource(R.string.signal_qualitative_note) + ")",
        )
        HistoryDetailRow(stringResource(R.string.history_detail_freshness), freshnessLabel(record.freshness))
        HistoryDetailRow(
            stringResource(R.string.history_detail_age),
            record.ageMs?.let { stringResource(R.string.history_detail_age_ms, it) }
                ?: stringResource(R.string.history_detail_age_unknown),
        )
        Text(stringResource(R.string.history_privacy_note), style = MaterialTheme.typography.bodySmall)
    }
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
