package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.feature.study.VerseStudySheet
import aarnav100.developer.biblescroll.feature.study.VerseStudyViewModel
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.feature.bible.picker.BookPickerSheet
import aarnav100.developer.biblescroll.feature.bible.picker.BookPickerViewModel
import aarnav100.developer.biblescroll.feature.bible.picker.TranslationPickerSheet
import aarnav100.developer.biblescroll.ui.theme.ReadingTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import aarnav100.developer.biblescroll.ui.components.TestTags
import aarnav100.developer.biblescroll.ui.components.automationTag
import androidx.compose.ui.Modifier
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import aarnav100.developer.biblescroll.feature.bible.sheets.CompareSheet
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseSheetEvent
import aarnav100.developer.biblescroll.feature.bible.sheets.WidgetVerseDialog
import aarnav100.developer.biblescroll.feature.share.CardContent
import aarnav100.developer.biblescroll.feature.share.ShareImageSheet
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseActionsSheet
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseSheet
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseSheetsViewModel
import aarnav100.developer.biblescroll.domain.streak.StreakRules
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import aarnav100.developer.biblescroll.data.bible.ChapterSpan
import aarnav100.developer.biblescroll.data.bible.ChapterSpans
import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubberVisibility
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubberLabelNumber
import aarnav100.developer.biblescroll.ui.components.scrubber.VerseScrubber
import aarnav100.developer.biblescroll.ui.components.scrubber.VerseScrubberDefaults
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.flow.filterNotNull

/**
 * B1: full-screen vertical verse feed, with the B2 liked filter, B3/B4 book picker, B5 translation
 * picker and B6 notes sheet.
 */
