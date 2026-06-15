package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NodeBridgeLimits {

    const val RUNTIME_MODULE_NAME = "autojs6:bridge-limits"

    private const val PROJECT_JSON = "project.json"
    private const val PACKAGE_JSON = "package.json"

    private val fields = linkedMapOf(
        "maxPendingBridgeCalls" to LimitField(defaultValue = 32, minValue = 1, hardMax = 128),
        "maxImageHandles" to LimitField(defaultValue = 32, minValue = 0, hardMax = 128),
        "maxShellCommands" to LimitField(defaultValue = 2, minValue = 0, hardMax = 4),
        "maxNetworkRequests" to LimitField(defaultValue = 4, minValue = 0, hardMax = 8),
        "maxWebSocketConnections" to LimitField(defaultValue = 2, minValue = 0, hardMax = 4),
        "accessibilityQueriesPerSecond" to LimitField(defaultValue = 20, minValue = 1, hardMax = 60),
    )

    val defaults = Policy()

    fun runtimeModuleSourceForWorkingDirectory(workingDirectory: String?): String =
        fromWorkingDirectory(workingDirectory).toJson()

    fun fromRuntimeModuleOrWorkingDirectory(runtimeModuleSource: String?, workingDirectory: String?): Policy {
        val runtime = runtimeModuleSource
            ?.takeIf { it.isNotBlank() }
            ?.let { fromRuntimeModuleSource(it) }
        return runtime ?: fromWorkingDirectory(workingDirectory)
    }

    fun fromWorkingDirectory(workingDirectory: String?): Policy {
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            ?: return defaults
        return fromJsonSources(
            projectJsonText = readTextIfFile(File(root, PROJECT_JSON)),
            packageJsonText = readTextIfFile(File(root, PACKAGE_JSON)),
        )
    }

    fun fromJsonSources(projectJsonText: String?, packageJsonText: String?): Policy {
        val builder = Builder()
        val warnings = mutableListOf<String>()
        projectJsonText?.let { text ->
            parseJsonObject(text, PROJECT_JSON, warnings)?.let { json ->
                collectLimitObject(json.optJSONObject("node")?.optJSONObject("bridgeLimits"), "project.json:node.bridgeLimits", builder)
                collectLimitObject(json.optJSONObject("node")?.optJSONObject("limits"), "project.json:node.limits", builder)
            }
        }
        packageJsonText?.let { text ->
            parseJsonObject(text, PACKAGE_JSON, warnings)?.let { json ->
                val autojs6 = json.optJSONObject("autojs6")
                collectLimitObject(autojs6?.optJSONObject("bridgeLimits"), "package.json:autojs6.bridgeLimits", builder)
                collectLimitObject(autojs6?.optJSONObject("node")?.optJSONObject("bridgeLimits"), "package.json:autojs6.node.bridgeLimits", builder)
                collectLimitObject(autojs6?.optJSONObject("node")?.optJSONObject("limits"), "package.json:autojs6.node.limits", builder)
            }
        }
        return builder.build(warnings)
    }

    fun fromRuntimeModuleSource(text: String): Policy? {
        val warnings = mutableListOf<String>()
        val json = parseJsonObject(text, RUNTIME_MODULE_NAME, warnings) ?: return null
        val builder = Builder()
        collectLimitObject(json, RUNTIME_MODULE_NAME, builder)
        return builder.build(warnings + stringArray(json.optJSONArray("warnings")))
    }

    private fun collectLimitObject(json: JSONObject?, source: String, builder: Builder) {
        if (json == null) {
            return
        }
        var consumed = false
        fields.forEach { (name, field) ->
            if (!json.has(name) || json.isNull(name)) {
                return@forEach
            }
            consumed = true
            builder.set(name, field, json.opt(name), source)
        }
        if (consumed) {
            builder.addSource(source)
        }
    }

    private fun parseJsonObject(text: String, source: String, warnings: MutableList<String>): JSONObject? {
        return runCatching { JSONObject(text) }
            .onFailure { error ->
                warnings += "$source bridge limits could not be parsed: ${error.message ?: error.javaClass.name}"
            }
            .getOrNull()
    }

    private fun readTextIfFile(file: File): String? {
        return runCatching {
            file.takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun stringArray(value: JSONArray?): List<String> {
        if (value == null) {
            return emptyList()
        }
        return (0 until value.length()).mapNotNull { index ->
            value.optString(index).takeIf { it.isNotBlank() }
        }
    }

    private data class LimitField(
        val defaultValue: Int,
        val minValue: Int,
        val hardMax: Int,
    )

    private class Builder {
        private val values = fields.mapValues { it.value.defaultValue }.toMutableMap()
        private val sources = mutableListOf<String>()
        private val warnings = mutableListOf<String>()

        fun addSource(source: String) {
            if (source !in sources) {
                sources += source
            }
        }

        fun set(name: String, field: LimitField, rawValue: Any?, source: String) {
            val parsed = when (rawValue) {
                is Int -> rawValue
                is Long -> rawValue.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                is Number -> {
                    val doubleValue = rawValue.toDouble()
                    if (doubleValue.isFinite()) doubleValue.toInt() else null
                }
                is String -> rawValue.trim().toIntOrNull()
                else -> null
            }
            if (parsed == null) {
                warnings += "$source.$name ignored: expected an integer."
                return
            }
            if (parsed < field.minValue || parsed > field.hardMax) {
                warnings += "$source.$name ignored: $parsed is outside ${field.minValue}..${field.hardMax}."
                return
            }
            values[name] = parsed
        }

        fun build(extraWarnings: List<String>): Policy {
            return Policy(
                maxPendingBridgeCalls = values.getValue("maxPendingBridgeCalls"),
                maxImageHandles = values.getValue("maxImageHandles"),
                maxShellCommands = values.getValue("maxShellCommands"),
                maxNetworkRequests = values.getValue("maxNetworkRequests"),
                maxWebSocketConnections = values.getValue("maxWebSocketConnections"),
                accessibilityQueriesPerSecond = values.getValue("accessibilityQueriesPerSecond"),
                sources = sources.ifEmpty { listOf("default") },
                warnings = (warnings + extraWarnings).distinct(),
            )
        }
    }

    data class Policy(
        val maxPendingBridgeCalls: Int = fields.getValue("maxPendingBridgeCalls").defaultValue,
        val maxImageHandles: Int = fields.getValue("maxImageHandles").defaultValue,
        val maxShellCommands: Int = fields.getValue("maxShellCommands").defaultValue,
        val maxNetworkRequests: Int = fields.getValue("maxNetworkRequests").defaultValue,
        val maxWebSocketConnections: Int = fields.getValue("maxWebSocketConnections").defaultValue,
        val accessibilityQueriesPerSecond: Int = fields.getValue("accessibilityQueriesPerSecond").defaultValue,
        val sources: List<String> = listOf("default"),
        val warnings: List<String> = emptyList(),
    ) {
        fun toJson(): String = toJsonObject().toString()

        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("version", 1)
            put("maxPendingBridgeCalls", maxPendingBridgeCalls)
            put("maxImageHandles", maxImageHandles)
            put("maxShellCommands", maxShellCommands)
            put("maxNetworkRequests", maxNetworkRequests)
            put("maxWebSocketConnections", maxWebSocketConnections)
            put("accessibilityQueriesPerSecond", accessibilityQueriesPerSecond)
            put("sources", JSONArray(sources))
            put("warnings", JSONArray(warnings))
            put("defaults", JSONObject().apply {
                fields.forEach { (name, field) -> put(name, field.defaultValue) }
            })
            put("hardMax", JSONObject().apply {
                fields.forEach { (name, field) -> put(name, field.hardMax) }
            })
        }

        fun nativeDiagnostics(): Map<String, String> = linkedMapOf(
            "embedded_script.bridge_limits.max_pending_bridge_calls" to maxPendingBridgeCalls.toString(),
            "embedded_script.bridge_limits.max_image_handles" to maxImageHandles.toString(),
            "embedded_script.bridge_limits.max_shell_commands" to maxShellCommands.toString(),
            "embedded_script.bridge_limits.max_network_requests" to maxNetworkRequests.toString(),
            "embedded_script.bridge_limits.max_websocket_connections" to maxWebSocketConnections.toString(),
            "embedded_script.bridge_limits.accessibility_queries_per_second" to accessibilityQueriesPerSecond.toString(),
            "embedded_script.bridge_limits.sources" to sources.joinToString(","),
            "embedded_script.bridge_limits.warning_count" to warnings.size.toString(),
        )

        fun valueForResourceType(type: NodeBridgeResourceType): Int? {
            return when (type) {
                NodeBridgeResourceType.IMAGE_HANDLE -> maxImageHandles
                NodeBridgeResourceType.SCREEN_CAPTURER -> maxImageHandles
                NodeBridgeResourceType.WEBSOCKET_CONNECTION -> maxWebSocketConnections
                NodeBridgeResourceType.SHELL_EXECUTION -> maxShellCommands
                NodeBridgeResourceType.NETWORK_CALL -> maxNetworkRequests
                else -> null
            }
        }
    }
}
