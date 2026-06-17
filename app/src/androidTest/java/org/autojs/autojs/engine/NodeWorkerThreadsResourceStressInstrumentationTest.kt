package org.autojs.autojs.engine

import android.os.Process
import android.os.SystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeWorkerThreadsResourceStressInstrumentationTest {

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
    fun createAndTerminateWorkersSequentiallyDoesNotGrowResourceCounters() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads resource stress")

        val before = resourceSnapshot("before")
        val workDir = createWorkDir("node-worker-resource-sequence")
        try {
            File(workDir, "ready.js").writeText(
                """
                    const { parentPort, workerData } = require("worker_threads");
                    parentPort.postMessage({ ready: workerData.index });
                    setInterval(() => {}, 1000);
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const workerThreads = require("worker_threads");
                      const { Worker } = workerThreads;
                      function once(worker, eventName) {
                        return new Promise((resolve, reject) => {
                          worker.once("error", reject);
                          worker.once(eventName, resolve);
                        });
                      }
                      for (let index = 0; index < 50; index += 1) {
                        const worker = new Worker("./ready.js", { workerData: { index } });
                        const message = await once(worker, "message");
                        if (!message || message.ready !== index) {
                          throw new Error("worker sequence mismatch " + index);
                        }
                        await worker.terminate();
                      }
                      const diagnostics = workerThreads.diagnostics();
                      console.log("worker.resource.sequence=" + [
                        diagnostics.activeWorkerCount,
                        diagnostics.maxActiveWorkerCount,
                        diagnostics.createdCount,
                        diagnostics.terminatedCount,
                        diagnostics.cleanupRequestedCount,
                        diagnostics.cleanupErrorCount
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-resource-sequence.js>",
                workingDirectory = workDir,
                timeoutMs = 45_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker sequence terminate", result)
            assertTrue(result.stdout, result.stdout.contains("worker.resource.sequence=0|1|50|50|0|0"))
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
            assertEquals("50", result.nativeValues["embedded_script.worker_created_count"])
            assertEquals("50", result.nativeValues["embedded_script.worker_terminated_count"])
            assertEquals("0", result.nativeValues["embedded_script.worker_cleanup_requested_count"])

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            SystemClock.sleep(500L)
            val after = resourceSnapshot("after")
            println("node.worker.resource.before=$before")
            println("node.worker.resource.after=$after")
            assertResourceGrowthBounded(before, after)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun workerExceptionDoesNotPoisonNextRun() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads exception lifecycle")

        val workDir = createWorkDir("node-worker-resource-exception")
        try {
            File(workDir, "thrower.js").writeText(
                """
                    throw new Error("worker resource boom");
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const workerThreads = require("worker_threads");
                      const { Worker } = workerThreads;
                      const worker = new Worker("./thrower.js");
                      const workerError = await new Promise((resolve) => worker.once("error", resolve));
                      const exitCode = await new Promise((resolve) => worker.once("exit", resolve));
                      const diagnostics = workerThreads.diagnostics();
                      console.log("worker.resource.throw=" + [
                        String(workerError && workerError.message || "").includes("worker resource boom"),
                        exitCode,
                        diagnostics.activeWorkerCount,
                        diagnostics.cleanupRequestedCount
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-resource-throw.js>",
                workingDirectory = workDir,
                timeoutMs = 15_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker exception", result)
            assertTrue(result.stdout, result.stdout.contains("worker.resource.throw=true|"))
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])

            val recovery = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("worker.resource.recovery=" + process.version);
                """.trimIndent(),
                sourceName = "<node-worker-resource-recovery.js>",
                timeoutMs = 5_000L,
                workerThreadsExperimentalEnabled = true,
            )
            NodeEmbeddedTestHarness.assertSucceeded("worker exception recovery", recovery)
            assertTrue(recovery.stdout, recovery.stdout.contains("worker.resource.recovery=v24.5.0"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun infiniteLoopWorkerTimeoutDoesNotPoisonNextRun() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads timeout lifecycle")

        val workDir = createWorkDir("node-worker-resource-timeout")
        try {
            File(workDir, "loop.js").writeText(
                """
                    while (true) {}
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val timeout = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const { Worker } = require("worker_threads");
                    new Worker("./loop.js");
                    setInterval(() => {}, 50);
                    console.log("worker.resource.loop.started");
                """.trimIndent(),
                sourceName = "<node-worker-resource-timeout.js>",
                workingDirectory = workDir,
                timeoutMs = 1_200L,
                workerThreadsExperimentalEnabled = true,
            )

            assertFalse(NodeEmbeddedTestHarness.describeResult("worker timeout", timeout), timeout.succeeded)
            assertTrue(NodeEmbeddedTestHarness.describeResult("worker timeout", timeout), timeout.timedOut)
            assertEquals("ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT", timeout.errorCode)

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val recovery = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("worker.resource.timeout.recovery=" + process.version);
                """.trimIndent(),
                sourceName = "<node-worker-resource-timeout-recovery.js>",
                timeoutMs = 5_000L,
                workerThreadsExperimentalEnabled = true,
            )
            NodeEmbeddedTestHarness.assertSucceeded("worker timeout recovery", recovery)
            assertTrue(recovery.stdout, recovery.stdout.contains("worker.resource.timeout.recovery=v24.5.0"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun pendingWorkerMessageIsRejectedDuringParentCompletionCleanup() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads completion cleanup")

        val workDir = createWorkDir("node-worker-resource-cleanup")
        try {
            File(workDir, "late.js").writeText(
                """
                    const { parentPort } = require("worker_threads");
                    setTimeout(() => parentPort.postMessage("late"), 1000);
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const workerThreads = require("worker_threads");
                    const { Worker } = workerThreads;
                    function codeOf(error) {
                      return String(error && (error.autojs6Code || error.code || error.name || ""));
                    }
                    const worker = new Worker("./late.js");
                    worker.once("message", () => console.log("worker.resource.late.message"));
                    worker.once("error", (error) => console.log("worker.resource.cleanup.error=" + codeOf(error)));
                    console.log("worker.resource.cleanup.before=" + workerThreads.diagnostics().activeWorkerCount);
                """.trimIndent(),
                sourceName = "<node-worker-resource-cleanup.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                workerThreadsExperimentalEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker completion cleanup", result)
            assertTrue(result.stdout, result.stdout.contains("worker.resource.cleanup.before=1"))
            assertTrue(
                result.stdout,
                result.stdout.contains("worker.resource.cleanup.error=${NodeRuntimeErrorCodes.WORKER_TERMINATED}"),
            )
            assertFalse(result.stdout.contains("worker.resource.late.message"))
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
            assertEquals("0", result.nativeValues["embedded_script.pending_worker_count"])
            assertEquals("false", result.nativeValues["embedded_script.pending_workers"])
            assertEquals("1", result.nativeValues["embedded_script.worker_cleanup_requested_count"])
            assertEquals("1", result.nativeValues["embedded_script.worker_cleanup_terminate_requested_count"])
            assertEquals("1", result.nativeValues["embedded_script.worker_cleanup_pending_message_rejected_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun createWorkDir(prefix: String): File =
        File(context.cacheDir, "$prefix-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create worker resource directory: $absolutePath" }
        }

    private fun resourceSnapshot(label: String): ResourceSnapshot {
        val pid = Process.myPid()
        return ResourceSnapshot(
            label = label,
            pid = pid,
            rssKb = readStatusLongKb(pid, "VmRSS:"),
            threadCount = File("/proc/$pid/task").safeCountChildren(),
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

    private fun assertResourceGrowthBounded(before: ResourceSnapshot, after: ResourceSnapshot) {
        val threadDelta = before.threadCount?.let { base -> after.threadCount?.minus(base) }
        if (threadDelta != null) {
            assertTrue("thread count grew too much: before=$before after=$after", threadDelta <= MAX_THREAD_DELTA)
        }
        val rssDelta = before.rssKb?.let { base -> after.rssKb?.minus(base) }
        if (rssDelta != null) {
            assertTrue("RSS grew too much: before=$before after=$after", rssDelta <= MAX_RSS_DELTA_KB)
        }
    }

    private data class ResourceSnapshot(
        val label: String,
        val pid: Int,
        val rssKb: Long?,
        val threadCount: Int?,
    )

    private companion object {
        private const val MAX_THREAD_DELTA = 16
        private const val MAX_RSS_DELTA_KB = 262_144L
    }
}