@Composable
fun BibleScreen(
    modifier: Modifier = Modifier,
    viewModel: BibleFeedViewModel = hiltViewModel(),
    pickerViewModel: BookPickerViewModel = hiltViewModel(),
    noteViewModel: VerseNoteViewModel = hiltViewModel(),
    sheetsViewModel: VerseSheetsViewModel = hiltViewModel(),
    studyViewModel: VerseStudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val chapterSpans by viewModel.chapterSpans.collectAsStateWithLifecycle()
    // Startup is "fully drawn" once real verses are on screen, not at the loading spinner.
    ReportDrawnWhen { (state as? BibleFeedUiState.Ready)?.pages?.isNotEmpty() == true }
    // Widget tap: open the linked verse once the feed is on screen.
    val linked by viewModel.pendingLink.collectAsStateWithLifecycle()
    LaunchedEffect(linked) { linked?.let(viewModel::openLinkedVerse) }
    when (val s = state) {
        BibleFeedUiState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is BibleFeedUiState.Ready -> {
            var bookPickerOpen by rememberSaveable { mutableStateOf(false) }
            var translationPickerOpen by rememberSaveable { mutableStateOf(false) }
            // The verse on screen in whichever feed is showing: drives the top bar and the picker.
            var current by remember { mutableStateOf<Verse?>(null) }
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val copiedMessage = stringResource(R.string.feed_copied)
            // Copy, then confirm in [host]. currentSnackbarData?.dismiss() so rapid copies don't queue up.
            val copyAndConfirm: (Verse, String, SnackbarHostState) -> Unit = remember(context, scope, copiedMessage) {
                { verse, reference, host ->
                    if (context.copyVerse(verse, reference)) {
                        host.currentSnackbarData?.dismiss()
                        scope.launch { host.showSnackbar(copiedMessage, duration = SnackbarDuration.Short) }
                    }
                }
            }

            val pageActions = remember(viewModel, noteViewModel, sheetsViewModel, context, copyAndConfirm) {
                PageActions(
                    onToggleLike = { viewModel.onToggleLike(it.ref) },
                    onToggleBookmark = { viewModel.onToggleBookmark(it.ref) },
                    onNote = noteViewModel::open,
                    onShare = { verse, reference -> context.shareVerse(verse, reference) },
                    onCopy = { verse, reference -> copyAndConfirm(verse, reference, snackbarHostState) },
                    onActions = sheetsViewModel::openActions,
                )
            }

            // The feed uses the reading background (S3), independent of the app's light/dark mode.
            ReadingTheme {
                Box(
                    modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    val likedFeed = s.likedFeed
                    if (likedFeed == null) {
                        VerseFeedPager(
                            pagerKey = s.pagerKey,
                            startPage = s.startPage,
                            pageCount = s.pageCount,
                            verseAt = { s.pages[it] },
                            state = s,
                            actions = pageActions,
                            scrubber = FeedScrubber.Chapter(chapterSpans?.takeIf { it.translationId == s.translationId }),
                            onCurrentVerse = { current = it },
                            onPageChanged = viewModel::onPageChanged,
                            onVerseSettled = viewModel::onVerseSettled,
                            onVerseDwelled = viewModel::onVerseDwelled,
                        )
                    } else {
                        LikedFeedContent(
                            feed = likedFeed,
                            state = s,
                            actions = pageActions,
                            onCurrentVerse = { current = it },
                            onVerseSettled = viewModel::onLikedVerseSettled,
                            onVerseDwelled = viewModel::onVerseDwelled,
                        )
                    }

                    val currentVerse = current
                    FeedTopBar(
                        likedFilterOn = likedFeed != null,
                        onToggleLikedFilter = viewModel::onToggleLikedFilter,
                        bookmarked = currentVerse?.let { it.ref in s.bookmarked },
                        onToggleBookmark = { currentVerse?.let { viewModel.onToggleBookmark(it.ref) } },
                        selectedTranslationId = s.translationId,
                        onOpenTranslationPicker = { translationPickerOpen = true },
                        chapterLabel = currentVerse?.let {
                            stringResource(R.string.feed_chapter_format, s.bookNames[it.ref.bookId].orEmpty(), it.ref.chapter)
                        }.orEmpty(),
                        onOpenBookPicker = { bookPickerOpen = true },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
                }
            }

            if (bookPickerOpen) {
                val pickerState by pickerViewModel.uiState.collectAsStateWithLifecycle()
                BookPickerSheet(
                    state = pickerState,
                    current = current?.ref,
                    onQueryChange = pickerViewModel::onQueryChange,
                    onOpenBook = pickerViewModel::openBook,
                    onBackToBooks = pickerViewModel::backToBooks,
                    onChapterSelected = viewModel::jumpToChapter,
                    onContinue = viewModel::resumeBook,
                    onToggleChapterRead = pickerViewModel::toggleChapterRead,
                    onToggleBookRead = pickerViewModel::toggleBookRead,
                    onDismiss = {
                        bookPickerOpen = false
                        pickerViewModel.reset()
                    },
                )
            }
            if (translationPickerOpen) {
                TranslationPickerSheet(
                    translations = s.translations,
                    selectedId = s.translationId,
                    onSelect = viewModel::onSelectTranslation,
                    onDismiss = { translationPickerOpen = false },
                )
            }

            VerseSheets(
                viewModel = sheetsViewModel,
                bookNames = s.bookNames,
                screenSnackbar = snackbarHostState,
                copyAndConfirm = copyAndConfirm,
                onStudy = { studyViewModel.open(it.ref) },
            )
            // B8 over the feed: the pager underneath never moves while it's open.
            VerseStudySheet(studyViewModel)

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
    }
}

/** B7 actions, B9 compare, B10 share image and B11 widget. None of them move the feed. Also used by the plan reader (H11) and Home. */
@Composable
internal fun VerseSheets(
    viewModel: VerseSheetsViewModel,
    bookNames: Map<Int, String>,
    screenSnackbar: SnackbarHostState,
    copyAndConfirm: (Verse, String, SnackbarHostState) -> Unit,
    /** B7 "Study this verse" (opens B8); null hides it. */
    onStudy: ((Verse) -> Unit)? = null,
) {
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    when (val open = sheet) {
        VerseSheet.None -> Unit
        is VerseSheet.Actions -> {
            val reference = open.verse.reference(bookNames)
            VerseActionsSheet(
                reference = reference,
                onCopy = {
                    viewModel.close()
                    copyAndConfirm(open.verse, reference, screenSnackbar)
                },
                onCompare = { viewModel.openCompare(open.verse) },
                onShareAsImage = { viewModel.openShareImage(open.verse) },
                onSetWidgetVerse = { viewModel.openWidgetConfirm(open.verse) },
                onDismiss = viewModel::close,
                onStudy = onStudy?.let { study -> { viewModel.close(); study(open.verse) } },
            )
        }
        is VerseSheet.Compare -> {
            val sheetSnackbar = remember { SnackbarHostState() }
            val resources = LocalContext.current.resources
            CompareSheet(
                reference = open.verse.reference(bookNames),
                rows = open.rows,
                onCopy = { row ->
                    val verse = row.verse ?: return@CompareSheet
                    val reference = resources.getString(
                        R.string.ref_verse,
                        bookNames[verse.ref.bookId].orEmpty(), verse.ref.chapter, verse.ref.verse,
                    )
                    copyAndConfirm(verse, reference, sheetSnackbar)
                },
                snackbarHostState = sheetSnackbar,
                onDismiss = viewModel::close,
            )
        }
        is VerseSheet.ShareImage -> {
            val reference = open.verse.reference(bookNames)
            ShareImageSheet(
                content = CardContent(
                    verseText = open.verse.text,
                    reference = reference,
                    translationId = open.verse.translationId,
                    appName = stringResource(R.string.app_name),
                ),
                shareText = open.verse.clipText(reference),
                onDismiss = viewModel::close,
            )
        }
        is VerseSheet.WidgetConfirm -> WidgetVerseDialog(
            state = open,
            reference = open.verse.reference(bookNames),
            onConfirm = viewModel::confirmWidgetVerse,
            onDismiss = viewModel::close,
        )
    }

    val updatedMsg = stringResource(R.string.widget_updated)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                VerseSheetEvent.WidgetVerseSet -> screenSnackbar.showSnackbar(updatedMsg)
            }
        }
    }
}

