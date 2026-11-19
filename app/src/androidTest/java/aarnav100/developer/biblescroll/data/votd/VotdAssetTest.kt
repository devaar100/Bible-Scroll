package aarnav100.developer.biblescroll.data.votd

import aarnav100.developer.biblescroll.core.content.AndroidAssetSource
import aarnav100.developer.biblescroll.data.bible.BibleDatabase
import aarnav100.developer.biblescroll.data.bible.OfflineBibleRepository
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every Verse of the Day id exists, with text, in every translation bundled in assets/bible.db. Reports all
 * failures at once; fix the list (or the translation), never this test. Uses its own copy of the database, so
 * nothing on the phone changes.
 */
@RunWith(AndroidJUnit4::class)
class VotdAssetTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: BibleDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, BibleDatabase::class.java, TEST_DB)
            .createFromAsset(BibleDatabase.ASSET_PATH)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun everyIdHasTextInEveryBundledTranslation() = runBlocking {
        val list = VotdSource(AndroidAssetSource(context.assets), Dispatchers.IO).list()!!
        assertEquals(366, list.entries.size)
        val bible = OfflineBibleRepository(db.bibleDao())
        val translations = bible.translations().first().map { it.id }
        val failures = list.entries.flatMap { entry ->
            translations.mapNotNull { translation ->
                val text = bible.verse(translation, entry.ref).first()?.text
                if (text.isNullOrBlank()) "${entry.monthDay} ${entry.id} missing or empty in $translation" else null
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val TEST_DB = "votd-asset-test.db"
    }
}
