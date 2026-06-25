package org.autojs.autojs.engine

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.autojs.autojs.App
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object NodeCrashEvidenceStore {

    const val MARKER_SCHEMA = "autojs6-node-phase13-crash-marker-v1"
    const val NEXT_LAUNCH_BUNDLE_SCHEMA = "autojs6-node-phase13-next-launch-crash-bundle-v1"
    const val STATE_RUNNING = "running"
    const val STATE_CLEAN_EXIT = "clean_exit"
    const val STATE_FAILED = "failed"
    const val STATE_TIMEOUT_KILL = "timeout_kill"
    const val STATE_SERVICE_DESTROY = "service_destroy"
    const val STATE_COLLECTED_AFTER_RESTART = "collected_after_restart"

    private const val TAG = "NodeCrashEvidenceStore"
    private const val ROOT_DIR = "nodejs/crash-evidence"
    private const val MARKER_DIR = "markers"
    private const val NEXT_LAUNCH_DIR = "next-launch"

    data class Marker(
        val id: String,
        val file: File,
        val startedAtEpochMs: Long,
    )

    fun collectPending(context: Context, reason: String): JSONArray {
        val collected = JSONArray()
        markerRoot(context).listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.sortedBy { it.name }
            .orEmpty()
            .forEach { markerFile ->
                val marker = readJson(markerFile) ?: return@forEach
                val state = marker.optString("state")
                if (state !in setOf(STATE_RUNNING, STATE_FAILED, STATE_TIMEOUT_KILL, STATE_SERVICE_DESTROY)) {
                    return@forEach
                }
                val now = System.currentTimeMillis()
                val markerId = marker.optString("markerId").ifBlank { markerFile.nameWithoutExtension }
                appendHistory(marker, STATE_COLLECTED_AFTER_RESTART, reason, now)
                marker.put("state", STATE_COLLECTED_AFTER_RESTART)
                marker.put("collectedAtEpochMs", now)
                marker.put("collectionReason", reason)
                marker.put("nextLaunchCollection", true)
                val bundle = JSONObject().apply {
                    put("schema", NEXT_LAUNCH_BUNDLE_SCHEMA)
                    put("markerId", markerId)
                    put("collectedAtEpochMs", now)
                    put("collectionReason", reason)
                    put("marker", marker)
                }
                val bundleFile = File(nextLaunchRoot(context), "$markerId.json")
                writeJsonAtomic(bundleFile, bundle)
                writeJsonAtomic(markerFile, marker)
                Log.i(
                    TAG,
                    "marker.collect schema=$NEXT_LAUNCH_BUNDLE_SCHEMA markerId=$markerId " +
                            "state=$STATE_COLLECTED_AFTER_RESTART previousState=$state reason=$reason " +
                            "scenario=${marker.optString("scenario")} " +
                            "launchSurface=${marker.optString("launchSurface")} " +
                            "executionMode=${marker.optString("executionMode")} " +
                            "bundle=${bundleFile.name} packageName=${context.packageName}",
                )
                collected.put(JSONObject().apply {
                    put("markerId", markerId)
                    put("bundlePath", bundleFile.absolutePath)
                    put("state", STATE_COLLECTED_AFTER_RESTART)
                    put("executionId", marker.optString("executionId"))
                    put("runtimeSlot", marker.optString("runtimeSlot"))
                })
            }
        return collected
    }

    fun begin(
        context: Context,
        executionId: String,
        sourceName: String,
        workingDirectory: String?,
        executionMode: String,
        launchSurface: String,
        timeoutMs: Long?,
        runtimeAdapter: String?,
        runtimeSlotId: String?,
        scenario: String,
    ): Marker? {
        return runCatching {
            val now = System.currentTimeMillis()
            val markerId = safeFileName("${executionId.ifBlank { "unknown" }}-$now")
            val markerFile = File(markerRoot(context), "$markerId.json")
            val processName = currentProcessName()
            val adapter = runtimeAdapter?.takeIf { it.isNotBlank() } ?: "auto"
            val marker = JSONObject().apply {
                put("schema", MARKER_SCHEMA)
                put("markerId", markerId)
                put("state", STATE_RUNNING)
                put("executionId", executionId.ifBlank { "unknown" })
                put("runtimeSlot", BuildConfig.NODEJS_RUNTIME_SLOT.ifBlank { "node24_5" })
                put("runtimeSlotId", runtimeSlotId ?: JSONObject.NULL)
                put("runtimeSlotTargetVersion", BuildConfig.NODEJS_RUNTIME_SLOT_TARGET_VERSION)
                put("runtimeAdapter", adapter)
                put("runtimeBackend", BuildConfig.NODEJS_RUNTIME_BACKEND)
                put("profile", NodeRuntimeCapabilities.PROFILE_NAME)
                put("engineVersion", NodeRuntimeCapabilities.ENGINE_VERSION)
                put("nodeHeaderVersion", NodeRuntimeCapabilities.NODE_HEADER_VERSION)
                put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
                put("scenario", scenario)
                put("processName", processName)
                put("pid", Process.myPid())
                put("sourceName", sourceName.ifBlank { "<embedded-script>" })
                put("workingDirectory", workingDirectory ?: JSONObject.NULL)
                put("executionMode", executionMode)
                put("launchSurface", launchSurface)
                put("timeoutMs", timeoutMs ?: JSONObject.NULL)
                put("startedAtEpochMs", now)
                put("updatedAtEpochMs", now)
                put("packageName", context.packageName)
                put("appVersionName", BuildConfig.VERSION_NAME)
                put("appVersionCode", BuildConfig.VERSION_CODE)
                put("buildType", BuildConfig.BUILD_TYPE)
                put("featureFlags", featureFlagsJson())
                put("safeSignalMarker", JSONObject().apply {
                    put("implemented", false)
                    put("reason", "phase13_runtime_json_marker; native signal-safe writer remains separate")
                })
                put("automaticCollection", true)
                put("resourceSnapshot", resourceSnapshot(STATE_RUNNING, emptyMap()))
                put("timelineTail", timelineTail(emptyMap()))
                put("stateHistory", JSONArray().apply {
                    put(historyEvent(STATE_RUNNING, "begin", now))
                })
            }
            writeJsonAtomic(markerFile, marker)
            Log.i(
                TAG,
                "marker.begin schema=$MARKER_SCHEMA markerId=$markerId state=$STATE_RUNNING " +
                        "scenario=$scenario launchSurface=$launchSurface executionMode=$executionMode " +
                        "runtimeAdapter=$adapter runtimeSlotId=${runtimeSlotId ?: "null"} " +
                        "processName=$processName packageName=${context.packageName}",
            )
            Marker(markerId, markerFile, now)
        }.onFailure {
            Log.w(TAG, "Failed to begin Node crash evidence marker.", it)
        }.getOrNull()
    }

    fun markEmbeddedResult(
        context: Context,
        marker: Marker?,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        val state = when {
            result.succeeded -> STATE_CLEAN_EXIT
            result.timedOut -> STATE_TIMEOUT_KILL
            else -> STATE_FAILED
        }
        markTerminal(
            context = context,
            marker = marker,
            state = state,
            reason = when (state) {
                STATE_CLEAN_EXIT -> "embedded_script.clean_exit"
                STATE_TIMEOUT_KILL -> "embedded_script.timeout"
                else -> "embedded_script.failure"
            },
            exitCode = result.exitCode,
            errorName = result.errorName,
            errorMessage = result.errorMessage,
            elapsedMs = result.elapsedMs,
            nativeValues = result.nativeValues,
            timedOut = result.timedOut,
        )
    }

    fun markNativeProcessExit(
        context: Context,
        marker: Marker?,
        exitCode: Int,
        elapsedMs: Long,
    ) {
        markTerminal(
            context = context,
            marker = marker,
            state = if (exitCode == 0) STATE_CLEAN_EXIT else STATE_FAILED,
            reason = "native_process.exit",
            exitCode = exitCode,
            errorName = null,
            errorMessage = null,
            elapsedMs = elapsedMs,
            nativeValues = emptyMap(),
            timedOut = false,
        )
    }

    fun markThrowable(
        context: Context,
        marker: Marker?,
        reason: String,
        throwable: Throwable,
        elapsedMs: Long,
        nativeValues: Map<String, String> = emptyMap(),
    ) {
        markTerminal(
            context = context,
            marker = marker,
            state = STATE_FAILED,
            reason = reason,
            exitCode = 1,
            errorName = throwable.javaClass.simpleName,
            errorMessage = throwable.message ?: throwable.javaClass.name,
            elapsedMs = elapsedMs,
            nativeValues = nativeValues,
            timedOut = false,
        )
    }

    fun markTimeout(
        context: Context,
        marker: Marker?,
        reason: String,
        nativeValues: Map<String, String> = emptyMap(),
    ) {
        if (marker == null || isTerminal(marker.file)) {
            return
        }
        markTerminal(
            context = context,
            marker = marker,
            state = STATE_TIMEOUT_KILL,
            reason = reason,
            exitCode = 124,
            errorName = "EmbeddedScriptTimeoutError",
            errorMessage = reason,
            elapsedMs = System.currentTimeMillis() - marker.startedAtEpochMs,
            nativeValues = nativeValues,
            timedOut = true,
        )
    }

    fun markServiceDestroy(
        context: Context,
        marker: Marker?,
        reason: String,
        nativeValues: Map<String, String> = emptyMap(),
    ) {
        if (marker == null || isTerminal(marker.file)) {
            return
        }
        markTerminal(
            context = context,
            marker = marker,
            state = STATE_SERVICE_DESTROY,
            reason = reason,
            exitCode = null,
            errorName = null,
            errorMessage = reason,
            elapsedMs = System.currentTimeMillis() - marker.startedAtEpochMs,
            nativeValues = nativeValues,
            timedOut = false,
        )
    }

    fun nativeValues(marker: Marker?): Map<String, String> {
        if (marker == null) {
            return emptyMap()
        }
        return mapOf(
            "embedded_script.crash_evidence.marker_schema" to MARKER_SCHEMA,
            "embedded_script.crash_evidence.marker_id" to marker.id,
            "embedded_script.crash_evidence.marker_path" to marker.file.absolutePath,
            "embedded_script.crash_evidence.automatic_collection" to "true",
        )
    }

    private fun markTerminal(
        context: Context,
        marker: Marker?,
        state: String,
        reason: String,
        exitCode: Int?,
        errorName: String?,
        errorMessage: String?,
        elapsedMs: Long,
        nativeValues: Map<String, String>,
        timedOut: Boolean,
    ) {
        if (marker == null) {
            return
        }
        runCatching {
            val now = System.currentTimeMillis()
            val json = readJson(marker.file) ?: JSONObject()
            appendHistory(json, state, reason, now)
            json.put("state", state)
            json.put("updatedAtEpochMs", now)
            json.put("endedAtEpochMs", now)
            json.put("durationMs", elapsedMs.coerceAtLeast(0L))
            json.put("nextLaunchCollection", true)
            json.put("result", JSONObject().apply {
                put("exitCode", exitCode ?: JSONObject.NULL)
                put("errorName", errorName ?: JSONObject.NULL)
                put("errorMessage", errorMessage ?: JSONObject.NULL)
                put("timedOut", timedOut)
                put("reason", reason)
            })
            json.put("resourceSnapshot", resourceSnapshot(state, nativeValues))
            json.put("timelineTail", timelineTail(nativeValues))
            json.put("nativeValuesSha256", sha256(JSONObject(nativeValues).toString()))
            writeJsonAtomic(marker.file, json)
            Log.i(
                TAG,
                "marker.terminal schema=$MARKER_SCHEMA markerId=${marker.id} state=$state " +
                        "reason=$reason timedOut=$timedOut exitCode=${exitCode ?: "null"} " +
                        "scenario=${json.optString("scenario")} " +
                        "launchSurface=${json.optString("launchSurface")} " +
                        "executionMode=${json.optString("executionMode")} " +
                        "packageName=${context.packageName}",
            )
        }.onFailure {
            Log.w(TAG, "Failed to update Node crash evidence marker ${marker.id}.", it)
        }
    }

    private fun resourceSnapshot(state: String, nativeValues: Map<String, String>): JSONObject {
        return JSONObject().apply {
            put("schema", "autojs6-node-final-crash-resource-snapshot-v1")
            put("implemented", nativeValues.isNotEmpty())
            put("state", state)
            put("summary", NodeInteractiveLongRunningStatusStore.activeResourceSummary(state, nativeValues))
            put("nativeValueCount", nativeValues.size)
        }
    }

    private fun timelineTail(nativeValues: Map<String, String>): JSONObject {
        val events = parseJsonArray(nativeValues["embedded_script.timeline_json"])
        val tail = JSONArray()
        val start = (events.length() - 256).coerceAtLeast(0)
        for (index in start until events.length()) {
            tail.put(events.opt(index))
        }
        return JSONObject().apply {
            put("schema", "autojs6-node-final-crash-timeline-tail-v1")
            put("implemented", events.length() > 0)
            put("eventCount", events.length())
            put("requiredEventCount", 256)
            put("events", tail)
        }
    }

    private fun featureFlagsJson(): JSONArray {
        return JSONArray().apply {
            NodeRuntimeCapabilities.featureFlags.forEach { flag ->
                put(JSONObject().apply {
                    put("id", flag.id)
                    put("diagnosticName", flag.diagnosticName)
                    put("defaultEnabled", flag.defaultEnabled)
                    put("enabled", runtimeFlagEnabled(flag))
                    put("securityLevel", flag.securityLevel)
                    put("status", flag.status)
                    put("gradleProperty", flag.gradleProperty ?: JSONObject.NULL)
                })
            }
        }
    }

    private fun runtimeFlagEnabled(flag: NodeRuntimeFeatureFlag): Boolean {
        return when (flag.id) {
            "esm" -> BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED
            "dynamic_import" -> BuildConfig.NODEJS_DYNAMIC_IMPORT_LOCAL_ENABLED ||
                    BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED
            "network" -> BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED
            "worker_threads" -> BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED
            "java_interop" -> BuildConfig.NODEJS_JAVA_INTEROP_EXPERIMENTAL_ENABLED
            else -> flag.defaultEnabled
        }
    }

    private fun isTerminal(file: File): Boolean {
        val state = readJson(file)?.optString("state").orEmpty()
        return state in setOf(
            STATE_CLEAN_EXIT,
            STATE_FAILED,
            STATE_TIMEOUT_KILL,
            STATE_SERVICE_DESTROY,
            STATE_COLLECTED_AFTER_RESTART,
        )
    }

    private fun appendHistory(json: JSONObject, state: String, reason: String, now: Long) {
        val history = json.optJSONArray("stateHistory") ?: JSONArray().also { json.put("stateHistory", it) }
        history.put(historyEvent(state, reason, now))
    }

    private fun historyEvent(state: String, reason: String, now: Long): JSONObject {
        return JSONObject().apply {
            put("state", state)
            put("reason", reason)
            put("epochMs", now)
        }
    }

    private fun markerRoot(context: Context): File {
        return File(File(context.filesDir, ROOT_DIR), MARKER_DIR).apply { mkdirs() }
    }

    private fun nextLaunchRoot(context: Context): File {
        return File(File(context.filesDir, ROOT_DIR), NEXT_LAUNCH_DIR).apply { mkdirs() }
    }

    private fun writeJsonAtomic(file: File, json: JSONObject) {
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
            throw it
        }
    }

    private fun readJson(file: File): JSONObject? {
        if (!file.isFile) {
            return null
        }
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun parseJsonArray(value: String?): JSONArray {
        if (value.isNullOrBlank()) {
            return JSONArray()
        }
        return runCatching { JSONArray(value) }.getOrDefault(JSONArray())
    }

    private fun currentProcessName(): String {
        return runCatching { App.getProcessNameCompat() }.getOrDefault("unknown")
    }

    private fun safeFileName(value: String): String {
        return value.ifBlank { "unknown" }.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(Locale.US, it.toInt() and 0xff) }
    }
}
