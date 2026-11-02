package org.autojs.autojs.rhino

import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope

/**
 * What a Context asks of the script it works for about promises (roadmap P9.5 of Rhino-For-AutoJs6, appendix C Q64 and Q65).
 * Microtasks belong to the Context of a thread and only a thread with a timer has a turn to run them, so [AutoJsContext]
 * sends what would stay in a queue nobody runs to a thread that has one. [TopLevelScope] holds it for the Context, which
 * can serve several scripts (the main thread of UI scripts).
 *
 * zh-CN: Context 就 Promise 向其服务的脚本询问的内容 (Rhino-For-AutoJs6 ROADMAP P9.5, 附录 C Q64 与 Q65). 微任务属于线程的 Context, 只有带 timer 的线程才有
 * 运行它们的时机, 因此 [AutoJsContext] 把会留在无人运行的队列中的任务转交给带 timer 的线程. 由 [TopLevelScope] 持有, 因为一个 Context 可能服务多个脚本 (UI 脚本的主线程).
 */
interface ScriptEventLoop {

    /** Whether [thread] runs a timer of this script. zh-CN: [thread] 是否运行本脚本的 timer. */
    fun hasTimer(thread: Thread): Boolean

    /**
     * Runs [task] as a turn of [thread], or of the main thread of the script when it is null, in a checkpoint of the Context
     * there ([AutoJsContext.runTask]). A script that is ending drops it.
     * zh-CN: 在 [thread] 上 (为 null 时在脚本主线程上) 以一个回合运行 [task], 运行于该处 Context 的检查点内 ([AutoJsContext.runTask]). 正在结束的脚本会丢弃它.
     */
    fun post(thread: Thread?, task: Runnable)

    /**
     * Reports a Promise that was rejected without a handler (appendix C Q6). The script goes on.
     * zh-CN: 报告没有处理器的 Promise 拒绝 (附录 C Q6). 脚本继续运行.
     */
    fun reportUnhandledRejection(description: String)

    companion object {

        /**
         * The event loop of the script that the object [scope] belongs to.
         * zh-CN: 对象 [scope] 所属脚本的事件循环.
         */
        fun of(scope: Scriptable?): ScriptEventLoop? = of(scope?.parentScope)

        /** @see of */
        fun of(scope: VarScope?): ScriptEventLoop? =
            scope?.let { (ScriptableObject.getTopLevelScope(it) as? TopLevelScope)?.eventLoop }

        /**
         * The rejection [reason] as the console shows it: the value, then the stack when it has one. The reason may be any
         * object, whose `toString` and `stack` are script code.
         * zh-CN: 控制台显示的拒绝原因 [reason]: 值本身, 有 stack 时再加 stack. 原因可以是任意对象, 其 toString 与 stack 是脚本代码.
         */
        fun describeRejection(reason: Any?): String {
            val text = try {
                Context.toString(reason)
            } catch (_: RhinoException) {
                reason?.javaClass?.simpleName.toString()
            }
            val stack = try {
                (reason as? Scriptable)?.let { ScriptableObject.getProperty(it, "stack") } as? CharSequence
            } catch (_: RhinoException) {
                null
            }
            return if (stack.isNullOrBlank()) text else "$text\n${stack.toString().trimEnd()}"
        }

    }

}
