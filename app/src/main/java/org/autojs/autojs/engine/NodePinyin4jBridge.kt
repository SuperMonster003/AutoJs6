package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.text.Pinyin4jPluginHost
import org.autojs.autojs.runtime.api.modules.pinyin4j.Pinyin4jOptions
import org.json.JSONArray
import org.json.JSONObject

interface NodePinyin4jBackend {
    fun of(text: String, options: Bundle): String
    fun asTone(text: String): String
    fun readings(text: String, options: Bundle): List<List<String>>
}

internal class AndroidNodePinyin4jBackend(context: Context) : NodePinyin4jBackend {
    private val appContext = context.applicationContext

    override fun of(text: String, options: Bundle): String = runBlocking {
        Pinyin4jPluginHost.of(appContext, text, options)
    }

    override fun asTone(text: String): String = runBlocking {
        Pinyin4jPluginHost.asTone(appContext, text)
    }

    override fun readings(text: String, options: Bundle): List<List<String>> = runBlocking {
        Pinyin4jPluginHost.readings(appContext, text, options)
    }
}

internal object NodePinyin4jBridge {

    fun dispatch(request: NodeBridgeRequest, backend: NodePinyin4jBackend): NodeBridgeResponse {
        return try {
            val args = JSONArray(request.argsJson.ifBlank { "[]" })
            when (request.method) {
                "of" -> {
                    requireArgumentCount(args, 1..2, request.method)
                    NodeBridgeResponse.success(
                        request.id,
                        jsonValue(backend.of(requiredString(args, 0, "source"), args.toOfOptions())),
                    )
                }
                "as" -> {
                    requireArgumentCount(args, 1..1, request.method)
                    NodeBridgeResponse.success(
                        request.id,
                        jsonValue(backend.asTone(requiredString(args, 0, "source"))),
                    )
                }
                "readings" -> {
                    requireArgumentCount(args, 1..2, request.method)
                    val options = optionalObject(args, 1, "options")
                    NodeBridgeResponse.success(
                        request.id,
                        matrixJson(
                            backend.readings(
                                requiredString(args, 0, "source"),
                                options.toReadingsOptions(),
                            ),
                        ),
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
                    message = error.message ?: "AutoJs6 Pinyin4j request is invalid.",
                    module = request.module,
                    method = request.method,
                ),
            )
        } catch (error: Throwable) {
            NodeBridgeResponse.failure(
                request.id,
                NodeBridgeError.providerFailed(
                    message = error.message ?: "AutoJs6 Pinyin4j provider failed.",
                    module = request.module,
                    method = request.method,
                    code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
                ),
            )
        }
    }

    private fun JSONArray.toOfOptions(): Bundle {
        if (length() < 2 || isNull(1)) return Pinyin4jOptions.ofBundle()
        return when (val raw = opt(1)) {
            is String -> Pinyin4jOptions.ofBundle(separator = raw)
            is JSONObject -> raw.toOfOptions()
            else -> throw IllegalArgumentException("pinyin4j options must be a string or object")
        }
    }

    private fun JSONObject.toOfOptions(): Bundle {
        return Pinyin4jOptions.ofBundle(
            separator = optionalString("separator", "sep") ?: Pinyin4jOptions.DEFAULT_SEPARATOR,
            caseType = optionalScalarString("case", "caseType"),
            toneType = optionalScalarString("tone", "toneType"),
            vCharType = optionalScalarString("v", "vChar", "vCharType"),
        )
    }

    private fun JSONObject?.toReadingsOptions(): Bundle {
        val source = this
        return Pinyin4jOptions.readingsBundle(
            romanization = source?.optionalString("romanization", "system"),
            caseType = source?.optionalScalarString("case", "caseType"),
            toneType = source?.optionalScalarString("tone", "toneType"),
            vCharType = source?.optionalScalarString("v", "vChar", "vCharType"),
            hasExplicitCaseType = source.hasNonNull("case", "caseType"),
            hasExplicitToneType = source.hasNonNull("tone", "toneType"),
            hasExplicitVCharType = source.hasNonNull("v", "vChar", "vCharType"),
        )
    }

    private fun JSONObject.optionalString(vararg names: String): String? {
        names.forEach { name ->
            if (has(name) && !isNull(name)) {
                return opt(name) as? String
                    ?: throw IllegalArgumentException("pinyin4j option $name must be a string")
            }
        }
        return null
    }

    private fun JSONObject.optionalScalarString(vararg names: String): String? {
        names.forEach { name ->
            if (!has(name) || isNull(name)) return@forEach
            val value: Any? = opt(name)
            return when (value) {
                is String -> value
                is Boolean -> value.toString()
                is Number -> value.toDouble().also {
                    require(it.isFinite()) { "pinyin4j option $name must be finite" }
                }.let { value.toString() }
                else -> throw IllegalArgumentException("pinyin4j option $name must be a string, number, or boolean")
            }
        }
        return null
    }

    private fun JSONObject?.hasNonNull(vararg names: String): Boolean {
        val source = this ?: return false
        return names.any { name -> source.has(name) && !source.isNull(name) }
    }

    private fun optionalObject(args: JSONArray, index: Int, name: String): JSONObject? {
        if (index >= args.length() || args.isNull(index)) return null
        return args.optJSONObject(index)
            ?: throw IllegalArgumentException("pinyin4j $name must be an object")
    }

    private fun requiredString(args: JSONArray, index: Int, name: String): String {
        return args.opt(index) as? String
            ?: throw IllegalArgumentException("pinyin4j $name must be a string")
    }

    private fun requireArgumentCount(args: JSONArray, expected: IntRange, method: String) {
        require(args.length() in expected) {
            val count = if (expected.first == expected.last) expected.first.toString() else "${expected.first}..${expected.last}"
            "pinyin4j.$method expects $count arguments"
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
