package aarnav100.developer.biblescroll.feature.community

import aarnav100.developer.biblescroll.core.content.MinistriesState
import aarnav100.developer.biblescroll.core.content.model.Ministry
import aarnav100.developer.biblescroll.data.community.CommunityRepository
import aarnav100.developer.biblescroll.data.community.CommunityRules
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@Immutable
data class MinistryUi(val id: String, val name: String, val description: String, val website: String, val giveUrl: String)

@Immutable
data class PastRoundUi(
    val month: String,
    /** Most votes first; counts are null when totals couldn't be loaded. */
    val ministries: List<Pair<MinistryUi, Long?>>,
    val myPickId: String?,
)

@Immutable
data class CommunityUiState(
    val loading: Boolean = true,
    /** No list cached and none could be downloaded. */
    val unavailable: Boolean = false,
    val month: String = "",
    val closesAt: Instant? = null,
    /** This month's round; empty means no spotlight right now. */
    val round: List<MinistryUi> = emptyList(),
    val status: VoteStatus = VoteStatus.CLOSED,
    val myPick: MinistryUi? = null,
    /** This month's totals: only after this phone voted (null otherwise, or while loading/offline). */
    val currentTotals: Map<String, Long>? = null,
    val past: List<PastRoundUi> = emptyList(),
    val sending: Boolean = false,
)

/** C1 this month's vote and C2 past spotlights. */
@HiltViewModel
class CommunityViewModel @Inject constructor(
    private val community: CommunityRepository,
) : ViewModel() {

    private val totals = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    private val sending = MutableStateFlow(false)
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

    val uiState: StateFlow<CommunityUiState> = combine(
        community.ministries, community.votes, clock, totals, sending,
    ) { ministries, votes, now, totals, isSending -> build(ministries, votes, now, totals, isSending) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CommunityUiState())

    init {
        viewModelScope.launch { community.refresh() }
        // Totals follow what may be shown: past rounds always, this month's once this phone has voted.
        viewModelScope.launch {
            uiState.map(::wantedTotals).distinctUntilChanged().collect { wanted ->
                if (wanted.isNotEmpty()) totals.value = totals.value + community.totals(wanted)
            }
        }
    }

    /** "Try again": the list and every total shown. */
    fun refresh() {
        viewModelScope.launch {
            community.refresh()
            val wanted = wantedTotals(uiState.value)
            if (wanted.isNotEmpty()) totals.value = totals.value + community.totals(wanted)
        }
    }

    fun askToVote(ministry: MinistryUi) {
        if (uiState.value.status == VoteStatus.CAN_VOTE) confirmTarget.value = ministry
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
                _results.send(community.vote(state.month, target.id, state.round.map { it.id }))
            } finally {
                sending.value = false
            }
        }
    }

    private fun wantedTotals(state: CommunityUiState): Map<String, List<String>> = buildMap {
        if (state.round.isNotEmpty() && state.myPick != null) put(state.month, state.round.map { it.id }.sorted())
        state.past.forEach { put(it.month, it.ministries.map { (m, _) -> m.id }.sorted()) }
    }

    private fun build(
        ministries: MinistriesState,
        votes: Map<String, CommunityVoteEntity>,
        now: Instant,
        totals: Map<String, Map<String, Long>>,
        isSending: Boolean,
    ): CommunityUiState {
        val file = (ministries as? MinistriesState.Ready)?.file
            ?: return CommunityUiState(loading = ministries == MinistriesState.Loading, unavailable = ministries == MinistriesState.Unavailable)
        val language = Locale.getDefault().language
        fun ui(m: Ministry) = MinistryUi(m.id, m.name, m.description(language), m.website, m.giveUrl)
        val month = CommunityRules.monthOf(now)
        val round = CommunityRules.round(file, month, community.includeTest)
        val vote = votes[month]
        val past = CommunityRules.pastMonths(file, now, community.includeTest).map { pastMonth ->
            val counts = totals[pastMonth]
            PastRoundUi(
                month = pastMonth,
                ministries = CommunityRules.round(file, pastMonth, community.includeTest)
                    .map { ui(it) to counts?.get(it.id) }
                    .sortedByDescending { it.second ?: -1 },
                myPickId = votes[pastMonth]?.ministryId,
            )
        }
        return CommunityUiState(
            loading = false,
            month = month,
            closesAt = CommunityRules.window(month).closesAt,
            round = round.map(::ui),
            status = CommunityRules.status(vote, month, now),
            myPick = vote?.let { v -> round.firstOrNull { it.id == v.ministryId }?.let(::ui) },
            currentTotals = totals[month]?.takeIf { CommunityRules.showTotals(vote, month, now) },
            past = past,
            sending = isSending,
        )
    }
}
