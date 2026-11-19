package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.plans.EnrollmentDetail
import aarnav100.developer.biblescroll.data.plans.MyPlan
import aarnav100.developer.biblescroll.data.plans.PassageFile
import aarnav100.developer.biblescroll.data.plans.PlanDayFile
import aarnav100.developer.biblescroll.data.plans.PlanFile
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** H1 Today's Reading: which pages show, in which state, in which order. */
class TodaysReadingRulesTest {

    private val zone = ZoneId.of("America/Chicago")
    private val today = LocalDate.of(2026, 11, 18)
    private fun millis(date: LocalDate, hour: Int = 9) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun plan(
        id: String,
        start: LocalDate = today,
        done: Set<Int> = emptySet(),
        days: Int = 5,
        startedAt: Long = millis(start),
        completedAt: Long? = null,
    ): EnrollmentDetail {
        val file = PlanFile(
            id = id, contentVersion = "v", title = "Plan $id", shortDescription = "", description = "", category = "c",
            status = "auto_checked", origin = "structural", estimatedMinutesPerDay = 7, difficulty = "easy",
            days = (1..days).map { PlanDayFile(it, title = "Title $it", passages = listOf(PassageFile("JHN", it, 1, it, 10))) },
        )
        val my = MyPlan(
            enrollmentId = "e-$id", planId = id, title = "Plan $id", totalDays = days, daysDone = done.size, minutesPerDay = 7,
            startDate = start, reminder = null, completed = completedAt != null, startedAt = startedAt, completedAt = completedAt,
        )
        return EnrollmentDetail(my, file, done)
    }

    private fun select(vararg plans: EnrollmentDetail, last: VerseRef? = null) =
        TodaysReadingRules.select(plans.toList(), today, zone, last?.let { it to "John 3:16" }) { d, day ->
            listOf("${d.plan.planId} day $day")
        }

    private fun List<ReadingItem>.planDays() = filterIsInstance<ReadingItem.PlanDay>()

    @Test
    fun noPlanAndNothingReadMeansStartReading() {
        assertEquals(listOf(ReadingItem.StartReading), select())
    }

    @Test
    fun noPlanMeansContinueReadingFromTheLastPlace() {
        assertEquals(listOf(ReadingItem.ContinueReading(VerseRef(43, 3, 16), "John 3:16")), select(last = VerseRef(43, 3, 16)))
    }

    @Test
    fun aPlanStartedTodayShowsDayOneDue() {
        val page = select(plan("a"), last = VerseRef(1, 1, 1)).single() as ReadingItem.PlanDay // no Continue page with an active plan
        assertEquals(PlanDayState.DUE, page.state)
        assertEquals(1, page.dayNumber)
        assertEquals("Title 1", page.dayTitle)
        assertEquals(listOf("a day 1"), page.passages)
        assertEquals(7, page.minutes)
        assertEquals(5, page.totalDays)
    }

    @Test
    fun beingBehindJustShowsTheNextDayNoCountOfMissedDays() {
        val page = select(plan("a", start = today.minusDays(4), done = setOf(1))).planDays().single()
        assertEquals(PlanDayState.DUE, page.state)
        assertEquals(2, page.dayNumber)
    }

    @Test
    fun todaysDayAlreadyReadIsCaughtUpWithTheNextDayToReadAhead() {
        val page = select(plan("a", done = setOf(1))).planDays().single()
        assertEquals(PlanDayState.CAUGHT_UP, page.state)
        assertEquals(2, page.dayNumber)
    }

    @Test
    fun aPlanStartingTomorrowSaysWhen() {
        val page = select(plan("a", start = today.plusDays(1))).planDays().single()
        assertEquals(PlanDayState.STARTS_LATER, page.state)
        assertEquals(1, page.dayNumber)
        assertEquals(today.plusDays(1), page.startDate)
    }

    @Test
    fun aPlanFinishedTodayShowsFinishedThenContinueReading() {
        val finished = plan("a", start = today.minusDays(4), done = (1..5).toSet(), completedAt = millis(today, 7))
        val items = select(finished, last = VerseRef(43, 3, 16))
        val page = items.first() as ReadingItem.PlanDay
        assertEquals(PlanDayState.FINISHED, page.state)
        assertEquals(5, page.dayNumber)
        assertEquals(ReadingItem.ContinueReading(VerseRef(43, 3, 16), "John 3:16"), items.last())
    }

    @Test
    fun aPlanFinishedYesterdayIsNoLongerShown() {
        val finished = plan("a", start = today.minusDays(5), done = (1..5).toSet(), completedAt = millis(today.minusDays(1), 22))
        assertEquals(listOf(ReadingItem.StartReading), select(finished))
    }

    @Test
    fun allDaysReadButNotYetClosedIsFinishedToo() {
        assertEquals(PlanDayState.FINISHED, select(plan("a", done = (1..5).toSet())).planDays().single().state)
    }

    @Test
    fun severalPlansAreMostRecentlyStartedFirstWithNoFallbackPage() {
        val items = select(
            plan("old", start = today.minusDays(10), startedAt = millis(today.minusDays(10))),
            plan("new", start = today.minusDays(1), startedAt = millis(today.minusDays(1))),
            plan("middle", start = today.minusDays(3), startedAt = millis(today.minusDays(3))),
            last = VerseRef(43, 3, 16),
        )
        assertEquals(listOf("e-new", "e-middle", "e-old"), items.planDays().map { it.enrollmentId })
        assertEquals(3, items.size)
    }

    @Test
    fun finishedTodayComesAfterActivePlans() {
        val items = select(
            plan("done", start = today.minusDays(4), done = (1..5).toSet(), completedAt = millis(today, 8)),
            plan("active", start = today.minusDays(2)),
        )
        assertEquals(listOf("e-active", "e-done"), items.planDays().map { it.enrollmentId })
    }
}
