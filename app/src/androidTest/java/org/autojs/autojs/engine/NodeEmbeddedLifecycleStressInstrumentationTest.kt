package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class NodeEmbeddedLifecycleStressInstrumentationTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun runSimpleScript100Times() {
        assumeEmbeddedScriptExecutionEnabled()

        repeat(STRESS_ITERATIONS) { index ->
            val result = runEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("stress.simple.$index=" + process.version);
                """.trimIndent(),
                sourceName = "<lifecycle-stress-simple-$index.js>",
                timeoutMs = SIMPLE_TIMEOUT_MS,
            )

            assertSucceeded("simple iteration $index", result)
            assertFalse("simple iteration $index timed out", result.timedOut)
            assertTrue(result.stdout, result.stdout.contains("stress.simple.$index=v24.5.0"))
            assertEquals("STOPPED", result.nativeValues["embedded_script.runtime_state"])
            assertTrue(
                "simple iteration $index missing execution id",
                result.nativeValues["embedded_script.execution_id"].orEmpty().isNotBlank(),
            )
            settleEmbeddedScriptServiceProcess()
        }
    }

    @Test
    fun timeoutDoesNotPoisonNextRun() {
        assumeEmbeddedScriptExecutionEnabled()

        val timeout = runEmbeddedScript(
            source = """
                "nodejs";
                setInterval(() => {}, 100);
                console.log("stress.timeout.started");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-timeout.js>",
            timeoutMs = FAULT_TIMEOUT_MS,
        )

        assertTimedOut("timeout run", timeout)
        assertEquals("DESTROYED", timeout.nativeValues["embedded_script.runtime_state"])
        assertTrue(
            timeout.nativeValues["embedded_script.lifecycle_transitions"].orEmpty(),
            timeout.nativeValues["embedded_script.lifecycle_transitions"].orEmpty().contains("RUNNING->TIMED_OUT"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.timeout")
    }

    @Test
    fun explicitLongRunningModeIsRejectedBeforeUserScriptRuns() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-long-running-policy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create long-running policy dir: $absolutePath" }
        }
        val marker = File(scriptDir, "unexpected.txt")

        try {
            val result = runEmbeddedScript(
                source = """
                    "nodejs";
                    require("fs").writeFileSync("./unexpected.txt", "ran");
                    setInterval(() => {}, 100);
                """.trimIndent(),
                sourceName = "<lifecycle-stress-long-running-policy.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = 0L,
                executionMode = "longRunning",
            )

            assertFalse("long-running policy gate unexpectedly succeeded", result.succeeded)
            assertFalse("long-running policy gate must not be reported as a script timeout", result.timedOut)
            assertEquals(NodeRuntimeErrorCodes.EMBEDDED_NODE_UNSUPPORTED_OPTION, result.errorCode)
            assertEquals(NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING, result.nativeValues["embedded_script.execution_mode"])
            assertEquals("partial", result.nativeValues["embedded_script.execution_mode_status"])
            assertEquals(NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT, result.nativeValues["embedded_script.execution_mode_launch_surface"])
            assertEquals("false", result.nativeValues["embedded_script.execution_mode_supported"])
            assertEquals("startup_timeout_stop_grace_and_watchdog", result.nativeValues["embedded_script.execution_mode_timeout_policy"])
            assertEquals("true", result.nativeValues["embedded_script.long_running_requested"])
            assertEquals("false", result.nativeValues["embedded_script.long_running_supported"])
            assertEquals("true", result.nativeValues["embedded_script.execution_mode_rejected"])
            assertTrue(result.errorMessage.orEmpty(), result.errorMessage.orEmpty().contains("Phase 7 runtime state machine"))
            assertFalse("long-running rejected script should not have entered user code", marker.exists())

            assertRecoveryRun("after.long_running.policy")
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun uncaughtExceptionDoesNotPoisonNextRun() {
        assumeEmbeddedScriptExecutionEnabled()

        val failure = runEmbeddedScript(
            source = """
                "nodejs";
                throw new Error("stress uncaught");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-uncaught.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )

        assertFalse(failure.succeeded)
        assertFalse(failure.timedOut)
        assertTrue(
            failure.errorMessage.orEmpty() + "\n" + failure.errorStack.orEmpty(),
            failure.errorMessage.orEmpty().contains("stress uncaught") ||
                failure.errorStack.orEmpty().contains("stress uncaught"),
        )
        assertTrue(
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty(),
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty().contains("RUNNING->FAILED"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.uncaught")
    }

    @Test
    fun unhandledRejectionDoesNotPoisonNextRun() {
        assumeEmbeddedScriptExecutionEnabled()

        val failure = runEmbeddedScript(
            source = """
                "nodejs";
                Promise.reject(new Error("stress rejection"));
            """.trimIndent(),
            sourceName = "<lifecycle-stress-rejection.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )

        assertFalse(failure.succeeded)
        assertFalse(failure.timedOut)
        assertTrue(
            failure.errorMessage.orEmpty() + "\n" + failure.errorStack.orEmpty(),
            failure.errorMessage.orEmpty().contains("stress rejection") ||
                failure.errorStack.orEmpty().contains("stress rejection"),
        )
        assertTrue(
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty(),
            failure.nativeValues["embedded_script.lifecycle_transitions"].orEmpty().contains("RUNNING->FAILED"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.rejection")
    }

    @Test
    fun unclearedIntervalTimesOutThenNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val timeout = runEmbeddedScript(
            source = """
                "nodejs";
                let ticks = 0;
                setInterval(() => {
                  ticks += 1;
                  if (ticks === 1) console.log("stress.interval.tick");
                }, 50);
            """.trimIndent(),
            sourceName = "<lifecycle-stress-interval.js>",
            timeoutMs = FAULT_TIMEOUT_MS,
        )

        assertTimedOut("uncleared interval", timeout)
        assertEquals(true, timeout.pendingTimers)
        assertTrue(
            timeout.errorMessage.orEmpty(),
            timeout.errorMessage.orEmpty().contains("pendingTimers=true"),
        )
        settleEmbeddedScriptServiceProcess()

        val recovery = assertRecoveryRun("after.interval")
        assertFalse(recovery.stdout.contains("stress.interval.tick"))
    }

    @Test
    fun activeScopedFsStreamTimesOutThenNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-lifecycle-fs-stream-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create lifecycle fs stream dir: $absolutePath" }
        }
        try {
            val timeout = runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.mkdirSync("./stream-timeout", { recursive: true });
                    fs.writeFileSync("./stream-timeout/input.bin", Buffer.alloc(2 * 1024 * 1024, 65));
                    globalThis.__autojs6HeldReadable = fs.createReadStream("./stream-timeout/input.bin", {
                      highWaterMark: 64 * 1024
                    });
                    globalThis.__autojs6HeldReadable.on("data", () => {
                      console.log("stress.fs.stream.paused");
                      globalThis.__autojs6HeldReadable.pause();
                    });
                    globalThis.__autojs6HeldReadable.on("end", () => console.log("stress.fs.stream.unexpected_end"));
                    console.log("stress.fs.stream.pending");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-fs-stream.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = FAULT_TIMEOUT_MS,
            )

            assertTimedOut("active scoped fs stream", timeout)
            assertEquals(false, timeout.pendingTimers)
            assertEquals(true, timeout.pendingScopedFsStreams)
            assertEquals(1, timeout.pendingScopedFsStreamCount)
            assertEquals(true, timeout.pendingCallbacks)
            assertTrue(
                timeout.errorMessage.orEmpty(),
                timeout.errorMessage.orEmpty().contains("pendingScopedFsStreamCount=1"),
            )
            settleEmbeddedScriptServiceProcess()

            assertRecoveryRun("after.fs.stream")
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun activeZlibStreamTimesOutThenNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val timeout = runEmbeddedScript(
            source = """
                "nodejs";
                const zlib = require("zlib");
                globalThis.__autojs6HeldGzip = zlib.createGzip();
                globalThis.__autojs6HeldGzip.on("data", () => {
                  console.log("stress.zlib.stream.data");
                });
                globalThis.__autojs6HeldGzip.write(Buffer.from("pending-zlib-stream"));
                console.log("stress.zlib.stream.pending");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-zlib-stream.js>",
            timeoutMs = FAULT_TIMEOUT_MS,
        )

        assertTimedOut("active zlib stream", timeout)
        assertEquals(false, timeout.pendingTimers)
        assertEquals(true, timeout.pendingZlibStreams)
        assertEquals(1, timeout.pendingZlibStreamCount)
        assertEquals(true, timeout.pendingCallbacks)
        assertTrue(
            timeout.errorMessage.orEmpty(),
            timeout.errorMessage.orEmpty().contains("pendingZlibStreamCount=1"),
        )
        settleEmbeddedScriptServiceProcess()

        assertRecoveryRun("after.zlib.stream")
    }

    @Test
    fun nestedFsCallbacksDrainBeforeSuccess() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-lifecycle-drain-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create lifecycle drain dir: $absolutePath" }
        }
        try {
            val result = runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.writeFile("./stress-drain.txt", "ok", "utf8", (writeError) => {
                      if (writeError) throw writeError;
                      fs.readFile("./stress-drain.txt", "utf8", (readError, text) => {
                        if (readError) throw readError;
                        setTimeout(() => {
                          console.log("stress.nested.callback=" + text);
                        }, 10);
                      });
                    });
                """.trimIndent(),
                sourceName = "<lifecycle-stress-drain.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = SIMPLE_TIMEOUT_MS,
            )

            assertSucceeded("nested fs callback drain", result)
            assertTrue(result.stdout, result.stdout.contains("stress.nested.callback=ok"))
            assertEquals("STOPPED", result.nativeValues["embedded_script.runtime_state"])
            assertEquals(false, result.pendingCallbacks)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun stdoutAndStderrDoNotLeakAcrossRuns() {
        assumeEmbeddedScriptExecutionEnabled()

        val first = runEmbeddedScript(
            source = """
                "nodejs";
                console.log("stress.stdout.first");
                console.error("stress.stderr.first");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-stdio-first.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )
        assertSucceeded("stdio first run", first)
        assertTrue(first.stdout, first.stdout.contains("stress.stdout.first"))
        assertTrue(first.stderr, first.stderr.contains("stress.stderr.first"))
        settleEmbeddedScriptServiceProcess()

        val second = runEmbeddedScript(
            source = """
                "nodejs";
                console.log("stress.stdout.second");
            """.trimIndent(),
            sourceName = "<lifecycle-stress-stdio-second.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )
        assertSucceeded("stdio second run", second)
        assertTrue(second.stdout, second.stdout.contains("stress.stdout.second"))
        assertFalse(second.stdout.contains("stress.stdout.first"))
        assertFalse(second.stderr.contains("stress.stderr.first"))
    }

    @Test
    fun concurrentExecutionIsRejectedAndNextRunWorks() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-concurrent-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create concurrent execution dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val firstFailure = AtomicReference<Throwable?>()
        val firstThread = Thread {
            runCatching {
                runEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        console.log("stress.concurrent.first.started");
                        return new Promise((resolve) => setTimeout(() => {
                          console.log("stress.concurrent.first.done");
                          resolve();
                        }, 3000));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-concurrent-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = CONCURRENT_TIMEOUT_MS,
                )
            }.onSuccess { result ->
                firstResult.set(result)
            }.onFailure { error ->
                firstFailure.set(error)
            }
        }.apply {
            name = "AutoJs6NodeConcurrentFirst"
            start()
        }

        try {
            assertTrue(
                "First concurrent run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )

            val second = runEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("stress.concurrent.second.unexpected");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-concurrent-second.js>",
                timeoutMs = SIMPLE_TIMEOUT_MS,
            )

            firstThread.join(CONCURRENT_TIMEOUT_MS + 3000L)
            firstFailure.get()?.let { error ->
                throw AssertionError("First concurrent run failed before producing a result.", error)
            }
            assertFalse("First concurrent run did not finish.", firstThread.isAlive)
            val first = firstResult.get()
            assertTrue("First concurrent run did not produce a result.", first != null)
            assertSucceeded("concurrent first", first!!)
            assertTrue(first.stdout, first.stdout.contains("stress.concurrent.first.done"))

            assertFalse("Second concurrent run must be rejected, not queued silently.", second.succeeded)
            assertFalse("Engine-busy rejection should not be reported as script timeout.", second.timedOut)
            assertEquals(NodeRuntimeErrorCodes.NODE_ENGINE_BUSY, second.errorCode)
            assertEquals("true", second.nativeValues["embedded_script.engine_busy"])
            assertEquals("single-active-execution-per-process", second.nativeValues["embedded_script.concurrent_policy"])
            assertTrue(second.errorMessage.orEmpty(), second.errorMessage.orEmpty().contains("only one active execution"))

            settleEmbeddedScriptServiceProcess()
            assertRecoveryRun("after.concurrent.busy")
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queuedExecutionRunsAfterActiveExecution() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-order-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue order dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val order = File(scriptDir, "order.txt")
        val firstId = "queue-first-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val firstFailure = AtomicReference<Throwable?>()
        val secondId = "queue-second-${System.nanoTime()}"
        val secondResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondFailure = AtomicReference<Throwable?>()

        val firstThread = Thread {
            runCatching {
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        fs.appendFileSync("./order.txt", "first-started\n");
                        console.log("stress.queue.first.started");
                        return new Promise((resolve) => setTimeout(() => {
                          fs.appendFileSync("./order.txt", "first-done\n");
                          console.log("stress.queue.first.done");
                          resolve();
                        }, 1500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                )
            }.onSuccess(firstResult::set)
                .onFailure(firstFailure::set)
        }.apply {
            name = "AutoJs6NodeQueueFirst"
            start()
        }

        val secondThread = Thread {
            runCatching {
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.appendFileSync("./order.txt", "second-run\n");
                        console.log("stress.queue.second.run");
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-second.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                    executionId = secondId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                )
            }.onSuccess(secondResult::set)
                .onFailure(secondFailure::set)
        }.apply {
            name = "AutoJs6NodeQueueSecond"
        }

        try {
            assertTrue(
                "First queued-order run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            secondThread.start()
            assertTrue(
                "Second execution was not visible in embedded execution queue.",
                waitUntil(5000L) {
                    NativeNodeEmbeddedScriptService.queueStatus(context).queuedExecutionIds.contains(secondId)
                },
            )
            val queuedStatus = NativeNodeEmbeddedScriptService.queueStatus(context)
            assertEquals(NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS, queuedStatus.maxQueuedExecutions)
            assertTrue(queuedStatus.activeExecutionId.orEmpty().isNotBlank())
            assertTrue(queuedStatus.queuedExecutionIds.contains(secondId))

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            secondThread.join(QUEUE_TEST_TIMEOUT_MS + SIMPLE_TIMEOUT_MS + 3000L)
            firstFailure.get()?.let { throw AssertionError("First queue run failed.", it) }
            secondFailure.get()?.let { throw AssertionError("Second queue run failed.", it) }
            assertFalse("First queue run did not finish.", firstThread.isAlive)
            assertFalse("Second queue run did not finish.", secondThread.isAlive)

            val first = firstResult.get()
            val second = secondResult.get()
            assertTrue("First queue run did not produce a result.", first != null)
            assertTrue("Second queue run did not produce a result.", second != null)
            assertSucceeded("queue first", first!!)
            assertSucceeded("queue second", second!!)
            assertEquals("true", second.nativeValues["embedded_script.queue_queued"])
            assertEquals("dequeued", second.nativeValues["embedded_script.queue_status"])
            assertTrue(
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty(),
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty().toLongOrNull() ?: 0L > 0L,
            )

            val orderText = order.readText()
            assertTrue(orderText, orderText.indexOf("first-started") >= 0)
            assertTrue(orderText, orderText.indexOf("first-done") > orderText.indexOf("first-started"))
            assertTrue(orderText, orderText.indexOf("second-run") > orderText.indexOf("first-done"))
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queuedExecutionCanBeCancelled() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-cancel-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue cancel dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val firstId = "queue-cancel-first-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondId = "queue-cancel-${System.nanoTime()}"
        val secondResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondFailure = AtomicReference<Throwable?>()

        val firstThread = Thread {
            firstResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        return new Promise((resolve) => setTimeout(resolve, 2500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-cancel-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueCancelFirst"
            start()
        }
        val secondThread = Thread {
            runCatching {
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        console.log("stress.queue.cancel.unexpected");
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-cancel-second.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                    executionId = secondId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                )
            }.onSuccess(secondResult::set)
                .onFailure(secondFailure::set)
        }.apply {
            name = "AutoJs6NodeQueueCancelSecond"
        }

        try {
            assertTrue(
                "First queue-cancel run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            secondThread.start()
            assertTrue(
                "Second execution was not queued before cancellation.",
                waitUntil(5000L) {
                    NativeNodeEmbeddedScriptService.queueStatus(context).queuedExecutionIds.contains(secondId)
                },
            )

            assertTrue("Queued execution cancellation returned false.", NativeNodeEmbeddedScriptService.cancelQueuedExecution(context, secondId))
            secondThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            secondFailure.get()?.let { throw AssertionError("Cancelled queue run threw before producing a result.", it) }
            assertFalse("Cancelled queue run did not finish.", secondThread.isAlive)
            val cancelled = secondResult.get()
            assertTrue("Cancelled queue run did not produce a result.", cancelled != null)
            assertFalse("Cancelled queue run unexpectedly succeeded.", cancelled!!.succeeded)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, cancelled.errorCode)
            assertEquals("true", cancelled.nativeValues["embedded_script.queue_cancelled"])

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val first = firstResult.get()
            assertTrue("First queue-cancel run did not produce a result.", first != null)
            assertSucceeded("queue cancel first", first!!)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queuedExecutionTimeoutReturnsStableError() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-timeout-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue timeout dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val secondMarker = File(scriptDir, "second-ran.txt")
        val firstId = "queue-timeout-first-${System.nanoTime()}"
        val secondId = "queue-timeout-second-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()

        val firstThread = Thread {
            firstResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        return new Promise((resolve) => setTimeout(resolve, 2500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-timeout-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueTimeoutFirst"
            start()
        }

        try {
            assertTrue(
                "First queue-timeout run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )

            val second = enqueueEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.writeFileSync("./second-ran.txt", "unexpected");
                    console.log("stress.queue.timeout.second.unexpected");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-queue-timeout-second.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = SIMPLE_TIMEOUT_MS,
                executionId = secondId,
                queueTimeoutMs = QUEUE_SHORT_TIMEOUT_MS,
            )

            assertFalse("Queue-timeout run unexpectedly succeeded.", second.succeeded)
            assertFalse("Queue-timeout failure should not be reported as a script timeout.", second.timedOut)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_TIMEOUT, second.errorCode)
            assertEquals("true", second.nativeValues["embedded_script.queue_timeout"])
            assertEquals("queue_timeout", second.nativeValues["embedded_script.queue_status"])
            assertTrue(
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty(),
                second.nativeValues["embedded_script.queue_wait_ms"].orEmpty().toLongOrNull() ?: 0L > 0L,
            )
            assertFalse("Timed-out queued script must not run user code.", secondMarker.exists())

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val first = firstResult.get()
            assertTrue("First queue-timeout run did not produce a result.", first != null)
            assertSucceeded("queue timeout first", first!!)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    @Test
    fun queueLimitReachedReturnsStableError() {
        assumeEmbeddedScriptExecutionEnabled()

        val scriptDir = File(context.cacheDir, "embedded-queue-full-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create queue full dir: $absolutePath" }
        }
        val firstStarted = File(scriptDir, "first-started.txt")
        val firstId = "queue-full-first-${System.nanoTime()}"
        val firstResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()
        val secondId = "queue-full-second-${System.nanoTime()}"
        val secondResult = AtomicReference<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult?>()

        val firstThread = Thread {
            firstResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        const fs = require("fs");
                        fs.writeFileSync("./first-started.txt", "started");
                        return new Promise((resolve) => setTimeout(resolve, 2500));
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-full-first.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = QUEUE_TEST_TIMEOUT_MS,
                    executionId = firstId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueFullFirst"
            start()
        }
        val secondThread = Thread {
            secondResult.set(
                enqueueEmbeddedScript(
                    source = """
                        "nodejs";
                        console.log("stress.queue.full.second");
                    """.trimIndent(),
                    sourceName = "<lifecycle-stress-queue-full-second.js>",
                    workingDirectory = scriptDir.absolutePath,
                    timeoutMs = SIMPLE_TIMEOUT_MS,
                    executionId = secondId,
                    queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
                ),
            )
        }.apply {
            name = "AutoJs6NodeQueueFullSecond"
        }

        try {
            assertTrue(
                "First queue-full run did not enter user script before timeout.",
                waitUntil(5000L) { firstStarted.isFile },
            )
            secondThread.start()
            assertTrue(
                "Second execution was not queued before queue-full check.",
                waitUntil(5000L) {
                    NativeNodeEmbeddedScriptService.queueStatus(context).queuedExecutionIds.contains(secondId)
                },
            )

            val third = enqueueEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("stress.queue.full.third.unexpected");
                """.trimIndent(),
                sourceName = "<lifecycle-stress-queue-full-third.js>",
                workingDirectory = scriptDir.absolutePath,
                timeoutMs = SIMPLE_TIMEOUT_MS,
                executionId = "queue-full-third-${System.nanoTime()}",
                queueTimeoutMs = QUEUE_TEST_TIMEOUT_MS,
            )

            assertFalse("Third queue-full run unexpectedly succeeded.", third.succeeded)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_FULL, third.errorCode)
            assertEquals("true", third.nativeValues["embedded_script.queue_full"])
            assertEquals(
                NodeExecutionQueuePolicy.DEFAULT_MAX_QUEUED_EXECUTIONS.toString(),
                third.nativeValues["embedded_script.queue_max_queued_executions"],
            )

            assertTrue("Queued second execution cancellation returned false.", NativeNodeEmbeddedScriptService.cancelQueuedExecution(context, secondId))
            secondThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val second = secondResult.get()
            assertTrue("Second queue-full run did not produce a result.", second != null)
            assertEquals(NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED, second!!.errorCode)

            firstThread.join(QUEUE_TEST_TIMEOUT_MS + 3000L)
            val first = firstResult.get()
            assertTrue("First queue-full run did not produce a result.", first != null)
            assertSucceeded("queue full first", first!!)
        } finally {
            scriptDir.deleteRecursively()
        }
    }

    private fun assertRecoveryRun(label: String): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val result = runEmbeddedScript(
            source = """
                "nodejs";
                const fs = require("fs");
                fs.writeFileSync("./$label.txt", "ok");
                console.log("$label.node=" + process.version);
                console.log("$label.fs=" + fs.readFileSync("./$label.txt", "utf8"));
            """.trimIndent(),
            sourceName = "<lifecycle-stress-$label.js>",
            timeoutMs = SIMPLE_TIMEOUT_MS,
        )
        assertSucceeded(label, result)
        assertFalse("$label timed out", result.timedOut)
        assertTrue(result.stdout, result.stdout.contains("$label.node=v24.5.0"))
        assertTrue(result.stdout, result.stdout.contains("$label.fs=ok"))
        assertEquals("STOPPED", result.nativeValues["embedded_script.runtime_state"])
        return result
    }

    private fun assertSucceeded(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertTrue(
            "$label failed\nstdout=${result.stdout}\nstderr=${result.stderr}\nerror=${result.errorName}: ${result.errorMessage}",
            result.succeeded,
        )
    }

    private fun assertTimedOut(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertFalse("$label unexpectedly succeeded", result.succeeded)
        assertTrue(
            "$label should time out\nstdout=${result.stdout}\nstderr=${result.stderr}\nerror=${result.errorName}: ${result.errorMessage}\nnativeValues=${result.nativeValues}",
            result.timedOut,
        )
        assertEquals("EmbeddedScriptTimeoutError", result.errorName)
        assertEquals("ERR_AUTOJS6_EMBEDDED_NODE_SCRIPT_TIMEOUT", result.errorCode)
        assertTrue(
            result.errorMessage.orEmpty(),
            result.errorMessage.orEmpty().contains("Embedded Node.js script timed out"),
        )
    }

    private fun runEmbeddedScript(
        source: String,
        sourceName: String,
        workingDirectory: String? = context.cacheDir.absolutePath,
        timeoutMs: Long = SIMPLE_TIMEOUT_MS,
        executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                executionMode = executionMode,
                timeoutMs = timeoutMs,
            ),
        )
    }

    private fun enqueueEmbeddedScript(
        source: String,
        sourceName: String,
        workingDirectory: String? = context.cacheDir.absolutePath,
        timeoutMs: Long = SIMPLE_TIMEOUT_MS,
        executionId: String,
        queueTimeoutMs: Long = QUEUE_TEST_TIMEOUT_MS,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.enqueueScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                timeoutMs = timeoutMs,
                executionId = executionId,
                queueTimeoutMs = queueTimeoutMs,
            ),
        )
    }

    private fun assumeEmbeddedScriptExecutionEnabled() {
        assumeTrue(
            "Build with -Pautojs.nodejs.embedded.script.enabled=true to run embedded lifecycle stress tests.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
    }

    private fun settleEmbeddedScriptServiceProcess() {
        context.stopService(Intent(context, NativeNodeEmbeddedScriptService::class.java))
        SystemClock.sleep(600L)
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) {
                return true
            }
            SystemClock.sleep(50L)
        }
        return condition()
    }

    private companion object {
        private const val STRESS_ITERATIONS = 100
        private const val SIMPLE_TIMEOUT_MS = 5000L
        private const val FAULT_TIMEOUT_MS = 900L
        private const val CONCURRENT_TIMEOUT_MS = 10000L
        private const val QUEUE_TEST_TIMEOUT_MS = 10000L
        private const val QUEUE_SHORT_TIMEOUT_MS = 500L
    }
}
