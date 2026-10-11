package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.runBlocking
import org.autojs.autojs6.R
import org.autojs.plugin.nodejs.api.INodeJsModuleSourceProvider
import org.autojs.plugin.nodejs.api.INodeJsRuntimeCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object NodeJsRuntimePluginBridge {

    internal data class FailureClassification(
        val fallbackSafe: Boolean,
        val failureKind: String,
        val errorCode: String,
    )

    private const val TAG = "NodeJsRuntimePlugin"
    const val ERROR_PLUGIN_UNAVAILABLE = NodeRuntimeErrorCodes.NODE_PLUGIN_UNAVAILABLE
    const val ERROR_PLUGIN_CONTRACT_MISMATCH = NodeRuntimeErrorCodes.NODE_PLUGIN_CONTRACT_MISMATCH
    const val ERROR_PLUGIN_EXECUTION_LOST = NodeRuntimeErrorCodes.NODE_PLUGIN_EXECUTION_LOST
    private val activeModuleSourceProviders =
        ConcurrentHashMap<String, INodeJsModuleSourceProvider>()
    private val activeExecutionIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledExecutionIds = ConcurrentHashMap.newKeySet<String>()
    private val backendExecutionIds = ConcurrentHashMap.newKeySet<String>()
    private val executionLifecycleLock = Any()

    internal fun classifyPluginFailure(error: Throwable): FailureClassification {
        val fallbackSafe = when (error) {
            is NodeRuntimePluginUnavailableException,
            is NodeRuntimePluginContractException,
            -> true
            is NodeRuntimePluginOperationException -> !error.dispatchStarted
            else -> false
        }
        val failureKind = when (error) {
            is NodeRuntimePluginContractException -> "contract"
            is NodeRuntimePluginUnavailableException -> "unavailable"
            is NodeRuntimePluginOperationException -> if (error.dispatchStarted) "execution_lost" else "session"
            else -> "unknown"
        }
        val errorCode = when (failureKind) {
            "contract" -> ERROR_PLUGIN_CONTRACT_MISMATCH
            "execution_lost" -> ERROR_PLUGIN_EXECUTION_LOST
            else -> ERROR_PLUGIN_UNAVAILABLE
        }
        return FailureClassification(fallbackSafe, failureKind, errorCode)
    }

    fun isRequested(): Boolean = true

    fun isRequired(): Boolean = true

    fun runScript(
        context: Context,
        request: NodePluginScriptRequest,
        launchSurface: String = NodeExecutionModePolicy.LAUNCH_SURFACE_SCRIPT,
        outputListener: ((type: String, text: String) -> Unit)? = null,
        stdinListener: ((ready: Boolean) -> Unit)? = null,
        startupTimings: Map<String, Long> = emptyMap(),
    ): NodePluginScriptResult {
        val startedAt = SystemClock.elapsedRealtime()
        NodeJsRuntimePluginHost.prepareForExecution(context)
        val launchStartedAt = startupTimings["launchStartedAtMs"]?.takeIf { it > 0L } ?: startedAt
        val firstOutputAt = java.util.concurrent.atomic.AtomicLong()
        val requestWithProvider = request.copy(
            executionId = request.executionId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString(),
            inspectorEnabled = request.inspectorEnabled ?: PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.key_nodejs_inspector_enabled), false),
        ).withHostModuleSourceProvider()
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
        var hostCapabilityBroker: NodeJsHostCapabilityBroker? = null
        return try {
            if (executionId in cancelledExecutionIds) {
                return pluginCancellationResult(
                    requestWithProvider,
                    startedAt,
                    "after_provider_registration",
                )
            }
            runCatching {
                val broker = NodeJsHostCapabilityBroker(
                    context,
                    requestWithProvider,
                    launchSurface,
                ).also { hostCapabilityBroker = it }
                val workspaceStartedAt = SystemClock.elapsedRealtime()
                val workspaceTransport = try {
                    NodeJsPluginWorkspaceArchiveTransport.create(context, requestWithProvider)
                } catch (error: Throwable) {
                    throw NodeRuntimePluginUnavailableException(
                        "Unable to prepare the external Node.js runtime workspace.",
                        error,
                    )
                }
                val workspacePreparedAt = SystemClock.elapsedRealtime()
                var reservation: NodeRuntimePluginExecutionRegistry.Reservation? = null
                try {
                    val pluginRequest = requestWithProvider.toPluginBundle(broker).also {
                        workspaceTransport.attachTo(it)
                    }
                    val backendDispatchRegistered = synchronized(executionLifecycleLock) {
                        if (executionId in cancelledExecutionIds) {
                            false
                        } else {
                            reservation = NodeJsRuntimePluginHost.reserveExecution(executionId)
                                ?: throw NodeRuntimePluginUnavailableException(
                                    "Unable to reserve Node.js runtime plugin execution '$executionId'.",
                                )
                            backendExecutionIds.add(executionId)
                            true
                        }
                    }
                    if (!backendDispatchRegistered) {
                        return@runCatching pluginCancellationResult(
                            requestWithProvider,
                            startedAt,
                            "before_backend_dispatch",
                        )
                    }
                    val streamedEventCount = java.util.concurrent.atomic.AtomicInteger(0)
                    val runtimeCallback = object : INodeJsRuntimeCallback.Stub() {
                        override fun onEvent(event: Bundle?) {
                            val type = event?.getString(NodeJsRuntimeContract.KEY_EVENT_TYPE) ?: return
                            if (type == NodeJsRuntimeContract.EVENT_STDIN_STATE) {
                                runCatching { stdinListener?.invoke(event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT) == "true") }
                                    .onFailure { Log.w(TAG, "Node.js stdin listener failed.", it) }
                                return
                            }
                            if (type != NodeJsRuntimeContract.EVENT_STDOUT &&
                                type != NodeJsRuntimeContract.EVENT_STDERR
                            ) {
                                return
                            }
                            val text = event.getString(NodeJsRuntimeContract.KEY_EVENT_TEXT).orEmpty()
                            if (text.isEmpty()) {
                                return
                            }
                            if (type == NodeJsRuntimeContract.EVENT_STDOUT) firstOutputAt.compareAndSet(0L, SystemClock.elapsedRealtime())
                            streamedEventCount.incrementAndGet()
                            runCatching { outputListener?.invoke(type, text) }.onFailure { error ->
                                Log.w(TAG, "Node.js runtime plugin output listener failed.", error)
                            }
                        }
                    }
                    val runResult = runBlocking {
                        NodeJsRuntimePluginHost.runScriptWithInfo(
                            context = context,
                            request = pluginRequest,
                            callback = runtimeCallback,
                            reservation = checkNotNull(reservation),
                        )
                    }
                    // A normal runScript result or authenticated async terminal result proves
                    // the plugin finished its output archive; an admission acknowledgement does not.
                    // A lost response is deliberately not replayed.
                    try {
                        if (validateWorkspaceCommit(runResult.result)) {
                            workspaceTransport.synchronizeBack()
                        }
                    } catch (error: Throwable) {
                        Log.e(TAG, "Node.js runtime plugin workspace commit failed.", error)
                        throw NodeRuntimePluginOperationException(
                            message = "Node.js runtime plugin workspace commit could not be verified or applied: " +
                                    (error.message ?: error.javaClass.name),
                            cause = error,
                            candidate = runResult.candidate,
                            diagnostics = runResult.diagnostics,
                            dispatchStarted = true,
                        )
                    }
                    if (executionId in cancelledExecutionIds) {
                        pluginCancellationResult(
                            requestWithProvider,
                            startedAt,
                            "after_backend_result",
                            backendDispatched = true,
                        )
                    } else {
                        val issuedAt = runResult.result.getLong("hostRunScriptIssuedAtMs", workspacePreparedAt)
                        val timingValues = startupTimings.filterKeys { it != "launchStartedAtMs" }.mapValues { it.value.toString() } + mapOf(
                            "timing.host.workspace_archive.ms" to (workspacePreparedAt - workspaceStartedAt).toString(),
                            "timing.host.bridge_prepare.ms" to (workspaceStartedAt - startedAt).toString(),
                            "timing.host.session_and_dispatch.ms" to (issuedAt - workspacePreparedAt).coerceAtLeast(0L).toString(),
                            "timing.host.launch_to_dispatch.ms" to (issuedAt - launchStartedAt).coerceAtLeast(0L).toString(),
                            "timing.host.dispatch_to_first_stdout.ms" to (firstOutputAt.get().takeIf { it > 0 }?.let { it - issuedAt } ?: -1L).toString(),
                            "timing.host.launch_to_first_stdout.ms" to (firstOutputAt.get().takeIf { it > 0 }?.let { it - launchStartedAt } ?: -1L).toString(),
                        )
                        val rawScriptResult = runResult.toPluginScriptResult(requestWithProvider, startedAt)
                        val scriptResult = rawScriptResult.copy(nativeValues = rawScriptResult.nativeValues + timingValues)
                        if (streamedEventCount.get() > 0) {
                            scriptResult.copy(
                                nativeValues = scriptResult.nativeValues + mapOf(
                                    "embedded_script.runtime_plugin.streamed_output_events" to
                                            streamedEventCount.get().toString(),
                                ),
                            )
                        } else {
                            scriptResult
                        }
                    }
                } finally {
                    reservation?.let(NodeJsRuntimePluginHost::releaseExecution)
                    workspaceTransport.close()
                }
            }.getOrElse { error ->
                if (executionId in cancelledExecutionIds) {
                    pluginCancellationResult(
                        requestWithProvider,
                        startedAt,
                        "after_backend_dispatch",
                        backendDispatched =
                                (error as? NodeRuntimePluginOperationException)?.dispatchStarted == true,
                    )
                } else {
                    pluginFailureResult(requestWithProvider, startedAt, error)
                }
            }
        } finally {
            hostCapabilityBroker?.destroy(
                Bundle().apply {
                    putString(
                        NodeJsRuntimeContract.KEY_ERROR_MESSAGE,
                        "Node.js runtime plugin host request finished.",
                    )
                },
            )
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

    fun postMessage(executionId: String, message: Bundle): Boolean =
        NodeJsRuntimePluginHost.postMessage(executionId, message)

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
        request: NodePluginScriptRequest,
        startedAt: Long,
        stage: String,
        backendDispatched: Boolean = false,
    ): NodePluginScriptResult {
        return NodePluginScriptResult(
            succeeded = false,
            exitCode = 1,
            resultText = null,
            stdout = "",
            stderr = "",
            errorName = "NodeRuntimePluginExecutionCancelledError",
            errorMessage = "Node.js runtime plugin execution was cancelled.",
            errorStack = null,
            errorCode = NodeRuntimeErrorCodes.NODE_QUEUE_CANCELLED,
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            processName = "",
            pid = Process.myPid(),
            sourceName = request.sourceName,
            nativeValues = mapOf(
                "embedded_script.runtime_plugin.enabled" to "true",
                "embedded_script.runtime_plugin.backend_dispatched" to backendDispatched.toString(),
                "embedded_script.runtime_plugin.fallback_safe" to "false",
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

    private fun NodePluginScriptRequest.toPluginBundle(
        hostCapabilityBroker: NodeJsHostCapabilityBroker,
    ): Bundle {
        return Bundle().apply {
            putInt(NodeJsRuntimeContract.KEY_CONTRACT_VERSION, NodeJsRuntimeContract.CONTRACT_VERSION)
            putString(NodeJsRuntimeContract.KEY_EXECUTION_ID, executionId)
            putString(NodeJsRuntimeContract.KEY_SOURCE, source)
            putString(NodeJsRuntimeContract.KEY_SOURCE_NAME, sourceName)
            putString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY, workingDirectory)
            putString(NodeJsRuntimeContract.KEY_SANDBOX_ROOT, sandboxRoot)
            putLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS, timeoutMs)
            if (idleExitMs > 0L) putLong(NodeJsRuntimeContract.KEY_IDLE_EXIT_MS, idleExitMs)
            putString(NodeJsRuntimeContract.KEY_EXECUTION_MODE, executionMode)
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
            putBoolean(
                NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SNAPSHOT,
                typeScriptPrecompiledSnapshot,
            )
            putStringArray(
                NodeJsRuntimeContract.KEY_TYPESCRIPT_PRECOMPILED_SOURCE_NAMES,
                typeScriptPrecompiledSourceNames.sorted().toTypedArray(),
            )
            esmEnabled?.let { putBoolean(NodeJsRuntimeContract.KEY_ESM_ENABLED, it) }
            dynamicImportEnabled?.let { putBoolean(NodeJsRuntimeContract.KEY_DYNAMIC_IMPORT_ENABLED, it) }
            rawNodeNetworkModulesEnabled?.let {
                putBoolean(NodeJsRuntimeContract.KEY_RAW_NODE_NETWORK_MODULES_ENABLED, it)
            }
            putBoolean(NodeJsRuntimeContract.KEY_INSPECTOR_ENABLED, inspectorEnabled == true)
            workerThreadsEnabled?.let { putBoolean(NodeJsRuntimeContract.KEY_WORKER_THREADS_ENABLED, it) }
            childProcessEnabled?.let { putBoolean(NodeJsRuntimeContract.KEY_CHILD_PROCESS_ENABLED, it) }
            javaInteropEnabled?.let { putBoolean(NodeJsRuntimeContract.KEY_JAVA_INTEROP_ENABLED, it) }
            putBinder(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER, hostCapabilityBroker)
            moduleSourceProvider?.let { provider ->
                putBinder(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER, provider.asBinder())
            }
            moduleSourceProviderVersion?.let { version ->
                putInt(NodeJsRuntimeContract.KEY_MODULE_SOURCE_PROVIDER_VERSION, version)
            }
        }
    }

    private fun NodeJsRuntimePluginHost.ScriptRunResult.toPluginScriptResult(
        request: NodePluginScriptRequest,
        startedAt: Long,
    ): NodePluginScriptResult {
        return result.toPluginScriptResult(
            request = request,
            startedAt = startedAt,
            runtimeInfo = runtimeInfo,
            candidate = candidate,
            hostDiagnostics = diagnostics,
        )
    }

    private fun Bundle.toPluginScriptResult(
        request: NodePluginScriptRequest,
        startedAt: Long,
        runtimeInfo: Bundle,
        candidate: NodeJsRuntimePluginHost.Candidate,
        hostDiagnostics: NodeJsRuntimePluginHost.OperationDiagnostics,
    ): NodePluginScriptResult {
        val capabilities = runtimeInfo.getStringArray(NodeJsRuntimeContract.KEY_CAPABILITIES).orEmpty()
        val succeeded = getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false)
        val timedOut = getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, false)
        val nativeValues = getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD)
            .parseNativePayload()
            .toMutableMap()
            .apply {
                putFallback("embedded_script.runtime_plugin.enabled", "true")
                putFallback("embedded_script.runtime_plugin.contract_version", NodeJsRuntimeContract.CONTRACT_VERSION.toString())
                putFallback("embedded_script.runtime_plugin.package_name", candidate.serviceInfo.packageName)
                putFallback("embedded_script.runtime_plugin.service_name", candidate.serviceInfo.name)
                putFallback("embedded_script.runtime_plugin.package_version_code", candidate.packageVersionCode?.toString().orEmpty())
                putFallback("embedded_script.runtime_plugin.package_last_update_time", candidate.packageLastUpdateTime.toString())
                putFallback("embedded_script.runtime_plugin.package_uid", candidate.packageUid.toString())
                putFallback("embedded_script.runtime_plugin.signer_sha256", candidate.signerSha256.orEmpty())
                putFallback("embedded_script.runtime_plugin.authorization_state", candidate.authorizationState)
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
                putFallback("embedded_script.runtime_plugin.fallback_safe", "false")
                pluginLifecycleNativeValues(
                    executionId = request.executionId.orEmpty(),
                    succeeded = succeeded,
                    timedOut = timedOut,
                ).forEach { (key, value) -> putFallback(key, value) }
                putAll(hostDiagnostics.toNativeValues())
            }
        return NodePluginScriptResult(
            succeeded = succeeded,
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
            timedOut = timedOut,
            timeoutMs = optionalLong(NodeJsRuntimeContract.KEY_TIMEOUT_MS),
            workingDirectory = getString(NodeJsRuntimeContract.KEY_WORKING_DIRECTORY) ?: request.workingDirectory,
        )
    }

    private fun pluginFailureResult(
        request: NodePluginScriptRequest,
        startedAt: Long,
        error: Throwable,
    ): NodePluginScriptResult {
        val operationError = error as? NodeRuntimePluginOperationException
        val classification = classifyPluginFailure(error)
        val candidate = operationError?.candidate
        val diagnostics = operationError?.diagnostics?.toNativeValues().orEmpty()
        return NodePluginScriptResult(
            succeeded = false,
            exitCode = 1,
            resultText = null,
            stdout = "",
            stderr = "",
            errorName = error.javaClass.simpleName,
            errorMessage = error.message ?: error.javaClass.name,
            errorStack = error.stackTraceToString(),
            errorCode = classification.errorCode,
            elapsedMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
            processName = "",
            pid = Process.myPid(),
            sourceName = request.sourceName,
            nativeValues = mapOf(
                "embedded_script.runtime_plugin.enabled" to "true",
                "embedded_script.runtime_plugin.failure" to "true",
                "embedded_script.runtime_plugin.failure_kind" to classification.failureKind,
                "embedded_script.runtime_plugin.backend_dispatched" to
                        (operationError?.dispatchStarted == true).toString(),
                "embedded_script.runtime_plugin.fallback_safe" to classification.fallbackSafe.toString(),
                "embedded_script.runtime_plugin.package_name" to
                        candidate?.serviceInfo?.packageName.orEmpty(),
                "embedded_script.runtime_plugin.service_name" to
                        candidate?.serviceInfo?.name.orEmpty(),
                "embedded_script.error_code" to classification.errorCode,
            ) + diagnostics,
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

    private fun validateWorkspaceCommit(result: Bundle): Boolean {
        if (
            result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE) == NodeRuntimeErrorCodes.NODE_PLUGIN_BUSY ||
            result.getBoolean(NodeJsRuntimeContract.KEY_TIMED_OUT, false)
        ) {
            return false
        }
        val nativeValues = result
            .getStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD)
            .parseNativePayload()
        val status = nativeValues[WORKSPACE_STATUS_KEY]
        val legacyScanRan = nativeValues[WORKSPACE_LEGACY_SCAN_KEY]
        val inputScanCount = nativeValues[WORKSPACE_INPUT_SCAN_COUNT_KEY]
        // A plugin that rejects a request before native dispatch (for example
        // unsupported TSX entries or policy-metadata failures) deliberately
        // withholds the workspace commit so its private materializations never
        // reach the host archive. That declared no-commit failure is a valid
        // terminal response, not a lost execution.
        val declaredPreDispatchFailure =
            !result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false) &&
                nativeValues[WORKSPACE_COMMIT_ALLOWED_KEY] == "false" &&
                nativeValues[NATIVE_DISPATCH_STARTED_KEY] == "false"
        if (declaredPreDispatchFailure) {
            return true
        }
        if (status != "committed" || legacyScanRan != "false" || inputScanCount != "0") {
            throw NodeRuntimePluginContractException(
                "Node.js runtime plugin did not return a complete zero-scan workspace commit: " +
                        "status=${status ?: "<missing>"}, " +
                        "legacyDirectoryScanRan=${legacyScanRan ?: "<missing>"}, " +
                        "inputDirectoryScanCount=${inputScanCount ?: "<missing>"}, " +
                        "pluginSucceeded=${result.getBoolean(NodeJsRuntimeContract.KEY_SUCCEEDED, false)}, " +
                        "pluginErrorCode=${result.getString(NodeJsRuntimeContract.KEY_ERROR_CODE).orEmpty()}, " +
                        "pluginErrorMessage=${result.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE).orEmpty()}.",
            )
        }
        return true
    }

    private fun pluginLifecycleNativeValues(
        executionId: String,
        succeeded: Boolean,
        timedOut: Boolean,
    ): Map<String, String> {
        val terminalState: String
        val transitions: String
        if (succeeded) {
            terminalState = "STOPPED"
            transitions = "CREATED->STARTING|STARTING->RUNNING|RUNNING->DRAINING|DRAINING->STOPPED"
        } else if (timedOut) {
            terminalState = "STOPPING"
            transitions = "CREATED->STARTING|STARTING->RUNNING|RUNNING->TIMED_OUT|TIMED_OUT->STOPPING"
        } else {
            terminalState = "STOPPING"
            transitions = "CREATED->STARTING|STARTING->RUNNING|RUNNING->FAILED|FAILED->STOPPING"
        }
        return linkedMapOf(
            "embedded_script.execution_id" to executionId,
            "embedded_script.runtime_state" to terminalState,
            "embedded_script.lifecycle_transitions" to transitions,
            "embedded_script.lifecycle_transition_count" to "4",
        )
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

    private const val WORKSPACE_STATUS_KEY =
        "embedded_script.runtime_plugin.workspace.status"
    private const val WORKSPACE_LEGACY_SCAN_KEY =
        "embedded_script.runtime_plugin.workspace.legacy_directory_scan_ran"
    private const val WORKSPACE_INPUT_SCAN_COUNT_KEY =
        "embedded_script.runtime_plugin.workspace.input_directory_scan_count"
    private const val WORKSPACE_COMMIT_ALLOWED_KEY =
        "embedded_script.runtime_plugin.workspace.commit_allowed"
    private const val NATIVE_DISPATCH_STARTED_KEY =
        "embedded_script.runtime_plugin.native_dispatch_started"
}
