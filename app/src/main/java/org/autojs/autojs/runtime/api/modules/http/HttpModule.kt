package org.autojs.autojs.runtime.api.modules.http

import okhttp3.OkHttpClient
import okhttp3.Request
import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * The `http` module on the new script module layer (roadmap P3.5): the module object has the Java object
 * `runtime.http` (`org.autojs.autojs.runtime.api.Http`) as its prototype and `__okhttp__` (READONLY + DONTENUM) as the
 * current `augment/http/Http`, then the request functions in its order (`del` / `delAsync` stay functions of their own,
 * named so, as in the current module). Requests are built and run by the current `Http.*RhinoWithRuntime` functions
 * (blocking, enqueued with a callback, through a continuation on the UI thread, or as a Promise for the `Async` forms,
 * all as documented), which return the current HttpResponse objects (appendix C Q24).
 *
 * Arguments follow the real signatures (T4, appendix C Q7) and the documented positions: `buildRequest`, `request` and
 * `requestAsync` take the options (with `method`), `postJson` the data and `postMultipart` the files object, the other
 * options and callbacks are optional. Checked here first (D10): a nullish URL is a TypeError (the current module threw
 * IllegalArgumentException), options that are not a plain object, a missing `method` and multipart files that are not
 * an object are TypeErrors (IllegalArgumentException), and so is a callback that is not a function (the current module
 * ignored it and ran the request synchronously). Errors of building and running the request keep their types (T5).
 *
 * zh-CN: 新脚本模块层上的 http 模块 (P3.5): 与现有 augment/http/Http 相同, 模块对象以 Java 对象 runtime.http 为原型, 有 __okhttp__ (READONLY + DONTENUM),
 * 其后是各请求函数, 顺序照旧 (del / delAsync 仍是各自独立, 名称如此的函数). 请求由现有 Http.*RhinoWithRuntime 函数构建与执行 (阻塞, 带回调时入队, UI 线程上
 * 经 continuation, Async 形式返回 Promise, 均按文档), 返回现有 HttpResponse 对象 (附录 C Q24). 参数按真实签名 (T4, 附录 C Q7) 与文档的位置: buildRequest,
 * request 与 requestAsync 须给出选项 (含 method), postJson 须给出数据, postMultipart 须给出 files 对象, 其余选项与回调可省略. 先在此检查 (D10): nullish URL 为
 * TypeError (现有模块为 IllegalArgumentException), 选项不是普通对象, 缺少 method, multipart 的 files 不是对象均为 TypeError (IllegalArgumentException), 回调不是函数
 * 也为 TypeError (现有模块忽略它并同步执行请求). 构建与执行请求的错误保留其类型 (T5).
 */
