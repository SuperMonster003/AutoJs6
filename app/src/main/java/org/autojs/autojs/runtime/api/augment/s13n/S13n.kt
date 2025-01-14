package org.autojs.autojs.runtime.api.augment.s13n

import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.isJsObject
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.colors.Colors
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_date_parseString
import org.autojs.autojs.util.TimeUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.util.concurrent.TimeUnit
import kotlin.Double.Companion.NaN
import kotlin.math.roundToInt
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.text.RegexOption.IGNORE_CASE
import android.graphics.Point as AndroidPoint
import org.opencv.core.Point as OpencvPoint

object S13n : Augmentable() {

    private const val KEY_NANO_SECONDS = "nanoSeconds"
    private const val KEY_MICRO_SECONDS = "microSeconds"
    private const val KEY_MILLI_SECONDS = "milliSeconds"
    private const val KEY_SECONDS = "seconds"
    private const val KEY_MINUTES = "minutes"
    private const val KEY_HOURS = "hours"
    private const val KEY_DAYS = "days"

    private const val FUNC_NAME_TO_NANOS = "toNanos"
    private const val FUNC_NAME_TO_MICROS = "toMicros"
    private const val FUNC_NAME_TO_MILLIS = "toMillis"
    private const val FUNC_NAME_TO_SECONDS = "toSeconds"
    private const val FUNC_NAME_TO_MINUTES = "toMinutes"
    private const val FUNC_NAME_TO_HOURS = "toHours"
    private const val FUNC_NAME_TO_DAYS = "toDays"

    private val timeUnitRex = mapOf(
        KEY_NANO_SECONDS to Regex("^NANO(SECONDS?|S)?$", IGNORE_CASE),
        KEY_MICRO_SECONDS to Regex("^MICRO(SECONDS?|S)?$", IGNORE_CASE),
        KEY_MILLI_SECONDS to Regex("^(MILLI(SECONDS?|S)?|MS)$", IGNORE_CASE),
        KEY_SECONDS to Regex("^(S(ECONDS?)?|SECS?)$", IGNORE_CASE),
        KEY_MINUTES to Regex("^(M(INUTES?)?|MINS?)$", IGNORE_CASE),
        KEY_HOURS to Regex("^(H(OURS?)?|HRS?)$", IGNORE_CASE),
        KEY_DAYS to Regex("^D(AYS?)?$", IGNORE_CASE)
    )

