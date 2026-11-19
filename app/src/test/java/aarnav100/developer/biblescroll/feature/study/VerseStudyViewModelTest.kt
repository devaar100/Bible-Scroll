package aarnav100.developer.biblescroll.feature.study

import aarnav100.developer.biblescroll.core.content.BookCodes
import aarnav100.developer.biblescroll.core.content.BookDownloader
import aarnav100.developer.biblescroll.core.content.BookImporter
import aarnav100.developer.biblescroll.core.content.BookSource
import aarnav100.developer.biblescroll.core.content.StoredBook
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** B8 Verse study over the feed: every study state, studied/saved marks, Hide AI, and that the feed never moves. */
@OptIn(ExperimentalCoroutinesApi::class)
class VerseStudyViewModelTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val content = FakeContent()
    private val dataStore = FakePreferencesDataStore()
    private val settings = SettingsRepository(dataStore)
    private var now = 1L
    private val userDao = FakeUserDao()
    private val userData = LocalUserDataRepository(userDao) { now }
    private val link = PendingVerseLink()
    private val john316 = VerseRef(43, 3, 16)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): VerseStudyViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = BookDownloader(content.api, content.store, BookImporter(content.store, ContentJson), temp.newFolder(),
            CoroutineScope(backgroundScope.coroutineContext + dispatcher), dispatcher, "https://cdn.test/")
        val studies = StudyRepository(content.store, downloader, content.network, ContentJson, dispatcher)
        val factory = StudySession.Factory(studies, userData, settings, FakeBibleRepository(), ReaderPreferences(dataStore))
        return VerseStudyViewModel(factory, link).also { vm -> backgroundScope.launch(dispatcher) { vm.uiState.collect {} } }
    }

    private fun VerseStudyViewModel.study() = uiState.value?.study

    // ---- States ----

    @Test
    fun notDownloadedBookDownloadsOnDemandThenShowsTheStudy() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        assertNull(vm.uiState.value) // no sheet until opened

        vm.open(john316)
        advanceUntilIdle()

        assertTrue(vm.study() is StudyState.Available)
        assertEquals(listOf("https://cdn.test/studies/"), content.api.downloads.map { it.substringBefore("v") })
        assertEquals(listOf("John 14:6"), vm.uiState.value!!.crossReferences.map { it.label })
    }

    @Test
    fun alreadyStoredStudyOpensWithoutDownloading() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        viewModel().apply { open(john316); advanceUntilIdle(); close() }
        content.api.downloads.clear()

        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        assertTrue(vm.study() is StudyState.Available)
        assertTrue(content.api.downloads.isEmpty())
    }

    @Test
    fun offlineShowsNotAvailableOfflineAndRetryWorksOnceOnline() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        content.network.onlineState.value = false
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        assertEquals(StudyState.NotDownloaded(43, StudyState.NotDownloaded.Reason.OFFLINE), vm.study())

        content.network.onlineState.value = true
        vm.retry()
        advanceUntilIdle()
        assertTrue(vm.study() is StudyState.Available)
    }

    @Test
    fun aFileThatFailsTheHashIsADownloadFailureWithRetry() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val url = content.api.files.keys.first { it.endsWith("JHN.json.gz") }
        content.api.files[url] = content.api.files.getValue(url) + byteArrayOf(0)
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        assertEquals(StudyState.NotDownloaded(43, StudyState.NotDownloaded.Reason.DOWNLOAD_FAILED), vm.study())
    }

    @Test
    fun verseWithoutAStudyIsUnavailable() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.17")) // the book is published, John 3:16 isn't in it
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        assertEquals(StudyState.Unavailable(StudyState.Unavailable.Reason.NO_STUDY), vm.study())

        content.publish("PSA", 19, sampleStudy("PSA.23.1"))
        vm.open(VerseRef(1, 1, 1)) // Genesis isn't published at all
        advanceUntilIdle()
        assertEquals(StudyState.Unavailable(StudyState.Unavailable.Reason.NO_STUDY), vm.study())
    }

    // ---- Marks ----

    @Test
    fun openingMarksStudiedOncePerVerse() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        now = 100
        vm.open(john316)
        advanceUntilIdle()
        assertTrue(userData.studyMarks(john316).first().studied)
        val first = userDao.getMark(43, 3, 16)!!

        now = 200
        vm.close()
        vm.open(john316)
        advanceUntilIdle()
        // Not rewritten on reopen: same studied date, same updated_at (H5 order and the backup merge stay stable).
        assertEquals(first, userDao.getMark(43, 3, 16))
    }

    @Test
    fun aStudyThatCantLoadIsNotMarkedStudied() = runTest {
        content.network.onlineState.value = false
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        assertFalse(userData.studyMarks(john316).first().studied)
    }

    @Test
    fun saveStudyTogglesAndShowsInSavedStudies() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()

        vm.toggleSaved()
        advanceUntilIdle()
        assertTrue(vm.uiState.value!!.marks.saved)
        assertTrue(userDao.getMark(43, 3, 16)!!.studySaved) // H4 lists marks with study_saved

        vm.toggleSaved()
        advanceUntilIdle()
        assertFalse(vm.uiState.value!!.marks.saved)
    }

    // ---- Hide AI studies ----

    @Test
    fun withHideAiStudiesOnB8StillLoadsTheDataSectionsAndCanTurnItOff() = runTest {
        settings.setHideAiStudies(true)
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()

        val state = vm.uiState.value!!
        assertTrue(state.hidden) // StudyBody(showProse = false): original language, cross refs, sources only
        assertTrue(state.study is StudyState.Available)
        assertEquals(listOf("John 14:6"), state.crossReferences.map { it.label })

        vm.showStudies()
        advanceUntilIdle()
        assertFalse(vm.uiState.value!!.hidden)
        assertFalse(settings.settings.first().hideAiStudies)
    }

    // ---- The feed's position ----

    @Test
    fun openingAndClosingNeverMovesTheFeed() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        vm.close()
        advanceUntilIdle()

        assertNull(link.ref.value) // nothing asked the feed to move
        assertNull(vm.uiState.value)
        assertFalse(vm.isOpen)
    }

    @Test
    fun goToVerseMovesTheFeedToTheReferenceAndCloses() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"))
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()

        vm.goToVerse(VerseRef(43, 14, 6))
        assertEquals(VerseRef(43, 14, 6), link.ref.value)
        assertFalse(vm.isOpen)
    }

    @Test
    fun openingAnotherVerseReplacesTheSession() = runTest {
        content.publish("JHN", 43, sampleStudy("JHN.3.16"), sampleStudy("JHN.3.17"))
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        vm.open(VerseRef(43, 3, 17))
        advanceUntilIdle()
        assertEquals(VerseRef(43, 3, 17), vm.uiState.value!!.ref)
        assertTrue(vm.study() is StudyState.Available)
    }

    @Test
    fun aBundledOrDebugImportedBookCountsAsStored() = runTest {
        // Same path the debug import uses: a stored book with the verse in it.
        val study = sampleStudy("JHN.3.16")
        content.store.replaceBook(
            StoredBook(43, BookCodes.code(43), "debug-1", "x", BookSource.DOWNLOADED, 1, 0, 1),
            listOf(aarnav100.developer.biblescroll.core.content.VerseKey(43, 3, 16) to
                aarnav100.developer.biblescroll.core.content.StudyRow(study.contentVersion, ContentJson.encodeToString(
                    aarnav100.developer.biblescroll.core.content.model.Study.serializer(), study))),
        )
        val vm = viewModel()
        vm.open(john316)
        advanceUntilIdle()
        assertTrue(vm.study() is StudyState.Available)
        assertTrue(content.api.downloads.isEmpty())
    }
}
