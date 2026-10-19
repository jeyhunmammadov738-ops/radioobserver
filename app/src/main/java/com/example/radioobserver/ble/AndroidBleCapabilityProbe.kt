package com.example.radioobserver.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.radioobserver.core.radio.permissions.AndroidApiBucket
import com.example.radioobserver.core.radio.permissions.CapabilitySnapshot
import com.example.radioobserver.core.radio.permissions.PermissionId
import com.example.radioobserver.core.radio.permissions.PermissionState
import com.example.radioobserver.core.radio.model.RadioType

/**
 * REAL permission/capability checks at the Android boundary (task 2.3). The pure
 * ObservationReadinessModel only decides; this probe observes actual device state via
 * ContextCompat.checkSelfPermission, the BluetoothAdapter, and the Location-mode setting.
 *
 * A pure-model "ready" result is never treated as proof of a runtime grant: this probe
 * always re-reads real state before a snapshot is evaluated.
 *
 * "Permanently denied" detection (shouldShowRequestPermissionRationale) requires an
 * Activity and heuristic interpretation; it is injected by the caller and defaults to
 * false — the app reports "denied" rather than guessing "permanently denied".
 *
 * Location services check uses Settings.Secure.LOCATION_MODE (deprecated in 28 but
 * functional across the whole supported range, unlike LocationManager.isLocationEnabled
 * which exists only on newer APIs).
 */
class AndroidBleCapabilityProbe(
    private val appContext: Context,
    private val adapterProvider: () -> BluetoothAdapter?,
    private val permanentDenialProvider: (PermissionId) -> Boolean = { false },
) {

    @Suppress("DEPRECATION")
    fun currentSnapshot(): CapabilitySnapshot {
        val bucket = AndroidApiBucket.fromSdkLevel(android.os.Build.VERSION.SDK_INT)
        val adapter = adapterProvider()
        val hardwarePresent = adapter != null
        val radioEnabled = adapter?.isEnabled == true
        val locationServicesEnabled = isLocationModeEnabled()

        val permissions = mutableMapOf<PermissionId, PermissionState>()
        fun stateOf(permission: String): PermissionState =
            if (ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }

        when (bucket) {
            AndroidApiBucket.API_29_30 -> {
                permissions[PermissionId.BLUETOOTH] = stateOf(Manifest.permission.BLUETOOTH)
                permissions[PermissionId.BLUETOOTH_ADMIN] = stateOf(Manifest.permission.BLUETOOTH_ADMIN)
                permissions[PermissionId.ACCESS_FINE_LOCATION] = stateOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            AndroidApiBucket.API_31_32, AndroidApiBucket.API_33_PLUS -> {
                permissions[PermissionId.BLUETOOTH_SCAN] = stateOf(Manifest.permission.BLUETOOTH_SCAN)
                permissions[PermissionId.ACCESS_FINE_LOCATION] = stateOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        // Only runtime-permission rows are stored; "permanently denied" is caller-injected.
        permissions.replaceAll { id, state ->
            if (state == PermissionState.DENIED && permanentDenialProvider(id)) {
                PermissionState.PERMANENTLY_DENIED
            } else {
                state
            }
        }

        return CapabilitySnapshot(
            apiBucket = bucket,
            radio = RadioType.BLE,
            hardware = com.example.radioobserver.core.radio.permissions.RadioHardwareStatus(
                hardwarePresent = hardwarePresent,
                radioEnabled = radioEnabled,
                locationServicesEnabled = locationServicesEnabled,
            ),
            permissions = permissions,
        )
    }

    @Suppress("DEPRECATION")
    private fun isLocationModeEnabled(): Boolean =
        try {
            Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.LOCATION_MODE,
            ) != Settings.Secure.LOCATION_MODE_OFF
        } catch (unexpected: Settings.SettingNotFoundException) {
            false // cannot verify: conservatively treat Location services as off
        }
}
