package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.votd.LocalClock
import aarnav100.developer.biblescroll.data.votd.VerseOfTheDayRepository
import aarnav100.developer.biblescroll.data.votd.VotdTime
import aarnav100.developer.biblescroll.data.votd.VotdVerse
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

sealed interface VotdUiState {
    data object Loading : VotdUiState

    /** No entry for today, or the verse is in no bundled translation: the card isn't shown. */
    data object Hidden : VotdUiState

    @Immutable
    data class Ready(val votd: VotdVerse) : VotdUiState
}

/** H1 Verse of the Day card. Moves to the next day's verse at local midnight while open, and on every resume. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VerseOfTheDayViewModel @Inject constructor(
    private val repository: VerseOfTheDayRepository,
    bible: BibleRepository,
    private val clock: LocalClock,
    private val pendingVerseLink: PendingVerseLink,
) : ViewModel() {

    /** Bumped on resume to restart the day ticker, which re-reads the time zone. */
    private val resumes = MutableStateFlow(0)

    val today: Flow<LocalDate> = resumes.flatMapLatest { dayTicks() }.distinctUntilChanged()

    val uiState: StateFlow<VotdUiState> = today
        .flatMapLatest { date -> repository.forDate(date) }
        .map { votd -> if (votd == null) VotdUiState.Hidden else VotdUiState.Ready(votd) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VotdUiState.Loading)

    /** Localized book names for the shared verse sheets (share image, widget dialog). */
    val bookNames: StateFlow<Map<Int, String>> = bible.books()
        .map { books -> books.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onResume() {
        resumes.value++
    }

    /** "Read in context": the app switches to the Bible tab and the feed opens at the verse. */
    fun readInContext() {
        (uiState.value as? VotdUiState.Ready)?.let { pendingVerseLink.post(it.votd.verse.ref) }
    }

    /** Today's date now, then again just after each local midnight. */
    private fun dayTicks(): Flow<LocalDate> = flow {
        while (true) {
            val now = clock.now()
            emit(now.toLocalDate())
            val untilMidnight = Duration.between(now, VotdTime.nextLocalMidnight(now)).toMillis()
            delay(untilMidnight + TICK_SLACK_MS)
        }
    }

    private companion object {
        /** Lands safely past midnight; distinctUntilChanged drops a tick that hasn't crossed it yet. */
        const val TICK_SLACK_MS = 1_000L
    }
}
