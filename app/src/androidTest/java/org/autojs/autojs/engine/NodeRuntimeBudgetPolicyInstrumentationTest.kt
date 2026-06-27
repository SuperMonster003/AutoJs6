package org.autojs.autojs.engine

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
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

class NodeRuntimeBudgetPolicyInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var activeExecutionId: String? = null

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("runtime budget policy tests")
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun tearDown() {
        activeExecutionId?.let { executionId ->
            NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, executionId)
        }
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        activeExecutionId = null
    }

    @Test
    fun runtimeBudgetPolicyParsesSafeOverridesAndReportsBreaches() {
        val bridgeLimits = NodeBridgeLimits.defaults.copy(
            maxPendingBridgeCalls = 2,
            maxImageHandles = 1,
            maxNetworkRequests = 1,
            maxWebSocketConnections = 1,
        )
        val defaults = NodeRuntimeBudgetPolicy.fromRuntimeModuleOrWorkingDirectory(
            runtimeModuleSource = null,
            workingDirectory = null,
            executionMode = NodeExecutionModePolicy.MODE_ONE_SHOT,
            timeoutMs = 5_000L,
            bridgeLimits = bridgeLimits,
        )
        val policy = NodeRuntimeBudgetPolicy.fromJsonSources(
            projectJsonText = """
                {
                  "type": "node",
                  "node": {
                    "runtimeBudget": {
                      "maxTimers": 4,
                      "maxPendingBridgeCalls": 1,
                      "maxImageHandles": 1,
                      "maxNetworkRequests": 99,
                      "heartbeatIntervalMs": 250,
                      "heartbeatStaleMs": 1000
                    }
                  }
                }
            """.trimIndent(),
            packageJsonText = null,
            defaults = defaults,
        )

        assertEquals(4, policy.maxTimers)
        assertEquals(1, policy.maxPendingBridgeCalls)
        assertEquals(1, policy.maxImageHandles)
        assertEquals(1, policy.maxNetworkRequests)
        assertEquals(250L, policy.heartbeatIntervalMs)
        assertEquals(1000L, policy.heartbeatStaleMs)
        assertTrue(policy.sources.contains("project.json:node.runtimeBudget"))
        assertTrue(policy.warnings.any { it.contains("maxNetworkRequests") && it.contains("outside") })

        val nativeValues = mapOf(
            "embedded_script.pending_timer_count" to "2",
            "embedded_script.bridge_live_dispatch_pending_count" to "2",
            "embedded_script.bridge_live_dispatch_count" to "3",
            "embedded_script.bridge_live_dispatch_last_request_at_epoch_ms" to "2000",
            "embedded_script.bridge_live_dispatch_last_response_at_epoch_ms" to "0",
            "embedded_script.image_active_handle_count" to "2",
            "embedded_script.bridge_resource.active_count" to "2",
            "embedded_script.bridge_resource.active_types" to """{"UI_SESSION":2}""",
        )
        val completion = NodeRuntimeBudgetPolicy.completionNativeValues(policy, nativeValues, elapsedMs = 5_100L)
        assertEquals(NodeRuntimeBudgetPolicy.STATUS_BREACHED, completion["embedded_script.runtime_budget.status"])
        assertEquals("false", completion["embedded_script.runtime_budget.within_budget"])
        assertTrue((completion["embedded_script.runtime_budget.breach_count"]?.toIntOrNull() ?: 0) >= 2)

        val heartbeat = NodeRuntimeBudgetPolicy.heartbeatSnapshot(
            policy = policy,
            nativeValues = nativeValues,
            executionId = "runtime-budget-test",
            executionMode = NodeExecutionModePolicy.MODE_ONE_SHOT,
            lifecycleState = NodeEmbeddedRuntimeState.RUNNING.name,
            sequence = 7,
            startedAtEpochMs = 1000L,
            nowEpochMs = 5000L,
        )
        assertEquals(NodeRuntimeBudgetPolicy.HEARTBEAT_STALE, heartbeat.getString("status"))
        assertEquals(NodeRuntimeBudgetPolicy.EVENT_LOOP_STALE, heartbeat.getString("eventLoopResponsiveness"))
        assertEquals("bridge_request", heartbeat.getString("lastJsProgressSource"))
        assertEquals(2, heartbeat.getInt("activeResourceRegistryEntries"))
        assertFalse(heartbeat.getJSONObject("resourceUsage").getBoolean("withinBudget"))
    }

    @Test
    fun providerResourceQuotaRejectsOffendingResourceWithoutLeaking() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "runtime-budget-provider"),
            bridgeLimits = NodeBridgeLimits.defaults.copy(maxShellCommands = 0),
        )
        val response = dispatch(
            provider,
            module = "shell",
            method = "exec",
            argsJson = """["echo denied",{"timeoutMs":1000}]""",
            permissions = listOf("shell"),
        )

        assertFalse(response.ok)
        assertEquals(NodeRuntimeErrorCodes.BRIDGE_RESOURCE_LIMIT, response.error?.code)
        val diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource_limit_rejection_count"])
    }

    @Test
    fun interactiveHeartbeatStatusReportsBudgetAndResourceCounts() {
        val workingDirectory = File(context.cacheDir, "node-runtime-budget-heartbeat-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create runtime budget heartbeat directory: $absolutePath" }
        }
        val runtimeBudget = NodeRuntimeBudgetPolicy.fromRuntimeModuleOrWorkingDirectory(
            runtimeModuleSource = null,
            workingDirectory = null,
            executionMode = NodeExecutionModePolicy.MODE_INTERACTIVE_LONG_RUNNING,
            timeoutMs = 60_000L,
            bridgeLimits = NodeBridgeLimits.defaults,
        ).copy(
            heartbeatIntervalMs = 250L,
            heartbeatStaleMs = 1_000L,
        )
        val source = """
            const fs = require("fs");
            fs.writeFileSync("./heartbeat-alive.txt", "running");
            setInterval(() => {}, 1000);
        """.trimIndent()

        val session = NativeNodeEmbeddedScriptService.startInteractiveLongRunning(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = "interactive-runtime-budget-heartbeat.js",
                workingDirectory = workingDirectory.absolutePath,
                timeoutMs = 60_000L,
                runtimeModuleSources = mapOf(
                    NodeRuntimeBudgetPolicy.RUNTIME_MODULE_NAME to NodeRuntimeBudgetPolicy.runtimeModuleSource(runtimeBudget),
                ),
            ),
        )
        activeExecutionId = session.executionId
        assertTrue("interactive session failed to start: ${session.error}", session.started)

        val running = waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_RUNNING &&
                it.optJSONObject("nativeValues")?.optString("embedded_script.runtime_heartbeat.status").orEmpty().isNotBlank()
        }
        val activeResources = running.getJSONObject("activeResources")
        val nativeValues = running.getJSONObject("nativeValues")
        assertEquals(NodeRuntimeBudgetPolicy.HEARTBEAT_HEALTHY, nativeValues.getString("embedded_script.runtime_heartbeat.status"))
        assertEquals(NodeRuntimeBudgetPolicy.STATUS_WITHIN_BUDGET, nativeValues.getString("embedded_script.runtime_budget.status"))
        assertTrue(activeResources.getBoolean("runtimeBudgetWithinPolicy"))
        assertTrue(activeResources.getInt("runtimeHeartbeatSequence") >= 1)
        assertNotNull(JSONObject(nativeValues.getString("embedded_script.runtime_heartbeat.json")))

        assertTrue(NativeNodeEmbeddedScriptService.stopInteractiveLongRunning(context, session.executionId))
        val stopped = waitForStatus(session.executionId) {
            it.optString("state") == NodeInteractiveLongRunningStatusStore.STATE_STOPPED
        }
        assertTrue(stopped.optBoolean("stopRequested"))
        assertEquals(
            NodeRuntimeBudgetPolicy.STATUS_WITHIN_BUDGET,
            stopped.getJSONObject("nativeValues").getString("embedded_script.runtime_budget.status"),
        )
        activeExecutionId = null
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "runtime-budget-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = 5_000L,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
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
        return last ?: fail("status not available for $executionId").let {
            throw AssertionError("status not available for $executionId")
        }
    }

    private fun waitUntil(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) {
                return
            }
            SystemClock.sleep(WAIT_POLL_MS)
        }
        fail("Timed out waiting for $label")
    }

    private companion object {
        private const val WAIT_TIMEOUT_MS = 15_000L
        private const val WAIT_POLL_MS = 100L
    }
}
