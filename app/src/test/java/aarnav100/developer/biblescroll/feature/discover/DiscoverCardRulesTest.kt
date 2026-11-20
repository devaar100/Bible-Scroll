package aarnav100.developer.biblescroll.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverCardRulesTest {

    @Test
    fun verseSizeThresholds() {
        assertEquals(DiscoverCardRules.VerseSize(24f, 1.4f), DiscoverCardRules.verseSize("a".repeat(110)))
        assertEquals(DiscoverCardRules.VerseSize(21f, 1.45f), DiscoverCardRules.verseSize("a".repeat(111)))
        assertEquals(DiscoverCardRules.VerseSize(21f, 1.45f), DiscoverCardRules.verseSize("a".repeat(190)))
        assertEquals(DiscoverCardRules.VerseSize(19f, 1.5f), DiscoverCardRules.verseSize("a".repeat(191)))
        assertEquals(DiscoverCardRules.VerseSize(24f, 1.4f), DiscoverCardRules.verseSize("Jesus wept."))
    }

    @Test
    fun surroundingWhitespaceDoesNotCount() {
        assertEquals(24f, DiscoverCardRules.verseSize("  " + "a".repeat(110) + "\n").fontSp)
    }

    @Test
    fun didYouKnowIsHiddenWhenEmptyOrMissing() {
        assertNull(DiscoverCardRules.sections("Hook.", "", aiHidden = false, hasStudyData = true).fact)
        assertNull(DiscoverCardRules.sections("Hook.", "   ", aiHidden = false, hasStudyData = true).fact)
        assertNull(DiscoverCardRules.sections(null, null, aiHidden = false, hasStudyData = false).fact)
        assertEquals("Cedars live long.", DiscoverCardRules.sections("Hook.", " Cedars live long. ", aiHidden = false, hasStudyData = true).fact)
    }

    @Test
    fun noStudyShowsNeitherHookNorFact() {
        val s = DiscoverCardRules.sections(null, null, aiHidden = false, hasStudyData = false)
        assertNull(s.hook)
        assertNull(s.fact)
        assertFalse(s.showDataLine)
    }

    @Test
    fun hiddenAiShowsTheDataLineInsteadOfAiText() {
        val s = DiscoverCardRules.sections("Hook.", "Fact.", aiHidden = true, hasStudyData = true)
        assertNull(s.hook)
        assertNull(s.fact)
        assertTrue(s.showDataLine)
        assertFalse(DiscoverCardRules.sections("Hook.", "Fact.", aiHidden = true, hasStudyData = false).showDataLine)
    }
}
