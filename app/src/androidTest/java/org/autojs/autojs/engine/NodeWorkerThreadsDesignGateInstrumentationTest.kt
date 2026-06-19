package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWorkerThreadsDesignGateInstrumentationTest {

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
    fun workerThreadsRemainDesignGatedAndDisabledByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads design gate")
        assumeTrue(
            "Default worker_threads design gate runs only when the build-level experimental flag is disabled.",
            !BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED,
        )
        assertFalse(BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED)
        assertTrue(
            NodeRuntimeCapabilities.experimentalFlags[NodeRuntimeCapabilities.WORKER_THREADS_EXPERIMENTAL_PROPERTY] == false,
        )

        val workDir = File(context.cacheDir, "node-worker-design-gate-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker design gate work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/worker_threads").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val source = """
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
                    const detail = detailOf(error);
                    if (
                      error.code !== "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" ||
                      error.autojs6Code !== "ERR_AUTOJS6_BUILTIN_DISABLED"
                    ) {
                      throw new Error(label + " expected builtin disabled but got " + detail);
                    }
                    console.log("worker." + label + "=" + detail);
                    return;
                  }
                  throw new Error(label + " was unexpectedly allowed");
                }

                console.log("worker.profile=" + [
                  profile.workerThreads,
                  profile.highRiskCapabilities.workerThreads,
                  profile.experimentalFlags.workerThreads,
                  profile.disabledBuiltins.includes("worker_threads")
                ].join("/"));
                console.log("worker.profileFrozen=" + Object.isFrozen(profile.experimentalFlags));

                expectDenied("requireBare", () => require("worker_threads"));
                expectDenied("requireNodePrefix", () => require("node:worker_threads"));
                expectDenied("resolveBare", () => require.resolve("worker_threads"));

                console.log("worker.ready=true");
            """.trimIndent()

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = source,
                sourceName = "<node-worker-threads-design-gate.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker_threads design gate", result)
            assertTrue(result.stdout, result.stdout.contains("worker.profile=false/false/false/true"))
            assertTrue(result.stdout, result.stdout.contains("worker.profileFrozen=true"))
            assertTrue(result.stdout, result.stdout.contains("worker.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("worker.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("worker.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"))
            assertTrue(result.stdout, result.stdout.contains("worker.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
