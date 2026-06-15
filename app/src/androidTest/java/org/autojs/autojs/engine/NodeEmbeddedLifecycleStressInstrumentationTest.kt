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
        assertTrue("$label should time out", result.timedOut)
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
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory,
                timeoutMs = timeoutMs,
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
    }
}
