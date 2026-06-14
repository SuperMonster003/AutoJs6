package org.autojs.autojs.engine

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

    private fun runExample(exampleName: String): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val projectDir = File(context.cacheDir, "node-example-$exampleName-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node example project directory: $absolutePath" }
        }
        return try {
            NodeEmbeddedTestHarness.copyAssetTree(exampleName, projectDir)
            val main = File(projectDir, "main.cjs")
            check(main.isFile) { "Node example $exampleName is missing main.cjs" }
            NodeEmbeddedTestHarness.runEmbeddedScript(
                source = main.readText(),
                sourceName = main.absolutePath,
                workingDirectory = projectDir,
                timeoutMs = 10_000L,
            )
        } finally {
            projectDir.deleteRecursively()
        }
    }
}
