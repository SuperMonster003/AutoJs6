package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

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
        // Since plugin v1.1.3 the permission/limit manifests come from the
        // runtime policy metadata preflight, not raw working-directory reads.
        assertEquals("exact_metadata_snapshot", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_permissions_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_limits_present"])
        assertEquals("exact_metadata_snapshot", result.nativeValues["embedded_script.runtime_plugin.runtime_module.bridge_limits_source"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.runtime_module.lifecycle_config_present"])
        assertEquals("bridge_engine_info", result.nativeValues["embedded_script.runtime_plugin.runtime_module.lifecycle_config_source"])
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_pending_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_request_count"])
    }

    /**
     * M1.3: an encrypted module resolved through the host's published v1
     * NodeJsEncryptedModuleSourceProvider must decrypt inside the external
     * plugin runtime. The plugin negotiates the provider contract down to v1
     * after the host rejects its initial v2 request header.
     */
    @Test
    fun pluginRuntimeDecryptsEncryptedModuleThroughHostV1Provider() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime plugin encrypted module smoke")
        val runtimeInfo = runBlocking {
            runCatching { NodeJsRuntimePluginHost.probe(context) }.getOrNull()
        }
        assumeTrue(
            "Install, enable, and authorize a Node.js runtime plugin to run the encrypted module smoke.",
            runtimeInfo != null,
        )
        val capabilities = runtimeInfo
            ?.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES)
            .orEmpty()
        assumeTrue(
            "Node.js runtime plugin must advertise ${NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER}.",
            NodeJsRuntimeContract.CAPABILITY_ON_DEMAND_MODULE_SOURCE_PROVIDER in capabilities,
        )

        val workDir = File(context.cacheDir, "node-runtime-plugin-encrypted-module").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin encrypted module working directory: $absolutePath" }
        }
        withTestScriptEncryption {
            writeEncryptedScript(
                File(workDir, "secret.cjs"),
                """module.exports = { answer: "plugin-encrypted-" + (40 + 2) };""",
            )
            val result = NodeJsRuntimePluginBridge.runScript(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = """
                        "nodejs";
                        const secret = require("./secret.cjs");
                        console.log("plugin.encrypted.value=" + secret.answer);
                    """.trimIndent(),
                    sourceName = "<node-runtime-plugin-encrypted-module.js>",
                    workingDirectory = workDir.absolutePath,
                    sandboxRoot = workDir.absolutePath,
                    timeoutMs = 10_000L,
                ),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node runtime plugin encrypted module smoke", result)
            assertTrue(result.stdout.contains("plugin.encrypted.value=plugin-encrypted-42"))
            assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.enabled"])
            assertEquals(
                "true",
                result.nativeValues["embedded_script.runtime_plugin.capability_module_source_provider"],
            )
            assertEquals(
                "1",
                result.nativeValues["embedded_script.runtime_plugin.module_provider.decrypted_count"],
            )
            assertEquals(
                "0",
                result.nativeValues["embedded_script.module_provider.transport_failure_count"],
            )
        }
    }

    private fun writeEncryptedScript(file: File, source: String) {
        file.parentFile?.let { parent -> check(parent.mkdirs() || parent.isDirectory) }
        val encrypted = AdvancedEncryptionStandard(
            TEST_ENCRYPTION_KEY.toByteArray(StandardCharsets.UTF_8),
            TEST_ENCRYPTION_VECTOR,
        ).encrypt(source.toByteArray(StandardCharsets.UTF_8))
        file.outputStream().use { output ->
            EncryptedScriptFileHeader.writeHeader(
                output,
                EncryptedScriptFileHeader.FLAG_EXECUTION_MODE_NODEJS,
            )
            output.write(encrypted)
        }
    }

    private inline fun <T> withTestScriptEncryption(block: () -> T): T {
        val keyField = ScriptEncryption::class.java.getDeclaredField("mKey").apply { isAccessible = true }
        val vectorField = ScriptEncryption::class.java.getDeclaredField("mInitVector").apply { isAccessible = true }
        val previousKey = keyField.get(null)
        val previousVector = vectorField.get(null)
        keyField.set(null, TEST_ENCRYPTION_KEY)
        vectorField.set(null, TEST_ENCRYPTION_VECTOR)
        return try {
            block()
        } finally {
            keyField.set(null, previousKey)
            vectorField.set(null, previousVector)
        }
    }

    private companion object {
        const val TEST_ENCRYPTION_KEY = "0123456789abcdef0123456789abcdef"
        const val TEST_ENCRYPTION_VECTOR = "0123456789abcdef"
    }
}
