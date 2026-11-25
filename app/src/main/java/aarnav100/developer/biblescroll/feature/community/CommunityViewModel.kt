package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.core.content.MinistriesFetchInfo
import aarnav100.developer.biblescroll.core.content.MinistriesProblem
import aarnav100.developer.biblescroll.core.content.MinistriesState
import aarnav100.developer.biblescroll.core.content.MinistryImages
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
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

@Immutable
data class MinistryUi(
    val id: String,
    val name: String,
    val description: String,
    val website: String,
    val giveUrl: String,
    /** From ministries.json `category` (comma-separated), trimmed; may be empty. */
    val categories: List<String> = emptyList(),
    /** ministries.json `image`; null = tinted fallback with a category icon. */
    val image: String? = null,
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
    /** When this month's counters were last read (only to tell whether the round is open), and whether it worked. */
    val roundReadAt: Instant?,
    val roundReadOk: Boolean?,
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
    /** Whole days until [closesAt], rounded up ("Closes in 3 days"); 0 on the last day; null when closed or unknown. */
    val daysLeft: Int? = null,
    /** This month's round. */
    val round: List<MinistryUi> = emptyList(),
    val status: VoteStatus = VoteStatus.CLOSED,
    /** The server side of this month's round (counters set up, window open). */
    val roundState: RoundState = RoundState.UNKNOWN,
    val myPick: MinistryUi? = null,
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
 * C1 this month's vote. No vote counts or percentages are shown anywhere (decision 23); the counters are read only to
 * tell whether the round is set up and open. Past months are on the History screen (donations, not votes).
 *
 * Refreshing: the ministry list is checked when the screen is shown if it's missing, over 6 hours old, or the cached
 * manifest pins a different hash (CommunityContentRepository.refreshIfStale); pull to refresh and Try again always
 * check. Counters (Firestore) are read when the screen opens, when the months shown change, and on every refresh.
 */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val community: CommunityRepository,
    private val images: MinistryImages,
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
        // This month's counters, only for the round's state (set up / open / closed).
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
                // Refused: find out why (not set up, or closed).
                if (result == VoteResult.REFUSED) read(mapOf(state.month to state.round.map { it.id }.sorted()), force = true)
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
    }

    /** A ministry card image as a local file (downloaded once from our Firebase hosts), or null for the fallback. */
    suspend fun imageFile(url: String): File? = images.file(url)

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
        fun ui(m: Ministry) = MinistryUi(m.id, m.name, m.description(language), m.website, m.giveUrl, m.categories(language), m.image)
        val round = CommunityRules.round(file, month, community.includeTest)
        val vote = votes[month]
        val anyListed = file.ministries.any { community.includeTest || !it.test }
        val current = reads[month]
        val closesAt = CommunityRules.window(month).closesAt
        return CommunityUiState(
            loading = false,
            empty = when {
                !anyListed -> CommunityEmpty.LIST_EMPTY
                round.isEmpty() -> CommunityEmpty.NO_MINISTRY_THIS_MONTH
                else -> null
            },
            nextMonth = if (round.isEmpty()) CommunityRules.nextMonth(file, now, community.includeTest) else null,
            month = month,
            closesAt = closesAt,
            daysLeft = daysLeft(now, closesAt),
            round = round.map(::ui),
            status = CommunityRules.status(vote, month, now),
            roundState = CommunityRules.roundState(current?.read, now),
            myPick = vote?.let { v -> round.firstOrNull { it.id == v.ministryId }?.let(::ui) },
            sending = isSending,
            refreshing = isPulling,
            sources = sources,
        )
    }

    private fun sources(info: MinistriesFetchInfo, reads: Map<String, MonthRead>, month: String) = CommunitySources(
        ministries = info,
        roundReadAt = reads[month]?.at,
        roundReadOk = reads[month]?.let { it.read != null },
    )
}

/**
 * Calendar days left until [closesAt] in the phone's time zone: 0 when it closes later today, 1 tomorrow, and so on;
 * null once it has closed. (The round closes at UTC month end; people read the date in their own time.)
 */
fun daysLeft(now: Instant, closesAt: Instant, zone: ZoneId = ZoneId.systemDefault()): Int? {
    if (!now.isBefore(closesAt)) return null
    // closesAt is the first instant of the next month: the last moment to vote is just before it.
    val lastDay = closesAt.minusMillis(1).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), lastDay).toInt().coerceAtLeast(0)
}
