package org.autojs.autojs.runtime.api.augment.http

import android.webkit.MimeTypeMap
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.autojs.autojs.pio.PFile
import org.autojs.autojs.pio.PFileInterface
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import java.net.URI

class RequestBuilderHelper(private val options: NativeObject) {

    @Suppress("HttpUrlsUsage")
    fun getUrl(url: String) = when {
        url.matches(Regex("^https?://.*")) -> url
        else -> "http://$url"
    }

    fun setHeaders(request: Request.Builder) {
        val headers = options.prop(Http.KEY_HEADERS)
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
        val method = RhinoUtils.coerceString(options.prop(Http.KEY_METHOD))
        // require(method is String) { "Property method is required for header options" }
        when {
            !options.prop(Http.KEY_BODY).isJsNullish() -> {
                request.method(method, parseBody(scriptRuntime))
            }
            !options.prop(Http.KEY_FILES).isJsNullish() -> {
                request.method(method, parseMultipart(scriptRuntime))
            }
            else -> {
                request.method(method, null)
            }
        }
    }

    fun parseBody(scriptRuntime: ScriptRuntime): RequestBody = when (val body = options.prop(Http.KEY_BODY)) {
        is RequestBody -> body
        is String -> {
            val mediaType = options.prop(Http.KEY_CONTENT_TYPE).takeUnless { it.isJsNullish() }
            body.toRequestBody(Context.toString(mediaType).toMediaTypeOrNull())
        }
        is BaseFunction -> object : RequestBody() {
            override fun contentType(): MediaType? {
                val mediaType = options.prop(Http.KEY_CONTENT_TYPE).takeUnless { it.isJsNullish() }
                return Context.toString(mediaType).toMediaTypeOrNull()
            }

            override fun writeTo(sink: BufferedSink) {
                RhinoUtils.withRhinoContext(scriptRuntime) { cx ->
                    body.call(cx, body, body, arrayOf(sink))
                }
            }
        }
        else -> throw WrappedIllegalArgumentException("Unknown type of body for header options")
    }

    fun parseMultipart(scriptRuntime: ScriptRuntime): MultipartBody {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)

        val files = options.prop(Http.KEY_FILES)
        if (files.isJsNullish()) return builder.build()
        require(files is NativeObject) { "Property files ${files.jsBrief()} for builder of http.request must be a JavaScript Object" }

        files.forEach { entry ->
            val (key, value) = entry
            when {
                value.isJsString() || value.isJsNumber() -> {
                    builder.addFormDataPart(RhinoUtils.coerceString(key), RhinoUtils.coerceString(value))
                }
                value is NativeArray -> when (value.length) {
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
                        val requestBody = file.asRequestBody(RhinoUtils.coerceString(mimeType).toMediaTypeOrNull())
                        processFile(builder, key, fileName, requestBody)
                    }
                    else -> listOf(
                        "Array value \"value\" for property \"files\"",
                        "in RequestBuilderHelper#parseMultipart",
                        "must be of length 2 or 3 instead of ${value.length}",
                    ).joinToString(" ").let { throw WrappedIllegalArgumentException(it) }
                }
                value is PFileInterface -> {
                    val path = value.path
                    val file = PFile(path.toRuntimePath(scriptRuntime))
                    val fileName = file.name
                    val mimeType = parseMimeType(file.extension)
                    val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
                    processFile(builder, key, fileName, requestBody)
                }
                else -> listOf(
                    "Value \"value\" ${value.jsBrief()} for property \"files\"",
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
        request.header(RhinoUtils.coerceString(key), Context.toString(it))
    }

    private fun processFile(builder: MultipartBody.Builder, key: Any?, fileName: Any?, requestBody: RequestBody) {
        builder.addFormDataPart(RhinoUtils.coerceString(key), fileName as? String, requestBody)
    }

}