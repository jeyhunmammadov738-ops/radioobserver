package com.example.radioobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.radioobserver.ble.AndroidBleObservationSource
import com.example.radioobserver.core.radio.lifecycle.RadioObservationSupervisor
import com.example.radioobserver.lifecycle.ObservationLifecycleBridge
import com.example.radioobserver.permission.AndroidPermissionGateway
import com.example.radioobserver.permission.PermissionFlowController
import com.example.radioobserver.ble.AndroidBleCapabilityProbe
import com.example.radioobserver.wifi.AndroidWifiCapabilityProbe
import com.example.radioobserver.core.radio.pipeline.ObservationPipeline
import com.example.radioobserver.core.radio.session.ObservationSessionOrchestrator
import com.example.radioobserver.session.ObservationSessionPresenter
import com.example.radioobserver.wifi.AndroidWifiObservationSource
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Phase 3-pending entry point. Phase 2.6 wiring state, honestly stated:
 * - WIRED: Activity RESUMED/paused/stopped/destroyed -> [ObservationLifecycleBridge] ->
 *   [RadioObservationSupervisor] foreground eligibility (default FALSE). Losing RESUMED
 *   stops any active BLE/Wi-Fi session; cleanup is idempotent.
 * - INERT BY DESIGN: the observation sources are constructed but nothing starts a session
 *   here. No auto-start on create/resume/restore; an explicit start call (Phase 3 UI)
 *   plus fresh permission/capability checks is still required for any observation.
 * - No scanning code, no permission dialogs, and no live data exist in this placeholder.
 */
class MainActivity : ComponentActivity() {

    private lateinit var observationSupervisor: RadioObservationSupervisor
    private lateinit var observationLifecycleBridge: ObservationLifecycleBridge

    // Phase 3.3: lifecycle-safe runtime permission bridge. The Activity Result launcher is
    // registered UNCONDITIONALLY in onCreate (recreation-safe, stable order) and can only
    // ever be LAUNCHED by an explicit user Start tap routed through onUserStartTap (the
    // Phase 3.5 Compose Start control will call it). Nothing here starts observation.
    private lateinit var permissionFlowController: PermissionFlowController

    // Phase 3.4: the production session call path (orchestrator + presenter). Registered
    // here but deliberately INERT: no Start/Stop button exists yet (Phase 3.5); nothing
    // is invoked by onCreate/onStart/onResume, permission callbacks or recreation.
    private lateinit var sessionOrchestrator: ObservationSessionOrchestrator
    private lateinit var sessionPresenter: ObservationSessionPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Eligibility holder: sources read this; only the supervisor (lifecycle-driven) sets it.
        val foregroundEligibility = AtomicBoolean(false)
        val bleSource = AndroidBleObservationSource(applicationContext) { foregroundEligibility.get() }
        val wifiSource = AndroidWifiObservationSource(applicationContext) { foregroundEligibility.get() }
        observationSupervisor = RadioObservationSupervisor(
            bleSource = bleSource,
            wifiSource = wifiSource,
        ) { eligible -> foregroundEligibility.set(eligible) }
        observationLifecycleBridge = ObservationLifecycleBridge(this, observationSupervisor)
        // 3.3 registration (before the activity reaches STARTED); the state listener stays
        // a no-op until the Phase 3.5 control screen consumes PermissionFlowState. The
        // entry point (permissionFlowController.onUserStartTap) is deliberately INERT
        // here: no temporary scan button is invented in this placeholder.
        permissionFlowController = PermissionFlowController(
            activity = this,
            gateway = AndroidPermissionGateway(applicationContext),
            onStateChange = { /* wired by the 3.5 control screen; inert until then */ },
        )
        // Phase 3.4 wiring: explicit-user-tap-only observation path. The 3.5 Compose Start
        // control will call sessionPresenter.onUserStartBleTap()/onUserStartWifiTap();
        // until then these entry points are never invoked. The pipeline uses the wall
        // clock; the supervisor supplies live foreground eligibility at tap time.
        sessionOrchestrator = ObservationSessionOrchestrator(
            supervisor = observationSupervisor,
            pipelineFactory = { ObservationPipeline(clock = { System.currentTimeMillis() }) },
        )
        sessionPresenter = ObservationSessionPresenter(
            orchestrator = sessionOrchestrator,
            foregroundEligible = { observationSupervisor.isForegroundEligible },
            permissionGateway = AndroidPermissionGateway(applicationContext),
            permissionFlowController = permissionFlowController,
            bleProbe = AndroidBleCapabilityProbe(
                applicationContext,
                adapterProvider = {
                    getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                },
            ),
            wifiProbe = AndroidWifiCapabilityProbe(
                applicationContext,
                wifiManagerProvider = { getSystemService(android.net.wifi.WifiManager::class.java) },
            ),
        )
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Radio Observer",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = "Phase 1 foundation. No scanning, no permissions, no data collection.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
