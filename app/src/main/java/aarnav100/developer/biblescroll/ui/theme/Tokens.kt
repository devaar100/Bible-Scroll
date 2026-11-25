package aarnav100.developer.biblescroll.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/*
 * The app's shared sizes (docs/ui-direction.md). Use these instead of new dp values or on-the-spot style copies, so
 * screens built at different times still match. Screens move onto them as they are redesigned; Community is first.
 * Pills and round icons keep CircleShape.
 */

/** Corner sizes: three steps only. Not wired into MaterialTheme.shapes, so stock components keep their M3 shapes. */
object AppShapes {
    /** Thumbnails, small insets. */
    val small = RoundedCornerShape(12.dp)

    /** Inset cards, list rows, notices. */
    val medium = RoundedCornerShape(20.dp)

    /** Hero cards (one main card per item on a screen). */
    val large = RoundedCornerShape(28.dp)
}

/** Spacing on an 8 dp grid (with 4 and 12 for tight spots). */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Left and right screen margin. */
    val gutter = 20.dp

    /** Padding inside cards and notices. */
    val card = 20.dp
}

/** Named text roles built on the theme's type scale. Colours stay at the call site (onSurface / onSurfaceVariant). */
object AppText {
    /** A screen's headline ("Choose this month's spotlight", "History"): Literata. */
    val screenTitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineMedium

    /** The line under a screen headline ("Each month, readers choose…"): Inter 14. Use onSurfaceVariant. */
    val screenSubtitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.bodyMedium

    /** Small label above a section or on a card ("DECEMBER SPOTLIGHT"): Inter, tracked; upper-case it at the call site. */
    val sectionLabel: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)

    /** The name on a hero card (a ministry, a plan): Literata. */
    val cardTitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium)

    /** The name on a list row or small card (a History month, a sheet header): Literata. */
    val itemTitle: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.titleLarge.copy(fontFamily = Literata, fontWeight = FontWeight.Medium)

    /** Labels on buttons, text links and pills ("Vote", "Website", "Your vote"): Inter. */
    val button: TextStyle
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
}
