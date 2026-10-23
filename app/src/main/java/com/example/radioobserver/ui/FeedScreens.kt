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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.radioobserver.core.radio.ui.LiveViewKind
import com.example.radioobserver.core.radio.ui.LiveViewProjection
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
    // WORKSTREAM 01: the selected live presentation is a UI preference ONLY —
    // it dispatches NOTHING (no scan, permission, session or history action) and
    // is never persisted. Short-lived Activity state, dies with the process.
    viewKind: LiveViewKind,
    onViewKindChange: (LiveViewKind) -> Unit,
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
            // WORKSTREAM 01 view switcher: Liste / Radar / Zaman tüneli. Same
            // 48dp-target FilterChip pattern as the source filters; pure UI state.
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Text(
                    text = stringResource(R.string.view_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .semantics { heading() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(
                        selected = viewKind == LiveViewKind.LIST,
                        onClick = { onViewKindChange(LiveViewKind.LIST) },
                        label = { Text(stringResource(R.string.view_list)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = viewKind == LiveViewKind.RADAR,
                        onClick = { onViewKindChange(LiveViewKind.RADAR) },
                        label = { Text(stringResource(R.string.view_radar)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                    FilterChip(
                        selected = viewKind == LiveViewKind.TIMELINE,
                        onClick = { onViewKindChange(LiveViewKind.TIMELINE) },
                        label = { Text(stringResource(R.string.view_timeline)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
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
            // The SAME honest empty taxonomy applies to ALL three views: a view
            // switch never invents content and never changes eligibility.
            when (ObservationFeedProjection.emptyKind(state, filter)) {
                null -> when (viewKind) {
                    LiveViewKind.LIST -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(rows, key = { it.rowId }) { row ->
                            ObservationRow(row = row, onSelect = { onRowSelect(row.rowId) })
                        }
                    }
                    LiveViewKind.RADAR -> RadarView(
                        state = state,
                        rows = rows,
                        onRowSelect = onRowSelect,
                    )
                    LiveViewKind.TIMELINE -> TimelineView(
                        state = state,
                        filter = filter,
                        onRowSelect = onRowSelect,
                    )
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

/**
 * WORKSTREAM 01 — RADAR: abstract, honest signal-category scheme over the CURRENT
 * sanitized rows. NOT a location, compass or distance display: rings are the
 * verified SignalBucket taxonomy, angles are deterministic rowId-derived visual
 * decoration, and the mandatory honesty copy sits above the canvas. Cached Wi-Fi
 * stays visibly "Eski" (hollow dots). Dots are tappable; the adjacent list makes
 * every item reachable WITHOUT the canvas, so nothing is dot-only.
 */
@Composable
private fun RadarView(
    state: ObservationUiState,
    rows: List<ObservationRowUi>,
    onRowSelect: (String) -> Unit,
) {
    val projection = LiveViewProjection.radarProjection(
        state, ObservationFeedProjection.FeedFilter.ALL,
    )
    val rowsForRadar = rows // same filtered membership as Liste (host-tested)
    Column(modifier = Modifier.fillMaxSize()) {
        // Prominent, accessible honesty copy — always visible, both semantics and text.
        Text(
            text = stringResource(R.string.radar_honesty_note),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                .semantics { heading() },
        )
        if (projection.allSignalUnavailable) {
            Text(
                text = stringResource(R.string.radar_all_unknown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }
        // Bounded schematic canvas (aspect-locked square; no sweep animation that
        // could imply a live physical scan).
        val bleColor = MaterialTheme.colorScheme.primary
        val wifiColor = MaterialTheme.colorScheme.tertiary
        val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
        val dotRadiusPx = with(LocalDensity.current) { 10.dp.toPx() }
        val tapRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .aspectRatio(1f)
                // Dot taps resolve the SAME rowId the list uses; a miss does nothing.
                .pointerInput(projection) {
                    detectTapGestures { tap ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = (minOf(size.width, size.height) / 2f).toFloat()
                        projection.dots.firstOrNull { dot ->
                            val angleRad = Math.toRadians(dot.angleDegrees)
                            // Schematic coordinates: 0 degrees is up; purely visual.
                            val pos = Offset(
                                x = center.x + (maxR * dot.radiusFraction * Math.sin(angleRad)).toFloat(),
                                y = center.y - (maxR * dot.radiusFraction * Math.cos(angleRad)).toFloat(),
                            )
                            (tap - pos).getDistance() <= tapRadiusPx
                        }?.let { dot -> onRowSelect(dot.rowId) }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = size.minDimension / 2f
                // Four abstract ring bands (SignalBucket taxonomy), gap included.
                val bands = LiveViewProjection.RING_ORDER.size
                for (i in 0 until bands) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.35f),
                        radius = maxR * ((i + 1).toFloat() / bands),
                        center = center,
                        style = Stroke(width = 2f),
                    )
                }
                for (dot in projection.dots) {
                    val angleRad = Math.toRadians(dot.angleDegrees)
                    val pos = Offset(
                        x = center.x + (maxR * dot.radiusFraction * Math.sin(angleRad)).toFloat(),
                        y = center.y - (maxR * dot.radiusFraction * Math.cos(angleRad)).toFloat(),
                    )
                    val color = if (dot.radio == RadioType.BLE) bleColor else wifiColor
                    val hollow = dot.freshness == FreshnessState.STALE
                    if (dot.radio == RadioType.WIFI) {
                        // Wi-Fi: rounded square so the two source types differ by
                        // SHAPE as well as color (never color alone).
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(pos.x - dotRadiusPx, pos.y - dotRadiusPx),
                            size = Size(dotRadiusPx * 2, dotRadiusPx * 2),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(dotRadiusPx / 2),
                            style = if (hollow) Stroke(width = 3f) else Fill,
                        )
                    } else {
                        drawCircle(
                            color = color,
                            radius = dotRadiusPx,
                            center = pos,
                            style = if (hollow) Stroke(width = 3f) else Fill,
                        )
                    }
                }
            }
        }
        // Legend: taxonomy + shape + freshness provenance, all in words.
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
            Text(
                text = stringResource(R.string.radar_legend_rings),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.radar_legend_style),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.radar_legend_center),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (projection.overflowCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.radar_overflow_note, projection.overflowCount, projection.overflowCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Adjacent accessible list: EVERY filtered row (including overflowed ones)
        // stays reachable without the canvas. Same keys, same detail navigation.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(rowsForRadar, key = { it.rowId }) { row ->
                RadarListRow(row = row, onSelect = { onRowSelect(row.rowId) })
            }
        }
    }
}

/** Compact, fully labeled radar-list row: name, source, signal word, last seen. */
@Composable
private fun RadarListRow(row: ObservationRowUi, onSelect: () -> Unit) {
    val name = row.name ?: stringResource(R.string.row_unnamed)
    val a11y = stringResource(
        R.string.a11y_row_format, name, radioWord(row.radio),
        stringResource(
            when (row.freshness) {
                FreshnessState.FRESH -> R.string.freshness_fresh
                FreshnessState.STALE -> R.string.freshness_stale
                FreshnessState.UNKNOWN -> R.string.freshness_unknown
            },
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = a11y }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceIcon(radio = row.radio)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = DetailTime.plainClockText(row.lastSeenAtEpochMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * WORKSTREAM 01 — TIMELINE: session-local chronology derived ONLY from the
 * sanitized rows' real last-seen wall clocks (no first-seen field exists and none
 * is invented; ordering + tie-break are host-tested). Derives from the CURRENT
 * state, so disposal/TTL/eviction empty it automatically; nothing is persisted.
 */
@Composable
private fun TimelineView(
    state: ObservationUiState,
    filter: FeedFilter,
    onRowSelect: (String) -> Unit,
) {
    val entries = LiveViewProjection.timelineProjection(state, filter)
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.timeline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(entries, key = { it.rowId }) { entry ->
                TimelineRow(entry = entry, onSelect = { onRowSelect(entry.rowId) })
            }
        }
    }
}

@Composable
private fun TimelineRow(entry: LiveViewProjection.TimelineEntry, onSelect: () -> Unit) {
    val name = entry.name ?: stringResource(R.string.row_unnamed)
    val fresh = stringResource(
        when (entry.freshness) {
            FreshnessState.FRESH -> R.string.freshness_fresh
            FreshnessState.STALE -> R.string.freshness_stale
            FreshnessState.UNKNOWN -> R.string.freshness_unknown
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = "$name, $fresh" }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = DetailTime.plainClockText(entry.lastSeenAtEpochMs),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(64.dp),
        )
        SourceIcon(radio = entry.radio)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = fresh,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
