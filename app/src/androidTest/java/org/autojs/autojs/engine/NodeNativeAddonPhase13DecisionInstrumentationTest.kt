package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeNativeAddonPhase13DecisionInstrumentationTest {

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
    fun nativeAddonProfileKeepsNodeFilesAndDlopenDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-39 native addon loading decision")

        val workDir = File(context.cacheDir, "node-native-addon-p13-39-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-39 native addon work dir: $absolutePath" }
        }
        try {
            File(workDir, "native.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            File(workDir, "node_modules/native-addon-main").apply {
                mkdirs()
                File(this, "package.json").writeText("""{"name":"native-addon-main","main":"addon.node"}""", StandardCharsets.UTF_8)
                File(this, "addon.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    const nativeAddonProfile = profile.nativeAddonProfile || {};

                    function detailOf(error) {
                      return [
                        error && error.code,
                        error && error.autojs6Code,
                        error && error.legacyCode
                      ].filter(Boolean).join("/");
                    }
                    function expectDenied(label, action, expectedCode) {
                      try {
                        action();
                      } catch (error) {
                        const detail = detailOf(error);
                        if (expectedCode && detail.indexOf(expectedCode) < 0) {
                          throw new Error(label + " expected " + expectedCode + " but got " + detail + ": " + (error && error.message));
                        }
                        console.log("p13native." + label + "=" + detail);
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("p13native.profile=" + [
                      nativeAddonProfile.status,
                      nativeAddonProfile.defaultEnabled,
                      Array.isArray(nativeAddonProfile.targetProfiles) && nativeAddonProfile.targetProfiles.join(","),
                      nativeAddonProfile.module,
                      nativeAddonProfile.requireExtension,
                      nativeAddonProfile.processDlopen,
                      nativeAddonProfile.processBinding,
                      nativeAddonProfile.nodeGyp,
                      nativeAddonProfile.downloadedArtifacts,
                      nativeAddonProfile.signedPackagedOnly,
                      nativeAddonProfile.abiAllowlist,
                      nativeAddonProfile.hashAllowlist,
                      nativeAddonProfile.dependencyScan,
                      nativeAddonProfile.crashIsolation,
                      nativeAddonProfile.packagedBehavior,
                      nativeAddonProfile.jniBridge,
                      nativeAddonProfile.rawDlopen,
                      nativeAddonProfile.nativeMemoryAccess,
                      nativeAddonProfile.rawNativeHandles,
                      nativeAddonProfile.downloadExec,
                      Object.isFrozen(nativeAddonProfile),
                      Object.isFrozen(nativeAddonProfile.targetProfiles)
                    ].join("|"));
                    console.log("p13native.boundary=" + [
                      profile.nativeAddon,
                      profile.highRiskCapabilities.nativeAddon,
                      profile.featureFlags.native_addon.enabled,
                      profile.featureFlags.native_addon.defaultEnabled,
                      profile.featureFlags.native_addon.projectJsonOverrideAllowed,
                      profile.featureFlags.native_addon.packagedApkOverrideAllowed,
                      profile.featureFlags.native_addon.releaseForbidden,
                      profile.errorCodes.nativeAddonDisabled,
                      typeof process.dlopen,
                      typeof process.binding,
                      typeof process._linkedBinding,
                      typeof globalThis.__autojs6_native_addon,
                      typeof globalThis.nativeAddon
                    ].join("|"));

                    expectDenied("requireRelativeNode", () => require("./native.node"), "ERR_AUTOJS6_NATIVE_ADDON_DISABLED");
                    expectDenied("resolveRelativeNode", () => require.resolve("./native.node"), "ERR_AUTOJS6_NATIVE_ADDON_DISABLED");
                    expectDenied("requirePackageMain", () => require("native-addon-main"), "ERR_AUTOJS6_NATIVE_ADDON_DISABLED");
                    expectDenied("resolvePackageMain", () => require.resolve("native-addon-main"), "ERR_AUTOJS6_NATIVE_ADDON_DISABLED");
                    expectDenied("processDlopen", () => process.dlopen({}, "./native.node"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
                    expectDenied("processBinding", () => process.binding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
                    expectDenied("linkedBinding", () => process._linkedBinding("fs"), "ERR_AUTOJS6_PROCESS_API_DISABLED");
                    console.log("p13native.loaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").endsWith("/native.node") ||
                        filename.replace(/\\/g, "/").includes("/native-addon-main/addon.node");
                    }));
                    console.log("p13native.ready=true");
                """.trimIndent(),
                sourceName = "<node-native-addon-phase13-p13-39.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-39 native addon loading decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13native.profile=disabled_by_default|false|desktop_compat_opt_in|" +
                        "denied|denied|denied|denied|denied|denied|required_before_enablement|" +
                        "required_before_enablement|required_before_enablement|required_before_enablement|" +
                        "required_before_enablement|not_promoted|denied|denied|denied|false|false|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13native.boundary=false|false|false|false|false|false|true|" +
                        "ERR_AUTOJS6_NATIVE_ADDON_DISABLED|function|function|function|undefined|undefined",
                ),
            )
            listOf(
                "p13native.requireRelativeNode=ERR_AUTOJS6_NATIVE_ADDON_DISABLED",
                "p13native.resolveRelativeNode=ERR_AUTOJS6_NATIVE_ADDON_DISABLED",
                "p13native.requirePackageMain=ERR_AUTOJS6_NATIVE_ADDON_DISABLED",
                "p13native.resolvePackageMain=ERR_AUTOJS6_NATIVE_ADDON_DISABLED",
                "p13native.processDlopen=ERR_AUTOJS6_PROCESS_API_DISABLED",
                "p13native.processBinding=ERR_AUTOJS6_PROCESS_API_DISABLED",
                "p13native.linkedBinding=ERR_AUTOJS6_PROCESS_API_DISABLED",
            ).forEach { token ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing P13-39 native addon denial $token", result),
                    result.stdout.contains(token),
                )
            }
            assertTrue(result.stdout, result.stdout.contains("p13native.loaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13native.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
