package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeInspectorPhase13DecisionInstrumentationTest {

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
    fun inspectorProfileKeepsDebugToolingDeniedAndDeferred() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-37 inspector debug profile")

        val workDir = File(context.cacheDir, "node-inspector-p13-37-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-37 inspector work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/inspector").apply {
                mkdirs()
                File(this, "index.js").writeText(
                    """module.exports = { shadowed: true };""" + "\n",
                    StandardCharsets.UTF_8,
                )
            }

            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const profile = require("autojs6:profile");
                    const inspectorProfile = profile.inspectorProfile || {};

                    function detailOf(error) {
                      return [
                        error && error.code,
                        error && error.autojs6Code,
                        error && error.legacyCode
                      ].filter(Boolean).join("/");
                    }
                    function expectDenied(label, action) {
                      try {
                        action();
                      } catch (error) {
                        console.log("p13inspector." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("p13inspector.profile=" + [
                      inspectorProfile.status,
                      inspectorProfile.defaultEnabled,
                      Array.isArray(inspectorProfile.targetProfiles) && inspectorProfile.targetProfiles.join(","),
                      inspectorProfile.module,
                      inspectorProfile.devtools,
                      inspectorProfile.inspectorProtocol,
                      inspectorProfile.adbForwarding,
                      inspectorProfile.explicitUserAction,
                      inspectorProfile.releaseBuild,
                      inspectorProfile.packagedBehavior,
                      inspectorProfile.portBinding,
                      inspectorProfile.remoteConnections,
                      inspectorProfile.bridgeModules,
                      inspectorProfile.processAccess,
                      inspectorProfile.heapSnapshot,
                      inspectorProfile.cpuProfiler,
                      inspectorProfile.sensitivePathRedaction,
                      inspectorProfile.rawSockets,
                      Object.isFrozen(inspectorProfile),
                      Object.isFrozen(inspectorProfile.targetProfiles)
                    ].join("|"));
                    console.log("p13inspector.boundary=" + [
                      profile.featureFlags.inspector.enabled,
                      profile.featureFlags.inspector.defaultEnabled,
                      profile.featureFlags.inspector.projectJsonOverrideAllowed,
                      profile.featureFlags.inspector.packagedApkOverrideAllowed,
                      profile.disabledBuiltins.includes("inspector"),
                      typeof globalThis.__autojs6_inspector,
                      typeof globalThis.inspector,
                      process.features.inspector,
                      process.features.debug
                    ].join("|"));
                    expectDenied("requireBare", () => require("inspector"));
                    expectDenied("requireNodePrefix", () => require("node:inspector"));
                    expectDenied("resolveBare", () => require.resolve("inspector"));
                    expectDenied("resolveNodePrefix", () => require.resolve("node:inspector"));
                    console.log("p13inspector.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/inspector/");
                    }));
                    console.log("p13inspector.ready=true");
                """.trimIndent(),
                sourceName = "<node-inspector-phase13-p13-37.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-37 inspector debug profile", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13inspector.profile=disabled_by_default|false|debug_unsafe_lab|" +
                        "denied|deferred|deferred|required_before_enablement|required_before_enablement|" +
                        "denied|not_promoted|denied|denied|denied|denied|denied|denied|" +
                        "not_proven|false|true|true",
                ),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13inspector.boundary=false|false|false|false|true|undefined|undefined|false|false"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13inspector.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13inspector.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13inspector.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13inspector.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13inspector.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13inspector.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
