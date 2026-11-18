package aarnav100.developer.biblescroll.widget

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeWidgetRefresher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetVerseRepositoryTest {

    private val refresher = FakeWidgetRefresher()
    private val dataStore = FakePreferencesDataStore()
    private val repo = WidgetVerseRepository(dataStore, FakeBibleRepository(), refresher)

    private val john316 = VerseRef(43, 3, 16)

    @Test
    fun defaultsToPsalm119_105UntilSet() = runTest {
        val verse = repo.widgetVerse.first()
        assertEquals(VerseRef(19, 119, 105), verse.ref)
        assertEquals("WEB", verse.translationId)
        assertTrue(verse.isDefault)

        val display = repo.display.first()
        assertEquals("Psalms 119:105", display.reference)
        assertEquals("WEB ${verse.ref}", display.text)
    }

    @Test
    fun setStoresVerseAndTranslationAndRefreshesWidgets() = runTest {
        repo.setWidgetVerse(john316, "WEB")

        assertEquals(WidgetVerse(john316, "WEB", isDefault = false), repo.widgetVerse.first())
        assertEquals(1, refresher.refreshes)
        val display = repo.display.first()
        assertEquals("John 3:16", display.reference)
        assertEquals(john316, display.ref)
    }

    @Test
    fun everyChangeRefreshes() = runTest {
        repo.setWidgetVerse(john316, "WEB")
        repo.setWidgetVerse(VerseRef(1, 1, 1), "SHORT")

        assertEquals(2, refresher.refreshes)
        assertEquals("SHORT", repo.widgetVerse.first().translationId)
        assertFalse(repo.widgetVerse.first().isDefault)
    }

    @Test
    fun verseMissingFromItsTranslationFallsBackToTheDefault() = runTest {
        // The fake SHORT translation has no John 3:16.
        repo.setWidgetVerse(john316, "SHORT")

        val display = repo.display.first()
        assertEquals(WidgetVerseRepository.DEFAULT.ref, display.ref)
        assertEquals("Psalms 119:105", display.reference)
        assertEquals("WEB", display.translationId)
    }

    @Test
    fun survivesANewRepositoryInstance() = runTest {
        repo.setWidgetVerse(john316, "WEB")

        val reopened = WidgetVerseRepository(dataStore, FakeBibleRepository(), FakeWidgetRefresher())

        assertEquals(john316, reopened.widgetVerse.first().ref)
    }
}
