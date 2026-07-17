package org.autojs.autojs.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class NodeRuntimePrewarmPoolInstrumentationTest {

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
    fun runtimeReadyPrewarmIsReusedByFreshExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime-ready prewarm gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("Node runtime-ready prewarm gate")

        val prewarm = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 10_000L)
        assertTrue("prewarm=$prewarm", prewarm.started)
        assertEquals("ready", prewarm.status)
        assertFalse("prewarm must initialize more than the service process", prewarm.processOnly)
        assertTrue("prewarm must load the process-global Node runtime", prewarm.runtimeLoaded)
        assertTrue("prewarm pid should be service pid: $prewarm", prewarm.pid > 0)
        assertEquals("ready", prewarm.nativeValues["process_runtime.state"])
        assertEquals("1", prewarm.nativeValues["process_runtime.init_count"])
        assertEquals("0", prewarm.nativeValues["process_runtime.execution_sequence"])
        val prewarmSessionGeneration = prewarm.nativeValues["embedded_script.process_session.generation"]?.toLongOrNull()
        val prewarmRuntimeGeneration = prewarm.nativeValues["process_runtime.generation"]?.toLongOrNull()
        assertTrue("prewarm session generation is missing: $prewarm", prewarmSessionGeneration != null)
        assertTrue("prewarm process runtime generation is missing: $prewarm", prewarmRuntimeGeneration != null)

        val workDir = File(context.cacheDir, "node-prewarm-pool-clean-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create prewarm work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    const fs = require("fs");
                    const marker = "prewarm-clean";
                    fs.writeFileSync("./marker.txt", marker);
                    const profile = require("autojs6:profile");
                    console.log("prewarm.clean=" + [
                      process.cwd().endsWith("${workDir.name}"),
                      fs.readFileSync("./marker.txt", "utf8") === marker,
                      process.env.AUTOJS6_NODE_PREWARM_POOL === undefined,
                      profile.compileCache && profile.compileCache.appPrivateOnly === true
                    ].join("/"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node runtime-ready prewarm gate", result)
            assertTrue(result.stdout, result.stdout.contains("prewarm.clean=true/true/true/true"))
            assertEquals("prewarmed process should run the script", prewarm.pid, result.pid)
            assertEquals(prewarmSessionGeneration, result.nativeValues["embedded_script.process_session.generation"]?.toLongOrNull())
            assertEquals(prewarmRuntimeGeneration, result.nativeValues["process_runtime.generation"]?.toLongOrNull())
            assertEquals("1", result.nativeValues["process_runtime.init_count"])
            assertEquals("1", result.nativeValues["process_runtime.execution_sequence"])
            assertEquals("true", result.nativeValues["process_runtime.reused"])
            assertEquals("true", result.nativeValues["process_runtime.teardown_clean"])
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun runtimeReadyPrewarmExpiresAndNextExecutionUsesNewSession() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime prewarm expiry gate")
        NodeEmbeddedTestHarness.assumePersistentRuntimeEnabled("Node runtime prewarm expiry gate")

        val prewarm = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 200L)
        assertTrue("prewarm=$prewarm", prewarm.started)
        assertEquals("ready", prewarm.status)
        assertFalse("prewarm must initialize more than the service process", prewarm.processOnly)
        assertTrue("prewarm must load the process-global Node runtime", prewarm.runtimeLoaded)
        val prewarmSessionGeneration = prewarm.nativeValues["embedded_script.process_session.generation"]?.toLongOrNull()
        assertTrue("prewarm session generation is missing: $prewarm", prewarmSessionGeneration != null)
        Thread.sleep(900L)

        val workDir = File(context.cacheDir, "node-prewarm-pool-expired-${System.nanoTime()}").apply {
            deleteRecursively()
            check(mkdirs()) { "Failed to create expired prewarm work dir: $absolutePath" }
        }
        try {
            val result = NodeEmbeddedTestHarness.runEmbeddedScript(
                source = """
                    "nodejs";
                    console.log("prewarm.expired.clean=" + [
                      process.cwd().endsWith("${workDir.name}"),
                      process.env.AUTOJS6_NODE_PREWARM_POOL === undefined
                    ].join("/"));
                """.trimIndent(),
                sourceName = File(workDir, "main.js").absolutePath,
                workingDirectory = workDir,
                timeoutMs = 10_000L,
            )

            NodeEmbeddedTestHarness.assertSucceeded("Node runtime prewarm expiry gate", result)
            assertTrue(result.stdout, result.stdout.contains("prewarm.expired.clean=true/true"))
            val nextSessionGeneration = result.nativeValues["embedded_script.process_session.generation"]?.toLongOrNull()
            assertTrue("post-expiry session generation is missing: ${result.nativeValues}", nextSessionGeneration != null)
            assertTrue(
                "expired prewarm session was unexpectedly retained: prewarm=$prewarmSessionGeneration next=$nextSessionGeneration",
                nextSessionGeneration!! > prewarmSessionGeneration!!,
            )
            assertEquals("1", result.nativeValues["process_runtime.init_count"])
            assertEquals("1", result.nativeValues["process_runtime.execution_sequence"])
            assertEquals("true", result.nativeValues["process_runtime.teardown_clean"])
        } finally {
            workDir.deleteRecursively()
        }
    }
}

