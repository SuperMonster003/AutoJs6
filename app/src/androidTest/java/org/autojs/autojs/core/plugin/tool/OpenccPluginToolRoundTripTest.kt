package org.autojs.autojs.core.plugin.tool

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
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
 * The OpenCC pilot of the plugin tool contract (MCP Server roadmap P12, release gate D43): the real 3-Hanzi CC plugin
 * declares `opencc_convert`, and this test runs it end to end through the catalog, the host's schema check, the
 * `plugins.tools` script API and the `plugin_tools` bridge module as MCP Server uses it. Every one of the 32 configs
 * must give what the host's own `opencc` module gives. Skipped when 3-Hanzi CC (2.1.0 or later) is not installed.
 *
 * zh-CN: 插件工具契约的 OpenCC 试点: 以真实的 3-Hanzi CC 插件走通目录, 宿主 schema 校验, `plugins.tools` 脚本 API 与 MCP 所用的
 * `plugin_tools` 桥接模块; 32 个 config 的结果须与宿主 opencc 模块一致. 未安装 3-Hanzi CC 2.1.0 及以上时跳过.
 */
class OpenccPluginToolRoundTripTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var root: File
    private var restore: () -> Unit = {}

    @Before
    fun setUp() {
        val installed = runCatching { context.packageManager.getPackageInfo(PLUGIN_PACKAGE, 0) }.getOrNull()
        assumeTrue("install 3-Hanzi CC 2.1.0 or later to run this test", installed != null && runCatching {
            context.packageManager.queryIntentServices(android.content.Intent("org.autojs.plugin.TOOL_PROVIDER").setPackage(PLUGIN_PACKAGE), 0).isNotEmpty()
        }.getOrDefault(false))
        restore = enable()
        root = File(context.cacheDir, "opencc-plugin-tool-${UUID.randomUUID()}").apply { mkdirs() }
        PluginToolRegistry.invalidate()
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
        restore()
        PluginToolRegistry.invalidate()
    }

    @Test
    fun scriptsGetWhatTheOpenccModuleGivesForEveryConfig() {
        val result = runScript(
            """
            let tool = plugins.tools.list().find(t => t.name === "opencc_convert");
            let configs = tool.inputSchema.properties.config.enum;
            let text = "汉字漢字软件軟體里面裏面鼠标打印机";
            let mismatches = [];
            configs.forEach(config => {
                let answer = plugins.tools.call("opencc_convert", { text: text, config: config });
                let expected = opencc.convert(text, config);
                if (answer.result.text !== expected || answer.result.config !== config || answer.text !== expected) {
                    mismatches.push(config + ": " + answer.result.text + " != " + expected);
                }
            });
            let defaulted = plugins.tools.call("opencc_convert", { text: "汉字" });
            let unknown = "";
            try { plugins.tools.call("opencc_convert", { text: "汉字", config: "S2T" }); } catch (e) { unknown = String(e.message || e); }
            let tooLong = "";
            try { plugins.tools.call("opencc_convert", { text: "字".repeat(16385) }); } catch (e) { tooLong = String(e.message || e); }
            files.write(MARKER, JSON.stringify({
                provider: tool.provider, trust: tool.trust, risk: tool.risk, count: configs.length, mismatches: mismatches,
                defaulted: defaulted.result, unknown: unknown, tooLong: tooLong,
            }));
            """,
        )
        assertEquals(PLUGIN_PACKAGE, result.getString("provider"))
        assertEquals("official", result.getString("trust"))
        assertEquals("readonly", result.getString("risk"))
        assertEquals(32, result.getInt("count"))
        assertEquals("[]", result.getJSONArray("mismatches").toString())
        assertEquals("漢字", result.getJSONObject("defaulted").getString("text"))
        assertEquals("s2t", result.getJSONObject("defaulted").getString("config"))
        assertTrue(result.getString("unknown"), result.getString("unknown").contains("$.config"))
        assertTrue(result.getString("tooLong"), result.getString("tooLong").contains("$.text"))
    }

    @Test
    fun mcpSessionsSeeAndRunTheOfficialToolWithTheModuleTokenAlone() {
        val policy = NodeBridgeModelPathPolicy(root, null) { false }
        val bridge = NodeBridgePluginToolsBridge(context, PluginToolPaths.of(policy), "mcp") { _ -> throw IllegalStateException("no screen") }
        try {
            val permissions = listOf(NodeBridgePermissionManifest.PLUGIN_TOOLS)
            val listed = result(call(bridge, "list", JSONArray(), permissions))
            val tool = listed.getJSONArray("tools").objects().single { it.getString("name") == "opencc_convert" }
            assertEquals("official", tool.getString("trust"))
            val provider = listed.getJSONArray("providers").objects().single { it.getString("packageName") == PLUGIN_PACKAGE }
            assertEquals("available", provider.getString("status"))
            assertEquals("opencc", provider.getString("shortName"))

            val args = JSONArray().put("opencc_convert").put(JSONObject().put("text", "鼠标和打印机").put("config", "s2twi"))
            val answer = result(call(bridge, "invoke", args, permissions))
            assertEquals("滑鼠和印表機", answer.getJSONObject("result").getString("text"))
            assertEquals("滑鼠和印表機", answer.getString("text"))
            assertEquals(PLUGIN_PACKAGE, answer.getString("provider"))
        } finally {
            bridge.destroy()
        }
    }

    private fun call(bridge: NodeBridgePluginToolsBridge, method: String, args: JSONArray, permissions: List<String>): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        bridge.dispatch(NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.PLUGIN_TOOLS, method, args.toString(), 20_000L, permissions)) {
            response = it
            latch.countDown()
        }
        assertTrue("plugin_tools.$method answered", latch.await(25, TimeUnit.SECONDS))
        return requireNotNull(response)
    }

    private fun result(response: NodeBridgeResponse): JSONObject {
        assertTrue("expected success, got ${response.error?.message}", response.ok)
        return JSONObject(requireNotNull(response.resultJson))
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
            StringScriptSource("opencc-plugin-tool", source),
            listener,
            ExecutionConfig(workingDirectory = root.absolutePath),
        )
        try {
            assertTrue("the script did not finish", completed.await(120, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("the script failed", it) }
            assertTrue("the script wrote no result", marker.isFile)
            return JSONObject(marker.readText()).also { marker.delete() }
        } finally {
            execution.engine?.forceStop()
        }
    }

    /** Enables and authorizes the plugin in the Plugin Center for the test and returns how to restore its state. */
    private fun enable(): () -> Unit {
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, PLUGIN_PACKAGE)
        val enable = context.getSharedPreferences("plugin_center_enable_state", Context.MODE_PRIVATE)
        val enableKey = "key_\$_enabled_plugin_\$_$PLUGIN_PACKAGE"
        val enableBefore = if (enable.contains(enableKey)) enable.getBoolean(enableKey, false) else null
        val authorization = context.getSharedPreferences("plugin_center_authorization", Context.MODE_PRIVATE)
        val keys = fingerprints.map { "key_\$_plugin_auth_\$_$PLUGIN_PACKAGE\$_$it" }
        val authorizationBefore = keys.associateWith { if (authorization.contains(it)) authorization.getBoolean(it, false) else null }
        fingerprints.forEach { PluginAuthorizationStore.grant(context, PLUGIN_PACKAGE, it) }
        PluginEnableStore.setEnabled(context, PLUGIN_PACKAGE, true)
        return {
            enable.edit().remove(enableKey).apply { enableBefore?.let { putBoolean(enableKey, it) } }.commit()
            authorization.edit().apply { authorizationBefore.forEach { (key, value) -> remove(key); value?.let { putBoolean(key, it) } } }.commit()
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private companion object {
        const val PLUGIN_PACKAGE = "io.github.supermonster003.autojs6.plugin.three.hanzi.cc"
    }
}
