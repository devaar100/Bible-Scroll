package aarnav100.developer.biblescroll.data.community

import aarnav100.developer.biblescroll.core.content.RoundRead
import aarnav100.developer.biblescroll.core.content.model.MinistriesFile
import aarnav100.developer.biblescroll.core.content.model.Ministry
import aarnav100.developer.biblescroll.data.community.CommunityRules.Decision
import aarnav100.developer.biblescroll.data.community.CommunityRules.RoundState
import aarnav100.developer.biblescroll.data.community.CommunityRules.VoteStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Month boundaries (UTC), windows, rounds and the vote state machine. */
class CommunityRulesTest {

    private fun at(text: String) = Instant.parse(text)
    private fun ministry(id: String, vararg months: String, test: Boolean = false) =
        Ministry(id, id, mapOf("en" to id), "https://$id.test", "https://$id.test/give", months.toList(), test)

    private val file = MinistriesFile(
        1,
        listOf(
            ministry("river", "2026-10", "2026-12"),
            ministry("harbor", "2026-11", "2026-12"),
            ministry("test-hill", "2026-12", "2027-01", test = true),
        ),
    )

    // ---- Month boundaries ----

    @Test
    fun monthIsTheUtcCalendarMonth() {
        assertEquals("2026-11", CommunityRules.monthOf(at("2026-11-30T23:59:59.999Z")))
        assertEquals("2026-12", CommunityRules.monthOf(at("2026-12-01T00:00:00Z")))
        assertEquals("2027-01", CommunityRules.monthOf(at("2027-01-01T00:00:00Z")))
    }

