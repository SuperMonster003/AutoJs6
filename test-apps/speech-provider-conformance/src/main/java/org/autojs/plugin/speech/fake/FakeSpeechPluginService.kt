package org.autojs.plugin.speech.fake

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.speech.api.ISpeechPlugin
import org.autojs.plugin.speech.api.ISpeechProgressCallback
import org.autojs.plugin.speech.api.ISpeechSession
import org.autojs.plugin.speech.api.ISpeechSessionCallback
import org.autojs.plugin.speech.api.ISpeechWakeCallback
import org.autojs.plugin.speech.api.SpeechCapabilityKeys
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechModelKeys
import org.autojs.plugin.speech.api.SpeechOptionKeys
import org.autojs.plugin.speech.api.SpeechPluginIds
import org.autojs.plugin.speech.api.SpeechResultKeys
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * A fake speech plugin for the host's device tests (3-Macaw Speech roadmap P1.4): a test-only app of its own (another
 * uid than the host, signed like the host so the Plugin Center trusts it), answers every transaction of contract version 1 with canned values, so the
 * device tests exercise discovery, `BIND_INCLUDE_CAPABILITIES` bindings, real Parcel marshalling, the callbacks, the
 * progress operations, the staged outputs and the death of the plugin process without the real plugin, which
 * implements the features in roadmap P2 to P4. Install it with `adb install -t` to run the round trip tests.
 *
 * A microphone session is the background microphone probe of roadmap D22: it records 16 kHz mono PCM16 with
 * `AudioRecord` in this process and reports every 500 ms a `vad` event with `active` (any non-zero sample), `peak`,
 * `frames` and `silenced` (`AudioRecordingConfiguration.isClientSilenced`, API 29+), until `finish`, `cancel` or
 * `maxDurationMillis`. Without `RECORD_AUDIO` it answers `PERMISSION_DENIED` like the real plugin.
 *
 * zh-CN: 供宿主设备测试使用的假语音插件 (3-Macaw Speech 路线图 P1.4): 独立的测试应用 (与宿主不同的 uid, 与宿主同签名, 因此插件中心信任它), 以固定值应答契约版本 1 的每个事务,
 * 使设备测试在没有真实插件 (其功能在路线图 P2 至 P4 实现) 的情况下覆盖发现, `BIND_INCLUDE_CAPABILITIES` 绑定, 真实 Parcel 编组, 回调, 进度操作,
 * 暂存输出与插件进程死亡. 以 `adb install -t` 安装后运行往返测试. 麦克风会话即路线图 D22 的后台麦克风探针: 在本进程用 `AudioRecord` 录制 16 kHz
 * 单声道 PCM16, 每 500 ms 报告一个 `vad` 事件, 带 `active` (存在非零样本), `peak`, `frames` 与 `silenced` (`isClientSilenced`, API 29+), 直到
 * `finish`, `cancel` 或 `maxDurationMillis`. 没有 `RECORD_AUDIO` 时与真实插件一样应答 `PERMISSION_DENIED`.
 */
class FakeSpeechPluginService : Service() {

    private val operations = AtomicInteger(0)

