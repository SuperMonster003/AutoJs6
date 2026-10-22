package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

/**
 * The documented fields of the options objects of `images` on the new script module layer (roadmap P3.1, appendix C
 * Q28), checked with [OptionFields] before the current function reads them, in the order and with the readers of the
 * current function (`augment/images/Images`): only the fields the function reads are checked, so a field another
 * function documents is still ignored. [image] is the image argument a region option is built on.
 *
 * zh-CN: 新脚本模块层上 images 选项对象的文档字段 (P3.1, 附录 C Q28), 在现有函数 (augment/images/Images) 读取之前用 OptionFields 按其顺序与读法检查: 只检查
 * 该函数读取的字段, 其他函数文档中的字段照旧忽略. image 为区域选项所依据的图片参数.
 */
internal object ImageOptions {

    /** The color find functions (`parseThreshold`: `threshold` is read only when it is a number, `similarity` as any number). */
    fun colors(function: String, options: NativeObject, image: Any?) {
        OptionFields(function, options).region("region", image).exclusive("threshold", "similarity").jsNumber("threshold").number("similarity")
    }

    /** `findCircles` (HoughCircles parameters, read with `parseNumber` / `parseIntNumber`). */
    fun circles(function: String, options: NativeObject, image: Any?) {
        OptionFields(function, options).region("region", image).number("dp", "minDst", "param1", "param2").int("minRadius", "maxRadius")
    }

    /**
     * The template functions (`findImage`, `matchTemplate` with [matching]): the similarity as for colors, the scales
     * of `parseScalesInternal` (`scale` is read only when `scales` is nullish).
     */
    fun templates(function: String, options: NativeObject, image: Any?, matching: Boolean) {
        val fields = OptionFields(function, options).region("region", image).number("weakThreshold")
            .exclusive("threshold", "similarity").jsNumber("threshold").number("similarity").int("level")
        if (GlobalValues.isNullish(fields["scales"])) fields.positives("scale") else fields.positives("scales")
        if (matching) {
            fields.int("max")
            fields.present("useTransparentMask", "transparentMask")?.let { fields.boolean(it) }
        }
    }

    /** `detectAndComputeFeatures`: the region and the detection fields. */
    fun features(function: String, options: NativeObject, image: Any?) {
        detection(OptionFields(function, options).region("region", image))
    }

    /**
     * `matchFeatures`: the matching fields, and the detection fields when the current function detects the features of
     * an image ([scene] or [object] given as an ImageWrapper or a path; it forwards those fields alone).
     */
    fun matching(function: String, options: NativeObject, scene: Any?, `object`: Any?) {
        val fields = OptionFields(function, options).constant("matcher", ImageConstant.MATCHER)
            .number("threshold", "number in (0, 1]") { it.toFloat() > 0f && it.toFloat() <= 1f }
            .number("ransacThreshold", "positive finite number") { it > 0 && it.isFinite() }
            .int("minInliers").string("drawMatches")
        if (listOf(scene, `object`).any { plain(it).let { v -> v is ImageWrapper || v is CharSequence } }) detection(fields)
    }

    /** The fields of `fillDetectAndComputeFeaturesOptions` (a scale not above 0 is automatic; a method number is passed as its name). */
    private fun detection(fields: OptionFields) {
        fields.number("scale", "number not greater than 8") { it.toFloat() <= 8f }.constant("method", ImageConstant.FEATURE_METHOD)
            .int("maxFeatures", "non-negative integer") { it >= 0 }.boolean("grayscale")
    }

    /**
     * The quantization options of `parseImageQuantizationOptions` (the native library clamps the integers to their
     * documented ranges, as before; a minimum quality above the maximum fails there).
     */
    fun quantization(function: String, options: NativeObject, param: String) {
        val fields = OptionFields(function, options, param).number("quality").int("maxColors", "speed", "minQuality", "maxQuality", "posterizeBits")
            .number("ditheringLevel", "finite number") { it.isFinite() }.boolean("preserveAlpha")
            .number("maxPixels", "positive integer", ::isPositiveLong).number("maxMemoryBytes", "positive integer", ::isPositiveLong)
        val min = fields["minQuality"].takeUnless { GlobalValues.isNullish(it) } ?: return
        val max = listOf(fields["maxQuality"], fields["quality"]).firstOrNull { !GlobalValues.isNullish(it) } ?: return
        if (quality(min, function) > quality(max, function)) throw GlobalValues.rangeError(function, "$param.minQuality", "integer not greater than the maximum quality", min)
    }

    /** `getSimilarity`: the metric (`metric`, or `type` when the options have no `metric`; the one read cannot be nullish). */
    fun similarity(function: String, options: NativeObject) {
        val fields = OptionFields(function, options)
        val name = fields.present("metric", "type") ?: return
        ImageConstant.SIMILARITY_METRIC.check(function, "options.$name", fields[name])
    }

    /** The screen capture options of `requestScreenCapture(Async)` (`isAsync`, or `async` when the options have no `isAsync`). */
    fun screenCapture(function: String, options: NativeObject) {
        val fields = OptionFields(function, options).int("width", "height").constant("orientation", ImageConstant.CAPTURE_ORIENTATION)
        fields.present("isAsync", "async")?.let { fields.boolean(it) }
    }

    private fun isPositiveLong(number: Double) = number >= 1 && number < 9.223372036854776E18

    /** A quality as the native library reads it (clamped to 0..100); the value was checked as a number. */
    private fun quality(value: Any?, function: String): Double = ScriptArgs.double(value, function, "quality").coerceIn(0.0, 100.0)

    private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

}
