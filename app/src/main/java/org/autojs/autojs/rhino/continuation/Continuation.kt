package org.autojs.autojs.rhino.continuation

import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Scriptable

class Continuation(val context: AutoJsContext, val scope: Scriptable, private val mTimer: Timer) {

    private val mThread: Thread = Thread.currentThread()

    var pending: ContinuationPending? = null
        private set

    class Result(@JvmField val result: Any?, @JvmField val error: Any?) {

        companion object {

            @JvmStatic
            fun success(result: Any?) = Result(result, null)

            @JvmStatic
            fun failure(error: Any?) = Result(null, error)

            @JvmStatic
            fun handle(o: Any?) = when (o) {
                is Result -> o.error?.also {
                    ScriptRuntime.popException(Context.toString(it))
                } ?: o.result
                else -> o
            }

            @JvmStatic
            fun getOrThrow(o: Any?) = when (o) {
                is Result -> o.error?.also {
                    when (it) {
                        is Throwable -> throw it
                        else -> throw RuntimeException(Context.toString(it))
                    }
                } ?: o.result
                else -> o
            }

        }

    }

    fun suspend(): Result {
        pending?.let {
            throw IllegalStateException(
                str(R.string.error_continuation_suspend_called_more_than_once)
            )
        }
        context.captureContinuation().let {
            pending = it
            throw it
        }
    }

    fun resumeWith(result: Result) {
        val continuation = pending?.continuation ?: throw IllegalStateException(
            str(R.string.error_continuation_resume_called_without_suspend)
        )
        if (mThread == Thread.currentThread()) {
            context.resumeContinuation(continuation, scope, Result.handle(result))
        } else {
            mTimer.postDelayed({
                context.resumeContinuation(continuation, scope, Result.handle(result))
            }, 0)
        }
    }

    companion object {

        fun create(runtime: ScriptRuntime, scope: Scriptable): Continuation {
            val context = Context.getCurrentContext() as AutoJsContext
            val timers = runtime.timers
            return Continuation(context, scope, timers.timerForCurrentThread ?: timers.mainTimer)
        }

    }

}
