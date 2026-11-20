package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.bible.VerseNoteSheet
import aarnav100.developer.biblescroll.feature.bible.VerseNoteUiState
import aarnav100.developer.biblescroll.feature.bible.VerseNoteViewModel
import aarnav100.developer.biblescroll.feature.bible.VerseSheets
import aarnav100.developer.biblescroll.feature.bible.copyVerse
import aarnav100.developer.biblescroll.feature.bible.reference
import aarnav100.developer.biblescroll.feature.bible.sheets.VerseSheetsViewModel
import aarnav100.developer.biblescroll.feature.study.StudyMessage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlin.math.abs

/** D1 Discover: a vertical feed of curated verse cards. Tap a card for its deep study (D2). */
@Composable
fun DiscoverScreen(
    onOpenStudy: (VerseRef) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel(),
    noteViewModel: VerseNoteViewModel = hiltViewModel(),
    sheetsViewModel: VerseSheetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bookNames by viewModel.bookNameMap.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.feed_copied)
    // Same copy format and confirmation as B7 in the feed.
    val copyAndConfirm: (Verse, String, SnackbarHostState) -> Unit = remember(context, scope, copied) {
        { verse, reference, host ->
            context.copyVerse(verse, reference)
            scope.launch {
                host.currentSnackbarData?.dismiss()
                host.showSnackbar(copied, duration = SnackbarDuration.Short)
            }
        }
    }
    val actions = remember(viewModel, noteViewModel, sheetsViewModel, copyAndConfirm) {
        DiscoverCardActions(
            onOpen = { onOpenStudy(it.ref) },
            onReadInContext = { viewModel.readInContext(it.ref) },
            onLike = viewModel::toggleLike,
            onNote = { ui -> ui.verse?.let(noteViewModel::open) },
            onBookmark = viewModel::toggleBookmark,
            onCopy = { ui -> ui.verse?.let { copyAndConfirm(it, ui.reference, snackbar) } },
            onShareImage = { ui -> ui.verse?.let(sheetsViewModel::openShareImage) },
        )
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            DiscoverUiState.Loading -> CircularProgressIndicator()
            DiscoverUiState.Offline -> Centered(Icons.Outlined.CloudOff) {
                StudyMessage(
                    stringResource(R.string.discover_offline_title), stringResource(R.string.discover_offline_body),
                    action = stringResource(R.string.study_retry), onAction = viewModel::retry,
                )
            }
            DiscoverUiState.Empty -> Centered(Icons.Outlined.Explore) {
                StudyMessage(stringResource(R.string.discover_empty_title), stringResource(R.string.discover_empty_body))
            }
            is DiscoverUiState.Ready -> key(s.cards) {
                // Created once per card list, starting on the card last shown (kept by the view model).
                val pager = rememberPagerState(initialPage = s.startIndex) { s.cards.size }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect(viewModel::onCardShown)
                }
                DiscoverPager(pager, s.cards.size, key = { s.cards[it].hashCode() }) { page ->
                    val ref = s.cards[page]
                    val data = remember(ref) { viewModel.card(ref) }
                    val ui by data.collectAsStateWithLifecycle(initialValue = null)
                    DiscoverCard(ui, actions, Modifier.fillMaxSize())
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    VerseSheets(viewModel = sheetsViewModel, bookNames = bookNames, screenSnackbar = snackbar, copyAndConfirm = copyAndConfirm)
    val noteState by noteViewModel.uiState.collectAsStateWithLifecycle()
    (noteState as? VerseNoteUiState.Open)?.let { open ->
        VerseNoteSheet(
            state = open,
            reference = open.verse.reference(bookNames),
            onTextChange = noteViewModel::onTextChange,
            onDelete = noteViewModel::delete,
            onDismiss = noteViewModel::close,
        )
    }
}

/**
 * The card is [CARD_FRACTION] of the pager's height, centred, 16dp from the sides; the next card's top edge peeks
 * out about [PEEK] below it, drawn slightly smaller so it reads as behind.
 */
@Composable
fun DiscoverPager(pager: PagerState, count: Int, key: (Int) -> Any, content: @Composable (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardHeight = maxHeight * CARD_FRACTION
        val gap = (maxHeight - cardHeight) / 2
        val spacing = maxOf(12.dp, gap - PEEK)
        VerticalPager(
            state = pager,
            pageSize = PageSize.Fixed(cardHeight),
            contentPadding = PaddingValues(top = gap, bottom = gap),
            pageSpacing = spacing,
            beyondViewportPageCount = 1,
            key = key,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            // > 0: below the current card; < 0: above (leaving).
            val offset = (page - pager.currentPage) - pager.currentPageOffsetFraction
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        val t = abs(offset).coerceIn(0f, 1f)
                        val scale = lerp(1f, NEXT_SCALE, t)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, if (offset > 0) 0f else 1f)
                        alpha = if (offset < 0) 1f - t else 1f
                    },
            ) { content(page) }
        }
    }
}

const val CARD_FRACTION = 0.74f
val PEEK: Dp = 58.dp
private const val NEXT_SCALE = 0.94f

@Composable
private fun Centered(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}
