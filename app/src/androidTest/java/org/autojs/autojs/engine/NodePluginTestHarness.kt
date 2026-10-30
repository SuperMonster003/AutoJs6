package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Shared instrumentation support for the external Node.js runtime plugin.
 */
internal object NodePluginTestHarness {

    val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun assumePluginRuntimeAvailable(label: String = "Node.js runtime plugin instrumentation") {
        val probe = runCatching {
            runBlocking { NodeJsRuntimePluginHost.probe(targetContext) }
        }
        assumeTrue(
            "$label requires a discoverable, trusted Node.js runtime plugin: ${probe.exceptionOrNull()?.message}",
            probe.isSuccess,
        )
    }

    fun assumePersistentRuntimeEnabled(label: String = "persistent Node.js runtime plugin instrumentation") =
        assumePluginRuntimeAvailable(label)

    /**
     * The plugin runs scripts in a pool of runtime slot processes (plugin 1.32.0, 4cd3a0e2): before the first execution
     * no slot is warm and the top-level pid is -1, so a probe identifies the plugin by its dispatcher process.
     */
    fun assertRemotePluginProcess(label: String, runtimeInfo: Bundle) {
        val dispatcherPid = runtimeInfo.getInt("dispatcherPid", -1)
        assertTrue(
            "$label: the plugin did not report a remote dispatcher PID: $runtimeInfo",
            dispatcherPid > 0 && dispatcherPid != Process.myPid(),
        )
    }

    /** [pid] belongs to one of the plugin's warm runtime slot processes, per a fresh probe. */
    fun assertRanInRuntimeSlot(label: String, pid: Int) {
        val runtimeInfo = runBlocking { NodeJsRuntimePluginHost.probe(targetContext) }
        @Suppress("DEPRECATION")
        val slotPids = runtimeInfo.getParcelableArrayList<Bundle>("slots").orEmpty().map { it.getInt("pid", -1) }
        assertTrue(
            "$label: pid $pid is not a runtime slot process of the plugin: $runtimeInfo",
            pid > 0 && pid != Process.myPid() && pid in slotPids,
        )
    }

    fun settlePluginRuntime() = Unit

    fun runPluginScript(
        source: String,
        sourceName: String,
        workingDirectory: File = targetContext.cacheDir,
        sandboxRoot: File? = null,
        timeoutMs: Long = 10_000L,
        env: Map<String, String> = emptyMap(),
        moduleSources: Map<String, String> = emptyMap(),
        runtimeModuleSources: Map<String, String> = emptyMap(),
        esmEnabled: Boolean? = null,
        dynamicImportEnabled: Boolean? = null,
        rawNodeNetworkModulesEnabled: Boolean? = null,
        inspectorEnabled: Boolean = false,
        workerThreadsEnabled: Boolean? = null,
        childProcessEnabled: Boolean? = null,
        javaInteropEnabled: Boolean? = null,
        executionId: String? = null,
        executionMode: String = NodeExecutionModePolicy.MODE_ONE_SHOT,
        spawnDepth: Int = 0,
    ): NodePluginScriptResult = NodeJsRuntimePluginBridge.runScript(
        targetContext,
        NodePluginScriptRequest(
            source = source,
            sourceName = sourceName,
            workingDirectory = workingDirectory.absolutePath,
            sandboxRoot = (sandboxRoot ?: workingDirectory).absolutePath,
            executionId = executionId,
            executionMode = executionMode,
            timeoutMs = timeoutMs,
            env = env,
            moduleSources = moduleSources,
            runtimeModuleSources = runtimeModuleSources,
            esmEnabled = esmEnabled,
            dynamicImportEnabled = dynamicImportEnabled,
            rawNodeNetworkModulesEnabled = rawNodeNetworkModulesEnabled,
            inspectorEnabled = inspectorEnabled,
            workerThreadsEnabled = workerThreadsEnabled,
            childProcessEnabled = childProcessEnabled,
            javaInteropEnabled = javaInteropEnabled,
            spawnDepth = spawnDepth,
        ),
    )

    fun assertSucceeded(label: String, result: NodePluginScriptResult) {
        assertTrue(describeResult(label, result), result.succeeded)
        assertTrue("$label timed out\n${describeResult(label, result)}", !result.timedOut)
    }

    /**
     * Asserts that every [expected] line is present in the script's stdout and prints the whole result
     * when one is missing, so a runtime plugin that changes an observable (for example what
     * `require.resolve("ocr")` returns) stays diagnosable from the failure message alone.
     */
    fun assertStdoutContains(label: String, result: NodePluginScriptResult, vararg expected: String) {
        expected.forEach { line ->
            assertTrue("$label: stdout does not contain \"$line\"\n${describeResult(label, result)}", result.stdout.contains(line))
        }
    }

    fun describeResult(label: String, result: NodePluginScriptResult): String = buildString {
        appendLine(label)
        appendLine("succeeded=${result.succeeded}")
        appendLine("exitCode=${result.exitCode}")
        appendLine("errorCode=${result.errorCode}")
        appendLine("errorName=${result.errorName}")
        appendLine("errorMessage=${result.errorMessage}")
        appendLine("stdout:")
        appendLine(result.stdout)
        appendLine("stderr:")
        appendLine(result.stderr)
        appendLine("nativeValues=${result.nativeValues}")
    }

}
