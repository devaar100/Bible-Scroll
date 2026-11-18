package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.sampleStudy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTextTest {

    @Test
    fun teaserIsTheFirstSentence() {
        assertEquals("God loves the world.", studyTeaser(sampleStudy("JHN.3.16")))
    }

    @Test
    fun longTeaserIsCutAtAWord() {
        val long = "This " + "very ".repeat(60) + "long sentence ends here."
        val teaser = studyTeaser(sampleStudy("JHN.3.16", meaning = long), maxChars = 50)
        assertTrue(teaser.endsWith("…") && teaser.length <= 51)
        assertTrue(!teaser.removeSuffix("…").endsWith(" "))
    }

    @Test
    fun crossReferenceLabels() {
        val names = mapOf(1 to "Genesis", 3 to "Leviticus", 4 to "Numbers", 45 to "Romans")
        fun label(ref: String) = formatRefRange(parseRefRange(ref)!!, names)
        assertEquals("Romans 8:28", label("ROM.8.28"))
        assertEquals("Romans 8:28–30", label("ROM.8.28-ROM.8.30"))
        assertEquals("Genesis 1:1–2:3", label("GEN.1.1-GEN.2.3"))
        assertEquals("Leviticus 27:34 – Numbers 1:1", label("LEV.27.34-NUM.1.1"))
        assertEquals(VerseRef(45, 8, 28), parseRefRange("ROM.8.28-ROM.8.30")!!.start)
        assertNull(parseRefRange("Romans 8:28"))
    }
}
