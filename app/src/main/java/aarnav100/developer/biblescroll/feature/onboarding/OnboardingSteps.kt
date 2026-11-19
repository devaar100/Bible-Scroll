package aarnav100.developer.biblescroll.feature.onboarding

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.bible.model.Translation
import aarnav100.developer.biblescroll.feature.bible.picker.translationDescription
import aarnav100.developer.biblescroll.feature.bible.redLetterText
import aarnav100.developer.biblescroll.feature.settings.ReminderTimeDialog
import aarnav100.developer.biblescroll.feature.settings.formatReminderTime
import aarnav100.developer.biblescroll.feature.settings.is24HourClock
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.ReadingTheme
import aarnav100.developer.biblescroll.ui.theme.ScriptureTextStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import java.time.LocalTime

// ---- Shared layout ----

/** Top bar (Back, optional actions), scrollable body, pinned bottom area. */
@Composable
internal fun StepScaffold(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onb_back))
                }
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        // Scrolls when text is large; at least viewport-tall so short steps can still be centred.
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()).heightIn(min = maxHeight) else Modifier.fillMaxSize())
                    .padding(horizontal = 24.dp),
                verticalArrangement = verticalArrangement,
                content = content,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = bottom,
        )
    }
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
    )
}

// ---- O2 Welcome ----

@Composable
internal fun WelcomeStep(onGetStarted: () -> Unit) {
    StepScaffold(
        onBack = null,
        verticalArrangement = Arrangement.Center,
        bottom = { PrimaryButton(stringResource(R.string.onb_get_started), onGetStarted) },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(112.dp)
                .align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() },
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onb_promise),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onb_promise_detail),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(48.dp))
    }
}

// ---- O4 / O6 quizzes ----

