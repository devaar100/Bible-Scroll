package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.ContentStorage
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class StoredStudyBook(val bookId: Int, val name: String, val bundled: Boolean, val studies: Int, val bytes: Long)

@Immutable
data class StorageUiState(
    val loading: Boolean = true,
    val books: List<StoredStudyBook> = emptyList(),
    val removing: Boolean = false,
) {
    val totalBytes: Long get() = books.sumOf { it.bytes }
    val downloadedCount: Int get() = books.count { !it.bundled }
}

/** Settings → Storage: study content on this phone. */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storage: ContentStorage,
    bible: BibleRepository,
) : ViewModel() {

    private val removing = MutableStateFlow(false)
    private val _removed = Channel<Int>(Channel.BUFFERED)
    /** Number of books removed, for the confirmation snackbar. */
    val removed = _removed.receiveAsFlow()

    val uiState: StateFlow<StorageUiState> = combine(storage.books, bible.books(), removing) { stored, books, busy ->
        val names = books.associate { it.id to it.name }
        StorageUiState(
            loading = false,
            books = stored.map {
                StoredStudyBook(it.bookId, names[it.bookId] ?: it.code, it.source == BookSource.BUNDLED, it.verseCount, it.storedBytes)
            },
            removing = busy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    fun removeDownloaded() {
        if (removing.value) return
        viewModelScope.launch {
            removing.value = true
            try {
                _removed.send(storage.removeDownloaded())
            } finally {
                removing.value = false
            }
        }
    }
}
