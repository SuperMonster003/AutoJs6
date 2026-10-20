package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.modules.ScriptBuiltIn
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `Numberx` module on the new script module layer (roadmap P2.1), also the extension of the built-in `Number`
 * (`Number.ICU`, the static parsing and checking functions, the `Number.prototype` methods taking the number as `this`,
 * and the radix-aware global `parseFloat`) when the "extend JavaScript built-in objects" option or `jsox` applies it.
 *
 * Behavior follows the documentation, d.ts and ROADMAP appendix E: numeric strings stay accepted where the current
 * Kotlin module accepted them (`clamp('5', 1, 3)`, `toFixedNum('1.5', 0)`, `check(1, '<', '2')`), `clamp` / `clampTo`
 * ignore bounds that are not numbers again (as the obsolete module did, `clamp(10, [false, 'Hi', [-1], '7'])` is 7),
 * `parsePercent` ignores whitespace inside the text and returns numbers as they are, `toFixedNum` defaults to 0 digits,
 * `check(a, b)` is true only for two equal numbers, `parseRatio` parses each side with the global `parseFloat` rules,
 * and numbers are written in their JS form (`padStart(1e21, 3)` is "1e+21"; the current module formatted them with
 * Kotlin in the default locale). The radix-less `parseFloat` implements the global algorithm instead of calling the
 * global function, which the extension replaces with this one. Errors are TypeError / RangeError (D10).
 *
 * zh-CN: 新脚本模块层上的 Numberx 模块 (P2.1), 启用 "JavaScript 内置对象扩展" 或调用 jsox 时也作为内置 Number 的扩展 (Number.ICU, 静态的解析与检查函数,
 * 以数字为 this 的 Number.prototype 方法, 支持 radix 的全局 parseFloat). 行为以文档, d.ts 与附录 E 为准: 现有 Kotlin 接受数值字符串的地方照旧接受;
 * clamp / clampTo 重新忽略非数值边界 (同旧 JS); parsePercent 忽略文本内部空白, 数字原样返回; toFixedNum 默认 0 位; check(a, b) 只在两个相等的数字时为 true;
 * parseRatio 两侧按全局 parseFloat 规则解析; 数字以 JS 形式写出 (现有模块用 Kotlin 按默认语言格式化). 不带 radix 的 parseFloat 实现全局算法而不调用全局函数
 * (扩展会把全局函数替换为本函数). 错误为 TypeError / RangeError (D10).
 */
@ScriptModule(name = "Numberx", dollarPrefix = false, builtIn = "Number")
object NumberxModule {

    private val OPERATORS = mapOf<String, (Double, Double) -> Boolean>(
        "<" to { a, b -> a < b },
        "<=" to { a, b -> a <= b },
        ">" to { a, b -> a > b },
        ">=" to { a, b -> a >= b },
        "=" to { a, b -> a == b },
        "==" to { a, b -> a == b },
        "!=" to { a, b -> a != b },
        "<>" to { a, b -> a != b },
    )

    private val PERCENT = Regex("^([+-]?\\d+(?:\\.\\d+)?)(%*)$")

    /** 996.ICU - Developers' lives matter. */
    @ScriptProperty(builtIn = [ScriptBuiltIn.STATIC])
    val ICU = 996

