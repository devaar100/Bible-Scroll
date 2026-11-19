package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.data.plans.PlanImporter
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePlanCatalogDao
import aarnav100.developer.biblescroll.testing.FakePlanProgressDao
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.MapAssets
import aarnav100.developer.biblescroll.testing.RecordingPlanScheduler
import aarnav100.developer.biblescroll.testing.planIndex
import aarnav100.developer.biblescroll.testing.planJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelsTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalog = FakePlanCatalogDao()
    private val progress = FakePlanProgressDao()
    private val customDao = aarnav100.developer.biblescroll.testing.FakeCustomPlanDao()
    private val scheduler = RecordingPlanScheduler()
    private val assets = MapAssets(
        mutableMapOf(
            "plans/index.json" to planIndex(listOf("john"), mapOf("gospels-and-acts" to listOf("john", "mark"), "letters" to listOf("hidden"))),
            "plans/john.json" to planJson("john", days = 10, title = "John in 10 days"),
            "plans/mark.json" to planJson("mark", days = 2),
            "plans/hidden.json" to planJson("hidden", status = "generated"),
        ),
    )
    private val today = LocalDate.parse("2026-11-18")
    private val reminders = ReminderRepository(FakePreferencesDataStore())
    private val notifications = object : NotificationAccess {
        var needs = false
        override fun canPost() = !needs
        override fun needsRuntimePermission() = needs
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val streaks = aarnav100.developer.biblescroll.testing.FakeStreakRepository()
    private val userData = aarnav100.developer.biblescroll.data.user.LocalUserDataRepository(aarnav100.developer.biblescroll.testing.FakeUserDao()) { 1L }

    private fun TestScope.repo() = PlanRepository(
        catalog, progress, customDao, PlanImporter(assets, catalog, json, 1, UnconfinedTestDispatcher(testScheduler)), scheduler,
        streaks, userData, aarnav100.developer.biblescroll.testing.bibleWithChapters(), json,
        { 1L }, UnconfinedTestDispatcher(testScheduler),
    )

    private fun TestScope.detail(id: String, repo: PlanRepository = repo()): PlanDetailViewModel {
        val vm = PlanDetailViewModel(id, repo, FakeBibleRepository(), reminders, notifications) { today }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private val PlanDetailViewModel.ready get() = uiState.value as PlanDetailUiState.Ready

    @Test
    fun browserShowsStartHereThenCategoriesAndMyPlans() = runTest {
        val repo = repo()
        repo.startPlan("mark", today, null)
        val vm = PlansBrowserViewModel(repo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        val s = vm.uiState.value as PlansBrowserUiState.Ready
        assertEquals(listOf(PlanImporter.START_HERE, "gospels-and-acts"), s.categories.map { it.id }) // "letters" only had a hidden plan
        assertEquals(listOf("mark"), s.myPlans.map { it.planId })
    }

    @Test
    fun detailShowsEveryDayWithReadablePassages() = runTest {
        val vm = detail("john")
        advanceUntilIdle()
        with(vm.ready) {
            assertEquals("John in 10 days", title)
            assertEquals(10, days.size)
            assertEquals(listOf("John 1:1–10"), days.first().passages)
            assertNull(active)
        }
    }

    @Test
    fun hiddenOrUnknownPlansShowNotFound() = runTest {
        val hidden = detail("hidden")
        val unknown = detail("nope")
        advanceUntilIdle()
        assertEquals(PlanDetailUiState.NotFound, hidden.uiState.value)
        assertEquals(PlanDetailUiState.NotFound, unknown.uiState.value)
    }

    @Test
    fun startDialogOffersTodayOrTomorrowWithTheDailyReminderTime() = runTest {
        reminders.setTime(LocalTime.of(6, 45))
        val vm = detail("john")
        vm.openStartDialog()
        advanceUntilIdle()
        val choices = vm.ready.startDialog!!
        assertEquals(listOf(today, today.plusDays(1)), choices.startOptions)
        assertEquals(today, choices.startDate)
        assertEquals(LocalTime.of(6, 45), choices.reminderTime)
        assertEquals(false, choices.reminderOn)
    }

    @Test
    fun startingTomorrowWithAReminderSavesItAndShowsProgress() = runTest {
        val vm = detail("john")
        val events = mutableListOf<PlanDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.openStartDialog()
        advanceUntilIdle()
        vm.chooseStartDate(today.plusDays(1))
        vm.setReminderOn(true)
        vm.setReminderTime(LocalTime.of(21, 0))
        vm.confirmStart()
        advanceUntilIdle()
        val saved = progress.enrollments.value.values.single()
        assertEquals("2026-11-19", saved.startDate)
        assertEquals(21 * 60, saved.reminderMinuteOfDay)
        assertNull(vm.ready.startDialog)
        assertNotNull(vm.ready.active)
        assertEquals(PlanDetailEvent.Started(today.plusDays(1), today), events.last())
        assertEquals(1, scheduler.planSyncs)
    }

    @Test
    fun turningOnTheReminderAsksForNotificationPermissionWhenNeeded() = runTest {
        notifications.needs = true
        val vm = detail("john")
        val events = mutableListOf<PlanDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        vm.openStartDialog()
        advanceUntilIdle()
        vm.setReminderOn(true)
        assertTrue(PlanDetailEvent.RequestNotificationPermission in events)
    }

    @Test
    fun dismissingTheDialogStartsNothing() = runTest {
        val vm = detail("john")
        vm.openStartDialog()
        advanceUntilIdle()
        vm.dismissStartDialog()
        vm.confirmStart()
        advanceUntilIdle()
        assertTrue(progress.enrollments.value.isEmpty())
    }

    @Test
    fun fallingBehindShowsTheNextUnreadDayAndAdjustScheduleLinesItUp() = runTest {
        val repo = repo()
        repo.startPlan("john", today.minusDays(4), null) // calendar says day 5
        val id = progress.enrollments.value.keys.single()
        repo.markDayRead(id, 1)
        val vm = detail("john", repo)
        advanceUntilIdle()
        assertEquals(2, vm.ready.nextDay)
        assertEquals(3, vm.ready.daysToCatchUp)
        vm.adjustSchedule()
        advanceUntilIdle()
        assertEquals(0, vm.ready.daysToCatchUp)
        assertEquals(2, vm.ready.nextDay)
        assertEquals(today.minusDays(1).toString(), progress.enrollments.value.getValue(id).startDate)
    }
}
