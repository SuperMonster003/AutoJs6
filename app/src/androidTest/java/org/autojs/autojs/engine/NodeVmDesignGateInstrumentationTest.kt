package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeVmDesignGateInstrumentationTest {

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
    fun vmRemainsDesignGatedAndDisabledByDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("vm design gate")
        val vmFlag = NodeRuntimeCapabilities.featureFlagsById["vm"]
        assertEquals("disabled", vmFlag?.status)
        assertEquals("high", vmFlag?.securityLevel)
        assertFalse(vmFlag?.defaultEnabled ?: true)
        assertFalse(vmFlag?.buildTimeOverrideAllowed ?: true)
        assertFalse(vmFlag?.projectJsonOverrideAllowed ?: true)
        assertFalse(vmFlag?.packagedApkOverrideAllowed ?: true)
        assertTrue(NodeRuntimeCapabilities.disabledBuiltinModuleNames.contains("vm"))

        val workDir = File(context.cacheDir, "node-vm-design-gate-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create vm design gate work dir: $absolutePath" }
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
                        const detail = detailOf(error);
                        if (
                          error.code !== "ERR_AUTOJS6_EMBEDDED_NODE_BUILTIN_DISABLED" ||
                          error.autojs6Code !== "ERR_AUTOJS6_BUILTIN_DISABLED"
                        ) {
                          throw new Error(label + " expected builtin disabled but got " + detail);
                        }
                        console.log("vm." + label + "=" + detail);
                        return;
                      }
                      throw new Error(label + " was unexpectedly allowed");
                    }

                    console.log("vm.profile=" + [
                      profile.featureFlags.vm.enabled,
                      profile.featureFlags.vm.defaultEnabled,
                      profile.featureFlags.vm.buildTimeOverrideAllowed,
                      profile.featureFlags.vm.projectJsonOverrideAllowed,
                      profile.featureFlags.vm.packagedApkOverrideAllowed,
                      profile.disabledBuiltins.includes("vm")
                    ].join("/"));
                    console.log("vm.profileFrozen=" + (
                      Object.isFrozen(profile.featureFlags.vm) &&
                      Object.isFrozen(profile.disabledBuiltins)
                    ));

                    expectDenied("requireBare", () => require("vm"));
                    expectDenied("requireNodePrefix", () => require("node:vm"));
                    expectDenied("resolveBare", () => require.resolve("vm"));
                    expectDenied("resolveNodePrefix", () => require.resolve("node:vm"));

                    console.log("vm.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/vm/");
                    }));
                    console.log("vm.ready=true");
                """.trimIndent(),
                sourceName = "<node-vm-design-gate.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("vm design gate", result)
            assertTrue(result.stdout, result.stdout.contains("vm.profile=false/false/false/false/false/true"))
            assertTrue(result.stdout, result.stdout.contains("vm.profileFrozen=true"))
            assertTrue(result.stdout, result.stdout.contains("vm.requireBare=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}"))
            assertTrue(result.stdout, result.stdout.contains("vm.requireNodePrefix=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}"))
            assertTrue(result.stdout, result.stdout.contains("vm.resolveBare=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}"))
            assertTrue(result.stdout, result.stdout.contains("vm.resolveNodePrefix=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.BUILTIN_DISABLED}/${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}"))
            assertTrue(result.stdout, result.stdout.contains("vm.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("vm.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
