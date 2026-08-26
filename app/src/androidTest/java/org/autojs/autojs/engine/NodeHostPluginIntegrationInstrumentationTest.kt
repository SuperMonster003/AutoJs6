package org.autojs.autojs.engine

import org.autojs.autojs.AutoJs
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.script.ScriptLaunchSourceFactory
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

/**
 * Minimal host-owned coverage for the external Node.js runtime boundary.
 * Runtime feature and sample conformance belongs to the plugin repository.
 */
class NodeHostPluginIntegrationInstrumentationTest {

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
    fun pluginExecutionRoundTripRuns() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node host/plugin execution smoke")

        val result = NodePluginTestHarness.runPluginScript(
            source = """
                "nodejs";
                console.log("host.integration.node=PASS");
            """.trimIndent(),
            sourceName = "host-integration.cjs",
        )

        NodePluginTestHarness.assertSucceeded("Node host/plugin execution smoke", result)
        assertTrue(result.stdout, result.stdout.contains("host.integration.node=PASS"))
    }

    @Test
    fun hostCapabilityBridgeRoundTripRuns() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node host capability bridge smoke")

        withProject(
            name = "host-bridge",
            entryName = "main.cjs",
            permissions = listOf("toast"),
            source = """
                "nodejs";
                (async () => {
                  const { showToast } = require("toast");
                  await showToast("AutoJs6 Node host bridge smoke", { log: true, timeoutMs: 1000 });
                  console.log("host.integration.bridge=PASS");
                })().catch((error) => {
                  console.error(error && (error.stack || error.message) || error);
                  process.exitCode = 1;
                });
            """.trimIndent(),
        ) { projectDir, entry ->
            val result = NodePluginTestHarness.runPluginScript(
                source = entry.readText(),
                sourceName = entry.absolutePath,
                workingDirectory = projectDir,
            )

            NodePluginTestHarness.assertSucceeded("Node host capability bridge smoke", result)
            assertTrue(result.stdout, result.stdout.contains("host.integration.bridge=PASS"))
            assertEquals("1", result.nativeValues["embedded_script.bridge_live_dispatch_count"])
        }
    }

    @Test
    fun typeScriptCompilerOutputIsDispatchedWithoutMaterializingFiles() {
        NodePluginTestHarness.assumePluginRuntimeAvailable("Node TypeScript host pipeline smoke")

        withProject(
            name = "host-typescript",
            entryName = "main.cts",
            permissions = emptyList(),
            source = """
                "nodejs";
                interface Payload { value: string }
                const payload: Payload = { value: "PASS" };
                console.log("host.integration.typescript=" + payload.value);
            """.trimIndent(),
        ) { projectDir, entry ->
            val originalFiles = projectFileInventory(projectDir)
            repeat(2) { attempt ->
                val result = executeTypeScriptEntry(projectDir, entry)
                NodePluginTestHarness.assertSucceeded("Node TypeScript host pipeline repeat ${attempt + 1}", result)
                assertTrue(result.stdout, result.stdout.contains("host.integration.typescript=PASS"))
                assertEquals(originalFiles, projectFileInventory(projectDir))
            }
        }
    }

    private fun withProject(
        name: String,
        entryName: String,
        permissions: List<String>,
        source: String,
        action: (projectDir: File, entry: File) -> Unit,
    ) {
        val projectDir = File(context.cacheDir, "node-host-$name-${System.nanoTime()}").apply {
            check(mkdirs()) { "Failed to create Node host integration project: $absolutePath" }
        }
        try {
            val permissionsJson = permissions.joinToString(",") { "\"$it\"" }
            File(projectDir, "project.json").writeText(
                """
                {
                  "name": "$name",
                  "type": "node",
                  "main": "$entryName",
                  "node": {
                    "timeoutMs": 10000,
                    "permissions": [$permissionsJson]
                  }
                }
                """.trimIndent() + "\n",
            )
            File(projectDir, "package.json").writeText(
                """{"name":"@autojs6-host-test/$name","private":true,"type":"commonjs"}""" + "\n",
            )
            val entry = File(projectDir, entryName).apply { writeText(source + "\n") }
            action(projectDir, entry)
        } finally {
            projectDir.deleteRecursively()
        }
    }

    private fun executeTypeScriptEntry(projectDir: File, entry: File): NodePluginScriptResult {
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
            ScriptLaunchSourceFactory.createFileLaunch(entry).source,
            listener,
            ExecutionConfig(workingDirectory = projectDir.path),
        )
        assertTrue(
            "Node TypeScript execution did not reach a terminal state",
            completed.await(TYPE_SCRIPT_EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS),
        )
        failure.get()?.let { throw AssertionError("Node TypeScript host pipeline failed", it) }
        assertTrue("Node TypeScript execution did not succeed", succeeded.get())
        val engine = execution.engine as? NodePluginJavaScriptEngine
            ?: throw AssertionError("TypeScript source did not use the Node plugin engine")
        return requireNotNull(engine.lastPluginScriptResult) { "Node TypeScript execution produced no plugin result" }
    }

    private fun projectFileInventory(projectDir: File): Set<String> = projectDir
        .walkTopDown()
        .filter(File::isFile)
        .map { file -> file.relativeTo(projectDir).invariantSeparatorsPath }
        .toSortedSet()

    private companion object {
        const val TYPE_SCRIPT_EXECUTION_TIMEOUT_SECONDS = 90L
    }
}
