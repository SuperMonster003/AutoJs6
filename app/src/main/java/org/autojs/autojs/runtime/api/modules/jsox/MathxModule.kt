package org.autojs.autojs.runtime.api.modules.jsox

import org.autojs.autojs.runtime.api.modules.ScriptBuiltIn
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import android.graphics.Point as AndroidPoint
import android.graphics.Rect as AndroidRect
import org.opencv.core.Point as OpenCVPoint

/**
 * The `Mathx` module on the new script module layer (roadmap P2.1), also the extension of the built-in `Math` (every
 * function but `max` / `min`, which extend `Math` as `maxi` / `mini`) when the "extend JavaScript built-in objects"
 * option or `jsox` applies it; `randomInt` / `randomFloat` are global functions as well.
 *
 * The statistics functions (`sum`, `mean` / `avg`, `median`, `var`, `std`, `cv`, `mode`, `max`, `min`) take numbers and
 * arrays (flattened recursively, holes skipped, every element converted with `Number(x)`); when all arguments but the
 * last are arrays and the last is a number, it is the count of fraction digits of the result (ROADMAP appendix E: the
 * current Kotlin module passed the arguments as one vararg element, so the fraction was summed as a number). Without
 * numbers `median` / `mode` are NaN (the current module failed with an index error and an empty-collection error) and,
 * following the documentation (appendix C Q11), `sum` is NaN, `max` is -Infinity and `min` is Infinity. NaN is
 * contagious. `randomInt` / `randomFloat` ignore NaN in a range of several values, as documented (the current module
 * failed on it). `roundLog` / `roundPow` round with `Math.round` semantics (the current module failed on NaN and turned
 * Infinity into Long.MAX_VALUE). Errors are TypeError / RangeError (D10).
 *
 * zh-CN: 新脚本模块层上的 Mathx 模块 (P2.1), 启用 "JavaScript 内置对象扩展" 或调用 jsox 时也作为内置 Math 的扩展 (除 max / min 外的全部函数, 二者在 Math 上
 * 名为 maxi / mini); randomInt / randomFloat 同时是全局函数. 统计函数接受数字与数组 (递归展平, 跳过空位, 元素按 Number(x) 转换); 除最后一个参数外都是数组且最后
 * 一个是数字时, 它是结果保留的小数位数 (附录 E: 现有 Kotlin 把参数整体当作一个可变参元素传入, fraction 被当作数值求和). 没有数字时 median / mode 为 NaN
 * (现有模块分别抛索引错误与空集合错误), 按文档 (附录 C Q11) sum 为 NaN, max 为 -Infinity, min 为 Infinity. NaN 具有传染性. randomInt / randomFloat 在多值范围中
 * 按文档忽略 NaN (现有模块会报错). roundLog / roundPow 按 Math.round 语义取整 (现有模块对 NaN 报错, 把 Infinity 变成 Long.MAX_VALUE). 错误为 TypeError /
 * RangeError (D10).
 */
@ScriptModule(name = "Mathx", dollarPrefix = false, builtIn = "Math")
object MathxModule {

    private const val MIN_SAFE_INTEGER = -9_007_199_254_740_991.0
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0
    private const val DEFAULT_LOG_FRACTION = 13.0

