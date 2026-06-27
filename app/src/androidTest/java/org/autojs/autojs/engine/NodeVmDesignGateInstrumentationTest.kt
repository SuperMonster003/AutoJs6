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
    fun vmNativeBuiltinIsAvailableAndCannotBeShadowed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("vm native builtin")
        val vmFlag = NodeRuntimeCapabilities.featureFlagsById["vm"]
        assertEquals("partial", vmFlag?.status)
        assertEquals("high", vmFlag?.securityLevel)
        assertTrue(vmFlag?.defaultEnabled ?: false)
        assertFalse(vmFlag?.buildTimeOverrideAllowed ?: true)
        assertFalse(vmFlag?.projectJsonOverrideAllowed ?: true)
        assertFalse(vmFlag?.packagedApkOverrideAllowed ?: true)
        assertTrue(NodeRuntimeCapabilities.limitedBuiltinModuleNames.contains("vm"))
        assertFalse(NodeRuntimeCapabilities.disabledBuiltinModuleNames.contains("vm"))

        val workDir = File(context.cacheDir, "node-vm-native-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create vm native work dir: $absolutePath" }
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
                    const vm = require("vm");
                    const nodeVm = require("node:vm");
                    const script = new vm.Script("answer + 1");
                    const context = vm.createContext({ answer: 41 });
                    const inContext = script.runInContext(context, { timeout: 1000 });
                    const inNewContext = vm.runInNewContext("value * 2", { value: 21 }, { timeout: 1000 });

                    console.log("vm.profile=" + [
                      profile.featureFlags.vm.enabled,
                      profile.featureFlags.vm.defaultEnabled,
                      profile.featureFlags.vm.buildTimeOverrideAllowed,
                      profile.featureFlags.vm.projectJsonOverrideAllowed,
                      profile.featureFlags.vm.packagedApkOverrideAllowed,
                      profile.disabledBuiltins.includes("vm"),
                      profile.limitedBuiltins.includes("vm")
                    ].join("/"));
                    console.log("vm.profileFrozen=" + (
                      Object.isFrozen(profile.featureFlags.vm) &&
                      Object.isFrozen(profile.disabledBuiltins) &&
                      Object.isFrozen(profile.limitedBuiltins)
                    ));
                    console.log("vm.module=" + [
                      vm === nodeVm,
                      require.resolve("vm"),
                      require.resolve("node:vm"),
                      typeof vm.Script,
                      typeof vm.createContext,
                      inContext,
                      inNewContext,
                      vm.shadow !== true
                    ].join("|"));
                    console.log("vm.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/vm/");
                    }));
                    console.log("vm.ready=true");
                """.trimIndent(),
                sourceName = "<node-vm-native.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("vm native builtin", result)
            assertTrue(result.stdout, result.stdout.contains("vm.profile=true/true/false/false/false/false/true"))
            assertTrue(result.stdout, result.stdout.contains("vm.profileFrozen=true"))
            assertTrue(result.stdout, result.stdout.contains("vm.module=true|vm|vm|function|function|42|42|true"))
            assertTrue(result.stdout, result.stdout.contains("vm.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("vm.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }
}
