package aarnav100.developer.biblescroll.feature.settings

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.data.settings.AccentPreset
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import aarnav100.developer.biblescroll.data.settings.ScriptureSize
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import aarnav100.developer.biblescroll.ui.theme.colors
import aarnav100.developer.biblescroll.ui.theme.isDark
import aarnav100.developer.biblescroll.ui.theme.tones
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import aarnav100.developer.biblescroll.data.settings.ScrubberMode

/** S1: every Settings section (S2 General, S6 Notifications, Storage, S9 Data & backup, S5 Widget guide, S7 Help, S8 Legal). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenDataBackup: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenWidgetGuide: () -> Unit,
    onOpenLegal: () -> Unit,
) {
    SettingsPage(title = stringResource(R.string.settings_title), onBack = onBack) {
        NavRow(Icons.Outlined.Tune, stringResource(R.string.settings_general), stringResource(R.string.settings_general_summary), onOpenGeneral)
        NavRow(
            Icons.Outlined.Notifications,
            stringResource(R.string.settings_notifications),
            stringResource(R.string.settings_notifications_summary),
            onOpenNotifications,
        )
        NavRow(
            Icons.Outlined.Storage,
            stringResource(R.string.settings_storage),
            stringResource(R.string.settings_storage_summary),
            onOpenStorage,
        )
        NavRow(
            Icons.Outlined.Backup,
            stringResource(R.string.settings_data_backup),
            stringResource(R.string.settings_data_backup_summary),
            onOpenDataBackup,
        )
        NavRow(Icons.Outlined.Widgets, stringResource(R.string.widget_guide_title), stringResource(R.string.widget_guide_summary), onOpenWidgetGuide)
        NavRow(
            Icons.AutoMirrored.Outlined.HelpOutline,
            stringResource(R.string.help_title),
            stringResource(R.string.help_summary),
            onOpenHelp,
        )
        NavRow(Icons.Outlined.Gavel, stringResource(R.string.legal_title), stringResource(R.string.legal_summary), onOpenLegal)
    }
}

/** S2. */
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenFontSize: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.settings_general), onBack = onBack) {
        NavRow(
            Icons.Outlined.Palette,
            stringResource(R.string.settings_appearance),
            stringResource(R.string.two_parts, stringResource(settings.themeMode.label), stringResource(settings.readingBackground.label)),
            onOpenAppearance,
        )
        NavRow(
            Icons.Outlined.FormatSize,
            stringResource(R.string.settings_font_size),
            stringResource(settings.scriptureSize.label),
            onOpenFontSize,
        )
        LanguageRow()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_red_letters)) },
            supportingContent = { Text(stringResource(R.string.settings_red_letters_summary)) },
            trailingContent = { Switch(checked = settings.redLetters, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = settings.redLetters,
                role = Role.Switch,
                onValueChange = viewModel::setRedLetters,
            ),
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_hide_ai_studies)) },
            supportingContent = { Text(stringResource(R.string.settings_hide_ai_studies_summary)) },
            trailingContent = { Switch(checked = settings.hideAiStudies, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = settings.hideAiStudies,
                role = Role.Switch,
                onValueChange = viewModel::setHideAiStudies,
            ),
        )
        VerseScrubberRow(settings.verseScrubber, viewModel::setVerseScrubber)
    }
}

/** S2 "Verse scrubber": Always (default) / Only while scrolling / Off. */
@Composable
private fun VerseScrubberRow(selected: ScrubberMode, onSelect: (ScrubberMode) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_verse_scrubber)) },
        supportingContent = { Text(stringResource(selected.label)) },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.settings_verse_scrubber)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    ScrubberMode.entries.forEach { mode ->
                        ListItem(
                            headlineContent = { Text(stringResource(mode.label)) },
                            leadingContent = { RadioButton(selected = mode == selected, onClick = null) },
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = mode == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(mode)
                                    open = false
                                },
                            ),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.notifications_cancel)) } },
        )
    }
}

