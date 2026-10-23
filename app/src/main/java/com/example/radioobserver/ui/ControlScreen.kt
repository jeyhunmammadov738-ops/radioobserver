package com.example.radioobserver.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.ui.ControlScreenProjection
import com.example.radioobserver.core.radio.ui.RecoveryStatusProjection
import com.example.radioobserver.core.radio.ui.RecoveryStatusProjection.RadioRecovery
import com.example.radioobserver.core.radio.ui.LocationAccessUi
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.core.radio.ui.RadioSessionUiState
import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.core.radio.ui.SessionIssueKey

/**
 * Post-roadmap professional UI iteration (developer-approved, 2026-10-23):
 * compact, professional MATERIAL 3 OBSERVATION DASHBOARD (Turkish).
 *
 * Redesign of the Phase 3.5 screen: the oversized title + always-visible paragraph
 * stack (phone screenshots) is replaced by a compact heading, a truthful session
 * summary and the two control cards FIRST; the long educational/privacy copy moves
 * into a collapsed, accessible "Bilgi ve gizlilik" disclosure. Feed/history entry is
 * now the four-tab bottom bar (no ad-hoc buttons on the dashboard).
 *
 * UNCHANGED FUNCTIONAL CONTRACT: a STATELESS renderer of the immutable sanitized
 * [ObservationUiState]; actions leave ONLY through explicit user-tap callbacks.
 * No session is ever started from composition, recomposition or navigation.
 */
@Composable
fun RadioObserverControlScreen(
    state: ObservationUiState,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit,
    onStartWifi: () -> Unit,
    onStopWifi: () -> Unit,
    onOpenPermissionSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onRecheckBle: () -> Unit = {},
    onRecheckWifi: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Compact branded heading: title + one-line subtitle, no paragraph stack.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.control_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.dashboard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Truthful summary chip: live observation count of the CURRENT session.
            if (state.rows.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.observation_count,
                        ControlScreenProjection.observationCount(state.rows),
                        ControlScreenProjection.observationCount(state.rows)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.empty_feed_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Permission guidance stays honest and contextual (unchanged copy).
            if (state.locationAccess == LocationAccessUi.NOT_REQUESTED ||
                state.locationAccess == LocationAccessUi.DENIED
            ) {
                Text(
                    text = stringResource(R.string.precise_location_rationale),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.locationAccess == LocationAccessUi.APPROXIMATE_ONLY) {
                Text(
                    text = stringResource(R.string.approximate_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            RadioControlCard(
                title = stringResource(R.string.ble_card_title),
                radioName = stringResource(R.string.radio_name_ble),
                session = state.bleSession,
                note = stringResource(R.string.ble_bounded_note),
                startLabel = stringResource(R.string.ble_start_button),
                stopLabel = stringResource(R.string.ble_stop_button),
                onStart = onStartBle,
                onStop = onStopBle,
                locationAccess = state.locationAccess,
                onOpenPermissionSettings = onOpenPermissionSettings,
                onOpenLocationSettings = onOpenLocationSettings,
                onRecheck = onRecheckBle,
            )
            RadioControlCard(
                title = stringResource(R.string.wifi_card_title),
                radioName = stringResource(R.string.radio_name_wifi),
                session = state.wifiSession,
                note = stringResource(R.string.wifi_passive_note),
                startLabel = stringResource(R.string.wifi_start_button),
                stopLabel = stringResource(R.string.wifi_stop_button),
                onStart = onStartWifi,
                onStop = onStopWifi,
                locationAccess = state.locationAccess,
                onOpenPermissionSettings = onOpenPermissionSettings,
                onOpenLocationSettings = onOpenLocationSettings,
                onRecheck = onRecheckWifi,
            )

            InfoDisclosure()
        }
    }
}

/**
 * Collapsed-by-default accessible disclosure: the long educational and privacy copy
 * (scope, temporary live observations, optional persisted history, background stop)
 * stays available and truthful — without burying the Start controls below the fold.
 */
@Composable
private fun InfoDisclosure() {
    var expanded by remember { mutableStateOf(false) }
    val expandA11y = stringResource(R.string.info_section_expand_a11y)
    val collapseA11y = stringResource(R.string.info_section_collapse_a11y)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.info_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .semantics {
                            contentDescription = if (expanded) collapseA11y else expandA11y
                        },
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) R.string.info_section_collapse else R.string.info_section_expand
                        ),
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.scope_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.background_stop_note), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** One radio's control card: honest status, issue text, Start/Stop, passivity note. */
@Composable
private fun RadioControlCard(
    title: String,
    radioName: String,
    session: RadioSessionUiState,
    note: String,
    startLabel: String,
    stopLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    locationAccess: LocationAccessUi = LocationAccessUi.NOT_REQUESTED,
    onOpenPermissionSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onRecheck: () -> Unit = {},
) {
    val statusLabel = statusLabel(session.status)
    val limitSeconds = ControlScreenProjection.sessionLimitSeconds(session)
    // 3.9 fix (preserved): the merged row description carries the SAME facts the
    // sighted row shows, including the session limit.
    val statusText = stringResource(R.string.a11y_status_format, statusLabel) +
        (limitSeconds?.let { ", " + stringResource(R.string.session_limit_format, it) } ?: "")
    // A11y descriptions are composed OUTSIDE semantics{} (non-composable context):
    val startA11y = stringResource(R.string.a11y_start_button_format, startLabel, radioName, statusText)
    val stopA11y = stringResource(R.string.a11y_stop_button_format, stopLabel, radioName, statusText)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                modifier = Modifier.semantics { contentDescription = statusText },
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                limitSeconds?.let { seconds ->
                    Text(
                        text = stringResource(R.string.session_limit_format, seconds),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            session.issueKey?.let { issue ->
                Text(
                    text = issueLabel(issue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // Phase 3.7 (preserved): at most ONE context-appropriate recovery action,
            // derived by the host-tested pure projection from CURRENT state.
            RecoverySection(
                recovery = RecoveryStatusProjection.recoveryFor(session, locationAccess),
                onOpenPermissionSettings = onOpenPermissionSettings,
                onOpenLocationSettings = onOpenLocationSettings,
                onRecheck = onRecheck,
            )
            Text(text = note, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ControlScreenProjection.startEnabled(session.status)) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = startA11y },
                    ) { Text(text = startLabel) }
                }
                if (ControlScreenProjection.stopEnabled(session.status)) {
                    OutlinedButton(
                        onClick = onStop,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = stopA11y },
                    ) { Text(text = stopLabel) }
                }
            }
        }
    }
}

