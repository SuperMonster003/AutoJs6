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
    private val moduleSnapshot: LuaModuleSnapshot = LuaModuleSnapshot.EMPTY,
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
        return try {
            when (request.capability) {
                LuaRuntimeHostCapabilities.DEVICE_INFO -> dispatchDeviceInfo(request)
                LuaRuntimeHostCapabilities.MODULE_SNAPSHOT -> dispatchModuleSnapshot(request)
                else -> return failOpened(
                    request,
                    LuaHostErrorCode.CAPABILITY_UNAVAILABLE,
                    "Host capability is unavailable",
                )
            }
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

    private fun dispatchDeviceInfo(request: LuaHostCallRequest): LuaHostDispatchOutcome {
        if (request.arguments != EMPTY_ARGUMENTS) {
            return failOpened(request, LuaHostErrorCode.INVALID_REQUEST, "Device info expects no arguments")
        }
        return complete(request, deviceInfoSource.snapshot())
    }

    private fun dispatchModuleSnapshot(request: LuaHostCallRequest): LuaHostDispatchOutcome {
        val arguments = request.arguments as? LuaValue.MapValue
            ?: return failOpened(
                request,
                LuaHostErrorCode.INVALID_REQUEST,
                "Module snapshot expects a name map",
            )
        val name = (arguments.values.takeIf { it.size == 1 }?.get(MODULE_NAME_KEY)
            as? LuaValue.StringValue)?.value
            ?: return failOpened(
                request,
                LuaHostErrorCode.INVALID_REQUEST,
                "Module snapshot expects exactly one string name",
            )
        if (!LuaModuleSnapshot.isValidModuleName(name)) {
            return failOpened(request, LuaHostErrorCode.INVALID_REQUEST, "Lua module name is invalid")
        }
        val module = moduleSnapshot.moduleFor(name)
        val value = if (module == null) {
            LuaValue.MapValue(mapOf(MODULE_FOUND_KEY to LuaValue.BooleanValue(false)))
        } else {
            LuaValue.MapValue(
                linkedMapOf(
                    MODULE_FOUND_KEY to LuaValue.BooleanValue(true),
                    MODULE_SOURCE_KEY to LuaValue.BytesValue(module.sourceBytes()),
                    MODULE_SHA256_KEY to LuaValue.BytesValue(module.sha256.toByteArray()),
                ),
            )
        }
        return complete(request, value)
    }

    private fun complete(request: LuaHostCallRequest, value: LuaValue): LuaHostDispatchOutcome.Completed {
        val result = LuaHostCallResult(request.executionId, request.callId, value)
        policy.complete(result)
        return LuaHostDispatchOutcome.Completed(result)
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
        const val MODULE_NAME_KEY = "name"
        const val MODULE_FOUND_KEY = "found"
        const val MODULE_SOURCE_KEY = "source"
        const val MODULE_SHA256_KEY = "sha256"
    }
}
