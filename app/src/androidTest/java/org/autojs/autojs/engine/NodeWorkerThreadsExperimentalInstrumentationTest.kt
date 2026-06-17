package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWorkerThreadsExperimentalInstrumentationTest {

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
    fun workerThreadsRemainDisabledByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads default gate")
        assertFalse(BuildConfig.NODEJS_WORKER_THREADS_EXPERIMENTAL_ENABLED)

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                function codeOf(error) {
                  return String(error && (error.autojs6Code || error.code || error.name || ""));
                }
                try {
                  require("worker_threads");
                } catch (error) {
                  console.log("worker.default=" + codeOf(error));
                }
            """.trimIndent(),
            sourceName = "<node-worker-default-disabled.js>",
            workerThreadsExperimentalEnabled = false,
        )

        NodeEmbeddedTestHarness.assertSucceeded("worker_threads default gate", result)
        assertTrue(result.stdout, result.stdout.contains("worker.default=${NodeRuntimeErrorCodes.BUILTIN_DISABLED}"))
        assertEquals("false", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
        assertEquals("false", result.nativeValues["embedded_script.worker_threads_enabled"])
    }

    @Test
    fun experimentalWorkerThreadsRunPureJsWorkersOnly() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads experimental")

        val workDir = File(context.cacheDir, "node-worker-experimental-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker experimental work dir: $absolutePath" }
        }
        try {
            File(workDir, "compute.js").writeText(
                """
                    const { parentPort, workerData, isMainThread } = require("worker_threads");
                    const assert = require("assert");
                    assert.strictEqual(isMainThread, false);
                    parentPort.postMessage({
                      kind: "compute",
                      value: workerData.left + workerData.right,
                      data: workerData.label
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "restricted.js").writeText(
                """
                    const { parentPort } = require("worker_threads");
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.name || ""));
                    }
                    function denied(label, action) {
                      try {
                        action();
                        parentPort.postMessage({ label, code: "allowed" });
                      } catch (error) {
                        parentPort.postMessage({ label, code: codeOf(error) });
                      }
                    }
                    denied("toast", () => require("toast"));
                    denied("accessibility", () => require("accessibility"));
                    denied("fs", () => require("fs"));
                    denied("net", () => require("net"));
                    denied("nested", () => new (require("worker_threads").Worker)("./compute.js"));
                    parentPort.postMessage({ label: "done", code: "done" });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
            File(workDir, "hold.js").writeText(
                """
                    setInterval(() => {}, 1000);
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )
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
                    (async function main() {
                      const profile = require("autojs6:profile");
                      const workerThreads = require("worker_threads");
                      const { Worker } = workerThreads;

                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }
                      function once(worker, eventName) {
                        return new Promise((resolve, reject) => {
                          worker.once("error", reject);
                          worker.once(eventName, resolve);
                        });
                      }
                      function collectUntilDone(worker) {
                        return new Promise((resolve, reject) => {
                          const values = [];
                          worker.on("message", (message) => {
                            values.push(message);
                            if (message && message.label === "done") {
                              resolve(values);
                            }
                          });
                          worker.once("error", reject);
                        });
                      }

                      console.log("worker.profile=" + [
                        profile.workerThreads,
                        profile.highRiskCapabilities.workerThreads,
                        profile.experimentalFlags.workerThreads,
                        profile.featureFlags.worker_threads.enabled,
                        profile.disabledBuiltins.includes("worker_threads")
                      ].join("|"));
                      console.log("worker.resolve=" + [
                        require.resolve("worker_threads"),
                        require.resolve("node:worker_threads")
                      ].join("|"));
                      console.log("worker.shadow=" + (workerThreads.shadowed === true));
                      console.log("worker.policy=" + [
                        workerThreads.policy.maxWorkers,
                        workerThreads.policy.startupTimeoutMs,
                        workerThreads.policy.allowedBuiltins.includes("assert")
                      ].join("|"));

                      const compute = new Worker("./compute.js", {
                        workerData: { left: 7, right: 5, label: "worker-data" }
                      });
                      const computeExit = once(compute, "exit");
                      const computeMessage = await once(compute, "message");
                      console.log("worker.compute=" + [computeMessage.value, computeMessage.data].join("|"));
                      console.log("worker.computeExit=" + await computeExit);

                      const hold = new Worker("./hold.js");
                      let limitCode = "";
                      try {
                        new Worker("./hold.js");
                      } catch (error) {
                        limitCode = codeOf(error);
                      }
                      const terminateCode = await hold.terminate();
                      console.log("worker.limit=" + limitCode);
                      console.log("worker.terminate=" + terminateCode);

                      const restricted = new Worker("./restricted.js");
                      const restrictedExit = once(restricted, "exit");
                      const restrictedMessages = await collectUntilDone(restricted);
                      await restrictedExit;
                      const restrictedCodes = Object.create(null);
                      for (const message of restrictedMessages) {
                        restrictedCodes[message.label] = message.code;
                      }
                      console.log("worker.restricted=" + [
                        restrictedCodes.toast,
                        restrictedCodes.accessibility,
                        restrictedCodes.fs,
                        restrictedCodes.net,
                        restrictedCodes.nested
                      ].join("|"));

                      let escapeCode = "";
                      try {
                        new Worker("../escape.js");
                      } catch (error) {
                        escapeCode = codeOf(error);
                      }
                      console.log("worker.escape=" + escapeCode);

                      const diagnostics = workerThreads.diagnostics();
                      console.log("worker.diagnostics=" + [
                        diagnostics.activeWorkerCount,
                        diagnostics.maxActiveWorkerCount,
                        diagnostics.createdCount,
                        diagnostics.terminatedCount,
                        diagnostics.rejectedByPolicyCount
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-experimental.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker_threads experimental", result)
            assertTrue(result.stdout, result.stdout.contains("worker.profile=true|true|true|true|false"))
            assertTrue(result.stdout, result.stdout.contains("worker.resolve=worker_threads|worker_threads"))
            assertTrue(result.stdout, result.stdout.contains("worker.shadow=false"))
            assertTrue(result.stdout, result.stdout.contains("worker.policy=1|5000|true"))
            assertTrue(result.stdout, result.stdout.contains("worker.compute=12|worker-data"))
            assertTrue(result.stdout, result.stdout.contains("worker.computeExit=0"))
            assertTrue(result.stdout, result.stdout.contains("worker.limit=${NodeRuntimeErrorCodes.WORKER_LIMIT_EXCEEDED}"))
            assertTrue(result.stdout, result.stdout.contains("worker.terminate="))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "worker.restricted=${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_BRIDGE_DENIED}|${NodeRuntimeErrorCodes.WORKER_DISABLED}",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("worker.escape=${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}"))
            assertTrue(result.stdout, result.stdout.contains("worker.diagnostics=0|1|"))
            assertEquals("true", result.nativeValues["embedded_script.request.worker_threads_experimental_enabled"])
            assertEquals("true", result.nativeValues["embedded_script.worker_threads_enabled"])
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }
}
