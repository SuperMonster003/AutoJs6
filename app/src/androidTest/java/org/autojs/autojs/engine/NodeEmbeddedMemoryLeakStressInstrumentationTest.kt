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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeEmbeddedMemoryLeakStressInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun repeatedExecutionsDoNotShowRunawayResourceGrowth() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("embedded Node memory leak stress")

        val report = JSONObject()
            .put("schema", "autojs6-node-memory-leak-stress-v1")
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put("configuration", configurationJson())
        val scenarios = JSONArray()
        val failures = JSONArray()
        report.put("scenarios", scenarios)
        report.put("failures", failures)

        var reportFile: File? = null
        try {
            runScenario(
                name = "simple",
                iterations = stressIterations(),
                timeoutMs = SUCCESS_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::simpleScript,
                validator = ::expectSuccess,
            )
            runScenario(
                name = "fsCallback",
                iterations = stressIterations(),
                timeoutMs = SUCCESS_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::fsCallbackScript,
                validator = ::expectSuccess,
            )
            runScenario(
                name = "fsPromises",
                iterations = stressIterations(),
                timeoutMs = SUCCESS_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::fsPromisesScript,
                validator = ::expectSuccess,
            )
            runScenario(
                name = "bridgeToastAppFake",
                iterations = stressIterations(),
                timeoutMs = BRIDGE_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::bridgeToastAppFakeScript,
                validator = ::expectSuccess,
            )
            runScenario(
                name = "imageFakeHandles",
                iterations = imageIterations(),
                timeoutMs = BRIDGE_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::imageFakeHandleScript,
                validator = ::expectSuccess,
            )
            runScenario(
                name = "uncaughtException",
                iterations = faultIterations(),
                timeoutMs = FAULT_SUCCESS_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::uncaughtExceptionScript,
                validator = ::expectUncaughtException,
            )
            runScenario(
                name = "timeout",
                iterations = faultIterations(),
                timeoutMs = TIMEOUT_SCRIPT_TIMEOUT_MS,
                reportScenarios = scenarios,
                failures = failures,
                sourceFactory = ::timeoutScript,
                validator = ::expectTimeout,
                extraSettleMs = TIMEOUT_EXTRA_SETTLE_MS,
            )
        } finally {
            reportFile = writeReports(report)
        }

        val writtenReport = reportFile
        val reportLength = writtenReport.length()
        assertTrue("memory leak stress report was not written", writtenReport.isFile && reportLength > 0L)
        assertTrue(
            "Memory leak stress failures detected. report=${writtenReport.absolutePath}\n${failures.toString(2)}",
            failures.length() == 0,
        )
    }

    private fun runScenario(
        name: String,
        iterations: Int,
        timeoutMs: Long,
        reportScenarios: JSONArray,
        failures: JSONArray,
        sourceFactory: (Int) -> String,
        validator: (String, Int, NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) -> Unit,
        extraSettleMs: Long = 0L,
    ) {
        val workDir = File(context.cacheDir, "node-memory-leak-stress-$name").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create memory leak stress directory: $absolutePath" }
        }
        val scenario = JSONObject()
            .put("name", name)
            .put("iterations", iterations)
            .put("timeoutMs", timeoutMs)
            .put("samples", JSONArray())
            .put("summary", JSONObject())
        val samples = scenario.getJSONArray("samples")
        val summary = scenario.getJSONObject("summary")
        var succeeded = 0
        var expectedFailures = 0
        var unexpectedFailures = 0
        var timedOut = 0
        var maxElapsedMs = 0L
        var totalElapsedMs = 0L

        samples.put(resourceSnapshot("$name.before", 0).toJson())
        for (iteration in 1..iterations) {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = sourceFactory(iteration),
                sourceName = "<memory-leak-$name-$iteration.js>",
                workingDirectory = workDir,
                timeoutMs = timeoutMs,
            )
            totalElapsedMs += result.elapsedMs
            maxElapsedMs = maxOf(maxElapsedMs, result.elapsedMs)
            if (result.succeeded) {
                succeeded += 1
            }
            if (result.timedOut) {
                timedOut += 1
            }
            try {
                validator(name, iteration, result)
                if (!result.succeeded || result.timedOut) {
                    expectedFailures += 1
                }
            } catch (error: AssertionError) {
                unexpectedFailures += 1
                failures.put(
                    failureJson(
                        scenario = name,
                        metric = "scriptResult",
                        message = error.message.orEmpty(),
                        actual = JSONObject()
                            .put("iteration", iteration)
                            .put("succeeded", result.succeeded)
                            .put("timedOut", result.timedOut)
                            .put("errorCode", result.errorCode ?: JSONObject.NULL)
                            .put("errorName", result.errorName ?: JSONObject.NULL)
                            .put("errorMessage", result.errorMessage ?: JSONObject.NULL)
                            .put("stdout", result.stdout.take(MAX_FAILURE_TEXT))
                            .put("stderr", result.stderr.take(MAX_FAILURE_TEXT)),
                    ),
                )
            }
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            if (extraSettleMs > 0L) {
                SystemClock.sleep(extraSettleMs)
            }
            if (iteration % sampleEvery(iterations) == 0 || iteration == iterations) {
                samples.put(resourceSnapshot("$name.after.$iteration", iteration).toJson())
            }
        }
        summary
            .put("succeeded", succeeded)
            .put("expectedFailures", expectedFailures)
            .put("unexpectedFailures", unexpectedFailures)
            .put("timedOut", timedOut)
            .put("maxElapsedMs", maxElapsedMs)
            .put("averageElapsedMs", if (iterations == 0) 0 else totalElapsedMs / iterations)

        analyzeRunaway(name, samples, failures)
        reportScenarios.put(scenario)
        workDir.deleteRecursively()
    }

    private fun expectSuccess(
        scenario: String,
        iteration: Int,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertTrue(
            "$scenario iteration $iteration failed\n${NodeEmbeddedTestHarness.describeResult(scenario, result)}",
            result.succeeded,
        )
        assertFalse("$scenario iteration $iteration timed out", result.timedOut)
    }

    private fun expectTimeout(
        scenario: String,
        iteration: Int,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertFalse("$scenario iteration $iteration unexpectedly succeeded", result.succeeded)
        assertTrue("$scenario iteration $iteration did not time out", result.timedOut)
        assertTrue(
            "$scenario iteration $iteration timeout code=${result.errorCode}",
            result.errorCode == "ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT",
        )
    }

    private fun expectUncaughtException(
        scenario: String,
        iteration: Int,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertFalse("$scenario iteration $iteration unexpectedly succeeded", result.succeeded)
        assertFalse("$scenario iteration $iteration unexpectedly timed out", result.timedOut)
        val combined = result.errorMessage.orEmpty() + "\n" + result.errorStack.orEmpty()
        assertTrue(combined, combined.contains("leak uncaught $iteration"))
    }

    private fun analyzeRunaway(scenario: String, samples: JSONArray, failures: JSONArray) {
        if (!failOnRunaway()) {
            return
        }
        RUNAWAY_METRICS.forEach { metric ->
            val values = (0 until samples.length())
                .mapNotNull { index -> samples.getJSONObject(index).optNullableLong(metric.name) }
            if (values.size < MIN_RUNAWAY_SAMPLES) {
                return@forEach
            }
            val baseline = values.first()
            val final = values.last()
            val delta = final - baseline
            val threshold = metric.threshold()
            if (delta <= threshold) {
                return@forEach
            }
            if (!looksMonotonicRunaway(values)) {
                return@forEach
            }
            failures.put(
                failureJson(
                    scenario = scenario,
                    metric = metric.name,
                    message = "Metric increased monotonically beyond threshold.",
                    actual = JSONObject()
                        .put("baseline", baseline)
                        .put("final", final)
                        .put("delta", delta)
                        .put("threshold", threshold)
                        .put("values", JSONArray(values)),
                ),
            )
        }
    }

    private fun looksMonotonicRunaway(values: List<Long>): Boolean {
        val increases = values.zipWithNext().count { (previous, current) -> current > previous }
        val recent = values.takeLast(minOf(values.size, RECENT_SAMPLE_WINDOW))
        val recentIncreases = recent.zipWithNext().count { (previous, current) -> current >= previous }
        return values.last() == values.maxOrNull() &&
                increases >= values.size - 2 &&
                recentIncreases == recent.size - 1
    }

    private fun resourceSnapshot(label: String, iteration: Int): ResourceSnapshot {
        Runtime.getRuntime().gc()
        System.runFinalization()
        SystemClock.sleep(25L)
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        val pid = Process.myPid()
        return ResourceSnapshot(
            label = label,
            iteration = iteration,
            pid = pid,
            rssKb = readStatusLongKb(pid, "VmRSS:"),
            javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
            javaHeapCommittedKb = runtime.totalMemory() / 1024L,
            nativeHeapKb = Debug.getNativeHeapAllocatedSize() / 1024L,
            totalPssKb = memoryInfo.totalPss,
            dalvikPssKb = memoryInfo.dalvikPss,
            nativePssKb = memoryInfo.nativePss,
            otherPssKb = memoryInfo.otherPss,
            threadCount = File("/proc/$pid/task").safeCountChildren(),
            fdCount = File("/proc/$pid/fd").safeCountChildren(),
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

    private fun simpleScript(iteration: Int): String = """
        "nodejs";
        console.log("leak.simple.$iteration=" + process.version);
    """.trimIndent()

    private fun fsCallbackScript(iteration: Int): String = """
        "nodejs";
        const fs = require("fs");
        fs.writeFile("./callback.txt", "callback-$iteration", "utf8", (writeError) => {
          if (writeError) throw writeError;
          fs.readFile("./callback.txt", "utf8", (readError, text) => {
            if (readError) throw readError;
            console.log("leak.fs.callback.$iteration=" + text);
          });
        });
    """.trimIndent()

    private fun fsPromisesScript(iteration: Int): String = """
        "nodejs";
        (async function main() {
          const fs = require("fs");
          if (!fs.promises) {
            console.log("leak.fs.promises.$iteration=skipped");
            return;
          }
          await fs.promises.writeFile("./promises.txt", "promises-$iteration", "utf8");
          const text = await fs.promises.readFile("./promises.txt", "utf8");
          await fs.promises.stat("./promises.txt");
          console.log("leak.fs.promises.$iteration=" + text);
        })();
    """.trimIndent()

    private fun bridgeToastAppFakeScript(iteration: Int): String = """
        "nodejs";
        (async function main() {
          const bridge = require("autojs6:bridge");
          bridge.__test.setTransport({
            postMessage(message) {
              const request = JSON.parse(message);
              let result = null;
              if (request.module === "app" && (request.method === "launchPackage" || request.method === "launchApp")) {
                result = true;
              }
              setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                id: request.id,
                ok: true,
                result
              })));
            }
          });
          const toast = require("toast");
          const app = require("app");
          await toast.showToast("leak bridge $iteration", { timeoutMs: 1000 });
          await app.launchPackage(app.packageName, { timeoutMs: 1000 });
          console.log("leak.bridge.$iteration=" + bridge.__test.pendingCount());
        })();
    """.trimIndent()

    private fun imageFakeHandleScript(iteration: Int): String = """
        "nodejs";
        (async function main() {
          const bridge = require("autojs6:bridge");
          bridge.__test.setTransport({
            postMessage(message) {
              const request = JSON.parse(message);
              let result = true;
              if (request.method === "captureScreen") {
                result = { id: "capture-$iteration", width: 1080, height: 2400 };
              } else if (request.method === "resize") {
                result = { id: request.args[0].id + ":resize", width: request.args[1].width, height: request.args[1].height };
              } else if (request.method === "clip") {
                result = { id: request.args[0].id + ":clip", width: request.args[1].width, height: request.args[1].height };
              } else if (request.method === "findImage") {
                result = { x: 1, y: 2 };
              }
              setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                id: request.id,
                ok: true,
                result
              })));
            }
          });
          const image = require("image");
          const capture = await image.captureScreen({ timeoutMs: 1000 });
          const clipped = await image.clip(capture, 0, 0, 100, 100, { timeoutMs: 1000 });
          const resized = await image.resize(clipped, 50, 50, { timeoutMs: 1000 });
          await image.findImage(resized, clipped, { timeoutMs: 1000 });
          await image.recycle(capture, { timeoutMs: 1000 });
          await image.recycle(clipped, { timeoutMs: 1000 });
          await image.recycle(resized, { timeoutMs: 1000 });
          console.log("leak.image.$iteration=" + bridge.__test.pendingCount());
        })();
    """.trimIndent()

    private fun timeoutScript(iteration: Int): String = """
        "nodejs";
        setInterval(() => {}, 50);
        console.log("leak.timeout.$iteration=started");
    """.trimIndent()

    private fun uncaughtExceptionScript(iteration: Int): String = """
        "nodejs";
        throw new Error("leak uncaught $iteration");
    """.trimIndent()

    private fun writeReports(report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/perf").apply { mkdirs() }
        val reportFile = File(reportDir, "embedded-node-memory-leak-stress-${System.currentTimeMillis()}.json")
        report
            .put("devicePrivateReportPath", reportFile.absolutePath)
            .put("devicePublicReportPath", "$DEVICE_REPORT_DIR/${reportFile.name}")
        val reportText = report.toString(2)
        reportFile.writeText(reportText)
        writePublicReport(reportFile.name, reportText)
        println("node.memory_leak.report=${reportFile.absolutePath}")
        println("node.memory_leak.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/perf")
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

    private fun configurationJson(): JSONObject =
        JSONObject()
            .put("stressIterations", stressIterations())
            .put("imageIterations", imageIterations())
            .put("faultIterations", faultIterations())
            .put("failOnRunaway", failOnRunaway())
            .put("sampleEvery", sampleEvery(stressIterations()))
            .put("thresholds", JSONObject().apply {
                RUNAWAY_METRICS.forEach { metric -> put(metric.name, metric.threshold()) }
            })

    private fun deviceJson(): JSONObject =
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("device", Build.DEVICE)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("primaryAbi", Build.SUPPORTED_ABIS.firstOrNull() ?: JSONObject.NULL)

    private fun stressIterations(): Int =
        instrumentationInt(ARG_ITERATIONS, DEFAULT_STRESS_ITERATIONS).coerceAtLeast(1)

    private fun imageIterations(): Int =
        instrumentationInt(ARG_IMAGE_ITERATIONS, DEFAULT_IMAGE_ITERATIONS).coerceAtLeast(1)

    private fun faultIterations(): Int =
        instrumentationInt(ARG_FAULT_ITERATIONS, DEFAULT_FAULT_ITERATIONS).coerceAtLeast(1)

    private fun sampleEvery(iterations: Int): Int =
        instrumentationInt(ARG_SAMPLE_EVERY, (iterations / 10).coerceAtLeast(1)).coerceAtLeast(1)

    private fun failOnRunaway(): Boolean =
        instrumentationBoolean(ARG_FAIL_ON_RUNAWAY, true)

    private fun instrumentationInt(name: String, defaultValue: Int): Int =
        arguments.getString(name)?.toIntOrNull() ?: defaultValue

    private fun instrumentationLong(name: String, defaultValue: Long): Long =
        arguments.getString(name)?.toLongOrNull() ?: defaultValue

    private fun instrumentationBoolean(name: String, defaultValue: Boolean): Boolean =
        arguments.getString(name)
            ?.let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
            ?: defaultValue

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    private fun RunawayMetric.threshold(): Long =
        instrumentationLong(thresholdProperty, defaultThreshold)

    private fun failureJson(
        scenario: String,
        metric: String,
        message: String,
        actual: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("scenario", scenario)
            .put("metric", metric)
            .put("message", message)
            .put("actual", actual)

    private data class ResourceSnapshot(
        val label: String,
        val iteration: Int,
        val pid: Int,
        val rssKb: Long?,
        val javaHeapKb: Long,
        val javaHeapCommittedKb: Long,
        val nativeHeapKb: Long,
        val totalPssKb: Int,
        val dalvikPssKb: Int,
        val nativePssKb: Int,
        val otherPssKb: Int,
        val threadCount: Int?,
        val fdCount: Int?,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("label", label)
                .put("iteration", iteration)
                .put("pid", pid)
                .put("rssKb", rssKb ?: JSONObject.NULL)
                .put("javaHeapKb", javaHeapKb)
                .put("javaHeapCommittedKb", javaHeapCommittedKb)
                .put("nativeHeapKb", nativeHeapKb)
                .put("totalPssKb", totalPssKb)
                .put("dalvikPssKb", dalvikPssKb)
                .put("nativePssKb", nativePssKb)
                .put("otherPssKb", otherPssKb)
                .put("threadCount", threadCount ?: JSONObject.NULL)
                .put("fdCount", fdCount ?: JSONObject.NULL)
    }

    private data class RunawayMetric(
        val name: String,
        val thresholdProperty: String,
        val defaultThreshold: Long,
    )

    private companion object {
        private const val SUCCESS_TIMEOUT_MS = 5_000L
        private const val FAULT_SUCCESS_TIMEOUT_MS = 10_000L
        private const val BRIDGE_TIMEOUT_MS = 10_000L
        private const val TIMEOUT_SCRIPT_TIMEOUT_MS = 650L
        private const val TIMEOUT_EXTRA_SETTLE_MS = 1_000L
        private const val DEFAULT_STRESS_ITERATIONS = 100
        private const val DEFAULT_IMAGE_ITERATIONS = 50
        private const val DEFAULT_FAULT_ITERATIONS = 10
        private const val MAX_FAILURE_TEXT = 1000
        private const val MIN_RUNAWAY_SAMPLES = 4
        private const val RECENT_SAMPLE_WINDOW = 4
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/perf"
        private const val ARG_ITERATIONS = "autojs.nodejs.leak.iterations"
        private const val ARG_IMAGE_ITERATIONS = "autojs.nodejs.leak.imageIterations"
        private const val ARG_FAULT_ITERATIONS = "autojs.nodejs.leak.faultIterations"
        private const val ARG_SAMPLE_EVERY = "autojs.nodejs.leak.sampleEvery"
        private const val ARG_FAIL_ON_RUNAWAY = "autojs.nodejs.leak.failOnRunaway"
        private const val ARG_MAX_RSS_DELTA_KB = "autojs.nodejs.leak.maxRssDeltaKb"
        private const val ARG_MAX_JAVA_HEAP_DELTA_KB = "autojs.nodejs.leak.maxJavaHeapDeltaKb"
        private const val ARG_MAX_NATIVE_HEAP_DELTA_KB = "autojs.nodejs.leak.maxNativeHeapDeltaKb"
        private const val ARG_MAX_THREAD_DELTA = "autojs.nodejs.leak.maxThreadDelta"
        private const val ARG_MAX_FD_DELTA = "autojs.nodejs.leak.maxFdDelta"

        private val RUNAWAY_METRICS = listOf(
            RunawayMetric("rssKb", ARG_MAX_RSS_DELTA_KB, 262_144L),
            RunawayMetric("javaHeapKb", ARG_MAX_JAVA_HEAP_DELTA_KB, 131_072L),
            RunawayMetric("nativeHeapKb", ARG_MAX_NATIVE_HEAP_DELTA_KB, 131_072L),
            RunawayMetric("threadCount", ARG_MAX_THREAD_DELTA, 24L),
            RunawayMetric("fdCount", ARG_MAX_FD_DELTA, 64L),
        )
    }
}
