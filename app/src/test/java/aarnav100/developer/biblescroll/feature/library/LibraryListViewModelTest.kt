package aarnav100.developer.biblescroll.feature.library

import aarnav100.developer.biblescroll.data.backup.BackupNotices
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.library.LibraryRepository
import aarnav100.developer.biblescroll.data.library.LibrarySort
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryListViewModelTest {
    private var now = 0L
    private val dao = FakeUserDao()
    private val userData = LocalUserDataRepository(dao) { now }
    private val link = PendingVerseLink()
    private val john316 = VerseRef(43, 3, 16)
    private val gen11 = VerseRef(1, 1, 1)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val notices = BackupNotices(FakePreferencesDataStore())

    private fun TestScope.viewModel(screen: LibraryScreen, saved: SavedStateHandle = SavedStateHandle(mapOf("screen" to screen.name))) =
        LibraryListViewModel(saved, LibraryRepository(dao) { now }, FakeBibleRepository(),
            ReaderPreferences(FakePreferencesDataStore()), link, notices)
            .also { vm -> backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} } }

    @Test
    fun rowsShowReferenceAndVerseTextAndUpdateLive() = runTest {
        now = 1; userData.setLiked(john316, true)
        val vm = viewModel(LibraryScreen.LIKED_AND_BOOKMARKED)
        advanceUntilIdle()
        val row = vm.uiState.value.rows.single()
        assertEquals("John 3:16", row.reference)
        assertEquals("WEB VerseRef(bookId=43, chapter=3, verse=16)", row.verseText)
        now = 2; userData.setLiked(gen11, true) // liked elsewhere (e.g. the feed): the list updates by itself
        advanceUntilIdle()
        assertEquals(listOf(gen11, john316), vm.uiState.value.rows.map { it.ref })
    }

    @Test
    fun filterChipSwitchesBetweenLikedAndBookmarked() = runTest {
        userData.setLiked(john316, true)
        userData.setBookmarked(gen11, true)
        val vm = viewModel(LibraryScreen.LIKED_AND_BOOKMARKED)
        advanceUntilIdle()
        assertEquals(listOf(john316), vm.uiState.value.rows.map { it.ref })
        vm.setFilter(MarkFilter.BOOKMARKED)
        advanceUntilIdle()
        assertEquals(listOf(gen11), vm.uiState.value.rows.map { it.ref })
    }

    @Test
    fun sortAndSearchSurviveRecreation() = runTest {
        now = 1; userData.saveNote(gen11, "creation")
        now = 2; userData.saveNote(john316, "love")
        val saved = SavedStateHandle(mapOf("screen" to LibraryScreen.NOTES.name))
        val vm = viewModel(LibraryScreen.NOTES, saved)
        vm.setSort(LibrarySort.RECENT)
        advanceUntilIdle()
        assertEquals(listOf(john316, gen11), vm.uiState.value.rows.map { it.ref })
        vm.setQuery("creat")
        advanceUntilIdle()
        assertEquals("creation", vm.uiState.value.rows.single().note)
        val again = viewModel(LibraryScreen.NOTES, saved)
        advanceUntilIdle()
        assertEquals(LibrarySort.RECENT to "creat", again.uiState.value.sort to again.uiState.value.query)
    }

    @Test
    fun studiesOpenTheStudyOthersOpenTheFeed() = runTest {
        val studies = viewModel(LibraryScreen.SAVED_STUDIES)
        assertEquals(LibraryOpen.Study(john316), studies.open(john316))
        assertNull(link.ref.value)
        val notes = viewModel(LibraryScreen.NOTES)
        assertEquals(LibraryOpen.Verse(john316), notes.open(john316))
        assertEquals(john316, link.ref.value)
    }

    @Test
    fun swipeRemoveThenUndo() = runTest {
        userData.setStudySaved(john316, true)
        val vm = viewModel(LibraryScreen.SAVED_STUDIES)
        advanceUntilIdle()
        val removal = vm.remove(john316)!!
        advanceUntilIdle()
        assertEquals(emptyList<VerseRef>(), vm.uiState.value.rows.map { it.ref })
        vm.undo(removal)
        advanceUntilIdle()
        assertEquals(listOf(john316), vm.uiState.value.rows.map { it.ref })
    }

    @Test
    fun notesTipShowsFromFiveNotesUntilDismissedForGood() = runTest {
        val vm = viewModel(LibraryScreen.NOTES)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showNotesTip.collect {} }
        repeat(4) { userData.saveNote(VerseRef(1, 1, it + 1), "note $it") }
        assertEquals(false, vm.showNotesTip.value)
        userData.saveNote(VerseRef(1, 1, 5), "fifth")
        assertEquals(true, vm.showNotesTip.value)
        vm.dismissNotesTip()
        assertEquals(false, vm.showNotesTip.value)
        assertEquals(false, viewModel(LibraryScreen.NOTES).also { again ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { again.showNotesTip.collect {} }
        }.showNotesTip.value)
    }

    @Test
    fun notesTipNeverShowsOnOtherLists() = runTest {
        repeat(6) { userData.saveNote(VerseRef(1, 1, it + 1), "note $it") }
        val vm = viewModel(LibraryScreen.LIKED_AND_BOOKMARKED)
        assertEquals(false, vm.showNotesTip.value)
    }
}
