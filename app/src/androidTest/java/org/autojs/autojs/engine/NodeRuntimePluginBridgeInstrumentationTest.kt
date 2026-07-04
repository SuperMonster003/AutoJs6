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

        val workDir = File(context.cacheDir, "node-runtime-plugin-live-device").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin live bridge working directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "node": {
                "permissions": ["device"]
              }
            }
            """.trimIndent(),
        )
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
            ),
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node runtime plugin live bridge device smoke", result)
        assertTrue(result.stdout.contains("plugin.live.device.screenOnType=boolean"))
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.enabled"])
        assertEquals("plugin", result.nativeValues["embedded_script.runtime_plugin.diagnostics_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.capability_live_bridge"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.host_broker.attached"])
        assertEquals("plugin", result.nativeValues["embedded_script.runtime_plugin.host_broker.diagnostics_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.host_broker.enabled"])
        assertEquals("false", result.nativeValues["embedded_script.runtime_plugin.host_broker.destroyed"])
        assertTrue(result.nativeValues["embedded_script.runtime_plugin.host_broker.id"].orEmpty().isNotBlank())
        assertEquals(
            NodeJsRuntimeContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION.toString(),
            result.nativeValues["embedded_script.runtime_plugin.host_broker.contract_version"],
        )
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.engine_info_present"])
        assertEquals("host_broker", result.nativeValues["embedded_script.runtime_plugin.runtime_module.engine_info_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.host_app_info_present"])
        assertEquals("bridge_engine_info", result.nativeValues["embedded_script.runtime_plugin.runtime_module.host_app_info_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.device_info_present"])
        assertEquals("plugin_context", result.nativeValues["embedded_script.runtime_plugin.runtime_module.device_info_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_permissions_present"])
        assertEquals("working_directory", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_permissions_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_limits_present"])
        assertEquals("working_directory", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_limits_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.lifecycle_config_present"])
        assertEquals("bridge_engine_info", result.nativeValues["embedded_script.runtime_plugin.runtime_module.lifecycle_config_source"])
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])
    }
}
