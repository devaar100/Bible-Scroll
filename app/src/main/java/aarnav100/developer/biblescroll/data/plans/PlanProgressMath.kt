package aarnav100.developer.biblescroll.data.plans

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Pure plan arithmetic (no Android): chapters fully read, catching up, schedule shifts. */
object PlanProgressMath {

    /** The calendar day number for [today] (1 on the start date; 0 or less before it). */
    fun calendarDay(startDate: LocalDate, today: LocalDate): Int = (ChronoUnit.DAYS.between(startDate, today) + 1).toInt()

    /**
     * How many days the calendar is ahead of the next unread day (0 when on track, ahead, not started or
     * finished). Shown neutrally as "next up"; never as "behind".
     */
    fun daysToCatchUp(totalDays: Int, done: Set<Int>, startDate: LocalDate, today: LocalDate): Int {
        val next = PlanSchedule.nextDay(totalDays, done) ?: return 0
        val calendar = calendarDay(startDate, today).coerceAtMost(totalDays)
        return (calendar - next).coerceAtLeast(0)
    }

    /**
     * "Adjust schedule": the start date that makes the next unread day fall on [today]. Only ever moves the
     * schedule forward (never earlier than [startDate]).
     */
    fun adjustedStart(totalDays: Int, done: Set<Int>, startDate: LocalDate, today: LocalDate): LocalDate {
        val next = PlanSchedule.nextDay(totalDays, done) ?: return startDate
        val shifted = today.minusDays((next - 1).toLong())
        return if (shifted.isAfter(startDate)) shifted else startDate
    }

    /**
     * Chapters whose every verse is covered by [passages] (all the read days' passages together), limited
     * to chapters [touched] by the day just read. [verseCount] gives a chapter's verse count (KJV numbering,
     * as in the plans), or null if unknown, in which case the chapter is never marked.
     */
    fun fullyReadChapters(
        passages: List<PassageFile>,
        touched: List<PassageFile>,
        verseCount: (bookId: Int, chapter: Int) -> Int?,
    ): Map<Int, Set<Int>> {
        val covered = HashMap<Pair<Int, Int>, MutableSet<Int>>()
        for (p in passages) {
            val book = p.bookId ?: continue
            for (chapter in p.startChapter..p.endChapter) {
                val count = verseCount(book, chapter) ?: continue
                val from = if (chapter == p.startChapter) p.startVerse else 1
                val to = if (chapter == p.endChapter) p.endVerse else count
                covered.getOrPut(book to chapter) { HashSet() }.addAll(from..to.coerceAtMost(count))
            }
        }
        val out = HashMap<Int, MutableSet<Int>>()
        for (p in touched) {
            val book = p.bookId ?: continue
            for (chapter in p.startChapter..p.endChapter) {
                val count = verseCount(book, chapter) ?: continue
                if (covered[book to chapter]?.containsAll((1..count).toList()) == true) out.getOrPut(book) { HashSet() } += chapter
            }
        }
        return out
    }

    /** Whether [ref] falls inside [p] (same book, between its first and last verse). */
    fun contains(p: PassageFile, ref: VerseRef): Boolean {
        if (p.bookId != ref.bookId) return false
        val at = ref.chapter to ref.verse
        return compareValuesBy(at, p.startChapter to p.startVerse, { it.first }, { it.second }) >= 0 &&
            compareValuesBy(at, p.endChapter to p.endVerse, { it.first }, { it.second }) <= 0
    }
}
