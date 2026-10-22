package org.autojs.autojs.engine

import org.autojs.autojs.runtime.resource.RuntimeResourceScope
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NodeBridgeResourceRegistryInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun directRegistryClosesLeakedBridgeResourcesAndIsIdempotent() {
        var now = 1_000L
        val closed = mutableListOf<String>()
        val registry = NodeBridgeResourceRegistry("resource-direct") { now++ }
        registry.register(
            NodeBridgeResourceType.IMAGE_HANDLE,
            id = "image-leak-1",
            metadata = mapOf("width" to "10", "height" to "20"),
        ) { reason ->
            closed += "image:$reason"
        }
        val capturer = registry.register(
            NodeBridgeResourceType.SCREEN_CAPTURER,
            id = "screen-capturer-1",
        ) { reason ->
            closed += "screen:$reason"
        }
        registry.register(
            NodeBridgeResourceType.WEBSOCKET_CONNECTION,
            id = "websocket-leak-1",
        ) { reason ->
            closed += "websocket:$reason"
        }

        assertEquals(3, registry.activeCount())
        assertTrue(capturer.close("screen.stop"))
        assertFalse(capturer.close("screen.stop.again"))
        assertEquals(listOf("screen:screen.stop"), closed)

        val summary = registry.closeAll("execution.destroy")
        assertEquals(2, summary.leakedCount)
        assertEquals(2, summary.closedCount)
        assertEquals(mapOf("IMAGE_HANDLE" to 1, "WEBSOCKET_CONNECTION" to 1), summary.activeTypeCounts)
        assertEquals(
            listOf(
                "screen:screen.stop",
                "image:execution.destroy",
                "websocket:execution.destroy",
            ),
            closed,
        )
        assertEquals(0, registry.activeCount())

        val second = registry.closeAll("execution.destroy.again")
        assertEquals(0, second.leakedCount)
        assertEquals(0, second.closedCount)

        val diagnostics = registry.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("3", diagnostics["embedded_script.bridge_resource.created_count"])
        assertEquals("3", diagnostics["embedded_script.bridge_resource.closed_count"])
        assertEquals("2", diagnostics["embedded_script.bridge_resource.destroy_closed_count"])
        assertEquals("2", diagnostics["embedded_script.bridge_resource.leak_count"])
        assertEquals("0", diagnostics["embedded_script.bridge_resource.close_error_count"])
        assertEquals("[]", diagnostics["embedded_script.bridge_resource.dump_json"])
    }

    @Test
    fun directRegistryClosesResourcesRegisteredAfterDestroy() {
        val closed = mutableListOf<String>()
        val registry = NodeBridgeResourceRegistry("resource-late")
        registry.closeAll("execution.destroy")

        // memory-phase2 C6: an overlay posted before destroy registers afterwards and must not stay active.
        val late = registry.register(NodeBridgeResourceType.OVERLAY_SESSION, id = "overlay-late-1") { reason ->
            closed += "overlay:$reason"
        }
        assertEquals(listOf("overlay:execution.destroy"), closed)
        assertEquals(0, registry.activeCount())
        assertFalse(late.close("overlay.close"))
        assertEquals(listOf("overlay:execution.destroy"), closed)

        val diagnostics = registry.nativeDiagnostics()
        assertEquals("1", diagnostics["embedded_script.bridge_resource.created_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.destroy_closed_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.leak_count"])
    }

    @Test
    fun directRegistryHandsTheObjectsBehindResourcesToItsTracker() {
        // memory-phase2 T6 (P1.2): the object behind a resource reaches the runtime scope for the weak check after the script ends.
        val scope = RuntimeResourceScope("runtime@resource-tracked")
        val registry = NodeBridgeResourceRegistry("resource-tracked", tracker = { resource, name -> scope.track(resource, name) })
        val overlay = Any()
        registry.register(NodeBridgeResourceType.OVERLAY_SESSION, id = "overlay-tracked-1", resource = overlay)
        registry.register(NodeBridgeResourceType.WEBSOCKET_CONNECTION, id = "websocket-untracked-1")

        val tracked = scope.trackedReferences()
        assertEquals(listOf("node.overlay"), tracked.map { it.name })
        assertSame(overlay, tracked.single().reference.get())
        registry.closeAll("execution.destroy")
    }

    @Test
    fun androidProviderCleansJavaHandlesOnDestroyAndDoesNotLeakToNextExecution() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "resource-java-provider-1"),
            javaInteropEnabled = true,
        )
        val rect = dispatch(
            provider,
            module = "java",
            method = "new",
            argsJson = """[{"className":"android.graphics.Rect","args":[1,2,3,4]}]""",
            permissions = listOf("java"),
        )
        assertTrue(rect.toString(), rect.ok)
        assertTrue(JSONObject(rect.resultJson.orEmpty()).getString("__autojs6JavaHandle").startsWith("java-"))

        var diagnostics = provider.nativeDiagnostics()
        assertEquals("1", diagnostics["embedded_script.java_interop_handle_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals(1, activeTypeCount(diagnostics, NodeBridgeResourceType.JAVA_OBJECT))

        provider.destroy()
        diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.java_interop_handle_count"])
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.destroy_closed_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.leak_count"])

        val nextProvider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(context, id = "resource-java-provider-2"),
            javaInteropEnabled = true,
        )
        val nextDiagnostics = nextProvider.nativeDiagnostics()
        assertEquals("0", nextDiagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("0", nextDiagnostics["embedded_script.java_interop_handle_count"])
    }

    @Test
    fun androidProviderTracksWorkManagerTasksAndCancellation() {
        val workDir = File(context.cacheDir, "node-bridge-resource-work-manager").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge resource work_manager directory: $absolutePath" }
        }
        File(workDir, "main.cjs").writeText("""console.log("scheduled");""")
        val engineInfo = NodeBridgeEngineInfo.fromContext(
            context,
            id = "resource-work-manager-provider",
            sourceName = "main.cjs",
            cwd = workDir.absolutePath,
        )
        val ownerKey = NodeWorkManagerBridgeScheduler.ownerKey(engineInfo)
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            engineInfo,
        )

        val once = dispatch(
            provider,
            module = "work_manager",
            method = "scheduleOnce",
            argsJson = """[{"scriptPath":"main.cjs","delayMs":1000}]""",
            permissions = listOf("work_manager"),
        )
        assertTrue(once.toString(), once.ok)
        val onceId = jsonString(once.resultJson.orEmpty())
        assertTrue(onceId.startsWith("node-work-"))
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        assertEquals(1, activeTypeCount(provider.nativeDiagnostics(), NodeBridgeResourceType.WORK_MANAGER_TASK))

        val cancel = dispatch(
            provider,
            module = "work_manager",
            method = "cancel",
            argsJson = JSONArray().put(onceId).toString(),
            permissions = listOf("work_manager"),
        )
        assertTrue(cancel.toString(), cancel.ok)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.closed_count"])

        val periodic = dispatch(
            provider,
            module = "work_manager",
            method = "schedulePeriodic",
            argsJson = """[{"scriptPath":"main.cjs","intervalMs":900000}]""",
            permissions = listOf("work_manager"),
        )
        assertTrue(periodic.toString(), periodic.ok)
        val periodicId = jsonString(periodic.resultJson.orEmpty())
        provider.destroy()
        NodeWorkManagerBridgeScheduler.cancel(context, periodicId, ownerKey)
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.work_manager_registered_task_count"])
        assertEquals("0", provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"])
        assertEquals("1", provider.nativeDiagnostics()["embedded_script.bridge_resource.leak_count"])
    }

    @Test
    fun androidProviderTracksShellProcessUntilTimeoutCleanup() {
        val workDir = File(context.cacheDir, "node-bridge-resource-shell").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge resource shell directory: $absolutePath" }
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "resource-shell-provider",
                sourceName = "shell.cjs",
                cwd = workDir.absolutePath,
            ),
        )

        val response = dispatch(
            provider,
            module = "shell",
            method = "exec",
            argsJson = """["sleep 5",{"timeoutMs":50}]""",
            permissions = listOf("shell"),
            timeoutMs = 2_000L,
        )
        assertTrue(response.toString(), response.ok)
        val shellResult = JSONObject(response.resultJson.orEmpty())
        assertEquals(124, shellResult.getInt("code"))
        assertTrue(shellResult.getString("stderr").contains("timed out"))

        val diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.created_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.closed_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.max_active_count"])
    }

    @Test
    fun androidProviderDestroyClosesPendingPrivilegedShellResource() {
        val workDir = File(context.cacheDir, "node-bridge-resource-shell-destroy").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create bridge resource shell destroy directory: $absolutePath" }
        }
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            NodeBridgeEngineInfo.fromContext(
                context,
                id = "resource-shell-destroy-provider",
                sourceName = "shell-destroy.cjs",
                cwd = workDir.absolutePath,
            ),
        )
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "shell-destroy-${System.nanoTime()}",
                module = "shell",
                method = "exec",
                argsJson = """["sleep 5",{"timeoutMs":10000}]""",
                timeoutMs = 11_000L,
                permissions = listOf("shell"),
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }

        assertTrue(
            "Timed out waiting for shell resource to become active",
            waitUntil { provider.nativeDiagnostics()["embedded_script.bridge_resource.active_count"] == "1" },
        )
        provider.destroy(NodeBridgeError.processDead("test service destroy"))
        // Since the host shell streams (9d8c48c7d), destroy has the shell bridge close its processes before the
        // registry sweep, so the resource is released as "shell.closed" and is not counted by the sweep.
        var diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.bridge_resource.active_count"])
        assertEquals("0", diagnostics["embedded_script.shell.active_handle_count"])

        // The pending exec loses its process, so it reports the closed execution instead of an exit code.
        assertTrue("Timed out waiting for shell callback after destroy", latch.await(5, TimeUnit.SECONDS))
        val closed = checkNotNull(response)
        assertFalse(closed.toString(), closed.ok)
        assertEquals(closed.toString(), NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD, closed.error?.code)
        diagnostics = provider.nativeDiagnostics()
        assertEquals("0", diagnostics["embedded_script.shell_active_command_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.created_count"])
        assertEquals("1", diagnostics["embedded_script.bridge_resource.closed_count"])
    }

    private fun dispatch(
        provider: NodeBridgeCapabilityProvider,
        module: String,
        method: String,
        argsJson: String,
        permissions: List<String>,
        timeoutMs: Long = 5_000L,
    ): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        provider.dispatch(
            NodeBridgeRequest(
                id = "bridge-resource-${System.nanoTime()}",
                module = module,
                method = method,
                argsJson = argsJson,
                timeoutMs = timeoutMs,
                permissions = permissions,
            ),
        ) { bridgeResponse ->
            response = bridgeResponse
            latch.countDown()
        }
        assertTrue("Timed out waiting for $module.$method response", latch.await(5, TimeUnit.SECONDS))
        return checkNotNull(response) { "Missing $module.$method response" }
    }

    private fun activeTypeCount(diagnostics: Map<String, String>, type: NodeBridgeResourceType): Int {
        return JSONObject(diagnostics["embedded_script.bridge_resource.active_types"].orEmpty()).optInt(type.name, 0)
    }

    private fun jsonString(raw: String): String {
        return JSONTokener(raw).nextValue() as String
    }

    private fun waitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (predicate()) {
                return true
            }
            Thread.sleep(25L)
        }
        return predicate()
    }
}
