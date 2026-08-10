package org.autojs.plugin.lua.runtime.api

import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LuaRuntimeMalformedAndLimitsTest {
    @Test
    fun truncationAtEveryHeaderBoundaryIsRejected() {
        val encoded = LuaRuntimeCodec.encodeCancellation(
            LuaExecutionCancellation(
                LuaRuntimeFixtures.REQUEST_ID,
                LuaCancellationReason.REQUESTED,
                1L,
            ),
        )

        for (size in 0 until 28) {
            assertThrows(TaggedWireException::class.java) {
                LuaRuntimeCodec.decodeCancellation(encoded.copyOf(size))
            }
        }
    }

    @Test
    fun invalidRequestIdAndDigestLengthsAreRejected() {
        assertThrows(LuaContractException::class.java) { LuaRequestId.fromBytes(ByteArray(15)) }
        assertThrows(LuaContractException::class.java) { LuaSha256.fromBytes(ByteArray(31)) }
    }

    @Test
    fun requestScalarTypeMismatchIsRejected() {
        val base = LuaRuntimeFixtures.request()
        val encoded = TaggedWireWriter(LuaRuntimeContract.SCHEMA_EXECUTION_REQUEST, 1, 0)
            .bytes(1, base.requestId.toByteArray(), requiredForReader = true)
            .int32(2, 1, requiredForReader = true)
            .int32(3, 0, requiredForReader = true)
            .string(4, base.sourceName, requiredForReader = true)
            .string(5, "wrong type", requiredForReader = true)
            .bytes(6, base.sourceSha256.toByteArray(), requiredForReader = true)
            .document(7, LuaValueCodec.encode(base.arguments), requiredForReader = true)
            .int64(9, base.outputByteLimit, requiredForReader = true)
            .int64(10, base.memoryByteLimit, requiredForReader = true)
            .int64(11, base.timeoutMillis, requiredForReader = true)
            .encode()

        val failure = assertThrows(TaggedWireException::class.java) {
            LuaRuntimeCodec.decodeExecutionRequest(encoded)
        }
        assertEquals(TaggedWireError.TYPE_MISMATCH, failure.error)
    }

    @Test
    fun sourceOutputMemoryAndTimeoutLimitsAreEnforced() {
        val base = LuaRuntimeFixtures.request()
        val invalidRequests = listOf(
            copyRequest(base, sourceLengthBytes = LuaRuntimeContract.MAX_SOURCE_BYTES + 1L),
            copyRequest(base, outputByteLimit = LuaRuntimeContract.MAX_OUTPUT_BYTES + 1L),
            copyRequest(base, memoryByteLimit = LuaRuntimeContract.MAX_MEMORY_BYTES + 1L),
            copyRequest(base, timeoutMillis = LuaRuntimeContract.MAX_TIMEOUT_MILLIS + 1L),
        )
        invalidRequests.forEach { request ->
            assertThrows(LuaContractException::class.java) { LuaRuntimeValidation.validateRequest(request) }
        }
    }

    @Test
    fun invalidOutputUtf8ShapeAndEmptyChunkFailClosed() {
        assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateOutput(
                LuaOutputChunk(LuaRuntimeFixtures.REQUEST_ID, 0L, LuaOutputStream.STDOUT, ""),
            )
        }
        assertThrows(LuaContractException::class.java) {
            LuaRuntimeValidation.validateOutput(
                LuaOutputChunk(
                    LuaRuntimeFixtures.REQUEST_ID,
                    0L,
                    LuaOutputStream.STDOUT,
                    "x".repeat(LuaRuntimeContract.MAX_OUTPUT_CHUNK_BYTES + 1),
                ),
            )
        }
    }

    @Test
    fun modelCollectionsAreImmutableSnapshots() {
        val capabilities = mutableListOf("console.log")
        val info = LuaRuntimeFixtures.runtimeInfo(capabilities = capabilities)
        capabilities += "shell.exec"
        assertEquals(listOf("console.log"), info.capabilities)
        assertThrows(UnsupportedOperationException::class.java) {
            (info.capabilities as MutableList).add("network.fetch")
        }
    }

    private fun copyRequest(
        base: LuaExecutionRequest,
        sourceLengthBytes: Long = base.sourceLengthBytes,
        outputByteLimit: Long = base.outputByteLimit,
        memoryByteLimit: Long = base.memoryByteLimit,
        timeoutMillis: Long = base.timeoutMillis,
    ) = LuaExecutionRequest(
        requestId = base.requestId,
        protocolVersion = base.protocolVersion,
        sourceName = base.sourceName,
        sourceLengthBytes = sourceLengthBytes,
        sourceSha256 = base.sourceSha256,
        arguments = base.arguments,
        requiredCapabilities = base.requiredCapabilities,
        outputByteLimit = outputByteLimit,
        memoryByteLimit = memoryByteLimit,
        timeoutMillis = timeoutMillis,
    )
}
