package aarnav100.developer.biblescroll.feature.home

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import android.text.format.DateFormat
import aarnav100.developer.biblescroll.ui.theme.Spacing
import aarnav100.developer.biblescroll.ui.theme.AppText
import aarnav100.developer.biblescroll.ui.components.SectionHeader
import aarnav100.developer.biblescroll.feature.plans.PlansBrowserViewModel
import aarnav100.developer.biblescroll.feature.plans.PlansBrowserUiState
import aarnav100.developer.biblescroll.feature.library.LibraryScreen
import aarnav100.developer.biblescroll.feature.streak.StreakUiState
import aarnav100.developer.biblescroll.feature.study.VerseStudySheet
import aarnav100.developer.biblescroll.feature.study.VerseStudyViewModel
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.feature.devotionals.DevotionalHomeCards
import aarnav100.developer.biblescroll.feature.library.LibraryViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import aarnav100.developer.biblescroll.feature.streak.StreakChip
import aarnav100.developer.biblescroll.feature.streak.StreakSheet
import aarnav100.developer.biblescroll.feature.streak.StreakViewModel
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.bible.VerseSheets
import aarnav100.developer.biblescroll.feature.bible.copyVerse
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseSheetsViewModel
import aarnav100.developer.biblescroll.widget.WidgetMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * H1 Home (redesigned 2026-11-25, docs/ui-direction.md), top to bottom: date and greeting with the streak chip and
 * Settings, the one-time restore notice when present, Verse of the Day, Today's reading, Today's devotional (photo
 * card), Reading plans (photo row), Find a verse, Your library (counts). Every card follows its Flow live; the scroll
 * position is saved and restored with the tab.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenPlan: (planId: String) -> Unit,
    onBuildPlan: () -> Unit,
    onOpenLibraryList: (LibraryScreen) -> Unit,
    onOpenDevotional: (date: String) -> Unit,
    onOpenDevotionalArchive: () -> Unit,
    onOpenPlanDay: (enrollmentId: String, day: Int) -> Unit,
    modifier: Modifier = Modifier,
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    streakViewModel: StreakViewModel = hiltViewModel(),
    votdViewModel: VerseOfTheDayViewModel = hiltViewModel(),
    sheetsViewModel: VerseSheetsViewModel = hiltViewModel(),
    studyViewModel: VerseStudyViewModel = hiltViewModel(),
    readingViewModel: TodaysReadingViewModel = hiltViewModel(),
    searchViewModel: VerseSearchViewModel = hiltViewModel(),
    plansViewModel: PlansBrowserViewModel = hiltViewModel(),
) {
    val plansState by plansViewModel.uiState.collectAsStateWithLifecycle()
    val reading by readingViewModel.items.collectAsStateWithLifecycle()
    val search by searchViewModel.uiState.collectAsStateWithLifecycle()
    val library by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val streak by streakViewModel.uiState.collectAsStateWithLifecycle()
    val votd by votdViewModel.uiState.collectAsStateWithLifecycle()
    val bookNames by votdViewModel.bookNames.collectAsStateWithLifecycle()
    var showStreak by rememberSaveable { mutableStateOf(false) }
    // A new day (or time zone) while the app was in the background shows on return.
    LifecycleResumeEffect(votdViewModel) {
        votdViewModel.onResume()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedMessage = stringResource(R.string.feed_copied)
    // Same copy format and confirmation as B7 in the feed.
    val copyAndConfirm: (Verse, String, SnackbarHostState) -> Unit = remember(context, scope, copiedMessage) {
        { verse, reference, host ->
            if (context.copyVerse(verse, reference)) {
                host.currentSnackbarData?.dismiss()
                scope.launch { host.showSnackbar(copiedMessage, duration = SnackbarDuration.Short) }
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter).padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            HomeHeader(
                streak = streak,
                onStreak = {
                    streakViewModel.resetMonth()
                    showStreak = true
                },
                onOpenSettings = onOpenSettings,
            )
            RestoreNoticeCard()
            (votd as? VotdUiState.Ready)?.votd?.let { today ->
                VerseOfTheDayCard(
                    votd = today,
                    onReadInContext = votdViewModel::readInContext,
                    onShareImage = { sheetsViewModel.openShareImage(today.verse) },
                    onSetWidget = { sheetsViewModel.openWidgetConfirm(today.verse, WidgetMode.VERSE_OF_THE_DAY) },
                    onCopy = { copyAndConfirm(today.verse, today.reference, snackbarHostState) },
                    onStudy = { studyViewModel.open(today.verse.ref) },
                )
            }
            reading?.takeIf { it.isNotEmpty() }?.let { items ->
                Column {
                    SectionHeader(stringResource(R.string.home_todays_reading))
                    TodaysReadingCard(
                        items = items,
                        onOpenPlanDay = onOpenPlanDay,
                        onContinue = readingViewModel::continueReading,
                        onOpenBible = { readingViewModel.continueReading(VerseRef(1, 1, 1)) },
                    )
                }
            }
            DevotionalHomeCards(onOpen = onOpenDevotional, onOpenArchive = onOpenDevotionalArchive)
            (plansState as? PlansBrowserUiState.Ready)?.let { ready ->
                // The featured ("start-here") plans first, then the rest of the catalogue, a few at most.
                val featured = (ready.categories.filter { it.id == FEATURED_CATEGORY } + ready.categories)
                    .flatMap { it.plans }.distinctBy { it.id }.take(HOME_PLANS)
                HomePlansRow(featured, onOpenPlan = onOpenPlan, onBuildYourOwn = onBuildPlan, onSeeAll = onOpenPlans)
            }
            VerseSearchCard(
                state = search,
                onSelectBook = searchViewModel::selectBook,
                onSelectChapter = searchViewModel::selectChapter,
                onSelectVerse = searchViewModel::selectVerse,
                onRead = searchViewModel::read,
                onStudy = { studyViewModel.open(search.selection) },
            )
            HomeLibraryCard(library, onOpenLibrary = onOpenLibrary, onOpenList = onOpenLibraryList)
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    // B10 share-as-image and the B11 widget dialog, shared with the feed.
    VerseSheets(
        viewModel = sheetsViewModel,
        bookNames = bookNames,
        screenSnackbar = snackbarHostState,
        copyAndConfirm = copyAndConfirm,
    )
    // B8 for the Verse of the Day; "Go to verse" switches to the Bible tab.
    VerseStudySheet(studyViewModel)
    if (showStreak) {
        StreakSheet(
            state = streak,
            onPreviousMonth = streakViewModel::showPreviousMonth,
            onNextMonth = streakViewModel::showNextMonth,
            onGraceDayChange = streakViewModel::setGraceDay,
            onDismiss = { showStreak = false },
        )
    }
}

private const val FEATURED_CATEGORY = "start-here"
private const val HOME_PLANS = 6

/** Today's date as a small label, a greeting for the time of day, then the streak chip and Settings. */
@Composable
private fun HomeHeader(streak: StreakUiState, onStreak: () -> Unit, onOpenSettings: () -> Unit) {
    val locale = Locale.getDefault()
    val now = LocalDateTime.now()
    val date = remember(now.toLocalDate(), locale) {
        now.toLocalDate().format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale))
    }
    val greeting = stringResource(
        when (now.hour) {
            in 5..11 -> R.string.home_greeting_morning
            in 12..16 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
    Row(Modifier.fillMaxWidth().padding(top = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(date.uppercase(locale), style = AppText.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.xs))
            Text(greeting, style = AppText.screenTitle, modifier = Modifier.semantics { heading() })
        }
        StreakChip(streak, onClick = onStreak)
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
