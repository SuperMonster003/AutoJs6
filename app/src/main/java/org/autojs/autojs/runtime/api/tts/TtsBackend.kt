package org.autojs.autojs.runtime.api.tts

import java.io.File
import java.util.Locale

/**
 * Platform-neutral boundary around a text-to-speech implementation.
 *
 * Android framework types intentionally do not appear in this contract. In particular, callers
 * identify locales with BCP 47 tags and voices with stable engine-provided names.
 */
internal fun interface TtsBackendFactory {

    fun create(
        requestedEngine: String?,
        listener: TtsProgressListener,
        onInit: (statusCode: Int) -> Unit,
    ): TtsBackend
}

internal interface TtsBackend {

    fun engineName(): String?

    fun attachProgressListener(listener: TtsProgressListener)

    /**
     * Stores and applies [configuration].
     *
     * A synthesis operation also applies the stored configuration if a caller has not explicitly
     * configured this backend yet. Explicit language and voice selections fully replace prior
     * state; only [TtsSelection.Default] restores the engine's default voice.
     */
    fun configure(configuration: TtsConfiguration): TtsConfigurationResult

    fun speak(
        text: String,
        queueMode: TtsQueueMode,
        utteranceId: String,
        parameters: TtsRequestParameters = TtsRequestParameters(),
    ): Int

    fun playSilence(
        durationMillis: Long,
        queueMode: TtsQueueMode,
        utteranceId: String,
    ): Int

    fun synthesizeToFile(
        text: String,
        file: File,
        utteranceId: String,
        parameters: TtsRequestParameters = TtsRequestParameters(),
    ): Int

    fun stop(): Int

    /**
     * Atomically prevents further callbacks without entering an engine or Binder service.
     *
     * Implementations must keep this method non-blocking. The potentially blocking resource
     * release remains in [shutdown] and is always invoked on the bounded native-call runner.
     */
    fun deactivate()

    fun shutdown()

    fun isSpeaking(): Boolean

    fun engines(): List<TtsEngine>

    fun voices(): List<TtsVoice>

    fun languages(): List<String>

    fun isLanguageAvailable(languageTag: String): TtsLanguageAvailability
}

internal interface TtsProgressListener {

    fun onStart(utteranceId: String)

    fun onDone(utteranceId: String)

    fun onStop(utteranceId: String, interrupted: Boolean)

    fun onError(utteranceId: String, errorCode: Int)

    fun onRangeStart(
        utteranceId: String,
        start: Int,
        end: Int,
        frame: Int,
    )

    fun onBeginSynthesis(
        utteranceId: String,
        format: TtsSynthesisFormat,
    )

    fun onAudioAvailable(utteranceId: String, audio: ByteArray)
}

internal data class TtsEngine(
    val name: String,
    val label: String,
    val isDefault: Boolean,
    val isRequested: Boolean,
)

internal data class TtsVoice(
    val name: String,
    val languageTag: String,
    val quality: Int,
    val latency: Int,
    val networkRequired: Boolean,
    val notInstalled: Boolean,
    val features: Set<String>,
) {

    val isOffline: Boolean
        get() = !networkRequired && !notInstalled
}

internal sealed class TtsSelection {

    data object Default : TtsSelection()

    data class Language(
        val languageTag: String,
    ) : TtsSelection() {

        init {
            require(languageTag == normalizeTtsLanguageTag(languageTag)) {
                "TTS language tag must be normalized"
            }
        }

        companion object {

            fun from(languageTag: String): Language =
                Language(normalizeTtsLanguageTag(languageTag))
        }
    }

