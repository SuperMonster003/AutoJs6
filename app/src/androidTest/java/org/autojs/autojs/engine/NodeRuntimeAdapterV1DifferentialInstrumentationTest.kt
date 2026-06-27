package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class NodeRuntimeAdapterV1DifferentialInstrumentationTest {

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
    fun adapterV1MatchesLegacyJniForFocusedCorpus() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("adapter v1 differential corpus")

        val workRoot = File(context.cacheDir, "node-adapter-v1-differential-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create adapter differential dir: $absolutePath" }
        }
        try {
            fixtures().forEach { fixture ->
                val legacy = runFixture(fixture, workRoot, RUNTIME_ADAPTER_LEGACY_JNI)
                val adapter = runFixture(fixture, workRoot, RUNTIME_ADAPTER_V1)

                assertSelectedAdapter(fixture.name, RUNTIME_ADAPTER_LEGACY_JNI, legacy)
                assertSelectedAdapter(fixture.name, RUNTIME_ADAPTER_V1, adapter)
                assertEquivalent(fixture, legacy, adapter)
                println(differentialResult(fixture, legacy, adapter))
            }
        } finally {
            workRoot.deleteRecursively()
        }
    }

    private fun runFixture(
        fixture: AdapterFixture,
        workRoot: File,
        runtimeAdapter: String,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        val workingDirectory = File(workRoot, "${fixture.name}-$runtimeAdapter").apply {
            check(mkdirs()) { "Failed to create fixture dir: $absolutePath" }
        }
        val main = File(workingDirectory, "main.js").apply {
            writeText(fixture.source, StandardCharsets.UTF_8)
        }
        val moduleSources = fixture.moduleSources(workingDirectory)
        return NodeEmbeddedTestHarness.runEmbeddedScript(
            source = main.readText(StandardCharsets.UTF_8),
            sourceName = main.canonicalPath,
            workingDirectory = workingDirectory,
            timeoutMs = 15_000L,
            moduleSources = moduleSources,
            runtimeAdapter = runtimeAdapter,
        )
    }

    private fun assertSelectedAdapter(
        fixtureName: String,
        expected: String,
        result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        assertEquals(
            "$fixtureName selected adapter\n${NodeEmbeddedTestHarness.describeResult(fixtureName, result)}",
            expected,
            result.nativeValues["embedded_script.runtime_adapter.effective"],
        )
        assertEquals(
            "$fixtureName adapter selection source",
            "request",
            result.nativeValues["embedded_script.runtime_adapter.source"],
        )
    }

    private fun assertEquivalent(
        fixture: AdapterFixture,
        legacy: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        adapter: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ) {
        NodeEmbeddedTestHarness.assertSucceeded("${fixture.name}: legacy", legacy)
        NodeEmbeddedTestHarness.assertSucceeded("${fixture.name}: adapter_v1", adapter)
        assertEquals("${fixture.name} exitCode", legacy.exitCode, adapter.exitCode)
        assertEquals("${fixture.name} resultText", legacy.resultText, adapter.resultText)
        assertEquals("${fixture.name} stdout", normalizeOutput(legacy.stdout), normalizeOutput(adapter.stdout))
        assertEquals("${fixture.name} stderr", normalizeOutput(legacy.stderr), normalizeOutput(adapter.stderr))
    }

    private fun differentialResult(
        fixture: AdapterFixture,
        legacy: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
        adapter: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult,
    ): String {
        val json = JSONObject()
            .put("schema", "autojs6-node-adapter-v1-differential-result-v1")
            .put("fixture", fixture.name)
            .put("legacySucceeded", legacy.succeeded)
            .put("adapterSucceeded", adapter.succeeded)
            .put("exitCodeMatched", legacy.exitCode == adapter.exitCode)
            .put("resultTextMatched", legacy.resultText == adapter.resultText)
            .put("stdoutMatched", normalizeOutput(legacy.stdout) == normalizeOutput(adapter.stdout))
            .put("stderrMatched", normalizeOutput(legacy.stderr) == normalizeOutput(adapter.stderr))
            .put("legacyAdapter", legacy.nativeValues["embedded_script.runtime_adapter.effective"].orEmpty())
            .put("adapter", adapter.nativeValues["embedded_script.runtime_adapter.effective"].orEmpty())
        return "$RESULT_PREFIX$json"
    }

    private fun normalizeOutput(value: String): String {
        return value.trim().replace("\r\n", "\n")
    }

    private fun fixtures(): List<AdapterFixture> {
        return listOf(
            AdapterFixture(
                name = "cjs-basic",
                source = """
                    "nodejs";
                    const data = [1, 2, 3, 4].reduce((sum, value) => sum + value, 0);
                    console.log("fixture=cjs-basic");
                    console.log("sum=" + data);
                    return "cjs:" + data;
                """.trimIndent(),
            ),
            AdapterFixture(
                name = "module-source",
                source = """
                    "nodejs";
                    const helper = require("./helper.cjs");
                    console.log("fixture=module-source");
                    console.log("helper=" + helper.value);
                    return helper.value;
                """.trimIndent(),
                moduleSources = { dir ->
                    val helper = File(dir, "helper.cjs").canonicalPath
                    mapOf(
                        helper to """exports.value = "module-source-ok";""",
                    )
                },
            ),
            AdapterFixture(
                name = "async-timer",
                source = """
                    "nodejs";
                    return new Promise((resolve) => {
                      setTimeout(() => {
                        console.log("fixture=async-timer");
                        console.log("timer=done");
                        resolve("timer-ok");
                      }, 1);
                    });
                """.trimIndent(),
            ),
            AdapterFixture(
                name = "scoped-fs",
                source = """
                    "nodejs";
                    const fs = require("fs");
                    fs.writeFileSync("./adapter-v1.txt", "fs-ok", "utf8");
                    const text = fs.readFileSync("./adapter-v1.txt", "utf8");
                    console.log("fixture=scoped-fs");
                    console.log("fs=" + text);
                    return text;
                """.trimIndent(),
            ),
        )
    }

    private data class AdapterFixture(
        val name: String,
        val source: String,
        val moduleSources: (File) -> Map<String, String> = { emptyMap() },
    )

    private companion object {
        private const val RUNTIME_ADAPTER_LEGACY_JNI = "legacy_jni"
        private const val RUNTIME_ADAPTER_V1 = "adapter_v1"
        private const val RESULT_PREFIX = "node.adapter_v1.differential.result="
    }
}
