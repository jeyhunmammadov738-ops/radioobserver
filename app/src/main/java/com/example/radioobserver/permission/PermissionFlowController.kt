package com.example.radioobserver.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.radioobserver.core.radio.permissions.PermissionId
import com.example.radioobserver.core.radio.ui.LocationAccessUi
import com.example.radioobserver.core.radio.ui.PermissionFlowEffect
import com.example.radioobserver.core.radio.ui.PermissionFlowEvent
import com.example.radioobserver.core.radio.ui.PermissionFlowReducer
import com.example.radioobserver.core.radio.ui.PermissionFlowScope
import com.example.radioobserver.core.radio.ui.PermissionFlowState
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 3.3 — thin, lifecycle-safe runtime permission & settings bridge.
 *
 * HONEST SCOPE (host-verified compile only; real dialog/OEM behavior UNVERIFIED):
 * - The launcher is registered UNCONDITIONALLY in [ComponentActivity.onCreate] (stable,
 *   recreation-safe); it is LAUNCHED only after an explicit user action routed through
 *   [onUserStartTap]. Nothing here ever starts BLE/Wi-Fi observation (task 3.4 wiring).
 * - After any callback the FRESH grant state is re-read via [AndroidPermissionGateway]
 *   (never trusting the callback payload alone), reduced through the pure policy, and
 *   the transient Start tap is consumed. A grant NEVER auto-starts anything: the next
 *   Start requires a NEW user tap (developer-approved two-tap rule).
 * - Settings recovery ([openAppPermissionSettings], [openLocationSettings]) is user
 *   initiated only; returning refreshes read-only readiness ([onSettingsReturned]) and
 *   never launches a dialog or an observation.
 * - No raw identifiers, no persisted callback tokens, no services/receivers/wake locks.
 */
class PermissionFlowController(
    activity: ComponentActivity,
    private val gateway: AndroidPermissionGateway,
    private val onStateChange: (PermissionFlowState) -> Unit = {},
) {
    private val state = AtomicReference(PermissionFlowState())
    private val currentActivity = AtomicReference(activity)

    /** Registered once, before the activity reaches STARTED, in a stable order. */
    private val runtimePermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Deliberately ignore the callback payload: re-read FRESH platform state instead,
        // which also handles revocation between the dialog and delivery.
        onRuntimeResult()
    }

    /**
     * Future 3.5 Compose entry point: the user explicitly tapped Start for [scope].
     * May show a runtime dialog; NEVER starts an observation, even on full grant.
     */
    fun onUserStartTap(scope: PermissionFlowScope) {
        val snapshot = gateway.snapshot()
        val (next, effect) = PermissionFlowReducer.reduce(
            state.get(),
            PermissionFlowEvent.UserStartTap(scope, snapshot),
            android.os.Build.VERSION.SDK_INT,
        )
        state.set(next)
        onStateChange(next)
        if (effect is PermissionFlowEffect.ShowRuntimeDialog) {
            launchRuntimeRequest(effect.request.permissions)
        }
    }

    private fun onRuntimeResult() {
        if (currentActivity.get() == null) return // destroyed: nothing to update
        val scope = state.get().pendingRequestScope ?: PermissionFlowScope.BLE
        val (next, _) = PermissionFlowReducer.reduce(
            state.get(),
            PermissionFlowEvent.PermissionResult(scope, gateway.snapshot()),
            android.os.Build.VERSION.SDK_INT,
        )
        state.set(next)
        onStateChange(next)
        // Intentionally NOTHING else: no scan, no queued start, no follow-up dialog.
    }

    /** User-initiated app permission Settings; availability-guarded, no success claims. */
    fun openAppPermissionSettings(): Boolean {
        val activity = currentActivity.get() ?: return false
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
        )
        if (intent.resolveActivity(activity.packageManager) == null) return false
        activity.startActivity(intent)
        return true
    }

    /** User-initiated system Location Settings (Wi-Fi observations need it enabled). */
    fun openLocationSettings(): Boolean {
        val activity = currentActivity.get() ?: return false
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        if (intent.resolveActivity(activity.packageManager) == null) return false
        activity.startActivity(intent)
        return true
    }

    /** Call when the user returns from Settings: read-only readiness refresh only. */
    fun onSettingsReturned() {
        val (next, _) = PermissionFlowReducer.reduce(
            state.get(),
            PermissionFlowEvent.SettingsReturned(gateway.snapshot()),
            android.os.Build.VERSION.SDK_INT,
        )
        state.set(next)
        onStateChange(next)
    }

    /** Called on Activity recreation so the transient Start intent is dropped (no replay). */
    fun onActivityRecreated() {
        val (next, _) = PermissionFlowReducer.reduce(
            state.get(), PermissionFlowEvent.ActivityRecreated, android.os.Build.VERSION.SDK_INT,
        )
        state.set(next)
    }

    private fun launchRuntimeRequest(permissions: List<PermissionId>) {
        val names = permissions.mapNotNull { it.manifestName() }.toTypedArray()
        if (names.isNotEmpty()) {
            runtimePermissionLauncher.launch(names)
        }
    }

    private fun PermissionId.manifestName(): String? = when (this) {
        PermissionId.ACCESS_FINE_LOCATION -> android.Manifest.permission.ACCESS_FINE_LOCATION
        PermissionId.ACCESS_COARSE_LOCATION -> android.Manifest.permission.ACCESS_COARSE_LOCATION
        PermissionId.BLUETOOTH_SCAN -> android.Manifest.permission.BLUETOOTH_SCAN
        // Install-time permissions are never runtime-requested (policy guarantees this):
        else -> null
    }
}
