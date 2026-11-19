package aarnav100.developer.biblescroll.feature.home

import aarnav100.developer.biblescroll.data.bible.BibleRepository
import aarnav100.developer.biblescroll.data.bible.model.Book
import aarnav100.developer.biblescroll.data.bible.model.Chapter
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** H1 Verse Search: wheel ranges (one-chapter books, verse counts per translation) and the Read jump. */
@OptIn(ExperimentalCoroutinesApi::class)
class VerseSearchTest {

    /** Real chapter/verse counts for a few books; 3 John has 14 verses in KJV and 15 in WEB. */
    private class WheelBible : BibleRepository by FakeBibleRepository() {
        private val books = listOf(
            Book(19, "PSA", "Psalms", Testament.OLD, 150),
            Book(31, "OBA", "Obadiah", Testament.OLD, 1),
            Book(43, "JHN", "John", Testament.NEW, 21),
            Book(57, "PHM", "Philemon", Testament.NEW, 1),
            Book(63, "2JN", "2 John", Testament.NEW, 1),
            Book(64, "3JN", "3 John", Testament.NEW, 1),
            Book(65, "JUD", "Jude", Testament.NEW, 1),
        )
        private val john = listOf(51, 25, 36, 54, 47, 71, 53, 59, 41, 42, 57, 50, 38, 31, 27, 33, 26, 40, 42, 31, 25)

        override fun books() = flowOf(books)
        override fun chapters(translationId: String, bookId: Int) = flowOf(
            when (bookId) {
                19 -> (1..150).map { Chapter(19, it, if (it == 117) 2 else if (it == 119) 176 else 20, null) }
                31 -> listOf(Chapter(31, 1, 21, null))
                43 -> john.mapIndexed { i, n -> Chapter(43, i + 1, n, null) }
                57 -> listOf(Chapter(57, 1, 25, null))
                63 -> listOf(Chapter(63, 1, 13, null))
                64 -> listOf(Chapter(64, 1, if (translationId == "KJV") 14 else 15, null))
                65 -> listOf(Chapter(65, 1, 25, null))
                else -> emptyList()
            },
        )
    }

    private val dataStore = FakePreferencesDataStore()
    private val userDao = FakeUserDao()
    private val userData = LocalUserDataRepository(userDao) { 1L }
    private val link = PendingVerseLink()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel() = VerseSearchViewModel(WheelBible(), ReaderPreferences(dataStore), userData, link).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    // ---- Pure range logic ----

    @Test
    fun clampKeepsChapterAndVerseInsideTheBook() {
        assertEquals(VerseRef(65, 1, 25), VerseWheelRanges.clamp(VerseRef(65, 3, 40), chapterCount = 1, verseCount = 25))
        assertEquals(VerseRef(43, 3, 16), VerseWheelRanges.clamp(VerseRef(43, 3, 16), chapterCount = 21, verseCount = 36))
        assertEquals(VerseRef(43, 1, 1), VerseWheelRanges.clamp(VerseRef(43, 0, 0), chapterCount = 21, verseCount = 51))
        // Counts not loaded yet (0) never produce chapter or verse 0.
        assertEquals(VerseRef(43, 1, 1), VerseWheelRanges.clamp(VerseRef(43, 5, 9), chapterCount = 0, verseCount = 0))
    }

    // ---- The wheels' ranges follow book and chapter ----

    @Test
    fun startsAtJohn316WhenNothingHasBeenRead() = runTest {
        val vm = viewModel()
        assertEquals(VerseRef(43, 3, 16), vm.uiState.value.selection)
        assertEquals(21, vm.uiState.value.chapterCount)
        assertEquals(36, vm.uiState.value.verseCount)
        assertEquals("John 3:16", vm.uiState.value.reference)
    }

    @Test
    fun oneChapterBooksHaveASingleChapterAndTheirOwnVerseCount() = runTest {
        val vm = viewModel()
        for ((book, verses) in listOf(31 to 21, 57 to 25, 63 to 13, 65 to 25)) {
            vm.selectBook(43)
            vm.selectChapter(21)
            vm.selectVerse(25)
            vm.selectBook(book)
            val s = vm.uiState.value
            assertEquals("book $book chapters", 1, s.chapterCount)
            assertEquals("book $book verses", verses, s.verseCount)
            assertEquals(VerseRef(book, 1, minOf(25, verses)), s.selection)
        }
    }

    @Test
    fun jumpingFromJohn21ToObadiahClampsTheVerse() = runTest {
        val vm = viewModel()
        vm.selectChapter(6)
        vm.selectVerse(71)
        vm.selectBook(31)
        assertEquals(VerseRef(31, 1, 21), vm.uiState.value.selection)
        assertEquals("Obadiah 1:21", vm.uiState.value.reference)
    }

    @Test
    fun changingChapterClampsTheVerse() = runTest {
        val vm = viewModel()
        vm.selectBook(19)
        vm.selectChapter(119)
        vm.selectVerse(176)
        assertEquals(VerseRef(19, 119, 176), vm.uiState.value.selection)
        vm.selectChapter(117)
        assertEquals(VerseRef(19, 117, 2), vm.uiState.value.selection)
        assertEquals(2, vm.uiState.value.verseCount)
    }

    @Test
    fun verseBeyondTheChapterIsClampedNotRejected() = runTest {
        val vm = viewModel()
        vm.selectBook(65)
        vm.selectVerse(99)
        assertEquals(VerseRef(65, 1, 25), vm.uiState.value.selection)
    }

    @Test
    fun verseCountFollowsTheReadersTranslation() = runTest {
        val prefs = ReaderPreferences(dataStore)
        val vm = viewModel()
        vm.selectBook(64)
        vm.selectVerse(15)
        assertEquals(VerseRef(64, 1, 15), vm.uiState.value.selection) // WEB has 3 John 1:15

        prefs.setTranslationId("KJV")
        assertEquals(14, vm.uiState.value.verseCount)
        assertEquals(VerseRef(64, 1, 14), vm.uiState.value.selection)
    }

    @Test
    fun startsAtTheLastReadingPlace() = runTest {
        userData.saveReadingPosition("WEB", VerseRef(65, 1, 3))
        val vm = viewModel()
        assertEquals(VerseRef(65, 1, 3), vm.uiState.value.selection)
    }

    // ---- Jump ----

    @Test
    fun turningTheWheelsNeverMovesTheFeedOnlyReadDoes() = runTest {
        val vm = viewModel()
        vm.selectBook(65)
        vm.selectVerse(20)
        assertNull(link.ref.value)

        vm.read()
        assertEquals(VerseRef(65, 1, 20), link.ref.value)
    }

    @Test
    fun readUsesTheClampedSelection() = runTest {
        val vm = viewModel()
        vm.selectBook(31)
        vm.selectVerse(40)
        vm.read()
        assertEquals(VerseRef(31, 1, 21), link.ref.value)
    }
}
