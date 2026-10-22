package org.autojs.autojs.engine

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.autojs.autojs.core.pref.SettingsJson
import org.autojs.autojs.core.pref.optArray
import org.autojs.autojs.core.pref.optBoolean
import org.autojs.autojs.core.pref.optInt
import org.autojs.autojs.core.pref.optLong
import org.autojs.autojs.core.pref.optObject
import org.autojs.autojs.core.pref.optString
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Tasks that Node.js scripts schedule with WorkManager; their records are the JSON file store
 * `JsonFileStores.NODE_WORK_MANAGER_TASKS`, read and written with `SettingsJson` (settings P5.1) and kept as maps of
 * plain values of `SettingsJson` (settings P5.3). The task that the Node.js bridge schedules arrives as an org.json
 * object and becomes plain values in [schedule]; the bridge writes the returned records to scripts as JSON text.
 *
 * zh-CN: Node.js 脚本通过 WorkManager 调度的任务; 其记录即 JSON 文件存储 `JsonFileStores.NODE_WORK_MANAGER_TASKS`, 以
 * `SettingsJson` 读写 (设置 P5.1), 在内存中为 `SettingsJson` 普通值映射 (设置 P5.3). Node.js 桥调度的任务以 org.json 对象传入,
 * 在 [schedule] 中转为普通值; 桥把返回的记录以 JSON 文本交给脚本.
 */
object NodeWorkManagerBridgeScheduler {

    const val SCHEDULER_NAME = "androidx.work"
    const val RECORD_SCHEMA_VERSION = 1
    const val STATUS_ENQUEUED = "enqueued"
    const val STATUS_RUNNING = "running"
    const val STATUS_SUCCEEDED = "succeeded"
    const val STATUS_FAILED = "failed"
    const val STATUS_CANCELLED = "cancelled"
    const val SCHEDULED_RUNNER_VERSION = 1

    private const val TAG = "NodeWorkManagerBridge"
    private const val WORK_TAG_ALL = "autojs6-node-work-manager"
    private const val TASK_DIR = "nodejs-work-manager/tasks"
    private const val KEY_TASK_ID = "taskId"
    private const val RESULT_STDOUT_MAX_CHARS = 8192
    private const val RESULT_STDERR_MAX_CHARS = 8192
    private const val RUN_HISTORY_MAX_ENTRIES = 8

    fun ownerKey(engineInfo: NodeBridgeEngineInfo): String {
        val packageName = engineInfo.packageName.ifBlank { "unknown-package" }
        val cwd = engineInfo.cwd.ifBlank { "unknown-cwd" }
        val canonicalCwd = runCatching { File(cwd).canonicalFile.absolutePath }.getOrDefault(cwd)
        return "$packageName:$canonicalCwd"
    }

    fun schedule(context: Context, task: JSONObject): ScheduleOutcome {
        val appContext = context.applicationContext
        val source = SettingsJson.normalizeObject(task)
        val id = source.optString("id").takeIf { it.isNotBlank() }
            ?: return ScheduleOutcome(errorMessage = "work_manager task is missing an id.")
        val kind = source.optString("type")
        val record = scheduledRecord(source, id, System.currentTimeMillis(), androidPolicy())
        val constraints = buildConstraints(record)
            ?: return ScheduleOutcome(errorMessage = "work_manager idle constraint requires Android 6.0 or newer.")

        return runCatching {
            when (kind) {
                ControlledWorkTaskKind.PERIODIC.wireName -> schedulePeriodic(appContext, id, record, constraints)
                ControlledWorkTaskKind.ONCE.wireName -> scheduleOnce(appContext, id, record, constraints)
                else -> return ScheduleOutcome(errorMessage = "work_manager task type is unsupported: $kind.")
            }
            ScheduleOutcome(task = publicRecord(record))
        }.getOrElse { error ->
            deleteRecord(appContext, id)
            ScheduleOutcome(errorMessage = "work_manager provider failed to enqueue $id: ${error.message ?: error.javaClass.name}.")
        }
    }

