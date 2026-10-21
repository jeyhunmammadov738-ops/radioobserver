package com.example.radioobserver.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import com.example.radioobserver.core.radio.boundary.ObservationSessionListener
import com.example.radioobserver.core.radio.boundary.ForegroundLossAwareSource
import com.example.radioobserver.core.radio.boundary.ObservationSessionSpec
import com.example.radioobserver.core.radio.boundary.ScheduledTask
import com.example.radioobserver.core.radio.boundary.SessionScheduler
import com.example.radioobserver.core.radio.boundary.SourceStartResult
import com.example.radioobserver.core.radio.boundary.WifiObservationSource
import com.example.radioobserver.core.radio.boundary.WifiSessionCoordinator
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.ObservationReadinessModel
import com.example.radioobserver.core.radio.permissions.PermissionId

/**
 * Android receive-only Wi-Fi observation source (task 2.4): thin wiring of real Android
 * inputs into the pure-JVM WifiSessionCoordinator.
 *
 * FOREGROUND ELIGIBILITY IS NOT WIRED ON PURPOSE: defaults to false, so nothing registers a
 * receiver or reads results until a later, separately authorized task supplies a real
 * foreground/user-session signal. Strictly receive-only: no scan trigger, no connection or
 * configuration API, no persistence, no upload, no background path.
 */
class AndroidWifiObservationSource(
    context: Context,
    foregroundEligibility: () -> Boolean = { false },
    permanentDenialProvider: (PermissionId) -> Boolean = { false },
) : WifiObservationSource, ForegroundLossAwareSource {

    private val appContext = context.applicationContext
    private val wifiManagerProvider: () -> WifiManager? = {
        appContext.getSystemService(WifiManager::class.java)
    }
    private val probe = AndroidWifiCapabilityProbe(appContext, wifiManagerProvider, permanentDenialProvider)

    private val coordinator = WifiSessionCoordinator(
        engine = AndroidWifiResultEngine(appContext),
        fetcher = AndroidWifiResultFetcher(wifiManagerProvider),
        readinessProvider = {
            ObservationReadinessModel.evaluate(probe.currentSnapshot()) as ObservationReadiness
        },
        foregroundEligible = foregroundEligibility,
        scheduler = WifiTaskScheduler(),
    )

    override val radio: RadioType = RadioType.WIFI

    override fun startObservation(
        spec: ObservationSessionSpec,
        listener: ObservationSessionListener,
    ): SourceStartResult = coordinator.startObservation(spec, listener)

    /** Explicit foreground-loss signal; task 2.6 will wire real lifecycle into this. */
    override fun notifyForegroundLost() = coordinator.notifyForegroundLost()
}

/** Main-thread single-shot scheduler (2.3 BLE keeps its own; no shared-code refactor here). */
private class WifiTaskScheduler : SessionScheduler {
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
