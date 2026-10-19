package com.example.radioobserver.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.radioobserver.core.radio.boundary.ObservationError
import com.example.radioobserver.core.radio.boundary.ScanFailureReason
import com.example.radioobserver.core.radio.boundary.WifiEngineStartOutcome
import com.example.radioobserver.core.radio.boundary.WifiResultEngine
import com.example.radioobserver.core.radio.boundary.WifiResultEngineCallback
import com.example.radioobserver.core.radio.model.RadioType
import java.util.concurrent.Executor

/**
 * Thin platform shell (task 2.4): session-scoped, context-registered receiver for the
 * system's scan-results broadcast. RECEIVE-ONLY: this class never calls
 * WifiManager.startScan(), WifiScanner, or any connection/configuration API.
 *
 * Registration flags: RECEIVER_NOT_EXPORTED — on targetSdk 34+ an export flag is mandatory;
 * NOT_EXPORTED prevents other apps from delivering spoofed broadcasts to this receiver,
 * and SCAN_RESULTS_AVAILABLE_ACTION is a protected system broadcast that remains deliverable
 * to NOT_EXPORTED receivers. Limitation: actual on-device delivery across OEM builds is
 * UNVERIFIED (device-free protocol).
 *
 * Threading: onReceive only extracts the flag and hands the work to a single-thread
 * executor, so the main thread is never blocked and broadcast ordering is preserved.
 */
class AndroidWifiResultEngine(
    private val appContext: Context,
    private val executor: Executor = singleThreadExecutor(),
) : WifiResultEngine {

    private var started = false
    private var receiver: BroadcastReceiver? = null

    override fun start(callback: WifiResultEngineCallback): WifiEngineStartOutcome {
        if (started) return WifiEngineStartOutcome.Failed(
            ObservationError.ScanStartFailed(ScanFailureReason.ALREADY_STARTED, 1, RadioType.WIFI))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Action guard: ignore anything that is not the expected system broadcast.
                if (intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                executor.execute { callback.onScanResultsBroadcast(updated) }
            }
        }
        return try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            started = true
            this.receiver = receiver
            WifiEngineStartOutcome.Started
        } catch (security: SecurityException) {
            WifiEngineStartOutcome.Failed(
                ObservationError.ScanStartFailed(ScanFailureReason.SECURITY_EXCEPTION, null, RadioType.WIFI))
        } catch (unexpected: RuntimeException) {
            WifiEngineStartOutcome.Failed(
                ObservationError.ScanStartFailed(ScanFailureReason.REGISTRATION_FAILED, null, RadioType.WIFI))
        }
    }

    override fun stop(): Boolean {
        if (!started) return false
        val toUnregister = receiver
        started = false
        receiver = null
        if (toUnregister == null) return false
        return try {
            appContext.unregisterReceiver(toUnregister)
            true
        } catch (notRegistered: IllegalArgumentException) {
            false // cleanup failure reported by the coordinator, not hidden
        }
    }

    companion object {
        private fun singleThreadExecutor(): Executor {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "wifi-observer").apply { isDaemon = true }
            }
            return executor
        }
    }
}
