package com.example.radioobserver.ui.alerts

import com.example.radioobserver.core.radio.alerts.AlertTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTriggerGroupsTest {
    @Test
    fun `every engine trigger is offered exactly once`() {
        val offered = AlertTriggerGroups.groups().flatMap { AlertTriggerGroups.triggers(it) }
        assertEquals(AlertTrigger.entries.size, offered.size)
        assertEquals(offered.size, offered.toSet().size)
        assertEquals(AlertTrigger.entries.toSet(), offered.toSet())
    }

    @Test
    fun `seven triggers are grouped in three readable groups`() {
        assertEquals(7, AlertTriggerGroups.totalTriggers())
        assertEquals(3, AlertTriggerGroups.groups().size)
    }

    @Test
    fun `source group holds the two source triggers`() {
        assertEquals(
            listOf(AlertTrigger.NEW_FRESH_BLE, AlertTrigger.NEW_FRESH_WIFI),
            AlertTriggerGroups.triggers(AlertTriggerGroup.SOURCE),
        )
    }

    @Test
    fun `signal group holds the three signal categories`() {
        assertEquals(
            listOf(
                AlertTrigger.SIGNAL_IMMEDIATE,
                AlertTrigger.SIGNAL_NEAR,
                AlertTrigger.SIGNAL_FAR,
            ),
            AlertTriggerGroups.triggers(AlertTriggerGroup.SIGNAL),
        )
    }

    @Test
    fun `manufacturer group holds both hint triggers`() {
        assertEquals(
            listOf(AlertTrigger.MANUFACTURER_KNOWN, AlertTrigger.MANUFACTURER_UNKNOWN),
            AlertTriggerGroups.triggers(AlertTriggerGroup.MANUFACTURER),
        )
    }

    @Test
    fun `selection summary counts only that group`() {
        val selected = setOf(AlertTrigger.NEW_FRESH_BLE, AlertTrigger.SIGNAL_FAR)
        assertEquals(1, AlertTriggerGroups.selectedIn(AlertTriggerGroup.SOURCE, selected))
        assertEquals(1, AlertTriggerGroups.selectedIn(AlertTriggerGroup.SIGNAL, selected))
        assertEquals(0, AlertTriggerGroups.selectedIn(AlertTriggerGroup.MANUFACTURER, selected))
    }

    @Test
    fun `defaults stay off`() {
        val none = emptySet<AlertTrigger>()
        AlertTriggerGroups.groups().forEach {
            assertEquals(0, AlertTriggerGroups.selectedIn(it, none))
        }
        assertTrue(AlertTriggerGroups.totalTriggers() > 0)
    }
}
