package aarnav100.developer.biblescroll.ui

import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.feature.bible.FeedTopBar
import aarnav100.developer.biblescroll.feature.onboarding.FeatureTourStep
import aarnav100.developer.biblescroll.feature.onboarding.Goal
import aarnav100.developer.biblescroll.feature.onboarding.GoalResponseStep
import aarnav100.developer.biblescroll.feature.onboarding.GoalStep
import aarnav100.developer.biblescroll.feature.onboarding.OnboardingUiState
import aarnav100.developer.biblescroll.feature.onboarding.PermissionStep
import aarnav100.developer.biblescroll.feature.onboarding.PlanStep
import aarnav100.developer.biblescroll.feature.onboarding.TimingStep
import aarnav100.developer.biblescroll.feature.onboarding.TourVerses
import aarnav100.developer.biblescroll.feature.onboarding.WelcomeStep
import aarnav100.developer.biblescroll.feature.settings.DataBackupContent
import aarnav100.developer.biblescroll.feature.settings.SettingsScreen
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime
import java.util.Locale

/**
 * Renders the text-heaviest screens in the en-XA pseudo-locale (every string accented and padded ~30-40% longer)
 * at 320 dp wide, the narrowest common phone, and saves PNGs for review:
 *   adb pull /sdcard/Android/data/aarnav100.developer.biblescroll/files/pseudolocale
 * Needs debug resources (isPseudoLocalesEnabled) and the screen on. Renders composables only: no database, no
 * ViewModels, nothing on the phone changes.
 */
@RunWith(AndroidJUnit4::class)
class PseudoLocaleScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(target.getExternalFilesDir(null), "pseudolocale").apply { mkdirs() }

    private fun shot(name: String, heightDp: Int = 720, content: @Composable () -> Unit) {
        val config = Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag("en-XA")) }
        val pseudo = target.createConfigurationContext(config)
        assertTrue("en-XA resources missing: is isPseudoLocalesEnabled on for debug?", pseudo.getString(aarnav100.developer.biblescroll.R.string.settings_title).contains('['))
        rule.setContent {
            CompositionLocalProvider(
                LocalContext provides pseudo,
                LocalConfiguration provides config,
                LocalResources provides pseudo.resources,
            ) {
                BibleScrollTheme(AppSettings()) {
                    Box(Modifier.size(320.dp, heightDp.dp)) { Surface { content() } }
                }
            }
        }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun settings() = shot("settings") { SettingsScreen({}, {}, {}, {}, {}) }
    @Test fun dataBackup() = shot("data_backup") { DataBackupContent(busy = false, onBack = {}, onExport = {}, onImport = {}) }
    @Test fun dataError() = shot("data_error") { DataErrorScreen(checking = false, onRetry = {}, onSaveCopy = {}, savedResults = emptyFlow()) }
    @Test fun welcome() = shot("onboarding_welcome") { WelcomeStep(onGetStarted = {}) }
    @Test fun goal() = shot("onboarding_goal") { GoalStep(selected = Goal.entries.first(), onSelect = {}, onBack = {}, onContinue = {}) }
    @Test fun timing() = shot("onboarding_timing") { TimingStep(selected = null, onSelect = {}, onBack = {}, onContinue = {}) }
    @Test fun goalResponse() = shot("onboarding_goal_response") { GoalResponseStep(goal = Goal.entries.first(), verse = null, onBack = {}, onContinue = {}) }
    @Test fun plan() = shot("onboarding_plan") {
        PlanStep(OnboardingUiState(goal = Goal.entries.first()), onBack = {}, onToggleReminder = {}, onTimeChange = {}, onContinue = {})
    }
    @Test fun permission() = shot("onboarding_permission") {
        PermissionStep(time = LocalTime.of(8, 0), declined = true, onBack = {}, onAllow = {}, onNotNow = {}, onFinish = {})
    }
    @Test fun featureTour() = shot("onboarding_tour") { FeatureTourStep(TourVerses(), onBack = {}, onSkip = {}, onContinue = {}) }

    @Test fun feedTopBarAndTabs() = shot("feed_top_bar_and_tabs", heightDp = 240) {
        Column {
            FeedTopBar(
                likedFilterOn = false, onToggleLikedFilter = {}, bookmarked = false, onToggleBookmark = {},
                selectedTranslationId = "WEB", onOpenTranslationPicker = {}, chapterLabel = "1 Thessalonians 5",
                onOpenBookPicker = {},
            )
            Spacer(Modifier.weight(1f))
            AppNavigationBar(isSelected = { it.ordinal == 3 }, onSelect = {})
        }
    }
}
