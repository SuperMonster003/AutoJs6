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
class NodePluginSystemParityPhase13DecisionInstrumentationTest {

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
    fun pluginSystemProfileAndHostCapabilityCeilingAreStable() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Phase 13 plugin system parity decision")
        val workDir = freshWorkDir("node-plugin-phase13")
        try {
            writePlugin(
                workDir = workDir,
                id = "org.example.phase13",
                permissions = listOf("toast", "network"),
                source = """
                    globalThis.__autojs6Phase13PluginExecuted = true;
                    module.exports = { ready: true };
                """.trimIndent(),
            )
            val manifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
                listOf("plugin.org.example.phase13", "toast"),
            )
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    const plugins = require("plugins");
                    const pluginProfile = profile.pluginSystemProfile;
                    console.log("p13plugin.profile=" + [
                      pluginProfile.status,
                      pluginProfile.defaultEnabled,
                      pluginProfile.targetProfiles.join(","),
                      pluginProfile.manager,
                      pluginProfile.pluginRoot,
                      pluginProfile.installMode,
                      pluginProfile.operations.join(","),
                      pluginProfile.permissionModel,
                      pluginProfile.hostManifestEnforcement,
                      pluginProfile.overDeclaredPluginCapabilities,
                      pluginProfile.androidProviderPlugins,
                      pluginProfile.nativePlugins,
                      pluginProfile.remoteMarketplace,
                      pluginProfile.signedArchives,
                      pluginProfile.updateChannel,
                      pluginProfile.packagedDistribution,
                      pluginProfile.resourceOwnership,
                      pluginProfile.debugReload,
                      pluginProfile.downloadInstaller,
                      pluginProfile.nativeAddonLoading,
                      pluginProfile.androidPluginApkBridge,
                      pluginProfile.marketplaceInstall,
                      pluginProfile.persistedGlobalState,
                      pluginProfile.grantsHostCapabilities,
                      Object.isFrozen(pluginProfile.operations),
                      pluginProfile.operations.includes("capabilities")
                    ].join("|"));
                    try {
                      plugins.install("org.example.phase13", {
                        source: "./plugins/org.example.phase13"
                      });
                      console.log("p13plugin.denial=allowed");
                    } catch (error) {
                      console.log("p13plugin.denial=" + [
                        error.code,
                        error.autojs6Code,
                        error.module,
                        error.method,
                        Array.isArray(error.missingCapabilities) ? error.missingCapabilities.join(",") : "",
                        globalThis.__autojs6Phase13PluginExecuted === true
                      ].join("|"));
                    }
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("Phase 13 plugin system parity decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13plugin.profile=js_only_local_manager_partial|true|pro_compat_opt_in,desktop_compat_opt_in|" +
                            "js_only_local_manager_v1|project_local_plugins_directory|already_present_current_execution|" +
                            "install,enable,disable,configure,load,start,stop,unload,reload,status,manifest,resolve,list,capabilities|" +
                            "exact_plugin_id_plus_requested_permissions|required_before_install_load_start|" +
                            "rejected_before_code_execution|deferred|denied|deferred|deferred|deferred|" +
                            "local_checked_files_only|plugin_lifecycle_context_partial|debug_only|" +
                            "false|false|false|false|false|false|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13plugin.denial=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|" +
                            "${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|plugins|install|network|false",
                ),
            )
            assertFalse(result.stdout.contains("p13plugin.denial=allowed"))
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
              "main": "index.cjs",
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
