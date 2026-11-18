package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.net.ContentApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** The real Retrofit/OkHttp stack against a local server. */
@OptIn(ExperimentalCoroutinesApi::class)
class BookDownloaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private val store = FakeContentStore()
    private lateinit var api: ContentApi

    @Before
    fun start() {
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(TestJson.asConverterFactory("application/json".toMediaType()))
            .build().create(ContentApi::class.java)
    }

    @After
    fun stop() = server.close()

    private fun respond(body: ByteArray, code: Int = 200, type: String = "application/gzip") =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", type).body(Buffer().write(body)).build())

    private fun downloader(scope: kotlinx.coroutines.CoroutineScope, dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        BookDownloader(api, store, BookImporter(store, TestJson), temp.newFolder(), scope, dispatcher, server.url("/").toString())

    @Test
    fun verifiesAndImportsARealDownload() = runTest {
        val (gz, entry) = bookFile("PSA", 19, study("PSA.23.1"), study("PSA.23.2", reviewed = true))
        respond(gz)
        val progress = mutableListOf<Long>()
        val d = downloader(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        assertNull(d.download(entry.copy(url = "studies/v1/PSA.json.gz")) { progress += it })
        assertEquals("/studies/v1/PSA.json.gz", server.takeRequest().target)
        assertEquals(gz.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress) // monotonic, ending at the full size
        val stored = store.books().single()
        assertEquals(listOf(19, 2, 1, BookSource.DOWNLOADED), listOf(stored.bookId, stored.verseCount, stored.reviewedCount, stored.source))
        assertTrue(store.studies.value.keys.containsAll(listOf(VerseKey(19, 23, 1), VerseKey(19, 23, 2))))
    }

    @Test
    fun wrongBytesFromTheServerAreRejected() = runTest {
        val (gz, entry) = bookFile("PSA", 19, study("PSA.23.1"))
        respond(gz + byteArrayOf(0)) // one extra byte
        val d = downloader(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        assertEquals(DownloadFailure.INTEGRITY, d.download(entry.copy(url = "PSA.json.gz")))
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun aFileThatMatchesItsHashButIsTheWrongBookIsRejected() = runTest {
        val (gz, entry) = bookFile("PSA", 19, study("PSA.23.1"))
        respond(gz)
        val d = downloader(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        assertEquals(DownloadFailure.INTEGRITY, d.download(entry.copy(book = "JHN", bookId = 43, url = "x.gz")))
        assertEquals(0, store.replaceCalls)
    }

    @Test
    fun serverErrorIsANetworkFailure() = runTest {
        val (_, entry) = bookFile("PSA", 19, study("PSA.23.1"))
        respond(ByteArray(0), code = 503)
        val d = downloader(backgroundScope, UnconfinedTestDispatcher(testScheduler))
        assertEquals(DownloadFailure.NETWORK, d.download(entry.copy(url = "PSA.json.gz")))
    }

    @Test
    fun weeklyRefreshUpdatesOnlyChangedBooksOnTheDevice() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val d = downloader(backgroundScope, dispatcher)
        val old = bookFile("PSA", 19, study("PSA.23.1"))
        respond(old.first)
        d.download(old.second.copy(url = "old.gz"))
        val new = bookFile("PSA", 19, study("PSA.23.1"), study("PSA.23.2"))
        val untouched = bookFile("GEN", 1, study("GEN.1.1")) // in the manifest but never downloaded
        val manifestJson = TestJson.encodeToString(
            aarnav100.developer.biblescroll.core.content.model.Manifest.serializer(),
            manifest(new.second.copy(url = "new.gz"), untouched.second),
        )
        respond(manifestJson.toByteArray(), type = "application/json")
        respond(new.first)
        assertNull(d.refreshStoredBooks())
        assertEquals(listOf(19), store.books().map { it.bookId })
        assertEquals(2, store.books().single().verseCount)
        server.takeRequest(); server.takeRequest()
        assertEquals("/new.gz", server.takeRequest().target)
        assertEquals(3, server.requestCount) // old + manifest + new; GEN not fetched
    }
}