    fun cancel(context: Context, id: String, ownerKey: String): CancelOutcome {
        val appContext = context.applicationContext
        val record = readRecord(appContext, id)
        if (record != null && ownerKey(record) != ownerKey) {
            return CancelOutcome(errorMessage = "work_manager task $id belongs to a different project scope.")
        }
        runCatching {
            WorkManager.getInstance(appContext).cancelUniqueWork(uniqueName(id))
            WorkManager.getInstance(appContext).cancelAllWorkByTag(taskTag(id))
        }.onFailure { error ->
            return CancelOutcome(errorMessage = "work_manager provider failed to cancel $id: ${error.message ?: error.javaClass.name}.")
        }
        if (record != null) {
            writeRecord(
                appContext,
                record.apply {
                    put("status", STATUS_CANCELLED)
                    put("updatedAtEpochMs", System.currentTimeMillis())
                    put("lastFailure", null)
                },
            )
        }
        deleteRecord(appContext, id)
        return CancelOutcome()
    }

    fun list(context: Context, ownerKey: String): List<Map<String, Any?>> {
        return readRecords(context.applicationContext)
            .filter { ownerKey(it) == ownerKey }
            .sortedBy { it.optLong("createdAtEpochMs", 0L) }
            .map { publicRecord(it) }
    }

    fun cancelAllForOwner(context: Context, ownerKey: String) {
        readRecords(context.applicationContext)
            .filter { ownerKey(it) == ownerKey }
            .forEach { record ->
                cancel(context, record.optString("id"), ownerKey)
            }
    }

    internal fun doWork(context: Context, id: String): androidx.work.ListenableWorker.Result {
        if (id.isBlank()) {
            return androidx.work.ListenableWorker.Result.failure()
        }
        val appContext = context.applicationContext
        val running = updateRecord(appContext, id) { record ->
            record.put("status", STATUS_RUNNING)
            record.put("updatedAtEpochMs", System.currentTimeMillis())
            record.put("lastRunStatus", STATUS_RUNNING)
            val nextAttempt = record.optInt("runAttemptCount", 0) + 1
            record.put("runAttemptCount", nextAttempt)
            record.put("lastTrigger", "work_manager")
            record.put("lastExecutionId", runExecutionId(id, nextAttempt))
            record.put("androidPolicy", androidPolicy())
        } ?: return androidx.work.ListenableWorker.Result.failure()

        if (running.optString("status") == STATUS_CANCELLED) {
            return androidx.work.ListenableWorker.Result.success()
        }

        val startedAt = System.currentTimeMillis()
        val launchStartedAt = android.os.SystemClock.elapsedRealtime()
        val entry = resolveEntry(running)
        if (entry.errorMessage != null || entry.file == null || entry.source == null) {
            markFailed(appContext, id, running, startedAt, entry.errorMessage ?: "work_manager task entry could not be resolved.")
            return androidx.work.ListenableWorker.Result.failure()
        }

        val result = NodeJsRuntimePluginBridge.runScript(
            appContext,
            NodePluginScriptRequest(
                source = entry.source,
                sourceName = entry.entryPath,
                workingDirectory = running.optString("workingDirectory").takeIf { it.isNotBlank() },
                executionId = running.optString("lastExecutionId").takeIf { it.isNotBlank() } ?: runExecutionId(id, running.optInt("runAttemptCount", 1)),
                executionMode = NodeExecutionModePolicy.MODE_SCHEDULED,
                timeoutMs = running.optLong("timeoutMs", ControlledWorkManagerPolicy.DEFAULT_TASK_TIMEOUT_MS)
                    .coerceAtLeast(1L),
                env = mapOf(
                    "AUTOJS6_NODE_SCHEDULED_TASK_ID" to id,
                    "AUTOJS6_NODE_SCHEDULED_TASK_TYPE" to running.optString("type"),
                    "AUTOJS6_NODE_SCHEDULED_TASK_ATTEMPT" to running.optInt("runAttemptCount", 1).toString(),
                ),
            ),
            launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            startupTimings = mapOf(
                "launchStartedAtMs" to launchStartedAt,
                "timing.host.request_prepare.ms" to (android.os.SystemClock.elapsedRealtime() - launchStartedAt),
            ),
        )

        markCompleted(appContext, id, running, startedAt, result)
        return if (result.succeeded) {
            androidx.work.ListenableWorker.Result.success()
        } else {
            androidx.work.ListenableWorker.Result.failure()
        }
    }

