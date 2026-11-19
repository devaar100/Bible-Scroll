package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.BundledDevotionalManifest
import aarnav100.developer.biblescroll.core.content.model.Devotional
import aarnav100.developer.biblescroll.core.content.model.DevotionalMonthFile
import aarnav100.developer.biblescroll.core.content.model.DevotionalPassage
import aarnav100.developer.biblescroll.core.content.model.DevotionalSeries
import aarnav100.developer.biblescroll.core.content.model.ManifestDevotionals
import aarnav100.developer.biblescroll.core.content.model.ManifestMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class FakeDevotionalStore : DevotionalStore {
    val months = MutableStateFlow<Map<String, StoredMonth>>(emptyMap())
    val rows = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap()) // date -> (month, json)
    override fun observeJson(date: String): Flow<String?> = rows.map { it[date]?.second }
    override fun observeMonthJson(month: String): Flow<List<String>> = rows.map { r -> r.filterValues { it.first == month }.toSortedMap().values.map { it.second } }
    override fun observeMonth(month: String): Flow<StoredMonth?> = months.map { it[month] }
    override suspend fun months() = months.value.values.sortedBy { it.month }
    override suspend fun replaceMonth(month: StoredMonth, devotionals: List<Triple<String, String, String>>) {
        rows.value = rows.value.filterValues { it.first != month.month } + devotionals.associate { (d, _, j) -> d to (month.month to j) }
        months.value = months.value + (month.month to month)
    }
    override suspend fun removeMonth(month: String) {
        rows.value = rows.value.filterValues { it.first != month }
        months.value = months.value - month
    }
}

fun devotional(date: String, title: String = "Title $date") = Devotional(
    id = "devotional-$date", contentVersion = "abcabcabcabc", date = date, dayOfYear = 1, title = title, theme = "Theme",
    series = DevotionalSeries("Series", "Advent", 1, 7), coverStyle = "dawn",
    verses = listOf(DevotionalPassage("JHN", 3, 16, 3, 17)), context = "c", reflect = "r", applyIt = "a", prayer = null,
)

/** A gzipped month file and its manifest entry. */
fun monthFile(month: String, vararg days: Devotional, formatVersion: Int = 1, schemaVersion: Int = 1): Pair<ByteArray, ManifestMonth> {
    val raw = TestJson.encodeToString(DevotionalMonthFile.serializer(), DevotionalMonthFile(formatVersion, schemaVersion, month, days.toList())).toByteArray()
    val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
    val sha = Sha256.hex(gz)
    return gz to ManifestMonth(month, "https://cdn.test/devotionals/v${sha.take(12)}/$month.json.gz", sha.take(12), days.size, 0, sha, gz.size.toLong())
}

@OptIn(ExperimentalCoroutinesApi::class)
class DevotionalContentTest {
    @get:Rule val temp = TemporaryFolder()

    private val store = FakeDevotionalStore()
    private val api = FakeContentApi()
    private val importer = DevotionalImporter(store, TestJson) { 1L }

    private fun TestScope.downloader() = DevotionalDownloader(
        api, store, importer, temp.newFolder(), CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        UnconfinedTestDispatcher(testScheduler), "https://cdn.test/",
    )

    private fun publish(vararg months: Pair<ByteArray, ManifestMonth>) {
        api.manifest = manifest().copy(devotionals = ManifestDevotionals(months.map { it.second }))
        months.forEach { (bytes, entry) -> api.files[entry.url] = bytes }
    }

    @Test
    fun importStoresEveryDayOfTheMonth() = runTest {
        val (bytes, entry) = monthFile("02", devotional("02-28"), devotional("02-29"))
        importer.import(bytes.inputStream(), entry, BookSource.BUNDLED)
        assertEquals(setOf("02-28", "02-29"), store.rows.value.keys)
        assertEquals(2, store.months.value.getValue("02").count)
    }

    @Test
    fun importRejectsDaysFromAnotherMonthAndUnknownFormats() = runTest {
        val (wrong, wrongEntry) = monthFile("03", devotional("02-28"))
        assertThrows { importer.import(wrong.inputStream(), wrongEntry, BookSource.DOWNLOADED) }
        val (future, futureEntry) = monthFile("03", devotional("03-01"), schemaVersion = 2)
        assertThrows { importer.import(future.inputStream(), futureEntry, BookSource.DOWNLOADED) }
        assertThrows { importer.import("nope".byteInputStream(), futureEntry, BookSource.DOWNLOADED) }
        assertTrue(store.rows.value.isEmpty())
    }

    @Test
    fun openingADayDownloadsItsMonthThenShowsIt() = runTest {
        publish(monthFile("11", devotional("11-18", "Waiting well")))
        val repo = DevotionalRepository(store, downloader(), FakeNetwork(true), TestJson, UnconfinedTestDispatcher(testScheduler))
        val states = mutableListOf<DevotionalState>()
        val job = collectInto(repo.devotional("11-18"), states)
        advanceUntilIdle()
        assertEquals("Waiting well", (states.last() as DevotionalState.Available).devotional.title)
        job.cancel()
    }

