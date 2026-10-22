package org.autojs.autojs.runtime.api.modules.images

import android.graphics.Bitmap
import android.net.Uri
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.runtime.api.augment.images.ImageFeatures
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper
import java.net.URL
import android.graphics.Rect as AndroidRect
import org.opencv.core.Mat as OpencvMat
import org.opencv.core.Rect as OpencvRect
import org.opencv.core.Size as OpencvSize

/**
 * One call of an `images` function on the new script module layer (roadmap P3.1): the given arguments (those before
 * the first omitted one, a trailing `undefined` counting as omitted, T4) and the checks of their documented types,
 * made before the current function (`augment/images/Images`) reads them (D10). A value of another type is a TypeError,
 * an unusable one (a blank path, NaN) a RangeError, with the messages of [ScriptMessages] (R4). Optional arguments may
 * be nullish (their defaults, as before). What the checks leave to the current function (colors, the parts of a region,
 * the names of OpenCV constants, the fields of options objects) is read there as before (to be moved out before P5.2).
 *
 * zh-CN: 新脚本模块层上 images 函数的一次调用 (P3.1): 给出的参数 (第一个省略的参数之前的参数, 末尾的 undefined 视为省略, T4) 及其文档类型的检查, 在现有函数
 * (augment/images/Images) 读取之前进行 (D10). 其他类型为 TypeError, 不可用的取值 (空白路径, NaN) 为 RangeError, 消息来自 ScriptMessages (R4). 可选参数可以是
 * null / undefined (取默认值, 同现有模块). 检查未涉及的部分 (颜色, 区域的各分量, OpenCV 常量名, 选项对象的字段) 照旧由现有函数读取 (P5.2 前迁出).
 */
internal class ImagesCall(private val name: String, vararg values: Any?) {

    /** The script name of the function, built only for an error message. */
    val function get() = "images.$name"

    /**
     * The vararg array is new on every call, so it is taken as it is when nothing is omitted (P3.1 R2: copying it
     * through a list made a successful `images.getSize` about 5 us slower than the current module).
     */
    val args: Array<out Any?> = values.indexOfFirst { it === ABSENT }.let { if (it < 0) values else values.copyOf(it) }

    val size get() = args.size

    /** An image: an ImageWrapper or the path of an image file (read once by the current function). */
    fun image(index: Int, param: String = "image") = source(index, param, IMAGE) { it is ImageWrapper }

    /** An image source of further types ([expected] names them all), or the path of an image file. */
    fun source(index: Int, param: String, expected: String, accepts: (Any?) -> Boolean) = also {
        if (index >= size) return@also
        val value = plain(args[index])
        when {
            value is CharSequence -> nonBlank(param, value, "image path")
            !accepts(value) -> throw GlobalValues.typeError(function, param, expected, args[index])
        }
    }

    /** A path or URL (a string, not blank). */
    fun path(index: Int, param: String = "path") = also {
        if (index >= size) return@also
        val value = plain(args[index]) as? CharSequence ?: throw GlobalValues.typeError(function, param, "string", args[index])
        nonBlank(param, value, param)
    }

    fun pathOrNullish(index: Int, param: String = "path") = if (omittedOrNullish(index)) this else path(index, param)

    fun string(index: Int, param: String) = check(index, param, "string") { plain(it) is CharSequence }

    fun stringOrNullish(index: Int, param: String) = if (omittedOrNullish(index)) this else string(index, param)

    /** A number (numeric strings included, as `RhinoUtils.coerceNumber` read them); NaN is a RangeError. */
    fun number(index: Int, param: String) = also {
        if (index >= size) return@also
        if (ScriptArgs.double(args[index], function, param).isNaN()) throw GlobalValues.rangeError(function, param, "number", args[index])
    }

    fun numberOrNullish(index: Int, param: String) = if (omittedOrNullish(index)) this else number(index, param)

    fun numbersOrNullish(from: Int, vararg params: String) = also { params.forEachIndexed { i, param -> numberOrNullish(from + i, param) } }

    fun booleanOrNullish(index: Int, param: String) = check(index, param, "boolean") { isNullish(it) || it is Boolean }

    /** The name or the value of an OpenCV / Android constant (a threshold or border type, an interpolation, a gravity). */
    fun constantOrNullish(index: Int, param: String) = check(index, param, "string | number") { isNullish(it) || plain(it).let { v -> v is CharSequence || v is Number } }

    fun options(index: Int, param: String = "options") = check(index, param, "Object") { isNullish(it) || it is NativeObject }

    /** An image quality, or (for the PNG forms) the quantization options. */
    fun quality(index: Int, withOptions: Boolean) = if (omittedOrNullish(index) || withOptions && args[index] is NativeObject) this else number(index, "quality")

