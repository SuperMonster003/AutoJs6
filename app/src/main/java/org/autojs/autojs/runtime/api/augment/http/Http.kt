package org.autojs.autojs.runtime.api.augment.http

import android.webkit.MimeTypeMap
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
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
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.continuation.Continuation
import org.autojs.autojs.runtime.api.augment.continuation.Creator
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
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
import java.io.IOException
import java.net.URI

@Suppress("unused", "UNUSED_PARAMETER")
class Http(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentProperties = listOf(
        "__okhttp__" to scriptRuntime.http.okhttp to DONTENUM
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

        private const val KEY_METHOD = "method"
        private const val KEY_CONTENT_TYPE = "contentType"
        private const val KEY_HEADERS = "headers"
        private const val KEY_FILES = "files"
        private const val KEY_BODY = "body"
        private const val KEY_TIMEOUT = "timeout"
        private const val KEY_MAX_RETRIES = "maxRetries"

        private val DEFAULT_CONTENT_TYPE = Mime.APPLICATION_X_WWW_FORM_URLENCODED

        @JvmField
        val DEFAULT_TIMEOUT = MutableOkHttp.DEFAULT_TIMEOUT

        @JvmField
        val DEFAULT_MAX_RETRIES = MutableOkHttp.DEFAULT_MAX_RETRIES

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun buildRequest(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Request = ensureArgumentsLengthInRange(args, 1..2) {
            val (url, options) = it
            buildRequestRhino(url, options)
        }

        @JvmStatic
        @JvmOverloads
        @RhinoFunctionBody
        fun buildRequestRhino(url: Any?, options: Any? = null): Request = when {
            options.isJsNullish() -> RequestBuilder(url).build()
            options is NativeObject -> RequestBuilder(url, options).build()
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

            scriptRuntime.http.okhttp.timeout = coerceLongNumber(opt.prop(KEY_TIMEOUT), DEFAULT_TIMEOUT)
            scriptRuntime.http.okhttp.maxRetries = coerceIntNumber(opt.prop(KEY_MAX_RETRIES), DEFAULT_MAX_RETRIES)

            val newCall = scriptRuntime.http.client().newCall(buildRequestRhino(url, options))

            if (!callback.isJsFunction() && cont == null) {
                return ResponseWrapper(newCall.execute()).wrap()
            }

            newCall.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val wrappedResponse = ResponseWrapper(response).wrap()
                    cont?.resume(wrappedResponse)
                    if (callback is BaseFunction) withRhinoContext { cx ->
                        callback.call(cx, callback, callback, arrayOf(wrappedResponse, null))
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    cont?.resumeError(e)
                    if (callback is BaseFunction) withRhinoContext { cx ->
                        callback.call(cx, callback, callback, arrayOf(null, e))
                    }
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
            require(niceOptions is NativeObject) { "Argument options ${options.jsBrief()} for http.get must be a JavaScript Object" }
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
            require(niceOptions is NativeObject) { "Argument options ${options.jsBrief()} for http.post must be a JavaScript Object" }
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
            require(niceOptions is NativeObject) { "Argument options ${options.jsBrief()} for http.postJson] must be a JavaScript Object" }
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
            require(niceOptions is NativeObject) { "Argument options ${options.jsBrief()} for http.postMultipart] must be a JavaScript Object" }
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

        private class ResponseWrapper(private val res: Response) {

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

                return ResponseBodyNativeObject(resBody, this).also {
                    it.defineFunctionProperties(arrayOf("string", "bytes", "json"), it.javaClass, READONLY or PERMANENT)
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

        @Suppress("unused")
        private class ResponseBodyNativeObject(val resBody: ResponseBody, val responseWrapper: ResponseWrapper) : NativeObject() {

            init {
                RhinoUtils.initNativeObjectPrototype(this)
            }

            companion object : FlexibleArray() {

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun string(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as ResponseBodyNativeObject
                    o.responseWrapper.resBodyString ?: o.resBody.string().also { o.responseWrapper.resBodyString = it }
                }

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun bytes(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): ByteArray = ensureArgumentsIsEmpty(args) {
                    val o = thisObj as ResponseBodyNativeObject
                    o.responseWrapper.resBodyBytes ?: o.resBody.bytes().also { o.responseWrapper.resBodyBytes = it }
                }

                @JvmStatic
                @RhinoStandardFunctionInterface
                fun json(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): Any? = ensureArgumentsIsEmpty(args) {
                    val str = string(cx, thisObj, args, funObj)
                    try {
                        js_json_parse(str)
                    } catch (_: Exception) {
                        throw IllegalStateException("Failed to parse JSON. Body string may be not in JSON format")
                    }
                }

            }

        }

        private class RequestBuilder(private val url: Any?, options: NativeObject = newNativeObject()) {

            private val mRequest = Request.Builder()
            private val mRequestBuilderHelper = RequestBuilderHelper(options)

            fun build(): Request {
                mRequest.url(mRequestBuilderHelper.getUrl(coerceString(url)))
                mRequestBuilderHelper.setHeaders(mRequest)
                mRequestBuilderHelper.setMethod(mRequest)
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

            fun setMethod(request: Request.Builder) {
                val method = coerceString(options.prop(KEY_METHOD))
                // require(method is String) { "Property method is required for header options" }
                when {
                    !options.prop(KEY_BODY).isJsNullish() -> {
                        request.method(method, parseBody())
                    }
                    !options.prop(KEY_FILES).isJsNullish() -> {
                        request.method(method, parseMultipart())
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

            fun parseMultipart(): MultipartBody {
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
                                val file = if (path is URI) PFile(path) else PFile(coerceString(path))
                                val mimeType = parseMimeType(file.extension)
                                val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                                processFile(builder, key, fileName, requestBody)
                            }
                            3L -> {
                                val (fileName, mimeType, path) = value
                                val file = if (path is URI) PFile(path) else PFile(coerceString(path))
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
                            val file = PFile(path)
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
