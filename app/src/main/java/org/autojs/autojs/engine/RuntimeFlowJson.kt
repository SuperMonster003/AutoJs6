package org.autojs.autojs.engine

import org.autojs.autojs.core.automator.flow.FlowError
import org.json.JSONArray
import org.json.JSONObject

/** The portable wire contains values and callback references, never Java objects. */
internal object RuntimeFlowJson {
    fun nullable(value: Any?): Any? = value?.takeUnless { it === JSONObject.NULL }
    fun encode(value: Any?): Any = when (value) {
        null, JSONObject.NULL -> JSONObject.NULL
        is RuntimeFlowNode -> JSONObject().put("__flowNode", RuntimeFlowQuery.snapshot(value.value))
        is Map<*, *> -> JSONObject().also { out -> value.forEach { (k, v) -> out.put(k.toString(), encode(v)) } }
        is Iterable<*> -> JSONArray().also { out -> value.forEach { out.put(encode(it)) } }
        is Number -> if (value.toDouble().isFinite()) value else JSONObject().put("__flowNumber", value.toString())
        is String, is Boolean, is JSONObject, is JSONArray -> value
        else -> throw IllegalArgumentException("Unsupported Flow result: ${value.javaClass.simpleName}")
    }
    fun list(value: Any?): List<Any?> {
        require(value is JSONArray) { "Expected an array" }
        return (0 until value.length()).map { nullable(value.get(it)) }
    }
    fun plain(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> list(value).map(::plain)
        else -> nullable(value)
    }
    fun callback(value: Any?): String? = (value as? JSONObject)?.optString("__flowCallback")?.takeIf { it.isNotEmpty() }
    fun truthy(value: Any?, wait: Boolean = false): Boolean = when (val v = nullable(value)) {
        null, false -> false
        is Number -> if (wait) !v.toDouble().isNaN() else v.toDouble() != 0.0 && !v.toDouble().isNaN()
        is String -> wait || v.isNotEmpty()
        is JSONObject -> if (v.optString("__flowNumber") == "NaN") false else true
        else -> true
    }
    fun millis(value: Any?, default: Long, name: String): Long {
        if (value == null || value === JSONObject.NULL) return default
        if (value is JSONObject && value.optString("__flowNumber") == "Infinity") return Long.MAX_VALUE
        require(value is Number && value.toDouble().isFinite() && value.toDouble() >= 0 && value.toDouble() < Long.MAX_VALUE.toDouble()) { "$name must be a non-negative duration or Infinity" }
        return value.toLong()
    }
    fun error(error: Throwable): JSONObject = JSONObject().put("name", if (error is FlowError) "FlowError" else "Error")
        .put("message", error.message ?: error.javaClass.simpleName).also { out ->
            if (error is FlowError) {
                out.put("code", error.code.name).put("step", error.step).put("selector", error.selector ?: JSONObject.NULL)
                    .put("elapsed", error.elapsed).put("attempts", error.attempts).put("reason", error.reason ?: JSONObject.NULL)
            }
            if (error is RuntimeFlowCallbackError) out.put("callbackError", error.token)
        }
}

internal class RuntimeFlowCallbackError(val token: String) : RuntimeException("Flow callback failed")
