package org.autojs.autojs.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.image.capture.ScreenCaptureRequester
import org.autojs.autojs.core.image.capture.ScreenCapturer
import org.autojs.autojs.core.pref.keys.RunningKeys
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.json.JSONObject
import java.util.UUID
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException

/** One execution's capture session, using the same consent and capture implementation as Images. */
internal class NodeScreenCaptureSession(
    private val context: Context,
    private val onStopped: (String) -> Unit,
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var closed = false
    private var capturer: ScreenCapturer? = null
    private var requester: ScreenCaptureRequester? = null
    private var id: String? = null
    private var readyAt = 0L
    private var pending: ((Result<JSONObject>) -> Unit)? = null
    private var timeout: Runnable? = null
    private var recording: NodeScreenRecording? = null

    fun request(options: JSONObject, timeoutMs: Long, callback: (Result<JSONObject>) -> Unit) {
        main.post {
            synchronized(lock) {
                if (closed) return@synchronized callback(Result.failure(CancellationException("Screen capture session is closed.")))
                if (capturer?.isReleased == false) return@synchronized callback(Result.success(info()))
                if (capturer != null) {
                    val previousId = id
                    stop()
                    previousId?.let(onStopped)
                }
                if (options.optBoolean("requireExistingPermission", false)) {
                    return@synchronized callback(Result.failure(SecurityException("Screen capture permission has not been granted for this execution.")))
                }
                if (pending != null) return@synchronized callback(Result.failure(IllegalStateException("A screen capture permission request is already pending.")))
                val orientation = when (options.optString("orientation", "auto")) {
                    "portrait" -> ScreenCapturer.ORIENTATION_PORTRAIT
                    "landscape" -> ScreenCapturer.ORIENTATION_LANDSCAPE
                    else -> ScreenCapturer.ORIENTATION_AUTO
                }
                val width = options.optInt("width", 0)
                val height = options.optInt("height", 0)
                if (width < 0 || height < 0 || width > 8192 || height > 8192 || ((width == 0) != (height == 0))) {
                    return@synchronized callback(Result.failure(IllegalArgumentException("Capture dimensions must both be omitted or in 1..8192.")))
                }
                pending = callback
                val request = ScreenCaptureRequester(true)
                requester = request
                val deadline = Runnable {
                    synchronized(lock) {
                        if (requester === request && pending != null) {
                            finish(Result.failure(TimeoutException("Screen capture consent timed out.")))
                            request.cancel()
                            requester = null
                        }
                    }
                }
                timeout = deadline
                main.postDelayed(deadline, timeoutMs.coerceAtLeast(1))
                try {
                    request.request(context, object : ScreenCaptureRequester.Callback {
                        override fun onRequestError(error: Throwable) = synchronized(lock) {
                            if (requester === request) {
                                finish(Result.failure(error))
                                request.cancel()
                                requester = null
                            }
                        }

                        override fun onRequestResult(resultCode: Int, data: Intent?) = synchronized(lock) {
                            if (closed || requester !== request || pending == null) {
                                request.unbindService()
                                return@synchronized
                            }
                            if (resultCode != Activity.RESULT_OK || data == null) {
                                finish(Result.failure(SecurityException("Screen capture permission was denied.")))
                                request.unbindService()
                                requester = null
                                return@synchronized
                            }
                            val result = runCatching {
                                val capture = ScreenCapturer(context, data, ScreenCapturer.Options(
                                    width, height, if (width > 0) ScreenCapturer.ORIENTATION_NONE else orientation,
                                    context.resources.displayMetrics.densityDpi, false,
                                ), main)
                                capturer = capture
                                id = "screen-${UUID.randomUUID()}"
                                readyAt = SystemClock.uptimeMillis() + RunningKeys.screenCaptureRequestDelay.coerceIn(0, 2000)
                                capture.setOnMediaProjectionStopListener {
                                    synchronized(lock) {
                                        if (capturer === capture) {
                                            cancelRecording()
                                            val stoppedId = id
                                            capturer = null
                                            id = null
                                            request.unbindService()
                                            requester = null
                                            stoppedId?.let(onStopped)
                                        }
                                    }
                                }
                                info()
                            }
                            finish(result)
                            if (result.isFailure) {
                                request.unbindService()
                                requester = null
                            }
                        }
                    })
                } catch (error: Exception) {
                    finish(Result.failure(error))
                    request.cancel()
                    requester = null
                }
            }
        }
    }

    // Called off the UI thread. Copy the ImageReader frame before releasing its buffer.
    fun capture(expectedId: String? = null): Bitmap = synchronized(lock) {
        val capture = capturer?.takeUnless { closed || it.isReleased || (expectedId != null && expectedId != id) }
            ?: throw SecurityException("Request screen capture permission before capturing an image.")
        val delay = readyAt - SystemClock.uptimeMillis()
        if (delay > 0) Thread.sleep(delay)
        recording?.let { return@synchronized it.capture() }
        captureFreshFrame {
            val frame = capture.capture() ?: return@captureFreshFrame null
            val bitmap = try { ImageWrapper.toBitmap(frame) } finally { frame.close() }
            if (isTransparentProjectionBuffer(bitmap)) {
                // Some display producers deliver a cleared RGBA buffer immediately after resize.
                // Black (opaque) content and partially transparent content remain valid frames.
                android.util.Log.d("NodeScreenCapture", "Waiting for a composed frame after a transparent projection buffer.")
                bitmap.recycle()
                null
            } else bitmap
        }
    }

    fun stop(expectedId: String? = null) = synchronized(lock) {
        if (expectedId != null && expectedId != id) return@synchronized
        cancelRecording()
        pending?.let { finish(Result.failure(CancellationException("Screen capture was stopped."))) }
        capturer?.release()
        capturer = null
        id = null
        requester?.cancel()
        requester = null
    }

    fun startRecording(output: File, options: JSONObject): JSONObject = synchronized(lock) {
        val capture = capturer?.takeUnless { closed || it.isReleased }
            ?: throw SecurityException("Request Android screen capture consent before recording.")
        check(recording == null) { "Screen recording is already active." }
        val size = info()
        lateinit var next: NodeScreenRecording
        next = NodeScreenRecording(context, capture, output, options, size.getInt("width"), size.getInt("height")) {
            // The service stop action must not wait for a session lock on the Android main thread.
            Thread({ synchronized(lock) { if (recording === next) cancelRecording() } }, "ScreenRecording-notification-stop").start()
        }
        next.start().also { recording = next }
    }

    fun stopRecording(expectedId: String? = null): JSONObject? = synchronized(lock) {
        val active = recording?.takeIf { expectedId == null || it.status().optString("id") == expectedId } ?: return@synchronized null
        try { active.finish() } finally { recording = null }
    }

    fun cancelRecording(expectedId: String? = null): Boolean = synchronized(lock) {
        val active = recording?.takeIf { expectedId == null || it.status().optString("id") == expectedId } ?: return@synchronized false
        recording = null
        active.close()
        true
    }

    fun captureOptions(): JSONObject? = synchronized(lock) {
        val capture = capturer?.takeUnless { closed || it.isReleased } ?: return@synchronized null
        val size = capture.captureDimensions
        JSONObject().put("width",size.x).put("height",size.y).put("orientation", when(capture.options.orientation()) {
            ScreenCapturer.ORIENTATION_PORTRAIT -> "portrait"; ScreenCapturer.ORIENTATION_LANDSCAPE -> "landscape"; else -> "auto"
        })
    }

    fun recordingStatus(): JSONObject = synchronized(lock) {
        recording?.status() ?: JSONObject().put("state", "idle")
    }

    override fun close() = synchronized(lock) {
        closed = true
        stop()
    }

    private fun finish(result: Result<JSONObject>) {
        timeout?.let(main::removeCallbacks)
        timeout = null
        val callback = pending
        pending = null
        callback?.invoke(result)
    }

    private fun info(): JSONObject {
        val options = checkNotNull(capturer).options
        val orientation = if (options.orientation() == ScreenCapturer.ORIENTATION_AUTO)
            context.resources.configuration.orientation else options.orientation()
        return JSONObject().put("id", id).put("__autojs6ScreenCapturerHandle", id)
            .put("width", options.width().takeIf { it > 0 } ?: ScreenMetrics.getOrientationAwareScreenWidth(orientation))
            .put("height", options.height().takeIf { it > 0 } ?: ScreenMetrics.getOrientationAwareScreenHeight(orientation))
    }

    companion object {
        internal fun isTransparentProjectionBuffer(bitmap: Bitmap): Boolean {
            if (bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) ushr 24 != 0) return false
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                if (row.any { it ushr 24 != 0 }) return false
            }
            return true
        }
        /** Each native capture waits at most 1.2 s and refreshes its display on timeout. Retry the
         * refreshed pipeline, as Images.captureScreen does, within the bridge's 15 s budget. */
        internal fun <T : Any> captureFreshFrame(pause: () -> Unit = { Thread.sleep(40L) }, capture: () -> T?): T {
            repeat(6) { attempt ->
                capture()?.let { return it }
                if (attempt < 5) pause()
            }
            throw IllegalStateException("No screen frame is available after 6 bounded attempts.")
        }
    }
}
