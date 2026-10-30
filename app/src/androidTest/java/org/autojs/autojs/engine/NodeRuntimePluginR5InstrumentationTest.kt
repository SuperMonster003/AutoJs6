package org.autojs.autojs.engine

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import java.io.File
import java.util.UUID

/** Focused release-device coverage for the external plugin lifecycle. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class NodeRuntimePluginR5InstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun settleBefore() {
        NodeJsRuntimePluginHost.invalidateForTest(context)
    }

    @Test
    fun r5_01_livePluginPreflightNegotiatesContractAndReportsRuntimeIdentity() {
        val runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(context) }
        NodeJsRuntimePluginHost.validateRuntimeInfoForTest(runtimeInfo)

        assertEquals(
            NodeJsRuntimeContract.CONTRACT_VERSION,
            runtimeInfo.getInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION),
        )
        assertTrue(runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT).orEmpty().isNotBlank())
        assertTrue(runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION).orEmpty().isNotBlank())
        assertTrue(runtimeInfo.getBoolean("persistentProcessRuntime", false))
        assertTrue(runtimeInfo.getBoolean("dedicatedRuntimeProcess", false))
        assertEquals(1, runtimeInfo.getInt("maxConcurrentExecutions", -1))
        NodePluginTestHarness.assertRemotePluginProcess("R5 live plugin preflight", runtimeInfo)

        val workDir = freshWorkDir("preflight")
        try {
            val result = runPlugin(
                workDir = workDir,
                label = "preflight",
                source = "\"nodejs\"; console.log(\"r5.preflight=\" + process.pid);",
            )
            assertPluginSucceeded("R5 live plugin preflight", result)
            NodePluginTestHarness.assertRanInRuntimeSlot("R5 live plugin preflight", result.pid)
            assertEquals("plugin", result.nativeValues[PLUGIN_DIAGNOSTICS_SOURCE_KEY])
            assertEquals("true", result.nativeValues[PLUGIN_PERSISTENT_RUNTIME_KEY])
            assertEquals("1", result.nativeValues[PLUGIN_MAX_CONCURRENT_KEY])
            assertCoreRuntimeHealthy("R5 live plugin preflight", result)
        } finally {
            workDir.deleteRecursively()
        }
    }

    @Test
    fun r5_02_persistentRuntimeIsReusedWithFreshExecutionState() {
        val workDir = freshWorkDir("persistent-isolation")
        try {
            val first = runPlugin(
                workDir = workDir,
                label = "persistent-first",
                env = mapOf("AUTOJS6_R5_REQUEST_ENV" to "first-request"),
                source = """
                    "nodejs";
                    globalThis.__autojs6R5GlobalLeak = "first";
                    process.env.AUTOJS6_R5_ENV_LEAK = "first";
                    console.log("r5.first=" + [
                      globalThis.__autojs6R5GlobalLeak,
                      process.env.AUTOJS6_R5_ENV_LEAK,
                      process.env.AUTOJS6_R5_REQUEST_ENV
                    ].join("/"));
                """.trimIndent(),
            )
            val second = runPlugin(
                workDir = workDir,
                label = "persistent-second",
                env = mapOf("AUTOJS6_R5_REQUEST_ENV" to "second-request"),
                source = """
                    "nodejs";
                    console.log("r5.second=" + [
                      typeof globalThis.__autojs6R5GlobalLeak,
                      typeof process.env.AUTOJS6_R5_ENV_LEAK,
                      process.env.AUTOJS6_R5_REQUEST_ENV
                    ].join("/"));
                """.trimIndent(),
            )

            assertPluginSucceeded("R5 persistent first execution", first)
            assertPluginSucceeded("R5 persistent second execution", second)
            assertTrue(first.stdout, first.stdout.contains("r5.first=first/first/first-request"))
            assertTrue(second.stdout, second.stdout.contains("r5.second=undefined/undefined/second-request"))
            assertEquals("second discovery did not hit the candidate cache", "true", second.nativeValues[CANDIDATE_CACHE_HIT_KEY])
            assertEquals("second execution did not reuse the Binder session", "true", second.nativeValues[SESSION_REUSED_KEY])
            assertEquals("persistent plugin process changed", first.pid, second.pid)
            assertEquals(
                "host Binder session generation changed",
                first.nativeValues[SESSION_GENERATION_KEY],
                second.nativeValues[SESSION_GENERATION_KEY],
            )
            assertEquals(
                "process runtime generation changed",
                first.nativeValues[PROCESS_RUNTIME_GENERATION_KEY],
                second.nativeValues[PROCESS_RUNTIME_GENERATION_KEY],
            )
            val firstSequence = requireNativeLong(first, PROCESS_RUNTIME_SEQUENCE_KEY)
            val secondSequence = requireNativeLong(second, PROCESS_RUNTIME_SEQUENCE_KEY)
            assertTrue("persistent runtime sequence did not advance", secondSequence > firstSequence)
            assertEquals("true", second.nativeValues[PROCESS_RUNTIME_REUSED_KEY])
            assertCoreRuntimeHealthy("R5 persistent first execution", first)
            assertCoreRuntimeHealthy("R5 persistent second execution", second)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun runPlugin(
        workDir: File,
        label: String,
        source: String,
        env: Map<String, String> = emptyMap(),
    ): NodePluginScriptResult = NodeJsRuntimePluginBridge.runScript(
        context,
        NodePluginScriptRequest(
            source = source,
            sourceName = File(workDir, "$label.js").absolutePath,
            workingDirectory = workDir.absolutePath,
            sandboxRoot = workDir.absolutePath,
            executionId = "r5-$label-${UUID.randomUUID()}",
            timeoutMs = 10_000L,
            env = env,
        ),
    )

    private fun assertPluginSucceeded(label: String, result: NodePluginScriptResult) {
        NodePluginTestHarness.assertSucceeded(label, result)
        assertEquals("$label did not execute in the plugin backend", "true", result.nativeValues[PLUGIN_ENABLED_KEY])
        assertEquals("$label unexpectedly became fallback-safe", "false", result.nativeValues[FALLBACK_SAFE_KEY])
    }

    private fun assertCoreRuntimeHealthy(label: String, result: NodePluginScriptResult) {
        assertTrue("$label omitted process runtime generation", result.nativeValues[PROCESS_RUNTIME_GENERATION_KEY]?.toLongOrNull() != null)
        assertEquals("$label process runtime is unhealthy", "true", result.nativeValues[PROCESS_RUNTIME_HEALTHY_KEY])
        assertEquals("$label process runtime teardown was not clean", "true", result.nativeValues[PROCESS_RUNTIME_TEARDOWN_KEY])
        assertEquals("$label execution teardown was not clean", "true", result.nativeValues[EXECUTION_TEARDOWN_KEY])
    }

    private fun requireNativeLong(result: NodePluginScriptResult, key: String): Long =
        checkNotNull(result.nativeValues[key]?.toLongOrNull()) { "Missing $key: ${result.nativeValues}" }

    private fun freshWorkDir(label: String) = File(
        context.cacheDir,
        "node-runtime-plugin-r5-$label-${System.nanoTime()}",
    ).apply {
        deleteRecursively()
        check(mkdirs()) { "Failed to create R5 working directory: $absolutePath" }
    }

    private companion object {
        const val PLUGIN_ENABLED_KEY = "embedded_script.runtime_plugin.enabled"
        const val PLUGIN_DIAGNOSTICS_SOURCE_KEY = "embedded_script.runtime_plugin.diagnostics_source"
        const val PLUGIN_PERSISTENT_RUNTIME_KEY = "embedded_script.runtime_plugin.persistent_process_runtime"
        const val PLUGIN_MAX_CONCURRENT_KEY = "embedded_script.runtime_plugin.max_concurrent_executions"
        const val CANDIDATE_CACHE_HIT_KEY = "embedded_script.runtime_plugin.candidate_cache_hit"
        const val SESSION_REUSED_KEY = "embedded_script.runtime_plugin.session_reused"
        const val SESSION_GENERATION_KEY = "embedded_script.runtime_plugin.session_generation"
        const val FALLBACK_SAFE_KEY = "embedded_script.runtime_plugin.fallback_safe"
        const val PROCESS_RUNTIME_GENERATION_KEY = "process_runtime.generation"
        const val PROCESS_RUNTIME_SEQUENCE_KEY = "process_runtime.execution_sequence"
        const val PROCESS_RUNTIME_REUSED_KEY = "process_runtime.reused"
        const val PROCESS_RUNTIME_HEALTHY_KEY = "process_runtime.healthy"
        const val PROCESS_RUNTIME_TEARDOWN_KEY = "process_runtime.teardown_clean"
        const val EXECUTION_TEARDOWN_KEY = "execution.teardown_clean"
    }
}
