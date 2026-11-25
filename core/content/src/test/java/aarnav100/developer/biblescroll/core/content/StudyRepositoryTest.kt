package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.StudyState.NotDownloaded
import aarnav100.developer.biblescroll.core.content.StudyState.Unavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StudyRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val store = FakeContentStore()
    private val api = FakeContentApi()
    private val john = bookFile("JHN", 43, study("JHN.3.16", reviewed = true), study("JHN.3.17"))

    private fun TestScope.setUp(online: Boolean): Triple<StudyRepository, FakeNetwork, BookDownloader> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        // Downloads run eagerly on the test scheduler but are cancelled with the test (TestScope.advanceUntilIdle
        // doesn't run work queued on backgroundScope's own dispatcher).
        val scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher)
        val downloader = BookDownloader(api, store, BookImporter(store, TestJson), temp.newFolder(), scope,
            dispatcher, "https://cdn.test/")
        val network = FakeNetwork(online)
        return Triple(StudyRepository(store, downloader, network, TestJson, dispatcher), network, downloader)
    }

    private fun TestScope.collect(repository: StudyRepository, chapter: Int = 3, verse: Int = 16): List<StudyState> {
        val states = mutableListOf<StudyState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.studyState(43, chapter, verse).collect { states += it }
        }
        return states
    }

    private fun serveJohn() {
        api.manifest = manifest(john.second)
        api.files[john.second.url] = john.first
    }

    @Test
    fun offlineAndNotDownloadedIsAClearStateNotAnError() = runTest {
        val (repository, _, _) = setUp(online = false)
        serveJohn()
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals(NotDownloaded(43, NotDownloaded.Reason.OFFLINE), states.last())
        assertEquals(0, api.manifestCalls) // didn't even try
    }

    @Test
    fun comingOnlineDownloadsWithProgressThenShowsTheStudy() = runTest {
        val (repository, network, downloader) = setUp(online = false)
        serveJohn()
        val states = collect(repository)
        val downloadStates = mutableListOf<DownloadState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { downloader.state(43).collect { downloadStates += it } }
        advanceUntilIdle()
        network.online.value = true
        advanceUntilIdle()
        val available = states.last() as StudyState.Available
        assertEquals("JHN.3.16", available.study.id)
        assertTrue(available.study.reviewed)
        // The UI sees Loading(progress) while downloading (intermediate values may be conflated)…
        assertTrue(states.any { it is StudyState.Loading && it.progress != null })
        // …while the downloader is Downloading. (StateFlow conflates the per-chunk byte counts here; the byte
        // progress itself is checked against a real HTTP stream in BookDownloaderTest.)
        assertTrue(downloadStates.any { it is DownloadState.Downloading })
        assertEquals(DownloadState.Done, downloadStates.last())
        assertEquals(1, api.downloads.size)
    }

    @Test
    fun storedStudyIsAvailableOffline() = runTest {
        val (repository, network, downloader) = setUp(online = true)
        serveJohn()
        downloader.download(john.second)
        network.online.value = false
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals("JHN.3.16", (states.last() as StudyState.Available).study.id)
    }

    @Test
    fun verseMissingFromADownloadedBookHasNoStudy() = runTest {
        val (repository, _, downloader) = setUp(online = true)
        serveJohn()
        downloader.download(john.second)
        val states = collect(repository, verse = 18) // flagged/unwritten verses aren't published
        advanceUntilIdle()
        assertEquals(Unavailable(Unavailable.Reason.NO_STUDY), states.last())
        assertEquals(1, api.downloads.size) // no second download attempt
    }

    @Test
    fun bookNotInManifestHasNoStudy() = runTest {
        val (repository, _, _) = setUp(online = true)
        api.manifest = manifest()
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals(Unavailable(Unavailable.Reason.NO_STUDY), states.last())
    }

    @Test
    fun corruptDownloadIsNeverImportedAndCanBeRetried() = runTest {
        val (repository, _, _) = setUp(online = true)
        serveJohn()
        api.files[john.second.url] = john.first.copyOf().also { it[20] = (it[20] + 1).toByte() }
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals(NotDownloaded(43, NotDownloaded.Reason.DOWNLOAD_FAILED), states.last())
        assertEquals(0, store.replaceCalls)
        assertEquals(emptyList<Any>(), temp.root.walk().filter { it.isFile }.toList()) // temp file cleaned up

        api.files[john.second.url] = john.first
        repository.retry(43)
        advanceUntilIdle()
        assertTrue(states.last() is StudyState.Available)
    }

    @Test
    fun networkFailureShowsFailedOnlineAndOfflineWhenDisconnected() = runTest {
        val (repository, network, _) = setUp(online = true)
        api.manifestError = IOException("timeout")
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals(NotDownloaded(43, NotDownloaded.Reason.DOWNLOAD_FAILED), states.last())
        network.online.value = false
        advanceUntilIdle()
        assertEquals(NotDownloaded(43, NotDownloaded.Reason.OFFLINE), states.last())
    }

    @Test
    fun failureWhileOfflineRetriesByItselfWhenBackOnline() = runTest {
        val (repository, network, _) = setUp(online = true)
        api.manifestError = IOException("no route")
        val states = collect(repository)
        advanceUntilIdle()
        network.online.value = false
        advanceUntilIdle()
        assertEquals(NotDownloaded(43, NotDownloaded.Reason.OFFLINE), states.last())

        // Connection back (and the server reachable): the study downloads without tapping Try again.
        api.manifestError = null
        serveJohn()
        network.online.value = true
        advanceUntilIdle()
        assertEquals("JHN.3.16", (states.last() as StudyState.Available).study.id)
        assertEquals(2, api.manifestCalls)
    }

    @Test
    fun reconnectRetriesOnlyOnceSoAFailingServerDoesNotLoop() = runTest {
        val (repository, network, _) = setUp(online = true)
        api.manifestError = IOException("server down")
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals(1, api.manifestCalls) // a failure while online isn't retried by itself
        network.online.value = false
        advanceUntilIdle()
        network.online.value = true
        advanceUntilIdle()
        assertEquals(NotDownloaded(43, NotDownloaded.Reason.DOWNLOAD_FAILED), states.last())
        assertEquals(2, api.manifestCalls) // one retry for the reconnect, then it stays failed
    }

    @Test
    fun newerContentFormatAsksForAnAppUpdate() = runTest {
        val (repository, _, _) = setUp(online = true)
        api.manifest = manifest(john.second, schemaVersion = 3)
        val states = collect(repository)
        advanceUntilIdle()
        assertEquals(Unavailable(Unavailable.Reason.APP_UPDATE_REQUIRED), states.last())
        assertEquals(0, api.downloads.size)
    }

    @Test
    fun twoVersesOfOneBookShareOneDownload() = runTest {
        val (repository, _, _) = setUp(online = true)
        serveJohn()
        val first = collect(repository, verse = 16)
        val second = collect(repository, verse = 17)
        advanceUntilIdle()
        assertTrue(first.last() is StudyState.Available && second.last() is StudyState.Available)
        assertEquals(1, api.downloads.size)
    }

    @Test
    fun firstStateIsLoading() = runTest {
        val (repository, _, _) = setUp(online = false)
        assertEquals(StudyState.Loading(), collect(repository).first())
    }
}
