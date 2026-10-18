package org.autojs.autojs.engine

import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.charset.Charset

/** Immutable decisions published before WebView invokes its synchronous callbacks. */
internal class RuntimeWebPolicy(options: JSONObject = JSONObject()) {
    private class Match(value: JSONObject) {
        val url = value.optString("url").takeIf { value.has("url") }
        val prefix = value.optString("urlPrefix").takeIf { value.has("urlPrefix") }
        val method = value.optString("method").uppercase().takeIf { value.has("method") }
        val mainFrame = value.opt("mainFrame")?.let(RuntimeViewAttributes::boolean)
        init { require(url != null || prefix != null) { "A WebView rule requires url or urlPrefix" } }
        fun accepts(request: WebResourceRequest) = (url == null || request.url.toString() == url) &&
            (prefix == null || request.url.toString().startsWith(prefix)) && (method == null || request.method == method) &&
            (mainFrame == null || request.isForMainFrame == mainFrame)
    }
    private class Response(value: JSONObject) {
        private val mime = value.optString("mimeType", "text/plain")
        private val encoding = value.optString("encoding", "UTF-8")
        private val status = value.opt("statusCode")?.let { RuntimeViewAttributes.integer(it, 100, 599) } ?: 200
        private val reason = value.optString("reasonPhrase", "OK")
        private val headers = (value.optJSONObject("headers") ?: JSONObject()).let { json ->
            json.keys().asSequence().associateWith { key -> json.getString(key) }
        }
        private val bytes = if (value.has("bodyBase64")) Base64.decode(value.getString("bodyBase64"), Base64.DEFAULT)
            else value.optString("body").toByteArray(Charset.forName(encoding))
        init {
            require(status !in 300..399 && reason.isNotBlank() && reason.all { it.code in 32..126 }) { "Invalid WebResourceResponse status/reason" }
            require(!value.has("body") || !value.has("bodyBase64")) { "Use body or bodyBase64, not both" }
        }
        fun open() = WebResourceResponse(mime, encoding, status, reason, headers, ByteArrayInputStream(bytes))
    }
    private val navigation = (options.optJSONArray("navigationRules") ?: JSONArray()).map { value ->
        Match(value) to when (value.getString("action")) { "allow" -> false; "block" -> true; else -> error("Navigation action must be allow or block") }
    }
    private val resources = (options.optJSONArray("resourceRules") ?: JSONArray()).map { value -> Match(value) to Response(value.getJSONObject("response")) }
    val keyCodes = (options.optJSONArray("blockedKeyCodes") ?: JSONArray()).let { list ->
        (0 until list.length()).map { RuntimeViewAttributes.integer(list.get(it), 0, Int.MAX_VALUE) }.toSet()
    }
    fun navigation(request: WebResourceRequest) = navigation.firstOrNull { it.first.accepts(request) }?.second ?: false
    fun resource(request: WebResourceRequest) = resources.firstOrNull { it.first.accepts(request) }?.second?.open()

    companion object {
        private fun <T> JSONArray.map(block: (JSONObject) -> T): List<T> {
            require(length() <= 128) { "Too many WebView rules" }
            return (0 until length()).map { block(getJSONObject(it)) }
        }
        fun request(request: WebResourceRequest) = JSONObject().put("url", request.url.toString()).put("method", request.method)
            .put("mainFrame", request.isForMainFrame).put("hasGesture", request.hasGesture()).put("redirect", request.isRedirect)
            .put("headers", JSONObject(request.requestHeaders ?: emptyMap<String, String>()))
    }
}
