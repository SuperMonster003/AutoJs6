package org.autojs.autojs.runtime.api.augment.jsox

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.annotation.RhinoRuntimeFunctionWithThisObjInterface
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.ArrayExtensions.unshiftWith
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.NumberExtensions.string
import org.autojs.autojs.extension.StringExtensions.padEnd
import org.autojs.autojs.extension.StringExtensions.padStart
import org.autojs.autojs.extension.StringExtensions.toDoubleOrNaN
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.toFunctionName
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * JavaScript build-in object extension for the native Number object.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate", "UNUSED_PARAMETER")
class Numberx(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), JsBuildInObjectExtensible {

    override val key: String = javaClass.simpleName

    override val selfAssignmentProperties = listOf(
        // @Hint by SuperMonster003 on Jun 20, 2024.
        //  ! A tiny magic for generating this constant value. :)
        //  ! zh-CN: 生成这个常量的一个小魔法. [笑脸符号]
        //  !
        //  # val workdays = 5
        //  # val weekends = 2
        //  # val health = "Your health"
        //  # val evil = "Hard working only"
        //  # evil.split(
        //  #     Regex("[${health.lowercase()}]")
        //  # ).sumOf {
        //  #     it.takeIf { it.isNotEmpty() }
        //  #         ?.codePointAt(0)?.toDouble()
        //  #         ?: (996.0 / workdays / weekends - weekends)
        //  # }.roundToInt()
        "ICU" to 996 to AS_JSOX_STATIC,
    )

    override val selfAssignmentFunctions = listOf(
        ::ensureNumber.name to AS_JSOX_STATIC,
        ::ensureNumberLike.name to AS_JSOX_STATIC,
        ::ensureNumbersLike.name to AS_JSOX_STATIC,
        ::check.name to AS_JSOX_STATIC,
        ::clamp.name to AS_JSOX_PROTO,
        ::clampTo.name to AS_JSOX_PROTO,
        ::toFixedNum.name to AS_JSOX_PROTO,
        ::padStart.name to AS_JSOX_PROTO,
        ::padEnd.name to AS_JSOX_PROTO,
        ::parseFloat.name to (AS_JSOX_STATIC or AS_JSOX_GLOBAL),
        ::parsePercent.name to AS_JSOX_STATIC,
        ::parseRatio.name to AS_JSOX_STATIC,
        ::parseAny.name to AS_JSOX_STATIC,
    )

    override fun extendBuildInObject() {
        extendBuildInObjectInternal(scriptRuntime, this, "Number", Proto::class.java)
    }