/** Per-verse callbacks shared by the main and liked feeds. Stable, so pages don't recompose for them. */
private class PageActions(
    val onToggleLike: (Verse) -> Unit,
    val onToggleBookmark: (Verse) -> Unit,
    val onNote: (Verse) -> Unit,
    val onShare: (Verse, String) -> Unit,
    val onCopy: (Verse, String) -> Unit,
    val onActions: (Verse) -> Unit,
)

/** B2: the liked-only feed, its loading state, or the empty hint. Same pager and gestures as B1. */
@Composable
private fun LikedFeedContent(
    feed: LikedFeed,
    state: BibleFeedUiState.Ready,
    actions: PageActions,
    onCurrentVerse: (Verse?) -> Unit,
    onVerseSettled: (Verse) -> Unit,
    onVerseDwelled: (Verse) -> Unit,
) {
    val verses = feed.verses
    when {
        verses == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        verses.isEmpty() -> {
            LaunchedEffect(Unit) { onCurrentVerse(null) }
            LikedEmptyState()
        }
        else -> VerseFeedPager(
            pagerKey = feed.pagerKey,
            startPage = feed.startPage,
            pageCount = verses.size,
            verseAt = { verses.getOrNull(it) },
            state = state,
            actions = actions,
            scrubber = FeedScrubber.List,
            onCurrentVerse = onCurrentVerse,
            onPageChanged = {},
            onVerseSettled = onVerseSettled,
            onVerseDwelled = onVerseDwelled,
        )
    }
}

@Composable
private fun LikedEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.feed_liked_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.feed_liked_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VerseFeedPager(
    pagerKey: String,
    startPage: Int,
    pageCount: Int,
    verseAt: (Int) -> Verse?,
    state: BibleFeedUiState.Ready,
    actions: PageActions,
    scrubber: FeedScrubber,
    onCurrentVerse: (Verse?) -> Unit,
    onPageChanged: (Int) -> Unit,
    onVerseSettled: (Verse) -> Unit,
    onVerseDwelled: (Verse) -> Unit,
) {
    val currentVerseAt by rememberUpdatedState(verseAt)
    val currentOnDwelled by rememberUpdatedState(onVerseDwelled)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val translationName = remember(state.translations, state.translationId) {
        state.translations.firstOrNull { it.id == state.translationId }?.name ?: state.translationId
    }

    // New pager per translation, jump or filter switch: a fresh pager opens straight on its start
    // page instead of scrolling there through the whole Bible.
    key(pagerKey) {
        val pagerState = rememberPagerState(initialPage = startPage) { pageCount }
        // True while the scrubber is dragging: pages fly past, so the reading position is saved on release only.
        val scrubbing = remember { mutableStateOf(false) }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { currentVerseAt(pagerState.currentPage) }.collect(onCurrentVerse)
        }
        LaunchedEffect(pagerState) {
            // Re-runs when the settled page's verse finishes loading, so the save isn't skipped. Held while
            // scrubbing; releasing the scrubber emits the verse it stopped on.
            snapshotFlow { if (scrubbing.value) null else currentVerseAt(pagerState.settledPage) }
                .filterNotNull()
                .distinctUntilChanged()
                .collect(onVerseSettled)
        }
        LaunchedEffect(pagerState, lifecycle) {
            // H2 streak: a verse counts once it has been on screen for ~2 s with the app in front.
            // Leaving the verse or the app restarts the wait.
            combine(snapshotFlow { currentVerseAt(pagerState.settledPage) }, lifecycle.currentStateFlow) { verse, state ->
                verse?.takeIf { state.isAtLeast(Lifecycle.State.RESUMED) }
            }
                .distinctUntilChanged()
                .collectLatest { verse ->
                    if (verse != null) {
                        delay(StreakRules.VERSE_DWELL_MILLIS)
                        currentOnDwelled(verse)
                    }
                }
        }

        val mode = BibleScrollTheme.settings.verseScrubber
        val textStart = ScrubberVisibility.textStartInsetDp(mode).dp
        Box(Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            // Compose the neighbouring verses ahead of time so swipes never show a blank page.
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier
                .fillMaxSize()
                .automationTag(TestTags.FEED_PAGER),
        ) { page ->
            val verse = verseAt(page)
            if (verse == null) {
                Box(Modifier.fillMaxSize())
            } else {
                val reference = verse.reference(state.bookNames)
                VersePage(
                    verse = verse,
                    reference = reference,
                    translationName = translationName,
                    liked = verse.ref in state.liked,
                    bookmarked = verse.ref in state.bookmarked,
                    hasNote = verse.ref in state.noted,
                    onToggleLike = { actions.onToggleLike(verse) },
                    onToggleBookmark = { actions.onToggleBookmark(verse) },
                    onNote = { actions.onNote(verse) },
                    onShare = { actions.onShare(verse, reference) },
                    onCopy = { actions.onCopy(verse, reference) },
                    onActions = { actions.onActions(verse) },
                    textStartInset = textStart,
                )
            }
        }
        FeedPagerScrubber(pagerState, pageCount, scrubber, mode, currentVerseAt, scrubbing)
        }
    }
}

