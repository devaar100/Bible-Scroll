package aarnav100.developer.biblescroll.feature.bible.picker

import aarnav100.developer.biblescroll.ui.components.SheetDragHandle
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.Literata
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import aarnav100.developer.biblescroll.ui.components.TestTags
import aarnav100.developer.biblescroll.ui.components.automationTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * B3 book picker and B4 chapter grid in one full-height sheet. Tapping a book swaps the sheet's
 * content to that book's chapters; back returns to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookPickerSheet(
    state: BookPickerUiState,
    current: VerseRef?,
    onQueryChange: (String) -> Unit,
    onOpenBook: (Int) -> Unit,
    onBackToBooks: () -> Unit,
    onChapterSelected: (bookId: Int, chapter: Int) -> Unit,
    onContinue: (bookId: Int) -> Unit,
    onToggleChapterRead: (Int) -> Unit,
    onToggleBookRead: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen = rememberHideThen(sheetState, onDismiss)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // In the chapter grid, back returns to the book list instead of closing the sheet.
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = state.grid == null),
        modifier = Modifier.automationTag(TestTags.BOOK_PICKER),
        dragHandle = { SheetDragHandle() },
    ) {
        BackHandler(enabled = state.grid != null, onBack = onBackToBooks)
        AnimatedContent(
            targetState = state.grid,
            contentKey = { it?.book?.id },
            transitionSpec = {
                val forward = targetState != null
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "bookPickerContent",
        ) { grid ->
            if (grid == null) {
                BookList(
                    state = state,
                    currentBookId = current?.bookId,
                    onQueryChange = onQueryChange,
                    onOpenBook = onOpenBook,
                )
            } else {
                ChapterGrid(
                    grid = grid,
                    currentChapter = current?.takeIf { it.bookId == grid.book.id }?.chapter,
                    onBack = onBackToBooks,
                    onChapterSelected = { chapter -> hideThen { onChapterSelected(grid.book.id, chapter) } },
                    onContinue = { hideThen { onContinue(grid.book.id) } },
                    onToggleChapterRead = onToggleChapterRead,
                    onToggleBookRead = onToggleBookRead,
                )
            }
        }
    }
}

/** Animates the sheet away, then runs [action] and dismisses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberHideThen(sheetState: SheetState, onDismiss: () -> Unit): (() -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(sheetState, onDismiss) {
        { action ->
            action()
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B3: book list
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookList(
    state: BookPickerUiState,
    currentBookId: Int?,
    onQueryChange: (String) -> Unit,
    onOpenBook: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current

    // Open scrolled to the current book, once the list has loaded.
    var scrolledToCurrent by rememberSaveable { mutableStateOf(false) }
    val loaded = state.oldTestament.isNotEmpty() || state.newTestament.isNotEmpty()
    LaunchedEffect(loaded) {
        if (!loaded || scrolledToCurrent || state.query.isNotEmpty() || currentBookId == null) return@LaunchedEffect
        val ot = state.oldTestament.indexOfFirst { it.book.id == currentBookId }
        val nt = state.newTestament.indexOfFirst { it.book.id == currentBookId }
        // Indices count the two section headers.
        val index = when {
            ot >= 0 -> 1 + ot
            nt >= 0 -> 2 + state.oldTestament.size + nt
            else -> 0
        }
        listState.scrollToItem((index - 2).coerceAtLeast(0))
        scrolledToCurrent = true
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.picker_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.picker_search_clear))
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (!loaded && state.query.isNotEmpty()) {
            Text(
                text = stringResource(R.string.picker_no_match, state.query),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            return@Column
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            section(R.string.picker_old_testament, state.oldTestament, currentBookId, onOpenBook)
            section(R.string.picker_new_testament, state.newTestament, currentBookId, onOpenBook)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.section(
    title: Int,
    rows: List<BookRow>,
    currentBookId: Int?,
    onOpenBook: (Int) -> Unit,
) {
    // Keep the header slot even when empty so the scroll-to-current index math holds.
    stickyHeader(key = "header-$title") {
        if (rows.isNotEmpty()) SectionHeader(stringResource(title))
    }
    items(rows, key = { it.book.id }) { row ->
        BookRowItem(row, isCurrent = row.book.id == currentBookId, onClick = { onOpenBook(row.book.id) })
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun BookRowItem(row: BookRow, isCurrent: Boolean, onClick: () -> Unit) {
    val chapters = pluralStringResource(R.plurals.picker_chapter_count, row.book.chapterCount, row.book.chapterCount)
    val description = stringResource(R.string.picker_book_cd, row.book.name, chapters, row.readCount)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .clearAndSetSemantics {
                contentDescription = description
                selected = isCurrent
            },
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.book.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = Literata,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            Text(
                text = chapters,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ProgressRing(progress = row.progress)
    }
}

@Composable
private fun ProgressRing(progress: Float) {
    val success = BibleScrollTheme.extendedColors.success
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
        CircularProgressIndicator(
            progress = { progress },
            color = success,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            strokeWidth = 3.dp,
            gapSize = 0.dp,
            modifier = Modifier.size(28.dp),
        )
        if (progress >= 1f) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = success, modifier = Modifier.size(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B4: chapter grid
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChapterGrid(
    grid: ChapterGridState,
    currentChapter: Int?,
    onBack: () -> Unit,
    onChapterSelected: (Int) -> Unit,
    onContinue: () -> Unit,
    onToggleChapterRead: (Int) -> Unit,
    onToggleBookRead: () -> Unit,
) {
    val fullSpan: (LazyGridItemSpanScope.() -> GridItemSpan) =
        { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = fullSpan, key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.picker_back_to_books))
                }
                Column(Modifier.padding(start = 4.dp)) {
                    Text(
                        text = grid.book.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = stringResource(R.string.picker_read_progress, grid.read.size, grid.book.chapterCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        grid.savedPosition?.let { pos ->
            item(span = fullSpan, key = "continue") {
                val reference = stringResource(R.string.feed_reference_format, grid.book.name, pos.chapter, pos.verse)
                FilledTonalButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.picker_continue, reference))
                }
            }
        }

        items((1..grid.book.chapterCount).toList(), key = { it }) { chapter ->
            ChapterTile(
                chapter = chapter,
                read = chapter in grid.read,
                isCurrent = chapter == currentChapter,
                onClick = { onChapterSelected(chapter) },
                onLongClick = { onToggleChapterRead(chapter) },
            )
        }

        item(span = fullSpan, key = "footer") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
                TextButton(onClick = onToggleBookRead) {
                    Text(
                        stringResource(
                            if (grid.allRead) R.string.picker_mark_book_unread else R.string.picker_mark_book_read,
                            grid.book.name,
                        ),
                    )
                }
                Text(
                    text = stringResource(R.string.picker_long_press_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterTile(
    chapter: Int,
    read: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors = BibleScrollTheme.extendedColors
    val description = stringResource(if (read) R.string.picker_chapter_read_cd else R.string.picker_chapter_cd, chapter)
    val longClickLabel = stringResource(if (read) R.string.picker_mark_unread else R.string.picker_mark_read)
    val shape = RoundedCornerShape(12.dp)

    Surface(
        shape = shape,
        color = if (read) colors.successContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (read) colors.onSuccessContainer else MaterialTheme.colorScheme.onSurface,
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(if (read) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                    onLongClick()
                },
                onLongClickLabel = longClickLabel,
            )
            .semantics {
                contentDescription = description
                selected = isCurrent
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = chapter.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            )
            if (read) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(14.dp),
                )
            }
        }
    }
}
