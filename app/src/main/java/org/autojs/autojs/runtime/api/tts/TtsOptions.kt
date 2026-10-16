package org.autojs.autojs.runtime.api.tts

import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal enum class TtsQueueMode(val value: String) {
    FLUSH("flush"),
    ADD("add"),
}

internal data class TtsOptions(
    val engine: String? = null,
    val locale: String? = null,
    val voice: String? = null,
    val rate: Float = 1.0F,
    val pitch: Float = 1.0F,
    val volume: Float = 1.0F,
    val pan: Float = 0.0F,
    val queueMode: TtsQueueMode = TtsQueueMode.FLUSH,
    val autoSplit: Boolean = true,
    val offlineOnly: Boolean = false,
    val audioEvents: Boolean = false,
    val usage: String = TtsOptionsParser.DEFAULT_USAGE,
    val timeoutMillis: Long? = null,
    val parameters: Map<String, Any> = emptyMap(),
    // Set only by speech.speak (TtsService.speech); never parsed from script options nor kept in the defaults.
    val speechSelection: TtsSelection? = null,
) {

    fun toPublicValue(): Map<String, Any?> = linkedMapOf(
        "engine" to engine,
        "locale" to locale,
        "voice" to voice,
        "rate" to rate.toDouble(),
        "pitch" to pitch.toDouble(),
        "volume" to volume.toDouble(),
        "pan" to pan.toDouble(),
        "queue" to queueMode.value,
        "autoSplit" to autoSplit,
        "offlineOnly" to offlineOnly,
        "audioEvents" to audioEvents,
        "usage" to usage,
        "timeout" to (timeoutMillis ?: "auto"),
        "parameters" to parameters,
    )
}

internal data class TtsFileOptions(
    // Android shared storage commonly rejects hard links. Atomic replacement is therefore the
    // practical default; callers can explicitly request strict no-replace publication.
    val overwrite: Boolean = DEFAULT_TTS_FILE_OVERWRITE,
    val createDirectories: Boolean = true,
)

private const val DEFAULT_TTS_FILE_OVERWRITE = true

internal data class TtsParsedOptions(
    val speech: TtsOptions,
    val file: TtsFileOptions,
)

internal object TtsOptionsParser {

    const val DEFAULT_USAGE = "media"
    const val MIN_RATE = 0.1F
    const val MAX_RATE = 4.0F
    const val MIN_PITCH = 0.1F
    const val MAX_PITCH = 4.0F
    const val MIN_TIMEOUT_MILLIS = 1_000L
    const val MAX_TIMEOUT_MILLIS = 24L * 60L * 60L * 1_000L
    const val MAX_ENGINE_LENGTH = 255
    const val MAX_VOICE_LENGTH = 512
    const val MAX_PARAMETERS = 32
    const val MAX_PARAMETER_KEY_LENGTH = 128
    const val MAX_PARAMETER_STRING_LENGTH = 4_096

    private val usages = setOf(
        "media",
        "accessibility",
        "assistant",
        "navigation",
        "alarm",
        "notification",
        "voiceCommunication",
    )
    internal val RESERVED_PARAMETER_NAMES = setOf(
        "rate",
        "voiceName",
        "language",
        "country",
        "variant",
        "engine",
        "pitch",
        "audioAttributes",
        "volume",
        "pan",
        "utteranceId",
        "streamType",
        "sessionId",
        "networkTts",
        "embeddedTts",
        "networkTimeoutMs",
        "networkRetriesCount",
        "notInstalled",
    )

