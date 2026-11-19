package aarnav100.developer.biblescroll.ui.theme

import aarnav100.developer.biblescroll.ui.components.scrubber.scrubberColors
import aarnav100.developer.biblescroll.data.settings.AccentPreset
import aarnav100.developer.biblescroll.data.settings.ReadingBackground
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG AA for every colour pairing the user can choose: text >= 4.5:1, icons >= 3:1.
 * Collects every failure before asserting so one run shows the whole picture.
 */
class ContrastTest {

    private val failures = mutableListOf<String>()

    private fun check(label: String, fg: Color, bg: Color, min: Double) {
        val ratio = contrastRatio(fg, bg)
        if (ratio < min) failures += "%s: %.2f:1 < %.1f:1".format(label, ratio, min)
    }

    private fun assertNoFailures() = assertTrue(failures.joinToString("\n", prefix = "\n"), failures.isEmpty())

    @Test
    fun formulaMatchesKnownValues() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.Red, Color.Red), 0.001)
        assertEquals(4.48, contrastRatio(Color(0xFF777777), Color.White), 0.01) // classic just-fails grey
        assertEquals(contrastRatio(Color.Blue, Color.Yellow), contrastRatio(Color.Yellow, Color.Blue), 1e-9)
    }

    @Test
    fun readingBackgroundsTextIsAA() {
        for (bg in ReadingBackground.entries) {
            val c = bg.colors()
            check("$bg body", c.text, c.background, Wcag.TEXT)
            check("$bg reference", c.secondaryText, c.background, Wcag.TEXT)
            check("$bg words of Jesus", c.wordsOfJesus, c.background, Wcag.TEXT)
            check("$bg pill text", c.text, c.container, Wcag.TEXT)
            check("$bg rail icons", c.secondaryText, c.background, Wcag.NON_TEXT)
        }
        assertNoFailures()
    }

    @Test
    fun everyAccentIconIsVisibleOnEveryReadingBackground() {
        for (bg in ReadingBackground.entries) {
            val c = bg.colors()
            for (accent in AccentPreset.entries) {
                check("$accent on $bg", readingAccent(c, accent, dynamic = null), c.background, Wcag.NON_TEXT)
                // The top-bar filter heart sits on the pill, not the page.
                check("$accent on $bg pill", readingAccent(c, accent, dynamic = null), c.container, Wcag.NON_TEXT)
            }
        }
        assertNoFailures()
    }

    /**
     * Verse scrubber (B1/B2/H11), the same scrubberColors() the composable draws with: active and passed capsules
     * 3:1, upcoming just visible (about 1.15:1, deliberately quiet, never louder than passed), current number 4.5:1,
     * dim end numbers 3:1 (the slider semantics carry the full position text).
     */
    @Test
    fun verseScrubberStatesAndLabelsOnEveryReadingBackground() {
        for (bg in ReadingBackground.entries) {
            val c = bg.colors()
            val s = scrubberColors(c)
            check("$bg scrubber active", s.active, c.background, Wcag.NON_TEXT)
            check("$bg scrubber passed", s.passed, c.background, Wcag.NON_TEXT)
            check("$bg scrubber current number", s.labelCurrent, c.background, Wcag.TEXT)
            check("$bg scrubber end numbers", s.labelDim, c.background, Wcag.NON_TEXT)
            val upcoming = contrastRatio(s.upcoming, c.background)
            if (upcoming !in 1.10..1.25) failures += "%s scrubber upcoming: %.2f:1, want about 1.15:1".format(bg, upcoming)
            // States stay distinguishable from each other, not only from the page.
            check("$bg scrubber active vs passed", s.active, s.passed, 1.5)
            check("$bg scrubber passed vs upcoming", s.passed, s.upcoming, 2.0)
        }
        assertNoFailures()
    }

    @Test
    fun appChromeTextIsAA() {
        for ((name, scheme, woj) in listOf(
            Triple("light", LightColors, Crimson),
            Triple("dark", DarkColors, CrimsonLight),
        )) {
            check("$name onBackground", scheme.onBackground, scheme.background, Wcag.TEXT)
            check("$name onSurface", scheme.onSurface, scheme.surface, Wcag.TEXT)
            check("$name onSurfaceVariant", scheme.onSurfaceVariant, scheme.surface, Wcag.TEXT)
            check("$name onSurfaceVariant on sheet", scheme.onSurfaceVariant, scheme.surfaceContainerLow, Wcag.TEXT)
            check("$name words of Jesus (compare sheet)", woj, scheme.surfaceContainerLow, Wcag.TEXT)
        }
        assertNoFailures()
    }

    @Test
    fun everyAccentWorksInAppChrome() {
        for ((name, scheme, dark) in listOf(Triple("light", LightColors, false), Triple("dark", DarkColors, true))) {
            for (accent in AccentPreset.entries) {
                val roles = accentRoles(accent.tones().on(dark), dark)
                check("$name $accent icon on surface", roles.primary, scheme.surface, Wcag.NON_TEXT)
                check("$name $accent button text", roles.onPrimary, roles.primary, Wcag.TEXT)
                check("$name $accent container text", roles.onPrimaryContainer, roles.primaryContainer, Wcag.TEXT)
            }
        }
        assertNoFailures()
    }

    @Test
    fun dynamicAccentFallsBackWhenTooFaint() {
        val charcoal = ReadingBackground.Charcoal.colors()
        val faint = Color(0xFF2A2A2A) // nearly the background
        val bright = Color(0xFF9FD0FF)

        assertEquals(AccentPreset.Teal.tones().onDark, readingAccent(charcoal, AccentPreset.Teal, faint))
        assertEquals(bright, readingAccent(charcoal, AccentPreset.Teal, bright))
    }
}