    data class Voice(
        val name: String,
    ) : TtsSelection() {

        init {
            require(name.isNotBlank() && name == name.trim()) {
                "TTS voice name must be normalized"
            }
            require(name.length <= TtsOptionsParser.MAX_VOICE_LENGTH) {
                "TTS voice name is too long"
            }
            require('\u0000' !in name) { "TTS voice name must not contain NUL" }
        }

        companion object {

            fun from(name: String): Voice {
                val normalized = name.trim()
                require(normalized.isNotEmpty()) { "TTS voice name must not be empty" }
                require(normalized.length <= TtsOptionsParser.MAX_VOICE_LENGTH) {
                    "TTS voice name is too long"
                }
                return Voice(normalized)
            }
        }
    }

    /**
     * A speaker of one model of an engine that names its voices like the speech plugin (3-Macaw Speech roadmap D19,
     * appendix D Q6): `<model>/<speaker>` in the speaker's first language and `<model>/<speaker>/<languageTag>` in the
     * model's other languages. Only `speech.speak` builds it; `tts` scripts name voices with [Voice]. [speaker] is a
     * number, a name the engine resolves itself, or null for the speaker that suits [languageTag]
     * ([resolveTtsModelVoice]).
     */
    data class ModelVoice(
        val model: String,
        val speaker: String? = null,
        val languageTag: String? = null,
    ) : TtsSelection() {

        init {
            require(model.isNotBlank() && model == model.trim()) { "TTS model name must be normalized" }
            require(speaker == null || (speaker.isNotBlank() && speaker == speaker.trim() && '/' !in speaker)) {
                "TTS model speaker must be a normalized number or name without '/'"
            }
            require("$model/${speaker.orEmpty()}".length <= TtsOptionsParser.MAX_VOICE_LENGTH) {
                "TTS voice name is too long"
            }
            require('\u0000' !in model && '\u0000' !in speaker.orEmpty()) { "TTS voice name must not contain NUL" }
            require(languageTag == null || languageTag == normalizeTtsLanguageTag(languageTag)) {
                "TTS language tag must be normalized"
            }
        }
    }

    /**
     * [Language] when the engine has the language, otherwise [Default] instead of a failure. Only `speech.speak`
     * builds it, with the language of the text when the script names neither a model nor a language.
     */
    data class PreferredLanguage(
        val languageTag: String,
    ) : TtsSelection() {

        init {
            require(languageTag == normalizeTtsLanguageTag(languageTag)) {
                "TTS language tag must be normalized"
            }
        }
    }
}

/**
 * The voice [resolveTtsModelVoice] chose: one the engine lists, or a speaker name the engine is asked to load
 * ([template] lends it a locale and quality).
 */
internal sealed class TtsModelVoiceChoice {

    data class Listed(val voice: TtsVoice) : TtsModelVoiceChoice()

    data class Named(val name: String, val template: TtsVoice) : TtsModelVoiceChoice()
}

/**
 * The voice of [selection] among the engine's [voices]. Only the voices in a speaker's first language take part
 * (`<model>/<number>`). A numbered speaker must be listed. A named speaker is not listed (the engine lists numbers only)
 * and becomes [TtsModelVoiceChoice.Named], which the engine accepts or refuses when the voice is set. Without a speaker
 * the lowest numbered voice whose language is the one of [TtsSelection.ModelVoice.languageTag] wins, otherwise the
 * lowest numbered voice: Kokoro multi-lang v1.1 lists speakers 0 to 2 in English and 3 to 102 in Chinese, so Chinese
 * text gets speaker 3 (the plugin's default speaker) and English text speaker 0. A model without listed voices (not
 * installed) and a number the model does not have fail with [TtsBackendException].
 */
