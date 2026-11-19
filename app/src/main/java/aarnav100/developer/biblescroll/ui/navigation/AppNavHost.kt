package aarnav100.developer.biblescroll.ui.navigation

import aarnav100.developer.biblescroll.feature.bible.BibleScreen
import aarnav100.developer.biblescroll.feature.community.CommunityScreen
import aarnav100.developer.biblescroll.feature.discover.DeepStudyScreen
import aarnav100.developer.biblescroll.feature.devotionals.DevotionalArchiveScreen
import aarnav100.developer.biblescroll.feature.devotionals.DevotionalDetailScreen
import aarnav100.developer.biblescroll.feature.devotionals.DevotionalReaderScreen
import aarnav100.developer.biblescroll.feature.discover.DiscoverScreen
import aarnav100.developer.biblescroll.feature.home.HomeScreen
import aarnav100.developer.biblescroll.feature.library.LibraryListScreen
import aarnav100.developer.biblescroll.feature.library.LibraryScreen
import aarnav100.developer.biblescroll.feature.plans.CreatePlanScreen
import aarnav100.developer.biblescroll.feature.plans.PlanCompleteScreen
import aarnav100.developer.biblescroll.feature.plans.PlanDaysScreen
import aarnav100.developer.biblescroll.feature.plans.PlanDetailScreen
import aarnav100.developer.biblescroll.feature.plans.PlanReaderScreen
import aarnav100.developer.biblescroll.feature.plans.PlansBrowserScreen
import aarnav100.developer.biblescroll.feature.settings.AppearanceScreen
import aarnav100.developer.biblescroll.feature.settings.FontSizeScreen
import aarnav100.developer.biblescroll.feature.settings.GeneralSettingsScreen
import aarnav100.developer.biblescroll.feature.settings.NotificationsScreen
import aarnav100.developer.biblescroll.feature.settings.SettingsScreen
import aarnav100.developer.biblescroll.feature.settings.DataBackupScreen
import aarnav100.developer.biblescroll.feature.settings.HelpScreen
import aarnav100.developer.biblescroll.feature.settings.LegalScreen
import aarnav100.developer.biblescroll.feature.settings.WidgetGuideScreen
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
@Serializable data object DataBackupRoute
@Serializable data object HelpRoute
@Serializable data object LegalRoute
@Serializable data object WidgetGuideRoute

/** H3 Library and its lists (H4-H7), pushed over the Home tab. screen = LibraryScreen name. */
@Serializable data object LibraryRoute
@Serializable data class LibraryListRoute(val screen: String)

/** H8 plans browser, H9 plan detail and H13 create plan, pushed over the Home tab. */
@Serializable data object PlansRoute
@Serializable data class PlanDetailRoute(val planId: String)
@Serializable data object CreatePlanRoute

/** H11 plan reader (immersive, no tab bar), H12 days & notes, and the completion screen. */
@Serializable data class PlanReaderRoute(val enrollmentId: String, val dayNumber: Int)
@Serializable data class PlanDaysRoute(val enrollmentId: String)
@Serializable data class PlanCompleteRoute(val enrollmentId: String)

/** H15 devotional detail and H16 reader (date = yyyy-MM-dd), H17 archive. Pushed over the Home tab. */
@Serializable data class DevotionalRoute(val date: String)
@Serializable data class DevotionalReaderRoute(val date: String)
@Serializable data object DevotionalArchiveRoute

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
                onOpenPlans = { navController.navigate(PlansRoute) },
                onOpenDevotional = { navController.navigate(DevotionalRoute(it)) },
                onOpenDevotionalArchive = { navController.navigate(DevotionalArchiveRoute) },
                onOpenPlanDay = { id, day -> navController.navigate(PlanReaderRoute(id, day)) },
            )
        }
        composable<DevotionalRoute> {
            DevotionalDetailScreen(onBack = back, onStart = { navController.navigate(DevotionalReaderRoute(it)) })
        }
        composable<DevotionalReaderRoute> { DevotionalReaderScreen(onClose = back) }
        composable<DevotionalArchiveRoute> {
            DevotionalArchiveScreen(onBack = back, onOpen = { navController.navigate(DevotionalRoute(it)) })
        }
        composable<PlansRoute> {
            PlansBrowserScreen(
                onBack = back,
                onOpenPlan = { navController.navigate(PlanDetailRoute(it)) },
                onBuildYourOwn = { navController.navigate(CreatePlanRoute) },
            )
        }
        composable<PlanDetailRoute> {
            PlanDetailScreen(
                onBack = back,
                onReadDay = { id, day -> navController.navigate(PlanReaderRoute(id, day)) },
                onOpenDays = { id -> navController.navigate(PlanDaysRoute(id)) },
            )
        }
        composable<PlanReaderRoute> {
            PlanReaderScreen(
                onClose = back,
                // "Next day" replaces this reader, so Back returns to where the reading started.
                onOpenDay = { id, day ->
                    navController.navigate(PlanReaderRoute(id, day)) { popUpTo<PlanReaderRoute> { inclusive = true } }
                },
                onCompleted = { id ->
                    navController.navigate(PlanCompleteRoute(id)) { popUpTo<PlanReaderRoute> { inclusive = true } }
                },
            )
        }
        composable<PlanDaysRoute> {
            PlanDaysScreen(onBack = back, onOpenDay = { id, day -> navController.navigate(PlanReaderRoute(id, day)) })
        }
        composable<PlanCompleteRoute> {
            // "Back to the plan": the plan's page (H9) if it's underneath, wherever the reading started from.
            PlanCompleteScreen(onDone = { navController.popBackStack<PlanDetailRoute>(inclusive = false) || navController.popBackStack() })
        }
        composable<CreatePlanRoute> {
            CreatePlanScreen(
                onClose = back,
                // The new plan's page replaces the create flow, so Back returns to the browser.
                onCreated = { id -> navController.navigate(PlanDetailRoute(id)) { popUpTo<CreatePlanRoute> { inclusive = true } } },
            )
        }
        composable<LibraryRoute> {
            LibraryScreen(onBack = back, onOpen = { navController.navigate(LibraryListRoute(it.name)) })
        }
        composable<LibraryListRoute> {
            LibraryListScreen(
                onBack = back,
                onOpenStudy = { ref -> navController.navigate(DeepStudyRoute(ref.bookId, ref.chapter, ref.verse)) },
                onOpenDataBackup = { navController.navigate(DataBackupRoute) },
            )
        }
        composable<BibleRoute> { BibleScreen() }

        composable<SettingsRoute> {
            SettingsScreen(
                onBack = back,
                onOpenGeneral = { navController.navigate(GeneralSettingsRoute) },
                onOpenNotifications = { navController.navigate(NotificationsRoute) },
                onOpenStorage = { navController.navigate(StorageRoute) },
                onOpenDataBackup = { navController.navigate(DataBackupRoute) },
                onOpenHelp = { navController.navigate(HelpRoute) },
                onOpenWidgetGuide = { navController.navigate(WidgetGuideRoute) },
                onOpenLegal = { navController.navigate(LegalRoute) },
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
        composable<DataBackupRoute> { DataBackupScreen(onBack = back) }
        composable<HelpRoute> { HelpScreen(onBack = back, onOpenPrivacy = { navController.navigate(LegalRoute) }) }
        composable<LegalRoute> { LegalScreen(onBack = back) }
        composable<WidgetGuideRoute> { WidgetGuideScreen(onBack = back) }
    }
}
