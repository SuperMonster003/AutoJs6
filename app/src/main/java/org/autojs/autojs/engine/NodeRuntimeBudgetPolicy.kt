package org.autojs.autojs.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object NodeRuntimeBudgetPolicy {

    const val RUNTIME_MODULE_NAME = "autojs6:runtime-budget"
    const val STATUS_WITHIN_BUDGET = "within_budget"
    const val STATUS_BREACHED = "breached"
    const val HEARTBEAT_HEALTHY = "healthy"
    const val HEARTBEAT_STALE = "stale"
    const val EVENT_LOOP_OBSERVED = "observed_via_bridge_response"
    const val EVENT_LOOP_NOT_OBSERVED = "not_observed"
    const val EVENT_LOOP_STALE = "stale_pending_bridge"

    private const val PROJECT_JSON = "project.json"
    private const val PACKAGE_JSON = "package.json"
    private const val DEFAULT_HEARTBEAT_INTERVAL_MS = 1_000L
    private const val DEFAULT_HEARTBEAT_STALE_MS = 5_000L
    private const val MAX_HEARTBEAT_INTERVAL_MS = 60_000L
    private const val MAX_HEARTBEAT_STALE_MS = 300_000L
    private const val DEFAULT_ONE_SHOT_MEMORY_SOFT_LIMIT_BYTES = 128L * 1024L * 1024L
    private const val DEFAULT_INTERACTIVE_MEMORY_SOFT_LIMIT_BYTES = 256L * 1024L * 1024L
    private const val HARD_MEMORY_SOFT_LIMIT_BYTES = 512L * 1024L * 1024L
    private const val HARD_EXECUTION_DURATION_MS = 24L * 60L * 60L * 1000L
    private const val DEFAULT_MAX_SENSORS = 4
    private const val DEFAULT_MAX_UI_HANDLES = 4
    private const val HARD_MAX_WORKERS = 4

    fun fromRuntimeModuleOrWorkingDirectory(
        runtimeModuleSource: String?,
        workingDirectory: String?,
        executionMode: String,
        timeoutMs: Long,
        bridgeLimits: NodeBridgeLimits.Policy,
    ): Policy {
        val defaults = defaultsFor(executionMode, timeoutMs, bridgeLimits)
        val runtime = runtimeModuleSource
            ?.takeIf { it.isNotBlank() }
            ?.let { fromRuntimeModuleSource(it, defaults) }
        return runtime ?: fromWorkingDirectory(workingDirectory, defaults)
    }

    fun fromWorkingDirectory(
        workingDirectory: String?,
        defaults: Policy,
    ): Policy {
        val root = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).canonicalFile }.getOrNull() }
            ?: return defaults
        return fromJsonSources(
            projectJsonText = readTextIfFile(File(root, PROJECT_JSON)),
            packageJsonText = readTextIfFile(File(root, PACKAGE_JSON)),
            defaults = defaults,
        )
    }

    fun fromJsonSources(
        projectJsonText: String?,
        packageJsonText: String?,
        defaults: Policy,
    ): Policy {
        val builder = Builder(defaults)
        val warnings = mutableListOf<String>()
        projectJsonText?.let { text ->
            parseJsonObject(text, PROJECT_JSON, warnings)?.let { json ->
                collectLimitObject(json.optJSONObject("node")?.optJSONObject("runtimeBudget"), "project.json:node.runtimeBudget", builder)
            }
        }
        packageJsonText?.let { text ->
            parseJsonObject(text, PACKAGE_JSON, warnings)?.let { json ->
                val autojs6 = json.optJSONObject("autojs6")
                collectLimitObject(autojs6?.optJSONObject("runtimeBudget"), "package.json:autojs6.runtimeBudget", builder)
                collectLimitObject(autojs6?.optJSONObject("node")?.optJSONObject("runtimeBudget"), "package.json:autojs6.node.runtimeBudget", builder)
            }
        }
        return builder.build(warnings)
    }

    fun fromRuntimeModuleSource(text: String, defaults: Policy): Policy? {
        val warnings = mutableListOf<String>()
        val json = parseJsonObject(text, RUNTIME_MODULE_NAME, warnings) ?: return null
        val builder = Builder(defaults)
        collectLimitObject(json, RUNTIME_MODULE_NAME, builder)
        return builder.build(warnings + stringArray(json.optJSONArray("warnings")))
    }

    fun completionNativeValues(policy: Policy, nativeValues: Map<String, String>, elapsedMs: Long): Map<String, String> {
        val usage = resourceUsage(policy, nativeValues, elapsedMs)
        return linkedMapOf<String, String>().apply {
            putAll(policy.nativeDiagnostics())
            put("embedded_script.runtime_budget.status", usage.getString("status"))
            put("embedded_script.runtime_budget.within_budget", usage.getBoolean("withinBudget").toString())
            put("embedded_script.runtime_budget.breach_count", usage.getJSONArray("breaches").length().toString())
            put("embedded_script.runtime_budget.usage_json", usage.toString())
        }
    }

    fun heartbeatSnapshot(
        policy: Policy,
        nativeValues: Map<String, String>,
        executionId: String,
        executionMode: String,
        lifecycleState: String,
        sequence: Int,
        startedAtEpochMs: Long,
        nowEpochMs: Long,
    ): JSONObject {
        val lastBridgeRequestAt = nativeValues.longValue("embedded_script.bridge_live_dispatch_last_request_at_epoch_ms")
        val lastBridgeResponseAt = nativeValues.longValue("embedded_script.bridge_live_dispatch_last_response_at_epoch_ms")
        val pendingBridge = nativeValues.intValue("embedded_script.bridge_live_dispatch_pending_count")
        val lastJsProgressAt = listOf(lastBridgeResponseAt, lastBridgeRequestAt, startedAtEpochMs)
            .filter { it > 0L }
            .maxOrNull() ?: 0L
        val eventLoopResponsiveness = when {
            pendingBridge > 0 && lastBridgeResponseAt <= 0L && lastBridgeRequestAt > 0L && nowEpochMs - lastBridgeRequestAt > policy.heartbeatStaleMs -> EVENT_LOOP_STALE
            pendingBridge > 0 && lastBridgeResponseAt > 0L && nowEpochMs - lastBridgeResponseAt > policy.heartbeatStaleMs -> EVENT_LOOP_STALE
            lastBridgeResponseAt > 0L -> EVENT_LOOP_OBSERVED
            else -> EVENT_LOOP_NOT_OBSERVED
        }
        val heartbeatStatus = if (eventLoopResponsiveness == EVENT_LOOP_STALE) HEARTBEAT_STALE else HEARTBEAT_HEALTHY
        val usage = resourceUsage(
            policy = policy,
            nativeValues = nativeValues,
            elapsedMs = (nowEpochMs - startedAtEpochMs).coerceAtLeast(0L),
        )
        return JSONObject().apply {
            put("schemaVersion", 1)
            put("executionId", executionId)
            put("executionMode", executionMode)
            put("lifecycleState", lifecycleState)
            put("sequence", sequence)
            put("status", heartbeatStatus)
            put("eventLoopResponsiveness", eventLoopResponsiveness)
            put("heartbeatIntervalMs", policy.heartbeatIntervalMs)
            put("heartbeatStaleMs", policy.heartbeatStaleMs)
            put("lastHeartbeatAtEpochMs", nowEpochMs)
            put("startedAtEpochMs", startedAtEpochMs)
            put("durationMs", (nowEpochMs - startedAtEpochMs).coerceAtLeast(0L))
            put("lastJsProgressAtEpochMs", lastJsProgressAt)
            put("lastJsProgressAgeMs", ageMs(nowEpochMs, lastJsProgressAt))
            put(
                "lastJsProgressSource",
                when {
                    lastBridgeResponseAt > 0L -> "bridge_response"
                    lastBridgeRequestAt > 0L -> "bridge_request"
                    else -> "service_runtime"
                },
            )
            put("lastBridgeRequestAtEpochMs", lastBridgeRequestAt)
            put("lastBridgeRequestAgeMs", ageMs(nowEpochMs, lastBridgeRequestAt))
            put("lastBridgeResponseAtEpochMs", lastBridgeResponseAt)
            put("lastBridgeResponseAgeMs", ageMs(nowEpochMs, lastBridgeResponseAt))
            put("pendingBridgeCalls", pendingBridge)
            put("completedBridgeCalls", nativeValues.intValue("embedded_script.bridge_live_dispatch_count"))
            put("activeLibuvHandles", activeLibuvHandles(nativeValues))
            put("activeResourceRegistryEntries", nativeValues.intValue("embedded_script.bridge_resource.active_count"))
            put("activeResourceTypes", jsonObjectOrEmpty(nativeValues["embedded_script.bridge_resource.active_types"]))
            put("resourceUsage", usage)
            put("actionable", actionableMessage(heartbeatStatus, usage))
        }
    }

    fun heartbeatNativeValues(snapshot: JSONObject): Map<String, String> {
        val usage = snapshot.getJSONObject("resourceUsage")
        return linkedMapOf(
            "embedded_script.runtime_heartbeat.status" to snapshot.getString("status"),
            "embedded_script.runtime_heartbeat.sequence" to snapshot.getInt("sequence").toString(),
            "embedded_script.runtime_heartbeat.event_loop_responsiveness" to snapshot.getString("eventLoopResponsiveness"),
            "embedded_script.runtime_heartbeat.last_js_progress_age_ms" to snapshot.getLong("lastJsProgressAgeMs").toString(),
            "embedded_script.runtime_heartbeat.last_bridge_response_age_ms" to snapshot.getLong("lastBridgeResponseAgeMs").toString(),
            "embedded_script.runtime_heartbeat.active_resource_registry_entries" to snapshot.getInt("activeResourceRegistryEntries").toString(),
            "embedded_script.runtime_heartbeat.json" to snapshot.toString(),
            "embedded_script.runtime_budget.status" to usage.getString("status"),
            "embedded_script.runtime_budget.within_budget" to usage.getBoolean("withinBudget").toString(),
            "embedded_script.runtime_budget.breach_count" to usage.getJSONArray("breaches").length().toString(),
            "embedded_script.runtime_budget.usage_json" to usage.toString(),
        )
    }

    fun runtimeModuleSource(policy: Policy): String = policy.toJsonObject().toString()

    private fun defaultsFor(
        executionMode: String,
        timeoutMs: Long,
        bridgeLimits: NodeBridgeLimits.Policy,
    ): Policy {
        val mode = NodeExecutionModePolicy.normalize(executionMode)
        val interactive = mode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING
        val timeout = timeoutMs.coerceAtLeast(1L)
        return Policy(
            executionMode = mode,
            maxTimers = if (interactive) 256 else 128,
            maxPendingBridgeCalls = bridgeLimits.maxPendingBridgeCalls,
            maxImageHandles = bridgeLimits.maxImageHandles,
            maxWebSocketConnections = bridgeLimits.maxWebSocketConnections,
            maxNetworkRequests = bridgeLimits.maxNetworkRequests,
            maxSensorSubscriptions = DEFAULT_MAX_SENSORS,
            maxUiHandles = DEFAULT_MAX_UI_HANDLES,
            maxWorkers = 0,
            memorySoftLimitBytes = if (interactive) DEFAULT_INTERACTIVE_MEMORY_SOFT_LIMIT_BYTES else DEFAULT_ONE_SHOT_MEMORY_SOFT_LIMIT_BYTES,
            maxExecutionDurationMs = if (interactive) 0L else timeout,
            maximumExecutionDurationPolicy = if (interactive) "foreground_user_visible_no_automatic_restart" else "request_timeout_ms",
            heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS,
            heartbeatStaleMs = DEFAULT_HEARTBEAT_STALE_MS,
            bridgeHardMax = BridgeHardMax(
                maxPendingBridgeCalls = bridgeLimits.maxPendingBridgeCalls,
                maxImageHandles = bridgeLimits.maxImageHandles,
                maxWebSocketConnections = bridgeLimits.maxWebSocketConnections,
                maxNetworkRequests = bridgeLimits.maxNetworkRequests,
            ),
        )
    }

    private fun resourceUsage(
        policy: Policy,
        nativeValues: Map<String, String>,
        elapsedMs: Long,
    ): JSONObject {
        val usage = linkedMapOf(
            "timers" to UsageValue(nativeValues.intValue("embedded_script.pending_timer_count"), policy.maxTimers),
            "pendingBridgeCalls" to UsageValue(nativeValues.intValue("embedded_script.bridge_live_dispatch_pending_count"), policy.maxPendingBridgeCalls),
            "imageHandles" to UsageValue(nativeValues.intValue("embedded_script.image_active_handle_count"), policy.maxImageHandles),
            "webSockets" to UsageValue(nativeValues.intValue("embedded_script.websocket_active_connection_count"), policy.maxWebSocketConnections),
            "networkRequests" to UsageValue(nativeValues.intValue("embedded_script.fetch_active_request_count"), policy.maxNetworkRequests),
            "sensorSubscriptions" to UsageValue(nativeValues.intValue("embedded_script.bridge_resource.active_types", "SENSOR_SUBSCRIPTION"), policy.maxSensorSubscriptions),
            "uiHandles" to UsageValue(nativeValues.intValue("embedded_script.bridge_resource.active_types", "UI_SESSION"), policy.maxUiHandles),
            "workers" to UsageValue(nativeValues.intValue("embedded_script.pending_worker_count"), policy.maxWorkers),
            "executionDurationMs" to UsageValue(elapsedMs.toInt().coerceAtLeast(0), policy.maxExecutionDurationMs.toInt().coerceAtLeast(0)),
        )
        val breaches = JSONArray()
        usage.forEach { (name, value) ->
            if (value.max > 0 && value.current > value.max) {
                breaches.put(JSONObject().put("name", name).put("current", value.current).put("max", value.max))
            }
        }
        return JSONObject().apply {
            put("status", if (breaches.length() == 0) STATUS_WITHIN_BUDGET else STATUS_BREACHED)
            put("withinBudget", breaches.length() == 0)
            usage.forEach { (name, value) ->
                put(name, JSONObject().put("current", value.current).put("max", value.max))
            }
            put("memorySoftLimitBytes", policy.memorySoftLimitBytes)
            put("breaches", breaches)
        }
    }

    private fun activeLibuvHandles(nativeValues: Map<String, String>): Any {
        val activeCount = nativeValues["uv.diagnostics.before_close.active_handle_count"]
            ?: nativeValues["uv.diagnostics.after_close.active_handle_count"]
            ?: nativeValues["embedded_script.active_uv_handle_count"]
        return activeCount?.toIntOrNull() ?: JSONObject.NULL
    }

    private fun actionableMessage(heartbeatStatus: String, usage: JSONObject): String {
        if (heartbeatStatus == HEARTBEAT_STALE) {
            return "bridge response heartbeat is stale; inspect pending bridge calls and provider diagnostics"
        }
        if (!usage.getBoolean("withinBudget")) {
            return "runtime budget breach detected; inspect resourceUsage breaches"
        }
        return "runtime heartbeat and budget diagnostics are within the configured policy"
    }

    private fun collectLimitObject(json: JSONObject?, source: String, builder: Builder) {
        if (json == null) {
            return
        }
        var consumed = false
        json.keys().forEach { name ->
            if (json.isNull(name)) {
                return@forEach
            }
            if (builder.set(name, json.opt(name), source)) {
                consumed = true
            }
        }
        if (consumed) {
            builder.addSource(source)
        }
    }

    private fun parseJsonObject(text: String, source: String, warnings: MutableList<String>): JSONObject? {
        return runCatching { JSONObject(text) }
            .onFailure { error -> warnings += "$source runtime budget could not be parsed: ${error.message ?: error.javaClass.name}" }
            .getOrNull()
    }

    private fun readTextIfFile(file: File): String? {
        return runCatching { file.takeIf { it.isFile }?.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun stringArray(value: JSONArray?): List<String> {
        if (value == null) {
            return emptyList()
        }
        return (0 until value.length()).mapNotNull { index -> value.optString(index).takeIf { it.isNotBlank() } }
    }

    private fun Map<String, String>.intValue(key: String): Int {
        return this[key]?.toIntOrNull() ?: 0
    }

    private fun Map<String, String>.intValue(jsonKey: String, typeName: String): Int {
        return jsonObjectOrEmpty(this[jsonKey]).optInt(typeName, 0)
    }

    private fun Map<String, String>.longValue(key: String): Long {
        return this[key]?.toLongOrNull() ?: 0L
    }

    private fun ageMs(nowEpochMs: Long, thenEpochMs: Long): Long {
        return if (thenEpochMs <= 0L) -1L else (nowEpochMs - thenEpochMs).coerceAtLeast(0L)
    }

    private fun jsonObjectOrEmpty(text: String?): JSONObject {
        return runCatching { JSONObject(text.orEmpty().ifBlank { "{}" }) }.getOrDefault(JSONObject())
    }

    data class Policy(
        val executionMode: String,
        val maxTimers: Int,
        val maxPendingBridgeCalls: Int,
        val maxImageHandles: Int,
        val maxWebSocketConnections: Int,
        val maxNetworkRequests: Int,
        val maxSensorSubscriptions: Int,
        val maxUiHandles: Int,
        val maxWorkers: Int,
        val memorySoftLimitBytes: Long,
        val maxExecutionDurationMs: Long,
        val maximumExecutionDurationPolicy: String,
        val heartbeatIntervalMs: Long,
        val heartbeatStaleMs: Long,
        val bridgeHardMax: BridgeHardMax,
        val sources: List<String> = listOf("default"),
        val warnings: List<String> = emptyList(),
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("version", 1)
            put("executionMode", executionMode)
            put("maxTimers", maxTimers)
            put("maxPendingBridgeCalls", maxPendingBridgeCalls)
            put("maxImageHandles", maxImageHandles)
            put("maxWebSocketConnections", maxWebSocketConnections)
            put("maxNetworkRequests", maxNetworkRequests)
            put("maxSensorSubscriptions", maxSensorSubscriptions)
            put("maxUiHandles", maxUiHandles)
            put("maxWorkers", maxWorkers)
            put("memorySoftLimitBytes", memorySoftLimitBytes)
            put("maxExecutionDurationMs", maxExecutionDurationMs)
            put("maximumExecutionDurationPolicy", maximumExecutionDurationPolicy)
            put("heartbeatIntervalMs", heartbeatIntervalMs)
            put("heartbeatStaleMs", heartbeatStaleMs)
            put("sources", JSONArray(sources))
            put("warnings", JSONArray(warnings))
            put("hardMax", JSONObject().apply {
                put("maxTimers", 1024)
                put("maxPendingBridgeCalls", bridgeHardMax.maxPendingBridgeCalls)
                put("maxImageHandles", bridgeHardMax.maxImageHandles)
                put("maxWebSocketConnections", bridgeHardMax.maxWebSocketConnections)
                put("maxNetworkRequests", bridgeHardMax.maxNetworkRequests)
                put("maxSensorSubscriptions", DEFAULT_MAX_SENSORS)
                put("maxUiHandles", DEFAULT_MAX_UI_HANDLES)
                put("maxWorkers", HARD_MAX_WORKERS)
                put("memorySoftLimitBytes", HARD_MEMORY_SOFT_LIMIT_BYTES)
                put("maxExecutionDurationMs", HARD_EXECUTION_DURATION_MS)
            })
        }

        fun nativeDiagnostics(): Map<String, String> = linkedMapOf(
            "embedded_script.runtime_budget.version" to "1",
            "embedded_script.runtime_budget.execution_mode" to executionMode,
            "embedded_script.runtime_budget.max_timers" to maxTimers.toString(),
            "embedded_script.runtime_budget.max_pending_bridge_calls" to maxPendingBridgeCalls.toString(),
            "embedded_script.runtime_budget.max_image_handles" to maxImageHandles.toString(),
            "embedded_script.runtime_budget.max_websocket_connections" to maxWebSocketConnections.toString(),
            "embedded_script.runtime_budget.max_network_requests" to maxNetworkRequests.toString(),
            "embedded_script.runtime_budget.max_sensor_subscriptions" to maxSensorSubscriptions.toString(),
            "embedded_script.runtime_budget.max_ui_handles" to maxUiHandles.toString(),
            "embedded_script.runtime_budget.max_workers" to maxWorkers.toString(),
            "embedded_script.runtime_budget.memory_soft_limit_bytes" to memorySoftLimitBytes.toString(),
            "embedded_script.runtime_budget.max_execution_duration_ms" to maxExecutionDurationMs.toString(),
            "embedded_script.runtime_budget.maximum_execution_duration_policy" to maximumExecutionDurationPolicy,
            "embedded_script.runtime_budget.heartbeat_interval_ms" to heartbeatIntervalMs.toString(),
            "embedded_script.runtime_budget.heartbeat_stale_ms" to heartbeatStaleMs.toString(),
            "embedded_script.runtime_budget.sources" to sources.joinToString(","),
            "embedded_script.runtime_budget.warning_count" to warnings.size.toString(),
        )
    }

    data class BridgeHardMax(
        val maxPendingBridgeCalls: Int,
        val maxImageHandles: Int,
        val maxWebSocketConnections: Int,
        val maxNetworkRequests: Int,
    )

    private data class UsageValue(val current: Int, val max: Int)

    private class Builder(private val defaults: Policy) {
        private var maxTimers = defaults.maxTimers
        private var maxPendingBridgeCalls = defaults.maxPendingBridgeCalls
        private var maxImageHandles = defaults.maxImageHandles
        private var maxWebSocketConnections = defaults.maxWebSocketConnections
        private var maxNetworkRequests = defaults.maxNetworkRequests
        private var maxSensorSubscriptions = defaults.maxSensorSubscriptions
        private var maxUiHandles = defaults.maxUiHandles
        private var maxWorkers = defaults.maxWorkers
        private var memorySoftLimitBytes = defaults.memorySoftLimitBytes
        private var maxExecutionDurationMs = defaults.maxExecutionDurationMs
        private var heartbeatIntervalMs = defaults.heartbeatIntervalMs
        private var heartbeatStaleMs = defaults.heartbeatStaleMs
        private val sources = mutableListOf<String>()
        private val warnings = mutableListOf<String>()

        fun addSource(source: String) {
            if (source !in sources) {
                sources += source
            }
        }

        fun set(name: String, rawValue: Any?, source: String): Boolean {
            return when (name) {
                "maxTimers" -> setInt(name, rawValue, source, 0, 1024) { maxTimers = it }
                "maxPendingBridgeCalls" -> setInt(name, rawValue, source, 1, defaults.bridgeHardMax.maxPendingBridgeCalls) { maxPendingBridgeCalls = it }
                "maxImageHandles" -> setInt(name, rawValue, source, 0, defaults.bridgeHardMax.maxImageHandles) { maxImageHandles = it }
                "maxWebSocketConnections" -> setInt(name, rawValue, source, 0, defaults.bridgeHardMax.maxWebSocketConnections) { maxWebSocketConnections = it }
                "maxNetworkRequests" -> setInt(name, rawValue, source, 0, defaults.bridgeHardMax.maxNetworkRequests) { maxNetworkRequests = it }
                "maxSensorSubscriptions" -> setInt(name, rawValue, source, 0, DEFAULT_MAX_SENSORS) { maxSensorSubscriptions = it }
                "maxUiHandles" -> setInt(name, rawValue, source, 0, DEFAULT_MAX_UI_HANDLES) { maxUiHandles = it }
                "maxWorkers" -> setInt(name, rawValue, source, 0, HARD_MAX_WORKERS) { maxWorkers = it }
                "memorySoftLimitBytes" -> setLong(name, rawValue, source, 0L, HARD_MEMORY_SOFT_LIMIT_BYTES) { memorySoftLimitBytes = it }
                "maxExecutionDurationMs" -> setLong(name, rawValue, source, 0L, HARD_EXECUTION_DURATION_MS) { maxExecutionDurationMs = it }
                "heartbeatIntervalMs" -> setLong(name, rawValue, source, 250L, MAX_HEARTBEAT_INTERVAL_MS) { heartbeatIntervalMs = it }
                "heartbeatStaleMs" -> setLong(name, rawValue, source, 1_000L, MAX_HEARTBEAT_STALE_MS) { heartbeatStaleMs = it }
                else -> false
            }
        }

        fun build(extraWarnings: List<String>): Policy {
            return defaults.copy(
                maxTimers = maxTimers,
                maxPendingBridgeCalls = maxPendingBridgeCalls,
                maxImageHandles = maxImageHandles,
                maxWebSocketConnections = maxWebSocketConnections,
                maxNetworkRequests = maxNetworkRequests,
                maxSensorSubscriptions = maxSensorSubscriptions,
                maxUiHandles = maxUiHandles,
                maxWorkers = maxWorkers,
                memorySoftLimitBytes = memorySoftLimitBytes,
                maxExecutionDurationMs = maxExecutionDurationMs,
                heartbeatIntervalMs = heartbeatIntervalMs,
                heartbeatStaleMs = heartbeatStaleMs,
                sources = sources.ifEmpty { defaults.sources },
                warnings = (warnings + extraWarnings).distinct(),
            )
        }

        private fun setInt(
            name: String,
            rawValue: Any?,
            source: String,
            min: Int,
            max: Int,
            assign: (Int) -> Unit,
        ): Boolean {
            val parsed = parseLong(rawValue)
                ?.takeIf { it >= Int.MIN_VALUE.toLong() && it <= Int.MAX_VALUE.toLong() }
                ?.toInt()
            if (parsed == null) {
                warnings += "$source.$name ignored: expected an integer."
                return true
            }
            if (parsed < min || parsed > max) {
                warnings += "$source.$name ignored: $parsed is outside $min..$max."
                return true
            }
            assign(parsed)
            return true
        }

        private fun setLong(
            name: String,
            rawValue: Any?,
            source: String,
            min: Long,
            max: Long,
            assign: (Long) -> Unit,
        ): Boolean {
            val parsed = parseLong(rawValue)
            if (parsed == null) {
                warnings += "$source.$name ignored: expected an integer."
                return true
            }
            if (parsed < min || parsed > max) {
                warnings += "$source.$name ignored: $parsed is outside $min..$max."
                return true
            }
            assign(parsed)
            return true
        }

        private fun parseLong(rawValue: Any?): Long? {
            return when (rawValue) {
                is Int -> rawValue.toLong()
                is Long -> rawValue
                is Number -> rawValue.toDouble().takeIf { it.isFinite() }?.toLong()
                is String -> rawValue.trim().toLongOrNull()
                else -> null
            }
        }
    }
}
