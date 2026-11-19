package aarnav100.developer.biblescroll.data.devotionals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DevotionalCalendarTest {
    private fun d(s: String) = LocalDate.parse(s)
    private fun at(local: String, zone: String) = ZonedDateTime.of(LocalDateTime.parse(local), ZoneId.of(zone))

    @Test
    fun keysAreMonthAndDay() {
        assertEquals("11-18", DevotionalCalendar.key(d("2026-11-18")))
        assertEquals("02-29", DevotionalCalendar.key(d("2028-02-29")))
        assertEquals("03", DevotionalCalendar.monthKey(3))
    }

    @Test
    fun feb29IsOnlyInLeapYears() {
        assertEquals(28, DevotionalCalendar.pastDays(2026, 2, d("2026-11-18")).size)
        assertEquals(29, DevotionalCalendar.pastDays(2028, 2, d("2028-11-18")).size)
        assertTrue(DevotionalCalendar.pastDays(2026, 2, d("2026-11-18")).none { DevotionalCalendar.key(it) == "02-29" })
        // The day after Feb 28 in a common year is Mar 1, never the bonus.
        assertEquals("03-01", DevotionalCalendar.key(d("2026-02-28").plusDays(1)))
        assertEquals("02-29", DevotionalCalendar.key(d("2028-02-28").plusDays(1)))
    }

    @Test
    fun pastDaysStopAtTodayAndSkipFutureMonths() {
        assertEquals(18, DevotionalCalendar.pastDays(2026, 11, d("2026-11-18")).size)
        assertEquals(emptyList<LocalDate>(), DevotionalCalendar.pastDays(2026, 12, d("2026-11-18")))
        assertEquals((11 downTo 1).toList(), DevotionalCalendar.archiveMonths(d("2026-11-18")))
    }

    @Test
    fun countdownRunsToLocalMidnight() {
        assertEquals(Duration.ofSeconds(1), DevotionalCalendar.untilMidnight(at("2026-11-18T23:59:59", "Europe/London")))
        assertEquals(Duration.ofHours(24), DevotionalCalendar.untilMidnight(at("2026-11-18T00:00", "Europe/London")))
        assertEquals("05:12:09", DevotionalCalendar.format(Duration.ofSeconds(5 * 3600 + 12 * 60 + 9)))
    }

    @Test
    fun countdownFollowsDaylightSaving() {
        // 8 March 2026 in New York is 23 hours long.
        assertEquals(Duration.ofMinutes(22 * 60 + 30), DevotionalCalendar.untilMidnight(at("2026-03-08T00:30", "America/New_York")))
        // 1 November 2026 is 25 hours long.
        assertEquals(Duration.ofHours(25), DevotionalCalendar.untilMidnight(at("2026-11-01T00:00", "America/New_York")))
    }

    @Test
    fun countdownUsesTheZoneThePhoneIsIn() {
        val instant = at("2026-11-18T22:00", "Europe/London").toInstant()
        assertEquals(Duration.ofHours(2), DevotionalCalendar.untilMidnight(instant.atZone(ZoneId.of("Europe/London"))))
        assertEquals(Duration.ofHours(10), DevotionalCalendar.untilMidnight(instant.atZone(ZoneId.of("America/Los_Angeles")))) // 14:00 there
    }
}
