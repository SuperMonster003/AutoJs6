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

    /**
     * M1.4: a project-shaped workspace (project.json, nested lib module, and
     * a node_modules package — all plaintext, none pre-listed in
     * moduleSources) must run through the workspace-archive transport. The
     * input archive only carries explicitly named files, so the nested
     * modules reach the plugin on demand through the module-source provider.
     */
    @Test
    fun pluginRuntimeRunsProjectWithNodeModulesThroughWorkspaceTransport() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime plugin project workspace smoke")
        val runtimeInfo = runBlocking {
            runCatching { NodeJsRuntimePluginHost.probe(context) }.getOrNull()
        }
        assumeTrue(
            "Install, enable, and authorize a Node.js runtime plugin to run the project workspace smoke.",
            runtimeInfo != null,
        )

        val workDir = File(context.cacheDir, "node-runtime-plugin-project-workspace").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin project working directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """{"name":"plugin-project-smoke","type":"node","main":"main.cjs"}""",
            StandardCharsets.UTF_8,
        )
        File(workDir, "lib/helper.cjs").apply {
            check(parentFile.mkdirs() || parentFile.isDirectory)
            writeText("module.exports = { part: 'helper' };\n", StandardCharsets.UTF_8)
        }
        File(workDir, "node_modules/demo-pkg/package.json").apply {
            check(parentFile.mkdirs() || parentFile.isDirectory)
            writeText("""{"name":"demo-pkg","main":"./index.js"}""", StandardCharsets.UTF_8)
        }
        File(workDir, "node_modules/demo-pkg/index.js").writeText(
            "module.exports = { part: 'demo-pkg' };\n",
            StandardCharsets.UTF_8,
        )
        val mainSource = """
            "nodejs";
            const helper = require("./lib/helper.cjs");
            const demo = require("demo-pkg");
            console.log("plugin.project.parts=" + [helper.part, demo.part].join("+"));
        """.trimIndent()
        File(workDir, "main.cjs").writeText(mainSource, StandardCharsets.UTF_8)

        val result = NodeJsRuntimePluginBridge.runScript(
            context,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = mainSource,
                sourceName = File(workDir, "main.cjs").absolutePath,
                workingDirectory = workDir.absolutePath,
                sandboxRoot = workDir.absolutePath,
                timeoutMs = 10_000L,
            ),
        )

        NodeEmbeddedTestHarness.assertSucceeded("Node runtime plugin project workspace smoke", result)
        assertTrue(result.stdout.contains("plugin.project.parts=helper+demo-pkg"))
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.enabled"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.workspace.mapped"])
    }

    /**
     * M1.5: integration failures must surface as an error code plus one
     * readable message, not a diagnostics key-value wall. Covers the three
     * failure shapes users hit first: a thrown script error, a missing
     * module, and unsupported TSX sources.
     */
    @Test
    fun pluginRuntimeFailuresCarryReadableCodeAndMessage() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime plugin failure readability smoke")
        val runtimeInfo = runBlocking {
            runCatching { NodeJsRuntimePluginHost.probe(context) }.getOrNull()
        }
        assumeTrue(
            "Install, enable, and authorize a Node.js runtime plugin to run the failure readability smoke.",
            runtimeInfo != null,
        )

        val workDir = File(context.cacheDir, "node-runtime-plugin-failure-readability").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin failure working directory: $absolutePath" }
        }

        data class FailureCase(
            val label: String,
            val sourceName: String,
            val source: String,
            // A plain JS throw carries no Node error code; its identity is
            // the error name. Structured failures carry an explicit code.
            val expectedCode: String?,
            val expectedName: String?,
            val expectedMessagePart: String,
        )

        listOf(
            FailureCase(
                label = "thrown script error",
                sourceName = "<plugin-failure-thrown.js>",
                source = """
                    "nodejs";
                    throw new Error("boom from user script");
                """.trimIndent(),
                expectedCode = null,
                expectedName = "Error",
                expectedMessagePart = "boom from user script",
            ),
            FailureCase(
                label = "missing module",
                sourceName = "<plugin-failure-missing-module.js>",
                source = """
                    "nodejs";
                    require("./no-such-module.cjs");
                """.trimIndent(),
                expectedCode = "MODULE_NOT_FOUND",
                expectedName = null,
                expectedMessagePart = "no-such-module",
            ),
            FailureCase(
                label = "unsupported tsx entry",
                sourceName = File(workDir, "widget.tsx").absolutePath,
                source = "export default <div />;",
                expectedCode = "ERR_AUTOJS6_TYPESCRIPT_UNSUPPORTED_EXTENSION",
                expectedName = null,
                expectedMessagePart = "TSX",
            ),
        ).forEach { case ->
            val result = NodeJsRuntimePluginBridge.runScript(
                context,
                NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                    source = case.source,
                    sourceName = case.sourceName,
                    workingDirectory = workDir.absolutePath,
                    sandboxRoot = workDir.absolutePath,
                    timeoutMs = 10_000L,
                ),
            )

            assertTrue("${case.label}: run unexpectedly succeeded", !result.succeeded)
            val code = result.errorCode.orEmpty()
            val message = result.errorMessage.orEmpty()
            case.expectedCode?.let { expected ->
                assertTrue(
                    "${case.label}: expected error code $expected, got '$code'",
                    code == expected,
                )
            }
            case.expectedName?.let { expected ->
                assertTrue(
                    "${case.label}: expected error name $expected, got '${result.errorName.orEmpty()}'",
                    result.errorName.orEmpty() == expected,
                )
            }
            assertTrue(
                "${case.label}: message does not mention '${case.expectedMessagePart}': $message",
                message.contains(case.expectedMessagePart, ignoreCase = true),
            )
            // The human-readable sentence must lead (an ERR_* code prefix and
            // a file path are fine); the single-line require diagnostics
            // context may follow it (the MVP suite pins that contract), but
            // diagnostic key-value walls may not appear.
            val firstSentence = message
                .removePrefix("$code: ")
                .substringBefore(". ")
            assertTrue(
                "${case.label}: leading sentence is not readable (${firstSentence.length} chars): $firstSentence",
                firstSentence.isNotBlank() && firstSentence.length <= 250,
            )
            assertTrue(
                "${case.label}: message repeats a boilerplate execution-failed prefix: $message",
                !message.contains("plugin execution failed", ignoreCase = true),
            )
            assertTrue(
                "${case.label}: message contains a diagnostics key-value wall: $message",
                !message.contains("nativeValues=") && !message.contains("embedded_script."),
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
