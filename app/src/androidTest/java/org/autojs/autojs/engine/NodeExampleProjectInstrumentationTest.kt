package org.autojs.autojs.engine

import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptLaunchSourceFactory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class NodeExampleProjectInstrumentationTest {

    private val context = NodePluginTestHarness.targetContext

    @Before
    fun settleBefore() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @After
    fun settleAfter() {
        NodePluginTestHarness.settlePluginRuntime()
    }

    @Test
    fun helloNodeExampleRuns() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node example project smoke")

        val result = runExample("hello-node")

        NodePluginTestHarness.assertSucceeded("hello-node example", result)
        assertTrue(result.stdout, result.stdout.contains("sample.hello-node.node=v24.5.0"))
        assertTrue(result.stdout, result.stdout.contains("sample.hello-node=PASS"))
    }

    @Test
    fun toastAppExampleRuns() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node toast/app example smoke")

        val result = runExample("toast-app")

        NodePluginTestHarness.assertSucceeded("toast-app example", result)
        assertTrue(result.stdout, result.stdout.contains("sample.toast-app.package="))
        assertTrue(result.stdout, result.stdout.contains("sample.toast-app=PASS"))
        assertTrue(
            NodePluginTestHarness.describeResult("toast-app live bridge dispatch", result),
            result.nativeValues["embedded_script.bridge_live_dispatch_count"] == "1",
        )
    }

    @Test
    fun commonJsFsNpmAndDisabledFeatureExamplesRun() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node stable example suite smoke")

        listOf(
            "cjs-package",
            "fs-scoped",
            "fs-promises",
            "npm-pure-js-cjs",
            "disabled-features-demo",
        ).forEach { exampleName ->
            NodePluginTestHarness.settlePluginRuntime()
            val result = runExample(exampleName)

            NodePluginTestHarness.assertSucceeded("$exampleName example", result)
            assertTrue(
                NodePluginTestHarness.describeResult("$exampleName output", result),
                result.stdout.contains("sample.$exampleName=PASS"),
            )
            NodePluginTestHarness.settlePluginRuntime()
        }
    }

    @Test
    fun permissionAwareBridgeExamplesRun() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node permission-aware bridge example smoke")

        listOf(
            "accessibility-click-text",
            "screenshot-find-image",
        ).forEach { exampleName ->
            NodePluginTestHarness.settlePluginRuntime()
            val result = runExample(exampleName)

            NodePluginTestHarness.assertSucceeded("$exampleName example", result)
            assertTrue(
                NodePluginTestHarness.describeResult("$exampleName output", result),
                result.stdout.contains("sample.$exampleName=PASS"),
            )
            NodePluginTestHarness.settlePluginRuntime()
        }
    }

    @Test
    fun typeScriptRuntimeExamplesRun() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node TypeScript example smoke")

        listOf(
            "typescript-cjs",
            "typescript-esm",
            "typescript-project",
            "packaged-typescript",
        ).forEach { exampleName ->
            NodePluginTestHarness.settlePluginRuntime()
            val result = runTypeScriptExample(exampleName)

            NodePluginTestHarness.assertSucceeded("$exampleName example", result)
            assertTrue(result.stdout, result.stdout.contains("sample.$exampleName=PASS"))
            NodePluginTestHarness.settlePluginRuntime()
        }
    }

    private fun runTypeScriptExample(exampleName: String): NodePluginScriptResult {
        val projectDir = File(context.cacheDir, "node-example-$exampleName-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node example project directory: $absolutePath" }
        }
        return try {
            NodePluginTestHarness.copyAssetTree(exampleName, projectDir)
            val main = exampleMainFile(projectDir, null)
            check(main.isFile) { "Node example $exampleName is missing declared main file: ${main.absolutePath}" }
            val originalFiles = projectFileInventory(projectDir)
            var result: NodePluginScriptResult? = null
            repeat(2) { attempt ->
                NodePluginTestHarness.settlePluginRuntime()
                result = executeTypeScriptEntry(exampleName, projectDir, main)
                NodePluginTestHarness.assertSucceeded(
                    "$exampleName TypeScript repeat ${attempt + 1}",
                    requireNotNull(result),
                )
                assertEquals(
                    "$exampleName materialized runtime-only compiler output after repeat ${attempt + 1}",
                    originalFiles,
                    projectFileInventory(projectDir),
                )
            }
            requireNotNull(result)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    private fun executeTypeScriptEntry(
        exampleName: String,
        projectDir: File,
        main: File,
    ): NodePluginScriptResult {
        val launch = ScriptLaunchSourceFactory.createFileLaunch(main)
        val completed = CountDownLatch(1)
        val succeeded = AtomicBoolean(false)
        val failure = AtomicReference<Throwable>()
        val listener = object : SimpleScriptExecutionListener() {
            override fun onSuccess(execution: ScriptExecution, result: Any?) {
                succeeded.set(true)
                completed.countDown()
            }

            override fun onException(execution: ScriptExecution, error: Throwable) {
                failure.set(error)
                completed.countDown()
            }
        }
        val execution = AutoJs.instance.scriptEngineService.execute(
            launch.source,
            listener,
            ExecutionConfig(workingDirectory = projectDir.path),
        )
        assertTrue(
            "$exampleName TypeScript execution did not reach a terminal state",
            completed.await(TYPE_SCRIPT_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        failure.get()?.let { error ->
            throw AssertionError("$exampleName TypeScript example failed", error)
        }
        assertTrue("$exampleName TypeScript execution did not succeed", succeeded.get())
        val engine = execution.engine as? NodePluginJavaScriptEngine
            ?: throw AssertionError("$exampleName did not use the Node plugin engine")
        return requireNotNull(engine.lastPluginScriptResult) {
            "$exampleName produced no Node plugin result"
        }
    }

    private fun projectFileInventory(projectDir: File): Set<String> = projectDir
        .walkTopDown()
        .filter(File::isFile)
        .map { file -> file.relativeTo(projectDir).invariantSeparatorsPath }
        .toSortedSet()

    @Test
    fun desktopParitySafeSubsetExamplesRun() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node desktop parity safe subset example smoke")

        val result = runExample("desktop-parity-suite", "smoke.cjs")
        val description = NodePluginTestHarness.describeResult("desktop-parity-suite safe subset smoke", result)

        NodePluginTestHarness.assertSucceeded("desktop-parity-suite safe subset smoke", result)
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.smoke.catalog=4"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.esm-loader=PASS"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.http-https.shape=function,function,function,function"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.http-https=PASS"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.npm-dependency=PASS"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.fs-advanced.handle=true"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.fs-advanced.watch=true"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.fs-advanced=PASS"))
        assertTrue(description, result.stdout.contains("sample.desktop-parity-suite.smoke=PASS"))
    }

    @Test
    fun proParitySafeSubsetExamplesRun() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node Pro parity safe subset example smoke")

        val result = runExample("pro-parity-suite", "smoke.cjs")

        NodePluginTestHarness.assertSucceeded("pro-parity-suite safe subset smoke", result)
        assertTrue(result.stdout, result.stdout.contains("sample.pro-parity-suite.smoke.catalog=2"))
        assertTrue(result.stdout, result.stdout.contains("sample.pro-parity-suite.ui-layout=PASS"))
        assertTrue(result.stdout, result.stdout.contains("sample.pro-parity-suite.tasks-work-manager=PASS"))
        assertTrue(result.stdout, result.stdout.contains("sample.pro-parity-suite.smoke=PASS"))
    }

    private fun runExample(exampleName: String): NodePluginScriptResult {
        return runExample(exampleName, null)
    }

    private fun runExample(exampleName: String, entryOverride: String?): NodePluginScriptResult {
        val projectDir = File(context.cacheDir, "node-example-$exampleName-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create Node example project directory: $absolutePath" }
        }
        return try {
            NodePluginTestHarness.copyAssetTree(exampleName, projectDir)
            val main = exampleMainFile(projectDir, entryOverride)
            val timeoutMs = exampleTimeoutMs(projectDir)
            check(main.isFile) { "Node example $exampleName is missing declared main file: ${main.absolutePath}" }
            NodePluginTestHarness.runPluginScript(
                source = main.readText(),
                sourceName = main.absolutePath,
                workingDirectory = projectDir,
                timeoutMs = timeoutMs,
            )
        } finally {
            projectDir.deleteRecursively()
        }
    }

    private fun exampleMainFile(projectDir: File, entryOverride: String?): File {
        val projectJson = JSONObject(File(projectDir, "project.json").readText())
        val entry = entryOverride ?: projectJson.optString("main", "main.cjs")
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

    private companion object {
        const val TYPE_SCRIPT_EXECUTION_TIMEOUT_SECONDS = 90L
    }
}
