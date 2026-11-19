package aarnav100.developer.biblescroll.domain.streak

import aarnav100.developer.biblescroll.domain.streak.DayStatus.Done
import aarnav100.developer.biblescroll.domain.streak.DayStatus.Future
import aarnav100.developer.biblescroll.domain.streak.DayStatus.Grace
import aarnav100.developer.biblescroll.domain.streak.DayStatus.Missed
import aarnav100.developer.biblescroll.domain.streak.DayStatus.NotStarted
import aarnav100.developer.biblescroll.domain.streak.DayStatus.Open
import aarnav100.developer.biblescroll.domain.streak.DayStatus.Travel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.YearMonth

/** 16 Nov 2026 is a Monday; 22 Nov a Sunday; 23 Nov the next Monday. */
class StreakCalculatorTest {

    @Before
    fun calendarIsWhatTheTestsAssume() {
        assertEquals(DayOfWeek.MONDAY, d("2026-11-16").dayOfWeek)
    }

    private fun calc(days: List<StreakDay>, grace: Boolean = false) = StreakCalculator(days, grace)

    private fun StreakCalculator.statusOn(iso: String, today: String) =
        month(YearMonth.from(d(iso)), d(today)).first { it.first == d(iso) }.second

    // ---- Basics -----------------------------------------------------------------------------------

    @Test
    fun nothingYet() {
        val s = calc(emptyList()).summary(d("2026-11-18"))
        assertEquals(0, s.current)
        assertEquals(0, s.longest)
        assertFalse(s.todayCounts)
        assertEquals(
            listOf(NotStarted, NotStarted, Open, Future, Future, Future, Future),
            s.week.map { it.second },
        )
    }

    @Test
    fun consecutiveDaysThroughToday() {
        val s = calc(days("2026-11-16", "2026-11-17", "2026-11-18")).summary(d("2026-11-18"))
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
        assertTrue(s.todayCounts)
    }

    @Test
    fun todayNotReadYetDoesNotBreakTheStreak() {
        val s = calc(days("2026-11-16", "2026-11-17")).summary(d("2026-11-18"))
        assertEquals(2, s.current)
        assertFalse(s.todayCounts)
        assertEquals(Open, s.week[2].second)
    }

    @Test
    fun aMissedPastDayEndsTheRunButLongestIsKept() {
        val s = calc(days("2026-11-14", "2026-11-15", "2026-11-16")).summary(d("2026-11-18"))
        assertEquals(0, s.current)
        assertEquals(3, s.longest)
        assertEquals(listOf(Done, Missed, Open), s.week.take(3).map { it.second })
    }

    @Test
    fun longestIsTheBestRunAcrossHistory() {
        val history = days("2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-10", "2026-11-17", "2026-11-18")
        val s = calc(history).summary(d("2026-11-18"))
        assertEquals(2, s.current)
        assertEquals(4, s.longest)
    }

    @Test
    fun weekRunsMondayToSunday() {
        val s = calc(days("2026-11-16")).summary(d("2026-11-22")) // Sunday
        assertEquals(d("2026-11-16"), s.week.first().first)
        assertEquals(d("2026-11-22"), s.week.last().first)
        val next = calc(days("2026-11-16")).summary(d("2026-11-23")) // Monday: a new week
        assertEquals(d("2026-11-23"), next.week.first().first)
    }

    @Test
    fun monthGridHasEveryDayIncludingLeapDay() {
        val c = calc(days("2028-02-28", "2028-02-29"))
        val feb = c.month(YearMonth.of(2028, 2), d("2028-03-01"))
        assertEquals(29, feb.size)
        assertEquals(Done, feb.last().second)
        assertEquals(NotStarted, feb.first().second)
        assertEquals(2, c.summary(d("2028-03-01")).current) // 1 March open
    }

    @Test
    fun unqualifiedRowsDontCount() {
        val partial = StreakDay(d("2026-11-17"), versesViewed = 4)
        val s = calc(listOf(counted("2026-11-16"), partial)).summary(d("2026-11-18"))
        assertEquals(0, s.current)
        assertEquals(Missed, s.week[1].second)
    }

    // ---- Grace day --------------------------------------------------------------------------------

    @Test
    fun graceCoversASingleMissedDayBetweenReadingDays() {
        val c = calc(days("2026-11-16", "2026-11-17", "2026-11-19"), grace = true)
        val s = c.summary(d("2026-11-19"))
        assertEquals(3, s.current) // the grace day keeps the run but doesn't add to it
        assertEquals(Grace, s.week[2].second)
    }

