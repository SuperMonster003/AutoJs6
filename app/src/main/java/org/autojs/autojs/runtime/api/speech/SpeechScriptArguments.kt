package org.autojs.autojs.runtime.api.speech

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.speech.SpeechErrorMapper
import org.autojs.autojs.core.plugin.speech.SpeechImportRequest
import org.autojs.autojs.core.plugin.speech.SpeechInstallRequest
import org.autojs.autojs.core.plugin.speech.SpeechKeyword
import org.autojs.autojs.core.plugin.speech.SpeechListenRequest
import org.autojs.autojs.core.plugin.speech.SpeechModelQuery
import org.autojs.autojs.core.plugin.speech.SpeechPluginException
import org.autojs.autojs.core.plugin.speech.SpeechRecognizeRequest
import org.autojs.autojs.core.plugin.speech.SpeechSynthesisRequest
import org.autojs.autojs.core.plugin.speech.SpeechVadRequest
import org.autojs.autojs.core.plugin.speech.SpeechWakeRequest
import org.autojs.autojs.runtime.api.tts.TtsSelection
import org.autojs.autojs.runtime.api.tts.normalizeTtsLanguageTag
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import java.util.Locale
import kotlin.math.floor

/**
 * Argument normalization of the `speech` script API (3-Macaw Speech roadmap P1.4, appendix B.2): the script values
 * arrive as Gson trees (the Rhino layer serializes each argument like `JSON.stringify`, so functions such as
 * `onProgress` are gone) and leave as the request shapes of the host client. Unknown option keys are refused by name,
 * every shape or range refusal is `INVALID_OPTIONS`, and the contract ceilings (hotwords, keywords, session length,
 * text length) are `LIMIT_EXCEEDED` before anything reaches the plugin. Paths stay unresolved here; the script layer
 * applies the working-directory rules. Nothing here touches Rhino or Android, so the JVM tests cover it.
 * zh-CN: `speech` 脚本 API 的参数规范化 (3-Macaw Speech 路线图 P1.4, 附录 B.2): 脚本值以 Gson 树到达 (Rhino 层像 `JSON.stringify` 一样序列化
 * 每个参数, 因此 `onProgress` 等函数已消失), 离开时是宿主客户端的请求形态. 未知选项键按名拒绝, 形态或范围错误均为 `INVALID_OPTIONS`, 契约
 * 上限 (热词, 关键词, 会话时长, 文本长度) 在到达插件前即为 `LIMIT_EXCEEDED`. 路径在这里不解析; 工作目录规则由脚本层应用. 这里不触碰 Rhino 与
 * Android, 因此由 JVM 测试覆盖.
 */
internal object SpeechScriptArguments {

    /** `speech.recognize` / `detectVoice` options with the PCM sample rate (used only for PCM sources). zh-CN: 带 PCM 采样率的 `speech.recognize` / `detectVoice` 选项 (采样率仅用于 PCM 来源). */
    class Recognize(val request: SpeechRecognizeRequest, val sampleRate: Int)

    class DetectVoice(val request: SpeechVadRequest, val sampleRate: Int)

    /**
     * `speech.speak(text, options?)`: the speech options and the `tts` options passed through (roadmap D20); the engine
     * is always the plugin's. [languageTag] is the language the voice should speak: `language` of the options with
     * [model], else the language of the text (null when the script gave a language without [model], which then goes to
     * `tts` unchanged, or when the text has no letters).
     * zh-CN: `speech.speak(text, options?)`: 语音选项与透传的 `tts` 选项 (路线图 D20); 引擎固定为本插件. [languageTag] 为语音应使用的语言:
     * 给出 [model] 时取选项中的 `language`, 否则取文本的语言 (未给 [model] 而给了语言时为 null, 该语言原样交给 `tts`; 文本没有字母时也为 null).
     */
    class Speak(val model: String?, val speaker: String?, val speed: Double?, val languageTag: String?, val ttsOptions: JsonObject) {
        /**
         * How the engine's voice is chosen: the voice of roadmap D19 the engine resolves (`<model>/<speaker>`, a speaker
         * name included, or the model's speaker of [languageTag]), else the engine's default voice of [languageTag]
         * when it has the language, else (null) the `tts` rules.
         * zh-CN: 引擎语音的选择方式: 由引擎解析的路线图 D19 语音 (`<model>/<speaker>`, 包括说话人名字, 或该模型适合 [languageTag] 的说话人),
         * 否则在引擎支持 [languageTag] 时取其默认语音, 否则 (null) 按 `tts` 的规则.
         */
        val selection: TtsSelection?
            get() = when {
                model != null -> TtsSelection.ModelVoice(model, speaker, languageTag)
                languageTag != null -> TtsSelection.PreferredLanguage(languageTag)
                else -> null
            }
    }

