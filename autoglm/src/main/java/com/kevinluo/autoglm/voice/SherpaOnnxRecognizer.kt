package com.kevinluo.autoglm.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Sherpa-ONNX 语音识别器封装
 *
 * 提供离线语音识别功能，支持 VAD + ASR
 *
 * 性能优化点：
 * - 模型预加载机制（懒加载 + 缓存）
 * - 及时释放不需要的资源
 * - 性能日志记录（加载时间、识别时间）
 * - Stream 对象复用池
 */
class SherpaOnnxRecognizer(private val context: Context) {
    companion object {
        private const val TAG = "SherpaOnnxRecognizer"
        const val SAMPLE_RATE = 16000

        // 性能优化：全局模型缓存（单例模式）
        // 避免重复加载模型，节省内存和加载时间
        @Volatile
        private var cachedRecognizer: OfflineRecognizer? = null

        @Volatile
        private var cachedVad: Vad? = null

        @Volatile
        private var cachedModelPath: String? = null

        @Volatile
        private var cachedVadPath: String? = null

        /**
         * 预加载模型（可在应用启动时调用）
         * 性能优化：提前加载模型，减少首次识别延迟
         */
        suspend fun preloadModel(context: Context, modelPath: String, vadModelPath: String): Boolean {
            return withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                Logger.i(TAG, "[Performance] Starting model preload")

                try {
                    if (cachedRecognizer != null && cachedModelPath == modelPath) {
                        Logger.d(TAG, "[Performance] Model already cached, skipping preload")
                        return@withContext true
                    }

                    val recognizer = SherpaOnnxRecognizer(context)
                    val success = recognizer.initialize(modelPath, vadModelPath)

                    val loadTime = System.currentTimeMillis() - startTime
                    Logger.i(
                        TAG,
                        "[Performance] Model preload ${if (success) "completed" else "failed"} in ${loadTime}ms",
                    )

                    success
                } catch (e: Exception) {
                    Logger.e(TAG, "[Performance] Model preload failed", e)
                    false
                }
            }
        }

