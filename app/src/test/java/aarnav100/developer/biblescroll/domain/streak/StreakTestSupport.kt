package aarnav100.developer.biblescroll.domain.streak

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

internal fun d(iso: String): LocalDate = LocalDate.parse(iso)

/** A day that counted at [hour]:00 local time in a zone with [offsetMinutes]. */
internal fun counted(iso: String, hour: Int = 9, offsetMinutes: Int = 0): StreakDay {
    val at = d(iso).atTime(hour, 0).toInstant(ZoneOffset.ofTotalSeconds(offsetMinutes * 60)).toEpochMilli()
    return StreakDay(d(iso), versesViewed = 5, countedAt = at, utcOffsetMinutes = offsetMinutes, updatedAt = at)
}

internal fun days(vararg iso: String) = iso.map { counted(it) }

internal fun zoned(local: String, zone: String): ZonedDateTime = ZonedDateTime.of(LocalDateTime.parse(local), ZoneId.of(zone))

/** Folds events through [StreakRules] exactly as the repository does, keyed by local date. */
internal class Reader {
    val days = linkedMapOf<LocalDate, StreakDay>()

    fun at(epochMillis: Long, zone: ZoneId, activity: StreakActivity = StreakActivity.VerseViewed) {
        val date = StreakRules.localDate(epochMillis, zone)
        days[date] = StreakRules.record(days[date], activity, epochMillis, zone)
    }

    /** [count] verses, one every 2 s, starting at [start]. */
    fun verses(start: ZonedDateTime, count: Int = StreakRules.VERSES_TO_COUNT) {
        repeat(count) { at(start.toInstant().toEpochMilli() + it * StreakRules.VERSE_DWELL_MILLIS, start.zone) }
    }

    fun read(local: String, zone: String) = verses(zoned(local, zone))

    fun calculator(grace: Boolean = false) = StreakCalculator(days.values.toList(), grace)
}
