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
 * `speech.speaker.enroll(name, files, options)` (contract version 2, roadmap D125, D133, D134): [name] already trimmed and
 * checked, [replace] drops the speaker's earlier samples, [model] picks the embedding model (the default speaker model
 * when null).
 * zh-CN: `speech.speaker.enroll(name, files, options)` (契约版本 2, 路线图 D125, D133, D134): [name] 已去空白并校验, [replace] 丢弃该说话人
 * 之前的样本, [model] 选择嵌入模型 (为 null 时用默认说话人模型).
 */
data class SpeechEnrollRequest(val name: String, val replace: Boolean = false, val model: String? = null)

/** `speech.speaker.verify(name, file, options)`; a null [threshold] takes the plugin's value. zh-CN: [threshold] 为 null 时取插件的默认值. */
data class SpeechVerifyRequest(val name: String, val threshold: Double? = null)

/** `speech.speaker.identify(file, options)`. zh-CN: `speech.speaker.identify(file, options)`. */
data class SpeechIdentifyRequest(val threshold: Double? = null, val topK: Int = SpeechLimits.DEFAULT_IDENTIFY_CANDIDATES)

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
 * The contract version and features the host agreed with one plugin: [contractVersion] is [SpeechContract.negotiate] of
 * the plugin's `speechContractVersion` and `speechMaxContractVersion` (roadmap D136), [features] its `speechFeatures`
 * array. A feature of contract version 2 counts only when the agreed version is 2, so the host never calls a method the
 * plugin's contract does not have.
 * zh-CN: 宿主与一个插件商定的契约版本与功能: [contractVersion] 为插件 `speechContractVersion` 与 `speechMaxContractVersion` 的
 * [SpeechContract.negotiate] 结果 (路线图 D136), [features] 为其 `speechFeatures` 数组. 契约版本 2 的功能只在商定版本为 2 时才算数,
 * 因此宿主从不调用插件契约中没有的方法.
 */
data class SpeechPeer(val contractVersion: Int, val features: Set<String>) {

    fun supports(feature: String): Boolean = feature in features &&
        (contractVersion >= SpeechContract.CALL_VERSION_2 || feature in SpeechContract.FEATURES_V1)

    companion object {
        val LEGACY = SpeechPeer(SpeechContract.MIN_CONTRACT_VERSION, emptySet())
    }
}
