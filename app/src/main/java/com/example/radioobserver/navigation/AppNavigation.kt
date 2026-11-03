package com.example.radioobserver.navigation

/**
 * Post-roadmap UI iteration (developer-approved, 2026-10-23): PURE, host-testable
 * navigation policy for the four-tab Material 3 bottom bar
 * (Gözlem, Gözlemler, Geçmiş, Ayarlar). No android.* imports — MainActivity applies
 * these transitions to its own Compose state; the policy itself is JVM-tested.
 *
 * SAFETY INVARIANT (JVM-tested): a tab switch NEVER produces a detail destination,
 * NEVER reopens the previous detail and dispatches no scanning/permission effect —
 * by construction the returned destination is only ever a plain tab screen.
 */
sealed interface AppDestination {
    val tab: AppTab

    data class TabScreen(override val tab: AppTab) : AppDestination

    /** Full-screen detail overlays; hide the bottom bar (see [isDetail]). */
    data class ObservationDetail(val rowId: String, override val tab: AppTab = AppTab.FEED) : AppDestination
    data class HistoryDetail(val recordId: String, override val tab: AppTab = AppTab.HISTORY) : AppDestination

    /**
     * WORKSTREAM 03B: classification-catalog management screen, owned by the
     * Ayarlar tab. Pure management destination — opening it dispatches nothing
     * (no scan/permission/session effect) and it never reopens stale details.
     */
    data class SignatureCatalog(override val tab: AppTab = AppTab.SETTINGS) : AppDestination

    /**
     * WORKSTREAM 05: named analytical group screens, owned by the Geçmiş tab.
     * PURE management destinations — opening any of them dispatches NOTHING
     * (no scan/permission/session/recording action) and never saves live
     * observations. Groups are analytical selections of ALREADY-SAVED records.
     */
    data class SessionGroups(override val tab: AppTab = AppTab.HISTORY) : AppDestination
    data class SessionGroupDetail(val groupId: String, override val tab: AppTab = AppTab.HISTORY) : AppDestination
    data class SessionGroupCompare(override val tab: AppTab = AppTab.HISTORY) : AppDestination

    /**
     * WORKSTREAM 06: explicit export screen (read-only projection of saved data).
     * Opening it dispatches NOTHING and it never reopens stale details.
     */
    data class ReportExport(
        val groupId: String? = null,
        override val tab: AppTab = AppTab.HISTORY,
    ) : AppDestination
}

enum class AppTab { OBSERVATION, FEED, HISTORY, SETTINGS }

object AppNavigation {

    /** The tab that owns the current screen (details report their parent tab). */
    fun currentTab(destination: AppDestination): AppTab = destination.tab

    /** Tab selection: deterministic, idempotent, NEVER reopens a stale detail. */
    fun onTabSelected(current: AppDestination, tab: AppTab): AppDestination =
        if (current.tab == tab && current is AppDestination.TabScreen) current
        else AppDestination.TabScreen(tab)

    /**
     * Back policy: a detail returns to its parent TAB (not to a stale feed state);
     * a non-primary tab returns to Gözlem; Gözlem hands Back to the system
     * (returns null — the app exits per the default Activity behavior).
     */
    fun onBack(current: AppDestination): AppDestination? = when {
        current is AppDestination.ObservationDetail -> AppDestination.TabScreen(AppTab.FEED)
        current is AppDestination.HistoryDetail -> AppDestination.TabScreen(AppTab.HISTORY)
        current is AppDestination.SignatureCatalog -> AppDestination.TabScreen(AppTab.SETTINGS)
        // WORKSTREAM 05: group screens walk back within the Geçmiş area.
        current is AppDestination.SessionGroups -> AppDestination.TabScreen(AppTab.HISTORY)
        current is AppDestination.SessionGroupDetail -> AppDestination.SessionGroups()
        current is AppDestination.SessionGroupCompare -> AppDestination.SessionGroups()
        current is AppDestination.ReportExport ->
            if (current.groupId == null) AppDestination.TabScreen(AppTab.HISTORY)
            else AppDestination.SessionGroupDetail(current.groupId)
        current.tab == AppTab.OBSERVATION -> null
        else -> AppDestination.TabScreen(AppTab.OBSERVATION)
    }

    /** Whether the app handles Back itself for this destination. */
    fun handlesBack(current: AppDestination): Boolean = onBack(current) != null

    /** Detail screens hide the bottom navigation bar (more room, clear focus). */
    fun showsBottomBar(current: AppDestination): Boolean =
        current is AppDestination.TabScreen
}
