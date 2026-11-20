package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.data.settings.AppSettings
import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import aarnav100.developer.biblescroll.ui.components.TestTags
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import aarnav100.developer.biblescroll.ui.theme.ReadingTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Semantics and touch handling of the verse scrubber, rendered alone (no database, no ViewModels). */
@RunWith(AndroidJUnit4::class)
class VerseScrubberTest {

    @get:Rule
    val rule = createComposeRule()

    private val scrubs = mutableListOf<Int>()
    private val scrubbing = mutableListOf<Boolean>()
    private var underneathClicks = 0

    private fun show(count: Int = 31, start: Int = 11, mode: ScrubberMode = ScrubberMode.Always) {
        rule.setContent {
            BibleScrollTheme(AppSettings()) {
                ReadingTheme {
                    Box(Modifier.size(320.dp, 640.dp)) {
                        // What a pager would be: anything under the strip must never see its touches.
                        Box(Modifier.fillMaxSize().clickable { underneathClicks++ })
                        var index by remember { mutableIntStateOf(start) }
                        VerseScrubber(
                            position = { index.toFloat() },
                            count = count,
                            labelNumber = { it + 1 },
                            stateDescription = { "Verse ${it + 1} of $count" },
                            contentDescription = "Position in chapter",
                            mode = mode,
                            scrolling = { false },
                            onScrub = {
                                scrubs += it
                                index = it
                            },
                            onScrubbingChange = { scrubbing += it },
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    }
                }
            }
        }
    }

    private fun scrubber() = rule.onNodeWithTag(TestTags.VERSE_SCRUBBER)

    @Test
    fun isASliderWithVerseStateForTalkBack() {
        show()
        scrubber()
            .assertContentDescriptionEquals("Position in chapter")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Verse 12 of 31"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(11f, 0f..30f, steps = 29)))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
    }

    @Test
    fun setProgressMovesToTheNearestVerse() {
        show()
        scrubber().performSemanticsAction(SemanticsActions.SetProgress) { it(19.6f) }
        rule.waitForIdle()
        assertEquals(listOf(20), scrubs)
        scrubber().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Verse 21 of 31"))
    }

    @Test
    fun tapOnTheTrackJumpsThere() {
        show(start = 0)
        scrubber().performTouchInput { click(Offset(centerX, height / 2f)) }
        rule.waitForIdle()
        assertEquals(listOf(15), scrubs) // middle of 31
        assertEquals("a jump is not a scrub: the pager settles and saves", emptyList<Boolean>(), scrubbing)
        assertEquals(0, underneathClicks)
    }

    @Test
    fun dragFromOffTheActiveCapsuleDoesNothing() {
        show(start = 0)
        scrubber().performTouchInput {
            down(Offset(centerX, height / 2f))
            for (step in 11..20) moveTo(Offset(centerX, height * step / 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals(emptyList<Int>(), scrubs)
        assertEquals(emptyList<Boolean>(), scrubbing)
        assertEquals(0, underneathClicks)
    }

    @Test
    fun dragScrubsVerseByVerseAndEndsOnce() {
        show(start = 0)
        scrubber().performTouchInput {
            // On the active (first) capsule.
            down(Offset(centerX, 1f))
            for (step in 1..20) moveTo(Offset(centerX, height * step / 20f))
            up()
        }
        rule.waitForIdle()
        assertEquals(30, scrubs.last())
        assertTrue("one scroll per verse change: $scrubs", scrubs.zipWithNext().all { (a, b) -> a != b })
        assertEquals("saved once, on release", listOf(true, false), scrubbing)
        assertEquals(0, underneathClicks)
    }

    @Test
    fun offShowsNothing() {
        show(mode = ScrubberMode.Off)
        scrubber().assertDoesNotExist()
    }

    @Test
    fun aSingleVerseShowsNothing() {
        show(count = 1, start = 0)
        scrubber().assertDoesNotExist()
    }

    @Test
    fun hiddenScrubberRevealsOnTapWithoutJumping() {
        show(start = 0, mode = ScrubberMode.WhileScrolling)
        scrubber().performTouchInput { click(Offset(centerX, height / 2f)) }
        rule.waitForIdle()
        assertEquals(emptyList<Int>(), scrubs)
        assertEquals(0, underneathClicks)
    }

    @Test
    fun swipesAndScrubsDoNotRecompose() {
        var compositions = 0
        VerseScrubberDefaults.onComposed = { compositions++ }
        try {
            val position = mutableFloatStateOf(0f)
            rule.setContent {
                BibleScrollTheme(AppSettings()) {
                    ReadingTheme {
                        Box(Modifier.size(320.dp, 640.dp)) {
                            VerseScrubber(
                                position = { position.floatValue },
                                count = 31,
                                labelNumber = { it + 1 },
                                stateDescription = { "Verse ${it + 1} of 31" },
                                contentDescription = "Position in chapter",
                                mode = ScrubberMode.Always,
                                scrolling = { false },
                                onScrub = { position.floatValue = it.toFloat() },
                                onScrubbingChange = {},
                                modifier = Modifier.align(Alignment.TopStart),
                            )
                        }
                    }
                }
            }
            rule.waitForIdle()
            val settled = compositions

            // A swipe: the pager position moves a little every frame.
            for (step in 1..60) {
                position.floatValue = step / 4f
                rule.mainClock.advanceTimeByFrame()
            }
            rule.waitForIdle()
            assertEquals("recomposed during a swipe", settled, compositions)

            // A scrub from the active capsule: only grabbing and letting go may recompose (the label grows and shrinks).
            position.floatValue = 0f
            rule.waitForIdle()
            val beforeDrag = compositions
            scrubber().performTouchInput {
                down(Offset(centerX, 1f))
                for (step in 1..30) moveTo(Offset(centerX, height * step / 30f))
                up()
            }
            rule.waitForIdle()
            assertTrue("recomposed ${compositions - beforeDrag} times during a 30-verse scrub", compositions - beforeDrag <= 2)
        } finally {
            VerseScrubberDefaults.onComposed = null
        }
    }
}
