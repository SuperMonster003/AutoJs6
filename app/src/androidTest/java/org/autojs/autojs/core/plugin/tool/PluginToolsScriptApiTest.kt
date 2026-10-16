package org.autojs.autojs.core.plugin.tool

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.StringScriptSource
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
 * The script API `plugins.tools` (MCP Server roadmap P12 host item 5) run from real scripts against the test-only provider
 * `test-apps/plugin-tool-provider-conformance`; skipped when it is not installed. Scripts may use USER_GRANTED tools
 * without any token (D34).
 *
 * zh-CN: 以真实脚本调用 `plugins.tools`, 对象为测试专用提供者; 未安装时跳过. 脚本无需令牌即可使用 USER_GRANTED 工具.
 */
class PluginToolsScriptApiTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private var restore: () -> Unit = {}

    @Before
    fun setUp() {
        assumeTrue("install test-apps/plugin-tool-provider-conformance (adb install -t) to run this test",
            runCatching { context.packageManager.getPackageInfo(FAKE_PACKAGE, 0) }.isSuccess)
        restore = authorize()
        root = File(context.cacheDir, "plugin-tools-script-${UUID.randomUUID()}").apply { mkdirs() }
        PluginToolRegistry.invalidate()
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
        restore()
        PluginToolRegistry.invalidate()
    }

    @Test
    fun listCallAndProvidersWork() {
        val result = runScript(
            """
            let tools = plugins.tools.list();
            let echo = tools.find(t => t.name === "faketool_echo");
            let answer = plugins.tools.call("faketool_echo", { text: "from a script" });
            let provider = plugins.tools.providers().find(p => p.packageName === "$FAKE_PACKAGE");
            let failure = "";
            try { plugins.tools.call("faketool_fail"); } catch (e) { failure = String(e.message || e); }
            let invalid = "";
            try { plugins.tools.call("faketool_echo", {}); } catch (e) { invalid = String(e.message || e); }
            files.write(MARKER, JSON.stringify({
                names: tools.map(t => t.name), risk: echo.risk, trust: echo.trust,
                text: answer.result.text, summary: answer.text, tool: answer.tool,
                status: provider.status, hidden: !!provider.toolsHidden, failure: failure, invalid: invalid,
            }));
            """,
        )
        assertTrue(result.getJSONArray("names").toString().contains("faketool_emit"))
        assertEquals("readonly", result.getString("risk"))
        assertEquals("user_granted", result.getString("trust"))
        assertEquals("from a script", result.getString("text"))
        assertEquals("from a script", result.getString("summary"))
        assertEquals("faketool_echo", result.getString("tool"))
        assertEquals("available", result.getString("status"))
        assertEquals(false, result.getBoolean("hidden"))
        assertTrue(result.getString("failure"), result.getString("failure").contains("failed with FAILED"))
        assertTrue(result.getString("invalid"), result.getString("invalid").contains("$.text: is required"))
    }

    @Test
    fun imagesFilesAndOutputsUseScriptPaths() {
        File(root, "input.txt").writeText("seven b")
        File(root, "pic.png").outputStream().use { android.graphics.Bitmap.createBitmap(16, 8, android.graphics.Bitmap.Config.ARGB_8888).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val result = runScript(
            """
            let image = images.read("pic.png");
            let inspected = plugins.tools.call("faketool_inspect", { image: image, file: "input.txt" });
            let emitted = plugins.tools.call("faketool_emit", { text: "written by a script" }, { outputs: { data: "out/emit.txt" } });
            files.write(MARKER, JSON.stringify({ inputs: inspected.result.inputs, output: emitted.outputs[0] }));
            """,
        )
        val inputs = result.getJSONArray("inputs")
        val names = (0 until inputs.length()).associate { inputs.getJSONObject(it).getString("name") to inputs.getJSONObject(it) }
        assertEquals("image/png", names.getValue("image").getString("mimeType"))
        assertTrue(names.getValue("image").getInt("bytes") > 0)
        assertEquals(7, names.getValue("file").getInt("bytes"))
        assertEquals(19, result.getJSONObject("output").getInt("bytes"))
        assertEquals("written by a script", File(root, "out/emit.txt").readText())
        assertTrue("the PNG copies are removed", File(context.cacheDir, "plugin-tool-script-images").listFiles().isNullOrEmpty())
    }

    @Test
    fun callAsyncResolvesAndRejects() {
        val result = runScript(
            """
            plugins.tools.callAsync("faketool_echo", { text: "async" })
                .then(answer => plugins.tools.callAsync("faketool_fail").then(() => ({ text: answer.result.text, rejected: false }), e => ({ text: answer.result.text, rejected: String(e.message || e) })))
                .then(outcome => files.write(MARKER, JSON.stringify(outcome)));
            """,
        )
        assertEquals("async", result.getString("text"))
        assertTrue(result.getString("rejected"), result.getString("rejected").contains("failed with FAILED"))
    }

    private fun runScript(body: String): JSONObject {
        val marker = File(root, "marker.json")
        val source = "var MARKER = " + JSONObject.quote(marker.path) + ";\n" + body.trimIndent()
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
            StringScriptSource("plugin-tools-script-api", source),
            listener,
            ExecutionConfig(workingDirectory = root.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(60, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText()).also { marker.delete() }
        } finally {
            execution.engine?.forceStop()
        }
    }

    private fun authorize(): () -> Unit {
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, FAKE_PACKAGE)
        val enable = context.getSharedPreferences("plugin_center_enable_state", Context.MODE_PRIVATE)
        val enableKey = "key_\$_enabled_plugin_\$_$FAKE_PACKAGE"
        val enableBefore = if (enable.contains(enableKey)) enable.getBoolean(enableKey, false) else null
        val authorization = context.getSharedPreferences("plugin_center_authorization", Context.MODE_PRIVATE)
        val keys = fingerprints.map { "key_\$_plugin_auth_\$_$FAKE_PACKAGE\$_$it" }
        val authorizationBefore = keys.associateWith { if (authorization.contains(it)) authorization.getBoolean(it, false) else null }
        fingerprints.forEach { PluginAuthorizationStore.grant(context, FAKE_PACKAGE, it) }
        PluginEnableStore.setEnabled(context, FAKE_PACKAGE, true)
        return {
            enable.edit().remove(enableKey).apply { enableBefore?.let { putBoolean(enableKey, it) } }.commit()
            authorization.edit().apply { authorizationBefore.forEach { (key, value) -> remove(key); value?.let { putBoolean(key, it) } } }.commit()
        }
    }

    private companion object {
        const val FAKE_PACKAGE = "org.autojs.plugin.tool.fake"
    }
}
