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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.core.radio.ui.ControlScreenProjection
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedEmptyKind
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter
import com.example.radioobserver.core.radio.ui.ObservationRowUi
import com.example.radioobserver.core.radio.ui.ObservationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 3.6 feed + detail, professionally restyled in the post-roadmap UI iteration
 * (developer-approved, 2026-10-23). Sanitized, EPHEMERAL, read-only behavior is
 * UNCHANGED: stateless renderers over the CURRENT sanitized [ObservationUiState];
 * filtering/selection/navigation dispatch NOTHING. Rows resolve via the pure
 * [ObservationFeedProjection]; disposed/evicted/replaced rows render the honest
 * unavailable state. The pipeline's 200-row bound / 15-min TTL stay authoritative.
 *
 * Visual changes only: consistent row anatomy (safe label, source badge, freshness
 * badge, last-seen time), refined chips/spacing, grouped detail sections. Source-level
 * design: TalkBack, clipping and font-scale behavior require the phone retest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ObservationsFeedScreen(
    state: ObservationUiState,
    filter: FeedFilter,
    onFilterChange: (FeedFilter) -> Unit,
    onRowSelect: (String) -> Unit,
) {
    val rows = ObservationFeedProjection.rowsFor(state, filter)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact tab heading (no paragraph stack; the tab bar owns navigation).
            Text(
                text = stringResource(R.string.feed_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    .semantics { heading() },
            )
            // Session summary: honest, authoritative count or the empty-session note.
            Text(
                text = if (state.rows.isNotEmpty()) {
                    val count = ControlScreenProjection.observationCount(state.rows)
                    androidx.compose.ui.res.pluralStringResource(
                        R.plurals.observation_count, count, count)
                } else stringResource(R.string.feed_empty_no_session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // 3.9 fix (preserved): FlowRow wraps chips instead of overflowing at large
            // font scales; 48dp minimum touch targets; no overlap with the list below.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = filter == FeedFilter.ALL,
                    onClick = { onFilterChange(FeedFilter.ALL) },
                    label = { Text(stringResource(R.string.filter_all)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = filter == FeedFilter.BLUETOOTH,
                    onClick = { onFilterChange(FeedFilter.BLUETOOTH) },
                    label = { Text(stringResource(R.string.filter_bluetooth)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = filter == FeedFilter.WIFI,
                    onClick = { onFilterChange(FeedFilter.WIFI) },
                    label = { Text(stringResource(R.string.filter_wifi)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            when (ObservationFeedProjection.emptyKind(state, filter)) {
                null -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rows, key = { it.rowId }) { row ->
                        ObservationRow(row = row, onSelect = { onRowSelect(row.rowId) })
                    }
                }
                FeedEmptyKind.SESSION_ACTIVE_NO_OBSERVATIONS -> EmptyState(R.string.empty_feed_note)
                FeedEmptyKind.NO_SESSION_NO_OBSERVATIONS -> EmptyState(R.string.feed_empty_no_session)
                FeedEmptyKind.NO_MATCHES_FOR_FILTER -> EmptyState(R.string.feed_empty_filter)
            }
        }
    }
}

@Composable
private fun EmptyState(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp),
    )
}

/** Small tonal badge; NEVER the only carrier of meaning (the same word also appears
 *  in the row's contentDescription for screen readers). */
@Composable
private fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** One sanitized feed row: safe label, source badge, freshness badge, last-seen. */
@Composable
private fun ObservationRow(row: ObservationRowUi, onSelect: () -> Unit) {
    val source = radioWord(row.radio)
    val fresh = freshnessWord(row.freshness)
    val name = row.name ?: stringResource(R.string.row_unnamed)
    val rowA11y = stringResource(R.string.a11y_row_format, name, source, fresh)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = rowA11y },
        onClick = onSelect,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = lastSeenText(row.lastSeenAtEpochMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(source)
                Badge(fresh)
            }
        }
    }
}