    private fun scheduleOnce(
        context: Context,
        id: String,
        record: MutableMap<String, Any?>,
        constraints: Constraints,
    ) {
        val request = OneTimeWorkRequestBuilder<NodeWorkManagerBridgeWorker>()
            .setInputData(Data.Builder().putString(KEY_TASK_ID, id).build())
            .setInitialDelay(record.optLong("delayMs", 0L).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG_ALL)
            .addTag(taskTag(id))
            .build()
        record.put("workRequestId", request.id.toString())
        writeRecord(context, record)
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun schedulePeriodic(
        context: Context,
        id: String,
        record: MutableMap<String, Any?>,
        constraints: Constraints,
    ) {
        val request = PeriodicWorkRequestBuilder<NodeWorkManagerBridgeWorker>(
            record.optLong("intervalMs", ControlledWorkManagerPolicy.MIN_PERIODIC_INTERVAL_MS),
            TimeUnit.MILLISECONDS,
        )
            .setInputData(Data.Builder().putString(KEY_TASK_ID, id).build())
            .setInitialDelay(record.optLong("delayMs", 0L).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG_ALL)
            .addTag(taskTag(id))
            .build()
        record.put("workRequestId", request.id.toString())
        writeRecord(context, record)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            uniqueName(id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun buildConstraints(record: Map<String, Any?>): Constraints? {
        val source = record.optObject("constraints") ?: emptyMap()
        if (source.optBoolean("idle", false) && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }
        return Constraints.Builder().apply {
            if (source.optBoolean("charging", false)) {
                setRequiresCharging(true)
            }
            if (source.optBoolean("network", false)) {
                setRequiredNetworkType(NetworkType.CONNECTED)
            }
            if (source.optBoolean("idle", false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setRequiresDeviceIdle(true)
            }
        }.build()
    }

    private fun markFailed(
        context: Context,
        id: String,
        record: Map<String, Any?>,
        startedAt: Long,
        reason: String,
    ) {
        Log.w(TAG, "WorkManager task $id failed before execution: $reason")
        updateRecord(context, id) { latest ->
            applyRunResult(latest, record, startedAt, succeeded = false, status = STATUS_FAILED, failureReason = reason)
        }
    }

    private fun markCompleted(
        context: Context,
        id: String,
        record: Map<String, Any?>,
        startedAt: Long,
        result: NodePluginScriptResult,
    ) {
        updateRecord(context, id) { latest ->
            val nextStatus = when {
                result.succeeded && record.optString("type") == ControlledWorkTaskKind.ONCE.wireName -> STATUS_SUCCEEDED
                result.succeeded -> STATUS_ENQUEUED
                else -> STATUS_FAILED
            }
            val failure = if (result.succeeded) {
                null
            } else {
                listOfNotNull(
                    result.errorCode,
                    result.errorName,
                    result.errorMessage,
                ).joinToString(": ").ifBlank { "Node.js plugin task failed." }
            }
            if (!result.succeeded) {
                Log.w(TAG, "WorkManager task $id finished with failure: $failure")
            }
            applyRunResult(latest, record, startedAt, result.succeeded, nextStatus, failure, result)
        }
    }

    private fun applyRunResult(
        target: MutableMap<String, Any?>,
        original: Map<String, Any?>,
        startedAt: Long,
        succeeded: Boolean,
        status: String,
        failureReason: String?,
        result: NodePluginScriptResult? = null,
    ) {
        val finishedAt = System.currentTimeMillis()
        target.put("status", status)
        target.put("updatedAtEpochMs", finishedAt)
        target.put("lastRunStatus", if (succeeded) STATUS_SUCCEEDED else STATUS_FAILED)
        target.put("lastFailure", failureReason)
        val lastRun = scheduledRunRecord(
            original = original,
            startedAt = startedAt,
            finishedAt = finishedAt,
            succeeded = succeeded,
            failureReason = failureReason,
            result = result,
        )
        target.put("lastRun", lastRun)
        appendRunHistory(target, lastRun)
    }

    private fun scheduledRunRecord(
        original: Map<String, Any?>,
        startedAt: Long,
        finishedAt: Long,
        succeeded: Boolean,
        failureReason: String?,
        result: NodePluginScriptResult?,
    ): MutableMap<String, Any?> {
        // A missing exit code, error or failure reason is written as a JSON null, as before.
        return linkedMapOf<String, Any?>(
            "startedAtEpochMs" to startedAt,
            "finishedAtEpochMs" to finishedAt,
            "elapsedMs" to (result?.elapsedMs ?: (finishedAt - startedAt).coerceAtLeast(0L)),
            "succeeded" to succeeded,
            "trigger" to original.optString("lastTrigger").ifBlank { "work_manager" },
            "runnerVersion" to original.optInt("scheduledRunnerVersion", SCHEDULED_RUNNER_VERSION),
            "attempt" to original.optInt("runAttemptCount", 0),
            "executionId" to original.optString("lastExecutionId"),
            "executionMode" to NodeExecutionModePolicy.MODE_SCHEDULED,
            "launchSurface" to NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
            "exitCode" to result?.exitCode,
            "sourceName" to (result?.sourceName ?: entryPath(original)),
            "errorCode" to result?.errorCode,
            "errorName" to result?.errorName,
            "errorMessage" to failureReason,
            "timedOut" to (result?.timedOut ?: false),
            "stdout" to result?.stdout.orEmpty().limitChars(RESULT_STDOUT_MAX_CHARS),
            "stderr" to result?.stderr.orEmpty().limitChars(RESULT_STDERR_MAX_CHARS),
            "androidPolicy" to androidPolicy(),
            "nativeValues" to scheduledNativeValues(result),
        )
    }

    /** Appends [runRecord] to the run history of [record], which keeps the last [RUN_HISTORY_MAX_ENTRIES] runs. */
    internal fun appendRunHistory(record: MutableMap<String, Any?>, runRecord: Map<String, Any?>) {
        val retained = ArrayList<Any?>(record.optArray("runHistory").orEmpty().takeLast(RUN_HISTORY_MAX_ENTRIES - 1))
        retained.add(LinkedHashMap(runRecord))
        record.put("runHistory", retained)
    }

    /**
     * The record of a task that [schedule] enqueues: the members of [task], then the scheduler members (a member that
     * [task] has already is replaced in place); `lastFailure` is a JSON null and `runHistory` is empty.
     * zh-CN: [schedule] 入队任务的记录: [task] 的成员, 其后为调度器成员 ([task] 已有的成员原位替换); `lastFailure` 为 JSON null,
     * `runHistory` 为空.
     */
    internal fun scheduledRecord(task: Map<String, Any?>, id: String, now: Long, androidPolicy: Map<String, Any?>): MutableMap<String, Any?> {
        return LinkedHashMap(task).apply {
            put("schemaVersion", RECORD_SCHEMA_VERSION)
            put("scheduler", SCHEDULER_NAME)
            put("status", STATUS_ENQUEUED)
            put("noImplicitRetry", true)
            put("scheduledRunnerVersion", SCHEDULED_RUNNER_VERSION)
            put("executionMode", NodeExecutionModePolicy.MODE_SCHEDULED)
            put("launchSurface", NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER)
            put("updatedAtEpochMs", now)
            put("lastFailure", null)
            put("runHistory", ArrayList<Any?>())
            put("workManagerUniqueName", uniqueName(id))
            put("workManagerTag", taskTag(id))
            put("androidPolicy", androidPolicy)
        }
    }

    internal fun encode(record: Map<String, Any?>): String = SettingsJson.stringify(record)

    internal fun decode(text: String): MutableMap<String, Any?> = SettingsJson.parseMap(text)

    private fun androidPolicy(): MutableMap<String, Any?> {
        return SettingsJson.normalizeObject(NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics())
    }

    private fun scheduledNativeValues(result: NodePluginScriptResult?): MutableMap<String, Any?> {
        val nativeValues = result?.nativeValues.orEmpty()
        val scheduledModeValues = NodeExecutionModePolicy.nativeValues(
            NodeExecutionModePolicy.MODE_SCHEDULED,
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
        )
        return linkedMapOf<String, Any?>().apply {
            nativeValues.filterKeys { it.startsWith("timing.") }.forEach { (key, value) -> put(key, value) }
            listOf(
                "embedded_script.execution_mode",
                "embedded_script.execution_mode_launch_surface",
                "embedded_script.execution_mode_supported",
                "embedded_script.runtime_budget.status",
                "embedded_script.runtime_budget.breach_count",
                "embedded_script.bridge_live_dispatch_status",
                "embedded_script.bridge_resource.active_count",
            ).forEach { key ->
                (nativeValues[key] ?: scheduledModeValues[key])?.let { put(key, it) }
            }
        }
    }

    private fun resolveEntry(record: Map<String, Any?>): EntryResolution {
        val cwd = record.optString("workingDirectory").takeIf { it.isNotBlank() }
            ?: return EntryResolution(errorMessage = "work_manager task is missing a working directory.")
        val entryPath = entryPath(record).takeIf { it.isNotBlank() }
            ?: return EntryResolution(errorMessage = "work_manager task is missing a script entry.")
        val base = runCatching { File(cwd).canonicalFile }.getOrNull()
            ?: return EntryResolution(errorMessage = "work_manager task working directory is invalid.")
        val file = runCatching { File(base, entryPath).canonicalFile }.getOrNull()
            ?: return EntryResolution(errorMessage = "work_manager task entry path is invalid.")
        val basePath = base.path.trimEnd(File.separatorChar)
        if (file.path != basePath && !file.path.startsWith(basePath + File.separator)) {
            return EntryResolution(errorMessage = "work_manager task entry escaped the scoped project root.")
        }
        if (!file.isFile) {
            return EntryResolution(errorMessage = "work_manager task entry file is missing: $entryPath.")
        }
        val source = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { error ->
            return EntryResolution(errorMessage = "work_manager task entry could not be read: ${error.message ?: error.javaClass.name}.")
        }
        return EntryResolution(entryPath = entryPath, file = file, source = source)
    }

    private fun updateRecord(context: Context, id: String, update: (MutableMap<String, Any?>) -> Unit): MutableMap<String, Any?>? {
        val record = readRecord(context, id) ?: return null
        update(record)
        writeRecord(context, record)
        return record
    }

    private fun readRecords(context: Context): List<MutableMap<String, Any?>> {
        return taskRoot(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { decode(file.readText(Charsets.UTF_8)) }
                    .onFailure { error -> Log.w(TAG, "Failed to read node work record ${file.name}: ${error.message}", error) }
                    .getOrNull()
            }
    }

    private fun readRecord(context: Context, id: String): MutableMap<String, Any?>? {
        val file = recordFile(context, id)
        if (!file.isFile) {
            return null
        }
        return runCatching { decode(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun writeRecord(context: Context, record: Map<String, Any?>) {
        val id = record.optString("id")
        require(id.isNotBlank()) { "Node work record id is required." }
        val target = recordFile(context, id)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.${android.os.Process.myPid()}.${System.nanoTime()}.tmp")
        temp.writeText(encode(record), Charsets.UTF_8)
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Failed to replace node work record: ${target.absolutePath}")
        }
        if (!temp.renameTo(target)) {
            throw IllegalStateException("Failed to commit node work record: ${target.absolutePath}")
        }
    }

    private fun deleteRecord(context: Context, id: String) {
        runCatching { recordFile(context, id).delete() }
    }

    private fun recordFile(context: Context, id: String): File {
        val safe = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(96)
            .ifBlank { "invalid" }
        return File(taskRoot(context), "$safe.json")
    }

    private fun taskRoot(context: Context): File = File(context.filesDir, TASK_DIR).apply {
        mkdirs()
    }

    private fun publicRecord(record: Map<String, Any?>): Map<String, Any?> {
        return decode(encode(record))
    }

    private fun ownerKey(record: Map<String, Any?>): String {
        val packageName = record.optString("packageName").ifBlank { "unknown-package" }
        val cwd = record.optString("workingDirectory").ifBlank { "unknown-cwd" }
        val canonicalCwd = runCatching { File(cwd).canonicalFile.absolutePath }.getOrDefault(cwd)
        return "$packageName:$canonicalCwd"
    }

    private fun uniqueName(id: String): String = "autojs6-node-work-$id"

    private fun taskTag(id: String): String = "autojs6-node-work-id-$id"

    private fun runExecutionId(id: String, attempt: Int): String = "node-work-run-$id-$attempt"

    private fun entryPath(record: Map<String, Any?>): String {
        return when (record.optString("entryKind")) {
            "projectEntry" -> record.optString("entry")
            else -> record.optString("scriptPath")
        }
    }

    private fun String.limitChars(maxChars: Int): String {
        if (length <= maxChars) {
            return this
        }
        return take(maxChars) + "\n...[truncated by AutoJs6 work_manager result store; originalChars=$length]"
    }

    data class ScheduleOutcome(
        val task: Map<String, Any?>? = null,
        val errorMessage: String? = null,
    )

    data class CancelOutcome(
        val errorMessage: String? = null,
    )

    private data class EntryResolution(
        val entryPath: String = "",
        val file: File? = null,
        val source: String? = null,
        val errorMessage: String? = null,
    )
}

class NodeWorkManagerBridgeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("taskId").orEmpty()
        return NodeWorkManagerBridgeScheduler.doWork(applicationContext, id)
    }
}
