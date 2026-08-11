package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PythonRuntimeCodecRoundTripTest {
    @Test
    fun allProtocolMessagesRoundTrip() {
        val info = PythonRuntimeFixtures.runtimeInfo
        assertEquals(info, PythonRuntimeCodec.decodeRuntimeInfo(PythonRuntimeCodec.encodeRuntimeInfo(info)))

        val capabilities = PythonRuntimeFixtures.capabilities
        assertEquals(
            capabilities,
            PythonRuntimeCodec.decodeCapabilities(PythonRuntimeCodec.encodeCapabilities(capabilities)),
        )

        val request = PythonRuntimeFixtures.request
        assertEquals(
            request,
            PythonRuntimeCodec.decodeExecutionRequest(PythonRuntimeCodec.encodeExecutionRequest(request)),
        )

        val started = PythonRuntimeFixtures.started
        assertEquals(
            started,
            PythonRuntimeCodec.decodeSessionStarted(PythonRuntimeCodec.encodeSessionStarted(started)),
        )

        val chunk = PythonOutputChunk(
            requestId = PythonRuntimeFixtures.requestId,
            sequence = 7,
            stream = PythonOutputStream.STDOUT,
            data = "hello".toByteArray(),
        )
        assertEquals(chunk, PythonRuntimeCodec.decodeOutputChunk(PythonRuntimeCodec.encodeOutputChunk(chunk)))

        val result = PythonExecutionResult(
            requestId = PythonRuntimeFixtures.requestId,
            exitCode = 0,
            elapsedMillis = 25,
            stdoutBytes = 5,
            stderrBytes = 0,
            stdoutChunks = 1,
            stderrChunks = 0,
        )
        assertEquals(
            result,
            PythonRuntimeCodec.decodeExecutionResult(PythonRuntimeCodec.encodeExecutionResult(result)),
        )

        val error = PythonExecutionError(
            requestId = PythonRuntimeFixtures.requestId,
            code = PythonErrorCode.PYTHON_EXCEPTION,
            phase = PythonFailurePhase.EXECUTION,
            message = "Python raised ValueError",
            retryableBeforeDispatch = false,
            exceptionType = "ValueError",
            exceptionMessage = "bad value",
            traceback = listOf(
                PythonTracebackFrame(
                    origin = PythonTracebackOrigin.PROJECT,
                    fileName = "src/main.py",
                    lineNumber = 4,
                    functionName = "run",
                    sourceLine = "raise ValueError()",
                ),
            ),
        )
        assertEquals(
            error,
            PythonRuntimeCodec.decodeExecutionError(PythonRuntimeCodec.encodeExecutionError(error)),
        )

        val cancellation = PythonExecutionCancellation(
            requestId = PythonRuntimeFixtures.requestId,
            reason = PythonCancellationReason.REQUESTED,
            phase = PythonFailurePhase.CANCELLATION,
            elapsedMillis = 50,
        )
        assertEquals(
            cancellation,
            PythonRuntimeCodec.decodeCancellation(PythonRuntimeCodec.encodeCancellation(cancellation)),
        )
    }

    @Test
    fun protocol11CapabilitySnapshotFieldsRoundTripWhileLegacyDefaultsStayDisabled() {
        val legacyCapabilities = PythonRuntimeCodec.decodeCapabilities(
            PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.capabilities),
        )
        assertFalse(legacyCapabilities.supportsHostCapabilitySnapshot)
        assertEquals(0L, legacyCapabilities.limits.maxHostCapabilitySnapshotBytes)

        assertEquals(
            PythonRuntimeFixtures.r5Capabilities,
            PythonRuntimeCodec.decodeCapabilities(
                PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r5Capabilities),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.r5Request,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r5Request),
            ),
        )
    }

    @Test
    fun mutableChunkAndDigestInputsAreDefensivelyCopied() {
        val input = byteArrayOf(1, 2, 3)
        val chunk = PythonOutputChunk(
            PythonRuntimeFixtures.requestId,
            0,
            PythonOutputStream.STDOUT,
            input,
        )
        input[0] = 99
        assertEquals(1, chunk.toByteArray()[0].toInt())

        val digestBytes = ByteArray(PythonSha256.BYTE_COUNT) { it.toByte() }
        val digest = PythonSha256.fromBytes(digestBytes)
        digestBytes[0] = 99
        assertEquals(0, digest.toByteArray()[0].toInt())
    }
}
