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
    fun processOnlyPrewarmIsConsumedByCleanOneShotExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime prewarm process-only gate")

        val prewarm = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 10_000L)
        assertTrue("prewarm=$prewarm", prewarm.started)
        assertEquals("ready", prewarm.status)
        assertTrue("prewarm should be process-only", prewarm.processOnly)
        assertFalse("prewarm must not load Node runtime", prewarm.runtimeLoaded)
        assertTrue("prewarm pid should be service pid: $prewarm", prewarm.pid > 0)
        assertEquals("true", prewarm.nativeValues["embedded_script.prewarm_pool.enabled"])
        assertEquals("false", prewarm.nativeValues["embedded_script.prewarm_pool.runtime_loaded_before_execution"])
        assertEquals("false", prewarm.nativeValues["embedded_script.prewarm_pool.capabilities_granted"])
        assertEquals("false", prewarm.nativeValues["embedded_script.prewarm_pool.user_modules_loaded"])

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

            NodeEmbeddedTestHarness.assertSucceeded("Node runtime prewarm process-only gate", result)
            assertTrue(result.stdout, result.stdout.contains("prewarm.clean=true/true/true/true"))
            assertEquals("prewarmed process should run the consumed script", prewarm.pid, result.pid)
            assertEquals("consumed", result.nativeValues["embedded_script.prewarm_pool.status"])
            assertEquals("true", result.nativeValues["embedded_script.prewarm_pool.consumed"])
            assertEquals("true", result.nativeValues["embedded_script.prewarm_pool.process_only"])
            assertEquals("false", result.nativeValues["embedded_script.prewarm_pool.runtime_loaded_before_execution"])
            assertEquals("false", result.nativeValues["embedded_script.prewarm_pool.capabilities_granted"])
            assertEquals("false", result.nativeValues["embedded_script.prewarm_pool.user_modules_loaded"])
            assertTrue(result.nativeValues["embedded_script.prewarm_pool.age_ms"].orEmpty().toLongOrNull() ?: -1L >= 0L)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun processOnlyPrewarmExpiresWithoutContaminatingNextExecution() {
        NodeEmbeddedTestHarness.assumeEmbeddedScriptExecutionEnabled("Node runtime prewarm expiry gate")

        val prewarm = NativeNodeEmbeddedScriptService.prewarmRuntime(context, ttlMs = 200L)
        assertTrue("prewarm=$prewarm", prewarm.started)
        assertEquals("ready", prewarm.status)
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
            assertEquals("not_requested", result.nativeValues["embedded_script.prewarm_pool.status"])
            assertEquals("false", result.nativeValues["embedded_script.prewarm_pool.consumed"])
        } finally {
            workDir.deleteRecursively()
        }
    }
}

