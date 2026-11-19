package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.plans.MyPlan
import aarnav100.developer.biblescroll.data.plans.PlanCategory
import aarnav100.developer.biblescroll.data.plans.PlanProgressMath
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.plans.PlanSchedule
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.ui.navigation.PlanDetailRoute
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

// ---- H8 Plans browser -----------------------------------------------------------------------------

sealed interface PlansBrowserUiState {
    data object Loading : PlansBrowserUiState

    @Immutable
    data class Ready(
        /** Active first (newest first), then finished. */
        val myPlans: List<MyPlan>,
        /** "start-here" first when present, then the index's categories. */
        val categories: List<PlanCategory>,
    ) : PlansBrowserUiState
}

@HiltViewModel
class PlansBrowserViewModel @Inject constructor(repository: PlanRepository) : ViewModel() {
    val uiState: StateFlow<PlansBrowserUiState> = combine(repository.categories(), repository.myPlans()) { categories, mine ->
        PlansBrowserUiState.Ready(mine.sortedBy { it.completed }, categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansBrowserUiState.Loading)
}

// ---- H9 Plan detail + H10 Start dialog ------------------------------------------------------------

@Immutable
data class DayRow(val dayNumber: Int, val title: String?, val intro: String?, val passages: List<String>)

/** H10 choices. Reminder time defaults to the daily reminder's time. */
@Immutable
data class StartChoices(
    val startOptions: List<LocalDate>,
    val startDate: LocalDate,
    val reminderOn: Boolean,
    val reminderTime: LocalTime,
)

sealed interface PlanDetailUiState {
    data object Loading : PlanDetailUiState

    /** Missing, or hidden because its status isn't reviewed/auto_checked. */
    data object NotFound : PlanDetailUiState

    @Immutable
    data class Ready(
        val id: String,
        val title: String,
        val shortDescription: String,
        val description: String,
        val credits: String,
        val totalDays: Int,
        val minutesPerDay: Int,
        val difficulty: String,
        val days: List<DayRow>,
        /** The reader's copy if this plan is in progress (H8 "My plans"). */
        val active: MyPlan?,
        /** For [active]: the next unread day (what "Read" opens) and how far the calendar is ahead of it. */
        val nextDay: Int?,
        val daysToCatchUp: Int,
        /** The most recent finished copy, if any. */
        val finished: MyPlan?,
        /** Non-null while the H10 dialog is open. */
        val startDialog: StartChoices?,
        /** A plan the reader built (H13/H14): it can be deleted. */
        val custom: Boolean = false,
    ) : PlanDetailUiState
}

sealed interface PlanDetailEvent {
    data class Started(val startDate: LocalDate, val today: LocalDate) : PlanDetailEvent

    /** Android 13+: ask for the notification permission so the plan reminder can show. */
    data object RequestNotificationPermission : PlanDetailEvent

    /** A custom plan was deleted: leave its page. */
    data object Deleted : PlanDetailEvent
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanDetailViewModel internal constructor(
    private val planId: String,
    private val repository: PlanRepository,
    bible: BibleRepository,
    private val reminders: ReminderRepository,
    private val notifications: NotificationAccess,
    private val today: () -> LocalDate,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: PlanRepository,
        bible: BibleRepository,
        reminders: ReminderRepository,
        notifications: NotificationAccess,
    ) : this(savedStateHandle.toRoute<PlanDetailRoute>().planId, repository, bible, reminders, notifications, LocalDate::now)

    private val dialog = MutableStateFlow<StartChoices?>(null)
    private val _events = MutableSharedFlow<PlanDetailEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<PlanDetailEvent> = _events

    /** The active copy's days read, for "next up" and catching up. */
    private val activeDone = repository.myPlans()
        .map { mine -> mine.firstOrNull { it.planId == planId && !it.completed }?.enrollmentId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptySet()) else repository.enrollment(id).map { it?.done.orEmpty() } }

    val uiState: StateFlow<PlanDetailUiState> = combine(
        repository.plan(planId), repository.myPlans(), bible.books(), dialog, activeDone,
    ) { detail, mine, books, choices, done ->
        val plan = detail?.file ?: return@combine PlanDetailUiState.NotFound
        val names = books.associate { it.id to it.name }
        val copies = mine.filter { it.planId == planId }
        PlanDetailUiState.Ready(
            id = plan.id, title = plan.title, shortDescription = plan.shortDescription, description = plan.description,
            credits = plan.credits, totalDays = plan.days.size, minutesPerDay = plan.estimatedMinutesPerDay,
            difficulty = plan.difficulty,
            days = plan.days.map { d -> DayRow(d.dayNumber, d.title, d.intro, d.passages.map { it.label(names) }) },
            active = copies.firstOrNull { !it.completed },
            nextDay = copies.firstOrNull { !it.completed }?.let { PlanSchedule.nextDay(it.totalDays, done) },
            daysToCatchUp = copies.firstOrNull { !it.completed }
                ?.let { PlanProgressMath.daysToCatchUp(it.totalDays, done, it.startDate, today()) } ?: 0,
            finished = copies.firstOrNull { it.completed },
            startDialog = choices,
            custom = detail.custom,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanDetailUiState.Loading)

    fun openStartDialog() {
        viewModelScope.launch {
            val now = today()
            dialog.value = StartChoices(PlanSchedule.startOptions(now), now, false, reminders.settings.first().time)
        }
    }

    fun dismissStartDialog() {
        dialog.value = null
    }

    fun chooseStartDate(date: LocalDate) = dialog.update { it?.copy(startDate = date) }

    fun setReminderOn(on: Boolean) {
        dialog.update { it?.copy(reminderOn = on) }
        if (on && notifications.needsRuntimePermission()) _events.tryEmit(PlanDetailEvent.RequestNotificationPermission)
    }

    fun setReminderTime(time: LocalTime) = dialog.update { it?.copy(reminderTime = time) }

    /** Shift the remaining days so the next unread one is today's. */
    fun adjustSchedule() {
        val active = (uiState.value as? PlanDetailUiState.Ready)?.active ?: return
        viewModelScope.launch { repository.adjustSchedule(active.enrollmentId, today()) }
    }

    /** Custom plans only: delete the plan, its progress and reminders. */
    fun deletePlan() {
        viewModelScope.launch {
            repository.deleteCustomPlan(planId)
            _events.emit(PlanDetailEvent.Deleted)
        }
    }

    fun confirmStart() {
        val choices = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            repository.startPlan(planId, choices.startDate, choices.reminderTime.takeIf { choices.reminderOn })
            _events.emit(PlanDetailEvent.Started(choices.startDate, today()))
        }
    }
}
