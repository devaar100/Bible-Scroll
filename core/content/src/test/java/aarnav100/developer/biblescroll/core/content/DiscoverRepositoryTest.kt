package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.DiscoverEntry
import aarnav100.developer.biblescroll.core.content.model.DiscoverFile
import aarnav100.developer.biblescroll.core.content.model.Manifest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val api = FakeContentApi()
    private val store = FakeContentStore()
    private val john = bookFile("JHN", 43, study("JHN.3.16"))

    private fun publish(vararg ids: String, tamper: Boolean = false): Manifest {
        val discover = TestJson.encodeToString(DiscoverFile.serializer(), DiscoverFile(1, ids.toList())).toByteArray()
        val manifest = manifest(john.second).copy(
            discover = DiscoverEntry("https://cdn.test/discover.json", ids.size, Sha256.hex(discover), discover.size.toLong()),
        )
        api.files["https://cdn.test/manifest.json"] = TestJson.encodeToString(Manifest.serializer(), manifest).toByteArray()
        api.files["https://cdn.test/discover.json"] = if (tamper) discover + " ".toByteArray() else discover
        return manifest
    }

    private fun kotlinx.coroutines.test.TestScope.repository(cacheDir: java.io.File = temp.root) =
        DiscoverRepository(api, ContentCache(cacheDir), store, TestJson, "https://cdn.test/", UnconfinedTestDispatcher(testScheduler))

    @Test
    fun onlyVersesFromAvailableBooksInFeedOrder() = runTest {
        // JHN is downloadable (in the manifest); PSA is bundled on the device; GEN is neither.
        store.replaceBook(StoredBook(19, "PSA", "v", "s", BookSource.BUNDLED, 1, 0, 1), emptyList())
        publish("PSA.23.1", "GEN.1.1", "JHN.3.16", "NOT.A.REF")
        val repo = repository()
        assertTrue(repo.refresh())
        assertEquals(DiscoverFeed.Ready(listOf(VerseKey(19, 23, 1), VerseKey(43, 3, 16))), repo.feed.first())
    }

    @Test
    fun tamperedDiscoverFileIsRejectedAndNotCached() = runTest {
        publish("JHN.3.16", tamper = true)
        val repo = repository()
        assertFalse(repo.refresh())
        assertEquals(DiscoverFeed.NeverLoaded, repo.feed.first())
        assertEquals(null, ContentCache(temp.root).read(ContentCache.DISCOVER))
    }

    @Test
    fun cachedFeedWorksOffline() = runTest {
        publish("JHN.3.16")
        assertTrue(repository().refresh())
        api.files.clear()
        api.downloadError = IOException("offline")
        val later = repository() // e.g. after an app restart, with no connection
        assertFalse(later.refresh())
        assertEquals(DiscoverFeed.Ready(listOf(VerseKey(43, 3, 16))), later.feed.first())
    }

    @Test
    fun neverLoadedWhenOfflineWithNothingCached() = runTest {
        api.downloadError = IOException("offline")
        val repo = repository(temp.newFolder())
        assertFalse(repo.refresh())
        assertEquals(DiscoverFeed.NeverLoaded, repo.feed.first())
    }
}
