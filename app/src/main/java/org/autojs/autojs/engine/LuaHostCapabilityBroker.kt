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
    private val storagePrincipal: LuaStoragePrincipal? = null,
    private val hostStorage: LuaHostStorage = LuaHostStorage.Unavailable,
    private val toastSink: LuaToastSink = LuaToastSink.Unavailable,
    private val deviceInfoSource: LuaDeviceInfoSource = AndroidLuaDeviceInfoSource,
) {
    private val grantedCapabilities = grantedCapabilities.toSet()
    private val policy = LuaHostCallPolicy(executionId, this.grantedCapabilities)
    private val admittedCallIds = linkedSetOf<String>()
    private var closed = false
    private var storageOperations = 0
    private var storageMutations = 0
    private var storageReadBytes = 0L
    private var storageWriteBytes = 0L
    private var toastDispatches = 0

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
                LuaRuntimeHostCapabilities.STORAGE_KV -> dispatchStorage(request)
                LuaRuntimeHostCapabilities.UI_TOAST -> dispatchToast(request)
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

    private fun dispatchStorage(request: LuaHostCallRequest): LuaHostDispatchOutcome {
        val principal = storagePrincipal
            ?: return failOpened(
                request,
                LuaHostErrorCode.CAPABILITY_UNAVAILABLE,
                "Persistent storage requires a stable file-backed script identity",
            )
        if (!chargeStorageOperation()) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage operation quota is exhausted")
        }
        val fields = (request.arguments as? LuaValue.MapValue)?.values
            ?: return invalidStorageRequest(request)
        val operation = fields[STORAGE_OPERATION_KEY] as? LuaValue.StringValue
            ?: return invalidStorageRequest(request)
        return when (operation.value) {
            STORAGE_GET -> dispatchStorageGet(request, principal, fields)
            STORAGE_PUT -> dispatchStoragePut(request, principal, fields)
            STORAGE_REMOVE -> dispatchStorageRemove(request, principal, fields)
            STORAGE_CLEAR -> dispatchStorageClear(request, principal, fields)
            else -> invalidStorageRequest(request)
        }
    }

    private fun dispatchStorageGet(
        request: LuaHostCallRequest,
        principal: LuaStoragePrincipal,
        fields: Map<String, LuaValue>,
    ): LuaHostDispatchOutcome {
        val key = storageKey(fields, setOf(STORAGE_OPERATION_KEY, STORAGE_KEY_KEY))
            ?: return invalidStorageRequest(request)
        val encoded = hostStorage.get(principal, key)
        if (encoded == null) {
            return complete(
                request,
                LuaValue.MapValue(mapOf(STORAGE_FOUND_KEY to LuaValue.BooleanValue(false))),
            )
        }
        if (!chargeStorageRead(encoded.size)) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage read-byte quota is exhausted")
        }
        val value = LuaStorageContract.decodeStoredValue(encoded)
        return complete(
            request,
            LuaValue.MapValue(
                linkedMapOf(
                    STORAGE_FOUND_KEY to LuaValue.BooleanValue(true),
                    STORAGE_VALUE_KEY to value,
                ),
            ),
        )
    }

    private fun dispatchStoragePut(
        request: LuaHostCallRequest,
        principal: LuaStoragePrincipal,
        fields: Map<String, LuaValue>,
    ): LuaHostDispatchOutcome {
        val expected = setOf(STORAGE_OPERATION_KEY, STORAGE_KEY_KEY, STORAGE_VALUE_KEY)
        val key = storageKey(fields, expected) ?: return invalidStorageRequest(request)
        val value = fields[STORAGE_VALUE_KEY] ?: return invalidStorageRequest(request)
        val encoded = try {
            LuaStorageContract.encodeStoredValue(value)
        } catch (_: IllegalArgumentException) {
            return invalidStorageRequest(request)
        }
        if (!chargeStorageMutation()) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage mutation quota is exhausted")
        }
        if (!chargeStorageWrite(encoded.size)) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage write-byte quota is exhausted")
        }
        return try {
            hostStorage.put(principal, key, encoded)
            complete(
                request,
                LuaValue.MapValue(mapOf(STORAGE_STORED_KEY to LuaValue.BooleanValue(true))),
            )
        } catch (_: LuaStorageQuotaExceededException) {
            failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage principal quota is exhausted")
        }
    }

    private fun dispatchStorageRemove(
        request: LuaHostCallRequest,
        principal: LuaStoragePrincipal,
        fields: Map<String, LuaValue>,
    ): LuaHostDispatchOutcome {
        val key = storageKey(fields, setOf(STORAGE_OPERATION_KEY, STORAGE_KEY_KEY))
            ?: return invalidStorageRequest(request)
        if (!chargeStorageMutation()) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage mutation quota is exhausted")
        }
        val removed = hostStorage.remove(principal, key)
        return complete(
            request,
            LuaValue.MapValue(mapOf(STORAGE_REMOVED_KEY to LuaValue.BooleanValue(removed))),
        )
    }

    private fun dispatchStorageClear(
        request: LuaHostCallRequest,
        principal: LuaStoragePrincipal,
        fields: Map<String, LuaValue>,
    ): LuaHostDispatchOutcome {
        if (fields.keys != setOf(STORAGE_OPERATION_KEY)) return invalidStorageRequest(request)
        if (!chargeStorageMutation()) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "Storage mutation quota is exhausted")
        }
        val removedCount = hostStorage.clear(principal)
        check(removedCount in 0..LuaStorageContract.MAX_KEYS_PER_PRINCIPAL) {
            "Lua Host storage returned an invalid clear count"
        }
        return complete(
            request,
            LuaValue.MapValue(
                mapOf(STORAGE_REMOVED_COUNT_KEY to LuaValue.Int64Value(removedCount.toLong())),
            ),
        )
    }

    private fun dispatchToast(request: LuaHostCallRequest): LuaHostDispatchOutcome {
        val fields = (request.arguments as? LuaValue.MapValue)?.values
            ?: return invalidToastRequest(request)
        if (fields.keys != setOf(TOAST_TEXT_KEY)) return invalidToastRequest(request)
        val text = (fields[TOAST_TEXT_KEY] as? LuaValue.StringValue)?.value
            ?: return invalidToastRequest(request)
        val textBytes = try {
            LuaStorageContract.strictUtf8Size(text, "Lua toast text")
        } catch (_: IllegalArgumentException) {
            return invalidToastRequest(request)
        }
        if (textBytes !in 1..MAX_TOAST_TEXT_BYTES) return invalidToastRequest(request)
        if (!chargeToastDispatch()) {
            return failOpened(request, LuaHostErrorCode.QUOTA_EXCEEDED, "UI toast quota is exhausted")
        }
        if (!toastSink.enqueue(text)) {
            return failOpened(request, LuaHostErrorCode.CAPABILITY_UNAVAILABLE, "Host UI lane is unavailable")
        }
        return complete(
            request,
            LuaValue.MapValue(mapOf(TOAST_ACCEPTED_KEY to LuaValue.BooleanValue(true))),
        )
    }

    private fun storageKey(fields: Map<String, LuaValue>, expectedKeys: Set<String>): String? {
        if (fields.keys != expectedKeys) return null
        val key = (fields[STORAGE_KEY_KEY] as? LuaValue.StringValue)?.value ?: return null
        return try {
            LuaStorageContract.requireValidKey(key)
            key
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun invalidStorageRequest(request: LuaHostCallRequest) =
        failOpened(request, LuaHostErrorCode.INVALID_REQUEST, "Storage request shape is invalid")

    private fun invalidToastRequest(request: LuaHostCallRequest) =
        failOpened(request, LuaHostErrorCode.INVALID_REQUEST, "UI toast request shape is invalid")

    @Synchronized
    private fun chargeStorageOperation(): Boolean {
        if (storageOperations >= LuaStorageContract.MAX_OPERATIONS_PER_EXECUTION) return false
        storageOperations += 1
        return true
    }

    @Synchronized
    private fun chargeStorageMutation(): Boolean {
        if (storageMutations >= LuaStorageContract.MAX_MUTATIONS_PER_EXECUTION) return false
        storageMutations += 1
        return true
    }

    @Synchronized
    private fun chargeStorageRead(bytes: Int): Boolean {
        if (storageReadBytes > LuaStorageContract.MAX_READ_BYTES_PER_EXECUTION.toLong() - bytes) return false
        storageReadBytes += bytes
        return true
    }

    @Synchronized
    private fun chargeStorageWrite(bytes: Int): Boolean {
        if (storageWriteBytes > LuaStorageContract.MAX_WRITE_BYTES_PER_EXECUTION.toLong() - bytes) return false
        storageWriteBytes += bytes
        return true
    }

    @Synchronized
    private fun chargeToastDispatch(): Boolean {
        if (toastDispatches >= MAX_TOAST_CALLS_PER_EXECUTION) return false
        toastDispatches += 1
        return true
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
        const val STORAGE_OPERATION_KEY = "op"
        const val STORAGE_KEY_KEY = "key"
        const val STORAGE_VALUE_KEY = "value"
        const val STORAGE_FOUND_KEY = "found"
        const val STORAGE_STORED_KEY = "stored"
        const val STORAGE_REMOVED_KEY = "removed"
        const val STORAGE_REMOVED_COUNT_KEY = "removedCount"
        const val STORAGE_GET = "get"
        const val STORAGE_PUT = "put"
        const val STORAGE_REMOVE = "remove"
        const val STORAGE_CLEAR = "clear"
        const val TOAST_TEXT_KEY = "text"
        const val TOAST_ACCEPTED_KEY = "accepted"
        const val MAX_TOAST_TEXT_BYTES = 1024
        const val MAX_TOAST_CALLS_PER_EXECUTION = 4
    }
}