    @Test
    fun graceIsOffByDefault() {
        val s = calc(days("2026-11-16", "2026-11-17", "2026-11-19")).summary(d("2026-11-19"))
        assertEquals(1, s.current)
        assertEquals(Missed, s.week[2].second)
    }

    @Test
    fun yesterdayIsCoveredWhileTodayIsStillOpen() {
        val s = calc(days("2026-11-16", "2026-11-17"), grace = true).summary(d("2026-11-19"))
        assertEquals(2, s.current)
        assertEquals(listOf(Done, Done, Grace, Open), s.week.take(4).map { it.second })
    }

    @Test
    fun ifTodayAlsoPassesUnreadTheGraceNoLongerBridgesAnything() {
        val s = calc(days("2026-11-16", "2026-11-17"), grace = true).summary(d("2026-11-20"))
        assertEquals(0, s.current)
        assertEquals(listOf(Done, Done, Missed, Missed, Open), s.week.take(5).map { it.second })
    }

    @Test
    fun onlyOneGraceDayPerWeekAndTheEarlierGetsIt() {
        val c = calc(days("2026-11-16", "2026-11-18", "2026-11-20"), grace = true)
        val s = c.summary(d("2026-11-20"))
        assertEquals(listOf(Done, Grace, Done, Missed, Done), s.week.take(5).map { it.second })
        assertEquals(1, s.current)
        assertEquals(2, s.longest)
    }

    @Test
    fun aNewWeekGetsANewGraceDay() {
        val read = days("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20", "2026-11-21", "2026-11-23", "2026-11-25")
        val c = calc(read, grace = true)
        val s = c.summary(d("2026-11-25"))
        assertEquals(Grace, c.statusOn("2026-11-22", "2026-11-25")) // Sunday, first week
        assertEquals(Grace, c.statusOn("2026-11-24", "2026-11-25")) // Tuesday, second week
        assertEquals(8, s.current)
    }

    @Test
    fun twoMissedDaysInARowAreNeverCoveredEvenAcrossAWeekBoundary() {
        val c = calc(days("2026-11-20", "2026-11-21", "2026-11-24"), grace = true)
        val s = c.summary(d("2026-11-24"))
        assertEquals(Missed, c.statusOn("2026-11-22", "2026-11-24"))
        assertEquals(Missed, c.statusOn("2026-11-23", "2026-11-24"))
        assertEquals(1, s.current)
    }

    @Test
    fun graceNeverAppliesBeforeTheFirstReadingDay() {
        val c = calc(days("2026-11-18"), grace = true)
        assertEquals(NotStarted, c.statusOn("2026-11-17", "2026-11-18"))
        assertEquals(1, c.summary(d("2026-11-18")).current)
    }

    @Test
    fun turningGraceOnAppliesToPastWeeksToo() {
        val read = days("2026-11-02", "2026-11-03", "2026-11-05", "2026-11-06")
        assertEquals(2, calc(read).summary(d("2026-11-06")).longest)
        assertEquals(4, calc(read, grace = true).summary(d("2026-11-06")).longest)
    }

    // ---- Time zones -------------------------------------------------------------------------------

    @Test
    fun flyingWestRepeatsADateWhichCountsOnce() {
        val r = Reader()
        r.read("2026-11-16T08:00", "Asia/Tokyo")
        r.read("2026-11-16T19:00", "America/Los_Angeles") // landed: it's the 16th again
        r.read("2026-11-17T09:00", "America/Los_Angeles")
        val s = r.calculator().summary(d("2026-11-17"))
        assertEquals(2, r.days.size)
        assertEquals(2, s.current)
    }

    @Test
    fun flyingEastOverTheDateLineSkipsADateWithoutBreakingTheStreak() {
        val r = Reader()
        r.read("2026-11-15T09:00", "America/Los_Angeles")
        r.read("2026-11-16T10:00", "America/Los_Angeles")
        // 11 h flight; it's the 18th in Tokyo on landing. The 17th was spent in the air.
        r.read("2026-11-18T07:00", "Asia/Tokyo")
        val c = r.calculator()
        val s = c.summary(d("2026-11-18"))
        assertEquals(Travel, s.week[1].second)
        assertEquals(3, s.current)
    }

    @Test
    fun travelDayDoesNotUseUpTheGraceDay() {
        val r = Reader()
        r.read("2026-11-16T10:00", "America/Los_Angeles")
        r.read("2026-11-18T07:00", "Asia/Tokyo")
        r.read("2026-11-19T08:00", "Asia/Tokyo")
        r.read("2026-11-21T08:00", "Asia/Tokyo") // missed the 20th
        val s = r.calculator(grace = true).summary(d("2026-11-21"))
        assertEquals(listOf(Done, Travel, Done, Done, Grace, Done), s.week.take(6).map { it.second })
        assertEquals(4, s.current)
    }

