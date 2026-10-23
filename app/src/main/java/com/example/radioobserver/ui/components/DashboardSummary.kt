package com.example.radioobserver.ui.components

import com.example.radioobserver.core.radio.ui.RadioSessionUiStatus

/**
 * PREMIUM UI/UX V2 (2026-10-23): pure, host-tested summary values for the Gözlem
 * dashboard header strip. TRUTHFUL ONLY: the active-source count and live record
 * count come exclusively from the orchestrator's sanitized state; nothing here
 * starts/stops a session, requests permissions or fabricates activity.
 */
object DashboardSummary {

    /** How many sources are currently in the OBSERVING state (0..2). */
    fun activeSourceCount(bleStatus: RadioSessionUiStatus, wifiStatus: RadioSessionUiStatus): Int {
        var count = 0
        if (bleStatus == RadioSessionUiStatus.OBSERVING) count += 1
        if (wifiStatus == RadioSessionUiStatus.OBSERVING) count += 1
        return count
    }

    /** True only when at least one source is OBSERVING. */
    fun anyActive(bleStatus: RadioSessionUiStatus, wifiStatus: RadioSessionUiStatus): Boolean =
        activeSourceCount(bleStatus, wifiStatus) > 0
}
