package aarnav100.developer.biblescroll.data.votd

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FAKE_VOTD_CSV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Date selection by local month-day, and the next-local-midnight maths behind the card and widget updates. */
class VerseOfTheDayTest {

    private val fake = VotdList.parse(FAKE_VOTD_CSV)
    private val real = VotdList.parse(File("src/main/assets/votd.csv").readText())

    @Test
    fun leapDayHasItsOwnEntry() {
        assertEquals(VerseRef(1, 1, 3), fake.entryFor(LocalDate.of(2028, 2, 29))!!.ref)
        // In a common year Feb 28 is followed by Mar 1; the 02-29 entry is simply not reached.
        assertEquals(VerseRef(1, 1, 2), fake.entryFor(LocalDate.of(2027, 2, 28))!!.ref)
        assertEquals(VerseRef(19, 119, 105), fake.entryFor(LocalDate.of(2027, 2, 28).plusDays(1))!!.ref)
    }

    @Test
    fun sameMonthDayGivesTheSameVerseInEveryYear() {
        for (year in listOf(2026, 2027, 2028, 2100)) {
            assertEquals(real.entryFor(LocalDate.of(2026, 7, 4)), real.entryFor(LocalDate.of(year, 7, 4)))
        }
        // 02-29 in two different leap years.
        assertEquals(real.entryFor(LocalDate.of(2028, 2, 29)), real.entryFor(LocalDate.of(2032, 2, 29)))
    }

    @Test
    fun monthEndsRollToTheFirstOfTheNextMonth() {
        val ends = listOf(
            LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2028, 2, 29), LocalDate.of(2027, 4, 30),
            LocalDate.of(2027, 6, 30), LocalDate.of(2027, 9, 30), LocalDate.of(2027, 11, 30), LocalDate.of(2027, 12, 31),
        )
        for (end in ends) {
            val next = end.plusDays(1)
            assertEquals(1, next.dayOfMonth)
            assertNotEquals("$end and $next show the same verse", real.entryFor(end), real.entryFor(next))
            assertEquals(next, VotdTime.nextLocalMidnight(end.atTime(23, 59).atZone(ZoneId.of("UTC"))).toLocalDate())
        }
    }

    @Test
    fun yearChangeGoesFromDecember31ToJanuary1() {
        val zone = ZoneId.of("Europe/Madrid")
        val newYearsEve = ZonedDateTime.of(2027, 12, 31, 22, 30, 0, 0, zone)
        val midnight = VotdTime.nextLocalMidnight(newYearsEve)

        assertEquals(ZonedDateTime.of(2028, 1, 1, 0, 0, 0, 0, zone), midnight)
        assertEquals(VerseRef(43, 14, 6), fake.entryFor(newYearsEve.toLocalDate())!!.ref)
        assertEquals(VerseRef(1, 1, 1), fake.entryFor(midnight.toLocalDate())!!.ref)
    }

    @Test
    fun timeZoneChangeUsesTheNewLocalDate() {
        // One instant: still 03-16 in Samoa (UTC-11), already 03-17 in Kiribati (UTC+14).
        val instant = Instant.parse("2028-03-16T12:00:00Z")
        val pagoPago = instant.atZone(ZoneId.of("Pacific/Pago_Pago"))
        val kiritimati = instant.atZone(ZoneId.of("Pacific/Kiritimati"))

        assertEquals(VerseRef(43, 3, 16), fake.entryFor(pagoPago.toLocalDate())!!.ref)
        assertEquals(VerseRef(43, 3, 17), fake.entryFor(kiritimati.toLocalDate())!!.ref)
        // The next redraw follows the zone it's computed in.
        assertEquals(LocalDateTime.of(2028, 3, 17, 0, 0), VotdTime.nextLocalMidnight(pagoPago).toLocalDateTime())
        assertEquals(LocalDateTime.of(2028, 3, 18, 0, 0), VotdTime.nextLocalMidnight(kiritimati).toLocalDateTime())
    }

    @Test
    fun midnightSkippedByDaylightSavingUsesTheFirstMomentOfTheDay() {
        // Cuba springs forward at 00:00 on 2026-03-08, so that day starts at 01:00.
        val havana = ZoneId.of("America/Havana")
        val next = VotdTime.nextLocalMidnight(ZonedDateTime.of(2026, 3, 7, 21, 0, 0, 0, havana))

        assertEquals(LocalDate.of(2026, 3, 8), next.toLocalDate())
        assertEquals(LocalTime.of(1, 0), next.toLocalTime())
    }

    @Test
    fun aDateMissingFromTheListHasNoEntry() {
        assertNull(fake.entryFor(LocalDate.of(2028, 6, 1)))
    }
}
