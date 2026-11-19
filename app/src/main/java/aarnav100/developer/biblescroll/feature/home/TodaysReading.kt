package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.References
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.plans.EnrollmentDetail
import aarnav100.developer.biblescroll.data.plans.PlanProgressMath
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.plans.PlanSchedule
import aarnav100.developer.biblescroll.data.user.UserDataRepository
import aarnav100.developer.biblescroll.data.votd.LocalClock
import aarnav100.developer.biblescroll.feature.plans.dayHeading
import aarnav100.developer.biblescroll.feature.plans.label
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import aarnav100.developer.biblescroll.ui.text.localizedList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/** Where a started plan stands today. None of these is a reproach: being behind just shows the next day. */
enum class PlanDayState {
    /** The next unread day is today's or earlier: read it. */
    DUE,

    /** Today's day is already read; the next one is there to read ahead. */
    CAUGHT_UP,

    /** Starts on a later date. */
    STARTS_LATER,

    /** Every day read (shown on the day it was finished). */
    FINISHED,
}

/** One page of the Today's Reading card. */
sealed interface ReadingItem {
    @Immutable
    data class PlanDay(
        val enrollmentId: String,
        val planTitle: String,
        val state: PlanDayState,
        /** The day to open (the next unread day; the last day when finished). */
        val dayNumber: Int,
        val totalDays: Int,
        val dayTitle: String?,
        /** In the app's language, e.g. "John 3:1–21". */
        val passages: List<String>,
        val minutes: Int,
        val startDate: LocalDate,
    ) : ReadingItem

    /** No active plan: back to the last reading place in the feed. */
    @Immutable
    data class ContinueReading(val ref: VerseRef, val reference: String) : ReadingItem

    /** No active plan and nothing read yet. */
    data object StartReading : ReadingItem
}

/** H1 Today's Reading: which pages to show. Pure, so the state selection is easy to test. */
object TodaysReadingRules {

    fun stateOf(detail: EnrollmentDetail, today: LocalDate): Pair<PlanDayState, Int> {
        val plan = detail.plan
        val next = PlanSchedule.nextDay(plan.totalDays, detail.done) ?: return PlanDayState.FINISHED to plan.totalDays
        return when {
            today.isBefore(plan.startDate) -> PlanDayState.STARTS_LATER to next
            next <= PlanProgressMath.calendarDay(plan.startDate, today) -> PlanDayState.DUE to next
            else -> PlanDayState.CAUGHT_UP to next
        }
    }