    /** The old name of [randomInt], deprecated. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun randInt(vararg range: Any?): Double = randomIntOf("Mathx.randInt", range)

    @ScriptFunction(global = ScriptGlobal.ALSO, builtIn = [ScriptBuiltIn.STATIC])
    fun randomInt(vararg range: Any?): Double = randomIntOf("Mathx.randomInt", range)

    /** The old name of [randomFloat], deprecated. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun randFloat(vararg range: Any?): Double = randomFloatOf("Mathx.randFloat", range)

    @ScriptFunction(global = ScriptGlobal.ALSO, builtIn = [ScriptBuiltIn.STATIC])
    fun randomFloat(vararg range: Any?): Double = randomFloatOf("Mathx.randomFloat", range)

    /** Alias of [randomFloat]; extending `Math` replaces `Math.random` with it (no arguments: a value in [0, 1)). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun random(vararg range: Any?): Double = randomFloatOf("Mathx.random", range)

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun sum(vararg num: Any?): Double = statistic(num) { values -> if (values.isEmpty()) Double.NaN else values.sum() }

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun mean(vararg num: Any?): Double = statistic(num, ::meanOf)

    /** Alias of [mean]. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun avg(vararg num: Any?): Double = statistic(num, ::meanOf)

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun median(vararg num: Any?): Double = statistic(num) { values ->
        if (values.isEmpty() || values.any { it.isNaN() }) return@statistic Double.NaN
        val sorted = values.sorted()
        if (sorted.size % 2 != 0) sorted[sorted.size / 2] else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }

    /** Population variance. */
    @ScriptFunction(name = "var", builtIn = [ScriptBuiltIn.STATIC])
    fun variance(vararg num: Any?): Double = statistic(num, ::varianceOf)

    /** Population standard deviation. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun std(vararg num: Any?): Double = statistic(num) { values -> sqrt(varianceOf(values)) }

    /** Coefficient of variation with the sample standard deviation. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun cv(vararg num: Any?): Double = statistic(num) { values ->
        if (values.size < 2 || values.any { it.isNaN() }) return@statistic Double.NaN
        val mean = values.sum() / values.size
        sqrt(values.sumOf { (it - mean).pow(2) } / (values.size - 1)) / mean
    }

    /** The first most frequent value. */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun mode(vararg num: Any?): Double = statistic(num) { values ->
        if (values.isEmpty() || values.any { it.isNaN() }) return@statistic Double.NaN
        val frequencies = LinkedHashMap<Double, Int>()
        values.forEach { frequencies[it] = (frequencies[it] ?: 0) + 1 }
        val most = frequencies.values.max()
        frequencies.entries.first { it.value == most }.key
    }

    /**
     * The distance between two points (`[x, y]`, `{ x, y }`, OpenCV or Android points, the center of Android rects), the
     * diagonal of one Android rect; [fraction] (or the second argument after a rect) is the count of fraction digits.
     */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun dist(pointA: Any? = Undefined.instance, pointB: Any? = Undefined.instance, fraction: Any? = Undefined.instance): Double {
        val args = listOf(pointA, pointB, fraction).dropLastWhile { Undefined.isUndefined(it) }.map { JsoxValues.unwrap(it) }
        return when (args.size) {
            0 -> Double.NaN
            1 -> when (val a = args[0]) {
                is AndroidRect -> distance(a.left.toDouble(), a.top.toDouble(), a.right.toDouble(), a.bottom.toDouble())
                else -> throw JsoxValues.typeError("Mathx.dist", "pointA", "android.graphics.Rect", pointA)
            }
            2 -> {
                val (a, b) = args
                if (a is AndroidRect && b is Number) {
                    JsoxValues.toFixed(distance(a.left.toDouble(), a.top.toDouble(), a.right.toDouble(), a.bottom.toDouble()), b)
                } else {
                    distance(pointOf(a, pointA, "pointA"), pointOf(b, pointB, "pointB"))
                }
            }
            else -> JsoxValues.toFixed(distance(pointOf(args[0], pointA, "pointA"), pointOf(args[1], pointB, "pointB")), args[2])
        }
    }

