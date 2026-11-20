package aarnav100.developer.biblescroll.feature.discover

import aarnav100.developer.biblescroll.R
import aarnav100.developer.biblescroll.core.content.DiscoverTheme
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * D1 card colours per theme, light and dark. The light Trust, dark Hope and light Love values are the approved
 * mockup's; the other three are derived from them (same hue, tuned for the other mode) and checked for contrast in
 * DiscoverPaletteTest. Screen background and fonts come from the app theme.
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

fun discoverPalette(theme: DiscoverTheme, dark: Boolean): DiscoverPalette = when (theme) {
    DiscoverTheme.TRUST -> if (dark) TrustDark else TrustLight
    DiscoverTheme.HOPE -> if (dark) HopeDark else HopeLight
    DiscoverTheme.LOVE -> if (dark) LoveDark else LoveLight
}

private val InkText = Color(0xFF1F1B17)
private val ChalkText = Color(0xFFF2EDE6)

internal val TrustLight = DiscoverPalette(
    card = Color(0xFFEDF1EA), border = Color(0xFFD6E0D6), accent = Color(0xFF24493E), pill = Color(0xFFDCE7DC),
    text = InkText, secondary = Color(0xFF4F5E56),
)
internal val TrustDark = DiscoverPalette(
    card = Color(0xFF1D2622), border = Color(0xFF2E3B34), accent = Color(0xFFA9CDB9), pill = Color(0xFF2A3A32),
    text = ChalkText, secondary = Color(0xFFB3C2B9),
)
internal val HopeLight = DiscoverPalette(
    card = Color(0xFFF7EFDF), border = Color(0xFFE9DABB), accent = Color(0xFF6E5212), pill = Color(0xFFF0E1C0),
    text = InkText, secondary = Color(0xFF66594A),
)
internal val HopeDark = DiscoverPalette(
    card = Color(0xFF2A241B), border = Color(0xFF3B3224), accent = Color(0xFFF0C987), pill = Color(0xFF3A2F1E),
    text = ChalkText, secondary = Color(0xFFC9BBA6),
)
internal val LoveLight = DiscoverPalette(
    card = Color(0xFFF8EBEC), border = Color(0xFFEBD2D5), accent = Color(0xFF7D0E22), pill = Color(0xFFF2D6D9),
    text = InkText, secondary = Color(0xFF6A5254),
)
internal val LoveDark = DiscoverPalette(
    card = Color(0xFF2C1C1F), border = Color(0xFF45292E), accent = Color(0xFFF2A5AE), pill = Color(0xFF4A2A30),
    text = ChalkText, secondary = Color(0xFFCDB5B8),
)

val DiscoverTheme.labelRes: Int
    get() = when (this) {
        DiscoverTheme.TRUST -> R.string.discover_theme_trust
        DiscoverTheme.HOPE -> R.string.discover_theme_hope
        DiscoverTheme.LOVE -> R.string.discover_theme_love
    }

val DiscoverTheme.icon: ImageVector
    get() = when (this) {
        DiscoverTheme.TRUST -> Icons.Outlined.Waves
        DiscoverTheme.HOPE -> Icons.Outlined.WbTwilight
        DiscoverTheme.LOVE -> Icons.Outlined.VolunteerActivism
    }

/**
 * Faint decorative art behind the card content (no semantics): Trust = wave lines over the lower part, Hope = rays and
 * rings from the top right, Love = rings at the bottom right.
 */
@Composable
fun DiscoverCardArt(theme: DiscoverTheme, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.2.dp.toPx())
        when (theme) {
            DiscoverTheme.TRUST -> {
                // Approved mockup: five soft waves over the lower part of the card, behind the fact and the actions.
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
            DiscoverTheme.HOPE -> {
                val origin = Offset(size.width + 8.dp.toPx(), -8.dp.toPx())
                val rays = accent.copy(alpha = 0.09f)
                repeat(7) { i ->
                    val angle = Math.toRadians(100.0 + i * 12.0)
                    val length = size.width * 0.95f
                    drawLine(
                        rays, origin,
                        Offset(origin.x + (length * Math.cos(angle)).toFloat(), origin.y + (length * Math.sin(angle)).toFloat()),
                        strokeWidth = stroke.width,
                    )
                }
                repeat(3) { i -> drawCircle(accent.copy(alpha = 0.10f), radius = (40 + i * 34).dp.toPx(), center = origin, style = stroke) }
            }
            DiscoverTheme.LOVE -> {
                val center = Offset(size.width + 10.dp.toPx(), size.height - 64.dp.toPx())
                repeat(4) { i -> drawCircle(accent.copy(alpha = 0.10f), radius = (36 + i * 30).dp.toPx(), center = center, style = stroke) }
            }
        }
    }
}