    /**
     * Active plans (most recently started first), then any plan finished today. With no active plan the card falls
     * back to Continue reading (last reading place) or Start reading.
     *
     * [labels] turns a day's passages into display text (book names in the app's language).
     */
    fun select(
        enrollments: List<EnrollmentDetail>,
        today: LocalDate,
        zone: ZoneId,
        lastPosition: Pair<VerseRef, String>?,
        labels: (EnrollmentDetail, Int) -> List<String>,
    ): List<ReadingItem> {
        val finishedToday = { d: EnrollmentDetail ->
            d.plan.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
        }
        val active = enrollments.filter { !it.plan.completed }.sortedByDescending { it.plan.startedAt }
        val finished = enrollments.filter { it.plan.completed && finishedToday(it) }.sortedByDescending { it.plan.completedAt }
        val pages = (active + finished).map { d ->
            val (state, day) = stateOf(d, today)
            val dayFile = d.file.days.firstOrNull { it.dayNumber == day }
            ReadingItem.PlanDay(
                enrollmentId = d.plan.enrollmentId,
                planTitle = d.plan.title,
                state = state,
                dayNumber = day,
                totalDays = d.plan.totalDays,
                dayTitle = dayFile?.title,
                passages = labels(d, day),
                minutes = d.plan.minutesPerDay,
                startDate = d.plan.startDate,
            )
        }
        if (active.isNotEmpty()) return pages
        val fallback = lastPosition?.let { (ref, reference) -> ReadingItem.ContinueReading(ref, reference) } ?: ReadingItem.StartReading
        return pages + fallback
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodaysReadingViewModel @Inject constructor(
    plans: PlanRepository,
    userData: UserDataRepository,
    bible: BibleRepository,
    private val clock: LocalClock,
    private val pendingVerseLink: PendingVerseLink,
) : ViewModel() {

    private val bookNames = bible.books().map { books -> books.associate { it.id to it.name } }

    /** Every started plan's own copy and progress, live. Finished plans are filtered by the rules. */
    private val enrollments: Flow<List<EnrollmentDetail>> = plans.myPlans().flatMapLatest { mine ->
        if (mine.isEmpty()) flowOf(emptyList())
        else combine(mine.map { plans.enrollment(it.enrollmentId) }) { details -> details.filterNotNull() }
    }

    val items: StateFlow<List<ReadingItem>?> = combine(enrollments, userData.observeLastReadingPosition(), bookNames) { details, last, names ->
        val now = clock.now()
        TodaysReadingRules.select(
            details, now.toLocalDate(), now.zone,
            last?.let { it to References.verse(names[it.bookId].orEmpty(), it) },
        ) { d, day -> d.file.days.firstOrNull { it.dayNumber == day }?.passages.orEmpty().map { it.label(names) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Continue reading": the feed opens at the last place (same path as a widget tap). */
    fun continueReading(ref: VerseRef) = pendingVerseLink.post(ref)
}

/** H1 Today's Reading card. Several plans: a small pager with dots. */
@Composable
fun TodaysReadingCard(
    items: List<ReadingItem>,
    onOpenPlanDay: (enrollmentId: String, day: Int) -> Unit,
    onContinue: (VerseRef) -> Unit,
    onOpenBible: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
            Text(
                stringResource(R.string.home_todays_reading),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            if (items.size == 1) {
                ReadingPage(items.single(), onOpenPlanDay, onContinue, onOpenBible)
            } else {
                val pager = rememberPagerState { items.size }
                HorizontalPager(state = pager, verticalAlignment = Alignment.Top, key = { i -> pageKey(items[i]) }) { i ->
                    ReadingPage(items[i], onOpenPlanDay, onContinue, onOpenBible)
                }
                val position = stringResource(R.string.home_reading_page_of, pager.currentPage + 1, items.size)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).clearAndSetSemantics { stateDescription = position },
                    horizontalArrangement = Arrangement.Center,
                ) {
                    repeat(items.size) { i ->
                        Box(
                            Modifier.padding(3.dp).size(6.dp).background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                        )
                    }
                }
            }
        }
    }
}

private fun pageKey(item: ReadingItem): String = when (item) {
    is ReadingItem.PlanDay -> item.enrollmentId
    is ReadingItem.ContinueReading -> "continue"
    ReadingItem.StartReading -> "start"
}

@Composable
private fun ReadingPage(item: ReadingItem, onOpenPlanDay: (String, Int) -> Unit, onContinue: (VerseRef) -> Unit, onOpenBible: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (item) {
            is ReadingItem.PlanDay -> {
                Text(item.planTitle, style = MaterialTheme.typography.titleMedium)
                when (item.state) {
                    PlanDayState.FINISHED -> Body(pluralStringResource(R.plurals.home_plan_finished, item.totalDays, item.totalDays))
                    PlanDayState.STARTS_LATER -> {
                        val date = item.startDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
                        Body(stringResource(R.string.home_plan_starts, date))
                    }
                    PlanDayState.DUE, PlanDayState.CAUGHT_UP -> {
                        if (item.state == PlanDayState.CAUGHT_UP) Body(stringResource(R.string.home_plan_caught_up))
                        Text(dayHeading(item.dayNumber, item.dayTitle), style = MaterialTheme.typography.bodyLarge)
                        if (item.passages.isNotEmpty()) Body(localizedList(item.passages))
                        Body(stringResource(R.string.two_parts,
                            stringResource(R.string.home_plan_day_of, item.dayNumber, item.totalDays),
                            pluralStringResource(R.plurals.home_plan_minutes, item.minutes, item.minutes)))
                    }
                }
                val action = when (item.state) {
                    PlanDayState.DUE -> R.string.home_plan_read_day
                    PlanDayState.CAUGHT_UP -> R.string.home_plan_read_ahead
                    PlanDayState.STARTS_LATER -> R.string.home_plan_preview
                    PlanDayState.FINISHED -> R.string.home_plan_look_back
                }
                TextButton(onClick = { onOpenPlanDay(item.enrollmentId, item.dayNumber) }, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(action))
                }
            }
            is ReadingItem.ContinueReading -> {
                Text(stringResource(R.string.home_continue_title), style = MaterialTheme.typography.titleMedium)
                Body(item.reference)
                TextButton(onClick = { onContinue(item.ref) }) { Text(stringResource(R.string.home_continue_action)) }
            }
            ReadingItem.StartReading -> {
                Text(stringResource(R.string.home_start_title), style = MaterialTheme.typography.titleMedium)
                Body(stringResource(R.string.home_start_body))
                TextButton(onClick = onOpenBible) { Text(stringResource(R.string.home_start_action)) }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
