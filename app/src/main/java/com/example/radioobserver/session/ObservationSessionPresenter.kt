package com.example.radioobserver.session

import com.example.radioobserver.ble.AndroidBleCapabilityProbe
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.permissions.ObservationReadiness
import com.example.radioobserver.core.radio.permissions.ObservationReadinessModel
import com.example.radioobserver.core.radio.permissions.ReadinessBlocker
import com.example.radioobserver.core.radio.session.ObservationSessionOrchestrator
import com.example.radioobserver.core.radio.session.ObservationSessionOrchestrator.StartAuthorization
import com.example.radioobserver.core.radio.ui.PermissionFlowScope
import com.example.radioobserver.core.radio.ui.SessionIssueKey
import com.example.radioobserver.permission.AndroidPermissionGateway
import com.example.radioobserver.permission.PermissionFlowController
import com.example.radioobserver.wifi.AndroidWifiCapabilityProbe

/**
 * Phase 3.4 — thin Android presenter: the production call path a future 3.5 Compose
 * Start/Stop control will invoke. Gathers FRESH facts at real tap time (never cached):
 * runtime grants via [AndroidPermissionGateway], capability via the existing probes, and
 * foreground eligibility from the supervisor; the orchestrator and the sources re-gate
 * at the actual start boundary.
 *
 * TWO-TAP RULE (developer-approved): when FINE location (or BLUETOOTH_SCAN on API 31+)
 * is missing, this tap only routes through the existing 3.3 permission controller. The
 * Start intent is discarded there; observation NEVER starts from a permission callback.
 * A NEW explicit tap is required, which then reaches [ObservationSessionOrchestrator].
 *
 * Nothing here is invoked by Activity creation, resume, recreation, permission
 * callbacks or Settings returns; MainActivity registers it but leaves it INERT until
 * the 3.5 UI wires real buttons. Real radio behavior remains UNVERIFIED (device-free).
 */
class ObservationSessionPresenter(
    private val orchestrator: ObservationSessionOrchestrator,
    private val foregroundEligible: () -> Boolean,
    private val permissionGateway: AndroidPermissionGateway,
    private val permissionFlowController: PermissionFlowController,
    private val bleProbe: AndroidBleCapabilityProbe,
    private val wifiProbe: AndroidWifiCapabilityProbe,
) {

    /** Future 3.5 entry point: the user explicitly tapped the Turkish BLE Start button. */
    fun onUserStartBleTap() = onUserStartTap(RadioType.BLE)

    /** Future 3.5 entry point: the user explicitly tapped the Turkish BLE Stop button. */
    fun onUserStopBleTap() { orchestrator.stopBleSession() }

    /** Future 3.5 entry point: the user explicitly tapped the Turkish Wi-Fi Observe button. */
    fun onUserStartWifiTap() = onUserStartTap(RadioType.WIFI)

    /** Future 3.5 entry point: the user explicitly tapped the Turkish Wi-Fi Stop button. */
    fun onUserStopWifiTap() { orchestrator.stopWifiObservation() }

    private fun onUserStartTap(radio: RadioType) {
        val grants = permissionGateway.snapshot()
        // Runtime-permission path first: FINE is mandatory, and BLE on API 31+ also needs
        // BLUETOOTH_SCAN. Either gap means ONLY the permission dialog may happen here.
        val missingRuntime = !grants.fineLocation ||
            (radio == RadioType.BLE && !grants.bluetoothScan)
        if (missingRuntime) {
            permissionFlowController.onUserStartTap(
                if (radio == RadioType.BLE) PermissionFlowScope.BLE else PermissionFlowScope.WIFI)
            return
        }
        // Capability facts from the existing probes (sources re-gate internally too):
        val readiness = when (radio) {
            RadioType.BLE -> ObservationReadinessModel.evaluate(bleProbe.currentSnapshot())
            RadioType.WIFI -> ObservationReadinessModel.evaluate(wifiProbe.currentSnapshot())
        }
        val authorization = StartAuthorization(
            foregroundEligible = foregroundEligible(),
            preciseLocation = grants.fineLocation,
            capabilityReady = readiness is ObservationReadiness.Ready,
            capabilityIssue = (readiness as? ObservationReadiness.Blocked)
                ?.let { blockerIssue(it.blockers) },
        )
        when (radio) {
            RadioType.BLE -> orchestrator.startBleSession(authorization)
            RadioType.WIFI -> orchestrator.startWifiObservation(authorization)
        }
    }

    private fun blockerIssue(blockers: Set<ReadinessBlocker>): SessionIssueKey? {
        val blocker = blockers.firstOrNull() ?: return null
        return when (blocker) {
            is ReadinessBlocker.PermissionMissing -> SessionIssueKey.PERMISSION_DENIED
            is ReadinessBlocker.RadioDisabled -> when (blocker.radio) {
                RadioType.BLE -> SessionIssueKey.BLUETOOTH_OFF
                RadioType.WIFI -> SessionIssueKey.WIFI_OFF
            }
            is ReadinessBlocker.LocationServicesDisabled -> SessionIssueKey.LOCATION_SERVICES_OFF
            is ReadinessBlocker.HardwareUnavailable -> SessionIssueKey.HARDWARE_UNSUPPORTED
        }
    }
}
