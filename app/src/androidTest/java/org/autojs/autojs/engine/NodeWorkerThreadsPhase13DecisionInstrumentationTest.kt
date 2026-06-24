package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWorkerThreadsPhase13DecisionInstrumentationTest {

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
    fun workerThreadsProfileKeepsDefaultDeniedAndExperimentalPreflightExplicit() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-34 worker_threads promotion decision")

        val workDir = File(context.cacheDir, "node-worker-p13-34-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-34 worker work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/worker_threads").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val denied = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    const worker = profile.workerThreadsProfile || {};
                    const policy = worker.policy || {};
                    const workerProfile = policy.workerProfile || {};
                    const workerFs = workerProfile.fs || {};
                    const workerPool = policy.workerPool || {};
                    const resourceLimits = policy.resourceLimits || {};

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
                        console.log("p13worker." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("p13worker.default=" + [
                      profile.workerThreads,
                      profile.highRiskCapabilities.workerThreads,
                      profile.experimentalFlags.workerThreads,
                      profile.featureFlags.worker_threads.enabled,
                      profile.disabledBuiltins.includes("worker_threads"),
                      worker.status,
                      worker.defaultEnabled,
                      worker.requestLocalOptIn,
                      worker.nativeAvailability,
                      worker.messageChannel,
                      worker.transferList,
                      worker.workerPool,
                      worker.bridgeModules,
                      worker.nestedWorkers,
                      worker.rawNativeHandles,
                      worker.packagedBehavior
                    ].join("|"));
                    console.log("p13worker.policy=" + [
                      policy.maxWorkers,
                      policy.startupTimeoutMs,
                      policy.maxMessageBytes,
                      policy.maxQueuedMessages,
                      Array.isArray(policy.allowedBuiltins) && policy.allowedBuiltins.includes("assert"),
                      Array.isArray(policy.allowedBuiltins) && policy.allowedBuiltins.includes("fs"),
                      workerFs.read,
                      workerFs.write,
                      workerProfile.network,
                      workerProfile.autoJsBridge,
                      workerProfile.shell,
                      workerProfile.javaInterop,
                      workerProfile.inspector,
                      workerPool.maxWorkers,
                      workerPool.queueLimit,
                      resourceLimits.maxOldGenerationSizeMb
                    ].join("|"));
                    console.log("p13worker.frozen=" + [
                      Object.isFrozen(worker),
                      Object.isFrozen(policy),
                      Object.isFrozen(workerProfile),
                      Object.isFrozen(workerFs),
                      Object.isFrozen(workerPool),
                      Object.isFrozen(resourceLimits)
                    ].join("|"));
                    expectDenied("requireBare", () => require("worker_threads"));
                    expectDenied("requireNodePrefix", () => require("node:worker_threads"));
                    expectDenied("resolveBare", () => require.resolve("worker_threads"));
                    console.log("p13worker.ready=true");
                """.trimIndent(),
                sourceName = "<node-worker-phase13-default-p13-34.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                workerThreadsExperimentalEnabled = false,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-34 worker_threads default decision", denied)
            assertTrue(
                denied.stdout,
                denied.stdout.contains(
                    "p13worker.default=false|false|false|false|true|disabled_by_default|false|false|" +
                        "preflight_required|partial|partial|partial|denied|false|false|native_available_only",
                ),
            )
            assertTrue(
                denied.stdout,
                denied.stdout.contains(
                    "p13worker.policy=1|5000|65536|32|true|true|workingDirectory|workingDirectory|" +
                        "denied|denied|denied|denied|denied|1|8|64",
                ),
            )
            assertTrue(denied.stdout, denied.stdout.contains("p13worker.frozen=true|true|true|true|true|true"))
            assertTrue(
                denied.stdout,
                denied.stdout.contains("p13worker.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                denied.stdout,
                denied.stdout.contains("p13worker.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                denied.stdout,
                denied.stdout.contains("p13worker.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(denied.stdout, denied.stdout.contains("p13worker.ready=true"))
            assertEquals("false", denied.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
            assertEquals("false", denied.nativeValues["embedded_script.worker_threads_enabled"])
            assertEquals("0", denied.nativeValues["embedded_script.worker_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }
}
