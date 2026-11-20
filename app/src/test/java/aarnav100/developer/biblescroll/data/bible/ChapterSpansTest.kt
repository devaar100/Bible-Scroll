package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterSpansTest {

    /** Psalm 118 (29), Psalm 119 (176), Psalm 120 (7), then Acts 8 with verse 37 missing (39 rows, last 40). */
    private val spans = ChapterSpans.of(
        "WEB",
        listOf(
            ChapterSpanRow(19, 118, firstOrdinal = 100, verseCount = 29, lastVerse = 29),
            ChapterSpanRow(19, 119, firstOrdinal = 129, verseCount = 176, lastVerse = 176),
            ChapterSpanRow(19, 120, firstOrdinal = 305, verseCount = 7, lastVerse = 7),
            ChapterSpanRow(44, 8, firstOrdinal = 312, verseCount = 39, lastVerse = 40),
        ),
    )

    @Test
    fun findsTheChapterOfAnyPage() {
        // page = ordinal - 1
        assertEquals(ChapterSpan(19, 118, firstPage = 99, count = 29, lastVerse = 29), spans.spanAt(99))
        assertEquals(19 to 118, spans.spanAt(127)!!.let { it.bookId to it.chapter })
        assertEquals(ChapterSpan(19, 119, firstPage = 128, count = 176, lastVerse = 176), spans.spanAt(128))
        assertEquals(19 to 119, spans.spanAt(303)!!.let { it.bookId to it.chapter })
        assertEquals(19 to 120, spans.spanAt(304)!!.let { it.bookId to it.chapter })
        assertEquals(40, spans.spanAt(349)!!.lastVerse) // last page of Acts 8 (ordinals 312-350)
    }

    @Test
    fun pagesOutsideEveryChapterHaveNoSpan() {
        assertNull(spans.spanAt(0))
        assertNull(spans.spanAt(98))
        assertNull(spans.spanAt(350))
    }

    @Test
    fun indexAndPageMapBothWaysWithClamping() {
        val psalm119 = spans.spanAt(200)!!
        assertEquals(0, psalm119.indexOf(128))
        assertEquals(175, psalm119.indexOf(303))
        assertEquals(128 + 104, psalm119.pageOf(104))
        assertEquals(128, psalm119.pageOf(-5))
        assertEquals(303, psalm119.pageOf(500))
        assertEquals(175, psalm119.indexOf(9_999))
    }

    @Test
    fun repositorySpansMatchTheVerses() = runTest {
        val bible = FakeBibleRepository()
        val web = bible.chapterSpans("WEB")
        val short = bible.chapterSpans("SHORT") // lacks John 3:16
        val john3Web = web.spanAt(bible.ordinalOf("WEB", VerseRef(43, 3, 15)) - 1)!!
        val john3Short = short.spanAt(bible.ordinalOf("SHORT", VerseRef(43, 3, 15)) - 1)!!
        assertEquals(3, john3Web.count)
        assertEquals(2, john3Short.count)
        assertEquals(17, john3Short.lastVerse)
    }
}
