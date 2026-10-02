package org.autojs.autojs.runtime.api.augment.terminal

import kotlinx.coroutines.launch
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.augment.toJsPromise
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Scriptable

/** The `xAsync` results (and, from P4.2 on, the session events) of the terminal API reach the script thread through the shared dispatcher. zh-CN: 终端 API 的 `xAsync` 结果 (自 P4.2 起还有会话事件) 经共享分发器回到脚本线程. */
internal typealias TerminalAsyncDispatcher = ScriptAsyncDispatcher

/** The work of one script method: runs in a coroutine; the dispatcher is present for the `Async` form only. zh-CN: 脚本方法的工作: 在协程里运行; 只有 `Async` 形态带分发器. */
internal typealias TerminalWork = suspend (TerminalAsyncDispatcher?) -> Any?

/**
 * Promise plumbing of the `xAsync` terminal methods (plugin roadmap P4.1): the work runs as a
 * coroutine of the script runtime, and the outcome comes back on the script thread through a
 * [TerminalAsyncDispatcher] created by the caller on that thread, which also keeps the script
 * alive until the Promise settled. Results are plain JVM values, script objects or an
 * [OnScriptThread] whose value is produced on the script thread (a session object needs the
 * Rhino context of that thread); failures become `TerminalError` instances ([TerminalJsErrors]).
 * zh-CN: 终端 `xAsync` 方法的 Promise 管道 (插件路线图 P4.1): 工作作为脚本运行时的协程运行, 结果经调用方在脚本线程上创建的 [TerminalAsyncDispatcher]
 * 回到脚本线程, 分发器同时让脚本活到 Promise 落定. 结果是普通 JVM 值, 脚本对象或在脚本线程上产出值的 [OnScriptThread] (会话对象需要该线程的 Rhino
 * 上下文); 失败成为 `TerminalError` 实例 ([TerminalJsErrors]).
 */
internal object TerminalPromises {

    /** A result whose script value must be built on the script thread. zh-CN: 其脚本值必须在脚本线程上构建的结果. */
    fun interface OnScriptThread {
        fun produce(): Any?
    }

    /** Must be called on the script thread. zh-CN: 必须在脚本线程上调用. */
    fun launch(scriptRuntime: ScriptRuntime, block: suspend () -> Any?): Scriptable = launchWith(scriptRuntime) { block() }

    /** Like [launch]; [block] receives the dispatcher for non-terminal callbacks. zh-CN: 同 [launch]; [block] 得到分发器以做非终结回调. */
    fun launchWith(scriptRuntime: ScriptRuntime, block: suspend (TerminalAsyncDispatcher) -> Any?): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val dispatcher = TerminalAsyncDispatcher(scriptRuntime)
        scriptRuntime.coroutineScope.launch {
            val outcome = runCatching { block(dispatcher) }
            deliver(scriptRuntime, dispatcher, adapter, outcome)
        }
        return adapter.toJsPromise(scriptRuntime) as Scriptable
    }

    /** A Promise already rejected with [error]. zh-CN: 已用 [error] 拒绝的 Promise. */
    fun rejected(scriptRuntime: ScriptRuntime, error: Throwable): Scriptable {
        val adapter = ScriptPromiseAdapter()
        val dispatcher = TerminalAsyncDispatcher(scriptRuntime)
        deliver(scriptRuntime, dispatcher, adapter, Result.failure(error))
        return adapter.toJsPromise(scriptRuntime) as Scriptable
    }

    private fun deliver(scriptRuntime: ScriptRuntime, dispatcher: TerminalAsyncDispatcher, adapter: ScriptPromiseAdapter, outcome: Result<Any?>) {
        try {
            outcome.fold(
                onSuccess = { value ->
                    if (value is OnScriptThread) {
                        dispatcher.terminalValues(emptyArray()) {
                            val produced = try {
                                value.produce()
                            } catch (t: Throwable) {
                                adapter.reject(TerminalJsErrors.toJs(scriptRuntime, t))
                                return@terminalValues
                            }
                            adapter.resolve(RhinoUtils.toJsValue(scriptRuntime.topLevelScope, produced))
                        }
                    } else {
                        dispatcher.resolve(adapter, value)
                    }
                },
                onFailure = { error ->
                    dispatcher.terminalValues(emptyArray()) { adapter.reject(TerminalJsErrors.toJs(scriptRuntime, error)) }
                },
            )
        } catch (_: Throwable) {
            // The script is gone (its looper no longer accepts work): nothing is left to settle.
            // zh-CN: 脚本已不在 (其 looper 不再接受工作): 没有可落定的对象了.
            runCatching { dispatcher.cancelSilently() }
        }
    }
}
