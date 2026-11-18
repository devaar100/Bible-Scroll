package aarnav100.developer.biblescroll.feature.bible.sheets

import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompareRowsTest {

    private val web = Translation("WEB", "World English Bible", "en", "PD")
    private val kjv = Translation("KJV", "King James Version", "en", "PD")
    private val bsb = Translation("BSB", "Berean Standard Bible", "en", "PD")
    private val all = listOf(web, kjv, bsb) // bundled sort order

    private val acts837 = VerseRef(44, 8, 37)
    private fun verse(t: String, ref: VerseRef = acts837) = Verse(t, ref, 1, "$t text", emptyList())

    @Test
    fun currentTranslationFirstThenBundledOrder() {
        val rows = compareRows(all, listOf(verse("WEB"), verse("KJV"), verse("BSB")), currentTranslationId = "KJV")

        assertEquals(listOf("KJV", "WEB", "BSB"), rows.map { it.translation.id })
        assertEquals(listOf(true, false, false), rows.map { it.isCurrent })
    }

    @Test
    fun missingVerseStillGetsARowWithNullVerse() {
        // Acts 8:37 is a textual variant: in KJV, absent from WEB.
        val rows = compareRows(all, listOf(verse("KJV")), currentTranslationId = "WEB")

        assertEquals(listOf("WEB", "KJV", "BSB"), rows.map { it.translation.id })
        assertNull(rows[0].verse)
        assertEquals("KJV text", rows[1].verse?.text)
        assertNull(rows[2].verse)
    }

    @Test
    fun verseInNoTranslationGivesAllMissingRowsNotAnEmptyList() {
        val rows = compareRows(all, emptyList(), currentTranslationId = "WEB")

        assertEquals(3, rows.size)
        rows.forEach { assertNull(it.verse) }
    }

    @Test
    fun unknownCurrentTranslationKeepsBundledOrderAndMarksNothing() {
        val rows = compareRows(all, emptyList(), currentTranslationId = "XYZ")

        assertEquals(listOf("WEB", "KJV", "BSB"), rows.map { it.translation.id })
        assertEquals(listOf(false, false, false), rows.map { it.isCurrent })
    }

    @Test
    fun ignoresVersesFromTranslationsNotInTheList() {
        val rows = compareRows(listOf(web), listOf(verse("WEB"), verse("OLD")), currentTranslationId = "WEB")

        assertEquals(listOf("WEB"), rows.map { it.translation.id })
    }
}
