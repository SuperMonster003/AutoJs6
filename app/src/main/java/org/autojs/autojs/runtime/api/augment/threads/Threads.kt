package org.autojs.autojs.runtime.api.augment.threads

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.looper.TimerThread
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceRunnable
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Synchronizer
import org.mozilla.javascript.Undefined
import java.util.concurrent.ThreadPoolExecutor

@Suppress("unused", "UNUSED_PARAMETER")
class Threads(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::interrupt.name,
        ::start.name,
        ::pool.name,
    )

    override val globalAssignmentFunctions = listOf(
        ::sync.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun interrupt(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined {
                if (it is TimerThread && it.isAlive) it.interrupt()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun start(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsOnlyOne(args) {
            try {
                scriptRuntime.threads.start(coerceRunnable(scriptRuntime, it))
            } catch (e: Exception) {
                if (!ScriptInterruptedException.causedByInterrupt(Throwable(e))) {
                    if (e.message?.endsWith(globalContext.getString(R.string.error_script_is_on_exiting)) != true) {
                        throw RuntimeException("${e}\n${e.stackTrace}")
                    }
                }
                UNDEFINED
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun pool(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ThreadPoolExecutor = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, NativeObject())
            scriptRuntime.threads.internalPool(
                opt.inquire("corePoolSize", ::coerceIntNumber, 0),
                opt.inquire("maxPoolSize", ::coerceIntNumber, 0),
                opt.inquire("keepAliveTime", ::coerceLongNumber, 60000L),
            )
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sync(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Synchronizer = ensureArgumentsLengthInRange(args, 1..2) {
            val (func, lock) = it
            require(func is BaseFunction) { "Argument func for global.sync must be a JavaScript Function" }
            Synchronizer(func, lock)
        }

    }

}
