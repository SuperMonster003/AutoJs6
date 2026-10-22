package org.autojs.autojs.runtime.api.modules.barcode

import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.images.OptionFields
import org.autojs.plugin.mlkit.barcode.api.BarcodeFormats
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

/**
 * The argument rules of `barcode` and `qrcode` on the new script module layer (roadmap P3.1), from their documented
 * overloads: the selection forms (the module call, `detect`, `recognizeText`) take `(options?, isAll?)`, `(isAll)`,
 * `(image, options?, isAll?)` or `(image, isAll)`, the list forms (`detectAll`, `recognizeTexts`) `(options?)` or
 * `(image, options?)`. An image is an ImageWrapper or a path, options are a plain object, `isAll` is a boolean; nullish
 * options or `isAll` stand for the defaults (as in the current modules). A value of another type is a TypeError, a blank
 * path a RangeError (D10); without an image the current screen is captured, as before.
 *
 * The documented fields of the options are checked as well (appendix C Q28): `format` of `barcode` (a format name or a
 * number, or an array of them; an unknown name is a RangeError, a number must be an Int after truncation),
 * `enableAllPotentialBarcodes` (and `enableAllPotentialQrCodes` of `qrcode`) and, for the selection forms, `isAll` as
 * booleans. The checked arguments are handed on unchanged to the current functions (`augment/barcode/Barcode` /
 * `QrCode`), which read them as before (to be moved out before P5.2).
 *
 * zh-CN: 新脚本模块层上 barcode 与 qrcode 的参数规则 (P3.1), 依据文档重载: 选择形态 (模块调用, detect, recognizeText) 接受 (options?, isAll?), (isAll),
 * (image, options?, isAll?) 或 (image, isAll), 列表形态 (detectAll, recognizeTexts) 接受 (options?) 或 (image, options?). 图片为 ImageWrapper 或路径, 选项为普通
 * 对象, isAll 为布尔值; nullish 的选项或 isAll 表示默认值 (同现有模块). 其他类型为 TypeError, 空白路径为 RangeError (D10); 未给出图片时照旧截取当前屏幕.
 * 选项中的文档字段同样检查 (附录 C Q28): barcode 的 format (格式名称或数值, 或其数组; 未知名称为 RangeError, 数值截断后须为 Int), enableAllPotentialBarcodes
 * (qrcode 另有 enableAllPotentialQrCodes) 以及选择形态的 isAll 为布尔值. 检查后的参数原样交给现有函数 (augment/barcode/Barcode / QrCode), 由其照旧读取
 * (P5.2 前迁出).
 */
internal object BarcodeArgs {

    /** The default of an omitted parameter (a trailing `undefined` counts as omitted, T4). */
    val ABSENT = Any()

    private const val IMAGE = "ImageWrapper | string"

    private const val QRCODE = "qrcode"

    private const val FORMAT_PREFIX = "FORMAT_"

    private val NON_WORD = Regex("\\W+")

    private val FORMATS = BarcodeFormats.BY_NAME.keys.joinToString(" | ") { "'$it'" } + " | number"

    /** The given arguments of a selection call, checked. */
    fun selection(function: String, image: Any?, options: Any?, isAll: Any?): Array<Any?> {
        val given = given(image, options, isAll)
        if (given.isEmpty()) return given
        val first = given[0]
        when {
            isImage(first) -> {
                path(function, first)
                if (given.size > 1) {
                    val second = given[1]
                    when {
                        given.size == 2 && second is Boolean -> Unit
                        !isOptions(second) -> throw GlobalValues.typeError(function, "options", if (given.size == 2) "Object | boolean" else "Object", second)
                        else -> fields(function, second, selection = true)
                    }
                }
                if (given.size > 2) isAll(function, given[2])
            }
            isOptions(first) -> {
                if (given.size == 3) throw GlobalValues.typeError(function, "image", IMAGE, first)
                fields(function, first, selection = true)
                if (given.size == 2) isAll(function, given[1])
            }
            first is Boolean -> if (given.size > 1) throw GlobalValues.typeError(function, "image", "$IMAGE | Object", first)
            else -> throw GlobalValues.typeError(function, "image", expected(given.size, "$IMAGE | Object | boolean", "$IMAGE | Object"), first)
        }
        return given
    }

    /** The given arguments of a list call, checked. */
    fun list(function: String, image: Any?, options: Any?): Array<Any?> {
        val given = given(image, options)
        if (given.isEmpty()) return given
        val first = given[0]
        when {
            isImage(first) -> {
                path(function, first)
                if (given.size > 1) {
                    if (!isOptions(given[1])) throw GlobalValues.typeError(function, "options", "Object", given[1])
                    fields(function, given[1], selection = false)
                }
            }
            isOptions(first) -> {
                if (given.size > 1) throw GlobalValues.typeError(function, "image", IMAGE, first)
                fields(function, first, selection = false)
            }
            else -> throw GlobalValues.typeError(function, "image", expected(given.size, "$IMAGE | Object", IMAGE), first)
        }
        return given
    }

    /** The documented fields of the options of `barcode` or `qrcode` (named by the start of [function]). */
    private fun fields(function: String, options: Any?, selection: Boolean) {
        if (options !is NativeObject) return
        val fields = OptionFields(function, options).boolean("enableAllPotentialBarcodes")
        if (selection) fields.boolean("isAll")
        if (function.startsWith(QRCODE)) {
            fields.boolean("enableAllPotentialQrCodes")
            return
        }
        val format = fields["format"]
        if (format is NativeArray) {
            for ((i, element) in format.withIndex()) format(function, "options.format[$i]", element)
        } else if (!ScriptArgs.isNullish(format)) {
            format(function, "options.format", format)
        }
    }

    /** A format name (as `Barcode` normalizes it) or a number read as an Int (`coerceIntNumber`). */
    private fun format(function: String, param: String, value: Any?) {
        when (val format = plain(value)) {
            is CharSequence -> if (formatName(format.toString()) !in BarcodeFormats.BY_NAME) throw GlobalValues.rangeError(function, param, FORMATS, value)
            is Number -> if (!OptionFields.isInt(format.toDouble())) throw GlobalValues.rangeError(function, param, "integer", value)
            else -> throw GlobalValues.typeError(function, param, "string | number", value)
        }
    }

    private fun formatName(name: String): String {
        val normalized = name.uppercase().replace(NON_WORD, "_").removePrefix(FORMAT_PREFIX)
        return if (normalized == "QRCODE") "QR_CODE" else normalized
    }

    private fun given(vararg args: Any?): Array<Any?> = args.takeWhile { it !== ABSENT }.toTypedArray()

    private fun expected(count: Int, alone: String, withOthers: String) = if (count == 1) alone else withOthers

    private fun isImage(value: Any?): Boolean = plain(value).let { it is CharSequence || it is ImageWrapper }

    private fun isOptions(value: Any?): Boolean = ScriptArgs.isNullish(value) || value is NativeObject

    private fun path(function: String, value: Any?) {
        val path = plain(value)
        if (path is CharSequence && path.isBlank()) throw GlobalValues.rangeError(function, "image", "image path", path)
    }

    private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private fun isAll(function: String, value: Any?) {
        if (!ScriptArgs.isNullish(value) && value !is Boolean) throw GlobalValues.typeError(function, "isAll", "boolean", value)
    }

}
