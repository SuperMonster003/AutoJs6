package org.autojs.autojs.runtime.api.modules.images

import android.view.Gravity
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.Wrapper
import org.opencv.core.Core
import org.opencv.features2d.DescriptorMatcher
import org.opencv.imgproc.Imgproc
import java.lang.reflect.Modifier
import kotlin.math.roundToInt

/**
 * A constant argument or option field of `images` on the new script module layer (roadmap P3.1, appendix C Q28): a
 * name, compared as the current function normalizes it ([normalize]), or (with [numbers]) a number the current function
 * accepts. Text that is no such name and an unaccepted number are RangeErrors; a number where only names are read and a
 * value of another type are TypeErrors (the current functions failed on them with other errors, or read the string of
 * the value). The names are listed on the first check; for OpenCV constants they are the names of the public static int
 * fields of the class (`Class.getFields` reads no value, so the class is not initialized and needs no native library).
 *
 * zh-CN: 新脚本模块层上 images 的常量参数或选项字段 (P3.1, 附录 C Q28): 按现有函数的规范化方式 (normalize) 比较的名称, 或 (有 numbers 时) 现有函数接受的数值.
 * 不是这些名称的文本与不接受的数值为 RangeError; 只读名称处给出数值以及其他类型为 TypeError (现有函数对其以其他错误失败, 或读取其字符串). 名称在第一次检查时
 * 列出; OpenCV 常量取类中 public static int 字段的名称 (Class.getFields 不读取值, 因此不初始化该类, 也不需要原生库).
 */
