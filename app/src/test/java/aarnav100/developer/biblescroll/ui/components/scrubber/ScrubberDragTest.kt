package aarnav100.developer.biblescroll.ui.components.scrubber

import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubEffect.Ended
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubEffect.Reveal
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubEffect.ScrubTo
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubEffect.Started
import aarnav100.developer.biblescroll.ui.components.scrubber.ScrubEffect.Tick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrubberDragTest {

    private val l = ScrubberLayout.of(31, 1000f, minSegment = 6f, gap = 2f, minGap = 1f)
    private val grab = 48f
    private val drag = ScrubberDrag(touchSlop = 8f)
    private val none = emptyList<ScrubEffect>()

    private fun y(index: Int) = l.centre(l.bucketOf(index))

    @Test
    fun holdingTheActiveCapsuleGrabsItWithoutMoving() {
        assertEquals(listOf(Started), drag.down(y(4), l, current = 4, visible = true, grabHeight = grab))
        assertEquals(ScrubberDrag.Phase.Dragging(4), drag.phase)
        assertEquals(4, drag.draggingIndex)
    }

    @Test
    fun draggingTheGrabbedCapsuleTicksOncePerItem() {
        drag.down(y(4), l, current = 4, visible = true, grabHeight = grab)
        assertEquals(none, drag.move(y(4) + 3f, l, current = 4))
        assertEquals(listOf(ScrubTo(5), Tick), drag.move(y(5), l, current = 4))
        assertEquals(none, drag.move(y(5) + 1f, l, current = 5))
        // A jump over several items is still one scroll and one tick, to the item under the finger.
        assertEquals(listOf(ScrubTo(20), Tick), drag.move(y(20), l, current = 5))
    }

    @Test
    fun tapOffTheActiveCapsuleJumpsOnRelease() {
        assertEquals(none, drag.down(y(20), l, current = 4, visible = true, grabHeight = grab))
        assertNull(drag.draggingIndex)
        assertEquals(listOf(ScrubTo(20), Tick), drag.up(l, current = 4))
        assertEquals(ScrubberDrag.Phase.Idle, drag.phase)
    }

    @Test
    fun dragStartingOffTheActiveCapsuleDoesNothing() {
        drag.down(y(20), l, current = 4, visible = true, grabHeight = grab)
        assertEquals(none, drag.move(y(24), l, current = 4))
        assertEquals(ScrubberDrag.Phase.Ignored, drag.phase)
        assertEquals(none, drag.move(y(28), l, current = 4))
        assertEquals("no jump either", none, drag.up(l, current = 4))
    }

    @Test
    fun upEndsTheScrubOnce() {
        drag.down(y(3), l, current = 3, visible = true, grabHeight = grab)
        drag.move(y(9), l, current = 3)
        assertEquals(listOf(Ended(cancelled = false)), drag.up(l, current = 9))
        assertEquals(ScrubberDrag.Phase.Idle, drag.phase)
        // The composable's finally block also cancels: that must not end it a second time.
        assertEquals(none, drag.cancel())
    }

    @Test
    fun cancelEndsTheScrubAsCancelled() {
        drag.down(y(3), l, current = 3, visible = true, grabHeight = grab)
        assertEquals(listOf(Ended(cancelled = true)), drag.cancel())
        assertEquals(none, drag.up(l, current = 3))
    }

    @Test
    fun cancelledTapDoesNotJump() {
        drag.down(y(20), l, current = 4, visible = true, grabHeight = grab)
        assertEquals(none, drag.cancel())
        assertEquals(none, drag.up(l, current = 4))
    }

    @Test
    fun hiddenScrubberRevealsOnTouchAndGrabsOnlyFromTheActiveCapsule() {
        assertEquals(listOf(Reveal), drag.down(y(10), l, current = 10, visible = false, grabHeight = grab))
        assertEquals(none, drag.move(y(10) + 5f, l, current = 10)) // within slop
        assertEquals(listOf(Started, ScrubTo(14), Tick), drag.move(y(14), l, current = 10))
    }

    @Test
    fun tapOnAHiddenScrubberDoesNotJump() {
        drag.down(y(20), l, current = 4, visible = false, grabHeight = grab)
        assertEquals(none, drag.up(l, current = 4))
    }

    @Test
    fun singleItemListsIgnoreTouches() {
        val one = ScrubberLayout.of(1, 1000f, 6f, 2f, 1f)
        assertEquals(none, drag.down(500f, one, current = 0, visible = true, grabHeight = grab))
        assertEquals(ScrubberDrag.Phase.Idle, drag.phase)
    }

    @Test
    fun dragPastTheEndsClamps() {
        drag.down(y(30), l, current = 30, visible = true, grabHeight = grab)
        assertEquals(none, drag.move(4000f, l, current = 30))
        assertEquals(listOf(ScrubTo(0), Tick), drag.move(-4000f, l, current = 30))
    }

    @Test
    fun bucketedChaptersStillScrubOneVerseAtATime() {
        val p = ScrubberLayout.of(176, 600f, 6f, 2f, 1f)
        val start = p.centre(p.bucketOf(0))
        drag.down(start, p, current = 0, visible = true, grabHeight = grab)
        val visited = mutableListOf<Int>()
        var current = 0
        var yy = start
        while (yy < 600f) {
            yy += 0.5f
            for (e in drag.move(yy, p, current)) if (e is ScrubTo) { visited += e.index; current = e.index }
        }
        assertEquals((1 until 176).toList(), visited)
    }
}
