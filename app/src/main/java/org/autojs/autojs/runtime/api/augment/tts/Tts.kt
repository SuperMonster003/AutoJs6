package org.autojs.autojs.runtime.api.augment.tts

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.tts.TtsTextChunker
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.runtime.api.modules.tts.TtsUtterance
import org.autojs.autojs.runtime.api.tts.TtsOptions
import org.autojs.autojs.runtime.api.tts.TtsOptionsParser
import org.autojs.autojs.runtime.api.tts.TtsQueueMode
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.autojs.runtime.api.tts.normalizeTtsEngineName
import org.autojs.autojs.runtime.api.tts.normalizeTtsLanguageTag
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.mozilla.javascript.Context
import org.mozilla.javascript.Undefined

class Tts internal constructor(
    private val scriptRuntime: ScriptRuntime,
    private val service: TtsService,
) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentProperties = listOf(
        "maxInputLength" to TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH,
        "maxSynthesisFileSize" to TtsService.MAX_SYNTHESIS_FILE_BYTES,
        "QUEUE_FLUSH" to TtsQueueMode.FLUSH.value,
        "QUEUE_ADD" to TtsQueueMode.ADD.value,
    )

    override val selfAssignmentFunctions = FUNCTION_ASSIGNMENTS

    override fun invoke(vararg args: Any?): Any = speak(scriptRuntime, args)

    @RhinoRuntimeFunctionInterface
    fun speak(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val (text, options) = parseTextAndOptions(args)
        return service.speech(text, options).result
    }

    @RhinoRuntimeFunctionInterface
    fun enqueue(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val (text, options) = parseTextAndOptions(args)
        return service.speech(text, options, TtsQueueMode.ADD).result
    }

    @RhinoRuntimeFunctionInterface
    fun utterance(runtime: ScriptRuntime, args: Array<out Any?>): TtsUtterance {
        checkRuntime(runtime)
        val (text, options) = parseTextAndOptions(args)
        return service.speech(text, options)
    }

    @RhinoRuntimeFunctionInterface
    fun enqueueUtterance(runtime: ScriptRuntime, args: Array<out Any?>): TtsUtterance {
        checkRuntime(runtime)
        val (text, options) = parseTextAndOptions(args)
        return service.speech(text, options, TtsQueueMode.ADD)
    }

    /**
     * Synthesizes to a staged file and atomically replaces the destination by default.
     *
     * `overwrite: false` requests strict atomic no-replace publication. It can fail on shared
     * storage file systems that do not support hard links instead of exposing a partial file.
     * File synthesis is one platform request limited to [TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH];
     * the speech-only `autoSplit` option does not split file output.
     */
    @RhinoRuntimeFunctionInterface
    fun synthesize(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val (text, path, options) = parseSynthesisArguments(args)
        return service.synthesis(text, path, options)
    }

    /**
     * Observable form of [synthesize], with the same staged publication guarantees.
     */
    @RhinoRuntimeFunctionInterface
    fun synthesis(runtime: ScriptRuntime, args: Array<out Any?>): TtsUtterance {
        checkRuntime(runtime)
        val (text, path, options) = parseSynthesisArguments(args)
        return service.synthesis(text, path, options, observable = true) as TtsUtterance
    }

    @RhinoRuntimeFunctionInterface
    fun silence(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val (duration, options) = parseSilenceArguments(args)
        return service.silence(duration, options)
    }

    @RhinoRuntimeFunctionInterface
    fun silenceTask(runtime: ScriptRuntime, args: Array<out Any?>): TtsUtterance {
        checkRuntime(runtime)
        val (duration, options) = parseSilenceArguments(args)
        return service.silence(duration, options, observable = true) as TtsUtterance
    }

    @RhinoRuntimeFunctionInterface
    fun ready(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val engine = parseOptionalEngine(args)
        val adapter = if (engine.present) service.ready(engine.value) else service.ready()
        return adapter.toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun engines(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val engine = parseOptionalEngine(args)
        val adapter = if (engine.present) service.engines(engine.value) else service.engines()
        return adapter.toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun voices(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val filter = parseVoiceFilter(args)
        val adapter = if (filter.engine.present) {
            service.voices(
                engine = filter.engine.value,
                locale = filter.locale,
                offlineOnly = filter.offlineOnly,
                installedOnly = filter.installedOnly,
            )
        } else {
            service.voices(
                locale = filter.locale,
                offlineOnly = filter.offlineOnly,
                installedOnly = filter.installedOnly,
            )
        }
        return adapter.toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun languages(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val engine = parseOptionalEngine(args)
        val adapter = if (engine.present) service.languages(engine.value) else service.languages()
        return adapter.toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun isLanguageAvailable(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val (locale, engine) = parseLanguageQuery(args)
        val adapter = if (engine.present) {
            service.isLanguageAvailable(locale, engine.value)
        } else {
            service.isLanguageAvailable(locale)
        }
        return adapter.toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun languageAvailability(runtime: ScriptRuntime, args: Array<out Any?>): Any {
        checkRuntime(runtime)
        val (locale, engine) = parseLanguageQuery(args)
        val adapter = if (engine.present) {
            service.languageAvailability(locale, engine.value)
        } else {
            service.languageAvailability(locale)
        }
        return adapter.toJsPromise(runtime)
    }

    @RhinoRuntimeFunctionInterface
    fun stop(runtime: ScriptRuntime, args: Array<out Any?>): Int {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::stopAll)
    }

    @RhinoRuntimeFunctionInterface
    fun reset(runtime: ScriptRuntime, args: Array<out Any?>): Int {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::reset)
    }

    @RhinoRuntimeFunctionInterface
    fun isSpeaking(runtime: ScriptRuntime, args: Array<out Any?>): Boolean {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::isSpeaking)
    }

    @RhinoRuntimeFunctionInterface
    fun isBusy(runtime: ScriptRuntime, args: Array<out Any?>): Boolean {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::isBusy)
    }

    @RhinoRuntimeFunctionInterface
    fun status(runtime: ScriptRuntime, args: Array<out Any?>): Map<String, Any?> {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::status)
    }

    @RhinoRuntimeFunctionInterface
    fun defaults(runtime: ScriptRuntime, args: Array<out Any?>): Map<String, Any?> {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::defaults)
    }

    @RhinoRuntimeFunctionInterface
    fun configure(runtime: ScriptRuntime, args: Array<out Any?>): Map<String, Any?> {
        checkRuntime(runtime)
        return ensureArgumentsOnlyOne(args) {
            service.configure(parseObject(it, "configuration"))
        }
    }

    @RhinoRuntimeFunctionInterface
    fun getMaxInputLength(runtime: ScriptRuntime, args: Array<out Any?>): Int {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args) {
            TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH
        }
    }

    @RhinoRuntimeFunctionInterface
    fun openSettings(runtime: ScriptRuntime, args: Array<out Any?>): Boolean {
        checkRuntime(runtime)
        return ensureArgumentsIsEmpty(args, service::openSettings)
    }

    @RhinoRuntimeFunctionInterface
    fun installData(runtime: ScriptRuntime, args: Array<out Any?>): Boolean {
        checkRuntime(runtime)
        val engine = parseOptionalEngine(args)
        return if (engine.present) service.installData(engine.value) else service.installData()
    }

    private fun checkRuntime(runtime: ScriptRuntime) {
        check(runtime === scriptRuntime) { "TTS module belongs to a different script runtime" }
    }

    private fun parseTextAndOptions(
        args: Array<out Any?>,
    ): Pair<String, JsonObject> = ensureArgumentsLengthInRange(args, 1..2) { arguments ->
        val text = parseText(arguments[0])
        val options = arguments.getOrNull(1).toOptions()
        text to options
    }

    private fun parseSynthesisArguments(
        args: Array<out Any?>,
    ): Triple<String, String, JsonObject> =
        ensureArgumentsLengthInRange(args, 2..3) { arguments ->
            val text = parseText(arguments[0])
            require(!arguments[1].isJsNullish()) { "TTS output path must not be nullish" }
            val path = scriptRuntime.files.nonNullPath(Context.toString(arguments[1]))
            Triple(text, path, arguments.getOrNull(2).toOptions())
        }

    private fun parseSilenceArguments(
        args: Array<out Any?>,
    ): Pair<Long, JsonObject> =
        ensureArgumentsLengthInRange(args, 1..2) { arguments ->
            require(!arguments[0].isJsNullish()) {
                "TTS silence duration must not be nullish"
            }
            val raw = Context.toNumber(arguments[0])
            require(raw.isFinite() && raw >= 0.0 && raw == raw.toLong().toDouble()) {
                "TTS silence duration must be a non-negative integer"
            }
            raw.toLong() to arguments.getOrNull(1).toOptions()
        }

    private fun parseText(value: Any?): String {
        require(!value.isJsNullish()) { "TTS text must not be nullish" }
        return Context.toString(value).also {
            require(it.isNotBlank()) { "TTS text must not be blank" }
            require(it.length <= TtsTextChunker.MAX_TEXT_LENGTH) {
                "TTS text must not exceed ${TtsTextChunker.MAX_TEXT_LENGTH} UTF-16 units"
            }
        }
    }

    private fun parseOptionalEngine(args: Array<out Any?>): OptionalString =
        ensureArgumentsAtMost(args, 1) { arguments ->
            if (arguments.isEmpty()) return@ensureArgumentsAtMost OptionalString(false, null)
            val value = arguments[0]
            when {
                value is Undefined -> OptionalString(false, null)
                value.isJsNullish() -> OptionalString(true, null)
                value is CharSequence -> OptionalString(
                    true,
                    normalizeTtsEngineName(value.toString()),
                )
                else -> {
                    val options = parseObject(value, "options")
                    options.requireOnlyKeys(setOf("engine"), "engine query")
                    options.optionalString("engine").validatedEngine()
                }
            }
        }

    private fun parseVoiceFilter(args: Array<out Any?>): VoiceFilter =
        ensureArgumentsAtMost(args, 1) { arguments ->
            if (arguments.isEmpty() || arguments[0] is Undefined) {
                return@ensureArgumentsAtMost VoiceFilter()
            }
            val value = arguments[0]
            if (value.isJsNullish()) {
                return@ensureArgumentsAtMost VoiceFilter(
                    engine = OptionalString(true, null),
                )
            }
            if (value is CharSequence) {
                return@ensureArgumentsAtMost VoiceFilter(
                    engine = OptionalString(
                        true,
                        normalizeTtsEngineName(value.toString()),
                    ),
                )
            }
            val options = parseObject(value, "voice filter")
            options.requireOnlyKeys(
                setOf(
                    "engine",
                    "locale",
                    "language",
                    "lang",
                    "offlineOnly",
                    "offline",
                    "installedOnly",
                    "installed",
                ),
                "voice filter",
            )
            val speechOptions = options.deepCopy().apply {
                remove("installedOnly")
                remove("installed")
            }
            val parsed = TtsOptionsParser.merge(TtsOptions(), speechOptions).speech
            VoiceFilter(
                engine = options.optionalString("engine").validatedEngine(),
                locale = parsed.locale,
                offlineOnly = options.optionalBoolean("offlineOnly", "offline") ?: false,
                installedOnly = options.optionalBoolean("installedOnly", "installed") ?: false,
            )
        }

    private fun parseLanguageQuery(
        args: Array<out Any?>,
    ): Pair<String, OptionalString> =
        ensureArgumentsLengthInRange(args, 1..2) { arguments ->
            require(!arguments[0].isJsNullish()) { "TTS locale must not be nullish" }
            val locale = normalizeTtsLanguageTag(
                Context.toString(arguments[0]).replace('_', '-'),
            )
            val engine = if (arguments.size < 2 || arguments[1] is Undefined) {
                OptionalString(false, null)
            } else {
                val value = arguments[1]
                when {
                    value.isJsNullish() -> OptionalString(true, null)
                    value is CharSequence -> OptionalString(
                        true,
                        normalizeTtsEngineName(value.toString()),
                    )
                    else -> parseObject(value, "options").also {
                        it.requireOnlyKeys(setOf("engine"), "language query")
                    }.optionalString("engine").validatedEngine()
                }
            }
            locale to engine
        }

    private fun Any?.toOptions(): JsonObject {
        if (this == null || this is Undefined || isJsNullish()) return JsonObject()
        return parseObject(this, "options")
    }

    private fun parseObject(value: Any?, label: String): JsonObject {
        val serialized = js_json_stringify(value)
        if (serialized is Undefined) {
            throw WrappedIllegalArgumentException("TTS $label cannot be undefined")
        }
        val parsed = try {
            JsonParser.parseString(Context.toString(serialized))
        } catch (error: RuntimeException) {
            throw WrappedIllegalArgumentException(
                "TTS $label must be JSON-compatible: ${error.message.orEmpty()}",
            )
        }
        if (!parsed.isJsonObject) {
            throw WrappedIllegalArgumentException("TTS $label must be a JavaScript Object")
        }
        return parsed.asJsonObject
    }

    private fun JsonObject.optionalString(name: String): OptionalString {
        if (!has(name)) return OptionalString(false, null)
        val element = get(name)
        if (element == null || element.isJsonNull) return OptionalString(true, null)
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) {
            "TTS option $name must be a string or null"
        }
        return OptionalString(true, element.asString.trim().takeIf(String::isNotEmpty))
    }

    private fun OptionalString.validatedEngine(): OptionalString =
        if (!present) this else copy(value = normalizeTtsEngineName(value))

    private fun JsonObject.optionalBoolean(vararg names: String): Boolean? {
        val element = names.firstNotNullOfOrNull { name ->
            get(name)?.takeUnless { it.isJsonNull }
        } ?: return null
        require(element.isJsonPrimitive && element.asJsonPrimitive.isBoolean) {
            "TTS option ${names.first()} must be a boolean"
        }
        return element.asBoolean
    }

    private fun JsonObject.requireOnlyKeys(
        allowed: Set<String>,
        label: String,
    ) {
        val unknown = keySet() - allowed
        require(unknown.isEmpty()) {
            "Unknown TTS $label option: ${unknown.sorted().joinToString()}"
        }
    }

    internal companion object {

        val FUNCTION_ASSIGNMENTS = listOf(
            Tts::speak.name,
            Tts::enqueue.name,
            Tts::utterance.name to listOf(Tts::utterance.name, "speakTask"),
            Tts::enqueueUtterance.name to listOf(
                Tts::enqueueUtterance.name,
                "enqueueTask",
            ),
            Tts::synthesize.name to listOf(
                Tts::synthesize.name,
                "synthesizeToFile",
            ),
            Tts::synthesis.name to listOf(
                Tts::synthesis.name,
                "synthesizeTask",
                "synthesisTask",
            ),
            Tts::silence.name,
            Tts::silenceTask.name,
            Tts::ready.name,
            Tts::engines.name,
            Tts::voices.name,
            Tts::languages.name,
            Tts::isLanguageAvailable.name,
            Tts::languageAvailability.name,
            Tts::stop.name to listOf(Tts::stop.name, "stopAll"),
            Tts::reset.name,
            Tts::isSpeaking.name,
            Tts::isBusy.name,
            Tts::status.name,
            Tts::defaults.name to listOf(Tts::defaults.name, "config"),
            Tts::configure.name,
            Tts::getMaxInputLength.name,
            Tts::openSettings.name,
            Tts::installData.name,
        )
    }

    private data class OptionalString(
        val present: Boolean,
        val value: String?,
    )

    private data class VoiceFilter(
        val engine: OptionalString = OptionalString(false, null),
        val locale: String? = null,
        val offlineOnly: Boolean = false,
        val installedOnly: Boolean = false,
    )
}
