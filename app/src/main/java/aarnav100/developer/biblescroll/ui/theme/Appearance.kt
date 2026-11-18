package aarnav100.developer.biblescroll.ui.theme

import aarnav100.developer.biblescroll.data.settings.AccentPreset
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Colours for one feed reading background (S3, proposal #5). Words-of-Jesus red is tuned per
 * background so it stays readable; ContrastTest checks every pairing against WCAG AA.
 */
@Immutable
data class ReadingColors(
    val isDark: Boolean,
    val background: Color,
    /** Pills and chips on the feed (top bar, actions pill). */
    val container: Color,
    val text: Color,
    /** Reference line and rail icons. */
    val secondaryText: Color,
    val wordsOfJesus: Color,
)

fun ReadingBackground.colors(): ReadingColors = when (this) {
    ReadingBackground.Paper -> ReadingColors(
        isDark = false,
        background = PaperBackground,
        container = PaperSurfaceContainerHigh,
        text = Ink,
        secondaryText = InkMuted,
        wordsOfJesus = Crimson,
    )
    ReadingBackground.Sepia -> ReadingColors(
        isDark = false,
        background = Color(0xFFF1E3C8),
        container = Color(0xFFE5D3B2),
        text = Color(0xFF2B2118),
        secondaryText = Color(0xFF5C4B3A),
        // Slightly deeper than Paper's crimson: sepia is darker, so the red needs more weight.
        wordsOfJesus = Color(0xFF931428),
    )
    ReadingBackground.Charcoal -> ReadingColors(
        isDark = true,
        background = CharcoalBackground,
        container = CharcoalSurfaceContainerHigh,
        text = Chalk,
        secondaryText = ChalkMuted,
        wordsOfJesus = Color(0xFFF4939E),
    )
    ReadingBackground.TrueBlack -> ReadingColors(
        isDark = true,
        background = Color.Black,
        container = Color(0xFF1E1D1C),
        // Off-white, not #FFF: pure white on true black halos on OLED.
        text = Color(0xFFE6E0D9),
        secondaryText = Color(0xFFA59D95),
        wordsOfJesus = Color(0xFFF2848F),
    )
}

/** An accent preset's tone for light surfaces and for dark ones, so it clears 3:1 on both. */
@Immutable
data class AccentTones(val onLight: Color, val onDark: Color) {
    fun on(dark: Boolean) = if (dark) onDark else onLight
}

fun AccentPreset.tones(): AccentTones = when (this) {
    AccentPreset.Crimson -> AccentTones(Crimson, CrimsonLight)
    AccentPreset.Rose -> AccentTones(Color(0xFF9C2457), Color(0xFFF5A3C4))
    AccentPreset.Amber -> AccentTones(Color(0xFF825400), Color(0xFFF0C062))
    AccentPreset.Sage -> AccentTones(Color(0xFF3A6642), Color(0xFFA3D2A8))
    AccentPreset.Teal -> AccentTones(Color(0xFF00636F), Color(0xFF7CD2DF))
    AccentPreset.Indigo -> AccentTones(Color(0xFF4A4FA6), Color(0xFFBCBFFF))
}

/** M3 primary roles derived from one accent tone. ContrastTest covers the text-on-fill pairs. */
@Immutable
data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

fun accentRoles(tone: Color, dark: Boolean): AccentRoles = if (dark) {
    AccentRoles(
        primary = tone,
        onPrimary = lerp(tone, Color.Black, 0.78f),
        primaryContainer = lerp(tone, Color.Black, 0.62f),
        onPrimaryContainer = lerp(tone, Color.White, 0.75f),
    )
} else {
    AccentRoles(
        primary = tone,
        onPrimary = Color.White,
        primaryContainer = lerp(tone, Color.White, 0.84f),
        onPrimaryContainer = lerp(tone, Color.Black, 0.65f),
    )
}

/**
 * Accent for icons on a reading background. A wallpaper colour is used only if it clears 3:1 there;
 * otherwise the preset's tone stands in, so dynamic colour can never make the like icon invisible.
 */
fun readingAccent(reading: ReadingColors, preset: AccentPreset, dynamic: Color?): Color =
    dynamic?.takeIf { contrastRatio(it, reading.background) >= Wcag.NON_TEXT } ?: preset.tones().on(reading.isDark)
