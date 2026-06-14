package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.autojs.autojs6.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeEsmExperimentalInstrumentationTest {

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
    fun mjsEntryStillRejectsWhenExperimentalFlagIsDisabled() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM experimental bootstrap default-off regression")
        assertFalse(BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED)

        val workDir = freshWorkDir("esm-default-off")
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """console.log("unexpected");""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = false,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("mjs disabled", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.EMBEDDED_NODE_ESM_UNSUPPORTED, result.errorCode)
        assertEquals("false", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("false", result.nativeValues["embedded_script.esm_entry"])
    }

    @Test
    fun experimentalMjsEntrySupportsLocalStaticImportAndTopLevelAwait() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM experimental bootstrap")
        val workDir = freshWorkDir("esm-local-import")
        val entry = File(workDir, "main.mjs")
        val dependency = File(workDir, "dep.mjs").apply {
            writeText(
                """
                export const value = "dep";
                export function suffix() { return "ok"; }
                """.trimIndent(),
            )
        }

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                "nodejs";
                import { value, suffix as s } from "./dep.mjs";
                const combined = await Promise.resolve(value + "." + s());
                console.log("esm.local=" + combined);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("experimental mjs import", result)
        assertTrue(result.stdout.contains("esm.local=dep.ok"))
        assertEquals("true", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertEquals(entry.absolutePath, result.nativeValues["embedded_script.esm_module_graph_root"])
        assertEquals("2", result.nativeValues["embedded_script.esm_module_graph_size"])
        assertTrue(result.nativeValues["embedded_script.esm_module_graph_modules"].orEmpty().contains(dependency.absolutePath))
    }

    @Test
    fun experimentalPackageTypeModuleEntryRunsAsEsm() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package type=module bootstrap")
        val workDir = freshWorkDir("esm-type-module")
        File(workDir, "package.json").writeText("""{"type":"module"}""")
        val entry = File(workDir, "main.js")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const value = await Promise.resolve("type-module");
                console.log("esm.type=" + value);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("experimental type=module entry", result)
        assertTrue(result.stdout.contains("esm.type=type-module"))
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertEquals("type=module accepted by Embedded Node experimental ESM", result.nativeValues["embedded_script.package_type_module_policy"])
    }

    @Test
    fun experimentalLocalStaticImportCannotEscapeWorkingDirectory() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM scoped import guard")
        val parent = freshWorkDir("esm-import-escape-parent")
        val workDir = File(parent, "project").apply { mkdirs() }
        File(parent, "outside.mjs").writeText("""export const value = "outside";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """import { value } from "../outside.mjs"; console.log(value);""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("experimental import escape", result), result.succeeded)
        assertEquals("ERR_AUTOJS6_FS_PATH_ESCAPE", result.errorCode)
        assertEquals("true", result.nativeValues["embedded_script.esm_enabled"])
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertTrue(result.nativeValues["embedded_script.esm_last_denied_reason"].orEmpty().contains("escapes working directory"))
    }

    @Test
    fun experimentalEsmImportsCommonJsDefault() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM importing CommonJS")
        val workDir = freshWorkDir("esm-import-cjs")
        val entry = File(workDir, "main.mjs")
        File(workDir, "bridge.cjs").writeText("""module.exports = { value: "cjs", nested: { ok: true } };""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import pkg from "./bridge.cjs";
                console.log("esm.cjs.default=" + pkg.value + "/" + pkg.nested.ok);
            """.trimIndent(),
            sourceName = entry.absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("experimental esm imports cjs default", result)
        assertTrue(result.stdout.contains("esm.cjs.default=cjs/true"))
    }

    @Test
    fun experimentalEsmImportsSupportedBuiltins() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM importing supported builtins")
        val workDir = freshWorkDir("esm-import-builtins")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import path from "node:path";
                import { EventEmitter } from "node:events";
                const events = new EventEmitter();
                let seen = false;
                events.on("ready", () => { seen = true; });
                events.emit("ready");
                console.log("esm.builtin=" + path.basename("/tmp/entry.txt") + "/" + seen);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("experimental esm imports builtins", result)
        assertTrue(result.stdout.contains("esm.builtin=entry.txt/true"))
    }

    @Test
    fun experimentalEsmImportDisabledBuiltinFails() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM disabled builtin guard")
        val workDir = freshWorkDir("esm-import-disabled-builtin")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """import cp from "node:child_process"; console.log(cp);""",
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("experimental disabled builtin import", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.LEGACY_BUILTIN_DISABLED, result.errorCode)
        assertTrue(result.errorMessage.orEmpty().contains("child_process"))
    }

    @Test
    fun experimentalCommonJsRequireMjsFailsWithRequireEsmCode() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("CJS require ESM boundary")
        val workDir = freshWorkDir("cjs-require-esm")
        File(workDir, "dep.mjs").writeText("""export const value = "esm";""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """require("./dep.mjs");""",
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            esmExperimentalEnabled = true,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("cjs require mjs", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.REQUIRE_ESM_UNSUPPORTED, result.errorCode)
        assertTrue(result.errorMessage.orEmpty().contains("CommonJS require() cannot load experimental ESM modules"))
    }

    @Test
    fun experimentalPackageExportsConditionsSplitByImporterMode() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM package condition selection")
        val workDir = freshWorkDir("esm-package-conditions")
        val packageDir = File(workDir, "node_modules/condition-pkg").apply { mkdirs() }
        File(packageDir, "package.json").writeText(
            """
            {
              "name": "condition-pkg",
              "exports": {
                ".": {
                  "import": "./esm-entry.cjs",
                  "require": "./cjs-entry.cjs",
                  "default": "./default-entry.cjs"
                }
              }
            }
            """.trimIndent(),
        )
        File(packageDir, "esm-entry.cjs").writeText("""module.exports = { value: "import" };""")
        File(packageDir, "cjs-entry.cjs").writeText("""module.exports = { value: "require" };""")
        File(packageDir, "default-entry.cjs").writeText("""module.exports = { value: "default" };""")

        val esm = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import pkg from "condition-pkg";
                console.log("esm.package.condition=" + pkg.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.mjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("esm package import condition", esm)
        assertTrue(esm.stdout.contains("esm.package.condition=import"))
        assertEquals("import", esm.nativeValues["embedded_script.package_resolution_selected_condition"])
        assertTrue(esm.stderr, esm.stderr.contains("[resolve] exports matched request=\".\""))
        assertTrue(esm.stderr, esm.stderr.contains("selectedCondition=\"import\""))

        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()

        val cjs = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const pkg = require("condition-pkg");
                console.log("cjs.package.condition=" + pkg.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            env = mapOf("AUTOJS6_NODE_RESOLVE_TRACE" to "1"),
            esmExperimentalEnabled = true,
        )

        NodeEmbeddedTestHarness.assertSucceeded("cjs package require condition", cjs)
        assertTrue(cjs.stdout.contains("cjs.package.condition=require"))
        assertEquals("require", cjs.nativeValues["embedded_script.package_resolution_selected_condition"])
        assertTrue(cjs.stderr, cjs.stderr.contains("[resolve] exports matched request=\".\""))
        assertTrue(cjs.stderr, cjs.stderr.contains("selectedCondition=\"require\""))
    }

    private fun freshWorkDir(name: String): File {
        return File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