    /** Fails with a TypeError unless every argument is a number. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun ensureNumber(vararg o: Any?) {
        o.forEach { if (JsoxValues.unwrap(it) !is Number) throw JsoxValues.typeError("Numberx.ensureNumber", "o", "number", it) }
    }

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun ensureNumberLike(value: Any?, allowNaN: Boolean = false): Double = numberLike("Numberx.ensureNumberLike", "value", value, allowNaN)

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun ensureNumbersLike(cx: Context, scope: VarScope, values: List<Any?>, allowNaN: Boolean = false, containsNaN: Boolean = false): NativeArray {
        val result = ArrayList<Any?>()
        values.forEachIndexed { index, value ->
            val x = numberLike("Numberx.ensureNumbersLike", "values[$index]", value, allowNaN)
            if (!x.isNaN() || containsNaN) result += x
        }
        return cx.newArray(scope, result.toTypedArray()) as NativeArray
    }

    /**
     * `check(o)`: whether `o` is a number; `check(a, b)`: whether both are numbers and equal; `check(a, op, b, op, c, ...)`:
     * every comparison of neighbours holds (numbers and numeric strings, operators `<`, `<=`, `>`, `>=`, `=`, `==`,
     * `!=`, `<>`).
     */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun check(vararg numOrOpr: Any?): Boolean {
        val inputs = numOrOpr.map { JsoxValues.unwrap(it) }
        when (inputs.size) {
            0 -> return false
            1 -> return inputs[0] is Number
            2 -> return inputs.all { it is Number } && (inputs[0] as Number).toDouble() == (inputs[1] as Number).toDouble()
        }
        for (i in 1 until inputs.size step 2) {
            val operator = (inputs[i] as? CharSequence)?.toString()?.let { OPERATORS[it] }
                ?: throw JsoxValues.typeError("Numberx.check", "numOrOpr[$i]", "comparison operator (${OPERATORS.keys.joinToString(", ")})", numOrOpr[i])
            val a = numberLike("Numberx.check", "numOrOpr[${i - 1}]", numOrOpr[i - 1], false)
            val b = numberLike("Numberx.check", "numOrOpr[${i + 1}]", numOrOpr.getOrNull(i + 1), false)
            if (!operator(a, b)) return false
        }
        return true
    }

