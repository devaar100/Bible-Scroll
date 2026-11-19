package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.ui.text.localizedList
import aarnav100.developer.biblescroll.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/** H12: all days with read status (tap to read), then notes written on verses within this plan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDaysScreen(
    onBack: () -> Unit,
    onOpenDay: (enrollmentId: String, day: Int) -> Unit,
    viewModel: PlanDaysViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans_days_and_notes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        when (val s = state) {
            PlanDaysUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            PlanDaysUiState.NotFound -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.plans_not_found))
            }
            is PlanDaysUiState.Ready -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(s.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            pluralStringResource(R.plurals.plans_progress, s.totalDays, s.daysDone, s.totalDays),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(s.days, key = { "day-${it.dayNumber}" }) { day ->
                    DayRowItem(day, onClick = { onOpenDay(s.enrollmentId, day.dayNumber) })
                    HorizontalDivider(Modifier.padding(start = 64.dp))
                }
                item(key = "notes-title") {
                    Text(
                        stringResource(R.string.plans_notes_heading),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp).semantics { heading() },
                    )
                }
                if (s.notes.isEmpty()) {
                    item(key = "notes-empty") {
                        Text(
                            stringResource(R.string.plans_notes_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                items(s.notes, key = { "note-${it.ref}" }) { note ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = stringResource(R.string.plans_day_number, note.dayNumber)) { onOpenDay(s.enrollmentId, note.dayNumber) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.two_parts, note.reference, stringResource(R.string.plans_day_number, note.dayNumber)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(note.text, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRowItem(day: PlanDayStatus, onClick: () -> Unit) {
    val status = stringResource(
        when {
            day.read -> R.string.plans_day_status_read
            day.isNext -> R.string.plans_day_status_next
            else -> R.string.plans_day_status_unread
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.plans_day_read_it), onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                day.read -> Icons.Filled.CheckCircle
                day.isNext -> Icons.Outlined.PlayCircle
                else -> Icons.Outlined.Circle
            },
            contentDescription = status,
            tint = if (day.read || day.isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(dayHeading(day.dayNumber, day.title), style = MaterialTheme.typography.titleSmall)
            Text(localizedList(day.passages), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
