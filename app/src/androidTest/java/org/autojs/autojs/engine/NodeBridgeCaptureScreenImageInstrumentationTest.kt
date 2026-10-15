package org.autojs.autojs.engine

import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.accessibility.AccessibilityService
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `accessibility.captureScreenImage` (MCP roadmap P11 D23): the screen as a provider-owned image handle. */
class NodeBridgeCaptureScreenImageInstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val capturePermissions = listOf("accessibility", "screen_capture", "image")
    private val TAG = "CaptureScreenImageTest"

    @Test
    fun registryDeclaresTheMethodAndItsCapabilities() {
        assertTrue(NodeBridgeModules.supports("accessibility", "captureScreenImage"))
        assertEquals(
            listOf(NodeBridgePermissionManifest.ACCESSIBILITY, NodeBridgePermissionManifest.SCREEN_CAPTURE, NodeBridgePermissionManifest.IMAGE),
            NodeBridgePermissionManifest.requiredCapabilitiesFor("accessibility", "captureScreenImage"),
        )
    }

    @Test
    fun captureRegistersTheScreenAsAnImageHandleOrReportsTheMissingPermission() {
        val provider = AndroidNodeBridgeCapabilityProvider(context, engineInfo("capture-screen-image"))
        try {
            awaitAccessibility(provider)
            val response = capture(provider)
            Log.i(TAG, "accessibility available=${accessibilityScreenshotAvailable()} response=$response")
            if (!accessibilityScreenshotAvailable()) {
                // No accessibility screenshot and no MediaProjection consent: the fallback names the missing permission.
                assertFalse(response.toString(), response.ok)
                assertEquals(response.toString(), NodeBridgeErrorCategory.PERMISSION_DENIED, response.error?.category)
                return
            }
            assertTrue(response.toString(), response.ok)
            val handle = JSONObject(response.resultJson.orEmpty())
            assertTrue(handle.getString("__autojs6ImageHandle").startsWith("image-"))
            assertEquals(handle.getString("__autojs6ImageHandle"), handle.getString("id"))
            assertTrue(handle.getInt("width") > 0)
            assertTrue(handle.getInt("height") > 0)
            assertEquals(NodeBridgeScreenCapture.SOURCE_ACCESSIBILITY, handle.getString("captureSource"))

            val recycle = dispatch(provider, "image", "recycle", JSONArray().put(handle).toString(), listOf("image"))
            assertTrue(recycle.toString(), recycle.ok)
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.image_active_handle_count"])
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    @Test
    fun captureCountsAgainstTheImageHandleLimit() {
        val provider = AndroidNodeBridgeCapabilityProvider(
            context,
            engineInfo("capture-screen-image-limit"),
            bridgeLimits = NodeBridgeLimits.defaults.copy(maxImageHandles = 1),
        )
        try {
            awaitAccessibility(provider)
            Log.i(TAG, "handle limit gate: accessibility available=${accessibilityScreenshotAvailable()}")
            assumeTrue("accessibility screenshot required for the handle limit gate", accessibilityScreenshotAvailable())
            val first = capture(provider)
            assertTrue(first.toString(), first.ok)
            val second = capture(provider)
            Log.i(TAG, "handle limit gate: first=$first second=$second")
            assertFalse(second.toString(), second.ok)
            assertEquals(second.toString(), NodeBridgeErrorCategory.RESOURCE_LIMIT, second.error?.category)

            val recycle = dispatch(provider, "image", "recycle", JSONArray().put(JSONObject(first.resultJson.orEmpty())).toString(), listOf("image"))
            assertTrue(recycle.toString(), recycle.ok)
            val third = capture(provider)
            assertTrue(third.toString(), third.ok)
            dispatch(provider, "image", "recycle", JSONArray().put(JSONObject(third.resultJson.orEmpty())).toString(), listOf("image"))
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.image_active_handle_count"])
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    private fun accessibilityScreenshotAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 30 && AccessibilityService.instance != null && AccessibilityService.registry.isOperational

    /** `am instrument` restarts the host process; give an enabled accessibility service time to reconnect before branching. */
    private fun awaitAccessibility(provider: NodeBridgeCapabilityProvider) {
        dispatch(provider, "accessibility", "ensureEnabled", "[]", listOf("accessibility"), timeoutMs = 10_000L)
    }

    private fun engineInfo(id: String): NodeBridgeEngineInfo =
        NodeBridgeEngineInfo.fromContext(context, id = id, sourceName = "$id.cjs", cwd = context.cacheDir.absolutePath)

    private fun capture(provider: NodeBridgeCapabilityProvider): NodeBridgeResponse =
        dispatch(provider, "accessibility", "captureScreenImage", "[]", capturePermissions, timeoutMs = 15_000L)

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
                id = "$module-$method-${System.nanoTime()}",
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
        assertTrue("Timed out waiting for $module.$method", latch.await(timeoutMs + 2_000L, TimeUnit.MILLISECONDS))
        return checkNotNull(response) { "Missing response for $module.$method" }
    }
}