@Composable
private fun RecoverySection(
    recovery: RadioRecovery?,
    onOpenPermissionSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRecheck: () -> Unit,
) {
    if (recovery == null) return
    val (labelRes, messageRes) = when (recovery.action) {
        RecoveryStatusProjection.RecoveryAction.OPEN_PERMISSION_SETTINGS ->
            R.string.action_open_permission_settings to recovery.messageRes()
        RecoveryStatusProjection.RecoveryAction.OPEN_LOCATION_SETTINGS ->
            R.string.action_open_location_settings to recovery.messageRes()
        RecoveryStatusProjection.RecoveryAction.RECHECK ->
            R.string.action_recheck to recovery.messageRes()
    }
    val label = stringResource(labelRes)
    val a11y = stringResource(R.string.a11y_recovery_action_format, label, radioWord2(recovery.radio))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = when (recovery.action) {
                RecoveryStatusProjection.RecoveryAction.OPEN_PERMISSION_SETTINGS -> onOpenPermissionSettings
                RecoveryStatusProjection.RecoveryAction.OPEN_LOCATION_SETTINGS -> onOpenLocationSettings
                RecoveryStatusProjection.RecoveryAction.RECHECK -> onRecheck
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = a11y },
        ) { Text(label) }
    }
}

@Composable
private fun RadioRecovery.messageRes(): Int = when (messageKey) {
    RecoveryStatusProjection.RecoveryMessageKey.PERMISSION_NEEDS_SETTING -> R.string.recovery_permission_needs_setting
    RecoveryStatusProjection.RecoveryMessageKey.APPROXIMATE_ONLY_NEEDS_SETTING -> R.string.recovery_approximate_only_needs_setting
    RecoveryStatusProjection.RecoveryMessageKey.LOCATION_SERVICES_NEED_SETTING -> R.string.recovery_location_services_need_setting
    RecoveryStatusProjection.RecoveryMessageKey.RADIO_OFF_RECHECK -> R.string.recovery_radio_off_recheck
    RecoveryStatusProjection.RecoveryMessageKey.SYSTEM_LIMIT_RECHECK -> R.string.recovery_system_limit_recheck
}

@Composable
private fun radioWord2(radio: com.example.radioobserver.core.radio.model.RadioType): String = stringResource(
    when (radio) {
        com.example.radioobserver.core.radio.model.RadioType.BLE -> R.string.source_ble
        com.example.radioobserver.core.radio.model.RadioType.WIFI -> R.string.source_wifi
    },
)

@Composable
private fun statusLabel(status: RadioSessionUiStatus): String = stringResource(
    when (status) {
        RadioSessionUiStatus.IDLE -> R.string.status_idle
        RadioSessionUiStatus.CHECKING -> R.string.status_checking
        RadioSessionUiStatus.PERMISSION_REQUIRED -> R.string.status_permission_required
        RadioSessionUiStatus.BLOCKED -> R.string.status_blocked
        RadioSessionUiStatus.OBSERVING -> R.string.status_observing
        RadioSessionUiStatus.STOPPING -> R.string.status_stopping
        RadioSessionUiStatus.STOPPED -> R.string.status_stopped
        RadioSessionUiStatus.TIMED_OUT -> R.string.status_timed_out
        RadioSessionUiStatus.ERROR -> R.string.status_error
    },
)

@Composable
private fun issueLabel(issue: SessionIssueKey): String = stringResource(
    when (issue) {
        SessionIssueKey.BLUETOOTH_OFF -> R.string.issue_bluetooth_off
        SessionIssueKey.WIFI_OFF -> R.string.issue_wifi_off
        SessionIssueKey.LOCATION_SERVICES_OFF -> R.string.issue_location_services_off
        SessionIssueKey.PERMISSION_DENIED -> R.string.issue_permission_denied
        SessionIssueKey.PERMISSION_APPROXIMATE_ONLY -> R.string.issue_approximate_only
        SessionIssueKey.SECURITY_EXCEPTION -> R.string.issue_security_exception
        SessionIssueKey.SCAN_THROTTLED -> R.string.issue_scan_throttled
        SessionIssueKey.HARDWARE_UNSUPPORTED -> R.string.issue_hardware_unsupported
        SessionIssueKey.FOREGROUND_LOST -> R.string.issue_foreground_lost
        SessionIssueKey.NOT_ELIGIBLE -> R.string.issue_not_eligible
        SessionIssueKey.INTERNAL_ERROR -> R.string.issue_internal_error
    },
)