    fun array(index: Int, param: String) = check(index, param, "Array") { it is NativeArray }

    /** A size: a number or an array of one or two numbers ([withSize]: also an OpenCV Size). */
    fun ksize(index: Int, withSize: Boolean = false) = also {
        if (index >= size) return@also
        val value = plain(args[index])
        when {
            value is NativeArray || withSize && value is OpencvSize -> Unit
            value is Number || value is CharSequence -> number(index, "size")
            else -> throw GlobalValues.typeError(function, "size", if (withSize) "number | Array | Size" else "number | Array", args[index])
        }
    }

    /** A region: an array, an OpenCV Rect or an Android Rect ([nullable]: or nullish, the whole image). */
    fun region(index: Int, nullable: Boolean, param: String = "region") = check(index, param, if (nullable) "Array | Rect | null" else "Array | Rect") {
        nullable && isNullish(it) || isRegion(it)
    }

    /**
     * The position after the images of a find function: options (alone) or the x of a region followed by y, width,
     * height and (for [withThreshold]) a threshold, each nullish or a number.
     */
    fun optionsOrRegion(index: Int, withThreshold: Boolean) = also {
        if (index >= size) return@also
        if (size == index + 1) {
            check(index, "options", "Object | number") { isNullish(it) || it is NativeObject || plain(it).let { v -> v is Number || v is CharSequence } }
            if (args[index] !is NativeObject) numberOrNullish(index, "x")
        } else {
            numbersOrNullish(index, "x", "y", "width", "height")
            if (withThreshold) numberOrNullish(index + 4, "threshold")
        }
    }

    fun mat(index: Int, param: String = "mat") = check(index, param, "Mat") { plain(it) is OpencvMat }

    fun matOrImage(index: Int, param: String) = source(index, param, "ImageWrapper | Mat | string") { it is ImageWrapper || it is OpencvMat }

    fun features(index: Int, param: String) = source(index, param, "ImageFeatures | ImageWrapper | string") { it is ImageFeatures || it is ImageWrapper }

    fun sizeSource(index: Int) = source(index, "src", "ImageWrapper | Mat | Bitmap | string") { it is ImageWrapper || it is OpencvMat || it is Bitmap }

    fun downsampleSource(index: Int) = source(index, "src", "ImageWrapper | Bitmap | byte[] | URL | Uri | string") {
        it is ImageWrapper || it is Bitmap || it is ByteArray || it is URL || it is Uri
    }

    fun bytes(index: Int) = check(index, "bytes", "byte[] | Array") { it is NativeArray || plain(it) is ByteArray }

    /** `clip` takes `(image, region)` or `(image, x, y, width, height)`. */
    fun clip() = also {
        when (size) {
            2 -> image(0).region(1, nullable = false)
            5 -> image(0).number(1, "x").number(2, "y").number(3, "width").number(4, "height")
            else -> throw UtilValues.typeError(function, ScriptMessages.noOverload(args.joinToString { ScriptArgs.describe(it) }, "(image, region), (image, x, y, width, height)"))
        }
    }

    /** `flip` takes `(image)`, `(image, orientations)` or `(image, horizontal, vertical)`. */
    fun flip() = also {
        image(0)
        when (size) {
            2 -> check(1, "orientations", "boolean | string | Array | Object") { isNullish(it) || it is Boolean || plain(it) is CharSequence || it is NativeArray || it is NativeObject }
            3 -> booleanOrNullish(1, "horizontal").booleanOrNullish(2, "vertical")
        }
    }

    /** `requestScreenCapture(Async)` takes `()`, `(landscape)`, `(options)` or `(width, height)`. */
    fun screenCapture() = also {
        when (size) {
            1 -> check(0, "options", "boolean | Object") { isNullish(it) || it is Boolean || it is NativeObject }
            2 -> number(0, "width").number(1, "height")
        }
    }

    private fun check(index: Int, param: String, expected: String, accepts: (Any?) -> Boolean) = also {
        if (index < size && !accepts(args[index])) throw GlobalValues.typeError(function, param, expected, args[index])
    }

    private fun omittedOrNullish(index: Int) = index >= size || isNullish(args[index])

    private fun nonBlank(param: String, value: CharSequence, expected: String) {
        if (value.isBlank()) throw GlobalValues.rangeError(function, param, expected, value)
    }

    companion object {

        /** The default of an omitted parameter. */
        val ABSENT = Any()

        private const val IMAGE = "ImageWrapper | string"

        private fun isNullish(value: Any?) = ScriptArgs.isNullish(value)

        private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

        private fun isRegion(value: Any?): Boolean = value.isJsArray() || plain(value).let { it is OpencvRect || it is AndroidRect }

    }

}
