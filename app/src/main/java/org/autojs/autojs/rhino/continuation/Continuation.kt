package org.autojs.autojs.rhino.continuation

import org.autojs.autojs.core.looper.Timer
import org.autojs.autojs.rhino.AutoJsContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.WrappedException

class Continuation internal constructor(val context: AutoJsContext, val scope: VarScope, private val post: (Runnable) -> Unit) {

    constructor(context: AutoJsContext, scope: VarScope, timer: Timer) : this(context, scope, { r -> timer.postDelayed(r, 0) })

    private val mThread: Thread = Thread.currentThread()

    var pending: ContinuationPending? = null
        private set

    /** The suspension failed and the caller got that error: nothing is left to resume. zh-CN: 挂起已失败, 调用方已收到该错误: 没有可恢复的内容. */
    @Volatile
    private var failed = false

    class Result(@JvmField val result: Any?, @JvmField val error: Any?) {

        companion object {

            @JvmStatic
            fun success(result: Any?) = Result(result, null)

            @JvmStatic
            fun failure(error: Any?) = Result(null, error)

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
        val captured = try {
            context.captureContinuation()
        } catch (e: RuntimeException) {
            failed = true
            throw e
        }
        pending = captured
        throw captured
    }

    /**
     * Resumes the coroutine with [result]. On another thread the coroutine may not have suspended yet (a thread started
     * before `await()`), so the resume is posted to the timer of the coroutine's thread and takes [pending] there, after
     * the script has suspended (docs/augment/ROADMAP.md Q98); it formerly took [pending] on the calling thread and threw
     * when that thread came first. A resume without any suspension still fails, on the timer then, but after a suspension that
     * failed the script already has the error (`await()` in an async function), and it is not repeated here.
     * zh-CN: 以 [result] 恢复协程. 在其他线程上调用时协程可能尚未挂起 (在 await() 之前启动的线程), 因此恢复操作投递到协程所在线程的 timer, 在脚本挂起之后
     * 再于该处取 [pending] (docs/augment/ROADMAP.md Q98); 原先在调用线程上先取 [pending], 该线程先到时即抛出. 始终没有挂起的恢复仍会失败, 此时在 timer 上;
     * 但挂起失败时脚本已收到该错误 (在 async 函数中调用 await()), 这里不再重复.
     */
    fun resumeWith(result: Result) {
        if (mThread == Thread.currentThread()) {
            resume(result)
        } else {
            post(Runnable { resume(result) })
        }
    }

    /**
     * Restores the script at its suspended call, which returns the result or throws the error there, so that a `try` around
     * `await()` catches it (docs/api-coverage/ROADMAP.md Q205). Rhino resumed a continuation with a value only, and the Java
     * stack of [suspend] is gone by now: since 6.3.4, when throwing crashed the app on the main looper, the error was the value
     * of `await()` after a toast. What the script does not catch now exits the script (`Timer.runCallback`).
     * zh-CN: 在挂起的调用处恢复脚本, 该调用返回结果或抛出错误, 因此 await() 外的 try 能捕获它 (docs/api-coverage/ROADMAP.md Q205). Rhino 原本只能以值恢复续体,
     * 而此时 [suspend] 的 Java 栈已不存在: 6.3.4 起 (当时抛出会在主 Looper 上使应用崩溃), 错误在弹出提示后成为 await() 的返回值. 现在脚本未捕获的错误会结束脚本 (`Timer.runCallback`).
     */
    private fun resume(result: Result) {
        if (failed) return
        val continuation = pending?.continuation ?: throw IllegalStateException(
            str(R.string.error_continuation_resume_called_without_suspend)
        )
        when (val error = result.error) {
            null -> context.resumeContinuation(continuation, scope, result.result)
            is RhinoException -> context.resumeContinuationWithError(continuation, scope, error)
            is Throwable -> context.resumeContinuationWithError(continuation, scope, WrappedException(error))
            else -> context.resumeContinuationWithError(continuation, scope, error)
        }
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
