package com.example.radioobserver.core.radio.boundary

import com.example.radioobserver.core.radio.model.ObservedRadioDevice
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.PermissionId

/**
 * Phase 2 task 2.2 — device-free observation boundary contracts.
 *
 * Pure Kotlin/JVM interfaces only: NO `android.*` imports may appear in this package.
 * The Android implementations (BLE scanner adapter, Wi-Fi broadcast observer) arrive in
 * later, separately authorized tasks (2.3/2.4) and must adapt to these contracts.
 *
 * Binding semantics:
 * - Every observation session is user-initiated, bounded by [ObservationSessionSpec],
 *   and cancellable. There is deliberately NO API to trigger a Wi-Fi scan and no pairing,
 *   connecting, advertising, or GATT surface anywhere in these contracts.
 * - Wi-Fi observation is passive: results arrive from platform/other-app scans and are
 *   always potentially cached/stale (see [ObservationEvent.WifiResultsAvailable]).
 */

/**
 * Bounded session parameters. A hard ceiling exists so no future caller can request an
 * unbounded session by accident; background/always-on observation is out of scope by design.
 */
data class ObservationSessionSpec(
    /** Maximum session duration in milliseconds; must be positive and <= [MAX_SESSION_DURATION_MS]. */
    val maxDurationMs: Long,
) {
    init {
        require(maxDurationMs > 0) { "maxDurationMs must be positive: $maxDurationMs" }
        require(maxDurationMs <= MAX_SESSION_DURATION_MS) {
            "maxDurationMs $maxDurationMs exceeds the bounded-session ceiling $MAX_SESSION_DURATION_MS"
        }
    }

    companion object {
        /** Hard ceiling: 10 minutes. Enforced to keep observation time-bounded and battery-honest. */
        const val MAX_SESSION_DURATION_MS: Long = 10 * 60 * 1000

        /** Default bounded window: 2 minutes, user-initiated. */
        fun default(): ObservationSessionSpec = ObservationSessionSpec(2 * 60 * 1000)
    }
}

/** Immutable session identifier handed out by a source when a session starts. */
data class ObservationSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "session id must not be blank" }
    }
}

/**
 * Why a session ended. No value means "still active". Scanning is foreground-eligible
 * only by design; there is no background-safe session end reason.
 */
enum class SessionEndReason {
    /** Source stopped cleanly (e.g. adapter stopped, spec reached through normal flow). */
    COMPLETED_NORMALLY,
    /** User (or app on the user's behalf) cancelled via [ObservationHandle.cancel]. */
    CANCELLED_BY_USER,
    /** Bounded duration elapsed; session auto-ended. */
    DURATION_ELAPSED,
    /** Source failed after start; see the carried [ObservationError]. */
    SOURCE_ERROR,
    /** The caller reported loss of foreground eligibility (task 2.3 gate; never background-safe). */
    FOREGROUND_LOST,
}

/** Typed, conservative errors a source can report. No error invents device facts. */
sealed interface ObservationError {
    val radio: RadioType

    /** A required permission is missing. [permanent] = "permanently denied" (route to settings). */
    data class PermissionDenied(
        val permission: PermissionId,
        val permanent: Boolean,
        override val radio: RadioType,
    ) : ObservationError

    /** The device radio (Bluetooth/Wi-Fi) is switched off. */
    data class RadioDisabled(override val radio: RadioType) : ObservationError

    /** No hardware for this radio on this device. */
    data class HardwareUnavailable(override val radio: RadioType) : ObservationError

    /** System Location services are off; conservative gate for scan results (see matrix 2.1). */
    data class LocationServicesDisabled(override val radio: RadioType) : ObservationError

    /** Only one session per source may be active at a time. */
    data class SessionAlreadyActive(override val radio: RadioType) : ObservationError

    /**
     * Caller-supplied foreground eligibility is false. Scanning never starts in this state;
     * there is no background scan path by design (task 2.3 gate).
     */
    data class ForegroundIneligible(override val radio: RadioType) : ObservationError

