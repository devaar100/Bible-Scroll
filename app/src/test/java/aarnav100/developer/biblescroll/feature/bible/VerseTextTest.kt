package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import org.junit.Assert.assertEquals
import org.junit.Test

class VerseTextTest {

    @Test
    fun formatsQuotedTextReferenceAndTranslation() {
        assertEquals(
            "\"For God so loved the world.\" — John 3:16 (WEB)",
            verseClipText("For God so loved the world.", "John 3:16", "WEB"),
        )
    }

    @Test
    fun redLetterRangesDoNotLeakIntoCopiedText() {
        val text = "Jesus said to him, “I am the way.”"
        val verse = Verse("WEB", VerseRef(43, 14, 6), 1, text, wordsOfJesus = listOf(19 until text.length))

        assertEquals("\"$text\" — John 14:6 (WEB)", verse.clipText("John 14:6"))
    }

    @Test
    fun trimsAndCollapsesWhitespace() {
        assertEquals(
            "\"In the beginning God created.\" — Genesis 1:1 (KJV)",
            verseClipText("  In the beginning\n God   created. ", "Genesis 1:1", "KJV"),
        )
    }

    @Test
    fun keepsTheVersesOwnQuotationMarks() {
        assertEquals(
            "\"He said, “Follow me.”\" — Mark 2:14 (WEB)",
            verseClipText("He said, “Follow me.”", "Mark 2:14", "WEB"),
        )
    }

    @Test
    fun spokenSentenceAddsStopOnlyWhenMissing() {
        assertEquals("In the beginning.", spokenSentence("In the beginning "))
        assertEquals("a light for my path.", spokenSentence("a light for my path."))
        assertEquals("He said, “Follow me.”", spokenSentence("He said, “Follow me.”"))
        assertEquals("Amen!", spokenSentence("Amen!"))
    }
}
