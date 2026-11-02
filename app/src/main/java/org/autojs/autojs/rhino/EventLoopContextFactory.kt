package org.autojs.autojs.rhino

import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Script
import org.mozilla.javascript.VarScope

/**
 * Runs the microtask checkpoint when the stack of a top call is empty (roadmap P9.5 of Rhino-For-AutoJs6), whatever made the
 * call: the main script, a timer callback, an event or an interface callback, on any thread. A coroutine that suspends also
 * ends its turn. Another exception does not: on the main looper it exits the script, and elsewhere it ends the thread.
 *
 * zh-CN: 在顶层调用的调用栈清空时运行微任务检查点 (Rhino-For-AutoJs6 ROADMAP P9.5), 不论该调用来自主脚本, timer 回调, 事件还是接口回调, 也不论在哪个线程上.
 * 协程挂起同样结束本回合. 其他异常不算: 它们在主 Looper 上结束脚本, 在其他线程上结束线程.
 */
open class EventLoopContextFactory : ContextFactory() {

    override fun doTopCall(callable: Callable, cx: Context, scope: VarScope, thisObj: Any?, args: Array<Any?>): Any? =
        thenCheckpoint(cx, scope) { super.doTopCall(callable, cx, scope, thisObj, args) }

    override fun doTopCall(script: Script, cx: Context, scope: VarScope, thisObj: Any?): Any? =
        thenCheckpoint(cx, scope) { super.doTopCall(script, cx, scope, thisObj) }

    private inline fun thenCheckpoint(cx: Context, scope: VarScope, call: () -> Any?): Any? {
        val result = try {
            call()
        } catch (e: ContinuationPending) {
            checkpoint(cx, scope)
            throw e
        }
        checkpoint(cx, scope)
        return result
    }

    private fun checkpoint(cx: Context, scope: VarScope) {
        (cx as? AutoJsContext)?.microtaskCheckpoint(ScriptEventLoop.of(scope))
    }

}
