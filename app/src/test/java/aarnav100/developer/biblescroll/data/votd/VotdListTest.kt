package aarnav100.developer.biblescroll.data.votd

import aarnav100.developer.biblescroll.core.content.BookCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.MonthDay

/**
 * Checks the real app/src/main/assets/votd.csv. Failures list every bad row so the list can be fixed by hand;
 * the test never edits it. That every id exists, non-empty, in every bundled translation needs bible.db, so it
 * lives in VotdAssetTest (androidTest).
 */
class VotdListTest {

    private val list = VotdList.parse(File("src/main/assets/votd.csv").readText())

    @Test
    fun every366DatesAppearsExactlyOnce() {
        val counts = list.entries.groupingBy { it.monthDay }.eachCount()
        // 2028 is a leap year, so this walks all 366 month-days including 02-29.
        val all = generateSequence(LocalDate.of(2028, 1, 1)) { it.plusDays(1) }.takeWhile { it.year == 2028 }
            .map { MonthDay.from(it) }.toList()
        assertEquals(366, all.size)
        val missing = all.filter { it !in counts }
        val duplicated = counts.filterValues { it > 1 }.keys
        assertTrue("Missing dates: $missing; listed more than once: $duplicated", missing.isEmpty() && duplicated.isEmpty())
        assertEquals(366, list.entries.size)
    }

    @Test
    fun idsAreUnique() {
        val repeated = list.entries.groupBy { it.id }.filterValues { it.size > 1 }
            .mapValues { (_, rows) -> rows.map { it.monthDay } }
        assertTrue("Ids used on more than one date: $repeated", repeated.isEmpty())
    }

    @Test
    fun idsAreWellFormedAndMatchTheirParsedVerse() {
        val bad = list.entries.filter { e ->
            BookCodes.parse(e.id)?.let { it.bookId != e.ref.bookId || it.chapter != e.ref.chapter || it.verse != e.ref.verse } ?: true
        }
        assertTrue("Bad ids: ${bad.map { it.monthDay to it.id }}", bad.isEmpty())
        assertTrue("Every row needs a section", list.entries.all { it.section.isNotBlank() })
    }

    @Test
    fun malformedRowsFailLoudly() {
        assertThrows(IllegalArgumentException::class.java) { VotdList.parse("date,id,section\n13-01,JHN.3.16,Gospels") }
        assertThrows(IllegalArgumentException::class.java) { VotdList.parse("date,id,section\n1-1,JHN.3.16,Gospels") }
        assertThrows(IllegalArgumentException::class.java) { VotdList.parse("date,id,section\n01-01,XYZ.3.16,Gospels") }
        assertThrows(IllegalArgumentException::class.java) { VotdList.parse("date,id,section\n01-01,JHN.3,Gospels") }
        assertThrows(IllegalArgumentException::class.java) { VotdList.parse("id,date,section\n") }
    }
}
