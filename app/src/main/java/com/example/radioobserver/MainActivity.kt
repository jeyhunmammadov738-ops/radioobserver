package com.example.radioobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.radioobserver.history.HistoryController
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import com.example.radioobserver.R
import com.example.radioobserver.core.radio.ui.LiveFilter
import com.example.radioobserver.core.radio.ui.LiveViewKind
import com.example.radioobserver.core.radio.ui.ObservationFeedProjection.FeedFilter
import com.example.radioobserver.ui.ObservationDetailScreen
import com.example.radioobserver.ui.ObservationsFeedScreen
import com.example.radioobserver.core.radio.ui.FilterPresets
import com.example.radioobserver.core.radio.ui.ObservationUiState
import com.example.radioobserver.filters.FilterPresetFileStorage
import com.example.radioobserver.signature.SignatureUserRuleFileStorage
import com.example.radioobserver.history.HistoryAnnotationFileStorage
import com.example.radioobserver.history.SessionGroupController
import com.example.radioobserver.history.SessionGroupFileStorage
import com.example.radioobserver.core.radio.history.HistoryAnnotationPersistence
import com.example.radioobserver.core.radio.ui.LiveFavorites
import com.example.radioobserver.core.radio.alerts.AlertTrigger
import com.example.radioobserver.core.radio.alerts.SessionAlert
import com.example.radioobserver.core.radio.alerts.SessionAlertEngine
import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus
import com.example.radioobserver.core.radio.signature.UserSignatureRuleRepository
import com.example.radioobserver.core.radio.signature.UserSignatureRule
import com.example.radioobserver.core.radio.signature.UserRuleSelector
import com.example.radioobserver.core.radio.signature.SignatureCategory
import com.example.radioobserver.core.radio.signature.UserRuleError
import com.example.radioobserver.core.radio.signature.UserRuleResult
import com.example.radioobserver.ui.HistoryDetailScreen
import com.example.radioobserver.ui.HistoryFilter
import com.example.radioobserver.ui.HistoryScreen
import com.example.radioobserver.ui.RadioObserverControlScreen
import com.example.radioobserver.ui.SettingsScreen
import com.example.radioobserver.ui.SessionGroupCompareScreen
import com.example.radioobserver.ui.SessionGroupDetailScreen
import com.example.radioobserver.ui.SessionGroupsScreen
import com.example.radioobserver.ui.SignatureCatalogScreen
import com.example.radioobserver.ui.theme.RadioObserverTheme
import com.example.radioobserver.diag.FieldDiagnostics
import com.example.radioobserver.diag.DeveloperDiagGate
import com.example.radioobserver.navigation.AppDestination
import com.example.radioobserver.navigation.AppDestination.TabScreen
import com.example.radioobserver.navigation.AppNavigation
import com.example.radioobserver.navigation.AppTab
import com.example.radioobserver.ui.FieldDiagnosticsPanel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    // Post-roadmap UI iteration (2026-10-23): four-tab bottom navigation state
    // (Gözlem, Gözlemler, Geçmiş, Ayarlar). PURE navigation: switching a tab changes
    // NO session/permission/scanning state and never reopens a stale detail
    // (JVM-tested policy in AppNavigation). Dies with the Activity.
    private val currentScreen = mutableStateOf<AppDestination>(TabScreen(AppTab.OBSERVATION))
    private val feedFilter = mutableStateOf(FeedFilter.ALL)
    // WORKSTREAM 01: selected live view (Liste/Radar/Zaman tüneli) — UI preference
    // ONLY; never persisted, dispatches nothing, dies with the Activity.
    private val feedView = mutableStateOf(LiveViewKind.LIST)
    // WORKSTREAM 03B: user-defined classification rules — UI state mirror of the
    // explicit-user-action repository (created/edited/toggled/deleted ONLY by user
    // gestures). Declared WITHOUT a Context-dependent initializer; the repository
    // is constructed in onCreate AFTER super.onCreate (WS02 hotfix contract).
    private lateinit var signatureUserRuleRepository: UserSignatureRuleRepository
    private val signatureUserRules = mutableStateOf(emptyList<UserSignatureRule>())
    // WORKSTREAM 04: SESSION-LOCAL live favorites — in-memory UI state ONLY
    // (no Context, no persistence: favorites die with the process/session). The
    // favorite set holds session-local rowId ordinals exclusively.
    private val liveFavoriteIds = mutableStateOf(emptySet<String>())
    // Favorites-only view toggles: ephemeral UI preferences, never persisted.
    private val feedFavoritesOnly = mutableStateOf(false)
    private val historyFavoritesOnly = mutableStateOf(false)
    // WORKSTREAM 04: controlled IN-APP alert engine. Pure Kotlin, constructed
    // with NO Android dependency (safe in a property initializer); DISABLED by
    // default and empty at every cold start (no persistence, no auto re-enable).
    private val sessionAlertEngine = SessionAlertEngine()
    private val sessionAlertUi = mutableStateOf(SessionAlertUiState())
    /** Compose-observed alert UI snapshot; updated ONLY from orchestrator publications. */
    data class SessionAlertUiState(
        val enabled: Boolean = false,
        val triggers: Set<AlertTrigger> = emptySet(),
        val alerts: List<SessionAlert> = emptyList(),
        val sessionActive: Boolean = false,
    ) {
        /** Converts to the shared control-screen snapshot type. */
        fun toUi() = com.example.radioobserver.ui.AlertsUi(
            enabled = enabled, triggers = triggers, alerts = alerts,
            sessionActive = sessionActive,
        )
    }
    // WORKSTREAM 02: advanced live filtering — UI state ONLY, never persisted on
    // its own (presets are saved EXPLICITLY by the user via the repository).
    // Changing any of it dispatches nothing (no scan, permission, session action).
    private val advancedCriteria = mutableStateOf(LiveFilter.AdvancedCriteria())
    private val searchQuery = mutableStateOf("")
    // Explicit user-managed presets, app-private and restart-surviving.
    // STARTUP CRASH HOTFIX (Workstream 02, 2026-10-24): these used to be property
    // INITIALIZERS, which execute during Activity instantiation — BEFORE Android
    // attaches the base context (attachBaseContext). At that moment mBase is null,
    // so FilterPresetFileStorage(applicationContext) crashed with
    // "NullPointerException: getApplicationContext() on a null object reference"
    // and the app could not even instantiate MainActivity (Xiaomi startup crash).
    // Lifecycle-correct pattern: declare lazily, initialize EXACTLY ONCE in
    // onCreate AFTER super.onCreate (same as HistoryController/FieldDiagnostics),
    // before setContent or any consumer reads the preset list.
    private lateinit var filterPresetRepository: FilterPresets.Repository
    private val filterPresets = mutableStateOf(emptyList<FilterPresets.FilterPreset>())
    private val historyFilter = mutableStateOf(HistoryFilter.ALL)

    // DEBUG-only, hidden-by-default FieldDiag-04 visibility (post-roadmap policy):
    // cold start ALWAYS hidden; seven taps on the version label in Ayarlar →
    // Uygulama hakkında unlock the developer section; NOTHING is persisted, so
    // process recreation re-locks and re-hides automatically.
    private val diagGate = DeveloperDiagGate()
    private var diagVisible = mutableStateOf(false)

    // OPTIONAL LOCAL HISTORY (post-roadmap feature, developer-approved 2026-10-23).
    // Default OFF; recording needs explicit user consent on the History screen.
    // The controller is the one-way live→history bridge; all its work is inert for
    // observation (no session, permission or scanning state can change from here).
    private lateinit var historyController: HistoryController
    // WORKSTREAM 05: named analytical groups over ALREADY-SAVED records. Same
    // lifecycle-safe pattern: lateinit, constructed in onCreate AFTER
    // super.onCreate (WS02 startup hotfix contract).
    private lateinit var sessionGroupController: SessionGroupController

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
        // STARTUP CRASH HOTFIX (Workstream 02, 2026-10-24): Context is now valid
        // (mBase attached by super.onCreate path). One-time, main-thread,
        // synchronous initialization of the preset repository — the exact
        // expression that previously ran during construction and crashed. Even a
        // corrupt/unsupported preset store fails closed to an empty list here
        // (codec drops malformed lines without touching the file), so startup
        // can never crash on preset data. No scan/permission/session action.
        filterPresetRepository =
            FilterPresets.Repository(FilterPresetFileStorage(applicationContext))
        filterPresets.value = filterPresetRepository.list()
        // WORKSTREAM 03B: user classification-catalog repository — same
        // lifecycle-correct pattern as above (constructed exactly ONCE, here,
        // never in a property initializer). Loading is fail-closed: a corrupt or
        // unsupported catalog file yields an empty list and the original file is
        // never rewritten. Observing devices creates no rule (explicit actions only).
        signatureUserRuleRepository =
            UserSignatureRuleRepository(SignatureUserRuleFileStorage(applicationContext))
        signatureUserRules.value = signatureUserRuleRepository.list()
        // DEBUG-only, privacy-safe field diagnostics (release builds emit nothing).
        FieldDiagnostics.initialize(applicationContext)
        // Eligibility holder: sources read this; only the supervisor (lifecycle-driven) sets it.
        val foregroundEligibility = AtomicBoolean(false)
        // ROOT-CAUSE FIX (2026-10-23, fifth field iteration): the trailing lambda used
        // to bind to the LAST constructor parameter (permanentDenialProvider), NOT to
        // foregroundEligibility — so both sources kept the DEFAULT { false } gate and
        // ALWAYS refused with ForegroundIneligible on the real phone (FieldDiag-04:
        // stage=SOURCE_REFUSAL, issue=NOT_ELIGIBLE with every fact true). Named
        // arguments make the binding explicit; the sources now make the parameter
        // REQUIRED, so this misbinding class can never compile again.
        val bleSource = AndroidBleObservationSource(
            applicationContext,
            foregroundEligibility = { foregroundEligibility.get() },
        )
        val wifiSource = AndroidWifiObservationSource(
            applicationContext,
            foregroundEligibility = { foregroundEligibility.get() },
        )
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
        // Optional history: fed with EVERY orchestrator publication (event-driven, not
        // recomposition-driven). The controller's tracker dedups to one bounded snapshot
        // per session-local observation and never injects anything back.
        // WORKSTREAM 04: record-bound annotation storage — same lifecycle-correct
        // pattern as above (Context-dependent storage constructed exactly ONCE,
        // here, after super.onCreate; never in a property initializer).
        historyController = HistoryController(
            applicationContext,
            HistoryAnnotationFileStorage(applicationContext) as HistoryAnnotationPersistence,
        )
        historyController.initialize()
        // WORKSTREAM 05: separate versioned app-private metadata store — group
        // operations NEVER touch the History store, consent or radio sessions.
        sessionGroupController = SessionGroupController(
            SessionGroupFileStorage(applicationContext),
        )
        sessionGroupController.initialize()
        sessionOrchestrator = ObservationSessionOrchestrator(
            supervisor = observationSupervisor,
            pipelineFactory = { ObservationPipeline(clock = { System.currentTimeMillis() }) },
            onUiState = { state ->
                controlUiState.value = state
                historyController.onLiveRows(state.rows)
                // WORKSTREAM 04: session-local favorite teardown — favorites die
                // with their rows (disposal/TTL/empty session clears the set).
                liveFavoriteIds.value = LiveFavorites.retain(
                    liveFavoriteIds.value, state.rows.map { it.rowId },
                )
                // WORKSTREAM 04: alert reducer — fed ONLY here (every orchestrator
                // publication), never by recomposition. A session is eligible only
                // while a user-started OBSERVING session is actually alive; any
                // other state (stop, timeout, foreground loss) resets the engine.
                val sessionActive = state.bleSession.status == RadioSessionUiStatus.OBSERVING ||
                    state.wifiSession.status == RadioSessionUiStatus.OBSERVING
                sessionAlertEngine.onRows(state.rows, sessionActive = sessionActive)
                sessionAlertUi.value = SessionAlertUiState(
                    enabled = sessionAlertEngine.enabled,
                    triggers = sessionAlertEngine.triggers,
                    alerts = sessionAlertEngine.alerts(),
                    sessionActive = sessionActive,
                )
            },
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
            FieldDiagnostics.log("activity_recreated",
                "note" to "timeline_continues_process_clock")
            permissionFlowController.onActivityRecreated()
        }
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            ""
        }
        setContent {
            // Phase 3.5 (behavior preserved): actions leave ONLY through these
            // user-click callbacks into the 3.4 presenter; composition,
            // recomposition, resume, permission callbacks and recreation dispatch
            // NOTHING on their own. The state is the orchestrator's sanitized snapshot.
            RadioObserverTheme {
            val destination = currentScreen.value
            // WORKSTREAM 05: retention/deletion reconciliation — every History
            // snapshot change drops dangling group references (idempotent, pure
            // metadata cleanup; never resurrects records).
            androidx.compose.runtime.LaunchedEffect(historyController.state.value) {
                sessionGroupController.syncWithRecords(
                    historyController.state.value.records.map { it.id }.toSet(),
                )
            }
            // One deterministic back policy for every destination (JVM-tested):
            // details return to their parent tab, non-primary tabs return to Gözlem,
            // Gözlem hands Back to the system.
            BackHandler(enabled = AppNavigation.handlesBack(destination)) {
                AppNavigation.onBack(destination)?.let { currentScreen.value = it }
            }
            Scaffold(
                bottomBar = {
                    if (AppNavigation.showsBottomBar(destination)) {
                        MainBottomBar(
                            currentTab = AppNavigation.currentTab(destination),
                            onTabSelected = { tab ->
                                currentScreen.value = AppNavigation.onTabSelected(destination, tab)
                            },
                        )
                    }
                },
            ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                // FieldDiag-04 (2026-10-23): DEBUG-ONLY on-screen diagnostics, now
                // HIDDEN BY DEFAULT. Visible ONLY when the build is debuggable AND
                // the developer section was unlocked with seven version-label taps AND
                // the user explicitly switched it on (pure, JVM-tested rule). Never
                // rendered in release builds; reads typed state only, no control-flow
                // effect, nothing persisted.
                if (DeveloperDiagGate.isVisible(
                        isDebugBuild = FieldDiagnostics.isDebugBuild,
                        unlocked = diagGate.isUnlocked,
                        userToggle = diagVisible.value,
                    )
                ) {
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
                is TabScreen -> when (screen.tab) {
                    AppTab.OBSERVATION -> RadioObserverControlScreen(
                        state = controlUiState.value,
                        onStartBle = sessionPresenter::onUserStartBleTap,
                        onStopBle = sessionPresenter::onUserStopBleTap,
                        onStartWifi = sessionPresenter::onUserStartWifiTap,
                        onStopWifi = sessionPresenter::onUserStopWifiTap,
                        // 3.7 recovery actions: guarded, user-invoked ONLY; the
                        // controller reports failure honestly (no success claim, no
                        // permission grant before OS state confirms it).
                        onOpenPermissionSettings = { permissionFlowController.openAppPermissionSettings() },
                        onOpenLocationSettings = { permissionFlowController.openLocationSettings() },
                        // Readiness recheck: refreshes status from CURRENT facts; never
                        // prompts a dialog, never starts a session (two-tap rule intact).
                        onRecheckBle = sessionPresenter::onUserRecheckBleTap,
                        onRecheckWifi = sessionPresenter::onUserRecheckWifiTap,
                        // WORKSTREAM 04: IN-APP session alerts. Controls are pure
                        // engine state changes — they never dispatch Start/Stop, a
                        // permission action or any radio event.
                        alerts = sessionAlertUi.value.toUi(),
                        onAlertsEnabledChange = { enabled ->
                            sessionAlertEngine.setEnabled(enabled)
                            sessionAlertUi.value = sessionAlertUi.value.copy(
                                enabled = sessionAlertEngine.enabled,
                                alerts = sessionAlertEngine.alerts(),
                            )
                        },
                        onAlertTriggerToggled = { trigger ->
                            val next = sessionAlertEngine.triggers.let {
                                if (trigger in it) it - trigger else it + trigger
                            }
                            sessionAlertEngine.setTriggers(next)
                            sessionAlertUi.value = sessionAlertUi.value.copy(
                                triggers = sessionAlertEngine.triggers,
                            )
                        },
                        onDismissAlert = { alertId ->
                            sessionAlertEngine.dismiss(alertId)
                            sessionAlertUi.value = sessionAlertUi.value.copy(
                                alerts = sessionAlertEngine.alerts(),
                            )
                        },
                        onDismissAllAlerts = {
                            sessionAlertEngine.dismissAll()
                            sessionAlertUi.value = sessionAlertUi.value.copy(
                                alerts = sessionAlertEngine.alerts(),
                            )
                        },
                    )
                    AppTab.FEED -> ObservationsFeedScreen(
                        state = controlUiState.value,
                        filter = feedFilter.value,
                        onFilterChange = { feedFilter.value = it },
                        viewKind = feedView.value,
                        onViewKindChange = { feedView.value = it },
                        // WORKSTREAM 02: advanced filter + preset wiring. All pure
                        // UI-state/repository mutations — nothing here starts/stops
                        // a session, requests permission or touches History.
                        criteria = advancedCriteria.value,
                        searchQuery = searchQuery.value,
                        onApplyAdvanced = { appliedCriteria, appliedQuery ->
                            advancedCriteria.value = appliedCriteria
                            searchQuery.value = appliedQuery
                        },
                        onResetAdvanced = {
                            advancedCriteria.value = LiveFilter.AdvancedCriteria()
                            searchQuery.value = ""
                        },
                        presets = filterPresets.value,
                        onSavePreset = { name ->
                            filterPresetRepository.save(
                                name, feedFilter.value, advancedCriteria.value,
                            )
                            filterPresets.value = filterPresetRepository.list()
                        },
                        onApplyPreset = { presetId ->
                            // Deterministic reconciliation: applying a preset adopts
                            // its source filter + criteria and CLEARS the temporary
                            // search text (search is transient by design).
                            filterPresetRepository.findById(presetId)?.let { preset ->
                                feedFilter.value = preset.sourceFilter
                                advancedCriteria.value = preset.criteria
                                searchQuery.value = ""
                            }
                        },
                        onRenamePreset = { presetId, newName ->
                            filterPresetRepository.rename(presetId, newName)
                            filterPresets.value = filterPresetRepository.list()
                        },
                        onDeletePreset = { presetId ->
                            filterPresetRepository.delete(presetId)
                            filterPresets.value = filterPresetRepository.list()
                        },
                        onRowSelect = { rowId ->
                            currentScreen.value = AppDestination.ObservationDetail(rowId)
                        },
                        // WORKSTREAM 04: session-local favorites (list view only —
                        // Radar/Timeline membership untouched; UI state, dispatches nothing).
                        favorites = liveFavoriteIds.value,
                        favoritesOnly = feedFavoritesOnly.value,
                        onToggleFavorite = { rowId ->
                            liveFavoriteIds.value =
                                LiveFavorites.toggle(liveFavoriteIds.value, rowId)
                        },
                        onFavoritesOnlyChange = { feedFavoritesOnly.value = it },
                    )
                    AppTab.HISTORY -> HistoryScreen(
                        // Browsing surface only: preferences live in Ayarlar, driven
                        // by the SAME HistoryController state (single source of truth).
                        state = historyController.state.value,
                        filter = historyFilter.value,
                        onFilterChange = { historyFilter.value = it },
                        // WORKSTREAM 04: saved favorites filter (ephemeral UI toggle).
                        favoritesOnly = historyFavoritesOnly.value,
                        onFavoritesOnlyChange = { historyFavoritesOnly.value = it },
                        onToggleFavorite = { recordId ->
                            val current = historyController.state.value
                                .annotations[recordId]?.favorite ?: false
                            historyController.setAnnotation(recordId, null, null, !current)
                        },
                        onDeleteOne = { historyController.deleteOne(it) },
                        onClearAll = { historyController.clearAll() },
                        onDismissNotices = { historyController.dismissNotices() },
                        onOpenDetail = { recordId ->
                            currentScreen.value = AppDestination.HistoryDetail(recordId)
                        },
                        onOpenSettings = { currentScreen.value = TabScreen(AppTab.SETTINGS) },
                        // WORKSTREAM 05: analytical groups live INSIDE the Geçmiş
                        // area (no fifth tab). Opening dispatches NOTHING.
                        onOpenGroups = { currentScreen.value = AppDestination.SessionGroups() },
                    )
                    AppTab.SETTINGS -> SettingsScreen(
                        state = historyController.state.value,
                        appVersion = appVersion,
                        isDebugBuild = FieldDiagnostics.isDebugBuild,
                        diagUnlocked = diagGate.isUnlocked,
                        diagVisible = diagVisible.value,
                        onConsentChange = { consent ->
                            if (consent.recordingEnabled != historyController.state.value.consent.recordingEnabled) {
                                historyController.setRecordingEnabled(consent.recordingEnabled)
                            }
                            if (consent.wifiNamesEnabled != historyController.state.value.consent.wifiNamesEnabled) {
                                historyController.setWifiNamesEnabled(consent.wifiNamesEnabled)
                            }
                        },
                        onRetentionChange = { historyController.setRetentionDays(it) },
                        onClearAll = { historyController.clearAll() },
                        onVersionTap = { diagGate.onVersionTap() },
                        onDiagVisibilityChange = { visible ->
                            diagVisible.value = visible
                        },
                        onOpenSignatureCatalog = {
                            currentScreen.value = AppDestination.SignatureCatalog()
                        },
                    )
                }
                is AppDestination.SessionGroups -> SessionGroupsScreen(
                    state = sessionGroupController.state.value,
                    records = historyController.state.value.records,
                    onCreate = { sessionGroupController.create(it) },
                    onRename = { id, name -> sessionGroupController.rename(id, name) },
                    onDelete = { id -> sessionGroupController.delete(id) },
                    onOpenDetail = { id ->
                        currentScreen.value = AppDestination.SessionGroupDetail(id)
                    },
                    onOpenCompare = { currentScreen.value = AppDestination.SessionGroupCompare() },
                    onDismissNotices = { sessionGroupController.dismissNotices() },
                    onBack = { currentScreen.value = TabScreen(AppTab.HISTORY) },
                )
                is AppDestination.SessionGroupDetail -> SessionGroupDetailScreen(
                    group = sessionGroupController.state.value.groups
                        .firstOrNull { it.id == screen.groupId },
                    records = historyController.state.value.records,
                    onAddRecord = { groupId, recordId ->
                        sessionGroupController.addRecord(
                            groupId, recordId,
                            historyController.state.value.records.map { it.id }.toSet(),
                        )
                    },
                    onRemoveRecord = { groupId, recordId ->
                        sessionGroupController.removeRecord(groupId, recordId)
                    },
                    onOpenHistoryDetail = { recordId ->
                        currentScreen.value = AppDestination.HistoryDetail(recordId)
                    },
                    onBack = { currentScreen.value = AppDestination.SessionGroups() },
                )
                is AppDestination.SessionGroupCompare -> SessionGroupCompareScreen(
                    groups = sessionGroupController.state.value.groups,
                    records = historyController.state.value.records,
                    onBack = { currentScreen.value = AppDestination.SessionGroups() },
                )
                is AppDestination.SignatureCatalog -> SignatureCatalogScreen(
                    userRules = signatureUserRules.value,
                    onCreateRule = { title, selector, category ->
                        val result = signatureUserRuleRepository.create(title, selector, category)
                        signatureUserRules.value = signatureUserRuleRepository.list()
                        (result as? UserRuleResult.Error)?.messageKey
                    },
                    onUpdateRule = { id, title, selector, category ->
                        val result = signatureUserRuleRepository.update(id, title, selector, category)
                        signatureUserRules.value = signatureUserRuleRepository.list()
                        (result as? UserRuleResult.Error)?.messageKey
                    },
                    onSetEnabled = { id, enabled ->
                        signatureUserRuleRepository.setEnabled(id, enabled)
                        signatureUserRules.value = signatureUserRuleRepository.list()
                    },
                    onDeleteRule = { id ->
                        signatureUserRuleRepository.delete(id)
                        signatureUserRules.value = signatureUserRuleRepository.list()
                    },
                    onBack = { currentScreen.value = TabScreen(AppTab.SETTINGS) },
                )
                is AppDestination.ObservationDetail -> ObservationDetailScreen(
                    state = controlUiState.value,
                    rowId = screen.rowId,
                    onBack = { currentScreen.value = TabScreen(AppTab.FEED) },
                    userRules = signatureUserRules.value,
                    // WORKSTREAM 04: session-local favorite affordance in the detail.
                    isFavorite = LiveFavorites.isFavorite(
                        liveFavoriteIds.value, screen.rowId),
                    onToggleFavorite = {
                        liveFavoriteIds.value =
                            LiveFavorites.toggle(liveFavoriteIds.value, screen.rowId)
                    },
                )
                is AppDestination.HistoryDetail -> HistoryDetailScreen(
                    record = historyController.state.value.records
                        .firstOrNull { it.id == screen.recordId },
                    // WORKSTREAM 04: record-bound annotation editor + favorite.
                    annotation = historyController.state.value.annotations[screen.recordId],
                    onSaveAnnotation = { label, note, favorite ->
                        historyController.setAnnotation(screen.recordId, label, note, favorite)
                    },
                    onDeleteAnnotation = { historyController.deleteAnnotation(screen.recordId) },
                    onBack = { currentScreen.value = TabScreen(AppTab.HISTORY) },
                )
            }
            }
        }
    }
    }
    }
    }

    /** Four-tab Material 3 bottom bar. PURE navigation: a tab tap only changes the
     *  destination — it never starts/stops a session, requests a permission or
     *  refreshes scanning state (JVM-tested in AppNavigationTest). */
    @Composable
    private fun MainBottomBar(
        currentTab: AppTab,
        onTabSelected: (AppTab) -> Unit,
    ) {
        NavigationBar {
            NavigationBarItem(
                selected = currentTab == AppTab.OBSERVATION,
                onClick = { onTabSelected(AppTab.OBSERVATION) },
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_observation)) },
            )
            NavigationBarItem(
                selected = currentTab == AppTab.FEED,
                onClick = { onTabSelected(AppTab.FEED) },
                icon = { Icon(Icons.Filled.List, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_feed)) },
            )
            NavigationBarItem(
                selected = currentTab == AppTab.HISTORY,
                onClick = { onTabSelected(AppTab.HISTORY) },
                icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_history)) },
            )
            NavigationBarItem(
                selected = currentTab == AppTab.SETTINGS,
                onClick = { onTabSelected(AppTab.SETTINGS) },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.tab_settings)) },
            )
        }
    }
}


