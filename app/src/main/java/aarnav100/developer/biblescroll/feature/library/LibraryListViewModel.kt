package aarnav100.developer.biblescroll.feature.library

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.backup.BackupNotices
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.library.LibraryList
import aarnav100.developer.biblescroll.data.library.LibraryRepository
import aarnav100.developer.biblescroll.data.library.LibrarySort
import aarnav100.developer.biblescroll.data.library.Removal
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The four Library screens; H6 shows Liked or Bookmarked through a filter chip. */
enum class LibraryScreen { SAVED_STUDIES, STUDIED, LIKED_AND_BOOKMARKED, NOTES }

enum class MarkFilter { LIKED, BOOKMARKED }

@Immutable
data class LibraryRow(val ref: VerseRef, val reference: String, val verseText: String?, val note: String?)

@Immutable
data class LibraryListUiState(
    val screen: LibraryScreen,
    val loading: Boolean = true,
    val sort: LibrarySort = LibrarySort.BIBLE,
    val filter: MarkFilter = MarkFilter.LIKED,
    val query: String = "",
    val rows: List<LibraryRow> = emptyList(),
) {
    val list: LibraryList get() = screen.listFor(filter)
}

fun LibraryScreen.listFor(filter: MarkFilter): LibraryList = when (this) {
    LibraryScreen.SAVED_STUDIES -> LibraryList.SAVED_STUDIES
    LibraryScreen.STUDIED -> LibraryList.STUDIED
    LibraryScreen.NOTES -> LibraryList.NOTES
    LibraryScreen.LIKED_AND_BOOKMARKED -> if (filter == MarkFilter.LIKED) LibraryList.LIKED else LibraryList.BOOKMARKED
}

/** Where tapping a row goes: a study (D2, the B8 layout) or the verse in the Bible feed. */
sealed interface LibraryOpen {
    data class Study(val ref: VerseRef) : LibraryOpen
    data class Verse(val ref: VerseRef) : LibraryOpen
}

/** H4-H7. Route arg "screen" = LibraryScreen name. Sort, filter and search survive process death. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryListViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val bible: BibleRepository,
    readerPreferences: ReaderPreferences,
    private val pendingVerseLink: PendingVerseLink,
    private val notices: BackupNotices,
) : ViewModel() {

    val screen: LibraryScreen = LibraryScreen.valueOf(checkNotNull(savedState.get<String>("screen")))

    private val sort = savedState.getStateFlow(KEY_SORT, LibrarySort.BIBLE.name).map(LibrarySort::valueOf)
    private val filter = savedState.getStateFlow(KEY_FILTER, MarkFilter.LIKED.name).map(MarkFilter::valueOf)
    private val query = savedState.getStateFlow(KEY_QUERY, "")
    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }

    private val rows = combine(sort, filter, query, ::Triple)
        .flatMapLatest { (s, f, q) -> library.entries(screen.listFor(f), s, if (screen == LibraryScreen.NOTES) q else "") }
        .let { entries -> combine(entries, readerPreferences.translationId, bookNames, ::Triple) }
        .mapLatest { (entries, translation, names) ->
            // One query for every verse's text, in the reader's translation.
            val texts = bible.versesByRefs(translation, entries.map { it.ref }).associate { it.ref to it.text }
            entries.map { e ->
                LibraryRow(e.ref, References.verse(names[e.ref.bookId].orEmpty(), e.ref), texts[e.ref], e.note)
            }
        }

    val uiState: StateFlow<LibraryListUiState> = combine(sort, filter, query, rows) { s, f, q, r ->
        LibraryListUiState(screen, loading = false, sort = s, filter = f, query = q, rows = r)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryListUiState(screen))

    /** H7 only: once there are [NOTES_TIP_AT] notes, a one-time tip to export a copy (dismissible). */
    val showNotesTip: StateFlow<Boolean> =
        if (screen != LibraryScreen.NOTES) {
            MutableStateFlow(false)
        } else {
            combine(library.counts(), notices.notesTipDismissed) { counts, dismissed -> counts.notes >= NOTES_TIP_AT && !dismissed }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
        }

    fun dismissNotesTip() {
        viewModelScope.launch { notices.dismissNotesTip() }
    }

    fun setSort(value: LibrarySort) { savedState[KEY_SORT] = value.name }
    fun setFilter(value: MarkFilter) { savedState[KEY_FILTER] = value.name }
    fun setQuery(value: String) { savedState[KEY_QUERY] = value }

    /** Studies open the study page; liked, bookmarked and noted verses open in the Bible feed. */
    fun open(ref: VerseRef): LibraryOpen = when (screen) {
        LibraryScreen.SAVED_STUDIES, LibraryScreen.STUDIED -> LibraryOpen.Study(ref)
        else -> LibraryOpen.Verse(ref).also { pendingVerseLink.post(ref) }
    }

    /** Swipe to remove. Returns what Undo needs (null if the item had already gone). */
    suspend fun remove(ref: VerseRef): Removal? = library.remove(uiState.value.list, ref)

    fun undo(removal: Removal) {
        viewModelScope.launch { library.undo(removal) }
    }

    companion object {
        const val NOTES_TIP_AT = 5
        private const val KEY_SORT = "library_sort"
        private const val KEY_FILTER = "library_filter"
        private const val KEY_QUERY = "library_query"
    }
}
