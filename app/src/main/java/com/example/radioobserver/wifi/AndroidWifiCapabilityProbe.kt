package com.example.radioobserver.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.AndroidApiBucket
import com.example.radioobserver.core.radio.permissions.CapabilitySnapshot
import com.example.radioobserver.core.radio.permissions.PermissionId
import com.example.radioobserver.core.radio.permissions.PermissionState
import com.example.radioobserver.core.radio.permissions.RadioHardwareStatus

/**
 * REAL Wi-Fi permission/capability checks at the Android boundary (task 2.4). Reads actual
 * grant state (ACCESS_WIFI_STATE, ACCESS_FINE_LOCATION), Wi-Fi manager presence and enabled
 * state (read-only), and the system Location mode. NEARBY_WIFI_DEVICES is deliberately NOT
 * consulted: per the official docs getScanResults() requires ACCESS_FINE_LOCATION even when
 * targeting 13+. "Permanently denied" is caller-injected (default false) — the app reports
 * "denied" instead of guessing. A model "ready" result never substitutes for these checks.
 */
class AndroidWifiCapabilityProbe(
    private val appContext: Context,
    private val wifiManagerProvider: () -> WifiManager?,
    private val permanentDenialProvider: (PermissionId) -> Boolean = { false },
) {

    @Suppress("DEPRECATION")
    fun currentSnapshot(): CapabilitySnapshot {
        val bucket = AndroidApiBucket.fromSdkLevel(android.os.Build.VERSION.SDK_INT)
        val manager = wifiManagerProvider()
        val hardwarePresent = manager != null
        val radioEnabled = manager?.isWifiEnabled == true // read-only state check
        val locationServicesEnabled = isLocationModeEnabled()

        fun stateOf(permission: String): PermissionState =
            if (ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED) {
                PermissionState.GRANTED
            } else {
                PermissionState.DENIED
            }

        val permissions = mutableMapOf(
            PermissionId.ACCESS_WIFI_STATE to stateOf(Manifest.permission.ACCESS_WIFI_STATE),
            PermissionId.ACCESS_FINE_LOCATION to stateOf(Manifest.permission.ACCESS_FINE_LOCATION),
        )
        permissions.replaceAll { id, state ->
            if (state == PermissionState.DENIED && permanentDenialProvider(id)) {
                PermissionState.PERMANENTLY_DENIED
            } else {
                state
            }
        }

        return CapabilitySnapshot(
            apiBucket = bucket,
            radio = RadioType.WIFI,
            hardware = RadioHardwareStatus(
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
        } catch (notFound: Settings.SettingNotFoundException) {
            false // cannot verify: conservatively treated as off
        }
}
