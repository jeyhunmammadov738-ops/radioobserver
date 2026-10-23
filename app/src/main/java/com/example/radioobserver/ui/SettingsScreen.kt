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

/**
 * AYARLAR tab — the single source-of-truth UI for the optional local history
 * preferences (post-roadmap UI iteration, 2026-10-23). Drives the SAME existing
 * [HistoryController] state Geçmiş reads: no duplicate settings, no preference
 * resets, no new storage. Recording and Wi-Fi-names consent stay OFF by default;
 * retention stays 7/14/30 days hard-capped at 30; the Clear All entry uses the
 * SAME confirmed controller behavior.
 *
 * "Uygulama hakkında" hosts the DEBUG-only developer access: tapping the version
 * label SEVEN times unlocks (in-memory only) the developer section with
 * Tanılamayı göster/gizle. Cold start always re-hides the FieldDiag panel.
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
) {
    var clearAllConfirm by remember { mutableStateOf(false) }
    val historyToggleA11y = stringResource(R.string.history_recording_toggle_a11y)
    val historyWifiNamesA11y = stringResource(R.string.history_wifi_names_toggle_a11y)
    val versionA11y = stringResource(R.string.app_version_a11y)
    val diagToggleA11y = stringResource(R.string.diag_toggle_a11y)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        // ---- Geçmiş kaydı: consent + separate Wi-Fi-names consent + retention ----
        SettingsCard(R.string.settings_history_section) {
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
                    onCheckedChange = {
                        onConsentChange(state.consent.copy(recordingEnabled = it))
                    },
                    modifier = Modifier.semantics { contentDescription = historyToggleA11y },
                )
            }
            Text(stringResource(R.string.history_consent_body), style = MaterialTheme.typography.bodySmall)

            if (state.consent.recordingEnabled) {
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
                        modifier = Modifier.semantics { contentDescription = historyWifiNamesA11y },
                    )
                }
                Text(stringResource(R.string.history_wifi_names_body), style = MaterialTheme.typography.bodySmall)

                Text(
                    stringResource(R.string.history_retention_label),
                    style = MaterialTheme.typography.titleMedium,
                )
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

        // ---- Gizlilik ve çalışma şekli: truthful copy, always reachable ----
        SettingsCard(R.string.settings_privacy_section) {
            Text(stringResource(R.string.scope_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.background_stop_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.wifi_passive_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.ble_bounded_note), style = MaterialTheme.typography.bodySmall)
        }

        // ---- Uygulama hakkında: version label hosts the DEBUG-only unlock ----
        SettingsCard(R.string.settings_about_section) {
            // The version label itself is the tappable target: SEVEN intentional
            // taps unlock the developer section (no visible hint — deliberate
            // developer-only control; nothing is persisted).
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
            SettingsCard(R.string.diag_section_title) {
                Text(stringResource(R.string.diag_hint), style = MaterialTheme.typography.bodySmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(if (diagVisible) R.string.diag_hide else R.string.diag_show),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = diagVisible,
                        onCheckedChange = onDiagVisibilityChange,
                        modifier = Modifier.semantics { contentDescription = diagToggleA11y },
                    )
                }
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
private fun RetentionChips(retentionDays: Int, onRetentionChange: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun SettingsCard(titleRes: Int, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}
