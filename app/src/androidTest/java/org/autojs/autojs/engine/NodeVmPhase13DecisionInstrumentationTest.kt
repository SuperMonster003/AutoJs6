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
    fun vmProfileRecordsNativeAvailabilityAndShadowProtection() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-36 vm native availability")

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
                    const vm = require("vm");
                    const nodeVm = require("node:vm");
                    const scriptResult = new vm.Script("value + 1").runInNewContext({ value: 41 }, { timeout: 1000 });
                    const context = vm.createContext({ value: 21 });
                    const contextResult = vm.runInContext("value * 2", context, { timeout: 1000 });

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
                      profile.limitedBuiltins.includes("vm"),
                      typeof globalThis.__autojs6_vm,
                      typeof globalThis.vm
                    ].join("|"));
                    console.log("p13vm.module=" + [
                      vm === nodeVm,
                      require.resolve("vm"),
                      require.resolve("node:vm"),
                      typeof vm.Script,
                      typeof vm.createContext,
                      typeof vm.compileFunction,
                      scriptResult,
                      contextResult,
                      vm.shadow !== true
                    ].join("|"));
                    console.log("p13vm.shadowLoaded=" + Object.keys(require.cache).some((filename) => {
                      return filename.replace(/\\/g, "/").includes("/node_modules/vm/");
                    }));
                    console.log("p13vm.ready=true");
                """.trimIndent(),
                sourceName = "<node-vm-phase13-p13-36.js>",
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("P13-36 vm native availability", result)
            assertTrue(
                result.stdout,
                result.stdout.contains(
                    "p13vm.profile=native_available|true|desktop_compat_opt_in,debug_unsafe_lab|" +
                        "native_available|native_available|native_available|native_run_options|" +
                        "native_available|native_available|native_available|native_available|denied|" +
                        "not_security_boundary|must_preserve|denied|not_injected_by_autojs6|" +
                        "native_node_defaults|native_builtin|true|true|true",
                ),
            )
            assertTrue(result.stdout, result.stdout.contains("p13vm.boundary=true|true|false|false|false|true|undefined|undefined"))
            assertTrue(result.stdout, result.stdout.contains("p13vm.module=true|vm|vm|function|function|function|42|42|true"))
            assertTrue(result.stdout, result.stdout.contains("p13vm.shadowLoaded=false"))
            assertTrue(result.stdout, result.stdout.contains("p13vm.ready=true"))
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun vmMemoryCompiledScriptBudgetAndTimeoutStressAreBounded() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("P13-36 vm stress evidence")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const vm = require("vm");
                const v8 = require("v8");
                const beforeHeap = v8.getHeapStatistics().used_heap_size;
                const retainedScripts = [];
                let checksum = 0;
                for (let index = 0; index < 96; index += 1) {
                  const script = new vm.Script("value + " + index);
                  const context = vm.createContext({ value: 42 });
                  checksum += script.runInContext(context, { timeout: 1000 });
                  if (index % 8 === 0) {
                    retainedScripts.push(script);
                  }
                }
                const afterHeap = v8.getHeapStatistics().used_heap_size;
                const heapDelta = Math.max(0, afterHeap - beforeHeap);
                const heapBudgetBytes = 32 * 1024 * 1024;
                let timeoutOk = false;
                let timeoutName = "";
                let timeoutMessageOk = false;
                try {
                  new vm.Script("while (true) {}").runInNewContext({}, { timeout: 25 });
                } catch (error) {
                  timeoutName = error && error.name || "";
                  timeoutMessageOk = String(error && error.message || "").includes("Script execution timed out");
                  timeoutOk = timeoutMessageOk;
                }
                const boundaryProbe = vm.runInNewContext([
                  "typeof globalThis.__autojs6",
                  "typeof globalThis.autojs",
                  "typeof globalThis.require",
                  "typeof globalThis.process"
                ].join(" + '|' + "));
                console.log("p13vm.budget=" + [
                  retainedScripts.length,
                  checksum,
                  heapDelta <= heapBudgetBytes,
                  heapBudgetBytes
                ].join("|"));
                console.log("p13vm.timeout=" + [timeoutOk, timeoutName, timeoutMessageOk].join("|"));
                console.log("p13vm.boundary_probe=" + boundaryProbe);
                console.log("p13vm.stress=true");
            """.trimIndent(),
            sourceName = "<node-vm-phase13-stress-p13-36.js>",
            timeoutMs = 30_000L,
        )

        NodeEmbeddedTestHarness.assertSucceeded("P13-36 vm stress evidence", result)
        val description = NodeEmbeddedTestHarness.describeResult("P13-36 vm stress evidence", result)
        assertTrue(description, result.stdout.contains("p13vm.budget=12|8592|true|33554432"))
        assertTrue(description, result.stdout.contains("p13vm.timeout=true|"))
        assertTrue(description, result.stdout.contains("p13vm.boundary_probe=undefined|undefined|undefined|undefined"))
        assertTrue(description, result.stdout.contains("p13vm.stress=true"))
    }
}
