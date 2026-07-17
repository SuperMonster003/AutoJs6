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
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("embedded Node persistent-runtime performance benchmark")

        val samplePlan = performanceSamplePlan()
        val cycles = mutableListOf<PerformanceCycle>()
        val snapshots = mutableListOf<ProcessSnapshot>()
        val beforeScript = processSnapshot("beforeScript", Process.myPid()).also(snapshots::add)

        repeat(samplePlan.coldCycles) { cycleIndex ->
            if (cycleIndex > 0) {
                NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            }
            snapshots += processSnapshot("cycle${cycleIndex + 1}.beforeCold.app", Process.myPid())

            val cold = runMeasured("cold-${cycleIndex + 1}")
            NodeEmbeddedTestHarness.assertSucceeded("cold performance run ${cycleIndex + 1}", cold.result)
            assertClientResultDeliveryIsNonBlocking(cold)
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterCold.app", Process.myPid())
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterCold.service", cold.result.pid)

            val warmRuns = mutableListOf<MeasuredRun>()
            var previous = cold
            repeat(samplePlan.runtimeWarmSamplesPerCold) { warmIndex ->
                val warm = runMeasured("runtime-warm-${cycleIndex + 1}-${warmIndex + 1}")
                NodeEmbeddedTestHarness.assertSucceeded(
                    "runtime-warm performance run ${cycleIndex + 1}.${warmIndex + 1}",
                    warm.result,
                )
                assertClientResultDeliveryIsNonBlocking(warm)
                assertSameProcessPoolSlot(cold, warm)
                assertRuntimeWarmReuse(cold, previous, warm)
                warmRuns += warm
                previous = warm
            }
            val lastWarm = warmRuns.last()
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterWarm.app", Process.myPid())
            snapshots += processSnapshot("cycle${cycleIndex + 1}.afterWarm.service", lastWarm.result.pid)
            cycles += PerformanceCycle(cycleIndex + 1, cold, warmRuns)
        }

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        val afterDestroy = processSnapshot("afterDestroy", Process.myPid()).also(snapshots::add)

        cycles.flatMap { it.runs }.forEach { run ->
            assertTrue("${run.label} did not print perf payload\n${run.result.stdout}", run.nodePayload != null)
        }

        val report = buildReport(
            beforeScript = beforeScript,
            afterDestroy = afterDestroy,
            samplePlan = samplePlan,
            cycles = cycles,
            snapshots = snapshots,
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
        afterDestroy: ProcessSnapshot,
        samplePlan: PerformanceSamplePlan,
        cycles: List<PerformanceCycle>,
        snapshots: List<ProcessSnapshot>,
    ): JSONObject {
        val coldRuns = cycles.map { it.cold }
        val warmRuns = cycles.flatMap { it.runtimeWarm }
        val cold = coldRuns.first()
        val warm = warmRuns.first()
        val afterFirstScript = snapshots.first { it.label == "cycle1.afterCold.app" }
        val coldLoadMs = cold.result.nativeValues["load.duration.ms"]?.toLongOrNull()
        val warmLoadMs = warm.result.nativeValues["load.duration.ms"]?.toLongOrNull()
        val appRssDeltaAfterFirstScript = afterFirstScript.rssKb?.minusOrNull(beforeScript.rssKb)
        val appRssDeltaAfterDestroy = afterDestroy.rssKb?.minusOrNull(beforeScript.rssKb)
        val appThreadDeltaAfterDestroy = afterDestroy.threadCount?.minusOrNull(beforeScript.threadCount)
        val appFdDeltaAfterDestroy = afterDestroy.fdCount?.minusOrNull(beforeScript.fdCount)

        return JSONObject()
            .put("schema", "autojs6-node-performance-v3")
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put("reportPull", reportPullJson())
            .put("samplePlan", samplePlan.toJson())
            .put(
                "metrics",
                JSONObject()
                    .put("serviceProcessStartTimeMs", cold.wallMs)
                    .put("serviceProcessStartTimeNote", "Measured as cold runScript wall time, including service start, bind, native load, script execution, and result return.")
                    .put("libnodeLoadMs", coldLoadMs ?: JSONObject.NULL)
                    .put("libnodeLoadWarmMs", warmLoadMs ?: JSONObject.NULL)
                    .put("libnodeLoadMeasurable", coldLoadMs != null || warmLoadMs != null)
                    .put("firstScriptExecutionWallMs", cold.result.elapsedMs)
                    .put("runtimeWarmScriptExecutionWallMs", warm.result.elapsedMs)
                    .put("coldRunWallMs", cold.wallMs)
                    .put("runtimeWarmRunWallMs", warm.wallMs)
                    .put("warmRunWallMs", warm.wallMs)
                    .put("appRssDeltaAfterFirstScriptKb", appRssDeltaAfterFirstScript ?: JSONObject.NULL)
                    .put("appRssDeltaAfterDestroyKb", appRssDeltaAfterDestroy ?: JSONObject.NULL)
                    .put("appThreadDeltaAfterDestroy", appThreadDeltaAfterDestroy ?: JSONObject.NULL)
                    .put("appFdDeltaAfterDestroy", appFdDeltaAfterDestroy ?: JSONObject.NULL),
            )
            .put("percentiles", percentileReport(coldRuns, warmRuns))
            .put("snapshots", JSONArray(snapshots.map { it.toJson() }))
            .put("cycles", JSONArray(cycles.map { it.toJson() }))
            .put("runs", JSONArray(cycles.flatMap { it.runs }.map { it.toJson() }))
            .put(
                "thresholds",
                JSONObject()
                    .put("enabled", instrumentationBoolean(ARG_FAIL_ON_THRESHOLD, false))
                    .put("maxColdWallMs", instrumentationLong(ARG_MAX_COLD_WALL_MS))
                    .put("maxWarmWallMs", instrumentationLong(ARG_MAX_WARM_WALL_MS))
                    .put("maxColdP50Ms", instrumentationLong(ARG_MAX_COLD_P50_MS))
                    .put("maxRuntimeWarmP50Ms", instrumentationLong(ARG_MAX_RUNTIME_WARM_P50_MS))
                    .put("maxRuntimeWarmP95Ms", instrumentationLong(ARG_MAX_RUNTIME_WARM_P95_MS))
                    .put("maxResultToReturnP95Ms", instrumentationLong(ARG_MAX_RESULT_TO_RETURN_P95_MS))
                    .put("maxAppRssDeltaAfterDestroyKb", instrumentationLong(ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB))
                    .put("maxServiceRssKb", instrumentationLong(ARG_MAX_SERVICE_RSS_KB)),
            )
    }

    private fun percentileReport(coldRuns: List<MeasuredRun>, warmRuns: List<MeasuredRun>): JSONObject =
        JSONObject()
            .put("method", "nearest-rank")
            .put("cold", runDistributionJson(coldRuns))
            .put("runtimeWarm", runDistributionJson(warmRuns))

    private fun runDistributionJson(runs: List<MeasuredRun>): JSONObject =
        JSONObject()
            .put("sampleCount", runs.size)
            .put("wallMs", distributionJson(runs.map { it.wallMs }))
            .put("resultElapsedMs", distributionJson(runs.map { it.result.elapsedMs }))
            .put(
                "resultToReturnMs",
                distributionJson(runs.mapNotNull { it.result.nativeValues[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull() }),
            )

    private fun distributionJson(values: List<Long>): JSONObject {
        val sorted = values.sorted()
        return JSONObject()
            .put("count", sorted.size)
            .put("min", sorted.firstOrNull() ?: JSONObject.NULL)
            .put("p50", nearestRank(sorted, 50) ?: JSONObject.NULL)
            .put("p95", nearestRank(sorted, 95) ?: JSONObject.NULL)
            .put("max", sorted.lastOrNull() ?: JSONObject.NULL)
            .put("mean", if (sorted.isEmpty()) JSONObject.NULL else sorted.average())
            .put("samples", JSONArray(values))
    }

    private fun nearestRank(sorted: List<Long>, percentile: Int): Long? {
        if (sorted.isEmpty()) return null
        val rank = ((sorted.size * percentile + 99) / 100).coerceIn(1, sorted.size)
        return sorted[rank - 1]
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
        val percentiles = report.getJSONObject("percentiles")
        val cold = percentiles.getJSONObject("cold")
        val runtimeWarm = percentiles.getJSONObject("runtimeWarm")
        assertBelow(
            "cold wall p50",
            cold.getJSONObject("wallMs").optNullableLong("p50"),
            instrumentationLong(ARG_MAX_COLD_P50_MS),
        )
        assertBelow(
            "runtime-warm wall p50",
            runtimeWarm.getJSONObject("wallMs").optNullableLong("p50"),
            instrumentationLong(ARG_MAX_RUNTIME_WARM_P50_MS),
        )
        assertBelow(
            "runtime-warm wall p95",
            runtimeWarm.getJSONObject("wallMs").optNullableLong("p95"),
            instrumentationLong(ARG_MAX_RUNTIME_WARM_P95_MS),
        )
        assertBelow(
            "cold result-to-return p95",
            cold.getJSONObject("resultToReturnMs").optNullableLong("p95"),
            instrumentationLong(ARG_MAX_RESULT_TO_RETURN_P95_MS),
        )
        assertBelow(
            "runtime-warm result-to-return p95",
            runtimeWarm.getJSONObject("resultToReturnMs").optNullableLong("p95"),
            instrumentationLong(ARG_MAX_RESULT_TO_RETURN_P95_MS),
        )
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
                if (snapshot.optString("label").contains("service", ignoreCase = true)) {
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

    private fun assertClientResultDeliveryIsNonBlocking(run: MeasuredRun) {
        val resultToReturnMs = run.result.nativeValues[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull()
        val processSettleDeferred = run.result.nativeValues[CLIENT_PROCESS_SETTLE_DEFERRED_KEY]?.toBooleanStrictOrNull()
        assertTrue(
            "${run.label} result-to-return timing is missing: ${run.result.nativeValues}",
            resultToReturnMs != null,
        )
        assertTrue(
            "${run.label} result delivery blocked for process settling: ${resultToReturnMs}ms",
            resultToReturnMs!! <= MAX_CLIENT_RESULT_TO_RETURN_MS,
        )
        assertTrue(
            "${run.label} still uses the one-shot process-settle safety window: ${run.result.nativeValues}",
            processSettleDeferred == false,
        )
    }

    private fun assertSameProcessPoolSlot(cold: MeasuredRun, warm: MeasuredRun) {
        val coldSlot = cold.result.nativeValues[PROCESS_POOL_SELECTED_SLOT_KEY]
        val warmSlot = warm.result.nativeValues[PROCESS_POOL_SELECTED_SLOT_KEY]
        assertTrue("cold process-pool slot is missing: ${cold.result.nativeValues}", !coldSlot.isNullOrBlank())
        assertTrue("warm process-pool slot is missing: ${warm.result.nativeValues}", !warmSlot.isNullOrBlank())
        assertTrue("cold/warm runs used different process-pool slots: cold=$coldSlot warm=$warmSlot", coldSlot == warmSlot)
    }

    private fun assertRuntimeWarmReuse(cold: MeasuredRun, previous: MeasuredRun, warm: MeasuredRun) {
        val coldValues = cold.result.nativeValues
        val previousValues = previous.result.nativeValues
        val warmValues = warm.result.nativeValues
        assertTrue("cold/runtime-warm runs used different service pids: cold=${cold.result.pid} warm=${warm.result.pid}", cold.result.pid == warm.result.pid)

        val coldSessionGeneration = coldValues[PROCESS_SESSION_GENERATION_KEY]?.toLongOrNull()
        val warmSessionGeneration = warmValues[PROCESS_SESSION_GENERATION_KEY]?.toLongOrNull()
        assertTrue("cold session generation is missing: $coldValues", coldSessionGeneration != null)
        assertTrue("runtime-warm session generation is missing: $warmValues", warmSessionGeneration != null)
        assertTrue(
            "cold/runtime-warm session generations differ: cold=$coldSessionGeneration warm=$warmSessionGeneration",
            coldSessionGeneration == warmSessionGeneration,
        )

        val coldRuntimeGeneration = coldValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull()
        val warmRuntimeGeneration = warmValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull()
        assertTrue("cold process runtime generation is missing: $coldValues", coldRuntimeGeneration != null)
        assertTrue("runtime-warm process runtime generation is missing: $warmValues", warmRuntimeGeneration != null)
        assertTrue(
            "cold/runtime-warm process runtime generations differ: cold=$coldRuntimeGeneration warm=$warmRuntimeGeneration",
            coldRuntimeGeneration == warmRuntimeGeneration,
        )

        val coldInitCount = coldValues[PROCESS_RUNTIME_INIT_COUNT_KEY]?.toLongOrNull()
        val warmInitCount = warmValues[PROCESS_RUNTIME_INIT_COUNT_KEY]?.toLongOrNull()
        assertTrue("cold process runtime init count is missing: $coldValues", coldInitCount != null)
        assertTrue("runtime-warm process runtime init count is missing: $warmValues", warmInitCount != null)
        assertTrue("process runtime initialized more than once: cold=$coldInitCount warm=$warmInitCount", coldInitCount == 1L && warmInitCount == coldInitCount)

        val previousSequence = previousValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
        val warmSequence = warmValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
        assertTrue("previous process runtime execution sequence is missing: $previousValues", previousSequence != null)
        assertTrue("runtime-warm process runtime execution sequence is missing: $warmValues", warmSequence != null)
        assertTrue(
            "process runtime execution sequence did not advance once: previous=$previousSequence warm=$warmSequence",
            previousSequence != null && warmSequence == previousSequence + 1L,
        )
        assertTrue("runtime-warm execution did not reuse the process runtime: $warmValues", warmValues[PROCESS_RUNTIME_REUSED_KEY] == "true")
        assertTrue("previous execution teardown was not clean: $previousValues", previousValues[PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY] == "true")
        assertTrue("runtime-warm execution teardown was not clean: $warmValues", warmValues[PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY] == "true")
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

    private fun performanceSamplePlan(): PerformanceSamplePlan {
        val arguments = InstrumentationRegistry.getArguments()
        val coldCycles = arguments.getString(ARG_COLD_CYCLES)
            ?.toIntOrNull()
            ?: DEFAULT_COLD_CYCLES
        val warmSamplesPerCold = arguments.getString(ARG_RUNTIME_WARM_SAMPLES_PER_COLD)
            ?.toIntOrNull()
            ?: if (coldCycles == 1) DEFAULT_RUNTIME_WARM_SAMPLES else 1
        require(coldCycles in 1..MAX_COLD_CYCLES) {
            "$ARG_COLD_CYCLES must be in 1..$MAX_COLD_CYCLES, actual=$coldCycles"
        }
        require(warmSamplesPerCold in 1..MAX_RUNTIME_WARM_SAMPLES_PER_COLD) {
            "$ARG_RUNTIME_WARM_SAMPLES_PER_COLD must be in 1..$MAX_RUNTIME_WARM_SAMPLES_PER_COLD, actual=$warmSamplesPerCold"
        }
        return PerformanceSamplePlan(coldCycles, warmSamplesPerCold)
    }

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

    private data class PerformanceSamplePlan(
        val coldCycles: Int,
        val runtimeWarmSamplesPerCold: Int,
    ) {
        val totalExecutions: Int
            get() = coldCycles * (runtimeWarmSamplesPerCold + 1)

        fun toJson(): JSONObject =
            JSONObject()
                .put("coldCycles", coldCycles)
                .put("runtimeWarmSamplesPerCold", runtimeWarmSamplesPerCold)
                .put("coldSampleCount", coldCycles)
                .put("runtimeWarmSampleCount", coldCycles * runtimeWarmSamplesPerCold)
                .put("totalExecutions", totalExecutions)
                .put("defaultPlan", coldCycles == DEFAULT_COLD_CYCLES && runtimeWarmSamplesPerCold == DEFAULT_RUNTIME_WARM_SAMPLES)
                .put(
                    "coldPercentileNote",
                    if (coldCycles == 1) {
                        "Single cold sample; set $ARG_COLD_CYCLES to 30 for an acceptance-grade cold percentile."
                    } else {
                        "Each cold sample starts after explicit persistent-session retirement."
                    },
                )
    }

    private data class PerformanceCycle(
        val index: Int,
        val cold: MeasuredRun,
        val runtimeWarm: List<MeasuredRun>,
    ) {
        val runs: List<MeasuredRun>
            get() = listOf(cold) + runtimeWarm

        fun toJson(): JSONObject =
            JSONObject()
                .put("index", index)
                .put("cold", cold.toJson())
                .put("runtimeWarm", JSONArray(runtimeWarm.map { it.toJson() }))
    }

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
        private const val CLIENT_RESULT_TO_RETURN_KEY = "embedded_script.client.result_to_return_ms"
        private const val CLIENT_PROCESS_SETTLE_DEFERRED_KEY = "embedded_script.client.process_settle_deferred"
        private const val PROCESS_POOL_SELECTED_SLOT_KEY = "embedded_script.process_pool.selected_slot_id"
        private const val PROCESS_SESSION_GENERATION_KEY = "embedded_script.process_session.generation"
        private const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        private const val PROCESS_RUNTIME_INIT_COUNT_KEY = "process_runtime.init_count"
        private const val PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY = "process_runtime.execution_sequence"
        private const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        private const val PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY = "process_runtime.teardown_clean"
        private const val MAX_CLIENT_RESULT_TO_RETURN_MS = 250L
        private const val SCRIPT_TIMEOUT_MS = 10_000L
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/perf"
        private const val NODE_PAYLOAD_PREFIX = "perf.node="
        private const val ARG_FAIL_ON_THRESHOLD = "autojs.nodejs.perf.failOnThreshold"
        private const val ARG_MAX_COLD_WALL_MS = "autojs.nodejs.perf.maxColdWallMs"
        private const val ARG_MAX_WARM_WALL_MS = "autojs.nodejs.perf.maxWarmWallMs"
        private const val ARG_MAX_COLD_P50_MS = "autojs.nodejs.perf.maxColdP50Ms"
        private const val ARG_MAX_RUNTIME_WARM_P50_MS = "autojs.nodejs.perf.maxRuntimeWarmP50Ms"
        private const val ARG_MAX_RUNTIME_WARM_P95_MS = "autojs.nodejs.perf.maxRuntimeWarmP95Ms"
        private const val ARG_MAX_RESULT_TO_RETURN_P95_MS = "autojs.nodejs.perf.maxResultToReturnP95Ms"
        private const val ARG_MAX_APP_RSS_DELTA_AFTER_DESTROY_KB = "autojs.nodejs.perf.maxAppRssDeltaAfterDestroyKb"
        private const val ARG_MAX_SERVICE_RSS_KB = "autojs.nodejs.perf.maxServiceRssKb"
        private const val ARG_COLD_CYCLES = "autojs.nodejs.perf.coldCycles"
        private const val ARG_RUNTIME_WARM_SAMPLES_PER_COLD = "autojs.nodejs.perf.runtimeWarmSamplesPerCold"
        private const val DEFAULT_COLD_CYCLES = 1
        private const val DEFAULT_RUNTIME_WARM_SAMPLES = 29
        private const val MAX_COLD_CYCLES = 50
        private const val MAX_RUNTIME_WARM_SAMPLES_PER_COLD = 100
    }
}
