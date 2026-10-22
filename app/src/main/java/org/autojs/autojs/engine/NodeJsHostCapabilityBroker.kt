package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.os.TransactionTooLargeException
import android.util.Log
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.concurrent.atomic.AtomicBoolean

internal class NodeJsHostCapabilityBroker(
    context: Context,
    request: NodePluginScriptRequest,
    launchSurface: String,
    console: org.autojs.autojs.core.console.ConsoleImpl? = null,
    agentExecutionState: org.autojs.autojs.execution.AgentScriptExecutionState? = null,
) : INodeJsHostCapabilityBroker.Stub() {

    val id: String = request.executionId
        ?.takeIf { it.isNotBlank() }
        ?: "node-plugin-host-${System.nanoTime()}"

    private val appContext = context.applicationContext
    private val destroyed = AtomicBoolean(false)
    private val bridgeLimits = NodeBridgeLimits.fromRuntimeModuleOrWorkingDirectory(
        request.runtimeModuleSources[NodeBridgeLimits.RUNTIME_MODULE_NAME],
        request.workingDirectory,
    )
    private val engineInfo = NodeBridgeEngineInfo
        .fromContext(
            context = appContext,
            id = id,
            sourceName = request.sourceName,
            cwd = request.workingDirectory.orEmpty(),
            executionMode = request.executionMode,
            launchSurface = launchSurface,
            spawnDepth = request.spawnDepth,
            pid = Process.myPid(),
        )
        .copy(backend = "plugin")
    private val provider = AndroidNodeBridgeCapabilityProvider(
        context = appContext,
        engineInfo = engineInfo,
        javaInteropEnabled = request.javaInteropEnabled != false,
        bridgeLimits = bridgeLimits,
        executionConsole = console,
        agentExecutionState = agentExecutionState,
    )

    override fun getBrokerInfo(): Bundle {
        return Bundle().apply {
            putInt(
                NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_VERSION,
                NodeJsRuntimeContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION,
            )
            putString(NodeJsRuntimeContract.KEY_HOST_CAPABILITY_BROKER_ID, id)
            putString(NodeJsRuntimeContract.KEY_BRIDGE_ENGINE_INFO, engineInfo.toJson())
            putStringArray(
                NodeJsRuntimeContract.KEY_HOST_CAPABILITY_MODULES,
                NodeBridgeModules.supportedMethodsByModule.keys.toTypedArray(),
            )
            // M20.2 batch 14: fetch bodies and WebSocket messages may arrive as descriptors.
            putString(
                NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_BINARY_TRANSPORT,
                NodeJsRuntimeContract.BRIDGE_REQUEST_BINARY_TRANSPORT_PFD,
            )
            // M20.2 batch 15: the plugin runtime shows this allowlist as java.policy instead of its own copy.
            putString(NodeJsRuntimeContract.KEY_JAVA_INTEROP_POLICY_JSON, ControlledJavaInteropPolicy.policyJson().toString())
            putStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD, nativePayload(nativeDiagnostics()))
        }
    }

    override fun dispatch(request: Bundle?, callback: INodeJsHostCapabilityCallback?) {
        if (callback == null) {
            return
        }
        if (destroyed.get()) {
            requestUpload(request)?.close()
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    requestId(request),
                    NodeBridgeError.processDead("AutoJs6 host capability broker is already destroyed."),
                ),
            )
            return
        }
        val requestJson = request
            ?.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON)
            .orEmpty()
        val upload = requestUpload(request)
        val bridgeRequest = runCatching {
            NodeBridgeRequest.fromJson(requestJson).copy(upload = upload)
        }.getOrElse { error ->
            upload?.close()
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    requestId(requestJson),
                    NodeBridgeError.invalidRequest(
                        error.message ?: "AutoJs6 host capability broker request is malformed.",
                    ),
                ),
            )
            return
        }
        runCatching {
            provider.dispatch(bridgeRequest) { response ->
                // The descriptor served this call; events keep flowing without it.
                if (!response.event) upload?.close()
                sendResponse(callback, response, bridgeRequest.module, bridgeRequest.method)
            }
        }.onFailure { error ->
            upload?.close()
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    bridgeRequest.id,
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = bridgeRequest.module,
                        method = bridgeRequest.method,
                        stack = error.stackTraceToString(),
                    ),
                ),
            )
        }
    }

    /** M20.2 batch 14: a request body or message delivered as a descriptor (closed once answered). */
    private fun requestUpload(request: Bundle?): NodeBridgeRequestUpload? {
        @Suppress("DEPRECATION")
        val descriptor = request?.getParcelable<ParcelFileDescriptor>(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_BINARY_PFD)
            ?: return null
        return NodeBridgeRequestUpload(
            descriptor,
            request.getLong(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_BINARY_BYTE_COUNT, -1L),
        )
    }

    override fun getNativeDiagnostics(): Bundle {
        return Bundle().apply {
            putStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD, nativePayload(nativeDiagnostics()))
        }
    }

    override fun destroy(reason: Bundle?) {
        if (destroyed.compareAndSet(false, true)) {
            provider.destroy(
                NodeBridgeError.processDead(
                    reason
                        ?.getString(NodeJsRuntimeContract.KEY_ERROR_MESSAGE)
                        ?.takeIf { it.isNotBlank() }
                        ?: "AutoJs6 host capability broker destroyed.",
                ),
            )
        }
    }

    private fun nativeDiagnostics(): Map<String, String> {
        return provider.nativeDiagnostics()
    }

    private fun sendResponse(
        callback: INodeJsHostCapabilityCallback,
        response: NodeBridgeResponse,
        module: String? = null,
        method: String? = null,
    ) {
        val responseJson = response.toJson()
        val responseBundle = Bundle().apply {
            putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON, responseJson)
            if (response.event) putBoolean("event", true)
            putBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, response.ok)
            response.binary?.let { descriptor ->
                putParcelable(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_PFD, descriptor)
                putLong(NodeJsRuntimeContract.KEY_BRIDGE_BINARY_BYTE_COUNT, response.binaryByteCount)
            }
            response.error?.message?.let { message ->
                putString(NodeJsRuntimeContract.KEY_BRIDGE_ERROR_MESSAGE, message)
            }
        }
        try {
            callback.onResponse(responseBundle)
        } catch (error: TransactionTooLargeException) {
            // Binder refused the reply, so the plugin never saw it. Answer with a small
            // provider failure instead of leaving the script to its bridge timeout.
            reportOversizedResponse(callback, response, responseJson.length, module, method, error)
        } catch (_: RemoteException) {
            // Plugin process may have died; the script result path will surface the runtime failure.
        } finally {
            response.binary?.close()
        }
    }

    private fun reportOversizedResponse(
        callback: INodeJsHostCapabilityCallback,
        response: NodeBridgeResponse,
        jsonLength: Int,
        module: String?,
        method: String?,
        error: TransactionTooLargeException,
    ) {
        val target = listOfNotNull(module, method).joinToString(".").ifBlank { "bridge call" }
        if (response.event) {
            Log.w(
                TAG,
                "AutoJs6 host event for $target ($jsonLength JSON chars) exceeded the Binder transaction size and was dropped: ${error.message}",
            )
            return
        }
        val failure = NodeBridgeResponse.failure(
            response.id,
            NodeBridgeError.providerFailed(
                message = "AutoJs6 host response for $target ($jsonLength JSON chars) exceeded the Binder transaction size; " +
                    "use the descriptor-based binary transport or request a smaller payload.",
                module = module,
                method = method,
                code = NodeRuntimeErrorCodes.BRIDGE_PROVIDER_FAILED,
            ),
        )
        val failureBundle = Bundle().apply {
            putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON, failure.toJson())
            putBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, false)
            failure.error?.message?.let { putString(NodeJsRuntimeContract.KEY_BRIDGE_ERROR_MESSAGE, it) }
        }
        try {
            callback.onResponse(failureBundle)
        } catch (_: RemoteException) {
            // Plugin process may have died; nothing else to report.
        }
    }

    private fun requestId(request: Bundle?): String {
        return requestId(request?.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON).orEmpty())
    }

    private fun requestId(requestJson: String): String {
        return NodeBridgeRequest.recoverId(requestJson) ?: "invalid"
    }

    private fun nativePayload(values: Map<String, String>): Array<String> {
        return values.map { (key, value) -> "$key=$value" }.toTypedArray()
    }

    private companion object {
        private const val TAG = "NodeJsHostCapabilityBroker"
    }
}
