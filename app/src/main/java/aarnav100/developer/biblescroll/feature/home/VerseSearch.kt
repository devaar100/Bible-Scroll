package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import aarnav100.developer.biblescroll.ui.theme.scriptureSp
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

/** H1 Verse Search: valid chapters and verses for the chosen book and chapter. Pure, so the ranges are easy to test. */
object VerseWheelRanges {
    /**
     * Keeps [wanted] inside the book's chapters and the chapter's verses. [verseCount] is for the clamped chapter, in
     * the reader's translation (3 John has 14 verses in KJV and 15 in WEB). Counts of 0 (not loaded yet) clamp to 1.
     */
    fun clamp(wanted: VerseRef, chapterCount: Int, verseCount: Int): VerseRef = VerseRef(
        wanted.bookId,
        wanted.chapter.coerceIn(1, max(1, chapterCount)),
        wanted.verse.coerceIn(1, max(1, verseCount)),
    )
}

@Immutable
data class VerseSearchUiState(
    val books: List<Book> = emptyList(),
    val chapterCount: Int = 0,
    val verseCount: Int = 0,
    /** Always valid for the counts above. */
    val selection: VerseRef = DEFAULT,
    val reference: String = "",
) {
    val bookIndex: Int get() = books.indexOfFirst { it.id == selection.bookId }.coerceAtLeast(0)

    companion object {
        /** John 3:16 until the reader has a reading place. */
        val DEFAULT = VerseRef(43, 3, 16)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VerseSearchViewModel @Inject constructor(
    private val bible: BibleRepository,
    readerPreferences: ReaderPreferences,
    userData: UserDataRepository,
    private val pendingVerseLink: PendingVerseLink,
) : ViewModel() {

    /** What the wheels asked for; the state clamps it to what exists. */
    private val wanted = MutableStateFlow(VerseSearchUiState.DEFAULT)

    private val chapters = combine(wanted.map { it.bookId }.distinctUntilChanged(), readerPreferences.translationId, ::Pair)
        .flatMapLatest { (book, translation) -> bible.chapters(translation, book) }

    val uiState: StateFlow<VerseSearchUiState> = combine(bible.books(), chapters, wanted) { books, chapterList, want ->
        val book = books.firstOrNull { it.id == want.bookId }
        val chapterCount = book?.chapterCount ?: 0
        val chapter = want.chapter.coerceIn(1, max(1, chapterCount))
        val verseCount = chapterList.firstOrNull { it.number == chapter }?.verseCount ?: 0
        val selection = VerseWheelRanges.clamp(want, chapterCount, verseCount)
        VerseSearchUiState(books, chapterCount, verseCount, selection, book?.let { References.verse(it.name, selection) }.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VerseSearchUiState())

    init {
        // Start where the reader last was.
        viewModelScope.launch { userData.lastReadingPosition()?.let { if (wanted.value == VerseSearchUiState.DEFAULT) wanted.value = it } }
    }

    fun selectBook(bookId: Int) {
        wanted.value = uiState.value.selection.copy(bookId = bookId)
    }

    fun selectChapter(chapter: Int) {
        wanted.value = uiState.value.selection.copy(chapter = chapter)
    }

    fun selectVerse(verse: Int) {
        wanted.value = uiState.value.selection.copy(verse = verse)
    }

    /** "Read": the feed opens at the verse (same path as Read in context and the widget). */
    fun read() = pendingVerseLink.post(uiState.value.selection)
}

/** H1 Verse Search card: book / chapter / verse wheels (or dropdowns with TalkBack on), Read and Study this verse. */
@Composable
fun VerseSearchCard(
    state: VerseSearchUiState,
    onSelectBook: (Int) -> Unit,
    onSelectChapter: (Int) -> Unit,
    onSelectVerse: (Int) -> Unit,
    onRead: () -> Unit,
    onStudy: () -> Unit,
    modifier: Modifier = Modifier,
    forceAccessiblePickers: Boolean? = null,
) {
    if (state.books.isEmpty()) return
    val accessible = forceAccessiblePickers ?: rememberTouchExploration()
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_verse_search),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() },
            )
            val bookNames = state.books.map { it.name }
            val chapters = (1..max(1, state.chapterCount)).map { it.toString() }
            val verses = (1..max(1, state.verseCount)).map { it.toString() }
            if (accessible) {
                AccessiblePicker(stringResource(R.string.home_wheel_book), bookNames, state.bookIndex) { onSelectBook(state.books[it].id) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccessiblePicker(stringResource(R.string.home_wheel_chapter), chapters, state.selection.chapter - 1, Modifier.weight(1f)) { onSelectChapter(it + 1) }
                    AccessiblePicker(stringResource(R.string.home_wheel_verse), verses, state.selection.verse - 1, Modifier.weight(1f)) { onSelectVerse(it + 1) }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WheelColumn(stringResource(R.string.home_wheel_book), bookNames, state.bookIndex, Modifier.weight(2f)) { onSelectBook(state.books[it].id) }
                    WheelColumn(stringResource(R.string.home_wheel_chapter), chapters, state.selection.chapter - 1, Modifier.weight(1f)) { onSelectChapter(it + 1) }
                    WheelColumn(stringResource(R.string.home_wheel_verse), verses, state.selection.verse - 1, Modifier.weight(1f)) { onSelectVerse(it + 1) }
                }
            }
            Text(
                state.reference,
                style = ScriptureTextStyle.copy(fontSize = scriptureSp(20.sp), color = MaterialTheme.colorScheme.onSurface),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            ActionRow(onRead, onStudy)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(onRead: () -> Unit, onStudy: () -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Button(onClick = onRead) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.home_verse_search_read))
        }
        OutlinedButton(onClick = onStudy) {
            Icon(Icons.Outlined.School, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.actions_study))
        }
    }
}

