package aarnav100.developer.biblescroll.data.reminder

import aarnav100.developer.biblescroll.data.bible.BibleDatabase
import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.OfflineBibleRepository
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Every reminder verse exists, and is short, in every bundled translation. */
@RunWith(AndroidJUnit4::class)
class ReminderVersesDbTest {
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
    fun allPresentAndShortInEveryTranslation() = runBlocking {
        val problems = mutableListOf<String>()
        for (t in repo.translations().first()) {
            for (ref in ReminderVerses.refs) {
                val v = repo.verse(t.id, ref).first()
                when {
                    v == null -> problems += "${t.id} $ref missing"
                    v.text.length > 180 -> problems += "${t.id} $ref is ${v.text.length} chars"
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val TEST_DB = "reminder-verses-test.db"
    }
}