@Composable
private fun <T> QuizStep(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    detail: @Composable (T) -> String?,
    onSelect: (T) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    note: String? = null,
) {
    StepScaffold(
        onBack = onBack,
        bottom = { PrimaryButton(stringResource(R.string.onb_continue), onContinue, enabled = selected != null) },
    ) {
        Spacer(Modifier.height(8.dp))
        StepTitle(title)
        if (note != null) {
            Spacer(Modifier.height(12.dp))
            Text(note, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { option ->
                OptionCard(
                    label = label(option),
                    detail = detail(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun OptionCard(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

// ---- Bible (Spanish UI only, after O2) ----

@Composable
internal fun BibleStep(
    options: List<Translation>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    QuizStep(
        title = stringResource(R.string.onb_bible_title),
        note = stringResource(R.string.onb_bible_note),
        options = options,
        selected = options.firstOrNull { it.id == selectedId },
        label = { it.name },
        detail = { t -> translationDescription(t.id)?.let { stringResource(it) } },
        onSelect = { onSelect(it.id) },
        onBack = onBack,
        onContinue = onContinue,
    )
}

@Composable
internal fun GoalStep(selected: Goal?, onSelect: (Goal) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    QuizStep(
        title = stringResource(R.string.onb_goal_title),
        options = Goal.entries,
        selected = selected,
        label = { stringResource(it.label) },
        detail = { stringResource(it.detail) },
        onSelect = onSelect,
        onBack = onBack,
        onContinue = onContinue,
    )
}

@Composable
internal fun TimingStep(selected: Timing?, onSelect: (Timing) -> Unit, onBack: () -> Unit, onContinue: () -> Unit) {
    QuizStep(
        title = stringResource(R.string.onb_timing_title),
        options = Timing.entries,
        selected = selected,
        label = { stringResource(it.label) },
        detail = { null },
        onSelect = onSelect,
        onBack = onBack,
        onContinue = onContinue,
    )
}

// ---- O5 Goal response ----

@Composable
internal fun GoalResponseStep(goal: Goal?, verse: ShownVerse?, onBack: () -> Unit, onContinue: () -> Unit) {
    StepScaffold(
        onBack = onBack,
        bottom = { PrimaryButton(stringResource(R.string.onb_continue), onContinue) },
    ) {
        Spacer(Modifier.height(8.dp))
        if (goal != null) StepTitle(stringResource(goal.responseTitle))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onb_response_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        VerseCallout(verse)
    }
}

/** A verse on the reading background, as it will look in the feed. */
@Composable
private fun VerseCallout(verse: ShownVerse?) {
    ReadingTheme {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
        ) {
            if (verse != null) {
                val jesus = BibleScrollTheme.extendedColors.wordsOfJesus
                val text = remember(verse, jesus) { verse.verse.redLetterText(jesus) }
                Column(Modifier.padding(24.dp)) {
                    BasicText(
                        text = text,
                        style = ScriptureTextStyle.copy(color = MaterialTheme.colorScheme.onBackground, lineHeight = 1.45.em),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(verse.reference(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun ShownVerse.reference(): String =
    stringResource(R.string.ref_verse, bookName, verse.ref.chapter, verse.ref.verse)

// ---- O7 Your plan ----

@Composable
internal fun PlanStep(
    state: OnboardingUiState,
    onBack: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val phrase = stringResource(state.goal?.planPhrase ?: R.string.onb_plan_phrase_default)
    val whenPhrase = stringResource(state.timing?.phrase ?: R.string.onb_when_daily)

    StepScaffold(
        onBack = onBack,
        bottom = { PrimaryButton(stringResource(R.string.onb_continue), onContinue) },
    ) {
        Text(
            stringResource(R.string.onb_plan_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        StepTitle(stringResource(R.string.onb_plan_headline, phrase, whenPhrase))
        Spacer(Modifier.height(20.dp))

        if (state.reminderOn) {
            NotificationPreview(verse = state.goalVerse ?: state.tour.reminder)
            Spacer(Modifier.height(16.dp))
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = state.reminderOn, role = Role.Switch, onValueChange = onToggleReminder)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.onb_plan_remind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.reminderOn, onCheckedChange = null)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (state.reminderOn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { pickingTime = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.onb_plan_time), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        formatReminderTime(context, state.reminderTime),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.onb_plan_no_reminder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (!state.reminderOn && state.timing == Timing.Whenever) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.onb_plan_widget_tip), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onb_plan_how_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        listOf(R.string.onb_plan_how_1, R.string.onb_plan_how_2, R.string.onb_plan_how_3, R.string.onb_plan_how_4).forEach { line ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = BibleScrollTheme.extendedColors.success,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(line), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (pickingTime) {
        ReminderTimeDialog(
            initial = state.reminderTime,
            is24Hour = is24HourClock(context),
            onConfirm = {
                onTimeChange(it)
                pickingTime = false
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** A made-up notification in our own style (not the system's), showing what the reminder carries. */
@Composable
internal fun NotificationPreview(verse: ShownVerse?, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onb_notification_preview_cd)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_notification),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.two_parts, stringResource(R.string.app_name), stringResource(R.string.onb_notification_now)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (verse != null) {
                Text(verse.reference(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(verse.verse.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
            } else {
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ---- O8 Notification permission ----

@Composable
internal fun PermissionStep(
    time: LocalTime,
    declined: Boolean,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    StepScaffold(
        onBack = onBack,
        verticalArrangement = Arrangement.Center,
        bottom = {
            if (declined) {
                PrimaryButton(stringResource(R.string.onb_start_reading), onFinish)
            } else {
                PrimaryButton(stringResource(R.string.onb_permission_allow), onAllow)
                TextButton(onClick = onNotNow, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.onb_permission_not_now))
                }
            }
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        if (declined) {
            StepTitle(stringResource(R.string.onb_permission_declined_title))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.onb_permission_declined_body), style = MaterialTheme.typography.bodyLarge)
        } else {
            StepTitle(stringResource(R.string.onb_permission_title))
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onb_permission_body, formatReminderTime(context, time)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.height(48.dp))
    }
}
