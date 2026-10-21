package org.autojs.autojs.runtime.api.modules.s13n

import org.autojs.autojs.runtime.api.augment.colors.ColorNativeObject
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.cvt.ByteUnits
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.util.ColorUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.util.concurrent.TimeUnit
import android.graphics.Point as AndroidPoint
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime
import org.opencv.core.Point as OpencvPoint

/**
 * The `s13n` module on the new script module layer (roadmap P2.2): standardizes colors, throwables, points, time values
 * and byte counts.
 *
 * Deviations of the current module fixed here (ROADMAP appendix E): `time` took a `TimeUnit` unit for an options object
 * and failed with ClassCastException (only plain objects are options now, as in the obsolete module); the second
 * coordinate of `point(x, y)` is truncated like the first one and like the obsolete module (the current module rounded
 * it); `bytes` returns an integer as documented (the target `B` ignored the fraction digits). Argument errors are
 * TypeError / RangeError (D10), surplus arguments a
 * TypeError (appendix C Q7). Color conversion needs `android.graphics.Color`, so on the JVM only the paths failing
 * before the conversion are covered (appendix E); the conversion goes through `ColorUtils` as in the current module, and
 * a `Color` object of the colors module is still the `augment` package's `ColorNativeObject` until colors migrate (P2.3).
 *
 * zh-CN: 新脚本模块层上的 s13n 模块 (P2.2): 把颜色, 可抛异常, 坐标点, 时间值与字节数标准化. 修正的现有模块偏差 (附录 E): time 把 TimeUnit 单位当作选项对象,
 * 以 ClassCastException 失败 (现在只有普通对象是选项, 与旧 JS 相同); point(x, y) 的第二个坐标与第一个一样截断取整 (与旧 JS 相同, 现有模块为四舍五入);
 * bytes 按文档返回整数 (目标单位 B 时原先忽略小数位数). 参数错误为 TypeError / RangeError (D10),
 * 多余参数为 TypeError (附录 C Q7). 颜色转换需要 android.graphics.Color, JVM 上只覆盖转换前就失败的路径 (附录 E); 转换与现有模块一样经 ColorUtils, colors 迁移
 * (P2.3) 之前 colors 模块的 Color 对象仍是 augment 包的 ColorNativeObject.
 */
@ScriptModule(name = "s13n")
object S13nModule {

    private const val COLOR = "s13n.color"
    private const val THROWABLE = "s13n.throwable"
    private const val POINT = "s13n.point"
    private const val TIME = "s13n.time"
    private const val BYTES = "s13n.bytes"

    private val UNITS = listOf(
        Regex("^NANO(SECONDS?|S)?$", RegexOption.IGNORE_CASE) to TimeUnit.NANOSECONDS,
        Regex("^MICRO(SECONDS?|S)?$", RegexOption.IGNORE_CASE) to TimeUnit.MICROSECONDS,
        Regex("^(MILLI(SECONDS?|S)?|MS)$", RegexOption.IGNORE_CASE) to TimeUnit.MILLISECONDS,
        Regex("^(S(ECONDS?)?|SECS?)$", RegexOption.IGNORE_CASE) to TimeUnit.SECONDS,
        Regex("^(M(INUTES?)?|MINS?)$", RegexOption.IGNORE_CASE) to TimeUnit.MINUTES,
        Regex("^(H(OURS?)?|HRS?)$", RegexOption.IGNORE_CASE) to TimeUnit.HOURS,
        Regex("^D(AYS?)?$", RegexOption.IGNORE_CASE) to TimeUnit.DAYS,
    )

    private val TIME_TEXT_SPACES = Regex("\\s+(?!\\d)|(^|\\D)\\s+")
    private val NON_DIGITS = Regex("\\D+")

    @ScriptFunction
    fun color(o: Any?): Int = when (val value = unwrap(o)) {
        is ColorNativeObject -> value.color
        is ThemeColor -> ColorUtils.toInt(value)
        is Number -> ColorUtils.toInt(value.toLong())
        is CharSequence -> value.toString().let { text ->
            try {
                ColorUtils.toInt(text)
            } catch (_: Exception) {
                throw rangeError(COLOR, ScriptMessages.argumentCannotBe("o", "\"$text\""))
            }
        }
        else -> throw typeError(COLOR, "o", "ColorHex / ColorInt / ColorName / Color / ThemeColor", o)
    }

    @ScriptFunction
    fun throwable(o: Any?): Throwable = when (val value = unwrap(o)) {
        is Throwable -> value
        is CharSequence -> Exception(value.toString())
        is NativeError -> listOf("rhinoException", "javaException").firstNotNullOfOrNull { name -> unwrap(ScriptableObject.getProperty(value, name)) as? Exception }
            ?: Exception(ScriptableObject.getProperty(value, "message").let { if (it === Scriptable.NOT_FOUND || ScriptArgs.isNullish(it)) "" else RhinoScriptRuntime.toString(it) })
        else -> throw typeError(THROWABLE, "o", "Throwable / string / Error", o)
    }

