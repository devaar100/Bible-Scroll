package aarnav100.developer.biblescroll.data.community

import aarnav100.developer.biblescroll.core.content.CommunityContentRepository
import aarnav100.developer.biblescroll.core.content.CommunityCounter
import aarnav100.developer.biblescroll.core.content.MinistriesState
import aarnav100.developer.biblescroll.core.content.SendOutcome
import aarnav100.developer.biblescroll.data.votd.LocalClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Debug builds show test:true ministries; release builds never do. */
data class CommunityConfig(val includeTest: Boolean)

/** Sends unsent votes later (WorkManager). Abstracted for tests. */
fun interface VoteSendScheduler {
    fun schedule()
}

enum class VoteResult {
    /** Counted by the server. */
    COUNTED,

    /** Saved here; will be sent when the phone is online. */
    PENDING,

    ALREADY_VOTED,

    /** Outside this month's window (client check; the server enforces it too). */
    CLOSED,

    NOT_IN_ROUND,

    /** The server refused it (e.g. this month's counters aren't set up). Not kept, so it can be tried again. */
    REFUSED,
}

/**
 * C1/C2 Community: the month's ministries (CDN, hash-verified), this phone's own votes (user.db) and the anonymous
 * counter (Firestore). One vote per month per phone, final. A vote is saved before it's sent, so going offline or
 * closing the app never loses it; WorkManager retries. If a send succeeded but its reply was lost, the retry counts
 * it twice: accepted (docs/decisions.md).
 */
@Singleton
class CommunityRepository @Inject constructor(
    private val content: CommunityContentRepository,
    private val dao: CommunityVoteDao,
    private val counter: CommunityCounter,
    private val sendLater: VoteSendScheduler,
    private val clock: LocalClock,
    private val config: CommunityConfig,
) {
    val ministries: Flow<MinistriesState> = content.ministries
    val votes: Flow<Map<String, CommunityVoteEntity>> = dao.observeAll().map { rows -> rows.associateBy { it.month } }
    val includeTest: Boolean get() = config.includeTest

    fun now(): Instant = clock.now().toInstant()

    suspend fun refresh(): Boolean = content.refresh()

    /** [roundIds]: the ministries in [month]'s round, as shown. */
    suspend fun vote(month: String, ministryId: String, roundIds: Collection<String>): VoteResult {
        val now = now()
        when (CommunityRules.decide(dao.get(month), month, ministryId, roundIds, now)) {
            CommunityRules.Decision.AlreadyVoted -> return VoteResult.ALREADY_VOTED
            CommunityRules.Decision.Closed -> return VoteResult.CLOSED
            CommunityRules.Decision.NotInRound -> return VoteResult.NOT_IN_ROUND
            CommunityRules.Decision.Accept -> Unit
        }
        val at = now.toEpochMilli()
        // Saved first: the insert is what makes it one vote per month, even with a double tap.
        if (dao.insertIfAbsent(CommunityVoteEntity(month, ministryId, at, sent = false, updatedAt = at)) == -1L) {
            return VoteResult.ALREADY_VOTED
        }
        return when (counter.addVote(month, ministryId)) {
            SendOutcome.COUNTED -> {
                dao.markSent(month, now().toEpochMilli())
                VoteResult.COUNTED
            }
            SendOutcome.RETRY -> {
                sendLater.schedule()
                VoteResult.PENDING
            }
            SendOutcome.REJECTED -> {
                dao.deleteUnsent(month)
                VoteResult.REFUSED
            }
        }
    }

    /**
     * Sends every unsent vote whose month is still open. Returns true when nothing is left to retry. A month that
     * closed before its vote got through keeps the vote here as "not counted"; a vote the server refuses is removed.
     */
    suspend fun sendPending(): Boolean {
        var retry = false
        for (vote in dao.pending()) {
            if (now() !in CommunityRules.window(vote.month)) continue
            when (counter.addVote(vote.month, vote.ministryId)) {
                SendOutcome.COUNTED -> dao.markSent(vote.month, now().toEpochMilli())
                SendOutcome.RETRY -> retry = true
                SendOutcome.REJECTED -> dao.deleteUnsent(vote.month)
            }
        }
        return !retry
    }

    /** True if some vote is waiting to be sent in an open month (app start schedules the sender then). */
    suspend fun hasPending(): Boolean = dao.pending().any { now() in CommunityRules.window(it.month) }

    /** Totals by month and ministry id; months that couldn't be read are missing from the result. */
    suspend fun totals(rounds: Map<String, List<String>>): Map<String, Map<String, Long>> =
        rounds.mapNotNull { (month, ids) -> counter.totals(month, ids)?.let { month to it } }.toMap()
}