    /** The logarithm of [antilogarithm] to [base], with [fraction] digits (13 by default). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun logMn(base: Double, antilogarithm: Double, fraction: Any? = null): Double {
        val digits = if (JsoxValues.isNullish(fraction)) DEFAULT_LOG_FRACTION else JsoxValues.toNumber(fraction).also {
            if (it.isNaN()) throw JsoxValues.typeError("Mathx.logMn", "fraction", "number", fraction)
        }
        return JsoxValues.toFixed(ln(antilogarithm) / ln(base), digits)
    }

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun floorLog(base: Double, antilogarithm: Double): Double = floor(log("Mathx.floorLog", base, antilogarithm))

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun ceilLog(base: Double, antilogarithm: Double): Double = ceil(log("Mathx.ceilLog", base, antilogarithm))

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun roundLog(base: Double, antilogarithm: Double): Double = round(log("Mathx.roundLog", base, antilogarithm))

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun floorPow(base: Double, antilogarithm: Double): Double = base.pow(floor(log("Mathx.floorPow", base, antilogarithm)))

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun ceilPow(base: Double, antilogarithm: Double): Double = base.pow(ceil(log("Mathx.ceilPow", base, antilogarithm)))

    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC])
    fun roundPow(base: Double, antilogarithm: Double): Double = base.pow(round(log("Mathx.roundPow", base, antilogarithm)))

    @ScriptFunction
    fun max(vararg num: Any?): Double = statistic(num, ::maxOfValues)

    /** [max] as the `Math` extension names it (`Math.max` stays the built-in one). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC], builtInOnly = true)
    fun maxi(vararg num: Any?): Double = statistic(num, ::maxOfValues)

    @ScriptFunction
    fun min(vararg num: Any?): Double = statistic(num, ::minOfValues)

    /** [min] as the `Math` extension names it (`Math.min` stays the built-in one). */
    @ScriptFunction(builtIn = [ScriptBuiltIn.STATIC], builtInOnly = true)
    fun mini(vararg num: Any?): Double = statistic(num, ::minOfValues)

    /**
     * [compute] over the numbers of [args]; when every argument but the last is an array and the last is a number (not
     * nullish), the last argument is the count of fraction digits of the result. Trailing `undefined` arguments count as
     * omitted, as for every function of the layer (roadmap T4).
     */
    private inline fun statistic(args: Array<out Any?>, compute: (List<Double>) -> Double): Double {
        val list = args.asList().dropLastWhile { Undefined.isUndefined(it) }
        if (list.size >= 2) {
            val last = list.last()
            if (!JsoxValues.isNullish(last) && !JsoxValues.isJsArray(last) && list.dropLast(1).all { JsoxValues.isJsArray(it) }) {
                val fraction = JsoxValues.toNumber(last)
                if (!fraction.isNaN()) return JsoxValues.toFixed(compute(numbersOf(list.dropLast(1))), fraction)
            }
        }
        return compute(numbersOf(list))
    }

    private fun numbersOf(values: List<Any?>): List<Double> = JsoxValues.flatten(values).map { JsoxValues.toNumber(it) }

    private fun meanOf(values: List<Double>): Double = values.sum() / values.size

    private fun varianceOf(values: List<Double>): Double {
        if (values.isEmpty() || values.any { it.isNaN() }) return Double.NaN
        val mean = meanOf(values)
        return values.fold(0.0) { acc, x -> acc + (x - mean).pow(2) } / values.size
    }

    private fun maxOfValues(values: List<Double>): Double = values.fold(Double.NEGATIVE_INFINITY) { acc, x -> Math.max(acc, x) }

    private fun minOfValues(values: List<Double>): Double = values.fold(Double.POSITIVE_INFINITY) { acc, x -> Math.min(acc, x) }

    private fun randomIntOf(function: String, range: Array<out Any?>): Double {
        val values = JsoxValues.flatten(range.asList().dropLastWhile { Undefined.isUndefined(it) })
        return when (values.size) {
            0 -> randomIntIn(MIN_SAFE_INTEGER, MAX_SAFE_INTEGER, function)
            1 -> single(function, values[0], range).let { n -> if (n > 0) randomIntIn(0.0, n, function) else randomIntIn(n, 0.0, function) }
            else -> {
                val numbers = values.map { JsoxValues.toNumber(it) }.filterNot { it.isNaN() }
                if (numbers.isEmpty()) throw JsoxValues.rangeError(function, ScriptMessages.argumentNoNumber("range"))
                randomIntIn(numbers.min(), numbers.max(), function)
            }
        }
    }

