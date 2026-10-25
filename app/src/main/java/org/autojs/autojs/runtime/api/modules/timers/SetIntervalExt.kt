package org.autojs.autojs.runtime.api.modules.timers

import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope

internal class SetIntervalExt(
    private val scriptRuntime: ScriptRuntime,
    private val listener: Callable,
    interval: Double = DEFAULT_INTERVAL,
    timeout: Double = DEFAULT_TIMEOUT,
    private val timeoutCondition: Callable? = null,
    private val callback: Callable? = null,
) {
    private var mTimeoutResult: Any? = null

    internal var initTimestamp = System.currentTimeMillis()

    var interval: Double = if (interval > 0) interval else DEFAULT_INTERVAL
    var timeout: Double = if (timeout > 0) timeout else DEFAULT_TIMEOUT

    fun execute(): Double = scriptRuntime.timers.setTimeout(object : BaseFunction() {
        override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any {
            val that = Context.javaToJS(this@SetIntervalExt, scope) as Scriptable
            callListener(listener, cx, scope)
            when {
                !isTimedOut(scope) -> execute()
                callback != null -> RhinoUtils.callFunction(scriptRuntime, callback, scope, that, arrayOf(mTimeoutResult))
            }
            return RhinoUtils.UNDEFINED
        }
    }, interval.toLong())

    private fun isTimedOut(scope: VarScope): Boolean {
        val f = timeoutCondition ?: object : BaseFunction() {
            override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Boolean {
                return System.currentTimeMillis() - initTimestamp > timeout
            }
        }
        RhinoUtils.callFunction(scriptRuntime, f, scope, Context.javaToJS(this, scope) as Scriptable, emptyArray()).also {
            mTimeoutResult = it
        }
        return mTimeoutResult != false && !mTimeoutResult.isJsNullish()
    }

    companion object {

        private const val DEFAULT_INTERVAL = 200.0
        private const val DEFAULT_TIMEOUT = Double.MAX_VALUE

        internal fun callListener(listener: Callable, @Suppress("UNUSED_PARAMETER") cx: Context, scope: VarScope): Any? {
            return RhinoUtils.callFunction(listener, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray())
        }

    }

}
