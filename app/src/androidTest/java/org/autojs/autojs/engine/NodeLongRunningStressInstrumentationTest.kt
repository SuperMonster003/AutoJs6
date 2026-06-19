package org.autojs.autojs.engine

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs6.BuildConfig
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NodeLongRunningStressInstrumentationTest {

    private val arguments = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var activeExecutionId: String? = null

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node long-running stress gate")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        NodeBridgeUiSessionStore.closeAll("long-running-stress-before")
    }

    @After
    fun tearDown() {
        activeExecutionId?.let { executionId ->
            NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, executionId)
        }
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        NodeBridgeUiSessionStore.closeAll("long-running-stress-after")
        activeExecutionId = null
    }

    @Test
    fun repeatedInteractiveStopsDrainTimersCheckpointsAndRecover() {
        repeat(stressIterations()) { index ->
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val workingDirectory = File(context.cacheDir, "node-long-running-stress-$index-${System.nanoTime()}").apply {
                deleteRecursively()
                check(mkdirs()) { "Failed to create long-running stress working directory: $absolutePath" }
            }
            val runningMarker = File(workingDirectory, "running-$index.txt")
            val stoppedMarker = File(workingDirectory, "stopped-$index.json")
            val source = """
                const fs = require("fs");
                const lifecycle = require("autojs6:lifecycle");
                let ticks = 0;
                const timer = setInterval(() => { ticks += 1; }, 25);
                const buffers = Array.from({ length: 16 }, (_, i) => Buffer.alloc(4096, i));
                lifecycle.onStop(async (reason) => {
                  await lifecycle.checkpoint({
                    iteration: $index,
                    ticks,
                    buffers: buffers.length,
                    reason
                  }, { reason: "stress.stop" });
                  clearInterval(timer);
                  fs.writeFileSync("./stopped-$index.json", JSON.stringify({ reason, ticks }));
                });
                fs.writeFileSync("./running-$index.txt", "running");
            """.trimIndent()

            val session = NativeNodeEmbeddedScriptService.startInteractiveLongRunning(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = source,
                    sourceName = "long-running-stress-$index.js",
                    workingDirectory = workingDirectory.absolutePath,
                    timeoutMs = 60_000L,
                ),
            )
            activeExecutionId = session.executionId
            assertTrue("long-running stress session failed to start: ${session.error}", session.started)
            waitForRunningStatus(session.executionId)
            waitUntil("running marker $index") { runningMarker.isFile }

            assertTrue(NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, session.executionId))
            waitUntil("stopped marker $index") { stoppedMarker.isFile }
            val stopped = waitForStatus(session.executionId) {
                it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_STOPPED
            }
            assertTrue(stopped.optBoolean("stopRequested"))
            assertFalse(stopped.optBoolean("automaticRestart", true))
            val checkpoint = stopped.getJSONObject("checkpoint")
            assertTrue("checkpoint status=$checkpoint", checkpoint.getBoolean("exists"))
            assertEquals("stress.stop", checkpoint.getString("reason"))
            val activeResources = stopped.getJSONObject("activeResources")
            assertEquals(
                "resource registry should be empty after stopped status: $stopped",
                0,
                activeResources.optInt("resourceRegistryActiveCount", 0),
            )
            activeExecutionId = null
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

            val recovery = NativeNodeEmbeddedScriptService.runScript(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = "console.log('long-running stress recovery $index');",
                    sourceName = "long-running-stress-recovery-$index.js",
                    workingDirectory = workingDirectory.absolutePath,
                    timeoutMs = 10_000L,
                ),
            )
            NodeEmbeddedTestHarness.assertSucceeded("long-running stress recovery $index", recovery)
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    @Test
    fun interactiveProviderDestroyClosesOwnedResourcesWithoutActiveLeaks() {
        val workDir = File(context.cacheDir, "node-long-running-provider-stress-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create long-running provider stress directory: $absolutePath" }
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "long-running-provider-stress",
                sourceName = "long-running-provider-stress.cjs",
                cwd = workDir.absolutePath,
                executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
                launchSurface = NodeExecutionModePolicy.LAUNCH_SURFACE_INTERACTIVE_SESSION,
            ),
            bridgeLimits = NodeBridgeLimits.defaults.copy(
                maxShellCommands = 1,
                maxWebSocketConnections = 1,
            ),
        )
        val uiHandles = mutableListOf<String>()
        val shellResponseRef = AtomicReference<NodeBridgeResponse?>()
        val shellLatch = CountDownLatch(1)

        try {
            val notification = dispatch(
                provider = provider,
                module = "notifications",
                method = "notify",
                argsJson = """[{"id":840000,"title":"AutoJs6 Node","text":"Long-running stress","channel":"node-long-running-stress","ongoing":true}]""",
                permissions = listOf("notifications"),
            )
            if (notification.ok) {
                assertEquals(1, activeTypeCount(provider, NodeBridgeResourceType.NOTIFICATION))
            } else {
                assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, notification.error?.code)
            }

            val sensor = dispatch(
                provider = provider,
                module = "sensors",
                method = "subscribe",
                argsJson = """[{"type":"accelerometer","samplingIntervalMs":250,"maxQueueSize":1,"timeoutMs":1000}]""",
                permissions = listOf("sensors"),
            )
            if (sensor.ok) {
                assertEquals(1, activeTypeCount(provider, NodeBridgeResourceType.SENSOR_SUBSCRIPTION))
            } else {
                assertEquals(NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED, sensor.error?.code)
            }

            val ui = dispatch(
                provider = provider,
                module = "ui",
                method = "showLayout",
                argsJson = """[{"type":"Column","children":[{"type":"Text","id":"title","text":"Long-running stress"},{"type":"Button","id":"stop","text":"Stop"}]}]""",
                permissions = listOf("ui"),
            )
            assertTrue(ui.toString(), ui.ok)
            val uiHandle = JSONObject(ui.resultJson.orEmpty()).getString("id")
            uiHandles += uiHandle
            assertTrue("UI session was not registered", waitUntil { NodeBridgeUiSessionStore.get(uiHandle) != null })
            assertEquals(1, activeTypeCount(provider, NodeBridgeResourceType.UI_SESSION))

            provider.dispatch(
                NodeBridgeRequest(
                    id = "long-running-shell-${System.nanoTime()}",
                    module = "shell",
                    method = "exec",
                    argsJson = """["sleep 5",{"timeoutMs":10000}]""",
                    timeoutMs = 11_000L,
                    permissions = listOf("shell"),
                ),
            ) { response ->
                shellResponseRef.set(response)
                shellLatch.countDown()
            }
            assertTrue(
                "Timed out waiting for long-running shell resource",
                waitUntil { activeTypeCount(provider, NodeBridgeResourceType.SHELL_EXECUTION) == 1 },
            )

            if (!BuildConfig.NODEJS_NETWORK_EXPERIMENTAL_ENABLED) {
                val websocket = dispatch(
                    provider = provider,
                    module = "websocket",
                    method = "connect",
                    argsJson = """[{"url":"wss://example.invalid/long-running-stress","timeoutMs":1000}]""",
                    permissions = listOf("network"),
                )
                assertFalse(websocket.toString(), websocket.ok)
                assertEquals(NodeRuntimeErrorCodes.NETWORK_DISABLED, websocket.error?.code)
                assertEquals("0", provider.nativeDiagnostics()["embedded_script.websocket_active_connection_count"])
            }

            assertTrue(
                "Expected at least one active provider-owned resource before destroy: ${provider.nativeDiagnostics()}",
                provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"]!!.toInt() > 0,
            )

            provider.destroy(NodeBridgeError.processDead("long-running stress destroy"))
            assertTrue(
                "Timed out waiting for provider resources to clear: ${provider.nativeDiagnostics()}",
                waitUntil { provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"] == "0" },
            )
            assertTrue("Timed out waiting for shell callback after destroy", shellLatch.await(5, TimeUnit.SECONDS))
            assertNotNull("Missing shell response after provider destroy", shellResponseRef.get())
            assertTrue("UI sessions were not closed by provider destroy", waitUntil { uiHandles.all { NodeBridgeUiSessionStore.get(it) == null } })

            val diagnostics = provider.nativeDiagnostics()
            assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
            assertEquals("{}", diagnostics["embedded_script.bridge_resource.active_types"])
            assertEquals("0", diagnostics["embedded_script.bridge_resource.close_error_count"])
            assertEquals("0", diagnostics["embedded_script.shell_active_command_count"])
            assertEquals("0", diagnostics["embedded_script.websocket_active_connection_count"])
        } finally {
            provider.destroy(NodeBridgeError.processDead("long-running stress cleanup"))
            NodeBridgeUiSessionStore.closeAll("long-running-stress-provider-cleanup")
        }
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        awaitSeconds: Long = 5L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        val responseRef = AtomicReference<NodeBridgeResponse?>()
        provider.dispatch(
            NodeBridgeRequest(
                id = "$module-$method-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = permissions,
            ),
        ) { response ->
            responseRef.set(response)
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(awaitSeconds, TimeUnit.SECONDS))
        return checkNotNull(responseRef.get()) { "Missing $module.$method response" }
    }

    private fun waitForStatus(
        executionId: String,
        predicate: (JSONObject) -> Boolean,
    ): JSONObject {
        var last: JSONObject? = null
        waitUntil("status predicate for $executionId") {
            last = NativeNodeEmbeddedScriptService.interactiveLongRunningStatus(context, executionId)
            last?.let(predicate) == true
        }
        return last ?: failStatus("status not available for $executionId")
    }

    private fun waitForRunningStatus(executionId: String): JSONObject {
        var last: JSONObject? = null
        waitUntil("running status for $executionId") {
            last = NativeNodeEmbeddedScriptService.interactiveLongRunningStatus(context, executionId)
            val state = last?.optString("state").orEmpty()
            if (state in TERMINAL_STATES) {
                fail("interactive runtime reached terminal state before running: $last")
            }
            state == NodeInteractiveLongRunningStatusStore.STATE_RUNNING
        }
        return last ?: failStatus("running status not available for $executionId")
    }

    private fun activeTypeCount(provider: AndroidNodeBridgeCapabilityProvider, type: NodeBridgeResourceType): Int {
        return JSONObject(provider.nativeDiagnostics()["embedded_script.bridge_resource.active_types"].orEmpty()).optInt(type.name, 0)
    }

    private fun waitUntil(label: String, predicate: () -> Boolean) {
        if (!waitUntil(predicate = predicate)) {
            fail("Timed out waiting for $label")
        }
    }

    private fun waitUntil(timeoutMs: Long = WAIT_TIMEOUT_MS, predicate: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) {
                return true
            }
            SystemClock.sleep(WAIT_POLL_MS)
        }
        return predicate()
    }

    private fun failStatus(message: String): JSONObject {
        fail(message)
        throw AssertionError(message)
    }

    private fun stressIterations(): Int =
        (arguments.getString(ARG_ITERATIONS)?.toIntOrNull() ?: DEFAULT_ITERATIONS).coerceIn(1, MAX_ITERATIONS)

    private companion object {
        private const val WAIT_TIMEOUT_MS = 20_000L
        private const val WAIT_POLL_MS = 100L
        private const val DEFAULT_ITERATIONS = 3
        private const val MAX_ITERATIONS = 12
        private const val ARG_ITERATIONS = "autojs.nodejs.longRunningStress.iterations"
        private val TERMINAL_STATES = setOf(
            NodeInteractiveLongRunningStatusStore.STATE_STOPPED,
            NodeInteractiveLongRunningStatusStore.STATE_FAILED,
            NodeInteractiveLongRunningStatusStore.STATE_TIMED_OUT,
        )
    }
}
