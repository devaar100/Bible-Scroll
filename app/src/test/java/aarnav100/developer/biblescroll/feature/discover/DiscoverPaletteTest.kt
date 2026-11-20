package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.core.content.DiscoverTheme
import aarnav100.developer.biblescroll.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG AA on every theme card, light and dark: text 4.5:1, accent labels and secondary text 4.5:1 (11–13sp). */
class DiscoverPaletteTest {
    @Test
    fun everyThemeIsReadableInBothModes() {
        for (theme in DiscoverTheme.entries) for (dark in listOf(false, true)) {
            val p = discoverPalette(theme, dark)
            listOf("text" to p.text, "accent" to p.accent, "secondary" to p.secondary).forEach { (name, color) ->
                val ratio = contrastRatio(color, p.card)
                assertTrue("$theme dark=$dark $name on card: $ratio", ratio >= 4.5)
            }
            val onPill = contrastRatio(p.accent, p.pill)
            assertTrue("$theme dark=$dark accent on pill: $onPill", onPill >= 4.5)
        }
    }
}
