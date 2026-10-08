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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

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
        val id = task.optString("id").takeIf { it.isNotBlank() }
            ?: return ScheduleOutcome(errorMessage = "work_manager task is missing an id.")
        val kind = task.optString("type")
        val now = System.currentTimeMillis()
        val record = JSONObject(task.toString()).apply {
            put("schemaVersion", RECORD_SCHEMA_VERSION)
            put("scheduler", SCHEDULER_NAME)
            put("status", STATUS_ENQUEUED)
            put("noImplicitRetry", true)
            put("scheduledRunnerVersion", SCHEDULED_RUNNER_VERSION)
            put("executionMode", NodeExecutionModePolicy.MODE_SCHEDULED)
            put("launchSurface", NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER)
            put("updatedAtEpochMs", now)
            put("lastFailure", JSONObject.NULL)
            put("runHistory", JSONArray())
            put("workManagerUniqueName", uniqueName(id))
            put("workManagerTag", taskTag(id))
            put("androidPolicy", NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics())
        }
        val constraints = buildConstraints(record)
            ?: return ScheduleOutcome(errorMessage = "work_manager idle constraint requires Android 6.0 or newer.")

        return runCatching {
            when (kind) {
                ControlledWorkTaskKind.PERIODIC.wireName -> schedulePeriodic(appContext, id, record, constraints)
                ControlledWorkTaskKind.ONCE.wireName -> scheduleOnce(appContext, id, record, constraints)
                else -> return ScheduleOutcome(errorMessage = "work_manager task type is unsupported: $kind.")
            }
            ScheduleOutcome(taskJson = publicRecord(record))
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
                JSONObject(record.toString()).apply {
                    put("status", STATUS_CANCELLED)
                    put("updatedAtEpochMs", System.currentTimeMillis())
                    put("lastFailure", JSONObject.NULL)
                },
            )
        }
        deleteRecord(appContext, id)
        return CancelOutcome()
    }

    fun list(context: Context, ownerKey: String): JSONArray {
        val result = JSONArray()
        readRecords(context.applicationContext)
            .filter { ownerKey(it) == ownerKey }
            .sortedBy { it.optLong("createdAtEpochMs", 0L) }
            .forEach { result.put(publicRecord(it)) }
        return result
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
            record.put("androidPolicy", NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics())
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
        record: JSONObject,
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
        record: JSONObject,
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

    private fun buildConstraints(record: JSONObject): Constraints? {
        val source = record.optJSONObject("constraints") ?: JSONObject()
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
        record: JSONObject,
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
        record: JSONObject,
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
        target: JSONObject,
        original: JSONObject,
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
        target.put("lastFailure", failureReason ?: JSONObject.NULL)
        target.put(
            "lastRun",
            scheduledRunRecord(
                original = original,
                startedAt = startedAt,
                finishedAt = finishedAt,
                succeeded = succeeded,
                failureReason = failureReason,
                result = result,
            ),
        )
        appendRunHistory(target, target.getJSONObject("lastRun"))
    }

    private fun scheduledRunRecord(
        original: JSONObject,
        startedAt: Long,
        finishedAt: Long,
        succeeded: Boolean,
        failureReason: String?,
        result: NodePluginScriptResult?,
    ): JSONObject {
        return JSONObject().apply {
            put("startedAtEpochMs", startedAt)
            put("finishedAtEpochMs", finishedAt)
            put("elapsedMs", result?.elapsedMs ?: (finishedAt - startedAt).coerceAtLeast(0L))
            put("succeeded", succeeded)
            put("trigger", original.optString("lastTrigger").ifBlank { "work_manager" })
            put("runnerVersion", original.optInt("scheduledRunnerVersion", SCHEDULED_RUNNER_VERSION))
            put("attempt", original.optInt("runAttemptCount", 0))
            put("executionId", original.optString("lastExecutionId"))
            put("executionMode", NodeExecutionModePolicy.MODE_SCHEDULED)
            put("launchSurface", NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER)
            put("exitCode", result?.exitCode ?: JSONObject.NULL)
            put("sourceName", result?.sourceName ?: entryPath(original))
            put("errorCode", result?.errorCode ?: JSONObject.NULL)
            put("errorName", result?.errorName ?: JSONObject.NULL)
            put("errorMessage", failureReason ?: JSONObject.NULL)
            put("timedOut", result?.timedOut ?: false)
            put("stdout", result?.stdout.orEmpty().limitChars(RESULT_STDOUT_MAX_CHARS))
            put("stderr", result?.stderr.orEmpty().limitChars(RESULT_STDERR_MAX_CHARS))
            put("androidPolicy", NodeAndroidRuntimePolicy.workManagerQuotaDiagnostics())
            put("nativeValues", scheduledNativeValues(result))
        }
    }

    private fun appendRunHistory(record: JSONObject, runRecord: JSONObject) {
        val existing = record.optJSONArray("runHistory") ?: JSONArray()
        val retained = JSONArray()
        val start = (existing.length() - (RUN_HISTORY_MAX_ENTRIES - 1)).coerceAtLeast(0)
        for (index in start until existing.length()) {
            retained.put(existing.get(index))
        }
        retained.put(JSONObject(runRecord.toString()))
        record.put("runHistory", retained)
    }

    private fun scheduledNativeValues(result: NodePluginScriptResult?): JSONObject {
        val nativeValues = result?.nativeValues.orEmpty()
        val scheduledModeValues = NodeExecutionModePolicy.nativeValues(
            NodeExecutionModePolicy.MODE_SCHEDULED,
            NodeExecutionModePolicy.LAUNCH_SURFACE_SCHEDULED_RUNNER,
        )
        return JSONObject().apply {
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

    private fun resolveEntry(record: JSONObject): EntryResolution {
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

    private fun updateRecord(context: Context, id: String, update: (JSONObject) -> Unit): JSONObject? {
        val record = readRecord(context, id) ?: return null
        update(record)
        writeRecord(context, record)
        return record
    }

    private fun readRecords(context: Context): List<JSONObject> {
        return taskRoot(context)
            .listFiles { file -> file.isFile && file.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { JSONObject(file.readText(Charsets.UTF_8)) }
                    .onFailure { error -> Log.w(TAG, "Failed to read node work record ${file.name}: ${error.message}", error) }
                    .getOrNull()
            }
    }

    private fun readRecord(context: Context, id: String): JSONObject? {
        val file = recordFile(context, id)
        if (!file.isFile) {
            return null
        }
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun writeRecord(context: Context, record: JSONObject) {
        val id = record.optString("id")
        require(id.isNotBlank()) { "Node work record id is required." }
        val target = recordFile(context, id)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.${android.os.Process.myPid()}.${System.nanoTime()}.tmp")
        temp.writeText(record.toString(), Charsets.UTF_8)
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

    private fun publicRecord(record: JSONObject): JSONObject {
        return JSONObject(record.toString())
    }

    private fun ownerKey(record: JSONObject): String {
        val packageName = record.optString("packageName").ifBlank { "unknown-package" }
        val cwd = record.optString("workingDirectory").ifBlank { "unknown-cwd" }
        val canonicalCwd = runCatching { File(cwd).canonicalFile.absolutePath }.getOrDefault(cwd)
        return "$packageName:$canonicalCwd"
    }

    private fun uniqueName(id: String): String = "autojs6-node-work-$id"

    private fun taskTag(id: String): String = "autojs6-node-work-id-$id"

    private fun runExecutionId(id: String, attempt: Int): String = "node-work-run-$id-$attempt"

    private fun entryPath(record: JSONObject): String {
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
        val taskJson: JSONObject? = null,
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
