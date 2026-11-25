package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.user.StudyMarks
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class StudyUiState(
    val ref: VerseRef,
    val bookName: String = "",
    val verseText: String? = null,
    /** The verse itself (for the header's red letters); null while loading. */
    val verse: Verse? = null,
    /** Settings → Hide AI studies is on. */
    val hidden: Boolean = false,
    val study: StudyState = StudyState.Loading(),
    val crossReferences: List<CrossReferenceUi> = emptyList(),
    val marks: StudyMarks = StudyMarks(),
    /** The reader's translation (verse text above the study); sent with "Report a problem". */
    val translationId: String = "",
)

/**
 * One verse's study, shared by D2 (Deep study route) and B8 (Verse study sheet over the feed): the study state
 * (downloading the book on demand with progress), cross references, saved/studied marks and their actions.
 *
 * [loadWhenHidden]: with Hide AI studies on, D2 shows only an explanation and never starts a download (false);
 * B8 still shows the study's data sections, so it loads (true).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudySession(
    val ref: VerseRef,
    private val scope: CoroutineScope,
    private val studies: StudyRepository,
    private val userData: UserDataRepository,
    private val settings: SettingsRepository,
    private val bible: BibleRepository,
    readerPreferences: ReaderPreferences,
    loadWhenHidden: Boolean,
) {
    private val hidden = settings.settings.map { it.hideAiStudies }.distinctUntilChanged()
    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }
    private val translationId = readerPreferences.translationId

    private val studyState: Flow<StudyState> = hidden.flatMapLatest { isHidden ->
        if (isHidden && !loadWhenHidden) flowOf(StudyState.Loading()) else studies.studyState(ref.bookId, ref.chapter, ref.verse)
    }

    private val crossReferences: Flow<List<CrossReferenceUi>> = combine(studyState, translationId, bookNames, ::Triple)
        .mapLatest { (state, translation, names) ->
            val study = (state as? StudyState.Available)?.study ?: return@mapLatest emptyList()
            study.crossReferences.mapNotNull { x ->
                val range = parseRefRange(x.ref) ?: return@mapNotNull null
                CrossReferenceUi(range.start, formatRefRange(range, names), bible.verse(translation, range.start).first()?.text, x.note)
            }
        }

    private val verse = translationId.flatMapLatest { bible.verse(it, ref) }

    val uiState: StateFlow<StudyUiState> = combine(
        hidden, studyState, crossReferences, userData.studyMarks(ref), combine(verse, bookNames, translationId, ::Triple),
    ) { isHidden, state, xrefs, marks, (v, names, translation) ->
        StudyUiState(ref, names[ref.bookId].orEmpty(), v?.text, v, isHidden, state, xrefs, marks, translation)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), StudyUiState(ref))

    init {
        // "Opening a study marks the verse studied" (H5): once the study is actually shown, and only if it isn't
        // already, so reopening doesn't rewrite the row. A study that couldn't load isn't counted.
        scope.launch {
            studyState.filter { it is StudyState.Available }.first()
            if (!userData.studyMarks(ref).first().studied) userData.setStudied(ref, true)
        }
    }

    fun toggleSaved() {
        val saved = uiState.value.marks.saved
        scope.launch { userData.setStudySaved(ref, !saved) }
    }

    fun toggleStudied() {
        val studied = uiState.value.marks.studied
        scope.launch { userData.setStudied(ref, !studied) }
    }

    /** Offline or a failed download: try again (a no-op if it's still offline; downloads also start when online). */
    fun retry() = studies.retry(ref.bookId)

    /** Turns Hide AI studies off. */
    fun showStudies() {
        scope.launch { settings.setHideAiStudies(false) }
    }

    class Factory @Inject constructor(
        private val studies: StudyRepository,
        private val userData: UserDataRepository,
        private val settings: SettingsRepository,
        private val bible: BibleRepository,
        private val readerPreferences: ReaderPreferences,
    ) {
        fun create(ref: VerseRef, scope: CoroutineScope, loadWhenHidden: Boolean) =
            StudySession(ref, scope, studies, userData, settings, bible, readerPreferences, loadWhenHidden)
    }
}
