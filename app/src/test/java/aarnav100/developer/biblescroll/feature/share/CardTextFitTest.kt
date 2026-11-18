package aarnav100.developer.biblescroll.feature.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTextFitTest {

    /** Text "fits" while size <= limit; counts calls so we can check it's a search, not a scan. */
    private class Measurer(val limit: Float) {
        var calls = 0
        fun fits(size: Float): Boolean {
            calls++
            return size <= limit
        }
    }

    @Test
    fun shortVerseGetsTheMaximumSize() {
        val m = Measurer(limit = 1000f)
        assertEquals(FitResult(72f, overflows = false), fitFontSize(30f, 72f, 2f, m::fits))
    }

    @Test
    fun picksTheLargestSizeOnTheStepGridThatFits() {
        assertEquals(FitResult(51f.snap(30f, 2f), false), fitFontSize(30f, 72f, 2f, Measurer(51f)::fits))
        assertEquals(FitResult(50f, false), fitFontSize(30f, 72f, 2f, Measurer(50f)::fits))
        assertEquals(FitResult(30f, false), fitFontSize(30f, 72f, 2f, Measurer(30f)::fits))
    }

    @Test
    fun overlongVerseFallsBackToMinimumAndFlagsOverflow() {
        val result = fitFontSize(30f, 72f, 2f, Measurer(limit = 20f)::fits)
        assertEquals(30f, result.fontSize)
        assertTrue(result.overflows)
    }

    @Test
    fun usesBinarySearchNotOneMeasurementPerStep() {
        val m = Measurer(limit = 47f)
        fitFontSize(30f, 72f, 2f, m::fits) // 22 grid sizes
        assertTrue("took ${m.calls} measurements", m.calls <= 7)
    }

    @Test
    fun agreesWithALinearScanForEveryLimit() {
        var limit = 25f
        while (limit < 80f) {
            val linear = generateSequence(72f) { it - 2f }.takeWhile { it >= 30f }.firstOrNull { it <= limit }
            val result = fitFontSize(30f, 72f, 2f, Measurer(limit)::fits)
            assertEquals("limit $limit", linear ?: 30f, result.fontSize)
            assertEquals("limit $limit", linear == null, result.overflows)
            limit += 0.5f
        }
    }

    @Test
    fun singleSizeRange() {
        assertEquals(FitResult(40f, false), fitFontSize(40f, 40f, 2f, Measurer(40f)::fits))
        assertEquals(FitResult(40f, true), fitFontSize(40f, 40f, 2f, Measurer(39f)::fits))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnInvertedRange() {
        fitFontSize(72f, 30f, 2f) { true }
    }

    @Test
    fun maxLinesForOverflowFillsTheTextBox() {
        val layout = CardLayout.of(CardFormat.Square) // 640 px box, 1.35 line height
        assertEquals(15, maxLinesFor(layout, 30f)) // 640 / 40.5
        assertEquals(1, maxLinesFor(layout, 10_000f))
    }

    @Test
    fun layoutsLeaveRoomForReferenceAndBrandUnderTheVerse() {
        for (format in CardFormat.entries) {
            val l = CardLayout.of(format)
            assertEquals(1080, format.widthPx)
            assertEquals(1080f - 2 * l.padding, l.textWidth)
            // Space below the verse: gap + rule + gap + reference line + gap + brand line.
            val below = 40 + 6 + 24 + l.referenceFont * 1.3f + 56 + l.brandFont * 1.3f
            assertTrue("$format", l.padding * 2 + l.textMaxHeight + below <= format.heightPx)
            assertFalse("$format", l.minFont >= l.maxFont)
        }
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("John-3-16", "John 3:16".toSafeFileName())
        assertEquals("1-Corinthians-13-4", "1 Corinthians 13:4".toSafeFileName())
        assertEquals("verse", "::".toSafeFileName())
    }

    private fun Float.snap(min: Float, step: Float) = min + ((this - min) / step).toInt() * step
}