@ScriptModule(name = "http")
class HttpModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.http

    @ScriptProperty(name = "__okhttp__", attributes = ScriptAttributes.READONLY or ScriptAttributes.DONTENUM or ScriptAttributes.PERMANENT)
    val okhttp: MutableOkHttp
        get() = runtime.http.okhttp

    @ScriptFunction
    fun client(): OkHttpClient = runtime.http.okhttp.client()

    @ScriptFunction
    fun buildRequest(url: String, options: Any?): Request =
        Http.buildRequestRhinoWithRuntime(runtime, url, builderOptions("http.buildRequest", options))

    @ScriptFunction
    fun request(url: String, options: Any?, callback: Any? = null): Any =
        Http.requestRhinoWithRuntime(runtime, url, builderOptions("http.request", options), callback("http.request", callback))

    @ScriptFunction
    fun requestAsync(url: String, options: Any?, callback: Any? = null): Any =
        Http.requestAsyncRhinoWithRuntime(runtime, url, builderOptions("http.requestAsync", options), callback("http.requestAsync", callback))

    @ScriptFunction
    fun get(url: String, options: Any? = null, callback: Any? = null): Any =
        Http.getRhinoWithRuntime(runtime, url, options("http.get", options), callback("http.get", callback))

    @ScriptFunction
    fun getAsync(url: String, options: Any? = null, callback: Any? = null): Any =
        Http.getAsyncRhinoWithRuntime(runtime, url, options("http.getAsync", options), callback("http.getAsync", callback))

    @ScriptFunction
    fun head(url: String, options: Any? = null, callback: Any? = null): Any =
        Http.headRhinoWithRuntime(runtime, url, options("http.head", options), callback("http.head", callback))

    @ScriptFunction
    fun headAsync(url: String, options: Any? = null, callback: Any? = null): Any =
        Http.headAsyncRhinoWithRuntime(runtime, url, options("http.headAsync", options), callback("http.headAsync", callback))

    @ScriptFunction
    fun post(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.postRhinoWithRuntime(runtime, url, data, options("http.post", options), callback("http.post", callback))

    @ScriptFunction
    fun postAsync(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.postAsyncRhinoWithRuntime(runtime, url, data, options("http.postAsync", options), callback("http.postAsync", callback))

    @ScriptFunction
    fun postJson(url: String, data: Any?, options: Any? = null, callback: Any? = null): Any =
        Http.postJsonRhinoWithRuntime(runtime, url, data, options("http.postJson", options), callback("http.postJson", callback))

    @ScriptFunction
    fun postJsonAsync(url: String, data: Any?, options: Any? = null, callback: Any? = null): Any =
        Http.postJsonAsyncRhinoWithRuntime(runtime, url, data, options("http.postJsonAsync", options), callback("http.postJsonAsync", callback))

    @ScriptFunction
    fun postMultipart(url: String, files: Any?, options: Any? = null, callback: Any? = null): Any =
        Http.postMultipartRhinoWithRuntime(runtime, url, files("http.postMultipart", files), options("http.postMultipart", options), callback("http.postMultipart", callback))

    @ScriptFunction
    fun postMultipartAsync(url: String, files: Any?, options: Any? = null, callback: Any? = null): Any =
        Http.postMultipartAsyncRhinoWithRuntime(runtime, url, files("http.postMultipartAsync", files), options("http.postMultipartAsync", options), callback("http.postMultipartAsync", callback))

    @ScriptFunction
    fun put(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.putRhinoWithRuntime(runtime, url, data, options("http.put", options), callback("http.put", callback))

    @ScriptFunction
    fun putAsync(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.putAsyncRhinoWithRuntime(runtime, url, data, options("http.putAsync", options), callback("http.putAsync", callback))

    @ScriptFunction
    fun delete(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.deleteRhinoWithRuntime(runtime, url, data, options("http.delete", options), callback("http.delete", callback))

    @ScriptFunction
    fun del(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.deleteRhinoWithRuntime(runtime, url, data, options("http.del", options), callback("http.del", callback))

    @ScriptFunction
    fun deleteAsync(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.deleteAsyncRhinoWithRuntime(runtime, url, data, options("http.deleteAsync", options), callback("http.deleteAsync", callback))

    @ScriptFunction
    fun delAsync(url: String, data: Any? = null, options: Any? = null, callback: Any? = null): Any =
        Http.deleteAsyncRhinoWithRuntime(runtime, url, data, options("http.delAsync", options), callback("http.delAsync", callback))

    private companion object {

        /** Optional options: nullish (the current modules then start from an empty object) or a plain object. */
        fun options(function: String, options: Any?): NativeObject? = when {
            ScriptArgs.isNullish(options) -> null
            options is NativeObject -> options
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
        }

        /** The options of `buildRequest` / `request` / `requestAsync`: a plain object with a `method` (documented). */
        fun builderOptions(function: String, options: Any?): NativeObject {
            if (options !is NativeObject) throw UtilValues.typeError(function, ScriptMessages.argumentType("options", "Object", ScriptArgs.describe(options)))
            val method = ScriptableObject.getProperty(options, "method")
            if (method === ScriptableObject.NOT_FOUND || ScriptArgs.isNullish(method)) {
                throw UtilValues.typeError(function, ScriptMessages.argumentType("options.method", "string", ScriptArgs.describe(if (method === ScriptableObject.NOT_FOUND) Undefined.instance else method)))
            }
            return options
        }

        /** A callback: nullish or a function. */
        fun callback(function: String, callback: Any?): Any? = when {
            ScriptArgs.isNullish(callback) -> null
            callback is Callable -> callback
            else -> throw UtilValues.typeError(function, ScriptMessages.argumentType("callback", "function", ScriptArgs.describe(callback)))
        }

        /** The fields and files of a multipart request: a plain object (documented). */
        fun files(function: String, files: Any?): NativeObject = files as? NativeObject
            ?: throw UtilValues.typeError(function, ScriptMessages.argumentType("files", "Object", ScriptArgs.describe(files)))

    }

}
