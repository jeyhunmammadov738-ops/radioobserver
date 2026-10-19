package com.example.radioobserver.core.radio.lifecycle

import com.example.radioobserver.core.radio.boundary.BleObservationSource
import com.example.radioobserver.core.radio.boundary.ForegroundLossAwareSource
import com.example.radioobserver.core.radio.boundary.ObservationError
import com.example.radioobserver.core.radio.boundary.ObservationHandle
import com.example.radioobserver.core.radio.boundary.ObservationSessionListener
import com.example.radioobserver.core.radio.boundary.ObservationSessionSpec
import com.example.radioobserver.core.radio.boundary.SourceStartResult
import com.example.radioobserver.core.radio.boundary.WifiObservationSource
import com.example.radioobserver.core.radio.model.RadioType
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Deterministic foreground lifecycle policy for both radios (task 2.6): pure JVM, no
 * android.* imports, host lifecycle supplied via callbacks (:app bridge calls these).
 *
 * POLICY (decision record: docs/phase2-lifecycle-battery-policy.md):
 * - Eligibility is CONSERVATIVE: true only while the host Activity is RESUMED. A paused
 *   but partially visible window (multi-window/split-screen) does NOT qualify.
 * - Eligibility starts FALSE. Process liveness, app context, or permission grants are
 *   never substitutes; [onHostResumed] is the only thing that establishes eligibility.
 * - NO AUTO-START: becoming RESUMED never starts or resumes a session. A session requires
 *   an explicit [startBleSession]/[startWifiObservation] call (future Phase 3 UI) AND the
 *   sources' own permission/capability gates.
 * - Loss of RESUMED ([onHostPaused]) is TERMINAL for active sessions: both radios' sessions
 *   are stopped promptly (FOREGROUND_LOST). Re-entering foreground does NOT revive them.
 * - [onHostStopped]/[onHostDestroyed] repeat the same cleanup defensively and idempotently.
 * - The two sessions are independent: cancelling one never stops the other; foreground
 *   loss stops both when present. Per-source single-session semantics are unchanged
 *   (one BLE + one Wi-Fi session may coexist).
 * - Lifecycle callbacks are expected on one thread (the Android main thread); atomics are
 *   used for visibility toward other threads, not for cross-thread start races.
 * - Process death may occur without any callback: in-memory sessions cannot survive it.
 *   On a new process a fresh supervisor starts ineligible, and nothing is recreated.
 */
class RadioObservationSupervisor(
    private val bleSource: BleObservationSource,
    private val wifiSource: WifiObservationSource,
    /** Optional sink to propagate eligibility transitions into the sources' gate providers. */
    private val eligibilitySink: (Boolean) -> Unit = {},
) {
    private val resumed = AtomicBoolean(false)
    private val bleHandle = AtomicReference<ObservationHandle?>(null)
    private val wifiHandle = AtomicReference<ObservationHandle?>(null)

    val isForegroundEligible: Boolean get() = resumed.get()

    /** Host reached RESUMED: eligibility ON. Deliberately starts nothing. */
    fun onHostResumed() {
        resumed.set(true)
        eligibilitySink(true)
    }

    /** Host left RESUMED (onPause): eligibility OFF and BOTH active sessions stop. */
    fun onHostPaused() = loseForeground()

    /** Defensive repeat (onStop): idempotent cleanup, no duplicate stop/unregister. */
    fun onHostStopped() = loseForeground()

    /** Defensive repeat (onDestroy): idempotent cleanup; a new Activity gets a new supervisor. */
    fun onHostDestroyed() = loseForeground()

    private fun loseForeground() {
        resumed.set(false)
        eligibilitySink(false)
        stopAllSessions()
    }

    private fun stopAllSessions() {
        val ble = bleHandle.getAndSet(null)
        if (bleSource is ForegroundLossAwareSource) {
            bleSource.notifyForegroundLost() // ends the session with FOREGROUND_LOST
        } else {
            ble?.cancel()
        }
        val wifi = wifiHandle.getAndSet(null)
        if (wifiSource is ForegroundLossAwareSource) {
            wifiSource.notifyForegroundLost()
        } else {
            wifi?.cancel()
        }
    }

    /** Explicit user/caller start for BLE; refuses BEFORE the source when ineligible. */
    fun startBleSession(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult {
        if (!resumed.get()) {
            return SourceStartResult.Refused(ObservationError.ForegroundIneligible(RadioType.BLE))
        }
        return when (val result = bleSource.startSession(spec, listener)) {
            is SourceStartResult.Started -> {
                bleHandle.set(result.handle)
                result
            }
            is SourceStartResult.Refused -> result
        }
    }

    /** Explicit user/caller start for Wi-Fi; refuses BEFORE the source when ineligible. */
    fun startWifiObservation(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult {
        if (!resumed.get()) {
            return SourceStartResult.Refused(ObservationError.ForegroundIneligible(RadioType.WIFI))
        }
        return when (val result = wifiSource.startObservation(spec, listener)) {
            is SourceStartResult.Started -> {
                wifiHandle.set(result.handle)
                result
            }
            is SourceStartResult.Refused -> result
        }
    }

    /** Cancel ONLY the BLE session; the Wi-Fi session is untouched. Idempotent. */
    fun cancelBleSession(): Boolean = bleHandle.getAndSet(null)?.cancel() ?: false

    /** Cancel ONLY the Wi-Fi session; the BLE session is untouched. Idempotent. */
    fun cancelWifiSession(): Boolean = wifiHandle.getAndSet(null)?.cancel() ?: false
}
