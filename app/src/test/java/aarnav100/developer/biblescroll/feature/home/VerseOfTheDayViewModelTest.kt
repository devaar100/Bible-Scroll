package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.votd.LocalClock
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.votdRepository
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class VerseOfTheDayViewModelTest {

    private val chicago = ZoneId.of("America/Chicago")
    private val link = PendingVerseLink()

    /** Starts at [start] and moves with the test's virtual time; [zone] can change underneath, like a trip abroad. */
    private class VirtualClock(private val start: ZonedDateTime, private val elapsedMs: () -> Long) : LocalClock {
        var zone: ZoneId = start.zone
        override fun now(): ZonedDateTime = start.plus(Duration.ofMillis(elapsedMs())).withZoneSameInstant(zone)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(clock: LocalClock): VerseOfTheDayViewModel {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return VerseOfTheDayViewModel(votdRepository(), FakeBibleRepository(), clock, link).also { vm ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        }
    }

    private fun VerseOfTheDayViewModel.shownRef() = (uiState.value as VotdUiState.Ready).votd.verse.ref

    @Test
    fun updatesAtLocalMidnightWhileOpen() = runTest {
        val clock = VirtualClock(ZonedDateTime.of(2028, 3, 16, 23, 59, 0, 0, chicago)) { testScheduler.currentTime }
        val vm = viewModel(clock)
        runCurrent()
        assertEquals(VerseRef(43, 3, 16), vm.shownRef())

        advanceTimeBy(Duration.ofSeconds(30).toMillis())
        assertEquals(VerseRef(43, 3, 16), vm.shownRef())

        advanceTimeBy(Duration.ofSeconds(32).toMillis())
        assertEquals(VerseRef(43, 3, 17), vm.shownRef())
    }

    @Test
    fun resumeAfterATimeZoneChangeShowsTheNewLocalDay() = runTest {
        // 18:00 in Chicago on 03-16 is already 03-17 in Kiribati.
        val clock = VirtualClock(ZonedDateTime.of(2028, 3, 16, 18, 0, 0, 0, chicago)) { testScheduler.currentTime }
        val vm = viewModel(clock)
        runCurrent()
        assertEquals(VerseRef(43, 3, 16), vm.shownRef())

        clock.zone = ZoneId.of("Pacific/Kiritimati")
        vm.onResume()
        runCurrent()

        assertEquals(VerseRef(43, 3, 17), vm.shownRef())
    }

    @Test
    fun hiddenWhenTodayHasNoEntry() = runTest {
        val vm = viewModel(VirtualClock(ZonedDateTime.of(2028, 6, 1, 9, 0, 0, 0, chicago)) { testScheduler.currentTime })
        runCurrent()
        assertEquals(VotdUiState.Hidden, vm.uiState.value)
    }

    @Test
    fun readInContextOpensTheVerseInTheFeed() = runTest {
        val vm = viewModel(VirtualClock(ZonedDateTime.of(2028, 3, 16, 9, 0, 0, 0, chicago)) { testScheduler.currentTime })
        runCurrent()

        vm.readInContext()

        assertEquals(VerseRef(43, 3, 16), link.ref.value)
    }
}
