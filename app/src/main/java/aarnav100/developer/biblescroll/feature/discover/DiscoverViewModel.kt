package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.DiscoverFeed
import aarnav100.developer.biblescroll.core.content.DiscoverRepository
import aarnav100.developer.biblescroll.core.content.DiscoverTheme
import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.model.Language
import aarnav100.developer.biblescroll.core.content.net.NetworkMonitor
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.feature.study.studyHook
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    /** Never loaded and no connection. */
    data object Offline : DiscoverUiState
    data object Empty : DiscoverUiState

    @Immutable
    data class Ready(
        val cards: List<DiscoverCardRef>,
        /** Card to open on: the one last shown, so the feed keeps its place across tab switches. */
        val startIndex: Int,
    ) : DiscoverUiState
}

@Immutable
data class DiscoverCardRef(val ref: VerseRef, val theme: DiscoverTheme)

/** Source data (not AI) shown in the hook's place when Hide AI studies is on. */
@Immutable
data class StudyDataLine(val language: Language, val crossReferences: Int)

@Immutable
data class DiscoverCardUi(
    val ref: VerseRef,
    val theme: DiscoverTheme,
    /** "Jeremiah 17:8" in the app's language. */
    val reference: String,
    val translationId: String,
    val verse: Verse?,
    /** First sentence of the study's meaning, max 140 characters; null when there's no study or AI is hidden. */
    val hook: String?,
    /** The study's `didYouKnow`; null when empty, missing or AI is hidden. */
    val fact: String?,
    /** Hide AI studies on and the study is on the phone. */
    val dataLine: StudyDataLine?,
    val aiHidden: Boolean,
    val liked: Boolean,
    val bookmarked: Boolean,
    val hasNote: Boolean,
)

/**
 * D1 Discover feed. The list itself is curated Scripture, so it shows even with Hide AI studies on; then each card
 * leaves out the AI-written hook and "Did you know?", shows the source-data line instead, opens the verse in context
 * rather than the study, and nothing is prefetched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val discover: DiscoverRepository,
    private val studies: StudyRepository,
    private val network: NetworkMonitor,
    private val settings: SettingsRepository,
    private val bible: BibleRepository,
    private val userData: UserDataRepository,
    private val pendingVerseLink: PendingVerseLink,
    readerPreferences: ReaderPreferences,
) : ViewModel() {

    private val hidden = settings.settings.map { it.hideAiStudies }.distinctUntilChanged()
    private val translationId = readerPreferences.translationId
    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }

    /** Localized book names for the shared verse sheets (share image). */
    val bookNameMap: StateFlow<Map<Int, String>> = bookNames.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val marks = combine(userData.likedVerses(), userData.bookmarkedVerses(), userData.notedVerses()) { l, b, n -> Triple(l, b, n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Triple(emptySet(), emptySet(), emptySet()))

    val uiState: StateFlow<DiscoverUiState> = discover.feed.map { feed ->
        when (feed) {
            DiscoverFeed.Loading -> DiscoverUiState.Loading
            DiscoverFeed.NeverLoaded -> DiscoverUiState.Offline
            is DiscoverFeed.Ready -> if (feed.verses.isEmpty()) DiscoverUiState.Empty else {
                val cards = feed.verses.map { DiscoverCardRef(VerseRef(it.key.bookId, it.key.chapter, it.key.verse), it.theme) }
                val saved = savedState.get<String>(KEY_CURRENT)
                DiscoverUiState.Ready(cards, cards.indexOfFirst { it.ref.key() == saved }.coerceAtLeast(0))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverUiState.Loading)

    init {
        // The list (ids + themes) is fetched once per visit; it holds no AI text.
        viewModelScope.launch { discover.refresh() }
    }

    fun retry() {
        viewModelScope.launch { discover.refresh() }
    }

    /** One card. Never downloads: the hook and fact appear once the book's studies are on the phone. */
    fun card(card: DiscoverCardRef): Flow<DiscoverCardUi> {
        val ref = card.ref
        val verse = translationId.flatMapLatest { id -> bible.verse(id, ref).map { id to it } }
        return combine(verse, studies.storedStudy(ref.bookId, ref.chapter, ref.verse), bookNames, hidden, marks) { (id, v), study, names, aiHidden, (liked, bookmarked, noted) ->
            DiscoverCardUi(
                ref = ref,
                theme = card.theme,
                reference = References.verse(names[ref.bookId].orEmpty(), ref),
                translationId = v?.translationId ?: id,
                verse = v,
                hook = if (aiHidden) null else study?.let(::studyHook),
                fact = if (aiHidden) null else study?.didYouKnow?.trim()?.takeIf { it.isNotEmpty() },
                dataLine = if (aiHidden) study?.let { StudyDataLine(it.originalLanguage.language, it.crossReferences.size) } else null,
                aiHidden = aiHidden,
                liked = ref in liked,
                bookmarked = ref in bookmarked,
                hasNote = ref in noted,
            )
        }
    }

    fun toggleLike(ui: DiscoverCardUi) {
        viewModelScope.launch { userData.setLiked(ui.ref, !ui.liked) }
    }

    /** The same verse bookmark as the feed's top bar (verse_marks.bookmarked; listed in H6). */
    fun toggleBookmark(ui: DiscoverCardUi) {
        viewModelScope.launch { userData.setBookmarked(ui.ref, !ui.bookmarked) }
    }

    /** "Read in context": the app switches to the Bible tab and the feed opens at the verse. */
    fun readInContext(ref: VerseRef) {
        pendingVerseLink.post(ref)
    }

    /** The feed settled on [index]: remember it, and on Wi-Fi fetch the next few cards' books ahead (not when AI is hidden). */
    fun onCardShown(index: Int) {
        val cards = (uiState.value as? DiscoverUiState.Ready)?.cards ?: return
        val ref = cards.getOrNull(index)?.ref ?: return
        savedState[KEY_CURRENT] = ref.key()
        val upcoming = cards.drop(index + 1).take(PREFETCH_AHEAD).map { it.ref.bookId }
        if (upcoming.isEmpty()) return
        viewModelScope.launch {
            if (!hidden.first() && network.isUnmetered.first()) studies.prefetch(upcoming)
        }
    }

    private fun VerseRef.key() = "$bookId.$chapter.$verse"

    companion object {
        const val PREFETCH_AHEAD = 3
        private const val KEY_CURRENT = "discover_current_card"
    }
}
