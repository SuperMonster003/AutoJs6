package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWorkerThreadsBootstrapInstrumentationTest {

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
    fun defaultRequestDeniesWorkerThreadsBeforeShadowing() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads bootstrap default denial")

        val workDir = File(context.cacheDir, "node-worker-bootstrap-denial-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker bootstrap denial dir: $absolutePath" }
        }
        try {
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
                        console.log("worker.bootstrap." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("worker.bootstrap.profile=" + [
                      profile.workerThreads,
                      profile.highRiskCapabilities.workerThreads,
                      profile.experimentalFlags.workerThreads,
                      profile.featureFlags.worker_threads.enabled,
                      profile.disabledBuiltins.includes("worker_threads")
                    ].join("|"));
                    expectDenied("requireBare", () => require("worker_threads"));
                    expectDenied("requireNodePrefix", () => require("node:worker_threads"));
                    expectDenied("resolveBare", () => require.resolve("worker_threads"));
                    console.log("worker.bootstrap.ready=true");
                """.trimIndent(),
                sourceName = "<node-worker-bootstrap-default-denial.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                workerThreadsExperimentalEnabled = false,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker_threads bootstrap default denial", result)
            assertTrue(result.stdout, result.stdout.contains("worker.bootstrap.profile=false|false|false|false|true"))
            assertTrue(result.stdout, result.stdout.contains("worker.bootstrap.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("worker.bootstrap.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("worker.bootstrap.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("worker.bootstrap.ready=true"))
            assertEquals("false", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
            assertEquals("false", result.nativeValues["embedded_script.worker_threads_enabled"])
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun experimentalRequestReportsNativeAvailabilityOrStableDisabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads bootstrap preflight")

        NodeWorkerThreadsTestSupport.probeNativeWorkerThreads()
    }
}
