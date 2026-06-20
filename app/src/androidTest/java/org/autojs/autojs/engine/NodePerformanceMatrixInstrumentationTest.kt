package org.autojs.autojs.engine

import android.content.ContentValues
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class NodePerformanceMatrixInstrumentationTest {

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
    fun phase8PerformanceMatrixReportIsWritten() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Phase 8 Node performance matrix")

        val compileCacheOnly = runMeasuredMode(
            modeId = "compile-cache-only",
            processPrewarm = false,
        )
        val compileCacheWithPrewarm = runMeasuredMode(
            modeId = "compile-cache-plus-process-prewarm",
            processPrewarm = true,
        )
        val modes = JSONArray()
            .put(unavailableMode(
                modeId = "no-cache-no-snapshot",
                cache = "disabled",
                snapshot = "disabled",
                reason = "Module compile cache is an internal v1.1 default and has no runtime or instrumentation switch that disables it.",
            ))
            .put(compileCacheOnly.toJson())
            .put(unavailableMode(
                modeId = "snapshot-only",
                cache = "disabled",
                snapshot = "startup-snapshot",
                reason = "S8-34 keeps startup snapshots design-gated; no snapshot blob is generated, packaged, loaded, or requested.",
            ))
            .put(unavailableMode(
                modeId = "compile-cache-plus-snapshot",
                cache = "compile-cache",
                snapshot = "startup-snapshot",
                reason = "S8-34 keeps startup snapshots design-gated, so compile-cache and snapshot interaction cannot be measured yet.",
            ))
            .put(compileCacheWithPrewarm.toJson())

        assertEquals("not_requested", compileCacheOnly.result.nativeValues["embedded_script.prewarm_pool.status"])
        assertEquals("consumed", compileCacheWithPrewarm.result.nativeValues["embedded_script.prewarm_pool.status"])
        assertCompileCacheEnabled(compileCacheOnly.result)
        assertCompileCacheEnabled(compileCacheWithPrewarm.result)

        val report = buildReport(modes)
        val reportFile = writeReport(report)

        assertTrue("performance matrix report was not written: ${reportFile.absolutePath}", reportFile.isFile && reportFile.length() > 0L)
        println("node.performance_matrix.report=${reportFile.absolutePath}")
        println("node.performance_matrix.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/perf")
    }

    private fun runMeasuredMode(
        modeId: String,
        processPrewarm: Boolean,
    ): MatrixRun {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        val workDir = File(context.cacheDir, "node-performance-matrix-$modeId-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create performance matrix work dir: $absolutePath" }
        }
        try {
            val cacheBefore = compileCacheSnapshot("$modeId.cache.before")
            val beforeApp = processSnapshot("$modeId.app.before", Process.myPid())
            val prewarm = if (processPrewarm) {
                NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = PREWARM_TTL_MS)
            } else {
                null
            }
            if (prewarm != null) {
                assertTrue("prewarm=$prewarm", prewarm.started)
                assertEquals("ready", prewarm.status)
                assertTrue("prewarm should be process-only", prewarm.processOnly)
                assertTrue("prewarm must not load Node runtime", !prewarm.runtimeLoaded)
            }
            val prewarmService = prewarm?.let { processSnapshot("$modeId.prewarm.service", it.pid) }
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = matrixScript(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = SCRIPT_TIMEOUT_MS,
                env = mapOf("AUTOJS6_NODE_TIMELINE_TRACE" to "1"),
                moduleSources = mapOf(
                    File(workDir, "helper.js").absolutePath to helperModuleSource(),
                    File(workDir, "packaged-entry.js").absolutePath to packagedEntryModuleSource(),
                ),
            )
            val wallMs = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos() - startedAt).coerceAtLeast(0L)
            val afterApp = processSnapshot("$modeId.app.after", Process.myPid())
            val afterService = processSnapshot("$modeId.service.after", result.pid)
            val cacheAfter = compileCacheSnapshot("$modeId.cache.after")

            NodeEmbeddedTestHarness.assertSucceeded("Phase 8 performance matrix $modeId", result)
            val nodePayload = parseMatrixPayload(result.stdout)
            assertNotNull("matrix payload missing for $modeId\n${result.stdout}", nodePayload)
            assertTrue("first module metric missing for $modeId\n${result.stdout}", nodePayload!!.optLong("firstModuleMs", -1L) >= 0L)
            assertTrue("first bridge metric missing for $modeId\n${result.stdout}", nodePayload.optLong("firstBridgeCallMs", -1L) >= 0L)
            assertTrue("fake bridge call failed for $modeId\n${result.stdout}", nodePayload.optBoolean("bridgeCallOk", false))
            if (prewarm != null) {
                assertEquals("prewarmed process should run the consumed script", prewarm.pid, result.pid)
            }

            return MatrixRun(
                modeId = modeId,
                processPrewarm = processPrewarm,
                wallMs = wallMs,
                result = result,
                nodePayload = nodePayload,
                prewarm = prewarm,
                cacheBefore = cacheBefore,
                cacheAfter = cacheAfter,
                snapshots = listOfNotNull(beforeApp, prewarmService, afterApp, afterService),
            )
        } finally {
            workDir.deleteRecursively()
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    private fun assertCompileCacheEnabled(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) {
        val values = result.nativeValues
        assertEquals("true", values["embedded_script.compile_cache.requested"])
        assertEquals("true", values["embedded_script.compile_cache.enabled"])
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("compile cache performance matrix", result),
            values["embedded_script.compile_cache.status"] in setOf("ENABLED", "ALREADY_ENABLED"),
        )
        assertEquals(NativeNodeEmbeddedRuntimeBridge.COMPILE_CACHE_SCHEMA_VERSION, values["embedded_script.compile_cache.schema"])
        val directory = values["embedded_script.compile_cache.directory"].orEmpty()
        assertTrue("compile cache directory should be app-private: $directory", directory.startsWith(
            NativeNodeEmbeddedRuntimeBridge.compileCacheBaseDir(context).absolutePath,
        ))
    }

    private fun buildReport(modes: JSONArray): JSONObject =
        JSONObject()
            .put("schema", "autojs6-node-phase8-performance-matrix-v1")
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put("reportPull", reportPullJson())
            .put(
                "policy",
                JSONObject()
                    .put("compileCacheUserSwitch", "none")
                    .put("compileCacheUserSwitchReason", "S8-33 keeps compile cache internal and filters user env override keys.")
                    .put("startupSnapshotStatus", "design_gated")
                    .put("startupSnapshotReason", "S8-34 does not enable snapshot generation or load paths.")
                    .put("processPrewarmAxis", "measured_separately"),
            )
            .put("modes", modes)

    private fun unavailableMode(
        modeId: String,
        cache: String,
        snapshot: String,
        reason: String,
    ): JSONObject =
        JSONObject()
            .put("modeId", modeId)
            .put("supported", false)
            .put("status", "unavailable")
            .put("cache", cache)
            .put("startupSnapshot", snapshot)
            .put("reason", reason)
            .put(
                "metrics",
                JSONObject()
                    .put("nativeLoadMs", JSONObject.NULL)
                    .put("environmentCreateMs", JSONObject.NULL)
                    .put("bootstrapMs", JSONObject.NULL)
                    .put("firstModuleMs", JSONObject.NULL)
                    .put("firstBridgeCallMs", JSONObject.NULL)
                    .put("packagedStartupMs", JSONObject.NULL)
                    .put("rssKb", JSONObject.NULL)
                    .put("cacheSizeBytes", JSONObject.NULL),
            )

    private fun writeReport(report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/perf").apply { mkdirs() }
        val reportFile = File(reportDir, "phase8-performance-matrix-${System.currentTimeMillis()}.json")
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

    private fun matrixScript(): String = """
        "nodejs";
        const firstModuleStart = Date.now();
        const helper = require("./helper.js");
        const firstModuleMs = Date.now() - firstModuleStart;

        (async function main() {
          const bridge = require("autojs6:bridge");
          const requests = [];
          bridge.__test.setTransport({
            postMessage(message) {
              const request = JSON.parse(message);
              requests.push({
                module: request.module,
                method: request.method,
                permissions: request.permissions
              });
              setImmediate(() => {
                bridge.__test.receiveMessage(JSON.stringify({
                  id: request.id,
                  ok: true,
                  result: false
                }));
              });
            }
          });

          const device = require("device");
          const bridgeStart = Date.now();
          const screenOn = await device.isScreenOn({ timeoutMs: 1000 });
          const firstBridgeCallMs = Date.now() - bridgeStart;
          const packagedGraphStart = Date.now();
          const packaged = require("./packaged-entry.js");
          const packagedGraphModuleMs = Date.now() - packagedGraphStart;
          const memory = process.memoryUsage();
          const payload = {
            node: process.version,
            versions: process.versions,
            arch: process.arch,
            platform: process.platform,
            helperValue: helper.value,
            packagedValue: packaged.value,
            firstModuleMs,
            firstBridgeCallMs,
            bridgeCallOk: screenOn === false &&
              requests.length === 1 &&
              requests[0].module === "device" &&
              requests[0].method === "isScreenOn" &&
              requests[0].permissions.indexOf("device") >= 0 &&
              bridge.__test.pendingCount() === 0,
            packagedGraphModuleMs,
            memory: {
              rss: memory.rss,
              heapTotal: memory.heapTotal,
              heapUsed: memory.heapUsed,
              external: memory.external,
              arrayBuffers: memory.arrayBuffers
            }
          };
          console.log("$NODE_PAYLOAD_PREFIX" + JSON.stringify(payload));
        })().catch(error => {
          console.error(error && (error.stack || error.message) || String(error));
          process.exitCode = 1;
        });
    """.trimIndent()

    private fun helperModuleSource(): String = """
        const crypto = require("crypto");
        module.exports = {
          value: crypto.createHash("sha256").update("phase8-performance-matrix").digest("hex").slice(0, 12)
        };
    """.trimIndent()

    private fun packagedEntryModuleSource(): String = """
        const helper = require("./helper.js");
        module.exports = {
          value: "packaged:" + helper.value
        };
    """.trimIndent()

    private fun parseMatrixPayload(stdout: String): JSONObject? =
        stdout
            .lineSequence()
            .mapNotNull { line ->
                val markerIndex = line.indexOf(NODE_PAYLOAD_PREFIX)
                if (markerIndex < 0) null else line.substring(markerIndex + NODE_PAYLOAD_PREFIX.length).trim()
            }
            .firstOrNull()
            ?.let { JSONObject(it) }

    private fun compileCacheSnapshot(label: String): CompileCacheSnapshot {
        val baseDir = NativeNodeEmbeddedRuntimeBridge.compileCacheBaseDir(context)
        return CompileCacheSnapshot(
            label = label,
            baseDir = baseDir.absolutePath,
            exists = baseDir.exists(),
            totalBytes = baseDir.safeTotalBytes(),
            fileCount = baseDir.safeFileCount(),
        )
    }

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

    private fun File.safeTotalBytes(): Long? =
        runCatching {
            if (!exists()) {
                return@runCatching 0L
            }
            var total = 0L
            walkTopDown().forEach { file ->
                if (file.isFile) {
                    total += file.length()
                }
            }
            total
        }.getOrNull()

    private fun File.safeFileCount(): Int? =
        runCatching {
            if (!exists()) {
                return@runCatching 0
            }
            var count = 0
            walkTopDown().forEach { file ->
                if (file.isFile) {
                    count += 1
                }
            }
            count
        }.getOrNull()

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

    private data class MatrixRun(
        val modeId: String,
        val processPrewarm: Boolean,
        val wallMs: Long,
        val result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        val nodePayload: JSONObject,
        val prewarm: NativeNodeEmbeddedScriptService.Companion.PrewarmRuntimeResult?,
        val cacheBefore: CompileCacheSnapshot,
        val cacheAfter: CompileCacheSnapshot,
        val snapshots: List<ProcessSnapshot>,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("modeId", modeId)
                .put("supported", true)
                .put("cache", "compile-cache")
                .put("startupSnapshot", "disabled")
                .put("processPrewarm", prewarmJson())
                .put(
                    "metrics",
                    JSONObject()
                        .put("wallMs", wallMs)
                        .put("resultElapsedMs", result.elapsedMs)
                        .putNullable("nativeLoadMs", result.nativeValues.nativeLong("load.duration.ms"))
                        .putNullable("environmentCreateMs", result.nativeValues.nativeLong("timing.environment_create.ms"))
                        .putNullable("bootstrapMs", result.bootstrapMs())
                        .putNullable("firstModuleMs", nodePayload.optNullableLong("firstModuleMs"))
                        .putNullable("firstBridgeCallMs", nodePayload.optNullableLong("firstBridgeCallMs"))
                        .put("packagedStartupMs", JSONObject.NULL)
                        .put("packagedStartupStatus", "not_measured_in_embedded_instrumentation")
                        .putNullable("packagedGraphModuleMs", nodePayload.optNullableLong("packagedGraphModuleMs"))
                        .putNullable("serviceRssKb", snapshots.firstOrNull { it.label.endsWith(".service.after") }?.rssKb)
                        .putNullable("appRssKb", snapshots.firstOrNull { it.label.endsWith(".app.after") }?.rssKb)
                        .putNullable("cacheSizeBytes", cacheAfter.totalBytes)
                        .putNullable("cacheFileCount", cacheAfter.fileCount)
                        .putNullable("timelineEventCount", result.nativeValues["embedded_script.timeline_event_count"]?.toIntOrNull()),
                )
                .put(
                    "compileCache",
                    JSONObject()
                        .put("requested", result.nativeValues["embedded_script.compile_cache.requested"] == "true")
                        .put("enabled", result.nativeValues["embedded_script.compile_cache.enabled"] == "true")
                        .put("status", result.nativeValues["embedded_script.compile_cache.status"].orEmpty())
                        .put("schema", result.nativeValues["embedded_script.compile_cache.schema"].orEmpty())
                        .put("directory", result.nativeValues["embedded_script.compile_cache.directory"].orEmpty())
                        .put("hitMissStatus", result.nativeValues["embedded_script.compile_cache.hit_miss_status"].orEmpty())
                        .put("customLoader", result.nativeValues["embedded_script.compile_cache.custom_loader"] == "true"),
                )
                .put("cacheBefore", cacheBefore.toJson())
                .put("cacheAfter", cacheAfter.toJson())
                .put("snapshots", JSONArray(snapshots.map { it.toJson() }))
                .put("nodePayload", nodePayload)
                .put("nativeValues", JSONObject(result.nativeValues.toSortedMap()))

        private fun prewarmJson(): JSONObject =
            if (!processPrewarm) {
                JSONObject()
                    .put("requested", false)
                    .put("status", result.nativeValues["embedded_script.prewarm_pool.status"].orEmpty())
                    .put("consumed", result.nativeValues["embedded_script.prewarm_pool.consumed"] == "true")
            } else {
                JSONObject()
                    .put("requested", true)
                    .put("prewarmStarted", prewarm?.started ?: false)
                    .put("prewarmStatus", prewarm?.status.orEmpty())
                    .put("prewarmReason", prewarm?.reason.orEmpty())
                    .put("prewarmPid", prewarm?.pid ?: JSONObject.NULL)
                    .put("processOnly", prewarm?.processOnly ?: false)
                    .put("runtimeLoadedBeforeExecution", prewarm?.runtimeLoaded ?: true)
                    .put("consumed", result.nativeValues["embedded_script.prewarm_pool.consumed"] == "true")
                    .put("executionStatus", result.nativeValues["embedded_script.prewarm_pool.status"].orEmpty())
                    .put("executionPid", result.pid)
                    .putNullable("ageMs", result.nativeValues["embedded_script.prewarm_pool.age_ms"]?.toLongOrNull())
            }
    }

    private data class CompileCacheSnapshot(
        val label: String,
        val baseDir: String,
        val exists: Boolean,
        val totalBytes: Long?,
        val fileCount: Int?,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("label", label)
                .put("baseDir", baseDir)
                .put("exists", exists)
                .putNullable("totalBytes", totalBytes)
                .putNullable("fileCount", fileCount)
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
                .putNullable("rssKb", rssKb)
                .putNullable("vmSizeKb", vmSizeKb)
                .putNullable("threadCount", threadCount)
                .putNullable("fdCount", fdCount)
                .putNullable("totalPssKb", totalPssKb)
                .putNullable("dalvikPssKb", dalvikPssKb)
                .putNullable("nativePssKb", nativePssKb)
                .putNullable("otherPssKb", otherPssKb)
    }

    private companion object {
        private const val SCRIPT_TIMEOUT_MS = 15_000L
        private const val PREWARM_TTL_MS = 10_000L
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/perf"
        private const val NODE_PAYLOAD_PREFIX = "perf.matrix="
    }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun JSONObject.optNullableLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

private fun Map<String, String>.nativeLong(name: String): Long? = get(name)?.toLongOrNull()

private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.bootstrapMs(): Long? =
    nativeValues.nativeLong("timing.bootstrap_script.ms") ?: nativeValues.nativeLong("timing.bootstrap.ms")
