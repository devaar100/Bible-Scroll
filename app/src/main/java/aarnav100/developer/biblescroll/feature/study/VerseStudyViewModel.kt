package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * B8 Verse study, a sheet over the Bible feed (and over Home for the Verse of the Day). Holds at most one open
 * [StudySession]; closing the sheet cancels it. Opening or closing never touches the feed: only "Go to verse" moves
 * it, through the same [PendingVerseLink] path as Read in context.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VerseStudyViewModel @Inject constructor(
    private val sessions: StudySession.Factory,
    private val pendingVerseLink: PendingVerseLink,
) : ViewModel() {

    private class Open(val session: StudySession, val scope: CoroutineScope)

    private val open = MutableStateFlow<Open?>(null)

    /** Null while no study is open (no sheet). */
    val uiState: StateFlow<StudyUiState?> = open
        .flatMapLatest { it?.session?.uiState ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val isOpen: Boolean get() = open.value != null

    /** "Study this verse" (B7, Verse of the Day card). B8 still shows the data sections with Hide AI studies on. */
    fun open(ref: VerseRef) {
        if (open.value?.session?.ref == ref) return
        close()
        val scope = CoroutineScope(viewModelScope.coroutineContext + SupervisorJob(viewModelScope.coroutineContext[Job]))
        open.value = Open(sessions.create(ref, scope, loadWhenHidden = true), scope)
    }

    /** Close, back or predictive back: the feed stays exactly where it was. */
    fun close() {
        open.value?.scope?.cancel()
        open.value = null
    }

    /** A cross reference's "Go to verse": the feed jumps there (the one action that moves it), then the sheet closes. */
    fun goToVerse(target: VerseRef) {
        pendingVerseLink.post(target)
        close()
    }

    fun toggleSaved() = open.value?.session?.toggleSaved()
    fun retry() = open.value?.session?.retry()
    fun showStudies() = open.value?.session?.showStudies()

    override fun onCleared() = close()
}
