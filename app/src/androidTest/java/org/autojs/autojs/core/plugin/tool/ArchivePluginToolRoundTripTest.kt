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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The archive pilot of the plugin tool contract (MCP Server roadmap P12, D53 - D56): the Archive Manager plugin lists,
 * reads and extracts an archive; extraction goes through a directory output, which AutoJs6 stages and commits into the
 * working directory, keeping files that are already there. Skipped unless the plugin with its tool service is
 * installed; needs AutoJs6 storage access.
 *
 * zh-CN: 插件工具契约的压缩包试点: Archive Manager 插件列出, 读取与解压压缩包; 解压经目录输出, 由 AutoJs6 暂存后提交到工作目录, 已有的文件保留.
 * 未安装带工具服务的插件时跳过; 需要 AutoJs6 有存储权限.
 */
class ArchivePluginToolRoundTripTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workingDirectory = File(Environment.getExternalStorageDirectory(), "Scripts")
    private val fixtures = File(workingDirectory, FIXTURES)

    @Before
    fun setUp() {
        val installed = runCatching {
            context.packageManager.queryIntentServices(Intent("org.autojs.plugin.TOOL_PROVIDER").setPackage(PLUGIN_PACKAGE), 0).isNotEmpty()
        }.getOrDefault(false)
        assumeTrue("install the Archive Manager plugin with its tool service to run this test", installed)
        fixtures.deleteRecursively()
        assumeTrue("AutoJs6 needs storage access to ${fixtures.path}", fixtures.mkdirs())
        ZipOutputStream(FileOutputStream(File(fixtures, "sample.zip"))).use { output ->
            listOf("readme.txt" to "Read me.", "docs/a.txt" to "alpha", "docs/b.txt" to "beta").forEach { (name, text) ->
                output.putNextEntry(ZipEntry(name))
                output.write(text.toByteArray())
                output.closeEntry()
            }
        }
        PluginToolRegistry.invalidate()
    }

    @After
    fun tearDown() {
        PluginToolRegistry.invalidate()
        fixtures.deleteRecursively()
    }

    @Test
    fun scriptsListReadAndExtractIntoADirectory() {
        val result = runScript(
            """
            let tools = plugins.tools.list().filter(t => t.shortName === "archive");
            let archive = "$FIXTURES/sample.zip";
            let listed = plugins.tools.call("archive_list", { archive: archive });
            let read = plugins.tools.call("archive_read", { archive: archive, path: "docs/a.txt" });
            let first = plugins.tools.call("archive_extract", { archive: archive }, { outputs: { files: "$FIXTURES/out" } });
            let again = plugins.tools.call("archive_extract", { archive: archive, paths: ["docs"] }, { outputs: { files: "$FIXTURES/out" } });
            let refused = "";
            try { plugins.tools.call("archive_extract", { archive: archive }); } catch (e) { refused = String(e.message || e); }
            files.write(files.path("$FIXTURES/$MARKER"), JSON.stringify({
                tools: tools.map(t => t.name + ":" + t.risk), outputs: tools.find(t => t.name === "archive_extract").outputs,
                listed: listed.result, read: read.result, first: first.result, firstOutputs: first.outputs, again: again.result, againOutputs: again.outputs, refused: refused,
            }));
            """,
        )
        assertEquals("[\"archive_list:readonly\",\"archive_read:readonly\",\"archive_extract:write\"]", result.getJSONArray("tools").toString())
        assertEquals("directory", result.getJSONArray("outputs").getJSONObject(0).getString("kind"))
        assertEquals(3, result.getJSONObject("listed").getInt("total"))
        assertEquals("alpha", result.getJSONObject("read").getString("text"))
        assertEquals(3, result.getJSONObject("first").getInt("filesExtracted"))
        val written = result.getJSONArray("firstOutputs").getJSONObject(0)
        assertEquals("directory", written.getString("kind"))
        assertEquals(3, written.getInt("files"))
        assertEquals("beta", File(fixtures, "out/docs/b.txt").readText())
        assertEquals(2, result.getJSONObject("again").getInt("kept"))
        assertEquals(0, result.getJSONArray("againOutputs").getJSONObject(0).getInt("files"))
        assertTrue("no staging directory is left", fixtures.listFiles()!!.none { it.name.startsWith(".out.") })
        assertTrue(result.getString("refused"), result.getString("refused").contains("directory output files"))
    }

    @Test
    fun mcpSessionsNeedTheFilesWriteTokenToExtract() {
        val policy = NodeBridgeModelPathPolicy(workingDirectory, null) { false }
        val bridge = NodeBridgePluginToolsBridge(context, PluginToolPaths.of(policy), "mcp") { _ -> throw IllegalStateException("no screen") }
        try {
            val list = JSONArray().put("archive_list").put(JSONObject().put("archive", "$FIXTURES/sample.zip"))
            assertTrue(call(bridge, list, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS, NodeBridgePermissionManifest.FILES)).ok)
            val extract = JSONArray().put("archive_extract").put(JSONObject().put("archive", "$FIXTURES/sample.zip"))
                .put(JSONObject().put("outputs", JSONObject().put("files", "$FIXTURES/mcp")))
            val denied = call(bridge, extract, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS, NodeBridgePermissionManifest.FILES))
            assertTrue(denied.error?.message, denied.error?.message.orEmpty().contains("files.write"))
            assertFalse(File(fixtures, "mcp").exists())
            val answer = call(bridge, extract, listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS, NodeBridgePermissionManifest.FILES, NodeBridgePermissionManifest.FILES_WRITE))
            assertTrue("expected success, got ${answer.error?.message}", answer.ok)
            assertEquals(3, JSONObject(answer.resultJson!!).getJSONArray("outputs").getJSONObject(0).getInt("files"))
            assertEquals("Read me.", File(fixtures, "mcp/readme.txt").readText())
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
            StringScriptSource("archive-plugin-tool", body.trimIndent()),
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
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.stack.archive"
        const val FIXTURES = "archive-plugin-tool-test"
        const val MARKER = "result.json"
    }
}
