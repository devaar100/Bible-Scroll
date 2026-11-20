package aarnav100.developer.biblescroll.feature.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp

/** Panel and icon colour for a ministry with no photo (approved Community/History mockups: sage, sand, rose, …). */
@Immutable
data class MinistryTint(val panel: Color, val icon: Color)

private val LightTints = listOf(
    MinistryTint(Color(0xFFDDE8E0), Color(0xFF3E5A4C)), // sage
    MinistryTint(Color(0xFFF1E4C3), Color(0xFF7A5B17)), // sand
    MinistryTint(Color(0xFFF3D9DD), Color(0xFF8E2A3A)), // rose
    MinistryTint(Color(0xFFDADFF0), Color(0xFF3F4C7A)), // lavender
    MinistryTint(Color(0xFFE7E1D9), Color(0xFF5D554B)), // stone
)
private val DarkTints = listOf(
    MinistryTint(Color(0xFF26332C), Color(0xFFA9CDB9)),
    MinistryTint(Color(0xFF3A311E), Color(0xFFF0C987)),
    MinistryTint(Color(0xFF45292E), Color(0xFFF2A5AE)),
    MinistryTint(Color(0xFF2A2F45), Color(0xFFB8C2EE)),
    MinistryTint(Color(0xFF34302A), Color(0xFFCFC6BA)),
)

/** Stable per ministry id (String.hashCode is defined by the JLS), so a ministry looks the same on both screens. */
internal fun tintIndex(ministryId: String): Int = Math.floorMod(ministryId.hashCode(), LightTints.size)

fun ministryTint(ministryId: String, dark: Boolean): MinistryTint =
    (if (dark) DarkTints else LightTints)[tintIndex(ministryId)]

@Composable
fun ministryTint(ministryId: String): MinistryTint =
    ministryTint(ministryId, MaterialTheme.colorScheme.background.luminance() < 0.5f)

/** The tinted placeholder: an image icon centred on the ministry's tint (decorative, no semantics). */
@Composable
fun MinistryPlaceholder(ministryId: String, iconSize: Dp, modifier: Modifier = Modifier) {
    val tint = ministryTint(ministryId)
    Box(modifier.background(tint.panel), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = tint.icon, modifier = Modifier.size(iconSize))
    }
}
