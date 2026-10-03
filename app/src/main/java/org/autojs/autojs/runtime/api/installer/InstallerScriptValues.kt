package org.autojs.autojs.runtime.api.installer

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.autojs.autojs.core.plugin.installer.InstallerError
import org.autojs.autojs.core.plugin.installer.InstallerJson
import org.autojs.autojs.core.plugin.installer.InstallerPluginException
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes

/** Plain snapshots for the script thread. Icon decoding and Rhino object creation happen there. */
object InstallerScriptValues {

    fun parse(json: String?): JsonElement {
        if (json == null) return JsonNull.INSTANCE
        if (json.isBlank()) throw invalid("installer result document is empty")
        if (json.toByteArray(Charsets.UTF_8).size > InstallerContract.MAX_JSON_BYTES) {
            throw invalid("installer result document exceeds ${InstallerContract.MAX_JSON_BYTES} UTF-8 bytes")
        }
        return try {
            JsonParser.parseString(json)
        } catch (e: RuntimeException) {
            throw invalid("installer result document is not valid JSON", e)
        }
    }

    fun toPlain(json: String?): Any? = toPlain(parse(json))

    fun json(json: String): Any? = toPlain(json)

    fun toPlain(element: JsonElement?): Any? = plain(element, 0)

    /** The script argument, rather than the number of returned items, determines the shape. */
    fun completion(value: InstallerJson.Completion, isBatch: Boolean): Any =
        if (isBatch) value.results.map(::installResult) else {
            if (value.results.size != 1) throw invalid("installer returned multiple results for one application")
            installResult(value.single)
        }

    fun installResult(value: InstallerJson.InstallResult): Map<String, Any?> = document(value.json).apply {
        put("ok", value.ok)
        value.packageName?.let { put("packageName", it) }
        value.versionName?.let { put("versionName", it) }
        value.versionCode?.let { put("versionCode", it) }
        value.previousVersionCode?.let { put("previousVersionCode", it) }
        value.authorizer?.let { put("authorizer", it) }
        value.interaction?.let { put("interaction", it) }
        value.durationMillis?.let { put("durationMillis", it) }
        value.sourceDeleted?.let { put("sourceDeleted", it) }
        value.sourceDeleteRequested?.let { put("sourceDeleteRequested", it) } ?: remove("sourceDeleteRequested")
        if (value.notes.isNotEmpty() || containsKey("notes")) put("notes", value.notes)
        value.error?.let { put("error", mergeError(get("error"), it)) }
    }

    fun uninstallResult(value: InstallerJson.UninstallResult): Map<String, Any?> = document(value.json).apply {
        put("ok", value.ok)
        value.packageName?.let { put("packageName", it) }
        value.authorizer?.let { put("authorizer", it) }
        value.error?.let { put("error", mergeError(get("error"), it)) }
    }

    fun error(value: InstallerError): Map<String, Any?> = linkedMapOf<String, Any?>(
        "name" to "InstallerError",
        "code" to value.code,
        "message" to value.message,
        "retryable" to value.retryable,
    ).apply {
        value.status?.let { put("status", it.toLong()) }
        value.systemMessage?.let { put("systemMessage", it) }
        value.packageName?.let { put("packageName", it) }
        value.authorizer?.let { put("authorizer", it) }
    }

    fun authorizer(value: InstallerJson.AuthorizerState, name: String): Map<String, Any?> = document(value.json).apply {
        put("name", name)
        put("available", value.available)
        put("running", value.running)
        put("granted", value.granted)
    }

    fun users(values: List<InstallerJson.DeviceUser>): List<Map<String, Any?>> = values.map { value ->
        linkedMapOf<String, Any?>(
            "id" to value.id.toLong(),
            "primary" to value.primary,
            "running" to value.running,
        ).apply { value.name?.let { put("name", it) } }
    }

    private fun mergeError(original: Any?, value: InstallerError): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        if (original is Map<*, *>) original.forEach { (key, item) -> if (key is String) put(key, item) }
        putAll(error(value))
    }

    private fun document(json: String): LinkedHashMap<String, Any?> {
        val element = parse(json)
        if (!element.isJsonObject) throw invalid("installer result document must be an object")
        return LinkedHashMap<String, Any?>().apply {
            element.asJsonObject.entrySet().forEach { (key, value) -> put(key, plain(value, 1)) }
        }
    }

    private fun plain(element: JsonElement?, depth: Int): Any? {
        if (depth > 64) throw invalid("installer result document is nested too deeply")
        return when {
            element == null || element.isJsonNull -> null
            element.isJsonObject -> LinkedHashMap<String, Any?>().apply {
                element.asJsonObject.entrySet().forEach { (key, value) -> put(key, plain(value, depth + 1)) }
            }
            element.isJsonArray -> element.asJsonArray.map { plain(it, depth + 1) }
            else -> primitive(element.asJsonPrimitive)
        }
    }

    private fun primitive(value: JsonPrimitive): Any = when {
        value.isBoolean -> value.asBoolean
        value.isNumber -> {
            val decimal = try {
                value.asBigDecimal
            } catch (e: NumberFormatException) {
                throw invalid("installer result contains a non-finite number", e)
            }
            if (decimal.stripTrailingZeros().scale() <= 0) {
                try {
                    decimal.longValueExact()
                } catch (e: ArithmeticException) {
                    throw invalid("installer result integer is outside the Long range", e)
                }
            } else {
                decimal.toDouble().also { if (!it.isFinite()) throw invalid("installer result contains a non-finite number") }
            }
        }
        else -> value.asString
    }

    private fun invalid(message: String, cause: Throwable? = null): InstallerPluginException =
        InstallerPluginException(InstallerError(InstallerErrorCodes.INTERNAL, message, retryable = false), cause)
}
