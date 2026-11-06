package com.example.radioobserver.ui.shell

import com.example.radioobserver.navigation.AppDestination
import com.example.radioobserver.navigation.AppTab

/**
 * UX-02 (2026-11-06): PURE global-shell presentation model. No Compose, no android.*,
 * no I/O and no navigation side effects. The shell only mirrors the CURRENT
 * destination; it never changes it, never starts or stops a session, never requests a
 * permission and never writes History.
 */
enum class ShellTitle {
    OBSERVATION,
    FEED,
    HISTORY,
    SETTINGS,
    LIVE_DETAIL,
    HISTORY_DETAIL,
    GROUPS,
    GROUP_DETAIL,
    GROUP_COMPARE,
    REPORT,
    CATALOG,
}

/** Who owns the window insets for a destination. */
enum class InsetsOwner {
    /** The single root Scaffold (top bar + bottom bar) owns top and bottom insets. */
    ROOT_SCAFFOLD,
}

/**
 * One authoritative chrome description for the current destination.
 *
 * @param backVisible the screen offers a back affordance
 * @param topBarVisible primary tabs draw the coordinated shell top bar; secondary
 *   destinations keep their own in-content header, so a second bar is NOT stacked
 * @param bottomBarVisible mirrors the existing navigation rule
 */
data class ShellChrome(
    val title: ShellTitle,
    val tab: AppTab,
    val backVisible: Boolean,
    val topBarVisible: Boolean,
    val bottomBarVisible: Boolean,
    val insetsOwner: InsetsOwner = InsetsOwner.ROOT_SCAFFOLD,
)

object ShellPolicy {

    /**
     * Deterministic chrome for a destination. Secondary destinations already provide
     * their own header and back action inside the content, so the shell does not stack
     * a second bar (prevents duplicate titles and double inset padding).
     */
    fun chromeFor(destination: AppDestination): ShellChrome = when (destination) {
        is AppDestination.TabScreen -> ShellChrome(
            title = titleForTab(destination.tab),
            tab = destination.tab,
            backVisible = false,
            topBarVisible = true,
            bottomBarVisible = true,
        )
        is AppDestination.ObservationDetail -> secondary(ShellTitle.LIVE_DETAIL, AppTab.FEED)
        is AppDestination.HistoryDetail -> secondary(ShellTitle.HISTORY_DETAIL, AppTab.HISTORY)
        is AppDestination.SignatureCatalog -> secondary(ShellTitle.CATALOG, AppTab.SETTINGS)
        is AppDestination.SessionGroups -> secondary(ShellTitle.GROUPS, AppTab.HISTORY)
        is AppDestination.SessionGroupDetail -> secondary(ShellTitle.GROUP_DETAIL, AppTab.HISTORY)
        is AppDestination.SessionGroupCompare -> secondary(ShellTitle.GROUP_COMPARE, AppTab.HISTORY)
        is AppDestination.ReportExport -> secondary(ShellTitle.REPORT, AppTab.HISTORY)
    }

    private fun secondary(title: ShellTitle, tab: AppTab) = ShellChrome(
        title = title,
        tab = tab,
        backVisible = true,
        topBarVisible = false,
        bottomBarVisible = false,
    )

    fun titleForTab(tab: AppTab): ShellTitle = when (tab) {
        AppTab.OBSERVATION -> ShellTitle.OBSERVATION
        AppTab.FEED -> ShellTitle.FEED
        AppTab.HISTORY -> ShellTitle.HISTORY
        AppTab.SETTINGS -> ShellTitle.SETTINGS
    }

    /**
     * The four tabs in their fixed order. Exactly four; FieldDiag-04 is never a tab and
     * stays reachable only through the hidden developer entry.
     */
    val TAB_ORDER: List<AppTab> = listOf(
        AppTab.OBSERVATION,
        AppTab.FEED,
        AppTab.HISTORY,
        AppTab.SETTINGS,
    )
}
