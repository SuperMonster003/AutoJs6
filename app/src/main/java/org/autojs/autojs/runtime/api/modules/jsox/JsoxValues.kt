package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.dtoa.DecimalFormatter
import java.math.BigInteger
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Value rules shared by the jsox modules of the new script module layer (roadmap P2.1): JS number conversion, recursive
 * flattening, the JS `parseFloat` / `parseInt` algorithms and `toFixed`, and the errors (D10). Only Rhino and the JDK
 * are used, so the modules run on the plain JVM (the old Kotlin reached Android static initialization through
 * `StringUtils`, and the radix-less `parseFloat` called the global `parseFloat`, which the Number extension replaces).
 *
 * zh-CN: 新脚本模块层 jsox 各模块共用的取值规则 (P2.1): JS 数值转换, 递归展平, JS parseFloat / parseInt 算法与 toFixed, 以及错误 (D10).
 * 只依赖 Rhino 与 JDK, 可在纯 JVM 运行 (旧 Kotlin 经 StringUtils 触及 Android 静态初始化, 不带 radix 的 parseFloat 调用全局 parseFloat,
 * 而 Number 扩展会替换它).
 */
internal object JsoxValues {

    fun isNullish(value: Any?): Boolean = value == null || Undefined.isUndefined(value)

    /** The Java value of a wrapped Java object; script objects stay as they are. */
    fun unwrap(value: Any?): Any? = if (value is Wrapper && value !is NativeArray && value !is NativeObject) value.unwrap() else value

    /** A JavaScript array (an object of class "Array"). */
    fun isJsArray(value: Any?): Boolean = value is Scriptable && value !== Scriptable.NOT_FOUND && value.className == "Array"

    /** JS `ToNumber`, with wrapped Java numbers, strings and booleans unwrapped first. */
    fun toNumber(value: Any?): Double = when (val v = unwrap(value)) {
        is Number -> v.toDouble()
        else -> RhinoScriptRuntime.toNumber(v)
    }

    /** JS `Number::toString` (as `String(1e21)` is "1e+21"). */
    fun numberToString(d: Double): String = RhinoScriptRuntime.numberToString(d, 10)

    /**
     * Every value of [values], with JS arrays (holes skipped, as `Array.prototype.flat`), Java collections and Java arrays
     * flattened recursively and wrapped Java objects unwrapped.
     */
    fun flatten(values: Iterable<Any?>): List<Any?> = ArrayList<Any?>().also { out -> values.forEach { flattenInto(it, out) } }

    fun flatten(values: Array<out Any?>): List<Any?> = flatten(values.asIterable())

    private fun flattenInto(value: Any?, out: MutableList<Any?>) {
        when (val v = unwrap(value)) {
            is NativeArray -> for (i in 0 until v.length) {
                val element = v.get(i.toInt(), v)
                if (element !== Scriptable.NOT_FOUND) flattenInto(element, out)
            }
            is Scriptable -> out += v
            is Iterable<*> -> v.forEach { flattenInto(it, out) }
            is Array<*> -> v.forEach { flattenInto(it, out) }
            else -> out += v
        }
    }

    /**
     * `Number.prototype.toFixed` as a number (`Numberx.toFixedNum`): the digits are `ToIntegerOrInfinity(fraction)` and must
     * lie in [0, 100] (Rhino's RangeError otherwise); a value that is not finite is returned as it is.
     */
    fun toFixed(x: Double, fraction: Any?): Double {
        val digits = RhinoScriptRuntime.toInteger(fraction)
        if (digits < 0.0 || digits > 100.0) {
            throw RhinoScriptRuntime.rangeError(RhinoScriptRuntime.getMessageById("msg.bad.precision", RhinoScriptRuntime.toString(fraction)))
        }
        return if (!x.isFinite()) x else DecimalFormatter.toFixed(x, RhinoScriptRuntime.toInt32(digits)).toDouble()
    }

    /** `StrWhiteSpaceChar` of the JS grammar (as Rhino's `ScriptRuntime.isStrWhiteSpaceChar`). */
    fun isStrWhiteSpace(c: Char): Boolean = when (c) {
        ' ', '\n', '\r', '\t', '\u00A0', '\u000C', '\u000B', '\u2028', '\u2029', '\uFEFF' -> true
        else -> Character.getType(c) == Character.SPACE_SEPARATOR.toInt()
    }

    /** The global `parseFloat` (the algorithm of Rhino's `NativeGlobal.js_parseFloat`). */
    fun parseFloat(s: String): Double {
        val len = s.length
        var start = 0
        while (start < len && isStrWhiteSpace(s[start])) start++
        if (start == len) return Double.NaN
        var i = start
        var c = s[i]
        if (c == '+' || c == '-') {
            if (++i == len) return Double.NaN
            c = s[i]
        }
        if (c == 'I') {
            return if (s.regionMatches(i, "Infinity", 0, 8)) {
                if (s[start] == '-') Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
            } else {
                Double.NaN
            }
        }
        var decimal = -1
        var exponent = -1
        var exponentValid = false
        scan@ while (i < len) {
            when (s[i]) {
                '.' -> {
                    if (decimal != -1) break@scan
                    decimal = i
                }
                'e', 'E' -> {
                    if (exponent != -1 || i == len - 1) break@scan
                    exponent = i
                }
                '+', '-' -> {
                    if (exponent != i - 1) break@scan
                    if (i == len - 1) {
                        i--
                        break@scan
                    }
                }
                in '0'..'9' -> if (exponent != -1) exponentValid = true
                else -> break@scan
            }
            i++
        }
        if (exponent != -1 && !exponentValid) i = exponent
        return s.substring(start, i).toDoubleOrNull() ?: Double.NaN
    }

    /** The global `parseInt(s, radix)` with an integer radix (ECMAScript `parseInt`; radix 0 means 10, or 16 for "0x"). */
    fun parseInt(s: String, radix: Int): Double {
        val len = s.length
        var i = 0
        while (i < len && isStrWhiteSpace(s[i])) i++
        var negative = false
        if (i < len && (s[i] == '+' || s[i] == '-')) {
            negative = s[i] == '-'
            i++
        }
        var r = radix
        var stripPrefix = true
        if (r != 0) {
            if (r < 2 || r > 36) return Double.NaN
            if (r != 16) stripPrefix = false
        } else {
            r = 10
        }
        if (stripPrefix && i + 1 < len && s[i] == '0' && (s[i + 1] == 'x' || s[i + 1] == 'X')) {
            i += 2
            r = 16
        }
        val begin = i
        while (i < len && digitOf(s[i]) in 0 until r) i++
        if (i == begin) return Double.NaN
        val digits = s.substring(begin, i)
        val value = if (r == 10) digits.toDouble() else BigInteger(digits, r).toDouble()
        return if (negative) -value else value
    }

    private fun digitOf(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'z' -> c - 'a' + 10
        in 'A'..'Z' -> c - 'A' + 10
        else -> -1
    }

    // Errors (D10): argument errors are TypeError / RangeError prefixed with the function name.

    fun typeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, cause))

    fun rangeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, cause))

    fun typeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        typeError(function, ScriptMessages.argumentType(param, expected, ScriptArgs.describe(value)))

    fun cannotBe(function: String, param: String, value: Any?): RuntimeException =
        typeError(function, ScriptMessages.argumentCannotBe(param, ScriptArgs.describe(value)))

}