internal fun resolveTtsModelVoice(
    voices: Collection<TtsVoice>,
    selection: TtsSelection.ModelVoice,
): TtsModelVoiceChoice {
    val prefix = "${selection.model}/"
    val speakers = voices.mapNotNull { voice ->
        val rest = voice.name.takeIf { it.startsWith(prefix) }?.substring(prefix.length) ?: return@mapNotNull null
        speakerNumber(rest)?.let { it to voice }
    }.sortedBy { it.first }
    if (speakers.isEmpty()) {
        throw TtsBackendException(
            operation = "selectModelVoice",
            statusCode = TtsBackendStatus.ERROR,
            message = "The TTS engine lists no voice of model ${selection.model}; is the model installed?",
        )
    }
    val speaker = selection.speaker ?: run {
        val language = selection.languageTag?.let { Locale.forLanguageTag(it).language }
        val chosen = speakers.firstOrNull { (_, voice) -> Locale.forLanguageTag(voice.languageTag).language == language }
            ?: speakers.first()
        return TtsModelVoiceChoice.Listed(chosen.second)
    }
    val number = speakerNumber(speaker) ?: return TtsModelVoiceChoice.Named(prefix + speaker, speakers.first().second)
    return speakers.firstOrNull { it.first == number }?.let { TtsModelVoiceChoice.Listed(it.second) }
        ?: throw TtsBackendException(
            operation = "selectModelVoice",
            statusCode = TtsBackendStatus.ERROR,
            message = "TTS voice is not available: $prefix$speaker " +
                "(${selection.model} has speakers ${speakers.first().first} to ${speakers.last().first})",
        )
}

private fun speakerNumber(text: String): Int? =
    text.takeIf { it.isNotEmpty() && it.length <= 9 && it.all { c -> c in '0'..'9' } }?.toInt()

internal enum class TtsAudioUsage(val value: String) {
    MEDIA("media"),
    ACCESSIBILITY("accessibility"),
    ASSISTANT("assistant"),
    NAVIGATION("navigation"),
    ALARM("alarm"),
    NOTIFICATION("notification"),
    VOICE_COMMUNICATION("voiceCommunication"),
    ;

    companion object {

        fun from(value: String): TtsAudioUsage =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unsupported TTS audio usage: $value")
    }
}

internal data class TtsConfiguration(
    val selection: TtsSelection = TtsSelection.Default,
    val speechRate: Float = 1.0F,
    val pitch: Float = 1.0F,
    val offlineOnly: Boolean = false,
    val audioUsage: TtsAudioUsage = TtsAudioUsage.MEDIA,
    val applySpeechProperties: Boolean = true,
) {

    init {
        require(speechRate.isFinite()) { "TTS speech rate must be finite" }
        require(speechRate in TtsOptionsParser.MIN_RATE..TtsOptionsParser.MAX_RATE) {
            "TTS speech rate must be between ${TtsOptionsParser.MIN_RATE} and " +
                TtsOptionsParser.MAX_RATE
        }
        require(pitch.isFinite()) { "TTS pitch must be finite" }
        require(pitch in TtsOptionsParser.MIN_PITCH..TtsOptionsParser.MAX_PITCH) {
            "TTS pitch must be between ${TtsOptionsParser.MIN_PITCH} and " +
                TtsOptionsParser.MAX_PITCH
        }
    }

    companion object {

        fun from(
            options: TtsOptions,
            applySpeechProperties: Boolean = true,
        ): TtsConfiguration {
            require(options.locale == null || options.voice == null) {
                "TTS locale and voice are mutually exclusive"
            }
            require(options.speechSelection == null || (options.locale == null && options.voice == null)) {
                "TTS speech selection excludes locale and voice"
            }
            return TtsConfiguration(
                selection = when {
                    options.speechSelection != null -> options.speechSelection
                    options.voice != null -> TtsSelection.Voice.from(options.voice)
                    options.locale != null -> TtsSelection.Language.from(options.locale)
                    else -> TtsSelection.Default
                },
                speechRate = options.rate,
                pitch = options.pitch,
                offlineOnly = options.offlineOnly,
                audioUsage = TtsAudioUsage.from(options.usage),
                applySpeechProperties = applySpeechProperties,
            )
        }
    }
}

internal data class TtsConfigurationResult(
    val configuration: TtsConfiguration,
    val engineName: String?,
    val selectedVoice: TtsVoice?,
    val languageTag: String?,
    val effectiveAudioUsage: TtsAudioUsage,
)