internal class ImageConstant(
    private val expected: String,
    private val normalize: (String) -> String,
    private val numbers: ((Double) -> Boolean)? = null,
    names: () -> Collection<String>,
) {

    private val names by lazy { names().toHashSet() }

    fun check(function: String, param: String, value: Any?) {
        when (val v = if (value is Wrapper) value.unwrap() else value) {
            is CharSequence -> if (normalize(v.toString()) !in names) throw GlobalValues.rangeError(function, param, expected, value)
            is Number -> when (val accepts = numbers) {
                null -> throw GlobalValues.typeError(function, param, "string", value)
                else -> if (!accepts(v.toDouble())) throw GlobalValues.rangeError(function, param, expected, value)
            }
            else -> throw GlobalValues.typeError(function, param, if (numbers == null) "string" else "string | number", value)
        }
    }

    companion object {

        /** `Images.parseThresholdType`: a THRESH_ field (the prefix optional, case and "-" ignored) or an integer. */
        val THRESHOLD_TYPE = ImageConstant("THRESH_* name (the prefix optional) | integer", prefixed("THRESH_"), OptionFields::isInt) { fields(Imgproc::class.java, "THRESH_") }

        /** `Images.parseBorderType`: a BORDER_ field of Core (as [THRESHOLD_TYPE]) or an integer. */
        val BORDER_TYPE = ImageConstant("BORDER_* name (the prefix optional) | integer", prefixed("BORDER_"), OptionFields::isInt) { fields(Core::class.java, "BORDER_") }

        /** `OpenCvImageArgumentPolicy.parseInterpolation`: a name (the INTER_ prefix optional) or its value. */
        val INTERPOLATION = ImageConstant(
            "'NEAREST' | 'LINEAR' | 'CUBIC' | 'AREA' | 'LANCZOS4' | 'LINEAR_EXACT' | 'NEAREST_EXACT' | 0..6",
            { it.trim().uppercase().replace('-', '_').removePrefix("INTER_") },
            { it % 1.0 == 0.0 && it in 0.0..6.0 },
        ) { listOf("NEAREST", "LINEAR", "CUBIC", "AREA", "LANCZOS4", "LINEAR_EXACT", "NEAREST_EXACT") }

        /** The adaptive method of `adaptiveThreshold` (an ADAPTIVE_THRESH_ field without the prefix). */
        val ADAPTIVE_METHOD = ImageConstant("'MEAN_C' | 'GAUSSIAN_C'", { "ADAPTIVE_THRESH_" + it.uppercase() }) { fields(Imgproc::class.java, "ADAPTIVE_THRESH_") }

        /** The threshold type of `adaptiveThreshold`: the two types OpenCV accepts there (documented), without the prefix. */
        val ADAPTIVE_TYPE = ImageConstant("'BINARY' | 'BINARY_INV'", { "THRESH_" + it.uppercase() }) { listOf("THRESH_BINARY", "THRESH_BINARY_INV") }

        /** The code of `cvtColor`: a COLOR_ field without the prefix, as written. */
        val CVT_CODE = ImageConstant("COLOR_* name without the prefix", { "COLOR_$it" }) { fields(Imgproc::class.java, "COLOR_") }

        private val DIRECTIONS = mapOf("START" to Gravity.START, "END" to Gravity.END, "TOP" to Gravity.TOP, "BOTTOM" to Gravity.BOTTOM)

        /** The direction of `concat`: a gravity name or value `ApiImages.concat` accepts. */
        val CONCAT_DIRECTION = ImageConstant(
            "'START' | 'END' | 'TOP' | 'BOTTOM' | ${DIRECTIONS.values.joinToString(" | ")}",
            { it.uppercase().replace('-', '_') },
            { OptionFields.isInt(it) && it.toInt() in DIRECTIONS.values },
        ) { DIRECTIONS.keys }

        /** The matcher of `matchFeatures`: a DescriptorMatcher field (case, spaces around and "-" ignored) or its value. */
        val MATCHER = ImageConstant(
            "DescriptorMatcher constant name | 1..6",
            { it.trim().uppercase().replace('-', '_') },
            { OptionFields.isInt(it) && it.toInt() in 1..6 },
        ) { fields(DescriptorMatcher::class.java, "") }

        /**
         * The feature detection method: a name or the documented integer (`FEATURE_MATCHING_METHOD_*`, Q29), which
         * [ImagesCall.featureMethod] passes as its name (the current function reads the string of the value).
         */
        val FEATURE_METHOD = ImageConstant("'SIFT' | 'ORB' | 1 | 2", { it.uppercase() }, { OptionFields.isInt(it) && it.toInt() in 1..2 }) { listOf("SIFT", "ORB") }

        /** The orientation of the screen capture options (numbers rounded as before). */
        val CAPTURE_ORIENTATION = ImageConstant(
            "'none' | 'auto' | 'portrait' | 'landscape' | -1..2",
            { it.lowercase() },
            { !it.isNaN() && it.roundToInt() in -1..2 },
        ) { listOf("none", "auto", "portrait", "landscape") }

        /** The algorithm of `detectColor` (`ColorDetector.get`). */
        val COLOR_ALGORITHM = ImageConstant("'diff' | 'rgb' | 'rgb+' | 'hs' | 'h' | 'equal' | 'oklab'", { it.trim().lowercase() }) {
            listOf("diff", "rgb", "rgb+", "hs", "h", "equal", "oklab")
        }

        /** An image format (`Images.parseImageFormat`; empty text is PNG). */
        val IMAGE_FORMAT = ImageConstant("'png' | 'jpg' | 'jpeg' | 'webp' | 'webp_lossless' | 'webp_lossy'", { it.lowercase().trim() }) {
            listOf("", "png", "jpg", "jpeg", "webp", "webp_lossless", "webp-lossless", "webp_lossy", "webp-lossy")
        }

        /** The metric of `getSimilarity` (the methods of ImageSimilarity it calls; "pnsr" is read as "psnr"). */
        val SIMILARITY_METRIC = ImageConstant("'psnr' | 'ssim' | 'mssim' | 'hist' | 'mse' | 'ncc'", { it.lowercase() }) {
            listOf("psnr", "pnsr", "ssim", "mssim", "hist", "mse", "ncc")
        }

        /** The orientations of `flip(image, orientations)` given as text. */
        val FLIP_ORIENTATION = ImageConstant("'horizontal' | 'vertical' | 'both' | 'h' | 'v' | 'x' | 'y' | 'xy'", { it.lowercase().trim() }) {
            listOf("both", "all", "h|v", "v|h", "xy", "yx", "x|y", "y|x", "h", "horizontal", "x", "v", "vertical", "y")
        }

        /** Prepends [prefix] unless it is there (case ignored), then upper case and "-" as "_", as the current functions do. */
        private fun prefixed(prefix: String): (String) -> String = { s -> (if (s.startsWith(prefix, ignoreCase = true)) s else prefix + s).uppercase().replace('-', '_') }

        private fun fields(type: Class<*>, prefix: String): List<String> = type.fields
            .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType && it.name.startsWith(prefix) }
            .map { it.name }

    }

}
