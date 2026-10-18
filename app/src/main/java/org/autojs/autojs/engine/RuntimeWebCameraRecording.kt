package org.autojs.autojs.engine

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import kotlin.math.abs

/** Native file recording for the case where Chromium suspends hidden-page camera tracks. */
internal class RuntimeWebCameraRecording(
    private val context: Context,
    private val options: JSONObject,
    private val rotation: Int,
    val id: String,
    private val published: (Result<JSONObject>) -> Unit,
) : Closeable {
    private val output = RuntimeWebFileChooser.newCapture(context, "mp4")
    private val thread = HandlerThread("web-camera-recording").apply { start() }
    private val handler = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var lease: Closeable? = null
    private var startedAt = 0L
    @Volatile private var terminal = false
    private var opening = false
    private var startReply: ((Result<JSONObject>) -> Unit)? = null
    @Volatile private var snapshot = JSONObject().put("id", id).put("state", "starting")
    fun status(): JSONObject = JSONObject(snapshot.toString())
    fun start(reply: (Result<JSONObject>) -> Unit) {
        startReply = reply
        lease = RuntimeWebMediaService.acquire(context, true, options.optBoolean("audio"), { result ->
            handler.post { if (!terminal) result.fold({ open() }, { fail(it) }) }
        }, { handler.post { finish(true, "notification-stop") } })
    }
    @Suppress("MissingPermission", "DEPRECATION")
    private fun open() {
        try {
            val manager = context.getSystemService(CameraManager::class.java)
            val facing = when (options.optString("facing", "back")) {
                "back" -> CameraCharacteristics.LENS_FACING_BACK
                "front" -> CameraCharacteristics.LENS_FACING_FRONT
                else -> error("facing must be back or front")
            }
            val camera = if (!options.isNull("cameraId")) options.getString("cameraId") else manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            } ?: error("Requested camera is unavailable")
            val characteristics = manager.getCameraCharacteristics(camera)
            val width = options.opt("width")?.let { RuntimeViewAttributes.integer(it, 1, 8192) } ?: 1280
            val height = options.opt("height")?.let { RuntimeViewAttributes.integer(it, 1, 8192) } ?: 720
            val sizes = requireNotNull(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)).getOutputSizes(MediaRecorder::class.java)
            val size = sizes.minByOrNull { abs(it.width.toDouble() / it.height - width.toDouble() / height) * 100000 + abs(it.width - width) + abs(it.height - height) } ?: error("Camera has no recording sizes")
            val frameRate = options.opt("frameRate")?.let { RuntimeViewAttributes.integer(it, 1, 60) } ?: 30
            val rates = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
            val rate = rates.filter { it.upper <= frameRate }.maxByOrNull { it.upper * 1000 + it.lower } ?: rates.minByOrNull { it.upper }
            val fps = rate?.upper ?: frameRate
            val audio = options.optBoolean("audio", false)
            val native = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).also { recorder = it }
            if (audio) native.setAudioSource(MediaRecorder.AudioSource.MIC)
            native.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            native.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            native.setOutputFile(output.first.absolutePath)
            native.setVideoEncodingBitRate(options.opt("bitRate")?.let { RuntimeViewAttributes.integer(it, 1, Int.MAX_VALUE) } ?: 4_000_000)
            native.setVideoEncoder(MediaRecorder.VideoEncoder.H264); native.setVideoSize(size.width, size.height); native.setVideoFrameRate(fps)
            if (audio) { native.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); native.setAudioChannels(1); native.setAudioSamplingRate(48000); native.setAudioEncodingBitRate(128000) }
            val sensor = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            native.setOrientationHint((sensor + (if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) rotation else -rotation) + 360) % 360)
            RuntimeWebFileChooser.durationLimit(options)?.let { native.setMaxDuration(it * 1000) }
            RuntimeWebFileChooser.sizeLimit(options)?.let(native::setMaxFileSize)
            native.setOnErrorListener { _, what, extra -> handler.post { fail(IllegalStateException("Camera recorder error $what/$extra")) } }
            native.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) handler.post { finish(true, "limit") } }
            native.prepare()
            snapshot = JSONObject().put("id", id).put("state", "starting").put("cameraId", camera).put("width", size.width).put("height", size.height).put("frameRate", fps).put("audio", audio)
            opening = true
            manager.openCamera(camera, object : CameraDevice.StateCallback() {
                override fun onOpened(opened: CameraDevice) {
                    opening = false
                    if (terminal) { opened.close(); thread.quitSafely(); return }
                    device = opened
                    try { opened.createCaptureSession(listOf(native.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(configured: CameraCaptureSession) {
                            if (terminal) { configured.close(); return }
                            session = configured
                            try {
                                val request = opened.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                                    addTarget(native.surface); set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                                    if (rate != null) set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, rate)
                                }
                                configured.setRepeatingRequest(request.build(), null, handler); native.start(); startedAt = SystemClock.elapsedRealtime()
                                snapshot = status().put("state", "recording"); val active = status(); val callback = startReply; startReply = null; main.post { callback?.invoke(Result.success(active)) }
                            } catch (error: Throwable) { fail(error) }
                        }
                        override fun onConfigureFailed(configured: CameraCaptureSession) { configured.close(); fail(IllegalStateException("Camera recording configuration failed")) }
                    }, handler) } catch (error: Throwable) { fail(error) }
                }
                override fun onDisconnected(camera: CameraDevice) { opening = false; camera.close(); if (terminal) thread.quitSafely() else fail(IllegalStateException("Camera disconnected")) }
                override fun onError(camera: CameraDevice, error: Int) { opening = false; camera.close(); if (terminal) thread.quitSafely() else fail(IllegalStateException("Camera error $error")) }
            }, handler)
        } catch (error: Throwable) { opening = false; fail(error) }
    }
    fun stop(reply: (Result<JSONObject>) -> Unit) {
        if (snapshot.optString("state") == "stopped") { reply(Result.success(status())); return }
        if (snapshot.optString("state") !in listOf("starting", "recording")) { reply(Result.failure(IllegalStateException("Recording is not active"))); return }
        handler.post { finish(true, "stopped", reply) }
    }
    fun cancel(reply: () -> Unit) {
        if (terminal) { reply(); return }
        handler.post { finish(false, "cancelled", { reply() }) }
    }
    private fun fail(error: Throwable) = finish(false, "failed", error = error)
    private fun finish(keep: Boolean, reason: String, reply: ((Result<JSONObject>) -> Unit)? = null, error: Throwable? = null) {
        if (terminal) { main.post { reply?.invoke(if (snapshot.optString("state") == "stopped") Result.success(status()) else Result.failure(IllegalStateException("Recording is not active"))) }; return }
        terminal = true
        var failure = error
        try { runCatching { session?.stopRepeating() }; runCatching { session?.abortCaptures() }; if (startedAt != 0L) recorder?.stop() }
        catch (caught: Throwable) { failure = failure ?: caught }
        finally { runCatching { session?.close() }; session = null; runCatching { device?.close() }; device = null; runCatching { recorder?.release() }; recorder = null; lease?.close(); lease = null }
        val duration = if (startedAt == 0L) 0 else SystemClock.elapsedRealtime() - startedAt
        if (keep && failure == null && startedAt != 0L) {
            try { RuntimeWebFileChooser.validateCapture(output.first, "video"); RuntimeWebFileAccess.capture(output.second, output.first) }
            catch (caught: Throwable) { failure = caught }
        } else if (keep && failure == null) failure = IllegalStateException("Recording stopped before camera startup")
        val result = if (keep && failure == null) {
            snapshot = status().put("state", "stopped").put("reason", reason).put("uri", output.second.toString()).put("mimeType", "video/mp4").put("size", output.first.length()).put("durationMs", duration)
            Result.success(status())
        } else {
            output.first.delete(); snapshot = status().put("state", if (reason == "cancelled") "cancelled" else "failed").put("reason", reason).put("error", failure?.message ?: "Recording cancelled")
            Result.failure(failure ?: IllegalStateException("Recording cancelled"))
        }
        val pending = startReply; startReply = null
        main.post { published(result); pending?.invoke(Result.failure(failure ?: IllegalStateException("Recording cancelled before startup"))); reply?.invoke(result) }
        if (!opening) thread.quitSafely()
    }
    override fun close() { if (!terminal) handler.post { finish(false, "cancelled") } }
}