internal sealed class TtsScalarValue {

    data class Text(val value: String) : TtsScalarValue()

    data class BooleanValue(val value: Boolean) : TtsScalarValue()

    data class IntValue(val value: Int) : TtsScalarValue()

    data class LongValue(val value: Long) : TtsScalarValue()

    data class FloatValue(val value: Float) : TtsScalarValue() {

        init {
            require(value.isFinite()) { "TTS engine parameter must be finite" }
        }
    }

    data class DoubleValue(val value: Double) : TtsScalarValue() {

        init {
            require(value.isFinite()) { "TTS engine parameter must be finite" }
        }
    }

    companion object {

        fun from(value: Any): TtsScalarValue = when (value) {
            is String -> Text(value)
            is Boolean -> BooleanValue(value)
            is Byte -> IntValue(value.toInt())
            is Short -> IntValue(value.toInt())
            is Int -> IntValue(value)
            is Long -> LongValue(value)
            is Float -> FloatValue(value)
            is Double -> DoubleValue(value)
            else -> throw IllegalArgumentException(
                "TTS engine parameter must be a string, boolean, or finite number",
            )
        }
    }
}

internal data class TtsRequestParameters(
    val volume: Float = 1.0F,
    val pan: Float = 0.0F,
    val engineParameters: Map<String, TtsScalarValue> = emptyMap(),
) {

    init {
        require(volume.isFinite() && volume in 0.0F..1.0F) {
            "TTS volume must be between 0 and 1"
        }
        require(pan.isFinite() && pan in -1.0F..1.0F) {
            "TTS pan must be between -1 and 1"
        }
        require(engineParameters.size <= TtsOptionsParser.MAX_PARAMETERS) {
            "TTS engine parameters cannot contain more than " +
                "${TtsOptionsParser.MAX_PARAMETERS} entries"
        }
        engineParameters.forEach { (name, value) ->
            require(name.isNotBlank() && name.length <= TtsOptionsParser.MAX_PARAMETER_KEY_LENGTH) {
                "Invalid TTS engine parameter name"
            }
            require('\u0000' !in name) { "TTS engine parameter name must not contain NUL" }
            require(name !in TtsOptionsParser.RESERVED_PARAMETER_NAMES) {
                "TTS engine parameter \"$name\" is reserved by Android"
            }
            if (value is TtsScalarValue.Text) {
                require(value.value.length <= TtsOptionsParser.MAX_PARAMETER_STRING_LENGTH) {
                    "TTS engine parameter \"$name\" is too long"
                }
            }
        }
    }

    companion object {

        fun from(options: TtsOptions): TtsRequestParameters = TtsRequestParameters(
            volume = options.volume,
            pan = options.pan,
            engineParameters = options.parameters.mapValues { (_, value) ->
                TtsScalarValue.from(value)
            },
        )
    }
}

internal fun validateTtsEngineParameterPrefixes(
    names: Iterable<String>,
    engineName: String,
) {
    require(engineName.isNotBlank()) { "The active TTS engine could not be identified" }
    val colonPrefix = "$engineName:"
    val underscorePrefix = "${engineName}_"
    names.forEach { name ->
        require(
            (name.startsWith(colonPrefix) && name.length > colonPrefix.length) ||
                (name.startsWith(underscorePrefix) && name.length > underscorePrefix.length),
        ) {
            "TTS engine parameter \"$name\" must be prefixed with " +
                "\"$colonPrefix\" or \"$underscorePrefix\""
        }
    }
}

internal data class TtsSynthesisFormat(
    val sampleRateInHz: Int,
    val audioEncoding: TtsAudioEncoding,
    val channelCount: Int,
) {

    init {
        require(sampleRateInHz >= 0) { "TTS synthesis sample rate must not be negative" }
        require(channelCount >= 0) { "TTS synthesis channel count must not be negative" }
    }
}

