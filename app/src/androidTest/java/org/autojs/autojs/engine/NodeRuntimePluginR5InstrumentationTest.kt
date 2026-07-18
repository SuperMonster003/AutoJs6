package org.autojs.autojs.engine

import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Release-device acceptance coverage for the R5 external Node.js runtime plugin lifecycle.
 *
 * Unlike optional developer smoke tests, this gate deliberately has no Assume-based escape
 * hatch: a missing, disabled, unauthorized, stale, or contract-incompatible live plugin is an
 * acceptance failure.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NodeRuntimePluginR5InstrumentationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private lateinit var originalSystemProperties: Map<String, String?>
    private var pluginEnableRestoration: Pair<String, Boolean>? = null

    @Before
    fun settleBefore() {
        originalSystemProperties = MUTATED_SYSTEM_PROPERTIES.associateWith(System::getProperty)
        System.clearProperty(PLUGIN_RUNTIME_ENABLED_PROPERTY)
        System.clearProperty(PLUGIN_RUNTIME_REQUIRED_PROPERTY)
        NodeJsRuntimePluginHost.invalidateForTest(context)
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        pluginEnableRestoration?.let { (packageName, enabled) ->
            PluginEnableStore.setEnabled(context, packageName, enabled)
        }
        pluginEnableRestoration = null
        originalSystemProperties.forEach { (name, value) ->
            if (value == null) System.clearProperty(name) else System.setProperty(name, value)
        }
        NodeJsRuntimePluginHost.invalidateForTest(context)
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun r5_01_livePluginPreflightNegotiatesContractAndReportsTrustIdentity() {
        val live = requireLivePlugin()
        val workDir = freshWorkDir("preflight")
        try {
            val result = runPlugin(
                workDir = workDir,
                label = "preflight",
                source = "\"nodejs\"; console.log(\"r5.preflight=\" + process.pid);",
            )

            assertPluginSucceeded("R5 live plugin preflight", result)
            assertEquals("true", result.nativeValues[PLUGIN_ENABLED_KEY])
            assertEquals(live.candidate.serviceInfo.packageName, result.nativeValues[PLUGIN_PACKAGE_KEY])
            assertEquals(live.candidate.serviceInfo.name, result.nativeValues[PLUGIN_SERVICE_KEY])
            assertEquals(live.candidate.signerSha256, result.nativeValues[PLUGIN_SIGNER_KEY])
            assertEquals(live.candidate.authorizationState, result.nativeValues[PLUGIN_AUTHORIZATION_KEY])
            assertEquals("plugin", result.nativeValues[PLUGIN_DIAGNOSTICS_SOURCE_KEY])
            assertEquals("true", result.nativeValues[PLUGIN_PERSISTENT_RUNTIME_KEY])
            assertEquals("1", result.nativeValues[PLUGIN_MAX_CONCURRENT_KEY])
            assertEquals("0", result.nativeValues[PLUGIN_QUEUE_CAPACITY_KEY])
            assertEquals(live.runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID), result.pid)
            assertCoreRuntimeHealthy("R5 live plugin preflight", result)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_02_candidateAndBinderCachesReusePersistentRuntimeWithFreshExecutionState() {
        requireLivePlugin()
        NodeJsRuntimePluginHost.invalidateForTest(context)
        val workDir = freshWorkDir("persistent-isolation")
        try {
            val first = runPlugin(
                workDir = workDir,
                label = "persistent-first",
                env = mapOf("AUTOJS6_R5_REQUEST_ENV" to "first-request"),
                source = """
                    "nodejs";
                    (async function main() {
                      globalThis.__autojs6R5GlobalLeak = "first";
                      process.env.AUTOJS6_R5_ENV_LEAK = "first";
                      await new Promise((resolve) => setTimeout(resolve, 10));
                      globalThis.__autojs6R5TimerLeak = "first-fired";
                      console.log("r5.first=" + [
                        globalThis.__autojs6R5GlobalLeak,
                        process.env.AUTOJS6_R5_ENV_LEAK,
                        process.env.AUTOJS6_R5_REQUEST_ENV,
                        globalThis.__autojs6R5TimerLeak
                      ].join("/"));
                    })();
                """.trimIndent(),
            )
            val second = runPlugin(
                workDir = workDir,
                label = "persistent-second",
                env = mapOf("AUTOJS6_R5_REQUEST_ENV" to "second-request"),
                source = """
                    "nodejs";
                    console.log("r5.second=" + [
                      typeof globalThis.__autojs6R5GlobalLeak,
                      typeof process.env.AUTOJS6_R5_ENV_LEAK,
                      process.env.AUTOJS6_R5_REQUEST_ENV,
                      typeof globalThis.__autojs6R5TimerLeak
                    ].join("/"));
                """.trimIndent(),
            )

            assertPluginSucceeded("R5 persistent first execution", first)
            assertPluginSucceeded("R5 persistent second execution", second)
            assertTrue(first.stdout, first.stdout.contains("r5.first=first/first/first-request/first-fired"))
            assertTrue(second.stdout, second.stdout.contains("r5.second=undefined/undefined/second-request/undefined"))
            assertEquals("first discovery unexpectedly hit the candidate cache", "false", first.nativeValues[CANDIDATE_CACHE_HIT_KEY])
            assertEquals("first execution unexpectedly reused a Binder session", "false", first.nativeValues[SESSION_REUSED_KEY])
            assertEquals("second discovery did not hit the candidate cache", "true", second.nativeValues[CANDIDATE_CACHE_HIT_KEY])
            assertEquals("second execution did not reuse the Binder session", "true", second.nativeValues[SESSION_REUSED_KEY])
            assertEquals("warm Binder reuse should not bind again", "0", second.nativeValues[BIND_ELAPSED_KEY])
            assertEquals("persistent plugin process changed", first.pid, second.pid)
            assertEquals(
                "host Binder session generation changed",
                first.nativeValues[SESSION_GENERATION_KEY],
                second.nativeValues[SESSION_GENERATION_KEY],
            )
            assertEquals(
                "process runtime generation changed",
                first.nativeValues[PROCESS_RUNTIME_GENERATION_KEY],
                second.nativeValues[PROCESS_RUNTIME_GENERATION_KEY],
            )
            assertEquals("process runtime initialized more than once", "1", first.nativeValues[PROCESS_RUNTIME_INIT_COUNT_KEY])
            assertEquals("process runtime initialized more than once", "1", second.nativeValues[PROCESS_RUNTIME_INIT_COUNT_KEY])
            val firstSequence = requireLongNativeValue("first execution sequence", first, PROCESS_RUNTIME_SEQUENCE_KEY)
            val secondSequence = requireLongNativeValue("second execution sequence", second, PROCESS_RUNTIME_SEQUENCE_KEY)
            assertEquals("persistent runtime sequence did not advance exactly once", firstSequence + 1L, secondSequence)
            assertEquals("second execution did not report process runtime reuse", "true", second.nativeValues[PROCESS_RUNTIME_REUSED_KEY])
            assertCoreRuntimeHealthy("R5 persistent first execution", first)
            assertCoreRuntimeHealthy("R5 persistent second execution", second)

            val workspaceInput = File(workDir, "workspace-input.txt").apply {
                writeText("workspace-input-v2")
            }
            val workspaceOutput = File(workDir, "workspace-output.txt")
            val workspace = runPlugin(
                workDir = workDir,
                label = "workspace-writeback-delete",
                env = mapOf(
                    R5_WORKSPACE_INPUT_ENV to workspaceInput.name,
                    R5_WORKSPACE_OUTPUT_ENV to workspaceOutput.name,
                ),
                source = """
                    "nodejs";
                    const fs = require("fs");
                    const input = process.env.$R5_WORKSPACE_INPUT_ENV;
                    const output = process.env.$R5_WORKSPACE_OUTPUT_ENV;
                    const value = fs.readFileSync(input, "utf8");
                    fs.writeFileSync(output, value + "-committed");
                    fs.unlinkSync(input);
                    console.log("r5.workspace=" + value);
                """.trimIndent(),
            )
            assertPluginSucceeded("R5 scoped workspace transport", workspace)
            assertTrue(workspace.stdout, workspace.stdout.contains("r5.workspace=workspace-input-v2"))
            assertFalse("explicit workspace input deletion was not synchronized", workspaceInput.exists())
            assertEquals("workspace output was not synchronized", "workspace-input-v2-committed", workspaceOutput.readText())
            assertEquals("workspace transport contract drift", "2", workspace.nativeValues[WORKSPACE_CONTRACT_VERSION_KEY])
            assertEquals("workspace transport was not mapped", "true", workspace.nativeValues[WORKSPACE_MAPPED_KEY])
            assertEquals("workspace output was not committed", "committed", workspace.nativeValues[WORKSPACE_STATUS_KEY])
            assertEquals("legacy host workspace scan ran", "false", workspace.nativeValues[WORKSPACE_LEGACY_SCAN_KEY])
            assertEquals("plugin input directory scan ran", "0", workspace.nativeValues[WORKSPACE_INPUT_SCAN_COUNT_KEY])
            assertEquals("plugin private output scan count drift", "1", workspace.nativeValues[WORKSPACE_OUTPUT_SCAN_COUNT_KEY])
            assertEquals("explicit input tombstone was not emitted", "1", workspace.nativeValues[WORKSPACE_DELETED_INPUT_COUNT_KEY])
            assertCoreRuntimeHealthy("R5 scoped workspace transport", workspace)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_03_embeddedAndPluginCorpusPreservesResultsCancellationAndDiagnostics() {
        val live = requireLivePlugin()
        requireEmbeddedAcceptanceRuntime("R5 embedded/plugin semantic parity")
        val workDir = freshWorkDir("paired-corpus")
        val cancellationExecutor = Executors.newSingleThreadExecutor()
        val corpus = listOf(
            CorpusCase(
                label = "stdio-success",
                source = """
                    "nodejs";
                    console.log("r5.stdout=line-1");
                    console.log("r5.stdout=line-2/42");
                    console.error("r5.stderr=warning/7");
                """.trimIndent(),
                expectedSuccess = true,
            ),
            CorpusCase(
                label = "typed-error",
                source = """
                    "nodejs";
                    const error = new TypeError("r5 parity boom");
                    error.code = "ERR_R5_PARITY";
                    throw error;
                """.trimIndent(),
                expectedSuccess = false,
            ),
        )
        try {
            corpus.forEach { case ->
                val embedded = NodeEmbeddedTestHarness.runEmbeddedScript(
                    source = case.source,
                    sourceName = File(workDir, "embedded-${case.label}.js").absolutePath,
                    workingDirectory = workDir,
                    sandboxRoot = workDir,
                )
                val plugin = runPlugin(workDir, "plugin-${case.label}", case.source)

                assertEquals("${case.label} embedded success state", case.expectedSuccess, embedded.succeeded)
                assertEquals("${case.label} plugin success state", case.expectedSuccess, plugin.succeeded)
                assertEquals("${case.label} exit code drift", embedded.exitCode, plugin.exitCode)
                assertEquals("${case.label} stdout drift", normalizeOutput(embedded.stdout), normalizeOutput(plugin.stdout))
                assertEquals("${case.label} stderr drift", normalizeOutput(embedded.stderr), normalizeOutput(plugin.stderr))
                assertEquals("${case.label} error name drift", embedded.errorName, plugin.errorName)
                assertEquals("${case.label} error message drift", embedded.errorMessage, plugin.errorMessage)
                assertEquals("${case.label} error code drift", embedded.errorCode, plugin.errorCode)
                assertCoreRuntimeHealthy("${case.label} embedded", embedded)
                assertCoreRuntimeHealthy("${case.label} plugin", plugin)
                assertSharedLifecycleAndNativeDiagnostics(case.label, embedded, plugin)
                assertEquals("plugin corpus result missed backend attribution", "true", plugin.nativeValues[PLUGIN_ENABLED_KEY])
                assertEquals("plugin corpus result missed native diagnostics attribution", "plugin", plugin.nativeValues[PLUGIN_DIAGNOSTICS_SOURCE_KEY])
            }

            val embeddedCancellation = cancelActiveEmbeddedExecution(workDir, cancellationExecutor)
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val pluginCancellation = cancelActivePluginExecution(workDir, cancellationExecutor)

            assertEquals(
                "active cancellation semantic drift\n" +
                        "embedded=${NodeEmbeddedTestHarness.describeResult("embedded cancellation", embeddedCancellation.result)}\n" +
                        "plugin=${NodeEmbeddedTestHarness.describeResult("plugin cancellation", pluginCancellation.result)}",
                embeddedCancellation.semantics,
                pluginCancellation.semantics,
            )
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, pluginCancellation.result.errorCode)
            assertEquals("plugin cancellation did not retain client attribution", "true", pluginCancellation.result.nativeValues[CLIENT_CANCELLED_KEY])
            assertEquals("plugin cancellation happened before backend dispatch", "true", pluginCancellation.result.nativeValues[BACKEND_DISPATCHED_KEY])
            assertEquals("active plugin cancellation was incorrectly fallback-safe", "false", pluginCancellation.result.nativeValues[FALLBACK_SAFE_KEY])

            val embeddedRecovery = NativeNodeEmbeddedScriptService.runScript(
                context,
                request(
                    workDir = workDir,
                    label = "embedded-cancellation-recovery",
                    source = "\"nodejs\"; console.log(\"r5.embeddedCancellationRecovery=\" + process.pid);",
                ),
            )
            NodeEmbeddedTestHarness.assertSucceeded("R5 embedded recovery after active cancellation", embeddedRecovery)
            assertTrue(
                "embedded cancellation did not restart its process: active=${embeddedCancellation.activePid} recovered=${embeddedRecovery.pid}",
                embeddedRecovery.pid > 0 && embeddedRecovery.pid != embeddedCancellation.activePid,
            )

            awaitPluginRecovery(
                packageName = live.candidate.serviceInfo.packageName,
                previousPid = pluginCancellation.activePid,
            )
            val pluginRecovery = runPlugin(
                workDir = workDir,
                label = "plugin-cancellation-recovery",
                source = "\"nodejs\"; console.log(\"r5.pluginCancellationRecovery=\" + process.pid);",
            )
            assertPluginSucceeded("R5 plugin recovery after active cancellation", pluginRecovery)
            assertTrue(
                "plugin cancellation did not restart its process: active=${pluginCancellation.activePid} recovered=${pluginRecovery.pid}",
                pluginRecovery.pid > 0 && pluginRecovery.pid != pluginCancellation.activePid,
            )
            assertCoreRuntimeHealthy("R5 embedded recovery after active cancellation", embeddedRecovery)
            assertCoreRuntimeHealthy("R5 plugin recovery after active cancellation", pluginRecovery)
            assertSharedLifecycleAndNativeDiagnostics("active-cancellation recovery", embeddedRecovery, pluginRecovery)
        } finally {
            cancellationExecutor.shutdownNow()
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_04_concurrentPluginCallsApplySingleActiveBackpressureWithoutQueueing() {
        requireLivePlugin()
        val workDir = freshWorkDir("backpressure")
        val callerCount = 4
        val executor = Executors.newFixedThreadPool(callerCount)
        val ready = CountDownLatch(callerCount)
        val start = CountDownLatch(1)
        try {
            val futures = (1..callerCount).map { index ->
                executor.submit<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult> {
                    ready.countDown()
                    check(start.await(5L, TimeUnit.SECONDS)) { "R5 backpressure start latch timed out." }
                    runPlugin(
                        workDir = workDir,
                        label = "backpressure-$index",
                        timeoutMs = 8_000L,
                        source = """
                            "nodejs";
                            (async function main() {
                              await new Promise((resolve) => setTimeout(resolve, 1500));
                              console.log("r5.backpressure.success=$index");
                            })();
                        """.trimIndent(),
                    )
                }
            }
            assertTrue("R5 backpressure callers did not become ready", ready.await(5L, TimeUnit.SECONDS))
            start.countDown()
            val results = futures.map { it.get(15L, TimeUnit.SECONDS) }
            val succeeded = results.filter { it.succeeded }
            val busy = results.filter { it.errorCode == NodeRuntimeErrorCodes.NODE_PLUGIN_BUSY }

            assertEquals("exactly one concurrent plugin execution should be admitted: ${describe(results)}", 1, succeeded.size)
            assertEquals("all non-admitted plugin executions should fail busy: ${describe(results)}", callerCount - 1, busy.size)
            busy.forEach { result ->
                assertEquals("busy result unexpectedly allowed fallback", "false", result.nativeValues[FALLBACK_SAFE_KEY])
                assertEquals("busy result did not report admission state", "busy", result.nativeValues[ADMISSION_STATUS_KEY])
                assertEquals("busy result advertised a queue", "0", result.nativeValues[ADMISSION_QUEUE_CAPACITY_KEY])
                assertTrue(
                    "busy result omitted active execution attribution: ${result.nativeValues}",
                    result.nativeValues[ADMISSION_ACTIVE_EXECUTION_KEY].orEmpty().isNotBlank(),
                )
            }
            assertPluginSucceeded("R5 admitted backpressure execution", succeeded.single())
        } finally {
            start.countDown()
            executor.shutdownNow()
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_05_requiredFailureAndOptionalFallbackAreSafeAndDoNotDoubleDispatch() {
        val live = requireLivePlugin()
        requireEmbeddedAcceptanceRuntime("R5 optional-plugin fallback")
        assertTrue(
            "pluginOptional requires a packaged embedded runtime, releaseFlavor=${BuildConfig.NODEJS_RELEASE_FLAVOR}",
            BuildConfig.NODEJS_RELEASE_FLAVOR in setOf("nodeFull", "nodeMini"),
        )
        val workDir = freshWorkDir("fallback")
        val marker = File(workDir, "dispatch-count.txt")
        rememberAndSetPluginEnabled(live.candidate.serviceInfo.packageName, false)
        try {
            System.setProperty(NODE_BACKEND_PROPERTY, "plugin")
            val required = NodeJsRuntimePluginBridge.runScriptWithEmbeddedFallback(
                context,
                request(
                    workDir = workDir,
                    label = "required-unavailable",
                    source = markerIncrementSource(),
                    env = mapOf(R5_MARKER_ENV to marker.absolutePath),
                ),
            )
            assertFalse(NodeEmbeddedTestHarness.describeResult("R5 required-plugin failure", required), required.succeeded)
            assertEquals(NodeJsRuntimePluginBridge.ERROR_PLUGIN_UNAVAILABLE, required.errorCode)
            assertEquals("required failure was not proven pre-dispatch", "false", required.nativeValues[BACKEND_DISPATCHED_KEY])
            assertEquals("pre-dispatch unavailable failure should be fallback-safe", "true", required.nativeValues[FALLBACK_SAFE_KEY])
            assertFalse("required-plugin mode executed user code", marker.exists())
            assertTrue("required-plugin mode unexpectedly used fallback", OPTIONAL_FALLBACK_USED_KEY !in required.nativeValues)

            System.setProperty(NODE_BACKEND_PROPERTY, "pluginOptional")
            val optional = NodeJsRuntimePluginBridge.runScriptWithEmbeddedFallback(
                context,
                request(
                    workDir = workDir,
                    label = "optional-unavailable",
                    source = markerIncrementSource(),
                    env = mapOf(R5_MARKER_ENV to marker.absolutePath),
                ),
            )
            NodeEmbeddedTestHarness.assertSucceeded("R5 optional-plugin fallback", optional)
            assertEquals("true", optional.nativeValues[OPTIONAL_FALLBACK_USED_KEY])
            assertEquals(NodeJsRuntimePluginBridge.ERROR_PLUGIN_UNAVAILABLE, optional.nativeValues[OPTIONAL_FALLBACK_REASON_KEY])
            assertEquals("fallback user code was not dispatched exactly once", "1", marker.readText())
            assertTrue(optional.stdout, optional.stdout.contains("r5.fallback.dispatchCount=1"))
        } finally {
            rememberAndSetPluginEnabled(live.candidate.serviceInfo.packageName, true)
            NodeJsRuntimePluginHost.invalidateForTest(context)
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_06_hostRejectsContractVersionAndMandatoryCapabilityMismatch() {
        requireLivePlugin()
        val compatible = compatibleRuntimeInfo()

        val wrongVersion = Bundle(compatible).apply {
            putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION + 1)
        }
        val versionError = captureContractFailure(wrongVersion)
        assertTrue(versionError.message.orEmpty(), versionError.message.orEmpty().contains("Unsupported"))

        val missingCapability = Bundle(compatible).apply {
            putStringArray(
                NodeJsRuntimeContract.KEY_CAPABILITIES,
                arrayOf(NodeJsRuntimeContract.CAPABILITY_SYNC_SCRIPT_EXECUTION),
            )
        }
        val capabilityError = captureContractFailure(missingCapability)
        assertTrue(capabilityError.message.orEmpty(), capabilityError.message.orEmpty().contains("missing mandatory capabilities"))
    }

    @Test
    fun r5_07_binderDeathReconnectsAtomicallyAndPreservesCrashAttribution() {
        val live = requireLivePlugin()
        val workDir = freshWorkDir("binder-death")
        val executor = Executors.newFixedThreadPool(4)
        try {
            val before = runPlugin(
                workDir = workDir,
                label = "before-binder-death",
                source = "\"nodejs\"; console.log(\"r5.beforeBinderDeath=\" + process.pid);",
            )
            assertPluginSucceeded("R5 execution before Binder death", before)
            val beforeSessionGeneration = requireLongNativeValue("pre-death host session generation", before, SESSION_GENERATION_KEY)
            val beforeDeathCount = requireLongNativeValue("pre-death Binder death count", before, BINDER_DEATH_COUNT_KEY)
            val beforeReconnectCount = requireLongNativeValue("pre-death reconnect count", before, RECONNECT_COUNT_KEY)
            assertTrue("plugin PID was not remote: ${before.pid}", before.pid > 0 && before.pid != Process.myPid())

            val crashExecutionId = "r5-plugin-crash-${UUID.randomUUID()}"
            val crashFuture = executor.submit<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult> {
                NodeJsRuntimePluginBridge.runScriptWithEmbeddedFallback(
                    context,
                    request(
                        workDir = workDir,
                        label = "binder-death-in-flight",
                        source = "\"nodejs\"; setInterval(() => {}, 1000);",
                        timeoutMs = ACTIVE_CANCELLATION_SCRIPT_TIMEOUT_MS,
                    ).copy(executionId = crashExecutionId),
                )
            }
            awaitPluginActiveAdmission(crashExecutionId)
            executeShellCommand("am force-stop ${live.candidate.serviceInfo.packageName}")
            val crashResult = crashFuture.get(ACTIVE_CANCELLATION_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            assertFalse(NodeEmbeddedTestHarness.describeResult("R5 in-flight Binder death", crashResult), crashResult.succeeded)
            assertEquals(NodeRuntimeErrorCodes.NODE_PLUGIN_EXECUTION_LOST, crashResult.errorCode)
            assertEquals("in-flight crash was not attributed after dispatch", "true", crashResult.nativeValues[BACKEND_DISPATCHED_KEY])
            assertEquals("in-flight crash was incorrectly fallback-safe", "false", crashResult.nativeValues[FALLBACK_SAFE_KEY])
            assertFalse(
                "in-flight crash unexpectedly used optional fallback",
                OPTIONAL_FALLBACK_USED_KEY in crashResult.nativeValues,
            )
            SystemClock.sleep(750L)

            val probeFutures = (1..4).map {
                executor.submit<Bundle> {
                    runBlocking { NodeJsRuntimePluginHost.probe(context) }
                }
            }
            val recoveredProbes = probeFutures.map { future -> future.get(15L, TimeUnit.SECONDS) }
            val recoveredPids = recoveredProbes.map { it.getInt(NodeJsRuntimeContract.KEY_PID) }.toSet()
            val recoveredSessionGenerations = recoveredProbes.map { it.getLong("hostSessionGeneration") }.toSet()
            assertEquals("concurrent reconnect created multiple plugin processes: $recoveredPids", 1, recoveredPids.size)
            assertEquals("concurrent reconnect created multiple host sessions: $recoveredSessionGenerations", 1, recoveredSessionGenerations.size)
            assertTrue("plugin process did not restart after force-stop: $recoveredPids", recoveredPids.single() != before.pid)
            assertTrue(
                "host Binder session generation did not advance: $recoveredSessionGenerations",
                recoveredSessionGenerations.single() > beforeSessionGeneration,
            )

            val after = runPlugin(
                workDir = workDir,
                label = "after-binder-death",
                source = "\"nodejs\"; console.log(\"r5.afterBinderDeath=\" + process.pid);",
            )
            assertPluginSucceeded("R5 execution after Binder death", after)
            assertEquals(recoveredPids.single(), after.pid)
            assertEquals(live.candidate.serviceInfo.packageName, after.nativeValues[SESSION_PACKAGE_KEY])
            assertEquals(live.candidate.serviceInfo.name, after.nativeValues[SESSION_SERVICE_KEY])
            assertTrue(
                "Binder death was not attributed: ${after.nativeValues}",
                requireLongNativeValue("post-death Binder death count", after, BINDER_DEATH_COUNT_KEY) > beforeDeathCount,
            )
            assertTrue(
                "Binder reconnect was not counted: ${after.nativeValues}",
                requireLongNativeValue("post-death reconnect count", after, RECONNECT_COUNT_KEY) > beforeReconnectCount,
            )
            assertEquals("post-reconnect call did not reuse the atomic session", "true", after.nativeValues[SESSION_REUSED_KEY])
        } finally {
            executor.shutdownNow()
            runCatching { runBlocking { NodeJsRuntimePluginHost.probe(context) } }
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_08_runtimeWarmSamplesMeetR2SloAndSeparateDiscoveryAndBindOverhead() {
        requireLivePlugin()
        val sampleCount = configuredWarmSampleCount()
        val workDir = freshWorkDir("performance")
        NodeJsRuntimePluginHost.invalidateForTest(context)
        try {
            val primerStartedAt = SystemClock.elapsedRealtime()
            val primer = runPlugin(
                workDir = workDir,
                label = "performance-primer",
                source = "\"nodejs\"; console.log(\"r5.performance.primer=1\");",
            )
            val primerWallMs = (SystemClock.elapsedRealtime() - primerStartedAt).coerceAtLeast(0L)
            assertPluginSucceeded("R5 performance primer", primer)
            val primerPid = primer.pid
            val primerRuntimeGeneration = primer.nativeValues[PROCESS_RUNTIME_GENERATION_KEY]
            val primerSessionGeneration = primer.nativeValues[SESSION_GENERATION_KEY]
            val warmWallMs = ArrayList<Long>(sampleCount)
            val warmExecutionElapsedMs = ArrayList<Long>(sampleCount)
            val discoveryElapsedMs = ArrayList<Long>(sampleCount)
            val bindElapsedMs = ArrayList<Long>(sampleCount)
            val servicePreNativeMs = ArrayList<Long>(sampleCount)
            val serviceNativeCallMs = ArrayList<Long>(sampleCount)
            val serviceWorkspaceCommitMs = ArrayList<Long>(sampleCount)
            val serviceLiveBridgeStopMs = ArrayList<Long>(sampleCount)
            val serviceDiagnosticsMs = ArrayList<Long>(sampleCount)

            repeat(sampleCount) { index ->
                val startedAt = SystemClock.elapsedRealtime()
                val result = runPlugin(
                    workDir = workDir,
                    label = "performance-warm-${index + 1}",
                    source = "\"nodejs\"; console.log(\"r5.performance.warm=${index + 1}\");",
                )
                warmWallMs += (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                warmExecutionElapsedMs += result.elapsedMs.coerceAtLeast(0L)
                assertPluginSucceeded("R5 runtime-warm sample ${index + 1}", result)
                assertEquals("warm sample changed plugin PID", primerPid, result.pid)
                assertEquals("warm sample changed process runtime generation", primerRuntimeGeneration, result.nativeValues[PROCESS_RUNTIME_GENERATION_KEY])
                assertEquals("warm sample changed host session generation", primerSessionGeneration, result.nativeValues[SESSION_GENERATION_KEY])
                assertEquals("warm discovery missed candidate cache", "true", result.nativeValues[CANDIDATE_CACHE_HIT_KEY])
                assertEquals("warm execution missed Binder session cache", "true", result.nativeValues[SESSION_REUSED_KEY])
                discoveryElapsedMs += requireLongNativeValue("warm discovery elapsed", result, DISCOVERY_ELAPSED_KEY)
                bindElapsedMs += requireLongNativeValue("warm bind elapsed", result, BIND_ELAPSED_KEY)
                servicePreNativeMs += requireLongNativeValue("service pre-native elapsed", result, SERVICE_PRE_NATIVE_KEY)
                serviceNativeCallMs += requireLongNativeValue("service native-call elapsed", result, SERVICE_NATIVE_CALL_KEY)
                serviceWorkspaceCommitMs += requireLongNativeValue("service workspace-commit elapsed", result, SERVICE_WORKSPACE_COMMIT_KEY)
                serviceLiveBridgeStopMs += requireLongNativeValue("service live-bridge-stop elapsed", result, SERVICE_LIVE_BRIDGE_STOP_KEY)
                serviceDiagnosticsMs += requireLongNativeValue("service diagnostics elapsed", result, SERVICE_DIAGNOSTICS_KEY)
            }

            val p50 = nearestRank(warmWallMs, 50)
            val p95 = nearestRank(warmWallMs, 95)
            val report = JSONObject()
                .put("schema", "autojs6-node-runtime-plugin-r5-performance-v1")
                .put("samples", sampleCount)
                .put("pluginPackage", primer.nativeValues[PLUGIN_PACKAGE_KEY])
                .put("pluginPid", primerPid)
                .put("processRuntimeGeneration", primerRuntimeGeneration)
                .put("hostSessionGeneration", primerSessionGeneration)
                .put("primerWallMs", primerWallMs)
                .put("primerDiscoveryMs", requireLongNativeValue("primer discovery elapsed", primer, DISCOVERY_ELAPSED_KEY))
                .put("primerBindMs", requireLongNativeValue("primer bind elapsed", primer, BIND_ELAPSED_KEY))
                .put("warmWallP50Ms", p50)
                .put("warmWallP95Ms", p95)
                .put("warmWallMaxMs", warmWallMs.maxOrNull())
                .put("warmExecutionP50Ms", nearestRank(warmExecutionElapsedMs, 50))
                .put("warmExecutionP95Ms", nearestRank(warmExecutionElapsedMs, 95))
                .put("servicePreNativeP50Ms", nearestRank(servicePreNativeMs, 50))
                .put("serviceNativeCallP50Ms", nearestRank(serviceNativeCallMs, 50))
                .put("serviceWorkspaceCommitP50Ms", nearestRank(serviceWorkspaceCommitMs, 50))
                .put("serviceLiveBridgeStopP50Ms", nearestRank(serviceLiveBridgeStopMs, 50))
                .put("serviceDiagnosticsP50Ms", nearestRank(serviceDiagnosticsMs, 50))
                .put("warmDiscoveryP50Ms", nearestRank(discoveryElapsedMs, 50))
                .put("warmDiscoveryP95Ms", nearestRank(discoveryElapsedMs, 95))
                .put("warmBindP50Ms", nearestRank(bindElapsedMs, 50))
                .put("warmBindP95Ms", nearestRank(bindElapsedMs, 95))
            Log.i(TAG, report.toString())
            println(report.toString())

            assertTrue("R5 runtime-warm p50 exceeded 250 ms: $report", p50 <= RUNTIME_WARM_P50_LIMIT_MS)
            assertTrue("R5 runtime-warm p95 exceeded 400 ms: $report", p95 <= RUNTIME_WARM_P95_LIMIT_MS)
            assertTrue("warm Binder session unexpectedly rebound: $report", bindElapsedMs.all { it == 0L })
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun requireLivePlugin(): LivePlugin {
        NodeJsRuntimePluginHost.invalidateForTest(context)
        val candidates = try {
            runBlocking { NodeJsRuntimePluginHost.discover(context) }
        } catch (error: Throwable) {
            throw AssertionError("R5 live plugin discovery failed.", error)
        }
        assertTrue(
            "R5 requires an installed, enabled, trusted Node.js runtime plugin; no candidate was discovered.",
            candidates.isNotEmpty(),
        )
        val candidate = candidates.first()
        assertTrue("R5 plugin service must be exported: ${candidate.component}", candidate.serviceInfo.exported)
        assertEquals(PLUGIN_PERMISSION, candidate.serviceInfo.permission)
        assertTrue("R5 plugin package version is missing: ${candidate.component}", (candidate.packageVersionCode ?: 0L) > 0L)
        assertTrue("R5 plugin package UID is missing: ${candidate.component}", candidate.packageUid > 0)
        assertTrue(
            "R5 plugin signer SHA-256 is missing or malformed: ${candidate.signerSha256}",
            candidate.signerSha256?.matches(SHA_256_PATTERN) == true,
        )
        assertTrue(
            "R5 plugin is not in an authorized trust state: ${candidate.authorizationState}",
            candidate.authorizationState in AUTHORIZED_STATES,
        )
        assertTrue(
            "R5 plugin candidate is disabled in host state: ${candidate.component}",
            PluginEnableStore.isEnabled(context, candidate.serviceInfo.packageName, true),
        )

        val runtimeInfo = try {
            runBlocking { NodeJsRuntimePluginHost.probe(context) }
        } catch (error: Throwable) {
            throw AssertionError("R5 live plugin contract probe failed for ${candidate.component}.", error)
        }
        NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
        assertEquals(NodeJsRuntimeContract.CONTRACT_VERSION, runtimeInfo.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION))
        assertTrue("R5 plugin runtime slot is blank", runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT).orEmpty().isNotBlank())
        assertTrue("R5 plugin Node version is blank", runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION).orEmpty().isNotBlank())
        val capabilities = runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty().toSet()
        REQUIRED_LIVE_CAPABILITIES.forEach { capability ->
            assertTrue("R5 live plugin omitted capability $capability: $capabilities", capability in capabilities)
        }
        assertTrue("R5 plugin persistent runtime is not ready: $runtimeInfo", runtimeInfo.getBoolean("runtimeReady", false))
        assertTrue("R5 plugin does not advertise persistent process runtime", runtimeInfo.getBoolean("persistentProcessRuntime", false))
        assertTrue("R5 plugin runtime is not in a dedicated process", runtimeInfo.getBoolean("dedicatedRuntimeProcess", false))
        assertEquals("R5 plugin concurrent execution contract drift", 1, runtimeInfo.getInt("maxConcurrentExecutions", -1))
        assertEquals("R5 plugin queue contract drift", 0, runtimeInfo.getInt("queueCapacity", -1))
        val pluginPid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1)
        assertTrue("R5 plugin probe did not report a remote PID: $pluginPid", pluginPid > 0 && pluginPid != Process.myPid())
        val processAbi = runtimeInfo.getString("processAbi").orEmpty()
        val supportedAbis = runtimeInfo.getStringArray("supportedAbis").orEmpty().toSet()
        assertTrue("R5 plugin process ABI is blank", processAbi.isNotBlank())
        assertTrue("R5 plugin process ABI $processAbi is not advertised: $supportedAbis", processAbi in supportedAbis)
        assertTrue("R5 plugin process ABI $processAbi is not supported by device: ${Build.SUPPORTED_ABIS.toList()}", processAbi in Build.SUPPORTED_ABIS)
        assertTrue("R5 preflight probe did not reuse candidate cache", runtimeInfo.getBoolean("hostCandidateCacheHit", false))
        assertTrue("R5 preflight discovery timing is missing", runtimeInfo.getLong("hostDiscoveryElapsedMs", -1L) >= 0L)
        assertTrue("R5 preflight bind timing is missing", runtimeInfo.getLong("hostBindElapsedMs", -1L) >= 0L)
        assertTrue("R5 preflight session generation is missing", runtimeInfo.getLong("hostSessionGeneration", 0L) > 0L)
        return LivePlugin(candidate, runtimeInfo)
    }

    private fun requireEmbeddedAcceptanceRuntime(label: String) {
        assertTrue(
            "$label requires -Pautojs.nodejs.embedded.script.enabled=true.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
        assertTrue(
            "$label requires the persistent embedded runtime on Android 10 or newer.",
            NodeEmbeddedProcessSessionManager.isEnabled(),
        )
    }

    private fun runPlugin(
        workDir: File,
        label: String,
        source: String,
        timeoutMs: Long = 10_000L,
        env: Map<String, String> = emptyMap(),
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult = NodeJsRuntimePluginBridge.runScript(
        context,
        request(workDir, label, source, timeoutMs, env),
    )

    private fun cancelActiveEmbeddedExecution(
        workDir: File,
        executor: ExecutorService,
    ): ActiveCancellationResult {
        val executionId = "r5-embedded-active-cancel-${UUID.randomUUID()}"
        val activeMarker = File(workDir, "embedded-active-cancel.marker")
        val activeRequest = request(
            workDir = workDir,
            label = "embedded-active-cancel",
            timeoutMs = ACTIVE_CANCELLATION_SCRIPT_TIMEOUT_MS,
            env = mapOf(R5_ACTIVE_MARKER_ENV to activeMarker.absolutePath),
            source = """
                "nodejs";
                const fs = require("fs");
                fs.writeFileSync(process.env.$R5_ACTIVE_MARKER_ENV, "active:" + process.pid);
                setInterval(() => {}, 1000);
            """.trimIndent(),
        ).copy(executionId = executionId)
        val future = executor.submit<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult> {
            NativeNodeEmbeddedScriptService.runScript(context, activeRequest)
        }
        val activePid = waitForActivePidMarker(activeMarker, "embedded active cancellation")
        val acknowledged = NativeNodeEmbeddedScriptService.cancelRunningScript(context, executionId)
        val result = future.get(ACTIVE_CANCELLATION_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return activeCancellationResult(
            label = "embedded",
            result = result,
            activePid = activePid,
            backendWasActive = true,
            cancellationAcknowledged = acknowledged,
        )
    }

    private fun cancelActivePluginExecution(
        workDir: File,
        executor: ExecutorService,
    ): ActiveCancellationResult {
        val executionId = "r5-plugin-active-cancel-${UUID.randomUUID()}"
        val activePid = runBlocking { NodeJsRuntimePluginHost.probe(context) }
            .getInt(NodeJsRuntimeContract.KEY_PID, -1)
        assertTrue("plugin active-cancellation preflight did not report a remote PID: $activePid", activePid > 0 && activePid != Process.myPid())
        val activeRequest = request(
            workDir = workDir,
            label = "plugin-active-cancel",
            timeoutMs = ACTIVE_CANCELLATION_SCRIPT_TIMEOUT_MS,
            source = """
                "nodejs";
                setInterval(() => {}, 1000);
            """.trimIndent(),
        ).copy(executionId = executionId)
        val future = executor.submit<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult> {
            NodeJsRuntimePluginBridge.runScript(context, activeRequest)
        }
        awaitPluginActiveAdmission(executionId)
        val acknowledged = NodeJsRuntimePluginBridge.cancelScript(context, executionId)
        val result = future.get(ACTIVE_CANCELLATION_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return activeCancellationResult(
            label = "plugin",
            result = result,
            activePid = activePid,
            backendWasActive = true,
            cancellationAcknowledged = acknowledged,
        )
    }

    private fun activeCancellationResult(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        activePid: Int,
        backendWasActive: Boolean,
        cancellationAcknowledged: Boolean,
    ): ActiveCancellationResult {
        assertTrue("$label active cancellation did not observe backend admission", backendWasActive)
        assertTrue("$label active cancellation was not acknowledged", cancellationAcknowledged)
        assertFalse(NodeEmbeddedTestHarness.describeResult("$label active cancellation", result), result.succeeded)
        assertFalse("$label active cancellation was reported as a timeout", result.timedOut)
        return ActiveCancellationResult(
            semantics = ActiveCancellationSemantics(
                backendWasActive = backendWasActive,
                cancellationAcknowledged = cancellationAcknowledged,
                executionSucceeded = result.succeeded,
                executionTimedOut = result.timedOut,
                errorCode = result.errorCode,
            ),
            result = result,
            activePid = activePid,
        )
    }

    private fun awaitPluginActiveAdmission(executionId: String) {
        val deadline = SystemClock.elapsedRealtime() + ACTIVE_ADMISSION_WAIT_TIMEOUT_MS
        var lastInfo: Bundle? = null
        var lastFailure: Throwable? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val info = try {
                runBlocking { NodeJsRuntimePluginHost.probe(context) }.also {
                    lastInfo = it
                    lastFailure = null
                }
            } catch (error: Throwable) {
                lastFailure = error
                null
            }
            if (info?.getString(NodeJsRuntimeContract.KEY_ACTIVE_EXECUTION_ID) == executionId) {
                return
            }
            SystemClock.sleep(ACTIVE_ADMISSION_POLL_MS)
        }
        throw AssertionError(
            "Plugin execution $executionId never became observably active before cancellation. " +
                    "lastInfo=$lastInfo",
            lastFailure,
        )
    }

    private fun waitForActivePidMarker(marker: File, label: String): Int {
        val deadline = SystemClock.elapsedRealtime() + ACTIVE_ADMISSION_WAIT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val payload = runCatching { marker.takeIf(File::isFile)?.readText() }.getOrNull().orEmpty()
            val pid = payload.substringAfter("active:", "").toIntOrNull()
            if (pid != null && pid > 0) {
                return pid
            }
            SystemClock.sleep(ACTIVE_ADMISSION_POLL_MS)
        }
        throw AssertionError("$label did not write a valid active PID marker: ${marker.absolutePath}")
    }

    private fun awaitPluginRecovery(packageName: String, previousPid: Int): Bundle {
        val deadline = SystemClock.elapsedRealtime() + PLUGIN_RECOVERY_WAIT_TIMEOUT_MS
        var lastFailure: Throwable? = null
        var lastInfo: Bundle? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                val runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(context) }
                NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
                lastInfo = runtimeInfo
                val recoveredPid = runtimeInfo.getInt(NodeJsRuntimeContract.KEY_PID, -1)
                if (recoveredPid > 0 && recoveredPid != previousPid) {
                    return runtimeInfo
                }
            } catch (error: Throwable) {
                lastFailure = error
            }
            SystemClock.sleep(PLUGIN_RECOVERY_POLL_MS)
        }
        throw AssertionError(
            "Plugin $packageName did not recover with a new process after active cancellation; " +
                    "previousPid=$previousPid lastInfo=$lastInfo",
            lastFailure,
        )
    }

    private fun request(
        workDir: File,
        label: String,
        source: String,
        timeoutMs: Long = 10_000L,
        env: Map<String, String> = emptyMap(),
    ) = NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
        source = source,
        sourceName = File(workDir, "$label.js").absolutePath,
        workingDirectory = workDir.absolutePath,
        sandboxRoot = workDir.absolutePath,
        executionId = "r5-$label-${UUID.randomUUID()}",
        timeoutMs = timeoutMs,
        env = env,
    )

    private fun assertPluginSucceeded(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        NodeEmbeddedTestHarness.assertSucceeded(label, result)
        assertEquals("$label did not execute in plugin backend", "true", result.nativeValues[PLUGIN_ENABLED_KEY])
        assertEquals("$label unexpectedly became fallback-safe after dispatch", "false", result.nativeValues[FALLBACK_SAFE_KEY])
    }

    private fun assertCoreRuntimeHealthy(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertTrue("$label omitted process runtime generation: ${result.nativeValues}", result.nativeValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull() != null)
        assertEquals("$label process runtime is unhealthy", "true", result.nativeValues[PROCESS_RUNTIME_HEALTHY_KEY])
        assertEquals("$label process runtime teardown was not clean", "true", result.nativeValues[PROCESS_RUNTIME_TEARDOWN_KEY])
        assertEquals("$label execution teardown was not clean", "true", result.nativeValues[EXECUTION_TEARDOWN_KEY])
    }

    private fun assertSharedLifecycleAndNativeDiagnostics(
        label: String,
        embedded: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        plugin: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        (SHARED_LIFECYCLE_DIAGNOSTIC_KEYS + SHARED_NATIVE_DIAGNOSTIC_KEYS).forEach { key ->
            val embeddedValue = embedded.nativeValues[key]
            val pluginValue = plugin.nativeValues[key]
            assertTrue(
                "$label embedded result omitted shared diagnostic $key: ${embedded.nativeValues}",
                !embeddedValue.isNullOrBlank(),
            )
            assertTrue(
                "$label plugin result omitted shared diagnostic $key: ${plugin.nativeValues}",
                !pluginValue.isNullOrBlank(),
            )
            assertEquals("$label shared diagnostic semantic drift for $key", embeddedValue, pluginValue)
        }
    }

    private fun requireLongNativeValue(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        key: String,
    ): Long {
        val value = result.nativeValues[key]?.toLongOrNull()
        assertNotNull("$label is missing ($key): ${result.nativeValues}", value)
        return value!!
    }

    private fun compatibleRuntimeInfo() = Bundle().apply {
        putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION)
        putString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT, "node_24_5")
        putString(NodeJsRuntimeContract.KEY_NODE_VERSION, "24.5.0")
        putStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES, REQUIRED_HOST_CAPABILITIES.toTypedArray())
    }

    private fun captureContractFailure(runtimeInfo: Bundle): NodeRuntimePluginContractException {
        val failure = try {
            NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)
            null
        } catch (error: NodeRuntimePluginContractException) {
            error
        }
        assertNotNull("Host accepted an incompatible Node.js runtime plugin contract: $runtimeInfo", failure)
        return failure!!
    }

    private fun rememberAndSetPluginEnabled(packageName: String, enabled: Boolean) {
        if (pluginEnableRestoration == null) {
            pluginEnableRestoration = packageName to PluginEnableStore.isEnabled(context, packageName, true)
        }
        PluginEnableStore.setEnabled(context, packageName, enabled)
    }

    private fun markerIncrementSource() = """
        "nodejs";
        const fs = require("fs");
        const marker = process.env.$R5_MARKER_ENV;
        const previous = fs.existsSync(marker) ? Number(fs.readFileSync(marker, "utf8")) : 0;
        const next = previous + 1;
        fs.writeFileSync(marker, String(next));
        console.log("r5.fallback.dispatchCount=" + next);
    """.trimIndent()

    private fun executeShellCommand(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return descriptor.use { pfd ->
            FileInputStream(pfd.fileDescriptor).bufferedReader().use { reader -> reader.readText() }
        }
    }

    private fun configuredWarmSampleCount(): Int {
        val configured = InstrumentationRegistry.getArguments()
            .getString(ARG_WARM_SAMPLES)
            ?.toIntOrNull()
            ?: DEFAULT_WARM_SAMPLES
        return configured.coerceAtLeast(DEFAULT_WARM_SAMPLES).coerceAtMost(MAX_WARM_SAMPLES)
    }

    private fun nearestRank(values: List<Long>, percentile: Int): Long {
        check(values.isNotEmpty()) { "Cannot compute a percentile for an empty sample." }
        val sorted = values.sorted()
        val rank = ((sorted.size * percentile + 99) / 100).coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun freshWorkDir(label: String) = File(
        context.cacheDir,
        "node-runtime-plugin-r5-$label-${System.nanoTime()}",
    ).apply {
        deleteRecursively()
        check(mkdirs()) { "Failed to create R5 working directory: $absolutePath" }
    }

    private fun normalizeOutput(value: String) = value.replace("\r\n", "\n").trimEnd()

    private fun describe(results: List<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult>) =
        results.joinToString(prefix = "[", postfix = "]") { result ->
            "{succeeded=${result.succeeded},errorCode=${result.errorCode},native=${result.nativeValues}}"
        }

    private data class LivePlugin(
        val candidate: NodeJsRuntimePluginHost.Candidate,
        val runtimeInfo: Bundle,
    )

    private data class CorpusCase(
        val label: String,
        val source: String,
        val expectedSuccess: Boolean,
    )

    private data class ActiveCancellationSemantics(
        val backendWasActive: Boolean,
        val cancellationAcknowledged: Boolean,
        val executionSucceeded: Boolean,
        val executionTimedOut: Boolean,
        val errorCode: String?,
    )

    private data class ActiveCancellationResult(
        val semantics: ActiveCancellationSemantics,
        val result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        val activePid: Int,
    )

    companion object {
        private const val TAG = "NodeRuntimePluginR5"
        private const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
        private const val NODE_BACKEND_PROPERTY = "autojs.nodejs.backend"
        private const val PLUGIN_RUNTIME_ENABLED_PROPERTY = "autojs.nodejs.runtime.plugin.enabled"
        private const val PLUGIN_RUNTIME_REQUIRED_PROPERTY = "autojs.nodejs.runtime.plugin.required"
        private const val ARG_WARM_SAMPLES = "autojs.nodejs.r5.pluginWarmSamples"
        private const val DEFAULT_WARM_SAMPLES = 30
        private const val MAX_WARM_SAMPLES = 200
        private const val RUNTIME_WARM_P50_LIMIT_MS = 250L
        private const val RUNTIME_WARM_P95_LIMIT_MS = 400L
        private const val R5_MARKER_ENV = "AUTOJS6_R5_DISPATCH_MARKER"
        private const val R5_ACTIVE_MARKER_ENV = "AUTOJS6_R5_ACTIVE_MARKER"
        private const val R5_WORKSPACE_INPUT_ENV = "AUTOJS6_R5_WORKSPACE_INPUT"
        private const val R5_WORKSPACE_OUTPUT_ENV = "AUTOJS6_R5_WORKSPACE_OUTPUT"
        private const val ACTIVE_CANCELLATION_SCRIPT_TIMEOUT_MS = 60_000L
        private const val ACTIVE_CANCELLATION_RESULT_TIMEOUT_MS = 20_000L
        private const val ACTIVE_ADMISSION_WAIT_TIMEOUT_MS = 10_000L
        private const val ACTIVE_ADMISSION_POLL_MS = 25L
        private const val PLUGIN_RECOVERY_WAIT_TIMEOUT_MS = 15_000L
        private const val PLUGIN_RECOVERY_POLL_MS = 100L

        private const val PLUGIN_ENABLED_KEY = "embedded_script.runtime_plugin.enabled"
        private const val PLUGIN_PACKAGE_KEY = "embedded_script.runtime_plugin.package_name"
        private const val PLUGIN_SERVICE_KEY = "embedded_script.runtime_plugin.service_name"
        private const val PLUGIN_SIGNER_KEY = "embedded_script.runtime_plugin.signer_sha256"
        private const val PLUGIN_AUTHORIZATION_KEY = "embedded_script.runtime_plugin.authorization_state"
        private const val PLUGIN_DIAGNOSTICS_SOURCE_KEY = "embedded_script.runtime_plugin.diagnostics_source"
        private const val PLUGIN_PERSISTENT_RUNTIME_KEY = "embedded_script.runtime_plugin.persistent_process_runtime"
        private const val PLUGIN_MAX_CONCURRENT_KEY = "embedded_script.runtime_plugin.max_concurrent_executions"
        private const val PLUGIN_QUEUE_CAPACITY_KEY = "embedded_script.runtime_plugin.queue_capacity"
        private const val CANDIDATE_CACHE_HIT_KEY = "embedded_script.runtime_plugin.candidate_cache_hit"
        private const val DISCOVERY_ELAPSED_KEY = "embedded_script.runtime_plugin.discovery_elapsed_ms"
        private const val BIND_ELAPSED_KEY = "embedded_script.runtime_plugin.bind_elapsed_ms"
        private const val SESSION_REUSED_KEY = "embedded_script.runtime_plugin.session_reused"
        private const val SESSION_GENERATION_KEY = "embedded_script.runtime_plugin.session_generation"
        private const val BINDER_DEATH_COUNT_KEY = "embedded_script.runtime_plugin.binder_death_count"
        private const val RECONNECT_COUNT_KEY = "embedded_script.runtime_plugin.reconnect_count"
        private const val SESSION_PACKAGE_KEY = "embedded_script.runtime_plugin.session_package_name"
        private const val SESSION_SERVICE_KEY = "embedded_script.runtime_plugin.session_service_name"
        private const val FALLBACK_SAFE_KEY = "embedded_script.runtime_plugin.fallback_safe"
        private const val BACKEND_DISPATCHED_KEY = "embedded_script.runtime_plugin.backend_dispatched"
        private const val CLIENT_CANCELLED_KEY = "embedded_script.client.cancelled"
        private const val OPTIONAL_FALLBACK_USED_KEY = "embedded_script.runtime_plugin.optional_fallback_used"
        private const val OPTIONAL_FALLBACK_REASON_KEY = "embedded_script.runtime_plugin.optional_fallback_reason"
        private const val ADMISSION_STATUS_KEY = "embedded_script.runtime_plugin.admission.status"
        private const val ADMISSION_QUEUE_CAPACITY_KEY = "embedded_script.runtime_plugin.admission.queue_capacity"
        private const val ADMISSION_ACTIVE_EXECUTION_KEY = "embedded_script.runtime_plugin.admission.active_execution_id"
        private const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        private const val PROCESS_RUNTIME_INIT_COUNT_KEY = "process_runtime.init_count"
        private const val PROCESS_RUNTIME_SEQUENCE_KEY = "process_runtime.execution_sequence"
        private const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        private const val PROCESS_RUNTIME_HEALTHY_KEY = "process_runtime.healthy"
        private const val PROCESS_RUNTIME_TEARDOWN_KEY = "process_runtime.teardown_clean"
        private const val EXECUTION_TEARDOWN_KEY = "execution.teardown_clean"
        private const val WORKSPACE_CONTRACT_VERSION_KEY =
            "embedded_script.runtime_plugin.workspace.contract_version"
        private const val WORKSPACE_MAPPED_KEY = "embedded_script.runtime_plugin.workspace.mapped"
        private const val WORKSPACE_STATUS_KEY = "embedded_script.runtime_plugin.workspace.status"
        private const val WORKSPACE_LEGACY_SCAN_KEY =
            "embedded_script.runtime_plugin.workspace.legacy_directory_scan_ran"
        private const val WORKSPACE_INPUT_SCAN_COUNT_KEY =
            "embedded_script.runtime_plugin.workspace.input_directory_scan_count"
        private const val WORKSPACE_OUTPUT_SCAN_COUNT_KEY =
            "embedded_script.runtime_plugin.workspace.output_private_workspace_scan_count"
        private const val WORKSPACE_DELETED_INPUT_COUNT_KEY =
            "embedded_script.runtime_plugin.workspace.deleted_input_files"
        private const val SERVICE_PRE_NATIVE_KEY =
            "embedded_script.runtime_plugin.timing.pre_native.ms"
        private const val SERVICE_NATIVE_CALL_KEY =
            "embedded_script.runtime_plugin.timing.native_call.ms"
        private const val SERVICE_WORKSPACE_COMMIT_KEY =
            "embedded_script.runtime_plugin.timing.workspace_commit.ms"
        private const val SERVICE_LIVE_BRIDGE_STOP_KEY =
            "embedded_script.runtime_plugin.timing.live_bridge_stop.ms"
        private const val SERVICE_DIAGNOSTICS_KEY =
            "embedded_script.runtime_plugin.timing.diagnostics.ms"

        private val MUTATED_SYSTEM_PROPERTIES = listOf(
            NODE_BACKEND_PROPERTY,
            PLUGIN_RUNTIME_ENABLED_PROPERTY,
            PLUGIN_RUNTIME_REQUIRED_PROPERTY,
        )
        private val SHA_256_PATTERN = Regex("[0-9a-fA-F]{64}")
        private val AUTHORIZED_STATES = setOf("official", "trusted", "user_granted")
        private val REQUIRED_HOST_CAPABILITIES = setOf(
            NodeJsRuntimeContract.CAPABILITY_SYNC_SCRIPT_EXECUTION,
            NodeJsRuntimeContract.CAPABILITY_BUNDLE_TRANSPORT,
            NodeJsRuntimeContract.CAPABILITY_NATIVE_EMBEDDED_RUNTIME,
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER,
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE,
            NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER,
            NodeJsRuntimeContract.CAPABILITY_SCOPED_WORKSPACE_ARCHIVE_TRANSPORT,
        )
        private val REQUIRED_LIVE_CAPABILITIES = REQUIRED_HOST_CAPABILITIES + setOf(
            "persistentProcessRuntime",
            "singleActiveBackpressure",
            "processRestartCancellation",
        )
        private val SHARED_LIFECYCLE_DIAGNOSTIC_KEYS = listOf(
            "embedded_script.runtime_state",
            "embedded_script.lifecycle_transitions",
        )
        private val SHARED_NATIVE_DIAGNOSTIC_KEYS = listOf(
            "embedded_script.phase_timing.schema",
            "embedded_script.phase_timing.status",
            "embedded_script.phase_timing.source",
            "embedded_script.status",
            "environment.free.status",
            "isolate.dispose.status",
            "uv.loop.close.status",
            "teardown.status",
            "process_runtime.state",
            "process_runtime.persistent_enabled",
            "process_runtime.healthy",
            "process_runtime.poisoned",
            "process_runtime.teardown_clean",
            "execution.teardown_clean",
        )
    }
}
