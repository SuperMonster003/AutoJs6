package org.autojs.plugin.speech.api

/**
 * Keys of the option and request Bundles of [ISpeechPlugin] (roadmap appendix B.2). The names follow the script
 * options of `speech`; a missing key takes the default documented here or in [SpeechLimits]. Unknown keys are ignored
 * so a newer host can add optional keys behind a capability.
 *
 * zh-CN: [ISpeechPlugin] 选项与请求 Bundle 的键 (路线图附录 B.2). 名称与 `speech` 的脚本选项一致; 缺省的键取这里或 [SpeechLimits]
 * 写明的默认值. 未知键被忽略, 便于较新的宿主在能力声明之后追加可选键.
 */
object SpeechOptionKeys {

    // Recognition (`openSession`, `recognizeFile`) and wake sessions.

    /** String: model id ([SpeechModelKeys.ID]); absent means the default model of the kind. */
    const val MODEL = "model"

    /** String: language hint (`zh`, `en`, `ja`, `ko`, `yue`); bilingual models ignore it. */
    const val LANGUAGE = "language"

    /** String: [SpeechContract.SOURCE_MIC] (default) or [SpeechContract.SOURCE_EXTERNAL]. */
    const val SOURCE = "source"

    /** Int: sample rate of external PCM; only [SpeechLimits.EXTERNAL_SAMPLE_RATE] in version 1. */
    const val SAMPLE_RATE = "sampleRate"

    /** Boolean: gate the microphone stream with VAD (default true). */
    const val VAD = "vad"

    /** Long: silence that ends an utterance (default [SpeechLimits.DEFAULT_SILENCE_END_MILLIS]). */
    const val SILENCE_END_MILLIS = "silenceEndMillis"

    /** Long: session ceiling (default [SpeechLimits.DEFAULT_MAX_DURATION_MILLIS], at most [SpeechLimits.MAX_AUDIO_MILLIS]). */
    const val MAX_DURATION_MILLIS = "maxDurationMillis"

    /** Long: end the session when no speech arrived (default [SpeechLimits.DEFAULT_TIMEOUT_MILLIS], 0 disables). */
    const val TIMEOUT_MILLIS = "timeoutMillis"

    /** String array: hotwords for transducer models (at most [SpeechLimits.MAX_HOTWORDS]); other models answer a warning. */
    const val HOTWORDS = "hotwords"

    /** Boolean: keep the punctuation the model emits (default true). */
    const val PUNCTUATION = "punctuation"

    /** String: file name of the audio input as the host knows it (`recognizeFile`, `detectVoice`). */
    const val DISPLAY_NAME = "displayName"

    // Wake sessions (`openWakeSession`).

    /** ArrayList of Bundle: keywords, each keyed by [KEYWORD], [BOOST] and [THRESHOLD] (at most [SpeechLimits.MAX_KEYWORDS]). */
    const val KEYWORDS = "keywords"

    /** String: the keyword as written by the script; the plugin converts it to model tokens (roadmap D18). */
    const val KEYWORD = "keyword"

    /** Float, optional: score boost of one keyword. */
    const val BOOST = "boost"

    /** Float, optional: trigger threshold of one keyword. */
    const val THRESHOLD = "threshold"

    /** Bundle, optional: dictation options (this object's recognition keys) applied after every hit; absent means hits only. */
    const val THEN_LISTEN = "thenListen"

    /** Long: smallest interval between two hits (default [SpeechLimits.DEFAULT_MIN_INTERVAL_MILLIS]). */
    const val MIN_INTERVAL_MILLIS = "minIntervalMillis"

    // Voice activity detection (`detectVoice`).

    /** Float, optional: speech probability threshold of the VAD model. */
    const val VAD_THRESHOLD = "vadThreshold"

    /** Long, optional: shortest speech kept as a segment. */
    const val MIN_SPEECH_MILLIS = "minSpeechMillis"

    /** Long, optional: shortest silence that splits two segments. */
    const val MIN_SILENCE_MILLIS = "minSilenceMillis"

    // Synthesis (`synthesizeToFile`).

    /** String: the text (at most [SpeechLimits.MAX_SYNTH_TEXT_CHARS]). */
    const val TEXT = "text"

    /** String: speaker id or name of the model; absent means the model's first speaker. */
    const val SPEAKER = "speaker"

    /** Double: [SpeechLimits.MIN_SPEED] to [SpeechLimits.MAX_SPEED] (default 1.0). */
    const val SPEED = "speed"

    /** String: [SpeechContract.FORMAT_WAV] (the only value of version 1). */
    const val FORMAT = "format"

    // Model management (`listModels`, `installModel`, `importModel`, `removeModel`, `setDefaultModel`).

    /** String: one of [SpeechContract.KINDS] (filter or target kind). */
    const val KIND = "kind"

    /** Boolean: `listModels` filter on the installed state. */
    const val INSTALLED = "installed"

    /** Boolean: the script accepted the license of a non-commercial model (roadmap D11). */
    const val ACCEPT_LICENSE = "acceptLicense"

    /** String: model type of an imported model whose identity is not in the catalog (`vits`, `zipformer`, ...). */
    const val MODEL_TYPE = "modelType"

    /** String: display name of an imported model. */
    const val NAME = "name"

    /** String array: languages of an imported model. */
    const val LANGUAGES = "languages"

    // Speakers (contract version 2: `enrollSpeaker`, `verifySpeaker`, `identifySpeaker`, `removeSpeaker`). The speaker
    // name is [NAME] (at most [SpeechLimits.MAX_SPEAKER_NAME_CHARS]); [THRESHOLD] is a Float in 0.0 - 1.0 compared with
    // the cosine score; [MODEL] chooses the embedding model of an enrollment.

    /** Boolean: `enrollSpeaker` replaces the speaker's samples instead of adding to them (default false, roadmap D134). */
    const val REPLACE = "replace"

    /** Int: candidates `identifySpeaker` answers (default [SpeechLimits.DEFAULT_IDENTIFY_CANDIDATES]). */
    const val TOP_K = "topK"

    /** String array: file names of the `enrollSpeaker` recordings as the host knows them, in descriptor order. */
    const val DISPLAY_NAMES = "displayNames"
}
