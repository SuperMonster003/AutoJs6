package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmClipboardPayload
import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.api.JvmToastPayload
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class JvmHostAppLaunchBridge(
    private val requestId: JvmRequestId,
    allowedMethods: Collection<String>,
    private val launchPackage: (String) -> Boolean,
    private val showToast: (String) -> Unit = {},
    private val readClipboard: () -> String = { "" },
    private val writeClipboard: (String) -> Unit = {},
    private val onViolation: (Throwable) -> Unit,
) : JvmSourceHostBridgeEndpoint {
    private val allowedMethods = allowedMethods.toSet()
    private val seenCallIds = ConcurrentHashMap.newKeySet<Long>()
    private val closed = AtomicBoolean(false)
    private val allowlistedOperationCount = AtomicInteger()
    private val rejectedOperationCount = AtomicInteger()

    data class Stats(
        val allowlistedOperationCount: Int,
        val rejectedOperationCount: Int,
    )

    override fun dispatch(encodedCall: ByteArray, callback: JvmSourceHostBridgeResponse) {
        val call = try {
            require(encodedCall.size in 1..JvmSourceContract.MAX_METADATA_BYTES)
            JvmSourceCodec.decodeHostCall(encodedCall).also(JvmSourceValidation::validateHostCall)
        } catch (error: Throwable) {
            rejectedOperationCount.incrementAndGet()
            onViolation(error)
            respondFailure(callback, null, "INVALID_CALL", "Host call metadata is invalid")
            return
        }
        if (closed.get()) {
            rejectedOperationCount.incrementAndGet()
            respondFailure(callback, call.callId, "BRIDGE_CLOSED", "Host bridge is closed")
            return
        }
        if (call.requestId != requestId) {
            reject(callback, call, "REQUEST_MISMATCH", "Host call belongs to another request")
            return
        }
        if (!seenCallIds.add(call.callId)) {
            reject(callback, call, "DUPLICATE_CALL", "Host call ID was already used")
            return
        }
        if (call.method !in SUPPORTED_METHODS || call.method !in allowedMethods) {
            reject(callback, call, "METHOD_NOT_ALLOWED", "Host method is not allowed")
            return
        }
        val response = when (call.method) {
            METHOD_APP_LAUNCH -> appLaunch(call, callback) ?: return
            METHOD_CLIPBOARD_GET -> clipboardGet(call, callback) ?: return
            METHOD_CLIPBOARD_SET -> clipboardSet(call, callback) ?: return
            METHOD_TOAST_SHOW -> toast(call, callback) ?: return
            else -> error("Unsupported allowlisted host method")
        }
        send(callback, response)
    }

    override fun destroy(encodedReason: ByteArray) {
        if (encodedReason.size > JvmSourceContract.MAX_METADATA_BYTES) {
            onViolation(IllegalArgumentException("Host bridge destroy reason exceeds metadata quota"))
        }
        closed.set(true)
    }

    override fun close() {
        closed.set(true)
    }

    fun stats(): Stats = Stats(
        allowlistedOperationCount = allowlistedOperationCount.get(),
        rejectedOperationCount = rejectedOperationCount.get(),
    )

    private fun parsePackageName(call: JvmHostCall): String {
        // Protocol v1 intentionally accepts only the provider proxy's canonical ASCII form.
        // This rejects duplicate keys, escapes, alternate whitespace, and hidden extra authority.
        return requireNotNull(APP_LAUNCH_PAYLOAD.matchEntire(call.payloadJson))
            .groupValues[1]
    }

    private fun appLaunch(
        call: JvmHostCall,
        callback: JvmSourceHostBridgeResponse,
    ): JvmHostResponse? {
        val packageName = runCatching { parsePackageName(call) }.getOrElse { error ->
            reject(callback, call, "INVALID_PAYLOAD", "app.launch payload is invalid", error)
            return null
        }
        allowlistedOperationCount.incrementAndGet()
        return runCatching { launchPackage(packageName) }.fold(
            onSuccess = { launched ->
                JvmHostResponse(requestId, call.callId, succeeded = true, payloadJson = launched.toString())
            },
            onFailure = {
                JvmHostResponse(
                    requestId,
                    call.callId,
                    succeeded = false,
                    errorCode = "APP_LAUNCH_FAILED",
                    // The provider receives a stable public error, never a host exception/path.
                    errorMessage = "Unable to launch package",
                )
            },
        )
    }

    private fun toast(
        call: JvmHostCall,
        callback: JvmSourceHostBridgeResponse,
    ): JvmHostResponse? {
        val message = runCatching { parseToastMessage(call) }.getOrElse { error ->
            reject(callback, call, "INVALID_PAYLOAD", "toast.show payload is invalid", error)
            return null
        }
        allowlistedOperationCount.incrementAndGet()
        return runCatching { showToast(message) }.fold(
            onSuccess = {
                JvmHostResponse(requestId, call.callId, succeeded = true, payloadJson = "true")
            },
            onFailure = {
                JvmHostResponse(
                    requestId,
                    call.callId,
                    succeeded = false,
                    errorCode = "TOAST_FAILED",
                    errorMessage = "Unable to show toast",
                )
            },
        )
    }

    private fun clipboardGet(
        call: JvmHostCall,
        callback: JvmSourceHostBridgeResponse,
    ): JvmHostResponse? {
        runCatching { JvmClipboardPayload.validateGetRequest(call.payloadJson) }.getOrElse { error ->
            reject(callback, call, "INVALID_PAYLOAD", "clipboard.get payload is invalid", error)
            return null
        }
        allowlistedOperationCount.incrementAndGet()
        return runCatching { JvmClipboardPayload.encodeText(readClipboard()) }.fold(
            onSuccess = { payload ->
                JvmHostResponse(requestId, call.callId, succeeded = true, payloadJson = payload)
            },
            onFailure = {
                JvmHostResponse(
                    requestId,
                    call.callId,
                    succeeded = false,
                    errorCode = "CLIPBOARD_GET_FAILED",
                    errorMessage = "Unable to read clipboard text",
                )
            },
        )
    }

    private fun clipboardSet(
        call: JvmHostCall,
        callback: JvmSourceHostBridgeResponse,
    ): JvmHostResponse? {
        val text = runCatching { JvmClipboardPayload.decodeText(call.payloadJson) }.getOrElse { error ->
            reject(callback, call, "INVALID_PAYLOAD", "clipboard.set payload is invalid", error)
            return null
        }
        allowlistedOperationCount.incrementAndGet()
        return runCatching { writeClipboard(text) }.fold(
            onSuccess = {
                JvmHostResponse(requestId, call.callId, succeeded = true, payloadJson = "true")
            },
            onFailure = {
                JvmHostResponse(
                    requestId,
                    call.callId,
                    succeeded = false,
                    errorCode = "CLIPBOARD_SET_FAILED",
                    errorMessage = "Unable to write clipboard text",
                )
            },
        )
    }

    private fun parseToastMessage(call: JvmHostCall): String {
        return JvmToastPayload.decode(call.payloadJson)
    }

    private fun reject(
        callback: JvmSourceHostBridgeResponse,
        call: JvmHostCall,
        code: String,
        message: String,
        cause: Throwable? = null,
    ) {
        rejectedOperationCount.incrementAndGet()
        onViolation(cause ?: SecurityException(message))
        respondFailure(callback, call.callId, code, message)
    }

    private fun respondFailure(
        callback: JvmSourceHostBridgeResponse,
        callId: Long?,
        code: String,
        message: String,
    ) {
        if (callId == null || callId <= 0L) return
        send(
            callback,
            JvmHostResponse(
                requestId = requestId,
                callId = callId,
                succeeded = false,
                errorCode = code,
                errorMessage = message,
            ),
        )
    }

    private fun send(callback: JvmSourceHostBridgeResponse, response: JvmHostResponse) {
        try {
            JvmSourceValidation.validateHostResponse(response)
            callback.onResponse(JvmSourceCodec.encodeHostResponse(response))
        } catch (error: Throwable) {
            onViolation(error)
        }
    }

    companion object {
        const val METHOD_APP_LAUNCH = "app.launch"
        const val METHOD_CLIPBOARD_GET = "clipboard.get"
        const val METHOD_CLIPBOARD_SET = "clipboard.set"
        const val METHOD_TOAST_SHOW = "toast.show"
        const val PACKAGE_NAME_FIELD = "packageName"
        private val SUPPORTED_METHODS = setOf(
            METHOD_APP_LAUNCH,
            METHOD_CLIPBOARD_GET,
            METHOD_CLIPBOARD_SET,
            METHOD_TOAST_SHOW,
        )
        private val APP_LAUNCH_PAYLOAD = Regex(
            "\\{\\\"packageName\\\":\\\"([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)\\\"\\}",
        )
    }
}