    private fun randomIntIn(low: Double, high: Double, function: String): Double {
        val min = ceil(low)
        val max = floor(high)
        if (min > max) throw JsoxValues.rangeError(function, ScriptMessages.rangeNoInteger(JsoxValues.numberToString(low), JsoxValues.numberToString(high)))
        return floor(Math.random() * (max - min + 1)) + min
    }

    private fun randomFloatOf(function: String, range: Array<out Any?>): Double {
        val values = JsoxValues.flatten(range.asList().dropLastWhile { Undefined.isUndefined(it) })
        return when (values.size) {
            0 -> Math.random()
            1 -> single(function, values[0], range).let { n -> if (n > 0) randomFloatIn(0.0, n) else randomFloatIn(n, 0.0) }
            else -> {
                val numbers = values.map { JsoxValues.toNumber(it) }.filterNot { it.isNaN() }
                if (numbers.isEmpty()) throw JsoxValues.rangeError(function, ScriptMessages.argumentNoNumber("range"))
                randomFloatIn(numbers.min(), numbers.max())
            }
        }
    }

    private fun randomFloatIn(min: Double, max: Double): Double = Math.random() * (max - min) + min

    /** The only number of a range: null, undefined and values that are not numbers fail with a TypeError. */
    private fun single(function: String, value: Any?, range: Array<out Any?>): Double {
        val n = if (JsoxValues.isNullish(value)) Double.NaN else JsoxValues.toNumber(value)
        if (n.isNaN()) throw JsoxValues.typeError(function, "range", "number", if (range.size == 1) range[0] else value)
        return n
    }

    /** The logarithm with the default 13 fraction digits; a base or antilogarithm that is NaN fails with a RangeError. */
    private fun log(function: String, base: Double, antilogarithm: Double): Double {
        if (base.isNaN()) throw JsoxValues.rangeError(function, ScriptMessages.argumentCannotBe("base", "NaN"))
        if (antilogarithm.isNaN()) throw JsoxValues.rangeError(function, ScriptMessages.argumentCannotBe("antilogarithm", "NaN"))
        return logMn(base, antilogarithm)
    }

    /** `Math.round` for the results of [log] (half up): NaN and infinities stay as they are. */
    private fun round(x: Double): Double = if (x.isNaN() || x.isInfinite()) x else Math.round(x).toDouble()

    private fun distance(ax: Double, ay: Double, bx: Double, by: Double): Double = sqrt((ax - bx).pow(2) + (ay - by).pow(2))

    private fun distance(a: DoubleArray, b: DoubleArray): Double = distance(a[0], a[1], b[0], b[1])

    /** The point of [o] ([raw] is the argument as passed, for messages). */
    private fun pointOf(o: Any?, raw: Any?, param: String): DoubleArray {
        val point = when (o) {
            is NativeArray -> {
                if (o.length != 2L) throw JsoxValues.typeError("Mathx.dist", ScriptMessages.argumentLength(param, 2, o.length.toInt()))
                coordinates(o.get(0, o), o.get(1, o))
            }
            is NativeObject -> coordinates(ScriptableObject.getProperty(o, "x"), ScriptableObject.getProperty(o, "y"))
            is AndroidRect -> doubleArrayOf(o.exactCenterX().toDouble(), o.exactCenterY().toDouble())
            is AndroidPoint -> doubleArrayOf(o.x.toDouble(), o.y.toDouble())
            is OpenCVPoint -> doubleArrayOf(o.x, o.y)
            else -> null
        }
        return point ?: throw JsoxValues.typeError("Mathx.dist", param, "point ([x, y], { x, y }, Point or Rect)", raw)
    }

    /** Both coordinates as numbers, or null when one is missing, nullish or NaN. */
    private fun coordinates(x: Any?, y: Any?): DoubleArray? {
        fun number(v: Any?): Double = if (v === Scriptable.NOT_FOUND || JsoxValues.isNullish(v)) Double.NaN else JsoxValues.toNumber(v)
        val nx = number(x)
        val ny = number(y)
        return if (nx.isNaN() || ny.isNaN()) null else doubleArrayOf(nx, ny)
    }

}
