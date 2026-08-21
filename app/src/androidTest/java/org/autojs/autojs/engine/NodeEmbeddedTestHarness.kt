package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.App
import org.autojs.autojs6.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import java.io.File

internal object NodeEmbeddedTestHarness {

    val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val testContext: Context
        get() = InstrumentationRegistry.getInstrumentation().context

    fun assumeEmbeddedScriptExecutionEnabled(label: String = "embedded Node instrumentation") {
        assumeTrue(
            "Build with -Pautojs.nodejs.embedded.script.enabled=true to run $label.",
            BuildConfig.NODEJS_EMBEDDED_SCRIPT_EXECUTION_BUILD_ENABLED,
        )
    }

    fun assumePluginRuntimeAvailable(label: String = "Node.js runtime plugin instrumentation") {
        val probe = runCatching {
            runBlocking { NodeJsRuntimePluginHost.probe(targetContext) }
        }
        assumeTrue(
            "$label requires a discoverable, trusted Node.js runtime plugin: ${probe.exceptionOrNull()?.message}",
            probe.isSuccess,
        )
    }

    fun assumePersistentRuntimeEnabled(label: String = "persistent embedded Node instrumentation") {
        assumeTrue(
            "Build with -Pautojs.nodejs.embedded.persistentRuntime=true on Android 10 or newer to run $label.",
            NodeEmbeddedProcessSessionManager.isEnabled(),
        )
    }

    fun settleEmbeddedScriptServiceProcess() {
        (targetContext.applicationContext as? App)?.cancelDeferredNativeNodePrewarm()
        if (NodeEmbeddedProcessSessionManager.isEnabled()) {
            check(
                NodeEmbeddedProcessSessionManager.shutdownAllAndAwait(
                    context = targetContext,
                    reason = "instrumentation_test_settle",
                ),
            ) { "Timed out while shutting down persistent embedded Node process sessions." }
            NodeEmbeddedProcessSessionManager.reopenForUse()
            check(!NodeEmbeddedProcessSessionManager.isClosing()) {
                "Persistent embedded Node process session manager did not reopen after test settlement."
            }
            return
        }
        NodeEmbeddedProcessPoolPolicy.declaredSlots.forEach { slot ->
            targetContext.stopService(Intent(targetContext, slot.serviceClass))
        }
        SystemClock.sleep(750L)
    }

    fun settlePluginRuntime() {
        (targetContext.applicationContext as? App)?.cancelDeferredNativeNodePrewarm()
    }

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
    ): NodePluginScriptResult {
        return NodeJsRuntimePluginBridge.runScript(
            targetContext,
            NodePluginScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory.absolutePath,
                sandboxRoot = (sandboxRoot ?: workingDirectory).absolutePath,
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
            ),
        )
    }

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
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NodeJsRuntimePluginBridge.runScript(
            targetContext,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory.absolutePath,
                sandboxRoot = sandboxRoot?.absolutePath,
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
                queueMode = queueMode,
                queueTimeoutMs = queueTimeoutMs,
                processPoolSlot = processPoolSlot,
            ),
        )
    }

    fun assertSucceeded(label: String, result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) {
        assertTrue(describeResult(label, result), result.succeeded)
        assertTrue("$label timed out\n${describeResult(label, result)}", !result.timedOut)
    }

    fun assertSucceeded(label: String, result: NodePluginScriptResult) {
        assertTrue(describeResult(label, result), result.succeeded)
        assertTrue("$label timed out\n${describeResult(label, result)}", !result.timedOut)
    }

    fun describeResult(label: String, result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult): String {
        return buildString {
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

    fun describeResult(label: String, result: NodePluginScriptResult): String {
        return buildString {
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

    fun copyAssetTree(assetPath: String, target: File) {
        val children = testContext.assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            testContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            target.mkdirs()
            children.forEach { child ->
                copyAssetTree("$assetPath/$child", File(target, child))
            }
        }
    }
}
