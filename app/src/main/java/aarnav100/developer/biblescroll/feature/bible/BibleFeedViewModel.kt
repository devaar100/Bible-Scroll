package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.compose.runtime.Immutable
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BibleFeedUiState {
    data object Loading : BibleFeedUiState

    /** Page index = verse ordinal - 1, so the pager spans the whole translation. */
    @Immutable
    data class Ready(
        val translationId: String,
        /** Changes on every translation switch or jump; the screen builds a fresh pager per key. */
        val pagerKey: String,
        val translations: List<Translation>,
        val pageCount: Int,
        /** Page to open on when this translation is (re)shown; only read when the pager is created. */
        val startPage: Int,
        /** Loaded verses around the current page; pages outside this window render empty. */
        val pages: Map<Int, Verse>,
        val bookNames: Map<Int, String>,
        val liked: Set<VerseRef>,
        val bookmarked: Set<VerseRef>,
        /** Verses with a note, for the rail's dot. */
        val noted: Set<VerseRef>,
        /** B2: non-null while the liked-only filter is on; the screen shows it instead of the main feed. */
        val likedFeed: LikedFeed?,
    ) : BibleFeedUiState
}

/** B2 liked-only feed: the verses liked when the filter was switched on, in Bible order. */
@Immutable
data class LikedFeed(
    val pagerKey: String,
    /** Null while loading. Empty means nothing is liked yet (show the hint). */
    val verses: List<Verse>?,
    val startPage: Int,
)

private data class Marks(val liked: Set<VerseRef>, val bookmarked: Set<VerseRef>, val noted: Set<VerseRef>)

private data class FeedSession(
    val translationId: String,
    val pageCount: Int,
    val startPage: Int,
    val key: String,
    /** The jump this session was built for, if any. */
    val jump: VerseRef?,
)

