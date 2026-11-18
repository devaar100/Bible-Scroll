package aarnav100.developer.biblescroll.data.reminder

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The next time a daily reminder at [time] should fire, strictly after [now], in [now]'s zone.
 *
 * DST: a [time] that doesn't exist that day (spring-forward gap, e.g. 02:30) moves later by the gap
 * length (03:30); a [time] that happens twice (fall-back overlap) uses the first occurrence. Both
 * are java.time's ZonedDateTime rules, so the reminder fires once a day either way.
 */
fun nextTrigger(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
    val today = ZonedDateTime.of(now.toLocalDate(), time, now.zone)
    if (today.isAfter(now)) return today
    return ZonedDateTime.of(now.toLocalDate().plusDays(1), time, now.zone)
}
