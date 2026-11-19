package aarnav100.developer.biblescroll.domain.streak

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** How a calendar day looks in the streak sheet. */
enum class DayStatus {
    /** The day counted. */
    Done,

    /** Missed, but covered by the weekly grace day (setting). Keeps the streak; doesn't add to it. */
    Grace,

    /** A calendar date skipped by flying east across many time zones. Keeps the streak; doesn't add. */
    Travel,

    /** Today, not counted yet. Never breaks a streak: the day isn't over. */
    Open,

    /** A past day that didn't count. Shown as a plain empty dot (no warning colours, no copy). */
    Missed,

    /** After today. */
    Future,

    /** Before the first day that ever counted. */
    NotStarted,
}

data class StreakSummary(
    /** Counted days in the unbroken run that reaches today (0 if it doesn't). */
    val current: Int,
    val longest: Int,
    val todayCounts: Boolean,
    /** Monday to Sunday of today's week. */
    val week: List<Pair<LocalDate, DayStatus>>,
)

/**
 * Current/longest streak, week view and month grid from the days that counted. Pure Kotlin.
 *
 * Rules:
 * - A run is broken only by a Missed day. Grace, Travel and Open days bridge a run without adding to it.
 * - Grace (when enabled): a single missed day whose day before counted (or was Travel) and whose day after
 *   counted or is today. At most one per Monday-Sunday week; with two such days, the earlier one gets it.
 * - Travel: a single missing date between two counted days where the UTC offset moved east by at least
 *   [TRAVEL_MIN_OFFSET_MINUTES] and the two counting moments are under 48 real hours apart, i.e. the date
 *   was mostly or entirely spent in the air. It never uses up the grace day.
 * - Clock set backwards: days after [today] are ignored for the current streak and shown as Future, but
 *   still count toward [StreakSummary.longest], so an achievement never disappears.
 */
class StreakCalculator(private val days: List<StreakDay>, private val graceEnabled: Boolean) {

    private val counted: Map<LocalDate, StreakDay> = days.filter { it.counts }.associateBy { it.date }
    private val first: LocalDate? = counted.keys.minOrNull()

    fun summary(today: LocalDate): StreakSummary {
        val statuses = statuses(today)
        val current = runs(statuses, today).let { (_, endRun) -> endRun }
        val lastCounted = counted.keys.maxOrNull()
        val longestEnd = if (lastCounted != null && lastCounted > today) lastCounted else today
        val longest = maxOf(runs(statuses(longestEnd), longestEnd).first, current)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (0L..6L).map { monday.plusDays(it) }.map { it to statusOf(it, statuses, today) }
        return StreakSummary(current, longest, counted.containsKey(today), week)
    }

    /** Every day of [month], for the calendar grid. */
    fun month(month: YearMonth, today: LocalDate): List<Pair<LocalDate, DayStatus>> {
        val statuses = statuses(today)
        return (1..month.lengthOfMonth()).map { month.atDay(it) }.map { it to statusOf(it, statuses, today) }
    }

    private fun statusOf(date: LocalDate, statuses: Map<LocalDate, DayStatus>, today: LocalDate): DayStatus = when {
        date > today -> DayStatus.Future
        first == null || date < first -> if (date == today) DayStatus.Open else DayStatus.NotStarted
        else -> statuses.getValue(date)
    }

    /** Status of every day from the first counted day to [until]. */
    private fun statuses(until: LocalDate): Map<LocalDate, DayStatus> {
        val start = first ?: return emptyMap()
        if (start > until) return emptyMap()
        val out = LinkedHashMap<LocalDate, DayStatus>()
        val graceWeeks = HashSet<LocalDate>()
        var date: LocalDate = start
        while (date <= until) {
            val previous = out[date.minusDays(1)]
            out[date] = when {
                counted.containsKey(date) -> DayStatus.Done
                date == until -> DayStatus.Open
                isTravel(date) -> DayStatus.Travel
                graceEnabled && (previous == DayStatus.Done || previous == DayStatus.Travel) &&
                    (counted.containsKey(date.plusDays(1)) || date.plusDays(1) == until) &&
                    graceWeeks.add(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))) -> DayStatus.Grace
                else -> DayStatus.Missed
            }
            date = date.plusDays(1)
        }
        return out
    }

    private fun isTravel(date: LocalDate): Boolean {
        val before = counted[date.minusDays(1)] ?: return false
        val after = counted[date.plusDays(1)] ?: return false
        val offsetBefore = before.utcOffsetMinutes ?: return false
        val offsetAfter = after.utcOffsetMinutes ?: return false
        val t1 = before.countedAt ?: return false
        val t2 = after.countedAt ?: return false
        val elapsed = t2 - t1
        return offsetAfter - offsetBefore >= TRAVEL_MIN_OFFSET_MINUTES && elapsed in 1 until TRAVEL_MAX_ELAPSED_MILLIS
    }

    /** (longest run, run reaching [end]) over [statuses]. */
    private fun runs(statuses: Map<LocalDate, DayStatus>, end: LocalDate): Pair<Int, Int> {
        var run = 0
        var best = 0
        var lastDate: LocalDate? = null
        for ((date, status) in statuses) {
            check(lastDate == null || ChronoUnit.DAYS.between(lastDate, date) == 1L)
            lastDate = date
            when (status) {
                DayStatus.Done -> run++
                DayStatus.Missed -> run = 0
                else -> Unit
            }
            best = maxOf(best, run)
        }
        val reachesEnd = lastDate == end
        return best to if (reachesEnd) run else 0
    }

    companion object {
        const val TRAVEL_MIN_OFFSET_MINUTES = 10 * 60
        const val TRAVEL_MAX_ELAPSED_MILLIS = 48L * 60 * 60 * 1000
    }
}
