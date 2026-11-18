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

/** S1. Only Phase 1 sections that exist are listed; later ones appear when they're built. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenStorage: () -> Unit,
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
            "${stringResource(settings.themeMode.label)} · ${stringResource(settings.readingBackground.label)}",
            onOpenAppearance,
        )
        NavRow(
            Icons.Outlined.FormatSize,
            stringResource(R.string.settings_font_size),
            stringResource(settings.scriptureSize.label),
            onOpenFontSize,
        )
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
private fun NavRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
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
private val ScriptureSize.label get() = when (this) {
    ScriptureSize.Default -> R.string.settings_size_default
    ScriptureSize.Large -> R.string.settings_size_large
    ScriptureSize.Larger -> R.string.settings_size_larger
    ScriptureSize.Largest -> R.string.settings_size_largest
}
