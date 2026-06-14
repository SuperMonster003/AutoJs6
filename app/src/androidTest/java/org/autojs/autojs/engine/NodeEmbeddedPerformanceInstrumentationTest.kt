package org.autojs.autojs.engine

import android.content.ContentValues
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class NodeEmbeddedPerformanceInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun embeddedStartupAndMemoryReportIsWritten() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded Node performance benchmark")

        val beforeScript = processSnapshot("beforeScript", Process.myPid())
        val cold = runMeasured("cold")
        NodeEmbeddedTestHarness.assertSucceeded("cold performance run", cold.result)
        val afterFirstScript = processSnapshot("afterFirstScript", Process.myPid())
        val afterFirstScriptService = processSnapshot("afterFirstScriptService", cold.result.pid)
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        val afterFirstDestroy = processSnapshot("afterFirstDestroy", Process.myPid())

        val beforeWarmScript = processSnapshot("beforeWarmScript", Process.myPid())
        val warm = runMeasured("warm")
        NodeEmbeddedTestHarness.assertSucceeded("warm performance run", warm.result)
        val afterWarmScript = processSnapshot("afterWarmScript", Process.myPid())
        val afterWarmScriptService = processSnapshot("afterWarmScriptService", warm.result.pid)
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        val afterDestroy = processSnapshot("afterDestroy", Process.myPid())

        assertTrue("cold run did not print perf payload\n${cold.result.stdout}", cold.nodePayload != null)
        assertTrue("warm run did not print perf payload\n${warm.result.stdout}", warm.nodePayload != null)

        val report = buildReport(
            beforeScript = beforeScript,
            cold = cold,
            afterFirstScript = afterFirstScript,
            afterFirstScriptService = afterFirstScriptService,
            afterFirstDestroy = afterFirstDestroy,
            beforeWarmScript = beforeWarmScript,
            warm = warm,
            afterWarmScript = afterWarmScript,
            afterWarmScriptService = afterWarmScriptService,
            afterDestroy = afterDestroy,
        )
        val reportFile = writeReports(report)

        assertThresholds(report)
        assertTrue("performance report was not written: ${reportFile.absolutePath}", reportFile.isFile && reportFile.length() > 0L)
        println("node.performance.report=${reportFile.absolutePath}")
        println("node.performance.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/perf")
    }

    private fun runMeasured(label: String): MeasuredRun {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = performanceScript(label),
            sourceName = "<embedded-node-performance-$label.js>",
            workingDirectory = File(context.cacheDir, "embedded-node-performance").apply { mkdirs() },
            timeoutMs = SCRIPT_TIMEOUT_MS,
        )
        val wallMs = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAt).coerceAtLeast(0L)
        return MeasuredRun(
            label = label,
            wallMs = wallMs,
            result = result,
            nodePayload = parseNodePayload(result.stdout),
        )
    }

    private fun buildReport(
        beforeScript: ProcessSnapshot,
        cold: MeasuredRun,
        afterFirstScript: ProcessSnapshot,
        afterFirstScriptService: ProcessSnapshot,
        afterFirstDestroy: ProcessSnapshot,
        beforeWarmScript: ProcessSnapshot,
        warm: MeasuredRun,
        afterWarmScript: ProcessSnapshot,
        afterWarmScriptService: ProcessSnapshot,
        afterDestroy: ProcessSnapshot,
    ): JSONObject {
        val coldLoadMs = cold.result.nativeValues["load.duration.ms"]?.toLongOrNull()
        val warmLoadMs = warm.result.nativeValues["load.duration.ms"]?.toLongOrNull()
        val appRssDeltaAfterFirstScript = afterFirstScript.rssKb?.minusOrNull(beforeScript.rssKb)
        val appRssDeltaAfterDestroy = afterDestroy.rssKb?.minusOrNull(beforeScript.rssKb)
        val appThreadDeltaAfterDestroy = afterDestroy.threadCount?.minusOrNull(beforeScript.threadCount)
        val appFdDeltaAfterDestroy = afterDestroy.fdCount?.minusOrNull(beforeScript.fdCount)

        return JSONObject()
            .put("schema", "autojs6-node-performance-v1")
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put("reportPull", reportPullJson())
            .put(
                "metrics",
                JSONObject()
                    .put("serviceProcessStartTimeMs", cold.wallMs)
                    .put("serviceProcessStartTimeNote", "Measured as cold runScript wall time, including service start, bind, native load, script execution, and result return.")
                    .put("libnodeLoadMs", coldLoadMs ?: JSONObject.NULL)
                    .put("libnodeLoadWarmMs", warmLoadMs ?: JSONObject.NULL)
                    .put("libnodeLoadMeasurable", coldLoadMs != null || warmLoadMs != null)
                    .put("firstScriptExecutionWallMs", cold.result.elapsedMs)
                    .put("warmScriptExecutionWallMs", warm.result.elapsedMs)
                    .put("coldRunWallMs", cold.wallMs)
                    .put("warmRunWallMs", warm.wallMs)
                    .put("appRssDeltaAfterFirstScriptKb", appRssDeltaAfterFirstScript ?: JSONObject.NULL)
                    .put("appRssDeltaAfterDestroyKb", appRssDeltaAfterDestroy ?: JSONObject.NULL)
                    .put("appThreadDeltaAfterDestroy", appThreadDeltaAfterDestroy ?: JSONObject.NULL)
                    .put("appFdDeltaAfterDestroy", appFdDeltaAfterDestroy ?: JSONObject.NULL),
            )
            .put("snapshots", JSONArray(listOf(
                beforeScript.toJson(),
                afterFirstScript.toJson(),
                afterFirstScriptService.toJson(),
                afterFirstDestroy.toJson(),
                beforeWarmScript.toJson(),
                afterWarmScript.toJson(),
                afterWarmScriptService.toJson(),
                afterDestroy.toJson(),
            )))
            .put("runs", JSONArray(listOf(cold.toJson(), warm.toJson())))
            .put(
                "thresholds",
                JSONObject()
                    .put("enabled", instrumentationBoolean(ARG_FAIL_ON_THRESHOLD, false))
                    .put("maxColdWallMs", instrumentationLong(ARG_MAX_COLD_WALL_MS))
                    .put("maxWarmWallMs", instrumentationLong(ARG_MAX_WARM_WALL_MS))
                    .put("maxAppRssDeltaAfterDestroyKb", instrumentationLong(ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB))
                    .put("maxServiceRssKb", instrumentationLong(ARG_MAX_SERVICE_RSS_KB)),
            )
    }

    private fun writeReports(report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/perf").apply { mkdirs() }
        val reportFile = File(reportDir, "embedded-node-performance-${System.currentTimeMillis()}.json")
        report
            .put("devicePrivateReportPath", reportFile.absolutePath)
            .put("devicePublicReportPath", "$DEVICE_REPORT_DIR/${reportFile.name}")
        val reportText = report.toString(2)
        reportFile.writeText(reportText)
        writePublicReport(reportFile.name, reportText)
        return reportFile
    }

    private fun writePublicReport(fileName: String, reportText: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AutoJs6/nodejs/perf")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { output ->
                    output.write(reportText.toByteArray(Charsets.UTF_8))
                } ?: return false
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                val reportDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "AutoJs6/nodejs/perf",
                ).apply { mkdirs() }
                File(reportDir, fileName).writeText(reportText)
                true
            }
        }.getOrDefault(false)

    private fun assertThresholds(report: JSONObject) {
        if (!instrumentationBoolean(ARG_FAIL_ON_THRESHOLD, false)) {
            return
        }
        val metrics = report.getJSONObject("metrics")
        assertBelow("cold run wall time", metrics.optNullableLong("coldRunWallMs"), instrumentationLong(ARG_MAX_COLD_WALL_MS))
        assertBelow("warm run wall time", metrics.optNullableLong("warmRunWallMs"), instrumentationLong(ARG_MAX_WARM_WALL_MS))
        assertBelow(
            "app RSS delta after destroy",
            metrics.optNullableLong("appRssDeltaAfterDestroyKb"),
            instrumentationLong(ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB),
        )
        val maxServiceRssKb = instrumentationLong(ARG_MAX_SERVICE_RSS_KB)
        if (maxServiceRssKb != null) {
            val snapshots = report.getJSONArray("snapshots")
            for (index in 0 until snapshots.length()) {
                val snapshot = snapshots.getJSONObject(index)
                if (snapshot.optString("label").contains("Service")) {
                    assertBelow(
                        "${snapshot.optString("label")} RSS",
                        snapshot.optNullableLong("rssKb"),
                        maxServiceRssKb,
                    )
                }
            }
        }
    }

    private fun assertBelow(label: String, actual: Long?, threshold: Long?) {
        if (actual == null || threshold == null) {
            return
        }
        assertTrue("$label exceeded threshold: actual=$actual threshold=$threshold", actual <= threshold)
    }

    private fun performanceScript(label: String): String = """
        "nodejs";
        const memory = process.memoryUsage();
        const resource = typeof process.resourceUsage === "function" ? process.resourceUsage() : {};
        const perf = globalThis.performance;
        const payload = {
          label: "$label",
          node: process.version,
          versions: process.versions,
          arch: process.arch,
          platform: process.platform,
          uptimeMs: Math.round(process.uptime() * 1000),
          perfNowMs: perf && typeof perf.now === "function" ? Math.round(perf.now()) : null,
          memory: {
            rss: memory.rss,
            heapTotal: memory.heapTotal,
            heapUsed: memory.heapUsed,
            external: memory.external,
            arrayBuffers: memory.arrayBuffers
          },
          resourceUsage: resource
        };
        console.log("$NODE_PAYLOAD_PREFIX" + JSON.stringify(payload));
    """.trimIndent()

    private fun parseNodePayload(stdout: String): JSONObject? =
        stdout
            .lineSequence()
            .mapNotNull { line ->
                val markerIndex = line.indexOf(NODE_PAYLOAD_PREFIX)
                if (markerIndex < 0) null else line.substring(markerIndex + NODE_PAYLOAD_PREFIX.length).trim()
            }
            .firstOrNull()
            ?.let { JSONObject(it) }

    private fun processSnapshot(label: String, pid: Int): ProcessSnapshot {
        val proc = File("/proc/$pid")
        val memoryInfo = if (pid == Process.myPid()) {
            Debug.MemoryInfo().also(Debug::getMemoryInfo)
        } else {
            null
        }
        return ProcessSnapshot(
            label = label,
            pid = pid,
            readable = pid > 0 && proc.exists(),
            rssKb = readStatusLongKb(pid, "VmRSS:"),
            vmSizeKb = readStatusLongKb(pid, "VmSize:"),
            threadCount = File(proc, "task").safeCountChildren(),
            fdCount = File(proc, "fd").safeCountChildren(),
            totalPssKb = memoryInfo?.totalPss,
            dalvikPssKb = memoryInfo?.dalvikPss,
            nativePssKb = memoryInfo?.nativePss,
            otherPssKb = memoryInfo?.otherPss,
        )
    }

    private fun readStatusLongKb(pid: Int, prefix: String): Long? =
        runCatching {
            File("/proc/$pid/status")
                .readLines()
                .firstOrNull { it.startsWith(prefix) }
                ?.split(Regex("""\s+"""))
                ?.firstOrNull { it.toLongOrNull() != null }
                ?.toLongOrNull()
        }.getOrNull()

    private fun File.safeCountChildren(): Int? =
        runCatching { listFiles()?.size }.getOrNull()

    private fun deviceJson(): JSONObject =
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("brand", Build.BRAND)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("primaryAbi", Build.SUPPORTED_ABIS.firstOrNull() ?: JSONObject.NULL)

    private fun reportPullJson(): JSONObject =
        JSONObject()
            .put("deviceDir", DEVICE_REPORT_DIR)
            .put("hostDir", "app/build/reports/nodejs/perf")
            .put("gradleTask", ":app:pullNodeEmbeddedPerformanceReports")
            .put("overrideProperty", "autojs.nodejs.perf.deviceDir")

    private fun instrumentationBoolean(name: String, defaultValue: Boolean): Boolean =
        InstrumentationRegistry.getArguments()
            .getString(name)
            ?.let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
            ?: defaultValue

    private fun instrumentationLong(name: String): Long? =
        InstrumentationRegistry.getArguments().getString(name)?.toLongOrNull()

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private fun Long.minusOrNull(other: Long?): Long? =
        other?.let { this - it }

    private fun Int.minusOrNull(other: Int?): Int? =
        other?.let { this - it }

    private data class MeasuredRun(
        val label: String,
        val wallMs: Long,
        val result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        val nodePayload: JSONObject?,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("label", label)
                .put("wallMs", wallMs)
                .put("resultElapsedMs", result.elapsedMs)
                .put("succeeded", result.succeeded)
                .put("timedOut", result.timedOut)
                .put("processName", result.processName)
                .put("pid", result.pid)
                .put("sourceName", result.sourceName)
                .put("exitCode", result.exitCode ?: JSONObject.NULL)
                .put("errorCode", result.errorCode ?: JSONObject.NULL)
                .put("errorName", result.errorName ?: JSONObject.NULL)
                .put("errorMessage", result.errorMessage ?: JSONObject.NULL)
                .put("node", nodePayload ?: JSONObject.NULL)
                .put("nativeValues", JSONObject(result.nativeValues.toSortedMap()))
    }

    private data class ProcessSnapshot(
        val label: String,
        val pid: Int,
        val readable: Boolean,
        val rssKb: Long?,
        val vmSizeKb: Long?,
        val threadCount: Int?,
        val fdCount: Int?,
        val totalPssKb: Int?,
        val dalvikPssKb: Int?,
        val nativePssKb: Int?,
        val otherPssKb: Int?,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("label", label)
                .put("pid", pid)
                .put("readable", readable)
                .put("rssKb", rssKb ?: JSONObject.NULL)
                .put("vmSizeKb", vmSizeKb ?: JSONObject.NULL)
                .put("threadCount", threadCount ?: JSONObject.NULL)
                .put("fdCount", fdCount ?: JSONObject.NULL)
                .put("totalPssKb", totalPssKb ?: JSONObject.NULL)
                .put("dalvikPssKb", dalvikPssKb ?: JSONObject.NULL)
                .put("nativePssKb", nativePssKb ?: JSONObject.NULL)
                .put("otherPssKb", otherPssKb ?: JSONObject.NULL)
    }

    private companion object {
        private const val SCRIPT_TIMEOUT_MS = 10_000L
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/perf"
        private const val NODE_PAYLOAD_PREFIX = "perf.node="
        private const val ARG_FAIL_ON_THRESHOLD = "autojs.nodejs.perf.failOnThreshold"
        private const val ARG_MAX_COLD_WALL_MS = "autojs.nodejs.perf.maxColdWallMs"
        private const val ARG_MAX_WARM_WALL_MS = "autojs.nodejs.perf.maxWarmWallMs"
        private const val ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB = "autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb"
        private const val ARG_MAX_SERVICE_RSS_KB = "autojs.nodejs.perf.maxServiceRssKb"
    }
}
