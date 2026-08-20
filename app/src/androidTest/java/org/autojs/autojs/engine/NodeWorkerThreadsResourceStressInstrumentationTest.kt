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
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads sequential resource stress")

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
                      for (let index = 0; index < $SEQUENTIAL_WORKER_JOB_COUNT; index += 1) {
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
                timeoutMs = 90_000L,
                workerThreadsEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker sequence terminate", result)
            assertTrue(result.stdout, result.stdout.contains("worker.resource.sequence=0|1|100|100|0|0"))
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
            assertEquals("100", result.nativeValues["embedded_script.worker_created_count"])
            assertEquals("100", result.nativeValues["embedded_script.worker_terminated_count"])
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
    fun workerPoolStressDoesNotLeakAfterFailuresTimeoutsAndTransfers() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads pool stress")
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads pool stress")

        val before = resourceSnapshot("pool-before")
        val workDir = createWorkDir("node-worker-pool-stress")
        try {
            File(workDir, "pool-stress.cjs").writeText(
                """
                    const { parentPort, threadId } = require("worker_threads");

                    parentPort.on("message", (task) => {
                      const id = task && task.__autojs6PoolTaskId;
                      const value = task && task.value || {};
                      if (value.kind === "throw") {
                        parentPort.postMessage({
                          __autojs6PoolTaskId: id,
                          error: "worker pool stress boom"
                        });
                        return;
                      }
                      if (value.kind === "never") {
                        return;
                      }
                      if (value.kind === "delay") {
                        setTimeout(() => {
                          parentPort.postMessage({
                            __autojs6PoolTaskId: id,
                            value: {
                              kind: "delay",
                              threadId
                            }
                          });
                        }, value.ms);
                        return;
                      }
                      if (value.kind === "buffer") {
                        const bytes = new Uint8Array(value.buffer);
                        parentPort.postMessage({
                          __autojs6PoolTaskId: id,
                          value: {
                            kind: "buffer",
                            length: value.buffer.byteLength,
                            first: bytes[0],
                            last: bytes[bytes.length - 1],
                            threadId
                          }
                        });
                        return;
                      }
                      parentPort.postMessage({
                        __autojs6PoolTaskId: id,
                        value: {
                          index: value.index,
                          value: value.index * 3,
                          threadId
                        }
                      });
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const workerThreads = require("worker_threads");
                      const { WorkerPool } = workerThreads;
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }

                      const pool = new WorkerPool("./pool-stress.cjs", {
                        minWorkers: 1,
                        maxWorkers: 1,
                        idleTimeoutMs: 5000,
                        taskTimeoutMs: 1000,
                        queueLimit: 4,
                        resourceLimits: {
                          maxOldGenerationSizeMb: 16
                        }
                      });

                      let firstThreadId = 0;
                      let reused = true;
                      let sum = 0;
                      for (let index = 0; index < $SEQUENTIAL_WORKER_JOB_COUNT; index += 1) {
                        const message = await pool.run({ index });
                        if (index === 0) firstThreadId = message.threadId;
                        else if (message.threadId !== firstThreadId) reused = false;
                        sum += message.value;
                      }

                      const buffer = new ArrayBuffer(16);
                      const bytes = new Uint8Array(buffer);
                      bytes[0] = 7;
                      bytes[15] = 11;
                      const transferred = await pool.run({ kind: "buffer", buffer }, { transferList: [buffer] });
                      const detached = buffer.byteLength === 0;

                      let failureCode = "";
                      try {
                        await pool.run({ kind: "throw" });
                      } catch (error) {
                        failureCode = codeOf(error);
                      }
                      const recoveredAfterFailure = await pool.run({ index: 7 });

                      const running = pool.run({ kind: "delay", ms: 150 });
                      const queued = pool.run({ index: 9 });
                      const queuedHandled = queued.catch((error) => codeOf(error));
                      const cancelResult = queued.cancel();
                      const cancelCode = await queuedHandled;
                      const runningResult = await running;

                      let timeoutCode = "";
                      try {
                        await pool.run({ kind: "never" }, { timeoutMs: 50 });
                      } catch (error) {
                        timeoutCode = codeOf(error);
                      }
                      const recoveredAfterTimeout = await pool.run({ index: 11 });

                      await pool.close();
                      const diagnostics = workerThreads.diagnostics();
                      console.log("worker.resource.pool.stress=" + [
                        sum,
                        reused,
                        recoveredAfterFailure.value,
                        recoveredAfterTimeout.value
                      ].join("|"));
                      console.log("worker.resource.pool.transfer=" + [
                        detached,
                        transferred.length,
                        transferred.first,
                        transferred.last
                      ].join("|"));
                      console.log("worker.resource.pool.failures=" + [
                        failureCode,
                        cancelResult,
                        cancelCode,
                        runningResult.kind,
                        timeoutCode
                      ].join("|"));
                      console.log("worker.resource.pool.shutdown=" + [
                        diagnostics.activeWorkerCount,
                        diagnostics.activeWorkerPoolCount,
                        diagnostics.workerPoolTaskCompletedCount,
                        diagnostics.workerPoolTaskCancelledCount,
                        diagnostics.workerPoolTaskTimeoutCount,
                        diagnostics.workerPoolQueueRejectedCount
                      ].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-worker-pool-stress.js>",
                workingDirectory = workDir,
                timeoutMs = 60_000L,
                workerThreadsEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker pool stress", result)
            assertTrue(result.stdout, result.stdout.contains("worker.resource.pool.stress=14850|true|21|33"))
            assertTrue(result.stdout, result.stdout.contains("worker.resource.pool.transfer=true|16|7|11"))
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "worker.resource.pool.failures=${NodeRuntimeErrorCodes.WORKER_POOL_TASK_FAILED}|true|${NodeRuntimeErrorCodes.WORKER_POOL_TASK_CANCELLED}|delay|${NodeRuntimeErrorCodes.WORKER_POOL_TASK_TIMEOUT}",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("worker.resource.pool.shutdown=0|0|104|1|1|0"))
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
            assertEquals("0", result.nativeValues["embedded_script.worker_pool_active_count"])
            assertEquals("104", result.nativeValues["embedded_script.worker_pool_task_completed_count"])
            assertEquals("1", result.nativeValues["embedded_script.worker_pool_task_cancelled_count"])
            assertEquals("1", result.nativeValues["embedded_script.worker_pool_task_timeout_count"])

            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            SystemClock.sleep(500L)
            val after = resourceSnapshot("pool-after")
            println("node.worker.pool.resource.before=$before")
            println("node.worker.pool.resource.after=$after")
            assertResourceGrowthBounded(before, after)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun workerPoolIsClosedDuringParentCompletionCleanup() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads pool parent cleanup")
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads pool parent cleanup")

        val workDir = createWorkDir("node-worker-pool-parent-cleanup")
        try {
            File(workDir, "pool-cleanup.cjs").writeText(
                """
                    const { parentPort } = require("worker_threads");
                    parentPort.on("message", (task) => {
                      setTimeout(() => {
                        parentPort.postMessage({
                          __autojs6PoolTaskId: task.__autojs6PoolTaskId,
                          value: "late"
                        });
                      }, 1000);
                    });
                """.trimIndent(),
                StandardCharsets.UTF_8,
            )

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const workerThreads = require("worker_threads");
                    const { WorkerPool } = workerThreads;
                    const pool = new WorkerPool("./pool-cleanup.cjs", {
                      minWorkers: 1,
                      maxWorkers: 1,
                      idleTimeoutMs: 5000,
                      taskTimeoutMs: 5000,
                      queueLimit: 1
                    });
                    pool.run({ kind: "late" }).catch(() => {});
                    const diagnostics = workerThreads.diagnostics();
                    console.log("worker.resource.pool.cleanup.before=" + [
                      diagnostics.activeWorkerCount,
                      diagnostics.activeWorkerPoolCount
                    ].join("|"));
                """.trimIndent(),
                sourceName = "<node-worker-pool-parent-cleanup.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                workerThreadsEnabled = true,
            )

            NodeEmbeddedTestHarness.assertSucceeded("worker pool parent cleanup", result)
            assertTrue(result.stdout, result.stdout.contains("worker.resource.pool.cleanup.before=1|1"))
            assertEquals("0", result.nativeValues["embedded_script.worker_active_count"])
            assertEquals("0", result.nativeValues["embedded_script.worker_pool_active_count"])
            assertEquals("1", result.nativeValues["embedded_script.worker_pool_task_cancelled_count"])
            assertEquals("2", result.nativeValues["embedded_script.worker_cleanup_requested_count"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun workerExceptionDoesNotPoisonNextRun() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("worker_threads exception lifecycle")
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads exception lifecycle")

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
                workerThreadsEnabled = true,
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
                workerThreadsEnabled = true,
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
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads timeout lifecycle")

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
                workerThreadsEnabled = true,
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
                workerThreadsEnabled = true,
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
        NodeWorkerThreadsTestSupport.assumeNativeWorkerThreadsAvailable("worker_threads completion cleanup")

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
                workerThreadsEnabled = true,
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
        private const val SEQUENTIAL_WORKER_JOB_COUNT = 100
        private const val MAX_THREAD_DELTA = 16
        private const val MAX_RSS_DELTA_KB = 262_144L
    }
}
