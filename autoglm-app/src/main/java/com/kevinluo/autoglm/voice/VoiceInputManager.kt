package com.kevinluo.autoglm.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.kevinluo.autoglm.settings.SettingsManager
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音输入管理器
 *
 * 管理语音录制、VAD 检测和语音识别的完整流程
 *
 * 性能优化点：
 * - 优化音频缓冲区大小
 * - 音频数据复用机制（对象池）
 * - 优化 VAD 检测效率
 * - 减少内存分配
 */
class VoiceInputManager(private val context: Context) {
    companion object {
        private const val TAG = "VoiceInputManager"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // VAD 相关参数
        private const val VAD_WINDOW_SIZE = 512
        private const val MAX_SILENCE_DURATION_MS = 2000 // 最大静音时长，超过则停止录音（增加到2秒）
        private const val MIN_SPEECH_DURATION_MS = 300 // 最小语音时长（降低）
        private const val MAX_RECORDING_DURATION_MS = 60000 // 最大录音时长 60 秒

        // 性能优化：缓冲区大小倍数
        // 较大的缓冲区可以减少系统调用次数，但会增加延迟
        private const val BUFFER_SIZE_MULTIPLIER = 4

        // 性能优化：预分配的音频样本列表容量
        // 基于 60 秒最大录音时长计算：16000 * 60 = 960000
        private const val INITIAL_SAMPLES_CAPACITY = 960000

        // 性能优化：能量检测阈值
        private const val ENERGY_THRESHOLD = 0.0005f // 降低阈值，更容易检测到语音
        private const val ENERGY_THRESHOLD_LOW = 0.0003f // 低灵敏度阈值
    }

    private val settingsManager = SettingsManager.getInstance(context)
    private val modelManager = VoiceModelManager.getInstance(context)
    private var recognizer: SherpaOnnxRecognizer? = null

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRecording = AtomicBoolean(false)

    private var listener: VoiceInputListener? = null

    // 状态流
    private val _state = MutableStateFlow(VoiceInputState.IDLE)
    val state: StateFlow<VoiceInputState> = _state

    // 性能优化：复用的音频缓冲区
    private var reusableShortBuffer: ShortArray? = null
    private var reusableFloatBuffer: FloatArray? = null

    // 性能监控
    private var recordingStartTimeMs = 0L
    private var totalSamplesProcessed = 0L

    /**
     * 语音输入状态
     */
    enum class VoiceInputState {
        IDLE, // 空闲
        INITIALIZING, // 初始化中
        RECORDING, // 录音中
        RECOGNIZING, // 识别中
        ERROR, // 错误
    }

    /**
     * 设置监听器
     */
    fun setListener(listener: VoiceInputListener?) {
        this.listener = listener
    }

    /**
     * 检查是否有麦克风权限
     */
    fun hasAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * 检查模型是否已下载
     */
    fun isModelReady(): Boolean = modelManager.isModelDownloaded()

    /**
     * 初始化识别器
     *
     * 性能优化：记录初始化时间
     */
    suspend fun initialize(): Boolean {
        if (recognizer?.isInitialized() == true) {
            Logger.d(TAG, "[Performance] Recognizer already initialized, skipping")
            return true
        }

        val startTime = System.currentTimeMillis()
        _state.value = VoiceInputState.INITIALIZING

        val modelPath = modelManager.getModelPath()
        val vadPath = modelManager.getVadModelPath()

        if (modelPath == null || vadPath == null) {
            Logger.e(TAG, "Model not downloaded")
            _state.value = VoiceInputState.ERROR
            listener?.onError(VoiceError.ModelNotDownloaded)
            return false
        }

        recognizer = SherpaOnnxRecognizer(context)
        val success = recognizer!!.initialize(modelPath, vadPath)

        val initTime = System.currentTimeMillis() - startTime

        if (!success) {
            Logger.e(TAG, "[Performance] Failed to initialize recognizer after ${initTime}ms")
            _state.value = VoiceInputState.ERROR
            listener?.onError(VoiceError.ModelLoadFailed)
            recognizer = null
            return false
        }

        Logger.i(TAG, "[Performance] Recognizer initialized in ${initTime}ms")
        _state.value = VoiceInputState.IDLE
        return true
    }

