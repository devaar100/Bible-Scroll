package aarnav100.developer.biblescroll.data.backup

import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeWidgetRefresher
import aarnav100.developer.biblescroll.testing.like
import aarnav100.developer.biblescroll.testing.note
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreAndReadableCopyTest {

    private val prefs = FakePreferencesDataStore()
    private val widgets = FakeWidgetRefresher()
    private val repair = RestoreRepair(prefs, FakeBibleRepository(), widgets)

    private val translation = stringPreferencesKey("translation_id")
    private val widgetBook = intPreferencesKey("widget_book")
    private val widgetChapter = intPreferencesKey("widget_chapter")
    private val widgetVerse = intPreferencesKey("widget_verse")
    private val widgetTranslation = stringPreferencesKey("widget_translation")

    private suspend fun setWidget(book: Int, chapter: Int, verse: Int, t: String) = prefs.edit {
        it[widgetBook] = book; it[widgetChapter] = chapter; it[widgetVerse] = verse; it[widgetTranslation] = t
    }

    @Test
    fun missingTranslationFallsBackToWeb() = runTest {
        prefs.edit { it[translation] = "NIV" } // chosen on the old phone, not on this one
        repair.repair()
        assertEquals("WEB", prefs.data.first()[translation])
    }

    @Test
    fun availableTranslationIsKept() = runTest {
        prefs.edit { it[translation] = "SHORT" }
        repair.repair()
        assertEquals("SHORT", prefs.data.first()[translation])
    }

    @Test
    fun widgetVerseThatDoesntExistResetsToDefault() = runTest {
        setWidget(43, 3, 16, "SHORT") // SHORT has no John 3:16
        repair.repair()
        val p = prefs.data.first()
        assertEquals(null, p[widgetBook])
        assertEquals(null, p[widgetTranslation])
        assertEquals(1, widgets.refreshes)
    }

    @Test
    fun widgetVerseInMissingTranslationResetsToDefault() = runTest {
        setWidget(43, 3, 16, "NIV")
        repair.repair()
        assertEquals(null, prefs.data.first()[widgetBook])
    }

    @Test
    fun validWidgetVerseIsKeptAndNoticeIsRaised() = runTest {
        setWidget(43, 3, 16, "WEB")
        repair.repair()
        val p = prefs.data.first()
        assertEquals(16, p[widgetVerse])
        assertEquals(true, p[booleanPreferencesKey("restore_notice")])
        assertTrue(BackupNotices(prefs).restoreNotice.first())
    }

    @Test
    fun readableCopyListsNotesLikesAndBookmarksInBibleOrderWithVerseText() {
        val tables = BackupTables(
            verseMarks = listOf(like(43, 3, 16, 1, bookmarked = true), like(1, 1, 1, 1), like(19, 1, 1, 1, liked = false)),
            notes = listOf(note(43, 3, 16, "Loved\nso much", 1), note(1, 1, 1, "Beginning", 1)),
        )
        val md = notesMarkdown(
            tables,
            verseText = mapOf(VerseRef(43, 3, 16) to "For God so loved the world", VerseRef(1, 1, 1) to "In the beginning"),
            bookNames = mapOf(1 to "Genesis", 43 to "John"),
            translationId = "WEB",
            date = "18 November 2026",
            labels = MarkdownLabels("My notes", "Exported %1\$s · %2\$s", "Notes", "Likes", "Bookmarks", "None yet."),
        )
        assertTrue(md.startsWith("# My notes\n\nExported 18 November 2026 · WEB\n"))
        assertTrue(md.indexOf("### Genesis 1:1") < md.indexOf("### John 3:16"))
        assertTrue(md.contains("> For God so loved the world\n\nLoved\nso much"))
        assertTrue(md.contains("## Likes (2)\n\n- **Genesis 1:1**: In the beginning\n- **John 3:16**: For God so loved the world"))
        assertTrue(md.contains("## Bookmarks (1)\n\n- **John 3:16**: For God so loved the world"))
        assertTrue(!md.contains("Psalms") && !md.contains("19 1:1")) // unliked mark isn't listed
    }
}
