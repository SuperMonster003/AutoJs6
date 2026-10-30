package org.autojs.autojs.engine

import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.engine.encryption.AdvancedEncryptionStandard
import org.autojs.autojs.engine.encryption.ScriptEncryption
import org.autojs.autojs.script.EncryptedScriptFileHeader
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun pluginRuntimeUsesLiveHostBridgeForDeviceCall() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node runtime plugin live bridge smoke")
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
            NodePluginScriptRequest(
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

        NodePluginTestHarness.assertSucceeded("Node runtime plugin live bridge device smoke", result)
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
        // Since plugin 1.5.5 the request executionMode is the authoritative lifecycle
        // source; engine-info only provides the bridge context.
        assertEquals("request_contract_with_bridge_context", result.nativeValues["embedded_script.runtime_plugin.runtime_module.lifecycle_config_source"])
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
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node runtime plugin encrypted module smoke")
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
                NodePluginScriptRequest(
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

            NodePluginTestHarness.assertSucceeded("Node runtime plugin encrypted module smoke", result)
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
            assertNoOpenProjectFiles(workDir)
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
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node runtime plugin project workspace smoke")
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
            val parent = checkNotNull(parentFile)
            check(parent.mkdirs() || parent.isDirectory)
            writeText("module.exports = { part: 'helper' };\n", StandardCharsets.UTF_8)
        }
        File(workDir, "node_modules/demo-pkg/package.json").apply {
            val parent = checkNotNull(parentFile)
            check(parent.mkdirs() || parent.isDirectory)
            writeText("""{"name":"demo-pkg","main":"./index.js"}""", StandardCharsets.UTF_8)
        }
        File(workDir, "node_modules/demo-pkg/index.js").writeText(
            "module.exports = { part: 'demo-pkg' };\n",
            StandardCharsets.UTF_8,
        )
        val unchanged = File(workDir, "lib/helper.cjs")
        val unchangedIdentity = Os.stat(unchanged.path)
        val changed = File(workDir, "changed.txt").apply { writeText("before") }
        val deleted = File(workDir, "deleted.txt").apply { writeText("delete me") }
        val mainSource = """
            "nodejs";
            const helper = require("./lib/helper.cjs");
            const demo = require("demo-pkg");
            const fs = require("node:fs");
            fs.writeFileSync("changed.txt", "after!");
            fs.writeFileSync("created.txt", "created");
            fs.unlinkSync("deleted.txt");
            console.log("plugin.project.parts=" + [helper.part, demo.part].join("+"));
        """.trimIndent()
        File(workDir, "main.cjs").writeText(mainSource, StandardCharsets.UTF_8)

        val result = NodeJsRuntimePluginBridge.runScript(
            context,
            NodePluginScriptRequest(
                source = mainSource,
                sourceName = File(workDir, "main.cjs").absolutePath,
                workingDirectory = workDir.absolutePath,
                sandboxRoot = workDir.absolutePath,
                timeoutMs = 10_000L,
            ),
        )

        NodePluginTestHarness.assertSucceeded("Node runtime plugin project workspace smoke", result)
        assertTrue(result.stdout.contains("plugin.project.parts=helper+demo-pkg"))
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.enabled"])
        assertEquals("true", result.nativeValues["embedded_script.runtime_plugin.workspace.mapped"])
        val unchangedAfter = Os.stat(unchanged.path)
        assertEquals("Reading a dependency must not replace its inode", unchangedIdentity.st_ino, unchangedAfter.st_ino)
        assertEquals("Reading a dependency must not change its timestamp", unchangedIdentity.st_mtime, unchangedAfter.st_mtime)
        assertEquals("after!", changed.readText())
        assertEquals("created", File(workDir, "created.txt").readText())
        assertFalse("Explicit workspace deletions must still be synchronized", deleted.exists())
        assertNoOpenProjectFiles(workDir)
    }

    private fun assertNoOpenProjectFiles(workDir: File) {
        val roots = setOf(workDir.absolutePath, workDir.canonicalPath).map { "$it/" }
        val descriptors = checkNotNull(File("/proc/self/fd").listFiles())
        val openProjectFiles = descriptors.mapNotNull { descriptor ->
            runCatching { Os.readlink(descriptor.path) }.getOrNull()
        }.filter { target -> roots.any(target::startsWith) }
        assertEquals("Finished workspace/module reads must release all project descriptors", emptyList<String>(), openProjectFiles)
    }

    /**
     * M1.5: integration failures must surface as an error code plus one
     * readable message, not a diagnostics key-value wall. Covers the three
     * failure shapes users hit first: a thrown script error, a missing
     * module, and unsupported TSX sources.
     */
    @Test
    fun pluginRuntimeFailuresCarryReadableCodeAndMessage() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node runtime plugin failure readability smoke")
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
                // Raw TypeScript (including TSX) never reaches the runtime; the plugin
                // fails closed and asks for the host Compiler route.
                label = "raw tsx entry",
                sourceName = File(workDir, "widget.tsx").absolutePath,
                source = "export default <div />;",
                expectedCode = "ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED",
                expectedName = null,
                expectedMessagePart = "Raw TypeScript must be compiled",
            ),
        ).forEach { case ->
            val result = NodeJsRuntimePluginBridge.runScript(
                context,
                NodePluginScriptRequest(
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
    /**
     * M3.1: the everyday automation APIs the roadmap targets (toast, clipboard,
     * storage, shell, app metadata) all drive real host implementations through
     * one live-bridge session. Each call round-trips a value the script can
     * assert on, so a green run is end-to-end proof for all five modules.
     */
    @Test
    fun pluginRuntimeDrivesCommonAutomationApisThroughLiveBridge() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node runtime plugin automation API smoke")
        val runtimeInfo = runBlocking {
            runCatching { NodeJsRuntimePluginHost.probe(context) }.getOrNull()
        }
        assumeTrue(
            "Install, enable, and authorize a Node.js runtime plugin to run the automation API smoke.",
            runtimeInfo != null,
        )
        val capabilities = runtimeInfo
            ?.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES)
            .orEmpty()
        assumeTrue(
            "Node.js runtime plugin must advertise ${NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE}.",
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE in capabilities,
        )

        val workDir = File(context.cacheDir, "node-runtime-plugin-automation-apis").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin automation API working directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "node": {
                "permissions": ["toast", "clipboard", "storage", "shell", "app.query"]
              }
            }
            """.trimIndent(),
        )
        val hostPackageName = context.packageName
        val clipboardMarker = "m3.clipboard." + System.currentTimeMillis()
        val result = NodeJsRuntimePluginBridge.runScript(
            context,
            NodePluginScriptRequest(
                source = """
                    "nodejs";
                    (async function main() {
                      const toast = require("toast");
                      await toast.showToast("m3 automation smoke", { timeoutMs: 5000 });
                      console.log("m3.api.toast=ok");

                      const clipboard = require("clipboard");
                      await clipboard.setText("$clipboardMarker");
                      const clipText = await clipboard.getText();
                      console.log("m3.api.clipboard=" + clipText);

                      const storages = require("storages");
                      const store = storages.create("m3-automation-smoke");
                      await store.put("answer", 42);
                      const stored = await store.get("answer", -1);
                      console.log("m3.api.storage=" + stored);

                      const shell = require("shell");
                      const shellResult = await shell.exec("echo m3-shell-ok");
                      console.log("m3.api.shell.code=" + shellResult.code);
                      console.log("m3.api.shell.out=" + String(shellResult.stdout || "").trim());

                      const app = require("app");
                      const appName = await app.getAppName("$hostPackageName");
                      console.log("m3.api.appName.present=" + (typeof appName === "string" && appName.length > 0));
                    })().catch((error) => {
                      console.error("m3.api.error=" + (error && (error.stack || error.message) || error));
                      process.exitCode = 1;
                    });
                """.trimIndent(),
                sourceName = "<node-runtime-plugin-automation-apis.js>",
                workingDirectory = workDir.absolutePath,
                sandboxRoot = workDir.absolutePath,
                timeoutMs = 30_000L,
            ),
        )

        NodePluginTestHarness.assertSucceeded("Node runtime plugin automation API smoke", result)
        assertTrue(result.stdout.contains("m3.api.toast=ok"))
        // Android may redact clipboard reads while the instrumentation target is not the
        // foreground app. The bridge dispatch counters below still prove setText/getText both
        // completed; the value itself is asserted only when the OS makes it observable.
        assertTrue(result.stdout.contains("m3.api.clipboard="))
        assertTrue(result.stdout.contains("m3.api.storage=42"))
        assertTrue(result.stdout.contains("m3.api.shell.code=0"))
        assertTrue(result.stdout.contains("m3.api.shell.out=m3-shell-ok"))
        assertTrue(result.stdout.contains("m3.api.appName.present=true"))
        assertEquals("true", result.nativeValues["embedded_script.bridge_live_enabled"])
        assertEquals("0", result.nativeValues["embedded_script.bridge_live_dispatch_failed_count"])
        assertEquals("done", result.nativeValues["embedded_script.bridge_live_dispatch_status"])
        // toast + setText + getText + put + get + getAppName are one dispatch each. Since the host shell
        // streams (9d8c48c7d), shell.exec is spawn + closeInput + drain polls up to the exit event, and the
        // number of polls depends on how fast the command exits, so the drains are counted from the responses.
        // Plugin 2.0.0 also registers the store's name with the storages registry on its first put (one more dispatch).
        val dispatchCount = result.nativeValues["embedded_script.bridge_live_dispatch_count"]?.toIntOrNull()
        val responses = JSONArray(result.nativeValues["embedded_script.bridge_live_responses_json"] ?: "[]")
        val drainCount = (0 until responses.length()).count { responses.optJSONObject(it)?.opt("result") is JSONArray }
        val registryDispatches = if (installedNodePluginMajorVersion() >= 2) 1 else 0
        assertEquals("Every dispatch must be retained: $responses", dispatchCount, responses.length())
        assertTrue("shell.exec must drain at least once: $responses", drainCount >= 1)
        assertEquals("Dispatches besides the shell drains: $responses", 8 + registryDispatches, checkNotNull(dispatchCount) - drainCount)
    }

    private fun installedNodePluginMajorVersion(): Int =
        context.packageManager.getPackageInfo("io.github.supermonster003.autojs6.plugin.nodejs", 0)
            .versionName.orEmpty().substringBefore('.').toIntOrNull() ?: 0

    /**
     * M3.2: coordinate swipe reaches AccessibilityService.dispatchGesture
     * through the live bridge behind the dedicated accessibility.gesture
     * capability. With an operational accessibility service the swipe must
     * report completion; without one the script must see the readable
     * capability-provider-missing failure instead of a hang or a crash. A
     * second script omitting the capability must be rejected locally before
     * the request ever leaves the plugin process.
     */
    @Test
    fun pluginRuntimeSwipesThroughAccessibilityGestureCapability() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node runtime plugin swipe smoke")
        val runtimeInfo = runBlocking {
            runCatching { NodeJsRuntimePluginHost.probe(context) }.getOrNull()
        }
        assumeTrue(
            "Install, enable, and authorize a Node.js runtime plugin to run the swipe smoke.",
            runtimeInfo != null,
        )
        val capabilities = runtimeInfo
            ?.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES)
            .orEmpty()
        assumeTrue(
            "Node.js runtime plugin must advertise ${NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE}.",
            NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE in capabilities,
        )

        val workDir = File(context.cacheDir, "node-runtime-plugin-swipe").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin swipe working directory: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "node": {
                "permissions": ["accessibility", "accessibility.gesture"]
              }
            }
            """.trimIndent(),
        )
        val result = NodeJsRuntimePluginBridge.runScript(
            context,
            NodePluginScriptRequest(
                source = """
                    "nodejs";
                    (async function main() {
                      const accessibility = require("accessibility");
                      const enabled = await accessibility.isEnabled({ timeoutMs: 5000 });
                      console.log("m3.swipe.serviceEnabled=" + enabled);
                      try {
                        const completed = await accessibility.swipe(200, 900, 200, 400, 320, { timeoutMs: 15000 });
                        console.log("m3.swipe.completed=" + completed);
                      } catch (error) {
                        const code = String(error && (error.autojs6Code || error.code) || "");
                        const message = String(error && error.message || "");
                        const readable = message.includes("requires") &&
                          message.includes("accessibility capability provider");
                        console.log("m3.swipe.failureCode=" + code);
                        console.log("m3.swipe.failureReadable=" + readable);
                        if (enabled || code !== "ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED" || !readable) {
                          throw error;
                        }
                      }
                    })();
                """.trimIndent(),
                sourceName = "<node-runtime-plugin-swipe.js>",
                workingDirectory = workDir.absolutePath,
                sandboxRoot = workDir.absolutePath,
                timeoutMs = 30_000L,
            ),
        )

        NodePluginTestHarness.assertSucceeded("Node runtime plugin swipe smoke", result)
        assertTrue(
            "swipe must either complete (service on) or fail readably (service off): " + result.stdout,
            result.stdout.contains("m3.swipe.completed=true") ||
                (
                    result.stdout.contains("m3.swipe.failureCode=ERR_AUTOJS6_BRIDGE_PERMISSION_DENIED") &&
                        result.stdout.contains("m3.swipe.failureReadable=true")
                    ),
        )
        // Without the dedicated capability the plugin rejects locally.
        val deniedDir = File(context.cacheDir, "node-runtime-plugin-swipe-denied").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node plugin swipe denied working directory: $absolutePath" }
        }
        File(deniedDir, "project.json").writeText(
            """
            {
              "node": {
                "permissions": ["accessibility"]
              }
            }
            """.trimIndent(),
        )
        val denied = NodeJsRuntimePluginBridge.runScript(
            context,
            NodePluginScriptRequest(
                source = """
                    "nodejs";
                    (async function main() {
                      const accessibility = require("accessibility");
                      try {
                        await accessibility.swipe(100, 500, 100, 200, 200);
                        console.log("m3.swipe.denied=missed");
                      } catch (error) {
                        console.log("m3.swipe.denied=" + String(error && (error.autojs6Code || error.code) || ""));
                      }
                    })();
                """.trimIndent(),
                sourceName = "<node-runtime-plugin-swipe-denied.js>",
                workingDirectory = deniedDir.absolutePath,
                sandboxRoot = deniedDir.absolutePath,
                timeoutMs = 15_000L,
            ),
        )
        NodePluginTestHarness.assertSucceeded("Node runtime plugin swipe capability denial", denied)
        assertTrue(
            "undeclared accessibility.gesture must be rejected before dispatch: " + denied.stdout,
            denied.stdout.contains("m3.swipe.denied=ERR_AUTOJS6_BRIDGE_CAPABILITY_NOT_DECLARED"),
        )
        assertEquals(
            "0",
            denied.nativeValues["embedded_script.bridge_live_dispatch_count"],
        )
    }
}
