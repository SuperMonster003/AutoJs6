package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeProcessWorkerReplacementPhase13DecisionInstrumentationTest {

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
    fun processWorkerReplacementKeepsSeparateFacadeUnintroducedAndProcessDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-35 process-worker replacement decision")

        val workDir = File(context.cacheDir, "node-process-worker-p13-35-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-35 process-worker work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/child_process").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }
            File(workDir, "node_modules/worker_threads").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    const replacement = profile.processWorkerReplacementProfile || {};

                    function detailOf(error) {
                      return [
                        error && error.code,
                        error && error.autojs6Code,
                        error && error.legacyCode
                      ].filter(Boolean).join("/");
                    }
                    function expectDenied(label, action) {
                      try {
                        action();
                      } catch (error) {
                        console.log("p13processworker." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("p13processworker.profile=" + [
                      replacement.status,
                      replacement.api,
                      replacement.facade,
                      replacement.preferredPrimitive,
                      replacement.workerThreads,
                      replacement.childProcess,
                      replacement.shellBridge,
                      replacement.executionMode,
                      replacement.secondExecutionSlot,
                      replacement.scopedScriptPath,
                      replacement.messageSizeBytes,
                      replacement.maxQueuedMessages,
                      replacement.cleanup,
                      replacement.packagedBehavior,
                      replacement.bridgeModules,
                      replacement.nestedWorkers,
                      replacement.rawProcessHandles,
                      Object.isFrozen(replacement)
                    ].join("|"));
                    console.log("p13processworker.boundary=" + [
                      typeof globalThis.process_worker,
                      typeof globalThis.compute_worker,
                      typeof globalThis.${'$'}processWorker,
                      profile.highRiskCapabilities.childProcess,
                      profile.featureFlags.child_process.enabled,
                      profile.featureFlags.worker_threads.enabled,
                      profile.disabledBuiltins.includes("child_process"),
                      profile.disabledBuiltins.includes("worker_threads"),
                      profile.workerThreadsProfile.workerPool
                    ].join("|"));
                    expectDenied("childProcessBare", () => require("child_process"));
                    expectDenied("childProcessNodePrefix", () => require("node:child_process"));
                    expectDenied("childProcessResolve", () => require.resolve("child_process"));
                    expectDenied("workerThreadsBare", () => require("worker_threads"));
                    console.log("p13processworker.ready=true");
                """.trimIndent(),
                sourceName = "<node-process-worker-phase13-p13-35.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                workerThreadsEnabled = false,
                childProcessEnabled = false,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-35 process-worker replacement decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13processworker.profile=decision_partial|not_introduced|not_introduced|" +
                        "worker_threads.WorkerPool|request_local_stable_preflight|denied|" +
                        "separate_scoped_command_bridge|worker_computation_reserved|closed_by_p13_06|" +
                        "workingDirectory|65536|32|worker_pool_close_or_execution_destroy|" +
                        "not_promoted|denied|false|false|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13processworker.boundary=undefined|undefined|undefined|false|false|false|true|true|partial"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13processworker.childProcessBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13processworker.childProcessNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13processworker.childProcessResolve=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13processworker.workerThreadsBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13processworker.ready=true"))
            assertEquals("false", result.nativeValues["embedded_script.request.worker_threads_enabled"])
            result.nativeValues["embedded_script.worker_threads_enabled"]?.let { assertEquals("false", it) }
            result.nativeValues["embedded_script.worker_active_count"]?.let { assertEquals("0", it) }
        } finally {
            workDir.deleteRecursively()
        }
    }
}
