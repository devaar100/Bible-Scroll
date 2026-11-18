package aarnav100.developer.biblescroll.data.reminder

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import java.time.LocalDate

/**
 * Short, encouraging verses for the daily reminder. References only: the text comes from the
 * bundled Bible in the reader's translation. All are under 180 characters in WEB and KJV
 * (checked by ReminderVersesDbTest against the real database).
 */
object ReminderVerses {
    val refs: List<VerseRef> = listOf(
        VerseRef(6, 1, 9), VerseRef(19, 16, 8), VerseRef(19, 18, 2), VerseRef(19, 23, 1),
        VerseRef(19, 27, 1), VerseRef(19, 28, 7), VerseRef(19, 30, 5), VerseRef(19, 31, 24),
        VerseRef(19, 34, 8), VerseRef(19, 34, 18), VerseRef(19, 37, 4), VerseRef(19, 46, 1),
        VerseRef(19, 46, 10), VerseRef(19, 55, 22), VerseRef(19, 56, 3), VerseRef(19, 73, 26),
        VerseRef(19, 91, 1), VerseRef(19, 103, 2), VerseRef(19, 118, 24), VerseRef(19, 119, 105),
        VerseRef(19, 121, 2), VerseRef(19, 139, 14), VerseRef(19, 143, 8), VerseRef(19, 145, 18),
        VerseRef(19, 147, 3), VerseRef(20, 3, 5), VerseRef(20, 3, 6), VerseRef(20, 16, 3),
        VerseRef(20, 18, 10), VerseRef(23, 26, 3), VerseRef(23, 40, 29), VerseRef(23, 40, 31),
        VerseRef(24, 29, 11), VerseRef(25, 3, 22), VerseRef(25, 3, 23), VerseRef(34, 1, 7),
        VerseRef(40, 6, 34), VerseRef(40, 11, 28), VerseRef(41, 10, 27), VerseRef(42, 1, 37),
        VerseRef(43, 8, 12), VerseRef(43, 10, 10), VerseRef(43, 14, 27), VerseRef(43, 16, 33),
        VerseRef(45, 8, 28), VerseRef(45, 12, 12), VerseRef(45, 15, 13), VerseRef(46, 16, 14),
        VerseRef(47, 5, 7), VerseRef(48, 6, 9), VerseRef(49, 4, 32), VerseRef(50, 4, 6),
        VerseRef(50, 4, 13), VerseRef(51, 3, 23), VerseRef(52, 5, 16), VerseRef(55, 1, 7),
        VerseRef(58, 13, 8), VerseRef(59, 1, 5), VerseRef(60, 5, 7), VerseRef(62, 4, 19),
    )

    /**
     * The verse for [date], then the rest of the list in order as fallbacks (for a translation that
     * lacks one). Same day, same verse; consecutive days walk through the list.
     */
    fun candidatesFor(date: LocalDate): List<VerseRef> {
        val start = Math.floorMod(date.toEpochDay(), refs.size.toLong()).toInt()
        return refs.drop(start) + refs.take(start)
    }
}