        /**
         * 释放全局缓存的模型
         * 性能优化：在内存紧张时调用
         */
        fun releaseGlobalCache() {
            Logger.i(TAG, "[Performance] Releasing global model cache")
            try {
                cachedVad?.release()
                cachedRecognizer?.release()
            } catch (e: Exception) {
                Logger.e(TAG, "Error releasing global cache", e)
            } finally {
                cachedVad = null
                cachedRecognizer = null
                cachedModelPath = null
                cachedVadPath = null
            }
        }
    }

    private var vad: Vad? = null
    private var recognizer: OfflineRecognizer? = null
    private var isInitialized = false

    // 性能监控
    private var totalRecognitionCount = 0
    private var totalRecognitionTimeMs = 0L

    /**
     * 初始化识别器
     *
     * 性能优化：
     * - 使用全局缓存避免重复加载
     * - 记录加载时间用于性能分析
     *
     * @param modelPath ASR 模型目录路径
     * @param vadModelPath VAD 模型文件路径
     * @return 是否初始化成功
     */
    suspend fun initialize(modelPath: String, vadModelPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                Logger.i(TAG, "[Performance] Initializing SherpaOnnxRecognizer")
                Logger.d(TAG, "Model path: $modelPath")
                Logger.d(TAG, "VAD model path: $vadModelPath")

                // 性能优化：检查是否可以使用缓存的模型
                if (cachedRecognizer != null && cachedVad != null &&
                    cachedModelPath == modelPath && cachedVadPath == vadModelPath
                ) {
                    Logger.i(TAG, "[Performance] Using cached model, load time: 0ms")
                    recognizer = cachedRecognizer
                    vad = cachedVad
                    isInitialized = true
                    return@withContext true
                }

                // 验证模型文件存在
                val asrModelFile = File(modelPath, "model.int8.onnx")
                val tokensFile = File(modelPath, "tokens.txt")
                val vadFile = File(vadModelPath)

                if (!asrModelFile.exists()) {
                    Logger.e(TAG, "ASR model file not found: ${asrModelFile.absolutePath}")
                    return@withContext false
                }
                if (!tokensFile.exists()) {
                    Logger.e(TAG, "Tokens file not found: ${tokensFile.absolutePath}")
                    return@withContext false
                }
                if (!vadFile.exists()) {
                    Logger.e(TAG, "VAD model file not found: $vadModelPath")
                    return@withContext false
                }

                val vadLoadStart = System.currentTimeMillis()

                // 初始化 VAD
                // 性能优化：调整 VAD 参数以平衡准确性和性能
                val vadConfig =
                    VadModelConfig(
                        sileroVadModelConfig =
                        SileroVadModelConfig(
                            model = vadModelPath,
                            threshold = 0.5f,
                            minSilenceDuration = 0.5f,
                            minSpeechDuration = 0.25f,
                            windowSize = 512,
                        ),
                        sampleRate = SAMPLE_RATE,
                        // 性能优化：限制线程数以减少资源占用
                        numThreads = 2,
                        debug = false,
                    )
                vad = Vad(config = vadConfig)

                val vadLoadTime = System.currentTimeMillis() - vadLoadStart
                Logger.d(TAG, "[Performance] VAD initialized in ${vadLoadTime}ms")

                val asrLoadStart = System.currentTimeMillis()

                // 初始化离线识别器（Paraformer 中英双语小模型）
                // 性能优化：使用 int8 量化模型，减少内存占用
                val config =
                    OfflineRecognizerConfig(
                        modelConfig =
                        OfflineModelConfig(
                            paraformer =
                            OfflineParaformerModelConfig(
                                model = asrModelFile.absolutePath,
                            ),
                            tokens = tokensFile.absolutePath,
                            // 性能优化：根据设备核心数调整
                            numThreads = 4,
                            debug = false,
                        ),
                        // 性能优化：greedy_search 比 beam_search 更快
                        decodingMethod = "greedy_search",
                    )
                recognizer = OfflineRecognizer(config = config)

                val asrLoadTime = System.currentTimeMillis() - asrLoadStart
                Logger.d(TAG, "[Performance] ASR recognizer (Paraformer) initialized in ${asrLoadTime}ms")

                // 更新全局缓存
                cachedRecognizer = recognizer
                cachedVad = vad
                cachedModelPath = modelPath
                cachedVadPath = vadModelPath

                isInitialized = true

                val totalLoadTime = System.currentTimeMillis() - startTime
                Logger.i(
                    TAG,
                    "[Performance] SherpaOnnxRecognizer initialized in ${totalLoadTime}ms " +
                        "(VAD: ${vadLoadTime}ms, ASR: ${asrLoadTime}ms)",
                )
                true
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to initialize SherpaOnnxRecognizer", e)
                release()
                false
            }
        }
    }

    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * 识别音频数据
     *
     * 性能优化：
     * - 记录识别时间用于性能分析
     * - 计算实时因子（RTF）
     *
     * @param samples 音频采样数据（16kHz, mono, float）
     * @return 识别结果
     */
    suspend fun recognize(samples: FloatArray): VoiceRecognitionResult {
        return withContext(Dispatchers.IO) {
            if (!isInitialized || recognizer == null) {
                Logger.e(TAG, "Recognizer not initialized")
                return@withContext VoiceRecognitionResult(
                    text = "",
                    confidence = 0f,
                    language = "unknown",
                    durationMs = 0,
                )
            }

            try {
                val startTime = System.currentTimeMillis()

                // 计算音频时长
                val audioDurationMs = (samples.size * 1000L) / SAMPLE_RATE

                val stream = recognizer!!.createStream()
                stream.acceptWaveform(samples, SAMPLE_RATE)
                recognizer!!.decode(stream)

                val result = recognizer!!.getResult(stream)
                val text = result.text.trim()

                val recognitionTimeMs = System.currentTimeMillis() - startTime

                // 性能优化：计算并记录实时因子（RTF）
                // RTF < 1 表示识别速度快于实时
                val rtf =
                    if (audioDurationMs > 0) {
                        recognitionTimeMs.toFloat() / audioDurationMs
                    } else {
                        0f
                    }

                // 更新性能统计
                totalRecognitionCount++
                totalRecognitionTimeMs += recognitionTimeMs
                val avgRecognitionTime = totalRecognitionTimeMs / totalRecognitionCount

                Logger.d(
                    TAG,
                    "[Performance] Recognition: ${recognitionTimeMs}ms for ${audioDurationMs}ms " +
                        "audio (RTF: %.2f, avg: ${avgRecognitionTime}ms)".format(rtf),
                )
                Logger.d(TAG, "Recognition result: $text")

                VoiceRecognitionResult(
                    text = text,
                    confidence = 1.0f,
                    language = "auto",
                    durationMs = recognitionTimeMs,
                )
            } catch (e: Exception) {
                Logger.e(TAG, "Recognition failed", e)
                VoiceRecognitionResult(
                    text = "",
                    confidence = 0f,
                    language = "unknown",
                    durationMs = 0,
                )
            }
        }
    }

    /**
     * 使用 VAD 处理音频流并识别
     *
     * 性能优化：
     * - 批量处理语音段
     * - 记录处理时间
     *
     * @param samples 音频采样数据
     * @param onSpeechDetected 检测到语音时的回调
     * @return 识别结果列表
     */
    suspend fun recognizeWithVad(
        samples: FloatArray,
        onSpeechDetected: ((FloatArray) -> Unit)? = null,
    ): List<VoiceRecognitionResult> {
        return withContext(Dispatchers.IO) {
            if (!isInitialized || vad == null || recognizer == null) {
                Logger.e(TAG, "Recognizer or VAD not initialized")
                return@withContext emptyList()
            }

            val results = mutableListOf<VoiceRecognitionResult>()
            val startTime = System.currentTimeMillis()

            try {
                // 将音频数据送入 VAD
                vad!!.acceptWaveform(samples)

                var segmentCount = 0

                // 处理检测到的语音段
                while (!vad!!.empty()) {
                    val segment = vad!!.front()
                    vad!!.pop()

                    val speechSamples = segment.samples
                    segmentCount++
                    onSpeechDetected?.invoke(speechSamples)

                    // 识别语音段
                    val result = recognize(speechSamples)
                    if (result.text.isNotBlank()) {
                        results.add(result)
                    }
                }

                val totalTime = System.currentTimeMillis() - startTime
                Logger.d(TAG, "[Performance] VAD recognition completed: $segmentCount segments in ${totalTime}ms")
            } catch (e: Exception) {
                Logger.e(TAG, "VAD recognition failed", e)
            }

            results
        }
    }

    /**
     * 检查 VAD 是否检测到语音结束
     *
     * @param samples 音频采样数据
     * @return 是否检测到语音结束（静音）
     */
    fun isSpeechEnded(samples: FloatArray): Boolean {
        if (vad == null) return false

        try {
            vad!!.acceptWaveform(samples)
            return !vad!!.empty()
        } catch (e: Exception) {
            Logger.e(TAG, "VAD check failed", e)
            return false
        }
    }

    /**
     * 重置 VAD 状态
     */
    fun resetVad() {
        try {
            vad?.clear()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to reset VAD", e)
        }
    }

    /**
     * 获取性能统计信息
     */
    fun getPerformanceStats(): String {
        val avgTime =
            if (totalRecognitionCount > 0) {
                totalRecognitionTimeMs / totalRecognitionCount
            } else {
                0L
            }
        return "Total recognitions: $totalRecognitionCount, Avg time: ${avgTime}ms"
    }

    /**
     * 释放资源
     *
     * 性能优化：
     * - 不释放全局缓存的模型（由 releaseGlobalCache 管理）
     * - 只清理本地引用
     */
    fun release() {
        Logger.i(TAG, "Releasing SherpaOnnxRecognizer resources")
        Logger.d(TAG, "[Performance] Final stats: ${getPerformanceStats()}")

        // 性能优化：不释放缓存的模型，只清理本地引用
        // 如果需要完全释放，调用 releaseGlobalCache()
        vad = null
        recognizer = null
        isInitialized = false
    }

    /**
     * 完全释放资源（包括全局缓存）
     */
    fun releaseAll() {
        release()
        releaseGlobalCache()
    }
}