    @Test
    fun aTamperedFileIsRejectedAndShownAsAFailedDownload() = runTest {
        val (bytes, entry) = monthFile("11", devotional("11-18"))
        publish(bytes to entry)
        api.files[entry.url] = bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0xFF).toByte() }
        val repo = DevotionalRepository(store, downloader(), FakeNetwork(true), TestJson, UnconfinedTestDispatcher(testScheduler))
        val states = mutableListOf<DevotionalState>()
        collectInto(repo.devotional("11-18"), states)
        advanceUntilIdle()
        assertEquals(DevotionalState.NotDownloaded("11", StudyState.NotDownloaded.Reason.DOWNLOAD_FAILED), states.last())
        assertTrue(store.rows.value.isEmpty())
    }

    @Test
    fun offlineShowsNotAvailableOfflineWithoutTrying() = runTest {
        val repo = DevotionalRepository(store, downloader(), FakeNetwork(false), TestJson, UnconfinedTestDispatcher(testScheduler))
        val states = mutableListOf<DevotionalMonthState>()
        collectInto(repo.month("05"), states)
        advanceUntilIdle()
        assertEquals(DevotionalMonthState.NotDownloaded(StudyState.NotDownloaded.Reason.OFFLINE), states.last())
        assertEquals(0, api.manifestCalls)
    }

    @Test
    fun aMonthNotInTheManifestIsNotPublishedAndAMissingDayInAStoredMonthToo() = runTest {
        publish(monthFile("11", devotional("11-18")))
        val repo = DevotionalRepository(store, downloader(), FakeNetwork(true), TestJson, UnconfinedTestDispatcher(testScheduler))
        val june = mutableListOf<DevotionalState>()
        val nov19 = mutableListOf<DevotionalState>()
        collectInto(repo.devotional("06-01"), june)
        collectInto(repo.devotional("11-19"), nov19)
        advanceUntilIdle()
        assertEquals(DevotionalState.Unavailable(DevotionalState.Unavailable.Reason.NOT_PUBLISHED), june.last())
        assertEquals(DevotionalState.Unavailable(DevotionalState.Unavailable.Reason.NOT_PUBLISHED), nov19.last())
    }

    @Test
    fun networkFailureCanBeRetried() = runTest {
        publish(monthFile("11", devotional("11-18")))
        api.downloadError = IOException("down")
        val downloader = downloader()
        val repo = DevotionalRepository(store, downloader, FakeNetwork(true), TestJson, UnconfinedTestDispatcher(testScheduler))
        val states = mutableListOf<DevotionalState>()
        collectInto(repo.devotional("11-18"), states)
        advanceUntilIdle()
        assertTrue(states.last() is DevotionalState.NotDownloaded)
        api.downloadError = null
        repo.retry("11")
        advanceUntilIdle()
        assertTrue(states.last() is DevotionalState.Available)
    }

    @Test
    fun bundledMonthsImportOnceAndAgainAfterAnUpdate() = runTest {
        val (bytes, entry) = monthFile("11", devotional("11-18"))
        val assets = mutableMapOf(
            "devotionals/11.json.gz" to bytes,
            "devotionals/manifest.json" to TestJson.encodeToString(BundledDevotionalManifest.serializer(),
                BundledDevotionalManifest(1, "r", listOf(entry.copy(url = "11.json.gz")))).toByteArray(),
        )
        val bundled = BundledDevotionals({ assets[it]?.inputStream() }, store, importer, TestJson, UnconfinedTestDispatcher(testScheduler))
        assertEquals(listOf("11"), bundled.importMissingOrUpdated().imported)
        assertEquals(emptyList<String>(), bundled.importMissingOrUpdated().imported)
        assertEquals(BookSource.BUNDLED, store.months.value.getValue("11").source)
        // A corrupted bundle is skipped, never half-imported.
        assets["devotionals/11.json.gz"] = bytes.copyOf(10)
        store.removeMonth("11")
        assertEquals(listOf("11"), bundled.importMissingOrUpdated().skipped)
    }

    @Test
    fun weeklyRefreshUpdatesChangedMonthsAndDropsUnlistedDownloads() = runTest {
        val (old, oldEntry) = monthFile("11", devotional("11-18", "Old"))
        importer.import(old.inputStream(), oldEntry, BookSource.DOWNLOADED)
        val (gone, goneEntry) = monthFile("10", devotional("10-01"))
        importer.import(gone.inputStream(), goneEntry, BookSource.DOWNLOADED)
        publish(monthFile("11", devotional("11-18", "New")))
        assertEquals(null, downloader().refreshStoredMonths())
        assertTrue(store.rows.value.getValue("11-18").second.contains("New"))
        assertTrue("10" !in store.months.value)
    }

    private fun <T> TestScope.collectInto(flow: Flow<T>, into: MutableList<T>) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { into += it } }

    private suspend fun assertThrows(block: suspend () -> Unit) {
        val failed = try { block(); false } catch (e: InvalidMonthException) { true }
        assertTrue("expected InvalidMonthException", failed)
    }
}
