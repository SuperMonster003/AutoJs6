package org.autojs.autojs.runtime.api.modules.epub

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.epub.EpubService
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * The Rhino-side plumbing shared by the `epub` global and the `EpubBook` objects (plugin roadmap
 * P6.1): the synchronous form of a method blocks the script thread inside the runtime's coroutine
 * context (so stopping the script cancels the waiting side), the `Async` form returns a Promise
 * settled on the script thread, script arguments become Gson trees like `JSON.stringify` sees
 * them, and every failure leaves as `EpubError`.
 * zh-CN: `epub` 全局对象与 `EpubBook` 对象共用的 Rhino 侧管道 (插件路线图 P6.1): 方法的同步形态在运行时协程上下文里阻塞脚本线程 (停止脚本即取消
 * 等待方), `Async` 形态返回在脚本线程落定的 Promise, 脚本参数像 `JSON.stringify` 所见那样成为 Gson 树, 每个失败都以 `EpubError` 离开.
 */
internal object EpubCalls {

    /** Runs [work] on the script thread until it completes and returns its outcome as a script value. zh-CN: 在脚本线程上运行 [work] 直到完成并把结果作为脚本值返回. */
    fun sync(scriptRuntime: ScriptRuntime, work: EpubWork): Any? {
        try {
            val value = runBlocking(scriptRuntime.coroutineContext) { work(null) }
            val produced = if (value is EpubPromises.OnScriptThread) value.produce() else value
            return RhinoUtils.toJsValue(scriptRuntime.topLevelScope, produced)
        } catch (t: Throwable) {
            EpubJsErrors.throwInto(scriptRuntime, t)
        }
    }

    /** Runs [work] as a coroutine and returns the Promise of its outcome. zh-CN: 把 [work] 作为协程运行并返回其结果的 Promise. */
    fun async(scriptRuntime: ScriptRuntime, work: EpubWork): Any = EpubPromises.launchWith(scriptRuntime) { dispatcher -> work(dispatcher) }

    /** [sync] of the work [build] produces; argument failures throw as `EpubError`. zh-CN: [build] 产出的工作的 [sync]; 参数失败以 `EpubError` 抛出. */
    fun syncOf(scriptRuntime: ScriptRuntime, build: () -> EpubWork): Any? = try {
        sync(scriptRuntime, build())
    } catch (t: Throwable) {
        EpubJsErrors.throwInto(scriptRuntime, t)
    }

    /** [async] of the work [build] produces; argument failures reject the Promise. zh-CN: [build] 产出的工作的 [async]; 参数失败拒绝 Promise. */
    fun asyncOf(scriptRuntime: ScriptRuntime, build: () -> EpubWork): Any {
        val work = try {
            build()
        } catch (t: Throwable) {
            return EpubPromises.rejected(scriptRuntime, t)
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
            if (serialized is Undefined) throw EpubJsErrors.failure(EpubErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible, got ${value.jsBrief()}")
            try {
                JsonParser.parseString(Context.toString(serialized))
            } catch (e: RuntimeException) {
                throw EpubJsErrors.failure(EpubErrorCodes.INVALID_ARGUMENT, "argument must be JSON-compatible: ${e.message.orEmpty()}")
            }
        }
        else -> throw EpubJsErrors.failure(EpubErrorCodes.INVALID_ARGUMENT, "unsupported argument ${value.jsBrief()}")
    }

    /** An output target: a non-blank path string resolved against the script's working directory. zh-CN: 输出目标: 相对脚本工作目录解析的非空白路径字符串. */
    fun targetPath(service: EpubService, value: Any?, label: String): String {
        val text = (value as? CharSequence)?.toString()
            ?: throw EpubJsErrors.failure(EpubErrorCodes.INVALID_ARGUMENT, "'$label' must be a directory or file path string, got ${value.jsBrief()}")
        if (text.isBlank()) throw EpubJsErrors.failure(EpubErrorCodes.INVALID_ARGUMENT, "'$label' must not be blank")
        return service.resolvePath(text)
    }

    /** Like [targetPath], or null when the script passed nothing. zh-CN: 同 [targetPath], 脚本未传时为 null. */
    fun optionalTargetPath(service: EpubService, value: Any?, label: String): String? =
        if (value.isJsNullish()) null else targetPath(service, value, label)

    /** Defines a read-only, non-enumerable function whose failures leave as `EpubError`. zh-CN: 定义只读且不可枚举的函数, 其失败以 `EpubError` 离开. */
    fun defineFunction(scriptRuntime: ScriptRuntime, holder: NativeObject, name: String, body: (Array<out Any?>) -> Any?) {
        val guarded: (Array<out Any?>) -> Any? = { args ->
            try {
                body(args)
            } catch (t: Throwable) {
                EpubJsErrors.throwInto(scriptRuntime, t)
            }
        }
        holder.defineProperty(
            name,
            newBaseFunction(scriptRuntime.topLevelScope, name, guarded, NOT_CONSTRUCTABLE),
            ScriptableObject.READONLY or ScriptableObject.DONTENUM or ScriptableObject.PERMANENT,
        )
    }
}
