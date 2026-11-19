package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.feature.bible.sheets.compareRows
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** B9 against the real bundled bible.db, including verses that only some translations have. */
@RunWith(AndroidJUnit4::class)
class BibleCompareQueryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: BibleDatabase
    private lateinit var repo: BibleRepository

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, BibleDatabase::class.java, TEST_DB)
            .createFromAsset(BibleDatabase.ASSET_PATH)
            .build()
        repo = OfflineBibleRepository(db.bibleDao())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    private suspend fun rows(ref: VerseRef, current: String) =
        compareRows(repo.translations().first(), repo.verseInAllTranslations(ref).first(), current)

    @Test
    fun johnThreeSixteenIsInEveryTranslationInSortOrder() = runBlocking {
        val verses = repo.verseInAllTranslations(VerseRef(43, 3, 16)).first()
        val translations = repo.translations().first()

        assertEquals(translations.map { it.id }, verses.map { it.translationId })
        // Red letters exactly where the source marks them (translation_info.has_red_letters: WEB, KJV).
        val marked = translations.associate { it.id to it.hasRedLetters }
        assertTrue(verses.all { it.wordsOfJesus.isNotEmpty() == marked.getValue(it.translationId) })
    }

    @Test
    fun acts837IsKjvOnly() = runBlocking {
        val rows = rows(VerseRef(44, 8, 37), current = "WEB")

        assertEquals("WEB", rows.first().translation.id)
        assertNull(rows.first { it.translation.id == "WEB" }.verse)
        assertNotNull(rows.first { it.translation.id == "KJV" }.verse)
    }

    @Test
    fun romansDoxologyNumberingDiffersBetweenWebAndKjv() = runBlocking {
        // WEB places it at 14:24-26, KJV at 16:25-27.
        val at14 = rows(VerseRef(45, 14, 24), current = "KJV")
        assertEquals("KJV", at14.first().translation.id)
        assertNull(at14.first { it.translation.id == "KJV" }.verse)
        assertNotNull(at14.first { it.translation.id == "WEB" }.verse)

        val at16 = rows(VerseRef(45, 16, 25), current = "KJV")
        assertNotNull(at16.first { it.translation.id == "KJV" }.verse)
        assertNull(at16.first { it.translation.id == "WEB" }.verse)
    }

    @Test
    fun aNonexistentVerseGivesMissingRowsNotACrash() = runBlocking {
        val rows = rows(VerseRef(43, 3, 99), current = "WEB")

        assertEquals(repo.translations().first().size, rows.size)
        assertTrue(rows.all { it.verse == null })
    }

    private companion object {
        const val TEST_DB = "bible-compare-test.db"
    }
}
