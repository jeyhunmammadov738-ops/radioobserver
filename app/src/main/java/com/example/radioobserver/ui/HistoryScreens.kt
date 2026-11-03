package com.example.radioobserver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.radioobserver.core.radio.history.HistoryAnnotation
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import com.example.radioobserver.history.HistoryAgeFormat
import com.example.radioobserver.history.HistoryController
import com.example.radioobserver.ui.components.CompactInfoRow
import com.example.radioobserver.ui.components.EmptyState
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.SourceIcon
import com.example.radioobserver.ui.components.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * PREMIUM UI/UX V2 Geçmiş (post-roadmap, 2026-10-23).
 * Defects fixed vs. the previous build: oversized history cards with large blank
 * areas and disproportionate actions → compact one-line-density rows with a clear
 * two-action footer (Ayrıntılar / Sil, both 48dp targets; row tap can NEVER delete).
 * The history detail now uses the SAME shared card/CompactInfoRow design language as
 * the live detail — the inconsistent near-black rectangular sub-panels are gone —
 * and the raw millisecond record age is shown as honest, human-readable Turkish
 * ("yaklaşık 47 dakika"), clearly labeled as the age AT RECORDING TIME.
 * Behavior UNCHANGED: browsing only; single delete and confirmed Clear All keep the
 * existing controller/store behavior; deleting history never touches live sessions;
 * saved records are never presented as currently broadcasting devices.
 */
enum class HistoryFilter { ALL, BLUETOOTH, WIFI }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    state: HistoryController.UiState,
    filter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    // WORKSTREAM 04: saved-record favorites filter + explicit star toggles.
    // A saved favorite belongs to ONE History record — it is NOT a persistent
    // device identity and can never match another session's observations.
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteOne: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismissNotices: () -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSettings: () -> Unit,
    // WORKSTREAM 06: explicit export entry (reads saved data only).
    onOpenReport: () -> Unit,
    // WORKSTREAM 05: analytical groups entry (Geçmiş area; no fifth tab).
    onOpenGroups: () -> Unit = {},
) {
    var clearAllConfirm by remember { mutableStateOf(false) }
    val historyClearAllA11y = stringResource(R.string.history_clear_all_a11y)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
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

            Text(
                text = stringResource(R.string.history_count, state.records.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            HistoryFilterRow(filter, onFilterChange)
            OutlinedButton(
                onClick = onOpenReport,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.report_open)) }
            // WORKSTREAM 05: functional entry to named analytical groups
            // (user-created selections of SAVED records; opens nothing that scans).
            OutlinedButton(
                onClick = onOpenGroups,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.group_entry_button)) }
            // WORKSTREAM 04: favorites-only chip (no new navigation tab).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = favoritesOnly,
                    onClick = { onFavoritesOnlyChange(!favoritesOnly) },
                    label = { Text(stringResource(R.string.history_favorites_only)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            val visible = state.records.filter {
                when (filter) {
                    HistoryFilter.ALL -> true
                    HistoryFilter.BLUETOOTH -> it.radio == RadioType.BLE
                    HistoryFilter.WIFI -> it.radio == RadioType.WIFI
                }
            }.filter { !favoritesOnly || state.annotations[it.id]?.favorite == true }
            if (visible.isEmpty()) {
                // Honest empty state + a contextual pointer to the preferences in Ayarlar.
                EmptyState(stringResource(R.string.history_empty))
                if (!state.consent.recordingEnabled) {
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.history_open_settings)) }
                }
            } else {
                visible.forEach { record ->
                    HistoryRecordRow(
                        record = record,
                        isFavorite = state.annotations[record.id]?.favorite == true,
                        onToggleFavorite = { onToggleFavorite(record.id) },
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(text, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.history_notice_dismiss))
            }
        }
    }
}

