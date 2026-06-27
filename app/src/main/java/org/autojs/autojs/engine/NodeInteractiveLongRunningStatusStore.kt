package org.autojs.autojs.engine

import android.content.Context
import org.json.JSONObject
import java.io.File

object NodeInteractiveLongRunningStatusStore {

    const val STATE_STARTING = "starting"
    const val STATE_RUNNING = "running"
    const val STATE_STOPPING = "stopping"
    const val STATE_STOPPED = "stopped"
    const val STATE_FAILED = "failed"
    const val STATE_TIMED_OUT = "timed_out"

    fun write(
        context: Context,
        executionId: String,
        state: String,
        sourceName: String,
        workingDirectory: String? = null,
        startedAtEpochMs: Long? = null,
        launchSurface: String? = null,
        stopRequested: Boolean = false,
        message: String? = null,
        activeResources: JSONObject? = null,
        nativeValues: Map<String, String> = emptyMap(),
    ): JSONObject {
        val now = System.currentTimeMillis()
        val previous = read(context, executionId)
        val resolvedStartedAt = startedAtEpochMs
            ?: previous?.optLong("startedAtEpochMs")?.takeIf { it > 0L }
            ?: now
        val resolvedLaunchSurface = launchSurface
            ?.takeIf { it.isNotBlank() }
            ?: previous?.optString("launchSurface")?.takeIf { it.isNotBlank() }
            ?: NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION
        val resolvedResources = activeResources
            ?: previous?.optJSONObject("activeResources")?.copy()
            ?: JSONObject()
        val resolvedStopRequested = stopRequested || previous?.optBoolean("stopRequested") == true
        val status = JSONObject().apply {
            put("schemaVersion", 1)
            put("executionId", executionId)
            put("executionMode", NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING)
            put("launchSurface", resolvedLaunchSurface)
            put("sourceName", sourceName.ifBlank { "<embedded-script>" })
            put("workingDirectory", workingDirectory)
            put("state", state)
            put("startedAtEpochMs", resolvedStartedAt)
            put("updatedAtEpochMs", now)
            put("durationMs", (now - resolvedStartedAt).coerceAtLeast(0L))
            put("stopRequested", resolvedStopRequested)
            put("restartPolicy", "never")
            put("automaticRestart", false)
            put("stopAction", STOP_ACTION_NAME)
            put("activeResources", resolvedResources)
            put("checkpoint", NodeLifecycleCheckpointStore.statusSnapshot(context, workingDirectory))
            put("policy", JSONObject(NodeExecutionModePolicy.nativeValues(
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                resolvedLaunchSurface,
            )))
            put("nativeValues", JSONObject(nativeValues))
            message?.let { put("message", it) }
        }
        writeJson(context, executionId, status)
        return status
    }

    fun read(context: Context, executionId: String): JSONObject? {
        val file = statusFile(context, executionId)
        if (!file.isFile) {
            return null
        }
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    fun activeResourceSummary(
        status: String,
        nativeValues: Map<String, String> = emptyMap(),
    ): JSONObject {
        return JSONObject().apply {
            put("status", status)
            put("bridgeLiveEnabled", nativeValues["embedded_script.bridge_live_enabled"] == "true")
            put("bridgePendingCalls", nativeValues["embedded_script.bridge_live_dispatch_pending_count"]?.toIntOrNull() ?: 0)
            put("bridgeCompletedCalls", nativeValues["embedded_script.bridge_live_dispatch_count"]?.toIntOrNull() ?: 0)
            put("pendingTimers", nativeValues["embedded_script.pending_timers"] == "true")
            put("pendingTimerCount", nativeValues["embedded_script.pending_timer_count"]?.toIntOrNull() ?: 0)
            put("pendingAsyncCallbacks", nativeValues["embedded_script.pending_async_callbacks"]?.toIntOrNull() ?: 0)
            put("runtimeHeartbeatStatus", nativeValues["embedded_script.runtime_heartbeat.status"].orEmpty().ifBlank { "unknown" })
            put("runtimeHeartbeatSequence", nativeValues["embedded_script.runtime_heartbeat.sequence"]?.toIntOrNull() ?: 0)
            put(
                "eventLoopResponsiveness",
                nativeValues["embedded_script.runtime_heartbeat.event_loop_responsiveness"].orEmpty().ifBlank { "unknown" },
            )
            put("runtimeBudgetStatus", nativeValues["embedded_script.runtime_budget.status"].orEmpty().ifBlank { "unknown" })
            put("runtimeBudgetWithinPolicy", nativeValues["embedded_script.runtime_budget.within_budget"] == "true")
            put("runtimeBudgetBreachCount", nativeValues["embedded_script.runtime_budget.breach_count"]?.toIntOrNull() ?: 0)
            put("resourceRegistryActiveCount", nativeValues["embedded_script.bridge_resource.active_count"]?.toIntOrNull() ?: 0)
            put("activeImageHandles", nativeValues["embedded_script.image_active_handle_count"]?.toIntOrNull() ?: 0)
            put("activeNetworkRequests", nativeValues["embedded_script.fetch_active_request_count"]?.toIntOrNull() ?: 0)
            put("activeWebSockets", nativeValues["embedded_script.websocket_active_connection_count"]?.toIntOrNull() ?: 0)
        }
    }

    private fun writeJson(context: Context, executionId: String, json: JSONObject) {
        val file = statusFile(context, executionId)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
        runCatching {
            temp.writeText(json.toString(2), Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(json.toString(2), Charsets.UTF_8)
                temp.delete()
            }
        }.onFailure {
            temp.delete()
        }
    }

    private fun statusFile(context: Context, executionId: String): File {
        return File(statusRoot(context), "${safeFileName(executionId)}.json")
    }

    private fun statusRoot(context: Context): File {
        return File(context.filesDir, "nodejs/interactive-long-running")
    }

    private fun safeFileName(value: String): String {
        return value.ifBlank { "unknown" }.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
    }

    private fun JSONObject.copy(): JSONObject = JSONObject(toString())

    const val STOP_ACTION_NAME = "org.autojs.autojs.engine.action.STOP_NATIVE_NODE_INTERACTIVE"
}
