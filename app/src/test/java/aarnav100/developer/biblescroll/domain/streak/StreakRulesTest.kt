package aarnav100.developer.biblescroll.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class StreakRulesTest {

    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun fiveVersesCountTheDayAndFourDoNot() {
        val r = Reader()
        r.verses(zoned("2026-11-18T08:00", "Europe/London"), count = 4)
        assertNull(r.days.getValue(d("2026-11-18")).countedAt)
        r.verses(zoned("2026-11-18T08:10", "Europe/London"), count = 1)
        assertTrue(r.days.getValue(d("2026-11-18")).counts)
    }

    @Test
    fun aPlanDayOrADevotionalCountsOnItsOwn() {
        val r = Reader()
        r.at(zoned("2026-11-18T08:00", "UTC").toInstant().toEpochMilli(), ZoneId.of("UTC"), StreakActivity.PlanDayCompleted)
        r.at(zoned("2026-11-19T08:00", "UTC").toInstant().toEpochMilli(), ZoneId.of("UTC"), StreakActivity.DevotionalFinished)
        assertTrue(r.days.getValue(d("2026-11-18")).counts)
        assertTrue(r.days.getValue(d("2026-11-19")).counts)
    }

    @Test
    fun theFirstCountingMomentAndItsOffsetAreKept() {
        val r = Reader()
        r.verses(zoned("2026-11-18T08:00", "America/New_York"))
        val first = r.days.getValue(d("2026-11-18"))
        r.verses(zoned("2026-11-18T20:00", "America/New_York"), count = 3)
        val later = r.days.getValue(d("2026-11-18"))
        assertEquals(first.countedAt, later.countedAt)
        assertEquals(-300, later.utcOffsetMinutes)
        assertEquals(8, later.versesViewed)
    }

    @Test
    fun versesEitherSideOfMidnightBelongToTheirOwnDays() {
        val r = Reader()
        // 23:59:52, :54, :56, :58 -> 18th; 00:00:00, :02 -> 19th.
        r.verses(zoned("2026-11-18T23:59:52", "America/New_York"), count = 6)
        assertEquals(4, r.days.getValue(d("2026-11-18")).versesViewed)
        assertEquals(2, r.days.getValue(d("2026-11-19")).versesViewed)
        assertFalse(r.days.getValue(d("2026-11-18")).counts)
        r.verses(zoned("2026-11-19T00:05", "America/New_York"), count = 3)
        assertTrue(r.days.getValue(d("2026-11-19")).counts)
    }

    @Test
    fun aVerseAtExactlyMidnightBelongsToTheNewDay() {
        val r = Reader()
        r.verses(zoned("2026-11-18T23:59:50", "UTC"), count = 5) // last at 23:59:58
        r.at(zoned("2026-11-19T00:00:00", "UTC").toInstant().toEpochMilli(), ZoneId.of("UTC"))
        assertTrue(r.days.getValue(d("2026-11-18")).counts)
        assertEquals(1, r.days.getValue(d("2026-11-19")).versesViewed)
    }

    @Test
    fun localDateFollowsTheZoneAtTheMomentOfReading() {
        val instant = zoned("2026-11-18T23:30", "America/Los_Angeles").toInstant().toEpochMilli()
        assertEquals(d("2026-11-18"), StreakRules.localDate(instant, ZoneId.of("America/Los_Angeles")))
        assertEquals(d("2026-11-19"), StreakRules.localDate(instant, newYork))
        assertEquals(d("2026-11-19"), StreakRules.localDate(instant, ZoneId.of("Pacific/Kiritimati"))) // UTC+14: 21:30
        assertEquals(d("2026-11-18"), StreakRules.localDate(instant, ZoneId.of("Pacific/Pago_Pago"))) // UTC-11: 20:30
    }

    @Test
    fun springForwardNightGivesNormalDates() {
        // New York skips 02:00-03:00 on 8 March 2026.
        val beforeGap = zoned("2026-03-08T01:59", "America/New_York").toInstant().toEpochMilli()
        val afterGap = beforeGap + 60_000 // one real minute later is 03:00 local
        assertEquals(d("2026-03-08"), StreakRules.localDate(afterGap, newYork))
        assertEquals(-240, StreakRules.utcOffsetMinutes(afterGap, newYork))
        assertEquals(-300, StreakRules.utcOffsetMinutes(beforeGap, newYork))
    }

    @Test
    fun fallBackRepeatedHourStaysOnOneDay() {
        // 01:00-02:00 happens twice on 1 November 2026 in New York.
        val firstPass = zoned("2026-11-01T01:30", "America/New_York").withEarlierOffsetAtOverlap()
        val secondPass = firstPass.withLaterOffsetAtOverlap()
        val r = Reader()
        r.verses(firstPass, count = 3)
        r.verses(secondPass, count = 2)
        assertEquals(1, r.days.size)
        assertTrue(r.days.getValue(d("2026-11-01")).counts)
    }

    @Test(expected = IllegalArgumentException::class)
    fun recordingIntoTheWrongDayFailsLoudly() {
        val day = StreakDay(d("2026-11-17"))
        StreakRules.record(day, StreakActivity.VerseViewed, zoned("2026-11-18T08:00", "UTC").toInstant().toEpochMilli(), ZoneId.of("UTC"))
    }
}
