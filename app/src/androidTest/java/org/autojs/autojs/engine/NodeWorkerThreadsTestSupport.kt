package org.autojs.autojs.engine

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue

internal object NodeWorkerThreadsTestSupport {

    fun probeNativeWorkerThreads(): NativeWorkerThreadsProbe {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads native availability preflight")
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                function codeOf(error) {
                  return String(error && (error.autojs6Code || error.code || error.name || ""));
                }
                try {
                  const workerThreads = require("worker_threads");
                  if (workerThreads.nativeAvailable !== true || typeof workerThreads.WorkerPool !== "function") {
                    const nodeWorkerThreads = require("node:worker_threads");
                    const diagnostics = workerThreads.diagnostics();
                    console.log("worker.preflight.facade=" + [
                      require.resolve("worker_threads"),
                      require.resolve("node:worker_threads"),
                      nodeWorkerThreads === workerThreads,
                      typeof workerThreads.Worker,
                      typeof workerThreads.MessageChannel,
                      workerThreads.nativeAvailable === false,
                      workerThreads.policy.nativeAvailable === false,
                      diagnostics.activeWorkerCount
                    ].join("|"));
                    console.log("worker.preflight.status=unavailable");
                    console.log("worker.preflight.error=ERR_AUTOJS6_WORKER_DISABLED|true");
                  } else {
                  const diagnostics = workerThreads.diagnostics();
                  console.log("worker.preflight.status=available");
                  console.log("worker.preflight.api=" + [
                    typeof workerThreads.Worker,
                    typeof workerThreads.WorkerPool,
                    typeof workerThreads.MessageChannel,
                    typeof workerThreads.MessagePort,
                    workerThreads.isMainThread === true,
                    workerThreads.parentPort === null,
                    workerThreads.workerData === undefined,
                    Number.isInteger(workerThreads.threadId),
                    workerThreads.policy.maxWorkers,
                    workerThreads.policy.startupTimeoutMs,
                    workerThreads.policy.maxMessageBytes,
                    workerThreads.policy.maxQueuedMessages,
                    workerThreads.policy.allowedBuiltins.includes("assert"),
                    workerThreads.policy.allowedBuiltins.includes("fs"),
                    workerThreads.policy.workerProfile.fs.read,
                    workerThreads.policy.workerProfile.network,
                    workerThreads.policy.workerPool.maxWorkers,
                    workerThreads.policy.workerPool.queueLimit,
                    workerThreads.policy.resourceLimits.maxOldGenerationSizeMb,
                    diagnostics.activeWorkerCount,
                    diagnostics.createdCount,
                    diagnostics.activeMessagePortCount
                  ].join("|"));
                  }
                } catch (error) {
                  const message = String(error && error.message || "");
                  console.log("worker.preflight.status=unavailable");
                  console.log("worker.preflight.error=" + [
                    codeOf(error),
                    message.includes("unavailable") || message.includes("disabled")
                  ].join("|"));
                }
            """.trimIndent(),
            sourceName = "<node-worker-native-preflight.js>",
            timeoutMs = 10_000L,
            workerThreadsExperimentalEnabled = true,
        )
        NodeEmbeddedTestHarness.assertSucceeded("worker_threads native availability preflight", result)
        return when {
            result.stdout.contains("worker.preflight.status=available") -> {
                assertTrue(
                    result.stdout,
                    result.stdout.contains("worker.preflight.api=function|function|function|function|true|true|true|true|1|5000|65536|32|true|true|workingDirectory|denied|1|8|64|0|0|0"),
                )
                NativeWorkerThreadsProbe(available = true, stdout = result.stdout)
            }
            result.stdout.contains("worker.preflight.status=unavailable") -> {
                assertTrue(
                    result.stdout,
                    result.stdout.contains("worker.preflight.facade=worker_threads|worker_threads|true|function|function|true|true|0"),
                )
                assertTrue(
                    result.stdout,
                    result.stdout.contains("worker.preflight.error=${NodeRuntimeErrorCodes.WORKER_DISABLED}|true"),
                )
                NativeWorkerThreadsProbe(available = false, stdout = result.stdout)
            }
            else -> {
                fail("Missing worker_threads preflight status: ${NodeEmbeddedTestHarness.describeResult("worker preflight", result)}")
                throw AssertionError("Missing worker_threads preflight status")
            }
        }
    }

    fun assumeNativeWorkerThreadsAvailable(label: String): NativeWorkerThreadsProbe {
        val probe = probeNativeWorkerThreads()
        assumeTrue(
            "Native worker_threads unavailable for $label; runnable worker tests remain promotion gates. stdout=${probe.stdout}",
            probe.available,
        )
        return probe
    }

    data class NativeWorkerThreadsProbe(
        val available: Boolean,
        val stdout: String,
    )
}
