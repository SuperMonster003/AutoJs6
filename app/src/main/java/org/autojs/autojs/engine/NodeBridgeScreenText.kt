package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.plugin.paddle.ocr.api.OcrOptions
import org.autojs.plugin.paddle.ocr.api.OcrResult
import org.autojs.plugin.paddle.ocr.api.PaddleOcrOptionExtraKeys as K
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** One provider owns the work, captures and deadlines. No image handle is returned to Agent. */
internal class NodeBridgeScreenText(
    context: Context,
    private val fallbackCapture: () -> Bitmap,
    private val recognizer: Recognizer = AndroidRecognizer(context.applicationContext),
    private val captureOverride: ((Long) -> Bitmap)? = null,
) : Closeable {
    fun interface Recognizer {
        /** Select first, so absent OCR is reported even if screen capture is unavailable. */
        suspend fun prepare(options: JSONObject): Detection
    }
    fun interface Detection { suspend fun detect(bitmap: Bitmap, timeoutMs: Long): List<OcrResult> }
    class MissingOcr : IllegalStateException("OCR_PLUGIN_REQUIRED")

    private val lock = Any()
    private var closed = false
    private val operations = LinkedHashSet<Operation>()
    private val workers = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8)) {
        Thread(it, "AutoJs6ScreenText").apply { isDaemon = true }
    }
    private val timer = Executors.newSingleThreadScheduledExecutor {
        Thread(it, "AutoJs6ScreenTextDeadline").apply { isDaemon = true }
    }

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val options = try {
            val args = JSONArray(request.argsJson)
            require(args.length() <= 1 && (args.length() == 0 || args.opt(0) is JSONObject))
            (args.optJSONObject(0) ?: JSONObject()).also { require(it.toString().toByteArray().size <= 8192) }
        } catch (_: Exception) {
            callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.invalidRequest("readScreenText expects one options object.", request.module, request.method)))
            return
        }
        val operation = Operation(request, callback)
        operation.task = FutureTask {
            fun remaining() = TimeUnit.NANOSECONDS.toMillis(operation.deadline - System.nanoTime()).also { if (it <= 0) throw TimeoutException() }
            val response = try {
                remaining()
                val detection = runBlocking { recognizer.prepare(options) }
                val screenshot = captureOverride?.invoke(remaining()) ?: capture(remaining())
                try {
                    val region = region(options, screenshot)
                    val input = region?.let { Bitmap.createBitmap(screenshot, it[0], it[1], it[2], it[3]) } ?: screenshot
                    try {
                        val results = runBlocking { detection.detect(input, remaining()) }
                        val offsetX = region?.get(0) ?: 0
                        val offsetY = region?.get(1) ?: 0
                        val json = NodeScreenTextCodec.encode(results.asSequence().map { result ->
                            NodeScreenTextCodec.Line(result.text.orEmpty(), result.bounds?.let {
                                NodeScreenTextCodec.Bounds(it.left + offsetX, it.top + offsetY, it.right + offsetX, it.bottom + offsetY)
                            }, result.confidence.toDouble())
                        })
                        NodeBridgeResponse.success(request.id, json)
                    } finally { if (input !== screenshot) input.recycle() }
                } finally { screenshot.recycle() }
            } catch (error: Throwable) {
                val mapped = when (error) {
                    is MissingOcr -> NodeBridgeError.capabilityProviderMissing(request.module, request.method, "an enabled compatible OCR plugin")
                        .copy(message = "OCR_PLUGIN_REQUIRED")
                    is TimeoutException -> NodeBridgeError.timeout(request.module, request.method)
                    is IllegalArgumentException -> NodeBridgeError.invalidRequest("Invalid screen text options or region.", request.module, request.method)
                    is SecurityException -> NodeBridgeError.permissionDenied("Screen capture permission is required.", request.module, request.method)
                    is InterruptedException, is java.util.concurrent.CancellationException -> NodeBridgeError.processDead("Screen text observation cancelled.")
                    else -> NodeBridgeError.providerFailed("Screen text observation failed.", request.module, request.method)
                }
                NodeBridgeResponse.failure(request.id, mapped)
            }
            operation.finish(response)
        }
        synchronized(lock) {
            if (closed) {
                operation.finish(NodeBridgeResponse.failure(request.id, NodeBridgeError.processDead()))
                return
            }
            operations.add(operation)
            try {
                operation.timeout = timer.schedule({
                    operation.finish(NodeBridgeResponse.failure(request.id, NodeBridgeError.timeout(request.module, request.method)), cancel = true)
                }, request.timeoutMs.coerceIn(1, 300_000), TimeUnit.MILLISECONDS)
                workers.execute(operation.task)
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                operation.finish(NodeBridgeResponse.failure(request.id, NodeBridgeError.resourceLimit("Screen text observation queue is full.", request.module, request.method)), cancel = true)
            }
        }
    }

    override fun close() {
        val pending = synchronized(lock) {
            closed = true
            operations.toList()
        }
        pending.forEach { it.finish(NodeBridgeResponse.failure(it.request.id, NodeBridgeError.processDead()), cancel = true) }
        timer.shutdownNow()
        workers.shutdownNow()
    }

    private inner class Operation(val request: NodeBridgeRequest, val callback: (NodeBridgeResponse) -> Unit) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(request.timeoutMs.coerceIn(1, 300_000))
        val completed = AtomicBoolean()
        lateinit var task: FutureTask<Unit>
        var timeout: ScheduledFuture<*>? = null
        fun finish(response: NodeBridgeResponse, cancel: Boolean = false) {
            if (!completed.compareAndSet(false, true)) return
            timeout?.cancel(false)
            if (cancel) { task.cancel(true); workers.remove(task) }
            synchronized(lock) { operations.remove(this) }
            runCatching { callback(response) }
        }
    }

    // Keeps the three attempts this observation always had; captureScreenImage uses the longer default.
    private fun capture(timeoutMs: Long): Bitmap = NodeBridgeScreenCapture.capture(timeoutMs, maxAttempts = 3, fallback = fallbackCapture).bitmap

    private fun region(options: JSONObject, bitmap: Bitmap): IntArray? {
        val raw = options.opt("region") ?: return null
        if (raw == JSONObject.NULL) return null
        val values = when (raw) {
            is JSONArray -> { require(raw.length() == 4); (0..3).map { raw.opt(it) } }
            is JSONObject -> listOf("x", "y", "width", "height").map { raw.opt(it) }
            else -> throw IllegalArgumentException()
        }.map { rawValue ->
            require(rawValue is Number)
            val value = rawValue.toDouble()
            require(value.isFinite() && value == value.toInt().toDouble())
            value.toInt()
        }.toIntArray()
        require(values[0] >= 0 && values[1] >= 0 && values[2] > 0 && values[3] > 0)
        require(values[0].toLong() + values[2] <= bitmap.width && values[1].toLong() + values[3] <= bitmap.height)
        return values
    }

    private class AndroidRecognizer(private val context: Context) : Recognizer {
        override suspend fun prepare(options: JSONObject): Detection {
            val target = try {
                OcrPluginHost.selectOrThrow(context,
                    engineId = options.string(K.ENGINE_ID), engine = options.string(K.ENGINE), variant = options.string(K.VARIANT),
                    moduleLabel = "OCR", automatic = true)
            } catch (_: IllegalStateException) { throw MissingOcr() }
            val pluginOptions = OcrOptions().apply {
                extras = Bundle().apply {
                    listOf(K.ENGINE_ID, K.ENGINE, K.VARIANT).forEach { key -> options.string(key)?.let { putString(key, it) } }
                    (options.string(K.PROFILE) ?: options.string("lang") ?: options.string("model"))?.let { putString(K.PROFILE, it) }
                    if (Build.VERSION.SDK_INT >= 27 && options.optBoolean("useRaw", true)) putBoolean(K.RAW_IMAGE, true)
                }
            }
            return Detection { bitmap, timeoutMs -> OcrPluginHost.detect(context, target, bitmap, pluginOptions, timeoutMs) }
        }
        private fun JSONObject.string(key: String) = optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
