package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlanProgressMathTest {
    private val d18 = LocalDate.parse("2026-11-18")
    private fun p(c1: Int, v1: Int, c2: Int, v2: Int, book: String = "JHN") = PassageFile(book, c1, v1, c2, v2)
    private val john: (Int, Int) -> Int? = { book, chapter -> if (book == 43) mapOf(1 to 51, 2 to 25, 3 to 36)[chapter] else null }

    @Test
    fun catchingUpIsZeroWhenOnTrackAheadOrNotStarted() {
        assertEquals(0, PlanProgressMath.daysToCatchUp(10, setOf(1, 2, 3), d18.minusDays(2), d18)) // day 3, read 3
        assertEquals(0, PlanProgressMath.daysToCatchUp(10, setOf(1, 2, 3, 4, 5), d18, d18))       // ahead
        assertEquals(0, PlanProgressMath.daysToCatchUp(10, emptySet(), d18.plusDays(1), d18))      // starts tomorrow
        assertEquals(0, PlanProgressMath.daysToCatchUp(3, setOf(1, 2, 3), d18.minusDays(9), d18))  // finished
    }

    @Test
    fun catchingUpCountsDaysTheCalendarIsAheadCappedAtThePlanLength() {
        assertEquals(2, PlanProgressMath.daysToCatchUp(10, setOf(1), d18.minusDays(3), d18))   // calendar day 4, next 2
        assertEquals(4, PlanProgressMath.daysToCatchUp(5, emptySet(), d18.minusDays(30), d18)) // capped at day 5
    }

    @Test
    fun adjustedStartPutsTheNextUnreadDayOnTodayAndNeverMovesBack() {
        assertEquals(d18.minusDays(1), PlanProgressMath.adjustedStart(10, setOf(1), d18.minusDays(3), d18))
        assertEquals(d18, PlanProgressMath.adjustedStart(10, emptySet(), d18.minusDays(3), d18))
        // Ahead of schedule: keep the original start.
        assertEquals(d18.minusDays(1), PlanProgressMath.adjustedStart(10, setOf(1, 2, 3, 4), d18.minusDays(1), d18))
        // Finished: unchanged.
        assertEquals(d18.minusDays(9), PlanProgressMath.adjustedStart(2, setOf(1, 2), d18.minusDays(9), d18))
    }

    @Test
    fun aWholeChapterInOnePassageIsRead() {
        val day = listOf(p(2, 1, 2, 25))
        assertEquals(mapOf(43 to setOf(2)), PlanProgressMath.fullyReadChapters(day, day, john))
    }

    @Test
    fun aChapterSplitAcrossDaysIsReadOnlyWhenBothHalvesAre() {
        val first = listOf(p(3, 1, 3, 18))
        val second = listOf(p(3, 19, 3, 36))
        assertEquals(emptyMap<Int, Set<Int>>(), PlanProgressMath.fullyReadChapters(first, first, john))
        assertEquals(mapOf(43 to setOf(3)), PlanProgressMath.fullyReadChapters(first + second, second, john))
    }

    @Test
    fun aPassageAcrossChaptersMarksOnlyTheCompleteOnes() {
        val day = listOf(p(1, 40, 3, 10)) // end of 1, all of 2, start of 3
        assertEquals(mapOf(43 to setOf(2)), PlanProgressMath.fullyReadChapters(day, day, john))
    }

    @Test
    fun onlyChaptersTouchedTodayAreReturnedAndUnknownLengthsAreSkipped() {
        val earlier = listOf(p(1, 1, 1, 51))
        val today = listOf(p(2, 1, 2, 25), PassageFile("XYZ", 1, 1, 1, 5))
        assertEquals(mapOf(43 to setOf(2)), PlanProgressMath.fullyReadChapters(earlier + today, today, john))
    }

    @Test
    fun containsChecksBookAndRange() {
        val passage = p(3, 16, 4, 2)
        assertTrue(PlanProgressMath.contains(passage, VerseRef(43, 3, 16)))
        assertTrue(PlanProgressMath.contains(passage, VerseRef(43, 3, 36)))
        assertTrue(PlanProgressMath.contains(passage, VerseRef(43, 4, 2)))
        assertFalse(PlanProgressMath.contains(passage, VerseRef(43, 3, 15)))
        assertFalse(PlanProgressMath.contains(passage, VerseRef(43, 4, 3)))
        assertFalse(PlanProgressMath.contains(passage, VerseRef(42, 3, 20)))
    }
}
