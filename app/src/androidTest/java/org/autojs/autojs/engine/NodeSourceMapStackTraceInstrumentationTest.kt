package org.autojs.autojs.engine

import android.util.Base64
import androidx.test.runner.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class NodeSourceMapStackTraceInstrumentationTest {

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
    fun inlineSourceMapMapsFailureStack() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("inline source map stack trace")
        val workDir = freshWorkDir("node-sourcemap-inline")
        val sourceMapUrl = sourceMapDataUrl(
            originalSource = "src/inline-original.ts",
            generatedLine = 3,
            originalLine = 42,
        )
        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = failingBundle("inline source map boom", sourceMapUrl),
            sourceName = File(workDir, "inline-bundle.js").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("inline source map", result), result.succeeded)
        assertTrue(result.errorMessage.orEmpty().contains("inline source map boom"))
        assertTrue(result.errorStack.orEmpty(), result.errorStack.orEmpty().contains("src/inline-original.ts:42:"))
        assertEquals("1", result.nativeValues["embedded_script.source_map_loaded_count"])
        assertEquals("0", result.nativeValues["embedded_script.source_map_denied_count"])
        assertTrue(
            result.nativeValues["embedded_script.source_map_mapped_frame_count"].orEmpty(),
            result.nativeValues["embedded_script.source_map_mapped_frame_count"].orEmpty().toInt() >= 1,
        )
    }

    @Test
    fun externalSourceMapInsideWorkingDirectoryMapsFailureStack() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("external source map stack trace")
        val workDir = freshWorkDir("node-sourcemap-external")
        File(workDir, "external-bundle.js.map").writeText(
            sourceMapJson(
                originalSource = "src/external-original.ts",
                generatedLine = 3,
                originalLine = 77,
            ),
            StandardCharsets.UTF_8,
        )

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = failingBundle("external source map boom", "external-bundle.js.map"),
            sourceName = File(workDir, "external-bundle.js").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("external source map", result), result.succeeded)
        assertTrue(result.errorMessage.orEmpty().contains("external source map boom"))
        assertTrue(result.errorStack.orEmpty(), result.errorStack.orEmpty().contains("src/external-original.ts:77:"))
        assertEquals("1", result.nativeValues["embedded_script.source_map_loaded_count"])
        assertEquals("0", result.nativeValues["embedded_script.source_map_denied_count"])
        assertTrue(result.nativeValues["embedded_script.source_map_last_map"].orEmpty().endsWith("external-bundle.js.map"))
    }

    @Test
    fun externalSourceMapOutsideWorkingDirectoryIsDenied() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("source map scoped path denial")
        val parent = freshWorkDir("node-sourcemap-deny-parent")
        val workDir = File(parent, "project").apply { mkdirs() }
        File(parent, "outside.map").writeText(
            sourceMapJson(
                originalSource = "src/outside-original.ts",
                generatedLine = 3,
                originalLine = 90,
            ),
            StandardCharsets.UTF_8,
        )

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = failingBundle("outside source map boom", "../outside.map"),
            sourceName = File(workDir, "outside-bundle.js").absolutePath,
            workingDirectory = workDir,
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("outside source map", result), result.succeeded)
        assertTrue(result.errorMessage.orEmpty().contains("outside source map boom"))
        assertFalse(result.errorStack.orEmpty(), result.errorStack.orEmpty().contains("src/outside-original.ts:90:"))
        assertEquals("0", result.nativeValues["embedded_script.source_map_loaded_count"])
        assertEquals("1", result.nativeValues["embedded_script.source_map_denied_count"])
        assertTrue(
            result.nativeValues["embedded_script.source_map_last_error"].orEmpty(),
            result.nativeValues["embedded_script.source_map_last_error"].orEmpty().contains("escapes working directory"),
        )
    }

    @Test
    fun esmGeneratedStackFramesMapBackToOriginalSourceLines() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("ESM generated stack trace mapping")
        val workDir = freshWorkDir("node-esm-generated-stack")
        val main = File(workDir, "main.mjs")
        val dep = File(workDir, "dep.mjs")
        val named = File(workDir, "named.mjs")

        val result = NodeEmbeddedTestHarness.runEmbeddedScript(
            source = """
                import value from "./dep.mjs";
                import { marker } from "./named.mjs";

                const combined = value + marker;
                function fromConsole() {
                    throw new Error("console mapped boom");
                }

                function fromFailure() {
                    throw new Error("failure mapped boom");
                }

                try {
                    fromConsole();
                } catch (err) {
                    console.error(err);
                }

                fromFailure();
            """.trimIndent(),
            sourceName = main.absolutePath,
            workingDirectory = workDir,
            moduleSources = mapOf(
                dep.absolutePath to """export default "dep";""",
                named.absolutePath to """export const marker = "named";""",
            ),
        )

        assertFalse(NodeEmbeddedTestHarness.describeResult("ESM generated stack trace mapping", result), result.succeeded)
        assertTrue(result.stderr, result.stderr.contains("${main.absolutePath}:6:15"))
        assertFalse(result.stderr, result.stderr.contains("${main.absolutePath}:13:"))
        assertTrue(result.errorStack.orEmpty(), result.errorStack.orEmpty().contains("${main.absolutePath}:10:15"))
        assertFalse(result.errorStack.orEmpty(), result.errorStack.orEmpty().contains("${main.absolutePath}:17:"))
        result.nativeValues["embedded_script.generated_stack_mapped_frame_count"]
            ?.toIntOrNull()
            ?.let { count ->
                assertTrue(count.toString(), count >= 2)
            }
    }

    private fun failingBundle(message: String, sourceMapUrl: String): String {
        return """
            "nodejs";
            function fail() {
              throw new Error("$message");
            }
            fail();
            //# sourceMappingURL=$sourceMapUrl
        """.trimIndent()
    }

    private fun sourceMapDataUrl(
        originalSource: String,
        generatedLine: Int,
        originalLine: Int,
        originalColumn: Int = 1,
    ): String {
        val json = sourceMapJson(originalSource, generatedLine, originalLine, originalColumn)
        val encoded = Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        return "data:application/json;base64,$encoded"
    }

    private fun sourceMapJson(
        originalSource: String,
        generatedLine: Int,
        originalLine: Int,
        originalColumn: Int = 1,
    ): String {
        return JSONObject()
            .put("version", 3)
            .put("sources", JSONArray().put(originalSource))
            .put("names", JSONArray())
            .put(
                "mappings",
                ";".repeat((generatedLine - 1).coerceAtLeast(0)) +
                    sourceMapSegment(
                        generatedColumn = 0,
                        sourceIndex = 0,
                        originalLine = (originalLine - 1).coerceAtLeast(0),
                        originalColumn = (originalColumn - 1).coerceAtLeast(0),
                    ),
            )
            .toString()
    }

    private fun sourceMapSegment(
        generatedColumn: Int,
        sourceIndex: Int,
        originalLine: Int,
        originalColumn: Int,
    ): String {
        return vlq(generatedColumn) + vlq(sourceIndex) + vlq(originalLine) + vlq(originalColumn)
    }

    private fun vlq(value: Int): String {
        var vlq = if (value < 0) ((-value) shl 1) + 1 else value shl 1
        val builder = StringBuilder()
        do {
            var digit = vlq and 31
            vlq = vlq ushr 5
            if (vlq > 0) {
                digit = digit or 32
            }
            builder.append(BASE64_VLQ[digit])
        } while (vlq > 0)
        return builder.toString()
    }

    private fun freshWorkDir(name: String): File {
        return File(context.cacheDir, "$name-${System.nanoTime()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private companion object {
        private const val BASE64_VLQ = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    }
}