    /**
     * A point from a point object, an `[x, y]` array, an `{ x, y }` object or numbers; a single number (with `y` omitted,
     * nullish or not a number) is used for both coordinates (documented; a nullish `y` keeps the current module's
     * behavior, appendix E). Coordinates are truncated toward zero, as Rhino converted them for the obsolete module.
     */
    @ScriptFunction
    fun point(source: Any?, y: Any? = null): AndroidPoint = when (val value = unwrap(source)) {
        is AndroidPoint -> value
        is OpencvPoint -> AndroidPoint(value.x.toInt(), value.y.toInt())
        is NativeArray -> AndroidPoint(coordinate(element(value, 0), "source[0]"), coordinate(element(value, 1), "source[1]"))
        is Number -> {
            val x = coordinate(value, "source")
            val other = if (ScriptArgs.isNullish(y)) Double.NaN else RhinoScriptRuntime.toNumber(unwrap(y))
            AndroidPoint(x, if (other.isNaN()) x else coordinate(other as Any?, "y"))
        }
        is Scriptable -> if (isPlainObject(value)) {
            AndroidPoint(coordinate(ScriptableObject.getProperty(value, "x"), "source.x"), coordinate(ScriptableObject.getProperty(value, "y"), "source.y"))
        } else {
            throw typeError(POINT, "source", "Point / number[] / { x, y } / number", source)
        }
        else -> throw typeError(POINT, "source", "Point / number[] / { x, y } / number", source)
    }

    /**
     * A time value converted from [fromUnit] to [toUnit] (both milliseconds by default): units are TimeUnit values or
     * case-insensitive names, an options object `{ fromUnit, toUnit }` may stand in for [fromUnit], and a string with six
     * number parts (year, month, day, hours, minutes, seconds) is a date-time. [cx] reads such strings as `Date` does.
     */
    @ScriptFunction
    fun time(cx: Context, source: Any?, fromUnit: Any? = null, toUnit: Any? = null): Double {
        val value = unwrap(source)
        if (value is Double && value.isNaN()) return Double.NaN
        if (value is NativeDate) return time(cx, value.date, fromUnit, toUnit)
        var niceSource = value
        if (value is CharSequence) {
            val text = value.toString().replace(TIME_TEXT_SPACES, "$1")
            val parts = text.split(NON_DIGITS).filter { it.isNotEmpty() }
            when (parts.size) {
                0 -> return Double.NaN
                6 -> {
                    val (year, month, day, hours, minutes) = parts
                    return time(cx, NativeDate.date_parseString(cx, "$year/$month/$day $hours:$minutes:${parts[5]}"), fromUnit, toUnit)
                }
                in 7..Int.MAX_VALUE -> return Double.NaN
            }
            niceSource = text
        }
        if (fromUnit is Scriptable && isPlainObject(fromUnit)) {
            return time(cx, niceSource, option(fromUnit, "fromUnit"), option(fromUnit, "toUnit"))
        }
        val number = if (ScriptArgs.isNullish(niceSource)) Double.NaN else RhinoScriptRuntime.toNumber(niceSource)
        if (number.isNaN()) throw typeError(TIME, "source", "number / string / Date", source)
        val from = timeUnit(fromUnit, "fromUnit")
        val to = timeUnit(toUnit, "toUnit")
        return from.toNanos(number.toLong()).toDouble() / to.toNanos(1)
    }

    /** An integer byte count from a number or a string with a unit, in the loose mode of `cvt.bytes` (documented). */
    @ScriptFunction
    fun bytes(source: Any?, fromUnit: Any? = null): Any {
        if (!ScriptArgs.isNullish(fromUnit) && !ScriptArgs.isString(fromUnit)) throw typeError(BYTES, "fromUnit", "string", fromUnit)
        return ByteUnits.number(ByteUnits.integerBytesRequest(BYTES, source, fromUnit))
    }

    private fun timeUnit(unit: Any?, param: String): TimeUnit {
        if (ScriptArgs.isNullish(unit)) return TimeUnit.MILLISECONDS
        return when (val value = unwrap(unit)) {
            is TimeUnit -> value
            is CharSequence -> value.toString().let { name -> UNITS.firstOrNull { it.first.matches(name) }?.second }
            else -> null
        } ?: throw typeError(TIME, param, "TimeUnit / string", unit)
    }

    private fun option(options: Scriptable, name: String): Any? = ScriptableObject.getProperty(options, name).takeUnless { it === Scriptable.NOT_FOUND }

    private fun element(array: NativeArray, index: Int): Any? = array.get(index, array).let { if (it === Scriptable.NOT_FOUND) Undefined.instance else it }

    /** A coordinate: a number (or numeric value) truncated toward zero; anything without a number is a TypeError. */
    private fun coordinate(value: Any?, param: String): Int {
        val d = if (ScriptArgs.isNullish(value) || value === Scriptable.NOT_FOUND) Double.NaN else RhinoScriptRuntime.toNumber(unwrap(value))
        if (d.isNaN()) throw typeError(POINT, param, "number", if (value === Scriptable.NOT_FOUND) Undefined.instance else value)
        if (d.isInfinite() || d < Int.MIN_VALUE || d > Int.MAX_VALUE) {
            throw rangeError(POINT, ScriptMessages.argumentInteger(param, 32, RhinoScriptRuntime.numberToString(d, 10)))
        }
        return d.toInt()
    }

    private fun isPlainObject(value: Scriptable) = value !is Wrapper && value.className == "Object"

    private fun unwrap(value: Any?): Any? = if (value is Wrapper && value !is NativeArray) value.unwrap() else value

    private fun typeError(function: String, param: String, expected: String, value: Any?): RuntimeException =
        RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentType(param, expected, ScriptArgs.describe(value))))

    private fun rangeError(function: String, cause: String): RuntimeException = RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, cause))

}
