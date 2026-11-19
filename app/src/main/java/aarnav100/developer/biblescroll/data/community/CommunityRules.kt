package aarnav100.developer.biblescroll.data.community

import aarnav100.developer.biblescroll.core.content.model.MinistriesFile
import aarnav100.developer.biblescroll.core.content.model.Ministry
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Community vote rules (C1/C2), pure so they're easy to test. Months are UTC calendar months, the same windows the
 * seed script gives each counter (opensAt = 1st 00:00Z, closesAt = next 1st 00:00Z). The app shows them in local time.
 */
object CommunityRules {
    private val MONTH = DateTimeFormatter.ofPattern("yyyy-MM")

    /** "2026-12" for an instant, in UTC. */
    fun monthOf(now: Instant): String = YearMonth.from(now.atOffset(ZoneOffset.UTC)).format(MONTH)

    data class Window(val opensAt: Instant, val closesAt: Instant) {
        operator fun contains(now: Instant) = !now.isBefore(opensAt) && now.isBefore(closesAt)
    }

    fun window(month: String): Window {
        val ym = YearMonth.parse(month, MONTH)
        return Window(ym.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC), ym.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC))
    }

    /** Ministries in [month]'s round, in file order. Test entries only when [includeTest] (debug builds). */
    fun round(file: MinistriesFile, month: String, includeTest: Boolean): List<Ministry> =
        file.ministries.filter { month in it.monthsFeatured && (includeTest || !it.test) }

    /** Closed months that had a round, newest first (at most [limit]). */
    fun pastMonths(file: MinistriesFile, now: Instant, includeTest: Boolean, limit: Int = 12): List<String> {
        val current = monthOf(now)
        return file.ministries.filter { includeTest || !it.test }
            .flatMap { it.monthsFeatured }
            .distinct()
            .filter { it < current } // "YYYY-MM" sorts as text
            .sortedDescending()
            .take(limit)
    }

    /** Where this phone's vote for a month stands. */
    enum class VoteStatus {
        /** Round open and no vote yet. */
        CAN_VOTE,

        /** Voted; the +1 is waiting to be sent (offline) or retried. */
        PENDING,

        /** The server counted it. */
        COUNTED,

        /** Voted, but the month closed before it could be sent: it didn't count. */
        NOT_COUNTED,

        /** No vote and the round isn't open (before or after the window). */
        CLOSED,
    }

    fun status(vote: CommunityVoteEntity?, month: String, now: Instant): VoteStatus {
        val open = now in window(month)
        return when {
            vote == null -> if (open) VoteStatus.CAN_VOTE else VoteStatus.CLOSED
            vote.sent -> VoteStatus.COUNTED
            open -> VoteStatus.PENDING
            else -> VoteStatus.NOT_COUNTED
        }
    }

    sealed interface Decision {
        data object Accept : Decision
        data object AlreadyVoted : Decision
        data object Closed : Decision
        data object NotInRound : Decision
    }

    /** Whether a new vote may be cast. The client blocks outside the window; the server enforces it anyway. */
    fun decide(existing: CommunityVoteEntity?, month: String, ministryId: String, roundIds: Collection<String>, now: Instant): Decision = when {
        existing != null -> Decision.AlreadyVoted
        now !in window(month) -> Decision.Closed
        ministryId !in roundIds -> Decision.NotInRound
        else -> Decision.Accept
    }

    /** Current-month totals are shown only to someone who has voted (C2); closed months to everyone. */
    fun showTotals(vote: CommunityVoteEntity?, month: String, now: Instant): Boolean =
        !now.isBefore(window(month).closesAt) || vote != null
}
