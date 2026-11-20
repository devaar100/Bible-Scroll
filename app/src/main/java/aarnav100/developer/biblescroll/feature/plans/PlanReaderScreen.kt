package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.feature.report.ContentType
import aarnav100.developer.biblescroll.feature.report.ReportContext
import aarnav100.developer.biblescroll.feature.report.ReportProblemSheet
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import aarnav100.developer.biblescroll.ui.text.localizedList
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.feature.bible.VerseNoteSheet
import aarnav100.developer.biblescroll.feature.bible.VerseNoteUiState
import aarnav100.developer.biblescroll.feature.bible.VerseNoteViewModel
import aarnav100.developer.biblescroll.feature.bible.VersePage
import aarnav100.developer.biblescroll.feature.bible.VerseSheets
import aarnav100.developer.biblescroll.feature.bible.copyVerse
import aarnav100.developer.biblescroll.feature.bible.reference
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseSheetsViewModel
import aarnav100.developer.biblescroll.feature.bible.shareVerse
import aarnav100.developer.biblescroll.ui.theme.ReadingTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubberVisibility
import aarnav100.developer.biblescroll.ui.components.scrubber.VerseScrubber
import aarnav100.developer.biblescroll.ui.components.scrubber.VerseScrubberDefaults
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp

/**
 * H11: the day's reading as an immersive verse feed. Same pager, page and gestures as B1 (double-tap to
 * like, rail actions, long-press sheet), limited to the day's passages: intro first, "Mark day as read" last.
 */
@Composable
fun PlanReaderScreen(
    onClose: () -> Unit,
    onOpenDay: (enrollmentId: String, day: Int) -> Unit,
    onCompleted: (enrollmentId: String) -> Unit,
    viewModel: PlanReaderViewModel = hiltViewModel(),
    noteViewModel: VerseNoteViewModel = hiltViewModel(),
    sheetsViewModel: VerseSheetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlanReaderEvent.Completed -> onCompleted(event.enrollmentId)
                is PlanReaderEvent.OpenDay -> onOpenDay(event.enrollmentId, event.dayNumber)
            }
        }
    }
    ReadingTheme {
        // Surface (not a Box background) so text and icons get the reading theme's content colour.
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize()) {
                when (val s = state) {
                    PlanReaderUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    PlanReaderUiState.NotFound -> Text(
                        stringResource(R.string.plans_not_found),
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    )
                    is PlanReaderUiState.Ready -> ReaderContent(s, viewModel, noteViewModel, sheetsViewModel)
                }
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.plans_reader_close))
                }
            }
        }
    }
}

