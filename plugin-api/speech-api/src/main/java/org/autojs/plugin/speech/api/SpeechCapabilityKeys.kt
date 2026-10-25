package org.autojs.plugin.speech.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle of the INFO and [ISpeechPlugin] services, and of the live
 * [ISpeechPlugin.getCapabilities] answer (roadmap D28, appendix C.1). Every value is readable without opening a session,
 * so the Plugin Center and `speech.capabilities()` can show compatibility up front.
 *
 * zh-CN: INFO 服务与 [ISpeechPlugin] 服务的 `PluginInfo.capabilities` Bundle 键, 以及 [ISpeechPlugin.getCapabilities] 实时应答的键
 * (路线图 D28, 附录 C.1). 每个值无需打开会话即可读取, 因此插件中心与 `speech.capabilities()` 能提前显示兼容性.
 */
object SpeechCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /**
     * Int: base contract version the plugin answers; plugins keep 1 here, which every host since build 5456 accepts
     * (roadmap D136), and advertise newer versions as [MAX_CONTRACT_VERSION].
     */
    const val CONTRACT_VERSION = "speechContractVersion"

    /**
     * Int, optional: highest contract version the plugin implements (2 since 3-Macaw Speech 1.3.0); absent means
     * [CONTRACT_VERSION]. The host agrees on [SpeechContract.negotiate] and calls the methods of a version only up to it.
     */
    const val MAX_CONTRACT_VERSION = "speechMaxContractVersion"

    /** String array: the features the build implements, a subset of [SpeechContract.FEATURES]. */
    const val FEATURES = "speechFeatures"

    /** Int: version of the model catalog bundled with the plugin, 0 before one is bundled. */
    const val CATALOG_VERSION = "speechCatalogVersion"

    /** Int: external PCM sessions one host Binder token may hold at a time (at most [SpeechLimits.MAX_PCM_SESSIONS_PER_TOKEN]). */
    const val MAX_PCM_SESSIONS = "speechMaxPcmSessions"

    /** String: package name of the system TTS engine the plugin registers (`tts(text, { engine })`), absent before roadmap P4. */
    const val TTS_ENGINE_PACKAGE = "speechTtsEnginePackage"

    /** String: name of the speech runtime (`sherpa-onnx`); `getCapabilities` only. */
    const val ENGINE_NAME = "speechEngineName"

    /** String: version of the speech runtime (`1.13.8`); `getCapabilities` only. */
    const val ENGINE_VERSION = "speechEngineVersion"

    /** Boolean: whether the plugin holds `RECORD_AUDIO` right now; `getCapabilities` only. */
    const val MICROPHONE_GRANTED = "speechMicrophoneGranted"
}