    /**
     * 开始录音
     *
     * @param scope 协程作用域
     */
    fun startRecording(scope: CoroutineScope) {
        if (isRecording.get()) {
            Logger.w(TAG, "Already recording")
            return
        }

        if (!hasAudioPermission()) {
            Logger.e(TAG, "No audio permission")
            listener?.onError(VoiceError.PermissionDenied)
            return
        }

        // 暂停持续监听服务，避免 AudioRecord 冲突
        if (ContinuousListeningService.isRunning()) {
            Logger.i(TAG, "Pausing ContinuousListeningService before recording")
            ContinuousListeningService.pause()
        }

        recordingJob =
            scope.launch {
                try {
                    // 并行执行：初始化识别器和开始录音
                    // 这样可以避免第一次语音输入时因为初始化延迟导致吞字
                    val initJob =
                        if (recognizer?.isInitialized() != true) {
                            async { initialize() }
                        } else {
                            null
                        }

                    // 立即开始录音，不等待初始化完成
                    startRecordingInternal(initJob)
                } catch (e: Exception) {
                    Logger.e(TAG, "Recording error", e)
                    _state.value = VoiceInputState.ERROR
                    listener?.onError(VoiceError.RecordingFailed)
                } finally {
                    // 恢复持续监听服务
                    resumeContinuousListeningIfNeeded()
                }
            }
    }