/** S3 Appearance (proposal #5). */
@Composable
fun AppearanceScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val verse by viewModel.previewVerse.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.settings_appearance), onBack = onBack) {
        VersePreviewCard(
            verse = verse,
            reference = stringResource(R.string.settings_preview_reference),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        SectionHeader(stringResource(R.string.settings_app_mode))
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 24.dp)) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(stringResource(mode.label)) }
            }
        }

        SectionHeader(stringResource(R.string.settings_reading_background))
        SwatchRow {
            ReadingBackground.entries.forEach { bg ->
                val c = bg.colors()
                ColorSwatch(
                    color = c.background,
                    sampleTextColor = c.text,
                    label = stringResource(bg.label),
                    selected = settings.readingBackground == bg,
                    onClick = { viewModel.setReadingBackground(bg) },
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_accent))
        val dynamicOn = settings.dynamicColor && DynamicColorSupported
        val appDark = settings.themeMode.isDark()
        Column(Modifier.selectableGroup()) {
            AccentPreset.entries.chunked(3).forEach { row ->
                SwatchRow {
                    row.forEach { accent ->
                        ColorSwatch(
                            color = accent.tones().on(appDark),
                            label = stringResource(accent.label),
                            selected = !dynamicOn && settings.accent == accent,
                            enabled = !dynamicOn,
                            onClick = { viewModel.setAccent(accent) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        if (DynamicColorSupported) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.settings_dynamic_color_summary)) },
                trailingContent = { Switch(checked = settings.dynamicColor, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = settings.dynamicColor,
                    role = Role.Switch,
                    onValueChange = viewModel::setDynamicColor,
                ),
            )
        }
    }
}

/** S4. The multiplier sits on top of the system font scale and only affects Scripture text. */
@Composable
fun FontSizeScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val verse by viewModel.previewVerse.collectAsStateWithLifecycle()
    SettingsPage(title = stringResource(R.string.settings_font_size), onBack = onBack) {
        VersePreviewCard(
            verse = verse,
            reference = stringResource(R.string.settings_preview_reference),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Text(
            stringResource(R.string.settings_font_size_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            ScriptureSize.entries.forEach { size ->
                ListItem(
                    headlineContent = { Text(stringResource(size.label)) },
                    leadingContent = { RadioButton(selected = settings.scriptureSize == size, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = settings.scriptureSize == size,
                        role = Role.RadioButton,
                        onClick = { viewModel.setScriptureSize(size) },
                    ),
                )
            }
        }
    }
}

@Composable
internal fun NavRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private val DynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@get:StringRes
private val ThemeMode.label get() = when (this) {
    ThemeMode.Light -> R.string.settings_mode_light
    ThemeMode.Dark -> R.string.settings_mode_dark
    ThemeMode.System -> R.string.settings_mode_system
}

@get:StringRes
private val ReadingBackground.label get() = when (this) {
    ReadingBackground.Paper -> R.string.settings_bg_paper
    ReadingBackground.Sepia -> R.string.settings_bg_sepia
    ReadingBackground.Charcoal -> R.string.settings_bg_charcoal
    ReadingBackground.TrueBlack -> R.string.settings_bg_true_black
}

@get:StringRes
private val AccentPreset.label get() = when (this) {
    AccentPreset.Crimson -> R.string.settings_accent_crimson
    AccentPreset.Rose -> R.string.settings_accent_rose
    AccentPreset.Amber -> R.string.settings_accent_amber
    AccentPreset.Sage -> R.string.settings_accent_sage
    AccentPreset.Teal -> R.string.settings_accent_teal
    AccentPreset.Indigo -> R.string.settings_accent_indigo
}

@get:StringRes
private val ScrubberMode.label get() = when (this) {
    ScrubberMode.Always -> R.string.settings_verse_scrubber_always
    ScrubberMode.WhileScrolling -> R.string.settings_verse_scrubber_while_scrolling
    ScrubberMode.Off -> R.string.settings_verse_scrubber_off
}

@get:StringRes
private val ScriptureSize.label get() = when (this) {
    ScriptureSize.Default -> R.string.settings_size_default
    ScriptureSize.Large -> R.string.settings_size_large
    ScriptureSize.Larger -> R.string.settings_size_larger
    ScriptureSize.Largest -> R.string.settings_size_largest
}
