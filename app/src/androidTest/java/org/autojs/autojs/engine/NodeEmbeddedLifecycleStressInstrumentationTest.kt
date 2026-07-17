package org.autojs.autojs.engine

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class NodeEmbeddedLifecycleStressInstrumentationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun runSimpleScript100Times() {
        assumeEmbeddedScriptExecutionEnabled()
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("R2 persistent-runtime resource-growth stress")

        val iterations = instrumentationInt(ARG_RESOURCE_ITERATIONS, STRESS_ITERATIONS, 2..MAX_STRESS_ITERATIONS)
        val sampleEvery = instrumentationInt(ARG_RESOURCE_SAMPLE_EVERY, DEFAULT_RESOURCE_SAMPLE_EVERY, 1..iterations)
        val runs = JSONArray()
        val samples = JSONArray()
        val report = JSONObject()
            .put("schema", "autojs6-node-r2-persistent-resource-growth-v1")
            .put("generatedAtEpochMs", System.currentTimeMillis())
            .put("packageName", context.packageName)
            .put("device", deviceJson())
            .put(
                "configuration",
                JSONObject()
                    .put("iterations", iterations)
                    .put("sampleEvery", sampleEvery)
                    .put("sameProcessRequired", true)
                    .put("settleBetweenExecutions", false)
                    .put("failOnGrowth", instrumentationBoolean(ARG_RESOURCE_FAIL_ON_GROWTH, false)),
            )
            .put("runs", runs)
            .put("samples", samples)

        var firstResult: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
        var previousResult: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
        var completed = 0
        var thrown: Throwable? = null
        var reportFile: File? = null
        try {
            repeat(iterations) { zeroBasedIndex ->
                val iteration = zeroBasedIndex + 1
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val result = runEmbeddedScript(
                    source = persistentResourceScript(iteration),
                    sourceName = "<lifecycle-stress-simple-$iteration.js>",
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                )
                val wallMs = ((SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val nodePayload = parsePersistentResourcePayload(result.stdout)

                assertSucceeded("simple iteration $iteration", result)
                assertFalse("simple iteration $iteration timed out", result.timedOut)
                assertTrue(result.stdout, result.stdout.contains("stress.simple.$iteration=v24.5.0"))
                assertTrue("simple iteration $iteration missing resource payload\n${result.stdout}", nodePayload != null)
                val payload = checkNotNull(nodePayload)
                assertEquals("STOPPED", result.nativeValues["embedded_script.runtime_state"])
                assertTrue(
                    "simple iteration $iteration missing execution id",
                    result.nativeValues["embedded_script.execution_id"].orEmpty().isNotBlank(),
                )

                val first = firstResult
                if (first == null) {
                    firstResult = result
                    assertEquals("first persistent execution must initialize once", "1", result.nativeValues[PROCESS_RUNTIME_INIT_COUNT_KEY])
                    assertTrue("first persistent execution PID is invalid: ${result.pid}", result.pid > 0)
                    assertEquals("first persistent execution sequence must be 1", "1", result.nativeValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY])
                    listOf(
                        PROCESS_POOL_SELECTED_SLOT_KEY,
                        PROCESS_SESSION_GENERATION_KEY,
                        PROCESS_RUNTIME_GENERATION_KEY,
                    ).forEach { key ->
                        assertTrue("first persistent execution is missing $key: ${result.nativeValues}", !result.nativeValues[key].isNullOrBlank())
                    }
                } else {
                    assertPersistentExecutionIdentity(iteration, first, checkNotNull(previousResult), result)
                }
                assertCleanPersistentExecution(iteration, result)

                runs.put(compactRunJson(iteration, wallMs, result, payload))
                if (iteration == 1 || iteration % sampleEvery == 0 || iteration == iterations) {
                    samples.put(persistentResourceSnapshot(iteration, wallMs, result, payload).toJson())
                }
                previousResult = result
                completed = iteration
            }
        } catch (error: Throwable) {
            thrown = error
            report.put(
                "failure",
                JSONObject()
                    .put("completedIterations", completed)
                    .put("type", error.javaClass.name)
                    .put("message", error.message ?: JSONObject.NULL),
            )
        } finally {
            val growth = analyzePersistentResourceGrowth(samples)
            report
                .put("completedIterations", completed)
                .put("growth", growth)
            val writtenReport = writePersistentResourceReports(report)
            reportFile = writtenReport
            println("node.r2.resource.report=${writtenReport.absolutePath}")
            println("node.r2.resource.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/perf")
        }

        thrown?.let { throw it }
        val writtenReport = checkNotNull(reportFile)
        assertTrue("persistent resource report was not written: ${writtenReport.absolutePath}", writtenReport.isFile && writtenReport.length() > 0L)
        assertEquals("not all persistent resource iterations completed", iterations, completed)
        if (instrumentationBoolean(ARG_RESOURCE_FAIL_ON_GROWTH, false)) {
            val growth = report.getJSONObject("growth")
            assertTrue(
                "persistent resource metrics were unavailable; report=${writtenReport.absolutePath}\n${growth.toString(2)}",
                growth.getBoolean("requiredMetricsAvailable"),
            )
            assertFalse(
                "persistent resource growth exceeded thresholds; report=${writtenReport.absolutePath}\n${growth.toString(2)}",
                growth.getBoolean("monotonicGrowthDetected"),
            )
        }
    }

    private fun persistentResourceScript(iteration: Int): String = """
        "nodejs";
        function activeHandleCount() {
          try {
            return typeof process._getActiveHandles === "function" ? process._getActiveHandles().length : null;
          } catch (error) {
            return null;
          }
        }
        console.log("stress.simple.$iteration=" + process.version);
        setImmediate(() => {
          const memory = process.memoryUsage();
          console.log("$RESOURCE_PAYLOAD_PREFIX" + JSON.stringify({
            iteration: $iteration,
            memory: {
              rss: memory.rss,
              heapTotal: memory.heapTotal,
              heapUsed: memory.heapUsed,
              external: memory.external,
              arrayBuffers: memory.arrayBuffers
            },
            libuvHandleCount: activeHandleCount()
          }));
        });
    """.trimIndent()

    private fun parsePersistentResourcePayload(stdout: String): JSONObject? =
        stdout.lineSequence()
            .mapNotNull { line ->
                val markerIndex = line.indexOf(RESOURCE_PAYLOAD_PREFIX)
                if (markerIndex < 0) null else line.substring(markerIndex + RESOURCE_PAYLOAD_PREFIX.length).trim()
            }
            .firstOrNull()
            ?.let(::JSONObject)

    private fun assertPersistentExecutionIdentity(
        iteration: Int,
        first: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        previous: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        current: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertEquals("iteration $iteration changed service PID", first.pid, current.pid)
        listOf(
            PROCESS_POOL_SELECTED_SLOT_KEY,
            PROCESS_SESSION_GENERATION_KEY,
            PROCESS_RUNTIME_GENERATION_KEY,
        ).forEach { key ->
            assertTrue("first execution is missing $key: ${first.nativeValues}", !first.nativeValues[key].isNullOrBlank())
            assertEquals("iteration $iteration changed $key", first.nativeValues[key], current.nativeValues[key])
        }
        assertEquals("iteration $iteration reinitialized process runtime", "1", current.nativeValues[PROCESS_RUNTIME_INIT_COUNT_KEY])
        assertEquals("iteration $iteration did not reuse process runtime", "true", current.nativeValues[PROCESS_RUNTIME_REUSED_KEY])
        val previousSequence = previous.nativeValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
        val currentSequence = current.nativeValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull()
        assertTrue("iteration $iteration missing previous execution sequence", previousSequence != null)
        assertEquals("iteration $iteration execution sequence did not advance once", previousSequence?.plus(1L), currentSequence)
    }

    private fun assertCleanPersistentExecution(
        iteration: Int,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        val values = result.nativeValues
        assertEquals("iteration $iteration process runtime is not healthy", "true", values[PROCESS_RUNTIME_HEALTHY_KEY])
        assertEquals("iteration $iteration process runtime is poisoned", "false", values[PROCESS_RUNTIME_POISONED_KEY])
        assertEquals("iteration $iteration native teardown was not clean", "true", values[PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY])
        assertEquals("iteration $iteration execution teardown was not clean", "true", values[EXECUTION_TEARDOWN_CLEAN_KEY])
        assertEquals("iteration $iteration service cleanup was not clean", "true", values[SERVICE_CLEANUP_CLEAN_KEY])
        assertEquals("iteration $iteration retained bridge resources", "0", values[BRIDGE_ACTIVE_COUNT_KEY])
        assertEquals("iteration $iteration retained the one-shot settle path", "false", values[CLIENT_PROCESS_SETTLE_DEFERRED_KEY])
        val resultToReturnMs = values[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull()
        assertTrue("iteration $iteration result-to-return timing is missing: $values", resultToReturnMs != null)
        assertTrue(
            "iteration $iteration result delivery blocked for ${resultToReturnMs}ms",
            resultToReturnMs != null && resultToReturnMs <= MAX_CLIENT_RESULT_TO_RETURN_MS,
        )
    }

    private fun compactRunJson(
        iteration: Int,
        wallMs: Long,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        nodePayload: JSONObject,
    ): JSONObject =
        JSONObject()
            .put("iteration", iteration)
            .put("wallMs", wallMs)
            .put("resultElapsedMs", result.elapsedMs)
            .put("resultToReturnMs", result.nativeValues[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull() ?: JSONObject.NULL)
            .put("pid", result.pid)
            .put("sessionGeneration", result.nativeValues[PROCESS_SESSION_GENERATION_KEY].orEmpty())
            .put("runtimeGeneration", result.nativeValues[PROCESS_RUNTIME_GENERATION_KEY].orEmpty())
            .put("runtimeExecutionSequence", result.nativeValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull() ?: JSONObject.NULL)
            .put("runtimeReused", result.nativeValues[PROCESS_RUNTIME_REUSED_KEY] == "true")
            .put("node", nodePayload)

    private fun persistentResourceSnapshot(
        iteration: Int,
        wallMs: Long,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        nodePayload: JSONObject,
    ): PersistentResourceSample {
        val memoryInfo = processMemoryInfo(result.pid)
        val nodeMemory = nodePayload.getJSONObject("memory")
        return PersistentResourceSample(
            iteration = iteration,
            pid = result.pid,
            wallMs = wallMs,
            resultElapsedMs = result.elapsedMs,
            resultToReturnMs = result.nativeValues[CLIENT_RESULT_TO_RETURN_KEY]?.toLongOrNull(),
            rssKb = readStatusLongKb(result.pid, "VmRSS:"),
            vmSizeKb = readStatusLongKb(result.pid, "VmSize:"),
            totalPssKb = memoryInfo?.totalPss,
            javaPssKb = memoryInfo?.dalvikPss,
            nativePssKb = memoryInfo?.nativePss,
            otherPssKb = memoryInfo?.otherPss,
            threadCount = File("/proc/${result.pid}/task").safeCountChildren(),
            fdCount = File("/proc/${result.pid}/fd").safeCountChildren(),
            nodeRssKb = nodeMemory.optLong("rss").bytesToKb(),
            nodeHeapTotalKb = nodeMemory.optLong("heapTotal").bytesToKb(),
            nodeHeapUsedKb = nodeMemory.optLong("heapUsed").bytesToKb(),
            nodeExternalKb = nodeMemory.optLong("external").bytesToKb(),
            nodeArrayBuffersKb = nodeMemory.optLong("arrayBuffers").bytesToKb(),
            libuvHandleCount = nodePayload.optNullableLong("libuvHandleCount"),
            bridgeActiveCount = result.nativeValues[BRIDGE_ACTIVE_COUNT_KEY]?.toLongOrNull(),
            runtimeExecutionSequence = result.nativeValues[PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY]?.toLongOrNull(),
        )
    }

    @Suppress("DEPRECATION")
    private fun processMemoryInfo(pid: Int): android.os.Debug.MemoryInfo? =
        runCatching {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
        }.getOrNull()

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

    private fun Long.bytesToKb(): Long = this / 1024L

    private fun analyzePersistentResourceGrowth(samples: JSONArray): JSONObject {
        var growthDetected = false
        var requiredMetricsAvailable = true
        val metrics = JSONArray()
        PERSISTENT_GROWTH_METRICS.forEach { metric ->
            val values = (0 until samples.length())
                .mapNotNull { index -> samples.getJSONObject(index).optNullableLong(metric.name) }
            val threshold = instrumentationLong(metric.thresholdArgument) ?: metric.defaultThreshold
            val delta = values.lastOrNull()?.minus(values.firstOrNull() ?: 0L)
            val available = values.size >= MIN_GROWTH_SAMPLES
            requiredMetricsAvailable = requiredMetricsAvailable && available
            val monotonic = available && looksMonotonicRunaway(values)
            val detected = delta != null && delta > threshold && monotonic
            growthDetected = growthDetected || detected
            metrics.put(
                JSONObject()
                    .put("name", metric.name)
                    .put("sampleCount", values.size)
                    .put("available", available)
                    .put("baseline", values.firstOrNull() ?: JSONObject.NULL)
                    .put("final", values.lastOrNull() ?: JSONObject.NULL)
                    .put("min", values.minOrNull() ?: JSONObject.NULL)
                    .put("max", values.maxOrNull() ?: JSONObject.NULL)
                    .put("delta", delta ?: JSONObject.NULL)
                    .put("threshold", threshold)
                    .put("monotonic", monotonic)
                    .put("growthDetected", detected)
                    .put("values", JSONArray(values)),
            )
        }
        return JSONObject()
            .put("analysis", "thresholded-monotonic-growth")
            .put("minimumSamples", MIN_GROWTH_SAMPLES)
            .put("requiredMetricsAvailable", requiredMetricsAvailable)
            .put("monotonicGrowthDetected", growthDetected)
            .put("metrics", metrics)
    }

    private fun looksMonotonicRunaway(values: List<Long>): Boolean {
        val increases = values.zipWithNext().count { (previous, current) -> current > previous }
        val recent = values.takeLast(minOf(values.size, RECENT_GROWTH_SAMPLE_WINDOW))
        val recentNonDecreases = recent.zipWithNext().count { (previous, current) -> current >= previous }
        return values.last() == values.maxOrNull() &&
                increases >= values.size - 2 &&
                recentNonDecreases == recent.size - 1
    }

    private fun writePersistentResourceReports(report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/perf").apply { mkdirs() }
        val reportFile = File(reportDir, "embedded-node-r2-resource-growth-${System.currentTimeMillis()}.json")
        report
            .put("devicePrivateReportPath", reportFile.absolutePath)
            .put("devicePublicReportPath", "$DEVICE_REPORT_DIR/${reportFile.name}")
        val reportText = report.toString(2)
        reportFile.writeText(reportText)
        writePublicResourceReport(reportFile.name, reportText)
        return reportFile
    }

    private fun writePublicResourceReport(fileName: String, reportText: String): Boolean =
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

    private fun instrumentationBoolean(name: String, defaultValue: Boolean): Boolean =
        InstrumentationRegistry.getArguments().getString(name)
            ?.let { it.equals("true", ignoreCase = true) || it.equals("yes", ignoreCase = true) || it == "1" }
            ?: defaultValue

    private fun instrumentationLong(name: String): Long? =
        InstrumentationRegistry.getArguments().getString(name)?.toLongOrNull()

    private fun instrumentationInt(name: String, defaultValue: Int, range: IntRange): Int {
        val value = InstrumentationRegistry.getArguments().getString(name)?.toIntOrNull() ?: defaultValue
        require(value in range) { "$name must be in $range, actual=$value" }
        return value
    }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    @Test
    fun timeoutDoesNotPoisonNextRun() {
        assumeEmbeddedScriptExecutionEnabled()

        val timeout = runEmbeddedScript(
            source = """
                "nodejs";
                setInterval(() => {}, 100);
                console.log("stress.timeout.started");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-timeout.js>",
            timeoutMs = FAULT_TIMEOUT_MS,
        )

        assertTimedOut("timeout run", timeout)
        assertEquals("DESTROYED", timeout.nativeValues["embedded_script.runtime_state"])
        assertTrue(
            timeout.nativeValues["embedded_script.lifecycle_transitions"].orEmpty(),
            timeout.nativeValues["embedded_script.lifecycle_transitions"].orEmpty().contains("RUNNING->TIMED_OUT"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.timeout")
    }

    @Test
    fun explicitLongRunningModeIsRejectedBeforeUserScriptRuns() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-long-running-policy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create long-running policy dir: $absolutePath" }
        }
        val marker = File(scriptDir, "unexpected.txt")

        try {
            val result = runEmbeddedScript(
                source = """
                    "nodejs";
                    require("fs").writeFileSync("./unexpected.txt", "ran");
                    setInterval(() => {}, 100);
                """.trimIndent(),
                sourceName = "<lifecycle-stress-long-running-policy.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = 0L,
                executionMode = "longRunning",
            )

            assertFalse("long-running policy gate unexpectedly succeeded", result.succeeded)
            assertFalse("long-running policy gate must not be reported as a script timeout", result.timedOut)
            assertEquals(NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION, result.errorCode)
            assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, result.nativeValues["embedded_script.execution_mode"])
            assertEquals("partial", result.nativeValues["embedded_script.execution_mode_status"])
            assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT, result.nativeValues["embedded_script.execution_mode_launch_surface"])
            assertEquals("false", result.nativeValues["embedded_script.execution_mode_supported"])
            assertEquals("startup_timeout_stop_grace_and_watchdog", result.nativeValues["embedded_script.execution_mode_timeout_policy"])
            assertEquals("true", result.nativeValues["embedded_script.long_running_requested"])
            assertEquals("false", result.nativeValues["embedded_script.long_running_supported"])
            assertEquals("true", result.nativeValues["embedded_script.execution_mode_rejected"])
            assertTrue(result.errorMessage.orEmpty(), result.errorMessage.orEmpty().contains("Phase 7 runtime state machine"))
            assertFalse("long-running rejected script should not have entered user code", marker.exists())

            assertRecoveryRun("after.long_running.policy")
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun uncaughtExceptionDoesNotPoisonNextRun() {
        assumeEmbeddedScriptExecutionEnabled()

        val failure = runEmbeddedScript(
            source = """
                "nodejs";
                throw new Error("stress uncaught");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-uncaught.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )

        assertFalse(failure.succeeded)
        assertFalse(failure.timedOut)
        assertTrue(
            failure.errorMessage.orEmpty() + "\n" + failure.errorStack.orEmpty(),
            failure.errorMessage.orEmpty().contains("stress uncaught") ||
                failure.errorStack.orEmpty().contains("stress uncaught"),
        )
        assertTrue(
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty(),
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty().contains("RUNNING->FAILED"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.uncaught")
    }

    @Test
    fun unhandledRejectionDoesNotPoisonNextRun() {
        assumeEmbeddedScriptExecutionEnabled()

        val failure = runEmbeddedScript(
            source = """
                "nodejs";
                Promise.reject(new Error("stress rejection"));
            """.trimIndent(),
            sourceName = "<lifecycle-stress-rejection.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )

        assertFalse(failure.succeeded)
        assertFalse(failure.timedOut)
        assertTrue(
            failure.errorMessage.orEmpty() + "\n" + failure.errorStack.orEmpty(),
            failure.errorMessage.orEmpty().contains("stress rejection") ||
                failure.errorStack.orEmpty().contains("stress rejection"),
        )
        assertTrue(
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty(),
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty().contains("RUNNING->FAILED"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.rejection")
    }

    @Test
    fun unclearedIntervalTimesOutThenNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val timeout = runEmbeddedScript(
            source = """
                "nodejs";
                let ticks = 0;
                setInterval(() => {
                  ticks += 1;
                  if (ticks === 1) console.log("stress.interval.tick");
                }, 50);
            """.trimIndent(),
            sourceName = "<lifecycle-stress-interval.js>",
            timeoutMs = FAULT_TIMEOUT_MS,
        )

        assertTimedOut("uncleared interval", timeout)
        assertEquals(true, timeout.pendingTimers)
        assertTrue(
            timeout.errorMessage.orEmpty(),
            timeout.errorMessage.orEmpty().contains("pendingTimers=true"),
        )
        settleEmbeddedScriptServiceProcess()

        val recovery = assertRecoveryRun("after.interval")
        assertFalse(recovery.stdout.contains("stress.interval.tick"))
    }

    @Test
    fun activeScopedFsStreamTimesOutThenNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-lifecycle-fs-stream-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create lifecycle fs stream dir: $absolutePath" }
        }
        try {
            val timeout = runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.mkdirSync("./stream-timeout", { recursive: true });
                    fs.writeFileSync("./stream-timeout/input.bin", Buffer.alloc(2 * 1024 * 1024, 65));
                    globalThis.__autojs6HeldReadable = fs.createReadStream("./stream-timeout/input.bin", {
                      highWaterMark: 64 * 1024
                    });
                    globalThis.__autojs6HeldReadable.on("data", () => {
                      console.log("stress.fs.stream.paused");
                      globalThis.__autojs6HeldReadable.pause();
                    });
                    globalThis.__autojs6HeldReadable.on("end", () => console.log("stress.fs.stream.unexpected_end"));
                    console.log("stress.fs.stream.pending");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-fs-stream.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = FAULT_TIMEOUT_MS,
            )

            assertTimedOut("active scoped fs stream", timeout)
            assertEquals(false, timeout.pendingTimers)
            assertEquals(true, timeout.pendingScopedFsStreams)
            assertEquals(1, timeout.pendingScopedFsStreamCount)
            assertEquals(true, timeout.pendingCallbacks)
            assertTrue(
                timeout.errorMessage.orEmpty(),
                timeout.errorMessage.orEmpty().contains("pendingScopedFsStreamCount=1"),
            )
            settleEmbeddedScriptServiceProcess()

            assertRecoveryRun("after.fs.stream")
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun activeZlibStreamTimesOutThenNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val timeout = runEmbeddedScript(
            source = """
                "nodejs";
                const zlib = require("zlib");
                globalThis.__autojs6HeldGzip = zlib.createGzip();
                globalThis.__autojs6HeldGzip.on("data", () => {
                  console.log("stress.zlib.stream.data");
                });
                globalThis.__autojs6HeldGzip.write(Buffer.from("pending-zlib-stream"));
                console.log("stress.zlib.stream.pending");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-zlib-stream.js>",
            timeoutMs = FAULT_TIMEOUT_MS,
        )

        assertTimedOut("active zlib stream", timeout)
        assertEquals(false, timeout.pendingTimers)
        assertEquals(true, timeout.pendingZlibStreams)
        assertEquals(1, timeout.pendingZlibStreamCount)
        assertEquals(true, timeout.pendingCallbacks)
        assertTrue(
            timeout.errorMessage.orEmpty(),
            timeout.errorMessage.orEmpty().contains("pendingZlibStreamCount=1"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.zlib.stream")
    }

    @Test
    fun nestedFsCallbacksDrainBeforeSuccess() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-lifecycle-drain-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create lifecycle drain dir: $absolutePath" }
        }
        try {
            val result = runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.writeFile("./stress-drain.txt", "ok", "utf8", (writeError) => {
                      if (writeError) throw writeError;
                      fs.readFile("./stress-drain.txt", "utf8", (readError, text) => {
                        if (readError) throw readError;
                        setTimeout(() => {
                          console.log("stress.nested.callback=" + text);
                        }, 10);
                      });
                    });
                """.trimIndent(),
                sourceName = "<lifecycle-stress-drain.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = SIMPLE_TIMEOUT_MS,
            )

            assertSucceeded("nested fs callback drain", result)
            assertTrue(result.stdout, result.stdout.contains("stress.nested.callback=ok"))
            assertEquals("STOPPED", result.nativeValues["embedded_script.runtime_state"])
            assertEquals(false, result.pendingCallbacks)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun stdoutAndStderrDoNotLeakAcrossRuns() {
        assumeEmbeddedScriptExecutionEnabled()

        val first = runEmbeddedScript(
            source = """
                "nodejs";
                console.log("stress.stdout.first");
                console.error("stress.stderr.first");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-stdio-first.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )
        assertSucceeded("stdio first run", first)
        assertTrue(first.stdout, first.stdout.contains("stress.stdout.first"))
        assertTrue(first.stderr, first.stderr.contains("stress.stderr.first"))
        settleEmbeddedScriptServiceProcess()

        val second = runEmbeddedScript(
            source = """
                "nodejs";
                console.log("stress.stdout.second");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-stdio-second.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )
        assertSucceeded("stdio second run", second)
        assertTrue(second.stdout, second.stdout.contains("stress.stdout.second"))
        assertFalse(second.stdout.contains("stress.stdout.first"))
        assertFalse(second.stderr.contains("stress.stderr.first"))
    }

    @Test
    fun explicitRejectWhenBusyStillRejectsAndNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-concurrent-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create concurrent execution dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val firstFailure = AtomicReference<Throwable?>()
        val firstThread = Thread {
            runCatching {
                runEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        console.log("stress.concurrent.first.started");
                        return new Promise((resolve) => setTimeout(() => {
                          console.log("stress.concurrent.first.done");
                          resolve();
                        }, 3000));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-concurrent-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = CONCURRENT_TIMEOUT_MS,
                    queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                    processPoolSlot = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                    processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
                )
            }.onSuccess { result ->
                firstResult.set(result)
            }.onFailure { error ->
                firstFailure.set(error)
            }
        }.apply {
            name = "AutoJs6NodeConcurrentFirst"
            start()
        }

        try {
            assertTrue(
                "First concurrent run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )

            val second = runEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("stress.concurrent.second.unexpected");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-concurrent-second.js>",
                timeoutMs = SIMPLE_TIMEOUT_MS,
                queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                processPoolSlot = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
            )

            firstThread.join(CONCURRENT_TIMEOUT_MS + 3000L)
            firstFailure.get()?.let { error ->
                throw AssertionError("First concurrent run failed before producing a result.", error)
            }
            assertFalse("First concurrent run did not finish.", firstThread.isAlive)
            val first = firstResult.get()
            assertTrue("First concurrent run did not produce a result.", first != null)
            assertSucceeded("concurrent first", first!!)
            assertTrue(first.stdout, first.stdout.contains("stress.concurrent.first.done"))

            assertFalse("Second concurrent run must be rejected, not queued silently.", second.succeeded)
            assertFalse("Engine-busy rejection should not be reported as script timeout.", second.timedOut)
            assertEquals(NodeRuntimeErrorCodes.NODE_ENGINE_BUSY, second.errorCode)
            assertEquals("true", second.nativeValues["embedded_script.engine_busy"])
            assertEquals("single-active-execution-per-process", second.nativeValues["embedded_script.concurrent_policy"])
            assertTrue(second.errorMessage.orEmpty(), second.errorMessage.orEmpty().contains("only one active execution"))

            settleEmbeddedScriptServiceProcess()
            assertRecoveryRun("after.concurrent.busy")
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun defaultConcurrentExecutionQueuesInsteadOfEngineBusy() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-default-queue-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create default queue dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val order = File(scriptDir, "order.txt")
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val firstFailure = AtomicReference<Throwable?>()
        val secondFailure = AtomicReference<Throwable?>()

        val firstThread = Thread {
            runCatching {
                runEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        fs.appendFileSync("./order.txt", "first-started\n");
                        console.log("stress.default.queue.first.started");
                        return new Promise((resolve) => setTimeout(() => {
                          fs.appendFileSync("./order.txt", "first-done\n");
                          console.log("stress.default.queue.first.done");
                          resolve();
                        }, 1500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-default-queue-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    processPoolSlot = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                    processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
                )
            }.onSuccess(firstResult::set)
                .onFailure(firstFailure::set)
        }.apply {
            name = "AutoJs6NodeDefaultQueueFirst"
            start()
        }

        val secondThread = Thread {
            runCatching {
                runEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.appendFileSync("./order.txt", "second-run\n");
                        console.log("stress.default.queue.second.run");
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-default-queue-second.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    processPoolSlot = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                    processPoolSlotSource = NodeEmbeddedProcessPoolPolicy.SLOT_SELECTION_EXPLICIT_REQUEST,
                )
            }.onSuccess(secondResult::set)
                .onFailure(secondFailure::set)
        }.apply {
            name = "AutoJs6NodeDefaultQueueSecond"
        }

        try {
            assertTrue(
                "First default queue run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            secondThread.start()

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            secondThread.join(QUEUE_TEST_TIMEOUT_MS + SIMPLE_TIMEOUT_MS + 3000L)
            firstFailure.get()?.let { throw AssertionError("First default queue run failed.", it) }
            secondFailure.get()?.let { throw AssertionError("Second default queue run failed.", it) }
            assertFalse("First default queue run did not finish.", firstThread.isAlive)
            assertFalse("Second default queue run did not finish.", secondThread.isAlive)

            val first = firstResult.get()
            val second = secondResult.get()
            assertTrue("First default queue run did not produce a result.", first != null)
            assertTrue("Second default queue run did not produce a result.", second != null)
            assertSucceeded("default queue first", first!!)
            assertSucceeded("default queue second", second!!)
            assertEquals("true", second.nativeValues["embedded_script.queue_queued"])
            assertEquals("dequeued", second.nativeValues["embedded_script.queue_status"])
            assertTrue(
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty(),
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty().toLongOrNull() ?: 0L > 0L,
            )
            assertFalse("Default queued run must not report engine busy.", second.errorCode == NodeRuntimeErrorCodes.NODE_ENGINE_BUSY)

            val orderText = order.readText()
            assertTrue(orderText, orderText.indexOf("first-started") >= 0)
            assertTrue(orderText, orderText.indexOf("first-done") > orderText.indexOf("first-started"))
            assertTrue(orderText, orderText.indexOf("second-run") > orderText.indexOf("first-done"))
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queuedExecutionRunsAfterActiveExecution() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-order-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue order dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val order = File(scriptDir, "order.txt")
        val firstId = "queue-first-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val firstFailure = AtomicReference<Throwable?>()
        val secondId = "queue-second-${System.nanoTime()}"
        val secondResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondFailure = AtomicReference<Throwable?>()

        val firstThread = Thread {
            runCatching {
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        fs.appendFileSync("./order.txt", "first-started\n");
                        console.log("stress.queue.first.started");
                        return new Promise((resolve) => setTimeout(() => {
                          fs.appendFileSync("./order.txt", "first-done\n");
                          console.log("stress.queue.first.done");
                          resolve();
                        }, 1500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                )
            }.onSuccess(firstResult::set)
                .onFailure(firstFailure::set)
        }.apply {
            name = "AutoJs6NodeQueueFirst"
            start()
        }

        val secondThread = Thread {
            runCatching {
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.appendFileSync("./order.txt", "second-run\n");
                        console.log("stress.queue.second.run");
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-second.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                    executionId = secondId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                )
            }.onSuccess(secondResult::set)
                .onFailure(secondFailure::set)
        }.apply {
            name = "AutoJs6NodeQueueSecond"
        }

        try {
            assertTrue(
                "First queued-order run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            secondThread.start()
            assertTrue(
                "Second execution was not visible in embedded execution queue.",
                waitUntil(5000L) {
                    NativeNodeEmbeddedScriptService.queueStatus(context).queuedExecutionIds.contains(secondId)
                },
            )
            val queuedStatus = NativeNodeEmbeddedScriptService.queueStatus(context)
            assertEquals(NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS, queuedStatus.maxQueuedExecutions)
            assertTrue(queuedStatus.activeExecutionId.orEmpty().isNotBlank())
            assertTrue(queuedStatus.queuedExecutionIds.contains(secondId))

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            secondThread.join(QUEUE_TEST_TIMEOUT_MS + SIMPLE_TIMEOUT_MS + 3000L)
            firstFailure.get()?.let { throw AssertionError("First queue run failed.", it) }
            secondFailure.get()?.let { throw AssertionError("Second queue run failed.", it) }
            assertFalse("First queue run did not finish.", firstThread.isAlive)
            assertFalse("Second queue run did not finish.", secondThread.isAlive)

            val first = firstResult.get()
            val second = secondResult.get()
            assertTrue("First queue run did not produce a result.", first != null)
            assertTrue("Second queue run did not produce a result.", second != null)
            assertSucceeded("queue first", first!!)
            assertSucceeded("queue second", second!!)
            assertEquals("true", second.nativeValues["embedded_script.queue_queued"])
            assertEquals("dequeued", second.nativeValues["embedded_script.queue_status"])
            assertTrue(
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty(),
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty().toLongOrNull() ?: 0L > 0L,
            )

            val orderText = order.readText()
            assertTrue(orderText, orderText.indexOf("first-started") >= 0)
            assertTrue(orderText, orderText.indexOf("first-done") > orderText.indexOf("first-started"))
            assertTrue(orderText, orderText.indexOf("second-run") > orderText.indexOf("first-done"))
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queuedExecutionCanBeCancelled() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-cancel-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue cancel dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val firstId = "queue-cancel-first-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondId = "queue-cancel-${System.nanoTime()}"
        val secondResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondFailure = AtomicReference<Throwable?>()

        val firstThread = Thread {
            firstResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        return new Promise((resolve) => setTimeout(resolve, 2500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-cancel-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueCancelFirst"
            start()
        }
        val secondThread = Thread {
            runCatching {
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        console.log("stress.queue.cancel.unexpected");
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-cancel-second.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                    executionId = secondId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                )
            }.onSuccess(secondResult::set)
                .onFailure(secondFailure::set)
        }.apply {
            name = "AutoJs6NodeQueueCancelSecond"
        }

        try {
            assertTrue(
                "First queue-cancel run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            secondThread.start()
            assertTrue(
                "Second execution was not queued before cancellation.",
                waitUntil(5000L) {
                    NativeNodeEmbeddedScriptService.queueStatus(context).queuedExecutionIds.contains(secondId)
                },
            )

            assertTrue("Queued execution cancellation returned false.", NativeNodeEmbeddedScriptService.cancelQueuedExecution(context, secondId))
            secondThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            secondFailure.get()?.let { throw AssertionError("Cancelled queue run threw before producing a result.", it) }
            assertFalse("Cancelled queue run did not finish.", secondThread.isAlive)
            val cancelled = secondResult.get()
            assertTrue("Cancelled queue run did not produce a result.", cancelled != null)
            assertFalse("Cancelled queue run unexpectedly succeeded.", cancelled!!.succeeded)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, cancelled.errorCode)
            assertEquals("true", cancelled.nativeValues["embedded_script.queue_cancelled"])

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val first = firstResult.get()
            assertTrue("First queue-cancel run did not produce a result.", first != null)
            assertSucceeded("queue cancel first", first!!)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queuedExecutionTimeoutReturnsStableError() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-timeout-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue timeout dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val secondMarker = File(scriptDir, "second-ran.txt")
        val firstId = "queue-timeout-first-${System.nanoTime()}"
        val secondId = "queue-timeout-second-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()

        val firstThread = Thread {
            firstResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        return new Promise((resolve) => setTimeout(resolve, 2500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-timeout-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueTimeoutFirst"
            start()
        }

        try {
            assertTrue(
                "First queue-timeout run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )

            val second = enqueueEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.writeFileSync("./second-ran.txt", "unexpected");
                    console.log("stress.queue.timeout.second.unexpected");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-queue-timeout-second.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = SIMPLE_TIMEOUT_MS,
                executionId = secondId,
                queueTimeoutMs = QUEUE_SHORT_TIMEOUT_MS,
            )

            assertFalse("Queue-timeout run unexpectedly succeeded.", second.succeeded)
            assertFalse("Queue-timeout failure should not be reported as a script timeout.", second.timedOut)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT, second.errorCode)
            assertEquals("true", second.nativeValues["embedded_script.queue_timeout"])
            assertEquals("queue_timeout", second.nativeValues["embedded_script.queue_status"])
            assertTrue(
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty(),
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty().toLongOrNull() ?: 0L > 0L,
            )
            assertFalse("Timed-out queued script must not run user code.", secondMarker.exists())

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val first = firstResult.get()
            assertTrue("First queue-timeout run did not produce a result.", first != null)
            assertSucceeded("queue timeout first", first!!)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queueLimitReachedReturnsStableError() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-full-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue full dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val firstId = "queue-full-first-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val queuedIds = List(NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS) { index ->
            "queue-full-waiting-$index-${System.nanoTime()}"
        }
        val queuedResults = queuedIds.map {
            AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        }

        val firstThread = Thread {
            firstResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        return new Promise((resolve) => setTimeout(resolve, 8000));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-full-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueFullFirst"
            start()
        }
        val queuedThreads = queuedIds.mapIndexed { index, queuedId ->
            Thread {
                queuedResults[index].set(
                    enqueueEmbeddedScript(
                        source = """
                            "nodejs";
                            console.log("stress.queue.full.waiting.$index");
                        """.trimIndent(),
                        sourceName = "<lifecycle-stress-queue-full-waiting-$index.js>",
                        workingDirectory = scriptDir.absolutePath,
                        timeoutMs = SIMPLE_TIMEOUT_MS,
                        executionId = queuedId,
                        queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    ),
                )
            }.apply {
                name = "AutoJs6NodeQueueFullWaiting$index"
            }
        }

        try {
            assertTrue(
                "First queue-full run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            queuedThreads.forEach(Thread::start)
            assertTrue(
                "Queue did not reach its configured capacity before queue-full check.",
                waitUntil(5000L) {
                    val queued = NativeNodeEmbeddedScriptService.queueStatus(context).queuedExecutionIds
                    queuedIds.all(queued::contains)
                },
            )

            val third = enqueueEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("stress.queue.full.third.unexpected");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-queue-full-third.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = SIMPLE_TIMEOUT_MS,
                executionId = "queue-full-third-${System.nanoTime()}",
                queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
            )

            assertFalse("Third queue-full run unexpectedly succeeded.", third.succeeded)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_FULL, third.errorCode)
            assertEquals("true", third.nativeValues["embedded_script.queue_full"])
            assertEquals(
                NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS.toString(),
                third.nativeValues["embedded_script.queue_max_queued_executions"],
            )

            queuedIds.forEach { queuedId ->
                assertTrue(
                    "Queued execution cancellation returned false for $queuedId.",
                    NativeNodeEmbeddedScriptService.cancelQueuedExecution(context, queuedId),
                )
            }
            queuedThreads.forEach { thread -> thread.join(QUEUE_TEST_TIMEOUT_MS + 3000L) }
            queuedResults.forEachIndexed { index, queuedResult ->
                val cancelled = queuedResult.get()
                assertTrue("Queued run $index did not produce a result.", cancelled != null)
                assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, cancelled!!.errorCode)
            }

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val first = firstResult.get()
            assertTrue("First queue-full run did not produce a result.", first != null)
            assertSucceeded("queue full first", first!!)
        } finally {
            queuedIds.forEach { queuedId ->
                NativeNodeEmbeddedScriptService.cancelQueuedExecution(context, queuedId)
            }
            queuedThreads.forEach { thread ->
                if (thread.isAlive) {
                    thread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
                }
            }
            if (firstThread.isAlive) {
                firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            }
            scriptDir.deleteRecursively()
        }
    }

    private fun assertRecoveryRun(label: String): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val result = runEmbeddedScript(
            source = """
                "nodejs";
                const fs = require("fs");
                fs.writeFileSync("./$label.txt", "ok");
                console.log("$label.node=" + process.version);
                console.log("$label.fs=" + fs.readFileSync("./$label.txt", "utf8"));
            """.trimIndent(),
            sourceName = "<lifecycle-stress-$label.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )
        assertSucceeded(label, result)
        assertFalse("$label timed out", result.timedOut)
        assertTrue(result.stdout, result.stdout.contains("$label.node=v24.5.0"))
        assertTrue(result.stdout, result.stdout.contains("$label.fs=ok"))
        assertEquals("STOPPED", result.nativeValues["embedded_script.runtime_state"])
        return result
    }

    private fun assertSucceeded(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertTrue(
            "$label failed\nstdout=${result.stdout}\nstderr=${result.stderr}\nerror=${result.errorName}: ${result.errorMessage}",
            result.succeeded,
        )
    }

    private fun assertTimedOut(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertFalse("$label unexpectedly succeeded", result.succeeded)
        assertTrue(
            "$label should time out\nstdout=${result.stdout}\nstderr=${result.stderr}\nerror=${result.errorName}: ${result.errorMessage}\nnativeValues=${result.nativeValues}",
            result.timedOut,
        )
        assertEquals("EmbeddedScriptTimeoutError", result.errorName)
        assertEquals("ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT", result.errorCode)
        assertTrue(
            result.errorMessage.orEmpty(),
            result.errorMessage.orEmpty().contains("Embedded Node.js script timed out"),
        )
    }

    private fun runEmbeddedScript(
        source: String,
        sourceName: String,
        workingDirectory: String? = context.cacheDir.absolutePath,
        timeoutMs: Long = SIMPLE_TIMEOUT_MS,
        executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        queueMode: String = NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY,
        queueTimeoutMs: Long = 0L,
        processPoolSlot: String? = null,
        processPoolSlotSource: String? = null,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                executionMode = executionMode,
                timeoutMs = timeoutMs,
                queueMode = queueMode,
                queueTimeoutMs = queueTimeoutMs,
                processPoolSlot = processPoolSlot,
                processPoolSlotSource = processPoolSlotSource,
            ),
        )
    }

    private fun enqueueEmbeddedScript(
        source: String,
        sourceName: String,
        workingDirectory: String? = context.cacheDir.absolutePath,
        timeoutMs: Long = SIMPLE_TIMEOUT_MS,
        executionId: String,
        queueTimeoutMs: Long = QUEUE_TEST_TIMEOUT_MS,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.enqueueScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                timeoutMs = timeoutMs,
                executionId = executionId,
                queueTimeoutMs = queueTimeoutMs,
            ),
        )
    }

    private fun assumeEmbeddedScriptExecutionEnabled() {
        assumeTrue(
            "Build with -Pautojs.nodejs.embedded.script.enabled=true to run embedded lifecycle stress tests.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
    }

    private fun settleEmbeddedScriptServiceProcess() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) {
                return true
            }
            SystemClock.sleep(50L)
        }
        return condition()
    }

    private data class PersistentResourceSample(
        val iteration: Int,
        val pid: Int,
        val wallMs: Long,
        val resultElapsedMs: Long,
        val resultToReturnMs: Long?,
        val rssKb: Long?,
        val vmSizeKb: Long?,
        val totalPssKb: Int?,
        val javaPssKb: Int?,
        val nativePssKb: Int?,
        val otherPssKb: Int?,
        val threadCount: Int?,
        val fdCount: Int?,
        val nodeRssKb: Long,
        val nodeHeapTotalKb: Long,
        val nodeHeapUsedKb: Long,
        val nodeExternalKb: Long,
        val nodeArrayBuffersKb: Long,
        val libuvHandleCount: Long?,
        val bridgeActiveCount: Long?,
        val runtimeExecutionSequence: Long?,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("iteration", iteration)
                .put("pid", pid)
                .put("wallMs", wallMs)
                .put("resultElapsedMs", resultElapsedMs)
                .putNullable("resultToReturnMs", resultToReturnMs)
                .putNullable("rssKb", rssKb)
                .putNullable("vmSizeKb", vmSizeKb)
                .putNullable("totalPssKb", totalPssKb)
                .putNullable("javaPssKb", javaPssKb)
                .putNullable("nativePssKb", nativePssKb)
                .putNullable("otherPssKb", otherPssKb)
                .putNullable("threadCount", threadCount)
                .putNullable("fdCount", fdCount)
                .put("nodeRssKb", nodeRssKb)
                .put("nodeHeapTotalKb", nodeHeapTotalKb)
                .put("nodeHeapUsedKb", nodeHeapUsedKb)
                .put("nodeExternalKb", nodeExternalKb)
                .put("nodeArrayBuffersKb", nodeArrayBuffersKb)
                .putNullable("libuvHandleCount", libuvHandleCount)
                .putNullable("bridgeActiveCount", bridgeActiveCount)
                .putNullable("runtimeExecutionSequence", runtimeExecutionSequence)

        private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
            put(name, value ?: JSONObject.NULL)
    }

    private data class PersistentGrowthMetric(
        val name: String,
        val thresholdArgument: String,
        val defaultThreshold: Long,
    )

    private companion object {
        private const val STRESS_ITERATIONS = 100
        private const val MAX_STRESS_ITERATIONS = 1000
        private const val DEFAULT_RESOURCE_SAMPLE_EVERY = 10
        private const val MIN_GROWTH_SAMPLES = 5
        private const val RECENT_GROWTH_SAMPLE_WINDOW = 4
        private const val MAX_CLIENT_RESULT_TO_RETURN_MS = 250L
        private const val SIMPLE_TIMEOUT_MS = 5000L
        private const val FAULT_TIMEOUT_MS = 900L
        private const val CONCURRENT_TIMEOUT_MS = 10000L
        private const val QUEUE_TEST_TIMEOUT_MS = 10000L
        private const val QUEUE_SHORT_TIMEOUT_MS = 500L
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/perf"
        private const val RESOURCE_PAYLOAD_PREFIX = "stress.resource="
        private const val PROCESS_POOL_SELECTED_SLOT_KEY = "embedded_script.process_pool.selected_slot_id"
        private const val PROCESS_SESSION_GENERATION_KEY = "embedded_script.process_session.generation"
        private const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        private const val PROCESS_RUNTIME_INIT_COUNT_KEY = "process_runtime.init_count"
        private const val PROCESS_RUNTIME_EXECUTION_SEQUENCE_KEY = "process_runtime.execution_sequence"
        private const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        private const val PROCESS_RUNTIME_HEALTHY_KEY = "process_runtime.healthy"
        private const val PROCESS_RUNTIME_POISONED_KEY = "process_runtime.poisoned"
        private const val PROCESS_RUNTIME_TEARDOWN_CLEAN_KEY = "process_runtime.teardown_clean"
        private const val EXECUTION_TEARDOWN_CLEAN_KEY = "execution.teardown_clean"
        private const val SERVICE_CLEANUP_CLEAN_KEY = "embedded_script.service.cleanup_clean"
        private const val BRIDGE_ACTIVE_COUNT_KEY = "embedded_script.bridge_resource.active_count"
        private const val CLIENT_RESULT_TO_RETURN_KEY = "embedded_script.client.result_to_return_ms"
        private const val CLIENT_PROCESS_SETTLE_DEFERRED_KEY = "embedded_script.client.process_settle_deferred"
        private const val ARG_RESOURCE_ITERATIONS = "autojs.nodejs.r2.resource.iterations"
        private const val ARG_RESOURCE_SAMPLE_EVERY = "autojs.nodejs.r2.resource.sampleEvery"
        private const val ARG_RESOURCE_FAIL_ON_GROWTH = "autojs.nodejs.r2.resource.failOnGrowth"
        private const val ARG_MAX_RSS_DELTA_KB = "autojs.nodejs.r2.resource.maxRssDeltaKb"
        private const val ARG_MAX_JAVA_PSS_DELTA_KB = "autojs.nodejs.r2.resource.maxJavaPssDeltaKb"
        private const val ARG_MAX_NATIVE_PSS_DELTA_KB = "autojs.nodejs.r2.resource.maxNativePssDeltaKb"
        private const val ARG_MAX_THREAD_DELTA = "autojs.nodejs.r2.resource.maxThreadDelta"
        private const val ARG_MAX_FD_DELTA = "autojs.nodejs.r2.resource.maxFdDelta"
        private const val ARG_MAX_NODE_HEAP_DELTA_KB = "autojs.nodejs.r2.resource.maxNodeHeapDeltaKb"
        private const val ARG_MAX_LIBUV_HANDLE_DELTA = "autojs.nodejs.r2.resource.maxLibuvHandleDelta"
        private const val ARG_MAX_BRIDGE_ACTIVE_DELTA = "autojs.nodejs.r2.resource.maxBridgeActiveDelta"

        private val PERSISTENT_GROWTH_METRICS = listOf(
            PersistentGrowthMetric("rssKb", ARG_MAX_RSS_DELTA_KB, 32_768L),
            PersistentGrowthMetric("javaPssKb", ARG_MAX_JAVA_PSS_DELTA_KB, 8_192L),
            PersistentGrowthMetric("nativePssKb", ARG_MAX_NATIVE_PSS_DELTA_KB, 32_768L),
            PersistentGrowthMetric("threadCount", ARG_MAX_THREAD_DELTA, 4L),
            PersistentGrowthMetric("fdCount", ARG_MAX_FD_DELTA, 8L),
            PersistentGrowthMetric("nodeHeapUsedKb", ARG_MAX_NODE_HEAP_DELTA_KB, 8_192L),
            PersistentGrowthMetric("libuvHandleCount", ARG_MAX_LIBUV_HANDLE_DELTA, 0L),
            PersistentGrowthMetric("bridgeActiveCount", ARG_MAX_BRIDGE_ACTIVE_DELTA, 0L),
        )
    }
}
