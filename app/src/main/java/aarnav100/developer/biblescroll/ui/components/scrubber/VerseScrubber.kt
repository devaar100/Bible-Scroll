package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import aarnav100.developer.biblescroll.ui.components.TestTags
import aarnav100.developer.biblescroll.ui.components.automationTag
import aarnav100.developer.biblescroll.ui.theme.BibleScrollTheme
import android.os.Build
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

object VerseScrubberDefaults {
    /** Touch strip from the physical start edge: 48 dp for accessibility (the track sits at 8 dp inside it). */
    val StripWidth = 48.dp

    /** Air between the track and the bars above and below it. */
    val VerticalGap = 12.dp

    /** System back-gesture exclusion around the active capsule (Android honours at most 200 dp per edge). */
    val ExclusionBand = 160.dp

    /** The active capsule's grab zone is at least this tall. */
    val GrabHeight = 48.dp

    internal val TrackCentre = 8.dp
    internal val InactiveWidth = 3.dp
    internal val ActiveWidth = 5.dp
    internal val Gap = 2.dp
    internal val MinGap = 1.dp
    internal val MinSegment = 6.dp
    internal val LabelStart = 15.dp
    internal const val LABEL_SP = 10f
    internal const val LABEL_SCALE_CAP = 1.2f

    /** The current number grows by this much while the capsule is held. */
    internal const val DRAG_LABEL_SCALE = 1.35f
    internal const val FADE_MILLIS = 200
    internal const val GRAB_MILLIS = 120

    /** Tests only: called on every (re)composition, so VerseScrubberTest can prove swipes don't recompose. */
    @androidx.annotation.VisibleForTesting
    var onComposed: (() -> Unit)? = null
}

/**
 * The segmented position track on the start edge of the verse feeds (B1 chapter, B2 liked list, H11 day): one capsule
 * per item (or per bucket of items in long chapters), the active one wider and full strength, the ones above it
 * medium, the ones below it quiet. Three numbers beside it: the first and last, dim, and the current one.
 *
 * - [position] is the pager's continuous position in items (currentPage + offset, relative to the span). It is read
 *   only in the draw phase, so swipes and scrubs redraw the track without recomposing it or the pages.
 * - Mid-swipe the old capsule fades to "passed" while the next widens and brightens in step with the finger; the
 *   numbers cross-fade the same way.
 * - Holding the active capsule and dragging calls [onScrub] once per item change (callers use scrollToPage) with a
 *   haptic tick, and [onScrubbingChange] true/false around it so callers save the reading position on release only.
 *   A tap elsewhere on the track jumps there.
 * - TalkBack: one slider node (progress range + setProgress, [stateDescription] "Verse 12 of 31"); the drawing itself
 *   has no nodes.
 *
 * Callers place it (align start, top/bottom padding); it sizes itself to [VerseScrubberDefaults.StripWidth] by the full
 * available height.
 */