/**
 * Compact history row: source icon, safe name, source + timestamp meta line, and a
 * clear two-action footer. Deleting requires the EXPLICIT Sil button (48dp) — a row
 * tap can never delete; Ayrıntılar opens the SAME sanitized detail path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRecordRow(
    record: HistoryRecord,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenDetail: () -> Unit,
    onDelete: () -> Unit,
) {
    val safeName = record.displayName ?: stringResource(R.string.history_unnamed)
    val detailA11y = stringResource(R.string.history_open_detail_a11y, safeName)
    val deleteA11y = stringResource(R.string.history_delete_one_a11y, safeName)
    // MICRO-POLISH density: the previous card stacked TWO bands (header, then a
    // full-width 48dp actions row) — the main source of the remaining empty space.
    // Now the meta line (source · date) shares ONE band with the actions: the meta
    // takes the remaining width (wrapping over multiple lines at 2.0x), and the two
    // explicit buttons keep their enforced 48dp targets with slimmed contentPadding.
    // The 48dp accessibility minimum itself cannot shrink — that residual height is
    // deliberate, not wasted. A card tap still does NOTHING.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceIcon(radio = record.radio)
                Text(
                    text = safeName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(
                        R.string.history_row_line,
                        radioLabel(record.radio),
                        formatTime(record.savedAtEpochMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // Wraps (never clips) long meta at 320dp/2.0x; the buttons flow
                    // to the next line only when the width genuinely runs out.
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = onOpenDetail,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = detailA11y },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text(stringResource(R.string.history_open_detail))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = deleteA11y },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                ) {
                    Text(stringResource(R.string.history_delete_one))
                }
                // WORKSTREAM 04: record-bound favorite star (one specific History
                // record; never a device identity; never starts an observation).
                val favoriteA11y = stringResource(R.string.history_favorite_toggle_a11y)
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = favoriteA11y },
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
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
}

@Composable
fun HistoryDetailScreen(
    record: HistoryRecord?,
    // WORKSTREAM 04: record-bound annotation (label/note/favorite). Bounded input,
    // explicit user actions only; dies with its record (no orphan resurrection).
    annotation: HistoryAnnotation? = null,
    onSaveAnnotation: (String?, String?, Boolean) -> Unit = { _, _, _ -> },
    onDeleteAnnotation: () -> Unit = {},
    onBack: () -> Unit,
) {
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
                EmptyState(stringResource(R.string.history_detail_missing))
                return@Column
            }
            // WORKSTREAM 04: record-bound annotation editor card.
            HistoryAnnotationCard(
                recordId = record.id,
                annotation = annotation,
                onSave = onSaveAnnotation,
                onDelete = onDeleteAnnotation,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceIcon(radio = record.radio)
                        Text(
                            text = record.displayName ?: stringResource(R.string.history_unnamed),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SectionHeader(stringResource(R.string.detail_section_source_time))
                    CompactInfoRow(stringResource(R.string.history_detail_radio), radioLabel(record.radio))
                    CompactInfoRow(
                        stringResource(R.string.history_detail_name),
                        record.displayName ?: stringResource(R.string.history_unnamed),
                    )
                    CompactInfoRow(
                        stringResource(R.string.history_detail_saved_at),
                        formatTime(record.savedAtEpochMs),
                    )
                    CompactInfoRow(
                        stringResource(R.string.history_detail_observed_at),
                        formatTime(record.observedAtEpochMs),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.detail_section_signal))
                    CompactInfoRow(
                        stringResource(R.string.history_detail_signal),
                        signalBucketLabel(record.signalBucket),
                    )
                    CompactInfoRow(
                        stringResource(R.string.history_detail_freshness),
                        freshnessLabel(record.freshness),
                    )
                    CompactInfoRow(
                        stringResource(R.string.history_detail_age),
                        // Kayıt ANINDAKİ yaş — mevcut geçen süre değil; ham milisaniye
                        // yerine dürüst insan-dostu biçim ("yaklaşık 47 dakika").
                        record.ageMs?.let { HistoryAgeFormat.formatApproximate(it) }
                            ?: stringResource(R.string.history_detail_age_unknown),
                    )
                    // FINAL POLISH: sinyal kategorisi KABA bir ipucudur ve kayıt
                    // anına aittir; şu anki yakınlık kanıtı değildir.
                    Text(stringResource(R.string.signal_qualitative_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.history_signal_record_note), style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader(stringResource(R.string.detail_section_hints))
                    record.manufacturerHint?.let { hint ->
                        // Classifier English sentinels localized at the presentation
                        // boundary; real OUI hints pass through unchanged. The locally
                        // administered sentinel additionally shows the honest Turkish
                        // explanation. Never a BSSID, never an invented vendor.
                        val bleUnknownId = ManufacturerPresentation.bleUnknownManufacturerId(hint)
                        when {
                            ManufacturerPresentation.isKnownFallback(hint) -> {
                                CompactInfoRow(
                                    stringResource(R.string.history_detail_manufacturer),
                                    stringResource(R.string.manufacturer_unavailable),
                                )
                                if (ManufacturerPresentation.isLocallyAdministeredFallback(hint)) {
                                    Text(
                                        text = stringResource(R.string.manufacturer_local_address_explanation),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // BLE sentinel: honest Turkish + the raw company id as
                            // SEPARATE context; the code never identifies a vendor.
                            bleUnknownId != null -> {
                                CompactInfoRow(
                                    stringResource(R.string.history_detail_manufacturer),
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
                            else -> CompactInfoRow(stringResource(R.string.history_detail_manufacturer), hint)
                        }
                    }
                    record.typeHint?.let {
                        CompactInfoRow(stringResource(R.string.history_detail_type), it)
                    }
                    // MICRO-POLISH: the coarse-signal caveat lives ONCE, in the
                    // signal card above; this card keeps only its DISTINCT warnings
                    // (recorded-time semantics is in the signal card, privacy here).
                    Text(stringResource(R.string.history_privacy_note), style = MaterialTheme.typography.bodySmall)
                }
            }
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


/** WORKSTREAM 04: label/note/favorite editor for ONE saved History record. */
@Composable
private fun HistoryAnnotationCard(
    recordId: String,
    annotation: HistoryAnnotation?,
    onSave: (String?, String?, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var editing by remember(recordId, annotation) { mutableStateOf(false) }
    var label by remember(recordId, annotation) { mutableStateOf(annotation?.label ?: "") }
    var note by remember(recordId, annotation) { mutableStateOf(annotation?.note ?: "") }
    var favorite by remember(recordId, annotation) { mutableStateOf(annotation?.favorite ?: false) }
    var tooLong by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SectionHeader(stringResource(R.string.history_annotation_section))
            Text(
                text = stringResource(R.string.history_annotation_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!editing) {
                if (annotation == null) {
                    Text(
                        text = stringResource(R.string.history_annotation_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    annotation.label?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium)
                    }
                    annotation.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        label = annotation?.label ?: ""
                        note = annotation?.note ?: ""
                        favorite = annotation?.favorite ?: false
                        tooLong = false
                        editing = true
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (annotation == null) R.string.history_annotation_edit
                            else R.string.history_annotation_edit),
                    )
                }
            } else {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.history_annotation_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.history_annotation_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.history_annotation_favorite))
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
                if (tooLong) {
                    Text(
                        text = stringResource(R.string.history_annotation_too_long),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(
                        onClick = {
                            if (label.trim().length > 40 || note.trim().length > 200) {
                                tooLong = true
                                return@TextButton
                            }
                            onSave(label.ifBlank { null }, note.ifBlank { null }, favorite)
                            editing = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.history_annotation_save)) }
                    TextButton(
                        onClick = { editing = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.history_cancel)) }
                    TextButton(
                        onClick = { onDelete(); editing = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.history_annotation_remove)) }
                }
            }
        }
    }
}
