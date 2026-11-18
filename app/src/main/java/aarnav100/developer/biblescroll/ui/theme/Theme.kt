package aarnav100.developer.biblescroll.ui.theme

import aarnav100.developer.biblescroll.data.settings.AccentPreset
import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import aarnav100.developer.biblescroll.data.settings.ThemeMode
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Also used by the Glance widget theme. */
internal val LightColors = lightColorScheme(
    primary = Crimson,
    onPrimary = Color.White,
    primaryContainer = CrimsonContainer,
    onPrimaryContainer = OnCrimsonContainer,
    secondary = Taupe,
    onSecondary = Color.White,
    secondaryContainer = TaupeContainer,
    onSecondaryContainer = OnTaupeContainer,
    tertiary = Gold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Color(0xFF261900),
    background = PaperBackground,
    onBackground = Ink,
    surface = PaperSurface,
    onSurface = Ink,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PaperSurfaceContainerLowest,
    surfaceContainerLow = PaperSurfaceContainerLow,
    surfaceContainer = PaperSurfaceContainer,
    surfaceContainerHigh = PaperSurfaceContainerHigh,
    surfaceContainerHighest = PaperSurfaceContainerHighest,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    error = ErrorLight,
)

internal val DarkColors = darkColorScheme(
    primary = CrimsonLight,
    onPrimary = Color(0xFF5E0718),
    primaryContainer = CrimsonDarkContainer,
    onPrimaryContainer = OnCrimsonDarkContainer,
    secondary = TaupeLight,
    onSecondary = Color(0xFF3A2E25),
    secondaryContainer = TaupeDarkContainer,
    onSecondaryContainer = OnTaupeDarkContainer,
    tertiary = GoldLight,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = GoldDarkContainer,
    onTertiaryContainer = GoldContainer,
    background = CharcoalBackground,
    onBackground = Chalk,
    surface = CharcoalSurface,
    onSurface = Chalk,
    surfaceVariant = CharcoalSurfaceVariant,
    onSurfaceVariant = ChalkMuted,
    surfaceContainerLowest = CharcoalSurfaceContainerLowest,
    surfaceContainerLow = CharcoalSurfaceContainerLow,
    surfaceContainer = CharcoalSurfaceContainer,
    surfaceContainerHigh = CharcoalSurfaceContainerHigh,
    surfaceContainerHighest = CharcoalSurfaceContainerHighest,
    outline = CharcoalOutline,
    outlineVariant = CharcoalOutlineVariant,
    error = ErrorDark,
)

/** App-specific colours that don't map onto a Material 3 role. */
@Immutable
data class ExtendedColors(
    val wordsOfJesus: Color,
    val like: Color,
    val streak: Color,
    val success: Color,
    /** Read-chapter tiles (B4). */
    val successContainer: Color,
    val onSuccessContainer: Color,
)

private val LightExtended = ExtendedColors(
    wordsOfJesus = Crimson,
    like = Crimson,
    streak = StreakOrangeLight,
    success = SuccessGreenLight,
    successContainer = SuccessContainerLight,
    onSuccessContainer = OnSuccessContainerLight,
)

private val DarkExtended = ExtendedColors(
    wordsOfJesus = CrimsonLight,
    like = CrimsonLight,
    streak = StreakOrangeDark,
    success = SuccessGreenDark,
    successContainer = SuccessContainerDark,
    onSuccessContainer = OnSuccessContainerDark,
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

private val LocalReadingColors = staticCompositionLocalOf { ReadingBackground.Charcoal.colors() }

/** Scripture size multiplier (S4); applied on top of the system font scale by [scriptureSp]. */
private val LocalScriptureScale = staticCompositionLocalOf { 1f }

/** The wallpaper accent pair when "Match my wallpaper" is on and supported, else null. */
private val LocalDynamicAccent = staticCompositionLocalOf<AccentTones?> { null }

private val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Whether the app chrome (not the feed) is dark for [mode]. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * App theme from the user's settings. Changing [settings] recomposes in place: no activity
 * recreation, so screen state (including the feed position) survives.
 */
@Composable
fun BibleScrollTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val dark = settings.themeMode.isDark()
    val context = LocalContext.current
    val useDynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicAccent = if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        remember(context) {
            AccentTones(
                onLight = dynamicLightColorScheme(context).primary,
                onDark = dynamicDarkColorScheme(context).primary,
            )
        }
    } else {
        null
    }

    val colorScheme = if (useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // Wallpaper colours for the accent roles only; surfaces stay our warm paper/charcoal.
        val wallpaper = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        (if (dark) DarkColors else LightColors).copy(
            primary = wallpaper.primary,
            onPrimary = wallpaper.onPrimary,
            primaryContainer = wallpaper.primaryContainer,
            onPrimaryContainer = wallpaper.onPrimaryContainer,
        )
    } else {
        val roles = accentRoles(settings.accent.tones().on(dark), dark)
        (if (dark) DarkColors else LightColors).copy(
            primary = roles.primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
        )
    }
    val baseExtended = if (dark) DarkExtended else LightExtended
    val extended = baseExtended.copy(
        like = colorScheme.primary,
        wordsOfJesus = if (settings.redLetters) baseExtended.wordsOfJesus else colorScheme.onSurface,
    )

    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalReadingColors provides settings.readingBackground.colors(),
        LocalScriptureScale provides settings.scriptureSize.multiplier,
        LocalDynamicAccent provides dynamicAccent,
        LocalAppSettings provides settings,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}

/**
 * Re-themes [content] for a reading background (the feed, and S3/S4 previews): the Material roles
 * the feed uses map onto [background]'s colours, likes use the accent tuned for it, and Words of
 * Jesus use its own red. Defaults to the user's chosen background.
 */
@Composable
fun ReadingTheme(
    background: ReadingBackground = LocalAppSettings.current.readingBackground,
    accent: AccentPreset = LocalAppSettings.current.accent,
    redLetters: Boolean = LocalAppSettings.current.redLetters,
    content: @Composable () -> Unit,
) {
    val reading = background.colors()
    val dynamic = LocalDynamicAccent.current?.on(reading.isDark)
    val accentColor = readingAccent(reading, accent, dynamic)
    val base = MaterialTheme.colorScheme
    val scheme = base.copy(
        primary = accentColor,
        background = reading.background,
        onBackground = reading.text,
        surface = reading.background,
        onSurface = reading.text,
        onSurfaceVariant = reading.secondaryText,
        surfaceContainerHigh = reading.container,
    )
    val extended = BibleScrollTheme.extendedColors.copy(
        like = accentColor,
        wordsOfJesus = if (redLetters) reading.wordsOfJesus else reading.text,
    )
    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalReadingColors provides reading,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
    }
}

/** Access via `BibleScrollTheme.extendedColors.like` etc. */
object BibleScrollTheme {
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

    val readingColors: ReadingColors
        @Composable @ReadOnlyComposable get() = LocalReadingColors.current

    val scriptureScale: Float
        @Composable @ReadOnlyComposable get() = LocalScriptureScale.current

    val settings: AppSettings
        @Composable @ReadOnlyComposable get() = LocalAppSettings.current
}

/** A Scripture font size scaled by the S4 setting (sp already follows the system font scale). */
@Composable
@ReadOnlyComposable
fun scriptureSp(base: TextUnit): TextUnit = base * LocalScriptureScale.current
