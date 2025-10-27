@file:Suppress("MayBeConstant")

package org.autojs.autojs.runtime.api.augment.http

import android.annotation.SuppressLint
import android.webkit.MimeTypeMap
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.core.http.MutableOkHttp
import org.autojs.autojs.extension.AnyExtensions.isJsFunction
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.FlexibleArray.Companion.component1
import org.autojs.autojs.extension.FlexibleArray.Companion.component2
import org.autojs.autojs.extension.FlexibleArray.Companion.component3
import org.autojs.autojs.extension.FlexibleArray.Companion.component4
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.continuation.Continuation
import org.autojs.autojs.runtime.api.augment.continuation.Creator
import org.autojs.autojs.runtime.api.augment.converter.core.Bytes
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.Undefined
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Suppress("unused", "UNUSED_PARAMETER")
class Http(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentProperties = listOf(
        "__okhttp__" to scriptRuntime.http.okhttp to (READONLY or DONTENUM or PERMANENT)
    )

    override val selfAssignmentFunctions = listOf(
        ::buildRequest.name,
        ::request.name,
        ::get.name,
        ::post.name,
        ::postJson.name,
        ::postMultipart.name,
    )

    companion object : Augmentable() {

        private const val METHOD_GET = "GET"
        private const val METHOD_POST = "POST"

        private const val KEY_CLIENT = "client"
        private const val KEY_METHOD = "method"
        private const val KEY_CONTENT_TYPE = "contentType"
        private const val KEY_HEADERS = "headers"
        private const val KEY_FILES = "files"
        private const val KEY_BODY = "body"
        private const val KEY_TIMEOUT = "timeout"
        private const val KEY_MAX_RETRIES = "maxRetries"
        private const val KEY_CACHE_BODY = "cacheBody"
        private const val KEY_BODY_CACHE_THRESHOLD_BYTES = "bodyCacheThresholdBytes"

        private val DEFAULT_CONTENT_TYPE = Mime.APPLICATION_X_WWW_FORM_URLENCODED

        @JvmField
        val DEFAULT_TIMEOUT = MutableOkHttp.DEFAULT_TIMEOUT

        @JvmField
        val DEFAULT_MAX_RETRIES = MutableOkHttp.DEFAULT_MAX_RETRIES

        @JvmField
        val DEFAULT_CACHE_BODY = false

        @JvmField
        val DEFAULT_BODY_CACHE_THRESHOLD_BYTES = 8L * 1024 * 1024

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun buildRequest(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Request = ensureArgumentsLengthInRange(args, 1..2) {
            val (url, options) = it
            buildRequestRhinoWithRuntime(scriptRuntime, url, options)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun buildRequestRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null): Request = when {
            options.isJsNullish() -> RequestBuilder(scriptRuntime, url).build()
            options is NativeObject -> RequestBuilder(scriptRuntime, url, options).build()
            else -> throw WrappedIllegalArgumentException("Argument options ${options.jsBrief()} must be a JavaScript Object")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun request(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            requestRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun requestRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): Any {
            val cont: Creator? = when {
                callback.isJsFunction() && isUiThread() && Continuation.isEnabled(scriptRuntime) -> Continuation.createRhinoWithRuntime(scriptRuntime)
                else -> null
            }

            val opt = coerceObject(options, newNativeObject())

            scriptRuntime.http.okhttp.apply {
                maxRetries = coerceIntNumber(opt.prop(KEY_MAX_RETRIES), DEFAULT_MAX_RETRIES)
                applyOkHttpClientBuilder(opt)
            }

            val cacheBody = opt.inquire(listOf(KEY_CACHE_BODY), ::coerceBoolean, DEFAULT_CACHE_BODY)
            val cacheThreshold = coerceLongNumber(opt.prop(KEY_BODY_CACHE_THRESHOLD_BYTES), DEFAULT_BODY_CACHE_THRESHOLD_BYTES)

            val newCall = scriptRuntime.http.client().newCall(buildRequestRhinoWithRuntime(scriptRuntime, url, options))

            if (!callback.isJsFunction() && cont == null) {
                return ResponseWrapper(scriptRuntime, newCall.execute(), cacheBody, cacheThreshold).wrap()
            }

            scriptRuntime.loopers.waitWhenIdle(true)
            newCall.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val wrappedResponse = ResponseWrapper(scriptRuntime, response, cacheBody, cacheThreshold).wrap()
                    cont?.resume(wrappedResponse)
                    if (callback is BaseFunction) {
                        scriptRuntime.bridges.call(callback, callback, arrayOf(wrappedResponse, null))
                    }
                    scriptRuntime.loopers.waitWhenIdle(false)
                }

                override fun onFailure(call: Call, e: IOException) {
                    cont?.resumeError(e)
                    if (callback is BaseFunction) {
                        scriptRuntime.bridges.call(callback, callback, arrayOf(null, e))
                    }
                    scriptRuntime.loopers.waitWhenIdle(false)
                }
            })
            cont?.await()
            return UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun get(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..3) {
            val (url, options, callback) = it
            getRhinoWithRuntime(scriptRuntime, url, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun getRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.get must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_GET)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun post(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            postRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any? = null, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.post must be a JavaScript Object" }
            put(niceOptions, KEY_METHOD to METHOD_POST)
            putIfAbsent(niceOptions, KEY_CONTENT_TYPE to DEFAULT_CONTENT_TYPE)
            fillPostData(niceOptions, data)
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postJson(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, data, options, callback) = it
            postJsonRhinoWithRuntime(scriptRuntime, url, data, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postJsonRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, data: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postJson] must be a JavaScript Object" }
            put(niceOptions, KEY_CONTENT_TYPE to Mime.APPLICATION_JSON)
            return postRhinoWithRuntime(scriptRuntime, url, data, niceOptions, callback)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun postMultipart(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsLengthInRange(args, 1..4) {
            val (url, files, options, callback) = it
            postMultipartRhinoWithRuntime(scriptRuntime, url, files, options, callback)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun postMultipartRhinoWithRuntime(scriptRuntime: ScriptRuntime, url: Any?, files: Any?, options: Any? = null, callback: Any? = null): Any {
            val niceOptions = if (options.isJsNullish()) newNativeObject() else options
            require(niceOptions is NativeObject) { "Argument \"options\" ${options.jsBrief()} for http.postMultipart] must be a JavaScript Object" }
            listOf(
                KEY_METHOD to METHOD_POST,
                KEY_CONTENT_TYPE to Mime.MULTIPART_FORM_DATA,
                KEY_FILES to files,
            ).let { list -> put(niceOptions, list) }
            return requestRhinoWithRuntime(scriptRuntime, url, niceOptions, callback)
        }

        private fun fillPostData(options: NativeObject, data: Any?) {
            when (options.prop(KEY_CONTENT_TYPE)) {
                DEFAULT_CONTENT_TYPE -> {
                    val builder = FormBody.Builder()
                    coerceObject(data, newNativeObject()).forEach { (key, value) ->
                        builder.add(coerceString(key), Context.toString(value))
                    }
                    options.put(KEY_BODY, options, builder.build())
                }
                Mime.APPLICATION_JSON -> {
                    options.put(KEY_BODY, options, js_json_stringify(data))
                }
                else -> {
                    options.put(KEY_BODY, options, data)
                }
            }
        }

        private fun MutableOkHttp.applyOkHttpClientBuilder(opt: NativeObject) {
            val clientProp = opt.prop(KEY_CLIENT).takeUnless { it.isJsNullish() }
            require(clientProp is NativeObject?) { "Argument \"client\" ${clientProp.jsBrief()} for http.request must be a JavaScript Object" }
            clientProp ?: return

            val timeout = coerceLongNumber(opt.prop(KEY_TIMEOUT), DEFAULT_TIMEOUT)
            val isInsecure = opt.inquire(listOf("isInsecure", "insecure"), ::coerceBoolean, false)

            val builder = this.client().newBuilder()
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)

            val builderClass = OkHttpClient.Builder::class.java

            fun coerceArg(paramType: Class<*>, value: Any?) = when {
                paramType == java.lang.Boolean.TYPE || paramType == java.lang.Boolean::class.java -> coerceBoolean(value, false)
                paramType == java.lang.Long.TYPE || paramType == java.lang.Long::class.java -> coerceLongNumber(value, 0L)
                paramType == Integer.TYPE || paramType == Integer::class.java -> coerceIntNumber(value, 0)
                paramType == java.lang.Double.TYPE || paramType == java.lang.Double::class.java -> coerceNumber(value, 0.0)
                paramType == String::class.java -> coerceString(value, "")
                paramType == TimeUnit::class.java -> when (value) {
                    is TimeUnit -> value
                    is String -> runCatching { TimeUnit.valueOf(value.trim().uppercase()) }.getOrElse {
                        throw WrappedIllegalArgumentException("Invalid TimeUnit string: $value")
                    }
                    else -> throw WrappedIllegalArgumentException("Invalid TimeUnit argument: ${value.jsBrief()}")
                }
                // Java object: Proxy/Dispatcher/ConnectionPool/Authenticator/SSLSocketFactory/...
                paramType.isInstance(value) -> value
                // Let reflection verify the type matching by itself.
                // zh-CN: 让反射自行校验类型是否匹配.
                else -> value
            }

            fun findCandidateMethods(name: String): List<Method> {
                return builderClass.methods.filter { it.name == name && it.declaringClass == builderClass }
            }

            clientProp.forEach { entry ->
                val (rawKey, rawVal) = entry
                val methodName = coerceString(rawKey, "").trim()
                if (methodName.isEmpty()) return@forEach

                val candidates = findCandidateMethods(methodName)
                require(candidates.isNotEmpty()) {
                    "No such Builder method: $methodName on ${builderClass.name}"
                }

                val zeroArg = candidates.firstOrNull { it.parameterCount == 0 }
                require(zeroArg == null) {
                    "Builder method \"$methodName\" with 0 parameter is not allowed to be invoked via client options"
                }
                val twoArgs = candidates.firstOrNull { it.parameterCount == 2 }
                if (twoArgs != null && rawVal is List<*> && rawVal.size == 2) {
                    val args2 = arrayOf(coerceArg(twoArgs.parameterTypes[0], rawVal[0]), coerceArg(twoArgs.parameterTypes[1], rawVal[1]))
                    twoArgs.invoke(builder, args2[0], args2[1])
                    return@forEach
                }

                val oneArg = candidates.firstOrNull { it.parameterCount == 1 }
                if (oneArg != null) {
                    val paramTypes = oneArg.parameterTypes
                    val arg0 = coerceArg(paramTypes[0], rawVal)
                    oneArg.invoke(builder, arg0)
                    return@forEach
                }

                val supported = candidates.joinToString { "(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                throw WrappedIllegalArgumentException(
                    "Builder method \"$methodName\" is not invokable with 1 or 2 parameters via client options. Supported overloads: $supported"
                )
            }

            if (isInsecure) {
                @SuppressLint("CustomX509TrustManager")
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS").apply {
                    init(null, trustAllCerts, SecureRandom())
                }
                val trustManager = trustAllCerts.first() as X509TrustManager
                builder.sslSocketFactory(sslContext.socketFactory, trustManager)
                builder.hostnameVerifier { _, _ -> true }
            }

            // Apply the new Builder to the internal client.
            // zh-CN: 应用新的 Builder 到内部客户端.
            muteClient(builder)
        }

        private class ResponseWrapper(
            private val scriptRuntime: ScriptRuntime,
            private val res: Response,
            private val cacheBody: Boolean,
            private val cacheThresholdBytes: Long,
        ) {
            private val mRequest = res.request

            var resBodyString: String? = null
            var resBodyBytes: ByteArray? = null

            fun wrap() = newNativeObject().apply {
                put("request", this, mRequest)
                put("statusMessage", this, res.message)
                put("statusCode", this, res.code)
                put("body", this, getBody())
                put("headers", this, getHeaders())
                put("url", this, mRequest.url)
                put("method", this, mRequest.method)
            }

            private fun getBody(): ResponseBodyNativeObject {

                // Returns a non-null value if this response
                // was passed to Callback.onResponse
                // or returned from Call.execute.
                val resBody = res.body!!

                return ResponseBodyNativeObject(scriptRuntime, resBody, this, cacheBody, cacheThresholdBytes).also {
                    it.defineFunctionProperties(arrayOf("string", "bytes", "json", "stream", "saveToFile", "close"), it.javaClass, READONLY or PERMANENT)
                    it.defineProperty(KEY_CONTENT_TYPE, { resBody.contentType() }, null, READONLY or PERMANENT)
                }
            }

            private fun getHeaders(): NativeObject {
                val result = newNativeObject()
                val headers = res.headers
                for (i in 0 until headers.size) {
                    val name = headers.name(i).lowercase()
                    val value = headers.value(i)
                    if (!result.containsKey(name)) {
                        result.put(name, result, value)
                        continue
                    }
                    val list = mutableListOf<Any?>()
                    val origin = result.prop(name)
                    if (origin !is NativeArray) {
                        list += origin
                    } else {
                        list.addAll(origin)
                    }
                    list += value
                    result.put(name, result, list.toNativeArray())
                }
                return result
            }

        }

        class HttpSaveResult @JvmOverloads constructor(
            private val resultCode: Int,
            private val outPath: String?,
            private val bytesCopied: Long,
            private val error: Throwable? = null,
        ) : NativeObject(), StringReadable {

            init {
                RhinoUtils.initNativeObjectPrototype(this)
                defineProperty("code", { resultCode }, null, READONLY or PERMANENT)
                defineProperty("path", { outPath }, null, READONLY or PERMANENT)
                defineProperty("bytesCopied", { bytesCopied }, null, READONLY or PERMANENT)
                defineProperty("success", { resultCode == RESULT_OK }, null, READONLY or PERMANENT)
                defineProperty("error", { error }, null, READONLY or PERMANENT)
                defineFunctionProperties(arrayOf("isSuccess"), javaClass, READONLY or PERMANENT)
            }

            override fun toStringReadable(): String = listOf(
                "${HttpSaveResult::class.java.simpleName} {",
                "  code: ${resultCode},",
                "  bytesCopied: $bytesCopied (${Bytes.string(bytesCopied.toDouble(), useSpace = true, strict = true)}),",
                "  path: '${outPath}',",
                "  error: ${error?.message?.take(256)?.let { "'$it'" }},",
                "}",
            ).joinToString("\n")

            companion object {

                @JvmField
                val RESULT_OK = 0

                private const val RESULT_FAILED_GENERIC = -1

                fun ok(path: String, bytesCopied: Long) =
                    HttpSaveResult(RESULT_OK, path, bytesCopied, null)

                fun fail(path: String?, bytesCopied: Long, e: Throwable?) =
                    HttpSaveResult(RESULT_FAILED_GENERIC, path, bytesCopied, e)

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun isSuccess(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Boolean = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as HttpSaveResult
                    o.resultCode == RESULT_OK
                }

            }
        }

        @Suppress("unused")
        private class ResponseBodyNativeObject(
            val scriptRuntime: ScriptRuntime,
            val resBody: ResponseBody,
            val responseWrapper: ResponseWrapper,
            private val cacheBody: Boolean,
            private val cacheThresholdBytes: Long,
        ) : NativeObject() {

            init {
                RhinoUtils.initNativeObjectPrototype(this)
            }

            // Record whether it has been explicitly closed.
            // zh-CN: 记录是否已显式关闭.
            @Volatile
            private var closed = false

            private fun ensureOpen() {
                if (closed) throw IllegalStateException("Response body already closed")
            }

            private fun autoCloseIfNeeded() {
                // Close immediately after reading the complete content to avoid resource leaks;
                // stream() close is handled by the caller.
                // zh-CN: 读取完整内容后立即关闭, 避免资源泄露; stream() 由调用者负责 close().
                if (!closed) {
                    runCatching { resBody.close() }
                    closed = true
                }
            }

            private fun shouldCache(lengthHint: Long?): Boolean {
                if (!cacheBody) return false
                if (lengthHint == null || lengthHint < 0) return true
                return lengthHint <= cacheThresholdBytes
            }

            companion object : FlexibleArray() {

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun string(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as ResponseBodyNativeObject
                    o.responseWrapper.resBodyString?.let { return@ensureArgumentsIsEmpty it }
                    o.ensureOpen()
                    val contentLength = runCatching {
                        o.resBody.contentLength()
                    }.getOrDefault(-1L)
                    val str = o.resBody.string()
                    if (o.shouldCache(contentLength)) {
                        o.responseWrapper.resBodyString = str
                    }
                    // string() can safely close after consuming the stream.
                    // zh-CN: string() 消费流后可安全关闭.
                    o.autoCloseIfNeeded()
                    return@ensureArgumentsIsEmpty str
                }

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun bytes(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ByteArray = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as ResponseBodyNativeObject
                    o.responseWrapper.resBodyBytes?.let { return@ensureArgumentsIsEmpty it }
                    o.ensureOpen()
                    val contentLength = runCatching {
                        o.resBody.contentLength()
                    }.getOrDefault(-1L)
                    val data = o.resBody.bytes()
                    if (o.shouldCache(contentLength)) {
                        o.responseWrapper.resBodyBytes = data
                    }
                    // bytes() 消费流后可安全关闭
                    o.autoCloseIfNeeded()
                    return@ensureArgumentsIsEmpty data
                }

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun json(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsIsEmpty(args) {
                    val str = string(cx, thisObj, args, funObj)
                    runCatching {
                        return@ensureArgumentsIsEmpty js_json_parse(str)
                    }.onFailure {
                        throw IllegalStateException("Failed to parse JSON. Body string may be not in JSON format")
                    }
                }

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun stream(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): InputStream = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as ResponseBodyNativeObject
                    o.ensureOpen()
                    // Don't auto-close; let the caller handle it, supporting streaming copy.
                    // zh-CN: 不自动关闭; 交给调用者处理, 支持流式拷贝.
                    o.resBody.byteStream()
                }

                // Save directly to file (avoid loading large responses into memory).
                // zh-CN: 直接保存到文件 (避免将大型响应加载到内存中).
                @JvmStatic
                @RhinoStandardFunctionInterface
                fun saveToFile(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): HttpSaveResult = ensureArgumentsLengthInRange(args, 1..2) { argList ->
                    val o = thisObj as ResponseBodyNativeObject
                    o.ensureOpen()

                    val (pathRaw, bufSizeRaw) = argList
                    val path = coerceString(pathRaw, "").toRuntimePath(o.scriptRuntime)
                    val bufSize = coerceIntNumber(bufSizeRaw, 0).let {
                        if (it > 0) it else 8192
                    }

                    val file = PFile(path)
                    val isDirectory = file.isDirectory || path.endsWith("/")
                    if (isDirectory) {
                        throw WrappedIllegalArgumentException("Path \"$path\" must be a file path instead of a directory path")
                    }
                    val buffer = ByteArray(bufSize)
                    var copied = 0L
                    var input: InputStream? = null
                    var output: OutputStream? = null
                    return@ensureArgumentsLengthInRange try {
                        input = o.resBody.byteStream()
                        output = file.outputStream()
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                        }
                        output.flush()
                        HttpSaveResult.ok(path, copied)
                    } catch (e: Throwable) {
                        HttpSaveResult.fail(path, copied, e)
                    } finally {
                        runCatching { input?.close() }
                        runCatching { output?.close() }
                        o.autoCloseIfNeeded()
                    }
                }

                // Explicit close.
                // zh-CN: 显式关闭.
                @JvmStatic
                @RhinoStandardFunctionInterface
                fun close(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Undefined = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as ResponseBodyNativeObject
                    if (!o.closed) {
                        runCatching { o.resBody.close() }
                        o.closed = true
                    }
                    UNDEFINED
                }
            }

        }

        private class RequestBuilder(private val scriptRuntime: ScriptRuntime, private val url: Any?, options: NativeObject = newNativeObject()) {

            private val mRequest = Request.Builder()
            private val mRequestBuilderHelper = RequestBuilderHelper(options)

            fun build(): Request {
                mRequest.url(mRequestBuilderHelper.getUrl(coerceString(url)))
                mRequestBuilderHelper.setHeaders(mRequest)
                mRequestBuilderHelper.setMethod(scriptRuntime, mRequest)
                return mRequest.build()
            }

        }

        private class RequestBuilderHelper(private val options: NativeObject) {

            @Suppress("HttpUrlsUsage")
            fun getUrl(url: String) = when {
                url.matches(Regex("^https?://.*")) -> url
                else -> "http://$url"
            }

            fun setHeaders(request: Request.Builder) {
                val headers = options.prop(KEY_HEADERS)
                if (headers.isJsNullish()) return
                require(headers is NativeObject) { "Property headers ${headers.jsBrief()} for builder of http.request must be a JavaScript Object" }
                headers.forEach { entry ->
                    val (key, value) = entry
                    when (value) {
                        is NativeArray -> value.forEach { setHeader(request, key, it) }
                        else -> setHeader(request, key, value)
                    }
                }
            }

            fun setMethod(scriptRuntime: ScriptRuntime, request: Request.Builder) {
                val method = coerceString(options.prop(KEY_METHOD))
                // require(method is String) { "Property method is required for header options" }
                when {
                    !options.prop(KEY_BODY).isJsNullish() -> {
                        request.method(method, parseBody())
                    }
                    !options.prop(KEY_FILES).isJsNullish() -> {
                        request.method(method, parseMultipart(scriptRuntime))
                    }
                    else -> {
                        request.method(method, null)
                    }
                }
            }

            fun parseBody(): RequestBody = when (val body = options.prop(KEY_BODY)) {
                is RequestBody -> body
                is String -> {
                    val mediaType = options.prop(KEY_CONTENT_TYPE).takeUnless { it.isJsNullish() }
                    body.toRequestBody(Context.toString(mediaType).toMediaTypeOrNull())
                }
                is BaseFunction -> object : RequestBody() {
                    override fun contentType(): MediaType? {
                        val mediaType = options.prop(KEY_CONTENT_TYPE).takeUnless { it.isJsNullish() }
                        return Context.toString(mediaType).toMediaTypeOrNull()
                    }

                    override fun writeTo(sink: BufferedSink) {
                        withRhinoContext { cx ->
                            body.call(cx, body, body, arrayOf(sink))
                        }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Unknown type of body for header options")
            }

            fun parseMultipart(scriptRuntime: ScriptRuntime): MultipartBody {
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

                val files = options.prop(KEY_FILES)
                if (files.isJsNullish()) return builder.build()
                require(files is NativeObject) { "Property files ${files.jsBrief()} for builder of http.request must be a JavaScript Object" }

                files.forEach { entry ->
                    val (key, value) = entry
                    when (value) {
                        is String -> {
                            builder.addFormDataPart(coerceString(key), value)
                        }
                        is NativeArray -> when (value.length) {
                            2L -> {
                                val (fileName, path) = value
                                val file = if (path is URI) PFile(path) else PFile(path.toRuntimePath(scriptRuntime))
                                val mimeType = parseMimeType(file.extension)
                                val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                                processFile(builder, key, fileName, requestBody)
                            }
                            3L -> {
                                val (fileName, mimeType, path) = value
                                val file = if (path is URI) PFile(path) else PFile(path.toRuntimePath(scriptRuntime))
                                val requestBody = file.asRequestBody(coerceString(mimeType).toMediaTypeOrNull())
                                processFile(builder, key, fileName, requestBody)
                            }
                            else -> listOf(
                                "Array value \"value\" for property \"files\"",
                                "in RequestBuilderHelper#parseMultipart",
                                "must be of length 2 or 3 instead of ${value.length}",
                            ).joinToString(" ").let { throw WrappedIllegalArgumentException(it) }
                        }
                        is PFileInterface -> {
                            val path = value.path
                            val file = PFile(path.toRuntimePath(scriptRuntime))
                            val fileName = file.name
                            val mimeType = parseMimeType(file.extension)
                            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                            processFile(builder, key, fileName, requestBody)
                        }
                        else -> listOf(
                            "Value \"value\" for property \"files\"",
                            "in RequestBuilderHelper#parseMultipart",
                            "must be either a string, an array",
                            "or a JavaScript object",
                        ).joinToString(" ").let { throw WrappedIllegalArgumentException(it) }
                    }
                }

                return builder.build()
            }

            fun parseMimeType(ext: String) = when {
                ext.isNotEmpty() -> {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: Mime.APPLICATION_OCTET_STREAM
                }
                else -> Mime.APPLICATION_OCTET_STREAM
            }

            private fun setHeader(request: Request.Builder, key: Any?, it: Any?) {
                request.header(coerceString(key), Context.toString(it))
            }

            private fun processFile(builder: MultipartBody.Builder, key: Any?, fileName: Any?, requestBody: RequestBody) {
                builder.addFormDataPart(coerceString(key), fileName as? String, requestBody)
            }

        }

    }

}
