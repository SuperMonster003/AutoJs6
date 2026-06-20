package org.autojs.autojs.engine

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeExampleProjectInstrumentationTest {

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
    fun helloNodeExampleRuns() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node example project smoke")

        val result = runExample("hello-node")

        NodeEmbeddedTestHarness.assertSucceeded("hello-node example", result)
        assertTrue(result.stdout, result.stdout.contains("sample.hello-node.node=v24.5.0"))
        assertTrue(result.stdout, result.stdout.contains("sample.hello-node=PASS"))
    }

    @Test
    fun toastAppExampleRuns() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node toast/app example smoke")

        val result = runExample("toast-app")

        NodeEmbeddedTestHarness.assertSucceeded("toast-app example", result)
        assertTrue(result.stdout, result.stdout.contains("sample.toast-app.package="))
        assertTrue(result.stdout, result.stdout.contains("sample.toast-app=PASS"))
        assertTrue(
            NodeEmbeddedTestHarness.describeResult("toast-app live bridge dispatch", result),
            result.nativeValues["embedded_script.bridge_live_dispatch_count"] == "1",
        )
    }

    @Test
    fun commonJsFsNpmAndDisabledFeatureExamplesRun() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node stable example suite smoke")

        listOf(
            "cjs-package",
            "fs-scoped",
            "fs-promises",
            "npm-pure-js-cjs",
            "disabled-features-demo",
        ).forEach { exampleName ->
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val result = runExample(exampleName)

            NodeEmbeddedTestHarness.assertSucceeded("$exampleName example", result)
            assertTrue(result.stdout, result.stdout.contains("sample.$exampleName=PASS"))
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    @Test
    fun permissionAwareBridgeExamplesRun() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node permission-aware bridge example smoke")

        listOf(
            "accessibility-click-text",
            "screenshot-find-image",
        ).forEach { exampleName ->
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val result = runExample(exampleName)

            NodeEmbeddedTestHarness.assertSucceeded("$exampleName example", result)
            assertTrue(result.stdout, result.stdout.contains("sample.$exampleName=PASS"))
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    @Test
    fun typeScriptRuntimeExamplesRun() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node TypeScript example smoke")

        listOf(
            "typescript-cjs",
            "typescript-esm",
        ).forEach { exampleName ->
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
            val result = runExample(exampleName)

            NodeEmbeddedTestHarness.assertSucceeded("$exampleName example", result)
            assertTrue(result.stdout, result.stdout.contains("sample.$exampleName=PASS"))
            NodeEmbeddedTestHarness.settleEmbeddedScriptServiceProcess()
        }
    }

    private fun runExample(exampleName: String): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val projectDir = File(context.cacheDir, "node-example-$exampleName-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node example project directory: $absolutePath" }
        }
        return try {
            NodeEmbeddedTestHarness.copyAssetTree(exampleName, projectDir)
            val main = exampleMainFile(projectDir)
            val timeoutMs = exampleTimeoutMs(projectDir)
            check(main.isFile) { "Node example $exampleName is missing declared main file: ${main.absolutePath}" }
            NodeEmbeddedTestHarness.runEmbeddedScript(
                source = main.readText(),
                sourceName = main.absolutePath,
                workingDirectory = projectDir,
                timeoutMs = timeoutMs,
            )
        } finally {
            projectDir.deleteRecursively()
        }
    }

    private fun exampleMainFile(projectDir: File): File {
        val projectJson = JSONObject(File(projectDir, "project.json").readText())
        val entry = projectJson.optString("main", "main.cjs")
        val file = File(projectDir, entry)
        val canonicalFile = file.canonicalFile
        val root = projectDir.canonicalFile
        check(canonicalFile.toPath().startsWith(root.toPath())) { "Node example main escapes project root: $entry" }
        return file
    }

    private fun exampleTimeoutMs(projectDir: File): Long {
        val projectTimeout = runCatching {
            JSONObject(File(projectDir, "project.json").readText())
                .optJSONObject("node")
                ?.optLong("timeoutMs", 10_000L)
                ?: 10_000L
        }.getOrDefault(10_000L)
        return projectTimeout.coerceAtLeast(30_000L)
    }
}
