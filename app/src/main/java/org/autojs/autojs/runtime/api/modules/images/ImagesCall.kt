package org.autojs.autojs.runtime.api.modules.images

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.api.ImageFeatureMatching
import org.autojs.autojs.runtime.api.augment.images.ImageFeatures
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.colors.ColorValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.net.URL
import kotlin.math.roundToInt
import android.graphics.Point as AndroidPoint
import org.opencv.core.Mat as OpencvMat
import org.opencv.core.Point as OpencvPoint
import org.opencv.core.Size as OpencvSize

/**
 * One call of an `images` function on the new script module layer (roadmap P3.1): the given arguments (those before
 * the first omitted one, a trailing `undefined` counting as omitted, T4) and the checks of their documented types and
 * values, made before the current function (`augment/images/Images`) reads them (D10, appendix C Q28). A value of
 * another type is a TypeError, an unusable one (a blank path, NaN, a color text that is no color, an unknown constant
 * name, a pixel or a region beyond an image given as an ImageWrapper) a RangeError, with the messages of
 * [ScriptMessages] (R4). Optional arguments may be nullish (their defaults, as before). Colors are read as `colors`
 * reads them ([ColorValues]), regions with [ImageRegions], constants with [ImageConstant] and the fields of options
 * objects with [ImageOptions]; the current function then reads the checked values again as before (to be moved out
 * before P5.2).
 *
 * zh-CN: 新脚本模块层上 images 函数的一次调用 (P3.1): 给出的参数 (第一个省略的参数之前的参数, 末尾的 undefined 视为省略, T4) 及其文档类型与取值的检查, 在现有
 * 函数 (augment/images/Images) 读取之前进行 (D10, 附录 C Q28). 其他类型为 TypeError, 不可用的取值 (空白路径, NaN, 不是颜色的颜色文本, 未知的常量名, 超出
 * ImageWrapper 图片的像素或区域) 为 RangeError, 消息来自 ScriptMessages (R4). 可选参数可以是 null / undefined (取默认值, 同现有模块). 颜色按 colors 的读法
 * (ColorValues), 区域经 ImageRegions, 常量经 ImageConstant, 选项对象的字段经 ImageOptions 检查; 之后现有函数照旧再次读取这些值 (P5.2 前迁出).
 */
internal class ImagesCall(private val name: String, vararg values: Any?) {

    /** The script name of the function, built once a check asks for it. */
    val function: String get() = builtFunction ?: "images.$name".also { builtFunction = it }

    private var builtFunction: String? = null

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

    /** A number (numeric strings included, as `RhinoUtils.coerceNumber` read them); NaN is a RangeError. */
    fun number(index: Int, param: String) = also { if (index < size) numberOf(args[index], param) }

    fun numberOrNullish(index: Int, param: String) = if (omittedOrNullish(index)) this else number(index, param)

    fun numbersOrNullish(from: Int, vararg params: String) = also { params.forEachIndexed { i, param -> numberOrNullish(from + i, param) } }

    /** A number read as an integer (`coerceIntNumber`, truncated as before): NaN, infinities and values out of the Int range are a RangeError. */
    fun int(index: Int, param: String) = also { if (index < size) intOf(args[index], param) }

    fun intOrNullish(index: Int, param: String) = if (omittedOrNullish(index)) this else int(index, param)

    fun positiveInt(index: Int, param: String) = also {
        if (index < size && intOf(args[index], param) < 1) throw GlobalValues.rangeError(function, param, "positive integer", args[index])
    }

    /** A number alone (`parseThreshold` read other values, numeric strings included, as the default); NaN is a RangeError. */
    fun jsNumberOrNullish(index: Int, param: String) = also {
        if (omittedOrNullish(index)) return@also
        val number = plain(args[index]) as? Number ?: throw GlobalValues.typeError(function, param, "number", args[index])
        if (number.toDouble().isNaN()) throw GlobalValues.rangeError(function, param, "number", args[index])
    }

    fun booleanOrNullish(index: Int, param: String) = check(index, param, "boolean") { isNullish(it) || it is Boolean }

    /** An OmniColor ([ColorValues.toInt]: text that is no color is a RangeError). */
    fun color(index: Int, param: String = "color") = also { if (index < size) ColorValues.toInt(args[index], function, param) }

