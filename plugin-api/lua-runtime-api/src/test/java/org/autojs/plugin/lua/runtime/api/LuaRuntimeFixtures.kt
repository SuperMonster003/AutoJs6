package org.autojs.plugin.lua.runtime.api

import java.util.UUID

internal object LuaRuntimeFixtures {
    val REQUEST_ID: LuaRequestId = LuaRequestId.fromUuid(
        UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
    )
    val OTHER_REQUEST_ID: LuaRequestId = LuaRequestId.fromUuid(
        UUID.fromString("10213243-5465-7687-98a9-bacbdcedfe0f"),
    )
    val SOURCE = "return { ok = true }".toByteArray()

    fun runtimeInfo(
        protocolMin: LuaProtocolVersion = LuaProtocolVersion(1, 0),
        protocolMax: LuaProtocolVersion = LuaProtocolVersion(1, 0),
        providerVersionName: String = "1.0.0",
        languageVersion: String = "5.4.8",
        capabilities: Collection<String> = listOf("device.info"),
        processAbi: String = "arm64-v8a",
        supportedAbis: Collection<String> = listOf("x86_64", "arm64-v8a"),
    ) = LuaRuntimeInfo(
        protocolMin = protocolMin,
        protocolMax = protocolMax,
        providerId = "org.autojs.lua.puc",
        providerVersionName = providerVersionName,
        providerVersionCode = 1L,
        runtimeFamily = LuaRuntimeFamily.PUC_LUA,
        runtimeSlot = LuaRuntimeContract.RUNTIME_SLOT_LUA54,
        languageVersion = languageVersion,
        processAbi = processAbi,
        supportedAbis = supportedAbis,
        capabilities = capabilities,
        limits = LuaRuntimeLimits(
            maxSourceBytes = 1024L * 1024L,
            maxMemoryBytes = 64L * 1024L * 1024L,
            maxOutputBytes = 2L * 1024L * 1024L,
            maxExecutionMillis = 120_000L,
            maxConcurrentExecutions = 1,
        ),
        minHostVersionCode = 5_000L,
    )

    fun request(
        requestId: LuaRequestId = REQUEST_ID,
        protocolVersion: LuaProtocolVersion = LuaProtocolVersion(1, 0),
        outputByteLimit: Long = 1024L,
        requiredCapabilities: Collection<String> = listOf("device.info"),
    ) = LuaExecutionRequest(
        requestId = requestId,
        protocolVersion = protocolVersion,
        sourceName = "main.lua",
        sourceLengthBytes = SOURCE.size.toLong(),
        sourceSha256 = LuaSha256.digest(SOURCE),
        arguments = LuaValue.MapValue(
            linkedMapOf(
                "count" to LuaValue.Int64Value(3L),
                "enabled" to LuaValue.BooleanValue(true),
            ),
        ),
        requiredCapabilities = requiredCapabilities,
        outputByteLimit = outputByteLimit,
        memoryByteLimit = 32L * 1024L * 1024L,
        timeoutMillis = 30_000L,
    )

    fun started(requestId: LuaRequestId = REQUEST_ID) = LuaExecutionStarted(
        requestId = requestId,
        protocolVersion = LuaProtocolVersion(1, 0),
        runtimeSlot = LuaRuntimeContract.RUNTIME_SLOT_LUA54,
        languageVersion = "5.4.8",
        firstOutputSequence = 7L,
        queueElapsedMillis = 2L,
    )
}
