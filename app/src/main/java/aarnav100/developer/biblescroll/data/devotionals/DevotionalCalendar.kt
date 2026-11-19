package aarnav100.developer.biblescroll.data.devotionals

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Which devotional belongs to which day. Pure java.time, unit-tested. */
object DevotionalCalendar {
    /** "MM-DD": devotionals repeat every year on the same month and day. */
    fun key(date: LocalDate): String = "%02d-%02d".format(date.monthValue, date.dayOfMonth)

    /** "01".."12" */
    fun monthKey(month: Int): String = "%02d".format(month)

    /**
     * Days of [year]'s [month] up to and including [today] (all of it for past months, none for future ones).
     * Feb 29 appears only in leap years, because only real calendar dates are listed.
     */
    fun pastDays(year: Int, month: Int, today: LocalDate): List<LocalDate> {
        val first = LocalDate.of(year, month, 1)
        if (first.isAfter(today)) return emptyList()
        val last = minOf(first.withDayOfMonth(first.lengthOfMonth()), today)
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }

    /** Months of [today]'s year up to its month, newest first (H17). */
    fun archiveMonths(today: LocalDate): List<Int> = (today.monthValue downTo 1).toList()

    /**
     * Time left until the next local midnight in [now]'s zone. Uses the start of tomorrow, so a DST change
     * that night (23 or 25 hour day, or a midnight that doesn't exist) is handled by java.time.
     */
    fun untilMidnight(now: ZonedDateTime): Duration =
        Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone))

    /** "05:12:09" */
    fun format(duration: Duration): String {
        val seconds = duration.seconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    }
}
