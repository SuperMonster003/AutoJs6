package org.autojs.autojs.core.virtualscreen

import kotlin.math.min

/**
 * Pure-JVM mapping between a letterboxed preview view and the logical pixels of a virtual display.
 * Kept free of Android types so the geometry can be unit tested on the JVM.
 *
 * zh-CN: 预览控件 (等比留边) 与虚拟屏逻辑像素之间的坐标换算. 不依赖 Android 类型, 便于在 JVM 单测中覆盖.
 *
 * Created for the virtual screen viewer on Nov 8, 2026.
 */
object DisplayCoordinateMapper {

    data class Geometry(
        val scale: Float,
        val offsetX: Float,
        val offsetY: Float,
        val screenWidth: Int,
        val screenHeight: Int,
    ) {
        /** Width actually used by the picture inside the view. */
        val drawnWidth: Float get() = screenWidth * scale

        /** Height actually used by the picture inside the view. */
        val drawnHeight: Float get() = screenHeight * scale
    }

    /** Fits [screenWidth]x[screenHeight] into [viewWidth]x[viewHeight], centered, never cropped. */
    @JvmStatic
    fun fit(viewWidth: Int, viewHeight: Int, screenWidth: Int, screenHeight: Int): Geometry {
        require(viewWidth > 0 && viewHeight > 0 && screenWidth > 0 && screenHeight > 0) {
            "View and screen sizes must be positive: ${viewWidth}x${viewHeight} / ${screenWidth}x${screenHeight}"
        }
        val scale = min(viewWidth.toFloat() / screenWidth, viewHeight.toFloat() / screenHeight)
        return Geometry(
            scale = scale,
            offsetX = (viewWidth - screenWidth * scale) / 2f,
            offsetY = (viewHeight - screenHeight * scale) / 2f,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
    }

    /**
     * Converts a touch point in view coordinates to a logical display pixel, clamped to the
     * display bounds (touches in the letterbox area land on the nearest edge).
     */
    @JvmStatic
    fun toDisplay(geometry: Geometry, viewX: Float, viewY: Float): Point {
        val x = ((viewX - geometry.offsetX) / geometry.scale)
            .coerceIn(0f, (geometry.screenWidth - 1).toFloat())
        val y = ((viewY - geometry.offsetY) / geometry.scale)
            .coerceIn(0f, (geometry.screenHeight - 1).toFloat())
        return Point(x.toInt(), y.toInt())
    }

    /** Whether a touch started outside the picture (letterbox): such gestures are ignored. */
    @JvmStatic
    fun isInside(geometry: Geometry, viewX: Float, viewY: Float): Boolean {
        val x = (viewX - geometry.offsetX) / geometry.scale
        val y = (viewY - geometry.offsetY) / geometry.scale
        return x >= 0f && y >= 0f && x < geometry.screenWidth && y < geometry.screenHeight
    }

    data class Point(val x: Int, val y: Int)

}
