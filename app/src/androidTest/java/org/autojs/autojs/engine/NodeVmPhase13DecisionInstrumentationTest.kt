package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeVmPhase13DecisionInstrumentationTest {

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
    fun vmProfileKeepsModuleDeniedAndEnablementDeferred() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-36 vm module decision")

        val workDir = File(context.cacheDir, "node-vm-p13-36-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create P13-36 vm work dir: $absolutePath" }
        }
        try {
            File(workDir, "node_modules/vm").apply {
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
                    const vmProfile = profile.vmProfile || {};

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
                        console.log("p13vm." + label + "=" + detailOf(error));
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("p13vm.profile=" + [
                      vmProfile.status,
                      vmProfile.defaultEnabled,
                      Array.isArray(vmProfile.targetProfiles) && vmProfile.targetProfiles.join(","),
                      vmProfile.module,
                      vmProfile.script,
                      vmProfile.context,
                      vmProfile.timeout,
                      vmProfile.compileFunction,
                      vmProfile.syntheticModule,
                      vmProfile.sourceTextModule,
                      vmProfile.dynamicImportHooks,
                      vmProfile.bridgeModules,
                      vmProfile.processAccess,
                      vmProfile.scopedFs,
                      vmProfile.privateBindings,
                      vmProfile.hostObjects,
                      vmProfile.memoryLimits,
                      vmProfile.packagedBehavior,
                      vmProfile.rawGlobalAccess,
                      Object.isFrozen(vmProfile),
                      Object.isFrozen(vmProfile.targetProfiles)
                    ].join("|"));
                    console.log("p13vm.boundary=" + [
                      profile.featureFlags.vm.enabled,
                      profile.featureFlags.vm.defaultEnabled,
                      profile.featureFlags.vm.projectJsonOverrideAllowed,
                      profile.featureFlags.vm.packagedApkOverrideAllowed,
                      profile.disabledBuiltins.includes("vm"),
                      typeof globalThis.__autojs6_vm,
                      typeof globalThis.vm
                    ].join("|"));
                    expectDenied("requireBare", () => require("vm"));
                    expectDenied("requireNodePrefix", () => require("node:vm"));
                    expectDenied("resolveBare", () => require.resolve("vm"));
                    expectDenied("resolveNodePrefix", () => require.resolve("node:vm"));
                    console.log("p13vm.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/vm/");
                    }));
                    console.log("p13vm.ready=true");
                """.trimIndent(),
                sourceName = "<node-vm-phase13-p13-36.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-36 vm module decision", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13vm.profile=disabled_by_default|false|desktop_compat_opt_in,debug_unsafe_lab|" +
                        "denied|deferred|deferred|required_before_enablement|denied|denied|denied|" +
                        "denied|denied|denied_for_future_contexts|must_preserve|denied|denied|" +
                        "not_defined|not_promoted|false|true|true",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13vm.boundary=false|false|false|false|true|undefined|undefined"))
            assertTrue(
                result.stdout,
                result.stdout.contains("p13vm.requireBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13vm.requireNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13vm.resolveBare=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(
                result.stdout,
                result.stdout.contains("p13vm.resolveNodePrefix=ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED/ERR_AUTOJS6_BUILTIN_DISABLED/ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED"),
            )
            assertTrue(result.stdout, result.stdout.contains("p13vm.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13vm.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
