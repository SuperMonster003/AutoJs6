package org.autojs.autojs.core.plugin.tool

import android.content.Context
import android.content.Intent
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
 * The YOLO pilot of the plugin tool contract (MCP Server roadmap P12): the real YOLO NCNN plugin declares `yolo_detect`
 * with a model directory parameter (D49). The test needs the YOLO11n export pushed to `<shared storage>/Scripts/models/
 * yolo11n` (model.json, model.ncnn.param, model.ncnn.bin, bus.jpg) and AutoJs6 storage access; it is skipped otherwise,
 * since no model ships with the plugin.
 *
 * zh-CN: 插件工具契约的 YOLO 试点: 真实 YOLO NCNN 插件以目录参数声明 `yolo_detect`. 需预先把 YOLO11n 导出目录推到共享存储的
 * `Scripts/models/yolo11n` 并让 AutoJs6 有存储权限, 否则跳过 (插件不带模型).
 */
class YoloPluginToolRoundTripTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workingDirectory = File(Environment.getExternalStorageDirectory(), "Scripts")
    private val model = File(workingDirectory, "models/yolo11n")

    @Before
    fun setUp() {
        val installed = runCatching {
            context.packageManager.queryIntentServices(Intent("org.autojs.plugin.TOOL_PROVIDER").setPackage(PLUGIN_PACKAGE), 0).isNotEmpty()
        }.getOrDefault(false)
        assumeTrue("install the YOLO NCNN plugin with its tool service to run this test", installed)
        assumeTrue("push the YOLO11n export to ${model.path} to run this test", listOf("model.json", "model.ncnn.param", "model.ncnn.bin", "bus.jpg").all { File(model, it).canRead() })
        PluginToolRegistry.invalidate()
    }

    @After
    fun tearDown() {
        PluginToolRegistry.invalidate()
        File(workingDirectory, MARKER).delete()
    }

    @Test
    fun scriptsDetectWithAModelDirectoryAndReuseTheLoadedModel() {
        val result = runScript(
            """
            let tool = plugins.tools.list().find(t => t.name === "yolo_detect");
            let first = plugins.tools.call("yolo_detect", { image: "models/yolo11n/bus.jpg", model: "models/yolo11n" });
            let second = plugins.tools.call("yolo_detect", { image: "models/yolo11n/bus.jpg", model: "models/yolo11n", labels: ["bus"] });
            let missing = "";
            try { plugins.tools.call("yolo_detect", { image: "models/yolo11n/bus.jpg", model: "models/nothing-here" }); } catch (e) { missing = String(e.message || e); }
            files.write(files.path("$MARKER"), JSON.stringify({
                trust: tool.trust, directories: tool.directoryParameters, images: tool.imageParameters,
                first: first.result, second: second.result, text: first.text, missing: missing,
            }));
            """,
        )
        assertEquals("official", result.getString("trust"))
        assertEquals("[\"model\"]", result.getJSONArray("directories").toString())
        assertEquals("[\"image\"]", result.getJSONArray("images").toString())
        val first = result.getJSONObject("first")
        assertEquals(810, first.getJSONObject("image").getInt("width"))
        val labels = (0 until first.getJSONArray("detections").length()).map { first.getJSONArray("detections").getJSONObject(it).getString("label") }
        assertTrue(labels.toString(), "bus" in labels && "person" in labels)
        // Whether the first call loads the model depends on the tests before it; the second one always reuses it.
        val second = result.getJSONObject("second")
        assertEquals(true, second.getJSONObject("model").getBoolean("cached"))
        assertEquals(1, second.getInt("count"))
        assertTrue(result.getString("text"), result.getString("text").contains("bus"))
        assertTrue(result.getString("missing"), result.getString("missing").contains("is not a readable directory"))
    }

    @Test
    fun aTimedOutDetectionIsCancelledAndTheNextOneRuns() {
        // The first call loads the model, so the 30 ms limit of the second one expires during the inference itself.
        val result = runScript(
            """
            let args = { image: "models/yolo11n/bus.jpg", model: "models/yolo11n" };
            plugins.tools.call("yolo_detect", args);
            let timedOut = "";
            try { plugins.tools.call("yolo_detect", args, { timeoutMs: 30 }); } catch (e) { timedOut = String(e.message || e); }
            let after = plugins.tools.call("yolo_detect", args);
            files.write(files.path("$MARKER"), JSON.stringify({ timedOut: timedOut, after: after.result }));
            """,
        )
        assertTrue(result.getString("timedOut"), result.getString("timedOut").contains("did not answer within 30 ms"))
        val after = result.getJSONObject("after")
        assertTrue(after.toString(), after.getInt("count") >= 1)
        assertEquals(true, after.getJSONObject("model").getBoolean("cached"))
    }

    @Test
    fun mcpSessionsDetectWithTheFilesToken() {
        val policy = NodeBridgeModelPathPolicy(workingDirectory, null) { false }
        val bridge = NodeBridgePluginToolsBridge(context, PluginToolPaths.of(policy), "mcp") { _ -> throw IllegalStateException("no screen") }
        try {
            val args = JSONArray().put("yolo_detect").put(JSONObject().put("image", "models/yolo11n/bus.jpg").put("model", "models/yolo11n").put("confidence", 0.5))
            val denied = call(bridge, args, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS))
            assertTrue(denied.error?.message, denied.error?.message.orEmpty().contains("files"))
            val answer = call(bridge, args, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS, NodeBridgePermissionManifest.FILES))
            assertTrue("expected success, got ${answer.error?.message}", answer.ok)
            val result = JSONObject(answer.resultJson!!).getJSONObject("result")
            assertTrue(result.toString(), result.getInt("count") >= 1)
            val detections = result.getJSONArray("detections")
            assertTrue((0 until detections.length()).all { detections.getJSONObject(it).getDouble("confidence") >= 0.5 })
        } finally {
            bridge.destroy()
        }
    }

    private fun call(bridge: NodeBridgePluginToolsBridge, args: JSONArray, permissions: List<String>): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.PLUGIN_TOOLS, "invoke", args.toString(), 120_000L, permissions)) {
            response = it
            latch.countDown()
        }
        assertTrue("plugin_tools.invoke answered", latch.await(130, TimeUnit.SECONDS))
        return requireNotNull(response)
    }

    private fun runScript(body: String): JSONObject {
        val marker = File(workingDirectory, MARKER)
        marker.delete()
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
            StringScriptSource("yolo-plugin-tool", body.trimIndent()),
            listener,
            ExecutionConfig(workingDirectory = workingDirectory.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(240, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText())
        } finally {
            execution.engine?.forceStop()
        }
    }

    private companion object {
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.yolo.ncnn"
        const val MARKER = "yolo-plugin-tool-test.json"
    }
}
