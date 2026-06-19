package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeModuleSafeSubsetInstrumentationTest {

    private val context
        get() = NodeEmbeddedTestHarness.targetContext

    @Before
    fun setUp() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @After
    fun tearDown() {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
    }

    @Test
    fun moduleBuiltinExposesOnlySafeSubsetAndCannotBeShadowed() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("node:module safe subset")
        val workDir = freshWorkDir("node-module-safe-surface")
        File(workDir, "node_modules/module/index.js").apply {
            parentFile?.mkdirs()
            writeText("""module.exports = { shadow: true };""")
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const moduleBuiltin = require("module");
                const nodeModuleBuiltin = require("node:module");
                const keys = Object.keys(moduleBuiltin).join(",");
                const disabled = ["child_process", "node:worker_threads", "vm", "node:inspector", "dgram"];
                const disabledState = disabled.map((name) => moduleBuiltin.isBuiltin(name)).join("/");
                const disabledListed = disabled.some((name) => {
                  const bare = name.replace(/^node:/, "");
                  return moduleBuiltin.builtinModules.includes(bare);
                });
                const allowed = [
                  "module",
                  "node:module",
                  "fs",
                  "node:fs/promises",
                  "path/posix",
                  "node:stream/web",
                  "dns",
                  "node:http",
                  "tls"
                ].map((name) => moduleBuiltin.isBuiltin(name)).join("/");
                const shadowLoaded = Object.keys(require.cache).some((filename) => {
                  return filename.replace(/\\/g, "/").includes("/node_modules/module/");
                });
                console.log("module.safe.same=" + (moduleBuiltin === nodeModuleBuiltin));
                console.log("module.safe.frozen=" + Object.isFrozen(moduleBuiltin));
                console.log("module.safe.keys=" + keys);
                console.log("module.safe.dangerous=" + (
                  moduleBuiltin.Module === undefined &&
                  moduleBuiltin._load === undefined &&
                  moduleBuiltin._resolveFilename === undefined &&
                  moduleBuiltin._compile === undefined &&
                  moduleBuiltin.wrap === undefined &&
                  moduleBuiltin.register === undefined &&
                  moduleBuiltin.registerHooks === undefined &&
                  moduleBuiltin.globalPaths === undefined
                ));
                console.log("module.safe.disabledState=" + disabledState);
                console.log("module.safe.disabledListed=" + disabledListed);
                console.log("module.safe.allowed=" + allowed);
                console.log("module.safe.shadow=" + (moduleBuiltin.shadow !== true && !shadowLoaded));
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("node:module safe surface", result)
        assertTrue(result.stdout.contains("module.safe.same=true"))
        assertTrue(result.stdout.contains("module.safe.frozen=true"))
        assertTrue(result.stdout.contains("module.safe.keys=createRequire,isBuiltin,builtinModules,syncBuiltinESMExports"))
        assertTrue(result.stdout.contains("module.safe.dangerous=true"))
        assertTrue(result.stdout.contains("module.safe.disabledState=false/false/false/false/false"))
        assertTrue(result.stdout.contains("module.safe.disabledListed=false"))
        assertTrue(result.stdout.contains("module.safe.allowed=true/true/true/true/true/true/true/true/true"))
        assertTrue(result.stdout.contains("module.safe.shadow=true"))
    }

    @Test
    fun createRequireLoadsScopedLocalAndPackageModules() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("module.createRequire scoped loading")
        val workDir = freshWorkDir("node-module-create-require")
        File(workDir, "child.js").writeText(
            """
            globalThis.__autojs6ModuleSafeChildLoads = (globalThis.__autojs6ModuleSafeChildLoads || 0) + 1;
            exports.value = "child";
            """.trimIndent(),
        )
        File(workDir, "node_modules/pkg").mkdirs()
        File(workDir, "node_modules/pkg/package.json").writeText("""{"name":"pkg","main":"index.js"}""")
        File(workDir, "node_modules/pkg/index.js").writeText("""exports.value = "pkg";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const { createRequire } = require("node:module");
                const req = createRequire(__filename);
                const childA = req("./child");
                const childB = require("./child");
                const pkg = req("pkg");
                console.log("module.safe.createRequire.local=" + childA.value);
                console.log("module.safe.createRequire.pkg=" + pkg.value);
                console.log("module.safe.createRequire.cache=" + (
                  childA === childB &&
                  globalThis.__autojs6ModuleSafeChildLoads === 1 &&
                  require.cache[req.resolve("./child")].exports === childA
                ));
                console.log("module.safe.createRequire.surface=" + (
                  req.cache === require.cache &&
                  req.extensions === require.extensions &&
                  req.main === require.main
                ));
                try {
                  req("child_process");
                  console.log("module.safe.createRequire.childProcess=allowed");
                } catch (error) {
                  console.log("module.safe.createRequire.childProcess=" + [
                    error.code,
                    error.autojs6Code,
                    /child_process/.test(String(error.message))
                  ].join(":"));
                }
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("module.createRequire scoped loading", result)
        assertTrue(result.stdout.contains("module.safe.createRequire.local=child"))
        assertTrue(result.stdout.contains("module.safe.createRequire.pkg=pkg"))
        assertTrue(result.stdout.contains("module.safe.createRequire.cache=true"))
        assertTrue(result.stdout.contains("module.safe.createRequire.surface=true"))
        assertTrue(result.stdout.contains("module.safe.createRequire.childProcess=${NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED}:${NodeRuntimeErrorCodes.BUILTIN_DISABLED}:true"))
    }

    @Test
    fun createRequireDeniesUnsafeBasesAndDangerousTargets() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("module.createRequire denial paths")
        val parent = freshWorkDir("node-module-create-require-deny-parent")
        val workDir = File(parent, "project").apply { mkdirs() }
        File(parent, "outside.js").writeText("""module.exports = "outside";""")
        File(workDir, "native.node").writeText("")
        File(workDir, "esm.mjs").writeText("""export default "esm";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                const { createRequire } = require("module");
                const req = createRequire(__filename);
                function check(label, fn) {
                  try {
                    fn();
                    console.log(label + ".unexpected=true");
                  } catch (error) {
                    console.log(label + ".code=" + error.code);
                    console.log(label + ".message=" + /module\.createRequire|ESM\/import|Unsupported Embedded Node local module extension/.test(String(error.message)));
                  }
                }
                check("module.safe.createRequire.escape", () => createRequire("../outside.js"));
                check("module.safe.createRequire.absolute", () => createRequire("${File(parent, "outside.js").absolutePath.replace("\\", "\\\\")}"));
                check("module.safe.createRequire.native", () => req("./native.node"));
                check("module.safe.createRequire.esm", () => req("./esm.mjs"));
            """.trimIndent(),
            sourceName = File(workDir, "main.js").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("module.createRequire denial paths", result)
        assertTrue(result.stdout.contains("module.safe.createRequire.escape.code=EPERM"))
        assertTrue(result.stdout.contains("module.safe.createRequire.absolute.code=EPERM"))
        assertTrue(result.stdout.contains("module.safe.createRequire.native.message=true"))
        assertTrue(result.stdout.contains("module.safe.createRequire.esm.code=${NodeRuntimeErrorCodes.REQUIRE_ESM_UNSUPPORTED}"))
        assertFalse(result.stdout.contains(".unexpected=true"))
        assertTrue(result.nativeValues["embedded_script.create_require_failure_reason"].orEmpty().contains("module.createRequire"))
    }

    private fun freshWorkDir(name: String): File {
        return File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
