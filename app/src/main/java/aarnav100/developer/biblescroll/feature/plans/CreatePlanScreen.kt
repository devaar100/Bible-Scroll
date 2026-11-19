package aarnav100.developer.biblescroll.feature.plans

import aarnav100.developer.biblescroll.ui.text.localizedList
import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Testament
import aarnav100.developer.biblescroll.data.plans.Pace
import aarnav100.developer.biblescroll.feature.bible.picker.SectionHeader
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/** H13 pick books → H14 pace → summary → H10 start. One screen, three steps; Back steps back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlanScreen(
    onClose: () -> Unit,
    onCreated: (planId: String) -> Unit,
    viewModel: CreatePlanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CreatePlanEvent.Created -> onCreated(event.planId)
                CreatePlanEvent.RequestNotificationPermission -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    BackHandler { if (!viewModel.back()) onClose() }
    val title = stringResource(
        when (state.step) {
            CreateStep.Books -> R.string.plans_create_books_title
            CreateStep.Pace -> R.string.plans_create_pace_title
            CreateStep.Summary -> R.string.plans_create_summary_title
        },
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { if (!viewModel.back()) onClose() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                        }
                    },
                    windowInsets = WindowInsets(0),
                )
                LinearProgressIndicator(
                    progress = { (state.step.ordinal + 1) / 3f },
                    drawStopIndicator = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bottomSummary(state),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    when (state.step) {
                        CreateStep.Summary -> Button(onClick = viewModel::openStartDialog, enabled = state.preview != null) {
                            Text(stringResource(R.string.plans_start))
                        }
                        else -> Button(
                            onClick = viewModel::next,
                            enabled = state.selectedCount > 0 && (state.step == CreateStep.Books || state.preview != null),
                        ) { Text(stringResource(R.string.plans_create_next)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state.step) {
                CreateStep.Books -> BooksStep(state, viewModel::onQueryChange, viewModel::toggleBook)
                CreateStep.Pace -> PaceStep(state, viewModel::setPace)
                CreateStep.Summary -> SummaryStep(state, viewModel::setTitle)
            }
        }
    }
    state.startDialog?.let { choices ->
        StartPlanDialog(
            title = state.title,
            choices = choices,
            onChooseDate = viewModel::chooseStartDate,
            onReminderOn = viewModel::setReminderOn,
            onReminderTime = viewModel::setReminderTime,
            onConfirm = viewModel::confirmStart,
            onDismiss = viewModel::dismissStartDialog,
        )
    }
}

@Composable
private fun bottomSummary(state: CreatePlanUiState): String = when {
    state.selectedCount == 0 -> stringResource(R.string.plans_create_pick_hint)
    state.step == CreateStep.Books -> pluralStringResource(R.plurals.plans_create_books_selected, state.selectedCount, state.selectedCount)
    state.preview == null -> stringResource(R.string.plans_create_calculating)
    else -> planMeta(state.preview.days, state.preview.minutesPerDay)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BooksStep(state: CreatePlanUiState, onQuery: (String) -> Unit, onToggle: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.plans_create_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (state.query.isBlank()) {
            item(key = "quick") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.plans_create_quick_picks), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.quickPicks.forEach { book ->
                            FilterChip(
                                selected = book.selected,
                                onClick = { onToggle(book.id) },
                                label = { Text(book.name) },
                                leadingIcon = if (book.selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else null,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        for (testament in Testament.entries) {
            val rows = state.books.filter { it.testament == testament }
            if (rows.isEmpty()) continue
            stickyHeader(key = "header-$testament") {
                SectionHeader(stringResource(if (testament == Testament.OLD) R.string.picker_old_testament else R.string.picker_new_testament))
            }
            items(rows, key = { it.id }) { book ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = book.selected, role = Role.Checkbox, onValueChange = { onToggle(book.id) })
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = book.selected, onCheckedChange = null)
                    Spacer(Modifier.width(16.dp))
                    Text(book.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PaceStep(state: CreatePlanUiState, onPace: (Pace) -> Unit) {
    val pace = state.pace
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val byMinutes = pace is Pace.MinutesPerDay
            SegmentedButton(
                selected = byMinutes,
                onClick = { if (!byMinutes) onPace(Pace.MinutesPerDay(10)) },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.plans_create_by_minutes)) }
            SegmentedButton(
                selected = !byMinutes,
                onClick = { if (byMinutes) onPace(Pace.Days(state.preview?.days ?: 30)) },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.plans_create_by_days)) }
        }
        Spacer(Modifier.height(20.dp))
        when (pace) {
            is Pace.MinutesPerDay -> {
                Column(Modifier.selectableGroup()) {
                    val custom = pace.value !in Pace.MINUTE_CHOICES
                    Pace.MINUTE_CHOICES.forEach { minutes ->
                        PaceOption(
                            label = pluralStringResource(R.plurals.plans_create_minutes, minutes, minutes),
                            selected = pace.value == minutes,
                            onClick = { onPace(Pace.MinutesPerDay(minutes)) },
                        )
                    }
                    PaceOption(
                        label = stringResource(R.string.plans_create_custom),
                        selected = custom,
                        onClick = { if (!custom) onPace(Pace.MinutesPerDay(25)) },
                    )
                    if (custom) {
                        Text(pluralStringResource(R.plurals.plans_create_minutes, pace.value, pace.value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        Slider(
                            value = pace.value.toFloat(),
                            onValueChange = { onPace(Pace.MinutesPerDay(it.roundToInt())) },
                            valueRange = Pace.MINUTES_RANGE.first.toFloat()..Pace.MINUTES_RANGE.last.toFloat(),
                        )
                    }
                }
            }
            is Pace.Days -> {
                Text(stringResource(R.string.plans_create_days_label), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = pace.value.toString(),
                    onValueChange = { text -> text.filter(Char::isDigit).take(3).toIntOrNull()?.let { onPace(Pace.Days(it)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOf(7, 14, 30, 60, 90, 365).forEach { days ->
                        FilterChip(selected = pace.value == days, onClick = { onPace(Pace.Days(days)) }, label = { Text(pluralStringResource(R.plurals.plans_days, days, days)) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.plans_create_result), style = MaterialTheme.typography.labelLarge)
                Text(
                    state.preview?.let { planMeta(it.days, it.minutesPerDay) } ?: stringResource(R.string.plans_create_calculating),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (pace is Pace.Days && state.preview != null && state.preview.days < pace.value) {
                    Text(stringResource(R.string.plans_create_days_capped), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PaceOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SummaryStep(state: CreatePlanUiState, onTitle: (String) -> Unit) {
    val preview = state.preview
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitle,
            label = { Text(stringResource(R.string.plans_create_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (preview != null) {
            Spacer(Modifier.height(16.dp))
            Text(planMeta(preview.days, preview.minutesPerDay), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.plans_create_first_days),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
            )
            preview.firstDays.forEachIndexed { i, passages ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.plans_day_number, i + 1), style = MaterialTheme.typography.titleSmall)
                    Text(localizedList(passages), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (preview.days > preview.firstDays.size) {
                Text(
                    pluralStringResource(R.plurals.plans_create_more_days, preview.days - preview.firstDays.size, preview.days - preview.firstDays.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