    /** The name or the value of a constant (an OpenCV threshold or border type, an interpolation, a gravity, a format). */
    fun constant(index: Int, param: String, constant: ImageConstant) = also { if (index < size) constant.check(function, param, args[index]) }

    fun constantOrNullish(index: Int, param: String, constant: ImageConstant) = if (omittedOrNullish(index)) this else constant(index, param, constant)

    fun formatOrNullish(index: Int) = constantOrNullish(index, "format", ImageConstant.IMAGE_FORMAT)

    /** Options: a plain object or nullish; [fields] checks the fields the current function reads. */
    fun options(index: Int, param: String = "options", fields: (NativeObject) -> Unit = {}) = check(index, param, "Object") { isNullish(it) || it is NativeObject }.also {
        (args.getOrNull(index) as? NativeObject)?.let(fields)
    }

    /**
     * An image quality, or (in the forms that quantize a PNG, [withOptions]: `save`, `saveImage`, `compress`,
     * `compressToBytes`) the quantization options, which a format other than PNG at [format] cannot take. In those forms
     * WEBP lossless takes only the quality 100 on Android 11+ (`Images.saveInternal`, after clamping and rounding).
     */
    fun quality(index: Int, format: Int, withOptions: Boolean) = also {
        if (omittedOrNullish(index)) return@also
        val value = args[index]
        val formatName = args.getOrNull(format)?.takeUnless { isNullish(it) }?.let { plain(it).toString().lowercase().trim() }
        if (withOptions && value is NativeObject) {
            if (formatName != null && formatName != "" && formatName != "png") throw GlobalValues.cannotBe(function, "quality", "an options object for a format other than PNG")
            ImageOptions.quantization(function, value, "quality")
            return@also
        }
        val quality = numberOf(value, "quality")
        if (withOptions && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && formatName in WEBP_LOSSLESS && quality.coerceIn(0.0, 100.0).roundToInt() != 100) {
            throw GlobalValues.rangeError(function, "quality", "100 for the WEBP lossless format", value)
        }
    }

    fun array(index: Int, param: String) = check(index, param, "Array") { it is NativeArray }

    /** A kernel size (`parseSize`): a number or an array of one or two numbers (one for both). */
    fun ksize(index: Int) = also {
        if (index >= size) return@also
        when (val value = plain(args[index])) {
            is NativeArray -> {
                if (value.length !in 1L..2L) throw GlobalValues.rangeError(function, "size", "array of one or two numbers", args[index])
                for (i in 0 until value.length.toInt()) numberOf(OptionFields.element(value, i), "size[$i]")
            }
            is Number, is CharSequence -> number(index, "size")
            else -> throw GlobalValues.typeError(function, "size", "number | Array", args[index])
        }
    }

    /** The aperture of `medianBlur`: an integer, an array of two equal numbers or a square OpenCV Size (an odd size is OpenCV's check). */
    fun medianSize(index: Int) = also {
        if (index >= size) return@also
        when (val value = plain(args[index])) {
            is NativeArray -> {
                if (value.length != 2L) throw GlobalValues.rangeError(function, "size", "array of two equal numbers", args[index])
                val (a, b) = (0..1).map { i ->
                    val element = OptionFields.element(value, i)
                    plain(element) as? Number ?: throw GlobalValues.typeError(function, "size[$i]", "number", element)
                }
                if (a.toDouble() != b.toDouble()) throw GlobalValues.rangeError(function, "size", "array of two equal numbers", args[index])
            }
            is OpencvSize -> if (value.width != value.height) throw GlobalValues.rangeError(function, "size", "square Size", args[index])
            is Number, is CharSequence -> int(index, "size")
            else -> throw GlobalValues.typeError(function, "size", "number | Array | Size", args[index])
        }
    }

    /** The size of `resize`: an array of one or two positive integers (the width and the height; one for both, truncated as before). */
    fun resizeSize(index: Int) = array(index, "size").also {
        val value = args.getOrNull(index) as? NativeArray ?: return@also
        if (value.length !in 1L..2L) throw GlobalValues.rangeError(function, "size", "array of one or two numbers", value)
        for (i in 0 until value.length.toInt()) {
            val element = OptionFields.element(value, i)
            val number = numberOf(element, "size[$i]")
            if (!(number >= 1 && number < Int.MAX_VALUE + 1.0)) throw GlobalValues.rangeError(function, "size[$i]", "positive integer", element)
        }
    }

