package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract

object NodeJsRuntimePluginBridge {

    private const val TAG = "NodeJsRuntimePlugin"
    private const val PLUGIN_RUNTIME_ENABLED_PROPERTY = "autojs.nodejs.runtime.plugin.enabled"
    private const val PLUGIN_RUNTIME_REQUIRED_PROPERTY = "autojs.nodejs.runtime.plugin.required"
    const val ERROR_PLUGIN_UNAVAILABLE = "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE"

    fun isRequested(): Boolean {
        return NodeRuntimeBackend.current() == NodeRuntimeBackend.PLUGIN ||
                booleanSystemProperty(PLUGIN_RUNTIME_ENABLED_PROPERTY)
    }

    fun isRequired(): Boolean {
        return NodeRuntimeBackend.current() == NodeRuntimeBackend.PLUGIN ||
                booleanSystemProperty(PLUGIN_RUNTIME_REQUIRED_PROPERTY)
    }

    fun runScriptWithEmbeddedFallback(
        context: Context,
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        if (!isRequested()) {
            return NativeNodeEmbeddedScriptService.runScript(context, request, launchSurface)
        }
        val pluginResult = runScript(context, request, launchSurface)
        if (
            pluginResult.errorCode == ERROR_PLUGIN_UNAVAILABLE &&
            !isRequired()
        ) {
            Log.w(
                TAG,
                "Node.js runtime plugin failed; falling back to embedded service " +
                        "sourceName=${request.sourceName} errorCode=${pluginResult.errorCode.orEmpty()}",
            )
            return NativeNodeEmbeddedScriptService.runScript(context, request, launchSurface)
        }
        return pluginResult.withLaunchSurfaceNativeValue(launchSurface)
    }

    fun runScript(
        context: Context,
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val startedAt = SystemClock.elapsedRealtime()
        return runCatching {
            val result = runBlocking {
                NodeJsRuntimePluginHost.runScript(context, request.toPluginBundle(context, launchSurface))
            }
            result.toEmbeddedScriptResult(request, startedAt)
        }.getOrElse { error ->
            pluginFailureResult(request, startedAt, error)
        }
    }

