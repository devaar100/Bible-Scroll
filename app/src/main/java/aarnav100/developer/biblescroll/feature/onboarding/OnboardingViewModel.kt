package aarnav100.developer.biblescroll.feature.onboarding

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.onboarding.OnboardingRepository
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.data.reminder.ReminderScheduler
import aarnav100.developer.biblescroll.data.reminder.ReminderSettings
import aarnav100.developer.biblescroll.data.settings.ContentLanguage
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import javax.inject.Inject

/** A verse ready to show: the text plus its book name for the reference line. */
@Immutable
data class ShownVerse(val verse: Verse, val bookName: String)

/** Real verses for the O3 demos, in the reader's translation. Empty lists while loading. */
@Immutable
data class TourVerses(
    val feed: List<ShownVerse> = emptyList(),
    val liked: ShownVerse? = null,
    val card: ShownVerse? = null,
    val reminder: ShownVerse? = null,
)

@Immutable
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val canGoBack: Boolean = false,
    val goal: Goal? = null,
    val timing: Timing? = null,
    /** O5 verse; null until a goal is picked and the verse has loaded. */
    val goalVerse: ShownVerse? = null,
    val reminderOn: Boolean = true,
    val reminderTime: LocalTime = ReminderSettings.DEFAULT_TIME,
    /** O8: the reader said no (or the system did); show the "you can turn it on later" note. */
    val permissionDeclined: Boolean = false,
    val tour: TourVerses = TourVerses(),
    /** Bible step: RV1909 then WEB, as bible.db has them. Empty when the step isn't offered. */
    val bibleOptions: List<Translation> = emptyList(),
    /** Bible step pick; null until the step is shown (it preselects RV1909). */
    val bibleChoice: String? = null,
)

