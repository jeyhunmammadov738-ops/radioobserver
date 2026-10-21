package com.example.radioobserver.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
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
 * Phase 3.5 — Material 3 OBSERVATION CONTROL screen (Turkish).
 *
 * A STATELESS renderer: all state arrives as the immutable sanitized [ObservationUiState]
 * (single source of truth: the 3.4 orchestrator); all actions leave ONLY through the
 * four explicit user callbacks (real button clicks in MainActivity wire them to the
 * 3.4 [com.example.radioobserver.session.ObservationSessionPresenter]). No session is
 * ever started from composition, recomposition, initialization effects or state
 * collection; enable/disable rules come from the host-tested pure
 * [ControlScreenProjection].
 *
 * Compile-gated only: real click delivery, TalkBack semantics, layout, font scaling and
 * dark mode on a device are UNVERIFIED in this device-free iteration.
 */
@Composable
fun RadioObserverControlScreen(
    state: ObservationUiState,
    onStartBle: () -> Unit,
    onStopBle: () -> Unit,
    onStartWifi: () -> Unit,
    onStopWifi: () -> Unit,
    onOpenObservations: () -> Unit = {},
    onOpenPermissionSettings: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onRecheckBle: () -> Unit = {},
    onRecheckWifi: () -> Unit = {},
) {
    val openFeedA11y = stringResource(R.string.a11y_open_feed)
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.control_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.home_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.scope_note),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodySmall,
            )

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
            if (ControlScreenProjection.showEmptyFeedNote(state)) {
                Text(
                    text = stringResource(R.string.empty_feed_note),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (state.rows.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.observation_count,
                        ControlScreenProjection.observationCount(state.rows),
                        ControlScreenProjection.observationCount(state.rows)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(R.string.background_stop_note),
                style = MaterialTheme.typography.bodySmall,
            )
            // Phase 3.6: read-only feed entry. Pure navigation: this callback changes
            // NO session, permission or scanning state — it only switches destination.
            OutlinedButton(
                onClick = onOpenObservations,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = openFeedA11y },
            ) { Text(stringResource(R.string.open_feed_button)) }
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
    val statusText = stringResource(R.string.a11y_status_format, statusLabel)
    // A11y descriptions are composed OUTSIDE semantics{} (non-composable context):
    val startA11y = stringResource(R.string.a11y_start_button_format, startLabel, radioName, statusText)
    val stopA11y = stringResource(R.string.a11y_stop_button_format, stopLabel, radioName, statusText)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.semantics { contentDescription = statusText },
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                ControlScreenProjection.sessionLimitSeconds(session)?.let { seconds ->
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
            // Phase 3.7: at most ONE context-appropriate recovery action, derived by the
            // host-tested pure projection from CURRENT state. Clicks only — recomposition
            // dispatches nothing; the buttons never claim a grant or enabled hardware.
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
        SessionIssueKey.INTERNAL_ERROR -> R.string.issue_internal_error
    },
)
