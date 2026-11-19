package aarnav100.developer.biblescroll.feature.streak

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.domain.streak.DayStatus
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Home header chip: flame + current streak. Opens the H2 sheet. */
@Composable
fun StreakChip(state: StreakUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val current = (state as? StreakUiState.Ready)?.current ?: 0
    val label = pluralStringResource(R.plurals.streak_chip_description, current, current)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = BibleScrollTheme.extendedColors.streak)
            Spacer(Modifier.width(4.dp))
            Text(current.toString(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** H2: current and longest streak, this week, a month calendar and the grace-day setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakSheet(
    state: StreakUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGraceDayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        if (state is StreakUiState.Ready) {
            StreakSheetContent(state, onPreviousMonth, onNextMonth, onGraceDayChange)
        } else {
            Spacer(Modifier.height(240.dp))
        }
    }
}

@Composable
internal fun StreakSheetContent(
    state: StreakUiState.Ready,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onGraceDayChange: (Boolean) -> Unit,
) {
    val streak = BibleScrollTheme.extendedColors.streak
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = streak, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                Text(
                    pluralStringResource(R.plurals.streak_current, state.current, state.current),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    pluralStringResource(R.plurals.streak_longest, state.longest, state.longest),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            stringResource(
                when {
                    state.todayCounts -> R.string.streak_message_done
                    state.current > 0 -> R.string.streak_message_keep_going
                    else -> R.string.streak_message_start
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            stringResource(R.string.streak_how_it_counts),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        SectionTitle(stringResource(R.string.streak_this_week))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.week.forEach { cell ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        cell.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                    Spacer(Modifier.height(6.dp))
                    DayDot(cell, isToday = cell.date == state.today, size = 36.dp)
                }
            }
        }

        MonthCalendar(state, onPreviousMonth, onNextMonth)
        Legend(state)

        HorizontalDivider(Modifier.padding(top = 20.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.streak_grace_title)) },
            supportingContent = { Text(stringResource(R.string.streak_grace_body)) },
            trailingContent = { Switch(checked = state.graceDayEnabled, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(
                value = state.graceDayEnabled,
                role = Role.Switch,
                onValueChange = onGraceDayChange,
            ),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 24.dp, bottom = 10.dp).semantics { heading() },
    )
}

@Composable
private fun MonthCalendar(state: StreakUiState.Ready, onPrevious: () -> Unit, onNext: () -> Unit) {
    val title = state.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClick = onPrevious, enabled = state.canShowPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.streak_previous_month))
        }
        IconButton(onClick = onNext, enabled = state.canShowNextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.streak_next_month))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).clearAndSetSemantics { },
                textAlign = TextAlign.Center,
            )
        }
    }
    // Monday-first grid with blanks before the 1st.
    val leading = state.month.atDay(1).dayOfWeek.value - 1
    val cells: List<DayCell?> = List(leading) { null } + state.monthDays
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            for (i in 0 until 7) {
                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                    week.getOrNull(i)?.let { DayDot(it, isToday = it.date == state.today, size = 32.dp, showNumber = true) }
                }
            }
        }
    }
}

@Composable
private fun Legend(state: StreakUiState.Ready) {
    val shown = (state.week + state.monthDays).map { it.status }.toSet()
    val items = buildList {
        add(DayStatus.Done to R.string.streak_legend_done)
        if (state.graceDayEnabled || DayStatus.Grace in shown) add(DayStatus.Grace to R.string.streak_legend_grace)
        if (DayStatus.Travel in shown) add(DayStatus.Travel to R.string.streak_legend_travel)
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { (status, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(status, isToday = false, size = 16.dp, number = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DayDot(cell: DayCell, isToday: Boolean, size: Dp, showNumber: Boolean = false) {
    val date = cell.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    val status = stringResource(
        when (cell.status) {
            DayStatus.Done -> R.string.streak_day_done
            DayStatus.Grace -> R.string.streak_day_grace
            DayStatus.Travel -> R.string.streak_day_travel
            DayStatus.Open -> R.string.streak_day_open
            DayStatus.Missed, DayStatus.NotStarted -> R.string.streak_day_none
            DayStatus.Future -> R.string.streak_day_future
        },
    )
    val dayDescription = stringResource(R.string.streak_day_cd, date, status)
    Box(Modifier.clearAndSetSemantics { contentDescription = dayDescription }) {
        Dot(cell.status, isToday, size, if (showNumber) cell.date.dayOfMonth else null)
    }
}

@Composable
private fun Dot(status: DayStatus, isToday: Boolean, size: Dp, number: Int?) {
    val streak = BibleScrollTheme.extendedColors.streak
    val scheme = MaterialTheme.colorScheme
    val base = Modifier.size(size)
    when (status) {
        DayStatus.Done -> Box(base.background(streak, CircleShape), contentAlignment = Alignment.Center) {
            if (number != null) DayNumber(number, OnStreak) else Icon(Icons.Filled.Check, null, tint = OnStreak, modifier = Modifier.size(size * 0.55f))
        }
        DayStatus.Grace, DayStatus.Travel -> Box(base.border(2.dp, streak, CircleShape), contentAlignment = Alignment.Center) {
            Icon(
                if (status == DayStatus.Grace) Icons.Outlined.Shield else Icons.Outlined.Flight,
                contentDescription = null,
                tint = streak,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        DayStatus.Open -> Box(base.border(2.dp, scheme.outline, CircleShape), contentAlignment = Alignment.Center) {
            if (number != null) DayNumber(number, scheme.onSurface)
        }
        DayStatus.Future -> Box(base, contentAlignment = Alignment.Center) {
            if (number != null) DayNumber(number, scheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        DayStatus.Missed, DayStatus.NotStarted -> Box(
            base.background(scheme.surfaceContainerHighest, CircleShape).then(
                if (isToday) Modifier.border(2.dp, scheme.outline, CircleShape) else Modifier,
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (number != null) DayNumber(number, scheme.onSurfaceVariant)
        }
    }
}

/** Dark ink on the streak orange: at least 5:1 on both the light and dark theme oranges (white is ~3.6:1). */
private val OnStreak = Color(0xFF1A1A1A)

@Composable
private fun DayNumber(number: Int, color: Color) {
    Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = color)
}
