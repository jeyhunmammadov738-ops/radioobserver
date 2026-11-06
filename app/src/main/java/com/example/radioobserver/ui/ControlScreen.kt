package com.example.radioobserver.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.ui.ControlScreenProjection
import com.example.radioobserver.core.radio.ui.RecoveryStatusProjection
import com.example.radioobserver.core.radio.ui.RecoveryStatusProjection.RadioRecovery
import com.example.radioobserver.core.radio.ui.LocationAccessUi
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.core.radio.ui.RadioSessionUiState
import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.core.radio.ui.SessionIssueKey
import com.example.radioobserver.ui.components.DashboardSummary
import com.example.radioobserver.ui.components.PillStyle
import com.example.radioobserver.core.radio.alerts.AlertTrigger
import com.example.radioobserver.core.radio.alerts.SessionAlert
import com.example.radioobserver.ui.components.SectionHeader
import com.example.radioobserver.ui.components.SourceIcon
import com.example.radioobserver.ui.components.Spacing
import com.example.radioobserver.ui.components.StatusPill

/**
 * PREMIUM UI/UX V2 Gözlem dashboard (post-roadmap, developer-approved 2026-10-23).
 * Screen defects fixed vs. the previous build (developer's dark-mode screenshots):
 * two repetitive grey cards with oversized copy and weak status priority → compact
 * tonal source cards with icons, a STATUS PILL first-class hierarchy, one-line
 * captions, and a truthful at-a-glance summary strip.
 *
 * UNCHANGED FUNCTIONAL CONTRACT: a STATELESS renderer of the immutable sanitized
 * [ObservationUiState]; actions leave ONLY through explicit user-tap callbacks.
 * No session is ever started from composition, recomposition or navigation. The
 * summary uses ONLY orchestrator-provided values — no fabricated countdown/timer;
 * the known session limit is shown as a fixed bound.
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
    // WORKSTREAM 04: controlled IN-APP session alerts. These controls are PURE
    // engine state changes: they never dispatch Start/Stop, permissions, scans
    // or background anything (no notification permission exists or is needed).
    alerts: AlertsUi = AlertsUi(),
    onAlertsEnabledChange: (Boolean) -> Unit = {},
    onAlertTriggerToggled: (AlertTrigger) -> Unit = {},
    onDismissAlert: (String) -> Unit = {},
    onDismissAllAlerts: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.dashboard_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SummaryStrip(state)

            // Contextual, actionable permission guidance (unchanged honest copy).
            if (state.locationAccess == LocationAccessUi.NOT_REQUESTED ||
                state.locationAccess == LocationAccessUi.DENIED
            ) {
                Text(
                    text = stringResource(R.string.precise_location_rationale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.locationAccess == LocationAccessUi.APPROXIMATE_ONLY) {
                Text(
                    text = stringResource(R.string.approximate_only_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            RadioControlCard(
                radio = RadioType.BLE,
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
                radio = RadioType.WIFI,
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

            // WORKSTREAM 04: in-app session alerts panel. IN-APP ONLY (no system
            // notification, no service, no sound/vibration); disabled by default.
            SessionAlertsPanel(
                alerts = alerts,
                onAlertsEnabledChange = onAlertsEnabledChange,
                onAlertTriggerToggled = onAlertTriggerToggled,
                onDismissAlert = onDismissAlert,
                onDismissAllAlerts = onDismissAllAlerts,
            )

            InfoDisclosure()
        }
    }
}

/**
 * Truthful at-a-glance strip: active source count + live record count (or the honest
 * none-active note). Pure [DashboardSummary] projection over orchestrator state only.
 */
