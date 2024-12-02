package org.autojs.autojs.runtime.api.augment.jsox

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.extension.AnyExtensions.isJsArray
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.flatten
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.opencv.core.Point
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random
import android.graphics.Point as AndroidPoint
import android.graphics.Rect as AndroidRect

/**
 * JavaScript build-in object extension for the native Math object.
 */
class Mathx(private val scriptRuntime: ScriptRuntime) : Augmentable(), JsBuildInObjectExtensible {

    override val key: String = javaClass.simpleName

    @Suppress("DEPRECATION")
    override val selfAssignmentFunctions = listOf(
        ::randInt.name to AS_JSOX_STATIC,
        ::randomInt.name to (AS_JSOX_STATIC or AS_GLOBAL),
        ::randFloat.name to AS_JSOX_STATIC,
        ::randomFloat.name to (AS_JSOX_STATIC or AS_GLOBAL),
        ::random.name to AS_JSOX_STATIC,
        ::sum.name to AS_JSOX_STATIC,
        ::mean.name to AS_JSOX_STATIC,
        ::avg.name to AS_JSOX_STATIC,
        ::median.name to AS_JSOX_STATIC,
        ::`var`.name to AS_JSOX_STATIC,
        ::std.name to AS_JSOX_STATIC,
        ::cv.name to AS_JSOX_STATIC,
        ::mode.name to AS_JSOX_STATIC,
        ::dist.name to AS_JSOX_STATIC,
        ::logMn.name to AS_JSOX_STATIC,
        ::floorLog.name to AS_JSOX_STATIC,
        ::ceilLog.name to AS_JSOX_STATIC,
        ::roundLog.name to AS_JSOX_STATIC,
        ::floorPow.name to AS_JSOX_STATIC,
        ::ceilPow.name to AS_JSOX_STATIC,
        ::roundPow.name to AS_JSOX_STATIC,
        ::max.name,
        ::maxi.name to (AS_JSOX_STATIC or AS_IGNORED),
        ::min.name,
        ::mini.name to (AS_JSOX_STATIC or AS_IGNORED),
    )

    override fun extendBuildInObject() {
        extendBuildInObjectInternal(scriptRuntime, this, "Math")
    }

