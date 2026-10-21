package com.example.radioobserver.ble

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.radioobserver.core.radio.boundary.BleObservationSource
import com.example.radioobserver.core.radio.boundary.ForegroundLossAwareSource
import com.example.radioobserver.core.radio.boundary.BleSessionCoordinator
import com.example.radioobserver.core.radio.boundary.ObservationSessionListener
import com.example.radioobserver.core.radio.boundary.ObservationSessionSpec
import com.example.radioobserver.core.radio.boundary.ScheduledTask
import com.example.radioobserver.core.radio.boundary.SessionScheduler
import com.example.radioobserver.core.radio.boundary.SourceStartResult
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.ObservationReadinessModel
import com.example.radioobserver.core.radio.permissions.PermissionId

/**
 * Android BLE observation source (task 2.3): thin wiring of real Android inputs into the
 * pure-JVM BleSessionCoordinator.
 *
 * FOREGROUND ELIGIBILITY IS NOT WIRED ON PURPOSE: [foregroundEligibility] defaults to
 * false, so NOTHING can start a scan until a later, separately authorized task supplies
 * a real foreground/user-session signal. Scanning never starts on app open, in the
 * background, or by any automatic path. This class is scan-only: no pairing, connection,
 * GATT, advertising, Wi-Fi, network, upload, or persistence code exists here.
 */
class AndroidBleObservationSource(
    context: Context,
    foregroundEligibility: () -> Boolean = { false },
    permanentDenialProvider: (PermissionId) -> Boolean = { false },
) : BleObservationSource, ForegroundLossAwareSource {

    private val appContext = context.applicationContext
    private val adapterProvider: () -> android.bluetooth.BluetoothAdapter? = {
        appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
    }
    private val probe = AndroidBleCapabilityProbe(appContext, adapterProvider, permanentDenialProvider)

    private val coordinator = BleSessionCoordinator(
        engine = AndroidBleScanEngine(adapterProvider),
        readinessProvider = {
            ObservationReadinessModel.evaluate(probe.currentSnapshot()) as ObservationReadiness
        },
        foregroundEligible = foregroundEligibility,
        scheduler = AndroidTaskScheduler(),
    )

    override val radio = com.example.radioobserver.core.radio.model.RadioType.BLE

    override fun startSession(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult = coordinator.startSession(spec, listener)

    /** Explicit foreground-loss signal; task 2.6 will wire real lifecycle into this. */
    override fun notifyForegroundLost() = coordinator.notifyForegroundLost()
}

/** Main-thread single-shot scheduler for session timeouts. */
private class AndroidTaskScheduler : SessionScheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun schedule(delayMs: Long, action: () -> Unit): ScheduledTask =
        object : ScheduledTask {
            private val runnable = Runnable { action() }
            private var cancelled = false

            init {
                handler.postDelayed(runnable, delayMs)
            }

            override fun cancel() {
                if (!cancelled) {
                    cancelled = true
                    handler.removeCallbacks(runnable)
                }
            }
        }
}
