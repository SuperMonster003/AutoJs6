package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.images.ImageRegions
import org.autojs.autojs.runtime.api.modules.images.OptionFields
import org.autojs.autojs.runtime.api.modules.ocr.Ocr.Companion.OcrMode
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

/**
 * The argument rules of `ocr` and its engine modules on the new script module layer (roadmap P3.1), from the documented
 * overloads: a recognition (the module calls, `recognizeText`, `detect`) takes `(options?)`, `(region)`, `(image, options?)`
 * or `(image, region)`, where an image is an ImageWrapper or a path, options are a plain object and a region an array, an
 * OpenCV Rect or an Android Rect; a mode (`tap`, the `mode` setter) is a mode name (case-insensitive; `""` and nullish
 * select automatically) or one of the engine modules. A value of another type is a TypeError, a blank path or an unknown
 * mode name a RangeError (D10); without an image the current screen is captured, as before.
 *
 * The documented fields of the options (appendix C Q28) are checked as well: the region as a region argument
 * ([ImageRegions], within the image when it is given as an ImageWrapper), `mode` as a mode (only for `ocr`; the engine
 * modules ignore it), the strings, booleans and numbers of their types ([OptionFields]); `ocr` checks the fields of all
 * engines, an engine module those its engine reads. The checked arguments are handed on unchanged to the current
 * functions (`augment/ocr/Ocr` and its engine augmentables), which read them as before (to be moved out before P5.2).
 *
 * zh-CN: 新脚本模块层上 ocr 及其引擎模块的参数规则 (P3.1), 依据文档重载: 识别 (模块调用, recognizeText, detect) 接受 (options?), (region), (image, options?)
 * 或 (image, region), 其中图片为 ImageWrapper 或路径, 选项为普通对象, 区域为数组, OpenCV Rect 或 Android Rect; 模式 (tap, mode 的 setter) 为模式名称 (不区分
 * 大小写; "" 与 nullish 表示自动选择) 或某个引擎模块. 其他类型为 TypeError, 空白路径与未知模式名称为 RangeError (D10); 未给出图片时照旧截取当前屏幕.
 * 选项中的文档字段同样检查 (附录 C Q28): region 按区域参数检查 ([ImageRegions], 图片为 ImageWrapper 时须在图片内), mode 按模式检查 (只对 ocr; 引擎模块忽略它),
 * 字符串, 布尔值与数值按其类型检查 ([OptionFields]); ocr 检查全部引擎的字段, 引擎模块只检查其引擎读取的字段. 检查后的参数原样交给现有函数 (augment/ocr/Ocr
 * 及各引擎), 由其照旧读取 (P5.2 前迁出).
 */
internal object OcrArgs {

    /** The default of an omitted parameter (a trailing `undefined` counts as omitted, T4). */
    val ABSENT = Any()

    private const val IMAGE = "ImageWrapper | string"

    private const val OPTIONS_OR_REGION = "Object | Array | Rect"

    private val MODES = OcrMode.entries.joinToString(" | ") { "'${it.value}'" } + " | ''"

    /** The given arguments of a recognition by [engine] (null: the engine `ocr` selects), checked. */
    fun recognition(function: String, runtime: ScriptRuntime, input: Any?, optionsOrRegion: Any?, engine: OcrMode? = null): Array<Any?> {
        val given = when {
            optionsOrRegion !== ABSENT -> arrayOf(input, optionsOrRegion)
            input !== ABSENT -> arrayOf(input)
            else -> return emptyArray()
        }
        val first = given[0]
        when {
            isImage(first) -> {
                val path = plain(first)
                if (path is CharSequence && path.isBlank()) throw GlobalValues.rangeError(function, "input", "image path", path)
                if (given.size > 1) optionsOrRegion(function, runtime, "optionsOrRegion", given[1], first, engine, OPTIONS_OR_REGION)
            }
            given.size > 1 -> throw GlobalValues.typeError(function, "input", IMAGE, first)
            else -> optionsOrRegion(function, runtime, "input", first, null, engine, "$IMAGE | $OPTIONS_OR_REGION")
        }
        return given
    }

    /** A mode for `ocr.tap`, the `ocr.mode` setter or `options.mode` ([param]), checked. */
    fun mode(function: String, runtime: ScriptRuntime, value: Any?, param: String = "mode") {
        val mode = plain(value)
        when {
            ScriptArgs.isNullish(mode) -> Unit
            mode is CharSequence -> if (mode.isNotEmpty() && OcrMode.entries.none { it.value.equals(mode.toString(), ignoreCase = true) }) {
                throw GlobalValues.rangeError(function, param, MODES, value)
            }
            mode === runtime.augmentedOcrMLKit || mode === runtime.augmentedOcrPaddle || mode === runtime.augmentedOcrRapid -> Unit
            else -> throw GlobalValues.typeError(function, param, "string | ocr.mlkit | ocr.paddle | ocr.rapid", value)
        }
    }

    /** Options or a region ([image]: the image argument, when one is given), with the documented fields of the options. */
    private fun optionsOrRegion(function: String, runtime: ScriptRuntime, param: String, value: Any?, image: Any?, engine: OcrMode?, expected: String) {
        when {
            ScriptArgs.isNullish(value) -> Unit
            value is NativeObject -> options(function, runtime, value, image, engine)
            ImageRegions.isRegion(value) -> ImageRegions.check(function, param, value, image)
            else -> throw GlobalValues.typeError(function, param, expected, value)
        }
    }

    private fun options(function: String, runtime: ScriptRuntime, options: NativeObject, image: Any?, engine: OcrMode?) {
        val fields = OptionFields(function, options)
        fields["region"].let { if (!ScriptArgs.isNullish(it)) ImageRegions.check(function, "options.region", it, image) }
        if (engine == null) fields["mode"].let { if (!ScriptArgs.isNullish(it)) mode(function, runtime, it, "options.mode") }
        fields.string("engineId", "engine", "variant", "profile", "imageFormat").boolean("useRaw", "raw").int("imageQuality")
        if (engine != OcrMode.MLKIT) fields.int("detLongSize").number("scoreThreshold")
        if (engine == null || engine == OcrMode.PADDLE) fields.int("cpuThreadNum", "detLimitSideLen").boolean("useSlim", "useOpenCL", "mergeLine", "splitWords", "useWordSegmentation")
        if (engine == null || engine == OcrMode.RAPID) fields.int("maxSideLen").number("boxScoreThresh")
    }

    private fun isImage(value: Any?): Boolean = plain(value).let { it is CharSequence || it is ImageWrapper }

    private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

}
