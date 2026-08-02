package org.autojs.autojs.engine

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.RemoteException
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityBroker
import org.autojs.plugin.nodejs.api.INodeJsHostCapabilityCallback
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class NodeJsHostCapabilityBroker(
    context: Context,
    request: NativeNodeEmbeddedRuntimeBridge.EmbeddedScriptRequest,
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
    private val callbackCapacity = bridgeLimits.maxPendingBridgeCalls.coerceAtLeast(1)
    private val callbackSlots = Semaphore(callbackCapacity, true)
    private val pendingCallbackCount = AtomicInteger(0)
    private val rejectedCallbackCount = AtomicInteger(0)
    private val callbackExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(callbackCapacity),
        { runnable ->
            Thread(runnable, "AutoJs6NodeHostCapabilityCallback-$id").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
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
        javaInteropExperimentalEnabled = request.javaInteropExperimentalEnabled,
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
            return
        }
        if (!callbackSlots.tryAcquire()) {
            rejectedCallbackCount.incrementAndGet()
            throw IllegalStateException(
                "AutoJs6 host capability broker exceeded its $callbackCapacity pending callback limit.",
            )
        }
        pendingCallbackCount.incrementAndGet()
        if (destroyed.get()) {
            releaseCallbackSlot()
            return
        }
        val responseScheduled = AtomicBoolean(false)
        val respond: (NodeBridgeResponse) -> Unit = { response ->
            if (responseScheduled.compareAndSet(false, true)) {
                sendResponse(callback, response)
            }
        }
        val requestJson = request
            ?.getString(NodeJsRuntimeContract.KEY_BRIDGE_REQUEST_JSON)
            .orEmpty()
        val bridgeRequest = runCatching {
            NodeBridgeRequest.fromJson(requestJson)
        }.getOrElse { error ->
            respond(
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
                respond(response)
            }
        }.onFailure { error ->
            respond(
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
            callbackExecutor.shutdownNow().forEach { _ -> releaseCallbackSlot() }
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
        return linkedMapOf<String, String>().apply {
            putAll(provider.nativeDiagnostics())
            put("embedded_script.host_broker_callback_pending_count", pendingCallbackCount.get().toString())
            put("embedded_script.host_broker_callback_pending_limit", callbackCapacity.toString())
            put("embedded_script.host_broker_callback_rejected_count", rejectedCallbackCount.get().toString())
            put("embedded_script.host_broker_destroyed", destroyed.get().toString())
        }
    }

    private fun sendResponse(
        callback: INodeJsHostCapabilityCallback,
        response: NodeBridgeResponse,
    ) {
        if (destroyed.get()) {
            releaseCallbackSlot()
            return
        }
        try {
            callbackExecutor.execute {
                try {
                    if (!destroyed.get()) {
                        deliverResponse(callback, response)
                    }
                } finally {
                    releaseCallbackSlot()
                }
            }
        } catch (_: RejectedExecutionException) {
            // destroy() seals the callback boundary before tearing down provider resources.
            if (!destroyed.get()) {
                rejectedCallbackCount.incrementAndGet()
            }
            releaseCallbackSlot()
        }
    }

    private fun releaseCallbackSlot() {
        pendingCallbackCount.updateAndGet { pending -> (pending - 1).coerceAtLeast(0) }
        callbackSlots.release()
    }

    private fun deliverResponse(
        callback: INodeJsHostCapabilityCallback,
        response: NodeBridgeResponse,
    ) {
        val responseBundle = Bundle().apply {
            putString(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_JSON, response.toJson())
            putBoolean(NodeJsRuntimeContract.KEY_BRIDGE_RESPONSE_OK, response.ok)
            response.error?.message?.let { message ->
                putString(NodeJsRuntimeContract.KEY_BRIDGE_ERROR_MESSAGE, message)
            }
        }
        try {
            callback.onResponse(responseBundle)
        } catch (_: RemoteException) {
            // Plugin process may have died; the script result path will surface the runtime failure.
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
