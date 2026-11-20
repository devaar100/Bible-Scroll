package aarnav100.developer.biblescroll.ui.components.scrubber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Px at density 1, so the numbers read as dp: 6 dp minimum capsule, 2 dp gap, 1 dp fallback gap. */
class ScrubberMathTest {

    private fun layout(count: Int, track: Float = 600f) = ScrubberLayout.of(count, track, minSegment = 6f, gap = 2f, minGap = 1f)

    @Test
    fun aChapterOf31GetsOneCapsuleEachFillingTheTrack() {
        val l = layout(31)
        assertEquals(31, l.segments)
        assertFalse(l.bucketed)
        assertEquals(2f, l.gap, 0f)
        assertEquals((600f - 30 * 2f) / 31, l.segmentHeight, 0.001f)
        assertEquals(600f, l.top(30) + l.segmentHeight, 0.01f)
    }

    @Test
    fun theGapDropsToOneBeforeItemsAreGrouped() {
        // 80 items: 2 dp gaps leave 5.5 dp capsules, 1 dp gaps leave 6.5 dp.
        val l = layout(80)
        assertEquals(80, l.segments)
        assertEquals(1f, l.gap, 0f)
        assertTrue(l.segmentHeight >= 6f)
    }

    @Test
    fun psalm119IsBucketedSoEveryCapsuleIsAtLeastSixTall() {
        val l = layout(176)
        assertTrue(l.bucketed)
        assertEquals(75, l.segments) // floor((600 + 2) / (6 + 2))
        assertEquals(2f, l.gap, 0f)
        assertTrue(l.segmentHeight >= 6f)
        assertEquals(600f, l.top(l.segments - 1) + l.segmentHeight, 0.01f)
    }

    @Test
    fun capsulesAreNeverBelowTheMinimumAndBucketsAreNeverEmpty() {
        for (track in listOf(200f, 451f, 600f, 900f)) {
            for (count in 2..400) {
                val l = layout(count, track)
                assertTrue("$count over $track: ${l.segmentHeight}", l.segmentHeight >= 6f - 1e-3f)
                assertEquals(0, l.firstItemOf(0))
                assertEquals(count, l.firstItemOf(l.segments))
                for (b in 0 until l.segments) {
                    assertTrue("$count/$track bucket $b empty", l.firstItemOf(b + 1) > l.firstItemOf(b))
                    assertEquals(b, l.bucketOf(l.firstItemOf(b)))
                    assertEquals(b, l.bucketOf(l.firstItemOf(b + 1) - 1))
                }
            }
        }
    }

    @Test
    fun tappingACapsuleCentreFindsItsItem() {
        for (count in listOf(2, 31, 80)) {
            val l = layout(count)
            for (i in 0 until count) assertEquals("count $count item $i", i, l.itemAt(l.centre(i)))
        }
        val p = layout(176)
        for (b in 0 until p.segments) assertEquals(b, p.bucketOf(p.itemAt(p.centre(b))))
    }

    @Test
    fun draggingThroughABucketVisitsEachOfItsItems() {
        val l = layout(176)
        val seen = (0..600).map { l.itemAt(it.toFloat()) }.toSortedSet()
        assertEquals((0 until 176).toSet(), seen)
    }

    @Test
    fun touchesOutsideTheTrackClamp() {
        val l = layout(31)
        assertEquals(0, l.itemAt(-500f))
        assertEquals(30, l.itemAt(5_000f))
    }

    @Test
    fun bucketPositionFollowsThePagerContinuously() {
        val l = layout(31)
        assertEquals(11.5f, l.bucketPosition(11.5f), 1e-4f)
        assertEquals(0f, l.bucketPosition(-0.4f), 0f)
        assertEquals(30f, l.bucketPosition(30.4f), 0f)

        val p = layout(176)
        // Inside one bucket the active capsule doesn't move; across a boundary it moves with the finger.
        val b = p.bucketOf(10)
        val first = p.firstItemOf(b)
        val next = p.firstItemOf(b + 1)
        assertEquals(b.toFloat(), p.bucketPosition(first.toFloat()), 0f)
        if (next - first > 1) assertEquals(b.toFloat(), p.bucketPosition(first + 0.5f), 0f)
        assertEquals(b + 0.5f, p.bucketPosition(next - 1 + 0.5f), 1e-4f)
    }

    @Test
    fun weightIsOneOnTheActiveCapsuleAndSplitsMidSwipe() {
        assertEquals(1f, ScrubberMath.weight(3, 3f), 0f)
        assertEquals(0.5f, ScrubberMath.weight(3, 3.5f), 1e-6f)
        assertEquals(0.5f, ScrubberMath.weight(4, 3.5f), 1e-6f)
        assertEquals(0f, ScrubberMath.weight(5, 3.5f), 0f)
        assertEquals(0f, ScrubberMath.weight(0, 3f), 0f)
    }

    @Test
    fun grabZoneIsAtLeastTheMinimumHeight() {
        val l = layout(31) // ~17.4 px capsules
        val c = l.centre(10)
        assertTrue(l.inGrabZone(c, 10, minHeight = 48f))
        assertTrue(l.inGrabZone(c + 23f, 10, minHeight = 48f))
        assertFalse(l.inGrabZone(c + 25f, 10, minHeight = 48f))
    }

    @Test
    fun degenerateInputsDontCrash() {
        val empty = ScrubberLayout.of(0, 600f, 6f, 2f, 1f)
        assertEquals(0, empty.itemAt(10f))
        val flat = ScrubberLayout.of(31, 0f, 6f, 2f, 1f)
        assertEquals(0, flat.segments)
        assertEquals(0, flat.itemAt(10f))
        assertEquals(0f, layout(1).bucketPosition(3f), 0f)
        val tiny = layout(31, track = 4f) // shorter than one capsule: one bucket
        assertEquals(1, tiny.segments)
    }
}