internal enum class TtsAudioEncoding {
    PCM_8_BIT,
    PCM_16_BIT,
    PCM_FLOAT,
    UNKNOWN,
}

internal data class TtsLanguageAvailability(
    val languageTag: String,
    val support: TtsLanguageSupport,
    val statusCode: Int,
) {

    val isAvailable: Boolean
        get() = support.isAvailable
}

internal enum class TtsLanguageSupport(
    val isAvailable: Boolean,
) {
    LANGUAGE(true),
    LANGUAGE_AND_COUNTRY(true),
    LANGUAGE_COUNTRY_AND_VARIANT(true),
    MISSING_DATA(false),
    NOT_SUPPORTED(false),
    UNKNOWN(false),
}

internal class TtsBackendException(
    val operation: String,
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

internal object TtsBackendStatus {

    const val SUCCESS = 0
    const val ERROR = -1
    const val STOPPED = -2
}

internal fun normalizeTtsLanguageTag(languageTag: String): String {
    val normalizedInput = languageTag.trim()
    require(normalizedInput.isNotEmpty()) { "TTS language tag must not be empty" }
    require(normalizedInput.length <= MAX_TTS_LANGUAGE_TAG_LENGTH) {
        "TTS language tag is too long"
    }
    val locale = try {
        Locale.Builder().setLanguageTag(normalizedInput).build()
    } catch (error: RuntimeException) {
        throw IllegalArgumentException("Invalid TTS BCP 47 language tag: $languageTag", error)
    }
    require(locale.language.isNotBlank() && locale.language != "und") {
        "Invalid TTS BCP 47 language tag: $languageTag"
    }
    return locale.toLanguageTag()
}

private const val MAX_TTS_LANGUAGE_TAG_LENGTH = 128

/**
 * Matches the fields explicitly requested by a BCP 47 language tag.
 *
 * A missing region or variant intentionally permits the engine to provide a more specific
 * locale. An explicitly requested script never crosses writing systems such as Hans and Hant.
 */
internal fun ttsLocaleMatches(
    candidateLanguageTag: String,
    requestedLanguageTag: String,
    matchCountryAndVariant: Boolean = true,
): Boolean {
    val candidate = Locale.forLanguageTag(candidateLanguageTag)
    val requested = Locale.forLanguageTag(normalizeTtsLanguageTag(requestedLanguageTag))
    val countryAndVariantMatch =
        (requested.country.isBlank() || candidate.country == requested.country) &&
            (requested.variant.isBlank() || candidate.variant == requested.variant)
    return candidate.language == requested.language &&
        (requested.script.isBlank() || candidate.script == requested.script) &&
        (!matchCountryAndVariant || countryAndVariantMatch)
}

/**
 * Chooses an installed, non-network voice for [preferredLanguageTag].
 *
 * A voice for another language is never selected merely because it is offline. Exact locale and
 * preferred voice matches win, then quality, latency, and name provide deterministic tie-breaks.
 */
internal fun selectOfflineTtsVoice(
    voices: Collection<TtsVoice>,
    preferredLanguageTag: String,
    preferredVoiceName: String? = null,
): TtsVoice? {
    val normalizedTag = normalizeTtsLanguageTag(preferredLanguageTag)
    return voices.asSequence()
        .filter(TtsVoice::isOffline)
        .filter { voice ->
            ttsLocaleMatches(
                candidateLanguageTag = voice.languageTag,
                requestedLanguageTag = normalizedTag,
                matchCountryAndVariant = false,
            )
        }
        .sortedWith(
            compareByDescending<TtsVoice> { it.name == preferredVoiceName }
                .thenByDescending { it.languageTag == normalizedTag }
                .thenByDescending(TtsVoice::quality)
                .thenBy(TtsVoice::latency)
                .thenBy(TtsVoice::name),
        )
        .firstOrNull()
}