    fun merge(
        base: TtsOptions,
        source: JsonObject,
        forcedQueueMode: TtsQueueMode? = null,
        includeFileOptions: Boolean = false,
    ): TtsParsedOptions {
        val allowedOptions = if (includeFileOptions) {
            speechOptionNames + fileOptionNames
        } else {
            speechOptionNames
        }
        val unknownOptions = source.keySet() - allowedOptions
        require(unknownOptions.isEmpty()) {
            "Unknown TTS option: ${unknownOptions.sorted().joinToString()}"
        }
        val engineField = source.optionalString("engine")
        val localeField = source.optionalString("locale", "language", "lang")
        val voiceField = source.optionalString("voice")

        require(!(localeField.isPresent && voiceField.isPresent &&
            localeField.value != null && voiceField.value != null)
        ) {
            "TTS options locale and voice are mutually exclusive"
        }

        var locale = when {
            localeField.isPresent -> localeField.value?.let(::normalizeLocale)
            voiceField.isPresent && voiceField.value != null -> null
            else -> base.locale
        }
        var voice = when {
            voiceField.isPresent -> voiceField.value?.validatedLength(
                "voice",
                MAX_VOICE_LENGTH,
            )
            localeField.isPresent && localeField.value != null -> null
            else -> base.voice
        }
        if (locale != null && voice != null) {
            // A per-call option can intentionally replace the opposite configured default.
            if (localeField.isPresent) voice = null else locale = null
        }

        val rate = source.optionalFiniteFloat("rate", "speechRate")
            ?.also { require(it in MIN_RATE..MAX_RATE) {
                "TTS rate must be between $MIN_RATE and $MAX_RATE"
            } }
            ?: base.rate
        val pitch = source.optionalFiniteFloat("pitch")
            ?.also { require(it in MIN_PITCH..MAX_PITCH) {
                "TTS pitch must be between $MIN_PITCH and $MAX_PITCH"
            } }
            ?: base.pitch
        val volume = source.optionalFiniteFloat("volume")
            ?.also { require(it in 0.0F..1.0F) {
                "TTS volume must be between 0 and 1"
            } }
            ?: base.volume
        val pan = source.optionalFiniteFloat("pan")
            ?.also { require(it in -1.0F..1.0F) {
                "TTS pan must be between -1 and 1"
            } }
            ?: base.pan

        val queueMode = forcedQueueMode
            ?: source.optionalQueueMode()
            ?: base.queueMode
        val usage = source.optionalString("usage", "audioUsage")
            .takeIf { it.isPresent }
            ?.value
            ?.also { require(it in usages) {
                "Unsupported TTS audio usage: $it"
            } }
            ?: base.usage
        val timeoutMillis = source.optionalTimeout("timeout", "timeoutMillis", "timeoutMs")
            .let { field ->
                when {
                    !field.isPresent -> base.timeoutMillis
                    field.value == null -> null
                    field.value == 0L -> 0L
                    else -> field.value.also {
                        require(it in MIN_TIMEOUT_MILLIS..MAX_TIMEOUT_MILLIS) {
                            "TTS timeout must be 0 or between $MIN_TIMEOUT_MILLIS and " +
                                "$MAX_TIMEOUT_MILLIS milliseconds"
                        }
                    }
                }
            }

        val options = TtsOptions(
            engine = when {
                engineField.isPresent -> normalizeTtsEngineName(engineField.value)
                else -> base.engine
            },
            locale = locale,
            voice = voice,
            rate = rate,
            pitch = pitch,
            volume = volume,
            pan = pan,
            queueMode = queueMode,
            autoSplit = source.optionalBoolean("autoSplit", "split")
                .valueOr(base.autoSplit),
            offlineOnly = source.optionalBoolean("offlineOnly", "offline")
                .valueOr(base.offlineOnly),
            audioEvents = source.optionalBoolean("audioEvents", "emitAudio")
                .valueOr(base.audioEvents),
            usage = usage,
            timeoutMillis = timeoutMillis,
            parameters = source.optionalObject("parameters", "params")
                ?.let(::parseParameters)
                ?: base.parameters,
        )
        return TtsParsedOptions(
            speech = options,
            file = TtsFileOptions(
                overwrite = source.optionalBoolean("overwrite")
                    .valueOr(DEFAULT_TTS_FILE_OVERWRITE),
                createDirectories = source.optionalBoolean("createDirectories", "mkdirs")
                    .valueOr(true),
            ),
        )
    }

    private fun parseQueueMode(value: String): TtsQueueMode = when (value.trim().lowercase()) {
        "flush", "replace" -> TtsQueueMode.FLUSH
        "add", "enqueue", "append" -> TtsQueueMode.ADD
        else -> throw IllegalArgumentException("TTS queue must be \"flush\" or \"add\"")
    }

