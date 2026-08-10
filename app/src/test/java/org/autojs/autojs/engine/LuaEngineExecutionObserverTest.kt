package org.autojs.autojs.engine

import org.autojs.plugin.lua.runtime.api.LuaCancellationReason
import org.autojs.plugin.lua.runtime.api.LuaExecutionCancellation
import org.autojs.plugin.lua.runtime.api.LuaExecutionResult
import org.autojs.plugin.lua.runtime.api.LuaExecutionStarted
import org.autojs.plugin.lua.runtime.api.LuaOutputChunk
import org.autojs.plugin.lua.runtime.api.LuaOutputStream
import org.autojs.plugin.lua.runtime.api.LuaProtocolVersion
import org.autojs.plugin.lua.runtime.api.LuaRequestId
import org.autojs.plugin.lua.runtime.api.LuaRuntimeCodec
import org.autojs.plugin.lua.runtime.api.LuaRuntimeFamily
import org.autojs.plugin.lua.runtime.api.LuaRuntimeInfo
import org.autojs.plugin.lua.runtime.api.LuaRuntimeLimits
import org.autojs.plugin.lua.runtime.api.LuaSha256
import org.autojs.plugin.lua.runtime.api.LuaValue
import org.autojs.plugin.lua.runtime.api.LuaExecutionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LuaEngineExecutionObserverTest {

    @Test
    fun inProcessCallbacksCompleteAndReplenishCredit() {
        val chunks = mutableListOf<String>()
        val observer = LuaEngineExecutionObserver(request(), runtimeInfo()) { chunks += it.text }
        assertTrue(observer.dispatchStart(1) {})
        observer.onStarted(LuaRuntimeCodec.encodeStarted(started()))
        assertTrue(observer.onOutput(LuaRuntimeCodec.encodeOutput(output(4L, "hello"))))
        observer.onCompleted(
            LuaRuntimeCodec.encodeResult(LuaExecutionResult(REQUEST_ID, LuaValue.Int64Value(7L), 3L)),
            descriptorCount = 0,
        )

        val outcome = observer.await(0L) as LuaEngineExecutionObserver.Outcome.Completed
        assertEquals(LuaValue.Int64Value(7L), outcome.result.value)
        assertEquals(listOf("hello"), chunks)
    }

    @Test
    fun malformedOrOutOfOrderCallbackFailsLocally() {
        val observer = LuaEngineExecutionObserver(request(), runtimeInfo()) {}
        assertTrue(observer.dispatchStart(1) {})
        assertEquals(false, observer.onOutput(LuaRuntimeCodec.encodeOutput(output(4L, "early"))))

        assertTrue(observer.await(0L) is LuaEngineExecutionObserver.Outcome.TransportViolation)
    }

    @Test
    fun terminalCreationFailurePreventsStartDispatch() {
        val observer = LuaEngineExecutionObserver(request(), runtimeInfo()) {}
        var dispatched = false
        observer.transportViolation(IllegalStateException("creation failed"))

        assertEquals(false, observer.dispatchStart(1) { dispatched = true })
        assertEquals(false, dispatched)
        assertTrue(observer.await(0L) is LuaEngineExecutionObserver.Outcome.TransportViolation)
    }

    @Test
    fun cancellationOwnsTheRaceAndTimeoutReturnsNull() {
        val observer = LuaEngineExecutionObserver(request(), runtimeInfo()) {}
        assertTrue(observer.dispatchStart(1) {})
        observer.onStarted(LuaRuntimeCodec.encodeStarted(started()))
        assertNull(observer.await(0L))
        assertTrue(observer.requestCancel())
        observer.onCancelled(
            LuaRuntimeCodec.encodeCancellation(
                LuaExecutionCancellation(REQUEST_ID, LuaCancellationReason.REQUESTED, 2L),
            ),
        )

        assertTrue(observer.await(0L) is LuaEngineExecutionObserver.Outcome.Cancelled)
    }

    @Test
    fun postDispatchBinderDeathOwnsTheTerminalRaceAndLateCompletionIsIgnored() {
        val observer = LuaEngineExecutionObserver(request(), runtimeInfo()) {}
        assertTrue(observer.dispatchStart(1) {})
        observer.onStarted(LuaRuntimeCodec.encodeStarted(started()))

        val binderDeath = IllegalStateException("provider Binder died")
        observer.transportViolation(binderDeath)
        observer.onCompleted(
            LuaRuntimeCodec.encodeResult(LuaExecutionResult(REQUEST_ID, LuaValue.Int64Value(7L), 3L)),
            descriptorCount = 0,
        )

        val outcome = observer.await(0L) as LuaEngineExecutionObserver.Outcome.TransportViolation
        assertTrue(outcome.error === binderDeath)
    }

    private fun request() = LuaExecutionRequest(
        requestId = REQUEST_ID,
        protocolVersion = VERSION,
        sourceName = "main.lua",
        sourceLengthBytes = SOURCE.size.toLong(),
        sourceSha256 = LuaSha256.digest(SOURCE),
        arguments = LuaValue.MapValue(emptyMap()),
        outputByteLimit = 1024L,
        memoryByteLimit = 1024L * 1024L,
        timeoutMillis = 1000L,
    )

    private fun runtimeInfo() = LuaRuntimeInfo(
        protocolMin = VERSION,
        protocolMax = VERSION,
        providerId = "org.autojs.lua.test",
        providerVersionName = "1.0",
        providerVersionCode = 1L,
        runtimeFamily = LuaRuntimeFamily.PUC_LUA,
        runtimeSlot = "lua54",
        languageVersion = "5.4.8",
        processAbi = "x86_64",
        supportedAbis = listOf("x86_64"),
        capabilities = emptyList(),
        limits = LuaRuntimeLimits(
            maxSourceBytes = 1024L,
            maxMemoryBytes = 1024L * 1024L,
            maxOutputBytes = 1024L,
            maxExecutionMillis = 1000L,
            maxConcurrentExecutions = 1,
        ),
    )

    private fun started() = LuaExecutionStarted(REQUEST_ID, VERSION, "lua54", "5.4.8", 4L, 0L)

    private fun output(sequence: Long, text: String) =
        LuaOutputChunk(REQUEST_ID, sequence, LuaOutputStream.STDOUT, text)

    private companion object {
        val REQUEST_ID: LuaRequestId = LuaRequestId.fromUuid(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"))
        val VERSION = LuaProtocolVersion(1, 0)
        val SOURCE = "return 7".toByteArray()
    }
}
