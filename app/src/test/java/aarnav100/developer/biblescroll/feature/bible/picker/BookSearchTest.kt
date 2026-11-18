package aarnav100.developer.biblescroll.feature.bible.picker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSearchTest {

    @Test
    fun emptyOrBlankQueryMatchesEverything() {
        assertTrue(matchesBookQuery("Genesis", "GEN", ""))
        assertTrue(matchesBookQuery("Genesis", "GEN", "   "))
    }

    @Test
    fun ignoresCaseSpacesAndPunctuation() {
        assertTrue(matchesBookQuery("1 Corinthians", "1CO", "1 cor"))
        assertTrue(matchesBookQuery("1 Corinthians", "1CO", "1cor"))
        assertTrue(matchesBookQuery("Song of Solomon", "SNG", "songof"))
        assertTrue(matchesBookQuery("Psalms", "PSA", "PSA"))
    }

    @Test
    fun matchesInsideNameAndByCode() {
        assertTrue(matchesBookQuery("1 John", "1JN", "john"))
        assertTrue(matchesBookQuery("John", "JHN", "jhn"))
        assertTrue(matchesBookQuery("1 Corinthians", "1CO", "1co"))
    }

    @Test
    fun rejectsNonMatches() {
        assertFalse(matchesBookQuery("Genesis", "GEN", "exo"))
        assertFalse(matchesBookQuery("2 Kings", "2KI", "1 ki"))
    }
}
