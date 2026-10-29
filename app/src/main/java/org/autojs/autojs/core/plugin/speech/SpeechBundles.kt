package org.autojs.autojs.core.plugin.speech

import android.os.Bundle
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
import org.autojs.plugin.speech.api.SpeechLimits
import org.autojs.plugin.speech.api.SpeechModelKeys
import org.autojs.plugin.speech.api.SpeechOptionKeys
import org.autojs.plugin.speech.api.SpeechResultKeys

/**
 * Bundle shapes of the speech contract as written and read by the host (roadmap D21, appendix C.1): every request
 * carries the host's contract version, answers are checked for their version and error code, and the answer, event and
 * model Bundles become plain JVM values (maps, lists, strings, booleans and numbers) before they leave the Binder
 * thread, so the script layer never touches a Bundle.
 * zh-CN: 宿主读写的语音契约 Bundle 形态 (路线图 D21, 附录 C.1): 每个请求带宿主的契约版本, 应答检查其版本与错误码, 应答, 事件与模型 Bundle
 * 在离开 Binder 线程前转换为普通 JVM 值 (map, list, 字符串, 布尔与数字), 因此脚本层从不接触 Bundle.
 */
object SpeechBundles {

    /**
     * The Bundle every request carries, stamped with the contract [version] of its call ([SpeechContract.CALL_VERSION_1]
     * for the methods of version 1, which every plugin answers; roadmap D136); [build] adds the request keys.
     * zh-CN: 每个请求携带的 Bundle, 带其调用所属的契约 [version] (版本 1 的方法为 [SpeechContract.CALL_VERSION_1], 所有插件都应答; 路线图
     * D136); [build] 添加请求键.
     */
    fun request(version: Int = SpeechContract.CALL_VERSION_1, build: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        putInt(SpeechContract.KEY_CONTRACT_VERSION, version)
        build()
    }

    fun listen(request: SpeechListenRequest): Bundle = request { putListen(request) }

    fun wake(request: SpeechWakeRequest): Bundle = request {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        putLong(SpeechOptionKeys.MIN_INTERVAL_MILLIS, request.minIntervalMillis)
        putParcelableArrayList(SpeechOptionKeys.KEYWORDS, ArrayList(request.keywords.map { keyword ->
            Bundle().apply {
                putString(SpeechOptionKeys.KEYWORD, keyword.keyword)
                keyword.boost?.let { putFloat(SpeechOptionKeys.BOOST, it.toFloat()) }
                keyword.threshold?.let { putFloat(SpeechOptionKeys.THRESHOLD, it.toFloat()) }
            }
        }))
        request.thenListen?.let { listen -> putBundle(SpeechOptionKeys.THEN_LISTEN, Bundle().apply { putListen(listen) }) }
    }

