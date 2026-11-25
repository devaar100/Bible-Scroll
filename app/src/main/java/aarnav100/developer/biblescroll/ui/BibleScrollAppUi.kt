package aarnav100.developer.biblescroll.ui

import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import aarnav100.developer.biblescroll.ui.navigation.DeepStudyRoute
import aarnav100.developer.biblescroll.ui.navigation.DiscoverRoute
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
import androidx.compose.material3.NavigationBarItemDefaults
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
    pendingVerse: PendingVerseLink,
    pendingPlan: PendingPlanLink,
    appDark: Boolean,
    onSystemBarsDark: (statusDark: Boolean, navigationDark: Boolean) -> Unit,
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

    // The tab under whatever is on screen (a study or library page is pushed on top of a tab, so this is the last tab
    // seen), and where Back on the Bible tab returns after an in-app verse link.
    var lastTab by rememberSaveable { mutableStateOf<TopLevelDestination?>(null) }
    var returnTo by rememberSaveable { mutableStateOf<TopLevelDestination?>(null) }
    LaunchedEffect(currentDestination) {
        TopLevelDestination.entries.firstOrNull { dest -> currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true }
            ?.let { lastTab = it }
    }

    val linked by pendingVerse.ref.collectAsStateWithLifecycle()
    LaunchedEffect(linked) {
        if (linked != null) {
            // A pill, "Read in context" or a library row: remember the tab it was tapped on (not the widget).
            returnTo = lastTab.takeIf { pendingVerse.fromApp && it != TopLevelDestination.BIBLE }
            navigateTo(TopLevelDestination.BIBLE)
        }
    }
    val onBibleTab = currentDestination?.hierarchy?.any { it.hasRoute(BibleRoute::class) } == true
    // Back on the Bible tab after an in-app verse link: return to that tab (with its pages, e.g. the study, restored)
    // instead of leaving the app. Sheets and other back handlers inside the feed still close first.
    BackHandler(enabled = returnTo != null && onBibleTab) {
        returnTo?.let(::navigateTo)
        returnTo = null
    }
    // Plan reminder taps: Home tab, then the plan's page (H9) on top.
    val linkedPlan by pendingPlan.planId.collectAsStateWithLifecycle()
    LaunchedEffect(linkedPlan) {
        val planId = linkedPlan ?: return@LaunchedEffect
        navigateTo(TopLevelDestination.HOME)
        navController.navigate(PlanDetailRoute(planId))
        pendingPlan.consume(planId)
    }

    // Discover is always dark (docs/ui-direction.md): the tab, and a study opened from one of its cards.
    val onDiscover = currentDestination?.hasRoute(DiscoverRoute::class) == true ||
        (currentDestination?.hasRoute(DeepStudyRoute::class) == true &&
            navController.previousBackStackEntry?.destination?.hasRoute(DiscoverRoute::class) == true)
    val chromeDark = appDark || onDiscover

    // The feed draws its reading background under the status bar; everything else is app chrome.
    val onFeed = currentDestination?.let { it.hasRoute(BibleRoute::class) || it.hasRoute(PlanReaderRoute::class) } ?: true
    val statusBarDark = if (onFeed) BibleScrollTheme.readingColors.isDark else chromeDark
    val reportBars by rememberUpdatedState(onSystemBarsDark)
    LaunchedEffect(statusBarDark, chromeDark) { reportBars(statusBarDark, chromeDark) }

    // Settings screens are pushed full-screen, without the tab bar.
    val onTopLevel = currentDestination == null ||
        TopLevelDestination.entries.any { dest -> currentDestination.hierarchy.any { it.hasRoute(dest.route::class) } }

    // Same tree either way (only the theme's values change), so switching tabs keeps every screen's state.
    val settings = BibleScrollTheme.settings
    BibleScrollTheme(if (onDiscover) settings.copy(themeMode = ThemeMode.Dark) else settings) {
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
                        onSelect = { destination ->
                            returnTo = null // picking a tab yourself ends the "back to where I was" trip
                            navigateTo(destination)
                        },
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
                // The selected tab takes the accent (docs/ui-direction.md accent rule); the others stay neutral.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
