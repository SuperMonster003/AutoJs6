package org.autojs.autojs.engine

import android.app.ActivityManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * R6 release soak for the required external runtime plugin.
 *
 * This deliberately does not reuse [NodeFinalSoakInstrumentationTest]: that older Phase 13
 * test executes the embedded backend and samples the instrumentation process. R6 must keep a
 * single plugin process-global runtime alive while every execution receives a fresh isolate and
 * execution state, and every resource sample must belong to the PID returned by the plugin.
 */
class NodeR6PersistentPluginSoakInstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()

    @Test
    fun persistentRequiredPluginSoakProducesR6Evidence() {
        val kind = argument(ARG_KIND, KIND_QUICK).lowercase()
        require(kind in SUPPORTED_KINDS) { "Unsupported R6 soak kind '$kind'." }
        val runToken = argument(ARG_RUN_TOKEN, "")
        require(RUN_TOKEN_PATTERN.matches(runToken)) {
            "R6 soak requires a runner-generated 64-character hexadecimal runToken."
        }
        assertEquals("R6 soak must be built as nodePlugin", "nodePlugin", BuildConfig.NODEJS_RELEASE_FLAVOR)
        assertEquals("R6 soak must be built with the required plugin backend", "plugin", BuildConfig.NODEJS_RUNTIME_BACKEND)

        val durationMs = durationMsFor(kind)
        val maximumSampleIntervalMs = if (kind == KIND_SMOKE) {
            minOf(MAX_SAMPLE_INTERVAL_MS, (durationMs / MIN_REQUIRED_SAMPLES).coerceAtLeast(MIN_SAMPLE_INTERVAL_MS))
        } else {
            MAX_SAMPLE_INTERVAL_MS
        }
        val sampleIntervalMs = argumentLong(
            ARG_SAMPLE_INTERVAL_MS,
            if (kind == KIND_SMOKE) SMOKE_SAMPLE_INTERVAL_MS else DEFAULT_SAMPLE_INTERVAL_MS,
        ).coerceIn(MIN_SAMPLE_INTERVAL_MS, maximumSampleIntervalMs)
        val cycleDelayMs = argumentLong(
            ARG_CYCLE_DELAY_MS,
            if (kind == KIND_SMOKE) SMOKE_CYCLE_DELAY_MS else DEFAULT_CYCLE_DELAY_MS,
        ).coerceIn(0L, minOf(MAX_CYCLE_DELAY_MS, sampleIntervalMs / 2L))
        val livenessRecoverabilityEveryCycles = argumentInt(
            ARG_LIVENESS_RECOVERABILITY_EVERY_CYCLES,
            if (kind == KIND_SMOKE) SMOKE_LIVENESS_RECOVERABILITY_EVERY_CYCLES else DEFAULT_LIVENESS_RECOVERABILITY_EVERY_CYCLES,
        ).coerceAtLeast(1)
        val minimumRequiredSamples = minimumRequiredSamples(kind, durationMs, sampleIntervalMs)
        var startWallMs = 0L
        var startElapsedMs = 0L
        var startUptimeMs = 0L
        var deadlineMs = 0L
        val samples = JSONArray()
        val runs = JSONArray()
        val failures = JSONArray()
        val livenessRecoverabilityDetails = JSONArray()
        val report = JSONObject()
            .put("schema", REPORT_SCHEMA)
            .put("kind", kind)
            .put("runToken", runToken)
            .put("status", "running")
            .put("packageName", context.packageName)
            .put("generatedAt", JSONObject.NULL)
            .put("startedAtEpochMs", JSONObject.NULL)
            .put("device", deviceJson())
            .put("identity", JSONObject())
            .put("artifacts", JSONObject())
            .put(
                "configuration",
                JSONObject()
                    .put("durationMs", durationMs)
                    .put("sampleIntervalMs", sampleIntervalMs)
                    .put("maximumSampleIntervalMs", MAX_SAMPLE_INTERVAL_MS)
                    .put("minimumRequiredSamples", minimumRequiredSamples)
                    .put("cycleDelayMs", cycleDelayMs)
                    .put("cycleTimeoutMs", CYCLE_TIMEOUT_MS)
                    .put("livenessRecoverabilityEveryCycles", livenessRecoverabilityEveryCycles)
                    .put("wakeLockPolicy", WAKE_LOCK_POLICY)
                    .put("wakeLockTimeoutMs", durationMs + WAKE_LOCK_GRACE_MS)
                    .put("wakeLockGraceMs", WAKE_LOCK_GRACE_MS)
                    .put("releaseFlavor", BuildConfig.NODEJS_RELEASE_FLAVOR)
                    .put("backend", BuildConfig.NODEJS_RUNTIME_BACKEND)
                    .put("pluginRequired", true)
                    .put("settleBetweenExecutions", false)
                    .put("processKillDuringSoak", false)
                    .put("processDeathRecoveryEvidenceScenario", "R5_07")
                    .put(
                        "livenessRecoverabilityMode",
                        "non_destructive_binder_session_health_and_recoverability",
                    )
                    .put(
                        "livenessRecoverabilityRationale",
                        "A process kill would end the continuous-runtime reuse experiment. " +
                                "Periodic checks instead validate the live required-plugin Binder session, " +
                                "runtime-ready state, persistent-process contract, stable PID, and host reconnect metadata. " +
                                "Process-death recovery is proven separately by device scenario R5_07.",
                    )
                    .put("thresholds", thresholdJson()),
            )
            .put("cycles", 0)
            .put("runs", runs)
            .put("samples", samples)
            .put("failures", failures)
            .put("livenessRecoverabilityCheckDetails", livenessRecoverabilityDetails)

        val originalProperties = MUTATED_SYSTEM_PROPERTIES.associateWith(System::getProperty)
        val workDir = File(context.cacheDir, "node-r6-persistent-plugin-soak-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create R6 plugin soak directory: $absolutePath" }
            File(this, "state.cjs").writeText("module.exports = { count: 0 };\n")
        }
        var cycles = 0
        var nextSampleAt = 0L
        var previousResult: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult? = null
        var firstIdentity: RuntimeIdentity? = null
        var lastCycle: CycleResult? = null
        var lastSampledCycle = 0
        var allFreshExecutionState = true
        var processRuntimeReused = true
        var livenessRecoverabilityChecksPassed = true
        var runtimeFailure = false
        var slotFailure = false
        var orphanProcess = true
        var wakeLock: PowerManager.WakeLock? = null
        var wakeLockAcquired = false
        var thrown: Throwable? = null

        fun recordCleanupFailure(metric: String, error: Throwable) {
            runtimeFailure = true
            slotFailure = true
            livenessRecoverabilityChecksPassed = false
            failures.put(
                JSONObject()
                    .put("scenario", "r6-persistent-plugin-soak-cleanup")
                    .put("metric", metric)
                    .put("type", error.javaClass.name)
                    .put("message", error.message.orEmpty()),
            )
        }

        try {
            val acquiredWakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "${context.packageName}:node-r6-persistent-plugin-soak",
                )
                .apply {
                    setReferenceCounted(false)
                }
            wakeLock = acquiredWakeLock
            acquiredWakeLock.acquire(durationMs + WAKE_LOCK_GRACE_MS)
            check(acquiredWakeLock.isHeld) {
                "Failed to acquire the bounded R6 soak partial wake lock."
            }
            wakeLockAcquired = true
            startWallMs = System.currentTimeMillis()
            startElapsedMs = SystemClock.elapsedRealtime()
            startUptimeMs = SystemClock.uptimeMillis()
            deadlineMs = startElapsedMs + durationMs
            nextSampleAt = startElapsedMs
            report.put("startedAtEpochMs", startWallMs)
            System.setProperty(NODE_BACKEND_PROPERTY, "plugin")
            System.setProperty(PLUGIN_RUNTIME_ENABLED_PROPERTY, "true")
            System.setProperty(PLUGIN_RUNTIME_REQUIRED_PROPERTY, "true")
            assertTrue("R6 soak did not select the plugin backend", NodeJsRuntimePluginBridge.isRequested())
            assertTrue("R6 soak did not select required-plugin semantics", NodeJsRuntimePluginBridge.isRequired())

            NodeJsRuntimePluginHost.invalidateForTest(context)
            val preflight = requirePersistentLivePlugin()
            report.put("plugin", preflight.toJson())

            // At least two executions are mandatory, including an explicitly short smoke run.
            while (cycles < MIN_EXECUTIONS || SystemClock.elapsedRealtime() < deadlineMs) {
                cycles += 1
                val cycle = runPluginCycle(workDir, cycles)
                val result = cycle.result
                val payload = cycle.payload
                assertPluginCycleSucceeded(cycles, result, payload)

                val currentIdentity = runtimeIdentity(result)
                val first = firstIdentity
                if (first == null) {
                    firstIdentity = currentIdentity
                    assertEquals("first plugin runtime initialization count", 1L, currentIdentity.initCount)
                    assertTrue("first plugin execution sequence is invalid", currentIdentity.executionSequence > 0L)
                } else {
                    assertStableRuntimeIdentity(cycles, first, checkNotNull(previousResult), result, currentIdentity)
                }
                allFreshExecutionState = allFreshExecutionState && payload.getBoolean("freshExecutionState")
                processRuntimeReused = processRuntimeReused && (cycles == 1 || result.nativeValues[PROCESS_RUNTIME_REUSED_KEY] == "true")
                runs.put(compactCycleRecord(cycle))
                previousResult = result
                lastCycle = cycle

                val now = SystemClock.elapsedRealtime()
                if (cycles == 1 || now >= nextSampleAt || now + cycleDelayMs >= deadlineMs) {
                    samples.put(
                        captureRemoteSample(
                            cycle = cycle,
                            startElapsedMs = startElapsedMs,
                            preflight = preflight,
                            identity = checkNotNull(firstIdentity),
                        ),
                    )
                    lastSampledCycle = cycles
                    do {
                        nextSampleAt += sampleIntervalMs
                    } while (nextSampleAt <= now)
                }

                if (cycles % livenessRecoverabilityEveryCycles == 0) {
                    val detail = performLivenessRecoverabilityCheck(preflight, checkNotNull(firstIdentity), cycles)
                    livenessRecoverabilityDetails.put(detail)
                }

                if (cycleDelayMs > 0L && now + cycleDelayMs < deadlineMs) {
                    SystemClock.sleep(cycleDelayMs)
                }
            }

            val finalCycle = checkNotNull(lastCycle)
            if (lastSampledCycle != cycles) {
                samples.put(
                    captureRemoteSample(
                        cycle = finalCycle,
                        startElapsedMs = startElapsedMs,
                        preflight = preflight,
                        identity = checkNotNull(firstIdentity),
                    ),
                )
            }
            if (livenessRecoverabilityDetails.length() == 0 || cycles % livenessRecoverabilityEveryCycles != 0) {
                livenessRecoverabilityDetails.put(
                    performLivenessRecoverabilityCheck(
                        preflight,
                        checkNotNull(firstIdentity),
                        cycles,
                    ),
                )
            }
            livenessRecoverabilityChecksPassed = (0 until livenessRecoverabilityDetails.length()).all { index ->
                livenessRecoverabilityDetails.getJSONObject(index).optBoolean("passed", false)
            }
            orphanProcess = hasUnexpectedPluginProcess(finalCycle.result)
            assertFalse("R6 soak found an orphan or duplicate plugin process", orphanProcess)
            assertTrue(
                "R6 soak captured ${samples.length()} samples; at least $minimumRequiredSamples are required",
                samples.length() >= minimumRequiredSamples,
            )
        } catch (error: Throwable) {
            thrown = error
            runtimeFailure = true
            slotFailure = true
            livenessRecoverabilityChecksPassed = false
            failures.put(
                JSONObject()
                    .put("scenario", "r6-persistent-plugin-soak")
                    .put("metric", "exception")
                    .put("type", error.javaClass.name)
                    .put("message", error.message.orEmpty()),
            )
        } finally {
            val endWallMs = if (wakeLockAcquired) System.currentTimeMillis() else 0L
            val endElapsedMs = if (wakeLockAcquired) SystemClock.elapsedRealtime() else 0L
            val endUptimeMs = if (wakeLockAcquired) SystemClock.uptimeMillis() else 0L
            val wakeLockHeldUntilCompletion = wakeLockAcquired && wakeLock?.isHeld == true
            val wakeLockHeldElapsedMs = if (wakeLockAcquired) {
                (endElapsedMs - startElapsedMs).coerceAtLeast(0L)
            } else {
                0L
            }
            val wakeLockHeldUptimeMs = if (wakeLockAcquired) {
                (endUptimeMs - startUptimeMs).coerceAtLeast(0L)
            } else {
                0L
            }
            val suspendDurationMs =
                (wakeLockHeldElapsedMs - wakeLockHeldUptimeMs).coerceAtLeast(0L)
            if (!wakeLockHeldUntilCompletion) {
                recordCleanupFailure(
                    "wakeLockHeldUntilCompletion",
                    IllegalStateException("The bounded R6 soak partial wake lock was not held until completion."),
                )
            }
            var propertiesRestored = true
            originalProperties.forEach { (name, value) ->
                runCatching {
                    if (value == null) System.clearProperty(name) else System.setProperty(name, value)
                }.onFailure { error ->
                    propertiesRestored = false
                    recordCleanupFailure("restoreSystemProperty:$name", error)
                }
            }
            val pluginHostInvalidated = runCatching {
                NodeJsRuntimePluginHost.invalidateForTest(context)
            }.fold(
                onSuccess = { true },
                onFailure = { error ->
                    recordCleanupFailure("invalidatePluginHost", error)
                    false
                },
            )
            val workDirectoryDeleted = runCatching {
                val deleted = !workDir.exists() || workDir.deleteRecursively()
                check(deleted && !workDir.exists()) {
                    "Failed to delete R6 plugin soak directory: ${workDir.absolutePath}"
                }
            }.fold(
                onSuccess = { true },
                onFailure = { error ->
                    recordCleanupFailure("deleteWorkDirectory", error)
                    false
                },
            )
            val wakeLockReleased = runCatching {
                val lock = checkNotNull(wakeLock) {
                    "The bounded R6 soak partial wake lock was not created."
                }
                if (lock.isHeld) {
                    lock.release()
                }
                check(!lock.isHeld) {
                    "Failed to release the R6 soak partial wake lock."
                }
            }.fold(
                onSuccess = { true },
                onFailure = { error ->
                    recordCleanupFailure("releaseWakeLock", error)
                    false
                },
            )
            val cleanup = JSONObject()
                .put("propertiesRestored", propertiesRestored)
                .put("pluginHostInvalidated", pluginHostInvalidated)
                .put("workDirectoryDeleted", workDirectoryDeleted)
                .put("wakeLockAcquired", wakeLockAcquired)
                .put("wakeLockHeldUntilCompletion", wakeLockHeldUntilCompletion)
                .put("wakeLockReleased", wakeLockReleased)
                .put(
                    "complete",
                    propertiesRestored &&
                            pluginHostInvalidated &&
                            workDirectoryDeleted &&
                            wakeLockAcquired &&
                            wakeLockHeldUntilCompletion &&
                            wakeLockReleased,
                )
            val powerManagement = JSONObject()
                .put("wakeLockType", "partial")
                .put("wakeLockHeldElapsedMs", wakeLockHeldElapsedMs)
                .put("wakeLockHeldUptimeMs", wakeLockHeldUptimeMs)
                .put("suspendDurationMs", suspendDurationMs)
            val growth = analyzeMonotonicGrowth(samples)
            val monotonicLeak = growth.getBoolean("monotonicLeak")
            if (monotonicLeak) {
                failures.put(
                    JSONObject()
                        .put("scenario", "r6-persistent-plugin-soak")
                        .put("metric", "monotonicLeak")
                        .put("message", "One or more remote plugin resource metrics grew monotonically beyond threshold.")
                        .put("analysis", growth),
                )
            }
            val durationActualMs = wakeLockHeldElapsedMs
            val analysis = JSONObject()
                .put("monotonicLeak", monotonicLeak)
                .put("orphanProcess", orphanProcess)
                .put("unrecoverableRuntime", runtimeFailure)
                .put("unrecoverableSlot", slotFailure)
                .put("processRuntimeReused", processRuntimeReused && cycles >= MIN_EXECUTIONS)
                .put("freshExecutionState", allFreshExecutionState && cycles >= MIN_EXECUTIONS)
                .put(
                    "livenessRecoverabilityChecksPassed",
                    livenessRecoverabilityChecksPassed && livenessRecoverabilityDetails.length() > 0,
                )
                .put("livenessRecoverabilityChecks", livenessRecoverabilityDetails.length())
                .put("livenessRecoverabilityMode", "non_destructive_binder_session_health_and_recoverability")
                .put("growth", growth)
            report
                .put("status", if (failures.length() == 0) "passed" else "failed")
                .put("generatedAt", if (endWallMs > 0L) isoTimestamp(endWallMs) else JSONObject.NULL)
                .put("endedAtEpochMs", if (endWallMs > 0L) endWallMs else JSONObject.NULL)
                .put("durationMs", durationActualMs)
                .put("durationHours", durationActualMs.toDouble() / MILLIS_PER_HOUR.toDouble())
                .put("cycles", cycles)
                .put("cleanup", cleanup)
                .put("powerManagement", powerManagement)
                .put("analysis", analysis)

            val reportFile = writeReport(kind, runToken, report)
            println("node.r6.persistent_plugin_soak.report=${reportFile.absolutePath}")
            println("node.r6.persistent_plugin_soak.public=${report.optString("devicePublicReportPath")}")
            println("node.r6.persistent_plugin_soak.pull=adb pull $DEVICE_REPORT_DIR/. app/build/reports/nodejs/r6-soak-runs")
        }

        thrown?.let { throw it }
        assertTrue("R6 persistent-plugin soak failures:\n${failures.toString(2)}", failures.length() == 0)
    }

    private fun requirePersistentLivePlugin(): PluginPreflight {
        val candidates = runBlocking { NodeJsRuntimePluginHost.discover(context) }
        assertTrue("R6 soak requires an installed and trusted runtime plugin", candidates.isNotEmpty())
        val candidate = candidates.first()
        assertTrue("R6 plugin service must be exported: ${candidate.component}", candidate.serviceInfo.exported)
        assertEquals("R6 plugin service permission drift", PLUGIN_PERMISSION, candidate.serviceInfo.permission)
        assertTrue(
            "R6 plugin signer SHA-256 is missing: ${candidate.signerSha256}",
            candidate.signerSha256?.matches(SHA_256_PATTERN) == true,
        )
        assertTrue(
            "R6 plugin trust state is not authorized: ${candidate.authorizationState}",
            candidate.authorizationState in AUTHORIZED_PLUGIN_STATES,
        )
        val runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(context) }
        NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
        val pid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1)
        assertTrue("R6 plugin PID is not a remote process: $pid", pid > 0 && pid != Process.myPid())
        assertTrue("R6 plugin runtime is not ready: $runtimeInfo", runtimeInfo.getBoolean("runtimeReady", false))
        assertTrue(
            "R6 plugin does not advertise a persistent process runtime: $runtimeInfo",
            runtimeInfo.getBoolean("persistentProcessRuntime", false),
        )
        assertTrue(
            "R6 plugin runtime is not dedicated: $runtimeInfo",
            runtimeInfo.getBoolean("dedicatedRuntimeProcess", false),
        )
        assertEquals("R6 plugin max-concurrency contract drift", 1, runtimeInfo.getInt("maxConcurrentExecutions", -1))
        assertEquals("R6 plugin queue-capacity contract drift", 0, runtimeInfo.getInt("queueCapacity", -1))
        val processAbi = runtimeInfo.getString("processAbi").orEmpty()
        assertTrue("R6 plugin process ABI is missing", processAbi.isNotBlank())
        val supportedAbis = runtimeInfo.getStringArray("supportedAbis").orEmpty().toList()
        assertTrue("R6 plugin process ABI $processAbi is not advertised: $supportedAbis", processAbi in supportedAbis)
        assertTrue("R6 plugin process ABI $processAbi is unsupported by the device", processAbi in Build.SUPPORTED_ABIS)
        val runtimeSlot = runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT).orEmpty()
        val nodeVersion = runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION).orEmpty()
        assertEquals("R6 plugin runtime slot drift", "node24_5", runtimeSlot)
        assertEquals("R6 plugin Node version drift", "24.5.0", nodeVersion.removePrefix("v"))
        return PluginPreflight(
            packageName = candidate.serviceInfo.packageName,
            serviceName = candidate.serviceInfo.name,
            signerSha256 = candidate.signerSha256.orEmpty(),
            authorizationState = candidate.authorizationState,
            runtimeSlot = runtimeSlot,
            nodeVersion = nodeVersion,
            processAbi = processAbi,
            supportedAbis = supportedAbis,
            pid = pid,
            hostSessionGeneration = runtimeInfo.getLong("hostSessionGeneration", 0L),
        )
    }

    private fun runPluginCycle(workDir: File, cycle: Int): CycleResult {
        val stateModule = File(workDir, "state.cjs")
        val result = NodeJsRuntimePluginBridge.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = pluginCycleSource(cycle),
                sourceName = "r6-soak-$cycle.js",
                workingDirectory = workDir.absolutePath,
                sandboxRoot = workDir.absolutePath,
                executionId = "r6-soak-$cycle-${UUID.randomUUID()}",
                timeoutMs = CYCLE_TIMEOUT_MS,
                env = mapOf(
                    REQUEST_CYCLE_ENV to cycle.toString(),
                    REQUEST_STATE_MODULE_ENV to stateModule.name,
                ),
            ),
        )
        NodeEmbeddedTestHarness.assertSucceeded("R6 persistent plugin cycle $cycle", result)
        val payload = parsePayload(result.stdout)
            ?: throw AssertionError("R6 cycle $cycle omitted $RESOURCE_PAYLOAD_PREFIX: ${result.stdout}")
        return CycleResult(result, payload)
    }

    private fun pluginCycleSource(cycle: Int): String = """
        "nodejs";
        const fs = require("node:fs");
        function activeHandleCount() {
          try {
            return typeof process._getActiveHandles === "function" ? process._getActiveHandles().length : -1;
          } catch (_) {
            return -2;
          }
        }
        const priorGlobal = typeof globalThis.__autojs6R6SoakLeak;
        const priorEnv = typeof process.env.AUTOJS6_R6_SOAK_LEAK;
        const state = require("./state.cjs");
        const priorModuleState = state.count;
        const moduleStateFresh = state.count === 0;
        state.count += 1;
        const requestCycle = Number(process.env.$REQUEST_CYCLE_ENV);
        globalThis.__autojs6R6SoakLeak = $cycle;
        process.env.AUTOJS6_R6_SOAK_LEAK = String($cycle);
        const timer = setInterval(() => {}, 1000);
        clearInterval(timer);
        const file = "r6-soak-cycle.tmp";
        fs.writeFileSync(file, "cycle-" + requestCycle, "utf8");
        const fileRoundTrip = fs.readFileSync(file, "utf8") === "cycle-" + requestCycle;
        fs.unlinkSync(file);
        setImmediate(() => {
          const memory = process.memoryUsage();
          console.log("$RESOURCE_PAYLOAD_PREFIX" + JSON.stringify({
            cycle: requestCycle,
            pid: process.pid,
            freshExecutionState: priorGlobal === "undefined" && priorEnv === "undefined" && moduleStateFresh,
            priorGlobal,
            priorEnv,
            priorModuleState,
            moduleStateFresh,
            moduleStateCount: state.count,
            fileRoundTrip,
            rssKb: Math.floor(memory.rss / 1024),
            nodeHeapTotalKb: Math.floor(memory.heapTotal / 1024),
            nodeHeapUsedKb: Math.floor(memory.heapUsed / 1024),
            nodeExternalKb: Math.floor(memory.external / 1024),
            nodeArrayBuffersKb: Math.floor(memory.arrayBuffers / 1024),
            libuvHandleCount: activeHandleCount()
          }));
        });
    """.trimIndent()

    private fun assertPluginCycleSucceeded(
        cycle: Int,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        payload: JSONObject,
    ) {
        assertEquals("cycle $cycle did not use plugin backend", "true", result.nativeValues[PLUGIN_ENABLED_KEY])
        assertEquals("cycle $cycle plugin fallback unexpectedly became safe", "false", result.nativeValues[FALLBACK_SAFE_KEY])
        assertEquals("cycle $cycle diagnostics did not originate in plugin", "plugin", result.nativeValues[DIAGNOSTICS_SOURCE_KEY])
        assertEquals("cycle $cycle process runtime is not healthy", "true", result.nativeValues[PROCESS_RUNTIME_HEALTHY_KEY])
        assertEquals("cycle $cycle process runtime is poisoned", "false", result.nativeValues[PROCESS_RUNTIME_POISONED_KEY])
        assertEquals("cycle $cycle runtime teardown is not clean", "true", result.nativeValues[PROCESS_RUNTIME_TEARDOWN_KEY])
        assertEquals("cycle $cycle execution teardown is not clean", "true", result.nativeValues[EXECUTION_TEARDOWN_KEY])
        assertEquals("cycle $cycle plugin process metrics are incomplete", "ready", result.nativeValues[RUNTIME_PROCESS_METRICS_STATUS_KEY])
        assertEquals(
            "cycle $cycle plugin process metrics did not originate from plugin procfs",
            "plugin_process_procfs",
            result.nativeValues[RUNTIME_PROCESS_METRICS_SOURCE_KEY],
        )
        assertEquals(
            "cycle $cycle plugin process metrics PID differs from result PID",
            result.pid.toString(),
            result.nativeValues[RUNTIME_PROCESS_PID_KEY],
        )
        assertEquals(
            "cycle $cycle plugin process status is unreadable",
            "true",
            result.nativeValues[RUNTIME_PROCESS_PROC_STATUS_READABLE_KEY],
        )
        assertTrue(
            "cycle $cycle plugin process RSS is unavailable",
            requireNativeLong(result, RUNTIME_PROCESS_RSS_KB_KEY) > 0L,
        )
        assertTrue(
            "cycle $cycle plugin process thread count is unavailable",
            requireNativeLong(result, RUNTIME_PROCESS_THREAD_COUNT_KEY) > 0L,
        )
        assertTrue(
            "cycle $cycle plugin process fd count is unavailable",
            requireNativeLong(result, RUNTIME_PROCESS_FD_COUNT_KEY) > 0L,
        )
        assertEquals("cycle $cycle retained bridge resources", "0", result.nativeValues[BRIDGE_ACTIVE_COUNT_KEY])
        assertEquals("cycle $cycle retained bridge requests", "0", result.nativeValues[BRIDGE_PENDING_COUNT_KEY])
        assertEquals("cycle $cycle payload PID differs from result PID", result.pid, payload.getInt("pid"))
        assertEquals("cycle $cycle payload sequence mismatch", cycle, payload.getInt("cycle"))
        assertTrue("cycle $cycle did not receive fresh execution state: $payload", payload.getBoolean("freshExecutionState"))
        assertTrue("cycle $cycle reused mutable module state: $payload", payload.getBoolean("moduleStateFresh"))
        assertEquals("cycle $cycle module state count drift", 1, payload.getInt("moduleStateCount"))
        assertTrue("cycle $cycle file workload failed: $payload", payload.getBoolean("fileRoundTrip"))
        assertTrue("cycle $cycle did not expose a remote PID", result.pid > 0 && result.pid != Process.myPid())
        REQUIRED_PAYLOAD_NUMBERS.forEach { key ->
            assertTrue("cycle $cycle payload metric $key is unavailable: $payload", payload.getLong(key) >= 0L)
        }
    }

    private fun runtimeIdentity(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult): RuntimeIdentity =
        RuntimeIdentity(
            pid = result.pid,
            processName = result.processName,
            sessionGeneration = requireNativeLong(result, PLUGIN_SESSION_GENERATION_KEY),
            runtimeGeneration = requireNativeLong(result, PROCESS_RUNTIME_GENERATION_KEY),
            initCount = requireNativeLong(result, PROCESS_RUNTIME_INIT_COUNT_KEY),
            executionSequence = requireNativeLong(result, PROCESS_RUNTIME_SEQUENCE_KEY),
        )

    private fun assertStableRuntimeIdentity(
        cycle: Int,
        first: RuntimeIdentity,
        previous: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        current: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        currentIdentity: RuntimeIdentity,
    ) {
        assertEquals("cycle $cycle changed plugin PID", first.pid, currentIdentity.pid)
        assertEquals("cycle $cycle changed plugin process name", first.processName, currentIdentity.processName)
        assertEquals("cycle $cycle changed host Binder session", first.sessionGeneration, currentIdentity.sessionGeneration)
        assertEquals("cycle $cycle changed process runtime generation", first.runtimeGeneration, currentIdentity.runtimeGeneration)
        assertEquals("cycle $cycle reinitialized process runtime", 1L, currentIdentity.initCount)
        assertEquals("cycle $cycle did not report runtime reuse", "true", current.nativeValues[PROCESS_RUNTIME_REUSED_KEY])
        val previousSequence = requireNativeLong(previous, PROCESS_RUNTIME_SEQUENCE_KEY)
        assertEquals("cycle $cycle execution sequence did not advance exactly once", previousSequence + 1L, currentIdentity.executionSequence)
    }

    private fun captureRemoteSample(
        cycle: CycleResult,
        startElapsedMs: Long,
        preflight: PluginPreflight,
        identity: RuntimeIdentity,
    ): JSONObject {
        val result = cycle.result
        val payload = cycle.payload
        val memory = retryRemoteMetric("process memory", result.pid) {
            remoteProcessMemory(result.pid)
        }
        val proc = retryOptionalRemoteMetric {
            remoteProcMetrics(result.pid)
        }
        val procFallbackLiveness = if (proc.value == null) {
            performLivenessRecoverabilityCheck(
                preflight = preflight,
                identity = identity,
                cycle = payload.getInt("cycle"),
            )
                .put("trigger", "remote_proc_probe_unavailable")
                .put("probeFailure", proc.failure.orEmpty())
        } else {
            null
        }
        val runtimeRssKb = requireNativeLong(result, RUNTIME_PROCESS_RSS_KB_KEY)
        val runtimeThreadCount = requireNativeLong(result, RUNTIME_PROCESS_THREAD_COUNT_KEY)
        val runtimeFdCount = requireNativeLong(result, RUNTIME_PROCESS_FD_COUNT_KEY)
        val runtimeJavaHeapKb = requireNativeLong(result, RUNTIME_PROCESS_JAVA_HEAP_USED_KB_KEY)
        val runtimeNativeHeapKb = requireNativeLong(result, RUNTIME_PROCESS_NATIVE_HEAP_ALLOCATED_KB_KEY)
        val bridgeDiagnostics = result.nativeValues
            .filterKeys(BRIDGE_DIAGNOSTIC_KEYS::contains)
        val runtimeProcessDiagnostics = result.nativeValues
            .filterKeys { key -> key.startsWith("runtime_process.") }
        return JSONObject()
            .put("label", "r6.plugin.cycle.${payload.getInt("cycle")}")
            .put("cycle", payload.getInt("cycle"))
            .put("capturedAtEpochMs", System.currentTimeMillis())
            .put("elapsedMs", SystemClock.elapsedRealtime() - startElapsedMs)
            .put("servicePid", result.pid)
            .put("processName", result.processName)
            .put("pluginSessionGeneration", requireNativeLong(result, PLUGIN_SESSION_GENERATION_KEY))
            .put("processRuntimeGeneration", requireNativeLong(result, PROCESS_RUNTIME_GENERATION_KEY))
            .put("processRuntimeInitCount", requireNativeLong(result, PROCESS_RUNTIME_INIT_COUNT_KEY))
            .put("executionSequence", requireNativeLong(result, PROCESS_RUNTIME_SEQUENCE_KEY))
            .put("runtimeReused", result.nativeValues[PROCESS_RUNTIME_REUSED_KEY] == "true")
            .put("rssKb", runtimeRssKb)
            .put("nodeRssKb", payload.getLong("rssKb"))
            .put(
                "remoteProcCaptureStatus",
                if (proc.value != null) "captured" else "unavailable_but_plugin_alive",
            )
            .put("procMetricsAvailable", proc.value != null)
            .put("procRssKb", proc.value?.rssKb ?: JSONObject.NULL)
            .put("totalPssKb", memory.value.totalPssKb)
            .put("javaPssKb", memory.value.javaPssKb)
            .put("nativePssKb", memory.value.nativePssKb)
            .put("javaHeapKb", runtimeJavaHeapKb)
            .put("javaHeapCommittedKb", requireNativeLong(result, RUNTIME_PROCESS_JAVA_HEAP_COMMITTED_KB_KEY))
            .put("javaHeapMaxKb", requireNativeLong(result, RUNTIME_PROCESS_JAVA_HEAP_MAX_KB_KEY))
            .put("nativeHeapKb", runtimeNativeHeapKb)
            .put("nativeHeapCommittedKb", requireNativeLong(result, RUNTIME_PROCESS_NATIVE_HEAP_COMMITTED_KB_KEY))
            .put("threadCount", runtimeThreadCount)
            .put("procThreadCount", proc.value?.threadCount ?: JSONObject.NULL)
            .put("fdCount", runtimeFdCount)
            .put("fdCountSource", "plugin_uid_proc_self_fd")
            .put("nodeHeapTotalKb", payload.getLong("nodeHeapTotalKb"))
            .put("nodeHeapUsedKb", payload.getLong("nodeHeapUsedKb"))
            .put("nodeExternalKb", payload.getLong("nodeExternalKb"))
            .put("nodeArrayBuffersKb", payload.getLong("nodeArrayBuffersKb"))
            .put("libuvHandleCount", payload.getLong("libuvHandleCount"))
            .put("activeBridgeResourceCount", requireNativeLong(result, BRIDGE_ACTIVE_COUNT_KEY))
            .put("pendingBridgeRequestCount", requireNativeLong(result, BRIDGE_PENDING_COUNT_KEY))
            .put("freshExecutionState", payload.getBoolean("freshExecutionState"))
            .put("remoteMemoryCaptureAttempts", memory.attempts)
            .put("remoteProcCaptureAttempts", proc.attempts)
            .put("remoteProcCaptureFailure", proc.failure ?: JSONObject.NULL)
            .put("remoteProcFallbackLiveness", procFallbackLiveness ?: JSONObject.NULL)
            .put("bridgeDiagnostics", JSONObject(bridgeDiagnostics))
            .put("runtimeProcessDiagnostics", JSONObject(runtimeProcessDiagnostics))
    }

    private fun compactCycleRecord(cycle: CycleResult): JSONObject {
        val result = cycle.result
        val payload = cycle.payload
        val bridgeDiagnostics = result.nativeValues
            .filterKeys(BRIDGE_DIAGNOSTIC_KEYS::contains)
        val runtimeProcessDiagnostics = result.nativeValues
            .filterKeys { key -> key.startsWith("runtime_process.") }
        return JSONObject()
            .put("cycle", payload.getInt("cycle"))
            .put("completedAtEpochMs", System.currentTimeMillis())
            .put("servicePid", result.pid)
            .put("processName", result.processName)
            .put("pluginSessionGeneration", requireNativeLong(result, PLUGIN_SESSION_GENERATION_KEY))
            .put("processRuntimeGeneration", requireNativeLong(result, PROCESS_RUNTIME_GENERATION_KEY))
            .put("processRuntimeInitCount", requireNativeLong(result, PROCESS_RUNTIME_INIT_COUNT_KEY))
            .put("executionSequence", requireNativeLong(result, PROCESS_RUNTIME_SEQUENCE_KEY))
            .put("runtimeReused", result.nativeValues[PROCESS_RUNTIME_REUSED_KEY] == "true")
            .put("nodeRssKb", payload.getLong("rssKb"))
            .put("nodeHeapTotalKb", payload.getLong("nodeHeapTotalKb"))
            .put("nodeHeapUsedKb", payload.getLong("nodeHeapUsedKb"))
            .put("nodeExternalKb", payload.getLong("nodeExternalKb"))
            .put("nodeArrayBuffersKb", payload.getLong("nodeArrayBuffersKb"))
            .put("libuvHandleCount", payload.getLong("libuvHandleCount"))
            .put("activeBridgeResourceCount", requireNativeLong(result, BRIDGE_ACTIVE_COUNT_KEY))
            .put("pendingBridgeRequestCount", requireNativeLong(result, BRIDGE_PENDING_COUNT_KEY))
            .put("freshExecutionState", payload.getBoolean("freshExecutionState"))
            .put("bridgeDiagnostics", JSONObject(bridgeDiagnostics))
            .put("runtimeProcessDiagnostics", JSONObject(runtimeProcessDiagnostics))
    }

    private fun <T> retryRemoteMetric(
        label: String,
        pid: Int,
        block: () -> T,
    ): RetriedMetric<T> {
        var lastFailure: Exception? = null
        repeat(REMOTE_METRIC_MAX_ATTEMPTS) { attempt ->
            try {
                return RetriedMetric(
                    value = block(),
                    attempts = attempt + 1,
                )
            } catch (error: Exception) {
                lastFailure = error
                if (attempt + 1 < REMOTE_METRIC_MAX_ATTEMPTS) {
                    SystemClock.sleep(REMOTE_METRIC_RETRY_DELAY_MS)
                }
            }
        }
        throw AssertionError(
            "Remote $label remained unavailable for plugin PID $pid after " +
                    "$REMOTE_METRIC_MAX_ATTEMPTS attempts: ${lastFailure?.message.orEmpty()}",
            lastFailure,
        )
    }

    private fun <T> retryOptionalRemoteMetric(
        block: () -> T,
    ): OptionalRetriedMetric<T> {
        var lastFailure: Exception? = null
        repeat(REMOTE_METRIC_MAX_ATTEMPTS) { attempt ->
            try {
                return OptionalRetriedMetric(
                    value = block(),
                    attempts = attempt + 1,
                    failure = null,
                )
            } catch (error: Exception) {
                lastFailure = error
                if (attempt + 1 < REMOTE_METRIC_MAX_ATTEMPTS) {
                    SystemClock.sleep(REMOTE_METRIC_RETRY_DELAY_MS)
                }
            }
        }
        return OptionalRetriedMetric(
            value = null,
            attempts = REMOTE_METRIC_MAX_ATTEMPTS,
            failure = lastFailure
                ?.message
                .orEmpty()
                .take(REMOTE_OUTPUT_DIAGNOSTIC_CHARS),
        )
    }

    @Suppress("DEPRECATION")
    private fun remoteProcessMemory(pid: Int): RemoteProcessMemory {
        val dumpsys = executeShellCommand("dumpsys meminfo $pid")
        val activityMemory = runCatching {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull()
        }.getOrNull()
        val totalPssKb = parseMemorySummary(dumpsys, "TOTAL PSS")
            ?: parseMemoryTablePss(dumpsys, "TOTAL")
            ?: activityMemory?.totalPss?.toLong()
        val javaHeapKb = parseMemorySummary(dumpsys, "Java Heap")
            ?: parseMemoryTablePss(dumpsys, "Dalvik Heap")
            ?: activityMemory?.dalvikPss?.toLong()
        val nativeHeapKb = parseMemorySummary(dumpsys, "Native Heap")
            ?: parseMemoryTablePss(dumpsys, "Native Heap")
            ?: activityMemory?.nativePss?.toLong()
        check(totalPssKb != null && totalPssKb >= 0L) {
            "Remote total PSS is unavailable for plugin PID $pid."
        }
        check(javaHeapKb != null && javaHeapKb >= 0L) {
            "Remote Java heap is unavailable for plugin PID $pid."
        }
        check(nativeHeapKb != null && nativeHeapKb >= 0L) {
            "Remote native heap is unavailable for plugin PID $pid."
        }
        return RemoteProcessMemory(
            totalPssKb = checkNotNull(totalPssKb),
            javaPssKb = checkNotNull(javaHeapKb),
            nativePssKb = checkNotNull(nativeHeapKb),
        )
    }

    private fun remoteProcMetrics(pid: Int): RemoteProcMetrics {
        val status = executeShellCommand("cat /proc/$pid/status")
        val rssKb = parseProcStatusNumber(status, "VmRSS")
        val threadCount = parseProcStatusNumber(status, "Threads")
        check(rssKb != null && rssKb >= 0L) {
            "Remote RSS is unavailable for plugin PID $pid: ${status.take(REMOTE_OUTPUT_DIAGNOSTIC_CHARS)}"
        }
        check(threadCount != null && threadCount > 0L) {
            "Remote thread count is unavailable for plugin PID $pid: ${status.take(REMOTE_OUTPUT_DIAGNOSTIC_CHARS)}"
        }
        return RemoteProcMetrics(
            rssKb = checkNotNull(rssKb),
            threadCount = checkNotNull(threadCount),
        )
    }

    private fun parseProcStatusNumber(output: String, label: String): Long? =
        Regex("(?m)^${Regex.escape(label)}:\\s*([0-9]+)")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    private fun parseMemorySummary(output: String, label: String): Long? =
        Regex("(?m)^\\s*${Regex.escape(label)}:\\s*([0-9,]+)")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.replace(",", "")
            ?.toLongOrNull()

    private fun parseMemoryTablePss(output: String, label: String): Long? =
        Regex("(?m)^\\s*${Regex.escape(label)}\\s+([0-9,]+)\\s")
            .find(output)
            ?.groupValues
            ?.get(1)
            ?.replace(",", "")
            ?.toLongOrNull()

    private fun performLivenessRecoverabilityCheck(
        preflight: PluginPreflight,
        identity: RuntimeIdentity,
        cycle: Int,
    ): JSONObject {
        var runtimeInfo: android.os.Bundle? = null
        var failure: Throwable? = null
        try {
            runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(context) }
            NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
            assertTrue("liveness/recoverability check $cycle lost required-plugin mode", NodeJsRuntimePluginBridge.isRequired())
            assertEquals(
                "liveness/recoverability check $cycle changed plugin PID",
                identity.pid,
                runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1),
            )
            assertTrue(
                "liveness/recoverability check $cycle runtime is not ready",
                runtimeInfo.getBoolean("runtimeReady", false),
            )
            assertTrue(
                "liveness/recoverability check $cycle lost persistent runtime capability",
                runtimeInfo.getBoolean("persistentProcessRuntime", false),
            )
            assertEquals(
                "liveness/recoverability check $cycle changed host session generation",
                identity.sessionGeneration,
                runtimeInfo.getLong("hostSessionGeneration", -1L),
            )
            val discoveredPackage = runBlocking {
                NodeJsRuntimePluginHost.discover(context).first().serviceInfo.packageName
            }
            assertEquals(
                "liveness/recoverability check $cycle changed plugin package",
                preflight.packageName,
                discoveredPackage,
            )
        } catch (error: Throwable) {
            failure = error
        }
        val passed = failure == null
        val detail = JSONObject()
            .put("cycle", cycle)
            .put("checkedAtEpochMs", System.currentTimeMillis())
            .put("mode", "non_destructive_binder_session_health_and_recoverability")
            .put("passed", passed)
            .put("servicePid", runtimeInfo?.getInt(NodeJsRuntimeContract.KEY_PID, -1) ?: -1)
            .put("runtimeReady", runtimeInfo?.getBoolean("runtimeReady", false) ?: false)
            .put(
                "persistentProcessRuntime",
                runtimeInfo?.getBoolean("persistentProcessRuntime", false) ?: false,
            )
            .put("hostSessionGeneration", runtimeInfo?.getLong("hostSessionGeneration", -1L) ?: -1L)
        if (failure != null) {
            detail.put("failure", failure.message.orEmpty())
            throw AssertionError("R6 liveness/recoverability check failed at cycle $cycle", failure)
        }
        return detail
    }

    private fun hasUnexpectedPluginProcess(result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult): Boolean {
        val processName = result.processName
        assertTrue("R6 plugin process name is missing", processName.isNotBlank())
        require(PROCESS_NAME_PATTERN.matches(processName)) { "Unsafe plugin process name '$processName'." }
        val pids = executeShellCommand("pidof $processName")
            .trim()
            .split(Regex("\\s+"))
            .mapNotNull(String::toIntOrNull)
            .distinct()
        assertTrue("pidof did not find expected R6 plugin PID ${result.pid}: $pids", result.pid in pids)
        return pids.size != 1 || pids.single() != result.pid
    }

    private fun analyzeMonotonicGrowth(samples: JSONArray): JSONObject {
        var monotonicLeak = false
        val metrics = JSONArray()
        GROWTH_METRICS.forEach { metric ->
            val values = (0 until samples.length())
                .map { index -> samples.getJSONObject(index).getLong(metric.name) }
            val threshold = argumentLong(metric.thresholdArgument, metric.defaultThreshold)
            val enoughSamples = values.size >= MIN_GROWTH_SAMPLES
            val delta = values.lastOrNull()?.minus(values.firstOrNull() ?: 0L) ?: 0L
            val monotonic = enoughSamples && looksMonotonicRunaway(values)
            val detected = delta > threshold
            monotonicLeak = monotonicLeak || detected
            metrics.put(
                JSONObject()
                    .put("name", metric.name)
                    .put("sampleCount", values.size)
                    .put("baseline", values.firstOrNull() ?: JSONObject.NULL)
                    .put("final", values.lastOrNull() ?: JSONObject.NULL)
                    .put("delta", delta)
                    .put("threshold", threshold)
                    .put("monotonic", monotonic)
                    .put("growthDetected", detected)
                    .put("values", JSONArray(values)),
            )
        }
        return JSONObject()
            .put("method", "thresholded_terminal_or_monotonic_remote_plugin_growth")
            .put("minimumSamples", MIN_GROWTH_SAMPLES)
            .put("monotonicLeak", monotonicLeak)
            .put("metrics", metrics)
    }

    private fun looksMonotonicRunaway(values: List<Long>): Boolean {
        val increases = values.zipWithNext().count { (previous, current) -> current > previous }
        val recent = values.takeLast(minOf(values.size, RECENT_SAMPLE_WINDOW))
        val recentNonDecreases = recent.zipWithNext().count { (previous, current) -> current >= previous }
        return values.last() == values.maxOrNull() &&
                increases >= values.size - 2 &&
                recentNonDecreases == recent.size - 1
    }

    private fun parsePayload(stdout: String): JSONObject? =
        stdout.lineSequence()
            .mapNotNull { line ->
                val marker = line.indexOf(RESOURCE_PAYLOAD_PREFIX)
                if (marker < 0) null else line.substring(marker + RESOURCE_PAYLOAD_PREFIX.length).trim()
            }
            .firstOrNull()
            ?.let(::JSONObject)

    private fun requireNativeLong(
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        key: String,
    ): Long = result.nativeValues[key]?.toLongOrNull()
        ?: throw AssertionError("R6 plugin result omitted numeric $key: ${result.nativeValues}")

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
        }
    }

    private fun isoTimestamp(epochMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(epochMs))

    private fun writeReport(kind: String, runToken: String, report: JSONObject): File {
        val reportDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "nodejs/r6-soak").apply {
            mkdirs()
        }
        val fileName = "r6-persistent-plugin-soak-$kind-${runToken.take(RUN_TOKEN_FILE_PREFIX_LENGTH)}-${System.currentTimeMillis()}.json"
        val reportFile = File(reportDir, fileName)
        report
            .put("devicePrivateReportPath", reportFile.absolutePath)
            .put("devicePublicReportPath", "$DEVICE_REPORT_DIR/$fileName")
        val reportText = report.toString(2)
        reportFile.writeText(reportText)
        assertTrue("Failed to publish R6 soak report", writePublicReport(fileName, reportText))
        return reportFile
    }

    private fun writePublicReport(fileName: String, reportText: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/AutoJs6/nodejs/r6-soak")
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
                    "AutoJs6/nodejs/r6-soak",
                ).apply { mkdirs() }
                File(reportDir, fileName).writeText(reportText)
                true
            }
        }.getOrDefault(false)

    private fun deviceJson(): JSONObject = JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("device", Build.DEVICE)
        .put("apiLevel", Build.VERSION.SDK_INT)
        .put("release", Build.VERSION.RELEASE)
        .put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
        .put("primaryAbi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())

    private fun durationMsFor(kind: String): Long {
        val explicitMs = arguments.getString(ARG_DURATION_MS)?.toLongOrNull()
        val explicitHours = arguments.getString(ARG_DURATION_HOURS)?.toDoubleOrNull()?.let {
            (it * MILLIS_PER_HOUR.toDouble()).toLong()
        }
        val explicit = explicitMs ?: explicitHours
        if (kind == KIND_SMOKE) {
            return (explicit ?: DEFAULT_SMOKE_DURATION_MS)
                .coerceIn(MIN_SMOKE_DURATION_MS, MAX_SMOKE_DURATION_MS)
        }
        val minimum = if (kind == KIND_FINAL) FINAL_DURATION_MS else QUICK_DURATION_MS
        require(explicit == null || explicit >= minimum) {
            "R6 $kind evidence cannot use a shortened duration: requested=${explicit}ms minimum=${minimum}ms. " +
                    "Use kind=smoke for non-archivable development checks."
        }
        return explicit ?: minimum
    }

    private fun minimumRequiredSamples(kind: String, durationMs: Long, sampleIntervalMs: Long): Int =
        if (kind == KIND_SMOKE) {
            MIN_REQUIRED_SAMPLES
        } else {
            maxOf(MIN_REQUIRED_SAMPLES, (durationMs / sampleIntervalMs).toInt() + 1)
        }

    private fun thresholdJson(): JSONObject = JSONObject().apply {
        GROWTH_METRICS.forEach { metric ->
            put(metric.name, argumentLong(metric.thresholdArgument, metric.defaultThreshold))
        }
    }

    private fun argument(name: String, defaultValue: String): String =
        arguments.getString(name)?.trim()?.takeIf(String::isNotEmpty) ?: defaultValue

    private fun argumentLong(name: String, defaultValue: Long): Long =
        arguments.getString(name)?.toLongOrNull() ?: defaultValue

    private fun argumentInt(name: String, defaultValue: Int): Int =
        arguments.getString(name)?.toIntOrNull() ?: defaultValue

    private data class CycleResult(
        val result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        val payload: JSONObject,
    )

    private data class RuntimeIdentity(
        val pid: Int,
        val processName: String,
        val sessionGeneration: Long,
        val runtimeGeneration: Long,
        val initCount: Long,
        val executionSequence: Long,
    )

    private data class RemoteProcessMemory(
        val totalPssKb: Long,
        val javaPssKb: Long,
        val nativePssKb: Long,
    )

    private data class RemoteProcMetrics(
        val rssKb: Long,
        val threadCount: Long,
    )

    private data class RetriedMetric<T>(
        val value: T,
        val attempts: Int,
    )

    private data class OptionalRetriedMetric<T>(
        val value: T?,
        val attempts: Int,
        val failure: String?,
    )

    private data class PluginPreflight(
        val packageName: String,
        val serviceName: String,
        val signerSha256: String,
        val authorizationState: String,
        val runtimeSlot: String,
        val nodeVersion: String,
        val processAbi: String,
        val supportedAbis: List<String>,
        val pid: Int,
        val hostSessionGeneration: Long,
    ) {
        fun toJson(): JSONObject = JSONObject()
            .put("packageName", packageName)
            .put("serviceName", serviceName)
            .put("signerSha256", signerSha256)
            .put("authorizationState", authorizationState)
            .put("runtimeSlot", runtimeSlot)
            .put("nodeVersion", nodeVersion)
            .put("runtimeProcessAbi", processAbi)
            .put("supportedAbis", JSONArray(supportedAbis))
            .put("pid", pid)
            .put("hostSessionGeneration", hostSessionGeneration)
    }

    private data class GrowthMetric(
        val name: String,
        val thresholdArgument: String,
        val defaultThreshold: Long,
    )

    private companion object {
        private const val REPORT_SCHEMA = "autojs6-node-r6-persistent-plugin-soak-v1"
        private const val DEVICE_REPORT_DIR = "/sdcard/Download/AutoJs6/nodejs/r6-soak"
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val QUICK_DURATION_MS = 8L * MILLIS_PER_HOUR
        private const val FINAL_DURATION_MS = 24L * MILLIS_PER_HOUR
        private const val DEFAULT_SMOKE_DURATION_MS = 30_000L
        private const val MIN_SMOKE_DURATION_MS = 5_000L
        private const val MAX_SMOKE_DURATION_MS = 600_000L
        private const val DEFAULT_SAMPLE_INTERVAL_MS = 300_000L
        private const val SMOKE_SAMPLE_INTERVAL_MS = 2_000L
        private const val MIN_SAMPLE_INTERVAL_MS = 1_000L
        private const val MAX_SAMPLE_INTERVAL_MS = 300_000L
        private const val MIN_REQUIRED_SAMPLES = 4
        private const val MAX_CYCLE_DELAY_MS = 30_000L
        private const val DEFAULT_CYCLE_DELAY_MS = 30_000L
        private const val SMOKE_CYCLE_DELAY_MS = 500L
        private const val DEFAULT_LIVENESS_RECOVERABILITY_EVERY_CYCLES = 20
        private const val SMOKE_LIVENESS_RECOVERABILITY_EVERY_CYCLES = 2
        private const val MIN_EXECUTIONS = 2
        private const val MIN_GROWTH_SAMPLES = 4
        private const val RECENT_SAMPLE_WINDOW = 4
        private const val CYCLE_TIMEOUT_MS = 15_000L
        private const val WAKE_LOCK_POLICY = "bounded_partial"
        private const val WAKE_LOCK_GRACE_MS = 15L * 60L * 1_000L
        private const val REMOTE_METRIC_MAX_ATTEMPTS = 5
        private const val REMOTE_METRIC_RETRY_DELAY_MS = 250L
        private const val REMOTE_OUTPUT_DIAGNOSTIC_CHARS = 256
        private const val KIND_QUICK = "quick"
        private const val KIND_FINAL = "final"
        private const val KIND_SMOKE = "smoke"
        private const val REQUEST_CYCLE_ENV = "AUTOJS6_R6_REQUEST_CYCLE"
        private const val REQUEST_STATE_MODULE_ENV = "AUTOJS6_R6_STATE_MODULE"
        private const val RESOURCE_PAYLOAD_PREFIX = "r6.soak.resource="
        private const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
        private const val RUN_TOKEN_FILE_PREFIX_LENGTH = 16

        private const val ARG_KIND = "autojs.nodejs.r6.soak.kind"
        private const val ARG_RUN_TOKEN = "autojs.nodejs.r6.soak.runToken"
        private const val ARG_DURATION_MS = "autojs.nodejs.r6.soak.durationMs"
        private const val ARG_DURATION_HOURS = "autojs.nodejs.r6.soak.durationHours"
        private const val ARG_SAMPLE_INTERVAL_MS = "autojs.nodejs.r6.soak.sampleIntervalMs"
        private const val ARG_CYCLE_DELAY_MS = "autojs.nodejs.r6.soak.cycleDelayMs"
        private const val ARG_LIVENESS_RECOVERABILITY_EVERY_CYCLES =
            "autojs.nodejs.r6.soak.livenessRecoverabilityEveryCycles"
        private const val ARG_MAX_RSS_DELTA_KB = "autojs.nodejs.r6.soak.maxRssDeltaKb"
        private const val ARG_MAX_JAVA_HEAP_DELTA_KB = "autojs.nodejs.r6.soak.maxJavaHeapDeltaKb"
        private const val ARG_MAX_NATIVE_HEAP_DELTA_KB = "autojs.nodejs.r6.soak.maxNativeHeapDeltaKb"
        private const val ARG_MAX_THREAD_DELTA = "autojs.nodejs.r6.soak.maxThreadDelta"
        private const val ARG_MAX_FD_DELTA = "autojs.nodejs.r6.soak.maxFdDelta"
        private const val ARG_MAX_NODE_HEAP_DELTA_KB = "autojs.nodejs.r6.soak.maxNodeHeapDeltaKb"
        private const val ARG_MAX_LIBUV_DELTA = "autojs.nodejs.r6.soak.maxLibuvHandleDelta"
        private const val ARG_MAX_BRIDGE_DELTA = "autojs.nodejs.r6.soak.maxBridgeActiveDelta"

        private const val NODE_BACKEND_PROPERTY = "autojs.nodejs.backend"
        private const val PLUGIN_RUNTIME_ENABLED_PROPERTY = "autojs.nodejs.runtime.plugin.enabled"
        private const val PLUGIN_RUNTIME_REQUIRED_PROPERTY = "autojs.nodejs.runtime.plugin.required"
        private const val PLUGIN_ENABLED_KEY = "embedded_script.runtime_plugin.enabled"
        private const val FALLBACK_SAFE_KEY = "embedded_script.runtime_plugin.fallback_safe"
        private const val DIAGNOSTICS_SOURCE_KEY = "embedded_script.runtime_plugin.diagnostics_source"
        private const val PLUGIN_SESSION_GENERATION_KEY = "embedded_script.runtime_plugin.session_generation"
        private const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        private const val PROCESS_RUNTIME_INIT_COUNT_KEY = "process_runtime.init_count"
        private const val PROCESS_RUNTIME_SEQUENCE_KEY = "process_runtime.execution_sequence"
        private const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        private const val PROCESS_RUNTIME_HEALTHY_KEY = "process_runtime.healthy"
        private const val PROCESS_RUNTIME_POISONED_KEY = "process_runtime.poisoned"
        private const val PROCESS_RUNTIME_TEARDOWN_KEY = "process_runtime.teardown_clean"
        private const val EXECUTION_TEARDOWN_KEY = "execution.teardown_clean"
        private const val BRIDGE_ACTIVE_COUNT_KEY = "embedded_script.bridge_resource.active_count"
        private const val BRIDGE_PENDING_COUNT_KEY = "embedded_script.bridge_live_dispatch_pending_count"
        private const val RUNTIME_PROCESS_RSS_KB_KEY = "runtime_process.rss_kb"
        private const val RUNTIME_PROCESS_METRICS_STATUS_KEY = "runtime_process.metrics.status"
        private const val RUNTIME_PROCESS_METRICS_SOURCE_KEY = "runtime_process.metrics.source"
        private const val RUNTIME_PROCESS_PID_KEY = "runtime_process.pid"
        private const val RUNTIME_PROCESS_PROC_STATUS_READABLE_KEY = "runtime_process.proc_status_readable"
        private const val RUNTIME_PROCESS_THREAD_COUNT_KEY = "runtime_process.thread_count"
        private const val RUNTIME_PROCESS_FD_COUNT_KEY = "runtime_process.fd_count"
        private const val RUNTIME_PROCESS_JAVA_HEAP_USED_KB_KEY = "runtime_process.java_heap_used_kb"
        private const val RUNTIME_PROCESS_JAVA_HEAP_COMMITTED_KB_KEY = "runtime_process.java_heap_committed_kb"
        private const val RUNTIME_PROCESS_JAVA_HEAP_MAX_KB_KEY = "runtime_process.java_heap_max_kb"
        private const val RUNTIME_PROCESS_NATIVE_HEAP_ALLOCATED_KB_KEY = "runtime_process.native_heap_allocated_kb"
        private const val RUNTIME_PROCESS_NATIVE_HEAP_COMMITTED_KB_KEY = "runtime_process.native_heap_committed_kb"

        private val SUPPORTED_KINDS = setOf(KIND_QUICK, KIND_FINAL, KIND_SMOKE)
        private val SHA_256_PATTERN = Regex("[0-9a-fA-F]{64}")
        private val RUN_TOKEN_PATTERN = Regex("[0-9a-f]{64}")
        private val AUTHORIZED_PLUGIN_STATES = setOf("official", "trusted", "user_granted")
        private val PROCESS_NAME_PATTERN = Regex("[A-Za-z0-9_.:-]+")
        private val MUTATED_SYSTEM_PROPERTIES = listOf(
            NODE_BACKEND_PROPERTY,
            PLUGIN_RUNTIME_ENABLED_PROPERTY,
            PLUGIN_RUNTIME_REQUIRED_PROPERTY,
        )
        private val BRIDGE_DIAGNOSTIC_KEYS = setOf(
            BRIDGE_ACTIVE_COUNT_KEY,
            BRIDGE_PENDING_COUNT_KEY,
        )
        private val REQUIRED_PAYLOAD_NUMBERS = listOf(
            "rssKb",
            "nodeHeapTotalKb",
            "nodeHeapUsedKb",
            "nodeExternalKb",
            "nodeArrayBuffersKb",
            "libuvHandleCount",
        )
        private val GROWTH_METRICS = listOf(
            GrowthMetric("rssKb", ARG_MAX_RSS_DELTA_KB, 262_144L),
            GrowthMetric("javaHeapKb", ARG_MAX_JAVA_HEAP_DELTA_KB, 131_072L),
            GrowthMetric("nativeHeapKb", ARG_MAX_NATIVE_HEAP_DELTA_KB, 131_072L),
            GrowthMetric("threadCount", ARG_MAX_THREAD_DELTA, 24L),
            GrowthMetric("fdCount", ARG_MAX_FD_DELTA, 64L),
            GrowthMetric("nodeHeapUsedKb", ARG_MAX_NODE_HEAP_DELTA_KB, 131_072L),
            GrowthMetric("libuvHandleCount", ARG_MAX_LIBUV_DELTA, 8L),
            GrowthMetric("activeBridgeResourceCount", ARG_MAX_BRIDGE_DELTA, 0L),
        )
    }
}
