package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.RemoteException
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.concurrent.atomic.AtomicBoolean

internal class NodeJsHostCapabilityBroker(
    context: Context,
    request: NodePluginScriptRequest,
    launchSurface: String,
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
            putStringArray(NodeJsRuntimeContract.KEY_NATIVE_PAYLOAD, nativePayload(nativeDiagnostics()))
        }
    }

    override fun dispatch(request: Bundle?, callback: INodeJsHostCapabilityCallback?) {
        if (callback == null) {
            return
        }
        if (destroyed.get()) {
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
        val bridgeRequest = runCatching {
            NodeBridgeRequest.fromJson(requestJson)
        }.getOrElse { error ->
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
                sendResponse(callback, response)
            }
        }.onFailure { error ->
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
    ) {
        val responseBundle = Bundle().apply {
            putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON, response.toJson())
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
        } catch (_: RemoteException) {
            // Plugin process may have died; the script result path will surface the runtime failure.
        } finally {
            response.binary?.close()
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
}
