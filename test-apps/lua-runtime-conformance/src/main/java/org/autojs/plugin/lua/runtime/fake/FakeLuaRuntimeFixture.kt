package org.autojs.plugin.lua.runtime.fake

import android.os.Build
import android.os.Bundle
import android.os.Process
import org.autojs.plugin.common.api.PluginInfo
import org.autojs.plugin.lua.runtime.api.LuaExecutionError
import org.autojs.plugin.lua.runtime.api.LuaExecutionErrorCode
import org.autojs.plugin.lua.runtime.api.LuaExecutionFailurePhase
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.autojs.plugin.lua.runtime.api.LuaExecutionResult
import org.autojs.plugin.lua.runtime.api.LuaExecutionStarted
import org.autojs.plugin.lua.runtime.api.LuaOutputChunk
import org.autojs.plugin.lua.runtime.api.LuaOutputStream
import org.autojs.plugin.lua.runtime.api.LuaPluginIds
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import org.autojs.plugin.lua.runtime.api.LuaRetryDisposition
import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.autojs.plugin.lua.runtime.api.LuaRuntimeFamily
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import org.autojs.plugin.lua.runtime.api.LuaRuntimeLimits
import org.autojs.plugin.lua.runtime.api.LuaValue

internal object FakeLuaRuntimeFixture {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val VERSION_CODE = 1L
    const val VERSION_NAME = "1.0.0"
    const val LANGUAGE_VERSION = "5.4.8"

    val protocolVersion = LuaProtocolVersion(
        LuaRuntimeContract.PROTOCOL_MAJOR,
        LuaRuntimeContract.PROTOCOL_MINOR,
    )

    fun pluginInfo(): PluginInfo = PluginInfo(
        name = "Lua Runtime Conformance",
        description = "Test-only hostile Binder fixture",
        instruction = null,
        author = "AutoJs6",
        collaborators = null,
        versionName = VERSION_NAME,
        versionCode = VERSION_CODE,
        versionDate = null,
        id = LuaPluginIds.ID,
        engine = LuaPluginIds.ENGINE,
        variant = LuaPluginIds.VARIANT_PUC_LUA_5_4,
        supportedAbis = supportedAbis().toTypedArray(),
        capabilities = Bundle(),
    )

    fun runtimeInfo(): LuaRuntimeInfo = LuaRuntimeInfo(
        protocolMin = protocolVersion,
        protocolMax = protocolVersion,
        providerId = "lua-runtime-conformance",
        providerVersionName = VERSION_NAME,
        providerVersionCode = VERSION_CODE,
        runtimeFamily = LuaRuntimeFamily.PUC_LUA,
        runtimeSlot = LuaRuntimeContract.RUNTIME_SLOT_LUA54,
        languageVersion = LANGUAGE_VERSION,
        processAbi = processAbi(),
        supportedAbis = supportedAbis(),
        capabilities = emptyList(),
        limits = LuaRuntimeLimits(
            maxSourceBytes = LuaRuntimeContract.MAX_SOURCE_BYTES,
            maxMemoryBytes = LuaRuntimeContract.MAX_MEMORY_BYTES,
            maxOutputBytes = LuaRuntimeContract.MAX_OUTPUT_BYTES,
            maxExecutionMillis = LuaRuntimeContract.MAX_TIMEOUT_MILLIS,
            maxConcurrentExecutions = LuaRuntimeContract.MAX_CONCURRENT_EXECUTIONS,
        ),
    )

    fun started(request: LuaExecutionRequest): LuaExecutionStarted = LuaExecutionStarted(
        requestId = request.requestId,
        protocolVersion = request.protocolVersion,
        runtimeSlot = LuaRuntimeContract.RUNTIME_SLOT_LUA54,
        languageVersion = LANGUAGE_VERSION,
        firstOutputSequence = 0L,
        queueElapsedMillis = 0L,
    )

    fun result(request: LuaExecutionRequest): LuaExecutionResult = LuaExecutionResult(
        requestId = request.requestId,
        value = LuaValue.StringValue("lua-conformance-ok"),
        elapsedMillis = 0L,
    )

    fun output(
        request: LuaExecutionRequest,
        sequence: Long,
        text: String,
        requestId: LuaRequestId = request.requestId,
    ): LuaOutputChunk = LuaOutputChunk(
        requestId = requestId,
        sequence = sequence,
        stream = LuaOutputStream.STDOUT,
        text = text,
    )

    fun busyError(request: LuaExecutionRequest): LuaExecutionError = LuaExecutionError(
        requestId = request.requestId,
        code = LuaExecutionErrorCode.BUSY,
        phase = LuaExecutionFailurePhase.QUEUE,
        message = "Lua conformance provider already has an active execution",
        retryDisposition = LuaRetryDisposition.NEW_REQUEST_MAY_SUCCEED,
    )

    fun differentRequestId(request: LuaExecutionRequest): LuaRequestId = LuaRequestId.fromBytes(
        request.requestId.toByteArray().also { bytes ->
            bytes[0] = (bytes[0].toInt() xor 0x01).toByte()
        },
    )

    private fun supportedAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    private fun processAbi(): String {
        val processAbis = if (Process.is64Bit()) {
            Build.SUPPORTED_64_BIT_ABIS
        } else {
            Build.SUPPORTED_32_BIT_ABIS
        }
        return processAbis.firstOrNull()
            ?: Build.SUPPORTED_ABIS.firstOrNull()
            ?: error("The Android runtime did not report a process ABI")
    }
}
