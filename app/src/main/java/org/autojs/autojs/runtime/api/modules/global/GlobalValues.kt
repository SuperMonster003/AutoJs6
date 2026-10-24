package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.api.Scale
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Argument rules of the global functions (roadmap P3.0), ported from the current `augment/global` (`Global` and
 * `CoordinateScale`) with the error model of the new layer: a value of the wrong type is a TypeError, an unusable value
 * a RangeError (D10, appendix C Q12), with the messages of [ScriptMessages] (R4). The pure parts (sleep range, wait
 * policy, coordinate conversion) are plain functions, tested on the JVM by `GlobalModuleTest`.
 *
 * zh-CN: 全局函数的参数规则 (P3.0), 移植自现有 augment/global (Global 与 CoordinateScale), 采用新层的错误模型: 类型不对为 TypeError, 取值不可用为
 * RangeError (D10, 附录 C Q12), 消息来自 ScriptMessages (R4). 纯计算部分 (sleep 范围, wait 轮询计划, 坐标换算) 为普通函数, 由 GlobalModuleTest 在 JVM 上测试.
 */
internal object GlobalValues {

    fun isNullish(value: Any?): Boolean = value == null || Undefined.isUndefined(value) || value === Scriptable.NOT_FOUND

    fun typeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, expected, ScriptArgs.describe(value))))

    fun rangeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, expected, UtilValues.quote(value))))

    /** An argument that cannot be combined with the others ([what] describes the combination). */
    fun cannotBe(function: String, param: String, what: String): RuntimeException =
        RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentCannotBe(param, what)))

    /**
     * A number argument read as the current `RhinoUtils.coerceNumber` reads it (JS ToNumber, so numeric strings and
     * booleans are accepted), nullish or NaN refused: NaN itself is a RangeError, a value without a number a TypeError.
     */
    fun number(function: String, param: String, value: Any?): Double {
        if (isNullish(value)) throw typeError(function, param, "number", value)
        val number = RhinoScriptRuntime.toNumber(value)
        if (number.isNaN()) throw if (value is Number) rangeError(function, param, "number", value) else typeError(function, param, "number", value)
        return number
    }

    /** A number argument where nullish and NaN are both NaN (the current `coerceNumber(o, NaN)`, `random`). */
    fun numberOrNaN(value: Any?): Double = if (isNullish(value)) Double.NaN else RhinoScriptRuntime.toNumber(value)

    /** An integral argument truncated toward zero (the current `coerceIntNumber` / `coerceLongNumber`). */
    fun integral(function: String, param: String, value: Any?, min: Double, max: Double, bits: Int): Double {
        val number = number(function, param, value)
        if (number.isInfinite()) throw rangeError(function, param, "finite number", value)
        val truncated = if (number > 0.0) floor(number) else ceil(number)
        if (truncated < min || truncated > max) throw RhinoScriptRuntime.rangeError(
            ScriptMessages.failedToCall(function, ScriptMessages.argumentInteger(param, bits, UtilValues.quote(value)))
        )
        return truncated
    }

    fun int(function: String, param: String, value: Any?): Int =
        integral(function, param, value, Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble(), 32).toInt()

    fun long(function: String, param: String, value: Any?): Long =
        integral(function, param, value, MIN_LONG_AS_DOUBLE, MAX_LONG_AS_DOUBLE, 64).toLong()

    private val MAX_LONG_AS_DOUBLE = Double.fromBits(0x43dfffffffffffffL)
    private val MIN_LONG_AS_DOUBLE = Double.fromBits(-4332462841530417152L)

    // sleep

    data class SleepRange(val min: Double, val max: Double)

    private val SLEEP_BOUNDS = Regex("""[+-]?(\d+(\.\d+)?(e\d+)?)""")

    /**
     * The range of `sleep(millisMin, millisMax)`: a number bound, a string holding a delta around millisMin, or millisMin
     * alone. millisMin is a number (a numeric string too, as the current Kotlin accepts); nullish is a TypeError, as in
     * the obsolete JS and the documented signature (the current Kotlin slept 0 ms).
     */
    fun sleepRange(millisMin: Any?, millisMax: Any?): SleepRange {
        val base = long("sleep", "millisMin", millisMin).coerceAtLeast(0L).toDouble()
        val (first, second) = when {
            isNullish(millisMax) -> base to base
            millisMax is Number -> base to number("sleep", "millisMax", millisMax)
            millisMax is CharSequence -> {
                val matched = SLEEP_BOUNDS.find(millisMax) ?: throw RhinoScriptRuntime.rangeError(
                    ScriptMessages.failedToCall("sleep", ScriptMessages.argumentNoNumber("millisMax"))
                )
                val delta = matched.value.toDouble()
                (base - delta) to (base + delta)
            }
            else -> throw typeError("sleep", "millisMax", "number | string", millisMax)
        }
        val min = minOf(first, second).coerceAtLeast(0.0)
        val max = maxOf(first, second).coerceAtLeast(0.0).coerceAtMost(Long.MAX_VALUE.toDouble())
        return SleepRange(min, max)
    }

    fun sleepMillis(range: SleepRange, random: Double): Long {
        require(random >= 0.0 && random < 1.0) { "Random value for sleep must be in [0, 1)" }
        val millis = range.min + ceil(random * (range.max - range.min))
        return when {
            millis <= 0.0 -> 0L
            millis >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
            else -> millis.toLong()
        }
    }

    // wait

    /** Polling plan of `wait`: a limit below 100 is a number of attempts, otherwise a timeout in milliseconds. */
    data class WaitPolicy(val times: Long, val timeout: Long, val interval: Long)

    /**
     * Maps the arguments of `wait` onto (condition, limit, interval, callback): an object at the limit or the interval
     * position is the callback, and the arguments after it are dropped.
     */
    fun normalizeWaitArguments(condition: Any?, limit: Any?, interval: Any?, callback: Any?): Array<Any?> = when {
        limit is NativeObject -> arrayOf(condition, null, null, limit)
        interval is NativeObject -> arrayOf(condition, limit, null, interval)
        else -> arrayOf(condition, limit, interval, callback)
    }

    fun waitPolicy(function: String, limitArg: Any?, intervalArg: Any?): WaitPolicy {
        val limitValue = numberOrNaN(limitArg).takeUnless { it.isNaN() } ?: 10000.0
        if (limitValue < 0) throw rangeError(function, "limit", "non-negative number", limitValue)
        val limit = limitValue.roundToLong()
        var (times, timeout) = if (limit < 100) limit to Long.MAX_VALUE else Long.MAX_VALUE to limit
        val intervalValue = numberOrNaN(intervalArg).takeUnless { it.isNaN() } ?: 200.0
        if (!intervalValue.isFinite() || intervalValue < 0) throw rangeError(function, "interval", "finite non-negative number", intervalValue)
        val interval = intervalValue.roundToLong()
        if (interval >= timeout) times = minOf(times, 1)
        return WaitPolicy(times, timeout, interval)
    }

    /** Only nullish, `false` and NaN fail a `wait` check; 0, -0 and "" pass. */
    fun isWaitConditionSatisfied(checked: Any?): Boolean =
        !(isNullish(checked) || checked == false || checked is Double && checked.isNaN() || checked is Float && checked.isNaN())

    // currentPackage / currentActivity / currentComponent

    enum class ComponentFetchMode { AUTOMATISM, ACCESSIBILITY, SHIZUKU, ROOT }

    /** The `mode` argument: a mode name, or an object with `by` or `mode`; nullish is automatic. */
    fun componentFetchMode(function: String, mode: Any?): ComponentFetchMode {
        if (isNullish(mode)) return ComponentFetchMode.AUTOMATISM
        if (mode is NativeObject) {
            val inner = listOf("by", "mode").map { ScriptableObject.getProperty(mode, it) }.firstOrNull { !isNullish(it) }
            return componentFetchMode(function, inner ?: "")
        }
        return when (RhinoScriptRuntime.toString(mode).lowercase()) {
            "", "auto", "automatic", "automatism" -> ComponentFetchMode.AUTOMATISM
            "a11y", "accessibility" -> ComponentFetchMode.ACCESSIBILITY
            "shizuku" -> ComponentFetchMode.SHIZUKU
            "root" -> ComponentFetchMode.ROOT
            else -> throw rangeError(function, "mode", "'auto' | 'accessibility' | 'a11y' | 'shizuku' | 'root'", mode)
        }
    }

    // cX / cY / cYx / cXy

    enum class Axis(val functionName: String, val usesWidth: Boolean, val cross: Boolean) {
        X("cX", true, false),
        Y("cY", false, false),
        Y_FROM_X("cYx", true, true),
        X_FROM_Y("cXy", false, true),
    }

    /**
     * The coordinate conversion of the current `CoordinateScale.convert` for the arguments as given ([args] holds only
     * the arguments the script passed). [width] / [height] are the display dimensions.
     */
    fun convert(axis: Axis, width: Int, height: Int, scale: Scale, args: Array<out Any?>): Double {
        val name = axis.functionName
        val dimension = if (axis.usesWidth) width.toDouble() else height.toDouble()
        if (args.isEmpty()) {
            if (axis.cross) throw typeError(name, "num", "number", Undefined.instance)
            return dimension
        }
        val value = number(name, "num", args[0])
        if (!value.isFinite()) throw rangeError(name, "num", "finite number", value)
        // A text built at run time (a ConsString) is a mode shortcut or a ratio as a literal is: the current functions
        // unwrapped their arguments first.
        var base = args.getOrNull(1).let { if (it is CharSequence) it.toString() else it }
        var modeArg = args.getOrNull(2).let { if (it is CharSequence) it.toString() else it }
        if (base is Boolean || base == "auto") {
            if (!isNullish(modeArg)) throw cannotBe(name, "isRatio", "given with a mode shortcut as the base")
            modeArg = base
            base = null
        }
        val mode: Boolean? = when {
            isNullish(modeArg) || modeArg == "auto" -> null
            modeArg is Boolean -> modeArg
            else -> throw typeError(name, "isRatio", "boolean | 'auto'", modeArg)
        }
        val literalRatio = if (axis.cross && base is CharSequence) {
            if (mode == false) throw cannotBe(name, "base", "an aspect ratio with absolute coordinate mode")
            parseAspectRatio(base.toString(), name)
        } else null
        val isRatio = literalRatio != null || (mode ?: (abs(value) < 1))

        val result = if (!isRatio) {
            val defaultBase = if (axis.usesWidth) scale.baseX else scale.baseY
            divideScaled(value, dimension, absoluteBase(base, defaultBase, name))
        } else if (!axis.cross) {
            // The direct proportional form intentionally does not use the base.
            dimension * value
        } else {
            val ratio = literalRatio ?: when {
                // Only the implicit aspect ratio needs a coherent pair; an explicit base or ratio does not.
                isNullish(base) -> scale.defaultAspectRatio()
                base is Number -> base.toDouble().also { requirePositiveRatio(it, name) }
                else -> throw typeError(name, "base", "number | 'width:height'", base)
            }
            // A ratio always means width / height, including landscape layouts.
            if (axis.usesWidth) divideScaled(value, dimension, ratio) else {
                val product = dimension * value
                if (product.isFinite()) product * ratio else value * ratio * dimension
            }
        }
        if (!result.isFinite()) throw rangeError(name, "num", "number with a finite scaled coordinate", value)
        return roundCoordinate(result)
    }

    private fun absoluteBase(base: Any?, default: Int, name: String): Double {
        if (isNullish(base)) return default.toDouble()
        if (base !is Number) throw typeError(name, "base", "number", base)
        val number = base.toDouble()
        if (!(number.isFinite() && number >= 1 && number <= Int.MAX_VALUE && number == floor(number))) {
            throw rangeError(name, "base", "positive 32-bit integer", number)
        }
        return number
    }

    private fun parseAspectRatio(text: String, name: String): Double {
        val parts = text.split(':')
        val width = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
        val height = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
        if (parts.size != 2 || width == null || height == null || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) {
            throw rangeError(name, "base", "aspect ratio 'width:height' of positive numbers", text)
        }
        return (width / height).also { requirePositiveRatio(it, name) }
    }

    private fun requirePositiveRatio(value: Double, name: String) {
        if (!(value.isFinite() && value > 0)) throw rangeError(name, "base", "positive finite aspect ratio", value)
    }

    private fun divideScaled(value: Double, dimension: Double, divisor: Double): Double {
        val product = dimension * value
        // Do not reject a representable result merely because the multiplication overflowed before the division.
        return if (product.isFinite()) product / divisor else value * (dimension / divisor)
    }

    private fun roundCoordinate(value: Double): Double {
        // roundToLong's nearest-integer, ties-toward-positive-infinity rule without clamping to Long's range.
        val lower = floor(value)
        return lower + if (value - lower >= 0.5) 1.0 else 0.0
    }

}
