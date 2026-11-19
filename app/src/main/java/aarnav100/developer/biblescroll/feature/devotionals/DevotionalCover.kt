package aarnav100.developer.biblescroll.feature.devotionals

import aarnav100.developer.biblescroll.ui.theme.Literata
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The eight drawn cover designs a devotional's coverStyle names (pipeline devotionals/calendar.py). No images:
 * a two-stop gradient, a thin rule and type. Ink colours keep at least 4.5:1 contrast on both gradient stops.
 */
@Immutable
data class CoverStyle(val top: Color, val bottom: Color, val ink: Color, val accent: Color) {
    companion object {
        private val styles = mapOf(
            "dawn" to CoverStyle(Color(0xFFFCE3D2), Color(0xFFF4B9B2), Color(0xFF3B1F1A), Color(0xFF82301F)),
            "meadow" to CoverStyle(Color(0xFFE3EFD9), Color(0xFFB9D6A8), Color(0xFF1E3318), Color(0xFF2F5222)),
            "ocean" to CoverStyle(Color(0xFF0F4C5C), Color(0xFF16324F), Color(0xFFF2F7F8), Color(0xFF9FD6DE)),
            "ember" to CoverStyle(Color(0xFF8A3B12), Color(0xFF4A1E0E), Color(0xFFFFF4E8), Color(0xFFFFC27A)),
            "dusk" to CoverStyle(Color(0xFF4B3A73), Color(0xFF231C3D), Color(0xFFF5F0FF), Color(0xFFD0BFFF)),
            "stone" to CoverStyle(Color(0xFF5A5650), Color(0xFF34312D), Color(0xFFF7F4EF), Color(0xFFE0D3BC)),
            "linen" to CoverStyle(Color(0xFFF8F1E4), Color(0xFFE9DCC5), Color(0xFF2E2620), Color(0xFF7A5C3A)),
            "night" to CoverStyle(Color(0xFF1B2440), Color(0xFF07090F), Color(0xFFEFF2FA), Color(0xFFB8C6EE)),
        )

        /** Unknown styles (a newer pipeline) fall back to linen. */
        fun of(name: String): CoverStyle = styles[name] ?: styles.getValue("linen")

        val names: Set<String> get() = styles.keys
    }

    val brush: Brush get() = Brush.verticalGradient(listOf(top, bottom))
}

/** A cover: series line, title and theme on the style's gradient. */
@Composable
fun DevotionalCover(
    style: String,
    title: String,
    theme: String,
    eyebrow: String,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    titleSize: TextUnit = 34.sp,
) {
    val cover = CoverStyle.of(style)
    Box(modifier.clip(shape).background(cover.brush), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(eyebrow.uppercase(), color = cover.accent, fontSize = 12.sp, letterSpacing = 2.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.width(40.dp).height(2.dp).background(cover.accent))
            Spacer(Modifier.height(18.dp))
            Text(title, color = cover.ink, fontFamily = Literata, fontWeight = FontWeight.SemiBold, fontSize = titleSize,
                lineHeight = titleSize * 1.15f, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(theme, color = cover.ink.copy(alpha = 0.85f), fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
}

/** A small swatch of a cover: the gradient and its rule, for lists and cards. */
@Composable
fun CoverThumb(style: String, modifier: Modifier = Modifier) {
    val cover = CoverStyle.of(style)
    Box(modifier.background(cover.brush), contentAlignment = Alignment.Center) {
        Box(Modifier.width(28.dp).height(2.dp).background(cover.accent))
    }
}
