package org.autojs.plugin.lua.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Test

class LuaRuntimeCodecRoundTripTest {
    @Test
    fun runtimeInfoRoundTrips() {
        val value = LuaRuntimeFixtures.runtimeInfo()
        val decoded = LuaRuntimeCodec.decodeRuntimeInfo(LuaRuntimeCodec.encodeRuntimeInfo(value))

        assertEquals(value.protocolMin, decoded.protocolMin)
        assertEquals(value.protocolMax, decoded.protocolMax)
        assertEquals(value.providerId, decoded.providerId)
        assertEquals(value.providerVersionName, decoded.providerVersionName)
        assertEquals(value.providerVersionCode, decoded.providerVersionCode)
        assertEquals(value.runtimeFamily, decoded.runtimeFamily)
        assertEquals(value.runtimeSlot, decoded.runtimeSlot)
        assertEquals(value.languageVersion, decoded.languageVersion)
        assertEquals(value.processAbi, decoded.processAbi)
        assertEquals(value.supportedAbis.sorted(), decoded.supportedAbis.sorted())
        assertEquals(value.capabilities.sorted(), decoded.capabilities.sorted())
        assertEquals(value.limits, decoded.limits)
        assertEquals(value.minHostVersionCode, decoded.minHostVersionCode)
        assertEquals(value.maxHostVersionCode, decoded.maxHostVersionCode)
    }

    @Test
    fun executionRequestRoundTrips() {
        val value = LuaRuntimeFixtures.request()
        val decoded = LuaRuntimeCodec.decodeExecutionRequest(LuaRuntimeCodec.encodeExecutionRequest(value))

        assertEquals(value.requestId, decoded.requestId)
        assertEquals(value.protocolVersion, decoded.protocolVersion)
        assertEquals(value.sourceName, decoded.sourceName)
        assertEquals(value.sourceLengthBytes, decoded.sourceLengthBytes)
        assertEquals(value.sourceSha256, decoded.sourceSha256)
        assertEquals(value.arguments, decoded.arguments)
        assertEquals(value.requiredCapabilities.sorted(), decoded.requiredCapabilities.sorted())
        assertEquals(value.outputByteLimit, decoded.outputByteLimit)
        assertEquals(value.memoryByteLimit, decoded.memoryByteLimit)
        assertEquals(value.timeoutMillis, decoded.timeoutMillis)
    }

    @Test
    fun executionEventsRoundTrip() {
        val started = LuaRuntimeFixtures.started()
        val output = LuaOutputChunk(LuaRuntimeFixtures.REQUEST_ID, 7L, LuaOutputStream.STDERR, "warning\n")
        val result = LuaExecutionResult(
            LuaRuntimeFixtures.REQUEST_ID,
            LuaValue.MapValue(mapOf("ok" to LuaValue.BooleanValue(true))),
            42L,
        )
        val error = LuaExecutionError(
            LuaRuntimeFixtures.REQUEST_ID,
            LuaExecutionErrorCode.RUNTIME_ERROR,
            LuaExecutionFailurePhase.EXECUTION,
            "attempt to index a nil value",
            LuaRetryDisposition.DO_NOT_RETRY,
        )
        val cancellation = LuaExecutionCancellation(
            LuaRuntimeFixtures.REQUEST_ID,
            LuaCancellationReason.REQUESTED,
            12L,
        )

        assertEquals(started, LuaRuntimeCodec.decodeStarted(LuaRuntimeCodec.encodeStarted(started)))
        assertEquals(output, LuaRuntimeCodec.decodeOutput(LuaRuntimeCodec.encodeOutput(output)))

        val decodedResult = LuaRuntimeCodec.decodeResult(LuaRuntimeCodec.encodeResult(result))
        assertEquals(result.requestId, decodedResult.requestId)
        assertEquals(result.value, decodedResult.value)
        assertEquals(result.elapsedMillis, decodedResult.elapsedMillis)

        val decodedError = LuaRuntimeCodec.decodeError(LuaRuntimeCodec.encodeError(error))
        assertEquals(error.requestId, decodedError.requestId)
        assertEquals(error.code, decodedError.code)
        assertEquals(error.phase, decodedError.phase)
        assertEquals(error.message, decodedError.message)
        assertEquals(error.retryDisposition, decodedError.retryDisposition)

        assertEquals(
            cancellation,
            LuaRuntimeCodec.decodeCancellation(LuaRuntimeCodec.encodeCancellation(cancellation)),
        )
    }

    @Test
    fun hostCallMessagesRoundTrip() {
        val request = LuaHostCallRequest(
            LuaRuntimeFixtures.REQUEST_ID,
            "call-1",
            "device.info",
            LuaValue.ArrayValue(listOf(LuaValue.StringValue("model"))),
        )
        val result = LuaHostCallResult(
            LuaRuntimeFixtures.REQUEST_ID,
            "call-1",
            LuaValue.StringValue("emulator"),
        )
        val error = LuaHostCallError(
            LuaRuntimeFixtures.REQUEST_ID,
            "call-1",
            LuaHostErrorCode.CAPABILITY_DENIED,
            "not granted",
        )

        val decodedRequest = LuaRuntimeCodec.decodeHostRequest(LuaRuntimeCodec.encodeHostRequest(request))
        assertEquals(request.executionId, decodedRequest.executionId)
        assertEquals(request.callId, decodedRequest.callId)
        assertEquals(request.capability, decodedRequest.capability)
        assertEquals(request.arguments, decodedRequest.arguments)

        val decodedResult = LuaRuntimeCodec.decodeHostResult(LuaRuntimeCodec.encodeHostResult(result))
        assertEquals(result.executionId, decodedResult.executionId)
        assertEquals(result.callId, decodedResult.callId)
        assertEquals(result.value, decodedResult.value)

        val decodedError = LuaRuntimeCodec.decodeHostError(LuaRuntimeCodec.encodeHostError(error))
        assertEquals(error.executionId, decodedError.executionId)
        assertEquals(error.callId, decodedError.callId)
        assertEquals(error.code, decodedError.code)
        assertEquals(error.message, decodedError.message)
    }
}
