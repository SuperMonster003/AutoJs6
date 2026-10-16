package org.autojs.plugin.speech.api

/**
 * Keys of one model Bundle ([SpeechResultKeys.MODELS] entries and [SpeechResultKeys.MODEL]), the `SpeechModelInfo` of
 * the script API (roadmap appendix B.3, D24).
 *
 * zh-CN: 单个模型 Bundle ([SpeechResultKeys.MODELS] 的条目与 [SpeechResultKeys.MODEL]) 的键, 即脚本 API 的 `SpeechModelInfo`
 * (路线图附录 B.3, D24).
 */
object SpeechModelKeys {

    /** String: catalog id (`asr.zipformer-streaming.zh-en.2023-02-20.int8`) or the id given to an import. */
    const val ID = "id"

    /** String: one of [SpeechContract.KINDS]. */
    const val KIND = "kind"

    /** String: model family the runtime loads (`zipformer`, `sense-voice`, `vits`, `kokoro`, `matcha`, `piper`, `silero`). */
    const val MODEL_TYPE = "modelType"

    /** String: display name in the host's language. */
    const val NAME = "name"

    /** String array: languages the model handles. */
    const val LANGUAGES = "languages"

    /** Long: download size in bytes (0 for an imported model). */
    const val SIZE_BYTES = "sizeBytes"

    /** Long: bytes on disk once installed; absent when not installed. */
    const val INSTALLED_BYTES = "installedBytes"

    /** Boolean: installed and ready to load. */
    const val INSTALLED = "installed"

    /** Boolean: the default model of its kind. */
    const val IS_DEFAULT = "isDefault"

    /** Boolean: recommended in the catalog. */
    const val RECOMMENDED = "recommended"

    /** Int: catalog version code of the model (`versionCode` of roadmap D24). */
    const val VERSION = "version"

    /** Bundle: [LICENSE_SPDX], [LICENSE_COMMERCIAL_USE] and [LICENSE_NOTICE_URL]. */
    const val LICENSE = "license"

    /** String: SPDX identifier or `LicenseRef-*`. */
    const val LICENSE_SPDX = "spdx"

    /** Boolean: false for models limited to non-commercial use (roadmap D11). */
    const val LICENSE_COMMERCIAL_USE = "commercialUse"

    /** String, optional: where the license text is published. */
    const val LICENSE_NOTICE_URL = "noticeUrl"

    /** Int, optional: speakers of a TTS model. */
    const val SPEAKERS = "speakers"

    /** Int, optional: sample rate of a TTS model's output. */
    const val SAMPLE_RATE = "sampleRate"

    /** String: one of [SpeechContract.MODEL_SOURCES]. */
    const val SOURCE = "source"
}