/** What the scrubber spans: B1 the current chapter, B2 the whole liked list. */
private sealed interface FeedScrubber {
    /** [spans] null while they load (no scrubber yet). */
    data class Chapter(val spans: ChapterSpans?) : FeedScrubber
    data object List : FeedScrubber
}

@Composable
private fun BoxScope.FeedPagerScrubber(
    pagerState: PagerState,
    pageCount: Int,
    scrubber: FeedScrubber,
    mode: ScrubberMode,
    verseAt: (Int) -> Verse?,
    scrubbing: MutableState<Boolean>,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val latestScrubber by rememberUpdatedState(scrubber)
    val latestVerseAt by rememberUpdatedState(verseAt)
    // Changes only when the pager crosses into another chapter, so page moves don't recompose anything here.
    val span by remember(pagerState, pageCount) {
        derivedStateOf {
            when (val sc = latestScrubber) {
                is FeedScrubber.Chapter -> sc.spans?.spanAt(pagerState.currentPage)
                FeedScrubber.List -> ChapterSpan(0, 0, firstPage = 0, count = pageCount, lastVerse = pageCount)
            }
        }
    }
    val current = span ?: return
    val chapter = scrubber is FeedScrubber.Chapter
    // B1 labels use the real verse number (a translation may skip one, e.g. WEB Acts 8:37) and the last verse.
    // Pages outside the loaded window fall back to the index (and the chapter's last verse for the last row).
    val number = ScrubberLabelNumber { i ->
        val loaded = if (chapter) latestVerseAt(current.pageOf(i))?.ref?.verse else null
        loaded ?: if (chapter && i == current.count - 1) current.lastVerse else i + 1
    }
    VerseScrubber(
        // Continuous: mid-swipe the track and numbers move with the finger. Read only in the scrubber's draw phase.
        position = { (pagerState.currentPage - current.firstPage) + pagerState.currentPageOffsetFraction },
        count = current.count,
        labelNumber = number,
        stateDescription = { i ->
            resources.getString(
                if (chapter) R.string.scrubber_chapter_state else R.string.scrubber_liked_state,
                number.numberAt(i),
                current.lastVerse,
            )
        },
        contentDescription = stringResource(if (chapter) R.string.scrubber_chapter_cd else R.string.scrubber_liked_cd),
        mode = mode,
        scrolling = { pagerState.isScrollInProgress },
        onScrub = { i -> scope.launch { pagerState.scrollToPage(current.pageOf(i)) } },
        onScrubbingChange = { scrubbing.value = it },
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = FeedScrubberTop, bottom = VerseScrubberDefaults.VerticalGap),
    )
}

/**
 * 12 dp under the top bar (8 dp + 48 dp buttons + 8 dp). The feed sits inside the Scaffold's padding, so the status
 * bar and the bottom navigation bar are already outside it; the bottom gap is 12 dp above the navigation bar.
 */
private val FeedScrubberTop = 64.dp + VerseScrubberDefaults.VerticalGap

@Composable
internal fun Verse.reference(bookNames: Map<Int, String>): String =
    stringResource(R.string.ref_verse, bookNames[ref.bookId].orEmpty(), ref.chapter, ref.verse)
