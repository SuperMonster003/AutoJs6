package org.autojs.autojs.runtime.api.augment.timers

import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceFunction
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Undefined

@Suppress("unused", "UNUSED_PARAMETER")
open class Timers(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::setIntervalExt.name,
        ::keepAlive.name,
    )

    override val globalAssignmentFunctions = listOf(
        ::setTimeout.name,
        ::setInterval.name,
        ::setImmediate.name,
        ::clearTimeout.name,
        ::clearInterval.name,
        ::clearImmediate.name,
        ::loop.name,
        ::keepAlive.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTimeout(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtLeast(args, 1) {
            val (callbackArg, intervalArg) = it
            val callback = coerceFunction(callbackArg)
            val interval = coerceLongNumber(intervalArg, 1L)
            when (it.size) {
                1 -> scriptRuntime.timers.setTimeout(callback)
                2 -> scriptRuntime.timers.setTimeout(callback, interval)
                else -> scriptRuntime.timers.setTimeout(callback, interval, *it.sliceArray(2 until it.size))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearTimeout(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.timers.clearTimeout(coerceNumber(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setInterval(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtLeast(args, 1) {
            val (callbackArg, intervalArg) = it
            val callback = coerceFunction(callbackArg)
            val interval = coerceLongNumber(intervalArg, 1L)
            when (it.size) {
                1 -> scriptRuntime.timers.setInterval(callback)
                2 -> scriptRuntime.timers.setInterval(callback, interval)
                else -> scriptRuntime.timers.setInterval(callback, interval, *it.sliceArray(2 until it.size))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearInterval(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.timers.clearInterval(coerceNumber(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setImmediate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtLeast(args, 1) {
            val (callbackArg) = it
            val callback = coerceFunction(callbackArg)
            when (it.size) {
                1 -> scriptRuntime.timers.setImmediate(callback)
                else -> scriptRuntime.timers.setImmediate(callback, *it.sliceArray(1 until it.size))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun clearImmediate(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            scriptRuntime.timers.clearImmediate(coerceNumber(it))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun loop(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            AutoJs.instance.globalConsole.warn("")
            AutoJs.instance.globalConsole.warn(AutoJs.instance.applicationContext.getString(R.string.error_abandoned_method, "loop"))
            AutoJs.instance.globalConsole.warn("")
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun keepAlive(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 1) {
            val (internal) = it
            setInterval(scriptRuntime, arrayOf(BaseFunction(), coerceNumber(internal, 10_000)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setIntervalExt(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..4) {
            when (it.size) {
                1 -> setIntervalExtRhino(scriptRuntime, coerceFunction(it[0]))
                2 -> setIntervalExtRhino(scriptRuntime, coerceFunction(it[0]), coerceNumber(it[1]))
                3 -> when {
                    it[2] is BaseFunction -> setIntervalExtRhino(scriptRuntime, coerceFunction(it[0]), coerceNumber(it[1]), coerceFunction(it[2]), null)
                    else -> setIntervalExtRhino(scriptRuntime, coerceFunction(it[0]), coerceNumber(it[1]), coerceNumber(it[2]))
                }
                4 -> when {
                    it[2] is BaseFunction -> setIntervalExtRhino(scriptRuntime, coerceFunction(it[0]), coerceNumber(it[1]), coerceFunction(it[2]), it[3] as? BaseFunction)
                    else -> setIntervalExtRhino(scriptRuntime, coerceFunction(it[0]), coerceNumber(it[1]), coerceNumber(it[2]), it[3] as? BaseFunction)
                }
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: BaseFunction, interval: Double, timeoutCondition: BaseFunction?, callback: BaseFunction?): Double {
            return SetIntervalExt(scriptRuntime, listener, interval, 0.0, timeoutCondition, callback).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: BaseFunction, interval: Double, timeout: Double, callback: BaseFunction?): Double {
            return SetIntervalExt(scriptRuntime, listener, interval, timeout, callback).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: BaseFunction, interval: Double, timeout: Double): Double {
            return SetIntervalExt(scriptRuntime, listener, interval, timeout).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: BaseFunction, interval: Double): Double {
            return SetIntervalExt(scriptRuntime, listener, interval).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: BaseFunction): Double {
            return SetIntervalExt(scriptRuntime, listener).execute()
        }

    }

}