    internal object Proto {

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun clamp(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): Double = unwrapArguments(args) {
            clamp(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun clampTo(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
            clampTo(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun toFixedNum(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): Double = ensureArgumentsLength(args, 1) {
            toFixedNum(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun padStart(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) {
            padStart(scriptRuntime, it.unshiftWith(thisObj))
        }

        @JvmStatic
        @RhinoRuntimeFunctionWithThisObjInterface
        fun padEnd(scriptRuntime: ScriptRuntime, thisObj: Scriptable?, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..2) {
            padEnd(scriptRuntime, it.unshiftWith(thisObj))
        }

    }

    companion object : FlexibleArray() {

        const val DEFAULT_PADDING_STRING = "0"

        private val compareOperators = mapOf<String, (Double, Double) -> Boolean>(
            "<" to { a, b -> a < b },
            "<=" to { a, b -> a <= b },
            ">" to { a, b -> a > b },
            ">=" to { a, b -> a >= b },
            "=" to { a, b -> a == b },
            "==" to { a, b -> a == b },
            "!=" to { a, b -> a != b },
            "<>" to { a, b -> a != b },
        )

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensureNumber(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsNotEmpty(args) {
            ensureNumberRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun ensureNumberRhino(vararg numbers: Any?) = numbers.forEach {
            require(it is Number) { "Argument must a be number instead of ${it.jsBrief()}" }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensureNumberLike(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
            when (it.size) {
                1 -> ensureNumberLikeRhino(it[0])
                2 -> ensureNumberLikeRhino(it[0], Context.toBoolean(it[1]))
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun ensureNumberLikeRhino(number: Any?, allowNaN: Boolean = false): Double {
            return ensureNumberLikeInternal(number, allowNaN)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun ensureNumbersLike(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsLengthInRange(args, 1..3) {
            when (it.size) {
                1 -> ensureNumbersLikeRhino(it[0])
                2 -> ensureNumbersLikeRhino(it[0], Context.toBoolean(it[1]))
                3 -> ensureNumbersLikeRhino(it[0], Context.toBoolean(it[1]), Context.toBoolean(it[2]))
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun ensureNumbersLikeRhino(numbers: Any?, allowNaN: Boolean = false, containsNaN: Boolean = false): NativeArray {
            return ensureNumbersLikeInternal(numbers, allowNaN, containsNaN).toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun check(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            checkRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun checkRhino(vararg inputs: Any?): Boolean {
            if (inputs.isEmpty()) return false
            if (inputs.size == 1) return inputs[0] is Number
            if (inputs.size == 2) return checkRhino(inputs[0], "==", inputs[1])
            for (i in 1 until inputs.size step 2) {
                val opr = inputs[i] as? String ?: throw RuntimeException("Arguments[$i] for Numberx.check must be a string as an operator.")
                if (opr !in compareOperators) throw RuntimeException("Arguments[$i] for Numberx.check must be an operator rather than $opr")
                val a = ensureNumberLikeInternal(inputs[i - 1], name = toFunctionName(Numberx::class, ::check, "num$${i - 1}"))
                val b = ensureNumberLikeInternal(inputs[i + 1], name = toFunctionName(Numberx::class, ::check, "num$${i + 1}"))
                if (!compareOperators[opr]!!(a, b)) return false
            }
            return true
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clamp(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtLeast(args, 1) {
            clampRhino(it[0], *it.sliceArray(1..<args.size))
        }

        @JvmStatic
        @RhinoFunctionBody
        fun clampRhino(num: Any?, vararg clamps: Any?): Double {
            val x = ensureNumberLikeInternal(num, name = toFunctionName(Numberx::class, ::clamp, "num"))
            val sortedClamps = ensureNumbersLikeInternal(
                RhinoUtils.flatten(clamps),
                name = toFunctionName(Numberx::class, ::clamp, "clamps")
            ).sorted()
            if (sortedClamps.isEmpty()) return x
            val min = sortedClamps.first()
            val max = sortedClamps.last()
            return when {
                x < min -> min
                x > max -> max
                else -> x
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clampTo(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 2..3) {
            val (num, range, cycle) = it
            clampToRhino(num, range, cycle)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun clampToRhino(num: Any?, range: Any?, cycle: Any? = null): Double {
            val x = ensureNumberLikeInternal(num, name = toFunctionName(Numberx::class, ::clampTo, "num"))
            val sortedRange = ensureNumbersLikeInternal(range).sorted()
            if (sortedRange.isEmpty()) return x
            val min = sortedRange.first()
            val max = sortedRange.last()
            val t = cycle?.let {
                ensureNumberLikeInternal(it, name = toFunctionName(Numberx::class, ::clampTo, "cycle"))
            } ?: (max - min)
            require(t > 0) { "Cycle must be a positive number." }
            return when {
                x < min -> x + ceil((min - x) / t) * t
                x > max -> x - ceil((x - max) / t) * t
                else -> x
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toFixedNum(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            val (num, fraction) = it
            toFixedNumRhino(num, fraction)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun toFixedNumRhino(num: Any?, fraction: Any?): Double {
            val x = parseAnyRhino(num)
            val frac = parseAnyRhino(fraction)
            return when {
                x.isNaN() || frac.isNaN() -> Double.NaN
                x.isInfinite() -> x
                else -> x.toBigDecimal().setScale(frac.roundToInt(), RoundingMode.HALF_UP).toDouble()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun padStart(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 2..3) {
            val (num, targetLength, pad) = it
            padStartRhino(num, targetLength, pad)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun padStartRhino(num: Any?, targetLength: Any?, pad: Any? = DEFAULT_PADDING_STRING): String {
            val niceTargetLength = Context.toNumber(targetLength)
            require(!niceTargetLength.isNaN() && niceTargetLength.isFinite()) { "Argument \"targetLength\" for Numberx.padStart must be a number" }
            val nicePad: String = when {
                pad.isJsNullish() -> DEFAULT_PADDING_STRING
                pad is Number -> pad.toDouble().string
                else -> pad.toString()
            }
            return parseAnyRhino(num).string.padStart(niceTargetLength.roundToInt(), nicePad)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun padEnd(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 2..3) {
            val (num, targetLength, pad) = it
            padEndRhino(num, targetLength, pad)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun padEndRhino(num: Any?, targetLength: Any?, pad: Any? = DEFAULT_PADDING_STRING): String {
            val niceTargetLength = Context.toNumber(targetLength)
            require(!niceTargetLength.isNaN() && niceTargetLength.isFinite()) { "Argument \"targetLength\" for Numberx.padEnd must be a number" }
            val nicePad: String = when {
                pad.isJsNullish() -> DEFAULT_PADDING_STRING
                pad is Number -> pad.toDouble().string
                else -> pad.toString()
            }
            return parseAnyRhino(num).string.padEnd(niceTargetLength.roundToInt(), nicePad)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun parseFloat(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..2) {
            val (string, radix) = it
            parseFloatRhinoRuntime(scriptRuntime, string, radix)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun parseFloatRhinoRuntime(scriptRuntime: ScriptRuntime, string: Any?, radix: Any? = null): Double {
            string ?: return Double.NaN
            var str = Context.toString(string)
            if (radix.isJsNullish()) return RhinoUtils.parseFloat(scriptRuntime, str)
            var niceRadix = ensureNumberLikeInternal(radix, true, name = toFunctionName(Numberx::class, ::parseFloat, "radix"))
            if (niceRadix.isNaN() || niceRadix.isInfinite()) niceRadix = 10.0

            // @Reference by SuperMonster003 on Nov 1, 2022.
            //  ! to https://stackoverflow.com/questions/37109968/how-to-convert-binary-fraction-to-decimal
            val stringParts = str.split('.')
            var fractionLength = 0
            if (stringParts.size > 1) {
                str = stringParts.joinToString("")
                fractionLength = stringParts[1].length
            }
            return try {
                RhinoUtils.parseInt(scriptRuntime, str, niceRadix.roundToInt()) / niceRadix.pow(fractionLength.toDouble())
            } catch (e: Exception) {
                Double.NaN
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun parsePercent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 1) {
            parsePercentRhino(it[0])
        }

        /**
         * @example
         * Numberx.parsePercent('1%'); // 0.01
         * Numberx.parsePercent('1%%'); // 0.0001
         */
        @JvmStatic
        @RhinoFunctionBody
        fun parsePercentRhino(percent: Any?): Double {
            if (percent.isJsNullish()) return Double.NaN
            val percentString = Context.toString(percent).trim()
            val matchResult = Regex("^([+-]?\\d+(?:\\.\\d+)?)(%*)$").matchEntire(percentString) ?: return Double.NaN
            val (number, percentSign) = matchResult.destructured
            return number.toDouble() / 100.0.pow(percentSign.length)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun parseRatio(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 1) {
            parseRatioRhino(it[0])
        }

        /**
         * @example
         * Numberx.parseRatio('3:2'); // 1.5
         */
        @JvmStatic
        @RhinoFunctionBody
        fun parseRatioRhino(ratio: Any?): Double {
            if (ratio.isJsNullish()) return Double.NaN
            val (x, y) = Context.toString(ratio).trim().split(':').map(String::trim)
            return x.toDoubleOrNaN() / y.toDoubleOrNaN()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun parseAny(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLength(args, 1) {
            parseAnyRhino(it[0])
        }

        @JvmStatic
        @RhinoFunctionBody
        fun parseAnyRhino(s: Any?): Double {
            if (s.isJsNullish()) return Double.NaN
            if (s is Number) return s.toDouble()
            val string = Context.toString(s).trim()
            return when {
                string.contains(':') -> parseRatioRhino(string)
                string.contains('%') -> parsePercentRhino(string)
                else -> Context.toNumber(string)
            }
        }

        private fun ensureNumberLikeInternal(
            number: Any?,
            allowNaN: Boolean = false,
            name: String = "Numberx.ensureNumberLike",
        ): Double {
            require(!number.isJsNullish()) { "Argument for $name must not be null or undefined." }
            if (number is Double && number.isNaN()) {
                require(allowNaN) { "Argument is NaN, which is not allowed for $name." }
                return Double.NaN
            }
            val x = parseAnyRhino(number)
            require(!x.isNaN()) { "Argument cannot be taken as a number for $name." }
            return x
        }

        private fun ensureNumbersLikeInternal(
            numbers: Any?,
            allowNaN: Boolean = false,
            containsNaN: Boolean = false,
            name: String = "Numberx.ensureNumbersLike",
        ): MutableList<Double> {
            require(numbers is List<*>) { "Argument \"numbers\" must be a list for $name" }
            val result = mutableListOf<Double>()
            numbers.forEachIndexed { index, value ->
                require(!value.isJsNullish()) { "Argument[$index] ${value.jsBrief()} for $name must not be null or undefined" }
                if (value is Double && value.isNaN()) {
                    require(allowNaN) { "Argument[$index] is NaN, which is not allowed for $name." }
                    if (containsNaN) result += Double.NaN
                    return@forEachIndexed
                }
                val x = parseAnyRhino(value)
                if (x.isNaN()) {
                    throw WrappedIllegalArgumentException("Argument[$index] ${value.jsBrief()} cannot be taken as a number for $name")
                }
                result += x
            }
            return result
        }

    }

}