    @Test
    fun aGenuinelyMissedDayAfterEastwardTravelStillCountsAsMissed() {
        val r = Reader()
        r.read("2026-11-16T06:00", "America/Los_Angeles") // 14:00 UTC on the 16th
        r.read("2026-11-18T23:59", "Asia/Tokyo")          // 14:59 UTC on the 18th: 49 real hours later
        val s = r.calculator().summary(d("2026-11-18"))
        assertEquals(Missed, s.week[1].second)
        assertEquals(1, s.current)
    }

    @Test
    fun aDaylightSavingShiftIsNeverTreatedAsTravel() {
        // London clocks go forward on 29 March 2026; the 28th is skipped by the reader, not by the clock.
        val r = Reader()
        r.read("2026-03-27T23:00", "Europe/London")
        r.read("2026-03-29T23:00", "Europe/London") // now BST: offset +60, 47 real hours later
        val s = r.calculator().summary(d("2026-03-29"))
        assertEquals(1, s.current)
    }

    @Test
    fun aStreakRunsThroughBothDaylightSavingChanges() {
        val r = Reader()
        for (day in 6..10) r.read("2026-03-%02dT23:30".format(day), "America/New_York") // spring forward on the 8th
        for (day in 30..31) r.read("2026-10-%02dT00:15".format(day), "America/New_York")
        r.read("2026-11-01T01:30", "America/New_York") // the repeated hour
        r.read("2026-11-02T00:01", "America/New_York")
        assertEquals(5, r.calculator().summary(d("2026-03-10")).current)
        assertEquals(4, r.calculator().summary(d("2026-11-02")).current)
    }

    @Test
    fun movingZonesEveryDayKeepsTheStreak() {
        val r = Reader()
        val zones = listOf("Europe/London", "Europe/Berlin", "Asia/Dubai", "Asia/Kolkata", "Asia/Singapore")
        zones.forEachIndexed { i, zone -> r.read("2026-11-%02dT21:00".format(14 + i), zone) }
        assertEquals(5, r.calculator().summary(d("2026-11-18")).current)
    }

    @Test
    fun readingJustAfterMidnightInANewZoneCountsForThatZonesDate() {
        val r = Reader()
        r.read("2026-11-17T20:00", "America/New_York")
        r.read("2026-11-19T00:10", "Europe/Paris") // overnight flight; 18:10 on the 18th in New York
        val s = r.calculator().summary(d("2026-11-19"))
        assertEquals(Missed, s.week[2].second) // the 18th really had no reading
        assertEquals(1, s.current)
    }

    // ---- Device clock set backwards ---------------------------------------------------------------

    @Test
    fun clockSetBackwardsShowsLaterDaysAsFutureAndKeepsLongest() {
        val c = calc(days("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-10"))
        val s = c.summary(d("2026-11-10"))
        assertEquals(1, s.current)
        assertEquals(3, s.longest)
        assertEquals(Future, c.statusOn("2026-11-17", "2026-11-10"))
    }

    @Test
    fun clockBeforeTheFirstReadingDayDoesNotCrash() {
        val c = calc(days("2026-11-16", "2026-11-17"))
        val s = c.summary(d("2026-11-05"))
        assertEquals(0, s.current)
        assertEquals(2, s.longest)
        assertEquals(listOf(NotStarted, NotStarted, NotStarted, Open, Future, Future, Future), s.week.map { it.second })
    }

    @Test
    fun afterTheClockIsFixedEverythingIsCountedNormally() {
        val r = Reader()
        r.read("2026-11-16T08:00", "UTC")
        r.read("2026-11-17T08:00", "UTC")
        r.read("2026-11-10T08:00", "UTC") // clock wrongly set back a week
        r.read("2026-11-18T08:00", "UTC") // fixed
        val s = r.calculator().summary(d("2026-11-18"))
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
    }

    @Test
    fun travelIsNeverInferredFromABackwardsClock() {
        val before = counted("2026-11-16", hour = 10, offsetMinutes = -480)
        val after = counted("2026-11-18", hour = 7, offsetMinutes = 540).let { it.copy(countedAt = before.countedAt!! - 1_000) }
        val s = calc(listOf(before, after)).summary(d("2026-11-18"))
        assertEquals(Missed, s.week[1].second)
    }
}
