package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class NodePluginSystemInstrumentationTest {

    private val context = NodeEmbeddedTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun settleAfter() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun jsOnlyPluginLoadsFromProjectPluginsDirectory() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin prototype")
        val workDir = freshWorkDir("node-plugin-safe")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.safe",
                permissions = listOf("toast"),
                source = """
                    globalThis.__autojs6PluginSafeLoads = (globalThis.__autojs6PluginSafeLoads || 0) + 1;
                    const sibling = require("./lib/sibling.cjs");
                    exports.id = "org.example.safe";
                    exports.value = sibling.value;
                    exports.count = globalThis.__autojs6PluginSafeLoads;
                    try {
                      require("../escape.cjs");
                      exports.escapeDenied = false;
                    } catch (error) {
                      exports.escapeDenied = /escapes/.test(String(error && error.message || ""));
                    }
                """.trimIndent(),
            )
            File(workDir, "plugins/org.example.safe/lib").mkdirs()
            File(workDir, "plugins/org.example.safe/lib/sibling.cjs").writeText(
                """exports.value = "safe-value";""",
                StandardCharsets.UTF_8,
            )
            File(workDir, "plugins/escape.cjs").writeText("""module.exports = "escape";""", StandardCharsets.UTF_8)
            File(workDir, "node_modules/plugins").mkdirs()
            File(workDir, "node_modules/plugins/index.js").writeText(
                """module.exports = { shadow: true };""",
                StandardCharsets.UTF_8,
            )

            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf("plugin.org.example.safe", "toast"),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    const info = plugins.manifest("org.example.safe");
                    const resolved = plugins.resolve("org.example.safe");
                    const loaded = plugins.load("org.example.safe");
                    const loadedAgain = plugins.load("org.example.safe");
                    console.log("plugins.safe.resolve=" + [
                      require.resolve("plugins") === "plugins",
                      resolved.replace(/\\/g, "/").endsWith("/plugins/org.example.safe/index.cjs")
                    ].join("|"));
                    console.log("plugins.safe.manifest=" + [
                      info.id,
                      info.capability,
                      info.permissions.join(","),
                      info.jsOnly,
                      info.nativeAddons
                    ].join("|"));
                    console.log("plugins.safe.load=" + [
                      loaded.id,
                      loaded.value,
                      loaded.count,
                      loaded === loadedAgain,
                      loaded.escapeDenied,
                      plugins.shadow === undefined
                    ].join("|"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin prototype safe load", result)
            assertTrue(result.stdout.contains("plugins.safe.resolve=true|true"))
            assertTrue(result.stdout.contains("plugins.safe.manifest=org.example.safe|plugin.org.example.safe|toast|true|false"))
            assertTrue(result.stdout.contains("plugins.safe.load=org.example.safe|safe-value|1|true|true|true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun pluginPermissionDeniedBeforeCodeExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin permission denial")
        val workDir = freshWorkDir("node-plugin-permission")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.denied",
                permissions = listOf("toast"),
                source = """
                    globalThis.__autojs6DeniedPluginExecuted = true;
                    module.exports = { ready: true };
                """.trimIndent(),
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("plugin.org.example.denied"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    try {
                      plugins.load("org.example.denied");
                      console.log("plugins.denied=allowed");
                    } catch (error) {
                      console.log("plugins.denied=" + [
                        error.code,
                        error.autojs6Code,
                        error.module,
                        error.method,
                        Array.isArray(error.missingCapabilities) ? error.missingCapabilities.join(",") : "",
                        globalThis.__autojs6DeniedPluginExecuted === true
                      ].join("|"));
                    }
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin permission denial", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("plugins.denied=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|plugins|load|toast|false"),
            )
            assertFalse(result.stdout.contains("plugins.denied=allowed"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun pluginNativeAddonFileIsRejected() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin native addon denial")
        val workDir = freshWorkDir("node-plugin-native")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.native",
                permissions = emptyList(),
                source = """module.exports = { ready: true };""",
            )
            File(workDir, "plugins/org.example.native/addon.node").writeText(
                "native-addon-placeholder\n",
                StandardCharsets.UTF_8,
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("plugin.org.example.native"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    try {
                      plugins.load("org.example.native");
                      console.log("plugins.native=allowed");
                    } catch (error) {
                      console.log("plugins.native=" + [
                        error.code,
                        error.autojs6Code,
                        /\.node/.test(String(error && error.message || ""))
                      ].join("|"));
                    }
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin native addon denial", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("plugins.native=${NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED}|${NodeRuntimeErrorCodes.NATIVE_ADDON_DISABLED}|true"),
            )
            assertFalse(result.stdout.contains("plugins.native=allowed"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun pluginEntryPathEscapeIsRejected() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin path escape denial")
        val workDir = freshWorkDir("node-plugin-escape")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.escape",
                permissions = emptyList(),
                entry = "../escape.cjs",
                source = """module.exports = { ready: true };""",
            )
            File(workDir, "plugins/escape.cjs").writeText("""module.exports = "escape";""", StandardCharsets.UTF_8)
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(listOf("plugin.org.example.escape"))
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    try {
                      plugins.load("org.example.escape");
                      console.log("plugins.escape=allowed");
                    } catch (error) {
                      console.log("plugins.escape=" + [
                        error.code,
                        error.autojs6Code,
                        error.module,
                        error.method
                      ].join("|"));
                    }
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin path escape denial", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("plugins.escape=EPERM|${NodeRuntimeErrorCodes.FS_PATH_ESCAPE}|plugins|load"),
            )
            assertFalse(result.stdout.contains("plugins.escape=allowed"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun freshWorkDir(name: String): File =
        File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create work dir: $absolutePath" }
        }

    private fun writePlugin(
        workDir: File,
        id: String,
        permissions: List<String>,
        entry: String = "index.cjs",
        source: String,
    ) {
        val pluginDir = File(workDir, "plugins/$id").apply {
            check(mkdirs()) { "Failed to create plugin dir: $absolutePath" }
        }
        val permissionsJson = permissions.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        File(pluginDir, "package.json").writeText(
            """
            {
              "name": "$id",
              "version": "1.0.0",
              "main": "$entry",
              "autojs6": {
                "plugin": {
                  "id": "$id",
                  "permissions": $permissionsJson
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(pluginDir, "index.cjs").writeText(source, StandardCharsets.UTF_8)
    }
}
