package org.autojs.autojs.runtime.api.modules.tts

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.tts.TtsTextChunker
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.toJsPromise
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.runtime.api.tts.TtsOptions
import org.autojs.autojs.runtime.api.tts.TtsOptionsParser
import org.autojs.autojs.runtime.api.tts.TtsQueueMode
import org.autojs.autojs.runtime.api.tts.TtsService
import org.autojs.autojs.runtime.api.tts.normalizeTtsEngineName
import org.autojs.autojs.runtime.api.tts.normalizeTtsLanguageTag
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `tts` module on the new script module layer (roadmap P3.5): `tts(text, options?)` speaks, the four data properties
 * and the functions with their aliases come in the order of the current `augment/tts/Tts`, and every call goes to the
 * runtime's TtsService as before (utterances are the current TtsUtterance objects, appendix C Q24; promises through
 * the current `toJsPromise`).
 *
 * The arguments are parsed here with the error model of the new layer (D10; the current module threw
 * IllegalArgumentException or WrappedIllegalArgumentException for all of them): nullish text, path, duration or locale,
 * options that cannot be serialized to a JSON object, a query or voice filter with keys it does not know, and option
 * values of the wrong type are TypeErrors; blank or too long text and a duration that is negative, fractional or not
 * finite are RangeErrors; surplus arguments are TypeErrors (appendix C Q7). The values the service itself rejects (an
 * unknown engine or voice, a silence longer than its limit, option contents) keep their Java types (T5).
 *
 * zh-CN: 新脚本模块层上的 tts 模块 (P3.5): tts(text, options?) 朗读, 四个数据属性与带别名的各函数按现有 augment/tts/Tts 的顺序, 每个调用照旧交给运行时的
 * TtsService (朗读任务为现有 TtsUtterance 对象, 附录 C Q24; Promise 经现有 toJsPromise). 参数在此按新层的错误模型解析 (D10; 现有模块对它们一律抛
 * IllegalArgumentException 或 WrappedIllegalArgumentException): nullish 的文本, 路径, 时长或语言, 不能序列化为 JSON 对象的选项, 含未知键的查询或声音筛选, 类型
 * 不对的选项值为 TypeError; 空白或过长的文本, 负数, 小数或非有限的时长为 RangeError; 多余参数为 TypeError (附录 C Q7). 服务自身拒绝的值 (未知引擎或声音,
 * 超过上限的静音, 选项内容) 保留 Java 类型 (T5).
 */
@ScriptModule(name = "tts")
class TtsModule(private val runtime: ScriptRuntime) {

    private val service: TtsService by lazy { TtsServices.of(runtime) }

    // Named apart from getMaxInputLength(), whose JVM name its getter would take.
    @ScriptProperty(name = "maxInputLength")
    val maxInputLengthValue: Int = TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH

    @ScriptProperty
    val maxSynthesisFileSize: Long = TtsService.MAX_SYNTHESIS_FILE_BYTES

    @ScriptProperty(name = "QUEUE_FLUSH")
    val queueFlush: String = TtsQueueMode.FLUSH.value

    @ScriptProperty(name = "QUEUE_ADD")
    val queueAdd: String = TtsQueueMode.ADD.value

    @ScriptInvoke
    fun invoke(cx: Context, scope: VarScope, text: Any?, options: Any? = null): Any = service.speech(text("tts", text), options(cx, scope, "tts", options)).result

    @ScriptFunction
    fun speak(cx: Context, scope: VarScope, text: Any?, options: Any? = null): Any =
        service.speech(text("tts.speak", text), options(cx, scope, "tts.speak", options)).result

    @ScriptFunction
    fun enqueue(cx: Context, scope: VarScope, text: Any?, options: Any? = null): Any =
        service.speech(text("tts.enqueue", text), options(cx, scope, "tts.enqueue", options), TtsQueueMode.ADD).result

    @ScriptFunction(aliases = ["speakTask"])
    fun utterance(cx: Context, scope: VarScope, text: Any?, options: Any? = null): TtsUtterance =
        service.speech(text("tts.utterance", text), options(cx, scope, "tts.utterance", options))

    @ScriptFunction(aliases = ["enqueueTask"])
    fun enqueueUtterance(cx: Context, scope: VarScope, text: Any?, options: Any? = null): TtsUtterance =
        service.speech(text("tts.enqueueUtterance", text), options(cx, scope, "tts.enqueueUtterance", options), TtsQueueMode.ADD)

