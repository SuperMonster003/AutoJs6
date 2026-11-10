package org.autojs.autojs.core.virtualscreen

import org.autojs.autojs.core.virtualscreen.DisplayCoordinateMapper.fit
import org.autojs.autojs.core.virtualscreen.DisplayCoordinateMapper.isInside
import org.autojs.autojs.core.virtualscreen.DisplayCoordinateMapper.toDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Viewer gestures must land on the same logical pixel the user touched, including letterbox
 * offsets and out-of-bounds clamps. A wrong scale here silently clicks the wrong UI element on the
 * virtual display, so the geometry is pinned on the JVM.
 *
 * zh-CN: 查看器手势必须落到用户触摸的同一个逻辑像素, 含留边偏移与越界收敛. 换算错误会点中虚拟屏上
 * 错误的控件, 因此在 JVM 上固定这套几何规则.
 */
class DisplayCoordinateMapperTest {

    @Test
    fun sameAspectRatioFillsTheWholeView() {
        val geometry = fit(400, 800, 1000, 2000)
        assertEquals(0.4f, geometry.scale, EPS)
        assertEquals(0f, geometry.offsetX, EPS)
        assertEquals(0f, geometry.offsetY, EPS)
        assertEquals(400f, geometry.drawnWidth, EPS)
        assertEquals(800f, geometry.drawnHeight, EPS)
    }

    @Test
    fun squareDisplayIsCenteredWithVerticalLetterbox() {
        val geometry = fit(400, 800, 1000, 1000)
        assertEquals(0.4f, geometry.scale, EPS)
        assertEquals(0f, geometry.offsetX, EPS)
        assertEquals(200f, geometry.offsetY, EPS)
        assertTrue(isInside(geometry, 200f, 400f))
        assertFalse(isInside(geometry, 200f, 100f))
        assertFalse(isInside(geometry, 200f, 700f))
    }

    @Test
    fun squareDisplayCenteredInAWideViewHasHorizontalLetterbox() {
        val geometry = fit(800, 400, 1000, 1000)
        assertEquals(0.4f, geometry.scale, EPS)
        assertEquals(200f, geometry.offsetX, EPS)
        assertEquals(0f, geometry.offsetY, EPS)
        assertFalse(isInside(geometry, 100f, 100f))
        assertTrue(isInside(geometry, 300f, 100f))
    }

    @Test
    fun touchCoordinatesMapBackToDisplayPixels() {
        val geometry = fit(400, 800, 1000, 2000)
        assertEquals(500, toDisplay(geometry, 200f, 400f).x)
        assertEquals(1000, toDisplay(geometry, 200f, 400f).y)
        assertEquals(0, toDisplay(geometry, 0f, 0f).x)
        assertEquals(0, toDisplay(geometry, 0f, 0f).y)
    }

    @Test
    fun letterboxOffsetIsSubtractedBeforeScaling() {
        val geometry = fit(400, 800, 1000, 1000)
        val topLeft = toDisplay(geometry, 0f, 200f)
        assertEquals(0, topLeft.x)
        assertEquals(0, topLeft.y)
        val center = toDisplay(geometry, 200f, 400f)
        assertEquals(500, center.x)
        assertEquals(500, center.y)
    }

    @Test
    fun touchesOutsideThePictureAreClampedToTheLastPixel() {
        val geometry = fit(400, 800, 1000, 1000)
        val above = toDisplay(geometry, 200f, 0f)
        assertEquals(0, above.y)
        val below = toDisplay(geometry, 200f, 799f)
        assertEquals(999, below.y)
        val right = toDisplay(geometry, 400f, 400f)
        assertEquals(999, right.x)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroViewWidthIsRejected() {
        fit(0, 800, 1000, 2000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroScreenWidthIsRejected() {
        fit(400, 800, 0, 2000)
    }

    private companion object {
        const val EPS = 0.0001f
    }

}
