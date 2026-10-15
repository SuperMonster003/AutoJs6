package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NodeImageDownsampleTest {

    @Test fun imagesWithinTheBudgetKeepFullSize() {
        assertEquals(1, NodeImageDownsample.sampleSize(300, 200))
        assertEquals(1, NodeImageDownsample.sampleSize(4096, 10))
        assertEquals(1, NodeImageDownsample.sampleSize(10, 4096))
    }

    @Test fun largerImagesHalveUntilTheLongestEdgeFits() {
        assertEquals(2, NodeImageDownsample.sampleSize(4097, 10))
        assertEquals(2, NodeImageDownsample.sampleSize(4100, 1000))
        assertEquals(2, NodeImageDownsample.sampleSize(8192, 100))
        assertEquals(4, NodeImageDownsample.sampleSize(8193, 100))
        // A 20000 x 15000 photo: 20000 / 4 = 5000 is still over budget, 20000 / 8 = 2500 fits.
        assertEquals(8, NodeImageDownsample.sampleSize(20000, 15000))
    }

    @Test fun theBudgetIsAdjustableAndRoundsUp() {
        assertEquals(2, NodeImageDownsample.sampleSize(101, 50, maxEdge = 100))
        // 201 -> 101 -> 51 -> 26: each halving rounds up, so 26 <= 50 needs sample 8, not 4.
        assertEquals(8, NodeImageDownsample.sampleSize(201, 10, maxEdge = 50))
    }

    @Test fun invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { NodeImageDownsample.sampleSize(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { NodeImageDownsample.sampleSize(10, -1) }
        assertThrows(IllegalArgumentException::class.java) { NodeImageDownsample.sampleSize(10, 10, maxEdge = 0) }
    }
}
