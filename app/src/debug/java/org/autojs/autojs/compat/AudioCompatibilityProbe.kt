package org.autojs.autojs.compat

import android.app.Activity
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.TextView
import org.autojs.autojs.runtime.api.Device
import org.autojs.autojs.service.ForegroundService
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sin

/** Debug-only probe: no instrumentation process or shell-adopted permissions affect the app UID. */
class AudioCompatibilityProbeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply { text = "SDK 37 audio compatibility probe" })
        val scenario = intent.getStringExtra("scenario") ?: "visible"
        require(scenario in setOf("visible", "visible-service", "recover-service", "background", "alarm-service", "alarm-stream", "arm-boot"))
        val probe = Intent(this, AudioCompatibilityProbeReceiver::class.java)
            .putExtra("scenario", scenario)
            .putExtra("tts", intent.getBooleanExtra("tts", false))
        AudioCompatibilityProbe.resultFile(this).delete()
        Handler(Looper.getMainLooper()).postDelayed({
            when (scenario) {
                "arm-boot" -> {
                    getSharedPreferences("sdk37-audio-probe", MODE_PRIVATE).edit()
                        .putBoolean("boot", true).putBoolean("tts", probe.getBooleanExtra("tts", false)).commit()
                    AudioCompatibilityProbe.save(this, JSONObject().put("armedForBoot", true))
                    finish()
                }
                "alarm-service", "alarm-stream" -> {
                    val alarm = getSystemService(AlarmManager::class.java)
                    val pending = PendingIntent.getBroadcast(this, 3702, probe, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 12_000, pending)
                    finish()
                }
                else -> {
                    if (scenario == "visible-service" || scenario == "recover-service") ForegroundService(this).start()
                    if (scenario != "visible") moveTaskToBack(true)
                    Thread({
                        Thread.sleep(if (scenario == "visible") 1_000 else 10_000)
                        AudioCompatibilityProbe.run(applicationContext, probe)
                    }, "Sdk37AudioProbe").start()
                }
            }
        }, intent.getLongExtra("delayMillis", 1_000).coerceIn(1_000, 60_000))
    }
}

class AudioCompatibilityProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferences = context.getSharedPreferences("sdk37-audio-probe", Context.MODE_PRIVATE)
            if (!preferences.getBoolean("boot", false)) return
            intent.putExtra("tts", preferences.getBoolean("tts", false))
            preferences.edit().clear().commit()
            intent.putExtra("scenario", "boot-service")
        }
        val pending = goAsync()
        val app = context.applicationContext
        Thread({
            try {
                ForegroundService(app).start()
                Thread.sleep(1_000)
                AudioCompatibilityProbe.run(app, intent)
            } catch (error: Throwable) {
                AudioCompatibilityProbe.save(app, JSONObject().put("scenario", intent.getStringExtra("scenario"))
                    .put("serviceStartError", error.toString()))
            } finally {
                pending.finish()
            }
        }, "Sdk37AudioReceiver").start()
    }
}

internal object AudioCompatibilityProbe {
    fun resultFile(context: Context) = File(context.filesDir, "sdk37-audio-probe.json")

    fun save(context: Context, result: JSONObject) {
        resultFile(context).writeText(result.toString(2))
        Log.i("Sdk37AudioProbe", result.toString())
    }

    @Suppress("DEPRECATION")
    fun run(context: Context, intent: Intent) {
        val scenario = intent.getStringExtra("scenario") ?: "unknown"
        val alarm = scenario == "alarm-stream"
        val usage = if (alarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA
        val stream = if (alarm) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC
        val audio = context.getSystemService(AudioManager::class.java)
        val info = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState)
        val result = JSONObject().put("scenario", scenario).put("sdk", Build.VERSION.SDK_INT)
            .put("target", context.applicationInfo.targetSdkVersion).put("uid", Process.myUid()).put("pid", Process.myPid())
            .put("importance", info.importance).put("foregroundService", ForegroundService(context).isRunning)
            .put("usage", usage).put("exactAlarm", Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
        fun attempt(key: String, action: () -> Any?) {
            try { result.put(key, action() ?: JSONObject.NULL) }
            catch (error: Throwable) { result.put(key + "Error", error.toString()) }
        }
        val before = audio.getStreamVolume(stream)
        val requested = if (before > 2) before - 1 else before + 1
        result.put("volumeBefore", before).put("volumeRequested", requested)
        attempt("volumeCall") {
            if (alarm) Device(context).setAlarmVolume(requested) else Device(context).setMusicVolume(requested)
            "returned"
        }
        Thread.sleep(350)
        attempt("volumeAfter") { audio.getStreamVolume(stream) }
        val focusListener = AudioManager.OnAudioFocusChangeListener { }
        try {
            attempt("focus") { audio.requestAudioFocus(focusListener, stream, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) }
            attempt("track") {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(16_000).setTransferMode(AudioTrack.MODE_STREAM).build()
                try {
                    track.play()
                    val pcm = ShortArray(16_000) { (1_000 * sin(2 * Math.PI * 440 * it / 16_000)).toInt().toShort() }
                    val written = track.write(pcm, 0, pcm.size)
                    Thread.sleep(1_000)
                    JSONObject().put("written", written).put("playState", track.playState).put("headFrames", track.playbackHeadPosition)
                } finally { track.release() }
            }
            if (intent.getBooleanExtra("tts", false)) attempt("tts") { probeTts(context, usage) }
        } finally {
            audio.abandonAudioFocus(focusListener)
            runCatching { audio.setStreamVolume(stream, before, 0) }
            save(context, result)
        }
    }

    private fun probeTts(context: Context, usage: Int): JSONObject {
        val initialized = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val initStatus = AtomicInteger(Int.MIN_VALUE)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val tts = TextToSpeech(context) { initStatus.set(it); initialized.countDown() }
        try {
            check(initialized.await(8, TimeUnit.SECONDS)) { "TTS initialization timed out" }
            val result = JSONObject().put("initStatus", initStatus.get()).put("engine", tts.defaultEngine)
            if (initStatus.get() != TextToSpeech.SUCCESS) return result
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) { events.add("start") }
                override fun onDone(id: String) { events.add("done"); finished.countDown() }
                @Deprecated("Platform callback") override fun onError(id: String) { events.add("error"); finished.countDown() }
                override fun onError(id: String, code: Int) { events.add("error:$code"); finished.countDown() }
            })
            tts.setAudioAttributes(AudioAttributes.Builder().setUsage(usage).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            result.put("speakStatus", tts.speak("Audio compatibility check", TextToSpeech.QUEUE_FLUSH, Bundle(), "sdk37-probe"))
            result.put("completed", finished.await(8, TimeUnit.SECONDS))
            result.put("events", org.json.JSONArray(events))
            return result
        } finally { tts.stop(); tts.shutdown() }
    }
}
