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
    fun pluginNamespaceCapabilityPermissionIsRejectedBeforeCodeExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin namespace capability denial")
        val workDir = freshWorkDir("node-plugin-namespace-permission")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.namespace",
                permissions = listOf("plugin.org.example.other"),
                source = """
                    globalThis.__autojs6NamespacePluginExecuted = true;
                    module.exports = { ready: true };
                """.trimIndent(),
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf("plugin.org.example.namespace", "plugin.org.example.other"),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    try {
                      plugins.load("org.example.namespace");
                      console.log("plugins.namespace=allowed");
                    } catch (error) {
                      console.log("plugins.namespace=" + [
                        error.code,
                        error.autojs6Code,
                        error.module,
                        error.method,
                        /plugin namespace capability/.test(String(error && error.message || "")),
                        globalThis.__autojs6NamespacePluginExecuted === true
                      ].join("|"));
                    }
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin namespace capability denial", result)
            assertTrue(
                result.stdout,
                result.stdout.contains("plugins.namespace=${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}|${NodeRuntimeErrorCodes.BRIDGE_INVALID_REQUEST}|plugins|load|true|false"),
            )
            assertFalse(result.stdout.contains("plugins.namespace=allowed"))
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

    @Test
    fun pluginManagerInstallEnableConfigureExportsAndUnload() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin manager v1")
        val workDir = freshWorkDir("node-plugin-manager")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.manager",
                permissions = listOf("toast"),
                version = "1.2.3",
                dependencies = listOf("org.example.dependency"),
                exports = mapOf("helper" to "lib/helper.cjs"),
                source = """
                    globalThis.__autojs6ManagerPluginLoads = (globalThis.__autojs6ManagerPluginLoads || 0) + 1;
                    const plugins = require("plugins");
                    exports.count = globalThis.__autojs6ManagerPluginLoads;
                    exports.mode = plugins.config("org.example.manager").mode;
                """.trimIndent(),
            )
            File(workDir, "plugins/org.example.manager/lib").mkdirs()
            File(workDir, "plugins/org.example.manager/lib/helper.cjs").writeText(
                """exports.name = "helper-export";""",
                StandardCharsets.UTF_8,
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf("plugin.org.example.manager", "toast"),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    const installed = plugins.install("org.example.manager", {
                      source: "./plugins/org.example.manager",
                      config: { mode: "configured" }
                    });
                    const helper = plugins.load("org.example.manager", "helper");
                    const main = plugins.load("org.example.manager");
                    const mainAgain = plugins.load("org.example.manager");
                    const beforeUnloadSame = main === mainAgain;
                    const unloaded = plugins.unload("org.example.manager");
                    const reloaded = plugins.load("org.example.manager");
                    const disabled = plugins.disable("org.example.manager");
                    let disabledCode = "";
                    try {
                      plugins.load("org.example.manager");
                    } catch (error) {
                      disabledCode = error.code;
                    }
                    const enabled = plugins.enable("org.example.manager");
                    const configured = plugins.configure("org.example.manager", { mode: "updated" });
                    console.log("plugins.manager=" + [
                      installed.version,
                      plugins.version("org.example.manager"),
                      plugins.dependencies("org.example.manager").join(","),
                      plugins.exports("org.example.manager").helper,
                      helper.name,
                      main.count,
                      main.mode,
                      beforeUnloadSame,
                      unloaded,
                      reloaded.count,
                      disabled.enabled,
                      plugins.isEnabled("org.example.manager"),
                      disabledCode,
                      enabled.enabled,
                      configured.mode,
                      plugins.list().length
                    ].join("|"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin manager v1", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "plugins.manager=1.2.3|1.2.3|org.example.dependency|lib/helper.cjs|helper-export|" +
                            "1|configured|true|true|2|false|true|${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|true|updated|1",
                ),
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun pluginLifecycleResourcesAndDebugReload() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node JS-only plugin lifecycle manager")
        val workDir = freshWorkDir("node-plugin-lifecycle")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.lifecycle",
                permissions = listOf("toast"),
                source = """
                    let starts = 0;
                    let stops = 0;
                    const closed = [];
                    exports.start = function(ctx) {
                      starts += 1;
                      ctx.resources.setInterval(function() {
                        globalThis.__autojs6LifecyclePluginTick = (globalThis.__autojs6LifecyclePluginTick || 0) + 1;
                      }, 1000);
                      ctx.resources.track("provider", { close: function() { closed.push("provider"); } });
                      ctx.resources.track("ui", { close: function() { closed.push("ui"); } });
                      ctx.resources.track("socket", { close: function() { closed.push("socket"); } });
                      ctx.resources.track("image", { recycle: function() { closed.push("image"); } });
                      ctx.resources.addCleanup(function() { closed.push("generic"); });
                      globalThis.__autojs6LifecycleContext = [
                        ctx.id,
                        ctx.namespace,
                        ctx.owner,
                        ctx.phase,
                        ctx.config.mode,
                        ctx.capabilities.join(","),
                        ctx.permissions.join(",")
                      ].join(";");
                    };
                    exports.stop = function(ctx) {
                      stops += 1;
                      closed.push("stop:" + ctx.phase);
                    };
                    exports.state = function() {
                      return {
                        starts,
                        stops,
                        closed: closed.slice().sort().join(","),
                        context: globalThis.__autojs6LifecycleContext || ""
                      };
                    };
                """.trimIndent(),
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf("plugin.org.example.lifecycle", "toast"),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const plugins = require("plugins");
                    plugins.install("org.example.lifecycle", {
                      source: "./plugins/org.example.lifecycle",
                      config: { mode: "lifecycle" }
                    });
                    const startStatus = plugins.start("org.example.lifecycle");
                    const runningStatus = plugins.status("org.example.lifecycle");
                    const loaded = plugins.load("org.example.lifecycle");
                    const beforeStop = loaded.state();
                    const stopStatus = plugins.stop("org.example.lifecycle");
                    const afterStop = loaded.state();
                    let reloadDenied = "";
                    try {
                      plugins.reload("org.example.lifecycle");
                    } catch (error) {
                      reloadDenied = error.code;
                    }
                    const reloaded = plugins.reload("org.example.lifecycle", { debug: true });
                    const reloadStatus = plugins.status("org.example.lifecycle");
                    const restarted = plugins.start("org.example.lifecycle");
                    const afterRestart = plugins.load("org.example.lifecycle").state();
                    const unloaded = plugins.unload("org.example.lifecycle");
                    const finalStatus = plugins.status("org.example.lifecycle");
                    console.log("plugins.lifecycle=" + [
                      startStatus.state,
                      startStatus.started,
                      runningStatus.resources.total,
                      runningStatus.resources.timers,
                      runningStatus.resources.providers,
                      runningStatus.resources.ui,
                      runningStatus.resources.sockets,
                      runningStatus.resources.images,
                      plugins.capabilities("org.example.lifecycle").join(","),
                      beforeStop.starts,
                      beforeStop.context,
                      stopStatus.state,
                      stopStatus.started,
                      stopStatus.resources.total,
                      afterStop.stops,
                      afterStop.closed,
                      reloadDenied,
                      reloaded.reloadCount,
                      reloadStatus.loaded,
                      restarted.state,
                      restarted.startCount,
                      afterRestart.starts,
                      unloaded,
                      finalStatus.loaded,
                      finalStatus.started,
                      finalStatus.resources.total
                    ].join("|"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node JS-only plugin lifecycle manager", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "plugins.lifecycle=started|true|6|1|1|1|1|1|plugin.org.example.lifecycle,toast|1|" +
                            "org.example.lifecycle;plugin:org.example.lifecycle;plugin:org.example.lifecycle;start;lifecycle;plugin.org.example.lifecycle,toast;toast|" +
                            "stopped|false|0|1|generic,image,provider,socket,stop:stop,ui|" +
                            "${NodeRuntimeErrorCodes.BRIDGE_PERMISSION_DENIED}|1|false|started|1|1|true|false|false|0",
                ),
            )
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
        version: String = "1.0.0",
        dependencies: List<String> = emptyList(),
        exports: Map<String, String> = emptyMap(),
        source: String,
    ) {
        val pluginDir = File(workDir, "plugins/$id").apply {
            check(mkdirs()) { "Failed to create plugin dir: $absolutePath" }
        }
        val permissionsJson = permissions.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val dependenciesJson = dependencies.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val exportsJson = exports.entries.joinToString(prefix = "{", postfix = "}") { (name, target) ->
            "\"$name\": \"$target\""
        }
        File(pluginDir, "package.json").writeText(
            """
            {
              "name": "$id",
              "version": "$version",
              "main": "$entry",
              "autojs6": {
                "plugin": {
                  "id": "$id",
                  "permissions": $permissionsJson,
                  "dependencies": $dependenciesJson,
                  "exports": $exportsJson
                }
              }
            }
            """.trimIndent(),
            StandardCharsets.UTF_8,
        )
        File(pluginDir, "index.cjs").writeText(source, StandardCharsets.UTF_8)
    }
}
