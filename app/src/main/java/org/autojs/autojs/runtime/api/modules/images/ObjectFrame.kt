package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.util.StringUtils
import org.opencv.core.Point
import org.opencv.core.Rect
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

// @Reference to module __images__.js from Auto.js Pro 9.3.11 by SuperMonster003 on Dec 19, 2023.
/**
 * Quadrilateral of an object located by `images.matchFeatures()`.
 * The four corners correspond to the corners of the object image itself, so a rotated or
 * perspective-distorted object yields a rotated frame; see [angle] and [bounds].
 * zh-CN: `images.matchFeatures()` 定位到的目标四边形. 四个角点与目标图片自身的角点一一对应,
 * 因此旋转或透视变形的目标会得到旋转的边框, 参阅 [angle] 与 [bounds].
 *
 * Created by SuperMonster003 on Aug 9, 2024.
 * Modified by SuperMonster003 as of Sep 15, 2026.
 */
class ObjectFrame @JvmOverloads constructor(
    @JvmField val topLeft: Point,
    @JvmField val topRight: Point,
    @JvmField val bottomLeft: Point,
    @JvmField val bottomRight: Point,
    /** Matches that passed the ratio test. zh-CN: 通过比例测试的匹配数. */
    @JvmField val matches: Int = 0,
    /** RANSAC inliers supporting this frame. zh-CN: 支撑该边框的 RANSAC 内点数. */
    @JvmField val inliers: Int = 0,
    inlierPoints: List<Point> = emptyList(),
) {
    @JvmField
    val centerX = (topLeft.x + topRight.x + bottomLeft.x + bottomRight.x) / 4

    @JvmField
    val centerY = (topLeft.y + topRight.y + bottomLeft.y + bottomRight.y) / 4

    @JvmField
    val center = Point(centerX, centerY)

    /** Length of the top edge (object width as seen in the scene). zh-CN: 上边长度 (目标在场景中的宽度). */
    @JvmField
    val width = hypot(topRight.x - topLeft.x, topRight.y - topLeft.y)

    /** Length of the left edge (object height as seen in the scene). zh-CN: 左边长度 (目标在场景中的高度). */
    @JvmField
    val height = hypot(bottomLeft.x - topLeft.x, bottomLeft.y - topLeft.y)

    /**
     * Rotation of the top edge in degrees within (-180, 180]; positive values are clockwise on screen.
     * zh-CN: 上边的旋转角度 (度), 取值 (-180, 180], 正值为屏幕上的顺时针方向.
     */
    @JvmField
    val angle = Math.toDegrees(atan2(topRight.y - topLeft.y, topRight.x - topLeft.x))

    /** Axis-aligned bounding rectangle of the four corners. zh-CN: 四角点的轴对齐外接矩形. */
    @JvmField
    val bounds: Rect = run {
        val xs = doubleArrayOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
        val ys = doubleArrayOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
        val left = floor(xs.min()).toInt()
        val top = floor(ys.min()).toInt()
        Rect(left, top, max(0, ceil(xs.max()).toInt() - left), max(0, ceil(ys.max()).toInt() - top))
    }

    /** Inlier points in scene coordinates. zh-CN: 场景坐标系中的内点. */
    @JvmField
    val points = inlierPoints.toNativeArray()

    override fun toString(): String {
        return "[${ObjectFrame::class.java.simpleName}] ${summary()}"
    }

    fun summary(): String = listOf(
        "topLeft" to { topLeft },
        "topRight" to { topRight },
        "bottomLeft" to { bottomLeft },
        "bottomRight" to { bottomRight },
        "center" to { center },
        "width" to { width },
        "height" to { height },
        "angle" to { angle },
        "matches" to { matches },
        "inliers" to { inliers },
    ).let { StringUtils.toFormattedSummary(it) }

    private fun DoubleArray.min() = fold(Double.POSITIVE_INFINITY) { acc, v -> min(acc, v) }

    private fun DoubleArray.max() = fold(Double.NEGATIVE_INFINITY) { acc, v -> max(acc, v) }

}
