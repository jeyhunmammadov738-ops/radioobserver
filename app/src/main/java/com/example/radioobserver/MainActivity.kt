package com.example.radioobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter
import com.example.radioobserver.ui.ObservationDetailScreen
import com.example.radioobserver.ui.ObservationsFeedScreen
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.ui.RadioObserverControlScreen
import com.example.radioobserver.diag.FieldDiagnostics
import com.example.radioobserver.ui.FieldDiagnosticsPanel
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

    // Third field iteration (2026-10-23): DEBUG panel inputs. The sink flag the SOURCE
    // adapters gate on, surfaced for the on-screen FieldDiag-03 diagnostics only.
    private val sourceEligibilityState = mutableStateOf<Boolean?>(null)

    // Phase 3.6: purely LOCAL read-only navigation state (no navigation dependency).
    // Switching destination changes NO session/permission/scanning state; the control
    // screen's Start/Stop wiring is untouched. Dies with the Activity: recreation
    // returns to Home and renders only current sanitized state.
    private sealed interface Screen {
        data object Home : Screen
        data object Observations : Screen
        data class ObservationDetail(val rowId: String) : Screen
    }
    private val currentScreen = mutableStateOf<Screen>(Screen.Home)
    private val feedFilter = mutableStateOf(FeedFilter.ALL)

    override fun onResume() {
        super.onResume()
        // Phase 3.7: read-only permission-state refresh after any return (Settings,
        // permission dialog dismissal, app switch). NEVER starts observation and never
        // prompts: the 3.3 reducer maps SettingsReturned to a pure state update. The
        // session status itself stays untouched — a separate explicit Start (fresh
        // gates) or the user's "Yeniden kontrol et" action updates it honestly.
        permissionFlowController.onSettingsReturned()
        FieldDiagnostics.log("activity_resume")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DEBUG-only, privacy-safe field diagnostics (release builds emit nothing).
        FieldDiagnostics.initialize(applicationContext)
        // Eligibility holder: sources read this; only the supervisor (lifecycle-driven) sets it.
        val foregroundEligibility = AtomicBoolean(false)
        val bleSource = AndroidBleObservationSource(applicationContext) { foregroundEligibility.get() }
        val wifiSource = AndroidWifiObservationSource(applicationContext) { foregroundEligibility.get() }
        observationSupervisor = RadioObservationSupervisor(
            bleSource = bleSource,
            wifiSource = wifiSource,
        ) { eligible ->
            foregroundEligibility.set(eligible)
            // Field fix 2 (2026-10-23, second phone): a genuine foreground re-entry must
            // not leave the transient NOT_ELIGIBLE hint latched as current state.
            sessionOrchestrator.onForegroundEligibilityChanged(eligible)
            FieldDiagnostics.log("eligibility_changed", "eligible" to eligible)
            sourceEligibilityState.value = eligible
        }
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
        // Phase 3.7: on recreation the transient Start intent is dropped (no replay);
        // the read-only refresh below re-reads OS facts on every resume.
        if (savedInstanceState != null) {
            permissionFlowController.onActivityRecreated()
        }
        setContent {
            // Phase 3.5: the real Turkish control screen. Actions leave ONLY through
            // these user-click callbacks into the 3.4 presenter; composition,
            // recomposition, resume, permission callbacks and recreation dispatch
            // NOTHING on their own. The state is the orchestrator's sanitized snapshot.
            Column {
                // Third field iteration (2026-10-23): DEBUG-ONLY on-screen diagnostics
                // (FieldDiag-03) so the next phone retest needs NO ADB. Never rendered
                // in release builds; reads typed state only, no control-flow effect.
                if (FieldDiagnostics.isDebugBuild) {
                    val s = controlUiState.value
                    FieldDiagnosticsPanel(
                        orchestratorEligible = observationSupervisor.isForegroundEligible,
                        sourceEligible = sourceEligibilityState.value,
                        sessionSummary = "Oturumlar: BLE=${s.bleSession.status}" +
                            (s.bleSession.issueKey?.let { "($it)" } ?: "") +
                            " | WIFI=${s.wifiSession.status}" +
                            (s.wifiSession.issueKey?.let { "($it)" } ?: ""),
                    )
                }
            Box(Modifier.weight(1f)) {
            when (val screen = currentScreen.value) {
                is Screen.Home -> RadioObserverControlScreen(
                    state = controlUiState.value,
                    onStartBle = sessionPresenter::onUserStartBleTap,
                    onStopBle = sessionPresenter::onUserStopBleTap,
                    onStartWifi = sessionPresenter::onUserStartWifiTap,
                    onStopWifi = sessionPresenter::onUserStopWifiTap,
                    onOpenObservations = { currentScreen.value = Screen.Observations },
                    // 3.7 recovery actions: guarded, user-invoked ONLY; the controller
                    // reports failure honestly (no success claim, no permission grant
                    // before OS state confirms it).
                    onOpenPermissionSettings = { permissionFlowController.openAppPermissionSettings() },
                    onOpenLocationSettings = { permissionFlowController.openLocationSettings() },
                    // Readiness recheck: refreshes status from CURRENT facts; never
                    // prompts a dialog, never starts a session (two-tap rule intact).
                    onRecheckBle = sessionPresenter::onUserRecheckBleTap,
                    onRecheckWifi = sessionPresenter::onUserRecheckWifiTap,
                )
                is Screen.Observations -> {
                    BackHandler { currentScreen.value = Screen.Home }
                    ObservationsFeedScreen(
                        state = controlUiState.value,
                        filter = feedFilter.value,
                        onFilterChange = { feedFilter.value = it },
                        onRowSelect = { rowId ->
                            currentScreen.value = Screen.ObservationDetail(rowId)
                        },
                        onBack = { currentScreen.value = Screen.Home },
                    )
                }
                is Screen.ObservationDetail -> {
                    BackHandler { currentScreen.value = Screen.Observations }
                    ObservationDetailScreen(
                        state = controlUiState.value,
                        rowId = screen.rowId,
                        onBack = { currentScreen.value = Screen.Observations },
                    )
                }
            }
            }
        }
    }
}
}
