package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeNativeAddonPolicyInstrumentationTest {

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
    fun nativeAddonEntrypointsRejectWithStableCode() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node native addon policy regression")

        val workDir = File(context.cacheDir, "node-native-addon-policy-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create native addon policy work dir: $absolutePath" }
        }
        try {
            File(workDir, "main.js").writeText(""""nodejs";""", StandardCharsets.UTF_8)
            File(workDir, "tool.js").writeText("""module.exports = {};""", StandardCharsets.UTF_8)
            File(workDir, "native.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            File(workDir, "package.json").writeText(
                """{"name":"native-policy-root","imports":{"#native":"./native.node"}}""",
                StandardCharsets.UTF_8,
            )
            File(workDir, "node_modules/pkg-main").apply {
                mkdirs()
                File(this, "package.json").writeText("""{"name":"pkg-main","main":"native.node"}""", StandardCharsets.UTF_8)
                File(this, "native.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            }
            File(workDir, "node_modules/pkg-exports").apply {
                mkdirs()
                File(this, "package.json").writeText("""{"name":"pkg-exports","exports":"./native.node"}""", StandardCharsets.UTF_8)
                File(this, "native.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            }
            File(workDir, "node_modules/pkg-subpath").apply {
                mkdirs()
                File(this, "package.json").writeText("""{"name":"pkg-subpath","main":"index.js"}""", StandardCharsets.UTF_8)
                File(this, "index.js").writeText("""module.exports = true;""", StandardCharsets.UTF_8)
                File(this, "addon.node").writeText("native-addon-placeholder\n", StandardCharsets.UTF_8)
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    (() => {
                      function codeOf(error) {
                        return String(error && (error.autojs6Code || error.code || error.name || ""));
                      }
                      function detailOf(error) {
                        return [
                          error && error.code,
                          error && error.autojs6Code,
                          error && error.legacyCode
                        ].filter(Boolean).join("/");
                      }
                      function expectNativeAddonDenied(label, action) {
                        try {
                          action();
                        } catch (error) {
                          const code = codeOf(error);
                          const message = String(error && error.message || "");
                          if (code !== "ERR_AUTOJS6_NATIVE_ADDON_DISABLED") {
                            throw new Error(label + " expected ERR_AUTOJS6_NATIVE_ADDON_DISABLED but got " + detailOf(error) + ": " + message);
                          }
                          if (message.indexOf(".node") < 0 || message.indexOf("Native addon loading is disabled") < 0) {
                            throw new Error(label + " message missing native addon context: " + message);
                          }
                          console.log("native_addon." + label + "=" + detailOf(error));
                          return;
                        }
                        throw new Error(label + " was unexpectedly allowed");
                      }

                      expectNativeAddonDenied("local", () => require("./native.node"));
                      expectNativeAddonDenied("package_main", () => require("pkg-main"));
                      expectNativeAddonDenied("package_exports", () => require("pkg-exports"));
                      expectNativeAddonDenied("package_subpath", () => require("pkg-subpath/addon.node"));
                      expectNativeAddonDenied("package_imports", () => require("#native"));
                      expectNativeAddonDenied("create_require", () => require("module").createRequire("./tool.js")("./native.node"));

                      const profile = require("autojs6:profile");
                      console.log("native_addon.profile=" + profile.featureFlags.native_addon.enabled + "/" + profile.errorCodes.nativeAddonDisabled);
                      console.log("native_addon.ready=true");
                    })();
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node native addon policy regression", result)
            listOf(
                "local",
                "package_main",
                "package_exports",
                "package_subpath",
                "package_imports",
                "create_require",
            ).forEach { label ->
                assertTrue(
                    NodeEmbeddedTestHarness.describeResult("missing native addon denial $label", result),
                    result.stdout.contains("native_addon.$label=ERR_AUTOJS6_NATIVE_ADDON_DISABLED"),
                )
            }
            assertTrue(result.stdout.contains("native_addon.profile=false/ERR_AUTOJS6_NATIVE_ADDON_DISABLED"))
            assertTrue(result.stdout.contains("native_addon.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