    const val MODEL = "model"
    const val LANGUAGE = "language"
    const val SOURCE = "source"
    const val SAMPLE_RATE = "sampleRate"
    const val VAD = "vad"
    const val SILENCE_END_MILLIS = "silenceEndMillis"
    const val MAX_DURATION_MILLIS = "maxDurationMillis"
    const val TIMEOUT_MILLIS = "timeoutMillis"
    const val HOTWORDS = "hotwords"
    const val PUNCTUATION = "punctuation"
    const val KEYWORD = "keyword"
    const val BOOST = "boost"
    const val THRESHOLD = "threshold"
    const val THEN_LISTEN = "thenListen"
    const val MIN_INTERVAL_MILLIS = "minIntervalMillis"
    const val MIN_SPEECH_MILLIS = "minSpeechMillis"
    const val MIN_SILENCE_MILLIS = "minSilenceMillis"
    const val SPEAKER = "speaker"
    const val SPEED = "speed"
    const val FORMAT = "format"
    const val KIND = "kind"
    const val INSTALLED = "installed"
    const val ACCEPT_LICENSE = "acceptLicense"
    const val ON_PROGRESS = "onProgress"
    const val ID = "id"
    const val MODEL_TYPE = "modelType"
    const val NAME = "name"
    const val LANGUAGES = "languages"
    const val ENGINE = "engine"
    const val VOICE = "voice"
    const val RATE = "rate"

    /** The largest PCM sample rate accepted for `recognize` / `detectVoice` (the plugin resamples). zh-CN: `recognize` / `detectVoice` 接受的最大 PCM 采样率 (插件负责重采样). */
    const val MAX_PCM_SAMPLE_RATE = 192_000
    const val MIN_PCM_SAMPLE_RATE = 8_000

    /** `silenceEndMillis` upper bound (one minute of silence still ends an utterance). zh-CN: `silenceEndMillis` 上限. */
    const val MAX_SILENCE_END_MILLIS = 60_000L

    /** `minIntervalMillis` upper bound. zh-CN: `minIntervalMillis` 上限. */
    const val MAX_MIN_INTERVAL_MILLIS = 600_000L

    private val LISTEN_KEYS = setOf(MODEL, LANGUAGE, SOURCE, SAMPLE_RATE, VAD, SILENCE_END_MILLIS, MAX_DURATION_MILLIS, TIMEOUT_MILLIS, HOTWORDS, PUNCTUATION)
    private val WAKE_KEYS = setOf(MODEL, THEN_LISTEN, MIN_INTERVAL_MILLIS)
    private val KEYWORD_KEYS = setOf(KEYWORD, BOOST, THRESHOLD)
    private val RECOGNIZE_KEYS = setOf(MODEL, LANGUAGE, HOTWORDS, PUNCTUATION, VAD, SAMPLE_RATE)
    private val DETECT_KEYS = setOf(MODEL, THRESHOLD, MIN_SPEECH_MILLIS, MIN_SILENCE_MILLIS, SAMPLE_RATE)
    private val SYNTHESIZE_KEYS = setOf(MODEL, SPEAKER, SPEED, FORMAT)
    private val QUERY_KEYS = setOf(KIND, LANGUAGE, INSTALLED)
    private val INSTALL_KEYS = setOf(ACCEPT_LICENSE, ON_PROGRESS)
    private val IMPORT_KEYS = setOf(ID, KIND, MODEL_TYPE, NAME, LANGUAGES, ACCEPT_LICENSE, ON_PROGRESS)

    /** `tts` options `speak` refuses: the engine is the plugin's, the voice and rate come from `model` / `speaker` / `speed`. zh-CN: `speak` 拒绝的 `tts` 选项. */
    private val SPEAK_RESERVED = setOf(ENGINE, VOICE, RATE, "speechRate")

