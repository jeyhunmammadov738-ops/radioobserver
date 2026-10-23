package com.example.radioobserver.navigation

import com.example.radioobserver.navigation.AppDestination.HistoryDetail
import com.example.radioobserver.navigation.AppDestination.ObservationDetail
import com.example.radioobserver.navigation.AppDestination.TabScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Post-roadmap UI iteration (2026-10-23): JVM tests for the pure four-tab
 * navigation policy. Safety invariants: a tab tap NEVER yields a detail
 * destination (no session/permission/scanning effect by construction) and
 * Back is deterministic everywhere.
 */
class AppNavigationTest {

    private fun tab(tab: AppTab): AppDestination = TabScreen(tab)

    @Test
    fun `tab selection switches to the plain tab screen`() {
        val next = AppNavigation.onTabSelected(tab(AppTab.OBSERVATION), AppTab.FEED)
        assertEquals(TabScreen(AppTab.FEED), next)
    }

    @Test
    fun `tab selection is idempotent on the current tab`() {
        val current = tab(AppTab.HISTORY)
        assertEquals(current, AppNavigation.onTabSelected(current, AppTab.HISTORY))
    }

    @Test
    fun `a tab tap NEVER reopens a stale detail destination`() {
        val detail = ObservationDetail(rowId = "r-7")
        assertEquals(TabScreen(AppTab.SETTINGS), AppNavigation.onTabSelected(detail, AppTab.SETTINGS))
        val historyDetail = HistoryDetail(recordId = "h-7")
        assertEquals(TabScreen(AppTab.FEED), AppNavigation.onTabSelected(historyDetail, AppTab.FEED))
    }

    @Test
    fun `details report their owning tab`() {
        assertEquals(AppTab.FEED, AppNavigation.currentTab(ObservationDetail("r")))
        assertEquals(AppTab.HISTORY, AppNavigation.currentTab(HistoryDetail("h")))
        assertEquals(AppTab.SETTINGS, AppNavigation.currentTab(tab(AppTab.SETTINGS)))
    }

    @Test
    fun `back from an observation detail returns to the feed TAB`() {
        assertEquals(TabScreen(AppTab.FEED), AppNavigation.onBack(ObservationDetail("r")))
        assertTrue(AppNavigation.handlesBack(ObservationDetail("r")))
    }

    @Test
    fun `back from a history detail returns to the history TAB`() {
        assertEquals(TabScreen(AppTab.HISTORY), AppNavigation.onBack(HistoryDetail("h")))
        assertTrue(AppNavigation.handlesBack(HistoryDetail("h")))
    }

    @Test
    fun `back from a non-primary tab returns to the observation tab`() {
        for (t in listOf(AppTab.FEED, AppTab.HISTORY, AppTab.SETTINGS)) {
            assertEquals(TabScreen(AppTab.OBSERVATION), AppNavigation.onBack(tab(t)))
            assertTrue(AppNavigation.handlesBack(tab(t)))
        }
    }

    @Test
    fun `observation tab hands Back to the system`() {
        assertNull(AppNavigation.onBack(tab(AppTab.OBSERVATION)))
        assertFalse(AppNavigation.handlesBack(tab(AppTab.OBSERVATION)))
    }

    @Test
    fun `bottom bar shows only on tab screens, never on details`() {
        assertTrue(AppNavigation.showsBottomBar(tab(AppTab.OBSERVATION)))
        assertTrue(AppNavigation.showsBottomBar(tab(AppTab.SETTINGS)))
        assertFalse(AppNavigation.showsBottomBar(ObservationDetail("r")))
        assertFalse(AppNavigation.showsBottomBar(HistoryDetail("h")))
    }
}
