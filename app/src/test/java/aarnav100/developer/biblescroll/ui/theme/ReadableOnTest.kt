package aarnav100.developer.biblescroll.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadableOnTest {
    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun lightAccentsGetDarkInkAndDarkAccentsGetWhite() {
        assertEquals(Color(0xFF1A1A1A), readableOn(Color(0xFFF0A0AA))) // light pink on charcoal (the H11 button)
        assertEquals(Color.White, readableOn(Color(0xFFA3122E)))       // crimson
    }

    @Test
    fun theChoiceIsAlwaysAtLeastAsGoodAsTheAlternative() {
        listOf(0xFFF0A0AA, 0xFFA3122E, 0xFFFF9A4D, 0xFF7FD3A0, 0xFF3D5AFE, 0xFF808080).map { Color(it) }.forEach { bg ->
            val chosen = readableOn(bg)
            val other = if (chosen == Color.White) Color(0xFF1A1A1A) else Color.White
            assertTrue("$bg", contrast(bg, chosen) >= contrast(bg, other) - 0.2)
        }
    }
}