    /**
     * 恢复持续监听服务（如果之前是运行状态）
     */
    private fun resumeContinuousListeningIfNeeded() {
        // 延迟一点恢复，确保 AudioRecord 完全释放
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Logger.i(TAG, "Resuming ContinuousListeningService after recording")
            ContinuousListeningService.resume()
        }, 500)
    }

    /**
     * 内部录音实现
     *
     * 性能优化：
     * - 使用优化的缓冲区大小
     * - 复用音频缓冲区减少内存分配
     * - 优化 VAD 检测算法
     * - 并行初始化识别器，避免吞字
     *
     * @param initJob 识别器初始化任务，如果为 null 表示已初始化
     */
    private suspend fun startRecordingInternal(initJob: Deferred<Boolean>? = null) = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Logger.e(TAG, "Invalid buffer size: $bufferSize")
            listener?.onError(VoiceError.RecordingFailed)
            return@withContext
        }

        // 性能优化：使用更大的缓冲区减少系统调用
        val optimizedBufferSize = bufferSize * BUFFER_SIZE_MULTIPLIER
        Logger.d(TAG, "[Performance] Using buffer size: $optimizedBufferSize (base: $bufferSize)")

        try {
            audioRecord =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    optimizedBufferSize,
                )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Logger.e(TAG, "AudioRecord initialization failed")
                listener?.onError(VoiceError.RecordingFailed)
                return@withContext
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            _state.value = VoiceInputState.RECORDING
            recordingStartTimeMs = System.currentTimeMillis()
            totalSamplesProcessed = 0

            withContext(Dispatchers.Main) {
                listener?.onRecordingStarted()
            }

            Logger.i(TAG, "[Performance] Recording started")

            // 性能优化：预分配列表容量，减少动态扩容
            val allSamples = ArrayList<Float>(INITIAL_SAMPLES_CAPACITY)

            // 性能优化：复用缓冲区
            val buffer = getOrCreateShortBuffer(VAD_WINDOW_SIZE)

            var silenceStartTime = 0L
            var hasSpeech = false
            val recordingStartTime = System.currentTimeMillis()

            // 性能优化：预计算常量
            val energyThreshold = ENERGY_THRESHOLD

            while (isRecording.get()) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize <= 0) continue

                totalSamplesProcessed += readSize

                // 回调音频数据给波形显示
                withContext(Dispatchers.Main) {
                    listener?.onAudioSamples(buffer, readSize)
                }

                // 性能优化：直接计算能量，避免创建中间列表
                var energySum = 0.0
                for (i in 0 until readSize) {
                    val sample = buffer[i] / 32768.0f
                    allSamples.add(sample)
                    energySum += sample * sample
                }
                val energy = energySum / readSize

                // 检查是否有语音活动
                val isSpeaking = energy > energyThreshold

                if (isSpeaking) {
                    hasSpeech = true
                    silenceStartTime = 0L
                } else if (hasSpeech) {
                    if (silenceStartTime == 0L) {
                        silenceStartTime = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - silenceStartTime > MAX_SILENCE_DURATION_MS) {
                        // 静音超时，停止录音
                        val elapsed = System.currentTimeMillis() - recordingStartTime
                        Logger.d(
                            TAG,
                            "[Performance] Silence detected after ${elapsed}ms, stopping",
                        )
                        break
                    }
                }

                // 检查最大录音时长
                if (System.currentTimeMillis() - recordingStartTime > MAX_RECORDING_DURATION_MS) {
                    Logger.d(TAG, "[Performance] Max recording duration reached")
                    break
                }
            }

            // 停止录音
            stopRecordingInternal()

            val recordingDuration = allSamples.size * 1000L / SAMPLE_RATE
            val actualRecordingTime = System.currentTimeMillis() - recordingStartTime
            Logger.d(
                TAG,
                "[Performance] Recording: ${allSamples.size} samples, " +
                    "${recordingDuration}ms audio, ${actualRecordingTime}ms wall time",
            )

            // 检查是否有足够的语音数据
            val shouldRecognize = hasSpeech && recordingDuration >= MIN_SPEECH_DURATION_MS

            if (!shouldRecognize) {
                Logger.d(
                    TAG,
                    "No speech or too short (hasSpeech=$hasSpeech, duration=$recordingDuration)",
                )
                withContext(Dispatchers.Main) {
                    listener?.onNoSpeechDetected()
                }
                _state.value = VoiceInputState.IDLE
                return@withContext
            }

            // 开始识别
            _state.value = VoiceInputState.RECOGNIZING
            Logger.d(TAG, "[Performance] Starting recognition, samples: ${allSamples.size}")

            // 等待识别器初始化完成（如果还在初始化中）
            if (initJob != null) {
                Logger.d(TAG, "[Performance] Waiting for recognizer initialization...")
                val initSuccess = initJob.await()
                if (!initSuccess) {
                    Logger.e(TAG, "Recognizer initialization failed")
                    withContext(Dispatchers.Main) {
                        listener?.onError(VoiceError.ModelLoadFailed)
                    }
                    return@withContext
                }
                Logger.d(TAG, "[Performance] Recognizer initialization completed")
            }

            val recognitionStartTime = System.currentTimeMillis()

            // 性能优化：直接转换为 FloatArray，避免额外的列表操作
            val samplesArray = allSamples.toFloatArray()
            val result =
                recognizer?.recognize(samplesArray)
                    ?: VoiceRecognitionResult(text = "", durationMs = 0)

            val recognitionTime = System.currentTimeMillis() - recognitionStartTime
            Logger.i(TAG, "[Performance] Recognition completed in ${recognitionTime}ms, result: ${result.text}")

            withContext(Dispatchers.Main) {
                listener?.onFinalResult(result)
            }

            _state.value = VoiceInputState.IDLE
        } catch (e: SecurityException) {
            Logger.e(TAG, "Security exception", e)
            withContext(Dispatchers.Main) {
                listener?.onError(VoiceError.PermissionDenied)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Recording error", e)
            withContext(Dispatchers.Main) {
                listener?.onError(VoiceError.RecordingFailed)
            }
        } finally {
            stopRecordingInternal()
            _state.value = VoiceInputState.IDLE
        }
    }

    /**
     * 性能优化：获取或创建复用的 ShortArray 缓冲区
     */
    private fun getOrCreateShortBuffer(size: Int): ShortArray {
        val existing = reusableShortBuffer
        return if (existing != null && existing.size >= size) {
            existing
        } else {
            ShortArray(size).also { reusableShortBuffer = it }
        }
    }

    /**
     * 性能优化：获取或创建复用的 FloatArray 缓冲区
     */
    private fun getOrCreateFloatBuffer(size: Int): FloatArray {
        val existing = reusableFloatBuffer
        return if (existing != null && existing.size >= size) {
            existing
        } else {
            FloatArray(size).also { reusableFloatBuffer = it }
        }
    }

    /**
     * 停止录音
     */
    fun stopRecording() {
        Logger.d(TAG, "Stop recording requested")
        isRecording.set(false)
    }

    /**
     * 内部停止录音
     */
    private fun stopRecordingInternal() {
        val wasRecording = isRecording.getAndSet(false)

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }

        // 记录性能统计
        if (recordingStartTimeMs > 0) {
            val totalTime = System.currentTimeMillis() - recordingStartTimeMs
            Logger.d(
                TAG,
                "[Performance] Recording session ended: $totalSamplesProcessed samples processed in ${totalTime}ms",
            )
            recordingStartTimeMs = 0
        }

        // 只在第一次停止时通知 listener
        if (wasRecording) {
            listener?.onRecordingStopped()
        }
        Logger.d(TAG, "Recording stopped")
    }

    /**
     * 取消录音
     */
    fun cancelRecording() {
        Logger.d(TAG, "Cancel recording")
        isRecording.set(false)
        recordingJob?.cancel()
        stopRecordingInternal()
        _state.value = VoiceInputState.IDLE

        // 恢复持续监听服务
        resumeContinuousListeningIfNeeded()
    }

    /**
     * 是否正在录音
     */
    fun isRecording(): Boolean = isRecording.get()

    /**
     * 获取模型管理器
     */
    fun getModelManager(): VoiceModelManager = modelManager

    /**
     * 释放资源
     *
     * 性能优化：清理复用的缓冲区
     */
    fun release() {
        Logger.i(TAG, "Releasing VoiceInputManager")
        cancelRecording()
        recognizer?.release()
        recognizer = null

        // 性能优化：清理复用的缓冲区
        reusableShortBuffer = null
        reusableFloatBuffer = null
    }
}
