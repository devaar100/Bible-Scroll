package aarnav100.developer.biblescroll.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderVersesTest {

    @Test
    fun sixtyDistinctValidRefs() {
        assertEquals(60, ReminderVerses.refs.size)
        assertEquals(60, ReminderVerses.refs.toSet().size)
        assertTrue(ReminderVerses.refs.all { it.bookId in 1..66 && it.chapter >= 1 && it.verse >= 1 })
    }

    @Test
    fun sameDaySameVerseNextDayNext() {
        val day = LocalDate.of(2026, 11, 17)
        assertEquals(ReminderVerses.candidatesFor(day), ReminderVerses.candidatesFor(day))
        assertNotEquals(ReminderVerses.candidatesFor(day).first(), ReminderVerses.candidatesFor(day.plusDays(1)).first())
    }

    @Test
    fun everyVerseComesUpOnceEverySixtyDays() {
        val start = LocalDate.of(2026, 1, 1)
        val firsts = (0 until 60).map { ReminderVerses.candidatesFor(start.plusDays(it.toLong())).first() }
        assertEquals(ReminderVerses.refs.toSet(), firsts.toSet())
    }

    @Test
    fun candidatesAreTheWholeListAsFallbacks() {
        val candidates = ReminderVerses.candidatesFor(LocalDate.of(1969, 7, 20)) // negative epoch day
        assertEquals(60, candidates.size)
        assertEquals(ReminderVerses.refs.toSet(), candidates.toSet())
    }
}
