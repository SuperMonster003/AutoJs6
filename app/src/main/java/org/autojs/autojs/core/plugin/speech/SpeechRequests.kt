package org.autojs.autojs.core.plugin.speech

import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechLimits

/**
 * One keyword of a wake session (roadmap D18): the text as the script wrote it, with an optional score boost and
 * trigger threshold.
 * zh-CN: 唤醒会话的一个关键词 (路线图 D18): 脚本所写的原文, 可选的分数增益与触发阈值.
 */
data class SpeechKeyword(val keyword: String, val boost: Double? = null, val threshold: Double? = null)

/**
 * Options of a dictation session (`speech.listen`, the `thenListen` rounds of `speech.wake`), already validated by the
 * script layer (roadmap appendix B.2).
 * zh-CN: 听写会话的选项 (`speech.listen`, `speech.wake` 的 `thenListen` 轮次), 已由脚本层校验 (路线图附录 B.2).
 */
data class SpeechListenRequest(
    val model: String? = null,
    val language: String? = null,
    val source: String = SpeechContract.SOURCE_MIC,
    val sampleRate: Int = SpeechLimits.EXTERNAL_SAMPLE_RATE,
    val vad: Boolean = true,
    val silenceEndMillis: Long = SpeechLimits.DEFAULT_SILENCE_END_MILLIS,
    val maxDurationMillis: Long = SpeechLimits.DEFAULT_MAX_DURATION_MILLIS,
    val timeoutMillis: Long = SpeechLimits.DEFAULT_TIMEOUT_MILLIS,
    val hotwords: List<String> = emptyList(),
    val punctuation: Boolean = true,
) {
    val isExternal: Boolean
        get() = source == SpeechContract.SOURCE_EXTERNAL
}

/** Options of a wake session (`speech.wake`). zh-CN: 唤醒会话 (`speech.wake`) 的选项. */
data class SpeechWakeRequest(
    val keywords: List<SpeechKeyword>,
    val model: String? = null,
    val minIntervalMillis: Long = SpeechLimits.DEFAULT_MIN_INTERVAL_MILLIS,
    val thenListen: SpeechListenRequest? = null,
)

/** Options of a file or PCM recognition (`speech.recognize`). zh-CN: 文件或 PCM 识别 (`speech.recognize`) 的选项. */
data class SpeechRecognizeRequest(
    val model: String? = null,
    val language: String? = null,
    val hotwords: List<String> = emptyList(),
    val punctuation: Boolean = true,
    val vad: Boolean = true,
)

/** Options of `speech.detectVoice`. zh-CN: `speech.detectVoice` 的选项. */
data class SpeechVadRequest(
    val model: String? = null,
    val threshold: Double? = null,
    val minSpeechMillis: Long? = null,
    val minSilenceMillis: Long? = null,
)

/** Options of `speech.synthesize` (the text travels in the request, the output as a staged descriptor). zh-CN: `speech.synthesize` 的选项 (文本随请求传输, 输出为暂存描述符). */
data class SpeechSynthesisRequest(
    val text: String,
    val model: String? = null,
    val speaker: String? = null,
    val speed: Double? = null,
    val format: String = SpeechContract.FORMAT_WAV,
)

/** Filter of `speech.models.list`. zh-CN: `speech.models.list` 的过滤条件. */
data class SpeechModelQuery(
    val kind: String? = null,
    val language: String? = null,
    val installed: Boolean? = null,
)

/** `speech.models.install(id, options)`. zh-CN: `speech.models.install(id, options)`. */
data class SpeechInstallRequest(val id: String, val acceptLicense: Boolean = false)

/**
 * `speech.models.importFile(path, options)`: [id], [kind], [modelType], [name] and [languages] describe a model that is
 * not in the catalog.
 * zh-CN: `speech.models.importFile(path, options)`: [id], [kind], [modelType], [name] 与 [languages] 描述不在目录中的模型.
 */
data class SpeechImportRequest(
    val id: String? = null,
    val kind: String? = null,
    val modelType: String? = null,
    val name: String? = null,
    val languages: List<String> = emptyList(),
    val acceptLicense: Boolean = false,
)

/**
 * The capabilities the plugin advertises, as `speech.capabilities()` answers them (roadmap appendix B.1).
 * zh-CN: 插件声明的能力, 即 `speech.capabilities()` 的应答 (路线图附录 B.1).
 */
data class SpeechCapabilities(
    val contractVersion: Int,
    val features: List<String>,
    val catalogVersion: Int,
    val engineName: String?,
    val engineVersion: String?,
    val ttsEnginePackage: String?,
    val microphoneGranted: Boolean?,
    val packageName: String,
    val pluginVersionName: String?,
    val pluginVersionCode: Long,
)

/**
 * The contract version and features the host agreed with one plugin: [contractVersion] is the plugin's
 * `speechContractVersion` (version 1 is the only one), [features] its `speechFeatures` array.
 * zh-CN: 宿主与一个插件商定的契约版本与功能: [contractVersion] 为插件的 `speechContractVersion` (目前只有版本 1), [features] 为其
 * `speechFeatures` 数组.
 */
data class SpeechPeer(val contractVersion: Int, val features: Set<String>) {

    fun supports(feature: String): Boolean = feature in features

    companion object {
        val LEGACY = SpeechPeer(SpeechContract.MIN_CONTRACT_VERSION, emptySet())
    }
}
