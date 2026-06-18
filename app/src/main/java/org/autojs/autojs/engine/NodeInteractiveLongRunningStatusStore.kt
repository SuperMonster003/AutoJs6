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
        val resolvedResources = activeResources
            ?: previous?.optJSONObject("activeResources")?.copy()
            ?: JSONObject()
        val status = JSONObject().apply {
            put("schemaVersion", 1)
            put("executionId", executionId)
            put("executionMode", NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING)
            put("launchSurface", NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION)
            put("sourceName", sourceName.ifBlank { "<embedded-script>" })
            put("workingDirectory", workingDirectory)
            put("state", state)
            put("startedAtEpochMs", resolvedStartedAt)
            put("updatedAtEpochMs", now)
            put("durationMs", (now - resolvedStartedAt).coerceAtLeast(0L))
            put("stopRequested", stopRequested)
            put("restartPolicy", "never")
            put("automaticRestart", false)
            put("stopAction", STOP_ACTION_NAME)
            put("activeResources", resolvedResources)
            put("policy", JSONObject(NodeExecutionModePolicy.nativeValues(
                NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
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
