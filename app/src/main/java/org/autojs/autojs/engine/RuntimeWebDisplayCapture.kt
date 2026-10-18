package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Explicit same-page adapter for WebViews without a native screen-capture implementation. */
internal class RuntimeWebDisplayCapture(private val web: RuntimeWebView) : Closeable {
    val id = UUID.randomUUID().toString()
    private val key = JSONObject.quote("__autojs6_display_" + id.replace("-", ""))
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private var capture: NodeScreenCaptureSession? = null
    private var requestId: String? = null
    private var consentTimeout = 120000L
    private var interval = 200L
    private var maxWidth = 1280; private var maxHeight = 720
    private var activeWidth = 1280; private var activeHeight = 720
    private var minimumInterval = 200L
    private var framePending = false
    private val tick = Runnable { poll() }

    fun install(options: JSONObject, complete: (Result<Any?>) -> Unit) {
        val origin = URI(options.getString("origin"))
        require(origin.scheme in listOf("https", "http") && origin.host != null && origin.rawUserInfo == null && origin.rawQuery == null && origin.rawFragment == null && origin.rawPath.orEmpty() in listOf("", "/")) { "origin must be an HTTP(S) origin" }
        maxWidth = options.opt("maxWidth")?.let { RuntimeViewAttributes.integer(it, 1, 8192) } ?: 1280
        maxHeight = options.opt("maxHeight")?.let { RuntimeViewAttributes.integer(it, 1, 8192) } ?: 720
        val frameRate = options.opt("frameRate")?.let { RuntimeViewAttributes.integer(it, 1, 60) } ?: 5
        interval = 1000L / frameRate
        minimumInterval = interval
        consentTimeout = options.opt("timeoutMs")?.let { RuntimeViewAttributes.integer(it, 100, 120000).toLong() } ?: 120000
        val config = JSONObject().put("origin", options.getString("origin")).put("maxWidth", maxWidth).put("maxHeight", maxHeight).put("frameRate", frameRate)
        val source = web.context.assets.open("runtime-web/display-capture.js").bufferedReader().use { it.readText() }
        web.evaluate("($source)($key,$config)", { result ->
            val value = result.mapCatching { check(it is JSONObject && it.optBoolean("installed")) { "Display capture installation failed: check secure context, origin and existing native support" }; it.put("id", id) }
            if (closed) complete(Result.failure(IllegalStateException("Display capture installation cancelled")))
            else { if (value.isSuccess) main.post(tick) else close(); complete(value) }
        }, 5000)
    }
    private fun evaluate(expression: String, reply: (Result<Any?>) -> Unit = {}) {
        if (!closed && !web.disposed) web.evaluate("window[$key] && $expression", reply, 5000)
    }
    private fun later() { if (!closed) main.postDelayed(tick, interval) }
    private fun poll() {
        if (closed || web.disposed) return
        evaluate("window[$key].poll()") { result ->
            if (closed) return@evaluate
            val value = result.getOrNull() as? JSONObject
            if (value == null || value.optBoolean("disabled")) { close(); return@evaluate }
            try { when {
                value.has("request") -> request(value)
                value.has("stop") -> if (value.getString("stop") == requestId) stop()
                !value.isNull("frame") && value.getString("frame") == requestId && !framePending -> frame()
            } } catch (error: Throwable) { reject(value.optString("request", requestId.orEmpty()), error.message ?: "Invalid display request", "TypeError"); stop() }
            later()
        }
    }
    private fun request(value: JSONObject) {
        val request = value.getString("request")
        if (!web.isShown || !web.hasWindowFocus()) { reject(request, "Screen sharing must start from a visible page", "InvalidStateError"); return }
        stop()
        activeWidth = RuntimeViewAttributes.integer(value.get("maxWidth"), 1, maxWidth)
        activeHeight = RuntimeViewAttributes.integer(value.get("maxHeight"), 1, maxHeight)
        val fps = value.getDouble("frameRate"); require(fps.isFinite() && fps > 0 && fps <= 60)
        interval = (1000 / fps).toLong().coerceAtLeast(minimumInterval)
        requestId = request
        val session = NodeScreenCaptureSession(web.context) { main.post { if (requestId == request) { reject(request, "Android screen sharing stopped", "AbortError"); stop() } } }
        capture = session
        session.request(JSONObject(), consentTimeout) { result ->
            if (closed || capture !== session) return@request
            result.fold({ info ->
                val width = info.getInt("width"); val height = info.getInt("height")
                val scale = minOf(1.0, activeWidth.toDouble() / width, activeHeight.toDouble() / height)
                evaluate("window[$key].accept(${JSONObject.quote(request)},${maxOf(1, (width * scale).roundToInt())},${maxOf(1, (height * scale).roundToInt())})") { accepted ->
                    if (accepted.getOrNull() != true && capture === session) stop()
                }
            }, { error -> reject(request, error.message ?: "Screen sharing denied", if (error is SecurityException) "NotAllowedError" else "AbortError"); stop() })
        }
    }
    private fun frame() {
        val session = capture ?: return; val request = requestId ?: return
        framePending = true
        io.execute {
            val result = runCatching {
                val bitmap = session.capture()
                try {
                    val scale = minOf(1.0, activeWidth.toDouble() / bitmap.width, activeHeight.toDouble() / bitmap.height)
                    val scaled = if (scale == 1.0) bitmap else Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).roundToInt()), maxOf(1, (bitmap.height * scale).roundToInt()), true)
                    try { ByteArrayOutputStream().use { output -> check(scaled.compress(Bitmap.CompressFormat.JPEG, 75, output)); Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP) } }
                    finally { if (scaled !== bitmap) scaled.recycle() }
                } finally { bitmap.recycle() }
            }
            main.post {
                framePending = false
                if (!closed && capture === session) result.fold({ data -> evaluate("window[$key].frame(${JSONObject.quote(request)},${JSONObject.quote(data)})") },
                    { error -> reject(request, error.message ?: "Screen frame failed", "NotReadableError"); stop() })
            }
        }
    }
    private fun reject(request: String, message: String, name: String) {
        evaluate("window[$key].reject(${JSONObject.quote(request)},${JSONObject.quote(message)},${JSONObject.quote(name)})")
    }
    private fun stop() { val previous = capture; capture = null; requestId = null; if (previous != null) io.execute { previous.close() } }
    override fun close() {
        if (closed) return
        closed = true; main.removeCallbacks(tick); stop()
        if (!web.disposed) runCatching { web.evaluateJavascript("window[$key] && window[$key].close()", null) }
    }
    companion object { private val io = Executors.newCachedThreadPool { task -> Thread(task, "web-display-capture").apply { isDaemon = true } } }
}