    /**
     * Synthesizes to a staged file and atomically replaces the destination by default (`overwrite: false` asks for an
     * atomic publication without replacing); one platform request of at most [maxInputLengthValue] units, as before.
     */
    @ScriptFunction(aliases = ["synthesizeToFile"])
    fun synthesize(cx: Context, scope: VarScope, text: Any?, path: Any?, options: Any? = null): Any {
        val (content, file, parsed) = synthesisArguments(cx, scope, "tts.synthesize", text, path, options)
        return service.synthesis(content, file, parsed)
    }

    @ScriptFunction(aliases = ["synthesizeTask", "synthesisTask"])
    fun synthesis(cx: Context, scope: VarScope, text: Any?, path: Any?, options: Any? = null): TtsUtterance {
        val (content, file, parsed) = synthesisArguments(cx, scope, "tts.synthesis", text, path, options)
        return service.synthesis(content, file, parsed, observable = true) as TtsUtterance
    }

    @ScriptFunction
    fun silence(cx: Context, scope: VarScope, duration: Any?, options: Any? = null): Any =
        service.silence(duration("tts.silence", duration), options(cx, scope, "tts.silence", options))

    @ScriptFunction
    fun silenceTask(cx: Context, scope: VarScope, duration: Any?, options: Any? = null): TtsUtterance =
        service.silence(duration("tts.silenceTask", duration), options(cx, scope, "tts.silenceTask", options), observable = true) as TtsUtterance

    @ScriptFunction
    fun ready(cx: Context, scope: VarScope, engine: Any? = ABSENT): Any {
        val query = engine(cx, scope, "tts.ready", engine, "engine")
        return (if (query.present) service.ready(query.value) else service.ready()).toJsPromise(runtime)
    }

    @ScriptFunction
    fun engines(cx: Context, scope: VarScope, engine: Any? = ABSENT): Any {
        val query = engine(cx, scope, "tts.engines", engine, "engine")
        return (if (query.present) service.engines(query.value) else service.engines()).toJsPromise(runtime)
    }

    @ScriptFunction
    fun voices(cx: Context, scope: VarScope, filter: Any? = ABSENT): Any {
        val parsed = voiceFilter(cx, scope, filter)
        val adapter = if (parsed.engine.present) {
            service.voices(engine = parsed.engine.value, locale = parsed.locale, offlineOnly = parsed.offlineOnly, installedOnly = parsed.installedOnly)
        } else {
            service.voices(locale = parsed.locale, offlineOnly = parsed.offlineOnly, installedOnly = parsed.installedOnly)
        }
        return adapter.toJsPromise(runtime)
    }

    @ScriptFunction
    fun languages(cx: Context, scope: VarScope, engine: Any? = ABSENT): Any {
        val query = engine(cx, scope, "tts.languages", engine, "engine")
        return (if (query.present) service.languages(query.value) else service.languages()).toJsPromise(runtime)
    }

    @ScriptFunction
    fun isLanguageAvailable(cx: Context, scope: VarScope, locale: Any?, engine: Any? = ABSENT): Any {
        val tag = locale("tts.isLanguageAvailable", locale)
        val query = engine(cx, scope, "tts.isLanguageAvailable", engine, "options")
        return (if (query.present) service.isLanguageAvailable(tag, query.value) else service.isLanguageAvailable(tag)).toJsPromise(runtime)
    }

    @ScriptFunction
    fun languageAvailability(cx: Context, scope: VarScope, locale: Any?, engine: Any? = ABSENT): Any {
        val tag = locale("tts.languageAvailability", locale)
        val query = engine(cx, scope, "tts.languageAvailability", engine, "options")
        return (if (query.present) service.languageAvailability(tag, query.value) else service.languageAvailability(tag)).toJsPromise(runtime)
    }

    @ScriptFunction(aliases = ["stopAll"])
    fun stop(): Int = service.stopAll()

    @ScriptFunction
    fun reset(): Int = service.reset()

    @ScriptFunction
    fun isSpeaking(): Boolean = service.isSpeaking()

    @ScriptFunction
    fun isBusy(): Boolean = service.isBusy()

    @ScriptFunction
    fun status(): Map<String, Any?> = service.status()

    @ScriptFunction(aliases = ["config"])
    fun defaults(): Map<String, Any?> = service.defaults()

