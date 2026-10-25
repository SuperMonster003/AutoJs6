package org.autojs.autojs.runtime.api.modules.web

import okio.ByteString
import org.autojs.autojs.core.web.InjectableWebClient
import org.autojs.autojs.core.web.InjectableWebView
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptJavaClass
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import org.autojs.autojs.core.web.WebSocket as CoreWebSocket
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `web` module on the new script module layer (roadmap P3.5): `newInjectableWebView`, `newInjectableWebClient` and
 * `newWebSocket`, also global functions, and the Java class `ByteString`, as the current `augment/web/Web`. The created
 * objects are the current `core.web` classes (appendix C Q24). WebViews are created by the current module's
 * `Web.newInjectableWebViewRhinoWithRuntime` (context and URL as two arguments), so whatever it does with them, such as
 * registering them with the runtime's resources (docs/memory/ROADMAP.md P4.4), applies here too.
 *
 * Arguments follow the documented overloads (T4): `newInjectableWebView()` uses the global `activity`, one string
 * argument is a URL (any string, the current module took only `java.lang.String` and treated a concatenated string as the
 * context), one other argument the Android context, two arguments the context and a nullable URL; a value that does not
 * unwrap to an Android context falls back to the application context (documented). `newInjectableWebClient` takes the
 * Rhino context of the call (T6) instead of `withRhinoContext`. `newWebSocket` options that are not a plain object are a
 * TypeError (the current module threw IllegalArgumentException), surplus arguments a TypeError (appendix C Q7).
 *
 * zh-CN: 新脚本模块层上的 web 模块 (P3.5): newInjectableWebView, newInjectableWebClient 与 newWebSocket (同时是全局函数), 以及 Java 类 ByteString,
 * 与现有 augment/web/Web 相同. 创建的对象是现有 core.web 的类 (附录 C Q24). WebView 由现有模块的 Web.newInjectableWebViewRhinoWithRuntime 创建 (上下文与 URL
 * 作为两个参数), 因此它对 WebView 的处理 (如登记到运行时资源, docs/memory/ROADMAP.md P4.4) 在此同样生效. 参数按文档的重载 (T4): newInjectableWebView() 使用
 * 全局 activity, 一个字符串参数为 URL (任何字符串; 现有模块只认 java.lang.String, 把拼接得到的字符串当作上下文), 一个其他参数为 Android 上下文, 两个参数为上下文
 * 与可为 null 的 URL; 解包后不是 Android 上下文的值回退到应用上下文 (文档). newInjectableWebClient 使用调用的 Rhino Context (T6), 不再 withRhinoContext.
 * newWebSocket 的选项不是普通对象为 TypeError (现有模块为 IllegalArgumentException), 多余参数为 TypeError (附录 C Q7).
 */
@ScriptModule(name = "web")
@ScriptJavaClass(name = "ByteString", type = ByteString::class)
class WebModule(private val runtime: ScriptRuntime) {

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun newInjectableWebView(first: Any? = ABSENT, url: Any? = ABSENT): InjectableWebView = when {
        first === ABSENT -> webView(activity(), null)
        url !== ABSENT -> webView(first, if (ScriptArgs.isNullish(url)) null else RhinoScriptRuntime.toString(url))
        (if (first is Wrapper) first.unwrap() else first) is CharSequence -> webView(activity(), RhinoScriptRuntime.toString(first))
        else -> webView(first, null)
    }

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun newInjectableWebClient(cx: Context): InjectableWebClient = InjectableWebClient(cx, runtime.topLevelScope.globalThis)

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun newWebSocket(url: String, options: Any? = null): CoreWebSocket =
        CoreWebSocket(runtime, runtime.http.okhttp, url, isInCurrentThread = isEventThreadThis(options))

    private fun activity(): Any? = ScriptableObject.getProperty(runtime.topLevelScope.globalThis, "activity")

    private fun webView(androidContext: Any?, url: String?): InjectableWebView = Web.newInjectableWebViewRhinoWithRuntime(runtime, androidContext, url)

    /** `options.eventThread === 'this'` schedules the listeners on the current thread's timer (documented). */
    private fun isEventThreadThis(options: Any?): Boolean {
        if (ScriptArgs.isNullish(options)) return false
        if (options !is NativeObject) throw UtilValues.typeError("web.newWebSocket", ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
        val thread = ScriptableObject.getProperty(options, "eventThread")
        return thread !== ScriptableObject.NOT_FOUND && !ScriptArgs.isNullish(thread) && RhinoScriptRuntime.toString(thread) == "this"
    }

    private companion object {
        /** The default of an omitted argument, as `newInjectableWebView` tells its overloads apart by their number. */
        val ABSENT = Any()
    }

}
