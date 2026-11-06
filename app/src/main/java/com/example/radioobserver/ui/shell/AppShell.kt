package com.example.radioobserver.ui.shell

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.radioobserver.R
import com.example.radioobserver.navigation.AppTab
import com.example.radioobserver.ui.components.Dimensions

/**
 * UX-02 global shell presentation. Stateless: the shell renders the destination title,
 * the four fixed tabs and a back affordance, and reports taps through caller callbacks.
 * It never starts a session, requests a permission, writes History or exposes the
 * hidden developer panel.
 */

/** Turkish title for a shell title slot (all copy comes from the existing string table). */
@Composable
fun shellTitleText(title: ShellTitle): String = stringResource(
    when (title) {
        ShellTitle.OBSERVATION -> R.string.control_title
        ShellTitle.FEED -> R.string.feed_title
        ShellTitle.HISTORY -> R.string.history_title
        ShellTitle.SETTINGS -> R.string.settings_title
        ShellTitle.LIVE_DETAIL -> R.string.detail_title
        ShellTitle.HISTORY_DETAIL -> R.string.history_detail_title
        ShellTitle.GROUPS -> R.string.groups_title
        ShellTitle.GROUP_DETAIL -> R.string.group_detail_title
        ShellTitle.GROUP_COMPARE -> R.string.group_compare_title
        ShellTitle.REPORT -> R.string.report_title
        ShellTitle.CATALOG -> R.string.catalog_title
    },
)

/**
 * Coordinated Control Deck top app bar for primary tabs. A single line, no elevation
 * theatre, title truncated gracefully for long Turkish titles on narrow screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckTopBar(
    title: ShellTitle,
    modifier: Modifier = Modifier,
    backVisible: Boolean = false,
    onBack: () -> Unit = {},
) {
    val label = shellTitleText(title)
    TopAppBar(
        title = {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        },
        navigationIcon = {
            if (backVisible) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = Dimensions.touchTarget),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.shell_back),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier,
    )
}

/** Icon for a tab; one consistent family, sized by the caller. */
fun tabIcon(tab: AppTab) = when (tab) {
    AppTab.OBSERVATION -> Icons.Filled.Home
    AppTab.FEED -> Icons.Filled.List
    AppTab.HISTORY -> Icons.Filled.DateRange
    AppTab.SETTINGS -> Icons.Filled.Settings
}

/** Turkish label for a tab (existing strings, no new copy). */
@Composable
fun tabLabel(tab: AppTab): String = stringResource(
    when (tab) {
        AppTab.OBSERVATION -> R.string.tab_observation
        AppTab.FEED -> R.string.tab_feed
        AppTab.HISTORY -> R.string.tab_history
        AppTab.SETTINGS -> R.string.tab_settings
    },
)

/**
 * Four-tab Control Deck bottom bar. Selection is conveyed by the Material 3 indicator
 * AND the label/semantics state, never by colour alone. A tap only reports the tab.
 */
@Composable
fun DeckBottomBar(
    currentTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        ShellPolicy.TAB_ORDER.forEach { tab ->
            NavigationBarItem(
                selected = tab == currentTab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = tabIcon(tab),
                        contentDescription = null,
                        modifier = Modifier.semantics {
                            contentDescription = ""
                        },
                    )
                },
                label = {
                    Text(
                        text = tabLabel(tab),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                alwaysShowLabel = true,
                modifier = Modifier.heightIn(min = Dimensions.touchTarget),
            )
        }
    }
}