    @ScriptFunction
    fun configure(cx: Context, scope: VarScope, configuration: Any?): Map<String, Any?> =
        service.configure(json(cx, scope, "tts.configure", "configuration", configuration))

    @ScriptFunction
    fun getMaxInputLength(): Int = TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH

    @ScriptFunction
    fun openSettings(): Boolean = service.openSettings()

    @ScriptFunction
    fun installData(cx: Context, scope: VarScope, engine: Any? = ABSENT): Boolean {
        val query = engine(cx, scope, "tts.installData", engine, "engine")
        return if (query.present) service.installData(query.value) else service.installData()
    }

    private fun text(function: String, value: Any?): String {
        if (ScriptArgs.isNullish(value)) throw UtilValues.typeError(function, ScriptMessages.argumentType("text", "string", ScriptArgs.describe(value)))
        val text = ScriptArgs.string(value, function, "text")
        if (text.isBlank() || text.length > TtsTextChunker.MAX_TEXT_LENGTH) {
            throw UtilValues.rangeError(function, ScriptMessages.argumentType("text", "non-blank string of at most ${TtsTextChunker.MAX_TEXT_LENGTH} UTF-16 units", UtilValues.quote(text.take(QUOTED))))
        }
        return text
    }

    /** Text, output path (resolved against the working directory after every argument is checked) and options. */
    private fun synthesisArguments(cx: Context, scope: VarScope, function: String, text: Any?, path: Any?, options: Any?): Triple<String, String, JsonObject> {
        val content = text(function, text)
        if (ScriptArgs.isNullish(path)) throw UtilValues.typeError(function, ScriptMessages.argumentType("path", "string", ScriptArgs.describe(path)))
        val file = ScriptArgs.string(path, function, "path")
        val parsed = options(cx, scope, function, options)
        return Triple(content, runtime.files.nonNullPath(file), parsed)
    }

    private fun duration(function: String, value: Any?): Long {
        val number = ScriptArgs.double(value, function, "duration")
        if (!(number.isFinite() && number >= 0.0 && number == number.toLong().toDouble())) {
            throw UtilValues.rangeError(function, ScriptMessages.argumentType("duration", "non-negative integer", UtilValues.quote(value)))
        }
        return number.toLong()
    }

    private fun locale(function: String, value: Any?): String {
        if (ScriptArgs.isNullish(value)) throw UtilValues.typeError(function, ScriptMessages.argumentType("locale", "string", ScriptArgs.describe(value)))
        return normalizeTtsLanguageTag(ScriptArgs.string(value, function, "locale").replace('_', '-'))
    }

    private fun options(cx: Context, scope: VarScope, function: String, value: Any?): JsonObject =
        if (ScriptArgs.isNullish(value)) JsonObject() else json(cx, scope, function, "options", value)

    /**
     * [value] as a JSON object through `JSON.stringify`, as the current module serialized it. Values that cannot serialize
     * to an object (primitives, functions, arrays) are refused before serializing, with the same TypeError (R2: the
     * failing call does no JSON work).
     */
    private fun json(cx: Context, scope: VarScope, function: String, param: String, value: Any?): JsonObject {
        if (value !is Scriptable || value is Callable || value is NativeArray) {
            throw UtilValues.typeError(function, ScriptMessages.argumentType(param, "Object (JSON)", ScriptArgs.describe(value)))
        }
        val serialized = RhinoUtils.js_json_stringify(value)
        val parsed = if (Undefined.isUndefined(serialized)) null else runCatching { JsonParser.parseString(RhinoScriptRuntime.toString(serialized)) }.getOrNull()
        if (parsed == null || !parsed.isJsonObject) throw UtilValues.typeError(function, ScriptMessages.argumentType(param, "Object (JSON)", ScriptArgs.describe(value)))
        return parsed.asJsonObject
    }

    /** An engine query: omitted / undefined (the default engine), null (the system default), a name, or `{ engine }`. */
    private fun engine(cx: Context, scope: VarScope, function: String, value: Any?, param: String): OptionalString {
        val raw = if (value is Wrapper) value.unwrap() else value
        return when {
            value === ABSENT || Undefined.isUndefined(value) -> OptionalString(false, null)
            value == null -> OptionalString(true, null)
            raw is CharSequence -> OptionalString(true, normalizeTtsEngineName(raw.toString()))
            else -> json(cx, scope, function, param, value).also { it.requireOnlyKeys(function, param, ENGINE_QUERY) }.optionalString(function, "$param.engine", "engine").validated()
        }
    }

