package org.autojs.autojs.engine

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
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

    fun settleEmbeddedScriptServiceProcess() {
        targetContext.stopService(Intent(targetContext, NativeNodeEmbeddedScriptService::class.java))
        SystemClock.sleep(600L)
    }

    fun runEmbeddedScript(
        source: String,
        sourceName: String,
        workingDirectory: File = targetContext.cacheDir,
        timeoutMs: Long = 10_000L,
        env: Map<String, String> = emptyMap(),
        moduleSources: Map<String, String> = emptyMap(),
        runtimeModuleSources: Map<String, String> = emptyMap(),
        esmExperimentalEnabled: Boolean = BuildConfig.NODEJS_ESM_EXPERIMENTAL_ENABLED,
        dynamicImportExperimentalEnabled: Boolean = BuildConfig.NODEJS_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedScriptService.runScript(
            targetContext,
            NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest(
                source = source,
                sourceName = sourceName,
                workingDirectory = workingDirectory.absolutePath,
                timeoutMs = timeoutMs,
                env = env,
                moduleSources = moduleSources,
                runtimeModuleSources = runtimeModuleSources,
                esmExperimentalEnabled = esmExperimentalEnabled,
                dynamicImportExperimentalEnabled = dynamicImportExperimentalEnabled,
            ),
        )
    }

    fun assertSucceeded(label: String, result: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult) {
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
