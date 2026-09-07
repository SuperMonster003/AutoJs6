package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.util.Log
import org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeEngineInfo
import org.autojs.autojs.engine.NodeBridgeError
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.autojs.engine.NodeBridgeLimits
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.McpServerContract
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Host-side capability proxy of one MCP server session (roadmap P1.2, decisions D2 and D17).
 *
 * Every `dispatch` carries a `NodeBridgeRequest` JSON envelope. The broker first applies the
 * session [grant] (method allow-list, permission subset, inline size), then hands the request to
 * an [AndroidNodeBridgeCapabilityProvider] whose permission manifest is the grant itself, so the
 * provider's own capability audit can never be wider than what the host granted.
 */
class McpHostCapabilityBroker(
    context: Context,
    val id: String,
    val grant: McpCapabilityGrant,
    sourceName: String = DEFAULT_SOURCE_NAME,
) : IMcpHostCapabilityBroker.Stub() {

    private val appContext = context.applicationContext
    private val destroyed = AtomicBoolean(false)

    private val engineInfo = NodeBridgeEngineInfo
        .fromContext(
            context = appContext,
            id = id,
            sourceName = sourceName.ifBlank { DEFAULT_SOURCE_NAME },
            cwd = runCatching { WorkingDirectoryUtils.path }.getOrDefault(""),
            pid = Process.myPid(),
        )
        .copy(engineName = ENGINE_NAME, backend = "plugin")

    private val provider = AndroidNodeBridgeCapabilityProvider(
        context = appContext,
        engineInfo = engineInfo,
        javaInteropEnabled = false,
        bridgeLimits = NodeBridgeLimits.Policy(
            maxPendingBridgeCalls = grant.maxConcurrentCalls,
            maxImageHandles = 4,
            maxShellCommands = 1,
            maxNetworkRequests = 0,
            maxWebSocketConnections = 0,
            accessibilityQueriesPerSecond = grant.accessibilityQueriesPerSecond,
            sources = listOf(GRANT_SOURCE),
        ),
        permissionManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
            capabilities = grant.permissions,
            enforced = true,
            source = GRANT_SOURCE,
        ),
    )

    override fun getBrokerInfo(): Bundle = Bundle().apply {
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
        putInt(McpServerContract.KEY_HOST_CAPABILITY_BROKER_VERSION, McpServerContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION)
        putString(McpServerContract.KEY_HOST_CAPABILITY_BROKER_ID, id)
        putString(McpServerContract.KEY_BRIDGE_ENGINE_INFO, engineInfo.toJson())
        putStringArray(McpServerContract.KEY_HOST_CAPABILITY_MODULES, grant.modules.toTypedArray())
        putStringArray(McpServerContract.KEY_GRANT_METHODS, grant.allowedMethods.toTypedArray())
        putStringArray(McpServerContract.KEY_GRANT_PERMISSIONS, grant.permissions.toTypedArray())
        putInt(McpServerContract.KEY_GRANT_MAX_REQUEST_BYTES, grant.maxRequestBytes)
        putInt(McpServerContract.KEY_GRANT_MAX_CONCURRENT_CALLS, grant.maxConcurrentCalls)
        putInt(McpServerContract.KEY_GRANT_ACCESSIBILITY_QUERIES_PER_SECOND, grant.accessibilityQueriesPerSecond)
        putLong(McpServerContract.KEY_GRANT_DEFAULT_TIMEOUT_MS, grant.defaultTimeoutMs)
        putLong(McpServerContract.KEY_GRANT_MAX_TIMEOUT_MS, grant.maxTimeoutMs)
    }

    override fun dispatch(request: Bundle?, callback: IMcpHostCapabilityCallback?) {
        if (callback == null) {
            return
        }
        val requestJson = request?.getString(McpServerContract.KEY_BRIDGE_REQUEST_JSON).orEmpty()
        val requestBytes = requestJson.toByteArray(StandardCharsets.UTF_8).size
        if (destroyed.get()) {
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    recoverId(requestJson),
                    NodeBridgeError.processDead("AutoJs6 MCP capability broker is already destroyed."),
                ),
            )
            return
        }
        if (requestBytes > McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES) {
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    recoverId(requestJson),
                    NodeBridgeError.resourceLimit(
                        "AutoJs6 MCP capability broker rejected an inline request of $requestBytes bytes " +
                                "(limit ${McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES}).",
                    ),
                ),
            )
            return
        }
        val bridgeRequest = runCatching { NodeBridgeRequest.fromJson(requestJson) }.getOrElse { error ->
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    recoverId(requestJson),
                    NodeBridgeError.invalidRequest(error.message ?: "AutoJs6 MCP capability broker request is malformed."),
                ),
            )
            return
        }
        val clientName = request?.getString(McpServerContract.KEY_BRIDGE_CLIENT_NAME)
            ?.take(McpServerContract.MAX_CLIENT_NAME_BYTES)
        val denial = grantDenial(bridgeRequest, requestBytes)
        if (denial != null) {
            Log.i(TAG, "denied ${bridgeRequest.module}.${bridgeRequest.method} for ${clientName ?: "unknown client"}: ${denial.message}")
            sendResponse(callback, NodeBridgeResponse.failure(bridgeRequest.id, denial))
            return
        }
        val boundedRequest = bridgeRequest.copy(timeoutMs = grant.effectiveTimeoutMs(bridgeRequest.timeoutMs))
        runCatching {
            provider.dispatch(boundedRequest) { response -> sendResponse(callback, response) }
        }.onFailure { error ->
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    boundedRequest.id,
                    NodeBridgeError.providerFailed(
                        message = error.message ?: error.javaClass.name,
                        module = boundedRequest.module,
                        method = boundedRequest.method,
                        stack = error.stackTraceToString(),
                    ),
                ),
            )
        }
    }

    override fun destroy(reason: Bundle?) {
        if (destroyed.compareAndSet(false, true)) {
            val message = reason?.getString(McpServerContract.KEY_ERROR_MESSAGE)?.takeIf { it.isNotBlank() }
                ?: reason?.getString(McpServerContract.KEY_ERROR_CODE)?.takeIf { it.isNotBlank() }
                ?: "AutoJs6 MCP capability broker destroyed."
            provider.destroy(NodeBridgeError.processDead(message))
            Log.i(TAG, "destroyed broker $id: $message")
        }
    }

    val isDestroyed: Boolean
        get() = destroyed.get()

    private fun grantDenial(request: NodeBridgeRequest, requestBytes: Int): NodeBridgeError? {
        return when (val decision = grant.evaluate(request.module, request.method, request.permissions, requestBytes)) {
            McpCapabilityGrant.Decision.Allowed -> null
            is McpCapabilityGrant.Decision.Denied -> when (decision.reason) {
                McpCapabilityGrant.Reason.METHOD_NOT_GRANTED -> NodeBridgeError.permissionDenied(
                    message = "AutoJs6 MCP session grant does not include ${request.module}.${request.method}.",
                    module = request.module,
                    method = request.method,
                    category = NodeBridgeErrorCategory.CAPABILITY_DENIED,
                )
                McpCapabilityGrant.Reason.PERMISSION_NOT_GRANTED -> NodeBridgeError.capabilityNotDeclared(
                    request.module,
                    request.method,
                    decision.detail,
                )
                McpCapabilityGrant.Reason.REQUEST_TOO_LARGE -> NodeBridgeError.resourceLimit(
                    message = "AutoJs6 MCP session grant rejected ${request.module}.${request.method}: request payload is $requestBytes bytes, limit ${grant.maxRequestBytes}.",
                    module = request.module,
                    method = request.method,
                )
            }
        }
    }

    private fun sendResponse(callback: IMcpHostCapabilityCallback, response: NodeBridgeResponse) {
        val payload = if (response.ok) extractOneShotPayload(response) else null
        var effective = payload?.response ?: response
        var responseJson = effective.toJson()
        if (responseJson.toByteArray(StandardCharsets.UTF_8).size > McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES) {
            effective = NodeBridgeResponse.failure(
                response.id,
                NodeBridgeError.resourceLimit(
                    "AutoJs6 MCP capability broker result exceeds the inline limit of " +
                            "${McpServerContract.MAX_BRIDGE_INLINE_JSON_BYTES} bytes; use a payload descriptor method.",
                ),
            )
            responseJson = effective.toJson()
        }
        val descriptor = payload?.descriptor?.takeIf { effective.ok }
        val bundle = Bundle().apply {
            putString(McpServerContract.KEY_BRIDGE_RESPONSE_JSON, responseJson)
            putBoolean(McpServerContract.KEY_BRIDGE_RESPONSE_OK, effective.ok)
            effective.error?.message?.let { putString(McpServerContract.KEY_BRIDGE_ERROR_MESSAGE, it) }
            if (descriptor != null) {
                putParcelable(McpServerContract.KEY_BRIDGE_PAYLOAD_FD, descriptor)
                putLong(McpServerContract.KEY_BRIDGE_PAYLOAD_BYTES, payload.bytes)
                putString(McpServerContract.KEY_BRIDGE_PAYLOAD_MIME, payload.mime)
            }
        }
        try {
            callback.onResponse(bundle)
        } catch (_: RemoteException) {
            // The plugin process died; the session lease observes the death separately.
        } finally {
            // The transaction carried its own duplicate; the receiver owns that one.
            payload?.descriptor?.let { runCatching { it.close() } }
        }
    }

    /**
     * A result whose `payload` is a one-shot file under the host cache (screenshots) travels as
     * a read-only descriptor: the file is opened, unlinked, and the JSON keeps only the metadata
     * (`payload.kind = "descriptor"`). Files elsewhere are left untouched and stay inline.
     */
    private fun extractOneShotPayload(response: NodeBridgeResponse): OneShotPayload? {
        val resultJson = response.resultJson ?: return null
        if (!resultJson.contains("\"oneShot\":true")) {
            return null
        }
        val json = runCatching { JSONObject(resultJson) }.getOrNull() ?: return null
        val payload = json.optJSONObject(KEY_PAYLOAD) ?: return null
        if (payload.optString(KEY_PAYLOAD_KIND) != PAYLOAD_KIND_FILE || !payload.optBoolean(KEY_PAYLOAD_ONE_SHOT, false)) {
            return null
        }
        val path = payload.optString(KEY_PAYLOAD_PATH).takeIf { it.isNotBlank() } ?: return null
        val cacheRoot = runCatching { appContext.cacheDir.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        if (!file.path.startsWith(cacheRoot.path + File.separator) || !file.isFile) {
            return null
        }
        val bytes = file.length()
        val mime = payload.optString(KEY_PAYLOAD_MIME)
        if (bytes > McpServerContract.MAX_BRIDGE_PAYLOAD_BYTES) {
            file.delete()
            return OneShotPayload(
                descriptor = null,
                bytes = bytes,
                mime = mime,
                response = NodeBridgeResponse.failure(
                    response.id,
                    NodeBridgeError.resourceLimit(
                        "AutoJs6 MCP capability broker payload of $bytes bytes exceeds ${McpServerContract.MAX_BRIDGE_PAYLOAD_BYTES}.",
                    ),
                ),
            )
        }
        val descriptor = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrElse { error ->
            Log.w(TAG, "unable to open payload ${file.path}: ${error.message}")
            return null
        }
        file.delete()
        payload.remove(KEY_PAYLOAD_PATH)
        payload.put(KEY_PAYLOAD_KIND, PAYLOAD_KIND_DESCRIPTOR)
        json.put(KEY_PAYLOAD, payload)
        return OneShotPayload(descriptor, bytes, mime, NodeBridgeResponse.success(response.id, json.toString()))
    }

    private class OneShotPayload(
        val descriptor: ParcelFileDescriptor?,
        val bytes: Long,
        val mime: String,
        val response: NodeBridgeResponse,
    )

    private fun recoverId(requestJson: String): String = NodeBridgeRequest.recoverId(requestJson) ?: "invalid"

    companion object {
        const val ENGINE_NAME = "mcp"
        const val DEFAULT_SOURCE_NAME = "mcp-server"
        private const val GRANT_SOURCE = "mcp-grant"
        private const val TAG = "McpHostBroker"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_PAYLOAD_KIND = "kind"
        private const val KEY_PAYLOAD_PATH = "path"
        private const val KEY_PAYLOAD_ONE_SHOT = "oneShot"
        private const val KEY_PAYLOAD_MIME = "mime"
        private const val PAYLOAD_KIND_FILE = "file"
        private const val PAYLOAD_KIND_DESCRIPTOR = "descriptor"
    }
}
