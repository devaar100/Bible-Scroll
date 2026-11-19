package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.passageVerses
import aarnav100.developer.biblescroll.data.bible.resolveTranslation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.plans.PassageFile
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.ui.navigation.PlanReaderRoute
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One page of the H11 reader. */
@Immutable
sealed interface ReaderPage {
    data class Intro(val dayNumber: Int, val totalDays: Int, val title: String?, val intro: String?, val passages: List<String>, val minutes: Int) : ReaderPage
    data class VersePage(val verse: Verse) : ReaderPage
    data class Finish(val dayNumber: Int, val read: Boolean, val nextDay: Int?) : ReaderPage
}

sealed interface PlanReaderUiState {
    data object Loading : PlanReaderUiState
    data object NotFound : PlanReaderUiState

    @Immutable
    data class Ready(
        val enrollmentId: String,
        /** The plan's id and the version of the copy saved when it was started (sent with "Report a problem"). */
        val planId: String,
        val planContentVersion: String,
        val planTitle: String,
        val dayNumber: Int,
        val totalDays: Int,
        val translationId: String,
        val translationName: String,
        val bookNames: Map<Int, String>,
        /** Intro, then the day's verses in Bible order, then the finish page. */
        val pages: List<ReaderPage>,
        val liked: Set<VerseRef>,
        val bookmarked: Set<VerseRef>,
        val noted: Set<VerseRef>,
    ) : PlanReaderUiState
}

sealed interface PlanReaderEvent {
    /** The last unread day was just read: show the completion screen. */
    data class Completed(val enrollmentId: String) : PlanReaderEvent
    data class OpenDay(val enrollmentId: String, val dayNumber: Int) : PlanReaderEvent
}

private data class DayContent(val translationId: String, val verses: List<Verse>)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanReaderViewModel internal constructor(
    private val route: PlanReaderRoute,
    private val plans: PlanRepository,
    private val bible: BibleRepository,
    private val userData: UserDataRepository,
    preferences: ReaderPreferences,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        plans: PlanRepository,
        bible: BibleRepository,
        userData: UserDataRepository,
        preferences: ReaderPreferences,
    ) : this(savedStateHandle.toRoute<PlanReaderRoute>(), plans, bible, userData, preferences)

    private val _events = MutableSharedFlow<PlanReaderEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlanReaderEvent> = _events
    private val marking = MutableStateFlow(false)

    private val enrollment = plans.enrollment(route.enrollmentId)

    /** The day's verses, reloaded only when the translation (or the day's passages) change. */
    private val content = combine(enrollment, preferences.translationId) { e, t -> e?.file?.days?.firstOrNull { it.dayNumber == route.dayNumber }?.passages to t }
        .distinctUntilChanged()
        .mapLatest { (passages, preferred) ->
            passages ?: return@mapLatest null
            val translationId = bible.resolveTranslation(preferred)
            DayContent(translationId, passages.flatMap { versesOf(translationId, it) })
        }

    val uiState: StateFlow<PlanReaderUiState> = combine(
        enrollment, content, bible.books(), bible.translations(),
        combine(userData.likedVerses(), userData.bookmarkedVerses(), userData.notedVerses(), ::Triple),
    ) { e, day, books, translations, (liked, bookmarked, noted) ->
        val detail = e ?: return@combine PlanReaderUiState.NotFound
        val planDay = detail.file.days.firstOrNull { it.dayNumber == route.dayNumber } ?: return@combine PlanReaderUiState.NotFound
        val loaded = day ?: return@combine PlanReaderUiState.Loading
        val names = books.associate { it.id to it.name }
        val read = route.dayNumber in detail.done
        val next = (1..detail.plan.totalDays).firstOrNull { it !in detail.done && it != route.dayNumber }
        PlanReaderUiState.Ready(
            enrollmentId = route.enrollmentId,
            planId = detail.file.id,
            planContentVersion = detail.file.contentVersion,
            planTitle = detail.plan.title,
            dayNumber = route.dayNumber,
            totalDays = detail.plan.totalDays,
            translationId = loaded.translationId,
            translationName = translations.firstOrNull { it.id == loaded.translationId }?.name ?: loaded.translationId,
            bookNames = names,
            pages = buildList {
                add(ReaderPage.Intro(planDay.dayNumber, detail.plan.totalDays, planDay.title, planDay.intro, planDay.passages.map { it.label(names) }, detail.plan.minutesPerDay))
                loaded.verses.forEach { add(ReaderPage.VersePage(it)) }
                add(ReaderPage.Finish(route.dayNumber, read, if (read) next else null))
            },
            liked = liked, bookmarked = bookmarked, noted = noted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanReaderUiState.Loading)

    fun markRead() {
        if (marking.value) return
        marking.value = true
        viewModelScope.launch {
            try {
                val result = plans.markDayRead(route.enrollmentId, route.dayNumber)
                if (result.planCompleted) _events.emit(PlanReaderEvent.Completed(route.enrollmentId))
            } finally {
                marking.value = false
            }
        }
    }

    fun openDay(dayNumber: Int) {
        _events.tryEmit(PlanReaderEvent.OpenDay(route.enrollmentId, dayNumber))
    }

    fun onToggleLike(ref: VerseRef, liked: Boolean) {
        viewModelScope.launch { userData.setLiked(ref, !liked) }
    }

    fun onToggleBookmark(ref: VerseRef, bookmarked: Boolean) {
        viewModelScope.launch { userData.setBookmarked(ref, !bookmarked) }
    }

    private suspend fun versesOf(translationId: String, p: PassageFile): List<Verse> {
        val book = p.bookId ?: return emptyList()
        return bible.passageVerses(translationId, book, p.startChapter, p.startVerse, p.endChapter, p.endVerse)
    }
}
