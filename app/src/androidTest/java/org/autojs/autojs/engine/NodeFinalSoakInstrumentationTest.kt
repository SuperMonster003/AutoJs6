package org.autojs.autojs.engine

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeFinalSoakInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun fullHeavyResourceSoakProducesArchiveReadyReport() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node final heavy resource soak")

        val kind = argument(ARG_KIND, "quick").lowercase()
        val durationMs = durationMsFor(kind)
        val sampleIntervalMs = argumentLong(ARG_SAMPLE_INTERVAL_MS, DEFAULT_SAMPLE_INTERVAL_MS).coerceAtLeast(1_000L)
        val cycleDelayMs = argumentLong(ARG_CYCLE_DELAY_MS, DEFAULT_CYCLE_DELAY_MS).coerceAtLeast(0L)
        val startWallMs = System.currentTimeMillis()
        val startElapsedMs = SystemClock.elapsedRealtime()
        val deadlineMs = startElapsedMs + durationMs
        val workDir = File(context.cacheDir, "node-final-soak").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create final soak directory: $absolutePath" }
        }
        writeProjectPermissions(workDir, "database")

        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "final-soak-$kind",
                sourceName = "main.cjs",
                cwd = workDir.absolutePath,
            ),
        )
        val samples = JSONArray()
        val failures = JSONArray()
        val report = JSONObject()
            .put("schema", "autojs6-node-final-soak-run-v1")
            .put("kind", kind)
            .put("status", "running")
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put("configuration", JSONObject()
                .put("durationMs", durationMs)
                .put("sampleIntervalMs", sampleIntervalMs)
                .put("cycleDelayMs", cycleDelayMs)
                .put("thresholds", thresholdJson()))
            .put("samples", samples)
            .put("failures", failures)
            .put("monotonicLeaks", false)
            .put("orphanProcess", false)
            .put("unrecoverableRuntime", false)

        var cycles = 0
        var nextSampleAt = startElapsedMs
        var thrown: Throwable? = null
        try {
            samples.put(snapshot("final-soak.start", provider))
            while (SystemClock.elapsedRealtime() < deadlineMs) {
                cycles += 1
                val libuvHandleCount = runEmbeddedCycle(workDir, cycles)
                runDatabaseCycle(provider, cycles)
                assertNoActiveProviderResources(provider, "cycle-$cycles")
                val now = SystemClock.elapsedRealtime()
                if (now >= nextSampleAt || now + cycleDelayMs >= deadlineMs) {
                    samples.put(snapshot("final-soak.cycle.$cycles", provider, libuvHandleCount))
                    nextSampleAt = now + sampleIntervalMs
                }
                if (cycleDelayMs > 0L && now + cycleDelayMs < deadlineMs) {
                    SystemClock.sleep(cycleDelayMs)
                }
            }
            samples.put(snapshot("final-soak.end", provider))
            val leakFailures = analyzeMonotonicLeaks(samples)
            for (index in 0 until leakFailures.length()) {
                failures.put(leakFailures.getJSONObject(index))
            }
            val durationActualMs = SystemClock.elapsedRealtime() - startElapsedMs
            report
                .put("status", if (failures.length() == 0) "passed" else "failed")
                .put("cycles", cycles)
                .put("startedAtEpochMs", startWallMs)
                .put("endedAtEpochMs", System.currentTimeMillis())
                .put("durationMs", durationActualMs)
                .put("durationHours", durationActualMs.toDouble() / MILLIS_PER_HOUR.toDouble())
                .put("monotonicLeaks", leakFailures.length() > 0)
                .put("orphanProcess", hasOrphanResources(provider))
                .put("unrecoverableRuntime", false)
        } catch (error: Throwable) {
            thrown = error
            failures.put(
                JSONObject()
                    .put("scenario", "final-soak")
                    .put("metric", "exception")
                    .put("message", error.message.orEmpty())
                    .put("type", error::class.java.name),
            )
            val durationActualMs = SystemClock.elapsedRealtime() - startElapsedMs
            report
                .put("status", "failed")
                .put("cycles", cycles)
                .put("startedAtEpochMs", startWallMs)
                .put("endedAtEpochMs", System.currentTimeMillis())
                .put("durationMs", durationActualMs)
                .put("durationHours", durationActualMs.toDouble() / MILLIS_PER_HOUR.toDouble())
                .put("unrecoverableRuntime", true)
        } finally {
            provider.destroy(NodeBridgeError.processDead("final soak finished"))
            workDir.deleteRecursively()
            val reportFile = writeReport(kind, report)
            println("node.final_soak.report=${reportFile.absolutePath}")
            println("node.final_soak.public=${report.optString("devicePublicReportPath")}")
            println("node.final_soak.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/final-soak-runs")
        }

        thrown?.let { throw it }
        assertTrue("Final soak failures detected:\n${failures.toString(2)}", failures.length() == 0)
    }

    private fun runEmbeddedCycle(workDir: File, cycle: Int): Int? {
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const fs = require("node:fs");
                function handleCount() {
                  try {
                    return typeof process._getActiveHandles === "function" ? process._getActiveHandles().length : -1;
                  } catch (error) {
                    return -2;
                  }
                }
                const before = handleCount();
                const timer = setInterval(() => {}, 1000);
                const during = handleCount();
                clearInterval(timer);
                fs.writeFileSync("./cycle-$cycle.txt", "cycle-$cycle", "utf8");
                const text = fs.readFileSync("./cycle-$cycle.txt", "utf8");
                if (text !== "cycle-$cycle") throw new Error("cycle file mismatch");
                fs.unlinkSync("./cycle-$cycle.txt");
                setImmediate(() => {
                  console.log("final.soak.libuv=" + [before, during, handleCount()].join("|"));
                });
            """.trimIndent(),
            sourceName = "<final-soak-$cycle.js>",
            workingDirectory = workDir,
            timeoutMs = 15_000L,
        )
        NodeEmbeddedTestHarness.assertSucceeded("final soak embedded cycle $cycle", result)
        return parsePipeValues(result.stdout, "final.soak.libuv", expectedCount = 3).last().toIntOrNull()
    }

    private fun runDatabaseCycle(provider: AndroidNodeBridgeCapabilityProvider, cycle: Int) {
        val open = dispatch(
            provider = provider,
            module = "database",
            method = "open",
            argsJson = JSONArray().put(JSONObject().put("name", "final_soak_$cycle")).toString(),
            permissions = listOf("database"),
        )
        assertTrue(open.toString(), open.ok)
        val handleId = JSONObject(open.resultJson.orEmpty()).getString("id")
        try {
            assertOk(databaseDispatch(provider, "exec", handleId, "DROP TABLE IF EXISTS items"))
            assertOk(databaseDispatch(provider, "exec", handleId, "CREATE TABLE items(name TEXT, value INTEGER)"))
            assertOk(databaseDispatch(provider, "exec", handleId, "INSERT INTO items(name,value) VALUES('cycle',$cycle)"))
            val query = databaseDispatch(provider, "get", handleId, "SELECT COUNT(*) AS count FROM items")
            assertOk(query)
            assertEquals(1, JSONObject(query.resultJson.orEmpty()).getInt("count"))
        } finally {
            assertOk(
                dispatch(
                    provider = provider,
                    module = "database",
                    method = "close",
                    argsJson = JSONArray().put(handleId).toString(),
                    permissions = listOf("database"),
                ),
            )
        }
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.database_active_handle_count"])
    }

    private fun databaseDispatch(
        provider: AndroidNodeBridgeCapabilityProvider,
        method: String,
        handleId: String,
        sql: String,
    ): NodeBridgeResponse =
        dispatch(
            provider = provider,
            module = "database",
            method = method,
            argsJson = JSONArray().put(handleId).put(sql).put(JSONArray()).toString(),
            permissions = listOf("database"),
        )

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        timeoutMs: Long = 10_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "$module-$method-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = timeoutMs,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method", latch.await(timeoutMs, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }

    private fun snapshot(
        label: String,
        provider: AndroidNodeBridgeCapabilityProvider,
        libuvHandleCount: Int? = null,
    ): JSONObject =
        NodeResourceStressSnapshot.capture(
            label = label,
            bridgeDiagnostics = provider.nativeDiagnostics(),
            libuvHandleCount = libuvHandleCount,
        ).toJson()

    private fun analyzeMonotonicLeaks(samples: JSONArray): JSONArray {
        val failures = JSONArray()
        RUNAWAY_METRICS.forEach { metric ->
            val values = (0 until samples.length())
                .mapNotNull { index -> samples.getJSONObject(index).optNullableLong(metric.name) }
            if (values.size < MIN_RUNAWAY_SAMPLES) {
                return@forEach
            }
            val delta = values.last() - values.first()
            if (delta <= metric.threshold()) {
                return@forEach
            }
            if (!looksMonotonicRunaway(values)) {
                return@forEach
            }
            failures.put(
                JSONObject()
                    .put("scenario", "final-soak")
                    .put("metric", metric.name)
                    .put("message", "Metric increased monotonically beyond threshold.")
                    .put("baseline", values.first())
                    .put("final", values.last())
                    .put("delta", delta)
                    .put("threshold", metric.threshold())
                    .put("values", JSONArray(values)),
            )
        }
        return failures
    }

    private fun looksMonotonicRunaway(values: List<Long>): Boolean {
        val increases = values.zipWithNext().count { (previous, current) -> current > previous }
        val recent = values.takeLast(minOf(values.size, RECENT_SAMPLE_WINDOW))
        val recentIncreases = recent.zipWithNext().count { (previous, current) -> current >= previous }
        return values.last() == values.maxOrNull() &&
                increases >= values.size - 2 &&
                recentIncreases == recent.size - 1
    }

    private fun hasOrphanResources(provider: AndroidNodeBridgeCapabilityProvider): Boolean {
        val diagnostics = provider.nativeDiagnostics()
        return diagnostics["embedded_script.bridge_resource.active_count"] != "0" ||
                diagnostics["embedded_script.database_active_handle_count"] != "0"
    }

    private fun assertNoActiveProviderResources(provider: AndroidNodeBridgeCapabilityProvider, label: String) {
        val diagnostics = provider.nativeDiagnostics()
        assertEquals("$label active_count diagnostics=$diagnostics", "0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("$label active_types diagnostics=$diagnostics", "{}", diagnostics["embedded_script.bridge_resource.active_types"])
        assertEquals("$label close_error_count diagnostics=$diagnostics", "0", diagnostics["embedded_script.bridge_resource.close_error_count"])
        assertEquals("$label database diagnostics=$diagnostics", "0", diagnostics["embedded_script.database_active_handle_count"])
    }

    private fun assertOk(response: NodeBridgeResponse) {
        assertTrue(response.toString(), response.ok)
    }

    private fun parsePipeValues(stdout: String, marker: String, expectedCount: Int): List<String> {
        val line = stdout
            .lineSequence()
            .firstOrNull { it.contains("$marker=") }
            ?: throw AssertionError("Missing $marker in stdout: $stdout")
        val values = line.substringAfter("$marker=").trim().split("|")
        assertEquals("Unexpected $marker count in line: $line", expectedCount, values.size)
        return values
    }

    private fun writeProjectPermissions(workDir: File, vararg permissions: String) {
        val permissionJson = permissions.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        File(workDir, "project.json").writeText(
            """
            {
              "node": {
                "permissions": $permissionJson
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
    }

    private fun writeReport(kind: String, report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/final-soak").apply {
            mkdirs()
        }
        val fileName = "final-soak-$kind-${System.currentTimeMillis()}.json"
        val reportFile = File(reportDir, fileName)
        report
            .put("devicePrivateReportPath", reportFile.absolutePath)
            .put("devicePublicReportPath", "$DEVICE_REPORT_DIR/$fileName")
        val reportText = report.toString(2)
        reportFile.writeText(reportText)
        writePublicReport(fileName, reportText)
        return reportFile
    }

    private fun writePublicReport(fileName: String, reportText: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AutoJs6/nodejs/final-soak")
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
                    "AutoJs6/nodejs/final-soak",
                ).apply { mkdirs() }
                File(reportDir, fileName).writeText(reportText)
                true
            }
        }.getOrDefault(false)

    private fun deviceJson(): JSONObject =
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("primaryAbi", Build.SUPPORTED_ABIS.firstOrNull() ?: JSONObject.NULL)

    private fun durationMsFor(kind: String): Long {
        val explicitMs = arguments.getString(ARG_DURATION_MS)?.toLongOrNull()
        if (explicitMs != null) {
            return explicitMs.coerceAtLeast(1_000L)
        }
        val explicitHours = arguments.getString(ARG_DURATION_HOURS)?.toDoubleOrNull()
        if (explicitHours != null) {
            return (explicitHours * MILLIS_PER_HOUR).toLong().coerceAtLeast(1_000L)
        }
        return if (kind == "final") 24L * MILLIS_PER_HOUR else 8L * MILLIS_PER_HOUR
    }

    private fun thresholdJson(): JSONObject =
        JSONObject().apply {
            RUNAWAY_METRICS.forEach { metric -> put(metric.name, metric.threshold()) }
        }

    private fun argument(name: String, defaultValue: String): String =
        arguments.getString(name) ?: defaultValue

    private fun argumentLong(name: String, defaultValue: Long): Long =
        arguments.getString(name)?.toLongOrNull() ?: defaultValue

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private fun RunawayMetric.threshold(): Long =
        argumentLong(thresholdProperty, defaultThreshold)

    private data class RunawayMetric(
        val name: String,
        val thresholdProperty: String,
        val defaultThreshold: Long,
    )

    private companion object {
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/final-soak"
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val DEFAULT_SAMPLE_INTERVAL_MS = 300_000L
        private const val DEFAULT_CYCLE_DELAY_MS = 30_000L
        private const val MIN_RUNAWAY_SAMPLES = 4
        private const val RECENT_SAMPLE_WINDOW = 4
        private const val ARG_KIND = "autojs.nodejs.finalSoak.kind"
        private const val ARG_DURATION_MS = "autojs.nodejs.finalSoak.durationMs"
        private const val ARG_DURATION_HOURS = "autojs.nodejs.finalSoak.durationHours"
        private const val ARG_SAMPLE_INTERVAL_MS = "autojs.nodejs.finalSoak.sampleIntervalMs"
        private const val ARG_CYCLE_DELAY_MS = "autojs.nodejs.finalSoak.cycleDelayMs"
        private const val ARG_MAX_RSS_DELTA_KB = "autojs.nodejs.finalSoak.maxRssDeltaKb"
        private const val ARG_MAX_JAVA_HEAP_DELTA_KB = "autojs.nodejs.finalSoak.maxJavaHeapDeltaKb"
        private const val ARG_MAX_NATIVE_HEAP_DELTA_KB = "autojs.nodejs.finalSoak.maxNativeHeapDeltaKb"
        private const val ARG_MAX_THREAD_DELTA = "autojs.nodejs.finalSoak.maxThreadDelta"
        private const val ARG_MAX_FD_DELTA = "autojs.nodejs.finalSoak.maxFdDelta"

        private val RUNAWAY_METRICS = listOf(
            RunawayMetric("rssKb", ARG_MAX_RSS_DELTA_KB, 262_144L),
            RunawayMetric("javaHeapKb", ARG_MAX_JAVA_HEAP_DELTA_KB, 131_072L),
            RunawayMetric("nativeHeapKb", ARG_MAX_NATIVE_HEAP_DELTA_KB, 131_072L),
            RunawayMetric("threadCount", ARG_MAX_THREAD_DELTA, 24L),
            RunawayMetric("fdCount", ARG_MAX_FD_DELTA, 64L),
        )
    }
}
