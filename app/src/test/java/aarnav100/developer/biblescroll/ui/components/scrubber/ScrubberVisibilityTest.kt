package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrubberVisibilityTest {

    @Test
    fun defaultIsAlways() {
        assertEquals(ScrubberMode.Always, AppSettings().verseScrubber)
    }

    @Test
    fun alwaysIsAlwaysVisible() {
        for (active in listOf(true, false)) {
            for (since in listOf(null, 0L, 10_000L)) {
                assertTrue(ScrubberVisibility.visible(ScrubberMode.Always, 31, active, since))
            }
        }
    }

    @Test
    fun whileScrollingShowsWhenActiveAndForOnePointFiveSecondsAfter() {
        val mode = ScrubberMode.WhileScrolling
        assertFalse("never touched", ScrubberVisibility.visible(mode, 31, active = false, millisSinceActive = null))
        assertTrue(ScrubberVisibility.visible(mode, 31, active = true, millisSinceActive = null))
        assertTrue(ScrubberVisibility.visible(mode, 31, active = false, millisSinceActive = 0))
        assertTrue(ScrubberVisibility.visible(mode, 31, active = false, millisSinceActive = 1_499))
        assertFalse(ScrubberVisibility.visible(mode, 31, active = false, millisSinceActive = 1_500))
        assertFalse(ScrubberVisibility.visible(mode, 31, active = false, millisSinceActive = 60_000))
    }

    @Test
    fun offIsNeverPresent() {
        assertFalse(ScrubberVisibility.present(ScrubberMode.Off, 176))
        assertFalse(ScrubberVisibility.visible(ScrubberMode.Off, 176, active = true, millisSinceActive = 0))
    }

    @Test
    fun hiddenWhenThereIsOnlyOneItem() {
        for (mode in ScrubberMode.entries) {
            assertFalse(ScrubberVisibility.present(mode, 1))
            assertFalse(ScrubberVisibility.present(mode, 0))
            assertFalse(ScrubberVisibility.visible(mode, 1, active = true, millisSinceActive = 0))
        }
        assertTrue(ScrubberVisibility.present(ScrubberMode.Always, 2))
    }

    @Test
    fun textClearsTheStripWheneverTheScrubberCanAppear() {
        // The touch strip starts at the physical edge; text starts past it, so drags on text always page.
        val stripEnd = VerseScrubberDefaults.StripWidth.value
        for (mode in ScrubberMode.entries) {
            val inset = ScrubberVisibility.textStartInsetDp(mode)
            if (mode == ScrubberMode.Off) assertEquals(28, inset) else assertTrue("$mode: $inset dp", inset >= stripEnd)
        }
    }
}
