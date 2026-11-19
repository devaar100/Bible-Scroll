package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.core.content.MinistriesFetchInfo
import aarnav100.developer.biblescroll.core.content.MinistriesProblem
import aarnav100.developer.biblescroll.core.content.MinistriesState
import aarnav100.developer.biblescroll.core.content.RoundRead
import aarnav100.developer.biblescroll.core.content.model.Ministry
import aarnav100.developer.biblescroll.data.community.CommunityRepository
import aarnav100.developer.biblescroll.data.community.CommunityRules
import aarnav100.developer.biblescroll.data.community.CommunityRules.RoundState
import aarnav100.developer.biblescroll.data.community.CommunityRules.VoteStatus
import aarnav100.developer.biblescroll.data.community.CommunityVoteEntity
import aarnav100.developer.biblescroll.data.community.VoteResult
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@Immutable
data class MinistryUi(val id: String, val name: String, val description: String, val website: String, val giveUrl: String)

@Immutable
data class PastRoundUi(
    val month: String,
    /** Most votes first; counts are null when totals aren't loaded. */
    val ministries: List<Pair<MinistryUi, Long?>>,
    val myPickId: String?,
    /** The read failed (as opposed to still loading). */
    val totalsUnavailable: Boolean = false,
)

/** Why there's no round to vote on, each with its own message. */
enum class CommunityEmpty {
    /** Offline (or CDN unreachable) and no list saved on this phone. */
    OFFLINE_NOTHING_CACHED,

    /** The CDN has no ministry list. */
    LIST_MISSING,

    /** A list was there but failed its checks. */
    LIST_INVALID,

    /** The list has no ministries (after leaving out test entries in release). */
    LIST_EMPTY,

    /** The list is fine but has no ministry for this month. */
    NO_MINISTRY_THIS_MONTH,
}

/** Debug builds: where each piece came from and when it was last fetched. */
@Immutable
data class CommunitySources(
    val ministries: MinistriesFetchInfo,
    /** When this month's counters were last read, and whether it worked. */
    val roundReadAt: Instant?,
    val roundReadOk: Boolean?,
    /** Latest past-month totals read. */
    val totalsReadAt: Instant?,
    val totalsReadOk: Boolean?,
)

@Immutable
data class CommunityUiState(
    val loading: Boolean = true,
    /** Set when there's no round to show; [round] is empty then. */
    val empty: CommunityEmpty? = null,
    /** With NO_MINISTRY_THIS_MONTH: the next month that has a round, if any. */
    val nextMonth: String? = null,
    val month: String = "",
    val closesAt: Instant? = null,
    /** This month's round. */
    val round: List<MinistryUi> = emptyList(),
    val status: VoteStatus = VoteStatus.CLOSED,
    /** The server side of this month's round (counters set up, window open). */
    val roundState: RoundState = RoundState.UNKNOWN,
    val myPick: MinistryUi? = null,
    /** This month's totals: only after this phone voted (null otherwise, or while loading/offline). */
    val currentTotals: Map<String, Long>? = null,
    /** Totals should show (voted) but couldn't be read. */
    val currentTotalsUnavailable: Boolean = false,
    val past: List<PastRoundUi> = emptyList(),
    val sending: Boolean = false,
    /** Pull to refresh / Try again in progress. */
    val refreshing: Boolean = false,
    /** Debug builds only. */
    val sources: CommunitySources? = null,
) {
    val canVote: Boolean
        get() = status == VoteStatus.CAN_VOTE && !sending && roundState != RoundState.NOT_OPEN_YET && roundState != RoundState.CLOSED
}

/** One month's counter read: [read] null if it failed. */
private data class MonthRead(val read: RoundRead?, val at: Instant)

