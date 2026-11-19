package aarnav100.developer.biblescroll.data.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class PlanScheduleTest {
    private val ny = ZoneId.of("America/New_York")
    private fun at(local: String) = ZonedDateTime.of(LocalDateTime.parse(local), ny)
    private val eight = LocalTime.of(8, 0)
    private val d18 = LocalDate.parse("2026-11-18")

    @Test
    fun startOptionsAreTodayAndTomorrow() {
        assertEquals(listOf(d18, d18.plusDays(1)), PlanSchedule.startOptions(d18))
        assertEquals(listOf(LocalDate.parse("2026-12-31"), LocalDate.parse("2027-01-01")), PlanSchedule.startOptions(LocalDate.parse("2026-12-31")))
    }

    @Test
    fun reminderForAPlanStartingTomorrowWaitsForTomorrow() {
        assertEquals(at("2026-11-19T08:00"), PlanSchedule.nextReminder(at("2026-11-18T07:00"), eight, d18.plusDays(1)))
    }

    @Test
    fun reminderForAPlanStartingTodayIsLaterTodayOrTomorrow() {
        assertEquals(at("2026-11-18T08:00"), PlanSchedule.nextReminder(at("2026-11-18T07:00"), eight, d18))
        assertEquals(at("2026-11-19T08:00"), PlanSchedule.nextReminder(at("2026-11-18T09:00"), eight, d18))
        assertEquals(at("2026-11-19T08:00"), PlanSchedule.nextReminder(at("2026-11-18T08:00"), eight, d18)) // strictly after
    }

    @Test
    fun reminderTimeInTheSpringForwardGapMovesLater() {
        // 02:30 doesn't exist in New York on 8 March 2026.
        val t = PlanSchedule.nextReminder(at("2026-03-07T12:00"), LocalTime.of(2, 30), LocalDate.parse("2026-03-08"))
        assertEquals(LocalTime.of(3, 30), t.toLocalTime())
    }

    @Test
    fun reminderDayFollowsProgressAndTheCalendar() {
        assertNull(PlanSchedule.reminderDay(5, emptySet(), d18.plusDays(1), d18))          // not started
        assertEquals(1, PlanSchedule.reminderDay(5, emptySet(), d18, d18))                 // day 1
        assertNull(PlanSchedule.reminderDay(5, setOf(1), d18, d18))                        // already read today's
        assertEquals(2, PlanSchedule.reminderDay(5, setOf(1), d18, d18.plusDays(1)))       // day 2 tomorrow
        assertEquals(2, PlanSchedule.reminderDay(5, setOf(1), d18, d18.plusDays(3)))       // behind: still the next unread
        assertNull(PlanSchedule.reminderDay(3, setOf(1, 2, 3), d18, d18.plusDays(2)))      // finished
    }

    @Test
    fun minuteOfDayRoundTrips() {
        assertEquals(LocalTime.of(21, 45), PlanSchedule.timeOf(PlanSchedule.minuteOfDay(LocalTime.of(21, 45))))
        assertEquals(2, PlanSchedule.nextDay(3, setOf(1, 3)))
    }
}
