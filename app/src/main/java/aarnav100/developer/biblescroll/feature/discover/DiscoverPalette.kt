package aarnav100.developer.biblescroll.feature.discover

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * D1 card colours, light and dark. One design for every card, whatever its theme label (the approved mockup's card;
 * the dark values are derived from it and checked for contrast in DiscoverPaletteTest). Screen background and fonts
 * come from the app theme.
 */
@Immutable
data class DiscoverPalette(
    val card: Color,
    val border: Color,
    val accent: Color,
    val pill: Color,
    /** Verse, hook and fact text. Words of Jesus use the app's red-letter colour on top. */
    val text: Color,
    /** Reference row and unselected action icons. */
    val secondary: Color,
)

fun discoverPalette(dark: Boolean): DiscoverPalette = if (dark) DiscoverDark else DiscoverLight

internal val DiscoverLight = DiscoverPalette(
    card = Color(0xFFEDF1EA), border = Color(0xFFD6E0D6), accent = Color(0xFF24493E), pill = Color(0xFFDCE7DC),
    text = Color(0xFF1F1B17), secondary = Color(0xFF4F5E56),
)
internal val DiscoverDark = DiscoverPalette(
    card = Color(0xFF1D2622), border = Color(0xFF2E3B34), accent = Color(0xFFA9CDB9), pill = Color(0xFF2A3A32),
    text = Color(0xFFF2EDE6), secondary = Color(0xFFB3C2B9),
)

/** The icon before every card's theme label (approved mockup). */
val DiscoverThemeIcon: ImageVector get() = Icons.Outlined.Waves

/** Faint decorative waves behind the card content (no semantics): five soft lines over the lower part of the card. */
@Composable
fun DiscoverCardArt(accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.2.dp.toPx())
        val color = accent.copy(alpha = 0.12f)
        val amplitude = 7.dp.toPx()
        val wavelength = size.width / 3.4f
        repeat(5) { i ->
            val baseY = size.height - 14.dp.toPx() - i * 36.dp.toPx()
            val path = Path().apply {
                moveTo(0f, baseY)
                var x = 0f
                while (x < size.width) {
                    relativeQuadraticTo(wavelength / 4, -amplitude, wavelength / 2, 0f)
                    relativeQuadraticTo(wavelength / 4, amplitude, wavelength / 2, 0f)
                    x += wavelength
                }
            }
            drawPath(path, color, style = stroke)
        }
    }
}
