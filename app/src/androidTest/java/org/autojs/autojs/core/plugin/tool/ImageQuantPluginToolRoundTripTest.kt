package org.autojs.autojs.core.plugin.tool

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.engine.NodeBridgeModelPathPolicy
import org.autojs.autojs.engine.NodeBridgeModules
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgePluginToolsBridge
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.autojs.engine.PluginToolPaths
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The image quantization pilot of the plugin tool contract (MCP Server roadmap P12): the Image Quantization plugin runs
 * `imagequant_quantize` in its own process (D41 / D51) and returns the PNG as a binary output, which AutoJs6 writes into
 * the working directory. Skipped unless the plugin with its tool service is installed; needs AutoJs6 storage access.
 *
 * zh-CN: 插件工具契约的图片量化试点: Image Quantization 插件在自身进程运行 `imagequant_quantize`, PNG 作为二进制输出由
 * AutoJs6 写入工作目录. 未安装带工具服务的插件时跳过; 需要 AutoJs6 有存储权限.
 */
class ImageQuantPluginToolRoundTripTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workingDirectory = File(Environment.getExternalStorageDirectory(), "Scripts")
    private val fixtures = File(workingDirectory, FIXTURES)

    @Before
    fun setUp() {
        val installed = runCatching {
            context.packageManager.queryIntentServices(Intent("org.autojs.plugin.TOOL_PROVIDER").setPackage(PLUGIN_PACKAGE), 0).isNotEmpty()
        }.getOrDefault(false)
        assumeTrue("install the Image Quantization plugin with its tool service to run this test", installed)
        fixtures.deleteRecursively()
        assumeTrue("AutoJs6 needs storage access to ${fixtures.path}", fixtures.mkdirs())
        File(fixtures, "gradient.png").outputStream().use { output ->
            val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply {
                setPixels(IntArray(160 * 120) { index ->
                    val x = index % 160
                    val y = index / 160
                    (0xff shl 24) or ((x * 255 / 160) shl 16) or ((y * 255 / 120) shl 8) or ((x + y) * 255 / 280)
                }, 0, 160, 0, 0, 160, 120)
            }
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            bitmap.recycle()
        }
        PluginToolRegistry.invalidate()
    }

    @After
    fun tearDown() {
        PluginToolRegistry.invalidate()
        fixtures.deleteRecursively()
    }

    @Test
    fun scriptsQuantizeAPathOrAnImageIntoAPng() {
        val result = runScript(
            """
            let tool = plugins.tools.list().find(t => t.name === "imagequant_quantize");
            let written = plugins.tools.call("imagequant_quantize", { image: "$FIXTURES/gradient.png", maxColors: 16 }, { outputs: { png: "$FIXTURES/out.png" } });
            let image = images.read(files.path("$FIXTURES/gradient.png"));
            let measured = plugins.tools.call("imagequant_quantize", { image: image, maxQuality: 80 });
            image.recycle();
            let refused = "";
            try { plugins.tools.call("imagequant_quantize", { image: "$FIXTURES/gradient.png", maxColors: 1 }); } catch (e) { refused = String(e.message || e); }
            files.write(files.path("$FIXTURES/$MARKER"), JSON.stringify({
                trust: tool.trust, risk: tool.risk, images: tool.imageParameters, outputs: tool.outputs.map(o => o.name),
                written: written.result, writtenOutputs: written.outputs, measured: measured.result, measuredOutputs: measured.outputs, refused: refused,
            }));
            """,
        )
        assertEquals("official", result.getString("trust"))
        assertEquals("write", result.getString("risk"))
        assertEquals("[\"image\"]", result.getJSONArray("images").toString())
        assertEquals("[\"png\"]", result.getJSONArray("outputs").toString())
        val written = result.getJSONObject("written")
        assertEquals(160, written.getInt("width"))
        val output = result.getJSONArray("writtenOutputs").getJSONObject(0)
        assertEquals("png", output.getString("name"))
        assertEquals("image/png", output.getString("mimeType"))
        val file = File(fixtures, "out.png")
        assertEquals(File(output.getString("path")).canonicalPath, file.canonicalPath)
        assertEquals(written.getLong("bytes"), file.length())
        assertEquals(output.getLong("bytes"), file.length())
        assertEquals(160, BitmapFactory.decodeFile(file.path).width)
        assertTrue("an image argument is sent as a PNG of the same size", result.getJSONObject("measured").getInt("height") == 120)
        assertEquals(0, result.getJSONArray("measuredOutputs").length())
        assertTrue(result.getString("refused"), result.getString("refused").contains("maxColors"))
    }

    @Test
    fun mcpSessionsNeedTheFilesWriteTokenForTheOutput() {
        val policy = NodeBridgeModelPathPolicy(workingDirectory, null) { false }
        val bridge = NodeBridgePluginToolsBridge(context, PluginToolPaths.of(policy), "mcp") { _ -> throw IllegalStateException("no screen") }
        try {
            val args = JSONArray().put("imagequant_quantize").put(JSONObject().put("image", "$FIXTURES/gradient.png"))
                .put(JSONObject().put("outputs", JSONObject().put("png", "$FIXTURES/mcp.png")))
            val denied = call(bridge, args, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS, NodeBridgePermissionManifest.FILES))
            assertTrue(denied.error?.message, denied.error?.message.orEmpty().contains("files.write"))
            assertFalse(File(fixtures, "mcp.png").exists())
            val answer = call(bridge, args, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS, NodeBridgePermissionManifest.FILES, NodeBridgePermissionManifest.FILES_WRITE))
            assertTrue("expected success, got ${answer.error?.message}", answer.ok)
            val outputs = JSONObject(answer.resultJson!!).getJSONArray("outputs")
            assertEquals(File(fixtures, "mcp.png").length(), outputs.getJSONObject(0).getLong("bytes"))
        } finally {
            bridge.destroy()
        }
    }

    private fun call(bridge: NodeBridgePluginToolsBridge, args: JSONArray, permissions: List<String>): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.PLUGIN_TOOLS, "invoke", args.toString(), 70_000L, permissions)) {
            response = it
            latch.countDown()
        }
        assertTrue("plugin_tools.invoke answered", latch.await(80, TimeUnit.SECONDS))
        return requireNotNull(response)
    }

    private fun runScript(body: String): JSONObject {
        val marker = File(fixtures, MARKER)
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            StringScriptSource("imagequant-plugin-tool", body.trimIndent()),
            listener,
            ExecutionConfig(workingDirectory = workingDirectory.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(180, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText())
        } finally {
            execution.engine?.forceStop()
        }
    }

    private companion object {
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.imagequant"
        const val FIXTURES = "imagequant-plugin-tool-test"
        const val MARKER = "result.json"
    }
}