    companion object : FlexibleArray() {

        @Deprecated("Deprecated in Java", ReplaceWith("randomInt"))
        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun randInt(args: Array<out Any?>): Int = randomInt(args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun randomInt(args: Array<out Any?>): Int = unwrapArguments(args) {
            randomIntRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun randomIntRhino(vararg args: Any?): Int {
            val argList = flatten(args)
            return when {
                // 如果无参数, 返回 0 或 1
                argList.isEmpty() -> {
                    randomIntRhino(0, 1)
                }
                // 如果只有一个参数, 根据其正负分别处理
                argList.size == 1 -> coerceNumber(argList[0]).let { num ->
                    when {
                        num == num.toInt().toDouble() -> num.toInt()
                        else -> when (Random.nextBoolean()) {
                            true -> floor(num).toInt()
                            else -> ceil(num).toInt()
                        }
                    }
                }
                // 如果有多个参数, 计算它们的最小值和最大值
                else -> {
                    val ranges = argList.map { coerceNumber(it) }
                    val min = ranges.minOrNull()?.let { ceil(it).toInt() } ?: throw WrappedIllegalArgumentException("Invalid range: no minimum value")
                    val max = ranges.maxOrNull()?.let { floor(it).toInt() } ?: throw WrappedIllegalArgumentException("Invalid range: no maximum value")

                    // 检查 min 和 max 是否有有效的整数范围
                    if (min > max) {
                        throw WrappedIllegalArgumentException("Invalid range: no integers within [${ranges.min()}, ${ranges.max()}]")
                    }

                    // 在 [min, max] 区间内生成随机整数
                    floor(Math.random() * (max - min + 1)).toInt() + min
                }
            }
        }

        @Deprecated("Deprecated in Java", ReplaceWith("randomFloat"))
        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun randFloat(args: Array<out Any?>): Double = randomFloat(args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun random(args: Array<out Any?>): Double = randomFloat(args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun randomFloat(args: Array<out Any?>): Double = unwrapArguments(args) {
            randomFloatRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun randomFloatRhino(vararg args: Any?): Double {
            val argList = flatten(args)
            return when {
                // 如果无参数, 返回 Math.random()
                argList.isEmpty() -> {
                    Math.random()
                }
                // 如果只有一个参数, 根据其正负分别处理
                argList.size == 1 -> coerceNumber(argList[0]).let { num ->
                    val min = floor(num)
                    var max = ceil(num)
                    if (min == max) max += 1
                    randomFloatRhino(num, max)
                }
                // 如果有多个参数, 计算它们的最小值和最大值
                else -> {
                    val ranges = argList.map { coerceNumber(it) }
                    val min = ranges.minOrNull() ?: throw WrappedIllegalArgumentException("Invalid range: $argList has no minimum value")
                    val max = ranges.maxOrNull() ?: throw WrappedIllegalArgumentException("Invalid range: $argList has no maximum value")

                    // 在 [min, max] 区间内生成随机浮点数
                    Math.random() * (max - min) + min
                }
            }
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun sum(args: Array<out Any?>): Double = unwrapArguments(args) {
            sumRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun sumRhino(vararg args: Any?): Double = calcBy(::sumArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun mean(args: Array<out Any?>): Double = unwrapArguments(args) {
            meanRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun meanRhino(vararg args: Any?): Double = calcBy(::meanArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun avg(args: Array<out Any?>): Double = mean(args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun median(args: Array<out Any?>): Double = unwrapArguments(args) {
            medianRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun medianRhino(vararg args: Any?): Double = calcBy(::medianArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun `var`(args: Array<out Any?>): Double = unwrapArguments(args) {
            varRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun varRhino(vararg args: Any?): Double = calcBy(::varArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun std(args: Array<out Any?>): Double = unwrapArguments(args) {
            stdRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun stdRhino(vararg args: Any?): Double = calcBy(::stdArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun cv(args: Array<out Any?>): Double = unwrapArguments(args) {
            cvRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun cvRhino(vararg args: Any?): Double = calcBy(::cvArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun mode(args: Array<out Any?>): Double = unwrapArguments(args) {
            modeRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun modeRhino(vararg args: Any?): Double = calcBy(::modeArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun max(args: Array<out Any?>): Double = unwrapArguments(args) {
            maxRhino(*it)
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun maxi(args: Array<out Any?>): Double = max(args)

        @JvmStatic
        @RhinoFunctionBody
        fun maxRhino(vararg args: Any?): Double = calcBy(::maxArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun min(args: Array<out Any?>): Double = unwrapArguments(args) {
            minRhino(*it)
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun mini(args: Array<out Any?>): Double = min(args)

        @JvmStatic
        @RhinoFunctionBody
        fun minRhino(vararg args: Any?): Double = calcBy(::minArgumentsByFlatten, args)

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun dist(args: Array<out Any?>): Double = unwrapArguments(args) {
            distRhino(*it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun distRhino(vararg args: Any?): Double = when (args.size) {
            0 -> Double.NaN
            1 -> {
                val (it) = args
                when (it) {
                    is AndroidRect -> distRhino(AndroidPoint(it.left, it.top), AndroidPoint(it.right, it.bottom))
                    else -> throw WrappedIllegalArgumentException("Argument[0] ${it.jsBrief()} is not supported for Mathx.dist")
                }
            }
            2 -> {
                val (a, b) = args
                when (a) {
                    is AndroidRect -> distRhino(AndroidPoint(a.left, a.top), AndroidPoint(a.right, a.bottom), b)
                    else -> {
                        val ptA = toPoint(a) ?: throw WrappedIllegalArgumentException("Argument[0] ${a.jsBrief()} cannot be taken as a valid Point")
                        val ptB = toPoint(b) ?: throw WrappedIllegalArgumentException("Argument[1] ${b.jsBrief()} cannot be taken as a valid Point")
                        sqrt((ptA.x - ptB.x).pow(2) + (ptA.y - ptB.y).pow(2))
                    }
                }
            }
            3 -> {
                val (a, b, frac) = args
                when {
                    a is AndroidRect && b is AndroidRect -> {
                        val centerPointA = Point(a.exactCenterX().toDouble(), a.exactCenterY().toDouble())
                        val centerPointB = Point(b.exactCenterX().toDouble(), b.exactCenterY().toDouble())
                        distRhino(centerPointA, centerPointB, frac)
                    }
                    else -> {
                        val ptA = toPoint(a) ?: throw WrappedIllegalArgumentException("Argument[0] ${a.jsBrief()} cannot be taken as a valid Point")
                        val ptB = toPoint(b) ?: throw WrappedIllegalArgumentException("Argument[1] ${b.jsBrief()} cannot be taken as a valid Point")
                        val res = sqrt((ptA.x - ptB.x).pow(2) + (ptA.y - ptB.y).pow(2))
                        Numberx.toFixedNumRhino(res, frac)
                    }
                }
            }
            else -> throw WrappedIllegalArgumentException("Arguments length (${args.size}) is unacceptable for Mathx.dist")
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun logMn(args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 2..3) {
            when (it.size) {
                2 -> logMnRhino(coerceNumber(it[0]), coerceNumber(it[1]))
                3 -> logMnRhino(coerceNumber(it[0]), coerceNumber(it[1]), it[2])
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun logMnRhino(base: Double, antilogarithm: Double, fraction: Any? = null): Double {
            val frac = coerceNumber(fraction, 13.0)
            val result = ln(antilogarithm) / ln(base)
            return Numberx.toFixedNumRhino(result, frac)
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun floorLog(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            logarithmBy(::floorLog.name, it) { base, antilogarithm ->
                floor(logMnRhino(base, antilogarithm))
            }
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun ceilLog(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            logarithmBy(::ceilLog.name, it) { base, antilogarithm ->
                ceil(logMnRhino(base, antilogarithm))
            }
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun roundLog(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            logarithmBy(::roundLog.name, it) { base, antilogarithm ->
                logMnRhino(base, antilogarithm).roundToLong().toDouble()
            }
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun floorPow(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            logarithmBy(::floorPow.name, it) { base, antilogarithm ->
                base.pow(floor(logMnRhino(base, antilogarithm)))
            }
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun ceilPow(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            logarithmBy(::ceilPow.name, it) { base, antilogarithm ->
                base.pow(ceil(logMnRhino(base, antilogarithm)))
            }
        }

        @JvmStatic
        @RhinoSingletonFunctionInterface
        fun roundPow(args: Array<out Any?>): Double = ensureArgumentsLength(args, 2) {
            logarithmBy(::roundPow.name, it) { base, antilogarithm ->
                base.pow(logMnRhino(base, antilogarithm).roundToLong().toDouble())
            }
        }

        private fun calcBy(f: (args: Array<out Any?>) -> Double, vararg args: Any?): Double = when {
            args.isEmpty() -> Double.NaN
            args.size >= 2 -> {
                val fraction = if (args.last().isJsArray()) Double.NaN else coerceNumber(args.last(), Double.NaN)
                if (!fraction.isNaN() && args.dropLast(1).all { it.isJsArray() }) {
                    Numberx.toFixedNumRhino(f(args.sliceArray(0..args.size - 2)), fraction)
                } else {
                    f(args)
                }
            }
            else -> f(args)
        }

        private fun logarithmBy(name: String, args: Array<out Any?>, f: (base: Double, antilogarithm: Double) -> Double): Double {
            val (baseArg, antilogarithmArg) = args
            val base = coerceNumber(baseArg, Double.NaN).takeUnless { o -> o.isNaN() } ?: throw WrappedIllegalArgumentException(
                "Argument base ${baseArg.jsBrief()} cannot be taken as a valid base for Mathx.$name",
            )
            val antilogarithm = coerceNumber(antilogarithmArg, Double.NaN).takeUnless { o -> o.isNaN() } ?: throw WrappedIllegalArgumentException(
                "Argument base ${antilogarithmArg.jsBrief()} cannot be taken as a valid antilogarithm for Mathx.$name",
            )
            return f(base, antilogarithm)
        }

        private fun sumArgumentsByFlatten(args: Array<out Any?>) = flatten(args).sumOf { coerceNumber(it, Double.NaN) }

        private fun meanArgumentsByFlatten(args: Array<out Any?>) = flatten(args).let { a -> a.sumOf { coerceNumber(it, Double.NaN) } / a.size }

        private fun medianArgumentsByFlatten(args: Array<out Any?>): Double {
            val nums = flatten(args).map { coerceNumber(it, Double.NaN) }
            if (nums.any { it.isNaN() }) return Double.NaN
            val sorted = nums.sorted()
            return when {
                sorted.size % 2 != 0 -> sorted[sorted.size / 2]
                else -> (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
            }
        }

        private fun varArgumentsByFlatten(args: Array<out Any?>): Double {
            val nums = flatten(args).map { coerceNumber(it, Double.NaN) }
            if (nums.any { it.isNaN() }) return Double.NaN

            val mean = meanArgumentsByFlatten(args)
            val variance = nums.fold(0.0) { acc, i -> acc + (i - mean).pow(2) } / nums.size

            return variance
        }

        private fun stdArgumentsByFlatten(args: Array<out Any?>): Double {
            return sqrt(varArgumentsByFlatten(args))
        }

        private fun cvArgumentsByFlatten(args: Array<out Any?>): Double {
            return stdArgumentsByFlatten(args) / meanArgumentsByFlatten(args)
        }

        private fun modeArgumentsByFlatten(args: Array<out Any?>): Double {
            val nums = flatten(args).map { coerceNumber(it, Double.NaN) }
            if (nums.any { it.isNaN() }) return Double.NaN
            val frequencyMap = mutableMapOf<Double, Int>()
            for (num in nums) {
                frequencyMap[num] = frequencyMap.getOrDefault(num, 0) + 1
            }
            val maxFreq = frequencyMap.values.maxOrNull() ?: 0
            val modes = frequencyMap.filterValues { it == maxFreq }.keys
            // Returns the first mode found. If there is a need for multiple modes, code here can be adjusted.
            return modes.first()
        }

        private fun maxArgumentsByFlatten(args: Array<out Any?>): Double {
            return flatten(args).maxOfOrNull { coerceNumber(it, Double.NaN) } ?: Double.NaN
        }

        private fun minArgumentsByFlatten(args: Array<out Any?>): Double {
            return flatten(args).minOfOrNull { coerceNumber(it, Double.NaN) } ?: Double.NaN
        }

        private fun toPoint(o: Any?): Point? {
            return when (o) {
                is NativeArray -> {
                    require(o.length == 2L) { "Points array must be length of 2" }
                    val x = coerceNumber(o[0], Double.NaN).takeUnless { it.isNaN() } ?: return null
                    val y = coerceNumber(o[1], Double.NaN).takeUnless { it.isNaN() } ?: return null
                    Point(x, y)
                }
                is NativeObject -> {
                    val x = coerceNumber(o.prop("x"), Double.NaN).takeUnless { it.isNaN() } ?: return null
                    val y = coerceNumber(o.prop("y"), Double.NaN).takeUnless { it.isNaN() } ?: return null
                    Point(x, y)
                }
                is AndroidRect -> {
                    // @Hint by SuperMonster003 on Oct 28, 2022.
                    //  ! centerX and centerY will lose the precision.
                    //  ! zh-CN: centerX 和 centerY 将丢失精度.
                    Point(o.exactCenterX().toDouble(), o.exactCenterY().toDouble())
                }
                is AndroidPoint -> {
                    Point(o.x.toDouble(), o.y.toDouble())
                }
                else -> o as? Point
            }
        }

    }

}