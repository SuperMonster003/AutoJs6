package org.autojs.autojs.runtime.api.modules.ocr

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.ocr.Ocr.Companion.OcrMode
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper
import android.graphics.Rect as AndroidRect
import org.opencv.core.Rect as OpencvRect

/**
 * The argument rules of `ocr` and its engine modules on the new script module layer (roadmap P3.1), from the documented
 * overloads: a recognition (the module calls, `recognizeText`, `detect`) takes `(options?)`, `(region)`, `(image, options?)`
 * or `(image, region)`, where an image is an ImageWrapper or a path, options are a plain object and a region an array, an
 * OpenCV Rect or an Android Rect; a mode (`tap`, the `mode` setter) is a mode name (case-insensitive; `""` and nullish
 * select automatically) or one of the engine modules. A value of another type is a TypeError, a blank path or an unknown
 * mode name a RangeError (D10); without an image the current screen is captured, as before.
 *
 * The checked arguments are handed on unchanged to the current functions (`augment/ocr/Ocr` and its engine augmentables),
 * which read the image, the region and the options (`mode`, `engine`, `engineId`, `variant`, ...) as before (to be moved out
 * before P5.2).
 *
 * zh-CN: 新脚本模块层上 ocr 及其引擎模块的参数规则 (P3.1), 依据文档重载: 识别 (模块调用, recognizeText, detect) 接受 (options?), (region), (image, options?)
 * 或 (image, region), 其中图片为 ImageWrapper 或路径, 选项为普通对象, 区域为数组, OpenCV Rect 或 Android Rect; 模式 (tap, mode 的 setter) 为模式名称 (不区分
 * 大小写; "" 与 nullish 表示自动选择) 或某个引擎模块. 其他类型为 TypeError, 空白路径与未知模式名称为 RangeError (D10); 未给出图片时照旧截取当前屏幕.
 * 检查后的参数原样交给现有函数 (augment/ocr/Ocr 及各引擎), 由其照旧读取图片, 区域与选项 (mode, engine, engineId, variant 等) (P5.2 前迁出).
 */
internal object OcrArgs {

    /** The default of an omitted parameter (a trailing `undefined` counts as omitted, T4). */
    val ABSENT = Any()

    private const val IMAGE = "ImageWrapper | string"

    private const val OPTIONS_OR_REGION = "Object | Array | Rect"

    private val MODES = OcrMode.entries.joinToString(" | ") { "'${it.value}'" } + " | ''"

    /** The given arguments of a recognition, checked. */
    fun recognition(function: String, input: Any?, optionsOrRegion: Any?): Array<Any?> {
        val given = arrayOf(input, optionsOrRegion).takeWhile { it !== ABSENT }.toTypedArray()
        if (given.isEmpty()) return given
        val first = given[0]
        when {
            isImage(first) -> {
                val path = plain(first)
                if (path is CharSequence && path.isBlank()) throw GlobalValues.rangeError(function, "input", "image path", path)
                if (given.size > 1 && !isOptionsOrRegion(given[1])) throw GlobalValues.typeError(function, "optionsOrRegion", OPTIONS_OR_REGION, given[1])
            }
            given.size > 1 -> throw GlobalValues.typeError(function, "input", IMAGE, first)
            !isOptionsOrRegion(first) -> throw GlobalValues.typeError(function, "input", "$IMAGE | $OPTIONS_OR_REGION", first)
        }
        return given
    }

    /** A mode for `ocr.tap` or the `ocr.mode` setter, checked. */
    fun mode(function: String, runtime: ScriptRuntime, value: Any?) {
        val mode = plain(value)
        when {
            ScriptArgs.isNullish(mode) -> Unit
            mode is CharSequence -> if (mode.isNotEmpty() && OcrMode.entries.none { it.value.equals(mode.toString(), ignoreCase = true) }) {
                throw GlobalValues.rangeError(function, "mode", MODES, value)
            }
            mode === runtime.augmentedOcrMLKit || mode === runtime.augmentedOcrPaddle || mode === runtime.augmentedOcrRapid -> Unit
            else -> throw GlobalValues.typeError(function, "mode", "string | ocr.mlkit | ocr.paddle | ocr.rapid", value)
        }
    }

    private fun isImage(value: Any?): Boolean = plain(value).let { it is CharSequence || it is ImageWrapper }

    private fun isOptionsOrRegion(value: Any?): Boolean =
        ScriptArgs.isNullish(value) || value is NativeObject || value is NativeArray || plain(value).let { it is OpencvRect || it is AndroidRect }

    private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

}
