package org.autojs.autojs.engine

import android.os.SystemClock
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

internal class NodeEmbeddedRuntimeTimeline private constructor(
    private val executionId: String,
    private val processName: String,
    private val pid: Int,
    private val sourceName: String,
    private val startedAtElapsedMs: Long,
) {

    private val events = mutableListOf<JSONObject>()

    @Synchronized
    fun mark(
        name: String,
        details: Map<String, Any?> = emptyMap(),
    ) {
        events += JSONObject().apply {
            put("index", events.size)
            put("name", name)
            put("elapsedMs", (SystemClock.elapsedRealtime() - startedAtElapsedMs).coerceAtLeast(0L))
            put(
                "details",
                JSONObject().apply {
                    put("executionId", executionId)
                    put("processName", processName)
                    put("pid", pid)
                    put("sourceName", sourceName)
                    details.forEach { (key, value) -> putJsonValue(key, value) }
                },
            )
        }
    }

    @Synchronized
    fun recordNativeResult(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) {
        val values = result.nativeValues
        mark(
            "native_libs_loaded",
            mapOf(
                "loadDurationMs" to values["load.duration.ms"].toLongOrNullValue(),
                "nodeVersion" to values["embedded_script.node_version"].orEmpty(),
                "succeeded" to result.succeeded,
            ),
        )
        mark(
            "v8_initialized",
            mapOf(
                "durationMs" to values["timing.isolate_create.ms"].toLongOrNullValue(),
                "status" to inferredStatus(values, "timing.isolate_create.ms"),
            ),
        )
        mark(
            "node_env_created",
            mapOf(
                "durationMs" to values["timing.environment_create.ms"].toLongOrNullValue(),
                "status" to inferredStatus(values, "timing.environment_create.ms"),
            ),
        )
        mark(
            "bootstrap_loaded",
            mapOf(
                "status" to (values["bootstrap_script.status"] ?: values["bootstrap.status"] ?: values["embedded_script.status"]).orEmpty(),
                "durationMs" to (
                    values["timing.bootstrap_script.ms"]
                        ?: values["timing.bootstrap.ms"]
                    ).toLongOrNullValue(),
                "loadEnvironmentResult" to (
                    values["bootstrap_script.load_environment.result"]
                        ?: values["bootstrap.load_environment.result"]
                    ).orEmpty(),
            ),
        )
        mark(
            "user_script_entered",
            mapOf(
                "sourceBytes" to result.nativeValues["embedded_script.source_bytes"].toLongOrNullValue(),
                "workingDirectory" to result.workingDirectory.orEmpty(),
            ),
        )
        val stdout = result.stdout
        if (stdout.isNotBlank()) {
            mark(
                "first_stdout",
                mapOf(
                    "bytes" to stdout.toByteArray(Charsets.UTF_8).size,
                    "line" to stdout.lineSequence().firstOrNull().orEmpty().truncateForTimeline(),
                ),
            )
        }
        mark(
            "pending_callbacks",
            mapOf(
                "pending" to result.pendingCallbacks,
                "asyncCount" to result.pendingAsyncCallbacks,
                "scopedFsCount" to result.pendingScopedFsCallbacks,
                "cryptoCount" to result.pendingCryptoCallbacks,
                "dnsCount" to result.pendingDnsCallbacks,
                "zlibCount" to result.pendingZlibCallbacks,
                "zlibStreamCount" to result.pendingZlibStreamCount,
            ),
        )
        mark(
            "timers_scheduled",
            mapOf(
                "pending" to result.pendingTimers,
                "count" to result.pendingTimerCount,
                "timeouts" to result.pendingTimeoutCount,
                "intervals" to result.pendingIntervalCount,
                "immediates" to result.pendingImmediateCount,
            ),
        )
    }

    @Synchronized
    fun recordBridge(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) {
        val values = result.nativeValues
        mark(
            "bridge_calls",
            mapOf(
                "queuedCount" to values["embedded_script.bridge_request_count"].toIntOrNullValue(),
                "dispatchedCount" to values["embedded_script.bridge_dispatch_count"].toIntOrNullValue(),
                "failedCount" to values["embedded_script.bridge_dispatch_failed_count"].toIntOrNullValue(),
                "pendingCount" to values["embedded_script.bridge_dispatch_pending_count"].toIntOrNullValue(),
                "liveRequestCount" to values["embedded_script.bridge_live_request_count"].toIntOrNullValue(),
                "liveDispatchCount" to values["embedded_script.bridge_live_dispatch_count"].toIntOrNullValue(),
                "liveFailedCount" to values["embedded_script.bridge_live_dispatch_failed_count"].toIntOrNullValue(),
                "livePendingCount" to values["embedded_script.bridge_live_dispatch_pending_count"].toIntOrNullValue(),
            ),
        )
    }

    @Synchronized
    fun nativeValues(): Map<String, String> {
        val timeline = JSONArray()
        events.forEach { timeline.put(it) }
        return linkedMapOf(
            "embedded_script.timeline_trace_enabled" to "true",
            "embedded_script.timeline_event_count" to events.size.toString(),
            "embedded_script.timeline_json" to timeline.toString(),
        )
    }

    private fun JSONObject.putJsonValue(key: String, value: Any?) {
        put(
            key,
            when (value) {
                null -> JSONObject.NULL
                is Number, is Boolean, is JSONObject, is JSONArray -> value
                else -> value.toString()
            },
        )
    }

    private fun String?.toLongOrNullValue(): Long? = this?.toLongOrNull()

    private fun String?.toIntOrNullValue(): Int? = this?.toIntOrNull()

    private fun inferredStatus(values: Map<String, String>, timingKey: String): String {
        return when {
            values.containsKey(timingKey) -> "observed"
            else -> "inferred"
        }
    }

    private fun String.truncateForTimeline(maxLength: Int = 160): String {
        return if (length <= maxLength) this else take(maxLength) + "..."
    }

    companion object {
        private const val ENV_TIMELINE_TRACE = "AUTOJS6_NODE_TIMELINE_TRACE"

        fun create(
            env: Map<String, String>,
            executionId: String,
            processName: String,
            pid: Int,
            sourceName: String,
            startedAtElapsedMs: Long,
        ): NodeEmbeddedRuntimeTimeline? {
            if (!BuildConfig.NODEJS_TIMELINE_TRACE_ENABLED && !env[ENV_TIMELINE_TRACE].isTruthy()) {
                return null
            }
            return NodeEmbeddedRuntimeTimeline(
                executionId = executionId,
                processName = processName,
                pid = pid,
                sourceName = sourceName,
                startedAtElapsedMs = startedAtElapsedMs,
            )
        }

        private fun String?.isTruthy(): Boolean {
            return this?.trim()?.let {
                it.equals("true", ignoreCase = true) ||
                    it.equals("yes", ignoreCase = true) ||
                    it.equals("on", ignoreCase = true) ||
                    it == "1"
            } == true
        }
    }
}
