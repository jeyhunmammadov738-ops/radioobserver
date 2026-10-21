package com.example.radioobserver.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.radioobserver.core.radio.ui.RuntimeGrantSnapshot

/**
 * Phase 3.3 — thin Android permission READ gateway. Converts real platform grant facts
 * into the pure-JVM [RuntimeGrantSnapshot]. No dialogs, no side effects, no scanning.
 * BLUETOOTH_SCAN is install-time-true below API 31 (legacy permissions live in the
 * manifest with maxSdkVersion=30 and cannot be runtime-requested there).
 */
class AndroidPermissionGateway(private val context: Context) {

    fun snapshot(): RuntimeGrantSnapshot = RuntimeGrantSnapshot(
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocation = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        bluetoothScan = Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_SCAN),
    )

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
