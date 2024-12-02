package org.autojs.autojs.runtime.api.augment.timers

import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

internal class SetIntervalExt(
    private val scriptRuntime: ScriptRuntime,
    private val listener: BaseFunction,
    interval: Double = DEFAULT_INTERVAL,
    timeout: Double = DEFAULT_TIMEOUT,
    private val timeoutCondition: BaseFunction? = null,
    private val callback: BaseFunction? = null,
) {
    private var mTimeoutResult: Any? = null

    internal var initTimestamp = System.currentTimeMillis()

    var interval: Double = if (interval > 0) interval else DEFAULT_INTERVAL
    var timeout: Double = if (timeout > 0) timeout else DEFAULT_TIMEOUT

    fun execute(): Double = scriptRuntime.timers.setTimeout(object : BaseFunction() {
        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any {
            val that = Context.javaToJS(this@SetIntervalExt, scope) as Scriptable
            listener.call(cx, scope, that, arrayOf())
            when {
                !isTimedOut(scope) -> execute()
                callback is BaseFunction -> callback.call(cx, scope, that, arrayOf(mTimeoutResult))
            }
            return RhinoUtils.UNDEFINED
        }
    }, interval.toLong())

    private fun isTimedOut(scope: Scriptable): Boolean {
        val f = timeoutCondition ?: object : BaseFunction() {
            override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Boolean {
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

    }

}