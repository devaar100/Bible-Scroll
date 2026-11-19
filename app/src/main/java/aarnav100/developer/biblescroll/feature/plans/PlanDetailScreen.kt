package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.ui.text.localizedList
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.plans.MyPlan
import aarnav100.developer.biblescroll.feature.settings.ReminderTimeDialog
import android.Manifest
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val COLLAPSED_DAYS = 7

/** H9 plan detail, with the H10 start dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onReadDay: (enrollmentId: String, day: Int) -> Unit,
    onOpenDays: (enrollmentId: String) -> Unit,
    viewModel: PlanDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val startedToday = stringResource(R.string.plans_started_today)
    val startsTomorrow = stringResource(R.string.plans_started_tomorrow)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PlanDetailEvent.Started ->
                    snackbar.showSnackbar(if (event.startDate.isAfter(event.today)) startsTomorrow else startedToday)
                PlanDetailEvent.RequestNotificationPermission -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                PlanDetailEvent.Deleted -> onBack()
            }
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    if ((state as? PlanDetailUiState.Ready)?.custom == true) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.plans_delete))
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            )
        },
    ) { padding ->
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.plans_delete_title)) },
                text = { Text(stringResource(R.string.plans_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.deletePlan()
                    }) { Text(stringResource(R.string.plans_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.plans_cancel)) } },
            )
        }
        when (val s = state) {
            PlanDetailUiState.Loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            PlanDetailUiState.NotFound -> Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.plans_not_found), style = MaterialTheme.typography.bodyLarge)
            }
            is PlanDetailUiState.Ready -> {
                PlanDetailContent(
                    s, Modifier.padding(padding),
                    onStart = viewModel::openStartDialog,
                    onReadDay = onReadDay,
                    onOpenDays = onOpenDays,
                    onAdjustSchedule = viewModel::adjustSchedule,
                )
                s.startDialog?.let { choices ->
                    StartPlanDialog(
                        title = s.title,
                        choices = choices,
                        onChooseDate = viewModel::chooseStartDate,
                        onReminderOn = viewModel::setReminderOn,
                        onReminderTime = viewModel::setReminderTime,
                        onConfirm = viewModel::confirmStart,
                        onDismiss = viewModel::dismissStartDialog,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanDetailContent(
    s: PlanDetailUiState.Ready,
    modifier: Modifier,
    onStart: () -> Unit,
    onReadDay: (String, Int) -> Unit,
    onOpenDays: (String) -> Unit,
    onAdjustSchedule: () -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val shownDays = if (showAll) s.days else s.days.take(COLLAPSED_DAYS)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
        item(key = "header") {
            Column {
                Text(s.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(s.shortDescription, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.two_parts, planMeta(s.totalDays, s.minutesPerDay), difficultyLabel(s.difficulty)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (s.description != s.shortDescription) { // custom plans have only the one line
                    Spacer(Modifier.height(16.dp))
                    Text(s.description, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(20.dp))
                when {
                    s.active != null -> ProgressCard(s.active, s.nextDay, s.daysToCatchUp, onReadDay, onOpenDays, onAdjustSchedule)
                    else -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (s.finished != null) R.string.plans_start_again else R.string.plans_start))
                    }
                }
                if (s.active == null && s.finished != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.plans_finished_before), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.plans_days_heading),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 28.dp, bottom = 4.dp).semantics { heading() },
                )
            }
        }
        items(shownDays, key = { it.dayNumber }) { day ->
            DayItem(day)
            HorizontalDivider()
        }
        if (s.days.size > COLLAPSED_DAYS) {
            item(key = "toggle") {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        if (showAll) stringResource(R.string.plans_show_fewer_days)
                        else pluralStringResource(R.plurals.plans_show_all_days, s.days.size, s.days.size),
                    )
                }
            }
        }
        if (s.credits.isNotBlank()) {
            item(key = "credits") {
                Text(s.credits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun DayItem(day: DayRow) {
    var expanded by rememberSaveable(day.dayNumber) { mutableStateOf(false) }
    val expandable = day.intro != null
    val heading = dayHeading(day.dayNumber, day.title)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (expandable) Modifier.clickable(onClickLabel = stringResource(if (expanded) R.string.plans_day_collapse else R.string.plans_day_expand)) { expanded = !expanded } else Modifier)
            .padding(vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(heading, style = MaterialTheme.typography.titleSmall)
                Text(localizedList(day.passages), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expandable) Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        if (expandable && expanded) {
            Text(day.intro!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ProgressCard(
    plan: MyPlan,
    nextDay: Int?,
    daysToCatchUp: Int,
    onReadDay: (String, Int) -> Unit,
    onOpenDays: (String) -> Unit,
    onAdjustSchedule: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.plans_in_progress), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            PlanProgressBar(plan)
            Spacer(Modifier.height(8.dp))
            Text(myPlanStatus(plan), style = MaterialTheme.typography.bodyMedium)
            val context = LocalContext.current
            plan.reminder?.let {
                Text(stringResource(R.string.plans_reminder_at, formatTime(it, DateFormat.is24HourFormat(context))), style = MaterialTheme.typography.bodySmall)
            }
            nextDay?.let { day ->
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onReadDay(plan.enrollmentId, day) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.plans_read_day, day))
                }
            }
            if (daysToCatchUp > 0) {
                // Never "behind": just what's next, and an easy way to line the schedule up with today.
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.plans_catch_up_body), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAdjustSchedule) { Text(stringResource(R.string.plans_adjust_schedule)) }
            }
            TextButton(onClick = { onOpenDays(plan.enrollmentId) }) { Text(stringResource(R.string.plans_days_and_notes)) }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: String) = stringResource(
    when (difficulty) {
        "easy" -> R.string.plans_difficulty_easy
        "hard" -> R.string.plans_difficulty_hard
        else -> R.string.plans_difficulty_medium
    },
)

private fun formatTime(time: LocalTime, is24Hour: Boolean): String =
    time.format(DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a"))

/** H10: start today or tomorrow, with an optional plan reminder. */
@Composable
internal fun StartPlanDialog(
    title: String,
    choices: StartChoices,
    onChooseDate: (LocalDate) -> Unit,
    onReminderOn: (Boolean) -> Unit,
    onReminderTime: (LocalTime) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plans_start_dialog_title, title)) },
        text = {
            Column {
                Text(stringResource(R.string.plans_start_when), style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    choices.startOptions.forEachIndexed { i, date ->
                        val label = stringResource(
                            R.string.two_parts,
                            stringResource(if (i == 0) R.string.plans_start_today else R.string.plans_start_tomorrow),
                            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = choices.startDate == date, role = Role.RadioButton, onClick = { onChooseDate(date) })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choices.startDate == date, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = choices.reminderOn, role = Role.Switch, onValueChange = onReminderOn)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.plans_reminder_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.plans_reminder_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = choices.reminderOn, onCheckedChange = null)
                }
                if (choices.reminderOn) {
                    TextButton(onClick = { pickingTime = true }) {
                        Text(stringResource(R.string.plans_reminder_time, formatTime(choices.reminderTime, is24Hour)))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.plans_start)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.plans_cancel)) } },
    )
    if (pickingTime) {
        ReminderTimeDialog(
            initial = choices.reminderTime,
            is24Hour = is24Hour,
            onConfirm = {
                onReminderTime(it)
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}
