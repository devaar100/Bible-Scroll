package aarnav100.developer.biblescroll.feature.bible

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
import kotlinx.coroutines.launch

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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                    context.copyVerse(verse, reference)
                    host.currentSnackbarData?.dismiss()
                    scope.launch { host.showSnackbar(copiedMessage, duration = SnackbarDuration.Short) }
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
                            onCurrentVerse = { current = it },
                            onPageChanged = viewModel::onPageChanged,
                            onVerseSettled = viewModel::onVerseSettled,
                        )
                    } else {
                        LikedFeedContent(
                            feed = likedFeed,
                            state = s,
                            actions = pageActions,
                            onCurrentVerse = { current = it },
                            onVerseSettled = viewModel::onLikedVerseSettled,
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
            )

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

/** B7 actions, B9 compare and the Session 7 stubs. None of them move the feed. */
@Composable
private fun VerseSheets(
    viewModel: VerseSheetsViewModel,
    bookNames: Map<Int, String>,
    screenSnackbar: SnackbarHostState,
    copyAndConfirm: (Verse, String, SnackbarHostState) -> Unit,
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
                        R.string.feed_reference_format,
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
            onCurrentVerse = onCurrentVerse,
            onPageChanged = {},
            onVerseSettled = onVerseSettled,
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
    onCurrentVerse: (Verse?) -> Unit,
    onPageChanged: (Int) -> Unit,
    onVerseSettled: (Verse) -> Unit,
) {
    val currentVerseAt by rememberUpdatedState(verseAt)
    val translationName = remember(state.translations, state.translationId) {
        state.translations.firstOrNull { it.id == state.translationId }?.name ?: state.translationId
    }

    // New pager per translation, jump or filter switch: a fresh pager opens straight on its start
    // page instead of scrolling there through the whole Bible.
    key(pagerKey) {
        val pagerState = rememberPagerState(initialPage = startPage) { pageCount }

        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect(onPageChanged)
        }
        LaunchedEffect(pagerState) {
            snapshotFlow { currentVerseAt(pagerState.currentPage) }.collect(onCurrentVerse)
        }
        LaunchedEffect(pagerState) {
            // Re-runs when the settled page's verse finishes loading, so the save isn't skipped.
            snapshotFlow { currentVerseAt(pagerState.settledPage) }
                .collect { verse -> if (verse != null) onVerseSettled(verse) }
        }

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
                )
            }
        }
    }
}

@Composable
private fun Verse.reference(bookNames: Map<Int, String>): String =
    stringResource(R.string.feed_reference_format, bookNames[ref.bookId].orEmpty(), ref.chapter, ref.verse)
