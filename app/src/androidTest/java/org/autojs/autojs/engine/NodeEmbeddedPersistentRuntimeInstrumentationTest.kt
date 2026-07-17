package org.autojs.autojs.engine

import android.content.ComponentCallbacks2
import android.os.Process
import android.os.SystemClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NodeEmbeddedPersistentRuntimeInstrumentationTest {

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
    fun sequentialExecutionsReuseProcessRuntimeButIsolateScriptState() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node runtime isolation gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node runtime isolation gate")

        val workDir = File(context.cacheDir, "node-persistent-isolation-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create persistent runtime isolation directory: $absolutePath" }
        }
        val stateModule = File(workDir, "state.cjs")
        val moduleSources = mapOf(stateModule.absolutePath to "module.exports = { count: 0 };")
        try {
            val first = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      globalThis.__autojs6R2GlobalLeak = "first";
                      process.env.AUTOJS6_R2_ENV_LEAK = "first";
                      const resolved = require.resolve("./state.cjs");
                      const state = require("./state.cjs");
                      state.count += 1;

                      const bridge = require("autojs6:bridge");
                      let bridgeRequestId = "";
                      bridge.__test.setTransport({
                        postMessage(message) {
                          bridgeRequestId = JSON.parse(message).id;
                        }
                      });
                      const pendingBridgeCall = bridge.callAutoJs("fake", "hold", [], { timeoutMs: 5000 }).then(
                        () => "unexpected",
                        (error) => error.code
                      );
                      await new Promise((resolve) => setImmediate(resolve));
                      const bridgePendingBeforeDestroy = bridge.__test.pendingCount();
                      bridge.__test.destroy("r2 first execution teardown");
                      const bridgeDestroyCode = await pendingBridgeCall;

                      const timerOrder = [];
                      await new Promise((resolve) => {
                        setImmediate(() => timerOrder.push("immediate"));
                        setTimeout(() => {
                          globalThis.__autojs6R2TimerLeak = "first-fired";
                          timerOrder.push("timeout");
                          resolve();
                        }, 10);
                      });
                      console.log("r2.first=" + [
                        globalThis.__autojs6R2GlobalLeak,
                        process.env.AUTOJS6_R2_ENV_LEAK,
                        process.env.AUTOJS6_R2_REQUEST_ENV,
                        state.count,
                        Boolean(require.cache[resolved]),
                        timerOrder.join("|"),
                        globalThis.__autojs6R2TimerLeak,
                        bridgePendingBeforeDestroy,
                        bridge.__test.pendingCount(),
                        bridgeDestroyCode,
                        bridgeRequestId.endsWith("-1")
                      ].join("/"));
                    })();
                """.trimIndent(),
                sourceName = File(workDir, "first.js").absolutePath,
                workingDirectory = workDir,
                env = mapOf("AUTOJS6_R2_REQUEST_ENV" to "first-request"),
                moduleSources = moduleSources,
            )

            NodeEmbeddedTestHarness.assertSucceeded("persistent runtime first isolation execution", first)
            assertTrue(
                first.stdout,
                first.stdout.contains(
                    "r2.first=first/first/first-request/1/true/immediate|timeout/first-fired/1/0/ERR_AUTOJS6_BRIDGE_PROCESS_DEAD/true",
                ),
            )
            assertExecutionTeardownClean("first", first)

            val second = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const resolved = require.resolve("./state.cjs");
                      const cacheBefore = Boolean(require.cache[resolved]);
                      const state = require("./state.cjs");
                      state.count += 1;

                      const bridge = require("autojs6:bridge");
                      const bridgePendingBefore = bridge.__test.pendingCount();
                      let bridgeRequestId = "";
                      bridge.__test.setTransport({
                        postMessage(message) {
                          const request = JSON.parse(message);
                          bridgeRequestId = request.id;
                          setImmediate(() => bridge.__test.receiveMessage(JSON.stringify({
                            id: request.id,
                            ok: true,
                            result: "fresh-bridge"
                          })));
                        }
                      });
                      const bridgeResult = await bridge.callAutoJs("fake", "fresh", [], { timeoutMs: 1000 });

                      const timerLeakBefore = typeof globalThis.__autojs6R2TimerLeak;
                      const timerOrder = [];
                      await new Promise((resolve) => {
                        setImmediate(() => timerOrder.push("immediate"));
                        setTimeout(() => {
                          timerOrder.push("timeout");
                          resolve();
                        }, 10);
                      });
                      console.log("r2.second=" + [
                        typeof globalThis.__autojs6R2GlobalLeak,
                        typeof process.env.AUTOJS6_R2_ENV_LEAK,
                        process.env.AUTOJS6_R2_REQUEST_ENV,
                        cacheBefore,
                        state.count,
                        timerLeakBefore,
                        timerOrder.join("|"),
                        bridgePendingBefore,
                        bridge.__test.pendingCount(),
                        bridgeResult,
                        bridgeRequestId.endsWith("-1")
                      ].join("/"));
                    })();
                """.trimIndent(),
                sourceName = File(workDir, "second.js").absolutePath,
                workingDirectory = workDir,
                env = mapOf("AUTOJS6_R2_REQUEST_ENV" to "second-request"),
                moduleSources = moduleSources,
            )

            NodeEmbeddedTestHarness.assertSucceeded("persistent runtime second isolation execution", second)
            assertTrue(
                second.stdout,
                second.stdout.contains(
                    "r2.second=undefined/undefined/second-request/false/1/undefined/immediate|timeout/0/0/fresh-bridge/true",
                ),
            )
            assertExecutionTeardownClean("second", second)
            assertPersistentRuntimeReused(first, second)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun adapterV1ExecutionsReuseProcessRuntimeAfterCleanTeardown() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node adapter_v1 reuse gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node adapter_v1 reuse gate")

        fun runAdapterProbe(label: String) = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = "\"nodejs\"; console.log(\"r2.adapter=$label/\" + process.pid);",
            sourceName = "<embedded-node-r2-adapter-$label.js>",
            runtimeAdapter = "adapter_v1",
        ).also { result ->
            NodeEmbeddedTestHarness.assertSucceeded("adapter_v1 $label", result)
            assertEquals(
                "adapter_v1 $label did not forward execution teardown health",
                "true",
                result.nativeValues["execution.teardown_clean"],
            )
            assertExecutionTeardownClean("adapter_v1 $label", result)
        }

        val first = runAdapterProbe("first")
        val second = runAdapterProbe("second")
        assertPersistentRuntimeReused(first, second)
    }

    @Test
    fun userExecutionWaitsForConcurrentColdPrewarmInitialization() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node prewarm race gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node prewarm race gate")

        val executor = Executors.newFixedThreadPool(2)
        val runtimeReadyRpcBlocked = CountDownLatch(1)
        val executionWaitingForRuntime = CountDownLatch(1)
        val allowRuntimeReadyRpc = CountDownLatch(1)
        NodeEmbeddedProcessSessionManager.beforeRuntimeReadyRpcForTests = {
            runtimeReadyRpcBlocked.countDown()
            check(allowRuntimeReadyRpc.await(5L, TimeUnit.SECONDS)) {
                "Timed out waiting to release the runtime-ready test gate."
            }
        }
        NodeEmbeddedProcessSessionManager.onRuntimeInitializationWaitForTests = {
            executionWaitingForRuntime.countDown()
        }
        try {
            val prewarmFuture = executor.submit<Triple<Boolean, Int, Map<String, String>>> {
                val prewarm = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 10_000L)
                Triple(prewarm.started, prewarm.pid, prewarm.nativeValues)
            }
            assertTrue(
                "cold prewarm never reached the runtime-ready RPC gate",
                runtimeReadyRpcBlocked.await(5L, TimeUnit.SECONDS),
            )
            val snapshot = NodeEmbeddedProcessSessionManager.sessionSnapshot(
                NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
            )
            assertEquals("prewarm did not own the session while runtime initialization was blocked: $snapshot", "busy", snapshot["embedded_script.process_session.state"])
            assertTrue(
                "runtime unexpectedly became healthy before the gated RPC: $snapshot",
                snapshot["process_runtime.state"] != "ready",
            )

            val executionFuture = executor.submit<NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult> {
                NodeEmbeddedTestHarness.runEmbeddedScript(
                    source = "\"nodejs\"; console.log(\"r2.prewarm-race=\" + process.pid);",
                    sourceName = "<embedded-node-r2-prewarm-race.js>",
                    processPoolSlot = NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
                    queueMode = NodeExecutionQueuePolicy.MODE_REJECT_WHEN_BUSY,
                )
            }
            assertTrue(
                "user execution never entered the runtime-initialization wait path",
                executionWaitingForRuntime.await(5L, TimeUnit.SECONDS),
            )
            assertFalse(
                "execution returned instead of waiting for in-flight runtime initialization",
                executionFuture.isDone,
            )
            allowRuntimeReadyRpc.countDown()
            val prewarm = prewarmFuture.get(15L, TimeUnit.SECONDS)
            val execution = executionFuture.get(15L, TimeUnit.SECONDS)

            assertTrue("concurrent cold prewarm failed: $prewarm", prewarm.first)
            NodeEmbeddedTestHarness.assertSucceeded("execution concurrent with cold prewarm", execution)
            assertEquals("execution did not reuse the prewarmed process", prewarm.second, execution.pid)
            assertEquals(
                "execution used a different process runtime generation than prewarm",
                prewarm.third["process_runtime.generation"],
                execution.nativeValues["process_runtime.generation"],
            )
        } finally {
            NodeEmbeddedProcessSessionManager.beforeRuntimeReadyRpcForTests = null
            NodeEmbeddedProcessSessionManager.onRuntimeInitializationWaitForTests = null
            allowRuntimeReadyRpc.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun explicitShutdownRetiresSessionBeforeNextExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node explicit shutdown gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node explicit shutdown gate")

        val first = runLifecycleProbe("before-explicit-shutdown")
        val firstGeneration = requireSessionGeneration("before explicit shutdown", first)
        assertTrue(
            "persistent process session did not shut down cleanly",
            NodeEmbeddedProcessSessionManager.shutdownAllAndAwait(
                context = context,
                reason = "instrumentation_explicit_shutdown",
            ),
        )
        NodeEmbeddedProcessSessionManager.reopenForUse()

        val second = runLifecycleProbe("after-explicit-shutdown")
        val secondGeneration = requireSessionGeneration("after explicit shutdown", second)
        assertTrue(
            "explicit shutdown did not create a new process session: first=$firstGeneration second=$secondGeneration",
            secondGeneration > firstGeneration,
        )
        assertEquals("new process runtime should initialize exactly once", "1", second.nativeValues["process_runtime.init_count"])
        assertEquals("new process runtime should start at its first execution", "1", second.nativeValues["process_runtime.execution_sequence"])
    }

    @Test
    fun trimMemoryRetiresIdleSessionBeforeNextExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node trim-memory gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node trim-memory gate")

        val first = runLifecycleProbe("before-trim-memory")
        val firstGeneration = requireSessionGeneration("before trim memory", first)
        NodeEmbeddedProcessSessionManager.onTrimMemory(context, ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)

        val second = runLifecycleProbe("after-trim-memory")
        val secondGeneration = requireSessionGeneration("after trim memory", second)
        assertTrue(
            "trim memory did not create a new process session: first=$firstGeneration second=$secondGeneration",
            secondGeneration > firstGeneration,
        )
        assertEquals("new process runtime should initialize exactly once", "1", second.nativeValues["process_runtime.init_count"])
        assertEquals("new process runtime should start at its first execution", "1", second.nativeValues["process_runtime.execution_sequence"])
    }

    @Test
    fun managerClosingRejectsPrewarmUntilShutdownCompletesAndManagerReopens() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node manager-closing lifecycle gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node manager-closing lifecycle gate")

        val first = runLifecycleProbe("before-manager-closing")
        val firstGeneration = requireSessionGeneration("before manager closing", first)
        val closingEpoch = NodeEmbeddedProcessSessionManager.beginClosing()
        assertTrue("session manager did not enter closing state", NodeEmbeddedProcessSessionManager.isClosing())

        val rejected = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 10_000L)
        assertFalse("prewarm unexpectedly started while the session manager was closing: $rejected", rejected.started)
        assertEquals("failed", rejected.status)
        assertTrue(
            "prewarm rejection did not identify the closing manager: $rejected",
            rejected.reason.contains("closing", ignoreCase = true),
        )
        assertTrue(
            "manager-closing shutdown did not complete",
            NodeEmbeddedProcessSessionManager.shutdownAllAndAwait(
                context = context,
                reason = "instrumentation_manager_closing",
                expectedClosingEpoch = closingEpoch,
            ),
        )

        NodeEmbeddedProcessSessionManager.reopenForUse()
        assertFalse("session manager remained closed after reopen", NodeEmbeddedProcessSessionManager.isClosing())
        val second = runLifecycleProbe("after-manager-reopen")
        val secondGeneration = requireSessionGeneration("after manager reopen", second)
        assertTrue(
            "manager reopen did not create a new process session: first=$firstGeneration second=$secondGeneration",
            secondGeneration > firstGeneration,
        )
    }

    @Test
    fun binderDeathRebuildsOnlyTheAffectedPersistentSlot() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("persistent Node Binder-death recovery gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("persistent Node Binder-death recovery gate")

        val prewarm = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 10_000L)
        assertTrue("prewarm failed before Binder-death gate: $prewarm", prewarm.started)
        assertTrue("prewarm did not use a remote service process: $prewarm", prewarm.pid > 0 && prewarm.pid != Process.myPid())
        val firstGeneration = prewarm.nativeValues["embedded_script.process_session.generation"]?.toLongOrNull()
        assertTrue("prewarm session generation is missing: $prewarm", firstGeneration != null)

        Process.killProcess(prewarm.pid)
        val deadline = SystemClock.elapsedRealtime() + 8_000L
        var binderFailureObserved = false
        var snapshot = NodeEmbeddedProcessSessionManager.sessionSnapshot(
            NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
        )
        while (SystemClock.elapsedRealtime() < deadline) {
            binderFailureObserved = binderFailureObserved ||
                    snapshot["embedded_script.process_session.state"] == "quarantined" ||
                    snapshot["embedded_script.process_session.last_failure"]
                        .orEmpty()
                        .contains("binder", ignoreCase = true)
            val retirementFinished =
                snapshot["embedded_script.process_session.retirement_in_flight"] == "false" &&
                        snapshot["embedded_script.process_session.process_exit_confirmed"] == "true"
            if (binderFailureObserved && retirementFinished) {
                break
            }
            SystemClock.sleep(50L)
            snapshot = NodeEmbeddedProcessSessionManager.sessionSnapshot(
                NodeEmbeddedProcessPoolPolicy.PRIMARY_SLOT_ID,
            )
        }
        assertTrue("manager never observed the killed slot's Binder failure: $snapshot", binderFailureObserved)
        assertEquals(
            "killed persistent slot retirement did not finish: $snapshot",
            "false",
            snapshot["embedded_script.process_session.retirement_in_flight"],
        )
        assertEquals(
            "killed persistent slot exit was not confirmed: $snapshot",
            "true",
            snapshot["embedded_script.process_session.process_exit_confirmed"],
        )
        val quarantineUntil = snapshot["embedded_script.process_session.quarantine_until_elapsed_ms"]
            ?.toLongOrNull()
            ?: 0L
        val remainingCooldown = quarantineUntil - SystemClock.elapsedRealtime()
        if (remainingCooldown > 0L) {
            SystemClock.sleep(remainingCooldown + 50L)
        }

        val rebuilt = runLifecycleProbe("after-binder-death")
        val rebuiltGeneration = requireSessionGeneration("after Binder death", rebuilt)
        assertTrue(
            "Binder death did not rebuild the affected session: before=$firstGeneration after=$rebuiltGeneration",
            rebuiltGeneration > firstGeneration!!,
        )
        assertTrue("Binder death unexpectedly reused the killed process: $rebuilt", rebuilt.pid != prewarm.pid)
        assertEquals("rebuilt process initialized Node more than once", "1", rebuilt.nativeValues["process_runtime.init_count"])
        assertEquals("rebuilt process did not start at execution sequence 1", "1", rebuilt.nativeValues["process_runtime.execution_sequence"])
    }

    private fun runLifecycleProbe(label: String): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                console.log("r2.lifecycle=$label/" + process.pid);
            """.trimIndent(),
            sourceName = "<embedded-node-r2-$label.js>",
        )
        NodeEmbeddedTestHarness.assertSucceeded(label, result)
        assertEquals("true", result.nativeValues["process_runtime.teardown_clean"])
        return result
    }

    private fun requireSessionGeneration(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): Long {
        val generation = result.nativeValues["embedded_script.process_session.generation"]?.toLongOrNull()
        assertTrue("$label session generation is missing: ${result.nativeValues}", generation != null)
        return generation!!
    }

    private fun assertExecutionTeardownClean(
        label: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertEquals("$label execution retained bridge resources", "0", result.nativeValues["embedded_script.bridge_resource.active_count"])
        assertEquals("$label execution retained bridge requests", "0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("$label native teardown was not clean", "true", result.nativeValues["process_runtime.teardown_clean"])
        assertEquals("$label execution teardown was not clean", "true", result.nativeValues["execution.teardown_clean"])
        assertEquals("$label service cleanup was not clean", "true", result.nativeValues["embedded_script.service.cleanup_clean"])
        assertEquals(
            "$label unexpectedly requested service retirement",
            "false",
            result.nativeValues["embedded_script.service.retirement_requested"],
        )
    }

    private fun assertPersistentRuntimeReused(
        first: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        second: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertEquals("sequential executions used different process ids", first.pid, second.pid)
        assertEquals(
            "sequential executions used different process sessions",
            first.nativeValues["embedded_script.process_session.generation"],
            second.nativeValues["embedded_script.process_session.generation"],
        )
        assertEquals(
            "sequential executions used different process runtime generations",
            first.nativeValues["process_runtime.generation"],
            second.nativeValues["process_runtime.generation"],
        )
        assertEquals("process runtime initialized more than once", "1", first.nativeValues["process_runtime.init_count"])
        assertEquals("process runtime initialized more than once", "1", second.nativeValues["process_runtime.init_count"])
        val firstSequence = first.nativeValues["process_runtime.execution_sequence"]?.toLongOrNull()
        val secondSequence = second.nativeValues["process_runtime.execution_sequence"]?.toLongOrNull()
        assertTrue("first execution sequence is missing: ${first.nativeValues}", firstSequence != null)
        assertEquals("process runtime execution sequence did not advance once", firstSequence!! + 1L, secondSequence)
        assertEquals("second execution did not reuse the process runtime", "true", second.nativeValues["process_runtime.reused"])
    }
}