    fun cancelScript(context: Context, executionId: String): Boolean {
        return runCatching {
            runBlocking {
                NodeJsRuntimePluginHost.cancelScript(context, executionId)
            }
        }.getOrDefault(false)
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult.withLaunchSurfaceNativeValue(
        launchSurface: String,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        if (launchSurface.isBlank()) {
            return this
        }
        return copy(
            nativeValues = nativeValues + ("embedded_script.execution_mode_launch_surface" to launchSurface),
        )
    }

    private fun NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest.toPluginBundle(
        context: Context,
        launchSurface: String,
    ): Bundle {
        val hostCapabilityBroker = NodeJsHostCapabilityBroker(context, this, launchSurface)
        return Bundle().apply {
            putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION)
            putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId)
            putString(NodeJsRuntimeContract.KEY_SOURCE, source)
            putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, sourceName)
            putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, workingDirectory)
            putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, sandboxRoot)
            putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs)
            putString(NodeJsRuntimeContract.KEY_EXECUTION_MODE, executionMode)
            putString(NodeJsRuntimeContract.KEY_RUNTIME_ADAPTER, runtimeAdapter)
            putStringMap(NodeJsRuntimeContract.KEY_ENV_NAMES, NodeJsRuntimeContract.KEY_ENV_VALUES, env)
            putStringMap(
                NodeJsRuntimeContract.KEY_MODULE_SOURCE_NAMES,
                NodeJsRuntimeContract.KEY_MODULE_SOURCES,
                moduleSources,
            )
            putStringMap(
                NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCE_NAMES,
                NodeJsRuntimeContract.KEY_RUNTIME_MODULE_SOURCES,
                runtimeModuleSources,
            )
            putBoolean(NodeJsRuntimeContract.KEY_ESM_EXPERIMENTAL_ENABLED, esmExperimentalEnabled)
            putBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_EXPERIMENTAL_ENABLED, dynamicImportExperimentalEnabled)
            putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_EXPERIMENTAL_ENABLED, rawNodeNetworkModulesExperimentalEnabled)
            putBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_EXPERIMENTAL_ENABLED, workerThreadsExperimentalEnabled)
            putBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_EXPERIMENTAL_ENABLED, childProcessExperimentalEnabled)
            putBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_EXPERIMENTAL_ENABLED, javaInteropExperimentalEnabled)
            putBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER, hostCapabilityBroker)
            putString(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_ID, hostCapabilityBroker.id)
            putInt(
                NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_VERSION,
                NodeJsRuntimeContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION,
            )
        }
    }

    private fun Bundle.toEmbeddedScriptResult(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        startedAt: Long,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val nativeValues = getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD)
            .parseNativePayload()
            .toMutableMap()
            .apply {
                put("embedded_script.runtime_plugin.enabled", "true")
                put("embedded_script.runtime_plugin.contract_version", NodeJsRuntimeContract.CONTRACT_VERSION.toString())
                put("embedded_script.runtime_plugin.host_broker.attached", "true")
                put(
                    "embedded_script.runtime_plugin.host_broker.contract_version",
                    NodeJsRuntimeContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION.toString(),
                )
            }
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
            succeeded = getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false),
            exitCode = optionalInt(NodeJsRuntimeContract.KEY_EXIT_CODE),
            resultText = getString(NodeJsRuntimeContract.KEY_RESULT_TEXT),
            stdout = getString(NodeJsRuntimeContract.KEY_STDOUT).orEmpty(),
            stderr = getString(NodeJsRuntimeContract.KEY_STDERR).orEmpty(),
            errorName = getString(NodeJsRuntimeContract.KEY_ERROR_NAME),
            errorMessage = getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE),
            errorStack = getString(NodeJsRuntimeContract.KEY_ERROR_STACK),
            errorCode = getString(NodeJsRuntimeContract.KEY_ERROR_CODE),
            elapsedMs = optionalLong(NodeJsRuntimeContract.KEY_ELAPSED_MS)
                ?: (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            processName = getString(NodeJsRuntimeContract.KEY_PROCESS_NAME).orEmpty(),
            pid = optionalInt(NodeJsRuntimeContract.KEY_PID) ?: -1,
            sourceName = getString(NodeJsRuntimeContract.KEY_SOURCE_NAME).orEmpty().ifBlank { request.sourceName },
            nativeValues = nativeValues,
            timedOut = getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, false),
            timeoutMs = optionalLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS),
            workingDirectory = getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY) ?: request.workingDirectory,
        )
    }

    private fun pluginFailureResult(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        startedAt: Long,
        error: Throwable,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
            succeeded = false,
            exitCode = 1,
            resultText = null,
            stdout = "",
            stderr = "",
            errorName = error.javaClass.simpleName,
            errorMessage = error.message ?: error.javaClass.name,
            errorStack = error.stackTraceToString(),
            errorCode = ERROR_PLUGIN_UNAVAILABLE,
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            processName = "",
            pid = Process.myPid(),
            sourceName = request.sourceName,
            nativeValues = mapOf(
                "embedded_script.runtime_plugin.enabled" to "true",
                "embedded_script.runtime_plugin.failure" to "true",
                "embedded_script.error_code" to ERROR_PLUGIN_UNAVAILABLE,
            ),
            workingDirectory = request.workingDirectory,
        )
    }

    private fun Bundle.putStringMap(namesKey: String, valuesKey: String, map: Map<String, String>) {
        putStringArray(namesKey, map.keys.toTypedArray())
        putStringArray(valuesKey, map.values.toTypedArray())
    }

    private fun Array<String>?.parseNativePayload(): Map<String, String> {
        return this
            ?.mapNotNull { entry ->
                val separator = entry.indexOf('=')
                if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
            }
            ?.toMap()
            .orEmpty()
    }

    private fun Bundle.optionalInt(key: String): Int? {
        return takeIf { containsKey(key) }?.getInt(key)
    }

    private fun Bundle.optionalLong(key: String): Long? {
        return takeIf { containsKey(key) }?.getLong(key)
    }

    private fun booleanSystemProperty(name: String): Boolean {
        return when (System.getProperty(name)?.trim()?.lowercase()) {
            "true", "1", "yes", "y", "on" -> true
            else -> false
        }
    }
}