/**
 * Detail for the SELECTED row, resolved against the CURRENT state only. A missing row
 * (TTL/expiry, eviction, dispose, session replacement) renders the honest unavailable
 * state; nothing restarts a scan to recover it.
 */
@Composable
fun ObservationDetailScreen(
    state: ObservationUiState,
    rowId: String,
    onBack: () -> Unit,
) {
    val detail = ObservationFeedProjection.detailFor(state, rowId)
    Surface(modifier = Modifier.fillMaxSize()) {
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
                    text = stringResource(R.string.detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.feed_back)) }
            }
            if (detail == null) {
                Text(
                    text = stringResource(R.string.detail_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                DetailContent(detail)
            }
        }
    }
}

/** Grouped, readable presentation of the SAME sanitized fields (no dense dump). */
@Composable
private fun DetailContent(row: ObservationRowUi) {
    val unknown = stringResource(R.string.value_not_available)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = row.name ?: stringResource(R.string.row_unnamed),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            DetailSectionTitle(R.string.detail_section_source_time)
            DetailField(stringResource(R.string.detail_field_source), radioWord(row.radio))
            DetailField(stringResource(R.string.detail_field_last_seen), lastSeenText(row.lastSeenAtEpochMs))
            DetailField(stringResource(R.string.detail_field_row_label), row.rowId)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailSectionTitle(R.string.detail_section_signal)
            DetailField(stringResource(R.string.detail_field_signal), signalWord(row.signalBucket))
            DetailField(stringResource(R.string.detail_field_freshness), freshnessWord(row.freshness))
            DetailField(stringResource(R.string.detail_field_confidence), confidenceWord(row.confidence))
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailSectionTitle(R.string.detail_section_hints)
            DetailField(
                stringResource(R.string.detail_field_manufacturer),
                row.manufacturerHint ?: unknown)
            DetailField(stringResource(R.string.detail_field_type), row.typeHint ?: unknown)
            DetailField(
                stringResource(R.string.detail_field_locally_administered),
                if (row.locallyAdministered) stringResource(R.string.value_yes)
                else stringResource(R.string.value_no))
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DetailSectionTitle(R.string.detail_section_limits)
            Text(stringResource(R.string.confidence_caution), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.signal_qualitative_note), style = MaterialTheme.typography.bodySmall)
            if (row.radio == RadioType.WIFI) {
                Text(stringResource(R.string.wifi_passive_note), style = MaterialTheme.typography.bodySmall)
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
private fun DetailField(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun radioWord(radio: RadioType): String = stringResource(
    when (radio) {
        RadioType.BLE -> R.string.source_ble
        RadioType.WIFI -> R.string.source_wifi
    },
)

@Composable
private fun freshnessWord(freshness: FreshnessState): String = stringResource(
    when (freshness) {
        FreshnessState.FRESH -> R.string.freshness_fresh
        FreshnessState.STALE -> R.string.freshness_stale
        FreshnessState.UNKNOWN -> R.string.freshness_unknown
    },
)

@Composable
private fun confidenceWord(confidence: ObservationConfidence): String = stringResource(
    when (confidence) {
        ObservationConfidence.UNKNOWN -> R.string.confidence_unknown
        ObservationConfidence.POSSIBLE -> R.string.confidence_possible
        ObservationConfidence.CANDIDATE -> R.string.confidence_candidate
        ObservationConfidence.VALIDATED_FORMAT -> R.string.confidence_validated_format
    },
)

@Composable
private fun signalWord(bucket: SignalBucket): String = stringResource(
    when (bucket) {
        SignalBucket.IMMEDIATE -> R.string.signal_immediate
        SignalBucket.NEAR -> R.string.signal_near
        SignalBucket.FAR -> R.string.signal_far
        SignalBucket.UNAVAILABLE -> R.string.signal_unavailable
    },
)

@Composable
private fun lastSeenText(epochMs: Long): String {
    // The epoch value comes from the sanitized pipeline (authoritative); we never
    // invent a time when the field is absent.
    val formatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochMs))
    return stringResource(R.string.last_seen_format, formatted)
}
