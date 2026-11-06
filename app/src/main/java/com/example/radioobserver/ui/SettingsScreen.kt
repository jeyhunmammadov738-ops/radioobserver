package com.example.radioobserver.ui

import androidx.compose.animation.AnimatedVisibility
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
import com.example.radioobserver.history.HistoryController
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.Spacing

/**
 * PREMIUM UI/UX V2 Ayarlar (post-roadmap, 2026-10-23).
 * Defects fixed vs. the previous build: long consent/privacy paragraphs overpowering
 * the controls → GROUPED preferences with real hierarchy. Each toggle carries a short
 * truthful summary line; the full consent/privacy text stays one tap away in
 * expandable sections (nothing essential deleted or hidden by default from consent
 * meaning — recording keeps its full consent text immediately visible on FIRST
 * enable via the expansion, and the summary always states the core fact).
 * Behavior UNCHANGED: the SAME HistoryController/preferences; recording default OFF,
 * separate Wi-Fi-names consent OFF, 7/14/30-day retention, 500-record capacity,
 * confirmed Clear All, DEBUG-only developer unlock preserved.
 */
@Composable
fun SettingsScreen(
    state: HistoryController.UiState,
    appVersion: String,
    isDebugBuild: Boolean,
    diagUnlocked: Boolean,
    diagVisible: Boolean,
    onConsentChange: (HistoryConsent) -> Unit,
    onRetentionChange: (Int) -> Unit,
    onClearAll: () -> Unit,
    onVersionTap: () -> Unit,
    onDiagVisibilityChange: (Boolean) -> Unit,
    onOpenSignatureCatalog: () -> Unit,
) {
    var clearAllConfirm by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var consentExpanded by remember { mutableStateOf(false) }
    var wifiNamesExpanded by remember { mutableStateOf(false) }
    val historyToggleA11y = stringResource(R.string.history_recording_toggle_a11y)
    val historyWifiNamesA11y = stringResource(R.string.history_wifi_names_toggle_a11y)
    val versionA11y = stringResource(R.string.app_version_a11y)
    val diagToggleA11y = stringResource(R.string.diag_toggle_a11y)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // ---- Geçmiş kaydı: toggle + short summary; full consent one tap away ----
        SettingsGroup(R.string.settings_history_section) {
            ToggleRow(
                label = stringResource(R.string.history_recording_toggle),
                a11y = historyToggleA11y,
                checked = state.consent.recordingEnabled,
                onCheckedChange = { onConsentChange(state.consent.copy(recordingEnabled = it)) },
            )
            Text(
                text = stringResource(
                    if (state.consent.recordingEnabled) R.string.settings_summary_recording_on
                    else R.string.settings_summary_recording_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpandableText(
                expanded = consentExpanded,
                onToggle = { consentExpanded = !consentExpanded },
                bodyRes = listOf(R.string.history_consent_body),
            )

            if (state.consent.recordingEnabled) {
                ToggleRow(
                    label = stringResource(R.string.history_wifi_names_toggle),
                    a11y = historyWifiNamesA11y,
                    checked = state.consent.wifiNamesEnabled,
                    onCheckedChange = { onConsentChange(state.consent.copy(wifiNamesEnabled = it)) },
                )
                Text(
                    text = stringResource(
                        if (state.consent.wifiNamesEnabled) R.string.settings_summary_wifi_names_on
                        else R.string.settings_summary_wifi_names_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The FULL Wi-Fi-names consent text stays one tap away (essential
                // consent information; never deleted).
                ExpandableText(
                    expanded = wifiNamesExpanded,
                    onToggle = { wifiNamesExpanded = !wifiNamesExpanded },
                    bodyRes = listOf(R.string.history_wifi_names_body),
                )

                SectionHeader(stringResource(R.string.history_retention_label))
                RetentionChips(state.consent.retentionDays, onRetentionChange)
            }

            if (state.records.isNotEmpty()) {
                // Dangerous action, clearly separated, SAME confirmed controller path.
                OutlinedButton(
                    onClick = { clearAllConfirm = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.settings_clear_history_entry)) }
            }
        }

        // ---- Gizlilik ve çalışma şekli: short line + expandable full copy ----
        SettingsGroup(R.string.settings_privacy_section) {
            Text(
                text = stringResource(R.string.settings_privacy_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpandableText(
                expanded = privacyExpanded,
                onToggle = { privacyExpanded = !privacyExpanded },
                bodyRes = listOf(
                    R.string.scope_note,
                    R.string.privacy_body,
                    R.string.background_stop_note,
                    R.string.wifi_passive_note,
                    R.string.ble_bounded_note,
                ),
            )
        }

        // ---- Uygulama hakkında: version label hosts the DEBUG-only unlock ----
        SettingsGroup(R.string.settings_about_section) {
            // The version label itself is the tappable target: SEVEN intentional
            // taps unlock the developer section (no visible hint — deliberate
            // developer-only control; nothing is persisted).
            // WORKSTREAM 03B: user classification-catalog management entry
            // (Ayarlar owns it; NO fifth bottom tab). Pure navigation.
            OutlinedButton(
                onClick = onOpenSignatureCatalog,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.signature_catalog_open))
            }
            TextButton(
                onClick = onVersionTap,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = versionA11y },
            ) {
                Text(
                    text = stringResource(R.string.app_version_format, appVersion),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // ---- Developer section: DEBUG builds only, unlocked by the seven taps ----
        if (isDebugBuild && diagUnlocked) {
            SettingsGroup(R.string.diag_section_title) {
                Text(stringResource(R.string.diag_hint), style = MaterialTheme.typography.bodySmall)
                ToggleRow(
                    label = stringResource(
                        if (diagVisible) R.string.diag_hide else R.string.diag_show),
                    a11y = diagToggleA11y,
                    checked = diagVisible,
                    onCheckedChange = onDiagVisibilityChange,
                )
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

/** One labeled switch row with a 48dp target; the label is a heading for scanning. */
@Composable
private fun ToggleRow(
    label: String,
    a11y: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = a11y },
        )
    }
}

/** Collapsed long-form copy with an explicit, accessible expand/collapse control. */
@Composable
private fun ExpandableText(
    expanded: Boolean,
    onToggle: () -> Unit,
    bodyRes: List<Int>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(
                text = stringResource(
                    if (expanded) R.string.card_info_collapse else R.string.card_info_expand),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                bodyRes.forEach { res -> Text(stringResource(res), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetentionChips(retentionDays: Int, onRetentionChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        HistoryLimits.ALLOWED_RETENTION_DAYS.forEach { days ->
            FilterChip(
                selected = retentionDays == days,
                onClick = { onRetentionChange(days) },
                label = { Text(stringResource(R.string.history_retention_days, days)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun SettingsGroup(titleRes: Int, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SectionHeader(stringResource(titleRes))
            content()
        }
    }
}
