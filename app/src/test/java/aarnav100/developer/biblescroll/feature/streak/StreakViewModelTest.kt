package aarnav100.developer.biblescroll.feature.streak

import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.domain.streak.DayStatus
import aarnav100.developer.biblescroll.domain.streak.StreakDay
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeStreakRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class StreakViewModelTest {

    private val streaks = FakeStreakRepository(today = LocalDate.parse("2026-11-19"))
    private val settings = SettingsRepository(FakePreferencesDataStore())

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun counted(iso: String) = StreakDay(LocalDate.parse(iso), versesViewed = 5, countedAt = 1L)

    private fun TestScope.viewModel(): StreakViewModel {
        val vm = StreakViewModel(streaks, settings, UnconfinedTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return vm
    }

    private val StreakViewModel.ready get() = uiState.value as StreakUiState.Ready

    @Test
    fun showsCurrentLongestWeekAndMonth() = runTest {
        streaks.days.value = listOf(counted("2026-11-16"), counted("2026-11-17"), counted("2026-11-19"))
        val vm = viewModel()
        advanceUntilIdle()
        with(vm.ready) {
            assertEquals(1, current)
            assertEquals(2, longest)
            assertTrue(todayCounts)
            assertEquals(7, week.size)
            assertEquals(YearMonth.of(2026, 11), month)
            assertEquals(30, monthDays.size)
            assertFalse(graceDayEnabled)
        }
    }

    @Test
    fun graceSettingIsAppliedRightAway() = runTest {
        streaks.days.value = listOf(counted("2026-11-16"), counted("2026-11-17"), counted("2026-11-19"))
        val vm = viewModel()
        vm.setGraceDay(true)
        advanceUntilIdle()
        assertTrue(vm.ready.graceDayEnabled)
        assertEquals(3, vm.ready.current)
        assertEquals(DayStatus.Grace, vm.ready.week[2].status)
    }

    @Test
    fun monthNavigationStopsAtTheFirstReadingMonthAndThisMonth() = runTest {
        streaks.days.value = listOf(counted("2026-09-30"), counted("2026-11-19"))
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.ready.canShowPreviousMonth)
        assertFalse(vm.ready.canShowNextMonth)
        repeat(5) { vm.showPreviousMonth() }
        assertEquals(YearMonth.of(2026, 9), vm.ready.month)
        assertFalse(vm.ready.canShowPreviousMonth)
        vm.showNextMonth()
        assertEquals(YearMonth.of(2026, 10), vm.ready.month)
        vm.resetMonth()
        assertEquals(YearMonth.of(2026, 11), vm.ready.month)
    }

    @Test
    fun midnightMovesTodayAndTheWeek() = runTest {
        streaks.days.value = listOf(counted("2026-11-22"))
        streaks.today.value = LocalDate.parse("2026-11-22") // Sunday
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(1, vm.ready.current)
        streaks.today.value = LocalDate.parse("2026-11-23") // Monday
        advanceUntilIdle()
        assertEquals(LocalDate.parse("2026-11-23"), vm.ready.week.first().date)
        assertEquals(1, vm.ready.current) // today still open
        assertFalse(vm.ready.todayCounts)
    }
}