    /**
     * The platform scan failed to start or failed while running. [platformCode] is the
     * raw platform error code when one exists (kept verbatim, never reinterpreted);
     * [reason] is the conservative typed mapping. UNKNOWN stays UNKNOWN — never success.
     */
    data class ScanStartFailed(
        val reason: ScanFailureReason,
        val platformCode: Int?,
        override val radio: RadioType,
    ) : ObservationError

    /** The platform scanner could not be stopped cleanly; cleanup failure is surfaced, not hidden. */
    data class ScanStopFailed(override val radio: RadioType) : ObservationError
}

/** All events a session listener can receive. Events are ordered per session. */
sealed interface ObservationEvent {
    data class SessionStarted(val sessionId: ObservationSessionId) : ObservationEvent
    data class DeviceObserved(val device: ObservedRadioDevice) : ObservationEvent

    /**
     * Wi-Fi scan-result delivery. [updatedSinceLastDelivery] mirrors the platform's
     * "results updated" signal; [resultsAgeMs] is a best-effort age marker of the cached
     * result set (null = unknown). Results may be stale by design.
     */
    data class WifiResultsAvailable(
        val observations: List<ObservedRadioDevice>,
        val updatedSinceLastDelivery: Boolean,
        val resultsAgeMs: Long?,
    ) : ObservationEvent

    data class SourceFailed(val error: ObservationError) : ObservationEvent
    data class SessionEnded(val sessionId: ObservationSessionId, val reason: SessionEndReason) :
        ObservationEvent
}

/** Callback sink for a session's events. */
fun interface ObservationSessionListener {
    fun onObservationEvent(event: ObservationEvent)
}

/** Handle to an active session. Cancel is the ONLY user-facing control. */
interface ObservationHandle {
    val sessionId: ObservationSessionId
    val isActive: Boolean

    /** @return true if this call ended the session; false if it was already inactive. */
    fun cancel(): Boolean
}

/** Result of asking a source to start. Refusal carries the typed blocker — never a crash. */
sealed interface SourceStartResult {
    data class Started(val handle: ObservationHandle) : SourceStartResult
    data class Refused(val error: ObservationError) : SourceStartResult
}

/** Common marker for both observation sources. No scan-trigger API exists on purpose. */
interface RadioObservationSource {
    val radio: RadioType
}

/**
 * BLE observation source contract: bounded, user-initiated session; the adapter (task 2.3)
 * maps `BluetoothLeScanner` callbacks onto [ObservationEvent]s. Never pairs/connects.
 */
interface BleObservationSource : RadioObservationSource {
    fun startSession(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult
}

/**
 * Wi-Fi system scan-result observer contract: passive listener for platform/other-app scan
 * completions (task 2.4 adapter). The implementation must NEVER call
 * `WifiManager.startScan()`; there is no method for it here by design.
 */
interface WifiObservationSource : RadioObservationSource {
    fun startObservation(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult
}

/**
 * Conservative mapping of platform scan-failure codes (BluetoothLeScanner constants).
 * Anything unrecognized maps to UNKNOWN and stays a failure — never silently "success".
 */
enum class ScanFailureReason {
    ALREADY_STARTED,
    REGISTRATION_FAILED,
    INTERNAL_ERROR,
    FEATURE_UNSUPPORTED,
    OUT_OF_RESOURCES,
    TOO_FREQUENT,
    /** Thrown by the platform when a required permission is actually missing at runtime. */
    SECURITY_EXCEPTION,
    /** No BluetoothLeScanner could be obtained (adapter null or Bluetooth off). */
    NO_SCANNER,
    UNKNOWN;

    companion object {
        fun fromPlatformCode(code: Int): ScanFailureReason = when (code) {
            1 -> ALREADY_STARTED
            2 -> REGISTRATION_FAILED
            3 -> INTERNAL_ERROR
            4 -> FEATURE_UNSUPPORTED
            5 -> OUT_OF_RESOURCES
            6 -> TOO_FREQUENT
            else -> UNKNOWN
        }
    }
}
