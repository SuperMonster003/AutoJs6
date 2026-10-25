package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.text.PinyinPluginHost
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinCustomDictionary
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions
import org.autojs.autojs.runtime.api.modules.pinyin.PinyinOptions.PinyinStyle
import org.autojs.autojs.runtime.api.modules.pinyin.Util
import org.autojs.plugin.pinyin.api.PinyinOptionKeys
import org.json.JSONArray
import org.json.JSONObject

interface NodePinyinBackend {
    fun convert(text: String, options: Bundle): String
    fun simple(text: String, enableNumericTone: Boolean, enableSegment: Boolean): String
    fun fromCodePoint(codePoint: Int): String?
    fun fromPhrase(phrase: String): String
}

internal class AndroidNodePinyinBackend(context: Context) : NodePinyinBackend {
    private val appContext = context.applicationContext

    override fun convert(text: String, options: Bundle): String = runBlocking {
        PinyinPluginHost.convert(appContext, text, options)
    }

    override fun simple(text: String, enableNumericTone: Boolean, enableSegment: Boolean): String = runBlocking {
        PinyinPluginHost.simple(appContext, text, enableNumericTone, enableSegment)
    }

    override fun fromCodePoint(codePoint: Int): String? = runBlocking {
        PinyinPluginHost.fromCodePoint(appContext, codePoint)
    }

    override fun fromPhrase(phrase: String): String = runBlocking {
        PinyinPluginHost.fromPhrase(appContext, phrase)
    }
}

internal object NodePinyinBridge {

