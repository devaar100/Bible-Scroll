package aarnav100.developer.biblescroll.feature.bible

import aarnav100.developer.biblescroll.data.bible.model.Verse
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.FakeUserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerseNoteViewModelTest {

    private val userData = LocalUserDataRepository(FakeUserDao()) { 1L }
    private val john316 = Verse("WEB", VerseRef(43, 3, 16), 26_137, "For God so loved the world…", emptyList())

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val VerseNoteViewModel.open get() = uiState.value as VerseNoteUiState.Open

    @Test
    fun openLoadsTheSavedNote() = runTest {
        userData.saveNote(john316.ref, "saved")
        val vm = VerseNoteViewModel(userData)

        vm.open(john316)
        assertNull(vm.open.initialText) // loading
        advanceUntilIdle()

        assertEquals("saved", vm.open.initialText)
        assertTrue(vm.open.hasSavedNote)
    }

    @Test
    fun typingSavesOnceAfterTheDebounce() = runTest {
        val vm = VerseNoteViewModel(userData)
        vm.open(john316)
        advanceUntilIdle()

        vm.onTextChange("G")
        advanceTimeBy(300)
        vm.onTextChange("Go")
        advanceTimeBy(300)
        vm.onTextChange("God")
        advanceTimeBy(VerseNoteViewModel.SAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertNull(userData.note(john316.ref))

        advanceTimeBy(2)
        runCurrent()
        assertEquals("God", userData.note(john316.ref))
    }

    @Test
    fun closingSavesImmediately() = runTest {
        val vm = VerseNoteViewModel(userData)
        vm.open(john316)
        advanceUntilIdle()

        vm.onTextChange("quick note")
        vm.close()
        runCurrent()

        assertEquals("quick note", userData.note(john316.ref))
        assertEquals(VerseNoteUiState.Closed, vm.uiState.value)
    }

    @Test
    fun clearingTheTextDeletesTheNote() = runTest {
        userData.saveNote(john316.ref, "old")
        val vm = VerseNoteViewModel(userData)
        vm.open(john316)
        advanceUntilIdle()

        vm.onTextChange("")
        advanceUntilIdle()

        assertNull(userData.note(john316.ref))
        assertFalse(vm.open.hasSavedNote)
    }

    @Test
    fun deleteDropsPendingEditsAndRemovesTheNote() = runTest {
        userData.saveNote(john316.ref, "old")
        val vm = VerseNoteViewModel(userData)
        vm.open(john316)
        advanceUntilIdle()

        vm.onTextChange("edited")
        vm.delete()
        advanceUntilIdle()

        assertNull(userData.note(john316.ref))
        assertEquals(VerseNoteUiState.Closed, vm.uiState.value)
    }
}