@Composable
fun VerseScrubber(
    position: ScrubberPosition,
    count: Int,
    labelNumber: ScrubberLabelNumber,
    stateDescription: (Int) -> String,
    contentDescription: String,
    mode: ScrubberMode,
    scrolling: () -> Boolean,
    onScrub: (Int) -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Screenshot tests: draw as if the capsule were held. */
    forceDragging: Boolean = false,
) {
    if (!ScrubberVisibility.present(mode, count)) return
    SideEffect { VerseScrubberDefaults.onComposed?.invoke() }

    val density = LocalDensity.current
    val view = LocalView.current
    val colors = scrubberColors(BibleScrollTheme.readingColors)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val drag = remember(touchSlop) { ScrubberDrag(touchSlop) }

    val latestCount by rememberUpdatedState(count)
    val latestPosition by rememberUpdatedState(position)
    val latestLabel by rememberUpdatedState(labelNumber)
    val latestOnScrub by rememberUpdatedState(onScrub)
    val latestOnScrubbing by rememberUpdatedState(onScrubbingChange)
    val latestScrolling by rememberUpdatedState(scrolling)

    var dragIndex by remember { mutableIntStateOf(-1) }
    var touching by remember { mutableStateOf(false) }
    var trackPx by remember { mutableFloatStateOf(0f) }
    val layout = remember(count, trackPx, density) {
        with(density) {
            ScrubberLayout.of(
                count = count,
                trackLength = trackPx,
                minSegment = VerseScrubberDefaults.MinSegment.toPx(),
                gap = VerseScrubberDefaults.Gap.toPx(),
                minGap = VerseScrubberDefaults.MinGap.toPx(),
            )
        }
    }
    val latestLayout by rememberUpdatedState(layout)
    val grabPx = with(density) { VerseScrubberDefaults.GrabHeight.toPx() }

    // The item shown: the finger's while dragging (the pager catches up a frame later), else the nearest page.
    val shownIndex by remember {
        derivedStateOf { if (dragIndex >= 0) dragIndex else latestPosition.position().roundToInt().coerceIn(0, latestCount - 1) }
    }
    val dragging by remember { derivedStateOf { dragIndex >= 0 } }
    val grab = remember { Animatable(if (forceDragging) 1f else 0f) }
    LaunchedEffect(dragging, forceDragging) {
        grab.animateTo(if (dragging || forceDragging) 1f else 0f, tween(VerseScrubberDefaults.GRAB_MILLIS))
    }

    // Visibility per S2 setting: "only while scrolling" shows on scroll or touch and hides 1.5 s after.
    var visible by remember { mutableStateOf(mode == ScrubberMode.Always) }
    val active by remember { derivedStateOf { touching || latestScrolling() } }
    LaunchedEffect(mode) {
        if (mode != ScrubberMode.WhileScrolling) {
            visible = ScrubberVisibility.visible(mode, latestCount, active = false, millisSinceActive = null)
            return@LaunchedEffect
        }
        snapshotFlow { active }.collectLatest { isActive ->
            if (isActive) {
                visible = true
            } else {
                delay(ScrubberVisibility.HIDE_DELAY_MS)
                visible = false
            }
        }
    }
    val alpha = animateFloatAsState(if (visible) 1f else 0f, tween(VerseScrubberDefaults.FADE_MILLIS), label = "scrubberAlpha")

    // Numbers: Inter, tabular, scaled with the system font but capped at 1.2x. Measured once per number and style.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val base = MaterialTheme.typography.labelSmall
    val labelSize = (VerseScrubberDefaults.LABEL_SP * min(density.fontScale, VerseScrubberDefaults.LABEL_SCALE_CAP) / density.fontScale).sp
    val labels = remember(measurer, base, labelSize, density) {
        val style = TextStyle(fontFamily = base.fontFamily, fontSize = labelSize, fontFeatureSettings = "tnum")
        ScrubberLabels(
            dim = LabelCache(measurer, style.copy(fontWeight = FontWeight.Medium)),
            current = LabelCache(measurer, style.copy(fontWeight = FontWeight.SemiBold)),
        )
    }

    fun handle(effects: List<ScrubEffect>) {
        for (effect in effects) when (effect) {
            ScrubEffect.Started -> {
                dragIndex = drag.draggingIndex ?: -1
                latestOnScrubbing(true)
            }
            is ScrubEffect.ScrubTo -> {
                if (drag.draggingIndex != null) dragIndex = effect.index
                latestOnScrub(effect.index)
            }
            ScrubEffect.Tick -> view.performHapticFeedback(tickFeedback(latestLayout.bucketed))
            ScrubEffect.Reveal -> visible = true
            is ScrubEffect.Ended -> {
                dragIndex = -1
                latestOnScrubbing(false)
            }
        }
    }

    Box(
        modifier
            .fillMaxHeight()
            .width(VerseScrubberDefaults.StripWidth)
            .onSizeChanged { trackPx = it.height.toFloat() }
            .graphicsLayer { this.alpha = alpha.value }
            .automationTag(TestTags.VERSE_SCRUBBER)
            .semantics {
                this.contentDescription = contentDescription
                val n = latestCount
                val index = shownIndex
                this.stateDescription = stateDescription(index)
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = index.toFloat(),
                    range = 0f..(n - 1).toFloat(),
                    // Steps between the ends, so each TalkBack step moves exactly one item.
                    steps = (n - 2).coerceAtLeast(0),
                )
                setProgress { value ->
                    val target = value.roundToInt().coerceIn(0, latestCount - 1)
                    if (target != shownIndex) latestOnScrub(target)
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    touching = true
                    try {
                        handle(drag.down(down.position.y, latestLayout, shownIndex, visible, grabPx))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // The release can carry the last bit of movement: land where the finger lifted.
                                if (change.positionChanged()) handle(drag.move(change.position.y, latestLayout, shownIndex))
                                event.changes.forEach { it.consume() }
                                handle(drag.up(latestLayout, shownIndex))
                                break
                            }
                            if (change.positionChanged()) {
                                handle(drag.move(change.position.y, latestLayout, shownIndex))
                            }
                            // Consumed: nothing underneath (pager, page gestures) reacts to the strip.
                            event.changes.forEach { it.consume() }
                        }
                    } finally {
                        // Pointer cancelled (e.g. the system took the gesture): end the scrub once, saving the position.
                        touching = false
                        handle(drag.cancel())
                    }
                }
            }
            .drawBehind {
                val pos = if (dragIndex >= 0) dragIndex.toFloat() else latestPosition.position().coerceIn(0f, (latestCount - 1).toFloat())
                drawTrack(layout, pos, colors)
                drawLabels(layout, pos, colors, labels, latestLabel, grab.value)
            },
    ) {
        // Follows the active capsule so back-gesture exclusion covers only a band around it. Moving the band re-places
        // it, which is what updates the system's exclusion rect; it moves only when the active capsule changes.
        Box(
            Modifier
                .offset {
                    val band = VerseScrubberDefaults.ExclusionBand.toPx()
                    val centre = layout.centre(layout.bucketOf(shownIndex))
                    IntOffset(0, (centre - band / 2f).coerceIn(0f, (trackPx - band).coerceAtLeast(0f)).roundToInt())
                }
                .width(VerseScrubberDefaults.StripWidth)
                .height(VerseScrubberDefaults.ExclusionBand)
                .systemGestureExclusion(),
        )
    }
}

