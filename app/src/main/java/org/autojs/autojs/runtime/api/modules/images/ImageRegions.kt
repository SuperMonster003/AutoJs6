package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Wrapper
import android.graphics.Rect as AndroidRect
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime
import org.opencv.core.Rect as OpencvRect

/**
 * The checks of a region (OmniRegion: an array `[x, y, width, height]`, an OpenCV Rect or an Android Rect) of `images`
 * and `ocr` on the new script module layer (roadmap P3.1, appendix C Q28), read as `Images.buildRegionInternal` reads it
 * before the current function builds it. A part of an array may be missing or nullish (its default: 0 for x and y,
 * the rest of the image for the width and the height); otherwise it is a number (numeric strings included), truncated
 * toward zero as before. A part of another type is a TypeError (the current function took it as its default), NaN, an
 * infinity or a value out of the Int range a RangeError (it took them as their defaults as well), a negative part other
 * than -1 (the screen width or height) a RangeError; elements after the fourth are not read, as before. When the image
 * is given as an ImageWrapper, a region beyond it is a RangeError; for a path the current function checks it after
 * reading the file. `clip` with five arguments and `__buildRegion` read the parts without screen sizes ([clip],
 * [dimensions]).
 *
 * zh-CN: 新脚本模块层上 images 与 ocr 区域参数 (OmniRegion: 数组 [x, y, width, height], OpenCV Rect 或 Android Rect) 的检查 (P3.1, 附录 C Q28), 按
 * Images.buildRegionInternal 的读法在现有函数构建区域之前进行. 数组的分量可以缺失或为 null / undefined (取默认值: x 与 y 为 0, 宽高为图片的剩余部分), 否则
 * 为数值 (包括数值字符串), 照旧向零截断. 其他类型的分量为 TypeError (现有函数取其默认值), NaN, 无穷与超出 Int 范围的值为 RangeError (现有函数同样取默认值),
 * -1 (屏幕宽或高) 以外的负数为 RangeError; 第四个之后的元素照旧不读取. 图片为 ImageWrapper 时超出图片的区域为 RangeError; 图片为路径时由现有函数在读取文件后
 * 检查. 五个参数的 clip 与 __buildRegion 的分量不含屏幕尺寸 (clip, dimensions).
 */
internal object ImageRegions {

    private val PARTS = arrayOf("x", "y", "width", "height")

    /** The names of the first n parts given as separate arguments, `(x, y)` for two, built once for the error messages. */
    private val SEPARATE = Array(PARTS.size + 1) { n -> PARTS.take(n).joinToString(", ", "(", ")") }

    /** The smallest part of a region where -1 stands for the screen width or height. */
    private const val SCREEN = -1

    fun isRegion(value: Any?): Boolean = value.isJsArray() || plain(value).let { it is OpencvRect || it is AndroidRect }

    /** The width and the height of [image] when it is an ImageWrapper that is not recycled. */
    fun size(image: Any?): Pair<Int, Int>? = (plain(image) as? ImageWrapper)?.takeUnless { it.isRecycled }?.let { it.width to it.height }

    /**
     * A region at [param] of [function] ([image]: the image argument, when one is given; [nonEmpty]: a width or a height
     * of 0 is a RangeError, as `clip` requires).
     */
    fun check(function: String, param: String, region: Any?, image: Any?, nonEmpty: Boolean = false) {
        within(function, param, parts(function, param, region, SCREEN), size(image), screen = true, nonEmpty)
    }

    /**
     * The parts of a region given as separate arguments (x, y, width and height in [values], missing ones omitted),
     * each nullish or a number, read as [check] reads the parts of an array.
     */
    fun check(function: String, values: Array<out Any?>, image: Any?) {
        val parts = Array(PARTS.size) { i -> values.getOrNull(i)?.let { part(function, PARTS[i], it, SCREEN) } }
        within(function, SEPARATE[minOf(values.size, PARTS.size)], parts, size(image), screen = true, nonEmpty = false)
    }

    /**
     * The region of `clip(image, x, y, width, height)` (`Images.clipInternal`): integers (numbers truncated toward zero
     * as before), x and y not negative, the width and the height positive, no screen sizes.
     */
    fun clip(function: String, values: Array<out Any?>, image: Any?) {
        val parts = Array<Int?>(PARTS.size) { i ->
            val value = values[i]
            val number = ScriptArgs.double(value, function, PARTS[i])
            if (!OptionFields.isInt(number)) throw GlobalValues.rangeError(function, PARTS[i], "integer", value)
            number.toInt().also { if (it < (if (i < 2) 0 else 1)) throw GlobalValues.rangeError(function, PARTS[i], if (i < 2) NON_NEGATIVE else POSITIVE, value) }
        }
        within(function, SEPARATE[PARTS.size], parts, size(image), screen = false, nonEmpty = true)
    }

