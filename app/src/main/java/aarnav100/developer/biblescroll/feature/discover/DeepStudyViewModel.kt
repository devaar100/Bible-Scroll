package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.user.StudyMarks
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.feature.study.CrossReferenceUi
import aarnav100.developer.biblescroll.feature.study.formatRefRange
import aarnav100.developer.biblescroll.feature.study.parseRefRange
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
data class DeepStudyUiState(
    val ref: VerseRef,
    val bookName: String = "",
    val verseText: String? = null,
    /** True when Settings → Hide AI studies is on: show the explanation instead. */
    val hidden: Boolean = false,
    val study: StudyState = StudyState.Loading(),
    val crossReferences: List<CrossReferenceUi> = emptyList(),
    val marks: StudyMarks = StudyMarks(),
)

/** D2 Deep study (B8 layout) for one verse. Route args: bookId, chapter, verse (see DeepStudyRoute). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DeepStudyViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val studies: StudyRepository,
    private val userData: UserDataRepository,
    private val settings: SettingsRepository,
    private val bible: BibleRepository,
    readerPreferences: ReaderPreferences,
    private val pendingVerseLink: PendingVerseLink,
) : ViewModel() {

    val ref = VerseRef(
        checkNotNull(savedState.get<Int>("bookId")),
        checkNotNull(savedState.get<Int>("chapter")),
        checkNotNull(savedState.get<Int>("verse")),
    )

    private val hidden = settings.settings.map { it.hideAiStudies }.distinctUntilChanged()
    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }
    private val translationId = readerPreferences.translationId

    /** Not collected while studies are hidden, so opening D2 then never starts a download. */
    private val studyState: Flow<StudyState> = hidden.flatMapLatest { isHidden ->
        if (isHidden) flowOf(StudyState.Loading()) else studies.studyState(ref.bookId, ref.chapter, ref.verse)
    }

    private val crossReferences: Flow<List<CrossReferenceUi>> = combine(studyState, translationId, bookNames, ::Triple)
        .mapLatest { (state, translation, names) ->
            val study = (state as? StudyState.Available)?.study ?: return@mapLatest emptyList()
            study.crossReferences.mapNotNull { x ->
                val range = parseRefRange(x.ref) ?: return@mapNotNull null
                CrossReferenceUi(range.start, formatRefRange(range, names), bible.verse(translation, range.start).first()?.text, x.note)
            }
        }

    private val verseText = translationId.flatMapLatest { bible.verse(it, ref) }.map { it?.text }

    val uiState: StateFlow<DeepStudyUiState> = combine(
        hidden, studyState, crossReferences, userData.studyMarks(ref), combine(verseText, bookNames, ::Pair),
    ) { isHidden, state, xrefs, marks, (text, names) ->
        DeepStudyUiState(ref, names[ref.bookId].orEmpty(), text, isHidden, state, xrefs, marks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeepStudyUiState(ref))

    init {
        // "Opening a deep study marks the verse as studied": once the study is actually on screen, so a
        // study that couldn't load (offline, unpublished, hidden) isn't counted.
        viewModelScope.launch {
            studyState.filter { it is StudyState.Available }.first()
            userData.setStudied(ref, true)
        }
    }

    fun toggleSaved() {
        val saved = uiState.value.marks.saved
        viewModelScope.launch { userData.setStudySaved(ref, !saved) }
    }

    fun toggleStudied() {
        val studied = uiState.value.marks.studied
        viewModelScope.launch { userData.setStudied(ref, !studied) }
    }

    fun retry() = studies.retry(ref.bookId)

    fun showStudies() {
        viewModelScope.launch { settings.setHideAiStudies(false) }
    }

    /** "Read in context": the Bible tab opens on this verse (same path as a widget tap). */
    fun readInContext(target: VerseRef = ref) = pendingVerseLink.post(target)
}