    fun recognize(request: SpeechRecognizeRequest, displayName: String?): Bundle = request {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        request.language?.let { putString(SpeechOptionKeys.LANGUAGE, it) }
        if (request.hotwords.isNotEmpty()) putStringArray(SpeechOptionKeys.HOTWORDS, request.hotwords.toTypedArray())
        putBoolean(SpeechOptionKeys.PUNCTUATION, request.punctuation)
        putBoolean(SpeechOptionKeys.VAD, request.vad)
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    fun detectVoice(request: SpeechVadRequest, displayName: String?): Bundle = request {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        request.threshold?.let { putFloat(SpeechOptionKeys.VAD_THRESHOLD, it.toFloat()) }
        request.minSpeechMillis?.let { putLong(SpeechOptionKeys.MIN_SPEECH_MILLIS, it) }
        request.minSilenceMillis?.let { putLong(SpeechOptionKeys.MIN_SILENCE_MILLIS, it) }
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    fun synthesis(request: SpeechSynthesisRequest): Bundle = request {
        putString(SpeechOptionKeys.TEXT, request.text)
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        request.speaker?.let { putString(SpeechOptionKeys.SPEAKER, it) }
        request.speed?.let { putDouble(SpeechOptionKeys.SPEED, it) }
        putString(SpeechOptionKeys.FORMAT, request.format)
    }

    fun modelQuery(query: SpeechModelQuery): Bundle = request {
        query.kind?.let { putString(SpeechOptionKeys.KIND, it) }
        query.language?.let { putString(SpeechOptionKeys.LANGUAGE, it) }
        query.installed?.let { putBoolean(SpeechOptionKeys.INSTALLED, it) }
    }

    fun install(request: SpeechInstallRequest): Bundle = request {
        putString(SpeechOptionKeys.MODEL, request.id)
        putBoolean(SpeechOptionKeys.ACCEPT_LICENSE, request.acceptLicense)
    }

    fun importModel(request: SpeechImportRequest, displayName: String?): Bundle = request {
        request.id?.let { putString(SpeechOptionKeys.MODEL, it) }
        request.kind?.let { putString(SpeechOptionKeys.KIND, it) }
        request.modelType?.let { putString(SpeechOptionKeys.MODEL_TYPE, it) }
        request.name?.let { putString(SpeechOptionKeys.NAME, it) }
        if (request.languages.isNotEmpty()) putStringArray(SpeechOptionKeys.LANGUAGES, request.languages.toTypedArray())
        putBoolean(SpeechOptionKeys.ACCEPT_LICENSE, request.acceptLicense)
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    fun modelRequest(id: String, kind: String? = null): Bundle = request {
        putString(SpeechOptionKeys.MODEL, id)
        kind?.let { putString(SpeechOptionKeys.KIND, it) }
    }

    // Speakers (contract version 2, roadmap D132 - D134).

    fun enrollSpeaker(request: SpeechEnrollRequest, displayNames: List<String>): Bundle = request(SpeechContract.CALL_VERSION_2) {
        putString(SpeechOptionKeys.NAME, request.name)
        putBoolean(SpeechOptionKeys.REPLACE, request.replace)
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        putStringArray(SpeechOptionKeys.DISPLAY_NAMES, displayNames.toTypedArray())
    }

    fun verifySpeaker(request: SpeechVerifyRequest, displayName: String?): Bundle = request(SpeechContract.CALL_VERSION_2) {
        putString(SpeechOptionKeys.NAME, request.name)
        request.threshold?.let { putDouble(SpeechOptionKeys.THRESHOLD, it) }
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    fun identifySpeaker(request: SpeechIdentifyRequest, displayName: String?): Bundle = request(SpeechContract.CALL_VERSION_2) {
        request.threshold?.let { putDouble(SpeechOptionKeys.THRESHOLD, it) }
        putInt(SpeechOptionKeys.TOP_K, request.topK)
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    /** `listSpeakers` (no [name]) and `removeSpeaker`. zh-CN: `listSpeakers` (无 [name]) 与 `removeSpeaker`. */
    fun speakerRequest(name: String? = null): Bundle = request(SpeechContract.CALL_VERSION_2) {
        name?.let { putString(SpeechOptionKeys.NAME, it) }
    }

    /**
     * The script's `{ name, score, matched, threshold }` of `verifySpeaker`, or with `candidates` of `identifySpeaker`
     * (`name` null when no score reached the threshold); scores and thresholds rounded to four decimals, never an
     * embedding (roadmap D125, D133).
     * zh-CN: 脚本得到的 `verifySpeaker` 结果 `{ name, score, matched, threshold }`, 或带 `candidates` 的 `identifySpeaker` 结果 (没有分数达到
     * 阈值时 `name` 为 null); 分数与阈值保留四位小数, 从不含嵌入向量 (路线图 D125, D133).
     */
    fun speakerMatch(answer: Bundle, identify: Boolean): Map<String, Any?> {
        val plain = plain(answer)
        val result = linkedMapOf<String, Any?>(
            SpeechResultKeys.NAME to plain[SpeechResultKeys.NAME],
            SpeechResultKeys.SCORE to score(plain[SpeechResultKeys.SCORE]),
            SpeechResultKeys.MATCHED to (plain[SpeechResultKeys.MATCHED] == true),
            SpeechResultKeys.THRESHOLD to score(plain[SpeechResultKeys.THRESHOLD]),
        )
        if (identify) {
            result[SpeechResultKeys.CANDIDATES] = (plain[SpeechResultKeys.CANDIDATES] as? List<*>).orEmpty().mapNotNull { item ->
                val candidate = item as? Map<*, *> ?: return@mapNotNull null
                linkedMapOf(SpeechResultKeys.NAME to candidate[SpeechResultKeys.NAME], SpeechResultKeys.SCORE to score(candidate[SpeechResultKeys.SCORE]))
            }
        }
        return result
    }

    /** The `enrollSpeaker` answer: `{ name, samples, durationMillis, modelId }`. zh-CN: `enrollSpeaker` 的应答. */
    fun speakerEnrollment(answer: Bundle): Map<String, Any?> {
        val plain = plain(answer)
        return linkedMapOf(
            SpeechResultKeys.NAME to plain[SpeechResultKeys.NAME],
            SpeechResultKeys.SAMPLES to plain[SpeechResultKeys.SAMPLES],
            SpeechResultKeys.DURATION_MILLIS to plain[SpeechResultKeys.DURATION_MILLIS],
            SpeechResultKeys.MODEL_ID to plain[SpeechResultKeys.MODEL_ID],
        )
    }

    /** The speakers of a `listSpeakers` answer: `{ name, samples, updatedAt, modelId }` each. zh-CN: `listSpeakers` 应答中的说话人. */
    fun speakers(answer: Bundle): List<Map<String, Any?>> {
        @Suppress("DEPRECATION")
        val list = answer.getParcelableArrayList<Bundle>(SpeechResultKeys.SPEAKERS).orEmpty()
        return list.take(SpeechLimits.MAX_SPEAKERS).map { bundle ->
            val plain = plain(bundle)
            linkedMapOf(
                SpeechResultKeys.NAME to plain[SpeechResultKeys.NAME],
                SpeechResultKeys.SAMPLES to plain[SpeechResultKeys.SAMPLES],
                SpeechResultKeys.UPDATED_AT to plain[SpeechResultKeys.UPDATED_AT],
                SpeechResultKeys.MODEL_ID to plain[SpeechResultKeys.MODEL_ID],
            )
        }
    }

    private fun score(value: Any?): Double? = (value as? Number)?.toDouble()?.let { Math.round(it * 10_000) / 10_000.0 }

    private fun Bundle.putListen(request: SpeechListenRequest) {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        request.language?.let { putString(SpeechOptionKeys.LANGUAGE, it) }
        putString(SpeechOptionKeys.SOURCE, request.source)
        putInt(SpeechOptionKeys.SAMPLE_RATE, request.sampleRate)
        putBoolean(SpeechOptionKeys.VAD, request.vad)
        putLong(SpeechOptionKeys.SILENCE_END_MILLIS, request.silenceEndMillis)
        putLong(SpeechOptionKeys.MAX_DURATION_MILLIS, request.maxDurationMillis)
        putLong(SpeechOptionKeys.TIMEOUT_MILLIS, request.timeoutMillis)
        if (request.hotwords.isNotEmpty()) putStringArray(SpeechOptionKeys.HOTWORDS, request.hotwords.toTypedArray())
        putBoolean(SpeechOptionKeys.PUNCTUATION, request.punctuation)
    }

    // Offline analyses (contract version 2, roadmap D147 - D151).

    fun diarize(request: SpeechDiarizeRequest, displayName: String?): Bundle = request(SpeechContract.CALL_VERSION_2) {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        request.speakerModel?.let { putString(SpeechOptionKeys.SPEAKER_MODEL, it) }
        putInt(SpeechOptionKeys.SPEAKER_COUNT, request.speakers)
        request.threshold?.let { putFloat(SpeechOptionKeys.THRESHOLD, it.toFloat()) }
        putBoolean(SpeechOptionKeys.IDENTIFY, request.identify)
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    fun detectLanguage(request: SpeechLanguageRequest, displayName: String?): Bundle = request(SpeechContract.CALL_VERSION_2) {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        putInt(SpeechOptionKeys.WINDOWS, request.windows)
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    fun tag(request: SpeechTagRequest, displayName: String?): Bundle = request(SpeechContract.CALL_VERSION_2) {
        request.model?.let { putString(SpeechOptionKeys.MODEL, it) }
        putInt(SpeechOptionKeys.TOP_K, request.topK)
        putBoolean(SpeechOptionKeys.TIMELINE, request.timeline)
        displayName?.let { putString(SpeechOptionKeys.DISPLAY_NAME, it) }
    }

    /**
     * The script's `{ segments: [{ startMillis, endMillis, speaker }], speakers, durationMillis, modelId }` of
     * `diarizeFile` from its [plain] completion, with `names: [{ speaker, name, score }]` when the plugin identified the
     * speakers (roadmap D147); the parallel arrays of the answer (D151) must have one length.
     * zh-CN: 由 `diarizeFile` 的 [plain] 完成值构造脚本结果, 插件辨认了说话人时另有 `names: [{ speaker, name, score }]` (路线图 D147); 应答中的
     * 并列数组 (D151) 须等长.
     */
    fun diarization(plain: Map<String, Any?>): Map<String, Any?> {
        val starts = numbers(plain[SpeechResultKeys.SEGMENT_STARTS])
        val ends = numbers(plain[SpeechResultKeys.SEGMENT_ENDS])
        val speakers = numbers(plain[SpeechResultKeys.SEGMENT_SPEAKERS]).map { it.toInt() }
        if (ends.size != starts.size || speakers.size != starts.size) {
            throw SpeechErrorMapper.failure(SpeechErrorCodes.INTERNAL, "diarizeFile answered ${starts.size} starts, ${ends.size} ends and ${speakers.size} speakers")
        }
        val result = linkedMapOf<String, Any?>(
            SpeechResultKeys.SEGMENTS to starts.indices.map { i ->
                linkedMapOf(
                    SpeechResultKeys.START_MILLIS to SpeechValues.number(starts[i]),
                    SpeechResultKeys.END_MILLIS to SpeechValues.number(ends[i]),
                    SpeechResultKeys.SPEAKER to speakers[i],
                )
            },
            FIELD_SPEAKERS to (plain[SpeechResultKeys.SPEAKER_COUNT] ?: (speakers.maxOrNull()?.plus(1) ?: 0)),
            SpeechResultKeys.DURATION_MILLIS to plain[SpeechResultKeys.DURATION_MILLIS],
            SpeechResultKeys.MODEL_ID to plain[SpeechResultKeys.MODEL_ID],
        )
        (plain[SpeechResultKeys.NAMES] as? List<*>)?.let { names ->
            result[SpeechResultKeys.NAMES] = names.mapNotNull { item ->
                val entry = item as? Map<*, *> ?: return@mapNotNull null
                linkedMapOf(
                    SpeechResultKeys.SPEAKER to entry[SpeechResultKeys.SPEAKER],
                    SpeechResultKeys.NAME to entry[SpeechResultKeys.NAME],
                    SpeechResultKeys.SCORE to score(entry[SpeechResultKeys.SCORE]),
                )
            }
        }
        return result
    }

    /**
     * The script's `{ language, candidates: [{ language, score }], segments: [{ startMillis, endMillis, language }],
     * durationMillis, modelId }` from the [plain] completion of `detectLanguage` (roadmap D148).
     * zh-CN: 由 `detectLanguage` 的 [plain] 完成值构造脚本结果 (路线图 D148).
     */
    fun languageResult(plain: Map<String, Any?>): Map<String, Any?> = linkedMapOf(
        SpeechResultKeys.LANGUAGE to plain[SpeechResultKeys.LANGUAGE],
        SpeechResultKeys.CANDIDATES to entries(plain[SpeechResultKeys.CANDIDATES]).map { entry ->
            linkedMapOf(SpeechResultKeys.LANGUAGE to entry[SpeechResultKeys.LANGUAGE], SpeechResultKeys.SCORE to score(entry[SpeechResultKeys.SCORE]))
        },
        SpeechResultKeys.SEGMENTS to entries(plain[SpeechResultKeys.SEGMENTS]).map { entry ->
            linkedMapOf(
                SpeechResultKeys.START_MILLIS to entry[SpeechResultKeys.START_MILLIS],
                SpeechResultKeys.END_MILLIS to entry[SpeechResultKeys.END_MILLIS],
                SpeechResultKeys.LANGUAGE to entry[SpeechResultKeys.LANGUAGE],
            )
        },
        SpeechResultKeys.DURATION_MILLIS to plain[SpeechResultKeys.DURATION_MILLIS],
        SpeechResultKeys.MODEL_ID to plain[SpeechResultKeys.MODEL_ID],
    )

    /**
     * The script's `{ labels: [{ label, mid, score, startMillis, endMillis }], durationMillis, modelId }` from the [plain]
     * completion of `tagAudio`, with `timeline: [{ startMillis, endMillis, labels: [{ label, mid, score }] }]` rebuilt
     * from the parallel arrays when the plugin sent them (roadmap D149, D151).
     * zh-CN: 由 `tagAudio` 的 [plain] 完成值构造脚本结果; 插件发送了并列数组时由其还原 `timeline` (路线图 D149, D151).
     */
    fun tagResult(plain: Map<String, Any?>): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>(
            SpeechResultKeys.LABELS to entries(plain[SpeechResultKeys.LABELS]).map { entry ->
                linkedMapOf(
                    SpeechResultKeys.LABEL to entry[SpeechResultKeys.LABEL],
                    SpeechResultKeys.MID to entry[SpeechResultKeys.MID],
                    SpeechResultKeys.SCORE to score(entry[SpeechResultKeys.SCORE]),
                    SpeechResultKeys.START_MILLIS to entry[SpeechResultKeys.START_MILLIS],
                    SpeechResultKeys.END_MILLIS to entry[SpeechResultKeys.END_MILLIS],
                )
            },
            SpeechResultKeys.DURATION_MILLIS to plain[SpeechResultKeys.DURATION_MILLIS],
            SpeechResultKeys.MODEL_ID to plain[SpeechResultKeys.MODEL_ID],
        )
        if (plain[SpeechResultKeys.TIMELINE_STARTS] == null) return result
        val starts = numbers(plain[SpeechResultKeys.TIMELINE_STARTS])
        val ends = numbers(plain[SpeechResultKeys.TIMELINE_ENDS])
        val positions = numbers(plain[SpeechResultKeys.TIMELINE_LABELS]).map { it.toInt() }
        val scores = numbers(plain[SpeechResultKeys.TIMELINE_SCORES])
        val names = (plain[SpeechResultKeys.LABEL_NAMES] as? List<*>).orEmpty().map { it?.toString() }
        val mids = (plain[SpeechResultKeys.LABEL_MIDS] as? List<*>).orEmpty().map { it?.toString() }
        val topK = (plain[SpeechResultKeys.TIMELINE_TOP_K] as? Number)?.toInt() ?: 0
        if (ends.size != starts.size || topK < 0 || positions.size != starts.size * topK || scores.size != positions.size ||
            mids.size != names.size || positions.any { it !in names.indices }
        ) {
            throw SpeechErrorMapper.failure(SpeechErrorCodes.INTERNAL, "tagAudio answered a timeline whose arrays do not match")
        }
        result[FIELD_TIMELINE] = starts.indices.map { i ->
            linkedMapOf(
                SpeechResultKeys.START_MILLIS to SpeechValues.number(starts[i]),
                SpeechResultKeys.END_MILLIS to SpeechValues.number(ends[i]),
                SpeechResultKeys.LABELS to (0 until topK).map { j ->
                    val p = positions[i * topK + j]
                    linkedMapOf(SpeechResultKeys.LABEL to names[p], SpeechResultKeys.MID to mids[p], SpeechResultKeys.SCORE to score(scores[i * topK + j]))
                },
            )
        }
        return result
    }

    private fun numbers(value: Any?): List<Number> = (value as? List<*>).orEmpty().map { it as? Number ?: 0 }

    private fun entries(value: Any?): List<Map<*, *>> = (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()

    /**
     * A Bundle answer of the contract: refuses a missing answer or an unsupported contract version, throws the carried
     * error, otherwise returns the Bundle for its result keys.
     * zh-CN: 契约的 Bundle 应答: 拒绝缺失的应答或不支持的契约版本, 抛出携带的错误, 否则返回 Bundle 以读取结果键.
     */
    fun answer(bundle: Bundle?, label: String): Bundle {
        if (bundle == null) throw SpeechErrorMapper.failure(SpeechErrorCodes.INTERNAL, "$label returned no answer")
        SpeechErrorMapper.fromBundle(bundle)?.let { throw SpeechPluginException(it) }
        val version = bundle.getInt(SpeechContract.KEY_CONTRACT_VERSION, 0)
        if (!SpeechContract.supportsContractVersion(version)) {
            throw SpeechErrorMapper.failure(SpeechErrorCodes.PLUGIN_INCOMPATIBLE, "$label answered with contract version $version")
        }
        return bundle
    }

    /** One warning or error Bundle as the script sees it: `{ code, message }`. zh-CN: 脚本看到的单个警告或错误 Bundle: `{ code, message }`. */
    fun problem(bundle: Bundle?): Map<String, Any?> {
        val error = SpeechErrorMapper.fromBundle(bundle) ?: SpeechErrorMapper.normalize(SpeechErrorCodes.INTERNAL, null)
        return linkedMapOf(FIELD_CODE to error.code, FIELD_MESSAGE to error.message)
    }

    /**
     * A result or event Bundle as plain JVM values; the contract keys `contractVersion`, `errorCode` and `errorMessage`
     * are left out, `warnings` become `{ code, message }` objects, and integral numbers become script-friendly numbers
     * ([SpeechValues.number]).
     * zh-CN: 把结果或事件 Bundle 转为普通 JVM 值; 契约键 `contractVersion`, `errorCode` 与 `errorMessage` 被省略, `warnings` 成为
     * `{ code, message }` 对象, 整数转为适合脚本的数字 ([SpeechValues.number]).
     */
    fun plain(bundle: Bundle?): Map<String, Any?> {
        if (bundle == null) return emptyMap()
        val result = LinkedHashMap<String, Any?>()
        bundle.keySet().sorted().forEach { key ->
            if (key in TRANSPORT_KEYS) return@forEach
            @Suppress("DEPRECATION")
            val value = bundle.get(key)
            result[key] = if (key == SpeechResultKeys.WARNINGS) {
                (value as? List<*>).orEmpty().map { problem(it as? Bundle) }
            } else {
                plainValue(value)
            }
        }
        return result
    }

    /**
     * The script's `SpeechSynthesisResult` from the [plain] completion of `synthesizeToFile`: the published [path], the
     * plugin's `sampleRate`, `durationMillis`, `modelId`, `speakerId` and `warnings` (an empty list when it sent none,
     * like recognition results), and the [size] the host published.
     * zh-CN: 由 `synthesizeToFile` 的 [plain] 完成值构造脚本的 `SpeechSynthesisResult`: 发布后的 [path], 插件给出的 `sampleRate`,
     * `durationMillis`, `modelId`, `speakerId` 与 `warnings` (未给出时为空列表, 与识别结果一致), 以及宿主发布的 [size].
     */
    fun synthesisResult(path: String, plain: Map<String, Any?>, size: Long): Map<String, Any?> = linkedMapOf(
        SpeechPluginHost.FIELD_PATH to path,
        SpeechResultKeys.SAMPLE_RATE to plain[SpeechResultKeys.SAMPLE_RATE],
        SpeechResultKeys.DURATION_MILLIS to plain[SpeechResultKeys.DURATION_MILLIS],
        SpeechResultKeys.MODEL_ID to plain[SpeechResultKeys.MODEL_ID],
        SpeechResultKeys.SPEAKER_ID to plain[SpeechResultKeys.SPEAKER_ID],
        SpeechResultKeys.SIZE_BYTES to SpeechValues.number(size),
        SpeechResultKeys.WARNINGS to (plain[SpeechResultKeys.WARNINGS] as? List<*> ?: emptyList<Any?>()),
    )

    /** One model Bundle as the script's `SpeechModelInfo`. zh-CN: 单个模型 Bundle 转为脚本的 `SpeechModelInfo`. */
    fun model(bundle: Bundle?): Map<String, Any?> = plain(bundle)

    /** The models of a `listModels` answer. zh-CN: `listModels` 应答中的模型. */
    fun models(answer: Bundle): List<Map<String, Any?>> {
        @Suppress("DEPRECATION")
        val list = answer.getParcelableArrayList<Bundle>(SpeechResultKeys.MODELS).orEmpty()
        return list.map(::model)
    }

    /** Whether a plain model value is the default of its kind. zh-CN: 普通模型值是否为其种类的默认模型. */
    fun isDefault(model: Map<String, Any?>): Boolean = model[SpeechModelKeys.IS_DEFAULT] == true

    private fun plainValue(value: Any?): Any? = when (value) {
        null -> null
        is Bundle -> plain(value)
        is String, is Boolean -> value
        is Number -> SpeechValues.number(value)
        is CharSequence -> value.toString()
        is Array<*> -> value.map(::plainValue)
        is IntArray -> value.map { it }
        is LongArray -> value.map { SpeechValues.number(it) }
        is FloatArray -> value.map { it.toDouble() }
        is DoubleArray -> value.toList()
        is BooleanArray -> value.toList()
        is List<*> -> value.map(::plainValue)
        else -> value.toString()
    }

    const val FIELD_CODE = "code"
    const val FIELD_MESSAGE = "message"

    /** `speakers` (a count) and `timeline` of the analysis results, rebuilt by the host (roadmap D147, D149). zh-CN: 分析结果中由宿主还原的 `speakers` (人数) 与 `timeline`. */
    const val FIELD_SPEAKERS = "speakers"
    const val FIELD_TIMELINE = "timeline"

    private val TRANSPORT_KEYS = setOf(SpeechContract.KEY_CONTRACT_VERSION, SpeechContract.KEY_ERROR_CODE, SpeechContract.KEY_ERROR_MESSAGE)
}

/**
 * Number conversion shared by the Bundle decoding and the script values: integral values that fit a script integer stay
 * `Int`, larger ones become `Double`, floats become `Double`, so `typeof` and `===` behave like for any script number.
 * Pure Kotlin.
 * zh-CN: Bundle 解码与脚本值共用的数字转换: 能放进脚本整数的整值保持 `Int`, 更大的转为 `Double`, 浮点转为 `Double`, 因此 `typeof` 与
 * `===` 的行为与普通脚本数字一致. 纯 Kotlin.
 */
object SpeechValues {

    fun number(value: Number): Number = when (value) {
        is Int, is Short, is Byte -> value.toInt()
        is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else value.toDouble()
        else -> value.toDouble()
    }
}