    /**
     * The region of `__buildRegion(region, imageWidth, imageHeight)` (`Images.buildRegionForDimensions`), read only when
     * both dimensions are given (the current function returns null otherwise): the dimensions are non-negative integers,
     * the parts of the region non-negative (no screen sizes) and the region within the dimensions.
     */
    fun dimensions(function: String, region: Any?, imageWidth: Any?, imageHeight: Any?) {
        if (ScriptArgs.isNullish(imageWidth) || ScriptArgs.isNullish(imageHeight)) return
        val size = dimension(function, "imageWidth", imageWidth) to dimension(function, "imageHeight", imageHeight)
        if (ScriptArgs.isNullish(region)) return
        within(function, "region", parts(function, "region", region, 0), size, screen = false, nonEmpty = false)
    }

    private fun dimension(function: String, param: String, value: Any?): Int {
        val number = ScriptArgs.double(value, function, param)
        if (!OptionFields.isInt(number)) throw GlobalValues.rangeError(function, param, "integer", value)
        return number.toInt().also { if (it < 0) throw GlobalValues.rangeError(function, param, NON_NEGATIVE, value) }
    }

    private fun parts(function: String, param: String, region: Any?, minimum: Int): Array<Int?> = when (val plain = plain(region)) {
        is OpencvRect -> rect(function, param, minimum, plain.x, plain.y, plain.width, plain.height)
        is AndroidRect -> rect(function, param, minimum, plain.left, plain.top, plain.width(), plain.height())
        else -> {
            if (!region.isJsArray()) throw GlobalValues.typeError(function, param, "Array | Rect", region)
            Array(PARTS.size) { i -> part(function, "$param[$i]", OptionFields.element(region as Scriptable, i), minimum) }
        }
    }

    private fun rect(function: String, param: String, minimum: Int, vararg values: Int): Array<Int?> =
        Array(PARTS.size) { i -> values[i].also { atLeast(function, "$param.${PARTS[i]}", it, it, minimum) } }

    /** A part (truncated as `coerceIntNumber` truncates), null when it is nullish. */
    private fun part(function: String, param: String, value: Any?, minimum: Int): Int? {
        if (GlobalValues.isNullish(value)) return null
        val number = ScriptArgs.double(value, function, param)
        if (!OptionFields.isInt(number)) throw GlobalValues.rangeError(function, param, "integer", value)
        return number.toInt().also { atLeast(function, param, it, value, minimum) }
    }

    private fun atLeast(function: String, param: String, part: Int, value: Any?, minimum: Int) {
        if (part < minimum) throw GlobalValues.rangeError(function, param, if (minimum == SCREEN) "non-negative integer or -1" else NON_NEGATIVE, value)
    }

    /**
     * The bounds of `Images.buildRegionInternal` (with [screen], -1 is the screen width or height), when the [size] of
     * the image is known; with [nonEmpty] a width or a height of 0 fails as well.
     */
    private fun within(function: String, param: String, parts: Array<Int?>, size: Pair<Int, Int>?, screen: Boolean, nonEmpty: Boolean) {
        if (nonEmpty && (parts[2] == 0 || parts[3] == 0)) {
            throw RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, "non-empty region", "[${parts.joinToString()}]")))
        }
        val (imageWidth, imageHeight) = size ?: return
        val x = screen(parts[0] ?: 0, true, screen)
        val y = screen(parts[1] ?: 0, false, screen)
        val width = screen(parts[2] ?: (imageWidth - x), true, screen)
        val height = screen(parts[3] ?: (imageHeight - y), false, screen)
        val empty = nonEmpty && (width == 0 || height == 0)
        if (width < 0 || height < 0 || empty || x.toLong() + width > imageWidth || y.toLong() + height > imageHeight) {
            val expected = "${if (nonEmpty) "non-empty " else ""}region within the image $imageWidth x $imageHeight"
            throw RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, expected, "[$x, $y, $width, $height]")))
        }
    }

    private fun screen(part: Int, horizontal: Boolean, screen: Boolean): Int = when {
        screen && part == SCREEN -> if (horizontal) ScreenMetrics.deviceScreenWidth else ScreenMetrics.deviceScreenHeight
        else -> part
    }

    private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private const val NON_NEGATIVE = "non-negative integer"

    private const val POSITIVE = "positive integer"

}
