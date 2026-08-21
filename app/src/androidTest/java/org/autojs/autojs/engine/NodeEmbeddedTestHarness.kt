package org.autojs.autojs.engine

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs6.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Shared instrumentation support for the external Node.js runtime plugin.
 *
 * The historical object and method names remain temporarily source-compatible with
 * host-capability tests; none of these helpers starts or inspects an embedded runtime.
 */
internal object NodeEmbeddedTestHarness {

    val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    fun assumePluginRuntimeAvailable(label: String = "Node.js runtime plugin instrumentation") {
        val probe = runCatching {
            runBlocking { NodeJsRuntimePluginHost.probe(targetContext) }
        }
        assumeTrue(
            "$label requires a discoverable, trusted Node.js runtime plugin: ${probe.exceptionOrNull()?.message}",
            probe.isSuccess,
        )
    }

    fun assumeEmbeddedScriptExecutionEnabled(label: String = "Node.js runtime plugin instrumentation") =
        assumePluginRuntimeAvailable(label)

    fun assumePersistentRuntimeEnabled(label: String = "persistent Node.js runtime plugin instrumentation") =
        assumePluginRuntimeAvailable(label)

    fun settlePluginRuntime() = Unit

    fun settleEmbeddedScriptServiceProcess() = settlePluginRuntime()

    fun runPluginScript(
        source: String,
        sourceName: String,
        workingDirectory: File = targetContext.cacheDir,
        sandboxRoot: File? = null,
        timeoutMs: Long = 10_000L,
        env: Map<String, String> = emptyMap(),
        moduleSources: Map<String, String> = emptyMap(),
        runtimeModuleSources: Map<String, String> = emptyMap(),
        esmEnabled: Boolean = BuildConfig.NODEJS_ESM_ENABLED,
        dynamicImportEnabled: Boolean = BuildConfig.NODEJS_DYNAMIC_IMPORT_ENABLED,
        rawNodeNetworkModulesEnabled: Boolean = BuildConfig.NODEJS_RAW_NETWORK_MODULES_ENABLED,
        workerThreadsEnabled: Boolean = BuildConfig.NODEJS_WORKER_THREADS_ENABLED,
        childProcessEnabled: Boolean = BuildConfig.NODEJS_CHILD_PROCESS_ENABLED,
        javaInteropEnabled: Boolean = BuildConfig.NODEJS_JAVA_INTEROP_ENABLED,
        runtimeAdapter: String? = null,
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
            workerThreadsEnabled = workerThreadsEnabled,
            childProcessEnabled = childProcessEnabled,
            javaInteropEnabled = javaInteropEnabled,
            spawnDepth = spawnDepth,
            runtimeAdapter = runtimeAdapter,
        ),
    )

    @Suppress("UNUSED_PARAMETER")
    fun runEmbeddedScript(
        source: String,
        sourceName: String,
        workingDirectory: File = targetContext.cacheDir,
        sandboxRoot: File? = null,
        timeoutMs: Long = 10_000L,
        env: Map<String, String> = emptyMap(),
        moduleSources: Map<String, String> = emptyMap(),
        runtimeModuleSources: Map<String, String> = emptyMap(),
        esmEnabled: Boolean = BuildConfig.NODEJS_ESM_ENABLED,
        dynamicImportEnabled: Boolean = BuildConfig.NODEJS_DYNAMIC_IMPORT_ENABLED,
        rawNodeNetworkModulesEnabled: Boolean = BuildConfig.NODEJS_RAW_NETWORK_MODULES_ENABLED,
        workerThreadsEnabled: Boolean = BuildConfig.NODEJS_WORKER_THREADS_ENABLED,
        childProcessEnabled: Boolean = BuildConfig.NODEJS_CHILD_PROCESS_ENABLED,
        javaInteropEnabled: Boolean = BuildConfig.NODEJS_JAVA_INTEROP_ENABLED,
        runtimeAdapter: String? = null,
        queueMode: String = NodeExecutionQueuePolicy.MODE_ENQUEUE_WHEN_BUSY,
        queueTimeoutMs: Long = 0L,
        processPoolSlot: String? = null,
    ): NodePluginScriptResult = runPluginScript(
        source = source,
        sourceName = sourceName,
        workingDirectory = workingDirectory,
        sandboxRoot = sandboxRoot,
        timeoutMs = timeoutMs,
        env = env,
        moduleSources = moduleSources,
        runtimeModuleSources = runtimeModuleSources,
        esmEnabled = esmEnabled,
        dynamicImportEnabled = dynamicImportEnabled,
        rawNodeNetworkModulesEnabled = rawNodeNetworkModulesEnabled,
        workerThreadsEnabled = workerThreadsEnabled,
        childProcessEnabled = childProcessEnabled,
        javaInteropEnabled = javaInteropEnabled,
        runtimeAdapter = runtimeAdapter,
    )

    fun assertSucceeded(label: String, result: NodePluginScriptResult) {
        assertTrue(describeResult(label, result), result.succeeded)
        assertTrue("$label timed out\n${describeResult(label, result)}", !result.timedOut)
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

    fun copyAssetTree(assetPath: String, target: File) {
        val children = testContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            testContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            children.forEach { child -> copyAssetTree("$assetPath/$child", File(target, child)) }
        }
    }
}