    fun dispatch(request: NodeBridgeRequest, backend: NodePinyinBackend): NodeBridgeResponse {
        return try {
            val args = JSONArray(request.argsJson.ifBlank { "[]" })
            when (request.method) {
                "convert" -> {
                    requireArgumentCount(args, 1..2, request.method)
                    val text = requiredString(args, 0, "text")
                    val options = optionalObject(args, 1, "options")
                    NodeBridgeResponse.success(
                        request.id,
                        normalizeMatrixJson(backend.convert(text, options.toPluginOptions())),
                    )
                }
                "simple" -> {
                    requireArgumentCount(args, 1..3, request.method)
                    NodeBridgeResponse.success(
                        request.id,
                        jsonValue(
                            backend.simple(
                                requiredString(args, 0, "text"),
                                optionalBoolean(args, 1, "enableNumericTone", false),
                                optionalBoolean(args, 2, "enableSegment", false),
                            ),
                        ),
                    )
                }
                "compare" -> {
                    requireArgumentCount(args, 2..2, request.method)
                    val options = Bundle().apply {
                        putInt(PinyinOptionKeys.STYLE, PinyinStyle.TONE.value)
                    }
                    val left = parseStringMatrix(JSONArray(backend.convert(requiredString(args, 0, "hanA"), options)))
                    val right = parseStringMatrix(JSONArray(backend.convert(requiredString(args, 1, "hanB"), options)))
                    NodeBridgeResponse.success(request.id, Util.compare(left, right).toString())
                }
                "compact" -> {
                    requireArgumentCount(args, 1..1, request.method)
                    val matrix = args.optJSONArray(0)
                        ?: throw IllegalArgumentException("pinyin.compact matrix must be a two-dimensional array")
                    NodeBridgeResponse.success(request.id, matrixJson(Util.compact(parseStringMatrix(matrix))))
                }
                "fromCodePoint" -> {
                    requireArgumentCount(args, 1..1, request.method)
                    val raw = args.opt(0) as? Number
                        ?: throw IllegalArgumentException("pinyin.fromCodePoint codePoint must be an integer")
                    val value = raw.toDouble()
                    require(value.isFinite() && value % 1.0 == 0.0 && value in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
                        "pinyin.fromCodePoint codePoint must be an integer"
                    }
                    NodeBridgeResponse.success(request.id, jsonValue(backend.fromCodePoint(value.toInt())))
                }
                "fromPhrase" -> {
                    requireArgumentCount(args, 1..1, request.method)
                    NodeBridgeResponse.success(
                        request.id,
                        normalizeMatrixJson(backend.fromPhrase(requiredString(args, 0, "phrase"))),
                    )
                }
                else -> NodeBridgeResponse.failure(
                    request.id,
                    NodeBridgeError.bridgeUnavailable(request.module, request.method),
                )
            }
        } catch (error: IllegalArgumentException) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.invalidRequest(
                    message = error.message ?: "AutoJs6 Pinyin request is invalid.",
                    module = request.module,
                    method = request.method,
                ),
            )
        } catch (error: Throwable) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.providerFailed(
                    message = error.message ?: "AutoJs6 Pinyin provider failed.",
                    module = request.module,
                    method = request.method,
                    code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                ),
            )
        }
    }

    private fun JSONObject?.toPluginOptions(): Bundle {
        val source = this
        return Bundle().apply {
            if (source == null) return@apply
            source.optionalValue("mode")?.let { raw ->
                putInt(PinyinOptionKeys.MODE, PinyinOptions.parseMode(raw) ?: return@let)
            }
            source.optionalValue("style")?.let { raw ->
                putInt(PinyinOptionKeys.STYLE, PinyinOptions.parseStyle(raw) ?: return@let)
            }
            source.optionalBoolean("segment")?.let { putBoolean(PinyinOptionKeys.SEGMENT, it) }
            source.optionalBoolean("heteronym")?.let { putBoolean(PinyinOptionKeys.HETERONYM, it) }
            source.optionalBoolean("group")?.let { putBoolean(PinyinOptionKeys.GROUP, it) }
            if (source.has("customDictionary") && !source.isNull("customDictionary")) {
                val dictionary = source.optJSONObject("customDictionary")
                    ?: throw IllegalArgumentException("pinyin customDictionary must be a plain object")
                PinyinCustomDictionary.encodeJson(dictionary)
                    ?.let { putString(PinyinOptionKeys.CUSTOM_DICTIONARY_JSON, it) }
            }
        }
    }

    private fun JSONObject.optionalValue(name: String): Any? {
        return if (has(name) && !isNull(name)) opt(name) else null
    }

    private fun JSONObject.optionalBoolean(name: String): Boolean? {
        if (!has(name) || isNull(name)) return null
        return opt(name) as? Boolean
            ?: throw IllegalArgumentException("pinyin option $name must be a boolean")
    }

    private fun optionalObject(args: JSONArray, index: Int, name: String): JSONObject? {
        if (index >= args.length() || args.isNull(index)) return null
        return args.optJSONObject(index)
            ?: throw IllegalArgumentException("pinyin $name must be an object")
    }

    private fun optionalBoolean(args: JSONArray, index: Int, name: String, defaultValue: Boolean): Boolean {
        if (index >= args.length() || args.isNull(index)) return defaultValue
        return args.opt(index) as? Boolean
            ?: throw IllegalArgumentException("pinyin $name must be a boolean")
    }

    private fun requiredString(args: JSONArray, index: Int, name: String): String {
        return args.opt(index) as? String
            ?: throw IllegalArgumentException("pinyin $name must be a string")
    }

    private fun requireArgumentCount(args: JSONArray, expected: IntRange, method: String) {
        require(args.length() in expected) {
            val count = if (expected.first == expected.last) expected.first.toString() else "${expected.first}..${expected.last}"
            "pinyin.$method expects $count arguments"
        }
    }

    private fun normalizeMatrixJson(json: String): String = matrixJson(parseStringMatrix(JSONArray(json)))

    private fun parseStringMatrix(matrix: JSONArray): List<List<String>> {
        return (0 until matrix.length()).map { rowIndex ->
            val row = matrix.optJSONArray(rowIndex)
                ?: throw IllegalArgumentException("pinyin matrix row $rowIndex must be an array")
            (0 until row.length()).map { itemIndex ->
                row.opt(itemIndex) as? String
                    ?: throw IllegalArgumentException("pinyin matrix item [$rowIndex][$itemIndex] must be a string")
            }
        }
    }

    private fun matrixJson(matrix: List<List<String>>): String = JSONArray().apply {
        matrix.forEach { row -> put(JSONArray(row)) }
    }.toString()

    private fun jsonValue(value: Any?): String {
        return if (value == null) {
            "null"
        } else {
            JSONArray().put(value).toString().let { wrapped -> wrapped.substring(1, wrapped.length - 1) }
        }
    }
}
