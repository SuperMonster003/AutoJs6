package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodePackagedCapabilityMetadataPhase13DecisionInstrumentationTest {

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
    fun packagedCapabilityMetadataRecordsProfilesPermissionsAndStableDenials() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-45 packaged capability metadata")

        val workDir = File(context.cacheDir, "node-packaged-capability-p13-45-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-45 packaged metadata work dir: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "profile": "desktop_compat_opt_in",
                "profiles": ["pro_compat_opt_in"],
                "permissions": ["network"],
                "builtins": ["fs", "node:crypto"],
                "nativeAssets": ["native/sqlite.node"],
                "filesystem": {
                  "roots": ["app_data", "documents"]
                },
                "executionMode": "interactive_long_running",
                "network": true
              }
            }
            """.trimIndent(),
        )
        File(workDir, "package.json").writeText(
            """
            {
              "name": "p13-packaged-capability-metadata",
              "autojs6": {
                "node": {
                  "profiles": ["debug_unsafe_lab"],
                  "builtins": ["path"],
                  "nativeAssets": ["native/addon.node"],
                  "filesystemRoots": ["cache"]
                }
              }
            }
            """.trimIndent(),
        )

        try {
            val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workDir.absolutePath)
            assertTrue(manifest.enforced)
            assertEquals(listOf("network"), manifest.permissions)
            assertEquals(
                listOf(NodeBridgePermissionManifest.ANDROID_PERMISSION_INTERNET),
                manifest.androidPermissions,
            )
            assertEquals("autojs6-packaged-capability-metadata-v1", manifest.packagedMetadata.schema)
            assertEquals("partial", manifest.packagedMetadata.status)
            assertEquals("desktop_compat_opt_in", manifest.packagedMetadata.profile)
            assertEquals(
                listOf("debug_unsafe_lab", "desktop_compat_opt_in", "pro_compat_opt_in"),
                manifest.packagedMetadata.requestedProfiles,
            )
            assertEquals(listOf("crypto", "fs", "path"), manifest.packagedMetadata.builtins)
            assertEquals(listOf("native/addon.node", "native/sqlite.node"), manifest.packagedMetadata.nativeAssets)
            assertEquals(listOf("app_data", "cache", "documents"), manifest.packagedMetadata.filesystemRoots)
            assertEquals("interactive_long_running", manifest.packagedMetadata.executionMode)
            assertTrue(manifest.packagedMetadata.longRunning)
            assertTrue(manifest.packagedMetadata.network.requested)
            assertTrue(manifest.packagedMetadata.network.declaredCapability)
            assertTrue(manifest.packagedMetadata.metadataOnly)
            assertFalse(manifest.packagedMetadata.grantsAuthority)

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (async function main() {
                      const bridge = require("autojs6:bridge");
                      const profile = require("autojs6:profile");
                      const packagedProfile = profile.packagedCapabilityProfile;
                      const report = bridge.permissions();
                      const metadata = report.packagedMetadata;
                      const profileMetadata = profile.bridgePermissions.packagedMetadata;
                      const requests = [];
                      bridge.__test.setTransport({
                        postMessage(message) {
                          requests.push(JSON.parse(message));
                        }
                      });

                      let deniedCode = "";
                      let deniedMissing = "";
                      try {
                        await require("accessibility").isEnabled({ timeoutMs: 1000 });
                      } catch (error) {
                        deniedCode = String(error && (error.autojs6Code || error.code || ""));
                        deniedMissing = Array.isArray(error && error.missingCapabilities)
                          ? error.missingCapabilities.join(",")
                          : "";
                      }

                      const profileOk =
                        packagedProfile.status === "packaged_metadata_partial" &&
                        packagedProfile.metadataSchema === "autojs6-packaged-capability-metadata-v1" &&
                        packagedProfile.permissionManifest === "node_permissions_enforced" &&
                        packagedProfile.androidManifestSynthesis === "bridge_android_permissions_only" &&
                        packagedProfile.profileMetadata === "diagnostic_only" &&
                        packagedProfile.nodeBuiltins === "declared_metadata_only_no_authority" &&
                        packagedProfile.nativeAssets === "declared_metadata_only_denied" &&
                        packagedProfile.filesystemRoots === "declared_metadata_only_scoped_default" &&
                        packagedProfile.network === "network_capability_maps_android_internet" &&
                        packagedProfile.longRunning === "execution_mode_metadata_partial" &&
                        packagedProfile.undeclaredCapabilities === "rejected_before_dispatch" &&
                        packagedProfile.rawProfilePromotion === false &&
                        packagedProfile.rawBuiltinPromotion === false &&
                        packagedProfile.nativeAssetLoading === false &&
                        packagedProfile.expandedFilesystemRoots === false &&
                        packagedProfile.unreviewedAndroidPermissions === false &&
                        Object.isFrozen(packagedProfile) &&
                        Object.isFrozen(packagedProfile.targetProfiles) &&
                        Object.isFrozen(packagedProfile.metadataSources);

                      const metadataOk =
                        metadata.schema === "autojs6-packaged-capability-metadata-v1" &&
                        metadata.status === "partial" &&
                        metadata.profile === "desktop_compat_opt_in" &&
                        metadata.requestedProfiles.join(",") === "debug_unsafe_lab,desktop_compat_opt_in,pro_compat_opt_in" &&
                        metadata.builtins.join(",") === "crypto,fs,path" &&
                        metadata.nativeAssets.join(",") === "native/addon.node,native/sqlite.node" &&
                        metadata.filesystemRoots.join(",") === "app_data,cache,documents" &&
                        metadata.executionMode === "interactive_long_running" &&
                        metadata.longRunning === true &&
                        metadata.network.requested === true &&
                        metadata.network.declaredCapability === true &&
                        metadata.network.androidPermissions.join(",") === "android.permission.INTERNET" &&
                        metadata.metadataOnly === true &&
                        metadata.grantsAuthority === false &&
                        profileMetadata.profile === metadata.profile &&
                        Object.isFrozen(metadata) &&
                        Object.isFrozen(metadata.requestedProfiles) &&
                        Object.isFrozen(metadata.network);

                      console.log("p13packaged.profile=" + [
                        packagedProfile.status,
                        packagedProfile.defaultEnabled,
                        packagedProfile.targetProfiles.join(","),
                        packagedProfile.metadataSchema,
                        packagedProfile.permissionManifest,
                        packagedProfile.androidManifestSynthesis,
                        packagedProfile.nodeBuiltins,
                        packagedProfile.nativeAssets,
                        packagedProfile.filesystemRoots,
                        packagedProfile.network,
                        packagedProfile.longRunning,
                        packagedProfile.undeclaredCapabilities,
                        packagedProfile.rawProfilePromotion,
                        packagedProfile.rawBuiltinPromotion,
                        packagedProfile.nativeAssetLoading,
                        packagedProfile.expandedFilesystemRoots,
                        packagedProfile.unreviewedAndroidPermissions,
                        Object.isFrozen(packagedProfile)
                      ].join("|"));
                      console.log("p13packaged.metadata=" + [
                        metadata.schema,
                        metadata.status,
                        metadata.profile,
                        metadata.requestedProfiles.join(","),
                        metadata.builtins.join(","),
                        metadata.nativeAssets.join(","),
                        metadata.filesystemRoots.join(","),
                        metadata.executionMode,
                        metadata.longRunning,
                        metadata.network.requested,
                        metadata.network.declaredCapability,
                        metadata.network.androidPermissions.join(","),
                        metadata.metadataOnly,
                        metadata.grantsAuthority,
                        metadata.sources.length > 0,
                        Object.isFrozen(metadata),
                        Object.isFrozen(metadata.network)
                      ].join("|"));
                      console.log("p13packaged.denial=" + [deniedCode, deniedMissing, requests.length].join("|"));
                      console.log("p13packaged.ready=" + [profileOk, metadataOk].join("|"));
                    })();
                """.trimIndent(),
                sourceName = "<node-packaged-capability-phase13-p13-45.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-45 packaged capability metadata", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13packaged.profile=packaged_metadata_partial|true|pro_compat_opt_in,desktop_compat_opt_in|" +
                        "autojs6-packaged-capability-metadata-v1|node_permissions_enforced|" +
                        "bridge_android_permissions_only|declared_metadata_only_no_authority|" +
                        "declared_metadata_only_denied|declared_metadata_only_scoped_default|" +
                        "network_capability_maps_android_internet|execution_mode_metadata_partial|" +
                        "rejected_before_dispatch|false|false|false|false|false|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13packaged.metadata=autojs6-packaged-capability-metadata-v1|partial|desktop_compat_opt_in|" +
                        "debug_unsafe_lab,desktop_compat_opt_in,pro_compat_opt_in|crypto,fs,path|" +
                        "native/addon.node,native/sqlite.node|app_data,cache,documents|interactive_long_running|" +
                        "true|true|true|android.permission.INTERNET|true|false|true|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13packaged.denial=${NodeRuntimeErrorCodes.BRIDGE_CAPABILITY_NOT_DECLARED}|accessibility|0",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13packaged.ready=true|true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun packagedCapabilityMetadataWarningsReachRuntimeReports() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-45 packaged capability metadata warnings")

        val workDir = File(context.cacheDir, "node-packaged-capability-warnings-p13-45-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-45 packaged metadata warnings work dir: $absolutePath" }
        }
        File(workDir, "project.json").writeText(
            """
            {
              "type": "node",
              "node": {
                "profile": "",
                "profiles": ["desktop_compat_opt_in", 7],
                "builtins": "fs",
                "network": {
                  "enabled": "yes"
                }
              }
            }
            """.trimIndent(),
        )
        File(workDir, "package.json").writeText(
            """
            {
              "name": "p13-packaged-capability-metadata-warnings",
              "autojs6": {
                "node": {
                  "nativeAssets": ["", "native/addon.node"],
                  "filesystemRoots": "cache"
                }
              }
            }
            """.trimIndent(),
        )

        try {
            val manifest = NodeBridgePermissionManifest.fromWorkingDirectory(workDir.absolutePath)
            assertTrue(manifest.packagedMetadata.warnings.any { it.contains("project.json:node.profile") })
            assertTrue(manifest.packagedMetadata.warnings.any { it.contains("project.json:node.builtins") })
            assertTrue(manifest.packagedMetadata.warnings.any { it.contains("package.json:autojs6.node.filesystemRoots") })
            assertEquals(manifest.packagedMetadata.warnings, manifest.warnings)

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const bridge = require("autojs6:bridge");
                    const profile = require("autojs6:profile");
                    const metadata = bridge.permissions().packagedMetadata;
                    const profileMetadata = profile.bridgePermissions.packagedMetadata;
                    const selectionWarnings = profile.profileSelection.warnings;
                    const warnings = metadata.warnings;
                    const profileWarnings = profileMetadata.warnings;
                    const warningsOk =
                      warnings.some((value) => value.includes("project.json:node.profile")) &&
                      warnings.some((value) => value.includes("project.json:node.builtins")) &&
                      warnings.some((value) => value.includes("package.json:autojs6.node.filesystemRoots")) &&
                      warnings.join("\n") === profileWarnings.join("\n") &&
                      warnings.join("\n") === selectionWarnings.join("\n") &&
                      Object.isFrozen(warnings) &&
                      Object.isFrozen(profileWarnings) &&
                      Object.isFrozen(selectionWarnings) &&
                      metadata.nativeAssets.join(",") === "native/addon.node" &&
                      metadata.requestedProfiles.join(",") === "desktop_compat_opt_in";
                    console.log("p13packaged.warnings=" + [
                      warnings.length,
                      profileWarnings.length,
                      warningsOk,
                      metadata.metadataOnly,
                      metadata.grantsAuthority
                    ].join("|"));
                """.trimIndent(),
                sourceName = "<node-packaged-capability-warnings-phase13-p13-45.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
                runtimeModuleSources = mapOf(NodeBridgePermissionManifest.RUNTIME_MODULE_NAME to manifest.toJson()),
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-45 packaged capability metadata warnings", result)
            assertTrue(result.stdout, result.stdout.contains("p13packaged.warnings=6|6|true|true|false"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
