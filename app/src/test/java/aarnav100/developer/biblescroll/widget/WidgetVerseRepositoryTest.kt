package aarnav100.developer.biblescroll.widget

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FakeHomeScreenWidgets
import aarnav100.developer.biblescroll.testing.FakeLocalClock
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeVotdWidgetScheduler
import aarnav100.developer.biblescroll.testing.FakeWidgetRefresher
import aarnav100.developer.biblescroll.testing.widgetVerseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNull
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetVerseRepositoryTest {

    private val refresher = FakeWidgetRefresher()
    private val dataStore = FakePreferencesDataStore()
    private val repo = widgetVerseRepository(dataStore, refresher = refresher)

    private val john316 = VerseRef(43, 3, 16)

    @Test
    fun defaultsToPsalm119_105UntilSet() = runTest {
        val verse = repo.widgetVerse.first()
        assertEquals(VerseRef(19, 119, 105), verse.ref)
        assertEquals("WEB", verse.translationId)
        assertTrue(verse.isDefault)

        val display = repo.display.first()
        assertEquals("Psalms 119:105", display.reference)
        assertEquals("WEB ${verse.ref}", display.text)
    }

    @Test
    fun setStoresVerseAndTranslationAndRefreshesWidgets() = runTest {
        repo.setWidgetVerse(john316, "WEB")

        assertEquals(WidgetVerse(john316, "WEB", isDefault = false), repo.widgetVerse.first())
        assertEquals(1, refresher.refreshes)
        val display = repo.display.first()
        assertEquals("John 3:16", display.reference)
        assertEquals(john316, display.ref)
    }

    @Test
    fun everyChangeRefreshes() = runTest {
        repo.setWidgetVerse(john316, "WEB")
        repo.setWidgetVerse(VerseRef(1, 1, 1), "SHORT")

        assertEquals(2, refresher.refreshes)
        assertEquals("SHORT", repo.widgetVerse.first().translationId)
        assertFalse(repo.widgetVerse.first().isDefault)
    }

    @Test
    fun verseMissingFromItsTranslationFallsBackToTheDefault() = runTest {
        // The fake SHORT translation has no John 3:16.
        repo.setWidgetVerse(john316, "SHORT")

        val display = repo.display.first()
        assertEquals(WidgetVerseRepository.DEFAULT.ref, display.ref)
        assertEquals("Psalms 119:105", display.reference)
        assertEquals("WEB", display.translationId)
    }

    @Test
    fun survivesANewRepositoryInstance() = runTest {
        repo.setWidgetVerse(john316, "WEB")

        val reopened = widgetVerseRepository(dataStore)

        assertEquals(john316, reopened.widgetVerse.first().ref)
    }

    // ---- Verse of the Day mode (B11 / H1) ----

    private val chicago = ZoneId.of("America/Chicago")
    private val clock = FakeLocalClock(ZonedDateTime.of(2028, 3, 16, 9, 0, 0, 0, chicago))
    private val scheduler = FakeVotdWidgetScheduler()
    private val home = FakeHomeScreenWidgets(placed = true)
    private val votdRepo = widgetVerseRepository(dataStore, refresher = refresher, clock = clock, scheduler = scheduler, homeScreen = home)

    @Test
    fun fixedIsTheDefaultMode() = runTest {
        assertEquals(WidgetMode.FIXED, votdRepo.mode.first())
        assertFalse(votdRepo.display.first().isVerseOfTheDay)
    }

    @Test
    fun verseOfTheDayShowsTodaysVerseAndArmsTheMidnightRedraw() = runTest {
        votdRepo.setVerseOfTheDay()

        val display = votdRepo.display.first()
        assertTrue(display.isVerseOfTheDay)
        assertEquals(john316, display.ref) // 03-16 in the fake list
        assertEquals("John 3:16", display.reference)
        assertEquals(1, refresher.refreshes)
        // Inexact alarm just after the next local midnight.
        assertEquals(ZonedDateTime.of(2028, 3, 17, 0, 1, 0, 0, chicago), scheduler.scheduledAt)
    }

    @Test
    fun midnightMovesToTheNextDaysVerseAndBooksTheNextNight() = runTest {
        votdRepo.setVerseOfTheDay()
        clock.now = ZonedDateTime.of(2028, 3, 17, 0, 1, 0, 0, chicago)

        votdRepo.onDayOrClockChanged()

        assertEquals(VerseRef(43, 3, 17), votdRepo.display.first().ref)
        assertEquals(2, refresher.refreshes)
        assertEquals(ZonedDateTime.of(2028, 3, 18, 0, 1, 0, 0, chicago), scheduler.scheduledAt)
    }

    @Test
    fun timeZoneChangeCanMoveTheWidgetToAnotherDay() = runTest {
        // 18:00 on 03-16 in Chicago is already 03-17 in Kiribati.
        clock.now = ZonedDateTime.of(2028, 3, 16, 18, 0, 0, 0, chicago)
        votdRepo.setVerseOfTheDay()
        assertEquals(john316, votdRepo.display.first().ref)

        clock.moveTo(ZoneId.of("Pacific/Kiritimati"))
        votdRepo.onDayOrClockChanged()

        assertEquals(VerseRef(43, 3, 17), votdRepo.display.first().ref)
        assertEquals(ZoneId.of("Pacific/Kiritimati"), scheduler.scheduledAt!!.zone)
    }

    @Test
    fun noAlarmWithoutAPlacedWidgetOrInFixedMode() = runTest {
        home.placed = false
        votdRepo.setVerseOfTheDay()
        assertNull(scheduler.scheduledAt)

        home.placed = true
        votdRepo.syncMidnightUpdate()
        assertEquals(ZonedDateTime.of(2028, 3, 17, 0, 1, 0, 0, chicago), scheduler.scheduledAt)

        votdRepo.setWidgetVerse(john316, "WEB")
        assertEquals(WidgetMode.FIXED, votdRepo.mode.first())
        assertNull(scheduler.scheduledAt)
    }

    @Test
    fun verseOfTheDayFallsBackToTheFixedVerseWhenTodayHasNoEntry() = runTest {
        clock.now = ZonedDateTime.of(2028, 6, 1, 9, 0, 0, 0, chicago) // not in the fake list
        votdRepo.setWidgetVerse(VerseRef(1, 1, 1), "WEB")
        votdRepo.setVerseOfTheDay()

        val display = votdRepo.display.first()
        assertFalse(display.isVerseOfTheDay)
        assertEquals(VerseRef(1, 1, 1), display.ref)
    }
}
