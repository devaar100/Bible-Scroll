package aarnav100.developer.biblescroll.core.content

import aarnav100.developer.biblescroll.core.content.model.CommunityEntry
import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.model.MinistriesFile
import aarnav100.developer.biblescroll.core.content.model.Ministry
import aarnav100.developer.biblescroll.core.content.net.FirestoreApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val root = "projects/demo/databases/(default)/documents"

    // ---- REST bodies and parsing ----

    @Test
    fun incrementBodyIsOneTransformOfExactlyOneOnAnExistingDoc() {
        val body = FirestoreJson.incrementBody(root, "2026-12_river")
        assertEquals(
            Json.parseToJsonElement(
                """{"writes":[{"transform":{"document":"$root/community_votes/2026-12_river",
                   "fieldTransforms":[{"fieldPath":"count","increment":{"integerValue":"1"}}]},
                   "currentDocument":{"exists":true}}]}""",
            ),
            body,
        )
    }

    @Test
    fun batchGetResponseParsesFoundAndSkipsMissing() {
        val text = """[
          {"found":{"name":"$root/community_votes/2026-12_river","fields":{"count":{"integerValue":"12"},
            "opensAt":{"timestampValue":"2026-12-01T00:00:00Z"},"closesAt":{"timestampValue":"2027-01-01T00:00:00Z"}},
            "createTime":"2026-11-30T10:00:00Z","updateTime":"2026-12-03T10:00:00Z"},"readTime":"2026-12-03T11:00:00Z"},
          {"missing":"$root/community_votes/2026-12_harbor","readTime":"2026-12-03T11:00:00Z"},
          {"found":{"name":"$root/community_votes/2026-12_hill","fields":{}},"readTime":"2026-12-03T11:00:00Z"},
          {"transaction":"abc"}
        ]"""
        assertEquals(mapOf("2026-12_river" to 12L, "2026-12_hill" to 0L), FirestoreJson.parseBatchGet(text))
    }

    @Test
    fun batchGetHandlesBigCountsAndRejectsNonArrays() {
        val big = """[{"found":{"name":"$root/community_votes/2026-12_river","fields":{"count":{"integerValue":"9007199254740993"}}}}]"""
        assertEquals(9_007_199_254_740_993L, FirestoreJson.parseBatchGet(big)["2026-12_river"])
        assertThrows(kotlinx.serialization.SerializationException::class.java) {
            FirestoreJson.parseBatchGet("""{"error":{"code":403}}""")
        }
    }

    @Test
    fun docIdsAreStrictBecauseTheyGoIntoAUrl() {
        assertEquals("2026-12_river-relief", CommunityCounter.docId("2026-12", "river-relief"))
        for ((month, id) in listOf("2026-13" to "river", "26-12" to "river", "2026-12" to "../x", "2026-12" to "River", "2026-12" to "a")) {
            assertThrows(IllegalArgumentException::class.java) { CommunityCounter.docId(month, id) }
        }
    }

    @Test
    fun statusCodesDecideRetryOrGiveUp() {
        assertEquals(SendOutcome.COUNTED, CommunityCounter.classify(200))
        for (code in listOf(408, 429, 500, 503)) assertEquals(SendOutcome.RETRY, CommunityCounter.classify(code))
        for (code in listOf(400, 403, 404, 409)) assertEquals(SendOutcome.REJECTED, CommunityCounter.classify(code))
    }

    // ---- CommunityCounter over a fake Firestore ----

    private class FakeFirestore : FirestoreApi {
        var commitCode = 200
        var commitError: IOException? = null
        var batchGetJson = "[]"
        val commits = mutableListOf<Pair<String, JsonObject>>()

        private fun response(code: Int, body: String): Response<ResponseBody> =
            if (code in 200..299) Response.success(code, body.toResponseBody("application/json".toMediaType()))
            else Response.error(code, body.toResponseBody("application/json".toMediaType()))

        override suspend fun commit(url: String, body: JsonObject): Response<ResponseBody> {
            commitError?.let { throw it }
            commits += url to body
            return response(commitCode, "{}")
        }

        override suspend fun batchGet(url: String, body: JsonObject) = response(200, batchGetJson)
    }

    @Test
    fun addVoteSendsOnlyTheIncrementToTheProjectsCommitUrl() = runTest {
        val firestore = FakeFirestore()
        val counter = CommunityCounter(firestore, "demo", UnconfinedTestDispatcher(testScheduler))

        assertEquals(SendOutcome.COUNTED, counter.addVote("2026-12", "river"))
        val (url, body) = firestore.commits.single()
        assertEquals("https://firestore.googleapis.com/v1/$root:commit", url)
        assertEquals(FirestoreJson.incrementBody(root, "2026-12_river"), body)

        firestore.commitCode = 403
        assertEquals(SendOutcome.REJECTED, counter.addVote("2026-12", "river"))
        firestore.commitError = IOException("offline")
        assertEquals(SendOutcome.RETRY, counter.addVote("2026-12", "river"))
    }

    @Test
    fun totalsFillMissingCountersWithZero() = runTest {
        val firestore = FakeFirestore().apply {
            batchGetJson = """[{"found":{"name":"$root/community_votes/2026-12_river","fields":{"count":{"integerValue":"5"}}}},
                               {"missing":"$root/community_votes/2026-12_harbor"}]"""
        }
        val counter = CommunityCounter(firestore, "demo", UnconfinedTestDispatcher(testScheduler))
        assertEquals(mapOf("river" to 5L, "harbor" to 0L), counter.totals("2026-12", listOf("river", "harbor")))
        assertEquals(emptyMap<String, Long>(), counter.totals("2026-12", emptyList()))
    }

    // ---- ministries.json from the CDN ----

    private val api = FakeContentApi()
    private val file = MinistriesFile(
        1,
        listOf(Ministry("river", "River", mapOf("en" to "Translation", "es" to "Traducción"), "https://r.test", "https://r.test/give", listOf("2026-12"))),
    )

    private fun publish(tamper: Boolean = false, withCommunity: Boolean = true) {
        val bytes = TestJson.encodeToString(MinistriesFile.serializer(), file).toByteArray()
        val entry = CommunityEntry("https://cdn.test/ministries.json", 1, Sha256.hex(bytes), bytes.size.toLong())
        val m: Manifest = manifest().copy(community = if (withCommunity) entry else null)
        api.files["https://cdn.test/manifest.json"] = TestJson.encodeToString(Manifest.serializer(), m).toByteArray()
        api.files["https://cdn.test/ministries.json"] = if (tamper) bytes + " ".toByteArray() else bytes
    }

    private var now = Instant.parse("2026-12-01T12:00:00Z")

    private fun TestScope.repository() =
        CommunityContentRepository(api, ContentCache(temp.root), TestJson, "https://cdn.test/", UnconfinedTestDispatcher(testScheduler)) { now }

    @Test
    fun verifiedFileIsShownAndCachedForOffline() = runTest {
        publish()
        assertTrue(repository().refresh())
        assertEquals(MinistriesState.Ready(file), repository().ministries.first()) // a new instance reads the cache
        assertEquals("Traducción", file.ministries[0].description("es"))
        assertEquals("Translation", file.ministries[0].description("de"))
    }

    @Test
    fun tamperedFileIsNeitherUsedNorCached() = runTest {
        publish(tamper = true)
        val repo = repository()
        assertFalse(repo.refresh())
        assertEquals(MinistriesState.Unavailable(MinistriesProblem.INVALID), repo.ministries.first())
        assertNull(ContentCache(temp.root).read(ContentCache.MINISTRIES))
    }

    @Test
    fun manifestWithoutCommunityMeansUnavailable() = runTest {
        publish(withCommunity = false)
        val repo = repository()
        assertTrue(repo.refresh()) // the check worked: there is no list
        assertEquals(MinistriesState.Unavailable(MinistriesProblem.NOT_PUBLISHED), repo.ministries.first())
    }

    @Test
    fun offlineKeepsTheCachedFile() = runTest {
        publish()
        repository().refresh()
        api.downloadError = IOException("offline")
        val repo = repository()
        assertFalse(repo.refresh())
        assertEquals(MinistriesState.Ready(file), repo.ministries.first())
    }

    @Test
    fun offlineWithNothingCachedSaysOffline() = runTest {
        api.downloadError = IOException("offline")
        val repo = repository()
        assertFalse(repo.refresh())
        assertEquals(MinistriesState.Unavailable(MinistriesProblem.OFFLINE), repo.ministries.first())
    }

    @Test
    fun withdrawnListIsNoLongerShown() = runTest {
        publish()
        repository().refresh()
        publish(withCommunity = false)
        val repo = repository()
        repo.refresh()
        assertEquals(MinistriesState.Unavailable(MinistriesProblem.NOT_PUBLISHED), repo.ministries.first())
        assertNull(ContentCache(temp.root).read(ContentCache.MINISTRIES))
    }

    @Test
    fun staleCheckRunsOnlyAfterSixHoursOrAHashChange() = runTest {
        publish()
        assertEquals(true, repository().refreshIfStale()) // nothing cached
        val repo = repository() // a new instance: last check comes from the cache
        repo.ministries.first()
        assertNull(repo.refreshIfStale())

        now = now.plusSeconds(5 * 3600)
        assertNull(repo.refreshIfStale())

        // Another downloader cached a manifest pinning a different list: check now.
        ContentCache(temp.root).write(ContentCache.MANIFEST, api.files.getValue("https://cdn.test/manifest.json").decodeToString()
            .replace(Sha256.hex(api.files.getValue("https://cdn.test/ministries.json")), "0".repeat(64)).toByteArray())
        assertEquals(true, repo.refreshIfStale())
        ContentCache(temp.root).delete(ContentCache.MANIFEST)

        now = now.plusSeconds(6 * 3600 + 1)
        api.downloadError = IOException("offline")
        assertEquals(false, repo.refreshIfStale()) // over 6 hours: checks (and keeps the cached list when offline)
        assertEquals(MinistriesState.Ready(file), repo.ministries.first())
        assertEquals(MinistriesProblem.OFFLINE, repo.info.value.lastProblem)
    }

    @Test
    fun unchangedHashSkipsTheDownload() = runTest {
        publish()
        val repo = repository()
        repo.refresh()
        api.files.remove("https://cdn.test/ministries.json") // would 404 if downloaded again
        assertTrue(repo.refresh())
        assertEquals(MinistriesState.Ready(file), repo.ministries.first())
    }
}
