package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.data.settings.ScrubberMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * The segmented track (px): [segments] equal capsules of [segmentHeight], [gap] apart, filling [trackLength].
 * One capsule per item while each can be at least the minimum height; longer lists (Psalm 119: 176 verses) group
 * items into buckets, [count] items over [segments] capsules.
 */
class ScrubberLayout private constructor(
    val count: Int,
    val trackLength: Float,
    val segments: Int,
    val segmentHeight: Float,
    val gap: Float,
) {
    private val slot get() = segmentHeight + gap

    val bucketed: Boolean get() = segments < count

    /** Capsule holding [item]. */
    fun bucketOf(item: Int): Int {
        if (segments <= 0 || count <= 0) return 0
        val i = item.coerceIn(0, count - 1)
        return ((i.toLong() * segments) / count).toInt()
    }

    /** First item in capsule [bucket] (count for the bucket after the last). */
    fun firstItemOf(bucket: Int): Int {
        if (segments <= 0) return 0
        val b = bucket.coerceIn(0, segments)
        return ((b.toLong() * count + segments - 1) / segments).toInt()
    }

    fun top(bucket: Int): Float = bucket * slot

    fun centre(bucket: Int): Float = top(bucket) + segmentHeight / 2f

    /**
     * Continuous pager position in items (0..count-1, fractional mid-swipe) to a continuous capsule position. Inside
     * one bucket it stays on that capsule; crossing into the next capsule moves in step with the finger.
     */
    fun bucketPosition(position: Float): Float {
        if (count <= 1) return 0f
        val p = position.coerceIn(0f, (count - 1).toFloat())
        val i = floor(p).toInt()
        val f = p - i
        val b0 = bucketOf(i)
        if (f == 0f) return b0.toFloat()
        val b1 = bucketOf(i + 1)
        return b0 + f * (b1 - b0)
    }

    /** The item under [y] (px from the track top), proportionally inside a bucket. Clamps outside the track. */
    fun itemAt(y: Float): Int {
        if (count <= 1 || segments <= 0) return 0
        val shifted = y + gap / 2f
        val bucket = floor(shifted / slot).toInt().coerceIn(0, segments - 1)
        val within = ((shifted - bucket * slot) / slot).coerceIn(0f, 0.9999f)
        val first = firstItemOf(bucket)
        val size = max(1, firstItemOf(bucket + 1) - first)
        return (first + floor(within * size).toInt()).coerceIn(0, count - 1)
    }

    /** Grab zone around the capsule of [item]: the capsule, but at least [minHeight] tall. */
    fun inGrabZone(y: Float, item: Int, minHeight: Float): Boolean {
        val half = max(segmentHeight, minHeight) / 2f
        return abs(y - centre(bucketOf(item))) <= half
    }

    companion object {
        /**
         * Lays [count] items over [trackLength]: one capsule each with [gap] if every capsule reaches [minSegment],
         * else with [minGap], else buckets with [gap] so every capsule is at least [minSegment] tall.
         */
        fun of(count: Int, trackLength: Float, minSegment: Float, gap: Float, minGap: Float): ScrubberLayout {
            if (count <= 0 || trackLength <= 0f) return ScrubberLayout(max(count, 0), max(trackLength, 0f), 0, 0f, gap)
            for (g in floatArrayOf(gap, minGap)) {
                val h = (trackLength - g * (count - 1)) / count
                if (h >= minSegment) return ScrubberLayout(count, trackLength, count, h, g)
            }
            val n = floor((trackLength + gap) / (minSegment + gap)).toInt().coerceIn(1, count)
            val h = (trackLength - gap * (n - 1)) / n
            return ScrubberLayout(count, trackLength, n, h, gap)
        }
    }
}

/** Per-capsule emphasis: 1 on the active capsule, falling to 0 one capsule away. Drives width, colour and labels. */
object ScrubberMath {
    fun weight(bucket: Int, bucketPosition: Float): Float = max(0f, 1f - abs(bucketPosition - bucket))

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}

/** What the drag machine asks the screen to do. */
sealed interface ScrubEffect {
    /** The active capsule was grabbed: stop saving the reading position until [Ended]. */
    data object Started : ScrubEffect

    /** Move the pager to [index] (scrollToPage, no animation). */
    data class ScrubTo(val index: Int) : ScrubEffect

    /** One haptic tick: the selected item changed. */
    data object Tick : ScrubEffect

    /** A touch on the hidden scrubber ("only while scrolling"): show it, don't move. */
    data object Reveal : ScrubEffect

    /** The scrub is over; save the position now. [cancelled] when the system took the gesture. */
    data class Ended(val cancelled: Boolean) : ScrubEffect
}

