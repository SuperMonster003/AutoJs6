package org.autojs.autojs.core.plugin.hostbroker

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.RemoteException
import android.util.Log
import org.autojs.autojs.engine.AndroidNodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeEngineInfo
import org.autojs.autojs.engine.NodeBridgeError
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.autojs.engine.NodeBridgeLimits
import org.autojs.autojs.engine.NodeBridgePermissionManifest
import org.autojs.autojs.engine.NodeBridgePluginFamilyStatus
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.autojs.autojs.core.plugin.ocr.OcrPluginHost
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.plugin.host.capability.api.HostCapabilityContract
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared capability dispatch, grant enforcement, and payload transport for one plugin lease.
 *
 * Every `dispatch` carries a `NodeBridgeRequest` JSON envelope. The broker first applies the
 * session [grant] (method allow-list, permission subset, inline size), then hands the request to
 * an [AndroidNodeBridgeCapabilityProvider] whose permission manifest is the grant itself, so the
 * provider's own capability audit can never be wider than what the host granted.
 */
class HostCapabilityBrokerCore(
    context: Context,
    val id: String,
    val grant: HostCapabilityGrant,
    sourceName: String = DEFAULT_SOURCE_NAME,
    engineName: String = "plugin",
    private val label: String = "host",
    grantSource: String = "host-grant",
    providerFactory: ((Context, NodeBridgeEngineInfo) -> NodeBridgeCapabilityProvider)? = null,
    private val ocrAvailable: (Context) -> Boolean = { OcrPluginHost.availableEngines(it).isNotEmpty() },
) {

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
        .copy(engineName = engineName, backend = "plugin")

    private val provider = providerFactory?.invoke(appContext, engineInfo) ?: AndroidNodeBridgeCapabilityProvider(
        context = appContext,
        engineInfo = engineInfo,
        javaInteropEnabled = false,
        bridgeLimits = NodeBridgeLimits.Policy(
            maxPendingBridgeCalls = grant.maxConcurrentCalls,
            // Image matching holds the screen and the template at once, so two handles per call (MCP roadmap P11 D23).
            maxImageHandles = 2 * grant.maxConcurrentCalls,
            maxShellCommands = 1,
            maxNetworkRequests = 0,
            maxWebSocketConnections = 0,
            accessibilityQueriesPerSecond = grant.accessibilityQueriesPerSecond,
            sources = listOf(grantSource),
        ),
        permissionManifest = NodeBridgePermissionManifest.fromDeclaredCapabilities(
            capabilities = grant.permissions,
            enforced = true,
            source = grantSource,
        ),
    )

    private val dispatchQueue = HostCapabilityDispatchQueue(provider, grant.maxConcurrentCalls) { response ->
        // Screenshot work may finish after a timeout; its one-shot cache file still belongs to us.
        extractOneShotPayload(response)?.descriptor?.close()
        response.binary?.close()
    }

    fun getBrokerInfo(): Bundle = Bundle().apply {
        putInt(HostCapabilityContract.KEY_CONTRACT_VERSION, HostCapabilityContract.CONTRACT_VERSION)
        putInt(HostCapabilityContract.KEY_HOST_CAPABILITY_BROKER_VERSION, HostCapabilityContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION)
        putString(HostCapabilityContract.KEY_HOST_CAPABILITY_BROKER_ID, id)
        putString(HostCapabilityContract.KEY_BRIDGE_ENGINE_INFO, engineInfo.toJson())
        putStringArray(HostCapabilityContract.KEY_HOST_CAPABILITY_MODULES, grant.modules.toTypedArray())
        putStringArray(HostCapabilityContract.KEY_AVAILABLE_OPTIONAL_METHODS, buildList {
            if (!destroyed.get()) {
                if ("accessibility.readScreenText" in grant.allowedMethods &&
                    grant.permissions.containsAll(listOf("accessibility", "screen_capture", "ocr")) &&
                    runCatching { ocrAvailable(appContext) }.getOrDefault(false)) add("accessibility.readScreenText")
                // Advertises the owned timeout/cancellation extension, not merely ordinary execScript.
                if (engineInfo.engineName == ThreeStoveAgentIds.ENGINE && provider is AndroidNodeBridgeCapabilityProvider &&
                    grant.allowedMethods.containsAll(listOf("engines.execScript", "engines.stop")) &&
                    grant.permissions.containsAll(listOf("engines", "engines.exec"))) add("engines.execScript")
                // Plugin families present when the session opens (MCP roadmap P11 D27); `app.pluginCapabilities` is the live
                // view. readScreenText keeps its dedicated branch above (its OCR check is injectable for tests).
                runCatching { NodeBridgePluginFamilyStatus.availableMethods(appContext) }.getOrDefault(emptySet()).forEach { key ->
                    val module = key.substringBefore('.')
                    val method = key.substringAfter('.')
                    if (key != "accessibility.readScreenText" && key in grant.allowedMethods && key !in this &&
                        grant.permissions.containsAll(NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method))) add(key)
                }
            }
        }.toTypedArray())
        putStringArray(HostCapabilityContract.KEY_GRANT_METHODS, grant.allowedMethods.toTypedArray())
        putStringArray(HostCapabilityContract.KEY_GRANT_PERMISSIONS, grant.permissions.toTypedArray())
        putInt(HostCapabilityContract.KEY_GRANT_MAX_REQUEST_BYTES, grant.maxRequestBytes)
        putInt(HostCapabilityContract.KEY_GRANT_MAX_CONCURRENT_CALLS, grant.maxConcurrentCalls)
        putInt(HostCapabilityContract.KEY_GRANT_ACCESSIBILITY_QUERIES_PER_SECOND, grant.accessibilityQueriesPerSecond)
        putLong(HostCapabilityContract.KEY_GRANT_DEFAULT_TIMEOUT_MS, grant.defaultTimeoutMs)
        putLong(HostCapabilityContract.KEY_GRANT_MAX_TIMEOUT_MS, grant.maxTimeoutMs)
        putString(HostCapabilityContract.KEY_GRANT_JSON, JSONObject().apply {
            put("methods", JSONArray(grant.allowedMethods))
            put("permissions", JSONArray(grant.permissions))
            put("maxRequestBytes", grant.maxRequestBytes)
            put("maxConcurrentCalls", grant.maxConcurrentCalls)
            put("modelCallsPerMinute", grant.modelCallsPerMinute)
            put("maxTotalTokens", grant.maxTotalTokens)
            put("maxInputBytesPerRequest", grant.maxInputBytesPerRequest)
        }.toString())
    }

    internal fun prepareAgentScriptRoots(roots: List<File>): () -> Unit {
        check(!destroyed.get()) { "Capability broker is destroyed." }
        check("agent.listScripts" in grant.allowedMethods) { "Agent scripts are outside this grant." }
        val androidProvider = provider as? AndroidNodeBridgeCapabilityProvider
            ?: throw IllegalStateException("Registered scripts require the Android capability provider.")
        return androidProvider.prepareAgentScriptRoots(roots)
    }

    fun dispatch(request: Bundle?, callback: ((Bundle) -> Unit)?) {
        val unexpectedPayload = closeUnexpectedPayload(request)
        if (callback == null) {
            return
        }
        val requestJson = runCatching {
            @Suppress("DEPRECATION")
            val value = request?.get(HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON)
            require(value == null || value is String) { "Bridge request JSON must be a string." }
            (value as? String).orEmpty()
        }.getOrElse {
            sendResponse(callback, NodeBridgeResponse.failure("invalid",
                NodeBridgeError.invalidRequest("Bridge request JSON must be a string.")))
            return
        }
        if (unexpectedPayload) {
            sendResponse(callback, NodeBridgeResponse.failure(recoverId(requestJson),
                NodeBridgeError.invalidRequest("Capability requests do not accept payload descriptors.")))
            return
        }
        val requestBytes = requestJson.toByteArray(StandardCharsets.UTF_8).size
        if (destroyed.get()) {
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    recoverId(requestJson),
                    NodeBridgeError.processDead("AutoJs6 $label capability broker is already destroyed."),
                ),
            )
            return
        }
        if (requestBytes > HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES) {
            sendResponse(
                callback,
                NodeBridgeResponse.failure(
                    recoverId(requestJson),
                    NodeBridgeError.resourceLimit(
                        "AutoJs6 $label capability broker rejected an inline request of $requestBytes bytes " +
                                "(limit ${HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES}).",
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
                    NodeBridgeError.invalidRequest(error.message ?: "AutoJs6 $label capability broker request is malformed."),
                ),
            )
            return
        }
        val denial = grantDenial(bridgeRequest, requestBytes)
        if (denial != null) {
            Log.i(TAG, "denied category=${denial.category} bytes=$requestBytes")
            sendResponse(callback, NodeBridgeResponse.failure(bridgeRequest.id, denial))
            return
        }
        val boundedRequest = bridgeRequest.copy(timeoutMs = grant.effectiveTimeoutMs(bridgeRequest.timeoutMs))
        dispatchQueue.dispatch(boundedRequest) { response -> sendResponse(callback, response) }
    }

    fun destroy(reason: Bundle?) {
        closeUnexpectedPayload(reason)
        if (destroyed.compareAndSet(false, true)) {
            val message = reasonString(reason, HostCapabilityContract.KEY_ERROR_MESSAGE)
                ?: reasonString(reason, HostCapabilityContract.KEY_ERROR_CODE)
                ?: "AutoJs6 $label capability broker destroyed."
            dispatchQueue.destroy(NodeBridgeError.processDead(message))
            Log.i(TAG, "destroyed broker $id")
        }
    }

    val isDestroyed: Boolean
        get() = destroyed.get()

    private fun grantDenial(request: NodeBridgeRequest, requestBytes: Int): NodeBridgeError? {
        return when (val decision = grant.evaluate(request.module, request.method, request.permissions, requestBytes)) {
            HostCapabilityGrant.Decision.Allowed -> null
            is HostCapabilityGrant.Decision.Denied -> when (decision.reason) {
                HostCapabilityGrant.Reason.METHOD_NOT_GRANTED -> NodeBridgeError.permissionDenied(
                    message = "AutoJs6 $label session grant does not include ${request.module}.${request.method}.",
                    module = request.module,
                    method = request.method,
                    category = NodeBridgeErrorCategory.CAPABILITY_DENIED,
                )
                HostCapabilityGrant.Reason.PERMISSION_NOT_GRANTED -> NodeBridgeError.capabilityNotDeclared(
                    request.module,
                    request.method,
                    decision.detail,
                )
                HostCapabilityGrant.Reason.REQUEST_TOO_LARGE -> NodeBridgeError.resourceLimit(
                    message = "AutoJs6 $label session grant rejected ${request.module}.${request.method}: request payload is $requestBytes bytes, limit ${grant.maxRequestBytes}.",
                    module = request.module,
                    method = request.method,
                )
            }
        }
    }

    private fun sendResponse(callback: (Bundle) -> Unit, response: NodeBridgeResponse) {
        val payload = if (response.ok) extractOneShotPayload(response) ?: extractJsonPayload(response) else null
        var effective = payload?.response ?: response
        var responseJson = effective.toJson()
        if (responseJson.toByteArray(StandardCharsets.UTF_8).size > HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES) {
            effective = NodeBridgeResponse.failure(
                response.id,
                NodeBridgeError.resourceLimit(
                    "AutoJs6 $label capability broker result exceeds the inline limit of " +
                            "${HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES} bytes; use a payload descriptor method.",
                ),
            )
            responseJson = effective.toJson()
        }
        val descriptor = payload?.descriptor?.takeIf { effective.ok }
        val bundle = Bundle().apply {
            putString(HostCapabilityContract.KEY_BRIDGE_RESPONSE_JSON, responseJson)
            putBoolean(HostCapabilityContract.KEY_BRIDGE_RESPONSE_OK, effective.ok)
            effective.error?.message?.let { putString(HostCapabilityContract.KEY_BRIDGE_ERROR_MESSAGE, it) }
            if (descriptor != null) {
                putParcelable(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD, descriptor)
                putLong(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_BYTES, payload.bytes)
                putString(HostCapabilityContract.KEY_BRIDGE_PAYLOAD_MIME, payload.mime)
            }
        }
        try {
            callback(bundle)
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
        if (bytes > HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES) {
            file.delete()
            return OneShotPayload(
                descriptor = null,
                bytes = bytes,
                mime = mime,
                response = NodeBridgeResponse.failure(
                    response.id,
                    NodeBridgeError.resourceLimit(
                        "AutoJs6 $label capability broker payload of $bytes bytes exceeds ${HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES}.",
                    ),
                ),
            )
        }
        val descriptor = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrElse { error ->
            Log.w(TAG, "unable to open broker payload: ${error.javaClass.simpleName}")
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

    /** Large JSON results (file content and bounded shell output) reuse the read-only payload channel. */
    private fun extractJsonPayload(response: NodeBridgeResponse): OneShotPayload? {
        val bytes = response.resultJson?.toByteArray(StandardCharsets.UTF_8) ?: return null
        if (bytes.size <= 64 * 1024) return null
        if (bytes.size > HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES) return OneShotPayload(null, bytes.size.toLong(), "application/json",
            NodeBridgeResponse.failure(response.id, NodeBridgeError.resourceLimit("AutoJs6 $label JSON result exceeds the payload limit.")))
        var file: File? = null
        return try {
            file = File.createTempFile("host-json-", ".payload", appContext.cacheDir)
            file.writeBytes(bytes)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val result = JSONObject().put(KEY_PAYLOAD, JSONObject().put(KEY_PAYLOAD_KIND, PAYLOAD_KIND_DESCRIPTOR)
                .put(KEY_PAYLOAD_MIME, "application/json").put("bytes", bytes.size).put(KEY_PAYLOAD_ONE_SHOT, true))
            OneShotPayload(descriptor, bytes.size.toLong(), "application/json", NodeBridgeResponse.success(response.id, result.toString()))
        } catch (_: Exception) {
            OneShotPayload(null, 0, "application/json", NodeBridgeResponse.failure(response.id,
                NodeBridgeError.resourceLimit("AutoJs6 $label could not allocate a JSON payload descriptor.")))
        } finally {
            file?.delete()
        }
    }

    private fun recoverId(requestJson: String): String = NodeBridgeRequest.recoverId(requestJson) ?: "invalid"

    private fun reasonString(reason: Bundle?, key: String): String? = runCatching {
        @Suppress("DEPRECATION")
        (reason?.get(key) as? String)?.take(HostCapabilityContract.MAX_ERROR_MESSAGE_BYTES)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    companion object {
        /** Incoming descriptors are owned by the receiver, including rejected/callback-less calls. */
        internal fun closeUnexpectedPayload(bundle: Bundle?): Boolean {
            val keys = runCatching { bundle?.keySet()?.toList().orEmpty() }.getOrDefault(emptyList())
            var found = false
            for (key in keys) {
                // Also close a descriptor maliciously placed in a String field or unknown key.
                @Suppress("DEPRECATION")
                val descriptor = runCatching { bundle?.get(key) }.getOrNull() as? ParcelFileDescriptor ?: continue
                found = true
                runCatching { descriptor.close() }
            }
            return found
        }

        const val DEFAULT_SOURCE_NAME = "host-capability"
        private const val TAG = "HostCapabilityBroker"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_PAYLOAD_KIND = "kind"
        private const val KEY_PAYLOAD_PATH = "path"
        private const val KEY_PAYLOAD_ONE_SHOT = "oneShot"
        private const val KEY_PAYLOAD_MIME = "mime"
        private const val PAYLOAD_KIND_FILE = "file"
        private const val PAYLOAD_KIND_DESCRIPTOR = "descriptor"
    }
}
