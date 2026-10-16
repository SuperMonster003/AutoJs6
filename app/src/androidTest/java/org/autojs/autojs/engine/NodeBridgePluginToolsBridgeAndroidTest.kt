package org.autojs.autojs.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginAuthorizationStore
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.autojs.core.plugin.center.PluginSignatureUtils
import org.autojs.autojs.core.plugin.tool.PluginToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The `plugin_tools` bridge module (MCP Server roadmap P12 host item 4) against the test-only provider
 * `test-apps/plugin-tool-provider-conformance`; skipped when it is not installed. The bridge runs with a model path
 * policy rooted at a temporary working directory and a synthetic screen, the provider is authorized as USER_GRANTED and
 * enabled for the test, and its Plugin Center state is restored afterwards.
 *
 * zh-CN: 以测试专用提供者验证 `plugin_tools` 桥接模块; 未安装时跳过. 桥接模块使用临时工作目录的路径策略与合成屏幕, 测试期间授权并启用
 * 该提供者, 结束后恢复其插件中心状态.
 */
class NodeBridgePluginToolsBridgeAndroidTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workingDirectory: File
    private lateinit var bridge: NodeBridgePluginToolsBridge
    private var restore: () -> Unit = {}

    @Before
    fun setUp() {
        assumeTrue("install test-apps/plugin-tool-provider-conformance (adb install -t) to run this test",
            runCatching { context.packageManager.getPackageInfo(FAKE_PACKAGE, 0) }.isSuccess)
        restore = authorizeFakeProvider()
        workingDirectory = File(context.cacheDir, "plugin-tools-bridge-test-${UUID.randomUUID()}").apply { mkdirs() }
        val policy = NodeBridgeModelPathPolicy(workingDirectory, null) { false }
        bridge = NodeBridgePluginToolsBridge(context, PluginToolPaths.of(policy), "mcp") { _ ->
            Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        }
        PluginToolRegistry.invalidate()
    }

    @After
    fun tearDown() {
        if (::bridge.isInitialized) bridge.destroy()
        if (::workingDirectory.isInitialized) workingDirectory.deleteRecursively()
        restore()
        PluginToolRegistry.invalidate()
    }

    @Test
    fun userGrantedToolsAreListedAndRunOnlyWithTheirToken() {
        val hidden = result(call("list", JSONArray(), BASE))
        assertFalse(hidden.getJSONArray("tools").names().contains("faketool_echo"))
        val provider = hidden.getJSONArray("providers").objects().single { it.getString("packageName") == FAKE_PACKAGE }
        assertEquals("user_granted", provider.getString("trust"))
        assertTrue(provider.getBoolean("toolsHidden"))

        val listed = result(call("list", JSONArray(), GRANTED))
        val echo = listed.getJSONArray("tools").objects().single { it.getString("name") == "faketool_echo" }
        assertEquals("readonly", echo.getString("risk"))
        assertEquals(5000, echo.getInt("timeoutMs"))
        assertEquals(listOf("image"), listed.getJSONArray("tools").objects().single { it.getString("name") == "faketool_inspect" }.getJSONArray("imageParameters").strings())
        assertTrue(provider.toString(), listed.getJSONArray("providers").objects().single { it.getString("packageName") == FAKE_PACKAGE }.getJSONArray("diagnostics").toString().contains("bad_schema"))

        val refused = call("invoke", JSONArray().put("faketool_echo").put(JSONObject().put("text", "hi")), BASE)
        assertFalse(refused.ok)
        assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, refused.error!!.category)
        assertTrue(refused.error!!.message.contains("plugin_tools.user_granted"))
    }

    @Test
    fun aCallReturnsTheProviderResult() {
        val answer = result(call("invoke", JSONArray().put("faketool_echo").put(JSONObject().put("text", "héllo")), GRANTED))
        assertEquals("faketool_echo", answer.getString("tool"))
        assertEquals(FAKE_PACKAGE, answer.getString("provider"))
        assertEquals("héllo", answer.getJSONObject("result").getString("text"))
        assertEquals("héllo", answer.getString("text"))
    }

    @Test
    fun argumentsAreCheckedAgainstTheSchemaFirst() {
        val missing = call("invoke", JSONArray().put("faketool_echo").put(JSONObject()), GRANTED)
        assertEquals(NodeBridgeErrorCategory.INVALID_REQUEST, missing.error!!.category)
        assertTrue(missing.error!!.message, missing.error!!.message.contains("$.text: is required"))
        val unknown = call("invoke", JSONArray().put("faketool_nothing").put(JSONObject()), GRANTED)
        assertTrue(unknown.error!!.message.contains("no plugin tool named faketool_nothing"))
    }

    @Test
    fun providerErrorsKeepTheirCode() {
        val failed = call("invoke", JSONArray().put("faketool_fail").put(JSONObject()), GRANTED)
        assertEquals(NodeBridgeErrorCategory.PROVIDER_FAILED, failed.error!!.category)
        assertTrue(failed.error!!.message.contains("failed with FAILED: the fake tool always fails"))
    }

    @Test
    fun aTimedOutCallIsCancelledAtTheProvider() {
        val invocationId = "timeout-${UUID.randomUUID()}"
        val timedOut = call("invoke", JSONArray().put("faketool_slow").put(JSONObject().put("sleepMs", 10_000)).put(JSONObject().put("invocationId", invocationId).put("timeoutMs", 400)), GRANTED)
        assertEquals(NodeBridgeErrorCategory.TIMEOUT, timedOut.error!!.category)
        val last = result(call("invoke", JSONArray().put("faketool_last_cancel").put(JSONObject()), GRANTED)).getJSONObject("result").getString("lastCancel")
        assertEquals("$invocationId:timeout", last)
    }

    @Test
    fun theCallerCanCancelARunningCall() {
        val invocationId = "cancel-${UUID.randomUUID()}"
        val latch = CountDownLatch(1)
        var answer: NodeBridgeResponse? = null
        bridge.dispatch(request("invoke", JSONArray().put("faketool_slow").put(JSONObject().put("sleepMs", 10_000)).put(JSONObject().put("invocationId", invocationId)), GRANTED)) {
            answer = it
            latch.countDown()
        }
        Thread.sleep(1500)
        val cancelled = result(call("cancel", JSONArray().put(JSONObject().put("invocationId", invocationId)), GRANTED))
        assertTrue(cancelled.getBoolean("cancelled"))
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        assertTrue(answer!!.error!!.message.contains("cancelled by the caller"))
        assertEquals("$invocationId:caller", result(call("invoke", JSONArray().put("faketool_last_cancel").put(JSONObject()), GRANTED)).getJSONObject("result").getString("lastCancel"))
        assertFalse(result(call("cancel", JSONArray().put(invocationId), GRANTED)).getBoolean("cancelled"))
    }

    @Test
    fun imagesAndFilesReachTheProviderAsDescriptors() {
        File(workingDirectory, "notes.txt").writeText("twelve bytes")
        val withoutToken = call("invoke", JSONArray().put("faketool_inspect").put(JSONObject().put("image", "screen")), GRANTED)
        assertTrue(withoutToken.error!!.message.contains("screen_capture"))

        val answer = result(call("invoke", JSONArray().put("faketool_inspect").put(JSONObject().put("image", "screen").put("file", "notes.txt")), GRANTED + listOf("screen_capture", "files")))
        val inputs = answer.getJSONObject("result").getJSONArray("inputs").objects()
        val screen = inputs.single { it.getString("name") == "image" }
        assertEquals("screen", screen.getString("source"))
        assertEquals("image/png", screen.getString("mimeType"))
        assertEquals(screen.getLong("declaredBytes"), screen.getLong("bytes"))
        val file = inputs.single { it.getString("name") == "file" }
        assertEquals(12, file.getInt("bytes"))
        assertEquals("text/plain", file.getString("mimeType"))

        val outside = call("invoke", JSONArray().put("faketool_inspect").put(JSONObject().put("file", "/system/build.prop")), GRANTED + "files")
        assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, outside.error!!.category)
    }

    @Test
    fun binaryOutputsAreWrittenIntoTheWorkingDirectory() {
        val permissions = GRANTED + "files.write"
        val args = JSONArray().put("faketool_emit").put(JSONObject().put("text", "output text")).put(JSONObject().put("outputs", JSONObject().put("data", "out/data.txt")))
        assertTrue(call("invoke", args, GRANTED).error!!.message.contains("files.write"))

        val first = result(call("invoke", args, permissions))
        val written = first.getJSONArray("outputs").getJSONObject(0)
        assertEquals("out/data.txt", written.getString("path"))
        assertEquals(11, written.getInt("bytes"))
        assertEquals("output text", File(workingDirectory, "out/data.txt").readText())
        assertTrue(workingDirectory.walk().none { it.name.endsWith(".part") })

        val again = call("invoke", args, permissions)
        assertTrue(again.error!!.message.contains("already exists"))
        val replaced = JSONArray().put("faketool_emit").put(JSONObject().put("text", "new")).put(JSONObject().put("outputs", JSONObject().put("data", "out/data.txt")).put("overwrite", true))
        result(call("invoke", replaced, permissions))
        assertEquals("new", File(workingDirectory, "out/data.txt").readText())

        val escape = JSONArray().put("faketool_emit").put(JSONObject().put("text", "x")).put(JSONObject().put("outputs", JSONObject().put("data", "../escape.txt")))
        assertEquals(NodeBridgeErrorCategory.PERMISSION_DENIED, call("invoke", escape, permissions).error!!.category)
        val undeclared = JSONArray().put("faketool_emit").put(JSONObject().put("text", "x")).put(JSONObject().put("outputs", JSONObject().put("thumbnail", "t.png")))
        assertTrue(call("invoke", undeclared, permissions).error!!.message.contains("declares no output named thumbnail"))
    }

    private fun call(method: String, args: JSONArray, permissions: List<String>, timeoutMs: Long = 20_000L): NodeBridgeResponse {
        val latch = CountDownLatch(1)
        var response: NodeBridgeResponse? = null
        bridge.dispatch(request(method, args, permissions, timeoutMs)) {
            response = it
            latch.countDown()
        }
        assertTrue("plugin_tools.$method answered", latch.await(timeoutMs + 5_000L, TimeUnit.MILLISECONDS))
        return requireNotNull(response)
    }

    private fun request(method: String, args: JSONArray, permissions: List<String>, timeoutMs: Long = 20_000L) =
        NodeBridgeRequest(UUID.randomUUID().toString(), NodeBridgeModules.PLUGIN_TOOLS, method, args.toString(), timeoutMs, permissions)

    private fun result(response: NodeBridgeResponse): JSONObject {
        assertTrue("expected success, got ${response.error?.message}", response.ok)
        return JSONObject(requireNotNull(response.resultJson))
    }

    private fun authorizeFakeProvider(): () -> Unit {
        val fingerprints = PluginSignatureUtils.getSha256Fingerprints(context, FAKE_PACKAGE)
        assertNotNull(fingerprints.firstOrNull())
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

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun JSONArray?.names(): List<String> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it).getString("name") }

    private companion object {
        const val FAKE_PACKAGE = "org.autojs.plugin.tool.fake"
        val BASE = listOf("plugin_tools")
        val GRANTED = listOf("plugin_tools", "plugin_tools.user_granted")
    }
}
