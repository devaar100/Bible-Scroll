package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.data.legal.TranslationLicence
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** S8 Legal: every translation in the real assets/bible.db has licence text and an attribution ("Source: …"). */
@RunWith(AndroidJUnit4::class)
class TranslationLicenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: BibleDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
        db = Room.databaseBuilder(context, BibleDatabase::class.java, TEST_DB).createFromAsset(BibleDatabase.ASSET_PATH).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun everyTranslationHasLicenceAndAttribution() = runBlocking {
        val translations = OfflineBibleRepository(db.bibleDao()).translations().first()
        assertFalse(translations.isEmpty())
        val problems = translations.mapNotNull { t ->
            val (licence, attribution) = TranslationLicence.split(t.licence)
            when {
                t.name.isBlank() -> "${t.id}: no name"
                licence.isBlank() -> "${t.id}: no licence text"
                attribution.isNullOrBlank() -> "${t.id}: no attribution (\"Source: …\")"
                else -> null
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val TEST_DB = "translation-licence-test.db"
    }
}
