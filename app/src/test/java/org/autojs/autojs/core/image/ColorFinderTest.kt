package org.autojs.autojs.core.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorFinderTest {

    @Test
    fun overlapCoversWholeRegionForZeroOffset() {
        assertArrayEquals(intArrayOf(0, 0, 100, 50), ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, 0, 0))
        assertArrayEquals(intArrayOf(0, 0, 30, 20), ColorFinder.shiftedOverlap(10, 5, 30, 20, 100, 50, 0, 0))
    }

    @Test
    fun positiveOffsetsTrimTheRightAndBottomEdges() {
        /* Candidates at x >= 95 would read column x + 5 >= 100, outside the image. */
        assertArrayEquals(intArrayOf(0, 0, 95, 47), ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, 5, 3))
        /* A region ending before the image edge is not trimmed when the offset still fits. */
        assertArrayEquals(intArrayOf(0, 0, 30, 20), ColorFinder.shiftedOverlap(10, 5, 30, 20, 100, 50, 5, 3))
    }

    @Test
    fun negativeOffsetsTrimTheLeftAndTopEdges() {
        assertArrayEquals(intArrayOf(5, 3, 100, 50), ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, -5, -3))
        /* Region starts at x = 10, so an offset of -5 keeps every candidate inside the image. */
        assertArrayEquals(intArrayOf(0, 0, 30, 20), ColorFinder.shiftedOverlap(10, 5, 30, 20, 100, 50, -5, -3))
        /* Offset -12 removes the first two columns of the region. */
        assertArrayEquals(intArrayOf(2, 0, 30, 20), ColorFinder.shiftedOverlap(10, 5, 30, 20, 100, 50, -12, 0))
    }

    @Test
    fun offsetsLeavingTheImageEntirelyYieldNoOverlap() {
        assertNull(ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, 100, 0))
        assertNull(ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, 0, -50))
        assertNull(ColorFinder.shiftedOverlap(10, 5, 30, 20, 100, 50, -40, 0))
        assertNull(ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, Int.MAX_VALUE, 0))
        assertNull(ColorFinder.shiftedOverlap(0, 0, 100, 50, 100, 50, 0, Int.MIN_VALUE))
    }
}