    /** [num] clamped to the smallest and largest numbers among [clamps] (flattened; values that are not numbers are ignored). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun clamp(num: Any?, vararg clamps: Any?): Double {
        val x = numberLike("Numberx.clamp", "num", num, false)
        val bounds = bounds(clamps.asIterable())
        if (bounds.isEmpty()) return x
        return x.coerceIn(bounds.first(), bounds.last())
    }

    /** [num] moved by whole cycles into the range of [range] (values that are not numbers are ignored). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun clampTo(num: Any?, range: List<Any?>, cycle: Any? = null): Double {
        val x = numberLike("Numberx.clampTo", "num", num, false)
        val bounds = bounds(range)
        if (bounds.isEmpty()) return x
        val min = bounds.first()
        val max = bounds.last()
        val t = if (JsoxValues.isNullish(cycle)) max - min else numberLike("Numberx.clampTo", "cycle", cycle, false)
        if (!(t > 0)) throw JsoxValues.rangeError("Numberx.clampTo", ScriptMessages.argumentPositive("cycle", JsoxValues.numberToString(t)))
        return when {
            x < min -> x + ceil((min - x) / t) * t
            x > max -> x - ceil((x - max) / t) * t
            else -> x
        }
    }

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun toFixedNum(num: Any?, fraction: Any? = 0.0): Double = JsoxValues.toFixed(parseAny(num), fraction)

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun padStart(num: Any?, targetLength: Any?, pad: Any? = null): String = pad("Numberx.padStart", num, targetLength, pad, start = true)

    @ScriptFunction(builtIn = [ScriptBuiltIn.PROTOTYPE])
    fun padEnd(num: Any?, targetLength: Any?, pad: Any? = null): String = pad("Numberx.padEnd", num, targetLength, pad, start = false)

    /**
     * The global `parseFloat` without [radix] (or with a nullish one); with a radix, the digits without the dot are read as
     * an integer in that radix and divided by radix ^ (digits after the first dot), as `parseFloat("0.101", 2)` is 0.625.
     */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC, ScriptBuiltIn.GLOBAL])
    fun parseFloat(string: Any?, radix: Any? = null): Double {
        if (string == null) return Double.NaN
        var text = RhinoScriptRuntime.toString(string)
        if (JsoxValues.isNullish(radix)) return JsoxValues.parseFloat(text)
        var r = numberLike("Numberx.parseFloat", "radix", radix, true)
        if (r.isNaN() || r.isInfinite()) r = 10.0
        val parts = text.split('.')
        var fractionLength = 0
        if (parts.size > 1) {
            text = parts.joinToString("")
            fractionLength = parts[1].length
        }
        return JsoxValues.parseInt(text, r.roundToInt()) / r.pow(fractionLength.toDouble())
    }

    /** "50%" is 0.5 and "1%%" 0.0001 (whitespace anywhere is ignored); a number is returned as it is. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun parsePercent(percentage: Any?): Double {
        if (JsoxValues.isNullish(percentage)) return Double.NaN
        val value = JsoxValues.unwrap(percentage)
        if (value is Number) return value.toDouble()
        val text = RhinoScriptRuntime.toString(value).filterNot { JsoxValues.isStrWhiteSpace(it) }
        val match = PERCENT.matchEntire(text) ?: return Double.NaN
        val (number, signs) = match.destructured
        return number.toDouble() / 100.0.pow(signs.length)
    }

    /** "3:2" is 1.5; each side is read with the global `parseFloat` rules. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun parseRatio(ratio: Any?): Double {
        if (JsoxValues.isNullish(ratio)) return Double.NaN
        val parts = RhinoScriptRuntime.toString(ratio).trim().split(':').map { it.trim() }
        val y = parts.getOrNull(1) ?: return Double.NaN
        return JsoxValues.parseFloat(parts[0]) / JsoxValues.parseFloat(y)
    }

    /** A number as it is; text with ":" as a ratio, with "%" as a percentage, otherwise as `Number(text)`. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun parseAny(o: Any?): Double {
        if (JsoxValues.isNullish(o)) return Double.NaN
        val value = JsoxValues.unwrap(o)
        if (value is Number) return value.toDouble()
        val text = RhinoScriptRuntime.toString(value).trim()
        return when {
            ':' in text -> parseRatio(text)
            '%' in text -> parsePercent(text)
            else -> RhinoScriptRuntime.toNumber(text)
        }
    }

    /**
     * [value] read with the rules of [parseAny]: null, undefined and values that are not numbers fail with a TypeError,
     * NaN itself with a RangeError unless [allowNaN].
     */
    internal fun numberLike(function: String, param: String, value: Any?, allowNaN: Boolean): Double {
        if (JsoxValues.isNullish(value)) throw JsoxValues.cannotBe(function, param, value)
        val v = JsoxValues.unwrap(value)
        if (v is Double && v.isNaN()) {
            if (!allowNaN) throw JsoxValues.rangeError(function, ScriptMessages.argumentCannotBe(param, "NaN"))
            return Double.NaN
        }
        val x = parseAny(v)
        if (x.isNaN()) throw JsoxValues.typeError(function, param, "number", value)
        return x
    }

    /** Numbers of [values] (flattened): numbers, numeric text as [parseAny] reads it, other values by `Number(value)`; NaN left out. */
    private fun bounds(values: Iterable<Any?>): List<Double> = JsoxValues.flatten(values).map { v ->
        when (v) {
            is Number -> v.toDouble()
            is CharSequence -> parseAny(v)
            else -> JsoxValues.toNumber(v)
        }
    }.filterNot { it.isNaN() }.sorted()

    private fun pad(function: String, num: Any?, targetLength: Any?, pad: Any?, start: Boolean): String {
        val length = JsoxValues.toNumber(targetLength)
        if (length.isNaN() || length.isInfinite()) throw JsoxValues.typeError(function, "targetLength", "number", targetLength)
        val filler = when (val p = JsoxValues.unwrap(pad)) {
            null, Undefined.instance -> "0"
            is Number -> JsoxValues.numberToString(p.toDouble())
            else -> RhinoScriptRuntime.toString(p)
        }
        val text = JsoxValues.numberToString(parseAny(num))
        // ToLength, as String.prototype.padStart / padEnd: fractions are truncated.
        val target = if (length <= 0) 0 else minOf(length, Int.MAX_VALUE.toDouble()).toInt()
        if (text.length >= target || filler.isEmpty()) return text
        val missing = target - text.length
        val padding = filler.repeat(missing / filler.length) + filler.take(missing % filler.length)
        return if (start) padding + text else text + padding
    }

}
