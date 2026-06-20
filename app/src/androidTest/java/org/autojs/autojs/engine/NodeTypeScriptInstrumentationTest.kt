package org.autojs.autojs.engine

import androidx.test.runner.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeTypeScriptInstrumentationTest {

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
    fun tsEntryRunsAsCommonJsWithLightweightTypeStripping() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript .ts CommonJS entry")
        val workDir = freshWorkDir("ts-cjs")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                type Label = string;
                interface Payload {
                  value: Label;
                }
                const value: Label = ("cjs" as string);
                function suffix<T>(input: T): string {
                  return String(input);
                }
                module.exports = { value, suffix };
                console.log("ts.cjs=" + suffix(value));
            """.trimIndent(),
            sourceName = File(workDir, "main.ts").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("TypeScript .ts CommonJS entry", result)
        assertTrue(result.stdout.contains("ts.cjs=cjs"))
        assertEquals("false", result.nativeValues["embedded_script.esm_entry"])
    }

    @Test
    fun tsEntryFollowsPackageTypeModule() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript .ts type=module entry")
        val workDir = freshWorkDir("ts-type-module")
        File(workDir, "package.json").writeText("""{"type":"module"}""")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                type Label = string;
                const value: Label = await Promise.resolve("module");
                export const named: Label = value;
                console.log("ts.esm.type=" + named);
            """.trimIndent(),
            sourceName = File(workDir, "main.ts").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("TypeScript .ts type=module entry", result)
        assertTrue(result.stdout.contains("ts.esm.type=module"))
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
        assertEquals("type=module accepted by Embedded Node partial ESM", result.nativeValues["embedded_script.package_type_module_policy"])
    }

    @Test
    fun mtsEntryRunsAsEsmWithTypeOnlyImportsRemoved() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript .mts ESM entry")
        val workDir = freshWorkDir("ts-mts")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import type { Label } from "./types.mts";
                type Local = string;
                const value: Local = await Promise.resolve("mts");
                export const named: Local = value;
                console.log("ts.mts=" + named);
            """.trimIndent(),
            sourceName = File(workDir, "main.mts").absolutePath,
            workingDirectory = workDir,
        )

        NodeEmbeddedTestHarness.assertSucceeded("TypeScript .mts ESM entry", result)
        assertTrue(result.stdout.contains("ts.mts=mts"))
        assertEquals("true", result.nativeValues["embedded_script.esm_entry"])
    }

    @Test
    fun ctsModuleCanBeRequiredFromCommonJs() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript .cts CommonJS module")
        val workDir = freshWorkDir("ts-cts")
        val dependency = File(workDir, "dep.cts")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                const dep = require("./dep.cts");
                console.log("ts.cts=" + dep.value);
            """.trimIndent(),
            sourceName = File(workDir, "main.cjs").absolutePath,
            workingDirectory = workDir,
            moduleSources = mapOf(
                dependency.absolutePath to """
                    type Label = string;
                    const value: Label = "cts";
                    module.exports = { value };
                """.trimIndent(),
            ),
        )

        NodeEmbeddedTestHarness.assertSucceeded("TypeScript .cts required module", result)
        assertTrue(result.stdout.contains("ts.cts=cts"))
    }

    @Test
    fun packagedModuleSourcesStripTsAndMtsGraph() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript packaged moduleSources graph")
        val workDir = freshWorkDir("ts-module-sources")
        File(workDir, "package.json").writeText("""{"type":"module"}""")
        val depTs = File(workDir, "dep.ts")
        val dynamicMts = File(workDir, "dynamic.mts")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import { value } from "./dep.ts";
                type Label = string;
                const dynamic = await import("./dynamic.mts");
                const marker: Label = value + ":" + dynamic.suffix;
                console.log("ts.module_sources=" + marker);
            """.trimIndent(),
            sourceName = File(workDir, "main.mts").absolutePath,
            workingDirectory = workDir,
            moduleSources = mapOf(
                depTs.absolutePath to """
                    export type Label = string;
                    export const value: Label = "ts";
                """.trimIndent(),
                dynamicMts.absolutePath to """
                    type Suffix = string;
                    export const suffix: Suffix = "mts";
                """.trimIndent(),
            ),
        )

        NodeEmbeddedTestHarness.assertSucceeded("TypeScript packaged moduleSources graph", result)
        assertTrue(result.stdout.contains("ts.module_sources=ts:mts"))
    }

    @Test
    fun tsxEntryFailsWithExplicitUnsupportedExtension() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript .tsx unsupported extension")
        val workDir = freshWorkDir("ts-tsx")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """export default <View />;""",
            sourceName = File(workDir, "main.tsx").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("TypeScript .tsx unsupported extension", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.TYPESCRIPT_UNSUPPORTED_EXTENSION, result.errorCode)
        assertEquals("tsx", result.nativeValues["embedded_script.typescript.syntax_kind"])
        assertTrue(result.errorMessage.orEmpty().contains("TSX"))
    }

    @Test
    fun unsupportedEnumSyntaxFailsBeforeNativeExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("TypeScript unsupported enum syntax")
        val workDir = freshWorkDir("ts-enum")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                enum Color { Red }
                console.log(Color.Red);
            """.trimIndent(),
            sourceName = File(workDir, "main.ts").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("TypeScript enum unsupported", result), result.succeeded)
        assertEquals(NodeRuntimeErrorCodes.UNSUPPORTED_TYPESCRIPT_SYNTAX, result.errorCode)
        assertEquals("enum", result.nativeValues["embedded_script.typescript.syntax_kind"])
        assertEquals("1", result.nativeValues["embedded_script.typescript.line"])
        assertTrue(result.errorMessage.orEmpty().contains("enum declarations require code generation"))
    }

    private fun freshWorkDir(name: String): File {
        return File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }
}
