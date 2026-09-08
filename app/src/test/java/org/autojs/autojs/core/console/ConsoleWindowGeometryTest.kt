package org.autojs.autojs.core.console

import org.autojs.autojs.core.console.ConsoleWindowGeometry.Point
import org.autojs.autojs.core.console.ConsoleWindowGeometry.Screen
import org.autojs.autojs.core.console.ConsoleWindowGeometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsoleWindowGeometryTest {

    // Mirrors android.view.Gravity; the constants are inlined at compile time.
    // zh-CN: 与 android.view.Gravity 一致; 常量在编译期内联.
    private val noGravity = 0
    private val left = 3
    private val right = 5
    private val centerHorizontal = 1
    private val top = 0x30
    private val bottom = 0x50
    private val centerVertical = 0x10
    private val start = 0x800003
    private val end = 0x800005

    private val screen = Screen(width = 1080, height = 2000, topInset = 80)

    @Test
    fun positionWithoutGravityIsOffsetByTopInset() {
        assertEquals(Point(0, 80), ConsoleWindowGeometry.resolve(noGravity, true, 0, 0, 500, 400, screen))
        assertEquals(Point(100, 380), ConsoleWindowGeometry.resolve(noGravity, true, 100, 300, 500, 400, screen))
        assertEquals(Point(0, 80), ConsoleWindowGeometry.resolve(noGravity, true, null, null, 500, 400, screen))
    }

    @Test
    fun gravityResolvesEdgesAndCenters() {
        assertEquals(Point(580, 1600), ConsoleWindowGeometry.resolve(right or bottom, true, 5, 5, 500, 400, screen))
        assertEquals(Point(0, 80), ConsoleWindowGeometry.resolve(left or top, true, 300, 300, 500, 400, screen))
        assertEquals(Point(290, 840), ConsoleWindowGeometry.resolve(centerHorizontal or centerVertical, true, 0, 0, 500, 400, screen))
        // A single axis keeps the configured position on the other one.
        // zh-CN: 单轴 gravity 保留另一轴的配置坐标.
        assertEquals(Point(580, 380), ConsoleWindowGeometry.resolve(right, true, 5, 300, 500, 400, screen))
    }

    @Test
    fun relativeGravityFollowsLayoutDirection() {
        assertEquals(Point(0, 80), ConsoleWindowGeometry.resolve(start, true, 300, 0, 500, 400, screen))
        assertEquals(Point(580, 80), ConsoleWindowGeometry.resolve(start, false, 300, 0, 500, 400, screen))
        assertEquals(Point(580, 80), ConsoleWindowGeometry.resolve(end, true, 300, 0, 500, 400, screen))
        assertEquals(Point(0, 80), ConsoleWindowGeometry.resolve(end, false, 300, 0, 500, 400, screen))
    }

    @Test
    fun positionsAreClampedIntoTheScreen() {
        assertEquals(Point(580, 1600), ConsoleWindowGeometry.clampPosition(2000, 3000, 500, 400, screen))
        assertEquals(Point(0, 80), ConsoleWindowGeometry.clampPosition(-50, -50, 500, 400, screen))
        // Oversized windows stick to the top-left corner.
        // zh-CN: 超出屏幕的窗口贴靠左上角.
        assertEquals(Point(0, 80), ConsoleWindowGeometry.clampPosition(100, 500, 1500, 2500, screen))
    }

    @Test
    fun sizesAreClampedToScreenAndMinimum() {
        assertEquals(Size(300, 200), ConsoleWindowGeometry.clampSize(10, 10, 0, 80, 300, 200, screen))
        assertEquals(Size(980, 1520), ConsoleWindowGeometry.clampSize(5000, 5000, 100, 480, 300, 200, screen))
        // Minimum wins over a tiny screen.
        // zh-CN: 屏幕过小时以最小尺寸为准.
        assertEquals(Size(300, 200), ConsoleWindowGeometry.clampSize(300, 200, 0, 0, 300, 200, Screen(100, 100, 0)))
    }

    @Test
    fun contentYSubtractsTopInset() {
        assertEquals(20, ConsoleWindowGeometry.toContentY(100, screen))
        assertEquals(100, ConsoleWindowGeometry.toContentY(100, Screen(1080, 2000, 0)))
    }

}
