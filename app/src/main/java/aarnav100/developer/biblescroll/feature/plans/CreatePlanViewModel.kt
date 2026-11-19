package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.plans.CustomPlanBuilder
import aarnav100.developer.biblescroll.data.plans.Pace
import aarnav100.developer.biblescroll.data.plans.PassageFile
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.plans.PlanSchedule
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.feature.bible.picker.matchesBookQuery
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class CreateStep { Books, Pace, Summary }

@Immutable
data class BookChoice(val id: Int, val name: String, val testament: Testament, val selected: Boolean)

/** The live result of the current books + pace (H14), and the first days for the summary. */
@Immutable
data class PlanPreview(val days: Int, val minutesPerDay: Int, val firstDays: List<List<String>>, val defaultTitle: String)

@Immutable
data class CreatePlanUiState(
    val step: CreateStep = CreateStep.Books,
    val query: String = "",
    /** Filtered by [query]; Old Testament then New. */
    val books: List<BookChoice> = emptyList(),
    val selectedCount: Int = 0,
    /** Quick picks: book ids of a Gospel each, Psalms and Proverbs, with whether each is selected. */
    val quickPicks: List<BookChoice> = emptyList(),
    val pace: Pace = Pace.MinutesPerDay(10),
    /** Null while calculating (or nothing selected). */
    val preview: PlanPreview? = null,
    /** What the reader typed; null = use the preview's default title. */
    val editedTitle: String? = null,
    val startDialog: StartChoices? = null,
) {
    val title: String get() = editedTitle ?: preview?.defaultTitle.orEmpty()
}

sealed interface CreatePlanEvent {
    /** The plan was saved and started: open its page (H9). */
    data class Created(val planId: String) : CreatePlanEvent
    data object RequestNotificationPermission : CreatePlanEvent
}

/** H13 (books), H14 (pace) and the summary, one step at a time, ending in the H10 start dialog. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CreatePlanViewModel internal constructor(
    bible: BibleRepository,
    private val builder: CustomPlanBuilder,
    private val plans: PlanRepository,
    private val reminders: ReminderRepository,
    private val notifications: NotificationAccess,
    private val titles: PlanTitles,
    private val today: () -> LocalDate,
    calculation: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        bible: BibleRepository,
        builder: CustomPlanBuilder,
        plans: PlanRepository,
        reminders: ReminderRepository,
        notifications: NotificationAccess,
        titles: ResourcePlanTitles,
    ) : this(bible, builder, plans, reminders, notifications, titles, LocalDate::now, Dispatchers.Default)

    private val step = MutableStateFlow(CreateStep.Books)
    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<Set<Int>>(emptySet())
    private val pace = MutableStateFlow<Pace>(Pace.MinutesPerDay(10))
    private val editedTitle = MutableStateFlow<String?>(null)
    private val dialog = MutableStateFlow<StartChoices?>(null)
    private val _events = MutableSharedFlow<CreatePlanEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<CreatePlanEvent> = _events
    private val books = bible.books()

    private val preview = combine(selected, pace, books, ::Triple)
        .mapLatest { (ids, pace, books) ->
            if (ids.isEmpty()) return@mapLatest null
            val generated = builder.generate(ids, pace)
            val names = books.associate { it.id to it.name }
            val ordered = ids.sorted()
            PlanPreview(
                days = generated.days.size,
                minutesPerDay = generated.minutesPerDay,
                firstDays = generated.days.take(PREVIEW_DAYS).map { day ->
                    day.passages.map { p ->
                        PassageFile(BookCodes.code(p.bookId), p.startChapter, p.startVerse, p.endChapter, p.endVerse).label(names)
                    }
                },
                defaultTitle = titles.title(ordered.map { names[it].orEmpty() }, ordered.last() - ordered.first() == ordered.size - 1, generated.days.size),
            )
        }
        .flowOn(calculation)

    val uiState: StateFlow<CreatePlanUiState> = combine(
        combine(step, query, selected, pace) { a, b, c, d -> Quad(a, b, c, d) },
        books,
        combine(preview, editedTitle, dialog) { a, b, c -> Triple(a, b, c) },
    ) { (step, query, selected, pace), books, (preview, title, dialog) ->
        CreatePlanUiState(
            step = step,
            query = query,
            books = books.filter { matchesBookQuery(it, query) }.map { it.choice(selected) },
            selectedCount = selected.size,
            quickPicks = QUICK_PICKS.mapNotNull { id -> books.firstOrNull { it.id == id }?.choice(selected) },
            pace = pace,
            preview = preview,
            editedTitle = title,
            startDialog = dialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CreatePlanUiState())

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun toggleBook(id: Int) = selected.update { if (id in it) it - id else it + id }

    fun clearBooks() {
        selected.value = emptySet()
    }

    fun setPace(value: Pace) {
        pace.value = when (value) {
            is Pace.MinutesPerDay -> Pace.MinutesPerDay(value.value.coerceIn(Pace.MINUTES_RANGE))
            is Pace.Days -> Pace.Days(value.value.coerceIn(1, Pace.MAX_DAYS))
        }
    }

    fun setTitle(text: String) {
        editedTitle.value = text
    }

    /** Forward one step; ignored if the current step isn't complete. */
    fun next() {
        step.value = when (step.value) {
            CreateStep.Books -> if (selected.value.isEmpty()) return else CreateStep.Pace
            CreateStep.Pace -> CreateStep.Summary
            CreateStep.Summary -> return
        }
    }

    /** Back one step; returns false on the first step (the screen then closes). */
    fun back(): Boolean {
        step.value = when (step.value) {
            CreateStep.Books -> return false
            CreateStep.Pace -> CreateStep.Books
            CreateStep.Summary -> CreateStep.Pace
        }
        return true
    }

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
        if (on && notifications.needsRuntimePermission()) _events.tryEmit(CreatePlanEvent.RequestNotificationPermission)
    }

    fun setReminderTime(time: LocalTime) = dialog.update { it?.copy(reminderTime = time) }

    /** Saves the plan exactly as previewed, starts it, and reports its id. */
    fun confirmStart() {
        val choices = dialog.value ?: return
        val state = uiState.value
        val ids = selected.value.sorted()
        if (ids.isEmpty()) return
        dialog.value = null
        viewModelScope.launch {
            val generated = builder.generate(ids, pace.value)
            val names = books.first().associate { it.id to it.name }
            val title = state.title.trim().ifEmpty { state.preview?.defaultTitle.orEmpty() }
            val file = CustomPlanBuilder.toPlanFile(plans.newCustomPlanId(), title, titles.books(ids.map { names[it].orEmpty() }), generated)
            val id = plans.saveCustomPlan(file, ids, pace.value)
            plans.startPlan(id, choices.startDate, choices.reminderTime.takeIf { choices.reminderOn })
            _events.emit(CreatePlanEvent.Created(id))
        }
    }

    private fun Book.choice(selected: Set<Int>) = BookChoice(id, name, testament, id in selected)

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        const val PREVIEW_DAYS = 5
        /** The four Gospels, Psalms and Proverbs. */
        val QUICK_PICKS = listOf(40, 41, 42, 43, 19, 20)
    }
}
