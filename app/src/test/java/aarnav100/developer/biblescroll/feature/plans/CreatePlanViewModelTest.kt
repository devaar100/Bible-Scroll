package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.data.plans.CustomPlanBuilder
import aarnav100.developer.biblescroll.data.plans.Pace
import aarnav100.developer.biblescroll.data.plans.PlanImporter
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.reminder.NotificationAccess
import aarnav100.developer.biblescroll.data.reminder.ReminderRepository
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.DirectoryAssets
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakeCustomPlanDao
import aarnav100.developer.biblescroll.testing.FakePlanCatalogDao
import aarnav100.developer.biblescroll.testing.FakePlanProgressDao
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeStreakRepository
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.testing.MapAssets
import aarnav100.developer.biblescroll.testing.RecordingPlanScheduler
import aarnav100.developer.biblescroll.testing.bibleWithChapters
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CreatePlanViewModelTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalog = FakePlanCatalogDao()
    private val progress = FakePlanProgressDao()
    private val customDao = FakeCustomPlanDao()
    private val today = LocalDate.parse("2026-11-18")
    private val titles = object : PlanTitles {
        override fun title(names: List<String>, contiguous: Boolean, days: Int) = "${names.joinToString("+")}|$contiguous|$days"
        override fun books(names: List<String>) = names.joinToString(", ")
    }
    private val notifications = object : NotificationAccess {
        override fun canPost() = true
        override fun needsRuntimePermission() = false
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): Pair<CreatePlanViewModel, MutableList<CreatePlanEvent>> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = PlanRepository(
            catalog, progress, customDao, PlanImporter(MapAssets(mutableMapOf()), catalog, json, 1, dispatcher), RecordingPlanScheduler(),
            FakeStreakRepository(), LocalUserDataRepository(FakeUserDao()) { 1L }, bibleWithChapters(), json, { 1L }, dispatcher,
        )
        val builder = CustomPlanBuilder(DirectoryAssets(File("src/main/assets")), dispatcher)
        val vm = CreatePlanViewModel(
            FakeBibleRepository(), builder, repo, ReminderRepository(FakePreferencesDataStore()), notifications, titles, { today }, dispatcher,
        )
        val events = mutableListOf<CreatePlanEvent>()
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        backgroundScope.launch(dispatcher) { vm.events.collect { events += it } }
        return vm to events
    }

    @Test
    fun quickPicksAreTheGospelsPsalmsAndProverbsThatExist() = runTest {
        val (vm, _) = viewModel()
        advanceUntilIdle()
        // FakeBibleRepository has Genesis, Psalms and John.
        assertEquals(listOf(19, 43), vm.uiState.value.quickPicks.map { it.id }.sorted())
    }

    @Test
    fun cantMoveOnWithoutABook() = runTest {
        val (vm, _) = viewModel()
        vm.next()
        assertEquals(CreateStep.Books, vm.uiState.value.step)
        assertFalse(vm.back()) // first step: the screen closes
    }

    @Test
    fun theLengthUpdatesLiveAndMatchesThePipelinePlan() = runTest {
        val (vm, _) = viewModel()
        vm.toggleBook(43)
        vm.next()
        vm.setPace(Pace.Days(21))
        advanceUntilIdle()
        val preview = vm.uiState.value.preview!!
        assertEquals(21, preview.days)
        assertEquals(5, preview.minutesPerDay)
        assertEquals(listOf("John 1:1–51"), preview.firstDays.first())
        assertEquals("John|true|21", preview.defaultTitle)
        vm.setPace(Pace.MinutesPerDay(20))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.preview!!.days < 21)
    }

    @Test
    fun searchFiltersBooksAndStepsGoBack() = runTest {
        val (vm, _) = viewModel()
        vm.onQueryChange("ps")
        advanceUntilIdle()
        assertEquals(listOf("Psalms"), vm.uiState.value.books.map { it.name })
        vm.toggleBook(19)
        vm.next()
        vm.next()
        assertEquals(CreateStep.Summary, vm.uiState.value.step)
        assertTrue(vm.back())
        assertEquals(CreateStep.Pace, vm.uiState.value.step)
    }

    @Test
    fun startingSavesTheEditedPlanStartsItAndOpensIt() = runTest {
        val (vm, events) = viewModel()
        vm.toggleBook(43)
        vm.next()
        vm.setPace(Pace.Days(21))
        vm.next()
        vm.setTitle("  My John  ")
        vm.openStartDialog()
        advanceUntilIdle()
        vm.chooseStartDate(today.plusDays(1))
        vm.confirmStart()
        advanceUntilIdle()
        val saved = customDao.rows.value.values.single()
        assertEquals("My John", saved.title)
        assertEquals("43", saved.bookIds)
        assertEquals("days" to 21, saved.paceKind to saved.paceValue)
        val enrollment = progress.enrollments.value.values.single()
        assertEquals(saved.id, enrollment.planId)
        assertEquals("2026-11-19", enrollment.startDate)
        assertEquals(listOf<CreatePlanEvent>(CreatePlanEvent.Created(saved.id)), events)
        assertNull(vm.uiState.value.startDialog)
    }
}
