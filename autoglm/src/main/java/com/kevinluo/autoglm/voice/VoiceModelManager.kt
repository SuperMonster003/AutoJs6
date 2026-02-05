package com.kevinluo.autoglm.voice

import android.content.Context
import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音模型状态的密封类
 *
 * 使用状态机模式管理语音模型的生命周期状态，确保状态一致性。
 *
 * 状态转换图:
 * ```
 *   ┌─────────────┐
 *   │ NotDownloaded│◄──────────────────────────┐
 *   └──────┬──────┘                            │
 *          │ downloadModel()                   │
 *          ▼                                   │
 *   ┌─────────────┐                            │
 *   │ Downloading │──────────────┐             │
 *   └──────┬──────┘              │             │
 *          │ success             │ cancel/fail │
 *          ▼                     ▼             │
 *   ┌─────────────┐       ┌─────────────┐      │
 *   │ Downloaded  │       │   Error     │──────┤
 *   └──────┬──────┘       └─────────────┘      │
 *          │ deleteModel()                     │
 *          └───────────────────────────────────┘
 * ```
 */
sealed class VoiceModelState {
    /**
     * 模型未下载状态
     */
    object NotDownloaded : VoiceModelState()

    /**
     * 模型下载中状态
     *
     * @property progress 下载进度 (0-100)
     * @property downloadedBytes 已下载字节数
     * @property totalBytes 总字节数
     */
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : VoiceModelState()

    /**
     * 模型已下载状态
     *
     * @property path 模型文件路径
     * @property sizeMB 模型大小（MB）
     */
    data class Downloaded(val path: String, val sizeMB: Long) : VoiceModelState()

    /**
     * 下载错误状态
     *
     * @property message 错误信息
     */
    data class Error(val message: String) : VoiceModelState()
}

/**
 * 语音模型管理器（单例）
 *
 * 负责语音识别模型的下载、解压、删除和状态管理。
 * 使用状态机模式管理模型状态，通过 StateFlow 提供响应式状态更新。
 *
 * 采用单例模式确保：
 * - 所有模块共享同一个状态
 * - 状态变化可以通过 StateFlow 实时通知所有观察者
 * - 资源只初始化一次
 *
 * 性能优化点：
 * - 下载断点续传支持
 * - 优化解压性能（使用缓冲）
 * - 性能日志记录
 */
class VoiceModelManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "VoiceModelManager"

        // 单例实例
        @Volatile
        private var instance: VoiceModelManager? = null

        /**
         * 获取 VoiceModelManager 单例实例
         *
         * @param context Android 上下文，会自动转换为 ApplicationContext
         * @return VoiceModelManager 单例实例
         */
        fun getInstance(context: Context): VoiceModelManager = instance ?: synchronized(this) {
            instance ?: VoiceModelManager(context.applicationContext).also {
                instance = it
                Logger.d(TAG, "VoiceModelManager singleton instance created")
            }
        }

        // 模型下载地址 (HuggingFace 镜像 - Paraformer 中英双语小模型)
        private const val PARAFORMER_MODEL_URL =
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09/resolve/main/model.int8.onnx"
        private const val PARAFORMER_TOKENS_URL =
            "https://hf-mirror.com/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09/resolve/main/tokens.txt"
        private const val VAD_MODEL_URL =
            "https://modelscope.cn/models/pengzhendong/silero-vad/resolve/master/silero_vad.onnx"

        // 模型目录名
        private const val MODELS_DIR = "sherpa-onnx-models"
        private const val PARAFORMER_DIR = "paraformer-zh-small"
        private const val VAD_FILE = "silero_vad.onnx"

        // 模型文件名
        private const val ASR_MODEL_FILE = "model.int8.onnx"
        private const val TOKENS_FILE = "tokens.txt"

        // 预估模型大小（用于显示）
        const val ESTIMATED_MODEL_SIZE_MB = 85

        // 性能优化：缓冲区大小
        private const val DOWNLOAD_BUFFER_SIZE = 32 * 1024 // 32KB 下载缓冲区

        // 断点续传临时文件后缀
        private const val TEMP_FILE_SUFFIX = ".download"
    }

    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR)

    private val paraformerDir: File
        get() = File(modelsDir, PARAFORMER_DIR)

    private val vadFile: File
        get() = File(modelsDir, VAD_FILE)

    private var downloadJob: Job? = null
    private val isCancelled = AtomicBoolean(false)

    // 状态机：使用 StateFlow 管理模型状态
    private val _state = MutableStateFlow<VoiceModelState>(VoiceModelState.NotDownloaded)

    /**
     * 模型状态的可观察流
     *
     * UI 层可以收集此流来响应状态变化。
     * 由于是单例，所有模块共享同一个状态流。
     */
    val state: StateFlow<VoiceModelState> = _state.asStateFlow()

    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true) // 支持跨域 HTTPS 重定向
            .build()

    init {
        // 初始化时根据文件存在性设置状态
        refreshState()
    }

    /**
     * 刷新状态，根据实际文件存在性更新状态机
     *
     * 在以下情况调用：
     * - 初始化时
     * - 下载完成后
     * - 删除模型后
     * - 需要同步状态时
     */
    fun refreshState() {
        val currentState = _state.value
        // 如果正在下载，不要刷新状态
        if (currentState is VoiceModelState.Downloading) {
            Logger.d(TAG, "Skip refresh: currently downloading")
            return
        }

        _state.value =
            if (checkModelFilesExist()) {
                val path = paraformerDir.absolutePath
                val sizeMB = calculateModelSizeMB()
                Logger.d(TAG, "State refreshed: Downloaded (path=$path, size=${sizeMB}MB)")
                VoiceModelState.Downloaded(path, sizeMB)
            } else {
                Logger.d(TAG, "State refreshed: NotDownloaded")
                VoiceModelState.NotDownloaded
            }
    }

    /**
     * 检查模型文件是否存在（内部方法）
     */
    private fun checkModelFilesExist(): Boolean {
        val asrModel = File(paraformerDir, ASR_MODEL_FILE)
        val tokens = File(paraformerDir, TOKENS_FILE)
        return asrModel.exists() && tokens.exists() && vadFile.exists()
    }

    /**
     * 计算模型大小（MB）
     */
    private fun calculateModelSizeMB(): Long {
        var totalSize = 0L
        paraformerDir.walkTopDown().forEach { file ->
            if (file.isFile) totalSize += file.length()
        }
        totalSize += vadFile.length()
        return totalSize / (1024 * 1024)
    }

    /**
     * 检查模型是否已下载
     *
     * @return true 如果模型已下载且状态为 Downloaded
     */
    fun isModelDownloaded(): Boolean = _state.value is VoiceModelState.Downloaded

    /**
     * 获取模型路径
     *
     * @return 模型路径，如果未下载则返回 null
     */
    fun getModelPath(): String? = (_state.value as? VoiceModelState.Downloaded)?.path

    /**
     * 获取 VAD 模型路径
     *
     * @return VAD 模型路径，如果模型未下载或文件不存在则返回 null
     */
    fun getVadModelPath(): String? = if (_state.value is VoiceModelState.Downloaded && vadFile.exists()) {
        vadFile.absolutePath
    } else {
        null
    }

    /**
     * 获取已下载模型的大小（MB）
     *
     * @return 模型大小，如果未下载则返回 0
     */
    fun getDownloadedModelSizeMB(): Long = (_state.value as? VoiceModelState.Downloaded)?.sizeMB ?: 0L

    /**
     * 下载模型
     *
     * 状态转换：NotDownloaded/Error -> Downloading -> Downloaded/Error
     *
     * 性能优化：
     * - 支持断点续传
     * - 使用优化的缓冲区大小
     * - 记录下载性能
     *
     * @param listener 下载进度监听器
     */
    suspend fun downloadModel(listener: VoiceModelDownloadListener) {
        // 检查当前状态是否允许下载
        val currentState = _state.value
        if (currentState is VoiceModelState.Downloading) {
            Logger.w(TAG, "Download already in progress, ignoring request")
            return
        }

        isCancelled.set(false)
        // 状态转换：-> Downloading
        _state.value = VoiceModelState.Downloading(0, 0, 0)

        withContext(Dispatchers.IO) {
            val downloadStartTime = System.currentTimeMillis()

            try {
                listener.onDownloadStarted()
                Logger.i(TAG, "[Performance] Starting model download from hf-mirror.com (Paraformer zh-small)")

                // 创建模型目录
                modelsDir.mkdirs()
                paraformerDir.mkdirs()

                // 下载 VAD 模型（较小，先下载）
                Logger.d(TAG, "[Performance] Downloading VAD model...")
                val vadStartTime = System.currentTimeMillis()
                downloadFileWithResume(VAD_MODEL_URL, vadFile, listener, 0, 5)
                val vadDownloadTime = System.currentTimeMillis() - vadStartTime
                Logger.d(TAG, "[Performance] VAD model downloaded in ${vadDownloadTime}ms")

                if (isCancelled.get()) {
                    _state.value = VoiceModelState.NotDownloaded
                    listener.onDownloadCancelled()
                    return@withContext
                }

                // 下载 ASR 模型文件
                Logger.d(TAG, "[Performance] Downloading Paraformer ASR model...")
                val asrStartTime = System.currentTimeMillis()
                val asrModelFile = File(paraformerDir, ASR_MODEL_FILE)
                downloadFileWithResume(PARAFORMER_MODEL_URL, asrModelFile, listener, 5, 85)
                val asrDownloadTime = System.currentTimeMillis() - asrStartTime
                val asrSizeMB = asrModelFile.length() / 1024 / 1024
                Logger.d(
                    TAG,
                    "[Performance] ASR model downloaded in ${asrDownloadTime}ms (${asrSizeMB}MB)",
                )

                if (isCancelled.get()) {
                    _state.value = VoiceModelState.NotDownloaded
                    listener.onDownloadCancelled()
                    return@withContext
                }

                // 下载 tokens 文件
                Logger.d(TAG, "[Performance] Downloading tokens file...")
                val tokensFile = File(paraformerDir, TOKENS_FILE)
                downloadFileWithResume(PARAFORMER_TOKENS_URL, tokensFile, listener, 85, 95)
                Logger.d(TAG, "[Performance] Tokens file downloaded")

                // 验证模型文件并更新状态
                if (!checkModelFilesExist()) {
                    _state.value = VoiceModelState.Error("Model files not found after download")
                    throw IOException("Model files not found after download")
                }

                val totalTime = System.currentTimeMillis() - downloadStartTime
                Logger.i(TAG, "[Performance] Paraformer model download completed in ${totalTime}ms")

                // 状态转换：Downloading -> Downloaded
                val sizeMB = calculateModelSizeMB()
                _state.value = VoiceModelState.Downloaded(paraformerDir.absolutePath, sizeMB)

                listener.onDownloadProgress(100, 0, 100)
                listener.onDownloadCompleted(paraformerDir.absolutePath)
            } catch (e: CancellationException) {
                Logger.d(TAG, "Download cancelled")
                _state.value = VoiceModelState.NotDownloaded
                listener.onDownloadCancelled()
            } catch (e: Exception) {
                Logger.e(TAG, "Download failed", e)
                // 状态转换：Downloading -> Error
                _state.value = VoiceModelState.Error(e.message ?: "Unknown error")
                listener.onDownloadFailed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 取消下载
     *
     * 状态转换：Downloading -> NotDownloaded
     */
    fun cancelDownload() {
        Logger.d(TAG, "Cancelling download")
        isCancelled.set(true)
        downloadJob?.cancel()
        if (_state.value is VoiceModelState.Downloading) {
            _state.value = VoiceModelState.NotDownloaded
        }
    }

    /**
     * 删除模型
     *
     * 状态转换：Downloaded -> NotDownloaded
     *
     * @return true 如果删除成功
     */
    fun deleteModel(): Boolean = try {
        Logger.i(TAG, "Deleting model")
        val result = modelsDir.deleteRecursively()
        if (result) {
            // 状态转换：-> NotDownloaded
            _state.value = VoiceModelState.NotDownloaded
        }
        result
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to delete model", e)
        false
    }

    /**
     * 性能优化：支持断点续传的文件下载
     */
    private fun downloadFileWithResume(
        url: String,
        targetFile: File,
        listener: VoiceModelDownloadListener,
        progressStart: Int,
        progressEnd: Int,
    ) {
        val tempFile = File(targetFile.absolutePath + TEMP_FILE_SUFFIX)
        var downloadedBytes = 0L

        // 检查是否有未完成的下载
        if (tempFile.exists()) {
            downloadedBytes = tempFile.length()
            Logger.d(TAG, "[Performance] Resuming download from $downloadedBytes bytes")
        }

        // 构建请求，支持断点续传
        val requestBuilder = Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$downloadedBytes-")
        }

        val request = requestBuilder.build()
        val response = httpClient.newCall(request).execute()

        // 检查响应状态
        val isPartialContent = response.code == 206
        val isSuccess = response.isSuccessful || isPartialContent

        if (!isSuccess) {
            // 如果服务器不支持断点续传，重新开始下载
            if (response.code == 416) {
                Logger.d(TAG, "[Performance] Range not satisfiable, restarting download")
                tempFile.delete()
                downloadedBytes = 0
                downloadFile(url, targetFile, listener, progressStart, progressEnd)
                return
            }
            throw IOException("Download failed: ${response.code}")
        }

        val body = response.body ?: throw IOException("Empty response body")

        // 计算总大小
        val contentLength = body.contentLength()
        val totalBytes =
            if (isPartialContent) {
                downloadedBytes + contentLength
            } else {
                contentLength
            }

        targetFile.parentFile?.mkdirs()

        // 性能优化：使用更大的缓冲区
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)

        body.byteStream().use { input ->
            // 使用追加模式写入临时文件
            FileOutputStream(tempFile, isPartialContent).use { output ->
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get()) {
                        throw CancellationException("Download cancelled")
                    }

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val progress =
                        calculateProgress(
                            downloadedBytes,
                            totalBytes,
                            progressStart,
                            progressEnd,
                        )
                    // 更新状态机中的下载进度
                    _state.value = VoiceModelState.Downloading(progress, downloadedBytes, totalBytes)
                    listener.onDownloadProgress(progress, downloadedBytes, totalBytes)
                }
            }
        }

        // 下载完成，重命名临时文件
        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)

        Logger.d(TAG, "[Performance] Download completed: ${targetFile.length()} bytes")
    }

    /**
     * 下载文件（不支持断点续传的备用方法）
     */
    private fun downloadFile(
        url: String,
        targetFile: File,
        listener: VoiceModelDownloadListener,
        progressStart: Int,
        progressEnd: Int,
    ) {
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Download failed: ${response.code}")
        }

        val body = response.body ?: throw IOException("Empty response body")
        val totalBytes = body.contentLength()
        var downloadedBytes = 0L

        targetFile.parentFile?.mkdirs()

        // 性能优化：使用更大的缓冲区
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)

        body.byteStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled.get()) {
                        throw CancellationException("Download cancelled")
                    }

                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val progress =
                        calculateProgress(
                            downloadedBytes,
                            totalBytes,
                            progressStart,
                            progressEnd,
                        )
                    // 更新状态机中的下载进度
                    _state.value = VoiceModelState.Downloading(progress, downloadedBytes, totalBytes)
                    listener.onDownloadProgress(progress, downloadedBytes, totalBytes)
                }
            }
        }
    }

    /**
     * 计算下载进度
     */
    private fun calculateProgress(downloadedBytes: Long, totalBytes: Long, progressStart: Int, progressEnd: Int): Int =
        if (totalBytes > 0) {
            val ratio = downloadedBytes.toFloat() / totalBytes
            progressStart + (ratio * (progressEnd - progressStart)).toInt()
        } else {
            progressStart
        }

    /**
     * 获取断点续传进度（如果有）
     *
     * @return 已下载的字节数，如果没有未完成的下载则返回 0
     */
    fun getResumeProgress(): Long {
        val asrTempFile = File(paraformerDir, "$ASR_MODEL_FILE$TEMP_FILE_SUFFIX")
        return if (asrTempFile.exists()) asrTempFile.length() else 0
    }

    /**
     * 清理未完成的下载
     */
    fun cleanupIncompleteDownload() {
        val asrTempFile = File(paraformerDir, "$ASR_MODEL_FILE$TEMP_FILE_SUFFIX")
        val tokensTempFile = File(paraformerDir, "$TOKENS_FILE$TEMP_FILE_SUFFIX")
        val vadTempFile = File(vadFile.absolutePath + TEMP_FILE_SUFFIX)

        listOf(asrTempFile, tokensTempFile, vadTempFile).forEach { file ->
            if (file.exists()) {
                file.delete()
                Logger.d(TAG, "Cleaned up incomplete download: ${file.name}")
            }
        }
    }
}
