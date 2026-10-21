package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.pref.SettingsJson
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Checkpoints of Node.js projects, the JSON file store `JsonFileStores.NODE_LIFECYCLE_CHECKPOINTS`, and stop request
 * files; they are read and written with `SettingsJson`, the checkpoints indented by 2 as before (settings P5.1).
 */
object NodeLifecycleCheckpointStore {

    private const val TAG = "NodeLifecycleCheckpointStore"
    const val CHECKPOINT_MARKER_SCHEMA = "autojs6-node-lifecycle-checkpoint-v1"
    const val RUNTIME_MODULE_NAME = "autojs6:lifecycle-config"
    const val BRIDGE_MODULE_NAME = "lifecycle"
    const val METHOD_CHECKPOINT = "checkpoint"
    const val METHOD_READ_CHECKPOINT = "readCheckpoint"
    const val METHOD_CLEAR_CHECKPOINT = "clearCheckpoint"
    const val SCHEMA_VERSION = 1
    const val MAX_CHECKPOINT_BYTES = 64 * 1024
    const val STOP_POLL_INTERVAL_MS = 50L
    const val STOP_GRACE_MS = 1500L

    fun runtimeModuleSource(
        context: Context,
        engineInfo: NodeBridgeEngineInfo,
        executionMode: String,
        launchSurface: String,
        stopRequestFile: File?,
    ): String {
        val scope = projectScope(context, engineInfo.cwd)
        val explicitLongRunningLifecycle = isExplicitLongRunningLifecycle(executionMode, launchSurface)
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("executionId", engineInfo.id)
            put("sourceName", engineInfo.sourceName)
            put("packageName", scope.packageName)
            put("workingDirectory", scope.workingDirectory)
            put("projectKey", scope.projectKey)
            put("executionMode", executionMode)
            put("launchSurface", launchSurface)
            put("checkpoint", JSONObject().apply {
                put("enabled", explicitLongRunningLifecycle)
                put("maxBytes", MAX_CHECKPOINT_BYTES)
                put("automaticRestart", false)
                put("restartPolicy", "never")
            })
            put("stop", JSONObject().apply {
                put("enabled", stopRequestFile != null)
                put("requestPath", stopRequestFile?.absolutePath ?: "")
                put("pollIntervalMs", STOP_POLL_INTERVAL_MS)
                put("graceMs", STOP_GRACE_MS)
            })
        }.toString()
    }

    fun writeStopRequest(
        file: File?,
        executionId: String,
        reason: String,
    ) {
        if (file == null) {
            return
        }
        val now = System.currentTimeMillis()
        val request = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("id", "stop-$executionId-$now")
            put("executionId", executionId)
            put("reason", reason)
            put("requestedAtEpochMs", now)
        }
        writeJsonAtomic(file, request)
    }

    fun writeCheckpoint(
        context: Context,
        engineInfo: NodeBridgeEngineInfo,
        reason: String,
        checkpoint: Any?,
    ): JSONObject {
        ensureInteractiveLifecycle(engineInfo, METHOD_CHECKPOINT)
        val normalized = normalizeJsonValue(checkpoint)
        val checkpointJson = jsonValueToString(normalized)
        val checkpointBytes = checkpointJson.toByteArray(Charsets.UTF_8).size
        if (checkpointBytes > MAX_CHECKPOINT_BYTES) {
            throw NodeLifecycleCheckpointException(
                NodeBridgeError.resourceLimit(
                    message = "AutoJs6 lifecycle checkpoint exceeded $MAX_CHECKPOINT_BYTES bytes.",
                    module = BRIDGE_MODULE_NAME,
                    method = METHOD_CHECKPOINT,
                ),
            )
        }
        val scope = projectScope(context, engineInfo.cwd)
        val now = System.currentTimeMillis()
        val record = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("projectKey", scope.projectKey)
            put("packageName", scope.packageName)
            put("workingDirectory", scope.workingDirectory)
            put("executionId", engineInfo.id)
            put("sourceName", engineInfo.sourceName)
            put("executionMode", engineInfo.executionMode)
            put("launchSurface", engineInfo.launchSurface)
            put("reason", reason.ifBlank { "manual" })
            put("checkpointBytes", checkpointBytes)
            put("createdAtEpochMs", readCheckpoint(context, scope)?.optLong("createdAtEpochMs")?.takeIf { it > 0L } ?: now)
            put("updatedAtEpochMs", now)
            put("automaticRestart", false)
            put("restartPolicy", "never")
            put("checkpoint", parseJsonValue(checkpointJson))
        }
        writeJsonAtomic(checkpointFile(context, scope), record)
        Log.i(
            TAG,
            "checkpoint.write schema=$CHECKPOINT_MARKER_SCHEMA projectKey=${scope.projectKey} " +
                "reason=${record.optString("reason")} launchSurface=${engineInfo.launchSurface} " +
                "executionMode=${engineInfo.executionMode} packageName=${scope.packageName} " +
                "checkpointBytes=$checkpointBytes",
        )
        return JSONObject(record.toString())
    }

    fun readCheckpoint(context: Context, engineInfo: NodeBridgeEngineInfo): JSONObject? {
        ensureInteractiveLifecycle(engineInfo, METHOD_READ_CHECKPOINT)
        val scope = projectScope(context, engineInfo.cwd)
        val record = readCheckpoint(context, scope)
        Log.i(
            TAG,
            "checkpoint.read schema=$CHECKPOINT_MARKER_SCHEMA projectKey=${scope.projectKey} " +
                "exists=${record != null} launchSurface=${engineInfo.launchSurface} " +
                "executionMode=${engineInfo.executionMode} packageName=${scope.packageName}",
        )
        return record
    }

    fun clearCheckpoint(context: Context, engineInfo: NodeBridgeEngineInfo): JSONObject {
        ensureInteractiveLifecycle(engineInfo, METHOD_CLEAR_CHECKPOINT)
        val scope = projectScope(context, engineInfo.cwd)
        val deleted = checkpointFile(context, scope).delete()
        Log.i(
            TAG,
            "checkpoint.clear schema=$CHECKPOINT_MARKER_SCHEMA projectKey=${scope.projectKey} " +
                "deleted=$deleted launchSurface=${engineInfo.launchSurface} " +
                "executionMode=${engineInfo.executionMode} packageName=${scope.packageName}",
        )
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("projectKey", scope.projectKey)
            put("deleted", deleted)
            put("updatedAtEpochMs", System.currentTimeMillis())
        }
    }

    fun statusSnapshot(context: Context, workingDirectory: String?): JSONObject {
        val scope = projectScope(context, workingDirectory)
        val record = readCheckpoint(context, scope)
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exists", record != null)
            put("projectKey", scope.projectKey)
            put("packageName", scope.packageName)
            put("workingDirectory", scope.workingDirectory)
            put("maxBytes", MAX_CHECKPOINT_BYTES)
            put("automaticRestart", false)
            put("restartPolicy", "never")
            if (record != null) {
                put("executionId", record.optString("executionId"))
                put("sourceName", record.optString("sourceName"))
                put("reason", record.optString("reason"))
                put("checkpointBytes", record.optInt("checkpointBytes"))
                put("createdAtEpochMs", record.optLong("createdAtEpochMs"))
                put("updatedAtEpochMs", record.optLong("updatedAtEpochMs"))
            }
        }
    }

    fun nativeDiagnostics(context: Context, engineInfo: NodeBridgeEngineInfo): Map<String, String> {
        val scope = projectScope(context, engineInfo.cwd)
        val record = readCheckpoint(context, scope)
        val enabled = isExplicitLongRunningLifecycle(engineInfo)
        return linkedMapOf(
            "embedded_script.lifecycle_checkpoint.enabled" to enabled.toString(),
            "embedded_script.lifecycle_checkpoint.project_key" to scope.projectKey,
            "embedded_script.lifecycle_checkpoint.exists" to (record != null).toString(),
            "embedded_script.lifecycle_checkpoint.max_bytes" to MAX_CHECKPOINT_BYTES.toString(),
            "embedded_script.lifecycle_checkpoint.automatic_restart" to "false",
            "embedded_script.lifecycle_checkpoint.restart_policy" to "never",
            "embedded_script.lifecycle_checkpoint.bytes" to (record?.optInt("checkpointBytes") ?: 0).toString(),
            "embedded_script.lifecycle_checkpoint.reason" to record?.optString("reason").orEmpty(),
        )
    }

    private fun ensureInteractiveLifecycle(engineInfo: NodeBridgeEngineInfo, method: String) {
        if (isExplicitLongRunningLifecycle(engineInfo)) {
            return
        }
        throw NodeLifecycleCheckpointException(
            NodeBridgeError.permissionDenied(
                message = "AutoJs6 lifecycle.$method is available only inside the explicit interactive long-running runtime.",
                module = BRIDGE_MODULE_NAME,
                method = method,
            ),
        )
    }

    private fun isExplicitLongRunningLifecycle(engineInfo: NodeBridgeEngineInfo): Boolean {
        return isExplicitLongRunningLifecycle(engineInfo.executionMode, engineInfo.launchSurface)
    }

    private fun isExplicitLongRunningLifecycle(executionMode: String, launchSurface: String): Boolean {
        return executionMode == NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING &&
            NodeExecutionModePolicy.isInteractiveLongRunningLaunchSurface(launchSurface)
    }

    private fun readCheckpoint(context: Context, scope: ProjectScope): JSONObject? {
        val file = checkpointFile(context, scope)
        if (!file.isFile) {
            return null
        }
        return runCatching { SettingsJson.parseObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun checkpointFile(context: Context, scope: ProjectScope): File {
        return File(File(context.filesDir, "nodejs/checkpoints"), "${scope.projectKey}.json")
    }

    private fun projectScope(context: Context, workingDirectory: String?): ProjectScope {
        val packageName = context.packageName.ifBlank { "unknown-package" }
        val cwd = workingDirectory
            ?.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { File(raw).canonicalPath }.getOrDefault(raw) }
            ?: runCatching { context.filesDir.canonicalPath }.getOrDefault(context.filesDir.absolutePath)
        val key = sha256("$packageName\n$cwd").take(32)
        return ProjectScope(packageName, cwd, key)
    }

    private fun normalizeJsonValue(value: Any?, depth: Int = 0): Any {
        if (depth > 32) {
            throw invalidCheckpoint("AutoJs6 lifecycle checkpoint exceeded maximum JSON nesting depth.")
        }
        return when (value) {
            null, JSONObject.NULL -> JSONObject.NULL
            is JSONObject -> JSONObject().apply {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, normalizeJsonValue(value.opt(key), depth + 1))
                }
            }
            is JSONArray -> JSONArray().apply {
                for (index in 0 until value.length()) {
                    put(normalizeJsonValue(value.opt(index), depth + 1))
                }
            }
            is String, is Boolean -> value
            is Number -> {
                if (!value.toDouble().isFinite()) {
                    throw invalidCheckpoint("AutoJs6 lifecycle checkpoint numbers must be finite.")
                }
                value
            }
            else -> throw invalidCheckpoint("AutoJs6 lifecycle checkpoint must be JSON-serializable.")
        }
    }

    private fun invalidCheckpoint(message: String): NodeLifecycleCheckpointException {
        return NodeLifecycleCheckpointException(
            NodeBridgeError.invalidRequest(
                message = message,
                module = BRIDGE_MODULE_NAME,
                method = METHOD_CHECKPOINT,
            ),
        )
    }

    private fun parseJsonValue(json: String): Any {
        return JSONTokener(json.trim().ifEmpty { "null" }).nextValue() ?: JSONObject.NULL
    }

    private fun jsonValueToString(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            else -> JSONArray().put(value).toString().let { wrapped ->
                wrapped.substring(1, wrapped.length - 1)
            }
        }
    }

    private fun writeJsonAtomic(file: File, json: JSONObject) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
        val text = SettingsJson.stringify(json, indent = 2)
        runCatching {
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        }.onFailure {
            temp.delete()
            throw it
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }

    private data class ProjectScope(
        val packageName: String,
        val workingDirectory: String,
        val projectKey: String,
    )
}

class NodeLifecycleCheckpointException(val bridgeError: NodeBridgeError) : RuntimeException(bridgeError.message)
