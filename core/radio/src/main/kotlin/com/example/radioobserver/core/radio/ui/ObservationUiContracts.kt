package com.example.radioobserver.core.radio.ui

import com.example.radioobserver.core.radio.model.ObservationConfidence
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket

/**
 * Phase 3 task 3.2 — UI state contracts and presentation mapping (PURE JVM).
 *
 * DATA CONTRACTS ONLY: nothing in this package touches Android APIs, requests runtime
 * permissions, starts/cancels sessions, registers receivers, or performs IO. The wiring
 * to real sources, permission launchers and Compose belongs to tasks 3.3+.
 *
 * Privacy boundary: the ONLY accepted data source for observation presentation is the
 * sanitized [com.example.radioobserver.core.radio.pipeline.PublicObservation] projection
 * (task 2.7). By construction no UI model here can carry a raw BLE address, Wi-Fi BSSID,
 * advertisement payload, parse-issue text, or a stable cross-session identifier. Row
 * identity is the pipeline's session-local ordinal, never derived from addresses.
 *
 * Copy policy: statuses and messages are TYPED KEYS ([UiCopyKey], [SessionIssueKey]);
 * the Turkish wording lives in docs/phase3-ux-spec.md and (in task 3.5) Android string
 * resources — never hardcoded in these pure models.
 *
 * Product decisions (developer-approved 2026-10-20): granting permissions NEVER starts
 * observation; a NEW explicit Start tap is required. Approximate-only location does not
 * satisfy FINE-required scanning. Public observations stay masked with no reveal control.
 */

/**
 * Session lifecycle status for one radio. Kept PER RADIO: BLE and Wi-Fi states are
 * structurally independent and never mutate together implicitly.
 */
enum class RadioSessionUiStatus {
    /** Default inert state; nothing has been requested. */
    IDLE,
    /** Readiness/permission evaluation in progress; no session yet. */
    CHECKING,
    /** A required permission (or precise location) is missing; user action needed. */
    PERMISSION_REQUIRED,
    /** Observation cannot run (hardware/services/permissions unavailable); NOT an error. */
    BLOCKED,
    /** A bounded observation session is active in the foreground. */
    OBSERVING,
    /** A stop is in progress; late callbacks must not revive the session. */
    STOPPING,
    /** Session ended by an explicit user Stop (or foreground loss). */
    STOPPED,
    /** Session ended because the configured bounded duration elapsed. */
    TIMED_OUT,
    /** Unexpected failure (e.g. SecurityException, internal error); retry is not automatic. */
    ERROR,
}

/**
 * Typed, conservative reason keys for BLOCKED/ERROR/STOPPED states. No free text here:
 * the UI layer resolves these keys to Turkish copy from the UX spec.
 */
enum class SessionIssueKey {
    BLUETOOTH_OFF,
    WIFI_OFF,
    LOCATION_SERVICES_OFF,
    PERMISSION_DENIED,
    /** User granted approximate-only location; FINE-required scanning stays unavailable. */
    PERMISSION_APPROXIMATE_ONLY,
    SECURITY_EXCEPTION,
    SCAN_THROTTLED,
    HARDWARE_UNSUPPORTED,
    /** Host activity lost RESUMED; both radios' sessions terminate (never auto-resume). */
    FOREGROUND_LOST,
    /**
     * Phase 3.10 field fix (2026-10-23): a START attempt was rejected because the app
     * was not in the foreground-eligible (RESUMED) state at tap time. This is TRANSIENT
     * tap-time feedback, never a session stop and never permanent unavailability:
     * nothing was observing, so nothing can truthfully be "stopped". It must be shown
     * with the truthful non-active status (e.g. IDLE) and an actionable retry hint.
     */
    NOT_ELIGIBLE,
    INTERNAL_ERROR,
}

/**
 * User location access as the UI may present it. Approximate-only is deliberately NOT
 * treated as precise: [isPreciseEnoughForScanning] is true only for [PRECISE], because
 * BLE scan results and Wi-Fi getScanResults() require FINE on all supported levels.
 */
