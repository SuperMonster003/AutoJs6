package org.autojs.autojs.engine

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.script.NodeProjectLaunchOptions
import org.autojs.autojs.script.NodeProjectLaunchSource
import org.autojs.autojs.script.NodeProjectScriptSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class NodeProjectTimeoutInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun projectEntryUsesConfiguredBudgetAndRunsPastFiveSeconds() = withProject { root, entry ->
        entry.writeText("setTimeout(() => console.log('project.timeout.after-five-seconds=PASS'), 6500);")
        val source = NodeProjectScriptSourceFactory.create(entry) as NodeProjectLaunchSource
        val request = NodeProjectRunner.buildResolvedProjectLaunchRequest(
            NodeProjectRunner.resolveProjectLaunchPlanWithDescriptor(NodeProjectRunner.buildProjectRequest(source)),
        )
        instrumentation.sendStatus(2, Bundle().apply {
            putString("stream", "\nproject.timeout configured=65000 launchOverride=${source.nodeProjectTimeoutMs} request=${request.timeoutMs}\n")
        })
        assertEquals("project.json node.timeoutMs must reach the plugin request", 65_000L, request.timeoutMs)
        val result = NodeJsRuntimePluginBridge.runScript(context, request)
        NodePluginTestHarness.assertSucceeded("project runs beyond the old five-second budget", result)
        assertTrue(result.stdout, result.stdout.contains("project.timeout.after-five-seconds=PASS"))
    }

    @Test fun explicitLaunchBudgetWinsAndMissingProjectBudgetKeepsTheDefault() = withProject { root, entry ->
        val overridden = NodeProjectScriptSourceFactory.createProjectLaunch(
            root, entry, launchOptions = NodeProjectLaunchOptions(timeoutMs = 5000L),
        ) as NodeProjectLaunchSource
        assertEquals(5000L, NodeProjectRunner.buildProjectRequest(overridden).timeoutMs)
        // No budget, or a budget of 0, means no limit (api-coverage Q126).
        assertEquals(0L, NodePluginScriptRequest.DEFAULT_TIMEOUT_MS)
        for (config in listOf("""{"type":"node","main":"main.cjs"}""", """{"type":"node","main":"main.cjs","node":{"timeoutMs":0}}""")) {
            root.resolve("project.json").writeText(config)
            val ordinary = NodeProjectScriptSourceFactory.create(entry) as NodeProjectLaunchSource
            assertEquals(config, NodePluginScriptRequest.DEFAULT_TIMEOUT_MS, NodeProjectRunner.buildProjectRequest(ordinary).timeoutMs)
        }
    }

    private fun withProject(test: (File, File) -> Unit) {
        val root = File(context.cacheDir, "node-project-timeout-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val entry = root.resolve("main.cjs").apply { writeText("console.log('project timeout fixture');") }
        root.resolve("project.json").writeText("""{"type":"node","main":"main.cjs","node":{"timeoutMs":65000}}""")
        try { test(root, entry) } finally { root.deleteRecursively() }
    }
}
