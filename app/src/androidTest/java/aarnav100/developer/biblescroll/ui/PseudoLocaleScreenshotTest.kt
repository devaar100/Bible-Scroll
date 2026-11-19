package aarnav100.developer.biblescroll.ui

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.votd.VotdVerse
import aarnav100.developer.biblescroll.feature.home.VerseOfTheDayCard
import java.time.LocalDate
import aarnav100.developer.biblescroll.feature.bible.FeedTopBar
import aarnav100.developer.biblescroll.feature.onboarding.FeatureTourStep
import aarnav100.developer.biblescroll.feature.onboarding.Goal
import aarnav100.developer.biblescroll.feature.onboarding.GoalResponseStep
import aarnav100.developer.biblescroll.feature.onboarding.GoalStep
import aarnav100.developer.biblescroll.feature.onboarding.OnboardingUiState
import aarnav100.developer.biblescroll.feature.onboarding.BibleStep
import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.feature.onboarding.PermissionStep
import aarnav100.developer.biblescroll.feature.onboarding.PlanStep
import aarnav100.developer.biblescroll.feature.onboarding.TimingStep
import aarnav100.developer.biblescroll.feature.onboarding.TourVerses
import aarnav100.developer.biblescroll.feature.onboarding.WelcomeStep
import aarnav100.developer.biblescroll.feature.settings.DataBackupContent
import aarnav100.developer.biblescroll.feature.settings.SettingsScreen
import aarnav100.developer.biblescroll.feature.settings.HelpContent
import aarnav100.developer.biblescroll.feature.home.PlanDayState
import aarnav100.developer.biblescroll.feature.home.ReadingItem
import aarnav100.developer.biblescroll.feature.home.TodaysReadingCard
import aarnav100.developer.biblescroll.feature.home.VerseSearchCard
import aarnav100.developer.biblescroll.feature.home.VerseSearchUiState
import aarnav100.developer.biblescroll.data.legal.OpenSourceLibrary
import aarnav100.developer.biblescroll.feature.settings.LegalContent
import aarnav100.developer.biblescroll.feature.settings.LegalUiState
import aarnav100.developer.biblescroll.feature.settings.TranslationNotice
import aarnav100.developer.biblescroll.feature.settings.WidgetGuideContent
import aarnav100.developer.biblescroll.feature.settings.WidgetGuideUiState
import aarnav100.developer.biblescroll.widget.WidgetDisplay
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.core.content.model.Citation
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.core.content.model.OriginalLanguage
import aarnav100.developer.biblescroll.core.content.model.Study
import aarnav100.developer.biblescroll.core.content.model.TextBasis
import aarnav100.developer.biblescroll.core.content.model.Word
import aarnav100.developer.biblescroll.feature.study.CrossReferenceUi
import aarnav100.developer.biblescroll.feature.study.StudyUiState
import aarnav100.developer.biblescroll.feature.study.VerseStudyContent
import aarnav100.developer.biblescroll.data.community.CommunityRules
import aarnav100.developer.biblescroll.feature.community.CommunityContent
import aarnav100.developer.biblescroll.feature.community.CommunityUiState
import aarnav100.developer.biblescroll.feature.community.MinistryUi
import aarnav100.developer.biblescroll.feature.community.PastRoundUi
import aarnav100.developer.biblescroll.feature.report.DeviceInfo
import aarnav100.developer.biblescroll.feature.report.NoEmailAppContent
import aarnav100.developer.biblescroll.feature.report.ProblemReport
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportProblemContent
import aarnav100.developer.biblescroll.feature.report.ReportType
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

    @Test fun settings() = shot("settings") { SettingsScreen({}, {}, {}, {}, {}, {}, {}, {}) }
    @Test fun dataBackup() = shot("data_backup") { DataBackupContent(busy = false, onBack = {}, onExport = {}, onImport = {}) }
    @Test fun dataError() = shot("data_error") { DataErrorScreen(checking = false, onRetry = {}, onSaveCopy = {}, savedResults = emptyFlow()) }
    @Test fun welcome() = shot("onboarding_welcome") { WelcomeStep(onGetStarted = {}) }
    @Test fun bible() = shot("onboarding_bible") {
        BibleStep(
            options = listOf(
                Translation("RV1909", "Reina-Valera 1909", "es", "PD", hasRedLetters = false),
                Translation("WEB", "World English Bible", "en", "PD"),
            ),
            selectedId = "RV1909", onSelect = {}, onBack = {}, onContinue = {},
        )
    }
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

    @Test fun community() = shot("community", heightDp = 1400) {
        val river = MinistryUi("river", "River Bible Translation", "Translates Scripture into languages that have none yet.", "https://example.org", "https://example.org/give")
        val harbor = MinistryUi("harbor", "Harbor Food Pantry", "Feeds families in port towns every week.", "https://example.org", "https://example.org/give")
        CommunityContent(
            CommunityUiState(
                loading = false, month = "2026-12", closesAt = java.time.Instant.parse("2027-01-01T00:00:00Z"),
                round = listOf(river, harbor), status = CommunityRules.VoteStatus.PENDING, myPick = river,
                currentTotals = mapOf("river" to 12L, "harbor" to 1L),
                past = listOf(PastRoundUi("2026-11", listOf(harbor to 40L, river to 31L), myPickId = "river")),
            ),
            onVote = {}, onOpen = {}, onRetry = {},
        )
    }
    @Test fun communityNoSpotlight() = shot("community_no_spotlight", heightDp = 480) {
        CommunityContent(CommunityUiState(loading = false, month = "2027-02"), onVote = {}, onOpen = {}, onRetry = {})
    }
    private fun studyState(hidden: Boolean) = StudyUiState(
        ref = VerseRef(43, 3, 16), bookName = "John", hidden = hidden,
        verseText = "For God so loved the world, that he gave his only begotten Son.",
        study = StudyState.Available(
            Study(
                id = "JHN.3.16", contentVersion = "0123456789ab",
                originalLanguage = OriginalLanguage(Language.GREEK, TextBasis.CRITICAL, listOf(
                    Word("ἠγάπησεν", "ἀγαπάω", "ēgapēsen", "G0025", "loved", "V-AAI-3S"),
                    Word("θεὸς", "θεός", "theos", "G2316", "God", "N-NSM"),
                    Word("κόσμον", "κόσμος", "kosmon", "G2889", "world", "N-ASM"),
                )),
                crossReferences = emptyList(),
                meaning = "God's love reaches the whole world.", historicalContext = "h", lifeInBibleTimes = "l",
                theology = "t", didYouKnow = "d", applyIt = "a",
                citations = listOf(Citation("STEP Bible", "CC BY 4.0", "https://stepbible.github.io/STEPBible-Data/")),
                reviewed = false, model = "claude-haiku-5-5",
            ),
        ),
        crossReferences = listOf(CrossReferenceUi(VerseRef(62, 4, 9), "1 John 4:9", "In this was manifested the love of God.", "love")),
    )
    @Test fun verseStudy() = shot("b8_verse_study", heightDp = 1600) {
        VerseStudyContent(studyState(hidden = false), "John 3:16", {}, {}, {}, {}, {}, {})
    }
    @Test fun verseStudyHiddenAi() = shot("b8_verse_study_hide_ai", heightDp = 1100) {
        VerseStudyContent(studyState(hidden = true), "John 3:16", {}, {}, {}, {}, {}, {})
    }

    @Test fun legal() = shot("s8_legal", heightDp = 2400) {
        LegalContent(
            LegalUiState(
                translations = listOf(TranslationNotice("WEB", "World English Bible", "Public domain.", "Source: eBible.org (engwebp).")),
                libraries = listOf(OpenSourceLibrary("Activity", "androidx.activity:activity", "1.13.0", listOf("Apache-2.0"), null)),
                contentRelease = "c8a40bd6e082", studyBooks = 5,
            ),
            onBack = {}, onOpenLicence = {}, onReport = {},
        )
    }
    @Test fun widgetGuide() = shot("s5_widget_guide", heightDp = 1400) {
        WidgetGuideContent(
            WidgetGuideUiState(preview = WidgetDisplay(VerseRef(19, 119, 105), "Your word is a lamp to my feet, and a light for my path.", "Psalm 119:105", "WEB"), placed = false, canPin = true),
            onBack = {}, onPin = {},
        )
    }
    @Test fun todaysReading() = shot("h1_todays_reading", heightDp = 520) {
        TodaysReadingCard(
            items = listOf(
                ReadingItem.PlanDay("e1", "The Gospels in 30 days", PlanDayState.CAUGHT_UP, 4, 30, "The Good Shepherd",
                    listOf("John 10:1–21", "Psalm 23"), 12, java.time.LocalDate.of(2026, 11, 15)),
                ReadingItem.ContinueReading(VerseRef(43, 3, 16), "John 3:16"),
            ),
            onOpenPlanDay = { _, _ -> }, onContinue = {}, onOpenBible = {},
        )
    }
    @Test fun verseSearch() = shot("h1_verse_search", heightDp = 520) { VerseSearchCard(searchState(), {}, {}, {}, {}, {}, forceAccessiblePickers = false) }
    @Test fun verseSearchTalkBack() = shot("h1_verse_search_talkback", heightDp = 620) { VerseSearchCard(searchState(), {}, {}, {}, {}, {}, forceAccessiblePickers = true) }
    private fun searchState() = VerseSearchUiState(
        books = listOf(aarnav100.developer.biblescroll.data.bible.model.Book(43, "JHN", "John", aarnav100.developer.biblescroll.data.bible.model.Testament.NEW, 21)),
        chapterCount = 21, verseCount = 36, selection = VerseRef(43, 3, 16), reference = "John 3:16",
    )
    @Test fun help() = shot("help_feedback") { HelpContent(onBack = {}, onReport = {}, onFeedback = {}) }
    @Test fun reportProblem() = shot("report_problem", heightDp = 900) {
        ReportProblemContent(selected = ReportType.WRONG_TEXT, onSelect = {}, note = "", onNoteChange = {}, onSend = {}, onCancel = {})
    }
    @Test fun reportNoEmailApp() = shot("report_no_email_app", heightDp = 900) {
        val device = DeviceInfo("1.0 (1)", "15 (API 35)", "Google Pixel 8", "en-XA")
        NoEmailAppContent(ProblemReport.build("help@example.org", ReportType.FACTUAL_ERROR, "", ReportContext.App, device), onClose = {})
    }

    @Test fun verseOfTheDayCard() = shot("home_verse_of_the_day", heightDp = 520) {
        val verse = Verse("KJV", VerseRef(43, 3, 16), 1, "For God so loved the world, that he gave his only begotten Son, that whosoever believeth in him should not perish, but have everlasting life.", emptyList())
        VerseOfTheDayCard(
            votd = VotdVerse(LocalDate.of(2028, 3, 16), verse, "John 3:16", requestedTranslationId = "BSB", translationName = "King James Version"),
            onReadInContext = {}, onShareImage = {}, onSetWidget = {}, onCopy = {}, onStudy = {},
        )
    }

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
