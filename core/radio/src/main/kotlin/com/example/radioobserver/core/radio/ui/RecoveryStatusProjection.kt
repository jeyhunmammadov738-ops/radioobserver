package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.RadioType

/**
 * Phase 3.7 — pure Kotlin/JVM recovery/status view projection (zero android.*
 * imports, no side effects, no competing state machine). Derives, per radio and ONLY
 * from the authoritative sanitized session state, ONE context-appropriate recovery
 * action — or none. It never infers platform conditions, never claims a granted
 * permission or enabled radio, and there is deliberately NO "permanently denied"
 * concept anywhere (rationale=false alone proves nothing; see LocationAccessUi).
 */
object RecoveryStatusProjection {

    /** Recovery actions actually supported by existing controllers. */
    enum class RecoveryAction {
        /** Open app permission Settings (guarded, user-invoked, no success claim). */
        OPEN_PERMISSION_SETTINGS,
        /** Open system Location Settings (guarded, user-invoked, no success claim). */
        OPEN_LOCATION_SETTINGS,
        /** Re-read CURRENT permission/capability facts; never prompts, never starts. */
        RECHECK,
    }

    /**
     * Per-radio recovery view model. Null = no recovery action applies (live session,
     * normal idle, or a state where no supported action exists — honest inaction).
     */
    data class RadioRecovery(
        val radio: RadioType,
        val action: RecoveryAction,
        /** Semantic message key resolved to Turkish copy by the :app layer. */
        val messageKey: RecoveryMessageKey,
    )

    /** Semantic explanation keys; Turkish text lives in :app string resources only. */
    enum class RecoveryMessageKey {
        PERMISSION_NEEDS_SETTING,
        APPROXIMATE_ONLY_NEEDS_SETTING,
        LOCATION_SERVICES_NEED_SETTING,
        RADIO_OFF_RECHECK,
        SYSTEM_LIMIT_RECHECK,
    }

    /**
     * Maps the CURRENT sanitized session state to at most one recovery action.
     * Live sessions (OBSERVING/STOPPING) never get a recovery banner — the Stop
     * control is the truth. States with no supported platform action (hardware
     * unsupported, timeout, foreground loss, completed, unknown error) honestly map
     * to null: the issue text plus Start/Stop controls already tell the truth.
     */
    fun recoveryFor(
        session: RadioSessionUiState,
        locationAccess: LocationAccessUi,
    ): RadioRecovery? {
        val radio = session.radio
        return when (session.status) {
            RadioSessionUiStatus.OBSERVING, RadioSessionUiStatus.STOPPING -> null
            RadioSessionUiStatus.PERMISSION_REQUIRED, RadioSessionUiStatus.BLOCKED -> when (session.issueKey) {
                SessionIssueKey.LOCATION_SERVICES_OFF -> RadioRecovery(
                    radio, RecoveryAction.OPEN_LOCATION_SETTINGS,
                    RecoveryMessageKey.LOCATION_SERVICES_NEED_SETTING)
                SessionIssueKey.PERMISSION_APPROXIMATE_ONLY -> RadioRecovery(
                    radio, RecoveryAction.OPEN_PERMISSION_SETTINGS,
                    RecoveryMessageKey.APPROXIMATE_ONLY_NEEDS_SETTING)
                SessionIssueKey.PERMISSION_DENIED -> RadioRecovery(
                    radio, RecoveryAction.OPEN_PERMISSION_SETTINGS,
                    RecoveryMessageKey.PERMISSION_NEEDS_SETTING)
                SessionIssueKey.BLUETOOTH_OFF, SessionIssueKey.WIFI_OFF -> RadioRecovery(
                    radio, RecoveryAction.RECHECK, RecoveryMessageKey.RADIO_OFF_RECHECK)
                SessionIssueKey.SCAN_THROTTLED, SessionIssueKey.SECURITY_EXCEPTION -> RadioRecovery(
                    radio, RecoveryAction.RECHECK, RecoveryMessageKey.SYSTEM_LIMIT_RECHECK)
                // Unsupported hardware has no supported action; never fabricate one:
                SessionIssueKey.HARDWARE_UNSUPPORTED, null -> null
                // Terminal/foreground issues are not readiness problems to "fix":
                SessionIssueKey.FOREGROUND_LOST, SessionIssueKey.INTERNAL_ERROR -> null
            // Field fix (2026-10-23): transient tap-time ineligibility is not a
            // readiness problem; the typed issue text itself is the honest retry hint.
            // Unreachable here in practice (published with IDLE status), kept explicit
            // so the mapping stays total and non-fabricating.
            SessionIssueKey.NOT_ELIGIBLE -> null
            }
            // Idle/checking/finished states: controls already truthful; never request
            // anything uninvited (NOT_REQUESTED permission is handled by the Start tap
            // through the 3.3 two-tap flow — a recovery banner here would invite
            // implicit prompting, which is forbidden).
            RadioSessionUiStatus.IDLE, RadioSessionUiStatus.CHECKING,
            RadioSessionUiStatus.STOPPED, RadioSessionUiStatus.TIMED_OUT,
            RadioSessionUiStatus.ERROR -> null
        }
    }
}