enum class LocationAccessUi {
    NOT_REQUESTED,
    /** Runtime request happened and location was NOT granted (denial strength unknown). */
    DENIED,
    APPROXIMATE_ONLY,
    PRECISE;

    val isPreciseEnoughForScanning: Boolean get() = this == PRECISE
}

/**
 * Inert, typed user intents. They are DATA ONLY: carrying an intent must never start a
 * scan, request a permission, or mutate state by itself. Actual handling (with fresh
 * readiness and foreground checks) is wired in task 3.4.
 */
sealed interface ObservationUserIntent {
    data object StartBleObservation : ObservationUserIntent
    data object StopBleObservation : ObservationUserIntent
    data object StartWifiObservation : ObservationUserIntent
    data object StopWifiObservation : ObservationUserIntent
}

/** Typed copy keys for static UX text; Turkish wording is tabulated in the UX spec. */
enum class UiCopyKey {
    HOME_INTRO,
    BLE_START_BUTTON,
    BLE_STOP_BUTTON,
    WIFI_START_BUTTON,
    WIFI_STOP_BUTTON,
    WIFI_PASSIVE_SOURCE_NOTE,
    PRECISE_LOCATION_RATIONALE,
    APPROXIMATE_ONLY_HINT,
    EMPTY_FEED_HONEST_NOTE,
    CONFIDENCE_CAUTION,
    SIGNAL_QUALITATIVE_NOTE,
    BACKGROUND_STOP_NOTE,
    PRIVACY_ABOUT_BODY,
}

/** UI state of ONE radio's session. Issues are typed; [remainingMs] is display-only. */
data class RadioSessionUiState(
    val radio: RadioType,
    val status: RadioSessionUiStatus = RadioSessionUiStatus.IDLE,
    val issueKey: SessionIssueKey? = null,
    val remainingMs: Long? = null,
) {
    init {
        require(radio == RadioType.BLE || radio == RadioType.WIFI) { "radio must be BLE or WIFI" }
        require(remainingMs == null || remainingMs >= 0) { "remainingMs must not be negative" }
    }

    companion object {
        fun idle(radio: RadioType) = RadioSessionUiState(radio)
    }
}

/**
 * Sanitized observation row for list/detail presentation. Every field originates from
 * [com.example.radioobserver.core.radio.pipeline.PublicObservation]; there is no field
 * that could carry a raw address, BSSID, payload, or parse-issue text. [rowId] is the
 * session-local ordinal passed through from the pipeline (never address/hash derived)
 * and is stable only within the current session.
 */
data class ObservationRowUi(
    val rowId: String,
    val radio: RadioType,
    val name: String?,
    val manufacturerHint: String?,
    val typeHint: String?,
    val confidence: ObservationConfidence,
    val signalBucket: SignalBucket,
    val rssiDbm: Int?,
    val locallyAdministered: Boolean,
    val freshness: FreshnessState,
    val ageMs: Long?,
    val lastSeenAtEpochMs: Long,
    val parseIssueCount: Int,
)

/**
 * Whole-screen presentation state. BLE and Wi-Fi session states are SEPARATE immutable
 * values: updating one can never silently change the other. [locationAccess] is the
 * current location grant as known to the UI; a grant transition alone never changes any
 * session status (developer decision: second Start tap required).
 */
data class ObservationUiState(
    val bleSession: RadioSessionUiState = RadioSessionUiState.idle(RadioType.BLE),
    val wifiSession: RadioSessionUiState = RadioSessionUiState.idle(RadioType.WIFI),
    val locationAccess: LocationAccessUi = LocationAccessUi.NOT_REQUESTED,
    val rows: List<ObservationRowUi> = emptyList(),
) {
    companion object {
        /** Inert initial state: nothing observing, nothing requested, empty feed. */
        fun initial() = ObservationUiState()
    }
}