    private fun voiceFilter(cx: Context, scope: VarScope, value: Any?): VoiceFilter {
        val raw = if (value is Wrapper) value.unwrap() else value
        return when {
            value === ABSENT || Undefined.isUndefined(value) -> VoiceFilter()
            value == null -> VoiceFilter(engine = OptionalString(true, null))
            raw is CharSequence -> VoiceFilter(engine = OptionalString(true, normalizeTtsEngineName(raw.toString())))
            else -> {
                val options = json(cx, scope, FUNCTION_VOICES, "filter", value)
                options.requireOnlyKeys(FUNCTION_VOICES, "filter", VOICE_FILTER)
                val offlineOnly = options.optionalBoolean(FUNCTION_VOICES, "filter.offlineOnly", "offlineOnly", "offline")
                val installedOnly = options.optionalBoolean(FUNCTION_VOICES, "filter.installedOnly", "installedOnly", "installed")
                val speech = options.deepCopy().apply {
                    remove("installedOnly")
                    remove("installed")
                }
                VoiceFilter(
                    engine = options.optionalString(FUNCTION_VOICES, "filter.engine", "engine").validated(),
                    locale = TtsOptionsParser.merge(TtsOptions(), speech).speech.locale,
                    offlineOnly = offlineOnly ?: false,
                    installedOnly = installedOnly ?: false,
                )
            }
        }
    }

    private fun JsonObject.requireOnlyKeys(function: String, param: String, allowed: List<String>) {
        if (!allowed.containsAll(keySet())) {
            throw UtilValues.typeError(function, ScriptMessages.argumentType(param, allowed.joinToString(", ", "{ ", " }"), (keySet() - allowed.toSet()).sorted().joinToString(", ", "{ ", " }")))
        }
    }

    private fun JsonObject.optionalString(function: String, param: String, name: String): OptionalString {
        if (!has(name)) return OptionalString(false, null)
        val element = get(name)
        if (element == null || element.isJsonNull) return OptionalString(true, null)
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw UtilValues.typeError(function, ScriptMessages.argumentType(param, "string | null", element.toString()))
        }
        return OptionalString(true, element.asString.trim().takeIf(String::isNotEmpty))
    }

    private fun JsonObject.optionalBoolean(function: String, param: String, vararg names: String): Boolean? {
        val element = names.firstNotNullOfOrNull { name -> get(name)?.takeUnless { it.isJsonNull } } ?: return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            throw UtilValues.typeError(function, ScriptMessages.argumentType(param, "boolean", element.toString()))
        }
        return element.asBoolean
    }

    private fun OptionalString.validated(): OptionalString = if (!present) this else copy(value = normalizeTtsEngineName(value))

    private data class OptionalString(val present: Boolean, val value: String?)

    private data class VoiceFilter(
        val engine: OptionalString = OptionalString(false, null),
        val locale: String? = null,
        val offlineOnly: Boolean = false,
        val installedOnly: Boolean = false,
    )

    private companion object {

        /** The default of an omitted engine query or filter, which differs from an explicit undefined only in the count. */
        val ABSENT = Any()

        const val FUNCTION_VOICES = "tts.voices"
        const val QUOTED = 40

        val ENGINE_QUERY = listOf("engine")
        val VOICE_FILTER = listOf("engine", "locale", "language", "lang", "offlineOnly", "offline", "installedOnly", "installed")

    }

}

/**
 * The TtsService of a runtime. `ScriptRuntime` keeps it in a private field that only its old `augment()` call passed on
 * (to `augment/tts/Tts` and `Speech`); until `ScriptRuntime` gets an internal accessor (the file had changes of other
 * sessions in progress when P3.5 migrated tts), the field is read once per module instance.
 *
 * zh-CN: 运行时的 TtsService. ScriptRuntime 把它放在私有字段中, 只由旧 augment() 调用传给 augment/tts/Tts 与 Speech; 在 ScriptRuntime 增加 internal 访问器之前
 * (P3.5 迁移 tts 时该文件有其他会话的在途改动), 每个模块实例读取该字段一次.
 */
internal object TtsServices {

    private val field = ScriptRuntime::class.java.getDeclaredField("tts").apply { isAccessible = true }

    fun of(runtime: ScriptRuntime): TtsService = field.get(runtime) as TtsService

}
