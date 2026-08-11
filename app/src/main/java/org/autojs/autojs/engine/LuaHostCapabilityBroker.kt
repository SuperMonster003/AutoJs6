package org.autojs.autojs.engine

import android.os.Build
import org.autojs.autojs.core.plugin.lua.LuaRuntimeHostCapabilities
import org.autojs.plugin.lua.runtime.api.LuaHostCallError
import org.autojs.plugin.lua.runtime.api.LuaHostCallPolicy
import org.autojs.plugin.lua.runtime.api.LuaHostCallRequest
import org.autojs.plugin.lua.runtime.api.LuaHostCallResult
import org.autojs.plugin.lua.runtime.api.LuaHostErrorCode
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaRuntimeValidation
import org.autojs.plugin.lua.runtime.api.LuaValue

internal fun interface LuaDeviceInfoSource {
    fun snapshot(): LuaValue.MapValue
}

internal object AndroidLuaDeviceInfoSource : LuaDeviceInfoSource {
    override fun snapshot() = LuaValue.MapValue(
        linkedMapOf(
            "brand" to LuaValue.StringValue(Build.BRAND.orEmpty()),
            "manufacturer" to LuaValue.StringValue(Build.MANUFACTURER.orEmpty()),
            "model" to LuaValue.StringValue(Build.MODEL.orEmpty()),
            "device" to LuaValue.StringValue(Build.DEVICE.orEmpty()),
            "product" to LuaValue.StringValue(Build.PRODUCT.orEmpty()),
            "sdkInt" to LuaValue.Int64Value(Build.VERSION.SDK_INT.toLong()),
        ),
    )
}

internal sealed interface LuaHostDispatchOutcome {
    data class Completed(val result: LuaHostCallResult) : LuaHostDispatchOutcome
    data class Failed(val error: LuaHostCallError) : LuaHostDispatchOutcome
}

/**
 * Session-scoped authorization and dispatch. Wire decoding and Binder caller verification stay in
 * the transport adapter; this class owns replay, quota, request-shape, and capability decisions.
 */
internal class LuaHostCapabilityDispatcher(
    private val executionId: LuaRequestId,
    grantedCapabilities: Collection<String>,
    private val deviceInfoSource: LuaDeviceInfoSource = AndroidLuaDeviceInfoSource,
) {
    private val grantedCapabilities = grantedCapabilities.toSet()
    private val policy = LuaHostCallPolicy(executionId, this.grantedCapabilities)
    private val admittedCallIds = linkedSetOf<String>()
    private var closed = false

    init {
        require(this.grantedCapabilities.all { it in LuaRuntimeHostCapabilities.ENABLED }) {
            "Lua host capability grant contains an unsupported capability"
        }
    }

    fun dispatch(request: LuaHostCallRequest, descriptorCount: Int): LuaHostDispatchOutcome {
        LuaRuntimeValidation.validateV1DescriptorCount(descriptorCount)
        LuaRuntimeValidation.validateHostRequest(request)
        require(request.executionId == executionId) { "Lua host request execution ID does not match" }
        admit(request.callId)

        if (request.capability !in grantedCapabilities) {
            return failed(request, LuaHostErrorCode.CAPABILITY_DENIED, "Host capability was not granted")
        }

        policy.open(request, descriptorCount)
        if (request.arguments != EMPTY_ARGUMENTS) {
            return failOpened(request, LuaHostErrorCode.INVALID_REQUEST, "Host capability expects no arguments")
        }

        return try {
            val value = when (request.capability) {
                LuaRuntimeHostCapabilities.DEVICE_INFO -> deviceInfoSource.snapshot()
                else -> return failOpened(
                    request,
                    LuaHostErrorCode.CAPABILITY_UNAVAILABLE,
                    "Host capability is unavailable",
                )
            }
            val result = LuaHostCallResult(request.executionId, request.callId, value)
            policy.complete(result)
            LuaHostDispatchOutcome.Completed(result)
        } catch (error: Throwable) {
            failOpened(request, LuaHostErrorCode.INTERNAL, "Host capability failed")
        }
    }

    @Synchronized
    fun close(): Boolean {
        if (closed) return false
        closed = true
        admittedCallIds.clear()
        policy.close()
        return true
    }

    @Synchronized
    private fun admit(callId: String) {
        check(!closed) { "Lua host capability dispatcher is closed" }
        check(callId !in admittedCallIds) { "Host call ID was replayed" }
        check(admittedCallIds.size < LuaRuntimeContract.MAX_HOST_CALLS_PER_EXECUTION) {
            "Host call lifetime limit is exhausted"
        }
        admittedCallIds += callId
    }

    private fun failOpened(
        request: LuaHostCallRequest,
        code: LuaHostErrorCode,
        message: String,
    ): LuaHostDispatchOutcome.Failed {
        val outcome = failed(request, code, message)
        policy.fail(outcome.error)
        return outcome
    }

    private fun failed(
        request: LuaHostCallRequest,
        code: LuaHostErrorCode,
        message: String,
    ) = LuaHostDispatchOutcome.Failed(
        LuaHostCallError(request.executionId, request.callId, code, message),
    )

    private companion object {
        val EMPTY_ARGUMENTS = LuaValue.MapValue(emptyMap())
    }
}
