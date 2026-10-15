package org.autojs.autojs.engine

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.mcp.McpHostCapabilityBroker
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** `image.readImage` decode budget for model-facing plugin sessions (MCP roadmap P11 D45). */
class NodeBridgeImageReadDownsampleInstrumentationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun mcpSessionsDecodeWithinTheEdgeBudgetAndReportTheScale() {
        val dir = fixtures("downsample-mcp")
        val provider = provider(dir, backend = "plugin", engineName = McpHostCapabilityBroker.ENGINE_NAME)
        try {
            val big = readImage(provider, "big.jpg")
            assertEquals(2050, big.getInt("width"))
            assertEquals(500, big.getInt("height"))
            assertEquals(4100, big.getInt("sourceWidth"))
            assertEquals(1000, big.getInt("sourceHeight"))
            assertEquals(0.5, big.getDouble("scale"), 1e-9)

            val small = readImage(provider, "small.png")
            assertEquals(300, small.getInt("width"))
            assertEquals(200, small.getInt("height"))
            assertEquals(300, small.getInt("sourceWidth"))
            assertEquals(1.0, small.getDouble("scale"), 1e-9)

            recycle(provider, big)
            recycle(provider, small)
            assertEquals("0", provider.nativeDiagnostics()["embedded_script.image_active_handle_count"])
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    @Test
    fun agentSessionsShareTheBudget() {
        val dir = fixtures("downsample-agent")
        val provider = provider(dir, backend = "plugin", engineName = ThreeStoveAgentIds.ENGINE)
        try {
            val big = readImage(provider, "big.jpg")
            assertEquals(2050, big.getInt("width"))
            assertEquals(0.5, big.getDouble("scale"), 1e-9)
            recycle(provider, big)
        } finally {
            provider.destroy(NodeBridgeError.processDead())
        }
    }

    @Test
    fun scriptSessionsKeepFullSizeDecodes() {
        val dir = fixtures("downsample-scripts")
        // The embedded Node family and the Node.js plugin's own sessions (backend "plugin", engine "nodejs") are user code.
        listOf(provider(dir, backend = "embedded", engineName = "nodejs"), provider(dir, backend = "plugin", engineName = "nodejs")).forEach { provider ->
            try {
                val big = readImage(provider, "big.jpg")
                assertEquals(4100, big.getInt("width"))
                assertEquals(1000, big.getInt("height"))
                assertFalse(big.has("scale"))
                assertFalse(big.has("sourceWidth"))
                recycle(provider, big)
            } finally {
                provider.destroy(NodeBridgeError.processDead())
            }
        }
    }

    private fun fixtures(name: String): File {
        val dir = File(context.cacheDir, "node-bridge-$name").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create fixture directory: $absolutePath" }
        }
        writeBitmap(File(dir, "big.jpg"), 4100, 1000, Bitmap.CompressFormat.JPEG)
        writeBitmap(File(dir, "small.png"), 300, 200, Bitmap.CompressFormat.PNG)
        return dir
    }

    private fun writeBitmap(file: File, width: Int, height: Int, format: Bitmap.CompressFormat) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565).apply { eraseColor(Color.rgb(40, 120, 200)) }
        try {
            file.outputStream().use { out -> check(bitmap.compress(format, 90, out)) { "Failed to write ${file.name}" } }
        } finally {
            bitmap.recycle()
        }
    }

    private fun provider(dir: File, backend: String, engineName: String): AndroidNodeBridgeCapabilityProvider {
        val info = NodeBridgeEngineInfo.fromContext(context, id = "${dir.name}-$backend-$engineName", sourceName = "readImage.cjs", cwd = dir.absolutePath)
            .copy(backend = backend, engineName = engineName)
        return AndroidNodeBridgeCapabilityProvider(context, info)
    }

    private fun readImage(provider: NodeBridgeCapabilityProvider, name: String): JSONObject {
        val response = dispatch(provider, "image", "readImage", JSONArray().put(name).toString(), listOf("image"), timeoutMs = 20_000L)
        assertTrue(response.toString(), response.ok)
        return JSONObject(response.resultJson.orEmpty())
    }

    private fun recycle(provider: NodeBridgeCapabilityProvider, handle: JSONObject) {
        val response = dispatch(provider, "image", "recycle", JSONArray().put(handle).toString(), listOf("image"))
        assertTrue(response.toString(), response.ok)
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
