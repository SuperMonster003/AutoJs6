package org.autojs.autojs.rhino

import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.util.RhinoUtils.unregisterStandardObjects
import org.autojs.autojs6.R
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

class AutoJsContext(factory: ContextFactory?) : Context(factory) {

    val continuations = HashSet<Any>()

    /** The thread of this Context, as a Context is made where it is first entered. zh-CN: 本 Context 的线程, Context 在首次进入的线程上创建. */
    val thread: Thread = Thread.currentThread()

    private var inCheckpoint = false

    init {
        setTrackUnhandledPromiseRejections(true)
    }

    /**
     * The engine this Context currently works for: the newest engine bound to it that has not released it yet.
     * zh-CN: 本 Context 当前服务的引擎: 绑定到它且尚未释放的引擎中最新的一个.
     */
    var rhinoJavaScriptEngine: RhinoJavaScriptEngine? = null

    /**
     * The engines bound to this Context, oldest first ([bind] / [release]). One Context serves every engine of its thread:
     * on the main thread every running UI script enters the same Context. Without the stack, the engine of the newest UI
     * script stayed bound (with its WrapFactory and its standard objects) after it ended, so the Context of a UI script
     * still running kept the ended runtime, and that script wrapped its Views for the wrong runtime
     * (docs/memory/ROADMAP.md H4 / P4.4).
     * zh-CN: 绑定到本 Context 的引擎, 最早的在前 ([bind] / [release]). 一个 Context 服务其线程上的全部引擎: 主线程上每个运行中的 UI 脚本都进入同一个
     * Context. 没有这个栈时, 最新的 UI 脚本结束后其引擎 (连同 WrapFactory 与标准对象) 仍绑定在 Context 上, 仍在运行的 UI 脚本的 Context 因此持有已结束的
     * 运行时, 并用错误的运行时包装自己的 View (docs/memory/ROADMAP.md H4 / P4.4).
     */
    private val bindings = ArrayList<Binding<RhinoJavaScriptEngine>>()

    /**
     * Makes [engine] the engine of this Context; [apply] installs its WrapFactory and standard objects, now and again when
     * a newer engine releases the Context while [engine] still runs.
     * zh-CN: 使 [engine] 成为本 Context 的引擎; [apply] 安装其 WrapFactory 与标准对象, 现在执行一次, 之后更新的引擎释放本 Context 而 [engine] 仍在运行时再执行.
     */
    fun bind(engine: RhinoJavaScriptEngine, apply: (AutoJsContext) -> Unit) {
        Binding.bind(bindings, engine, apply)
        rhinoJavaScriptEngine = engine
        apply(this)
    }

    /**
     * Called by [engine] when it ends: drops its binding and, if it was the current one, binds the newest remaining engine
     * again, or none.
     * zh-CN: [engine] 结束时调用: 移除其绑定; 若它是当前绑定, 重新绑定剩余引擎中最新的一个, 没有则不绑定.
     */
    fun release(engine: RhinoJavaScriptEngine) {
        val next = Binding.release(bindings, engine) ?: return
        val owner = next.owner
        rhinoJavaScriptEngine = owner
        if (owner != null) next.apply(this) else clearBinding()
    }

    // No engine is left: back to what the factory installs in a new Context (the Context is usually exited right after).
    // zh-CN: 已没有引擎: 恢复为工厂在新 Context 中安装的内容 (通常随后即退出该 Context).
    private fun clearBinding() {
        (factory as? AndroidContextFactory)?.let { wrapFactory = it.wrapFactory }
        unregisterStandardObjects()
    }

    /**
     * Rhino captures a continuation only down to a top call made for one (the main script, a timer callback), which an async
     * function and a callback of a Promise are not. Its IllegalStateException named Rhino's own entry points.
     * zh-CN: Rhino 只能捕获到专为协程发起的顶层调用 (主脚本, timer 回调) 为止的续体, async 函数与 Promise 回调都不是. 原先的 IllegalStateException 说的是 Rhino 自己的入口.
     */
    override fun captureContinuation(): ContinuationPending {
        val continuationPending = try {
            super.captureContinuation()
        } catch (_: IllegalStateException) {
            throw RhinoScriptRuntime.typeError(
                ScriptMessages.resource(
                    R.string.error_continuation_cannot_suspend_here,
                    "A coroutine cannot suspend here. Use the await keyword in an async function, and then() in a callback of a Promise",
                )
            )
        }
        continuations.add(continuationPending.continuation)
        return continuationPending
    }

    override fun resumeContinuation(continuation: Any, scope: VarScope?, functionResult: Any?): Any {
        continuations.remove(continuation)
        return super.resumeContinuation(continuation, scope, functionResult)
    }

    override fun resumeContinuationWithError(continuation: Any, scope: VarScope?, error: Any?): Any {
        continuations.remove(continuation)
        return super.resumeContinuationWithError(continuation, scope, error)
    }

    fun hasPendingContinuation(): Boolean {
        return continuations.isNotEmpty()
    }

