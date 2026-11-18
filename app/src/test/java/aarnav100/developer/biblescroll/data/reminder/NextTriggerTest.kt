package aarnav100.developer.biblescroll.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NextTriggerTest {

    private val newYork = ZoneId.of("America/New_York") // DST 2026: Mar 8 (gap 02:00-03:00), Nov 1 (overlap 01:00-02:00)
    private val london = ZoneId.of("Europe/London")
    private val kolkata = ZoneId.of("Asia/Kolkata") // no DST

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi, s), zone)

    private val eight = LocalTime.of(8, 0)

    @Test
    fun laterTodayFiresToday() {
        assertEquals(at(kolkata, 2026, 11, 17, 8, 0), nextTrigger(at(kolkata, 2026, 11, 17, 6, 30), eight))
    }

    @Test
    fun alreadyPassedFiresTomorrow() {
        assertEquals(at(kolkata, 2026, 11, 18, 8, 0), nextTrigger(at(kolkata, 2026, 11, 17, 9, 0), eight))
    }

    @Test
    fun exactlyNowMeansTomorrowSoAFiringAlarmNeverReschedulesItself() {
        assertEquals(at(kolkata, 2026, 11, 18, 8, 0), nextTrigger(at(kolkata, 2026, 11, 17, 8, 0), eight))
        // Inexact alarms fire late; rescheduling a few minutes after still means tomorrow.
        assertEquals(at(kolkata, 2026, 11, 18, 8, 0), nextTrigger(at(kolkata, 2026, 11, 17, 8, 7), eight))
    }

    @Test
    fun oneSecondBeforeStillFiresToday() {
        assertEquals(at(kolkata, 2026, 11, 17, 8, 0), nextTrigger(at(kolkata, 2026, 11, 17, 7, 59, 59), eight))
    }

    @Test
    fun acrossMidnight() {
        val midnight = LocalTime.MIDNIGHT
        // 23:59 with a 00:00 reminder -> one minute later, on the next date.
        assertEquals(at(kolkata, 2026, 11, 18, 0, 0), nextTrigger(at(kolkata, 2026, 11, 17, 23, 59), midnight))
        // Late-night reminder already passed -> tomorrow night.
        val late = LocalTime.of(23, 30)
        assertEquals(at(kolkata, 2026, 11, 18, 23, 30), nextTrigger(at(kolkata, 2026, 11, 17, 23, 45), late))
        // Just after midnight, a 23:30 reminder is still today.
        assertEquals(at(kolkata, 2026, 11, 18, 23, 30), nextTrigger(at(kolkata, 2026, 11, 18, 0, 5), late))
    }

    @Test
    fun acrossYearEndAndLeapDay() {
        assertEquals(at(kolkata, 2027, 1, 1, 8, 0), nextTrigger(at(kolkata, 2026, 12, 31, 21, 0), eight))
        assertEquals(at(kolkata, 2028, 2, 29, 8, 0), nextTrigger(at(kolkata, 2028, 2, 28, 9, 0), eight))
        assertEquals(at(kolkata, 2028, 3, 1, 8, 0), nextTrigger(at(kolkata, 2028, 2, 29, 9, 0), eight))
    }

    @Test
    fun springForwardKeepsWallClockTimeAndTheDayIs23Hours() {
        val before = nextTrigger(at(newYork, 2026, 3, 7, 7, 0), eight) // Sat 08:00 EST
        val after = nextTrigger(before, eight) // Sun 08:00 EDT
        assertEquals(LocalTime.of(8, 0), after.toLocalTime())
        assertEquals(ZoneOffset.ofHours(-4), after.offset)
        assertEquals(Duration.ofHours(23), Duration.between(before, after))
    }

    @Test
    fun fallBackKeepsWallClockTimeAndTheDayIs25Hours() {
        val before = nextTrigger(at(newYork, 2026, 10, 31, 7, 0), eight) // Sat 08:00 EDT
        val after = nextTrigger(before, eight) // Sun 08:00 EST
        assertEquals(LocalTime.of(8, 0), after.toLocalTime())
        assertEquals(ZoneOffset.ofHours(-5), after.offset)
        assertEquals(Duration.ofHours(25), Duration.between(before, after))
    }

    @Test
    fun timeInsideTheSpringForwardGapMovesLaterByTheGap() {
        // 02:30 doesn't exist on 2026-03-08 in New York; it fires at 03:30 EDT that day.
        val trigger = nextTrigger(at(newYork, 2026, 3, 8, 1, 0), LocalTime.of(2, 30))
        assertEquals(at(newYork, 2026, 3, 8, 3, 30), trigger)
        assertEquals(ZoneOffset.ofHours(-4), trigger.offset)
        // ...and the next day is back to 02:30.
        assertEquals(LocalTime.of(2, 30), nextTrigger(trigger, LocalTime.of(2, 30)).toLocalTime())
    }

    @Test
    fun timeInsideTheFallBackOverlapFiresOnceAtTheFirstOccurrence() {
        val time = LocalTime.of(1, 30) // happens twice on 2026-11-01 in New York
        val first = nextTrigger(at(newYork, 2026, 11, 1, 0, 0), time)
        assertEquals(ZoneOffset.ofHours(-4), first.offset) // EDT, the earlier 01:30

        // Firing at the first 01:30 must not schedule the second 01:30 (an hour later) the same night.
        val next = nextTrigger(first, time)
        assertEquals(java.time.LocalDate.of(2026, 11, 2), next.toLocalDate())
        assertTrue(Duration.between(first, next) > Duration.ofHours(24))
    }

    @Test
    fun alwaysStrictlyInTheFutureAndWithinADayAndAnHour() {
        val zones = listOf(newYork, london, kolkata, ZoneId.of("Australia/Lord_Howe")) // last has a 30-min DST shift
        for (zone in zones) {
            var now = at(zone, 2026, 1, 1, 0, 0)
            repeat(365 * 4) { // every 6 hours for a year
                for (time in listOf(LocalTime.MIDNIGHT, LocalTime.of(2, 30), eight, LocalTime.of(23, 59))) {
                    val t = nextTrigger(now, time)
                    assertTrue("$zone $now $time -> $t", t.isAfter(now))
                    assertTrue("$zone $now $time -> $t", Duration.between(now, t) <= Duration.ofHours(25))
                }
                now = now.plusHours(6)
            }
        }
    }
}