@Composable
private fun SummaryStrip(state: ObservationUiState) {
    // UX-03: compact session status header — REAL status pills for each supported
    // source (text + tone, never colour alone) and the live record count. No padding
    // gymnastics, no nested card, no fabricated combined-session state.
    val active = DashboardSummary.activeSourceCount(
        state.bleSession.status, state.wifiSession.status)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    text = stringResource(R.string.source_ble) + " · " +
                        statusLabel(state.bleSession.status),
                    kind = PillStyle.kindFor(state.bleSession.status),
                )
                StatusPill(
                    text = stringResource(R.string.source_wifi) + " · " +
                        statusLabel(state.wifiSession.status),
                    kind = PillStyle.kindFor(state.wifiSession.status),
                )
            }
            Text(
                text = if (DashboardSummary.anyActive(
                        state.bleSession.status, state.wifiSession.status)) {
                    stringResource(
                        R.string.summary_active_sources,
                        active,
                    ) + " · " + stringResource(
                        R.string.summary_live_records,
                        ControlScreenProjection.observationCount(state.rows),
                    )
                } else {
                    stringResource(R.string.summary_none_active)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One refined source card: icon + title + STATUS PILL header row, honest issue text,
 * at most one recovery action, a one-line caption and the prominent Start/Stop pair.
 * The session limit is a fixed bound (20s BLE / 30s Wi-Fi) — never a fake countdown.
 */
@Composable
private fun RadioControlCard(
    radio: RadioType,
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
    val statusText = stringResource(R.string.a11y_status_format, statusLabel) +
        (limitSeconds?.let { ", " + stringResource(R.string.session_limit_format, it) } ?: "")
    val startA11y = stringResource(R.string.a11y_start_button_format, startLabel, radioName, statusText)
    val stopA11y = stringResource(R.string.a11y_stop_button_format, stopLabel, radioName, statusText)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Header row: icon + title + status pill — status gets FIRST-CLASS priority.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                SourceIcon(radio = radio)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                StatusPill(text = statusLabel, kind = PillStyle.kindFor(session.status))
            }
            limitSeconds?.let { seconds ->
                Text(
                    text = stringResource(R.string.session_limit_format, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // FINAL POLISH responsiveness: FlowRow keeps the two primary actions
            // side-by-side on normal widths but REFLOWS them to full lines at
            // ~320dp / 2.0x font scale — a label is never clipped or squashed.
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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

/**
 * Collapsed-by-default accessible disclosure: the long educational and privacy copy
 * stays available and truthful — without burying the Start controls below the fold.
 */
@Composable
private fun InfoDisclosure() {
    var expanded by remember { mutableStateOf(false) }
    val expandA11y = stringResource(R.string.info_section_expand_a11y)
    val collapseA11y = stringResource(R.string.info_section_collapse_a11y)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    text = stringResource(R.string.info_section_title),
                    modifier = Modifier.weight(1f),
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.scope_note), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.background_stop_note), style = MaterialTheme.typography.bodySmall)
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
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun radioWord2(radio: RadioType): String = stringResource(
    when (radio) {
        RadioType.BLE -> R.string.source_ble
        RadioType.WIFI -> R.string.source_wifi
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


/** WORKSTREAM 04: alert UI snapshot (mirrors the pure engine; disabled by default). */
data class AlertsUi(
    val enabled: Boolean = false,
    val triggers: Set<AlertTrigger> = emptySet(),
    val alerts: List<SessionAlert> = emptyList(),
    val sessionActive: Boolean = false,
)

/**
 * Compact in-app alerts card: enable switch, trigger chips, honest lifecycle
 * disclosure, current alert queue with per-alert and bulk dismissal, and a
 * clear "no active session" indication. Disabled by default; nothing here is
 * persisted, and the engine clears itself when the session ends.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionAlertsPanel(
    alerts: AlertsUi,
    onAlertsEnabledChange: (Boolean) -> Unit,
    onAlertTriggerToggled: (AlertTrigger) -> Unit,
    onDismissAlert: (String) -> Unit,
    onDismissAllAlerts: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SectionHeader(stringResource(R.string.alerts_title))
            val enableA11y = stringResource(R.string.alerts_enable_a11y)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.alerts_enable),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = alerts.enabled,
                    onCheckedChange = onAlertsEnabledChange,
                    modifier = Modifier.semantics { contentDescription = enableA11y },
                )
            }
            Text(
                text = stringResource(R.string.alerts_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.alerts_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!alerts.sessionActive) {
                Text(
                    text = stringResource(R.string.alerts_no_session),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (alerts.enabled) {
                // UX-03: the seven trigger chips live behind a disclosure so the
                // dashboard never becomes one enormous vertical stack. Collapsed by
                // default; the current selection count stays visible when collapsed.
                var triggersOpen by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { triggersOpen = !triggersOpen },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.alerts_triggers_toggle_format,
                            alerts.triggers.size,
                        ),
                    )
                }
                if (triggersOpen) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        AlertTrigger.entries.forEach { trigger ->
                            FilterChip(
                                selected = trigger in alerts.triggers,
                                onClick = { onAlertTriggerToggled(trigger) },
                                label = { Text(alertTriggerLabel(trigger)) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
                if (alerts.alerts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.alerts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    alerts.alerts.forEach { alert ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.alert_item_format,
                                    alertTriggerLabel(alert.trigger),
                                    if (alert.radio == RadioType.BLE) {
                                        stringResource(R.string.alert_radio_ble)
                                    } else {
                                        stringResource(R.string.alert_radio_wifi)
                                    },
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            val dismissA11y = stringResource(R.string.alert_item_dismiss_a11y)
                            TextButton(
                                onClick = { onDismissAlert(alert.alertId) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics { contentDescription = dismissA11y },
                            ) { Text(stringResource(R.string.alert_dismiss)) }
                        }
                    }
                    OutlinedButton(
                        onClick = onDismissAllAlerts,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.alert_dismiss_all)) }
                }
            }
        }
    }
}

@Composable
private fun alertTriggerLabel(trigger: AlertTrigger): String = when (trigger) {
    AlertTrigger.NEW_FRESH_BLE -> stringResource(R.string.alert_trigger_new_fresh_ble)
    AlertTrigger.NEW_FRESH_WIFI -> stringResource(R.string.alert_trigger_new_fresh_wifi)
    AlertTrigger.SIGNAL_IMMEDIATE -> stringResource(R.string.alert_trigger_signal_immediate)
    AlertTrigger.SIGNAL_NEAR -> stringResource(R.string.alert_trigger_signal_near)
    AlertTrigger.SIGNAL_FAR -> stringResource(R.string.alert_trigger_signal_far)
    AlertTrigger.MANUFACTURER_KNOWN -> stringResource(R.string.alert_trigger_manufacturer_known)
    AlertTrigger.MANUFACTURER_UNKNOWN -> stringResource(R.string.alert_trigger_manufacturer_unknown)
}
