package org.autojs.autojs.runtime.api.modules.http

import okhttp3.Response
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY

class ResponseWrapper(
    private val scriptRuntime: ScriptRuntime,
    private val res: Response,
    private val cacheBody: Boolean,
    private val cacheThresholdBytes: Long,
) {
    private val mRequest = res.request

    var resBodyString: String? = null
    var resBodyBytes: ByteArray? = null

    fun wrap() = newNativeObject(scriptRuntime.topLevelScope).apply {
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
            RhinoUtils.defineFunctionProperties(scriptRuntime.topLevelScope, it, arrayOf("string", "bytes", "json", "stream", "saveToFile", "close"), it.javaClass, READONLY or PERMANENT)
            it.defineProperty(Http.KEY_CONTENT_TYPE, { resBody.contentType() }, null, READONLY or PERMANENT)
        }
    }

    private fun getHeaders(): NativeObject {
        val scope = scriptRuntime.topLevelScope
        val result = newNativeObject(scope)
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
            result.put(name, result, newNativeArray(scope, list.toTypedArray()))
        }
        return result
    }

}
