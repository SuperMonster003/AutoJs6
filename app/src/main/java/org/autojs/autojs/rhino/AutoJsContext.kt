package org.autojs.autojs.rhino

import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.util.RhinoUtils.unregisterStandardObjects
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.VarScope

class AutoJsContext(factory: ContextFactory?) : Context(factory) {

    val continuations = HashSet<Any>()

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

    override fun captureContinuation(): ContinuationPending {
        val continuationPending = super.captureContinuation()
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
