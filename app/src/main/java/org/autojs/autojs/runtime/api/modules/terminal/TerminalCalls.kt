package org.autojs.autojs.runtime.api.modules.terminal

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.terminal.api.TerminalErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * The Rhino-side plumbing shared by the `terminal` global and the `TerminalSession` objects
 * (plugin roadmap P4.1): the synchronous form of a method blocks the script thread inside the
 * runtime's coroutine context (so stopping the script cancels the waiting side), the `Async`
 * form returns a Promise settled on the script thread, script arguments become Gson trees like
 * `JSON.stringify` sees them, and every failure leaves as `TerminalError`.
 * zh-CN: `terminal` 全局对象与 `TerminalSession` 对象共用的 Rhino 侧管道 (插件路线图 P4.1): 方法的同步形态在运行时协程上下文里阻塞脚本线程 (停止脚本即取消
 * 等待方), `Async` 形态返回在脚本线程落定的 Promise, 脚本参数像 `JSON.stringify` 所见那样成为 Gson 树, 每个失败都以 `TerminalError` 离开.
 */
internal object TerminalCalls {

    /** Runs [work] on the script thread until it completes (pumping this thread's terminal events meanwhile) and returns its outcome as a script value. zh-CN: 在脚本线程上运行 [work] 直到完成 (期间泵送本线程的终端事件) 并把结果作为脚本值返回. */
    fun sync(scriptRuntime: ScriptRuntime, work: TerminalWork): Any? {
        try {
            val value = TerminalPromises.sync(scriptRuntime) { work(null) }
            val produced = if (value is TerminalPromises.OnScriptThread) value.produce() else value
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, produced)
        } catch (t: Throwable) {
            TerminalJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** Runs [work] as a coroutine and returns the Promise of its outcome. zh-CN: 把 [work] 作为协程运行并返回其结果的 Promise. */
    fun async(scriptRuntime: ScriptRuntime, work: TerminalWork): Any = TerminalPromises.launchWith(scriptRuntime) { dispatcher -> work(dispatcher) }

    /** [sync] of the work [build] produces; argument failures throw as `TerminalError`. zh-CN: [build] 产出的工作的 [sync]; 参数失败以 `TerminalError` 抛出. */
    fun syncOf(scriptRuntime: ScriptRuntime, build: () -> TerminalWork): Any? = try {
        sync(scriptRuntime, build())
    } catch (t: Throwable) {
        TerminalJsErrors.throwInto(scriptRuntime, t)
    }

    /** [async] of the work [build] produces; argument failures reject the Promise. zh-CN: [build] 产出的工作的 [async]; 参数失败拒绝 Promise. */
    fun asyncOf(scriptRuntime: ScriptRuntime, build: () -> TerminalWork): Any {
        val work = try {
            build()
        } catch (t: Throwable) {
            return TerminalPromises.rejected(scriptRuntime, t)
        }
        return async(scriptRuntime, work)
    }

    /** A script argument as a Gson tree, like `JSON.stringify` would see it; `undefined` and null become null. zh-CN: 脚本参数的 Gson 树, 如 `JSON.stringify` 所见; `undefined` 与 null 成为 null. */
    fun json(value: Any?): JsonElement? = when (value) {
        null, is Undefined -> null
        is CharSequence -> JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Scriptable -> {
            val serialized = js_json_stringify(value)
            if (serialized is Undefined) throw TerminalJsErrors.failure(TerminalErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible, got ${value.jsBrief()}")
            try {
                JsonParser.parseString(Context.toString(serialized))
            } catch (e: RuntimeException) {
                throw TerminalJsErrors.failure(TerminalErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible: ${e.message.orEmpty()}")
            }
        }
        else -> throw TerminalJsErrors.failure(TerminalErrorCodes.INVALID_ARGUMENT, "unsupported argument ${value.jsBrief()}")
    }

    /** Defines a read-only, non-enumerable function whose failures leave as `TerminalError`. zh-CN: 定义只读且不可枚举的函数, 其失败以 `TerminalError` 离开. */
    fun defineFunction(scriptRuntime: ScriptRuntime, holder: NativeObject, name: String, body: (Array<out Any?>) -> Any?) {
        val guarded: (Array<out Any?>) -> Any? = { args ->
            try {
                body(args)
            } catch (t: Throwable) {
                TerminalJsErrors.throwInto(scriptRuntime, t)
            }
        }
        holder.defineProperty(
            name,
            newBaseFunction(scriptRuntime.topLevelScope, name, guarded, NOT_CONSTRUCTABLE),
            ScriptableObject.READONLY or ScriptableObject.DONTENUM or ScriptableObject.PERMANENT,
        )
    }
}
