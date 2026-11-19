package aarnav100.developer.biblescroll.feature.bible.picker

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.normalizeForSearch
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class BookRow(val book: Book, val readCount: Int) {
    val progress: Float get() = if (book.chapterCount == 0) 0f else readCount.toFloat() / book.chapterCount
}

@Immutable
data class ChapterGridState(
    val book: Book,
    val read: Set<Int>,
    /** Where the reader left this book, for the Continue button; null if never opened. */
    val savedPosition: VerseRef?,
) {
    val allRead: Boolean get() = read.size >= book.chapterCount
}

@Immutable
data class BookPickerUiState(
    val query: String = "",
    val oldTestament: List<BookRow> = emptyList(),
    val newTestament: List<BookRow> = emptyList(),
    /** Non-null while B4 (the chapter grid) is showing. */
    val grid: ChapterGridState? = null,
)

/** State for the B3 book picker and B4 chapter grid, which share one sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookPickerViewModel @Inject constructor(
    bible: BibleRepository,
    private val userData: UserDataRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedBookId = MutableStateFlow<Int?>(null)

    private val books = bible.books()
    private val readChapters = userData.readChapters()

    private val savedPosition = selectedBookId.flatMapLatest { id ->
        if (id == null) flowOf(null) else userData.observeReadingPosition(id)
    }

    val uiState: StateFlow<BookPickerUiState> = combine(
        books, readChapters, query, selectedBookId, savedPosition,
    ) { books, read, query, selectedId, position ->
        val rows = books
            .filter { matchesBookQuery(it, query) }
            .map { BookRow(it, read[it.id]?.size ?: 0) }
        BookPickerUiState(
            query = query,
            oldTestament = rows.filter { it.book.testament == Testament.OLD },
            newTestament = rows.filter { it.book.testament == Testament.NEW },
            grid = books.firstOrNull { it.id == selectedId }?.let { book ->
                ChapterGridState(book, read[book.id].orEmpty(), position)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookPickerUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun openBook(bookId: Int) {
        selectedBookId.value = bookId
    }

    fun backToBooks() {
        selectedBookId.value = null
    }

    /** Sheet closed: next time it opens on a fresh book list. */
    fun reset() {
        query.value = ""
        selectedBookId.value = null
    }

    fun toggleChapterRead(chapter: Int) {
        val grid = uiState.value.grid ?: return
        viewModelScope.launch {
            userData.setChaptersRead(grid.book.id, listOf(chapter), read = chapter !in grid.read)
        }
    }

    /** "Mark [Book] as read", or as unread once every chapter is read. */
    fun toggleBookRead() {
        val grid = uiState.value.grid ?: return
        viewModelScope.launch {
            userData.setChaptersRead(grid.book.id, (1..grid.book.chapterCount).toList(), read = !grid.allRead)
        }
    }
}

/**
 * Live search for B3 and H13. Ignores case, accents, spaces and punctuation, and matches anywhere in the book's
 * name, abbreviation or search aliases in the app's language, its English name, or its USFM code, so "1 cor",
 * "1co" and "primera de corintios" all find 1 Corinthians, and "genesis" finds "Génesis".
 */
internal fun matchesBookQuery(book: Book, query: String): Boolean =
    matchesBookQuery(book.searchTerms.ifEmpty { listOf(normalizeForSearch(book.name)) } + normalizeForSearch(book.code), query)

internal fun matchesBookQuery(name: String, code: String, query: String): Boolean =
    matchesBookQuery(listOf(normalizeForSearch(name), normalizeForSearch(code)), query)

private fun matchesBookQuery(terms: List<String>, query: String): Boolean {
    val q = normalizeForSearch(query)
    return q.isEmpty() || terms.any { q in it }
}
