package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.library.LibraryList
import aarnav100.developer.biblescroll.data.library.LibraryRepository
import aarnav100.developer.biblescroll.data.library.LibrarySort
import aarnav100.developer.biblescroll.data.plans.PlanProgressMath
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.plans.PlanSchedule
import aarnav100.developer.biblescroll.ui.navigation.PlanDaysRoute
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@Immutable
data class PlanDayStatus(val dayNumber: Int, val title: String?, val passages: List<String>, val read: Boolean, val isNext: Boolean)

/** A note on a verse inside the plan's passages, and the first day that contains that verse. */
@Immutable
data class PlanNote(val ref: VerseRef, val reference: String, val text: String, val dayNumber: Int)

sealed interface PlanDaysUiState {
    data object Loading : PlanDaysUiState
    data object NotFound : PlanDaysUiState

    @Immutable
    data class Ready(
        val enrollmentId: String,
        val title: String,
        val daysDone: Int,
        val totalDays: Int,
        val days: List<PlanDayStatus>,
        val notes: List<PlanNote>,
    ) : PlanDaysUiState
}

/** H12: every day with its read status, plus notes on verses within the plan's passages. */
@HiltViewModel
class PlanDaysViewModel internal constructor(
    enrollmentId: String,
    plans: PlanRepository,
    bible: BibleRepository,
    library: LibraryRepository,
) : ViewModel() {

    @Inject
    constructor(savedStateHandle: SavedStateHandle, plans: PlanRepository, bible: BibleRepository, library: LibraryRepository) :
        this(savedStateHandle.toRoute<PlanDaysRoute>().enrollmentId, plans, bible, library)

    val uiState: StateFlow<PlanDaysUiState> = combine(
        plans.enrollment(enrollmentId), bible.books(), library.entries(LibraryList.NOTES, LibrarySort.BIBLE),
    ) { e, books, notes ->
        val detail = e ?: return@combine PlanDaysUiState.NotFound
        val names = books.associate { it.id to it.name }
        val next = PlanSchedule.nextDay(detail.plan.totalDays, detail.done)
        PlanDaysUiState.Ready(
            enrollmentId = enrollmentId,
            title = detail.plan.title,
            daysDone = detail.done.size,
            totalDays = detail.plan.totalDays,
            days = detail.file.days.map { d ->
                PlanDayStatus(d.dayNumber, d.title, d.passages.map { it.label(names) }, d.dayNumber in detail.done, d.dayNumber == next)
            },
            notes = notes.mapNotNull { entry ->
                val day = detail.file.days.firstOrNull { d -> d.passages.any { PlanProgressMath.contains(it, entry.ref) } }
                    ?: return@mapNotNull null
                PlanNote(entry.ref, References.verse(names[entry.ref.bookId].orEmpty(), entry.ref), entry.note.orEmpty(), day.dayNumber)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDaysUiState.Loading)
}