@Composable
private fun ReaderContent(
    s: PlanReaderUiState.Ready,
    viewModel: PlanReaderViewModel,
    noteViewModel: VerseNoteViewModel,
    sheetsViewModel: VerseSheetsViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.feed_copied)
    val copyAndConfirm: (Verse, String, SnackbarHostState) -> Unit = remember(context, scope, copied) {
        { verse, reference, host ->
            context.copyVerse(verse, reference)
            scope.launch {
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(copied, duration = SnackbarDuration.Short)
            }
        }
    }
    val pagerState = rememberPagerState { s.pages.size }
    var reporting by rememberSaveable { mutableStateOf(false) }
    val scrubberMode = BibleScrollTheme.settings.verseScrubber
    val textStart = ScrubberVisibility.textStartInsetDp(scrubberMode).dp
    val resources = LocalResources.current
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = pagerState, beyondViewportPageCount = 1, key = { it }, modifier = Modifier.fillMaxSize()) { index ->
            when (val page = s.pages[index]) {
                is ReaderPage.Intro -> IntroPage(page, s.planTitle, textStart)
                is ReaderPage.VersePage -> {
                    val verse = page.verse
                    val reference = verse.reference(s.bookNames)
                    val liked = verse.ref in s.liked
                    val bookmarked = verse.ref in s.bookmarked
                    VersePage(
                        verse = verse,
                        reference = reference,
                        translationName = s.translationName,
                        liked = liked,
                        bookmarked = bookmarked,
                        hasNote = verse.ref in s.noted,
                        onToggleLike = { viewModel.onToggleLike(verse.ref, liked) },
                        onToggleBookmark = { viewModel.onToggleBookmark(verse.ref, bookmarked) },
                        onNote = { noteViewModel.open(verse) },
                        onShare = { context.shareVerse(verse, reference) },
                        onCopy = { copyAndConfirm(verse, reference, snackbar) },
                        onActions = { sheetsViewModel.openActions(verse) },
                        textStartInset = textStart,
                    )
                }
                is ReaderPage.Finish -> FinishPage(page, textStart, onMarkRead = viewModel::markRead, onNextDay = viewModel::openDay)
            }
        }
        // H11: position within the day's pages (intro, verses, finish). Plan progress is saved by "Mark day as read",
        // not per page, so there's nothing to hold back while scrubbing.
        VerseScrubber(
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            count = s.pages.size,
            labelNumber = { i -> i + 1 },
            stateDescription = { i -> resources.getString(R.string.scrubber_plan_state, i + 1, s.pages.size) },
            contentDescription = stringResource(R.string.scrubber_plan_cd),
            mode = scrubberMode,
            scrolling = { pagerState.isScrollInProgress },
            onScrub = { i -> scope.launch { pagerState.scrollToPage(i) } },
            onScrubbingChange = {},
            modifier = Modifier
                .align(Alignment.TopStart)
                // 12 dp below the close button (4 dp + 48 dp) and 12 dp above the bottom edge (inside the system bars).
                .padding(top = 52.dp + VerseScrubberDefaults.VerticalGap, bottom = VerseScrubberDefaults.VerticalGap),
        )
        Text(
            stringResource(R.string.plans_reader_day, s.dayNumber, s.totalDays),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        IconButton(onClick = { reporting = true }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Icon(Icons.Outlined.Flag, contentDescription = stringResource(R.string.report_title))
        }
    }
    if (reporting) {
        // On a verse, name it: a wrong-verse report then says which one. Otherwise the plan and day.
        val verse = (s.pages.getOrNull(pagerState.currentPage) as? ReaderPage.VersePage)?.verse
        val where = verse?.reference(s.bookNames) ?: stringResource(R.string.plans_reader_day, s.dayNumber, s.totalDays)
        ReportProblemSheet(
            ReportContext(
                contentType = ContentType.PLAN,
                contentId = "${s.planId} day ${s.dayNumber}",
                reference = stringResource(R.string.two_parts, s.planTitle, where),
                translationId = s.translationId,
                contentVersion = s.planContentVersion,
            ),
            onDismiss = { reporting = false },
        )
    }

    VerseSheets(viewModel = sheetsViewModel, bookNames = s.bookNames, screenSnackbar = snackbar, copyAndConfirm = copyAndConfirm)
    val noteState by noteViewModel.uiState.collectAsStateWithLifecycle()
    (noteState as? VerseNoteUiState.Open)?.let { open ->
        VerseNoteSheet(
            state = open,
            reference = open.verse.reference(s.bookNames),
            onTextChange = noteViewModel::onTextChange,
            onDelete = noteViewModel::delete,
            onDismiss = noteViewModel::close,
        )
    }
}

@Composable
private fun IntroPage(page: ReaderPage.Intro, planTitle: String, textStart: Dp) {
    PageColumn(textStart) {
        Text(planTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            page.title ?: stringResource(R.string.plans_day_number, page.dayNumber),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(localizedList(page.passages), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Text(
            page.intro ?: pluralStringResource(R.plurals.plans_reader_minutes, page.minutes, page.minutes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Outlined.KeyboardDoubleArrowUp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.plans_reader_swipe), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FinishPage(page: ReaderPage.Finish, textStart: Dp, onMarkRead: () -> Unit, onNextDay: (Int) -> Unit) {
    PageColumn(textStart) {
        if (!page.read) {
            Text(
                stringResource(R.string.plans_reader_end_of_day, page.dayNumber),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onMarkRead, modifier = Modifier.widthIn(min = 220.dp)) { Text(stringResource(R.string.plans_reader_mark_read)) }
        } else {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.plans_reader_day_read, page.dayNumber),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            page.nextDay?.let { next ->
                Spacer(Modifier.height(24.dp))
                Button(onClick = { onNextDay(next) }, modifier = Modifier.widthIn(min = 220.dp)) {
                    Text(stringResource(R.string.plans_reader_next_day, next))
                }
            }
        }
    }
}

@Composable
private fun PageColumn(textStart: Dp, content: @Composable () -> Unit) {
    Column(
        // Start clears the scrubber strip when it's on; at least the old 32 dp either way.
        Modifier.fillMaxSize().padding(start = maxOf(textStart, 32.dp), end = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