    /**
     * The anchor of `blur` (`S13n.point`): an Android or OpenCV Point, an array `[x, y]`, a number (both) or an object
     * `{ x, y }`, the parts read as integers. An `undefined` anchor is no anchor, as `null` (the current function failed
     * on it, appendix E).
     */
    fun anchor(index: Int) = also {
        if (index >= size) return@also
        val param = "anchor"
        if (isNullish(args[index])) {
            @Suppress("UNCHECKED_CAST")
            (args as Array<Any?>)[index] = null
            return@also
        }
        when (val value = plain(args[index])) {
            is AndroidPoint, is OpencvPoint -> Unit
            is NativeArray -> (0..1).forEach { i -> intOf(OptionFields.element(value, i), "$param[$i]") }
            is Number -> int(index, param)
            is NativeObject -> listOf("x", "y").forEach { name -> intOf(property(value, name), "$param.$name") }
            else -> throw GlobalValues.typeError(function, param, "Point | Array | number | Object", args[index])
        }
    }

    /** A pixel position (integers, truncated as before) within the image when it is an ImageWrapper (`Bitmap.getPixel`). */
    fun pixel(xIndex: Int, yIndex: Int) = also {
        if (yIndex >= size) return@also
        val x = intOf(args[xIndex], "x")
        val y = intOf(args[yIndex], "y")
        val (width, height) = ImageRegions.size(args[0]) ?: return@also
        if (x !in 0 until width) throw GlobalValues.rangeError(function, "x", "integer in 0..${width - 1}", args[xIndex])
        if (y !in 0 until height) throw GlobalValues.rangeError(function, "y", "integer in 0..${height - 1}", args[yIndex])
    }

    /**
     * The paths of the multi-color functions: an array of `[dx, dy, color]` (the offsets read as integers, as before).
     * The current function takes a color text only as a String, so paths with a concatenated text (a ConsString) are
     * passed as a copy with the text as a String.
     */
    fun paths(index: Int) = array(index, "paths").also {
        val paths = args.getOrNull(index) as? NativeArray ?: return@also
        var copy: Array<Any?>? = null
        for (i in 0 until paths.length.toInt()) {
            val param = "paths[$i]"
            val path = OptionFields.element(paths, i)
            if (path !is NativeArray) throw GlobalValues.typeError(function, param, "[dx, dy, color]", path)
            intOf(OptionFields.element(path, 0), "$param[0]")
            intOf(OptionFields.element(path, 1), "$param[1]")
            val color = OptionFields.element(path, 2)
            ColorValues.toInt(color, function, "$param[2]")
            if (color is CharSequence && color !is String) {
                val elements = copy ?: Array(paths.length.toInt()) { OptionFields.element(paths, it) }.also { copy = it }
                elements[i] = arrayLike(path, Array(path.length.toInt()) { OptionFields.element(path, it) }.also { it[2] = color.toString() })
            }
        }
        copy?.let { replace(index, arrayLike(paths, it)) }
    }

    /** A region: an array, an OpenCV Rect or an Android Rect ([nullable]: or nullish, the whole image), within the image at 0 ([bounds]). */
    fun region(index: Int, nullable: Boolean, param: String = "region", bounds: Boolean = true) = check(index, param, if (nullable) "Array | Rect | null" else "Array | Rect") {
        nullable && isNullish(it) || ImageRegions.isRegion(it)
    }.also {
        if (bounds && index < size && !isNullish(args[index])) ImageRegions.check(function, param, args[index], args[0])
    }

    /**
     * The position after the color of a color find function: options alone, or the x of a region followed by y, width,
     * height and (for [withThreshold]) a threshold (an integer). A number alone is ignored by the current function (it is
     * checked as a number).
     */
    fun colorOptionsOrRegion(index: Int, withThreshold: Boolean) = optionsOrRegion(index, positional = false, { ImageOptions.colors(function, it, args[0]) }) {
        if (withThreshold) intOrNullish(index + 4, "threshold")
    }