/**
 * O2-O8. Every answer lives in [SavedStateHandle], so rotation and process death resume on the same
 * step. Nothing leaves the device: the goal and timing answers are only used to pick defaults, and only
 * the resulting reminder, first verse and "done" flag are saved when the reader finishes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val bible: BibleRepository,
    private val readerPreferences: ReaderPreferences,
    private val userData: UserDataRepository,
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val access: NotificationAccess,
    private val onboarding: OnboardingRepository,
) : ViewModel() {

    /** Steps visited, current last. Back pops it, so Back after "Skip" returns to the tour. */
    private val history = savedState.getStateFlow(KEY_HISTORY, arrayListOf(OnboardingStep.Welcome.name))
    private val goal = savedState.getStateFlow<String?>(KEY_GOAL, null)
    private val timing = savedState.getStateFlow<String?>(KEY_TIMING, null)
    private val reminderOn = savedState.getStateFlow(KEY_REMINDER_ON, true)
    private val reminderMinute = savedState.getStateFlow(KEY_REMINDER_MINUTE, ReminderSettings.DEFAULT_TIME.toMinuteOfDay())
    private val declined = savedState.getStateFlow(KEY_DECLINED, false)
    /** Bible step pick, saved as the reader's translation only when onboarding finishes. */
    private val bibleChoice = savedState.getStateFlow<String?>(KEY_BIBLE, null)

    /** Spanish UI and RV1909 in bible.db: offer it after O2. Read once; onboarding is a single sitting. */
    private val bibleOptions: StateFlow<List<Translation>> = bible.translations()
        .map { all -> if (ContentLanguage.code.value == SPANISH) BIBLE_OFFER.mapNotNull { id -> all.firstOrNull { it.id == id } } else emptyList() }
        .map { if (it.any { t -> t.id == SPANISH_BIBLE }) it else emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Guards against a double tap finishing twice. Not saved: a finish interrupted by process death just runs again. */
    private var finishing = false

    /** The Bible step's pick if any, else the saved preference; always one bible.db has. */
    private val translationId: Flow<String> = combine(readerPreferences.translationId, bibleChoice, bible.translations()) { saved, picked, all ->
        val preferred = picked ?: saved
        all.firstOrNull { it.id == preferred }?.id ?: all.firstOrNull()?.id ?: preferred
    }.distinctUntilChanged()

    private val bookNames: Flow<Map<Int, String>> = bible.books().map { books -> books.associate { it.id to it.name } }

    private val goalVerse: Flow<ShownVerse?> = combine(goal.map { it.toEnum<Goal>() }, translationId, ::Pair)
        .flatMapLatest { (g, tid) ->
            if (g == null) flow { emit(null) } else combine(bible.verse(tid, g.verse), bookNames) { v, names -> v?.shown(names) }
        }

    private val tourVerses: Flow<TourVerses> = combine(translationId, bookNames, ::Pair)
        .mapLatest { (tid, names) ->
            val byRef = bible.versesByRefs(tid, TOUR_FEED + TOUR_LIKED + TOUR_CARD + TOUR_REMINDER).associateBy { it.ref }
            TourVerses(
                feed = TOUR_FEED.mapNotNull { byRef[it]?.shown(names) },
                liked = byRef[TOUR_LIKED]?.shown(names),
                card = byRef[TOUR_CARD]?.shown(names),
                reminder = byRef[TOUR_REMINDER]?.shown(names),
            )
        }

    private val answers = combine(history, goal, timing, reminderOn, reminderMinute) { h, g, t, on, minute ->
        OnboardingUiState(
            step = OnboardingStep.valueOf(h.last()),
            canGoBack = h.size > 1,
            goal = g.toEnum<Goal>(),
            timing = t.toEnum<Timing>(),
            reminderOn = on,
            reminderTime = LocalTime.of(minute / 60, minute % 60),
        )
    }

    val uiState: StateFlow<OnboardingUiState> = combine(
        answers, declined, goalVerse, tourVerses, combine(bibleOptions, bibleChoice, ::Pair),
    ) { a, d, verse, tour, (options, choice) ->
        a.copy(permissionDeclined = d, goalVerse = verse, tour = tour, bibleOptions = options, bibleChoice = choice)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState())

    /** Synchronous first frame from the saved state, so a restored flow doesn't flash O2. */
    private fun initialState() = OnboardingUiState(
        step = OnboardingStep.valueOf(history.value.last()),
        canGoBack = history.value.size > 1,
        goal = goal.value.toEnum<Goal>(),
        timing = timing.value.toEnum<Timing>(),
        reminderOn = reminderOn.value,
        reminderTime = reminderMinute.value.let { LocalTime.of(it / 60, it % 60) },
        permissionDeclined = declined.value,
        bibleChoice = bibleChoice.value,
    )

    // O2
    fun onGetStarted() {
        if (bibleOptions.value.isEmpty()) return push(OnboardingStep.Tour)
        if (bibleChoice.value == null) savedState[KEY_BIBLE] = SPANISH_BIBLE
        push(OnboardingStep.Bible)
    }

    // Bible (Spanish UI only)
    fun onBibleSelected(id: String) {
        if (bibleOptions.value.any { it.id == id }) savedState[KEY_BIBLE] = id
    }

    fun onBibleContinue() = push(OnboardingStep.Tour)

    // O3
    fun onTourContinue() = push(OnboardingStep.Goal)
    fun onTourSkip() = push(OnboardingStep.Plan)

    // O4
    fun onGoalSelected(value: Goal) {
        savedState[KEY_GOAL] = value.name
    }

    fun onGoalContinue() {
        if (goal.value.toEnum<Goal>() != null) push(OnboardingStep.GoalResponse)
    }

    // O5
    fun onGoalResponseContinue() = push(OnboardingStep.Timing)

    // O6
    /** A new answer resets O7 to that answer's default; re-picking the same one keeps any edits. */
    fun onTimingSelected(value: Timing) {
        if (timing.value == value.name) return
        savedState[KEY_TIMING] = value.name
        savedState[KEY_REMINDER_ON] = value.defaultTime != null
        value.defaultTime?.let { savedState[KEY_REMINDER_MINUTE] = it.toMinuteOfDay() }
    }

    fun onTimingContinue() {
        if (timing.value.toEnum<Timing>() != null) push(OnboardingStep.Plan)
    }

    // O7
    fun onReminderToggle(on: Boolean) {
        savedState[KEY_REMINDER_ON] = on
    }

    fun onReminderTimeChange(time: LocalTime) {
        savedState[KEY_REMINDER_MINUTE] = time.toMinuteOfDay()
        savedState[KEY_REMINDER_ON] = true
    }

    /** O8 only when a reminder was chosen and asking can change anything (Android 13+, not yet granted). */
    fun onPlanContinue() {
        if (reminderOn.value && access.needsRuntimePermission()) {
            savedState[KEY_DECLINED] = false
            push(OnboardingStep.Permission)
        } else {
            finish(withReminder = reminderOn.value)
        }
    }

    // O8
    fun onPermissionResult(granted: Boolean) {
        if (granted) finish(withReminder = true) else savedState[KEY_DECLINED] = true
    }

    /** "Not now": same as a denial. We never ask again on our own; S6 can turn it on later. */
    fun onPermissionNotNow() {
        savedState[KEY_DECLINED] = true
    }

    /** After a denial: finish without a reminder. Never blocks. */
    fun onFinishWithoutReminder() = finish(withReminder = false)

    /** Returns false on the first step, so the system handles Back (leaves the app). */
    fun onBack(): Boolean {
        val h = history.value
        if (h.size <= 1) return false
        if (h.last() == OnboardingStep.Permission.name) savedState[KEY_DECLINED] = false
        savedState[KEY_HISTORY] = ArrayList(h.dropLast(1))
        return true
    }

    private fun push(step: OnboardingStep) {
        val h = history.value
        if (h.last() == step.name) return // double tap
        savedState[KEY_HISTORY] = ArrayList(h + step.name)
    }

    private fun finish(withReminder: Boolean) {
        if (finishing) return
        finishing = true
        val goalRef = goal.value.toEnum<Goal>()?.verse
        val time = reminderMinute.value.let { LocalTime.of(it / 60, it % 60) }
        viewModelScope.launch {
            // Finish even if the screen goes away mid-write: MainActivity swaps to the app on the flag.
            withContext(NonCancellable) {
                reminders.setTime(time)
                // Pre-13 phones with notifications switched off: leave it off, S6 explains how to fix it.
                reminders.setEnabled(withReminder && access.canPost())
                scheduler.syncFromStore()
                bibleChoice.value?.let { readerPreferences.setTranslationId(it) }
                if (goalRef != null) userData.saveReadingPosition(translationId.first(), goalRef)
                onboarding.markComplete()
            }
        }
    }

    private companion object {
        const val KEY_HISTORY = "onb_history"
        const val KEY_GOAL = "onb_goal"
        const val KEY_TIMING = "onb_timing"
        const val KEY_REMINDER_ON = "onb_reminder_on"
        const val KEY_REMINDER_MINUTE = "onb_reminder_minute"
        const val KEY_DECLINED = "onb_permission_declined"
        const val KEY_BIBLE = "onb_bible"

        const val SPANISH = "es"
        const val SPANISH_BIBLE = "RV1909"
        /** Bible step options in display order: the Spanish Bible first, then the default English one. */
        val BIBLE_OFFER = listOf(SPANISH_BIBLE, "WEB") // WEB: the app default (ReaderPreferences)

        val TOUR_FEED = listOf(VerseRef(19, 23, 1), VerseRef(19, 23, 2), VerseRef(19, 23, 3), VerseRef(19, 23, 4))
        val TOUR_LIKED = VerseRef(19, 46, 1)
        val TOUR_CARD = VerseRef(43, 3, 16)
        val TOUR_REMINDER = VerseRef(19, 118, 24)
    }
}

private fun LocalTime.toMinuteOfDay() = hour * 60 + minute

private fun Verse.shown(bookNames: Map<Int, String>) = ShownVerse(this, bookNames[ref.bookId].orEmpty())

private inline fun <reified E : Enum<E>> String?.toEnum(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }
