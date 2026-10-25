package org.autojs.autojs.runtime.api.augment.timers

import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.modules.tasks.WorkManager
import org.autojs.autojs.runtime.api.modules.timers.SetIntervalExt
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceCallable
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Undefined

@Suppress("unused", "UNUSED_PARAMETER")
open class Timers(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::setIntervalExt.name,
        ::keepAlive.name,
        ::addDailyTask.name,
        ::addWeeklyTask.name,
        ::addDisposableTask.name,
        ::addIntentTask.name,
        ::getTimedTask.name,
        ::getIntentTask.name,
        ::removeIntentTask.name,
        ::removeTimedTask.name,
        ::queryIntentTasks.name,
        ::queryTimedTasks.name,
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

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setTimeout(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtLeast(args, 1) {
            val (callbackArg, intervalArg) = it
            val callback = coerceCallable(callbackArg)
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
            val callback = coerceCallable(callbackArg)
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
            val callback = coerceCallable(callbackArg)
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
        fun addDailyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = WorkManager.addDailyTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addWeeklyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = WorkManager.addWeeklyTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDisposableTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = WorkManager.addDisposableTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = WorkManager.addIntentTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = WorkManager.getTimedTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = WorkManager.getIntentTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean? = WorkManager.removeIntentTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean? = WorkManager.removeTimedTask(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryIntentTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = WorkManager.queryIntentTasks(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryTimedTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = WorkManager.queryTimedTasks(scriptRuntime, args)

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun keepAlive(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 1) {
            val (timeoutRaw) = it
            when (val timeout = coerceLongNumber(timeoutRaw, 0).coerceAtLeast(0)) {
                0L -> scriptRuntime.timers.setInterval(newBaseFunction(scriptRuntime.topLevelScope, "callback", {
                    /* Empty body. */
                }, NOT_CONSTRUCTABLE), 10_000)
                else -> scriptRuntime.timers.setTimeout(newBaseFunction(scriptRuntime.topLevelScope, "callback", {
                    /* Empty body. */
                }, NOT_CONSTRUCTABLE), timeout)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setIntervalExt(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..4) {
            when (it.size) {
                1 -> setIntervalExtRhino(scriptRuntime, coerceCallable(it[0]))
                2 -> setIntervalExtRhino(scriptRuntime, coerceCallable(it[0]), coerceNumber(it[1]))
                3 -> when {
                    it[2] is Callable -> setIntervalExtRhino(scriptRuntime, coerceCallable(it[0]), coerceNumber(it[1]), coerceCallable(it[2]), null)
                    else -> setIntervalExtRhino(scriptRuntime, coerceCallable(it[0]), coerceNumber(it[1]), coerceNumber(it[2]))
                }
                4 -> when {
                    it[2] is Callable -> setIntervalExtRhino(scriptRuntime, coerceCallable(it[0]), coerceNumber(it[1]), coerceCallable(it[2]), it[3] as? Callable)
                    else -> setIntervalExtRhino(scriptRuntime, coerceCallable(it[0]), coerceNumber(it[1]), coerceNumber(it[2]), it[3] as? Callable)
                }
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: Callable, interval: Double, timeoutCondition: Callable?, callback: Callable?): Double {
            return SetIntervalExt(scriptRuntime, listener, interval, 0.0, timeoutCondition, callback).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: Callable, interval: Double, timeout: Double, callback: Callable?): Double {
            return SetIntervalExt(scriptRuntime, listener, interval, timeout, callback = callback).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: Callable, interval: Double, timeout: Double): Double {
            return SetIntervalExt(scriptRuntime, listener, interval, timeout).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: Callable, interval: Double): Double {
            return SetIntervalExt(scriptRuntime, listener, interval).execute()
        }

        @JvmStatic
        @RhinoFunctionBody
        fun setIntervalExtRhino(scriptRuntime: ScriptRuntime, listener: Callable): Double {
            return SetIntervalExt(scriptRuntime, listener).execute()
        }

    }

}