/**
 * Down / move / up / cancel for one pointer on the scrubber strip. Pure: the composable feeds it positions and acts on
 * the returned effects.
 *
 * - Down on the active capsule's zone grabs it; dragging then scrubs, one tick per item.
 * - A tap anywhere else on the track jumps to the item under the finger (on release, so it can't fire mid-swipe).
 * - A drag that starts off the active zone does nothing: only the grabbed capsule scrubs.
 * - On a hidden scrubber ("only while scrolling") a touch only reveals it; a drag from the active zone still grabs.
 * - Up or cancel ends a scrub once: the reading position is saved then, not on every page.
 */
class ScrubberDrag(private val touchSlop: Float) {

    sealed interface Phase {
        data object Idle : Phase
        /** Finger down, not yet past the slop. [onActive]: on the grab zone. [tapJumps]: a release here jumps. */
        data class Pressed(val downY: Float, val onActive: Boolean, val tapJumps: Boolean) : Phase
        data class Dragging(val index: Int) : Phase
        /** Moved off the active zone: ignored until release. */
        data object Ignored : Phase
    }

    var phase: Phase = Phase.Idle
        private set

    /** Index under the finger while scrubbing, else null. */
    val draggingIndex: Int? get() = (phase as? Phase.Dragging)?.index

    fun down(y: Float, layout: ScrubberLayout, current: Int, visible: Boolean, grabHeight: Float): List<ScrubEffect> {
        if (layout.count <= 1) return emptyList()
        val onActive = layout.inGrabZone(y, current, grabHeight)
        if (!visible) {
            phase = Phase.Pressed(y, onActive, tapJumps = false)
            return listOf(ScrubEffect.Reveal)
        }
        if (onActive) {
            phase = Phase.Dragging(current)
            return listOf(ScrubEffect.Started)
        }
        phase = Phase.Pressed(y, onActive = false, tapJumps = true)
        return emptyList()
    }

    fun move(y: Float, layout: ScrubberLayout, current: Int): List<ScrubEffect> =
        when (val p = phase) {
            Phase.Idle, Phase.Ignored -> emptyList()
            is Phase.Pressed -> when {
                abs(y - p.downY) <= touchSlop -> emptyList()
                p.onActive -> {
                    val index = layout.itemAt(y)
                    phase = Phase.Dragging(index)
                    if (index == current) listOf(ScrubEffect.Started) else listOf(ScrubEffect.Started, ScrubEffect.ScrubTo(index), ScrubEffect.Tick)
                }
                else -> {
                    phase = Phase.Ignored
                    emptyList()
                }
            }
            is Phase.Dragging -> {
                val index = layout.itemAt(y)
                if (index == p.index) {
                    emptyList()
                } else {
                    phase = Phase.Dragging(index)
                    listOf(ScrubEffect.ScrubTo(index), ScrubEffect.Tick)
                }
            }
        }

    fun up(layout: ScrubberLayout, current: Int): List<ScrubEffect> {
        val p = phase
        phase = Phase.Idle
        return when {
            p is Phase.Dragging -> listOf(ScrubEffect.Ended(cancelled = false))
            p is Phase.Pressed && p.tapJumps -> {
                val index = layout.itemAt(p.downY)
                if (index == current) emptyList() else listOf(ScrubEffect.ScrubTo(index), ScrubEffect.Tick)
            }
            else -> emptyList()
        }
    }

    fun cancel(): List<ScrubEffect> {
        val wasDragging = phase is Phase.Dragging
        phase = Phase.Idle
        return if (wasDragging) listOf(ScrubEffect.Ended(cancelled = true)) else emptyList()
    }
}

/** When the scrubber is composed and when it is drawn, per S2 setting. */
object ScrubberVisibility {
    /** "Only while scrolling": how long it stays after the last scroll or touch. */
    const val HIDE_DELAY_MS = 1_500L

    /** Composed at all (takes touches, excludes system gestures): not Off, and more than one item. */
    fun present(mode: ScrubberMode, count: Int): Boolean = mode != ScrubberMode.Off && count > 1

    /**
     * Drawn now. [active] = the pager is scrolling or the scrubber is touched; [millisSinceActive] = time since that
     * stopped (null if it never happened).
     */
    fun visible(mode: ScrubberMode, count: Int, active: Boolean, millisSinceActive: Long?): Boolean = when {
        !present(mode, count) -> false
        mode == ScrubberMode.Always -> true
        active -> true
        else -> millisSinceActive != null && millisSinceActive < HIDE_DELAY_MS
    }

    /** Start inset for page text so it never sits under the track or its labels. */
    fun textStartInsetDp(mode: ScrubberMode): Int = if (mode == ScrubberMode.Off) DEFAULT_TEXT_START_DP else SCRUBBER_TEXT_START_DP

    const val DEFAULT_TEXT_START_DP = 28

    /**
     * Label column start (15 dp) + widest label ("176" at the 1.2x font cap, enlarged while dragging: ~30 dp) + air.
     * ScrubberScreenshotTest.largestFont checks it.
     */
    const val SCRUBBER_TEXT_START_DP = 52
}
