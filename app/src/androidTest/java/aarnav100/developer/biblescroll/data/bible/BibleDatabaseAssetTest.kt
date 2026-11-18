package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Opens the real assets/bible.db through Room, so a schema mismatch fails here, not in the app. */
@RunWith(AndroidJUnit4::class)
class BibleDatabaseAssetTest {
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

    @Test
    fun assetPassesRoomValidationAndHasBothTranslations() = runBlocking {
        assertEquals(listOf("WEB", "KJV"), repo.translations().first().map { it.id })
        assertEquals(66, repo.books().first().size)
        assertEquals(31_102, db.bibleDao().observeVersesFrom("KJV", 1, 40_000).first().size)
    }

    @Test
    fun johnThreeSixteenIsRedLetter() = runBlocking {
        val verse = repo.verse("KJV", VerseRef(bookId = 43, chapter = 3, verse = 16)).first()!!
        assertTrue(verse.text.startsWith("For God so loved the world"))
        assertEquals(listOf(verse.text.indices), verse.wordsOfJesus)
    }

    @Test
    fun feedCrossesBookBoundary() = runBlocking {
        val lastOfGenesis = repo.verse("WEB", VerseRef(1, 50, 26)).first()!!
        val next = repo.versesFrom("WEB", lastOfGenesis.ordinal + 1, 1).first().single()
        assertEquals(VerseRef(2, 1, 1), next.ref)
    }

    private companion object {
        const val TEST_DB = "bible-asset-test.db"
    }
}