/**
 * C1 this month's vote and C2 past spotlights.
 *
 * Refreshing: the ministry list is checked when the screen is shown if it's missing, over 6 hours old, or the cached
 * manifest pins a different hash (CommunityContentRepository.refreshIfStale); pull to refresh and Try again always
 * check. Counters (Firestore) are read when the screen opens, when the months shown change, and on every refresh.
 */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val community: CommunityRepository,
) : ViewModel() {

    private val reads = MutableStateFlow<Map<String, MonthRead>>(emptyMap())
    private val sending = MutableStateFlow(false)
    private val pulling = MutableStateFlow(false)
    private val confirmTarget = MutableStateFlow<MinistryUi?>(null)
    private val _results = Channel<VoteResult>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    /** Re-evaluated every minute, so the screen moves to the next round when a UTC month starts. */
    private val clock = flow {
        while (true) {
            emit(community.now())
            delay(60_000)
        }
    }

    val confirm: StateFlow<MinistryUi?> = confirmTarget

    private val flags = combine(sending, pulling, community.ministriesInfo) { s, p, info -> Triple(s, p, info) }

    val uiState: StateFlow<CommunityUiState> = combine(
        community.ministries, community.votes, clock, reads, flags,
    ) { ministries, votes, now, reads, (isSending, isPulling, info) -> build(ministries, votes, now, reads, isSending, isPulling, info) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunityUiState())

    init {
        // Counters follow what may be shown: this month's round (for its state) and past rounds (totals).
        viewModelScope.launch {
            uiState.map(::wantedReads).distinctUntilChanged().collect { wanted -> read(wanted.filterKeys { it !in reads.value }, force = false) }
        }
    }

    /** Screen shown (each resume): re-checks the list only when it's stale or its hash changed. */
    fun onShown() {
        viewModelScope.launch { community.refreshIfStale() }
    }

    /** Pull to refresh and Try again: the list and every counter shown. */
    fun refresh() {
        if (pulling.value) return
        pulling.value = true
        viewModelScope.launch {
            try {
                community.refresh()
                read(wantedReads(uiState.value), force = true)
            } finally {
                pulling.value = false
            }
        }
    }

    fun askToVote(ministry: MinistryUi) {
        if (uiState.value.canVote) confirmTarget.value = ministry
    }

    fun dismissConfirm() {
        confirmTarget.value = null
    }

    fun confirmVote() {
        val target = confirmTarget.value ?: return
        confirmTarget.value = null
        val state = uiState.value
        if (sending.value) return
        sending.value = true
        viewModelScope.launch {
            try {
                val result = community.vote(state.month, target.id, state.round.map { it.id })
                _results.send(result)
                // Counted: show totals. Refused: find out why (not set up, or closed).
                if (result == VoteResult.COUNTED || result == VoteResult.REFUSED) read(mapOf(state.month to state.round.map { it.id }.sorted()), force = true)
            } finally {
                sending.value = false
            }
        }
    }

    /** Uses the repository's short cache unless [force]; see CommunityRepository.read. */
    private suspend fun read(wanted: Map<String, List<String>>, force: Boolean) {
        for ((month, ids) in wanted) {
            val result = community.read(month, ids, force)
            reads.update { it + (month to MonthRead(result.read, result.fetchedAt)) }
        }
    }

    private fun wantedReads(state: CommunityUiState): Map<String, List<String>> = buildMap {
        if (state.round.isNotEmpty()) put(state.month, state.round.map { it.id }.sorted())
        state.past.forEach { put(it.month, it.ministries.map { (m, _) -> m.id }.sorted()) }
    }

    private fun build(
        ministries: MinistriesState,
        votes: Map<String, CommunityVoteEntity>,
        now: Instant,
        reads: Map<String, MonthRead>,
        isSending: Boolean,
        isPulling: Boolean,
        info: MinistriesFetchInfo,
    ): CommunityUiState {
        val month = CommunityRules.monthOf(now)
        val sources = if (community.showSources) sources(info, reads, month) else null
        val file = when (ministries) {
            MinistriesState.Loading -> return CommunityUiState(loading = true, refreshing = isPulling, sources = sources)
            is MinistriesState.Unavailable -> return CommunityUiState(
                loading = false,
                empty = when (ministries.problem) {
                    MinistriesProblem.OFFLINE -> CommunityEmpty.OFFLINE_NOTHING_CACHED
                    MinistriesProblem.NOT_PUBLISHED -> CommunityEmpty.LIST_MISSING
                    MinistriesProblem.INVALID -> CommunityEmpty.LIST_INVALID
                },
                refreshing = isPulling,
                sources = sources,
            )
            is MinistriesState.Ready -> ministries.file
        }
        val language = Locale.getDefault().language
        fun ui(m: Ministry) = MinistryUi(m.id, m.name, m.description(language), m.website, m.giveUrl)
        val round = CommunityRules.round(file, month, community.includeTest)
        val vote = votes[month]
        val past = CommunityRules.pastMonths(file, now, community.includeTest).map { pastMonth ->
            val read = reads[pastMonth]
            PastRoundUi(
                month = pastMonth,
                ministries = CommunityRules.round(file, pastMonth, community.includeTest)
                    .map { ui(it) to read?.read?.counts?.get(it.id) }
                    .sortedByDescending { it.second ?: -1 },
                myPickId = votes[pastMonth]?.ministryId,
                totalsUnavailable = read != null && read.read == null,
            )
        }
        val anyListed = file.ministries.any { community.includeTest || !it.test }
        val current = reads[month]
        val showTotals = CommunityRules.showTotals(vote, month, now)
        return CommunityUiState(
            loading = false,
            empty = when {
                !anyListed -> CommunityEmpty.LIST_EMPTY
                round.isEmpty() -> CommunityEmpty.NO_MINISTRY_THIS_MONTH
                else -> null
            },
            nextMonth = if (round.isEmpty()) CommunityRules.nextMonth(file, now, community.includeTest) else null,
            month = month,
            closesAt = CommunityRules.window(month).closesAt,
            round = round.map(::ui),
            status = CommunityRules.status(vote, month, now),
            roundState = CommunityRules.roundState(current?.read, now),
            myPick = vote?.let { v -> round.firstOrNull { it.id == v.ministryId }?.let(::ui) },
            currentTotals = current?.read?.counts?.takeIf { showTotals },
            currentTotalsUnavailable = showTotals && current != null && current.read == null,
            past = past,
            sending = isSending,
            refreshing = isPulling,
            sources = sources,
        )
    }

    private fun sources(info: MinistriesFetchInfo, reads: Map<String, MonthRead>, month: String): CommunitySources {
        val latestPast = reads.filterKeys { it != month }.values.maxByOrNull { it.at }
        return CommunitySources(
            ministries = info,
            roundReadAt = reads[month]?.at,
            roundReadOk = reads[month]?.let { it.read != null },
            totalsReadAt = latestPast?.at,
            totalsReadOk = latestPast?.let { it.read != null },
        )
    }
}
