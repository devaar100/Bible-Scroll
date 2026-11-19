package aarnav100.developer.biblescroll.data.votd

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.votdRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VerseOfTheDayRepositoryTest {

    private val dataStore = FakePreferencesDataStore()
    private val repo = votdRepository(dataStore)
    private val march16 = LocalDate.of(2028, 3, 16)

    @Test
    fun showsTheVerseInTheReadersTranslation() = runTest {
        val votd = repo.forDate(march16).first()!!

        assertEquals(VerseRef(43, 3, 16), votd.verse.ref)
        assertEquals("WEB", votd.verse.translationId)
        assertEquals("John 3:16", votd.reference)
        assertEquals("World English Bible", votd.translationName)
        assertFalse(votd.fellBack)
    }

    @Test
    fun fallsBackToWebWhenTheTranslationLacksTheVerse() = runTest {
        ReaderPreferences(dataStore).setTranslationId("SHORT") // the fake SHORT has no John 3:16

        val votd = repo.forDate(march16).first()!!

        assertEquals("WEB", votd.verse.translationId)
        assertEquals("SHORT", votd.requestedTranslationId)
        assertTrue(votd.fellBack)
    }

    @Test
    fun noFallbackWhenTheTranslationHasTheVerse() = runTest {
        ReaderPreferences(dataStore).setTranslationId("SHORT")

        val votd = repo.forDate(LocalDate.of(2028, 3, 17)).first()!!

        assertEquals("SHORT", votd.verse.translationId)
        assertFalse(votd.fellBack)
    }

    @Test
    fun followsATranslationChange() = runTest {
        val prefs = ReaderPreferences(dataStore)
        val date = LocalDate.of(2028, 3, 17)
        assertEquals("WEB", repo.forDate(date).first()!!.verse.translationId)

        prefs.setTranslationId("SHORT")

        assertEquals("SHORT", repo.forDate(date).first()!!.verse.translationId)
    }

    @Test
    fun nullWhenTheDateHasNoEntry() = runTest {
        assertNull(repo.forDate(LocalDate.of(2028, 6, 1)).first())
    }

    @Test
    fun nullWhenNoTranslationHasTheVerse() = runTest {
        val repo = votdRepository(dataStore, csv = "date,id,section\n03-16,REV.22.21,Revelation")
        assertNull(repo.forDate(march16).first())
    }
}
