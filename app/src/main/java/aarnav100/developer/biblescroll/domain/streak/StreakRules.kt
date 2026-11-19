package aarnav100.developer.biblescroll.domain.streak

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * H2 streak rules. Pure Kotlin + java.time: no Android types, so every rule is unit-testable.
 *
 * A day counts when, on that day, the reader did any of:
 *  - viewed VERSES_TO_COUNT different verses in the feed, each on screen for VERSE_DWELL_MILLIS;
 *  - completed a plan day;
 *  - finished a devotional.
 * "That day" is the device's local date at the moment of each event (zone read at that moment), so a
 * verse seen at 00:00:01 belongs to the new day, and a later time-zone change never moves past days.
 */
object StreakRules {
    const val VERSES_TO_COUNT = 5
    const val VERSE_DWELL_MILLIS = 2_000L

    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun utcOffsetMinutes(epochMillis: Long, zone: ZoneId): Int =
        zone.rules.getOffset(Instant.ofEpochMilli(epochMillis)).totalSeconds / 60

    /**
     * [day] with [activity] added. [day] must be null or the record for the event's local date.
     * The first moment the day counts is kept (with its UTC offset); later events never move it.
     */
    fun record(day: StreakDay?, activity: StreakActivity, epochMillis: Long, zone: ZoneId): StreakDay {
        val date = localDate(epochMillis, zone)
        require(day == null || day.date == date) { "Event on $date recorded into ${day?.date}" }
        val base = day ?: StreakDay(date)
        val next = when (activity) {
            StreakActivity.VerseViewed -> base.copy(versesViewed = base.versesViewed + 1)
            StreakActivity.PlanDayCompleted -> base.copy(planDaysCompleted = base.planDaysCompleted + 1)
            StreakActivity.DevotionalFinished -> base.copy(devotionalsFinished = base.devotionalsFinished + 1)
        }
        val counted = base.countedAt ?: if (next.meetsGoal) epochMillis else null
        val offset = if (base.countedAt == null && counted != null) utcOffsetMinutes(epochMillis, zone) else base.utcOffsetMinutes
        return next.copy(countedAt = counted, utcOffsetMinutes = offset, updatedAt = epochMillis)
    }
}

enum class StreakActivity { VerseViewed, PlanDayCompleted, DevotionalFinished }

/** One local calendar day of activity. [countedAt] is set (epoch millis) once the day counts. */
data class StreakDay(
    val date: LocalDate,
    val versesViewed: Int = 0,
    val planDaysCompleted: Int = 0,
    val devotionalsFinished: Int = 0,
    val countedAt: Long? = null,
    /** UTC offset at [countedAt], for spotting a calendar date skipped by eastward travel. */
    val utcOffsetMinutes: Int? = null,
    val updatedAt: Long = 0,
) {
    val meetsGoal: Boolean
        get() = versesViewed >= StreakRules.VERSES_TO_COUNT || planDaysCompleted > 0 || devotionalsFinished > 0

    val counts: Boolean get() = countedAt != null || meetsGoal
}
