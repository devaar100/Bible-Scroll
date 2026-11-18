package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface VerseNoteUiState {
    data object Closed : VerseNoteUiState

    @Immutable
    data class Open(
        /** The verse as shown when the sheet opened (its text heads the sheet). */
        val verse: Verse,
        /** Null while the saved note loads; the editor is created once with this text. */
        val initialText: String?,
        val hasSavedNote: Boolean,
    ) : VerseNoteUiState {
        val ref: VerseRef get() = verse.ref
    }
}

/**
 * B6 verse notes. The text field owns the live text; this saves it 500 ms after the last edit, and
 * immediately when the sheet closes. A blank note deletes the row.
 */
@HiltViewModel
class VerseNoteViewModel @Inject constructor(
    private val userData: UserDataRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<VerseNoteUiState>(VerseNoteUiState.Closed)
    val uiState: StateFlow<VerseNoteUiState> = _uiState.asStateFlow()

    /** Latest text not yet written, or null when everything is saved. */
    private var pending: String? = null
    private var saveJob: Job? = null
    private var loadJob: Job? = null

    /** FIFO lock so writes land in the order they were made (a delete never beats an earlier save). */
    private val writes = Mutex()

    fun open(verse: Verse) {
        flush()
        _uiState.value = VerseNoteUiState.Open(verse, initialText = null, hasSavedNote = false)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val saved = userData.note(verse.ref)
            _uiState.value = VerseNoteUiState.Open(verse, initialText = saved.orEmpty(), hasSavedNote = saved != null)
        }
    }

    fun onTextChange(text: String) {
        if (currentRef() == null) return
        pending = text
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            saveJob = null
            flush()
        }
    }

    /** Sheet dismissed: save whatever is pending right away, then close. */
    fun close() {
        flush()
        loadJob?.cancel()
        _uiState.value = VerseNoteUiState.Closed
    }

    fun delete() {
        val ref = currentRef() ?: return
        saveJob?.cancel()
        pending = null
        loadJob?.cancel()
        _uiState.value = VerseNoteUiState.Closed
        viewModelScope.launch { writes.withLock { userData.deleteNote(ref) } }
    }

    private fun flush() {
        saveJob?.cancel()
        saveJob = null
        val ref = currentRef() ?: return
        val text = pending ?: return
        pending = null
        val state = _uiState.value as VerseNoteUiState.Open
        _uiState.value = state.copy(hasSavedNote = text.isNotBlank())
        viewModelScope.launch { writes.withLock { userData.saveNote(ref, text) } }
    }

    private fun currentRef() = (_uiState.value as? VerseNoteUiState.Open)?.ref

    internal companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
