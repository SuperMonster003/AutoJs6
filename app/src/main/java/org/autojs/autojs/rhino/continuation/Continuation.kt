package org.autojs.autojs.rhino.continuation

import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope

class Continuation internal constructor(val context: AutoJsContext, val scope: VarScope, private val post: (Runnable) -> Unit) {

    constructor(context: AutoJsContext, scope: VarScope, timer: Timer) : this(context, scope, { r -> timer.postDelayed(r, 0) })

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

    /**
     * Resumes the coroutine with [result]. On another thread the coroutine may not have suspended yet (a thread started
     * before `await()`), so the resume is posted to the timer of the coroutine's thread and takes [pending] there, after
     * the script has suspended (docs/augment/ROADMAP.md Q98); it formerly took [pending] on the calling thread and threw
     * when that thread came first. A resume without any suspension still fails, on the timer then.
     * zh-CN: 以 [result] 恢复协程. 在其他线程上调用时协程可能尚未挂起 (在 await() 之前启动的线程), 因此恢复操作投递到协程所在线程的 timer, 在脚本挂起之后
     * 再于该处取 [pending] (docs/augment/ROADMAP.md Q98); 原先在调用线程上先取 [pending], 该线程先到时即抛出. 始终没有挂起的恢复仍会失败, 此时在 timer 上.
     */
    fun resumeWith(result: Result) {
        if (mThread == Thread.currentThread()) {
            resume(result)
        } else {
            post(Runnable { resume(result) })
        }
    }

    private fun resume(result: Result) {
        val continuation = pending?.continuation ?: throw IllegalStateException(
            str(R.string.error_continuation_resume_called_without_suspend)
        )
        context.resumeContinuation(continuation, scope, Result.handle(result))
    }

    companion object {

        fun create(runtime: ScriptRuntime, scope: VarScope): Continuation {
            val context = Context.getCurrentContext() as AutoJsContext
            val timers = runtime.timers
            return Continuation(context, scope, timers.timerForCurrentThread ?: timers.mainTimer)
        }

        fun create(runtime: ScriptRuntime, scope: Scriptable): Continuation = create(runtime, scope.parentScope)

    }

}
