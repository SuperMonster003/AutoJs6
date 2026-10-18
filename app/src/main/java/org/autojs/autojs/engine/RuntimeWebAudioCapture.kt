package org.autojs.autojs.engine

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.autojs.autojs6.R
import org.json.JSONObject

/** An explicit foreground audio chooser; no external recorder application is required. */
internal class RuntimeWebAudioCapture(
    private val context: Context,
    options: JSONObject,
    private val complete: (Result<List<Uri>?>) -> Unit,
) : RuntimeWebActionActivity.Action(context) {
    private val duration = RuntimeWebFileChooser.durationLimit(options)
    private val size = RuntimeWebFileChooser.sizeLimit(options)
    private val output = RuntimeWebFileChooser.newCapture(context, "m4a")
    private val main = Handler(Looper.getMainLooper())
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    private var elapsed = 0L
    private var usable = false
    private var error: String? = null
    private var status: TextView? = null
    private var start: Button? = null
    private var stop: Button? = null
    private var use: Button? = null
    private val tick = object : Runnable {
        override fun run() { render(); if (recorder != null && !isDone) main.postDelayed(this, 250) }
    }

    init {
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        require(Manifest.permission.RECORD_AUDIO in permissions) { "Audio capture requires android.permission.RECORD_AUDIO in the application manifest" }
    }

    override fun attach(activity: Activity, state: Bundle?) {
        val padding = (24 * activity.resources.displayMetrics.density).toInt()
        val background = android.util.TypedValue().also { activity.theme.resolveAttribute(android.R.attr.colorBackground, it, true) }.data
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding); setBackgroundColor(background)
        }
        fun label(text: String, size: Float) = TextView(activity).apply {
            this.text = text; textSize = size; gravity = Gravity.CENTER
            layout.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        label(activity.getString(R.string.text_web_audio_capture), 22f)
        label(activity.getString(R.string.text_web_audio_capture_instruction), 16f)
        status = label("", 18f).apply { id = R.id.runtime_web_audio_status; accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE }
        fun button(id: Int, text: Int, action: () -> Unit) = Button(activity).apply {
            this.id = id; setText(text); setOnClickListener { action() }
            layout.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        start = button(R.id.runtime_web_audio_start, R.string.text_start_record) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) record()
            else activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION)
        }
        stop = button(R.id.runtime_web_audio_stop, R.string.text_stop) { stopRecording() }
        use = button(R.id.runtime_web_audio_use, R.string.text_web_use_recording) {
            if (usable && recorder == null && !isDone) {
                RuntimeWebFileAccess.capture(output.second, output.first)
                finish { try { complete(Result.success(listOf(output.second))) } finally { cleanup() } }
            }
        }
        button(R.id.runtime_web_audio_cancel, R.string.text_cancel) { close() }
        activity.setContentView(layout)
        if (recorder != null) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    override fun permissions(code: Int, results: IntArray) {
        if (code != AUDIO_PERMISSION || isDone) return
        if (results.singleOrNull() == PackageManager.PERMISSION_GRANTED) record() else close()
    }

    private fun record() {
        if (isDone || recorder != null) return
        error = null; usable = false; elapsed = 0
        try {
            @Suppress("DEPRECATION")
            val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            recorder = next
            next.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(48000); setAudioChannels(1); setAudioEncodingBitRate(128000)
                setOutputFile(output.first.path)
                duration?.let { setMaxDuration(it * 1000) }
                size?.let(::setMaxFileSize)
                setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) main.post { if (recorder === next) stopRecording() } }
                setOnErrorListener { _, what, extra -> main.post { if (recorder === next) abort(IllegalStateException("MediaRecorder error $what/$extra")) } }
                prepare(); start()
            }
            startedAt = SystemClock.elapsedRealtime()
            activity?.get()?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            main.post(tick)
        } catch (failure: Exception) { abort(failure) }
        render()
    }

    private fun stopRecording() {
        val active = recorder ?: return
        recorder = null
        main.removeCallbacks(tick)
        activity?.get()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        elapsed = SystemClock.elapsedRealtime() - startedAt
        try {
            active.stop()
            RuntimeWebFileChooser.validateCapture(output.first, "audio")
            usable = true
        } catch (failure: Exception) {
            usable = false; output.first.delete()
            error = context.getString(R.string.error_web_audio_capture, failure.message ?: failure.javaClass.simpleName)
        } finally { active.release() }
        render()
    }

    private fun abort(failure: Exception) {
        val active = recorder; recorder = null
        runCatching { active?.reset() }; runCatching { active?.release() }
        main.removeCallbacks(tick); output.first.delete(); usable = false
        activity?.get()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        error = context.getString(R.string.error_web_audio_capture, failure.message ?: failure.javaClass.simpleName)
        render()
    }

    private fun render() {
        val recording = recorder != null
        val seconds = (if (recording) SystemClock.elapsedRealtime() - startedAt else elapsed) / 1000
        val clock = String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
        status?.text = error ?: if (recording) context.getString(R.string.text_node_audio_recording) + "\n" + clock
            else if (usable) context.getString(R.string.text_record_finished) + "\n" + clock else clock
        start?.isEnabled = !recording && !isDone
        stop?.isEnabled = recording && !isDone
        use?.isEnabled = usable && !recording && !isDone
    }

    // A chooser is not a background recording service. Rotation reattaches the same action.
    override fun background() { if (!isDone) stopRecording() }
    override fun detach() { status = null; start = null; stop = null; use = null }
    private fun cleanup() {
        val active = recorder; recorder = null
        runCatching { active?.reset() }; runCatching { active?.release() }
        main.removeCallbacks(tick)
        activity?.get()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        RuntimeWebFileAccess.discardUnusedCapture(output.second)
        if (!RuntimeWebFileAccess.isCapture(output.second)) output.first.delete()
    }
    override fun close() = finish { cleanup(); complete(Result.success(null)) }

    companion object { private const val AUDIO_PERMISSION = 4029 }
}
