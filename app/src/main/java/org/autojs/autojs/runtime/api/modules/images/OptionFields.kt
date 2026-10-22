package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper

/**
 * The checks of the documented fields of an options object of `images`, `ocr`, `barcode` and `qrcode` on the new script
 * module layer (roadmap P3.1, appendix C Q28), made before the current function reads them (`inquire` with the
 * `RhinoUtils.coerce*` helpers). A missing or nullish field is the default, as before. A field of another type is a
 * TypeError (the current functions converted any value: `coerceBoolean` took the truthiness, `coerceString` the string of
 * it), an unusable number (NaN, an integer out of range, a value out of the documented range) a RangeError (D10). Numbers
 * include numeric strings and are truncated by the current functions where they read integers, as before (appendix E);
 * [jsNumber] fields take numbers alone, as `parseThreshold` read them.
 *
 * zh-CN: 新脚本模块层上 images, ocr, barcode 与 qrcode 选项对象中文档字段的检查 (P3.1, 附录 C Q28), 在现有函数读取 (inquire 与 RhinoUtils.coerce*)
 * 之前进行. 缺失或 nullish 的字段取默认值 (同现有模块). 其他类型的字段为 TypeError (现有函数会转换任何值: coerceBoolean 取真值性, coerceString 取其字符串),
 * 不可用的数值 (NaN, 超出范围的整数, 超出文档范围的值) 为 RangeError (D10). 数值包括数值字符串, 读取整数处照旧由现有函数截断 (附录 E); jsNumber 字段只接受
 * 数值, 同 parseThreshold 的读法.
 */
internal class OptionFields(private val function: String, private val options: Scriptable, private val param: String = "options") {

    /** The value of a field, null when it is missing (the getters of the field run here and again in the current function). */
    operator fun get(name: String): Any? = ScriptableObject.getProperty(options, name).takeUnless { it === Scriptable.NOT_FOUND }

    /** The first of [names] the options have (`hasProp`: the current function reads that one alone, even when it is nullish). */
    fun present(vararg names: String): String? = names.firstOrNull { ScriptableObject.hasProperty(options, it) }

    fun boolean(vararg names: String) = each(names) { name, value -> if (plain(value) !is Boolean) throw typeError(name, "boolean", value) }

    fun string(vararg names: String) = each(names) { name, value -> if (plain(value) !is CharSequence) throw typeError(name, "string", value) }

    /** Numbers: NaN is a RangeError. */
    fun number(vararg names: String) = each(names) { name, value -> number(name, value) }

    /** A number that [accepts] ([expected] names the range); others are a RangeError. */
    fun number(name: String, expected: String, accepts: (Double) -> Boolean) = each(arrayOf(name)) { _, value ->
        if (!accepts(number(name, value))) throw GlobalValues.rangeError(function, field(name), expected, value)
    }

    /** Numbers alone (the current function read other values as the default, numeric strings included); NaN is a RangeError. */
    fun jsNumber(vararg names: String) = each(names) { name, value ->
        val number = plain(value) as? Number ?: throw typeError(name, "number", value)
        if (number.toDouble().isNaN()) throw GlobalValues.rangeError(function, field(name), "number", value)
    }

    /** Numbers read as integers (`coerceIntNumber`): NaN, infinities and values out of the Int range are a RangeError. */
    fun int(vararg names: String) = each(names) { name, value -> int(name, value) }

    /** An integer that [accepts] ([expected] names the range); others are a RangeError. */
    fun int(name: String, expected: String, accepts: (Int) -> Boolean) = each(arrayOf(name)) { _, value ->
        if (!accepts(int(name, value))) throw GlobalValues.rangeError(function, field(name), expected, value)
    }

    /** A field of other documented types ([expected] names them). */
    fun check(name: String, expected: String, accepts: (Any?) -> Boolean) = each(arrayOf(name)) { _, value -> if (!accepts(value)) throw typeError(name, expected, value) }

    /** The name of one of [names] (compared as [normalize] gives it) or, with [numbers], a number; an unknown name is a RangeError. */
    fun named(name: String, expected: String, names: Collection<String>, numbers: Boolean = false, normalize: (String) -> String = { it }) = each(arrayOf(name)) { _, value ->
        when (val v = plain(value)) {
            is CharSequence -> if (normalize(v.toString()) !in names) throw GlobalValues.rangeError(function, field(name), expected, value)
            is Number -> if (numbers) number(name, value) else throw typeError(name, "string", value)
            else -> throw typeError(name, if (numbers) "string | number" else "string", value)
        }
    }

    /** The name or the value of a constant ([ImageConstant]). */
    fun constant(name: String, constant: ImageConstant) = each(arrayOf(name)) { _, value -> constant.check(function, field(name), value) }

    /** A region ([ImageRegions.check]; [image]: the image the current function builds it on). */
    fun region(name: String, image: Any?) = each(arrayOf(name)) { _, value -> ImageRegions.check(function, field(name), value, image) }

    /** Two fields that cannot be given together (both not nullish; the current function failed with another error). */
    fun exclusive(first: String, second: String) = also {
        if (!GlobalValues.isNullish(get(first)) && !GlobalValues.isNullish(get(second))) throw GlobalValues.cannotBe(function, field(second), "given with ${field(first)}")
    }

    /**
     * Positive finite numbers (`parseScalesInternal`): a number or a non-empty array of them; numeric strings are read as
     * numbers, as before.
     */
    fun positives(name: String) = each(arrayOf(name)) { _, value ->
        val items = when (val v = plain(value)) {
            is Iterable<*> -> v.toList().also { if (it.isEmpty()) throw GlobalValues.rangeError(function, field(name), "non-empty array", value) }
            is Number, is CharSequence -> listOf(v)
            else -> throw typeError(name, "number | Array", value)
        }
        items.forEachIndexed { i, item ->
            val param = if (items.size == 1 && item === plain(value)) field(name) else "${field(name)}[$i]"
            val number = ScriptArgs.double(item ?: Undefined.instance, function, param)
            if (!number.isFinite() || number <= 0) throw GlobalValues.rangeError(function, param, "positive finite number", item)
        }
    }

    private fun number(name: String, value: Any?): Double {
        val number = ScriptArgs.double(value, function, field(name))
        if (number.isNaN()) throw GlobalValues.rangeError(function, field(name), "number", value)
        return number
    }

    private fun int(name: String, value: Any?): Int {
        val number = number(name, value)
        if (!isInt(number)) throw GlobalValues.rangeError(function, field(name), "integer", value)
        return number.toInt()
    }

    private inline fun each(names: Array<out String>, check: (String, Any?) -> Unit) = also {
        for (name in names) get(name).let { if (!GlobalValues.isNullish(it)) check(name, it) }
    }

    private fun field(name: String) = "$param.$name"

    private fun typeError(name: String, expected: String, value: Any?) = GlobalValues.typeError(function, field(name), expected, value)

    private fun plain(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    companion object {

        /** Whether `coerceIntNumber` reads [number] (finite, in the Int range after truncation toward zero). */
        fun isInt(number: Double) = !number.isNaN() && !number.isInfinite() && number > Int.MIN_VALUE - 1.0 && number < Int.MAX_VALUE + 1.0

        /** The element [index] of a script array, `undefined` for a hole or an index beyond its length. */
        fun element(array: Scriptable, index: Int): Any? = array.get(index, array).let { if (it === Scriptable.NOT_FOUND) Undefined.instance else it }

    }

}
