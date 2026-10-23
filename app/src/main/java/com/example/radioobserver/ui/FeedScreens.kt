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
import androidx.compose.ui.res.pluralStringResource
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
import com.example.radioobserver.ui.components.CompactInfoRow
import com.example.radioobserver.ui.DetailTime
import com.example.radioobserver.ui.ManufacturerPresentation
import com.example.radioobserver.ui.components.EmptyState
import com.example.radioobserver.ui.components.PillStyle
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.SourceIcon
import com.example.radioobserver.ui.components.Spacing
import com.example.radioobserver.ui.components.StatusPill

/**
 * PREMIUM UI/UX V2 Gözlemler feed + live detail (post-roadmap, 2026-10-23).
 * Defects fixed vs. the previous build: equally heavy rows with repeated anonymous
 * names → denser, breathable rows with a leading source icon, a deliberate hierarchy
 * (name first, meta second, time right-aligned) and a freshness PILL instead of a
 * second badge copy. Behavior UNCHANGED: stateless renderers over the CURRENT
 * sanitized [ObservationUiState]; filtering/selection/navigation dispatch NOTHING;
 * rows resolve via the pure [ObservationFeedProjection] and dispose honestly; LazyColumn
 * keys stay tied to REAL session-local row ids (never indices); freshness is never
 * promoted just because the row is displayed now.
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
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Text(
                    text = stringResource(R.string.feed_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(top = Spacing.md)
                        .semantics { heading() },
                )
                Text(
                    text = if (state.rows.isNotEmpty()) {
                        val count = ControlScreenProjection.observationCount(state.rows)
                        pluralStringResource(R.plurals.observation_count, count, count)
                    } else stringResource(R.string.feed_empty_no_session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
            }
            // Filter chips: FlowRow wraps at large font scales; 48dp targets; the
            // list below has its own top padding so chips never overlap rows.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                        start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(rows, key = { it.rowId }) { row ->
                        ObservationRow(row = row, onSelect = { onRowSelect(row.rowId) })
                    }
                }
                FeedEmptyKind.SESSION_ACTIVE_NO_OBSERVATIONS -> EmptyState(stringResource(R.string.empty_feed_note))
                FeedEmptyKind.NO_SESSION_NO_OBSERVATIONS -> EmptyState(stringResource(R.string.feed_empty_no_session))
                FeedEmptyKind.NO_MATCHES_FOR_FILTER -> EmptyState(stringResource(R.string.feed_empty_filter))
            }
        }
    }
}

/**
 * One dense, breathable row: source icon, safe name, freshness pill and last-seen
 * time in a deliberate hierarchy. Repeated "Adı bilinmiyor" rows stay DISTINCT because
 * each renders its own session-local rowId-driven identity (never invented names or
 * stable tracking ids). Long names wrap (maxLines 2) instead of clipping.
 */
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
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(radio = row.radio)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = lastSeenText(row.lastSeenAtEpochMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusPill(
                    text = fresh,
                    kind = freshnessPill(row.freshness),
                )
            }
        }
    }
}

private fun freshnessPill(freshness: FreshnessState): com.example.radioobserver.ui.components.PillKind =
    when (freshness) {
        FreshnessState.FRESH -> com.example.radioobserver.ui.components.PillKind.ACTIVE
        FreshnessState.STALE -> com.example.radioobserver.ui.components.PillKind.ATTENTION
        FreshnessState.UNKNOWN -> com.example.radioobserver.ui.components.PillKind.NEUTRAL
    }

/**
 * Detail for the SELECTED row, resolved against the CURRENT state only. A missing row
 * (TTL/expiry, eviction, dispose, session replacement) renders the honest unavailable
 * state; nothing restarts a scan to recover it. Key/value pairs use the shared
 * CompactInfoRow system (no uneven alignment); conservative warnings kept verbatim.
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
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                EmptyState(stringResource(R.string.detail_unavailable))
            } else {
                DetailContent(detail)
            }
        }
    }
}

/** Grouped presentation of the SAME sanitized fields, one design language everywhere. */
@Composable
private fun DetailContent(row: ObservationRowUi) {
    val unknown = stringResource(R.string.value_not_available)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceIcon(radio = row.radio)
                Text(
                    text = row.name ?: stringResource(R.string.row_unnamed),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            SectionHeader(stringResource(R.string.detail_section_source_time))
            CompactInfoRow(stringResource(R.string.detail_field_source), radioWord(row.radio))
            // MICRO-POLISH: the label already says "Son görülme"; the VALUE is now
            // the bare authoritative clock time (no duplicated prefix).
            CompactInfoRow(stringResource(R.string.detail_field_last_seen), timeText(row.lastSeenAtEpochMs))
            CompactInfoRow(stringResource(R.string.detail_field_row_label), row.rowId)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(stringResource(R.string.detail_section_signal))
            CompactInfoRow(stringResource(R.string.detail_field_signal), signalWord(row.signalBucket))
            CompactInfoRow(stringResource(R.string.detail_field_freshness), freshnessWord(row.freshness))
            CompactInfoRow(stringResource(R.string.detail_field_confidence), confidenceWord(row.confidence))
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(stringResource(R.string.detail_section_hints))
            // Classifier English sentinels (Wi-Fi vendor + BLE unknown-manufacturer)
            // are localized at the presentation boundary (ManufacturerPresentation);
            // real, verified OUI/company hints pass through unchanged.
            val bleUnknownId = ManufacturerPresentation.bleUnknownManufacturerId(row.manufacturerHint)
            when {
                ManufacturerPresentation.isKnownFallback(row.manufacturerHint) -> {
                    CompactInfoRow(
                        stringResource(R.string.detail_field_manufacturer),
                        stringResource(R.string.manufacturer_unavailable),
                    )
                    if (row.radio == RadioType.WIFI && row.locallyAdministered) {
                        Text(
                            text = stringResource(R.string.manufacturer_local_address_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // BLE sentinel: honest Turkish + the raw company id as SEPARATE
                // context. The code never identifies a vendor.
                bleUnknownId != null -> {
                    CompactInfoRow(
                        stringResource(R.string.detail_field_manufacturer),
                        stringResource(R.string.manufacturer_unavailable),
                    )
                    Text(
                        text = stringResource(
                            R.string.manufacturer_code_format,
                            ManufacturerPresentation.formatBleCompanyId(bleUnknownId)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> CompactInfoRow(
                    stringResource(R.string.detail_field_manufacturer),
                    row.manufacturerHint ?: unknown)
            }
            CompactInfoRow(stringResource(R.string.detail_field_type), row.typeHint ?: unknown)
            CompactInfoRow(
                stringResource(R.string.detail_field_locally_administered),
                if (row.locallyAdministered) stringResource(R.string.value_yes)
                else stringResource(R.string.value_no))
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SectionHeader(stringResource(R.string.detail_section_limits))
            Text(stringResource(R.string.confidence_caution), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.signal_qualitative_note), style = MaterialTheme.typography.bodySmall)
            if (row.radio == RadioType.WIFI) {
                Text(stringResource(R.string.wifi_passive_note), style = MaterialTheme.typography.bodySmall)
            }
        }
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
    // Feed rows carry their own label ("Son görülme: HH:mm:ss") because the pill
    // column has no other caption. The pipeline epoch value is authoritative.
    return stringResource(R.string.last_seen_format, DetailTime.plainClockText(epochMs))
}

@Composable
private fun timeText(epochMs: Long): String = DetailTime.plainClockText(epochMs)
