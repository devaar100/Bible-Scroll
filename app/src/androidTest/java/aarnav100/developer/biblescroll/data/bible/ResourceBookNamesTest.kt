package aarnav100.developer.biblescroll.data.bible

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.settings.AppLanguage
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Spanish UI: names are Spanish, search also matches English, and loading the English names doesn't switch the
 * app's own resources to English (a Resources sharing the AssetManager did, so Spanish phones saw "Psalms 23:2").
 */
@RunWith(AndroidJUnit4::class)
class ResourceBookNamesTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    /** AppLanguage sets process-wide ContentLanguage and reference patterns: put them back for later tests. */
    @After
    fun tearDown() {
        AppLanguage(target)
    }

    @Test
    fun spanishNamesWithEnglishSearchTerms() = runBlocking {
        val spanish = target.createConfigurationContext(
            Configuration(target.resources.configuration).apply { setLocale(Locale.forLanguageTag("es")) },
        )
        val books = ResourceBookNames(AppLanguage(spanish)).books.first()!!

        assertEquals("Salmos", books.getValue(19).name)
        assertEquals("Juan", books.getValue(43).name)
        assertTrue(books.getValue(19).searchTerms.contains("psalms"))
        assertEquals("Salmos", spanish.resources.getStringArray(R.array.book_names)[18]) // still Spanish afterwards
    }
}
