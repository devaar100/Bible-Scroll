package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import aarnav100.developer.biblescroll.testing.FakeStreakRepository
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.testing.bibleWithChapters
import aarnav100.developer.biblescroll.testing.day
import aarnav100.developer.biblescroll.testing.passage
import aarnav100.developer.biblescroll.testing.FakePlanCatalogDao
import aarnav100.developer.biblescroll.testing.FakePlanProgressDao
import aarnav100.developer.biblescroll.testing.MapAssets
import aarnav100.developer.biblescroll.testing.RecordingPlanScheduler
import aarnav100.developer.biblescroll.testing.planIndex
import aarnav100.developer.biblescroll.testing.planJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class PlanRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalog = FakePlanCatalogDao()
    private val progress = FakePlanProgressDao()
    private val customDao = aarnav100.developer.biblescroll.testing.FakeCustomPlanDao()
    private val scheduler = RecordingPlanScheduler()
    private val assets = MapAssets(
        mutableMapOf(
            "plans/index.json" to planIndex(emptyList(), mapOf("gospels-and-acts" to listOf("john", "mark", "hidden", "split"))),
            "plans/john.json" to planJson("john", days = 3),
            "plans/mark.json" to planJson("mark", days = 2),
            "plans/split.json" to planJson(
                "split",
                daysJson = "[" + day(1, passage("JHN", 3, 1, 3, 20)) + "," + day(2, passage("JHN", 3, 21, 4, 54)) + "," +
                    day(3, passage("JHN", 3, 16)) + "]",
            ),
            "plans/hidden.json" to planJson("hidden", status = "flagged"),
        ),
    )
    private val io = UnconfinedTestDispatcher()
    private var now = 1_000L
    private var ids = 0
    private val streaks = FakeStreakRepository()
    private val userData = LocalUserDataRepository(FakeUserDao()) { now }
    private val repo = PlanRepository(
        catalog, progress, customDao, PlanImporter(assets, catalog, json, 1, io), scheduler, streaks, userData, bibleWithChapters(), json,
        { now }, io, newId = { "e${++ids}" },
    )
    private val today = LocalDate.parse("2026-11-18")

    @Test
    fun categoriesImportOnFirstUseAndHideUnpublishedPlans() = runTest(io) {
        val categories = repo.categories().first()
        assertEquals(listOf("john", "mark", "split"), categories.single().plans.map { it.id })
        assertNull(repo.plan("hidden").first())
        assertEquals(3, repo.plan("john").first()!!.file.days.size)
    }

    @Test
    fun startingAPlanStoresACopyAndSchedulesReminders() = runTest(io) {
        val id = repo.startPlan("john", today.plusDays(1), LocalTime.of(7, 30))
        val saved = progress.enrollments.value.getValue(id)
        assertEquals("2026-11-19", saved.startDate)
        assertEquals(450, saved.reminderMinuteOfDay)
        assertEquals(3, saved.totalDays)
        assertTrue(saved.planJson.contains("\"id\":\"john\""))
        assertEquals(saved.createdAt, saved.updatedAt)
        assertEquals(1, scheduler.planSyncs)
    }

    @Test
    fun severalPlansCanBeActiveButNotTheSamePlanTwice() = runTest(io) {
        val john = repo.startPlan("john", today, null)
        val mark = repo.startPlan("mark", today, null)
        assertNotEquals(john, mark)
        assertEquals(john, repo.startPlan("john", today, null))
        val mine = repo.myPlans().first()
        assertEquals(setOf("john", "mark"), mine.map { it.planId }.toSet())
        assertTrue(mine.none { it.completed })
    }

    @Test
    fun finishingEveryDayCompletesThePlanAndItCanBeStartedAgain() = runTest(io) {
        val id = repo.startPlan("mark", today, LocalTime.of(8, 0))
        now = 2_000
        repo.setDayCompleted(id, 1, true)
        assertFalse(repo.myPlans().first().single().completed)
        now = 3_000
        repo.setDayCompleted(id, 2, true)
        val done = repo.myPlans().first().single()
        assertTrue(done.completed)
        assertEquals(2, done.daysDone)
        assertEquals(3_000, progress.enrollments.value.getValue(id).updatedAt)
        // A finished plan can be started again as a new copy.
        val again = repo.startPlan("mark", today, null)
        assertNotEquals(id, again)
        assertEquals(2, repo.myPlans().first().size)
    }

    @Test
    fun unmarkingADayReopensAFinishedPlanAndKeepsTheRow() = runTest(io) {
        val id = repo.startPlan("mark", today, null)
        repo.setDayCompleted(id, 1, true)
        repo.setDayCompleted(id, 2, true)
        repo.setDayCompleted(id, 2, false)
        assertFalse(repo.myPlans().first().single().completed)
        assertFalse(progress.days.value.getValue(id to 2).completed)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aHiddenPlanCantBeStarted() = runTest(io) {
        repo.startPlan("hidden", today, null)
    }

    @Test
    fun enrollmentsSurviveThePlanLeavingTheBundle() = runTest(io) {
        val id = repo.startPlan("mark", today, null)
        assets.files["plans/index.json"] = planIndex(emptyList(), mapOf("gospels-and-acts" to listOf("john")))
        PlanImporter(assets, catalog, json, 2, io).importIfNeeded()
        assertNull(repo.plan("mark").first())
        assertEquals("Plan mark", repo.myPlans().first().single().title)
        repo.setDayCompleted(id, 1, true)
        assertEquals(1, repo.myPlans().first().single().daysDone)
    }

    // ---- H11: marking days read ----

    @Test
    fun markingADayCreditsTheStreakOnceAndOffersTheNextDay() = runTest(io) {
        val id = repo.startPlan("john", today, null)
        val first = repo.markDayRead(id, 1)
        assertEquals(MarkResult(newlyRead = true, planCompleted = false, nextDay = 2), first)
        val again = repo.markDayRead(id, 1)
        assertFalse(again.newlyRead)
        assertEquals(listOf(StreakActivity.PlanDayCompleted), streaks.activities)
    }

    @Test
    fun readingDaysOutOfOrderStillPointsAtTheFirstUnread() = runTest(io) {
        val id = repo.startPlan("john", today, null)
        assertEquals(1, repo.markDayRead(id, 2).nextDay)
        assertEquals(3, repo.markDayRead(id, 1).nextDay)
    }

    @Test
    fun theLastDayCompletesThePlan() = runTest(io) {
        val id = repo.startPlan("mark", today, null)
        repo.markDayRead(id, 2)
        val last = repo.markDayRead(id, 1)
        assertTrue(last.planCompleted)
        assertNull(last.nextDay)
        assertTrue(repo.myPlans().first().single().completed)
        assertFalse(repo.markDayRead(id, 1).planCompleted) // re-marking a finished plan isn't a second completion
    }

    @Test
    fun chaptersAreMarkedReadOnlyOnceEveryVerseIsCovered() = runTest(io) {
        val id = repo.startPlan("split", today, null)
        repo.markDayRead(id, 1) // John 3:1-20: half of chapter 3
        assertEquals(emptyMap<Int, Set<Int>>(), userData.readChapters().first())
        repo.markDayRead(id, 2) // John 3:21-4:54: finishes 3, all of 4
        assertEquals(mapOf(43 to setOf(3, 4)), userData.readChapters().first())
    }

    @Test
    fun adjustScheduleMovesTheStartSoTheNextUnreadDayIsToday() = runTest(io) {
        val id = repo.startPlan("john", today.minusDays(5), null)
        repo.markDayRead(id, 1)
        now = 9_000
        repo.adjustSchedule(id, today)
        val saved = progress.enrollments.value.getValue(id)
        assertEquals(today.minusDays(1).toString(), saved.startDate) // day 2 falls on today
        assertEquals(9_000, saved.updatedAt)
        repo.adjustSchedule(id, today.minusDays(3)) // never moves the start earlier
        assertEquals(today.minusDays(1).toString(), progress.enrollments.value.getValue(id).startDate)
    }

    @Test
    fun enrollmentCarriesItsOwnPlanCopyAndDaysRead() = runTest(io) {
        val id = repo.startPlan("john", today, null)
        repo.markDayRead(id, 2)
        val detail = repo.enrollment(id).first()!!
        assertEquals(setOf(2), detail.done)
        assertEquals(3, detail.file.days.size)
        assertEquals(1, detail.plan.daysDone)
    }

    // ---- H13/H14 custom plans ----

    private suspend fun custom(title: String = "Mine"): String {
        val file = PlanFile("custom-abc", "v", title, "John", "John", "custom", "custom", "custom", "", 5, "easy",
            days = listOf(PlanDayFile(1, passages = listOf(PassageFile("JHN", 3, 16, 3, 16))), PlanDayFile(2, passages = listOf(PassageFile("JHN", 3, 17, 3, 17)))))
        return repo.saveCustomPlan(file, listOf(43), Pace.Days(2))
    }

    @Test
    fun customPlansOpenStartAndCanBeDeleted() = runTest(io) {
        val id = custom()
        val detail = repo.plan(id).first()!!
        assertTrue(detail.custom)
        val enrollment = repo.startPlan(id, today, null)
        repo.markDayRead(enrollment, 1)
        assertEquals("Mine", repo.myPlans().first().single().title)
        now = 7_000
        repo.deleteCustomPlan(id)
        assertNull(repo.plan(id).first())
        assertTrue(repo.myPlans().first().isEmpty())
        assertTrue(progress.days.value.isEmpty())
        val tombstone = customDao.rows.value.getValue(id)
        assertTrue(tombstone.deleted)
        assertEquals(7_000, tombstone.updatedAt) // the delete survives an import merge
    }

    @Test(expected = IllegalArgumentException::class)
    fun bundledPlansCantBeDeleted() = runTest(io) {
        repo.deleteCustomPlan("john")
    }

    @Test
    fun customIdsNeverLookLikeBundledOnes() {
        val id = repo.newCustomPlanId()
        assertTrue(isCustomPlan(id))
        assertTrue(aarnav100.developer.biblescroll.ui.navigation.PlanLinks.parse(aarnav100.developer.biblescroll.ui.navigation.PlanLinks.build(id)) == id)
    }
}
