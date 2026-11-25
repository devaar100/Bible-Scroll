package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.core.content.DevotionalMonthState
import aarnav100.developer.biblescroll.core.content.DevotionalState
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.devotionals.DevotionalsRepository
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.domain.streak.StreakActivity
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakeDevotionalProgressDao
import aarnav100.developer.biblescroll.testing.FakeDevotionalStoreApp
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeStreakRepository
import aarnav100.developer.biblescroll.testing.devotionalContent
import aarnav100.developer.biblescroll.testing.sampleDevotional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DevotionalViewModelsTest {
    private val store = FakeDevotionalStoreApp()
    private val progress = FakeDevotionalProgressDao()
    private val streaks = FakeStreakRepository(today = LocalDate.parse("2026-11-18"))
    private val dataStore = FakePreferencesDataStore()
    private val settings = SettingsRepository(dataStore)
    private val preferences = ReaderPreferences(FakePreferencesDataStore())
    private val bible = FakeBibleRepository()
    private val date = LocalDate.parse("2026-11-18")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.repo(online: Boolean = false): DevotionalsRepository {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return DevotionalsRepository(devotionalContent(store, dispatcher, backgroundScope, online), progress, streaks) { 5L }
    }

    private fun <T> TestScope.collect(flow: kotlinx.coroutines.flow.Flow<T>) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }

    @Test
    fun readerPagesAreCoverVersesSectionsAndOptionalPrayer() = runTest {
        store.put(sampleDevotional("11-18"))
        val vm = DevotionalReaderViewModel(date, repo(), bible, preferences)
        collect(vm.uiState)
        advanceUntilIdle()
        val pages = vm.uiState.value.pages
        assertEquals(StoryPage.Cover, pages.first())
        assertEquals(
            listOf(VerseRef(43, 3, 15), VerseRef(43, 3, 16), VerseRef(43, 3, 17), VerseRef(19, 119, 105)),
            pages.filterIsInstance<StoryPage.VersePage>().map { it.verse.ref },
        )
        assertEquals(listOf(SectionKind.Context, SectionKind.Reflect, SectionKind.ApplyIt, SectionKind.Prayer),
            pages.filterIsInstance<StoryPage.Section>().map { it.kind })
        assertEquals("John 3:15", (pages[1] as StoryPage.VersePage).reference)
    }

    @Test
    fun noPrayerMeansNoPrayerPage() = runTest {
        store.put(sampleDevotional("11-18", prayer = null))
        val vm = DevotionalReaderViewModel(date, repo(), bible, preferences)
        collect(vm.uiState)
        advanceUntilIdle()
        assertEquals(SectionKind.ApplyIt, (vm.uiState.value.pages.last() as StoryPage.Section).kind)
    }

    @Test
    fun finishingSavesProgressAndCountsTheStreakOnce() = runTest {
        store.put(sampleDevotional("11-18"))
        val vm = DevotionalReaderViewModel(date, repo(), bible, preferences)
        collect(vm.uiState)
        advanceUntilIdle()
        vm.finish()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.finished)
        val row = progress.rows.value.getValue("2026-11-18")
        assertEquals("devotional-11-18" to "cv11-18", row.devotionalId to row.contentVersion)
        assertEquals(5L, row.updatedAt)
        vm.finish()
        advanceUntilIdle()
        assertEquals(listOf(StreakActivity.DevotionalFinished), streaks.activities)
    }

    @Test
    fun devotionalsStayVisibleWithHideAiStudiesOn() = runTest {
        store.put(sampleDevotional("11-18"))
        settings.setHideAiStudies(true)
        val vm = DevotionalDetailViewModel(date, repo(), bible, preferences)
        collect(vm.uiState)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.content is DevotionalState.Available)
    }

    @Test
    fun detailHasReferencesReadStatusAndTheVerseToShare() = runTest {
        store.put(sampleDevotional("11-18"))
        val repo = repo()
        repo.markFinished(date, sampleDevotional("11-18"))
        val vm = DevotionalDetailViewModel(date, repo, bible, preferences)
        collect(vm.uiState)
        advanceUntilIdle()
        with(vm.uiState.value) {
            assertEquals(listOf("John 3:15–17", "Psalms 119:105"), passages)
            assertTrue(read)
            assertEquals(VerseRef(43, 3, 15), shareVerse?.ref)
        }
    }

    @Test
    fun offlineAndNotDownloadedSaysSo() = runTest {
        val vm = DevotionalDetailViewModel(date, repo(online = false), bible, preferences)
        collect(vm.uiState)
        advanceUntilIdle()
        assertEquals(DevotionalState.NotDownloaded("11", StudyState.NotDownloaded.Reason.OFFLINE), vm.uiState.value.content)
        assertNull(vm.uiState.value.shareVerse)
    }

    @Test
    fun archiveListsPastDaysByMonthNewestFirstWithReadStatus() = runTest {
        store.put(sampleDevotional("11-18"), sampleDevotional("11-17"), sampleDevotional("10-31"))
        val repo = repo()
        repo.markFinished(LocalDate.parse("2026-11-17"), sampleDevotional("11-17"))
        val vm = DevotionalArchiveViewModel(repo)
        collect(vm.uiState)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals((11 downTo 1).toList(), s.months.map { it.month })
        val november = s.months.first()
        assertEquals(18, november.days.size)
        assertEquals(LocalDate.parse("2026-11-18"), november.days.first().date)
        assertEquals("Title 11-17" to true, november.days[1].title to november.days[1].read)
        assertNull(november.days[2].title) // 16 Nov isn't published in this test
        assertEquals(1, s.readCount)
        // Months not on the device, offline: say so.
        assertEquals(DevotionalMonthState.NotDownloaded(StudyState.NotDownloaded.Reason.OFFLINE), s.months.last().state)
        assertEquals(28, s.months.first { it.month == 2 }.days.size) // 2026: no bonus day
    }

    @Test
    fun homeShowsTodayTomorrowsThemeAndReadToday() = runTest {
        store.put(sampleDevotional("11-18"), sampleDevotional("11-19"))
        val repo = repo()
        val vm = HomeDevotionalViewModel(repo)
        collect(vm.uiState)
        advanceUntilIdle()
        assertEquals("Theme 11-19", vm.uiState.value.tomorrow?.theme)
        assertFalse(vm.uiState.value.readToday)
        repo.markFinished(date, sampleDevotional("11-18"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.readToday)
        streaks.today.value = LocalDate.parse("2026-11-19") // midnight
        advanceUntilIdle()
        assertFalse(vm.uiState.value.readToday)
        assertEquals("Title 11-19", (vm.uiState.value.todayState as DevotionalState.Available).devotional.title)
    }
}
