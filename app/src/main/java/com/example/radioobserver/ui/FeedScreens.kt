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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.radioobserver.core.radio.ui.FilterPresets
import com.example.radioobserver.core.radio.ui.LiveFilter
import com.example.radioobserver.core.radio.ui.LiveViewKind
import com.example.radioobserver.core.radio.signature.SignatureCategory
import com.example.radioobserver.core.radio.ui.LiveFavorites
import com.example.radioobserver.core.radio.signature.SignatureClassifier
import com.example.radioobserver.core.radio.signature.UserSignatureRule
import com.example.radioobserver.core.radio.signature.SignatureHint
import com.example.radioobserver.core.radio.signature.SignatureHintStatus
import com.example.radioobserver.core.radio.signature.SignatureInput
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
    // WORKSTREAM 02: advanced live filtering + explicit user-managed presets.
    // Same purity contract: every callback below is UI state or app-private
    // preset storage mutation — nothing starts/stops a session, requests a
    // permission, refreshes cached Wi-Fi or touches stored History.
    criteria: LiveFilter.AdvancedCriteria,
    searchQuery: String,
    onApplyAdvanced: (LiveFilter.AdvancedCriteria, String) -> Unit,
    onResetAdvanced: () -> Unit,
    presets: List<FilterPresets.FilterPreset>,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onRowSelect: (String) -> Unit,
    // WORKSTREAM 04: SESSION-LOCAL favorites — pure UI state; toggling a star
    // dispatches NOTHING (no session action, no History write) and the ids die
    // with their session (never persisted, never matched across sessions).
    favorites: Set<String>,
    favoritesOnly: Boolean,
    onToggleFavorite: (String) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
) {
    // THE single shared boundary (LiveFilter.rowsFor) feeds ALL THREE views.
    val rows = LiveFilter.rowsFor(state, filter, criteria, searchQuery)
    // WORKSTREAM 04: favorites-only applies to the LISTE view ONLY — Radar and
    // Zaman tüneli keep their unchanged membership and marker accounting.
    val displayRows = if (favoritesOnly) LiveFavorites.rowsFor(rows, favorites) else rows
    val advancedActive = LiveFilter.activeCriteriaCount(criteria, searchQuery) > 0
    // Non-persisted panel visibility; dies with the Activity like every other
    // Gözlemler UI preference.
    var filterPanelOpen by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Text(
                    text = if (state.rows.isNotEmpty()) {
                        val count = ControlScreenProjection.observationCount(state.rows)
                        pluralStringResource(R.plurals.observation_count, count, count)
                    } else stringResource(R.string.feed_empty_no_session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
                // WORKSTREAM 04: honest session-local favorites disclosure.
                if (favoritesOnly) {
                    Text(
                        text = stringResource(R.string.live_favorites_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                // WORKSTREAM 02: honest result accounting — session observations vs
                // currently displayed ones (only when advanced filtering is active).
                if (advancedActive) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.filter_result_count,
                            rows.size, rows.size, state.rows.size,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
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
                // WORKSTREAM 02: advanced filter control with an applied-criteria
                // badge. PURE UI state: expanding it dispatches nothing.
                OutlinedButton(
                    onClick = { filterPanelOpen = !filterPanelOpen },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    val active = LiveFilter.activeCriteriaCount(criteria, searchQuery)
                    Text(
                        if (active > 0) stringResource(R.string.filters_active_badge_format, active)
                        else stringResource(R.string.filters_button),
                    )
                }
                // WORKSTREAM 04: session-local favorites-only view (Liste only).
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { onFavoritesOnlyChange(!favoritesOnly) },
                    label = { Text(stringResource(R.string.live_favorites_only)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            if (filterPanelOpen) {
                FilterPanel(
                    appliedCriteria = criteria,
                    appliedQuery = searchQuery,
                    presets = presets,
                    onApplyAdvanced = onApplyAdvanced,
                    onResetAdvanced = onResetAdvanced,
                    onSavePreset = onSavePreset,
                    onApplyPreset = onApplyPreset,
                    onRenamePreset = onRenamePreset,
                    onDeletePreset = onDeletePreset,
                )
            }
            // The SAME honest empty taxonomy applies to ALL three views: a view
            // switch never invents content and never changes eligibility. With an
            // active ADVANCED filter, exclusion is reported as filtering (never
            // as "no devices found"): the filter_no_matches state keeps the
            // session-vs-display accounting above visible.
            val emptyKind = when {
                displayRows.isNotEmpty() -> null
                state.rows.isNotEmpty() -> FeedEmptyKind.NO_MATCHES_FOR_FILTER
                else -> ObservationFeedProjection.emptyKind(state, filter)
            }
            when (emptyKind) {
                null -> when (viewKind) {
                    LiveViewKind.LIST -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(displayRows, key = { it.rowId }) { row ->
                            ObservationRow(
                                row = row,
                                isFavorite = LiveFavorites.isFavorite(favorites, row.rowId),
                                onToggleFavorite = { onToggleFavorite(row.rowId) },
                                onSelect = { onRowSelect(row.rowId) },
                            )
                        }
                    }
                    LiveViewKind.RADAR -> RadarView(
                        state = state,
                        // QA 2026-10-23: the radar projection now honors the CURRENT
                        // source filter (previously hard-wired to ALL — inconsistent
                        // with Liste/Zaman tüneli whenever Bluetooth/Wi-Fi was chosen).
                        // WORKSTREAM 02: the SAME advanced criteria + search feed it.
                        filter = filter,
                        criteria = criteria,
                        query = searchQuery,
                        onRowSelect = onRowSelect,
                    )
                    LiveViewKind.TIMELINE -> TimelineView(
                        state = state,
                        filter = filter,
                        criteria = criteria,
                        query = searchQuery,
                        onRowSelect = onRowSelect,
                    )
                }
                FeedEmptyKind.SESSION_ACTIVE_NO_OBSERVATIONS -> EmptyState(stringResource(R.string.empty_feed_note))
                FeedEmptyKind.NO_SESSION_NO_OBSERVATIONS -> EmptyState(stringResource(R.string.feed_empty_no_session))
                FeedEmptyKind.NO_MATCHES_FOR_FILTER -> EmptyState(
                    if (favoritesOnly) stringResource(R.string.live_favorites_empty)
                    else if (advancedActive) stringResource(R.string.filter_no_matches)
                    else stringResource(R.string.feed_empty_filter),
                )
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
private fun ObservationRow(
    row: ObservationRowUi,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelect: () -> Unit,
) {
    val source = radioWord(row.radio)
    val fresh = freshnessWord(row.freshness)
    val name = row.name ?: stringResource(R.string.row_unnamed)
    // WORKSTREAM 03A: pure per-row classification projection from SANITIZED fields
    // only (radio + pipeline type hint + format confidence). Pure display: it never
    // changes membership, dispatches nothing, and persists nothing. Wi-Fi rows keep
    // the source chip as their structural context (no redundant hint line).
    val signatureHint = remember(row) {
        SignatureClassifier.classify(SignatureInput.fromRow(row))
    }
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
                if (signatureHint.status != SignatureHintStatus.UNKNOWN &&
                    signatureHint.status != SignatureHintStatus.CONFLICT &&
                    signatureHint.category != SignatureCategory.ACCESS_POINT
                ) {
                    Text(
                        text = stringResource(
                            R.string.signature_row_hint_format,
                            signatureCategoryLabel(signatureHint.category!!),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
            // WORKSTREAM 04: session-local favorite star. PURE UI state toggle —
            // it dispatches nothing and writes NO History record; state dies with
            // the session. 48dp target; tapping it cannot open or delete anything
            // (the row card's own onClick handles selection separately).
            val favoriteA11y = stringResource(R.string.live_favorite_toggle_a11y)
            TextButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = favoriteA11y },
            ) {
                Text(
                    text = stringResource(
                        if (isFavorite) R.string.live_favorite_star_on
                        else R.string.live_favorite_star_off),
                    style = MaterialTheme.typography.titleLarge,
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
    // WORKSTREAM 03B: user-defined catalog for UNVERIFIED suggestions (display-only).
    userRules: List<UserSignatureRule> = emptyList(),
    // WORKSTREAM 04: session-local favorite affordance (UI state, dispatches nothing).
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
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
                // WORKSTREAM 04: session-local favorite star in the detail header.
                val favoriteA11y = stringResource(R.string.live_favorite_toggle_a11y)
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = favoriteA11y },
                ) {
                    Text(
                        text = stringResource(
                            if (isFavorite) R.string.live_favorite_star_on
                            else R.string.live_favorite_star_off),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            if (detail == null) {
                EmptyState(stringResource(R.string.detail_unavailable))
            } else {
                DetailContent(detail, userRules)
            }
        }
    }
}

/** Grouped presentation of the SAME sanitized fields, one design language everywhere. */
@Composable
private fun DetailContent(row: ObservationRowUi, userRules: List<UserSignatureRule>) {
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
    // WORKSTREAM 03A: honest classification provenance section. Explains WHY a hint
    // was suggested (safe field provenance, rule id + catalog version) and shows the
    // truthful Turkish fallback when nothing supportable can be inferred.
    SignatureHintSection(row, userRules)
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
    filter: FeedFilter,
    criteria: LiveFilter.AdvancedCriteria,
    query: String,
    onRowSelect: (String) -> Unit,
) {
    // WORKSTREAM 02: identical eligibility as Liste — filtering happens BEFORE
    // marker layout and cluster accounting (JVM-tested invariant).
    val projection = LiveViewProjection.radarProjection(state, filter, criteria, query)
    val rowsForRadar = LiveFilter.rowsFor(state, filter, criteria, query)
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
        val bleColor = MaterialTheme.colorScheme.primary
        val wifiColor = MaterialTheme.colorScheme.tertiary
        val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
        val clusterColor = MaterialTheme.colorScheme.surfaceVariant
        val onClusterColor = MaterialTheme.colorScheme.onSurfaceVariant
        // Bounded schematic square: rings drawn on Canvas; markers are REAL Compose
        // elements (clickable, semantic, never dot-only) positioned deterministically.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .aspectRatio(1f),
        ) {
            val canvasSide = maxWidth
            val maxR = canvasSide / 2
            val center = canvasSide / 2
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rMax = size.minDimension / 2f
                val bands = LiveViewProjection.RING_ORDER.size
                for (i in 0 until bands) {
                    drawCircle(
                        color = ringColor.copy(alpha = 0.35f),
                        radius = rMax * ((i + 1).toFloat() / bands),
                        center = Offset(cx, cy),
                        style = Stroke(width = 2f),
                    )
                }
            }
            projection.markers.forEach { marker ->
                val angleRad = Math.toRadians(marker.angleDegrees)
                val x = center + maxR * (marker.radiusFraction * Math.sin(angleRad)).toFloat()
                val y = center - maxR * (marker.radiusFraction * Math.cos(angleRad)).toFloat()
                if (marker.isCluster) {
                    // Explicit count-bearing cluster: members stay individually
                    // reachable in the adjoining list (also announced here).
                    val clusterA11y = pluralStringResource(
                        R.plurals.a11y_radar_cluster, marker.count, marker.count,
                    )
                    Box(
                        modifier = Modifier
                            .offset(x - 20.dp, y - 20.dp)
                            .size(40.dp)
                            .semantics { contentDescription = clusterA11y },
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = clusterColor,
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                if (marker.radio == RadioType.BLE) bleColor else wifiColor,
                            ),
                        ) {
                            Text(
                                text = marker.count.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = onClusterColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                } else {
                    val dot = marker.members.first()
                    val name = dot.name ?: stringResource(R.string.row_unnamed)
                    val markerA11y = stringResource(
                        R.string.a11y_radar_marker_format, name, radioWord(dot.radio),
                        stringResource(
                            when (dot.freshness) {
                                FreshnessState.FRESH -> R.string.freshness_fresh
                                FreshnessState.STALE -> R.string.freshness_stale
                                FreshnessState.UNKNOWN -> R.string.freshness_unknown
                            },
                        ),
                    )
                    val shape = if (dot.radio == RadioType.BLE) CircleShape
                        else androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                    val color = if (dot.radio == RadioType.BLE) bleColor else wifiColor
                    val hollow = dot.freshness == FreshnessState.STALE
                    // 32dp touch target around a 16dp symbol: dots are tappable and
                    // announced; the list below remains the always-available path.
                    Box(
                        modifier = Modifier
                            .offset(x - 16.dp, y - 16.dp)
                            .size(32.dp)
                            .clickable(onClick = { onRowSelect(dot.rowId) })
                            .semantics { contentDescription = markerA11y },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .let {
                                    if (hollow) {
                                        it.border(2.dp, color, shape)
                                    } else {
                                        it.background(color, shape)
                                    }
                                },
                        )
                    }
                }
            }
        }
        // Legend: taxonomy + shape + freshness provenance + cluster semantics.
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
            Text(
                text = stringResource(R.string.radar_legend_cluster),
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
        // Adjacent accessible list: EVERY filtered row (including cluster members
        // and overflowed ones) stays reachable without the canvas. Same keys, same
        // detail navigation.
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
    criteria: LiveFilter.AdvancedCriteria,
    query: String,
    onRowSelect: (String) -> Unit,
) {
    // WORKSTREAM 02: identical eligibility as Liste; ordering stays the honest
    // last-seen chronology with the stable rowId tie-break.
    val entries = LiveViewProjection.timelineProjection(state, filter, criteria, query)
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

/**
 * WORKSTREAM 02 — advanced filter panel + saved preset management, all inside the
 * existing Gözlemler tab (no new navigation destination). Purity contract: the panel
 * edits a LOCAL DRAFT and commits ONLY on Uygula (explicit Apply/Reset actions);
 * applying a preset updates the applied state which resyncs this draft. Nothing
 * here dispatches sessions, permissions or History writes. The preset section
 * delegates to the app-private repository via MainActivity callbacks.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(
    appliedCriteria: LiveFilter.AdvancedCriteria,
    appliedQuery: String,
    presets: List<FilterPresets.FilterPreset>,
    onApplyAdvanced: (LiveFilter.AdvancedCriteria, String) -> Unit,
    onResetAdvanced: () -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onDeletePreset: (String) -> Unit,
) {
    // Draft state: resyncs whenever the applied state changes externally
    // (preset applied / reset) so the panel always reflects reality.
    var draftCriteria by remember(appliedCriteria) { mutableStateOf(appliedCriteria) }
    var draftQuery by remember(appliedQuery) { mutableStateOf(appliedQuery) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FilterPresets.FilterPreset?>(null) }
    var deleteTarget by remember { mutableStateOf<FilterPresets.FilterPreset?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(R.string.filter_panel_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (LiveFilter.activeCriteriaCount(appliedCriteria, appliedQuery) == 0) {
                    stringResource(R.string.filter_summary_unrestricted)
                } else {
                    val applied = LiveFilter.activeCriteriaCount(appliedCriteria, appliedQuery)
                    pluralStringResource(R.plurals.filter_summary_count, applied, applied)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Sinyal kategorisi: verified taxonomy only, multi-select (OR).
            FilterGroupLabel(stringResource(R.string.filter_category_signal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    SignalBucket.IMMEDIATE to R.string.signal_immediate,
                    SignalBucket.NEAR to R.string.signal_near,
                    SignalBucket.FAR to R.string.signal_far,
                    SignalBucket.UNAVAILABLE to R.string.signal_unavailable,
                ).forEach { (bucket, label) ->
                    FilterChip(
                        selected = bucket in draftCriteria.signalBuckets,
                        onClick = {
                            draftCriteria = draftCriteria.copy(
                                signalBuckets =
                                    if (bucket in draftCriteria.signalBuckets)
                                        draftCriteria.signalBuckets - bucket
                                    else draftCriteria.signalBuckets + bucket,
                            )
                        },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            // Tazelik: FRESH/STALE/UNKNOWN multi-select; UNKNOWN is a deliberate
            // choice, never a silent fallback.
            FilterGroupLabel(stringResource(R.string.filter_category_freshness))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    FreshnessState.FRESH to R.string.freshness_fresh,
                    FreshnessState.STALE to R.string.freshness_stale,
                    FreshnessState.UNKNOWN to R.string.freshness_unknown,
                ).forEach { (fresh, label) ->
                    FilterChip(
                        selected = fresh in draftCriteria.freshness,
                        onClick = {
                            draftCriteria = draftCriteria.copy(
                                freshness =
                                    if (fresh in draftCriteria.freshness)
                                        draftCriteria.freshness - fresh
                                    else draftCriteria.freshness + fresh,
                            )
                        },
                        label = { Text(stringResource(label)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            // Üretici ipucu: known/unknown toggle; hints are never identity proof.
            FilterGroupLabel(stringResource(R.string.filter_category_manufacturer))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = draftCriteria.manufacturerKnownOnly == true,
                    onClick = {
                        draftCriteria = draftCriteria.copy(
                            manufacturerKnownOnly =
                                if (draftCriteria.manufacturerKnownOnly == true) null else true,
                        )
                    },
                    label = { Text(stringResource(R.string.filter_manufacturer_known)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = draftCriteria.manufacturerKnownOnly == false,
                    onClick = {
                        draftCriteria = draftCriteria.copy(
                            manufacturerKnownOnly =
                                if (draftCriteria.manufacturerKnownOnly == false) null else false,
                        )
                    },
                    label = { Text(stringResource(R.string.filter_manufacturer_unknown)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }

            // Geçici metin arama: sanitized display names only; never persisted.
            OutlinedTextField(
                value = draftQuery,
                onValueChange = { draftQuery = it },
                label = { Text(stringResource(R.string.filter_search_label)) },
                supportingText = { Text(stringResource(R.string.filter_search_note)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Button(
                    onClick = { onApplyAdvanced(draftCriteria, draftQuery) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.filter_apply)) }
                OutlinedButton(
                    onClick = {
                        // Reset: unrestricted criteria + cleared search, both draft
                        // and applied (Restores the All-sources default).
                        draftCriteria = LiveFilter.AdvancedCriteria()
                        draftQuery = ""
                        onResetAdvanced()
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.filter_reset)) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))

            // Kayıtlı ön ayarlar: explicit user-managed only (never auto-saved).
            Text(
                text = stringResource(R.string.filter_presets_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (presets.isEmpty()) {
                Text(
                    text = stringResource(R.string.filter_presets_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                presets.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onApplyPreset(preset.id) }) {
                            Text(stringResource(R.string.filter_preset_apply))
                        }
                        TextButton(onClick = { renameTarget = preset }) {
                            Text(stringResource(R.string.filter_preset_rename))
                        }
                        TextButton(onClick = { deleteTarget = preset }) {
                            Text(stringResource(R.string.filter_preset_delete))
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { showSaveDialog = true },
                modifier = Modifier
                    .padding(top = Spacing.xs)
                    .heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.filter_preset_save)) }
        }
    }

    // --- Preset dialogs (save / rename / delete confirmation) ---

    if (showSaveDialog) {
        PresetNameDialog(
            title = stringResource(R.string.filter_preset_save_title),
            initialName = "",
            presets = presets,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                onSavePreset(name)
                showSaveDialog = false
            },
        )
    }
    renameTarget?.let { target ->
        PresetNameDialog(
            title = stringResource(R.string.filter_preset_rename_title),
            initialName = target.name,
            presets = presets.filter { it.id != target.id },
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRenamePreset(target.id, name)
                renameTarget = null
            },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.filter_preset_delete_title)) },
            text = {
                Text(stringResource(R.string.filter_preset_delete_confirm, target.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(target.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.filter_preset_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun FilterGroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = Spacing.sm)
            .semantics { heading() },
    )
}

/**
 * Save/rename dialog with LOCAL validation (non-blank, ≤40 chars, no duplicate
 * against the current list, case-folded). The repository re-validates — the local
 * check only keeps the dialog honest and deterministic.
 */
@Composable
private fun PresetNameDialog(
    title: String,
    initialName: String,
    presets: List<FilterPresets.FilterPreset>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val invalid = trimmed.isBlank() || trimmed.length > FilterPresets.Repository.MAX_NAME_LENGTH
    val duplicate = presets.any { LiveFilter.fold(it.name) == LiveFilter.fold(trimmed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text(stringResource(R.string.filter_preset_name_label)) },
                    singleLine = true,
                    isError = invalid || duplicate,
                )
                if (invalid) {
                    Text(
                        text = stringResource(R.string.filter_preset_name_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (duplicate) {
                    Text(
                        text = stringResource(R.string.filter_preset_name_duplicate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !invalid && !duplicate,
                onClick = { onConfirm(trimmed) },
            ) { Text(stringResource(R.string.filter_preset_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/**
 * WORKSTREAM 03A — detail classification provenance section. Shows the broad family
 * hint (when supportable), its status, the matching rule id + catalog version and the
 * non-sensitive rationale, or the honest "could not be determined" fallback. Never
 * claims identity, model, direction or distance; never implies two observations are
 * the same physical device.
 */
@Composable
private fun SignatureHintSection(row: ObservationRowUi, userRules: List<UserSignatureRule>) {
    // WS03B: built-in verdict is IDENTICAL to WS03A; user rules only add SEPARATE
    // unverified suggestions, never an upgrade or override.
    val hint = remember(row, userRules) {
        SignatureClassifier.classifyWithUserRules(
            SignatureInput.fromRow(row), userRules = userRules,
        )
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SectionHeader(stringResource(R.string.signature_detail_section))
            when (hint.status) {
                SignatureHintStatus.UNKNOWN -> {
                    Text(
                        text = stringResource(R.string.signature_detail_unknown_title),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.signature_detail_unknown_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SignatureHintStatus.CONFLICT -> {
                    Text(
                        text = stringResource(R.string.signature_status_conflict),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.signature_conflict_format,
                            hint.matchedRuleIds.joinToString(", "),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.signature_caution),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    hint.category?.let { category ->
                        Text(
                            text = stringResource(
                                R.string.signature_row_hint_format,
                                signatureCategoryLabel(category),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = when (hint.status) {
                                SignatureHintStatus.SUPPORTED ->
                                    stringResource(R.string.signature_status_supported)
                                else -> stringResource(R.string.signature_status_ambiguous)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        hint.rationale.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        hint.matchedRuleIds.forEach { ruleId ->
                            Text(
                                text = stringResource(
                                    R.string.signature_rule_format,
                                    ruleId, hint.catalogVersion,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(R.string.signature_caution),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (hint.userSuggestions.isNotEmpty()) {
                SectionHeader(stringResource(R.string.signature_detail_user_section))
                hint.userSuggestions.forEach { s ->
                    Text(
                        text = stringResource(
                            R.string.signature_row_hint_format,
                            signatureCategoryLabel(s.category),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.signature_catalog_unverified_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.signature_rule_format, s.ruleId, s.schemaVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.signature_catalog_why_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun signatureCategoryLabel(category: SignatureCategory): String = when (category) {
    SignatureCategory.ACCESS_POINT -> stringResource(R.string.signature_category_access_point)
    SignatureCategory.IBEACON_FAMILY -> stringResource(R.string.signature_category_ibeacon_family)
    SignatureCategory.FIND_MY_FAMILY -> stringResource(R.string.signature_category_find_my_family)
}
