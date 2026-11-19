package aarnav100.developer.biblescroll.feature.streak

import aarnav100.developer.biblescroll.data.settings.SettingsRepository
import aarnav100.developer.biblescroll.data.streak.StreakRepository
import aarnav100.developer.biblescroll.domain.streak.DayStatus
import aarnav100.developer.biblescroll.domain.streak.StreakCalculator
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@Immutable
data class DayCell(val date: LocalDate, val status: DayStatus)

sealed interface StreakUiState {
    data object Loading : StreakUiState

    @Immutable
    data class Ready(
        val current: Int,
        val longest: Int,
        val todayCounts: Boolean,
        val today: LocalDate,
        /** Monday to Sunday. */
        val week: List<DayCell>,
        val month: YearMonth,
        val monthDays: List<DayCell>,
        val canShowPreviousMonth: Boolean,
        val canShowNextMonth: Boolean,
        val graceDayEnabled: Boolean,
    ) : StreakUiState
}

/** H2 streak sheet and the Home flame chip. */
@HiltViewModel
class StreakViewModel internal constructor(
    private val streaks: StreakRepository,
    private val settings: SettingsRepository,
    calculationDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(streaks: StreakRepository, settings: SettingsRepository) : this(streaks, settings, Dispatchers.Default)

    /** Null = the current month (follows midnight). */
    private val shownMonth = MutableStateFlow<YearMonth?>(null)

    val uiState: StateFlow<StreakUiState> = combine(
        streaks.days(), streaks.today(), settings.streakGraceDay, shownMonth,
    ) { days, today, grace, shown ->
        val calculator = StreakCalculator(days, grace)
        val summary = calculator.summary(today)
        val thisMonth = YearMonth.from(today)
        val earliest = days.filter { it.counts }.minOfOrNull { YearMonth.from(it.date) }?.coerceAtMost(thisMonth) ?: thisMonth
        val month = (shown ?: thisMonth).coerceIn(earliest, thisMonth)
        StreakUiState.Ready(
            current = summary.current,
            longest = summary.longest,
            todayCounts = summary.todayCounts,
            today = today,
            week = summary.week.map { (d, s) -> DayCell(d, s) },
            month = month,
            monthDays = calculator.month(month, today).map { (d, s) -> DayCell(d, s) },
            canShowPreviousMonth = month > earliest,
            canShowNextMonth = month < thisMonth,
            graceDayEnabled = grace,
        )
    }
        .flowOn(calculationDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreakUiState.Loading)

    fun showPreviousMonth() = moveMonth(-1)

    fun showNextMonth() = moveMonth(1)

    /** Back to the current month whenever the sheet opens. */
    fun resetMonth() {
        shownMonth.value = null
    }

    fun setGraceDay(enabled: Boolean) {
        viewModelScope.launch { settings.setStreakGraceDay(enabled) }
    }

    private fun moveMonth(by: Long) {
        val state = uiState.value as? StreakUiState.Ready ?: return
        shownMonth.value = state.month.plusMonths(by)
    }
}

private fun YearMonth.coerceIn(min: YearMonth, max: YearMonth) = if (this < min) min else if (this > max) max else this
private fun YearMonth.coerceAtMost(max: YearMonth) = if (this > max) max else this
