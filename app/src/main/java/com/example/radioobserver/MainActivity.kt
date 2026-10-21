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
import androidx.compose.runtime.mutableStateOf
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.ui.RadioObserverControlScreen
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Phase 3-pending entry point. Phase 2.6 wiring state, honestly stated:
 * - WIRED: Activity RESUMED/paused/stopped/destroyed -> [ObservationLifecycleBridge] ->
 *   [RadioObservationSupervisor] foreground eligibility (default FALSE). Losing RESUMED
 *   stops any active BLE/Wi-Fi session; cleanup is idempotent.
 * - Phase 3.5: the real Material 3 Turkish control screen renders the orchestrator's
 *   sanitized state and forwards ONLY genuine button clicks to the presenter. No
 *   auto-start on create/resume/restore/recomposition; observation still requires an
 *   explicit user Start tap plus fresh permission/capability gates (Phase 3.3/3.4).
 * - No feed/detail/recovery screens exist yet (Tasks 3.6/3.7).
 */
class MainActivity : ComponentActivity() {

    private lateinit var observationSupervisor: RadioObservationSupervisor
    private lateinit var observationLifecycleBridge: ObservationLifecycleBridge

    // Phase 3.3: lifecycle-safe runtime permission bridge. The Activity Result launcher is
    // registered UNCONDITIONALLY in onCreate (recreation-safe, stable order) and can only
    // ever be LAUNCHED by an explicit user Start tap routed through onUserStartTap (the
    // Phase 3.5 Compose Start control will call it). Nothing here starts observation.
    private lateinit var permissionFlowController: PermissionFlowController

    // Phase 3.4: the production session call path (orchestrator + presenter). The
    // Phase 3.5 control screen wires REAL user Start/Stop taps to the presenter.
    private lateinit var sessionOrchestrator: ObservationSessionOrchestrator
    private lateinit var sessionPresenter: ObservationSessionPresenter

    // Phase 3.5: Activity-scoped Compose state holder — the single source of truth for
    // the control screen. Session statuses come ONLY from the 3.4 orchestrator; the
    // locationAccess field is refreshed by the 3.3 permission controller (never a
    // session status — a grant alone changes nothing; the two-tap rule stands). The
    // holder dies with this Activity: recreation starts from a fresh inert state.
    private val controlUiState = mutableStateOf(ObservationUiState.initial())

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
            // 3.5 wiring: refresh ONLY the location-access field of the control state.
            // This callback cannot start anything (3.3's effect type has no start
            // member) and never replays a pending Start tap (two-tap rule).
            onStateChange = { flow ->
                controlUiState.value = controlUiState.value.copy(
                    locationAccess = flow.locationAccess)
            },
        )
        // Phase 3.4 wiring: explicit-user-tap-only observation path. The Phase 3.5
        // control screen invokes sessionPresenter.onUserStartBleTap()/onUserStartWifiTap()
        // ONLY from real button clicks; no implicit trigger exists. The pipeline uses the
        // wall clock; the supervisor supplies live foreground eligibility at tap time.
        sessionOrchestrator = ObservationSessionOrchestrator(
            supervisor = observationSupervisor,
            pipelineFactory = { ObservationPipeline(clock = { System.currentTimeMillis() }) },
            onUiState = { state -> controlUiState.value = state },
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
            // Phase 3.5: the real Turkish control screen. Actions leave ONLY through
            // these user-click callbacks into the 3.4 presenter; composition,
            // recomposition, resume, permission callbacks and recreation dispatch
            // NOTHING on their own. The state is the orchestrator's sanitized snapshot.
            RadioObserverControlScreen(
                state = controlUiState.value,
                onStartBle = sessionPresenter::onUserStartBleTap,
                onStopBle = sessionPresenter::onUserStopBleTap,
                onStartWifi = sessionPresenter::onUserStartWifiTap,
                onStopWifi = sessionPresenter::onUserStopWifiTap,
            )
        }
    }
}
