package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.Donation
import aarnav100.developer.biblescroll.core.content.model.HistoryEntry
import aarnav100.developer.biblescroll.core.content.model.HistoryFile
import aarnav100.developer.biblescroll.core.content.model.Manifest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityHistoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val api = FakeContentApi()
    private var now = Instant.parse("2026-12-03T10:00:00Z")

    private fun donation(month: String, amount: String = "300", currency: String = "USD") =
        Donation(month, "river", "River", JsonPrimitive(amount.toBigDecimal()), currency)

    // --- the monthly fetch rule ---

    @Test
    fun neverFetchedFetches() {
        assertTrue(HistoryRules.shouldFetch(null, emptySet(), now))
    }

    @Test
    fun atMostOncePerCalendarMonthWhenLastMonthIsThere() {
        val earlier = Instant.parse("2026-12-01T00:30:00Z")
        assertFalse(HistoryRules.shouldFetch(earlier, setOf("2026-11"), now))
        assertFalse(HistoryRules.shouldFetch(earlier, setOf("2026-11"), Instant.parse("2026-12-31T23:59:59Z")))
        assertTrue(HistoryRules.shouldFetch(earlier, setOf("2026-11"), Instant.parse("2027-01-01T00:00:00Z")))
    }

    @Test
    fun whileLastMonthIsMissingRecheckAtMostDaily() {
        val earlier = Instant.parse("2026-12-03T00:00:00Z")
        assertFalse(HistoryRules.shouldFetch(earlier, setOf("2026-10"), now)) // 10 hours later
        assertTrue(HistoryRules.shouldFetch(earlier, setOf("2026-10"), Instant.parse("2026-12-04T00:00:00Z")))
    }

    @Test
    fun aClockThatWentBackFetches() {
        assertTrue(HistoryRules.shouldFetch(Instant.parse("2027-02-01T00:00:00Z"), setOf("2026-11"), now))
    }

    // --- finished months ---

    @Test
    fun onlyFinishedMonthsNewestFirst() {
        val all = listOf(donation("2026-10"), donation("2026-12"), donation("2026-11"), donation("2027-01"), donation("bad"))
        assertEquals(listOf("2026-11", "2026-10"), HistoryRules.finished(all, now).map { it.month })
    }

    @Test
    fun theCurrentMonthNeverAppearsEvenOnItsLastSecond() {
        val atEnd = Instant.parse("2026-11-30T23:59:59Z")
        assertEquals(emptyList<Donation>(), HistoryRules.finished(listOf(donation("2026-11")), atEnd))
    }

    @Test
    fun amountsAreKeptExactly() {
        assertEquals("312.50".toBigDecimal(), donation("2026-11", "312.50").amountValue)
        assertNull(Donation("2026-11", "r", "R", JsonPrimitive("300"), "USD").amountValue) // a string isn't an amount
        assertNull(donation("2026-11", "0").amountValue)
    }

    // --- repository ---

    private fun TestScope.repository() = CommunityHistoryRepository(
        api, ContentCache(temp.root), TestJson, "https://cdn.test/", UnconfinedTestDispatcher(testScheduler), clock = { now },
    )

    private fun publish(vararg donations: Donation) {
        val bytes = TestJson.encodeToString(HistoryFile.serializer(), HistoryFile(1, donations.toList())).toByteArray()
        val m: Manifest = manifest().copy(history = HistoryEntry("https://cdn.test/history.json", donations.size, Sha256.hex(bytes), bytes.size.toLong()))
        api.files["https://cdn.test/manifest.json"] = TestJson.encodeToString(Manifest.serializer(), m).toByteArray()
        api.files["https://cdn.test/history.json"] = bytes
    }

    @Test
    fun fetchesCachesAndShowsOfflineWithoutRefetching() = runTest {
        publish(donation("2026-11"), donation("2026-12"))
        val repo = repository()
        assertEquals(true, repo.refreshIfDue())
        assertEquals(HistoryState.Ready(listOf(donation("2026-11"))), repo.history.first())

        // Later the same month, offline, after a restart: the cached copy, and no request at all.
        api.downloads.clear()
        api.downloadError = IOException("offline")
        val later = repository()
        assertNull(later.refreshIfDue())
        assertTrue(api.downloads.isEmpty())
        assertEquals(HistoryState.Ready(listOf(donation("2026-11"))), later.history.first())
    }

    @Test
    fun offlineWithNothingCachedIsUnavailable() = runTest {
        api.downloadError = IOException("offline")
        val repo = repository()
        assertEquals(false, repo.refreshIfDue())
        assertEquals(HistoryState.Unavailable(offline = true), repo.history.first())
    }

    @Test
    fun aTamperedFileIsNotUsed() = runTest {
        publish(donation("2026-11"))
        api.files["https://cdn.test/history.json"] = api.files["https://cdn.test/history.json"]!! + " ".toByteArray()
        val repo = repository()
        assertEquals(false, repo.refreshIfDue())
        assertEquals(HistoryState.Unavailable(offline = false), repo.history.first())
        assertNull(ContentCache(temp.root).read(ContentCache.HISTORY))
    }

    @Test
    fun nothingPublishedIsAnEmptyHistory() = runTest {
        api.files["https://cdn.test/manifest.json"] = TestJson.encodeToString(Manifest.serializer(), manifest()).toByteArray()
        val repo = repository()
        assertEquals(true, repo.refreshIfDue())
        assertEquals(HistoryState.Ready(emptyList()), repo.history.first())
    }

    // --- ministry image hosts ---

    @Test
    fun imagesOnlyFromOurFirebaseHosts() {
        val images = MinistryImages(OkHttpClient(), temp.newFolder(), "https://biblescroll-content.web.app/", "biblescroll-content",
            UnconfinedTestDispatcher())
        assertTrue(images.isAllowed("https://biblescroll-content.web.app/images/river.jpg"))
        assertTrue(images.isAllowed("https://firebasestorage.googleapis.com/v0/b/biblescroll-content.firebasestorage.app/o/m%2Fr.jpg?alt=media"))
        assertFalse(images.isAllowed("https://firebasestorage.googleapis.com/v0/b/someone-else.appspot.com/o/r.jpg"))
        assertFalse(images.isAllowed("http://biblescroll-content.web.app/r.jpg"))
        assertFalse(images.isAllowed("https://example.org/r.jpg"))
        assertFalse(images.isAllowed("https://biblescroll-content.web.app:8443/r.jpg"))
    }
}
