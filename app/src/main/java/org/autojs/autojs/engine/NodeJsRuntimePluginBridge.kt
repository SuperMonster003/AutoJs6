package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NodeJsRuntimePluginBridge {

    private const val TAG = "NodeJsRuntimePlugin"
    private const val PLUGIN_RUNTIME_ENABLED_PROPERTY = "autojs.nodejs.runtime.plugin.enabled"
    private const val PLUGIN_RUNTIME_REQUIRED_PROPERTY = "autojs.nodejs.runtime.plugin.required"
    const val ERROR_PLUGIN_UNAVAILABLE = "ERR_AUTOJS6_NODE_PLUGIN_UNAVAILABLE"
    private val activeModuleSourceProviders =
        ConcurrentHashMap<String, INodeJsModuleSourceProvider>()
    private val activeExecutionIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledExecutionIds = ConcurrentHashMap.newKeySet<String>()
    private val backendExecutionIds = ConcurrentHashMap.newKeySet<String>()
    private val executionLifecycleLock = Any()

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
        val requestWithProvider = request.withHostModuleSourceProvider()
        if (!isRequested()) {
            return NativeNodeEmbeddedScriptService.runScript(context, requestWithProvider, launchSurface)
        }
        val pluginResult = runScript(context, requestWithProvider, launchSurface)
        if (
            pluginResult.errorCode == ERROR_PLUGIN_UNAVAILABLE &&
            !isRequired()
        ) {
            Log.w(
                TAG,
                "Node.js runtime plugin failed; falling back to embedded service " +
                        "sourceName=${request.sourceName} errorCode=${pluginResult.errorCode.orEmpty()}",
            )
            return NativeNodeEmbeddedScriptService.runScript(
                context,
                requestWithProvider.copy(moduleSourceProvider = null),
                launchSurface,
            )
        }
        return pluginResult.withLaunchSurfaceNativeValue(launchSurface)
    }

    fun runScript(
        context: Context,
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val startedAt = SystemClock.elapsedRealtime()
        val requestWithProvider = if (request.executionId.isNullOrBlank()) {
            request.copy(executionId = UUID.randomUUID().toString())
        } else {
            request
        }.withHostModuleSourceProvider()
        val provider = requestWithProvider.moduleSourceProvider
        val executionId = requestWithProvider.executionId.orEmpty()
        val lifecycleRegistered = synchronized(executionLifecycleLock) {
            if (executionId.isBlank() || !activeExecutionIds.add(executionId)) {
                false
            } else {
                cancelledExecutionIds.remove(executionId)
                provider?.let { activeModuleSourceProviders[executionId] = it }
                true
            }
        }
        if (!lifecycleRegistered) {
            cancelModuleSourceProvider(provider, "Node.js runtime plugin request registration failed.")
            return pluginCancellationResult(requestWithProvider, startedAt, "provider_registration")
        }
        return try {
            if (executionId in cancelledExecutionIds) {
                return pluginCancellationResult(
                    requestWithProvider,
                    startedAt,
                    "after_provider_registration",
                )
            }
            val backendDispatchRegistered = synchronized(executionLifecycleLock) {
                if (executionId in cancelledExecutionIds) {
                    false
                } else {
                    backendExecutionIds.add(executionId)
                    true
                }
            }
            if (!backendDispatchRegistered) {
                return pluginCancellationResult(
                    requestWithProvider,
                    startedAt,
                    "before_backend_dispatch",
                )
            }
            runCatching {
                val runResult = runBlocking {
                    NodeJsRuntimePluginHost.runScriptWithInfo(
                        context,
                        requestWithProvider.toPluginBundle(context, launchSurface),
                    )
                }
                runResult.toEmbeddedScriptResult(requestWithProvider, startedAt)
            }.getOrElse { error ->
                pluginFailureResult(requestWithProvider, startedAt, error)
            }
        } finally {
            val registeredProvider = synchronized(executionLifecycleLock) {
                backendExecutionIds.remove(executionId)
                activeExecutionIds.remove(executionId)
                cancelledExecutionIds.remove(executionId)
                activeModuleSourceProviders.remove(executionId)
            }
            cancelModuleSourceProvider(registeredProvider, "Node.js runtime plugin request finished.")
            if (registeredProvider !== provider) {
                cancelModuleSourceProvider(provider, "Node.js runtime plugin request finished.")
            }
        }
    }

    fun cancelScript(context: Context, executionId: String): Boolean {
        var provider: INodeJsModuleSourceProvider? = null
        var backendActive = false
        var lifecycleKnown = false
        val acknowledgedBeforeDispatch = synchronized(executionLifecycleLock) {
            if (executionId !in activeExecutionIds) {
                false
            } else {
                lifecycleKnown = true
                cancelledExecutionIds.add(executionId)
                provider = activeModuleSourceProviders.remove(executionId)
                backendActive = executionId in backendExecutionIds
                !backendActive
            }
        }
        cancelModuleSourceProvider(provider, "Node.js runtime plugin request was cancelled.")
        if (!lifecycleKnown) {
            return false
        }
        if (acknowledgedBeforeDispatch) {
            return true
        }
        val pluginCancelled = runCatching {
            runBlocking {
                NodeJsRuntimePluginHost.cancelScript(context, executionId)
            }
        }.getOrDefault(false)
        return pluginCancelled
    }

    private fun pluginCancellationResult(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        startedAt: Long,
        stage: String,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult(
            succeeded = false,
            exitCode = 1,
            resultText = null,
            stdout = "",
            stderr = "",
            errorName = "NodeRuntimePluginExecutionCancelledError",
            errorMessage = "Node.js runtime plugin execution was cancelled before backend dispatch.",
            errorStack = null,
            errorCode = NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED,
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            processName = "",
            pid = Process.myPid(),
            sourceName = request.sourceName,
            nativeValues = mapOf(
                "embedded_script.runtime_plugin.enabled" to "true",
                "embedded_script.runtime_plugin.backend_dispatched" to "false",
                "embedded_script.client.cancelled" to "true",
                "embedded_script.client.cancellation_stage" to stage,
                "embedded_script.error_code" to NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED,
            ),
            workingDirectory = request.workingDirectory,
        )
    }

    private fun cancelModuleSourceProvider(
        provider: INodeJsModuleSourceProvider?,
        reason: String,
    ) {
        provider ?: return
        runCatching { provider.cancel(reason) }
            .onFailure { error -> Log.w(TAG, "module_source_provider.cancel.failed", error) }
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
            moduleSourceProvider?.let { provider ->
                putBinder(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER, provider.asBinder())
            }
        }
    }

    private fun NodeJsRuntimePluginHost.ScriptRunResult.toEmbeddedScriptResult(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        startedAt: Long,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        return result.toEmbeddedScriptResult(
            request = request,
            startedAt = startedAt,
            runtimeInfo = runtimeInfo,
            candidate = candidate,
        )
    }

    private fun Bundle.toEmbeddedScriptResult(
        request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
        startedAt: Long,
        runtimeInfo: Bundle,
        candidate: NodeJsRuntimePluginHost.Candidate,
    ): NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptResult {
        val capabilities = runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty()
        val nativeValues = getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD)
            .parseNativePayload()
            .toMutableMap()
            .apply {
                putFallback("embedded_script.runtime_plugin.enabled", "true")
                putFallback("embedded_script.runtime_plugin.contract_version", NodeJsRuntimeContract.CONTRACT_VERSION.toString())
                putFallback("embedded_script.runtime_plugin.package_name", candidate.serviceInfo.packageName)
                putFallback("embedded_script.runtime_plugin.service_name", candidate.serviceInfo.name)
                putFallback("embedded_script.runtime_plugin.package_version_code", candidate.packageVersionCode?.toString().orEmpty())
                putFallback("embedded_script.runtime_plugin.runtime_slot", runtimeInfo.getString(NodeJsRuntimeContract.KEY_RUNTIME_SLOT).orEmpty())
                putFallback("embedded_script.runtime_plugin.node_version", runtimeInfo.getString(NodeJsRuntimeContract.KEY_NODE_VERSION).orEmpty())
                putFallback("embedded_script.runtime_plugin.capability_count", capabilities.size.toString())
                putFallback(
                    "embedded_script.runtime_plugin.capability_host_broker",
                    (NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_BROKER in capabilities).toString(),
                )
                putFallback(
                    "embedded_script.runtime_plugin.capability_live_bridge",
                    (NodeJsRuntimeContract.CAPABILITY_HOST_CAPABILITY_LIVE_BRIDGE in capabilities).toString(),
                )
                putFallback("embedded_script.runtime_plugin.host_broker.attached", "true")
                putFallback(
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

    private fun MutableMap<String, String>.putFallback(key: String, value: String) {
        if (!containsKey(key)) {
            put(key, value)
        }
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