    /** The position after the template of an image find function: options alone, or the x of a region followed by y, width, height and a threshold (a number alone). */
    fun templateOptionsOrRegion(index: Int) = optionsOrRegion(index, positional = true, { ImageOptions.templates(function, it, args[0], matching = false) }) {
        jsNumberOrNullish(index + 4, "threshold")
    }

    fun mat(index: Int, param: String = "mat") = check(index, param, "Mat") { plain(it) is OpencvMat }

    fun matOrImage(index: Int, param: String) = source(index, param, "ImageWrapper | Mat | string") { it is ImageWrapper || it is OpencvMat }

    fun features(index: Int, param: String) = source(index, param, "ImageFeatures | ImageWrapper | string") { it is ImageFeatures || it is ImageWrapper }

    /**
     * The feature detection method of the options at [index] given as its documented integer (Q29, checked by the
     * options): the current function reads the string of the value, so the options are passed as a view whose own
     * `method` is the name, the other fields read from the options through the prototype chain, as the current
     * function reads them (`inquire`, `hasProp`).
     */
    fun featureMethod(index: Int) = also {
        val options = args.getOrNull(index) as? NativeObject ?: return@also
        val name = when ((plain(ScriptableObject.getProperty(options, "method")) as? Number)?.toInt()) {
            ImageFeatureMatching.FEATURE_MATCHING_METHOD_SIFT -> "SIFT"
            ImageFeatureMatching.FEATURE_MATCHING_METHOD_ORB -> "ORB"
            else -> return@also
        }
        replace(index, optionsView(options, "method", name))
    }

    fun sizeSource(index: Int) = source(index, "src", "ImageWrapper | Mat | Bitmap | string") { it is ImageWrapper || it is OpencvMat || it is Bitmap }

    fun downsampleSource(index: Int) = source(index, "src", "ImageWrapper | Bitmap | byte[] | URL | Uri | string") {
        it is ImageWrapper || it is Bitmap || it is ByteArray || it is URL || it is Uri
    }

    /** Bytes: a Java byte array or an array of integers (truncated to bytes as before). */
    fun bytes(index: Int) = check(index, "bytes", "byte[] | Array") { it is NativeArray || plain(it) is ByteArray }.also {
        val array = args.getOrNull(index) as? NativeArray ?: return@also
        for (i in 0 until array.length.toInt()) intOf(OptionFields.element(array, i), "bytes[$i]")
    }

    /**
     * `clip` takes `(image, region)` or `(image, x, y, width, height)`; the region is not empty and lies within an image
     * given as an ImageWrapper ([ImageRegions.clip] for the five arguments).
     */
    fun clip() = also {
        when (size) {
            2 -> image(0).also { ImageRegions.check(function, "region", args[1], args[0], nonEmpty = true) }
            5 -> image(0).also { ImageRegions.clip(function, args.copyOfRange(1, 5), args[0]) }
            else -> throw UtilValues.typeError(function, ScriptMessages.noOverload(args.joinToString { ScriptArgs.describe(it) }, "(image, region), (image, x, y, width, height)"))
        }
    }

    /**
     * `flip` takes `(image)`, `(image, orientations)` or `(image, horizontal, vertical)`; the orientations are a boolean
     * (horizontal), a name, an array of two booleans or an object of booleans (`x` / `h` / `horizontal`, `y` / `v` /
     * `vertical`), as `parseFlipOrientations` reads them.
     */
    fun flip() = also {
        image(0)
        when (size) {
            2 -> orientations(args[1])
            3 -> booleanOrNullish(1, "horizontal").booleanOrNullish(2, "vertical")
        }
    }

    /** `requestScreenCapture(Async)` takes `()`, `(landscape)`, `(options)` or `(width, height)`. */
    /**
     * The options of the screen capture functions (an orientation given as a concatenated text is passed as a String
     * through a view: the current function ignores any other text) or a width and a height.
     */
    fun screenCapture() = also {
        when (size) {
            1 -> check(0, "options", "boolean | Object") { isNullish(it) || it is Boolean || it is NativeObject }.also {
                val options = args[0] as? NativeObject ?: return@also
                ImageOptions.screenCapture(function, options)
                val orientation = property(options, "orientation")
                if (orientation is CharSequence && orientation !is String) replace(0, optionsView(options, "orientation", orientation.toString()))
            }
            2 -> int(0, "width").int(1, "height")
        }
    }