/**
 * One snapping wheel: three rows visible, the middle one selected. Row height follows the font scale. A haptic tick
 * marks each new row passing the middle; the choice is reported when scrolling stops. For TalkBack the wheel is one
 * item ("Book, John") with Previous/Next actions.
 */
@Composable
private fun WheelColumn(label: String, items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelected: (Int) -> Unit) {
    val rowHeight: Dp = with(LocalDensity.current) { (MaterialTheme.typography.bodyLarge.lineHeight * 1.7f).toDp() }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, max(0, items.lastIndex)))
    val fling = rememberSnapFlingBehavior(state, SnapPosition.Start)
    val haptics = LocalHapticFeedback.current
    val report by rememberUpdatedState(onSelected)
    val centered by remember(items.size) {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > rowPx / 2) 1 else 0).coerceIn(0, max(0, items.lastIndex))
        }
    }
    // Tick as each row reaches the middle while the person scrolls.
    LaunchedEffect(centered) { if (state.isScrollInProgress) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    // Report once the wheel settles.
    LaunchedEffect(state.isScrollInProgress) { if (!state.isScrollInProgress && centered != selected) report(centered) }
    // Follow changes from outside (another wheel clamped this one, or TalkBack actions).
    LaunchedEffect(selected, items.size) {
        val target = selected.coerceIn(0, max(0, items.lastIndex))
        if (!state.isScrollInProgress && centered != target) state.scrollToItem(target)
    }

    val value = items.getOrNull(selected).orEmpty()
    val spoken = stringResource(R.string.home_wheel_cd, label, value)
    val previous = stringResource(R.string.home_wheel_previous)
    val next = stringResource(R.string.home_wheel_next)
    Box(
        modifier
            .height(rowHeight * 3)
            .clearAndSetSemantics {
                contentDescription = spoken
                customActions = listOf(
                    CustomAccessibilityAction(previous) { if (selected > 0) { report(selected - 1); true } else false },
                    CustomAccessibilityAction(next) { if (selected < items.lastIndex) { report(selected + 1); true } else false },
                )
            },
    ) {
        // The selection band behind the middle row.
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().height(rowHeight).align(Alignment.Center),
        ) {}
        LazyColumn(state = state, flingBehavior = fling, contentPadding = PaddingValues(vertical = rowHeight)) {
            itemsIndexed(items) { i, item ->
                Box(Modifier.fillMaxWidth().height(rowHeight), contentAlignment = Alignment.Center) {
                    Text(
                        item,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (i == centered) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

/** With TalkBack on: a standard dropdown per field, which TalkBack reads and operates well. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessiblePicker(label: String, items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelected: (Int) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = items.getOrNull(selected).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { i, item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onSelected(i); expanded = false })
            }
        }
    }
}

/** True while TalkBack (touch exploration) is on; follows changes. */
@Composable
private fun rememberTouchExploration(): Boolean {
    val manager = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    var on by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { on = it }
        manager?.addTouchExplorationStateChangeListener(listener)
        onDispose { manager?.removeTouchExplorationStateChangeListener(listener) }
    }
    return on
}
