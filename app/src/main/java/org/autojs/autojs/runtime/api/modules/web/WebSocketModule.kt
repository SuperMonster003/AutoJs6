package org.autojs.autojs.runtime.api.modules.web

import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptConstruct
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptNameLookup
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import java.lang.reflect.Modifier
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The global constructor `WebSocket` on the new script module layer (roadmap P3.5), without a `$` name, as the current
 * `augment/web/WebSocket`: `new WebSocket(url)` and `new WebSocket(client, url)` create the current
 * `org.autojs.autojs.core.web.WebSocket` (appendix C Q24) with the runtime's MutableOkHttp client, or the given one
 * when it is a MutableOkHttp (other values fall back to the runtime's client, as documented; the current module never
 * took the given client, because it checked the type before unwrapping the script value). The constants (`CODE_*`,
 * `EVENT_*`) come from the name lookup prototype [WebSocketConstants], as the current `WebSocketFields`. The URL must be the
 * documented string (other values are a TypeError; the current module converted anything with ToString and threw
 * IllegalArgumentException for nullish); calling `WebSocket(...)` without `new` is a TypeError as in the obsolete JS.
 *
 * zh-CN: 新脚本模块层上的全局构造器 WebSocket (P3.5), 没有 `$` 名称, 与现有 augment/web/WebSocket 相同: new WebSocket(url) 与 new WebSocket(client, url)
 * 创建现有 core.web.WebSocket (附录 C Q24), 使用运行时的 MutableOkHttp 客户端, 给出的 client 是 MutableOkHttp 时用它 (其他值按文档回退到运行时的客户端;
 * 现有模块在解包脚本值之前判断类型, 从未用上给出的客户端). 常量 (CODE_*, EVENT_*) 来自名称查找原型 WebSocketConstants, 同现有 WebSocketFields. URL 须为
 * 文档所述的字符串 (其他值为 TypeError; 现有模块以 ToString 转换任何值, nullish 时抛 IllegalArgumentException); 不用 new 调用 WebSocket(...) 与旧 JS
 * 一样为 TypeError.
 */
@ScriptModule(name = "WebSocket", dollarPrefix = false)
class WebSocketModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val constants: ScriptNameLookup = WebSocketConstants

    /** Calling without `new` is a TypeError, as in the obsolete JS (the current module returned undefined). */
    @ScriptInvoke
    fun invoke(first: Any?, url: Any? = null): Nothing = throw RhinoScriptRuntime.typeErrorById("msg.only.from.new", "WebSocket")

    @ScriptConstruct
    fun construct(first: Any?, url: Any? = ABSENT): Any {
        if (url === ABSENT) return CoreWebSocket(runtime, runtime.http.okhttp, url(first))
        val client = (if (first is Wrapper) first.unwrap() else first) as? MutableOkHttp ?: runtime.http.okhttp
        return CoreWebSocket(runtime, client, url(url))
    }

    /** The documented string URL (other values are a TypeError; the current module converted anything with ToString). */
    private fun url(value: Any?): String = when (val raw = if (value is Wrapper) value.unwrap() else value) {
        is CharSequence -> raw.toString()
        else -> throw UtilValues.typeError(CONSTRUCTOR, ScriptMessages.argumentType("url", "string", ScriptArgs.describe(value)))
    }

    private companion object {
        const val CONSTRUCTOR = "new WebSocket"
        val ABSENT = Any()
    }

}

/**
 * The public constants of `core.web.WebSocket` (the `const val` of its companion, static fields of the class) by name,
 * read through `WebSocket.CODE_CLOSE_NORMAL` and so on; nothing is enumerable, as with the current `WebSocketFields`.
 *
 * zh-CN: core.web.WebSocket 的公开常量 (伴生对象的 const val, 即该类的静态字段), 按名称经 WebSocket.CODE_CLOSE_NORMAL 等读取; 与现有 WebSocketFields
 * 一样都不可枚举.
 */
internal object WebSocketConstants : ScriptNameLookup {

    val VALUES: Map<String, Any> by lazy {
        CoreWebSocket::class.java.declaredFields
            .filter { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && Modifier.isFinal(it.modifiers) && (it.type == Int::class.javaPrimitiveType || it.type == String::class.java) }
            .associate { it.name to it.get(null) }
    }

    override fun lookup(scope: VarScope, name: String): Any? = VALUES[name] ?: Scriptable.NOT_FOUND

}
