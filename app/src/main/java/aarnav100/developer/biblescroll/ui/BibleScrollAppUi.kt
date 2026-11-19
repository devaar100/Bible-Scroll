package aarnav100.developer.biblescroll.ui

import aarnav100.developer.biblescroll.ui.navigation.PendingPlanLink
import aarnav100.developer.biblescroll.ui.navigation.PlanDetailRoute
import aarnav100.developer.biblescroll.ui.navigation.PlanReaderRoute
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.ui.navigation.AppNavHost
import aarnav100.developer.biblescroll.ui.navigation.BibleRoute
import aarnav100.developer.biblescroll.ui.navigation.TopLevelDestination
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.StateFlow

/**
 * [pendingVerse]: a verse link to open (from the widget); switches to the Bible tab, which consumes it.
 * [onSystemBarsDark]: called with whether the status bar sits on a dark surface.
 */
@Composable
fun BibleScrollAppUi(
    pendingVerse: StateFlow<VerseRef?>,
    pendingPlan: PendingPlanLink,
    appDark: Boolean,
    onSystemBarsDark: (Boolean) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateTo(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val linked by pendingVerse.collectAsStateWithLifecycle()
    LaunchedEffect(linked) {
        if (linked != null) navigateTo(TopLevelDestination.BIBLE)
    }
    // Plan reminder taps: Home tab, then the plan's page (H9) on top.
    val linkedPlan by pendingPlan.planId.collectAsStateWithLifecycle()
    LaunchedEffect(linkedPlan) {
        val planId = linkedPlan ?: return@LaunchedEffect
        navigateTo(TopLevelDestination.HOME)
        navController.navigate(PlanDetailRoute(planId))
        pendingPlan.consume(planId)
    }

    // The feed draws its reading background under the status bar; everything else is app chrome.
    val onFeed = currentDestination?.let { it.hasRoute(BibleRoute::class) || it.hasRoute(PlanReaderRoute::class) } ?: true
    val statusBarDark = if (onFeed) BibleScrollTheme.readingColors.isDark else appDark
    val reportBars by rememberUpdatedState(onSystemBarsDark)
    LaunchedEffect(statusBarDark, appDark) { reportBars(statusBarDark) }

    // Settings screens are pushed full-screen, without the tab bar.
    val onTopLevel = currentDestination == null ||
        TopLevelDestination.entries.any { dest -> currentDestination.hierarchy.any { it.hasRoute(dest.route::class) } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // On the feed, the strip under the status bar continues the reading background.
        containerColor = if (onFeed) BibleScrollTheme.readingColors.background else MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTopLevel) {
                AppNavigationBar(
                    isSelected = { destination ->
                        currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    },
                    onSelect = ::navigateTo,
                )
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** The four tabs. Labels stay on one line (ellipsised if a language makes them too long; screen readers get the full text). */
@Composable
internal fun AppNavigationBar(isSelected: (TopLevelDestination) -> Boolean, onSelect: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = isSelected(destination)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