    /**
     * The microtask checkpoint of this thread (roadmap P9.5 of Rhino-For-AutoJs6): when the stack of a top call is empty
     * ([EventLoopContextFactory]), and before a looper decides that nothing is left. Afterwards the rejections that nothing
     * handled are reported once to [loop] (appendix C Q6). Inside a checkpoint this does nothing: a task that calls a script
     * function makes a top call of its own, and the queue must wait for that task to end.
     * zh-CN: 本线程的微任务检查点 (Rhino-For-AutoJs6 ROADMAP P9.5): 在顶层调用的调用栈清空时 ([EventLoopContextFactory]), 以及 Looper 判定无事可做之前. 之后向 [loop]
     * 报告一次无人处理的拒绝 (附录 C Q6). 在检查点内调用时不做任何事: 任务调用脚本函数时会发起自己的顶层调用, 队列要等该任务结束.
     */
    fun microtaskCheckpoint(loop: ScriptEventLoop?) {
        if (inCheckpoint) return
        runMicrotasks()
        unhandledPromiseTracker.process { reason -> loop?.reportUnhandledRejection(ScriptEventLoop.describeRejection(reason)) }
    }

    /**
     * Runs the microtasks to the end, also from inside a task: the thread of `Promise.prototype.wait` must see the reactions it
     * waits for. A coroutine that a reaction resumed may suspend again, which ends that task and leaves the others queued. It
     * reports nothing, as the task that waits has not ended and may still handle a rejection.
     * zh-CN: 运行全部微任务, 也可在任务内调用: `Promise.prototype.wait` 所在线程要等到反应运行. 反应恢复的协程可能再次挂起, 这只结束该任务, 其余任务仍在队列中.
     * 这里不做报告, 因为等待中的任务尚未结束, 之后仍可能处理拒绝.
     */
    fun runMicrotasks() {
        val outer = inCheckpoint
        inCheckpoint = true
        try {
            while (true) {
                try {
                    processMicrotasks()
                    return
                } catch (_: ContinuationPending) {
                    /* Suspended again, see above. */
                }
            }
        } finally {
            inCheckpoint = outer
        }
    }

    /**
     * Runs a [task] forwarded to this thread and the microtasks it leaves. It goes through the queue so that it runs inside
     * the checkpoint, as any reaction: a checkpoint at the end of a top call inside it would run other reactions in the middle
     * of an async function.
     * zh-CN: 运行转交到本线程的 [task] 及其留下的微任务. 它经由队列运行, 因此与其他反应一样在检查点内: 否则其中顶层调用结束时的检查点会在 async 函数中途运行其他反应.
     */
    fun runTask(loop: ScriptEventLoop?, task: Runnable) {
        enqueueMicrotask(task)
        microtaskCheckpoint(loop)
    }

    /**
     * Sends a settlement or the reaction of an `await` to a thread that runs it (appendix C Q64 and Q65). The settlement of a
     * Promise ([home] is null) stays here during a top call of a thread with a timer, whose checkpoint runs the reactions.
     * Without a timer (a binder thread, a thread of OkHttp) nobody runs them and they go to the main thread of the script, where
     * the reactions of the Promise of AutoJs6 always went; without a top call (the UI thread enters a Context for each call)
     * they wait for the next turn of this thread. An `await` goes back to the thread of its function ([home]), as a coroutine does
     * (docs/augment/ROADMAP.md Q98), unless that thread has ended or has no timer.
     * zh-CN: 把 Promise 的落定或 await 的反应转交给能运行它的线程 (附录 C Q64 与 Q65). Promise 落定 ([home] 为 null) 时, 带 timer 的线程在顶层调用内留在本地, 由其检查点运行反应.
     * 没有 timer 时 (binder 线程, OkHttp 线程) 无人运行, 转到脚本主线程, 这也是 AutoJs6 的 Promise 一直以来的去处; 没有顶层调用时 (UI 线程每次调用才进入 Context)
     * 留到本线程的下一回合. await 回到其函数所在线程 ([home]), 与协程相同 (docs/augment/ROADMAP.md Q98), 除非该线程已结束或没有 timer.
     */
    override fun forwardTask(home: Context?, owner: Scriptable, task: Runnable): Boolean {
        val loop = ScriptEventLoop.of(owner) ?: return false
        val current = Thread.currentThread()
        val target = when {
            home != null -> {
                val thread = (home as? AutoJsContext)?.thread
                if (thread == null || thread === current || !thread.isAlive || !loop.hasTimer(thread)) return false
                thread
            }
            !loop.hasTimer(current) -> null
            RhinoScriptRuntime.hasTopCall(this) -> return false
            else -> current
        }
        loop.post(target, task)
        return true
    }

    /**
     * One owner bound to a Context and how to install it. [bind] and [release] keep the list of a Context (oldest first) and
     * are plain functions so that the JVM tests can check them without an engine.
     * zh-CN: 绑定到 Context 的一个 owner 及其安装方式. [bind] 与 [release] 维护一个 Context 的列表 (最早的在前), 写成普通函数以便 JVM 测试在没有引擎时检查.
     */
    class Binding<T : Any> internal constructor(val owner: T?, val apply: (AutoJsContext) -> Unit) {

        companion object {

            /** Adds [owner] as the newest binding (moving it to the end when it was bound before). */
            fun <T : Any> bind(bindings: MutableList<Binding<T>>, owner: T, apply: (AutoJsContext) -> Unit) {
                bindings.removeAll { it.owner === owner }
                bindings += Binding(owner, apply)
            }

            /**
             * Removes [owner]; returns null when the current binding did not change (it was not the newest one or was not
             * bound), otherwise the binding to install: the newest remaining one, or one with a null owner when none is left.
             */
            fun <T : Any> release(bindings: MutableList<Binding<T>>, owner: T): Binding<T>? {
                val index = bindings.indexOfLast { it.owner === owner }
                if (index < 0) return null
                val wasCurrent = index == bindings.lastIndex
                bindings.removeAt(index)
                if (!wasCurrent) return null
                return bindings.lastOrNull() ?: Binding(null) {}
            }
        }
    }

}
