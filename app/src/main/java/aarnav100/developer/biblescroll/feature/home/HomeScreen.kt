package aarnav100.developer.biblescroll.feature.home

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
 * H1 Home, top to bottom: header (streak chip, Settings gear), the one-time restore notice when present, Verse of the
 * Day, Today's Reading, Today's Devotional, Verse Search, Library (H3), Reading plans (H8). Every card follows its
 * Flow live; the scroll position is saved and restored with the tab.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlans: () -> Unit,
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
) {
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
            context.copyVerse(verse, reference)
            host.currentSnackbarData?.dismiss()
            scope.launch { host.showSnackbar(copiedMessage, duration = SnackbarDuration.Short) }
        }
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.tab_home),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                StreakChip(streak, onClick = {
                    streakViewModel.resetMonth()
                    showStreak = true
                })
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
            RestoreNoticeCard()
            (votd as? VotdUiState.Ready)?.votd?.let { today ->
                VerseOfTheDayCard(
                    votd = today,
                    onReadInContext = votdViewModel::readInContext,
                    onShareImage = { sheetsViewModel.openShareImage(today.verse) },
                    onSetWidget = { sheetsViewModel.openWidgetConfirm(today.verse, WidgetMode.VERSE_OF_THE_DAY) },
                    onCopy = { copyAndConfirm(today.verse, today.reference, snackbarHostState) },
                    onStudy = { studyViewModel.open(today.verse.ref) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            reading?.let { items ->
                TodaysReadingCard(
                    items = items,
                    onOpenPlanDay = onOpenPlanDay,
                    onContinue = readingViewModel::continueReading,
                    onOpenBible = { readingViewModel.continueReading(VerseRef(1, 1, 1)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            DevotionalHomeCards(onOpen = onOpenDevotional, onOpenArchive = onOpenDevotionalArchive)
            VerseSearchCard(
                state = search,
                onSelectBook = searchViewModel::selectBook,
                onSelectChapter = searchViewModel::selectChapter,
                onSelectVerse = searchViewModel::selectVerse,
                onRead = searchViewModel::read,
                onStudy = { studyViewModel.open(search.selection) },
                modifier = Modifier.padding(top = 16.dp),
            )
            HomeEntry(
                icon = Icons.Outlined.CollectionsBookmark,
                title = stringResource(R.string.library_title),
                subtitle = pluralStringResource(R.plurals.library_total, library.total, library.total),
                onClick = onOpenLibrary,
            )
            HomeEntry(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.plans_title),
                subtitle = stringResource(R.string.plans_home_body),
                onClick = onOpenPlans,
            )
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

@Composable
private fun HomeEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}
