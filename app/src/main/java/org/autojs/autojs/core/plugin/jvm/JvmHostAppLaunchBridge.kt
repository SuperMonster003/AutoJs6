package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmHostCall
import org.autojs.plugin.jvmsource.api.JvmHostResponse
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class JvmHostAppLaunchBridge(
    private val requestId: JvmRequestId,
    allowedMethods: Collection<String>,
    private val launchPackage: (String) -> Boolean,
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
        if (call.method != METHOD_APP_LAUNCH || call.method !in allowedMethods) {
            reject(callback, call, "METHOD_NOT_ALLOWED", "Host method is not allowed")
            return
        }
        val packageName = runCatching { parsePackageName(call) }.getOrElse { error ->
            reject(callback, call, "INVALID_PAYLOAD", "app.launch payload is invalid", error)
            return
        }
        allowlistedOperationCount.incrementAndGet()
        val response = runCatching { launchPackage(packageName) }.fold(
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
        const val PACKAGE_NAME_FIELD = "packageName"
        private val APP_LAUNCH_PAYLOAD = Regex(
            "\\{\\\"packageName\\\":\\\"([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+)\\\"\\}",
        )
    }
}