    private val binder = object : ISpeechPlugin.Stub() {

        override fun getInfo(): PluginInfo = PluginInfo().apply {
            name = "Fake Speech"
            author = "AutoJs6 tests"
            versionName = "0.0.1"
            versionCode = 1L
            id = FAKE_ID
            engine = SpeechPluginIds.ENGINE
            variant = SpeechPluginIds.VARIANT_DEFAULT
            capabilities = Bundle().apply {
                putLong(PluginCapabilityKeys.REQUIRES_HOST_VERSION, SpeechPluginIds.REQUIRED_HOST_VERSION_CODE)
                putInt(SpeechCapabilityKeys.CONTRACT_VERSION, SpeechContract.CALL_VERSION_1)
                putStringArray(SpeechCapabilityKeys.FEATURES, FEATURES)
                putInt(SpeechCapabilityKeys.CATALOG_VERSION, 1)
            }
        }

        override fun getCapabilities(): Bundle = answer {
            putString(SpeechCapabilityKeys.ENGINE_NAME, "fake")
            putString(SpeechCapabilityKeys.ENGINE_VERSION, "1")
            putBoolean(SpeechCapabilityKeys.MICROPHONE_GRANTED, microphoneGranted())
            putStringArray(SpeechCapabilityKeys.FEATURES, FEATURES)
        }

        override fun listModels(query: Bundle?): Bundle {
            val kind = query?.getString(SpeechOptionKeys.KIND)
            val models = MODELS.filter { kind == null || it.getString(SpeechModelKeys.KIND) == kind }
            return answer { putParcelableArrayList(SpeechResultKeys.MODELS, ArrayList(models.map { Bundle(it) })) }
        }

        override fun installModel(request: Bundle?, callback: ISpeechProgressCallback?): Int {
            val id = request?.getString(SpeechOptionKeys.MODEL) ?: throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.INVALID_OPTIONS, "model is required"))
            val model = MODELS.firstOrNull { it.getString(SpeechModelKeys.ID) == id }
                ?: throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.MODEL_MISSING, "unknown model $id"))
            val opId = operations.incrementAndGet()
            Thread {
                if (id == NON_COMMERCIAL && request.getBoolean(SpeechOptionKeys.ACCEPT_LICENSE) != true) {
                    callback?.onFailed(opId, failure(SpeechErrorCodes.MODEL_LICENSE_NOT_ACCEPTED, "accept the license of $id first"))
                    return@Thread
                }
                for (step in 1..4) {
                    SystemClock.sleep(50)
                    callback?.onProgress(opId, step * 25L, 100L)
                }
                callback?.onCompleted(opId, answer { putBundle(SpeechResultKeys.MODEL, Bundle(model).apply { putBoolean(SpeechModelKeys.INSTALLED, true) }) })
            }.start()
            return opId
        }

        override fun cancelOperation(opId: Int) = Unit

        override fun removeModel(request: Bundle?): Bundle {
            val id = request?.getString(SpeechOptionKeys.MODEL)
            if (MODELS.none { it.getString(SpeechModelKeys.ID) == id }) return failure(SpeechErrorCodes.MODEL_MISSING, "unknown model $id")
            return answer { putBoolean(SpeechResultKeys.REMOVED, true) }
        }

        override fun recognizeFile(audio: ParcelFileDescriptor?, options: Bundle?): Bundle {
            val model = options?.getString(SpeechOptionKeys.MODEL)
            if (model == MISSING_MODEL) return failure(SpeechErrorCodes.MODEL_MISSING, "model $model is not installed")
            val wav = readWav(audio ?: return failure(SpeechErrorCodes.IO_FAILED, "no audio"))
            val name = options?.getString(SpeechOptionKeys.DISPLAY_NAME)
            val hotwords = options?.getStringArray(SpeechOptionKeys.HOTWORDS).orEmpty()
            return answer {
                putString(SpeechResultKeys.TEXT, "fake:$name:${wav.durationMillis}")
                putString(SpeechResultKeys.LANGUAGE, options?.getString(SpeechOptionKeys.LANGUAGE) ?: "zh")
                putLong(SpeechResultKeys.DURATION_MILLIS, wav.durationMillis)
                putString(SpeechResultKeys.MODEL_ID, model ?: ASR_MODEL)
                putParcelableArrayList(SpeechResultKeys.SEGMENTS, arrayListOf(segment(0, wav.durationMillis, "fake:$name")))
                if (hotwords.isNotEmpty()) {
                    putParcelableArrayList(SpeechResultKeys.WARNINGS, arrayListOf(failure(SpeechErrorCodes.INVALID_OPTIONS, "hotwords ignored: ${hotwords.size}")))
                }
            }
        }

        override fun detectVoice(audio: ParcelFileDescriptor?, options: Bundle?): Bundle {
            val wav = readWav(audio ?: return failure(SpeechErrorCodes.IO_FAILED, "no audio"))
            return answer {
                putLong(SpeechResultKeys.DURATION_MILLIS, wav.durationMillis)
                putParcelableArrayList(SpeechResultKeys.SEGMENTS, arrayListOf(segment(0, wav.durationMillis, null)))
            }
        }

        override fun openSession(options: Bundle?, callback: ISpeechSessionCallback?): ISpeechSession {
            val listener = callback ?: throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.INVALID_OPTIONS, "no callback"))
            val model = options?.getString(SpeechOptionKeys.MODEL)
            if (model == CRASH_MODEL) {
                Thread {
                    SystemClock.sleep(300)
                    Process.killProcess(Process.myPid())
                }.start()
            }
            return if (options?.getString(SpeechOptionKeys.SOURCE) == SpeechContract.SOURCE_EXTERNAL) {
                ExternalSession(listener)
            } else {
                MicrophoneSession(listener, options?.getLong(SpeechOptionKeys.MAX_DURATION_MILLIS, 30_000L) ?: 30_000L)
            }
        }

        override fun openWakeSession(options: Bundle?, callback: ISpeechWakeCallback?, listenCallback: ISpeechSessionCallback?): ISpeechSession {
            val wake = callback ?: throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.INVALID_OPTIONS, "no callback"))
            @Suppress("DEPRECATION")
            val keywords = options?.getParcelableArrayList<Bundle>(SpeechOptionKeys.KEYWORDS).orEmpty().mapNotNull { it.getString(SpeechOptionKeys.KEYWORD) }
            val listen = options?.getBundle(SpeechOptionKeys.THEN_LISTEN) != null
            return WakeSession(wake, if (listen) listenCallback else null, keywords.firstOrNull() ?: "?")
        }

        override fun synthesizeToFile(request: Bundle?, output: ParcelFileDescriptor?, callback: ISpeechProgressCallback?): Int {
            val text = request?.getString(SpeechOptionKeys.TEXT).orEmpty()
            val target = output?.dup() ?: throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.IO_FAILED, "no output"))
            val opId = operations.incrementAndGet()
            Thread {
                try {
                    val samples = 8000
                    FileOutputStream(target.fileDescriptor).use { out ->
                        out.write(wavHeader(16_000, samples * 2))
                        out.write(ByteArray(samples * 2))
                    }
                    callback?.onProgress(opId, text.length.toLong(), text.length.toLong())
                    callback?.onCompleted(opId, answer {
                        putInt(SpeechResultKeys.SAMPLE_RATE, 16_000)
                        putLong(SpeechResultKeys.DURATION_MILLIS, 500L)
                        putString(SpeechResultKeys.MODEL_ID, request?.getString(SpeechOptionKeys.MODEL) ?: TTS_MODEL)
                        putString(SpeechResultKeys.SPEAKER_ID, request?.getString(SpeechOptionKeys.SPEAKER) ?: "0")
                    })
                } catch (t: Throwable) {
                    callback?.onFailed(opId, failure(SpeechErrorCodes.IO_FAILED, t.message))
                } finally {
                    runCatching { target.close() }
                }
            }.start()
            return opId
        }

        override fun importModel(archive: ParcelFileDescriptor?, request: Bundle?, callback: ISpeechProgressCallback?): Int {
            val opId = operations.incrementAndGet()
            val id = request?.getString(SpeechOptionKeys.MODEL) ?: "imported.model"
            Thread {
                callback?.onCompleted(opId, answer {
                    putBundle(SpeechResultKeys.MODEL, Bundle().apply {
                        putString(SpeechModelKeys.ID, id)
                        putString(SpeechModelKeys.KIND, request?.getString(SpeechOptionKeys.KIND) ?: SpeechContract.KIND_TTS)
                        putBoolean(SpeechModelKeys.INSTALLED, true)
                        putString(SpeechModelKeys.SOURCE, SpeechContract.MODEL_SOURCE_IMPORTED)
                    })
                })
            }.start()
            return opId
        }

        override fun setDefaultModel(request: Bundle?): Bundle {
            val id = request?.getString(SpeechOptionKeys.MODEL)
            val model = MODELS.firstOrNull { it.getString(SpeechModelKeys.ID) == id } ?: return failure(SpeechErrorCodes.MODEL_MISSING, "unknown model $id")
            return answer { putBundle(SpeechResultKeys.MODEL, Bundle(model).apply { putBoolean(SpeechModelKeys.IS_DEFAULT, true) }) }
        }

        override fun refreshCatalog(callback: ISpeechProgressCallback?): Int {
            val opId = operations.incrementAndGet()
            Thread {
                callback?.onCompleted(opId, answer {
                    putInt(SpeechResultKeys.CATALOG_VERSION, 1)
                    putBoolean(SpeechResultKeys.UPDATED, false)
                })
            }.start()
            return opId
        }

        // Contract version 2 (3-Macaw Speech roadmap D132): this provider stays on version 1, so the host never calls
        // these; a direct call is answered like a plugin that lacks the feature.
        override fun enrollSpeaker(audio: Array<out ParcelFileDescriptor>?, options: Bundle?): Bundle = unsupported("enrollSpeaker")

        override fun verifySpeaker(audio: ParcelFileDescriptor?, options: Bundle?): Bundle = unsupported("verifySpeaker")

        override fun identifySpeaker(audio: ParcelFileDescriptor?, options: Bundle?): Bundle = unsupported("identifySpeaker")

        override fun listSpeakers(query: Bundle?): Bundle = unsupported("listSpeakers")

        override fun removeSpeaker(request: Bundle?): Bundle = unsupported("removeSpeaker")

        override fun diarizeFile(audio: ParcelFileDescriptor?, options: Bundle?, callback: ISpeechProgressCallback?): Int =
            throw IllegalStateException(SpeechErrorCodes.encode(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, "diarizeFile"))

        override fun detectLanguage(audio: ParcelFileDescriptor?, options: Bundle?, callback: ISpeechProgressCallback?): Int =
            throw IllegalStateException(SpeechErrorCodes.encode(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, "detectLanguage"))

        override fun tagAudio(audio: ParcelFileDescriptor?, options: Bundle?, callback: ISpeechProgressCallback?): Int =
            throw IllegalStateException(SpeechErrorCodes.encode(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, "tagAudio"))

        private fun unsupported(method: String): Bundle = failure(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, "$method needs speech contract version 2")
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun microphoneGranted(): Boolean = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** External PCM: counts the fed bytes and answers them as the final text. */
    private inner class ExternalSession(private val callback: ISpeechSessionCallback) : ISpeechSession.Stub() {
        private val fed = AtomicInteger(0)
        private val ended = AtomicBoolean(false)

        init {
            callback.onReady(answer {
                putString(SpeechResultKeys.SESSION_ID, "external-${operations.incrementAndGet()}")
                putString(SpeechResultKeys.MODEL_ID, ASR_MODEL)
            })
        }

        override fun feed(pcm16: ByteArray?) {
            if (ended.get()) throw IllegalStateException(SpeechErrorCodes.encode(SpeechErrorCodes.SESSION_CLOSED))
            val total = fed.addAndGet(pcm16?.size ?: 0)
            callback.onPartial(answer {
                putString(SpeechResultKeys.TEXT, "fed $total")
                putInt(SpeechResultKeys.SEGMENT_INDEX, 0)
            })
        }

        override fun finish() {
            if (!ended.compareAndSet(false, true)) return
            val text = "fed ${fed.get()} bytes"
            callback.onFinal(answer {
                putString(SpeechResultKeys.TEXT, text)
                putInt(SpeechResultKeys.SEGMENT_INDEX, 0)
                putLong(SpeechResultKeys.START_MILLIS, 0L)
                putLong(SpeechResultKeys.END_MILLIS, fed.get() / 32L)
            })
            callback.onEndpoint()
            callback.onEnd(answer {
                putString(SpeechResultKeys.REASON, SpeechContract.REASON_STOPPED)
                putString(SpeechResultKeys.TEXT, text)
            })
        }

        override fun cancel() {
            if (!ended.compareAndSet(false, true)) return
            callback.onEnd(answer { putString(SpeechResultKeys.REASON, SpeechContract.REASON_CANCELLED) })
        }

        override fun isActive(): Boolean = !ended.get()

        override fun snapshot(): Bundle = answer {
            putString(SpeechResultKeys.STATE, if (ended.get()) SpeechContract.STATE_ENDED else SpeechContract.STATE_ACTIVE)
            putString(SpeechResultKeys.TEXT, "fed ${fed.get()}")
        }

        override fun finishRound(discard: Boolean) = Unit
    }

    /** The background microphone probe of roadmap D22. */
    private inner class MicrophoneSession(private val callback: ISpeechSessionCallback, private val maxDurationMillis: Long) : ISpeechSession.Stub() {
        private val ended = AtomicBoolean(false)

        init {
            if (!microphoneGranted()) {
                Thread {
                    callback.onError(failure(SpeechErrorCodes.PERMISSION_DENIED, "the fake plugin has no RECORD_AUDIO permission"))
                    end(SpeechContract.REASON_ERROR)
                }.start()
            } else {
                Thread(::record, "fake-speech-microphone").start()
            }
        }

        private fun record() {
            val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val record = try {
                @Suppress("MissingPermission")
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 32_000))
            } catch (t: Throwable) {
                callback.onError(failure(SpeechErrorCodes.ENGINE_FAILED, "AudioRecord: ${t.message}"))
                end(SpeechContract.REASON_ERROR)
                return
            }
            try {
                record.startRecording()
                callback.onReady(answer {
                    putString(SpeechResultKeys.SESSION_ID, "mic-${Process.myPid()}")
                    putString(SpeechResultKeys.MODEL_ID, "probe")
                })
                val started = SystemClock.elapsedRealtime()
                val buffer = ShortArray(8_000)
                while (!ended.get() && SystemClock.elapsedRealtime() - started < maxDurationMillis) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 0) {
                        callback.onWarning(failure(SpeechErrorCodes.ENGINE_FAILED, "AudioRecord.read = $read"))
                        SystemClock.sleep(500)
                        continue
                    }
                    var peak = 0
                    var nonZero = 0
                    for (i in 0 until read) {
                        val v = abs(buffer[i].toInt())
                        if (v > peak) peak = v
                        if (v != 0) nonZero++
                    }
                    val silenced = if (Build.VERSION.SDK_INT >= 29) record.activeRecordingConfiguration?.isClientSilenced else null
                    callback.onVoiceActivity(answer {
                        putBoolean(SpeechResultKeys.ACTIVE, nonZero > 0)
                        putInt(PROBE_PEAK, peak)
                        putInt(PROBE_NON_ZERO, nonZero)
                        putInt(PROBE_FRAMES, read)
                        putLong(PROBE_AT, System.currentTimeMillis())
                        if (silenced != null) putBoolean(PROBE_SILENCED, silenced)
                    })
                }
            } catch (t: Throwable) {
                callback.onError(failure(SpeechErrorCodes.ENGINE_FAILED, "AudioRecord: ${t.message}"))
            } finally {
                runCatching { record.stop() }
                record.release()
                end(SpeechContract.REASON_STOPPED)
            }
        }

        private fun end(reason: String) {
            if (ended.getAndSet(true) && reason != SpeechContract.REASON_STOPPED) return
            runCatching { callback.onEnd(answer { putString(SpeechResultKeys.REASON, reason) }) }
        }

        override fun feed(pcm16: ByteArray?) {
            throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.INVALID_OPTIONS, "feed() is for external sessions"))
        }

        override fun finish() {
            ended.set(true)
        }

        override fun cancel() {
            ended.set(true)
        }

        override fun isActive(): Boolean = !ended.get()

        override fun snapshot(): Bundle = answer { putString(SpeechResultKeys.STATE, if (ended.get()) SpeechContract.STATE_ENDED else SpeechContract.STATE_ACTIVE) }

        override fun finishRound(discard: Boolean) = Unit
    }

    /** One hit after 200 ms; with a listen callback one dictation round follows the hit. */
    private inner class WakeSession(
        private val callback: ISpeechWakeCallback,
        private val listen: ISpeechSessionCallback?,
        private val keyword: String,
    ) : ISpeechSession.Stub() {
        private val ended = AtomicBoolean(false)

        init {
            Thread {
                callback.onReady(answer {
                    putString(SpeechResultKeys.SESSION_ID, "wake-${operations.incrementAndGet()}")
                    putString(SpeechResultKeys.MODEL_ID, KWS_MODEL)
                })
                SystemClock.sleep(200)
                if (ended.get()) return@Thread
                callback.onWake(answer {
                    putString(SpeechResultKeys.KEYWORD, keyword)
                    putFloat(SpeechResultKeys.SCORE, 0.75f)
                    putLong(SpeechResultKeys.TIMESTAMP_MILLIS, System.currentTimeMillis())
                    if (listen != null) putInt(SpeechResultKeys.ROUND, 0)
                })
                listen?.let { round ->
                    round.onReady(answer {
                        putString(SpeechResultKeys.SESSION_ID, "round-0")
                        putString(SpeechResultKeys.MODEL_ID, ASR_MODEL)
                        putInt(SpeechResultKeys.ROUND, 0)
                    })
                    round.onPartial(answer { putString(SpeechResultKeys.TEXT, "turn on") })
                    round.onFinal(answer {
                        putString(SpeechResultKeys.TEXT, "turn on the light")
                        putInt(SpeechResultKeys.SEGMENT_INDEX, 0)
                    })
                    round.onEnd(answer {
                        putString(SpeechResultKeys.REASON, SpeechContract.REASON_STOPPED)
                        putString(SpeechResultKeys.TEXT, "turn on the light")
                        putInt(SpeechResultKeys.ROUND, 0)
                    })
                }
            }.start()
        }

        override fun feed(pcm16: ByteArray?) {
            throw IllegalArgumentException(SpeechErrorCodes.encode(SpeechErrorCodes.INVALID_OPTIONS, "feed() is for external sessions"))
        }

        override fun finish() {
            if (!ended.compareAndSet(false, true)) return
            callback.onEnd(answer { putString(SpeechResultKeys.REASON, SpeechContract.REASON_STOPPED) })
        }

        override fun cancel() {
            if (!ended.compareAndSet(false, true)) return
            callback.onEnd(answer { putString(SpeechResultKeys.REASON, SpeechContract.REASON_CANCELLED) })
        }

        override fun isActive(): Boolean = !ended.get()

        override fun snapshot(): Bundle = answer { putString(SpeechResultKeys.STATE, if (ended.get()) SpeechContract.STATE_ENDED else SpeechContract.STATE_ACTIVE) }

        override fun finishRound(discard: Boolean) = Unit
    }

    private class Wav(val sampleRate: Int, val dataBytes: Long) {
        val durationMillis: Long get() = if (sampleRate <= 0) 0L else dataBytes * 1000L / (sampleRate * 2L)
    }

    /** Reads the canonical 44-byte header the host writes (and the test fixtures use). */
    private fun readWav(audio: ParcelFileDescriptor): Wav {
        val header = ByteArray(44)
        FileInputStream(audio.fileDescriptor).use { input ->
            var read = 0
            while (read < header.size) {
                val n = input.read(header, read, header.size - read)
                if (n < 0) break
                read += n
            }
        }
        fun int32(offset: Int) = (header[offset].toInt() and 0xff) or ((header[offset + 1].toInt() and 0xff) shl 8) or
            ((header[offset + 2].toInt() and 0xff) shl 16) or ((header[offset + 3].toInt() and 0xff) shl 24)
        return Wav(int32(24), int32(40).toLong() and 0xffffffffL)
    }

    companion object {
        const val FAKE_ID = "fake-speech"
        const val ASR_MODEL = "asr.fake.zh-en"
        const val TTS_MODEL = "tts.fake.zh"
        const val KWS_MODEL = "kws.fake.zh-en"
        const val NON_COMMERCIAL = "tts.fake.noncommercial"
        const val MISSING_MODEL = "asr.missing"
        const val CRASH_MODEL = "crash.process"

        const val PROBE_PEAK = "peak"
        const val PROBE_NON_ZERO = "nonZero"
        const val PROBE_FRAMES = "frames"
        const val PROBE_SILENCED = "silenced"
        const val PROBE_AT = "at"

        val FEATURES = arrayOf(
            SpeechContract.FEATURE_ASR,
            SpeechContract.FEATURE_ASR_STREAMING,
            SpeechContract.FEATURE_VAD,
            SpeechContract.FEATURE_KWS,
            SpeechContract.FEATURE_TTS,
            SpeechContract.FEATURE_MODELS,
        )

        private fun model(id: String, kind: String, isDefault: Boolean, commercial: Boolean = true): Bundle = Bundle().apply {
            putString(SpeechModelKeys.ID, id)
            putString(SpeechModelKeys.KIND, kind)
            putString(SpeechModelKeys.MODEL_TYPE, "fake")
            putString(SpeechModelKeys.NAME, id)
            putStringArray(SpeechModelKeys.LANGUAGES, arrayOf("zh", "en"))
            putLong(SpeechModelKeys.SIZE_BYTES, 1024L)
            putBoolean(SpeechModelKeys.INSTALLED, isDefault)
            putBoolean(SpeechModelKeys.IS_DEFAULT, isDefault)
            putBoolean(SpeechModelKeys.RECOMMENDED, true)
            putInt(SpeechModelKeys.VERSION, 1)
            putBundle(SpeechModelKeys.LICENSE, Bundle().apply {
                putString(SpeechModelKeys.LICENSE_SPDX, if (commercial) "Apache-2.0" else "LicenseRef-NonCommercial")
                putBoolean(SpeechModelKeys.LICENSE_COMMERCIAL_USE, commercial)
            })
            putString(SpeechModelKeys.SOURCE, SpeechContract.MODEL_SOURCE_CATALOG)
        }

        val MODELS: List<Bundle> = listOf(
            model(ASR_MODEL, SpeechContract.KIND_ASR, isDefault = true),
            model(TTS_MODEL, SpeechContract.KIND_TTS, isDefault = true),
            model(KWS_MODEL, SpeechContract.KIND_KWS, isDefault = true),
            model(NON_COMMERCIAL, SpeechContract.KIND_TTS, isDefault = false, commercial = false),
        )

        fun answer(build: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
            putInt(SpeechContract.KEY_CONTRACT_VERSION, SpeechContract.CALL_VERSION_1)
            build()
        }

        fun failure(code: String, message: String?): Bundle = answer {
            putString(SpeechContract.KEY_ERROR_CODE, code)
            putString(SpeechContract.KEY_ERROR_MESSAGE, message ?: code)
        }

        /** The canonical 44-byte header of mono PCM16 LE. */
        fun wavHeader(sampleRate: Int, dataBytes: Int): ByteArray {
            val buffer = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + dataBytes).put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            buffer.putShort(2).putShort(16).put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
            return buffer.array()
        }

        fun segment(start: Long, end: Long, text: String?): Bundle = Bundle().apply {
            putLong(SpeechResultKeys.START_MILLIS, start)
            putLong(SpeechResultKeys.END_MILLIS, end)
            text?.let { putString(SpeechResultKeys.TEXT, it) }
        }
    }
}

/**
 * Asks for `RECORD_AUDIO` on behalf of the fake plugin (roadmap D22 probe): the maintainer answers the system dialog
 * on the device. `adb shell am start -n org.autojs.plugin.speech.fake/.FakeSpeechPermissionActivity`
 */
class FakeSpeechPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