    @Test
    fun localEveningCanAlreadyBeNextMonthInUtc() {
        // 18:00 on 30 November in Chicago is 00:00 on 1 December UTC: the December round is open.
        val chicago = ZonedDateTime.of(2026, 11, 30, 18, 0, 0, 0, ZoneId.of("America/Chicago")).toInstant()
        assertEquals("2026-12", CommunityRules.monthOf(chicago))
        // 08:00 on 1 December in Auckland is still 30 November UTC.
        val auckland = ZonedDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneId.of("Pacific/Auckland")).toInstant()
        assertEquals("2026-11", CommunityRules.monthOf(auckland))
    }

    @Test
    fun windowOpensAtTheFirstAndClosesAtTheNextFirst() {
        val w = CommunityRules.window("2026-12")
        assertEquals(at("2026-12-01T00:00:00Z"), w.opensAt)
        assertEquals(at("2027-01-01T00:00:00Z"), w.closesAt)
        assertEquals(at("2028-03-01T00:00:00Z"), CommunityRules.window("2028-02").closesAt) // leap February
        assertTrue(at("2026-12-01T00:00:00Z") in w) // opensAt inclusive, like the rules' >=
        assertTrue(at("2026-12-31T23:59:59.999Z") in w)
        assertFalse(at("2027-01-01T00:00:00Z") in w) // closesAt exclusive, like the rules' <
        assertFalse(at("2026-11-30T23:59:59.999Z") in w)
    }

    // ---- Rounds ----

    @Test
    fun roundHidesTestEntriesUnlessDebug() {
        assertEquals(listOf("river", "harbor"), CommunityRules.round(file, "2026-12", includeTest = false).map { it.id })
        assertEquals(listOf("river", "harbor", "test-hill"), CommunityRules.round(file, "2026-12", includeTest = true).map { it.id })
        assertEquals(emptyList<Ministry>(), CommunityRules.round(file, "2027-01", includeTest = false)) // only a test entry
        assertEquals(emptyList<Ministry>(), CommunityRules.round(file, "2027-05", includeTest = true))
    }

    @Test
    fun pastMonthsAreClosedRoundsNewestFirst() {
        val now = at("2026-12-15T12:00:00Z")
        assertEquals(listOf("2026-11", "2026-10"), CommunityRules.pastMonths(file, now, includeTest = false))
        assertEquals(listOf("2026-11"), CommunityRules.pastMonths(file, now, includeTest = false, limit = 1))
        assertEquals(listOf("2026-12", "2026-11", "2026-10"), CommunityRules.pastMonths(file, at("2027-01-01T00:00:00Z"), includeTest = false))
    }

    // ---- Vote state machine ----

    private fun vote(sent: Boolean) = CommunityVoteEntity("2026-12", "river", 1, sent, 1)
    private val open = at("2026-12-10T00:00:00Z")
    private val after = at("2027-01-02T00:00:00Z")
    private val before = at("2026-11-20T00:00:00Z")

    @Test
    fun statusCoversEveryState() {
        assertEquals(VoteStatus.CAN_VOTE, CommunityRules.status(null, "2026-12", open))
        assertEquals(VoteStatus.CLOSED, CommunityRules.status(null, "2026-12", before))
        assertEquals(VoteStatus.CLOSED, CommunityRules.status(null, "2026-12", after))
        assertEquals(VoteStatus.PENDING, CommunityRules.status(vote(sent = false), "2026-12", open))
        assertEquals(VoteStatus.COUNTED, CommunityRules.status(vote(sent = true), "2026-12", open))
        assertEquals(VoteStatus.COUNTED, CommunityRules.status(vote(sent = true), "2026-12", after))
        assertEquals(VoteStatus.NOT_COUNTED, CommunityRules.status(vote(sent = false), "2026-12", after))
    }

    @Test
    fun decideAllowsOneVoteInTheRoundWhileOpen() {
        val ids = listOf("river", "harbor")
        assertEquals(Decision.Accept, CommunityRules.decide(null, "2026-12", "river", ids, open))
        assertEquals(Decision.AlreadyVoted, CommunityRules.decide(vote(sent = false), "2026-12", "harbor", ids, open))
        assertEquals(Decision.AlreadyVoted, CommunityRules.decide(vote(sent = true), "2026-12", "river", ids, open))
        assertEquals(Decision.Closed, CommunityRules.decide(null, "2026-12", "river", ids, before))
        assertEquals(Decision.Closed, CommunityRules.decide(null, "2026-12", "river", ids, after))
        assertEquals(Decision.NotInRound, CommunityRules.decide(null, "2026-12", "elsewhere", ids, open))
    }

    @Test
    fun currentTotalsOnlyAfterVotingClosedMonthsForEveryone() {
        assertFalse(CommunityRules.showTotals(null, "2026-12", open))
        assertTrue(CommunityRules.showTotals(vote(sent = false), "2026-12", open))
        assertTrue(CommunityRules.showTotals(null, "2026-12", after))
    }

    @Test
    fun roundStateComesFromTheCounters() {
        val now = at("2026-12-10T00:00:00Z")
        val open = RoundRead(mapOf("a" to 1L), emptySet(), at("2026-12-01T00:00:00Z"), at("2027-01-01T00:00:00Z"))
        assertEquals(RoundState.UNKNOWN, CommunityRules.roundState(null, now))
        assertEquals(RoundState.OPEN, CommunityRules.roundState(open, now))
        assertEquals(RoundState.NOT_OPEN_YET, CommunityRules.roundState(open.copy(missing = setOf("b")), now))
        assertEquals(RoundState.NOT_OPEN_YET, CommunityRules.roundState(open, at("2026-11-30T23:59:59Z")))
        assertEquals(RoundState.CLOSED, CommunityRules.roundState(open, at("2027-01-01T00:00:00Z")))
    }

    @Test
    fun nextMonthIsTheFirstFutureRound() {
        val f = MinistriesFile(1, listOf(ministry("a", "2026-11", "2027-02"), ministry("b", "2027-01"), ministry("test-c", "2026-12", test = true)))
        assertEquals("2027-01", CommunityRules.nextMonth(f, at("2026-12-10T00:00:00Z"), includeTest = false))
        assertEquals(null, CommunityRules.nextMonth(f, at("2027-02-10T00:00:00Z"), includeTest = false))
    }
}

