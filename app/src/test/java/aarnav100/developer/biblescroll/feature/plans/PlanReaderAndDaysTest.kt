package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.library.LibraryRepository
import aarnav100.developer.biblescroll.data.plans.PlanImporter
import aarnav100.developer.biblescroll.data.plans.PlanRepository
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePlanCatalogDao
import aarnav100.developer.biblescroll.testing.FakePlanProgressDao
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeStreakRepository
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.testing.MapAssets
import aarnav100.developer.biblescroll.testing.RecordingPlanScheduler
import aarnav100.developer.biblescroll.testing.bibleWithChapters
import aarnav100.developer.biblescroll.testing.day
import aarnav100.developer.biblescroll.testing.passage
import aarnav100.developer.biblescroll.testing.planIndex
import aarnav100.developer.biblescroll.testing.planJson
import aarnav100.developer.biblescroll.ui.navigation.PlanReaderRoute
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PlanReaderAndDaysTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalog = FakePlanCatalogDao()
    private val progress = FakePlanProgressDao()
    private val customDao = aarnav100.developer.biblescroll.testing.FakeCustomPlanDao()
    private val userDao = FakeUserDao()
    private val userData = LocalUserDataRepository(userDao) { 1L }
    private val bible = FakeBibleRepository()
    private val preferences = ReaderPreferences(FakePreferencesDataStore())
    private val assets = MapAssets(
        mutableMapOf(
            "plans/index.json" to planIndex(emptyList(), mapOf("c" to listOf("p"))),
            "plans/p.json" to planJson(
                "p",
                daysJson = "[" + day(1, passage("JHN", 3, 15, 3, 17), intro = "Nicodemus comes at night.") + "," +
                    day(2, passage("GEN", 1, 1, 1, 3), passage("JHN", 14, 6)) + "]",
            ),
        ),
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.repo() = PlanRepository(
        catalog, progress, customDao, PlanImporter(assets, catalog, json, 1, UnconfinedTestDispatcher(testScheduler)), RecordingPlanScheduler(),
        FakeStreakRepository(), userData, bibleWithChapters(), json, { 1L }, UnconfinedTestDispatcher(testScheduler),
    )

    private fun TestScope.reader(repo: PlanRepository, id: String, dayNumber: Int): PlanReaderViewModel {
        val vm = PlanReaderViewModel(PlanReaderRoute(id, dayNumber), repo, bible, userData, preferences)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private val PlanReaderViewModel.ready get() = uiState.value as PlanReaderUiState.Ready

    @Test
    fun pagesAreIntroThenTheDaysVersesThenFinish() = runTest {
        val repo = repo()
        val id = repo.startPlan("p", LocalDate.parse("2026-11-18"), null)
        val vm = reader(repo, id, 1)
        advanceUntilIdle()
        val pages = vm.ready.pages
        val intro = pages.first() as ReaderPage.Intro
        assertEquals("Nicodemus comes at night.", intro.intro)
        assertEquals(listOf("John 3:15\u201317"), intro.passages)
        assertEquals(listOf(VerseRef(43, 3, 15), VerseRef(43, 3, 16), VerseRef(43, 3, 17)), pages.filterIsInstance<ReaderPage.VersePage>().map { it.verse.ref })
        assertEquals(ReaderPage.Finish(1, read = false, nextDay = null), pages.last())
    }

    @Test
    fun aDayWithSeveralPassagesReadsThemInOrderAndStopsAtEachEnd() = runTest {
        val repo = repo()
        val id = repo.startPlan("p", LocalDate.parse("2026-11-18"), null)
        val vm = reader(repo, id, 2)
        advanceUntilIdle()
        assertEquals(
            listOf(VerseRef(1, 1, 1), VerseRef(1, 1, 2), VerseRef(1, 1, 3), VerseRef(43, 14, 6)),
            vm.ready.pages.filterIsInstance<ReaderPage.VersePage>().map { it.verse.ref },
        )
    }

    @Test
    fun theReadersTranslationIsUsedAndMissingVersesAreSkipped() = runTest {
        preferences.setTranslationId("SHORT")
        val repo = repo()
        val id = repo.startPlan("p", LocalDate.parse("2026-11-18"), null)
        val vm = reader(repo, id, 1)
        advanceUntilIdle()
        assertEquals("SHORT", vm.ready.translationId)
        assertEquals(2, vm.ready.pages.filterIsInstance<ReaderPage.VersePage>().size) // SHORT lacks John 3:16
    }

    @Test
    fun markingReadShowsNextDayThenTheLastDayCompletes() = runTest {
        val repo = repo()
        val id = repo.startPlan("p", LocalDate.parse("2026-11-18"), null)
        val first = reader(repo, id, 1)
        val events = mutableListOf<PlanReaderEvent>()
        advanceUntilIdle()
        first.markRead()
        advanceUntilIdle()
        assertEquals(ReaderPage.Finish(1, read = true, nextDay = 2), first.ready.pages.last())
        val second = reader(repo, id, 2)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { second.events.collect { events += it } }
        advanceUntilIdle()
        second.markRead()
        advanceUntilIdle()
        assertEquals(listOf<PlanReaderEvent>(PlanReaderEvent.Completed(id)), events)
    }

    @Test
    fun anUnknownDayOrPlanIsNotFound() = runTest {
        val repo = repo()
        val id = repo.startPlan("p", LocalDate.parse("2026-11-18"), null)
        val missingDay = reader(repo, id, 9)
        val missingPlan = reader(repo, "nope", 1)
        advanceUntilIdle()
        assertEquals(PlanReaderUiState.NotFound, missingDay.uiState.value)
        assertEquals(PlanReaderUiState.NotFound, missingPlan.uiState.value)
    }

    @Test
    fun daysAndNotesShowReadStatusAndOnlyNotesInsideThePlan() = runTest {
        val repo = repo()
        val id = repo.startPlan("p", LocalDate.parse("2026-11-18"), null)
        repo.markDayRead(id, 1)
        userData.saveNote(VerseRef(43, 14, 6), "The way")
        userData.saveNote(VerseRef(43, 3, 16), "Loved")
        userData.saveNote(VerseRef(19, 119, 105), "Lamp") // not in this plan
        val vm = PlanDaysViewModel(id, repo, bible, LibraryRepository(userDao))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
        val s = vm.uiState.value as PlanDaysUiState.Ready
        assertEquals(listOf(true to false, false to true), s.days.map { it.read to it.isNext })
        assertEquals(listOf("John 3:16" to 1, "John 14:6" to 2), s.notes.map { it.reference to it.dayNumber })
        assertTrue(s.notes.none { it.text == "Lamp" })
    }
}