    /** The `tts` language keys in the order `tts` reads them. zh-CN: `tts` 的语言键, 顺序与 `tts` 读取时一致. */
    private val SPEAK_LANGUAGE_KEYS = listOf("locale", LANGUAGE, "lang")

    private val LANGUAGE_TAG = Regex("^[A-Za-z]{2,8}([-_][A-Za-z0-9]{1,8}){0,4}$")

    /** `content://`, `file://`, `http://` and friends: the script side has no authorization model for them. zh-CN: `content://`, `file://`, `http://` 等: 脚本侧没有针对它们的授权模型. */
    private val URI_SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]+:")

    // ------------------------------------------------------------------ sessions

    /** `speech.listen(options?)`. zh-CN: `speech.listen(options?)`. */
    fun listen(options: JsonElement?): SpeechListenRequest = listenFrom(optionalObject(options, "options"), "options")

    private fun listenFrom(given: JsonObject?, label: String): SpeechListenRequest {
        if (given == null) return SpeechListenRequest()
        given.rejectUnknown(label, LISTEN_KEYS)
        val source = given.stringOrNull(label, SOURCE)?.trim()?.lowercase(Locale.ROOT) ?: SpeechContract.SOURCE_MIC
        if (source !in SpeechContract.SOURCES) throw invalid("'$label.source' must be one of ${SpeechContract.SOURCES.joinToString()}, got $source")
        val sampleRate = given.intOrNull(label, SAMPLE_RATE) ?: SpeechLimits.EXTERNAL_SAMPLE_RATE
        if (sampleRate != SpeechLimits.EXTERNAL_SAMPLE_RATE) {
            throw invalid("'$label.sampleRate' must be ${SpeechLimits.EXTERNAL_SAMPLE_RATE} (16 kHz mono PCM16), got $sampleRate")
        }
        val silenceEnd = given.longOrNull(label, SILENCE_END_MILLIS) ?: SpeechLimits.DEFAULT_SILENCE_END_MILLIS
        if (silenceEnd !in 1..MAX_SILENCE_END_MILLIS) throw invalid("'$label.silenceEndMillis' must be between 1 and $MAX_SILENCE_END_MILLIS, got $silenceEnd")
        val maxDuration = given.longOrNull(label, MAX_DURATION_MILLIS) ?: SpeechLimits.DEFAULT_MAX_DURATION_MILLIS
        if (maxDuration < 1) throw invalid("'$label.maxDurationMillis' must be a positive integer, got $maxDuration")
        if (maxDuration > SpeechLimits.MAX_AUDIO_MILLIS) {
            throw limit("'$label.maxDurationMillis' is at most ${SpeechLimits.MAX_AUDIO_MILLIS} (30 minutes), got $maxDuration")
        }
        val timeout = given.longOrNull(label, TIMEOUT_MILLIS) ?: SpeechLimits.DEFAULT_TIMEOUT_MILLIS
        if (timeout !in 0..SpeechLimits.MAX_AUDIO_MILLIS) throw invalid("'$label.timeoutMillis' must be between 0 and ${SpeechLimits.MAX_AUDIO_MILLIS}, got $timeout")
        return SpeechListenRequest(
            model = token(given.get(MODEL), "$label.model"),
            language = language(given.get(LANGUAGE), "$label.language"),
            source = source,
            sampleRate = sampleRate,
            vad = given.booleanOrNull(label, VAD) ?: true,
            silenceEndMillis = silenceEnd,
            maxDurationMillis = maxDuration,
            timeoutMillis = timeout,
            hotwords = hotwords(given.get(HOTWORDS), "$label.hotwords"),
            punctuation = given.booleanOrNull(label, PUNCTUATION) ?: true,
        )
    }

    /**
     * `speech.wake(keywords, options?)`: [keywords] is one keyword, or an array of strings and `{ keyword, boost?,
     * threshold? }` objects (1 to `MAX_KEYWORDS`); `thenListen` is `true` or the options of the dictation rounds.
     * zh-CN: `speech.wake(keywords, options?)`: [keywords] 为一个关键词, 或字符串与 `{ keyword, boost?, threshold? }` 对象的数组 (1 到
     * `MAX_KEYWORDS` 个); `thenListen` 为 `true` 或听写轮次的选项.
     */
    fun wake(keywords: JsonElement?, options: JsonElement?): SpeechWakeRequest {
        val list = when {
            keywords == null || keywords.isJsonNull -> throw invalid("'keywords' is required: a keyword or an array of keywords")
            keywords.isJsonArray -> keywords.asJsonArray.mapIndexed { i, element -> keyword(element, "keywords[$i]") }
            else -> listOf(keyword(keywords, "keywords"))
        }
        if (list.isEmpty()) throw invalid("'keywords' must name at least one keyword")
        if (list.size > SpeechLimits.MAX_KEYWORDS) throw limit("a wake session takes at most ${SpeechLimits.MAX_KEYWORDS} keywords, not ${list.size}")
        if (list.map { it.keyword }.toSet().size != list.size) throw invalid("'keywords' must not repeat a keyword")
        val given = optionalObject(options, "options") ?: return SpeechWakeRequest(list)
        given.rejectUnknown("options", WAKE_KEYS)
        val interval = given.longOrNull("options", MIN_INTERVAL_MILLIS) ?: SpeechLimits.DEFAULT_MIN_INTERVAL_MILLIS
        if (interval !in 0..MAX_MIN_INTERVAL_MILLIS) throw invalid("'options.minIntervalMillis' must be between 0 and $MAX_MIN_INTERVAL_MILLIS, got $interval")
        val thenListen = given.get(THEN_LISTEN)?.takeUnless { it.isJsonNull }?.let { value ->
            when {
                value.isJsonPrimitive && value.asJsonPrimitive.isBoolean -> if (value.asBoolean) SpeechListenRequest() else null
                value.isJsonObject -> listenFrom(value.asJsonObject, "options.thenListen")
                else -> throw invalid("'options.thenListen' must be a boolean or the options of speech.listen, got ${brief(value)}")
            }
        }
        if (thenListen != null && thenListen.isExternal) throw invalid("'options.thenListen.source' must be \"mic\": the wake session listens to the microphone")
        return SpeechWakeRequest(list, token(given.get(MODEL), "options.model"), interval, thenListen)
    }

    private fun keyword(value: JsonElement, label: String): SpeechKeyword = when {
        value.isJsonPrimitive -> SpeechKeyword(keywordText(value, label))
        value.isJsonObject -> {
            val given = value.asJsonObject
            given.rejectUnknown(label, KEYWORD_KEYS)
            val boost = given.doubleOrNull(label, BOOST)
            if (boost != null && boost !in -100.0..100.0) throw invalid("'$label.boost' must be between -100 and 100, got $boost")
            val threshold = given.doubleOrNull(label, THRESHOLD)
            if (threshold != null && threshold !in 0.0..1.0) throw invalid("'$label.threshold' must be between 0 and 1, got $threshold")
            SpeechKeyword(keywordText(given.get(KEYWORD), "$label.keyword"), boost, threshold)
        }
        else -> throw invalid("'$label' must be a string or { keyword, boost?, threshold? }, got ${brief(value)}")
    }

    private fun keywordText(value: JsonElement?, label: String): String {
        val text = string(value, label).trim()
        if (text.isEmpty()) throw invalid("'$label' must not be blank")
        if (text.length > SpeechLimits.MAX_TOKEN_CHARS) throw limit("'$label' exceeds ${SpeechLimits.MAX_TOKEN_CHARS} characters")
        return text
    }

    // ------------------------------------------------------------------ files and PCM

    /** `speech.recognize(source, options?)`. zh-CN: `speech.recognize(source, options?)`. */
    fun recognize(options: JsonElement?): Recognize {
        val given = optionalObject(options, "options") ?: return Recognize(SpeechRecognizeRequest(), SpeechLimits.EXTERNAL_SAMPLE_RATE)
        given.rejectUnknown("options", RECOGNIZE_KEYS)
        val request = SpeechRecognizeRequest(
            model = token(given.get(MODEL), "options.model"),
            language = language(given.get(LANGUAGE), "options.language"),
            hotwords = hotwords(given.get(HOTWORDS), "options.hotwords"),
            punctuation = given.booleanOrNull("options", PUNCTUATION) ?: true,
            vad = given.booleanOrNull("options", VAD) ?: true,
        )
        return Recognize(request, pcmSampleRate(given))
    }

    /** `speech.detectVoice(source, options?)`. zh-CN: `speech.detectVoice(source, options?)`. */
    fun detectVoice(options: JsonElement?): DetectVoice {
        val given = optionalObject(options, "options") ?: return DetectVoice(SpeechVadRequest(), SpeechLimits.EXTERNAL_SAMPLE_RATE)
        given.rejectUnknown("options", DETECT_KEYS)
        val threshold = given.doubleOrNull("options", THRESHOLD)
        if (threshold != null && threshold !in 0.0..1.0) throw invalid("'options.threshold' must be between 0 and 1, got $threshold")
        val minSpeech = given.longOrNull("options", MIN_SPEECH_MILLIS)
        if (minSpeech != null && minSpeech !in 0..SpeechLimits.MAX_AUDIO_MILLIS) throw invalid("'options.minSpeechMillis' must be between 0 and ${SpeechLimits.MAX_AUDIO_MILLIS}, got $minSpeech")
        val minSilence = given.longOrNull("options", MIN_SILENCE_MILLIS)
        if (minSilence != null && minSilence !in 0..SpeechLimits.MAX_AUDIO_MILLIS) throw invalid("'options.minSilenceMillis' must be between 0 and ${SpeechLimits.MAX_AUDIO_MILLIS}, got $minSilence")
        return DetectVoice(SpeechVadRequest(token(given.get(MODEL), "options.model"), threshold, minSpeech, minSilence), pcmSampleRate(given))
    }

    private fun pcmSampleRate(given: JsonObject): Int {
        val rate = given.intOrNull("options", SAMPLE_RATE) ?: SpeechLimits.EXTERNAL_SAMPLE_RATE
        if (rate !in MIN_PCM_SAMPLE_RATE..MAX_PCM_SAMPLE_RATE) {
            throw invalid("'options.sampleRate' must be between $MIN_PCM_SAMPLE_RATE and $MAX_PCM_SAMPLE_RATE, got $rate")
        }
        return rate
    }

    /** A script path: a non-blank string that is not a URI; the caller applies the working-directory rules. zh-CN: 脚本路径: 非空白且不是 URI 的字符串; 工作目录规则由调用方应用. */
    fun path(value: JsonElement?, label: String): String {
        val text = string(value, label)
        if (text.isBlank()) throw invalid("'$label' must not be blank")
        if (URI_SCHEME.containsMatchIn(text)) {
            throw invalid("'$label' must be a file path (relative to the script directory or absolute); URIs such as content:// are not accepted, got $text")
        }
        return text
    }

    // ------------------------------------------------------------------ synthesis

    /** `speech.synthesize(text, path, options?)`: the text (1 to `MAX_SYNTH_TEXT_CHARS`) and the synthesis options. zh-CN: `speech.synthesize(text, path, options?)`: 文本 (1 到 `MAX_SYNTH_TEXT_CHARS` 字符) 与合成选项. */
    fun synthesize(text: JsonElement?, options: JsonElement?): SpeechSynthesisRequest {
        val content = synthesisText(text)
        val given = optionalObject(options, "options") ?: return SpeechSynthesisRequest(content)
        given.rejectUnknown("options", SYNTHESIZE_KEYS)
        val format = given.stringOrNull("options", FORMAT)?.trim()?.lowercase(Locale.ROOT) ?: SpeechContract.FORMAT_WAV
        if (format !in SpeechContract.FORMATS) throw invalid("'options.format' must be one of ${SpeechContract.FORMATS.joinToString()}, got $format")
        return SpeechSynthesisRequest(
            text = content,
            model = token(given.get(MODEL), "options.model"),
            speaker = speaker(given.get(SPEAKER), "options.speaker"),
            speed = speed(given, "options"),
            format = format,
        )
    }

    /**
     * `speech.speak(text, options?)`: `model`, `speaker` and `speed` become the plugin voice and the speech rate, every
     * other key goes to `tts` unchanged (it validates them itself); `engine`, `voice` and `rate` are refused. With
     * `model` the `tts` language keys (`locale`, `language`, `lang`) only choose the model's speaker when `speaker` is
     * absent ([textLanguage] of [text] otherwise) and do not reach `tts`; without `model` and without a language key
     * the voice follows the [textLanguage] of [text] when the engine has it.
     * zh-CN: `speech.speak(text, options?)`: `model`, `speaker` 与 `speed` 转为插件语音与语速, 其余键原样交给 `tts` (由其自行校验); 拒绝
     * `engine`, `voice` 与 `rate`. 给出 `model` 时, `tts` 的语言键 (`locale`, `language`, `lang`) 只在未给 `speaker` 时用于选择模型的说话人
     * (否则用 [text] 的 [textLanguage]), 不交给 `tts`; 既无 `model` 也无语言键时, 引擎支持 [text] 的 [textLanguage] 则按其选择语音.
     */
    fun speak(options: JsonElement?, text: String): Speak {
        val given = optionalObject(options, "options") ?: return Speak(null, null, null, textLanguage(text), JsonObject())
        given.keySet().firstOrNull { it in SPEAK_RESERVED }?.let { key ->
            throw invalid("'options.$key' is not accepted: speech.speak always uses the 3-Macaw Speech engine; choose the voice with 'model' / 'speaker' and the pace with 'speed'")
        }
        val passThrough = given.deepCopy().apply {
            remove(MODEL)
            remove(SPEAKER)
            remove(SPEED)
        }
        val speaker = speaker(given.get(SPEAKER), "options.speaker")
        val model = token(given.get(MODEL), "options.model")
        if (speaker != null && model == null) throw invalid("'options.speaker' needs 'options.model'")
        if (speaker != null && '/' in speaker) throw invalid("'options.speaker' must be a speaker id or name without '/', got $speaker")
        val languageKey = SPEAK_LANGUAGE_KEYS.firstOrNull { given.has(it) && !given.get(it).isJsonNull }
        if (model == null) return Speak(null, null, speed(given, "options"), if (languageKey == null) textLanguage(text) else null, passThrough)
        SPEAK_LANGUAGE_KEYS.forEach(passThrough::remove)
        val languageTag = languageKey?.let { key ->
            val tag = language(given.get(key), "options.$key") ?: return@let null
            try {
                normalizeTtsLanguageTag(tag)
            } catch (e: IllegalArgumentException) {
                throw invalid("'options.$key' must be a language tag such as zh, en or zh-CN, got $tag")
            }
        } ?: textLanguage(text)
        return Speak(model, speaker, speed(given, "options"), languageTag, passThrough)
    }

    /**
     * The language a speaker should read [text] in: `ja` for kana, `ko` for Hangul, `zh` for other text with Han
     * characters (mixed Chinese and English included), `en` for Latin letters only, null otherwise.
     * zh-CN: 朗读 [text] 的说话人应使用的语言: 含假名为 `ja`, 含谚文为 `ko`, 其他含汉字的文本 (包括中英混合) 为 `zh`, 只有拉丁字母为 `en`, 其余为 null.
     */
    fun textLanguage(text: String): String? {
        var han = false
        var latin = false
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> return "ja"
                Character.UnicodeScript.HANGUL -> return "ko"
                Character.UnicodeScript.HAN -> han = true
                Character.UnicodeScript.LATIN -> latin = true
                else -> Unit
            }
            i += Character.charCount(codePoint)
        }
        return when {
            han -> "zh"
            latin -> "en"
            else -> null
        }
    }

    fun synthesisText(value: JsonElement?): String {
        val text = string(value, "text")
        if (text.isBlank()) throw invalid("'text' must not be blank")
        if (text.length > SpeechLimits.MAX_SYNTH_TEXT_CHARS) {
            throw limit("'text' has ${text.length} characters; one synthesis takes at most ${SpeechLimits.MAX_SYNTH_TEXT_CHARS}")
        }
        return text
    }

    private fun speaker(value: JsonElement?, label: String): String? {
        if (value == null || value.isJsonNull) return null
        val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: throw invalid("'$label' must be a speaker id or name, got ${brief(value)}")
        if (primitive.isNumber) {
            val number = integral(value, label, 0.0, Int.MAX_VALUE.toDouble())
            return number.toLong().toString()
        }
        return token(value, label)
    }

    private fun speed(given: JsonObject, label: String): Double? {
        val speed = given.doubleOrNull(label, SPEED) ?: return null
        if (speed < SpeechLimits.MIN_SPEED || speed > SpeechLimits.MAX_SPEED) {
            throw invalid("'$label.speed' must be between ${SpeechLimits.MIN_SPEED} and ${SpeechLimits.MAX_SPEED}, got $speed")
        }
        return speed
    }

    // ------------------------------------------------------------------ models

    /** `speech.models.list(filter?)`. zh-CN: `speech.models.list(filter?)`. */
    fun modelQuery(filter: JsonElement?): SpeechModelQuery {
        val given = optionalObject(filter, "filter") ?: return SpeechModelQuery()
        given.rejectUnknown("filter", QUERY_KEYS)
        return SpeechModelQuery(
            kind = given.get(KIND)?.takeUnless { it.isJsonNull }?.let { kind(it, "filter.kind") },
            language = language(given.get(LANGUAGE), "filter.language"),
            installed = given.booleanOrNull("filter", INSTALLED),
        )
    }

    /** `speech.models.install(id, options?)`. zh-CN: `speech.models.install(id, options?)`. */
    fun install(id: JsonElement?, options: JsonElement?): SpeechInstallRequest {
        val modelId = modelId(id, "id")
        val given = optionalObject(options, "options") ?: return SpeechInstallRequest(modelId)
        given.rejectUnknown("options", INSTALL_KEYS)
        rejectNonFunctionProgress(given)
        return SpeechInstallRequest(modelId, given.booleanOrNull("options", ACCEPT_LICENSE) ?: false)
    }

    /** `speech.models.importFile(path, options?)`. zh-CN: `speech.models.importFile(path, options?)`. */
    fun importModel(options: JsonElement?): SpeechImportRequest {
        val given = optionalObject(options, "options") ?: return SpeechImportRequest()
        given.rejectUnknown("options", IMPORT_KEYS)
        rejectNonFunctionProgress(given)
        val languages = given.get(LANGUAGES)?.takeUnless { it.isJsonNull }?.let { value ->
            if (!value.isJsonArray) throw invalid("'options.languages' must be an array of language tags, got ${brief(value)}")
            value.asJsonArray.mapIndexed { i, element -> language(element, "options.languages[$i]") ?: throw invalid("'options.languages[$i]' must not be null") }
        }.orEmpty()
        return SpeechImportRequest(
            id = given.get(ID)?.takeUnless { it.isJsonNull }?.let { modelId(it, "options.id") },
            kind = given.get(KIND)?.takeUnless { it.isJsonNull }?.let { kind(it, "options.kind") },
            modelType = token(given.get(MODEL_TYPE), "options.modelType"),
            name = given.stringOrNull("options", NAME)?.trim()?.takeIf { it.isNotEmpty() }?.also {
                if (it.length > SpeechLimits.MAX_TOKEN_CHARS) throw limit("'options.name' exceeds ${SpeechLimits.MAX_TOKEN_CHARS} characters")
            },
            languages = languages,
            acceptLicense = given.booleanOrNull("options", ACCEPT_LICENSE) ?: false,
        )
    }

    fun modelId(value: JsonElement?, label: String): String = token(value, label) ?: throw invalid("'$label' is required")

    fun kind(value: JsonElement?, label: String): String {
        val text = string(value, label).trim().lowercase(Locale.ROOT)
        if (text !in SpeechContract.KINDS) throw invalid("'$label' must be one of ${SpeechContract.KINDS.joinToString()}, got $text")
        return text
    }

    /** `onProgress` survives `JSON.stringify` only when it is not a function. zh-CN: 只有当 `onProgress` 不是函数时才会在 `JSON.stringify` 后留下. */
    private fun rejectNonFunctionProgress(given: JsonObject) {
        if (given.has(ON_PROGRESS) && !given.get(ON_PROGRESS).isJsonNull) throw invalid("'options.onProgress' must be a function")
    }

    // ------------------------------------------------------------------ helpers

    /** An `INVALID_OPTIONS` failure as the script sees it. zh-CN: 脚本眼中的 `INVALID_OPTIONS` 失败. */
    fun invalid(message: String): SpeechPluginException = SpeechErrorMapper.failure(SpeechErrorCodes.INVALID_OPTIONS, message)

    /** A `LIMIT_EXCEEDED` failure as the script sees it. zh-CN: 脚本眼中的 `LIMIT_EXCEEDED` 失败. */
    fun limit(message: String): SpeechPluginException = SpeechErrorMapper.failure(SpeechErrorCodes.LIMIT_EXCEEDED, message)

    private fun hotwords(value: JsonElement?, label: String): List<String> {
        if (value == null || value.isJsonNull) return emptyList()
        if (!value.isJsonArray) throw invalid("'$label' must be an array of strings, got ${brief(value)}")
        val list = value.asJsonArray.mapIndexed { i, element -> keywordText(element, "$label[$i]") }.distinct()
        if (list.size > SpeechLimits.MAX_HOTWORDS) throw limit("'$label' holds at most ${SpeechLimits.MAX_HOTWORDS} hotwords, not ${list.size}")
        return list
    }

    /** A short identifier (model id, model type): trimmed, non-blank, at most `MAX_TOKEN_CHARS`; null when absent. zh-CN: 短标识 (模型 id, 模型类型): 去空白, 非空, 至多 `MAX_TOKEN_CHARS`; 缺省为 null. */
    private fun token(value: JsonElement?, label: String): String? {
        if (value == null || value.isJsonNull) return null
        val text = string(value, label).trim()
        if (text.isEmpty()) throw invalid("'$label' must not be blank")
        if (text.length > SpeechLimits.MAX_TOKEN_CHARS) throw limit("'$label' exceeds ${SpeechLimits.MAX_TOKEN_CHARS} characters")
        return text
    }

    private fun language(value: JsonElement?, label: String): String? {
        val text = token(value, label) ?: return null
        if (!LANGUAGE_TAG.matches(text)) throw invalid("'$label' must be a language tag such as zh, en or yue, got $text")
        return text.replace('_', '-')
    }

    private fun string(value: JsonElement?, label: String): String {
        if (value == null || value.isJsonNull) throw invalid("'$label' is required")
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw invalid("'$label' must be a string, got ${brief(value)}")
        return value.asString
    }

    private fun integral(value: JsonElement, label: String, min: Double, max: Double): Double {
        val number = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asDouble
            ?: throw invalid("'$label' must be an integer, got ${brief(value)}")
        if (number.isNaN() || number.isInfinite() || number != floor(number) || number < min || number > max) {
            throw invalid("'$label' must be an integer between ${min.toLong()} and ${max.toLong()}, got ${value.asJsonPrimitive.asString}")
        }
        return number
    }

    private fun optionalObject(value: JsonElement?, label: String): JsonObject? {
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonObject) throw invalid("'$label' must be an object, got ${brief(value)}")
        return value.asJsonObject
    }

    private fun JsonObject.rejectUnknown(label: String, known: Set<String>) {
        keySet().firstOrNull { it !in known }?.let { key -> throw invalid("unknown option '$label.$key'") }
    }

    private fun JsonObject.stringOrNull(label: String, key: String): String? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return string(value, "$label.$key")
    }

    private fun JsonObject.intOrNull(label: String, key: String): Int? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return integral(value, "$label.$key", Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble()).toInt()
    }

    private fun JsonObject.longOrNull(label: String, key: String): Long? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        return integral(value, "$label.$key", -MAX_SAFE_INTEGER, MAX_SAFE_INTEGER).toLong()
    }

    private fun JsonObject.doubleOrNull(label: String, key: String): Double? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        val number = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asDouble
            ?: throw invalid("'$label.$key' must be a number, got ${brief(value)}")
        if (number.isNaN() || number.isInfinite()) throw invalid("'$label.$key' must be a finite number")
        return number
    }

    private fun JsonObject.booleanOrNull(label: String, key: String): Boolean? {
        val value = get(key)
        if (value == null || value.isJsonNull) return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isBoolean) throw invalid("'$label.$key' must be a boolean, got ${brief(value)}")
        return value.asBoolean
    }

    private fun brief(value: JsonElement): String = when {
        value.isJsonNull -> "null"
        value.isJsonArray -> "an array"
        value.isJsonObject -> "an object"
        value.asJsonPrimitive.isString -> "\"${value.asString.take(40)}\""
        else -> value.asJsonPrimitive.asString
    }

    /** The largest integer a script number holds exactly (2^53 - 1). zh-CN: 脚本数字能精确表示的最大整数 (2^53 - 1). */
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0
}
