package org.autojs.autojs.core.plugin.speech

import android.os.Bundle
import org.autojs.plugin.speech.api.SpeechContract
import org.autojs.plugin.speech.api.SpeechErrorCodes
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

    /** The Bundle every request carries, stamped with the contract version; [build] adds the request keys. zh-CN: 每个请求携带的 Bundle, 带契约版本; [build] 添加请求键. */
    fun request(build: Bundle.() -> Unit = {}): Bundle = Bundle().apply {
        putInt(SpeechContract.KEY_CONTRACT_VERSION, SpeechContract.CONTRACT_VERSION)
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
