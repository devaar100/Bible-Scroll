package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.data.reminder.nextTrigger
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Plan dates and reminder times. Pure functions (java.time only), unit-tested. */
object PlanSchedule {

    /** H10 offers today or tomorrow. */
    fun startOptions(today: LocalDate): List<LocalDate> = listOf(today, today.plusDays(1))

    /**
     * The next plan reminder strictly after [now]: never before the plan's [startDate], otherwise daily at
     * [time] like the main reminder (same DST rules, see [nextTrigger]).
     */
    fun nextReminder(now: ZonedDateTime, time: LocalTime, startDate: LocalDate): ZonedDateTime {
        val first = ZonedDateTime.of(startDate, time, now.zone)
        return if (first.isAfter(now)) first else nextTrigger(now, time)
    }

    /** The day to read next: the first one not done, or null when the plan is finished. */
    fun nextDay(totalDays: Int, done: Set<Int>): Int? = (1..totalDays).firstOrNull { it !in done }

    /**
     * The day a reminder on [today] should mention, or null for no reminder: before the start date, when
     * the plan is finished, or when the reader has already read up to (or past) today's day.
     */
    fun reminderDay(totalDays: Int, done: Set<Int>, startDate: LocalDate, today: LocalDate): Int? {
        if (today.isBefore(startDate)) return null
        val next = nextDay(totalDays, done) ?: return null
        val calendarDay = ChronoUnit.DAYS.between(startDate, today) + 1
        return next.takeIf { it <= calendarDay }
    }

    fun minuteOfDay(time: LocalTime) = time.hour * 60 + time.minute

    fun timeOf(minuteOfDay: Int): LocalTime = LocalTime.of(minuteOfDay / 60 % 24, minuteOfDay % 60)
}
