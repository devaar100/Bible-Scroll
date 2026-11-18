package aarnav100.developer.biblescroll.ui.navigation

import aarnav100.developer.biblescroll.feature.bible.BibleScreen
import aarnav100.developer.biblescroll.feature.community.CommunityScreen
import aarnav100.developer.biblescroll.feature.discover.DeepStudyScreen
import aarnav100.developer.biblescroll.feature.discover.DiscoverScreen
import aarnav100.developer.biblescroll.feature.home.HomeScreen
import aarnav100.developer.biblescroll.feature.library.LibraryListScreen
import aarnav100.developer.biblescroll.feature.library.LibraryScreen
import aarnav100.developer.biblescroll.feature.settings.AppearanceScreen
import aarnav100.developer.biblescroll.feature.settings.FontSizeScreen
import aarnav100.developer.biblescroll.feature.settings.GeneralSettingsScreen
import aarnav100.developer.biblescroll.feature.settings.NotificationsScreen
import aarnav100.developer.biblescroll.feature.settings.SettingsScreen
import aarnav100.developer.biblescroll.feature.settings.StorageScreen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

// S1-S4. Pushed on top of the Home tab.
@Serializable data object SettingsRoute
@Serializable data object GeneralSettingsRoute
@Serializable data object AppearanceRoute
@Serializable data object FontSizeRoute
@Serializable data object NotificationsRoute
@Serializable data object StorageRoute

/** H3 Library and its lists (H4-H7), pushed over the Home tab. screen = LibraryScreen name. */
@Serializable data object LibraryRoute
@Serializable data class LibraryListRoute(val screen: String)

/** D2 Deep study, pushed full-screen over the Discover tab. */
@Serializable data class DeepStudyRoute(val bookId: Int, val chapter: Int, val verse: Int)

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = BibleRoute,
        modifier = modifier,
    ) {
        composable<CommunityRoute> { CommunityScreen() }
        composable<DiscoverRoute> {
            DiscoverScreen(onOpenStudy = { ref -> navController.navigate(DeepStudyRoute(ref.bookId, ref.chapter, ref.verse)) })
        }
        composable<DeepStudyRoute> { DeepStudyScreen(onLeave = back) }
        composable<HomeRoute> {
            HomeScreen(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenLibrary = { navController.navigate(LibraryRoute) },
            )
        }
        composable<LibraryRoute> {
            LibraryScreen(onBack = back, onOpen = { navController.navigate(LibraryListRoute(it.name)) })
        }
        composable<LibraryListRoute> {
            LibraryListScreen(
                onBack = back,
                onOpenStudy = { ref -> navController.navigate(DeepStudyRoute(ref.bookId, ref.chapter, ref.verse)) },
            )
        }
        composable<BibleRoute> { BibleScreen() }

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = back,
                onOpenGeneral = { navController.navigate(GeneralSettingsRoute) },
                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                onOpenStorage = { navController.navigate(StorageRoute) },
            )
        }
        composable<GeneralSettingsRoute> {
            GeneralSettingsScreen(
                onBack = back,
                onOpenAppearance = { navController.navigate(AppearanceRoute) },
                onOpenFontSize = { navController.navigate(FontSizeRoute) },
            )
        }
        composable<AppearanceRoute> { AppearanceScreen(onBack = back) }
        composable<FontSizeRoute> { FontSizeScreen(onBack = back) }
        composable<NotificationsRoute> { NotificationsScreen(onBack = back) }
        composable<StorageRoute> { StorageScreen(onBack = back) }
    }
}
