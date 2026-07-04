package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeRuntimePluginBridgeInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun pluginRuntimeUsesLiveHostBridgeForDeviceCall() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime plugin live bridge smoke")
        val runtimeInfo = runBlocking {
            runCatching { NodeJsRuntimePluginHost.probe(context) }.getOrNull()
        }
        assumeTrue(
            "Install, enable, and authorize a Node.js runtime plugin to run the live bridge smoke.",
            runtimeInfo != null,
        )
        val capabilities = runtimeInfo
            ?.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES)
            .orEmpty()
        assumeTrue(
            "Node.js runtime plugin must advertise ${NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE}.",
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE in capabilities,
        )

        val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("device"))
        val workDir = File(context.cacheDir, "node-runtime-plugin-live-device").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin live bridge working directory: $absolutePath" }
        }
        val result = NodeJsRuntimePluginBridge.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = """
                    "nodejs";
                    (async function main() {
                      const device = require("device");
                      const screenOn = await device.isScreenOn({ timeoutMs: 1500 });
                      console.log("plugin.live.device.screenOnType=" + typeof screenOn);
                      console.log("plugin.live.device.screenOnValue=" + screenOn);
                    })();
                """.trimIndent(),
                sourceName = "<node-runtime-plugin-live-device.js>",
                workingDirectory = workDir.absolutePath,
                sandboxRoot = workDir.absolutePath,
                timeoutMs = 10_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            ),
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node runtime plugin live bridge device smoke", result)
        assertTrue(result.stdout.contains("plugin.live.device.screenOnType=boolean"))
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.enabled"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.capability_live_bridge"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.host_broker.attached"])
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])
    }
}