/** The pager's continuous position in items. A fun interface, not () -> Float, so reading it every frame doesn't box. */
fun interface ScrubberPosition {
    fun position(): Float
}

/** The number shown for item [index] (verse number or 1-based index). Primitive, so per-frame reads don't box. */
fun interface ScrubberLabelNumber {
    fun numberAt(index: Int): Int
}

/** Measured numbers per style, so steady-state frames allocate nothing. */
internal class LabelCache(private val measurer: TextMeasurer, private val style: TextStyle) {
    private val cache = SparseArray<TextLayoutResult>()

    operator fun get(number: Int): TextLayoutResult =
        cache.get(number) ?: measurer.measure(number.toString(), style).also { cache.put(number, it) }
}

internal class ScrubberLabels(val dim: LabelCache, val current: LabelCache)

private fun DrawScope.trackX(): Float {
    val x = VerseScrubberDefaults.TrackCentre.toPx()
    return if (layoutDirection == LayoutDirection.Ltr) x else size.width - x
}

/** Capsule i: weight w = max(0, 1 - |position - i|) widens it from 3 to 5 dp and lerps base -> active. */
private fun DrawScope.drawTrack(layout: ScrubberLayout, position: Float, colors: ScrubberColors) {
    val x = trackX()
    val inactive = VerseScrubberDefaults.InactiveWidth.toPx()
    val activeWidth = VerseScrubberDefaults.ActiveWidth.toPx()
    val bp = layout.bucketPosition(position)
    val h = layout.segmentHeight
    for (b in 0 until layout.segments) {
        val w = ScrubberMath.weight(b, bp)
        val width = ScrubberMath.lerp(inactive, activeWidth, w)
        val base = if (b < bp) colors.passed else colors.upcoming
        drawRoundRect(
            color = if (w <= 0f) base else lerp(base, colors.active, w),
            topLeft = Offset(x - width / 2f, layout.top(b)),
            size = Size(width, h),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}

/**
 * First and last numbers dim; the current one bold. Mid-swipe the outgoing and incoming numbers are both drawn, at
 * 1 - f and f. A dim end label fades out while a current number sits on top of it.
 */
private fun DrawScope.drawLabels(
    layout: ScrubberLayout,
    position: Float,
    colors: ScrubberColors,
    labels: ScrubberLabels,
    number: ScrubberLabelNumber,
    grab: Float,
) {
    if (layout.segments <= 0) return
    val count = layout.count
    val i0 = floor(position).toInt().coerceIn(0, count - 1)
    val f = position - i0
    val i1 = (i0 + 1).coerceAtMost(count - 1)
    val y0 = layout.centre(layout.bucketOf(i0))
    val y1 = layout.centre(layout.bucketOf(i1))
    val a0 = 1f - f
    val a1 = if (i1 != i0) f else 0f

    val first = labels.dim[number.numberAt(0)]
    val last = labels.dim[number.numberAt(count - 1)]
    val firstY = layout.centre(0)
    val lastY = layout.centre(layout.segments - 1)
    val lh = first.size.height.toFloat()
    drawLabel(first, firstY, colors.labelDim, 1f - covered(firstY, y0, a0, y1, a1, lh), 1f)
    drawLabel(last, lastY, colors.labelDim, 1f - covered(lastY, y0, a0, y1, a1, lh), 1f)

    val scale = ScrubberMath.lerp(1f, VerseScrubberDefaults.DRAG_LABEL_SCALE, grab)
    if (a0 > 0f) drawLabel(labels.current[number.numberAt(i0)], y0, colors.labelCurrent, a0, scale)
    if (a1 > 0f) drawLabel(labels.current[number.numberAt(i1)], y1, colors.labelCurrent, a1, scale)
}

/** How much the current numbers (at [y0]/[y1], alphas [a0]/[a1]) cover an end label at [y]: 0 clear, 1 on top. */
private fun covered(y: Float, y0: Float, a0: Float, y1: Float, a1: Float, lineHeight: Float): Float {
    fun near(cy: Float) = 1f - ((abs(cy - y) - lineHeight) / lineHeight).coerceIn(0f, 1f)
    return maxOf(a0 * near(y0), a1 * near(y1))
}

private fun DrawScope.drawLabel(text: TextLayoutResult, centreY: Float, color: Color, alpha: Float, scale: Float) {
    if (alpha <= 0.01f) return
    val start = VerseScrubberDefaults.LabelStart.toPx()
    val ltr = layoutDirection == LayoutDirection.Ltr
    val w = text.size.width.toFloat()
    val x = if (ltr) start else size.width - start - w
    val top = centreY - text.size.height / 2f
    val pivot = Offset(if (ltr) x else x + w, centreY)
    scale(scale, pivot) {
        drawText(text, color = color, topLeft = Offset(x, top), alpha = alpha)
    }
}

/** Segment ticks on Android 14+ (lighter, frequent ones when items are bucketed); a clock tick before. */
private fun tickFeedback(bucketed: Boolean): Int = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        if (bucketed) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.SEGMENT_TICK
    else -> HapticFeedbackConstants.CLOCK_TICK
}
