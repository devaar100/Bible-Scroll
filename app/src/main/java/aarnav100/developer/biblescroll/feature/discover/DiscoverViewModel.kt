package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.core.content.DiscoverFeed
import aarnav100.developer.biblescroll.core.content.DiscoverRepository
import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.feature.study.studyTeaser
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    /** Settings → Hide AI studies is on. */
    data object Hidden : DiscoverUiState
    /** Never loaded and no connection. */
    data object Offline : DiscoverUiState
    data object Empty : DiscoverUiState

    @Immutable
    data class Ready(
        val cards: List<VerseRef>,
        /** Card to open on: the one last shown, so the feed keeps its place across tab switches. */
        val startIndex: Int,
    ) : DiscoverUiState
}

@Immutable
data class DiscoverCardUi(val reference: String, val verseText: String?, val teaser: String?)

/** D1 Discover feed. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val discover: DiscoverRepository,
    private val studies: StudyRepository,
    private val network: NetworkMonitor,
    private val settings: SettingsRepository,
    private val bible: BibleRepository,
    readerPreferences: ReaderPreferences,
) : ViewModel() {

    private val hidden = settings.settings.map { it.hideAiStudies }.distinctUntilChanged()
    private val translationId = readerPreferences.translationId
    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }

    val uiState: StateFlow<DiscoverUiState> = hidden.flatMapLatest { isHidden ->
        if (isHidden) kotlinx.coroutines.flow.flowOf(DiscoverUiState.Hidden)
        else discover.feed.map { feed ->
            when (feed) {
                DiscoverFeed.Loading -> DiscoverUiState.Loading
                DiscoverFeed.NeverLoaded -> DiscoverUiState.Offline
                is DiscoverFeed.Ready -> if (feed.verses.isEmpty()) DiscoverUiState.Empty else {
                    val cards = feed.verses.map { VerseRef(it.bookId, it.chapter, it.verse) }
                    val saved = savedState.get<String>(KEY_CURRENT)
                    DiscoverUiState.Ready(cards, cards.indexOfFirst { it.key() == saved }.coerceAtLeast(0))
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState.Loading)

    init {
        // Fetch the latest list once per visit unless studies are hidden (then nothing is downloaded).
        viewModelScope.launch {
            hidden.filter { !it }.first()
            discover.refresh()
        }
    }

    fun retry() {
        viewModelScope.launch { discover.refresh() }
    }

    fun showStudies() {
        viewModelScope.launch { settings.setHideAiStudies(false) }
    }

    /** One card's verse and teaser. Never downloads: the teaser appears once the book is on the phone. */
    fun card(ref: VerseRef): Flow<DiscoverCardUi> = combine(
        translationId.flatMapLatest { bible.verse(it, ref) },
        studies.storedStudy(ref.bookId, ref.chapter, ref.verse),
        bookNames,
    ) { verse, study, names ->
        DiscoverCardUi(References.verse(names[ref.bookId].orEmpty(), ref), verse?.text, study?.let(::studyTeaser))
    }

    /** The feed settled on [index]: remember it, and on Wi-Fi fetch the next few cards' books ahead. */
    fun onCardShown(index: Int) {
        val cards = (uiState.value as? DiscoverUiState.Ready)?.cards ?: return
        val ref = cards.getOrNull(index) ?: return
        savedState[KEY_CURRENT] = ref.key()
        val upcoming = cards.drop(index + 1).take(PREFETCH_AHEAD).map { it.bookId }
        if (upcoming.isEmpty()) return
        viewModelScope.launch {
            if (network.isUnmetered.first()) studies.prefetch(upcoming)
        }
    }

    private fun VerseRef.key() = "$bookId.$chapter.$verse"

    companion object {
        const val PREFETCH_AHEAD = 3
        private const val KEY_CURRENT = "discover_current_card"
    }
}
