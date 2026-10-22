package org.autojs.autojs.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.autojs.autojs6.R
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/** MediaRecorder and its output descriptor are accessed only on the audio executor. */
internal class NodeAudioRecorder(private val context: Context, private val resolveFile: (String) -> File?) {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    private var pending: Pending? = null
    private var active: Recording? = null
    private var lastResult: JSONObject? = null
    private var lastError: Throwable? = null

    private data class Pending(val id: String, val path: String, val file: File, val options: JSONObject,
        val callback: (Result<JSONObject?>) -> Unit)
    private data class Recording(val id: String, val path: String, val file: File, val recorder: MediaRecorder,
        val descriptor: ParcelFileDescriptor, val startedAt: Long, val maxDurationMs: Int)

    fun dispatch(method: String, options: JSONObject, callback: (Result<JSONObject?>) -> Unit) {
        if (closed) { callback(Result.failure(IllegalStateException("The script has ended."))); return }
        enqueue {
            try {
                when (method) {
                    "getStatus" -> callback(Result.success(status()))
                    "start" -> start(options, callback)
                    "stop" -> callback(stop("stopped"))
                }
            } catch (error: Throwable) { callback(Result.failure(error)) }
        }
    }

    private fun start(options: JSONObject, callback: (Result<JSONObject?>) -> Unit) {
        check(!closed) { "The script has ended." }
        check(active == null && pending == null) { "This script is already recording audio." }
        if (!hasPermission()) throw SecurityException("Grant AutoJs6 microphone permission in Android app settings before recording audio.")
        val path = options.optString("path", "recording.m4a")
        val file = resolveFile(path) ?: throw SecurityException("The audio output path must be a non-empty file path without NUL bytes.")
        require(options.optString("format", "m4a") in listOf("m4a", "mp4")) { "Audio recording supports m4a/mp4 with AAC audio." }
        require(options.optInt("maxDurationMs", 60_000) in 1000..300_000) { "maxDurationMs must be between 1000 and 300000." }
        require(options.optInt("sampleRate", 44_100) in 8000..192_000) { "sampleRate must be between 8000 and 192000." }
        require(options.optInt("bitRate", 128_000) in 16_000..320_000) { "bitRate must be between 16000 and 320000." }
        val request = Pending("recording-${UUID.randomUUID()}", path, file, options, callback)
        pending = request
        lastResult = null
        lastError = null
        NodeAudioRecordingService.acquire(context, request.id,
            onReady = { result -> enqueue { if (pending === request) result.fold({ begin(request) }, { failPending(request, it) }) } },
            onStop = { reason -> enqueue { if (pending?.id == request.id || active?.id == request.id) stop(reason) } })
    }