    private fun JsonObject.optionalQueueMode(): TtsQueueMode? {
        val (present, element) = find("queue")
        if (!present) return null
        require(element != null && !element.isJsonNull && element.isJsonPrimitive) {
            "TTS queue must be \"flush\", \"add\", 0, or 1"
        }
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isString -> parseQueueMode(primitive.asString)
            primitive.isNumber -> when (primitive.asDouble) {
                0.0 -> TtsQueueMode.FLUSH
                1.0 -> TtsQueueMode.ADD
                else -> throw IllegalArgumentException(
                    "TTS queue must be \"flush\", \"add\", 0, or 1",
                )
            }
            else -> throw IllegalArgumentException(
                "TTS queue must be \"flush\", \"add\", 0, or 1",
            )
        }
    }

    private fun normalizeLocale(value: String): String {
        val normalizedInput = value.trim().replace('_', '-')
        return normalizeTtsLanguageTag(normalizedInput)
    }

    private fun parseParameters(source: JsonObject): Map<String, Any> {
        require(source.size() <= MAX_PARAMETERS) {
            "TTS parameters cannot contain more than $MAX_PARAMETERS entries"
        }
        return linkedMapOf<String, Any>().apply {
            source.entrySet().forEach { (key, element) ->
                require(key.isNotBlank() && key.length <= MAX_PARAMETER_KEY_LENGTH) {
                    "Invalid TTS parameter name"
                }
                require('\u0000' !in key) {
                    "TTS parameter name must not contain NUL"
                }
                require(key !in RESERVED_PARAMETER_NAMES) {
                    "TTS parameter \"$key\" must use its dedicated option"
                }
                require(element.isJsonPrimitive) {
                    "TTS parameter \"$key\" must be a string, boolean, or finite number"
                }
                val primitive = element.asJsonPrimitive
                val value: Any = when {
                    primitive.isString -> primitive.asString.also {
                        require(it.length <= MAX_PARAMETER_STRING_LENGTH) {
                            "TTS parameter \"$key\" is too long"
                        }
                    }
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isNumber -> primitive.asDouble.also {
                        require(it.isFinite()) {
                            "TTS parameter \"$key\" must be finite"
                        }
                    }
                    else -> throw IllegalArgumentException(
                        "TTS parameter \"$key\" has an unsupported value",
                    )
                }
                put(key, value)
            }
        }
    }

    private fun String.validatedLength(name: String, maxLength: Int): String {
        val value = trim()
        require(value.isNotEmpty()) { "TTS $name must not be empty" }
        require(value.length <= maxLength) { "TTS $name is too long" }
        require('\u0000' !in value) { "TTS $name must not contain NUL" }
        return value
    }

    private data class OptionalField<T>(
        val isPresent: Boolean,
        val value: T?,
    ) {
        fun valueOr(default: T): T = if (isPresent && value != null) value else default
    }

    private fun JsonObject.find(vararg names: String): Pair<Boolean, JsonElement?> {
        names.forEach { name ->
            if (has(name)) return true to get(name)
        }
        return false to null
    }

    private fun JsonObject.optionalString(vararg names: String): OptionalField<String> {
        val (present, element) = find(*names)
        if (!present || element == null || element.isJsonNull) {
            return OptionalField(present, null)
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "TTS option ${names.first()} must be a string or null"
        }
        return OptionalField(true, element.asString.trim().takeIf(String::isNotEmpty))
    }

    private fun JsonObject.optionalBoolean(vararg names: String): OptionalField<Boolean> {
        val (present, element) = find(*names)
        if (!present) return OptionalField(false, null)
        require(element != null && !element.isJsonNull) {
            "TTS option ${names.first()} must be a boolean"
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            "TTS option ${names.first()} must be a boolean"
        }
        return OptionalField(true, element.asBoolean)
    }

    private fun JsonObject.optionalFiniteFloat(vararg names: String): Float? {
        val (present, element) = find(*names)
        if (!present) return null
        require(element != null && !element.isJsonNull) {
            "TTS option ${names.first()} must be a finite number"
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            "TTS option ${names.first()} must be a finite number"
        }
        return element.asFloat.also {
            require(it.isFinite()) { "TTS option ${names.first()} must be finite" }
        }
    }

    private fun JsonObject.optionalLong(vararg names: String): OptionalField<Long> {
        val (present, element) = find(*names)
        if (!present) return OptionalField(false, null)
        require(element != null && !element.isJsonNull) {
            "TTS option ${names.first()} must be an integer"
        }
        require(element.isJsonPrimitive && element.asJsonPrimitive.isNumber) {
            "TTS option ${names.first()} must be an integer"
        }
        val number = element.asJsonPrimitive.asNumber
        val value = number.toLong()
        require(number.toDouble().isFinite() && number.toDouble() == value.toDouble()) {
            "TTS option ${names.first()} must be an integer"
        }
        return OptionalField(true, value)
    }

    private fun JsonObject.optionalTimeout(vararg names: String): OptionalField<Long> {
        val (present, element) = find(*names)
        if (!present) return OptionalField(false, null)
        if (element == null || element.isJsonNull) return OptionalField(true, null)
        if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            require(element.asString.trim().equals("auto", ignoreCase = true)) {
                "TTS option ${names.first()} must be an integer, null, or \"auto\""
            }
            return OptionalField(true, null)
        }
        return optionalLong(*names)
    }

    private fun JsonObject.optionalObject(vararg names: String): JsonObject? {
        val (present, element) = find(*names)
        if (!present || element == null || element.isJsonNull) return null
        require(element.isJsonObject) {
            "TTS option ${names.first()} must be an object"
        }
        return element.asJsonObject
    }

    private val speechOptionNames = setOf(
        "engine",
        "locale",
        "language",
        "lang",
        "voice",
        "rate",
        "speechRate",
        "pitch",
        "volume",
        "pan",
        "queue",
        "autoSplit",
        "split",
        "offlineOnly",
        "offline",
        "audioEvents",
        "emitAudio",
        "usage",
        "audioUsage",
        "timeout",
        "timeoutMillis",
        "timeoutMs",
        "parameters",
        "params",
    )

    private val fileOptionNames = setOf(
        "overwrite",
        "createDirectories",
        "mkdirs",
    )
}

internal fun normalizeTtsEngineName(value: String?): String? {
    val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    require(normalized.length <= TtsOptionsParser.MAX_ENGINE_LENGTH) {
        "TTS engine name is too long"
    }
    require('\u0000' !in normalized) { "TTS engine name must not contain NUL" }
    return normalized
}
