package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.runtime.api.ScreenMetrics
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The screen size the screen-relative selector arguments are resolved against: a value in `(-1, 1)`
 * is a fraction of the screen side, `-1` is the whole side, anything else is a pixel value and never
 * touches the screen size. The sides are read lazily, so a device-backed scale only queries the
 * display for arguments that need it. Filters take a [Source] so that the device is consulted on
 * every query (rotation) while unit tests inject a fixed size.
 *
 * zh-CN: 屏幕相关选择器参数所依据的屏幕尺寸: `(-1, 1)` 内的值为屏幕边长的比例, `-1` 为整条边, 其余为像素值且不会触及屏幕
 * 尺寸. 边长惰性读取, 因此设备尺寸只在需要它的参数上才查询显示器. 过滤器持有 [Source], 使每次查询都以设备为准 (旋转),
 * 而单元测试可注入固定尺寸.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class ScreenScale(private val widthSupplier: () -> Int, private val heightSupplier: () -> Int) {

    constructor(width: Int, height: Int) : this({ width }, { height })

    enum class Axis { X, Y }

    val width: Int get() = widthSupplier()

    val height: Int get() = heightSupplier()

    fun side(axis: Axis): Int = when (axis) {
        Axis.X -> width
        Axis.Y -> height
    }

    fun round(axis: Axis, value: Double): Int = resolve(value, axis) { it.roundToInt() }

    fun floor(axis: Axis, value: Double): Int = resolve(value, axis) { floor(it).toInt() }

    fun ceil(axis: Axis, value: Double): Int = resolve(value, axis) { ceil(it).toInt() }

    private inline fun resolve(value: Double, axis: Axis, rounding: (Double) -> Int): Int = when {
        value == -1.0 -> side(axis)
        value > -1.0 && value < 1.0 -> rounding(value * side(axis))
        else -> rounding(value)
    }

    override fun toString() = "ScreenScale($width x $height)"

    /** Where a filter gets the screen scale at evaluation time. zh-CN: 过滤器在求值时从何处取得屏幕尺寸. */
    fun interface Source {

        fun scale(): ScreenScale

        companion object {

            private val device = ScreenScale({ ScreenMetrics.deviceScreenWidth }, { ScreenMetrics.deviceScreenHeight })

            /**
             * The device, whose sides are read on demand so that a rotation between two queries is
             * honoured. Replaceable for JVM tests; production code never reassigns it.
             * zh-CN: 设备尺寸, 边长按需读取以反映两次查询之间的旋转. JVM 测试可替换; 生产代码从不重新赋值.
             */
            @JvmStatic
            @Volatile
            var default: Source = Source { device }

            @JvmStatic
            fun fixed(width: Int, height: Int): Source = ScreenScale(width, height).let { Source { it } }

        }

    }

}