    private fun orientations(value: Any?) {
        val param = "orientations"
        when (val v = plain(value)) {
            is Boolean -> Unit
            is CharSequence -> ImageConstant.FLIP_ORIENTATION.check(function, param, value)
            is NativeArray -> (0..1).forEach { i -> OptionFields.element(v, i).let { if (!isNullish(it) && it !is Boolean) throw GlobalValues.typeError(function, "$param[$i]", "boolean", it) } }
            is NativeObject -> OptionFields(function, v, param).boolean("x", "h", "horizontal", "y", "v", "vertical")
            else -> if (!isNullish(value)) throw GlobalValues.typeError(function, param, "boolean | string | Array | Object", value)
        }
    }

    /**
     * Options (alone, or nullish) or the x of a region followed by y, width, height and a threshold ([threshold]). A
     * number alone is the x of the region when [positional]; otherwise the current function ignores it.
     */
    private inline fun optionsOrRegion(index: Int, positional: Boolean, options: (NativeObject) -> Unit, threshold: () -> Unit) = also {
        if (index >= size) return@also
        val first = args[index]
        if (size == index + 1) {
            check(index, "options", "Object | number") { isNullish(it) || it is NativeObject || plain(it).let { v -> v is Number || v is CharSequence } }
            when {
                first is NativeObject -> return@also options(first)
                isNullish(first) -> return@also
                !positional -> {
                    numberOf(first, "x")
                    return@also
                }
            }
        }
        ImageRegions.check(function, args.copyOfRange(index, minOf(size, index + 4)), args[0])
        threshold()
    }

    private fun check(index: Int, param: String, expected: String, accepts: (Any?) -> Boolean) = also {
        if (index < size && !accepts(args[index])) throw GlobalValues.typeError(function, param, expected, args[index])
    }

    private fun numberOf(value: Any?, param: String): Double = ScriptArgs.double(value, function, param).also {
        if (it.isNaN()) throw GlobalValues.rangeError(function, param, "number", value)
    }

    private fun intOf(value: Any?, param: String): Int {
        val number = ScriptArgs.double(value, function, param)
        if (!OptionFields.isInt(number)) throw GlobalValues.rangeError(function, param, "integer", value)
        return number.toInt()
    }

    private fun omittedOrNullish(index: Int) = index >= size || isNullish(args[index])

    private fun nonBlank(param: String, value: CharSequence, expected: String) {
        if (value.isBlank()) throw GlobalValues.rangeError(function, param, expected, value)
    }

    /** Passes [value] to the current function in place of the argument at [index]. */
    private fun replace(index: Int, value: Any?) {
        @Suppress("UNCHECKED_CAST")
        (args as Array<Any?>)[index] = value
    }

    companion object {

        /** The default of an omitted parameter. */
        val ABSENT = Any()

        private const val IMAGE = "ImageWrapper | string"

        private val WEBP_LOSSLESS = setOf("webp_lossless", "webp-lossless")

        private fun isNullish(value: Any?) = ScriptArgs.isNullish(value)

        private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

        /** A property of a script object, `undefined` when it is missing. */
        private fun property(obj: NativeObject, name: String): Any? = ScriptableObject.getProperty(obj, name).let { if (it === Scriptable.NOT_FOUND) Undefined.instance else it }

        /**
         * A view of [options] whose own [name] is [value], the other fields read from the options through the prototype
         * chain, as the current functions read them (`inquire`, `hasProp`).
         */
        private fun optionsView(options: NativeObject, name: String, value: Any?) = NativeObject().also { view ->
            view.prototype = options
            view.parentScope = options.parentScope
            view.put(name, view, value)
        }

        /** A new array of [elements] with the prototype and the scope of [array]. */
        private fun arrayLike(array: NativeArray, elements: Array<Any?>) = NativeArray(elements).also {
            it.prototype = array.prototype
            it.parentScope = array.parentScope
        }

    }

}