    override val selfAssignmentFunctions = listOf(
        ::color.name,
        ::throwable.name,
        ::point.name,
        ::time.name,
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun color(args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) { o ->
        runCatching {
            Colors.toIntRhino(o)
        }.getOrElse {
            throw WrappedIllegalArgumentException("Failed to make ${o.jsBrief()} a color being")
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun throwable(args: Array<out Any?>): Throwable = ensureArgumentsOnlyOne(args) { o ->
        when (o) {
            is Throwable -> o
            is String -> Exception(o)
            is NativeError -> (o.prop("rhinoException") as? Exception)
                ?: (o.prop("javaException") as? Exception)
                ?: Exception(coerceString(o.prop("message"), ""))
            else -> throw WrappedIllegalArgumentException("Failed to make ${o.jsBrief()} a throwable being")
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun point(args: Array<out Any?>): AndroidPoint = ensureArgumentsLengthInRange(args, 1..2) {
        val (o, other) = it
        when (o) {
            is AndroidPoint -> o
            is OpencvPoint -> AndroidPoint(o.x.toInt(), o.y.toInt())
            is NativeArray -> {
                val (x, y) = o
                AndroidPoint(coerceIntNumber(x), coerceIntNumber(y))
            }
            is Number -> {
                val x = coerceIntNumber(o)
                val tmp = Context.toNumber(other ?: NaN)
                val y = if (tmp.isNaN()) x else tmp.roundToInt()
                AndroidPoint(x, y)
            }
            is NativeObject -> {
                val x = coerceIntNumber(o.prop("x"))
                val y = coerceIntNumber(o.prop("y"))
                AndroidPoint(x, y)
            }
            else -> throw WrappedIllegalArgumentException("Failed to make ${o.jsBrief()} a point being")
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun time(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..3) { argList ->
        val (arg0, arg1, arg2) = argList
        var source = arg0
        when {
            source is Double && source.isNaN() -> NaN
            source is NativeDate -> time(arrayOf(source.date, arg1, arg2))
            source is String -> {
                source = source.replace(Regex("\\s+(?!\\d)|(^|\\D)\\s+"), "$1")
                // TODO by SuperMonster003 on Jan 14, 2024.
                //  ! Support s13n.time('00:02:10', unit).
                //  ! zh-CN: 支持 s13n.time('00:02:10', unit).
                val split = source.split(Regex("\\D+")).filter { it.isNotEmpty() }
                when (split.size) {
                    6 -> {
                        val (y, m, d, hh, mm, ss) = split.map { coerceIntNumber(it) }.toTypedArray()
                        val src = js_date_parseString("$y/$m/$d $hh:$mm:$ss")
                        time(arrayOf(src, arg1, arg2))
                    }
                    !in 1..6 -> NaN
                    else -> null
                }
            }
            else -> null
        } ?: when {
            arg1.isJsObject() -> {
                val options = arg1 as ScriptableObject
                time(arrayOf(source, options.prop("fromUnit"), options.prop("toUnit")))
            }
            else -> {
                val num = coerceNumber(source, NaN).also { require(!it.isNaN()) { "Failed to make ${arg0.jsBrief()} a number time being" } }
                val fromUnit = if (arg1.isJsNullish()) TimeUnit.MILLISECONDS else arg1
                when {
                    fromUnit.isJsNullish() -> num
                    else -> {
                        val toUnit = if (arg2.isJsNullish()) TimeUnit.MILLISECONDS else arg2
                        val conversionFuncName = getTimeUnitConversionFunctionName(toUnit)
                        val function = TimeUtils::class.declaredMemberFunctions.find { it.name == conversionFuncName } ?: throw WrappedIllegalArgumentException(
                            "Conversion function name $conversionFuncName is not found in ${TimeUtils::class.java.name}"
                        )
                        coerceNumber(function.call(TimeUtils, getTimeUnitSourceObject(fromUnit), num.toLong()), NaN)
                    }
                }
            }
        }
    }

    private fun getTimeUnitSourceObject(unit: Any?): TimeUnit = when (unit) {
        is TimeUnit -> unit
        is String -> timeUnitRex.entries.find { it.value.matches(unit) }?.let {
            when (it.key) {
                KEY_NANO_SECONDS -> TimeUnit.NANOSECONDS
                KEY_MICRO_SECONDS -> TimeUnit.MICROSECONDS
                KEY_MILLI_SECONDS -> TimeUnit.MILLISECONDS
                KEY_SECONDS -> TimeUnit.SECONDS
                KEY_MINUTES -> TimeUnit.MINUTES
                KEY_HOURS -> TimeUnit.HOURS
                KEY_DAYS -> TimeUnit.DAYS
                else -> null
            }
        }
        else -> null
    } ?: throw WrappedIllegalArgumentException("Failed to make ${unit.jsBrief()} a time unit being")

    private fun getTimeUnitConversionFunctionName(unit: Any?): String {
        return when (unit) {
            is TimeUnit -> when (unit) {
                TimeUnit.NANOSECONDS -> FUNC_NAME_TO_NANOS
                TimeUnit.MICROSECONDS -> FUNC_NAME_TO_MICROS
                TimeUnit.MILLISECONDS -> FUNC_NAME_TO_MILLIS
                TimeUnit.SECONDS -> FUNC_NAME_TO_SECONDS
                TimeUnit.MINUTES -> FUNC_NAME_TO_MINUTES
                TimeUnit.HOURS -> FUNC_NAME_TO_HOURS
                TimeUnit.DAYS -> FUNC_NAME_TO_DAYS
            }
            is String -> timeUnitRex.entries.find { it.value.matches(unit) }?.let {
                when (it.key) {
                    KEY_NANO_SECONDS -> FUNC_NAME_TO_NANOS
                    KEY_MICRO_SECONDS -> FUNC_NAME_TO_MICROS
                    KEY_MILLI_SECONDS -> FUNC_NAME_TO_MILLIS
                    KEY_SECONDS -> FUNC_NAME_TO_SECONDS
                    KEY_MINUTES -> FUNC_NAME_TO_MINUTES
                    KEY_HOURS -> FUNC_NAME_TO_HOURS
                    KEY_DAYS -> FUNC_NAME_TO_DAYS
                    else -> null
                }
            }
            else -> null
        } ?: throw WrappedIllegalArgumentException("Failed to make ${unit.jsBrief()} a time unit conversion function name")
    }

}