private data class PageWindow(val translationId: String, val pages: Map<Int, Verse>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BibleFeedViewModel @Inject constructor(
    private val bible: BibleRepository,
    private val userData: UserDataRepository,
    private val preferences: ReaderPreferences,
    private val verseLink: PendingVerseLink,
) : ViewModel() {

    /** A verse link (from the widget) waiting to be opened. */
    val pendingLink: StateFlow<VerseRef?> = verseLink.ref

    private val currentPage = MutableStateFlow(0)

    private val translations = bible.translations()

    /**
     * The verse the feed should open on next: the verse on screen, or a jump target. Null until the
     * first verse settles, so a cold start falls back to the saved reading position.
     */
    @Volatile
    private var anchor: VerseRef? = null

    /** Bumped by jumps so the session recomputes even when the translation is unchanged. */
    private val jumpSeq = MutableStateFlow(0L)

    /**
     * Jump target not yet on screen. While set, settles are ignored: they come from the pager that's
     * about to be replaced (e.g. the old pager re-shown when returning from another tab) and would
     * otherwise overwrite [anchor] before the session reads it.
     */
    @Volatile
    private var pendingJump: VerseRef? = null

    private val translationId = combine(preferences.translationId, translations) { preferred, available ->
        available.firstOrNull { it.id == preferred }?.id ?: available.firstOrNull()?.id
    }.distinctUntilChanged()

    /** Re-evaluated on a translation switch or a jump: resolves the anchor to a page. */
    private val session = combine(translationId, jumpSeq, ::Pair)
        .mapLatest { (translationId, seq) ->
            requireNotNull(translationId) { "Bundled Bible database has no translations" }
            val count = bible.verseCount(translationId)
            val jump = pendingJump
            val ref = jump ?: anchor ?: userData.lastReadingPosition() ?: VerseRef(1, 1, 1)
            // At-or-after: a verse missing from this translation (e.g. WEB Acts 8:37) lands on the next one.
            val ordinal = bible.ordinalAtOrAfter(translationId, ref) ?: count
            FeedSession(
                translationId = translationId,
                pageCount = count,
                startPage = (ordinal - 1).coerceIn(0, count - 1),
                key = "$translationId/$seq",
                jump = jump,
            )
        }
        .onEach { s ->
            currentPage.value = s.startPage
            if (s.jump != null) {
                anchor = s.jump
                if (pendingJump == s.jump) pendingJump = null
            }
        }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Chunk-aligned start of the loaded window, so small scrolls don't requery. */
    private val windowStartPage = currentPage
        .map { page -> ((page / CHUNK) - 1).coerceAtLeast(0) * CHUNK }
        .distinctUntilChanged()

    private val window = combine(session, windowStartPage, ::Pair)
        .flatMapLatest { (s, startPage) ->
            bible.versesFrom(s.translationId, fromOrdinal = startPage + 1, count = CHUNK * 3)
                .map { verses -> PageWindow(s.translationId, verses.associateBy { it.ordinal - 1 }) }
        }

    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }

    private val marks = combine(userData.likedVerses(), userData.bookmarkedVerses(), userData.notedVerses(), ::Marks)

    /**
     * B2: refs liked when the filter was switched on, or null when it's off. A snapshot (not the live
     * set) so unliking a verse inside the filter doesn't yank pages out from under the reader.
     */
    private val likedSnapshot = MutableStateFlow<LikedSnapshot?>(null)

    /** Verse on screen in the liked feed, so a translation switch keeps your place there. */
    @Volatile
    private var likedAnchor: VerseRef? = null

    private val likedFeed = combine(likedSnapshot, translationId, ::Pair)
        .transformLatest { (snapshot, translationId) ->
            if (snapshot == null || translationId == null) {
                emit(null)
                return@transformLatest
            }
            val key = "liked/$translationId/${snapshot.seq}"
            emit(LikedFeed(key, verses = null, startPage = 0))
            val verses = bible.versesByRefs(translationId, snapshot.refs)
            val start = likedAnchor?.let { anchor -> verses.indexOfFirst { it.ref == anchor } } ?: -1
            emit(LikedFeed(key, verses, startPage = start.coerceAtLeast(0)))
        }

    private val feed = combine(
        session, translations, window, bookNames, marks,
    ) { s, available, w, names, m ->
        BibleFeedUiState.Ready(
            translationId = s.translationId,
            pagerKey = s.key,
            translations = available,
            pageCount = s.pageCount,
            startPage = s.startPage,
            // Drop a stale window from the previous translation while the new one loads.
            pages = if (w.translationId == s.translationId) w.pages else emptyMap(),
            bookNames = names,
            liked = m.liked,
            bookmarked = m.bookmarked,
            noted = m.noted,
            likedFeed = null,
        )
    }

    val uiState: StateFlow<BibleFeedUiState> = combine(feed, likedFeed) { ready, liked ->
        ready.copy(likedFeed = liked)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BibleFeedUiState.Loading)

    fun onPageChanged(page: Int) {
        currentPage.value = page
    }

    /** Called with the verse the pager settled on, once it has loaded. */
    fun onVerseSettled(verse: Verse) {
        if (pendingJump != null) return
        anchor = verse.ref
        viewModelScope.launch { userData.saveReadingPosition(verse.translationId, verse.ref) }
    }

    /** Liked feed equivalent of [onVerseSettled]. Doesn't move the main feed or the reading position. */
    fun onLikedVerseSettled(verse: Verse) {
        likedAnchor = verse.ref
    }

    /** B4 tile: open [chapter] at its first verse. Leaves the liked filter if it's on. */
    fun jumpToChapter(bookId: Int, chapter: Int) = jumpTo(VerseRef(bookId, chapter, 1))

    /** B4 "Continue": back to where the reader left [bookId], or its start. */
    fun resumeBook(bookId: Int) {
        viewModelScope.launch {
            jumpTo(userData.readingPosition(bookId) ?: VerseRef(bookId, 1, 1))
        }
    }

    /** Jumps to a linked verse and marks the link handled. */
    fun openLinkedVerse(ref: VerseRef) {
        jumpTo(ref)
        verseLink.consume(ref)
    }

    private fun jumpTo(ref: VerseRef) {
        likedSnapshot.value = null
        pendingJump = ref
        anchor = ref
        jumpSeq.value++
    }

    /** Double-tap and the rail heart both toggle. */
    fun onToggleLike(ref: VerseRef) {
        val state = uiState.value as? BibleFeedUiState.Ready ?: return
        viewModelScope.launch { userData.setLiked(ref, ref !in state.liked) }
    }

    /** Top-bar bookmark: marks or unmarks the verse on screen. */
    fun onToggleBookmark(ref: VerseRef) {
        val state = uiState.value as? BibleFeedUiState.Ready ?: return
        viewModelScope.launch { userData.setBookmarked(ref, ref !in state.bookmarked) }
    }

    /**
     * B2 heart. On: snapshot the liked verses. Off: rebuild the main feed on [anchor], which the liked
     * feed never touches, so the reader lands on exactly the verse they left.
     */
    fun onToggleLikedFilter() {
        val state = uiState.value as? BibleFeedUiState.Ready ?: return
        if (likedSnapshot.value == null) {
            likedAnchor = null
            likedSnapshot.value = LikedSnapshot(state.liked, ++likedSeq)
        } else {
            likedSnapshot.value = null
            // Fresh pager key so the main feed is recreated at the anchor rather than its old start page.
            jumpSeq.value++
        }
    }

    private var likedSeq = 0L

    fun onSelectTranslation(id: String) {
        viewModelScope.launch { preferences.setTranslationId(id) }
    }

    private data class LikedSnapshot(val refs: Set<VerseRef>, val seq: Long)

    private companion object {
        /** Window = 3 chunks: the current page's chunk plus one either side. */
        const val CHUNK = 200
    }
}
