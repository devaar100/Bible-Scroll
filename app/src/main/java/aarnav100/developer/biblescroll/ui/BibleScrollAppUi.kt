package aarnav100.developer.biblescroll.ui

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

    // The feed draws its reading background under the status bar; everything else is app chrome.
    val onFeed = currentDestination?.hasRoute(BibleRoute::class) ?: true
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
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.hasRoute(destination.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
