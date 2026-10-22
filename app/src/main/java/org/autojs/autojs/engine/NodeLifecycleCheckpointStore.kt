package org.autojs.autojs.engine

import android.content.Context
import android.util.Log
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optInt
import org.autojs.autojs.core.pref.optLong
import org.autojs.autojs.core.pref.optString
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Checkpoints of Node.js projects, the JSON file store `JsonFileStores.NODE_LIFECYCLE_CHECKPOINTS`, and stop request
 * files; they are read and written with `SettingsJson`, the checkpoints indented by 2 as before (settings P5.1). The
 * records are maps of plain values of `SettingsJson` (settings P5.3): the Node.js bridge writes the returned records
 * to scripts as JSON text, and a checkpoint value that a script sends as org.json values becomes plain values here.
 *
 * zh-CN: Node.js 项目的检查点 (JSON 文件存储 `JsonFileStores.NODE_LIFECYCLE_CHECKPOINTS`) 与停止请求文件, 以 `SettingsJson`
 * 读写, 检查点照旧缩进 2 格 (设置 P5.1). 记录为 `SettingsJson` 普通值映射 (设置 P5.3): Node.js 桥把返回的记录以 JSON 文本交给
 * 脚本, 脚本以 org.json 值发来的检查点值在这里转为普通值.
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
    private const val MAX_CHECKPOINT_DEPTH = 32

    fun runtimeModuleSource(
        context: Context,
        engineInfo: NodeBridgeEngineInfo,
        executionMode: String,
        launchSurface: String,
        stopRequestFile: File?,
    ): String {
        val scope = projectScope(context, engineInfo.cwd)
        val explicitLongRunningLifecycle = isExplicitLongRunningLifecycle(executionMode, launchSurface)
        return SettingsJson.stringify(
            linkedMapOf<String, Any?>(
                "schemaVersion" to SCHEMA_VERSION,
                "executionId" to engineInfo.id,
                "sourceName" to engineInfo.sourceName,
                "packageName" to scope.packageName,
                "workingDirectory" to scope.workingDirectory,
                "projectKey" to scope.projectKey,
                "executionMode" to executionMode,
                "launchSurface" to launchSurface,
                "checkpoint" to linkedMapOf<String, Any?>(
                    "enabled" to explicitLongRunningLifecycle,
                    "maxBytes" to MAX_CHECKPOINT_BYTES,
                    "automaticRestart" to false,
                    "restartPolicy" to "never",
                ),
                "stop" to linkedMapOf<String, Any?>(
                    "enabled" to (stopRequestFile != null),
                    "requestPath" to (stopRequestFile?.absolutePath ?: ""),
                    "pollIntervalMs" to STOP_POLL_INTERVAL_MS,
                    "graceMs" to STOP_GRACE_MS,
                ),
            ),
        )
    }

    fun writeStopRequest(
        file: File?,
        executionId: String,
        reason: String,
    ) {
        if (file == null) {
            return
        }
        writeJsonAtomic(file, stopRequest(executionId, reason, System.currentTimeMillis()))
    }

    fun writeCheckpoint(
        context: Context,
        engineInfo: NodeBridgeEngineInfo,
        reason: String,
        checkpoint: Any?,
    ): Map<String, Any?> {
        ensureInteractiveLifecycle(engineInfo, METHOD_CHECKPOINT)
        val (value, checkpointBytes) = checkedCheckpoint(checkpoint)
        val scope = projectScope(context, engineInfo.cwd)
        val now = System.currentTimeMillis()
        val record = checkpointRecord(scope, engineInfo, reason, value, checkpointBytes, readCheckpoint(context, scope), now)
        writeJsonAtomic(checkpointFile(context, scope), record)
        Log.i(
            TAG,
            "checkpoint.write schema=$CHECKPOINT_MARKER_SCHEMA projectKey=${scope.projectKey} " +
                "reason=${record.optString("reason")} launchSurface=${engineInfo.launchSurface} " +
                "executionMode=${engineInfo.executionMode} packageName=${scope.packageName} " +
                "checkpointBytes=$checkpointBytes",
        )
        return record
    }

    fun readCheckpoint(context: Context, engineInfo: NodeBridgeEngineInfo): Map<String, Any?>? {
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

    fun clearCheckpoint(context: Context, engineInfo: NodeBridgeEngineInfo): Map<String, Any?> {
        ensureInteractiveLifecycle(engineInfo, METHOD_CLEAR_CHECKPOINT)
        val scope = projectScope(context, engineInfo.cwd)
        val deleted = checkpointFile(context, scope).delete()
        Log.i(
            TAG,
            "checkpoint.clear schema=$CHECKPOINT_MARKER_SCHEMA projectKey=${scope.projectKey} " +
                "deleted=$deleted launchSurface=${engineInfo.launchSurface} " +
                "executionMode=${engineInfo.executionMode} packageName=${scope.packageName}",
        )
        return linkedMapOf<String, Any?>(
            "schemaVersion" to SCHEMA_VERSION,
            "projectKey" to scope.projectKey,
            "deleted" to deleted,
            "updatedAtEpochMs" to System.currentTimeMillis(),
        )
    }

    fun statusSnapshot(context: Context, workingDirectory: String?): Map<String, Any?> {
        val scope = projectScope(context, workingDirectory)
        return statusSnapshot(scope, readCheckpoint(context, scope))
    }

    fun nativeDiagnostics(context: Context, engineInfo: NodeBridgeEngineInfo): Map<String, String> {
        val scope = projectScope(context, engineInfo.cwd)
        return nativeDiagnostics(scope, isExplicitLongRunningLifecycle(engineInfo), readCheckpoint(context, scope))
    }

    /** The text of a checkpoint or stop request file: indented by 2 like Android's `JSONObject.toString(2)`. */
    internal fun encode(record: Map<String, Any?>): String = SettingsJson.stringify(record, indent = 2)

    internal fun decode(text: String): MutableMap<String, Any?> = SettingsJson.parseMap(text)

    internal fun stopRequest(executionId: String, reason: String, now: Long): MutableMap<String, Any?> = linkedMapOf<String, Any?>(
        "schemaVersion" to SCHEMA_VERSION,
        "id" to "stop-$executionId-$now",
        "executionId" to executionId,
        "reason" to reason,
        "requestedAtEpochMs" to now,
    )

    /**
     * The checkpoint value of a script as plain values ([normalizeCheckpointValue]) and the UTF-8 size of its JSON text,
     * which may not exceed [MAX_CHECKPOINT_BYTES]. `SettingsJson` writes that text (settings P5.3), so the size can differ
     * by a few bytes from the size of the text that Android's org.json wrote before: `/` is not escaped, an integral
     * double is written as `1.0`, and U+2028 / U+2029 are escaped.
     *
     * zh-CN: 脚本检查点值的普通值形式及其 JSON 文本的 UTF-8 字节数 (不得超过 [MAX_CHECKPOINT_BYTES]). 文本由 `SettingsJson` 写出
     * (设置 P5.3), 与以前 Android org.json 写出的文本相比字节数可能相差几个字节: 不转义 `/`, 整数值的 double 写为 `1.0`, 转义
     * U+2028 / U+2029.
     */
    internal fun checkedCheckpoint(checkpoint: Any?): Pair<Any?, Int> {
        val value = normalizeCheckpointValue(checkpoint)
        val checkpointBytes = SettingsJson.stringify(value).toByteArray(Charsets.UTF_8).size
        if (checkpointBytes > MAX_CHECKPOINT_BYTES) {
            throw NodeLifecycleCheckpointException(
                NodeBridgeError.resourceLimit(
                    message = "AutoJs6 lifecycle checkpoint exceeded $MAX_CHECKPOINT_BYTES bytes.",
                    module = BRIDGE_MODULE_NAME,
                    method = METHOD_CHECKPOINT,
                ),
            )
        }
        return value to checkpointBytes
    }

    /**
     * Converts a checkpoint value (org.json values from the bridge, or plain values) into plain values; nesting deeper than
     * [MAX_CHECKPOINT_DEPTH], non-finite numbers and other values are rejected.
     * zh-CN: 把检查点值 (桥传来的 org.json 值或普通值) 转为普通值; 拒绝嵌套超过 [MAX_CHECKPOINT_DEPTH] 层, 非有限数与其他值.
     */
    internal fun normalizeCheckpointValue(value: Any?, depth: Int = 0): Any? {
        if (depth > MAX_CHECKPOINT_DEPTH) {
            throw invalidCheckpoint("AutoJs6 lifecycle checkpoint exceeded maximum JSON nesting depth.")
        }
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> LinkedHashMap<String, Any?>().apply {
                value.keys().forEach { key -> put(key, normalizeCheckpointValue(value.opt(key), depth + 1)) }
            }
            is Map<*, *> -> LinkedHashMap<String, Any?>().apply {
                value.forEach { (key, item) ->
                    val name = key as? String ?: throw invalidCheckpoint("AutoJs6 lifecycle checkpoint must be JSON-serializable.")
                    put(name, normalizeCheckpointValue(item, depth + 1))
                }
            }
            is JSONArray -> (0 until value.length()).mapTo(ArrayList<Any?>()) { normalizeCheckpointValue(value.opt(it), depth + 1) }
            is List<*> -> value.mapTo(ArrayList<Any?>()) { normalizeCheckpointValue(it, depth + 1) }
            is String, is Boolean -> value
            is Number -> {
                if (!value.toDouble().isFinite()) {
                    throw invalidCheckpoint("AutoJs6 lifecycle checkpoint numbers must be finite.")
                }
                SettingsJson.normalize(value)
            }
            else -> throw invalidCheckpoint("AutoJs6 lifecycle checkpoint must be JSON-serializable.")
        }
    }

    /** The record of a checkpoint file; [previous] is the record it replaces, whose creation time it keeps. */
    internal fun checkpointRecord(
        scope: ProjectScope,
        engineInfo: NodeBridgeEngineInfo,
        reason: String,
        checkpoint: Any?,
        checkpointBytes: Int,
        previous: Map<String, Any?>?,
        now: Long,
    ): MutableMap<String, Any?> = linkedMapOf<String, Any?>(
        "schemaVersion" to SCHEMA_VERSION,
        "projectKey" to scope.projectKey,
        "packageName" to scope.packageName,
        "workingDirectory" to scope.workingDirectory,
        "executionId" to engineInfo.id,
        "sourceName" to engineInfo.sourceName,
        "executionMode" to engineInfo.executionMode,
        "launchSurface" to engineInfo.launchSurface,
        "reason" to reason.ifBlank { "manual" },
        "checkpointBytes" to checkpointBytes,
        "createdAtEpochMs" to (previous?.optLong("createdAtEpochMs")?.takeIf { it > 0L } ?: now),
        "updatedAtEpochMs" to now,
        "automaticRestart" to false,
        "restartPolicy" to "never",
        "checkpoint" to checkpoint,
    )

    internal fun statusSnapshot(scope: ProjectScope, record: Map<String, Any?>?): MutableMap<String, Any?> = linkedMapOf<String, Any?>(
        "schemaVersion" to SCHEMA_VERSION,
        "exists" to (record != null),
        "projectKey" to scope.projectKey,
        "packageName" to scope.packageName,
        "workingDirectory" to scope.workingDirectory,
        "maxBytes" to MAX_CHECKPOINT_BYTES,
        "automaticRestart" to false,
        "restartPolicy" to "never",
    ).apply {
        if (record != null) {
            put("executionId", record.optString("executionId"))
            put("sourceName", record.optString("sourceName"))
            put("reason", record.optString("reason"))
            put("checkpointBytes", record.optInt("checkpointBytes"))
            put("createdAtEpochMs", record.optLong("createdAtEpochMs"))
            put("updatedAtEpochMs", record.optLong("updatedAtEpochMs"))
        }
    }

    internal fun nativeDiagnostics(scope: ProjectScope, enabled: Boolean, record: Map<String, Any?>?): Map<String, String> = linkedMapOf(
        "embedded_script.lifecycle_checkpoint.enabled" to enabled.toString(),
        "embedded_script.lifecycle_checkpoint.project_key" to scope.projectKey,
        "embedded_script.lifecycle_checkpoint.exists" to (record != null).toString(),
        "embedded_script.lifecycle_checkpoint.max_bytes" to MAX_CHECKPOINT_BYTES.toString(),
        "embedded_script.lifecycle_checkpoint.automatic_restart" to "false",
        "embedded_script.lifecycle_checkpoint.restart_policy" to "never",
        "embedded_script.lifecycle_checkpoint.bytes" to (record?.optInt("checkpointBytes") ?: 0).toString(),
        "embedded_script.lifecycle_checkpoint.reason" to record?.optString("reason").orEmpty(),
    )

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

    private fun readCheckpoint(context: Context, scope: ProjectScope): MutableMap<String, Any?>? {
        val file = checkpointFile(context, scope)
        if (!file.isFile) {
            return null
        }
        return runCatching { decode(file.readText(Charsets.UTF_8)) }.getOrNull()
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

    private fun invalidCheckpoint(message: String): NodeLifecycleCheckpointException {
        return NodeLifecycleCheckpointException(
            NodeBridgeError.invalidRequest(
                message = message,
                module = BRIDGE_MODULE_NAME,
                method = METHOD_CHECKPOINT,
            ),
        )
    }

    private fun writeJsonAtomic(file: File, json: Map<String, Any?>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp-${System.nanoTime()}")
        val text = encode(json)
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

    internal data class ProjectScope(
        val packageName: String,
        val workingDirectory: String,
        val projectKey: String,
    )
}

class NodeLifecycleCheckpointException(val bridgeError: NodeBridgeError) : RuntimeException(bridgeError.message)
