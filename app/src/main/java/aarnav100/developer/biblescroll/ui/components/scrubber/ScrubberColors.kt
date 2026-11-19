package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.ui.theme.ReadingColors
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Scrubber colours: the reading text colour (onBackground) at per-background alphas, flattened onto the background
 * so lerps between states are exact. ContrastTest: [active] and [passed] reach 3:1 on every background (WCAG 1.4.11),
 * [upcoming] about 1.15:1 (visible, deliberately quiet), the labels as listed on [labelDim] and [labelCurrent].
 */
@Immutable
data class ScrubberColors(
    val active: Color,
    /** Capsules above the active one. */
    val passed: Color,
    /** Capsules below it: very quiet. */
    val upcoming: Color,
    /** First and last numbers. Same strength as [passed], so 3:1; the slider semantics carry the full text. */
    val labelDim: Color,
    /** The current number: full reading text colour, so >= 4.5:1. */
    val labelCurrent: Color,
)

fun scrubberColors(reading: ReadingColors): ScrubberColors {
    val bg = reading.background
    val fg = reading.text
    val passed = fg.copy(alpha = reading.scrubberPassedAlpha).compositeOver(bg)
    return ScrubberColors(
        active = fg,
        passed = passed,
        upcoming = fg.copy(alpha = reading.scrubberUpcomingAlpha).compositeOver(bg),
        labelDim = passed,
        labelCurrent = fg,
    )
}
