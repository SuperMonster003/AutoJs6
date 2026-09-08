package org.autojs.autojs.core.console

import android.view.Gravity

/**
 * Pure placement and sizing math for the floating console window. Android-free (only
 * `android.view.Gravity` compile-time constants are used) so the policy is unit-testable.
 *
 * Script-facing positions are "content" coordinates: `y` is measured from the bottom edge of the
 * area the window should avoid (the status bar when `avoidStatusBar` is on), so `setPosition(0, 0)`
 * lands the window flush with the usable top-left corner. Window coordinates returned by
 * [resolve] and [clampPosition] are absolute screen coordinates.
 *
 * zh-CN: 控制台浮动窗口的位置与尺寸纯数学计算. 不依赖 Android (仅使用 `android.view.Gravity` 的编译期常量), 便于单元测试.
 * 脚本侧的位置为 "内容" 坐标: `y` 自需要避开的区域 (开启 avoidStatusBar 时为状态栏) 的下边缘起算, 因此 `setPosition(0, 0)` 会让窗口
 * 与可用区域的左上角对齐. [resolve] 与 [clampPosition] 返回的窗口坐标为屏幕绝对坐标.
 *
 * Created by SuperMonster003 on Sep 8, 2026.
 */
object ConsoleWindowGeometry {

    /** @property topInset the height of the area to avoid at the top of the screen (0 when nothing is avoided). */
    class Screen(val width: Int, val height: Int, val topInset: Int)

    class Point(val x: Int, val y: Int) {
        override fun toString() = "($x, $y)"
        override fun equals(other: Any?) = other is Point && other.x == x && other.y == y
        override fun hashCode() = x * 31 + y
    }

    class Size(val width: Int, val height: Int) {
        override fun toString() = "${width}x$height"
        override fun equals(other: Any?) = other is Size && other.width == width && other.height == height
        override fun hashCode() = width * 31 + height
    }

    /**
     * Resolves the absolute window position of a `width x height` window from [gravity] and the
     * optional content position ([x], [y]); axes not covered by the gravity fall back to the
     * position (or 0). The result is clamped into the screen.
     *
     * zh-CN: 根据 [gravity] 与可选的内容坐标 ([x], [y]) 计算 `width x height` 窗口的绝对位置; gravity 未覆盖的轴回退到该坐标 (或 0). 结果被限制在屏幕内.
     */
    @JvmStatic
    fun resolve(gravity: Int, isLtr: Boolean, x: Int?, y: Int?, width: Int, height: Int, screen: Screen): Point {
        val horizontal = resolveHorizontalGravity(gravity, isLtr)
        val vertical = gravity and Gravity.VERTICAL_GRAVITY_MASK
        val resolvedX = when (horizontal) {
            Gravity.LEFT -> 0
            Gravity.RIGHT -> screen.width - width
            Gravity.CENTER_HORIZONTAL -> (screen.width - width) / 2
            else -> x ?: 0
        }
        val resolvedY = when (vertical) {
            Gravity.TOP -> screen.topInset
            Gravity.BOTTOM -> screen.height - height
            Gravity.CENTER_VERTICAL -> screen.topInset + (screen.height - screen.topInset - height) / 2
            else -> screen.topInset + (y ?: 0)
        }
        return clampPosition(resolvedX, resolvedY, width, height, screen)
    }

    /** Keeps a `width x height` window at absolute ([x], [y]) inside the screen. zh-CN: 将位于绝对坐标 ([x], [y]) 的 `width x height` 窗口限制在屏幕内. */
    @JvmStatic
    fun clampPosition(x: Int, y: Int, width: Int, height: Int, screen: Screen): Point {
        val maxX = maxOf(0, screen.width - width)
        val maxY = maxOf(screen.topInset, screen.height - height)
        return Point(x.coerceIn(0, maxX), y.coerceIn(screen.topInset, maxY))
    }

    /**
     * Clamps a requested window size so that the window anchored at absolute ([x], [y]) stays on
     * screen and never shrinks below the minimum. When the screen is smaller than the minimum the
     * minimum wins.
     *
     * zh-CN: 限制请求的窗口尺寸, 使锚定在绝对坐标 ([x], [y]) 的窗口不超出屏幕且不小于最小尺寸. 屏幕小于最小尺寸时以最小尺寸为准.
     */
    @JvmStatic
    fun clampSize(width: Int, height: Int, x: Int, y: Int, minWidth: Int, minHeight: Int, screen: Screen): Size {
        val maxWidth = maxOf(minWidth, screen.width - maxOf(0, x))
        val maxHeight = maxOf(minHeight, screen.height - maxOf(screen.topInset, y))
        return Size(width.coerceIn(minWidth, maxWidth), height.coerceIn(minHeight, maxHeight))
    }

    /** Converts an absolute window `y` into the content `y` reported to scripts. zh-CN: 将窗口绝对 `y` 转换为脚本可见的内容 `y`. */
    @JvmStatic
    fun toContentY(windowY: Int, screen: Screen): Int = windowY - screen.topInset

    private fun resolveHorizontalGravity(gravity: Int, isLtr: Boolean): Int {
        val horizontal = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        val isRelative = gravity and Gravity.RELATIVE_LAYOUT_DIRECTION != 0
        return when {
            !isRelative -> horizontal
            horizontal == Gravity.LEFT -> if (isLtr) Gravity.LEFT else Gravity.RIGHT
            horizontal == Gravity.RIGHT -> if (isLtr) Gravity.RIGHT else Gravity.LEFT
            else -> horizontal
        }
    }

}
