package aarnav100.developer.biblescroll.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x relative luminance of an opaque sRGB colour. */
fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** WCAG contrast ratio, 1.0 (none) to 21.0 (black on white). Order doesn't matter. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** WCAG AA minimums. */
object Wcag {
    const val TEXT = 4.5
    const val NON_TEXT = 3.0
}