    @Suppress("DEPRECATION")
    private fun begin(request: Pending) {
        if (closed) { failPending(request, IllegalStateException("The script has ended.")); return }
        var recorder: MediaRecorder? = null
        var descriptor: ParcelFileDescriptor? = null
        try {
            request.file.parentFile?.mkdirs()
            descriptor = ParcelFileDescriptor.open(request.file,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            val maxDurationMs = request.options.optInt("maxDurationMs", 60_000)
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(request.options.optInt("sampleRate", 44_100))
                setAudioEncodingBitRate(request.options.optInt("bitRate", 128_000))
                setMaxDuration(maxDurationMs)
                setOutputFile(descriptor.fileDescriptor)
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) enqueue {
                    if (active?.id == request.id) stop("max-duration")
                } }
                setOnErrorListener { _, what, extra -> enqueue { if (active?.id == request.id) stop("recorder-error-$what-$extra") } }
                prepare()
                start()
            }
            active = Recording(request.id, request.path, request.file, recorder, descriptor, SystemClock.elapsedRealtime(), maxDurationMs)
            pending = null
        } catch (error: Throwable) {
            runCatching { recorder?.release() }
            runCatching { descriptor?.close() }
            failPending(request, error)
            return
        }
        request.callback(Result.success(recordingInfo(active!!)))
    }

    private fun failPending(request: Pending, error: Throwable) {
        if (pending !== request) return
        pending = null
        NodeAudioRecordingService.release(request.id)
        request.callback(Result.failure(error))
    }

    private fun stop(reason: String): Result<JSONObject?> {
        pending?.let { failPending(it, IllegalStateException("Audio recording was cancelled before it started.")) }
        val current = active ?: return lastError?.let { Result.failure(it) } ?: Result.success(lastResult)
        active = null
        var failure: Throwable? = null
        try { current.recorder.stop() } catch (error: RuntimeException) { failure = error }
        finally {
            runCatching { current.recorder.release() }
            runCatching { current.descriptor.close() }
            NodeAudioRecordingService.release(current.id)
        }
        lastResult = recordingInfo(current).put("recording", false).put("reason", reason)
            .put("durationMs", SystemClock.elapsedRealtime() - current.startedAt).put("byteCount", current.file.length())
        lastError = failure
        return failure?.let { Result.failure(it) } ?: Result.success(lastResult)
    }

    private fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun status(): JSONObject = JSONObject().put("schema", "autojs6-node-recorder-status-v1")
        .put("recording", active != null).put("starting", pending != null).put("providerAvailable", true)
        .put("microphonePermissionRequired", true).put("microphonePermissionGranted", hasPermission())
        .put("foregroundDisclosureRequired", true).put("reason", if (hasPermission()) "available" else "microphone-permission-required")
        .put("active", active?.let(::recordingInfo) ?: JSONObject.NULL).put("lastRecording", lastResult ?: JSONObject.NULL)

    private fun recordingInfo(value: Recording): JSONObject = JSONObject().put("id", value.id).put("path", value.path)
        .put("recording", true).put("format", "m4a").put("mimeType", "audio/mp4").put("maxDurationMs", value.maxDurationMs)

    private fun enqueue(action: () -> Unit) {
        try { executor.execute(action) } catch (_: RejectedExecutionException) { /* Already closed; pending callbacks were completed by close. */ }
    }

    @Synchronized fun close() {
        if (closed) return
        closed = true
        val finished = executor.submit { stop("script-ended") }
        executor.shutdown()
        try { finished.get(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        catch (_: Exception) { /* MediaRecorder cleanup continues on its executor. */ }
    }
}

/** A shared foreground lease keeps the microphone disclosure visible until all recordings stop. */
class NodeAudioRecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        if (intent?.action == ACTION_STOP) {
            entries.values.toList().forEach { it.onStop("notification-stop") }
            stopIfIdle()
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_ID).orEmpty()
        val entry = entries[id]
        if (entry == null) { stopIfIdle(); return START_NOT_STICKY }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL,
                getString(R.string.text_node_audio_recording_channel), NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 0, Intent(this, NodeAudioRecordingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle(getString(R.string.text_node_audio_recording_channel))
                .setContentText(getString(R.string.text_node_audio_recording)).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE).setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.text_stop), stop).build()
            if (Build.VERSION.SDK_INT >= 30) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            else startForeground(NOTIFICATION_ID, notification)
            entry.onReady(Result.success(Unit))
        } catch (error: RuntimeException) {
            entries.remove(id)
            entry.onReady(Result.failure(error))
            stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun stopIfIdle() { if (entries.isEmpty()) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } }
    override fun onDestroy() {
        instance = null
        val remaining = entries.values.toList()
        entries.clear()
        remaining.forEach { it.onStop("service-destroyed") }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "node_audio_recording"
        private const val NOTIFICATION_ID = 0xC9
        private const val EXTRA_ID = "recordingId"
        private const val ACTION_STOP = "org.autojs.autojs6.STOP_NODE_AUDIO_RECORDING"
        private data class Entry(val onReady: (Result<Unit>) -> Unit, val onStop: (String) -> Unit)
        private val entries = ConcurrentHashMap<String, Entry>()
        private val main = Handler(Looper.getMainLooper())
        @Volatile private var instance: NodeAudioRecordingService? = null

        /** Recordings held, for the bridge leak gate (docs/memory-phase2/ROADMAP.md T1 (h)). zh-CN: 持有的录音数, 供桥接泄漏门禁使用 (docs/memory-phase2/ROADMAP.md T1 (h)). */
        internal fun entryCount(): Int = entries.size
        internal fun acquire(context: Context, id: String, onReady: (Result<Unit>) -> Unit, onStop: (String) -> Unit) {
            entries[id] = Entry(onReady, onStop)
            try {
                val intent = Intent(context, NodeAudioRecordingService::class.java).putExtra(EXTRA_ID, id)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            } catch (error: RuntimeException) { entries.remove(id); onReady(Result.failure(error)) }
        }
        internal fun release(id: String) { entries.remove(id); main.post { instance?.stopIfIdle() } }
    }
}
