package com.example.radioobserver.ui.alerts

import com.example.radioobserver.core.radio.alerts.AlertTrigger

/** UX-08: presentation grouping of the SEVEN existing alert triggers. */
enum class AlertTriggerGroup { SOURCE, SIGNAL, MANUFACTURER }

/**
 * PURE grouping of the existing alert triggers for readable multi-select UI. Every
 * trigger appears exactly once and no new trigger is invented; the engine, defaults
 * (all OFF) and session lifetime are untouched.
 */
object AlertTriggerGroups {
    private val byGroup: Map<AlertTriggerGroup, List<AlertTrigger>> = mapOf(
        AlertTriggerGroup.SOURCE to listOf(
            AlertTrigger.NEW_FRESH_BLE,
            AlertTrigger.NEW_FRESH_WIFI,
        ),
        AlertTriggerGroup.SIGNAL to listOf(
            AlertTrigger.SIGNAL_IMMEDIATE,
            AlertTrigger.SIGNAL_NEAR,
            AlertTrigger.SIGNAL_FAR,
        ),
        AlertTriggerGroup.MANUFACTURER to listOf(
            AlertTrigger.MANUFACTURER_KNOWN,
            AlertTrigger.MANUFACTURER_UNKNOWN,
        ),
    )

    fun groups(): List<AlertTriggerGroup> = AlertTriggerGroup.entries.toList()

    fun triggers(group: AlertTriggerGroup): List<AlertTrigger> = byGroup[group].orEmpty()

    /** Total number of triggers the UI offers (must equal the engine's enum size). */
    fun totalTriggers(): Int = byGroup.values.sumOf { it.size }

    /** How many triggers of [group] are currently selected. */
    fun selectedIn(group: AlertTriggerGroup, selected: Set<AlertTrigger>): Int =
        triggers(group).count { it in selected }
}
