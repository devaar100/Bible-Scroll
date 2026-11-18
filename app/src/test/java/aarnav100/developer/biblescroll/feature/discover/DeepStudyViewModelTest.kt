package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.BookDownloader
import aarnav100.developer.biblescroll.core.content.BookImporter
import aarnav100.developer.biblescroll.core.content.StudyRepository
import aarnav100.developer.biblescroll.core.content.StudyState
import aarnav100.developer.biblescroll.data.bible.model.VerseRef
import aarnav100.developer.biblescroll.data.settings.ReaderPreferences
import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.user.LocalUserDataRepository
import aarnav100.developer.biblescroll.testing.ContentJson
import aarnav100.developer.biblescroll.testing.FakeBibleRepository
import aarnav100.developer.biblescroll.testing.FakeContent
import aarnav100.developer.biblescroll.testing.FakePreferencesDataStore
import aarnav100.developer.biblescroll.testing.FakeUserDao
import aarnav100.developer.biblescroll.testing.sampleStudy
import aarnav100.developer.biblescroll.ui.navigation.PendingVerseLink
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DeepStudyViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val content = FakeContent()
    private val dataStore = FakePreferencesDataStore()
    private val settings = SettingsRepository(dataStore)
    private val userData = LocalUserDataRepository(FakeUserDao()) { 1L }
    private val link = PendingVerseLink()
    private val ref = VerseRef(43, 3, 16)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): DeepStudyViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = BookDownloader(content.api, content.store, BookImporter(content.store, ContentJson), temp.newFolder(),
            CoroutineScope(backgroundScope.coroutineContext + dispatcher), dispatcher, "https://cdn.test/")
        val studies = StudyRepository(content.store, downloader, content.network, ContentJson, dispatcher)
        return DeepStudyViewModel(
            SavedStateHandle(mapOf("bookId" to 43, "chapter" to 3, "verse" to 16)),
            studies, userData, settings, FakeBibleRepository(), ReaderPreferences(dataStore), link,
        ).also { vm -> backgroundScope.launch(dispatcher) { vm.uiState.collect {} } }
    }

    @Test
    fun openingDownloadsShowsTheStudyAndMarksItStudied() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(state.study is StudyState.Available)
        assertEquals("WEB VerseRef(bookId=43, chapter=3, verse=16)", state.verseText)
        assertEquals(listOf("John 14:6"), state.crossReferences.map { it.label })
        assertTrue(userData.studyMarks(ref).first().studied)
    }

    @Test
    fun aStudyThatCantLoadIsNotMarkedStudied() = runTest {
        content.network.onlineState.value = false
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.study is StudyState.NotDownloaded)
        assertFalse(userData.studyMarks(ref).first().studied)
    }

    @Test
    fun hiddenStudiesShowTheExplanationAndDownloadNothing() = runTest {
        settings.setHideAiStudies(true)
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hidden)
        assertTrue(content.api.downloads.isEmpty())
        assertFalse(userData.studyMarks(ref).first().studied)
        vm.showStudies()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hidden)
        assertTrue(vm.uiState.value.study is StudyState.Available)
    }

    @Test
    fun saveTogglesAndReadInContextOpensTheVerse() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleSaved()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.marks.saved)
        vm.toggleSaved()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.marks.saved)
        vm.readInContext()
        assertEquals(ref, link.ref.value)
    }
}
