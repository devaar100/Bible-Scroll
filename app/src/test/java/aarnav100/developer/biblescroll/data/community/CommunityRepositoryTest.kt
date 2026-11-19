package aarnav100.developer.biblescroll.data.community

import aarnav100.developer.biblescroll.core.content.CommunityContentRepository
import aarnav100.developer.biblescroll.core.content.CommunityCounter
import aarnav100.developer.biblescroll.core.content.ContentCache
import aarnav100.developer.biblescroll.core.content.model.Manifest
import aarnav100.developer.biblescroll.core.content.net.ContentApi
import aarnav100.developer.biblescroll.core.content.net.FirestoreApi
import aarnav100.developer.biblescroll.testing.FakeLocalClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.IOException
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CommunityRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeDao : CommunityVoteDao {
        val rows = MutableStateFlow<Map<String, CommunityVoteEntity>>(emptyMap())
        override fun observeAll(): Flow<List<CommunityVoteEntity>> = rows.map { it.values.toList() }
        override suspend fun get(month: String) = rows.value[month]
        override suspend fun insertIfAbsent(vote: CommunityVoteEntity): Long {
            if (vote.month in rows.value) return -1
            rows.value = rows.value + (vote.month to vote)
            return 1
        }
        override suspend fun markSent(month: String, now: Long) {
            rows.value[month]?.let { rows.value = rows.value + (month to it.copy(sent = true, updatedAt = now)) }
        }
        override suspend fun deleteUnsent(month: String) {
            if (rows.value[month]?.sent == false) rows.value = rows.value - month
        }
        override suspend fun pending() = rows.value.values.filter { !it.sent }
    }

    /** Firestore that answers each commit with the next queued result (HTTP code, or an IOException for offline). */
    private class FakeFirestore : FirestoreApi {
        val replies = ArrayDeque<Int>()
        val committed = mutableListOf<String>()
        private fun body(text: String) = text.toResponseBody("application/json".toMediaType())
        override suspend fun commit(url: String, body: JsonObject): Response<ResponseBody> {
            val code = replies.removeFirstOrNull() ?: 200
            if (code == OFFLINE) throw IOException("offline")
            committed += body.toString().substringAfter("community_votes/").substringBefore('"')
            return if (code in 200..299) Response.success(code, body("{}")) else Response.error(code, body("{}"))
        }
        var batchGets = 0
        override suspend fun batchGet(url: String, body: JsonObject): Response<ResponseBody> {
            batchGets++
            return Response.success(body("[]"))
        }
        companion object { const val OFFLINE = -1 }
    }

    private object NoContent : ContentApi {
        override suspend fun manifest(): Manifest = throw IOException()
        override suspend fun download(url: String): ResponseBody = throw IOException()
    }

    private val dao = FakeDao()
    private val firestore = FakeFirestore()
    private var scheduled = 0
    private val clock = FakeLocalClock(ZonedDateTime.of(2026, 12, 10, 12, 0, 0, 0, ZoneOffset.UTC))
    private val round = listOf("river", "harbor")

    private fun TestScope.repository(): CommunityRepository {
        val io = UnconfinedTestDispatcher(testScheduler)
        return CommunityRepository(
            CommunityContentRepository(NoContent, ContentCache(temp.root), Json, "https://cdn.test/", io),
            dao,
            CommunityCounter(firestore, "demo", io),
            { scheduled++ },
            clock,
            CommunityConfig(includeTest = false),
        )
    }

    @Test
    fun aCountedVoteIsSavedAsSent() = runTest {
        assertEquals(VoteResult.COUNTED, repository().vote("2026-12", "river", round))
        val row = dao.rows.value.getValue("2026-12")
        assertEquals("river", row.ministryId)
        assertTrue(row.sent)
        assertEquals(listOf("2026-12_river"), firestore.committed)
        assertEquals(0, scheduled)
    }

    @Test
    fun oneVotePerMonth_aSecondOneIsNeverSent() = runTest {
        val repo = repository()
        repo.vote("2026-12", "river", round)
        assertEquals(VoteResult.ALREADY_VOTED, repo.vote("2026-12", "harbor", round))
        assertEquals(VoteResult.ALREADY_VOTED, repo.vote("2026-12", "river", round))
        assertEquals(listOf("2026-12_river"), firestore.committed)
        assertEquals("river", dao.rows.value.getValue("2026-12").ministryId)
    }

    @Test
    fun aNewMonthAllowsANewVote() = runTest {
        val repo = repository()
        repo.vote("2026-12", "river", round)
        clock.now = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(VoteResult.COUNTED, repo.vote("2027-01", "harbor", round))
        assertEquals(setOf("2026-12", "2027-01"), dao.rows.value.keys)
    }

    @Test
    fun offlineVoteIsKeptPendingAndScheduled() = runTest {
        firestore.replies += FakeFirestore.OFFLINE
        val repo = repository()
        assertEquals(VoteResult.PENDING, repo.vote("2026-12", "river", round))
        assertFalse(dao.rows.value.getValue("2026-12").sent)
        assertEquals(1, scheduled)
        assertTrue(repo.hasPending())

        // Back online: the worker sends it.
        assertTrue(repo.sendPending())
        assertTrue(dao.rows.value.getValue("2026-12").sent)
        assertFalse(repo.hasPending())
    }

    @Test
    fun serverErrorsAreRetriedLater() = runTest {
        firestore.replies += listOf(503, 429)
        val repo = repository()
        assertEquals(VoteResult.PENDING, repo.vote("2026-12", "river", round))
        assertFalse(repo.sendPending()) // 429: retry again
        assertTrue(repo.sendPending()) // 200
        assertTrue(dao.rows.value.getValue("2026-12").sent)
    }

    @Test
    fun aRefusedVoteIsNotKeptSoItCanBeTriedAgain() = runTest {
        firestore.replies += 403
        val repo = repository()
        assertEquals(VoteResult.REFUSED, repo.vote("2026-12", "river", round))
        assertNull(dao.rows.value["2026-12"])
        assertEquals(VoteResult.COUNTED, repo.vote("2026-12", "harbor", round))
    }

    @Test
    fun aPendingVoteRefusedLaterIsDropped() = runTest {
        firestore.replies += listOf(FakeFirestore.OFFLINE, 404)
        val repo = repository()
        repo.vote("2026-12", "river", round)
        assertTrue(repo.sendPending())
        assertNull(dao.rows.value["2026-12"])
    }

    @Test
    fun aPendingVoteFromAClosedMonthIsNotSentAndStaysNotCounted() = runTest {
        firestore.replies += FakeFirestore.OFFLINE
        val repo = repository()
        repo.vote("2026-12", "river", round)
        clock.now = ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

        assertTrue(repo.sendPending()) // nothing to retry: the window closed
        assertTrue(firestore.committed.isEmpty()) // never reached the server
        val row = dao.rows.value.getValue("2026-12")
        assertFalse(row.sent)
        assertEquals(CommunityRules.VoteStatus.NOT_COUNTED, CommunityRules.status(row, "2026-12", repo.now()))
        assertFalse(repo.hasPending())
    }

    @Test
    fun clientBlocksVotingOutsideTheWindowAndOutsideTheRound() = runTest {
        val repo = repository()
        assertEquals(VoteResult.CLOSED, repo.vote("2026-11", "river", round))
        assertEquals(VoteResult.CLOSED, repo.vote("2027-01", "river", round))
        assertEquals(VoteResult.NOT_IN_ROUND, repo.vote("2026-12", "elsewhere", round))
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(firestore.committed.isEmpty())
    }

    @Test
    fun counterReadsAreCachedForFiveMinutesInAnOpenMonth() = runTest {
        val repo = repository()
        repo.read("2026-12", round)
        clock.now = clock.now.plusMinutes(4)
        repo.read("2026-12", round)
        assertEquals(1, firestore.batchGets) // reopened within 5 minutes: no read
        repo.read("2026-12", round, force = true)
        assertEquals(2, firestore.batchGets) // pull to refresh always reads
        clock.now = clock.now.plusMinutes(6)
        repo.read("2026-12", round)
        assertEquals(3, firestore.batchGets)
        repo.read("2026-11", round) // closed month: cached for an hour
        clock.now = clock.now.plusMinutes(30)
        repo.read("2026-11", round)
        assertEquals(4, firestore.batchGets)
    }
}
