package com.example.radioobserver.ui.shell

import com.example.radioobserver.navigation.AppDestination
import com.example.radioobserver.navigation.AppTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UX-02 host tests: the shell is a PURE projection of the current destination. These
 * assertions are behavioural (values and rules), not textual greps.
 */
class ShellPolicyTest {

    @Test
    fun `A exactly four tabs in the existing order`() {
        assertEquals(
            listOf(AppTab.OBSERVATION, AppTab.FEED, AppTab.HISTORY, AppTab.SETTINGS),
            ShellPolicy.TAB_ORDER,
        )
        assertEquals(4, ShellPolicy.TAB_ORDER.size)
    }

    @Test
    fun `B every primary tab shows the shell top bar and the bottom bar`() {
        AppTab.values().forEach { tab ->
            val chrome = ShellPolicy.chromeFor(AppDestination.TabScreen(tab))
            assertTrue("topBar for $tab", chrome.topBarVisible)
            assertTrue("bottomBar for $tab", chrome.bottomBarVisible)
            assertFalse("no back on primary $tab", chrome.backVisible)
            assertEquals(tab, chrome.tab)
            assertEquals(InsetsOwner.ROOT_SCAFFOLD, chrome.insetsOwner)
        }
    }

    @Test
    fun `C secondary destinations never stack a second bar and keep back`() {
        val secondary = listOf(
            AppDestination.ObservationDetail("r1"),
            AppDestination.HistoryDetail("h1"),
            AppDestination.SignatureCatalog(),
            AppDestination.SessionGroups(),
            AppDestination.SessionGroupDetail("G-0001"),
            AppDestination.SessionGroupCompare(),
            AppDestination.ReportExport(),
        )
        secondary.forEach { dest ->
            val chrome = ShellPolicy.chromeFor(dest)
            assertTrue("back for $dest", chrome.backVisible)
            assertFalse("no stacked top bar for $dest", chrome.topBarVisible)
            assertFalse("no bottom bar for $dest", chrome.bottomBarVisible)
            assertEquals(InsetsOwner.ROOT_SCAFFOLD, chrome.insetsOwner)
        }
    }

    @Test
    fun `D each tab maps to its own title slot`() {
        assertEquals(ShellTitle.OBSERVATION, ShellPolicy.titleForTab(AppTab.OBSERVATION))
        assertEquals(ShellTitle.FEED, ShellPolicy.titleForTab(AppTab.FEED))
        assertEquals(ShellTitle.HISTORY, ShellPolicy.titleForTab(AppTab.HISTORY))
        assertEquals(ShellTitle.SETTINGS, ShellPolicy.titleForTab(AppTab.SETTINGS))
    }

    @Test
    fun `E group and report screens stay owned by History`() {
        listOf(
            AppDestination.SessionGroups(),
            AppDestination.SessionGroupDetail("G-1"),
            AppDestination.SessionGroupCompare(),
            AppDestination.ReportExport(),
        ).forEach { dest ->
            assertEquals(AppTab.HISTORY, ShellPolicy.chromeFor(dest).tab)
        }
    }

    @Test
    fun `F chrome is deterministic and pure for the same destination`() {
        val dest = AppDestination.TabScreen(AppTab.HISTORY)
        assertEquals(ShellPolicy.chromeFor(dest), ShellPolicy.chromeFor(dest))
    }

    @Test
    fun `G shell model has no developer diagnostics surface`() {
        assertEquals(4, ShellPolicy.TAB_ORDER.size)
        assertFalse(ShellPolicy.TAB_ORDER.any { it.name.contains("DIAG", ignoreCase = true) })
        ShellTitle.values().forEach { assertFalse(it.name.contains("DIAG")) }
    }
}
