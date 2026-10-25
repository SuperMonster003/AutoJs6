package org.autojs.autojs.runtime.api.modules.images

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.opencv.core.Point

class ObjectFrameTest {

    @Before
    fun setUp() {
        Context.enter()
    }

    @After
    fun tearDown() {
        Context.exit()
    }

    @Test
    fun axisAlignedFrameReportsItsGeometry() {
        val frame = ObjectFrame(Point(300.0, 200.0), Point(460.0, 200.0), Point(300.0, 320.0), Point(460.0, 320.0), 40, 25, listOf(Point(1.0, 2.0), Point(3.0, 4.0)))
        assertEquals(160.0, frame.width, 1e-9)
        assertEquals(120.0, frame.height, 1e-9)
        assertEquals(0.0, frame.angle, 1e-9)
        assertEquals(380.0, frame.centerX, 1e-9)
        assertEquals(260.0, frame.centerY, 1e-9)
        assertEquals(300, frame.bounds.x)
        assertEquals(200, frame.bounds.y)
        assertEquals(160, frame.bounds.width)
        assertEquals(120, frame.bounds.height)
        assertEquals(40, frame.matches)
        assertEquals(25, frame.inliers)
        assertEquals(2L, frame.points.length)
    }

    @Test
    fun rotatedFrameKeepsCornerCorrespondenceAndAngle() {
        /* The object rotated 90 degrees clockwise: its top-left corner is now at the top-right of the area. */
        val frame = ObjectFrame(Point(220.0, 100.0), Point(220.0, 260.0), Point(100.0, 100.0), Point(100.0, 260.0))
        assertEquals(90.0, frame.angle, 1e-9)
        assertEquals(160.0, frame.width, 1e-9)
        assertEquals(120.0, frame.height, 1e-9)
        assertEquals(100, frame.bounds.x)
        assertEquals(100, frame.bounds.y)
        assertEquals(120, frame.bounds.width)
        assertEquals(160, frame.bounds.height)
        assertEquals(0, frame.matches)
        assertEquals(0L, frame.points.length)
    }

    @Test
    fun boundsRoundOutwardsForFractionalCorners() {
        val frame = ObjectFrame(Point(10.4, 20.6), Point(50.5, 20.2), Point(10.1, 60.9), Point(50.8, 60.3))
        assertEquals(10, frame.bounds.x)
        assertEquals(20, frame.bounds.y)
        assertEquals(41, frame.bounds.width)
        assertEquals(41, frame.bounds.height)
    }
}
