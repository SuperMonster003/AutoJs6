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

        val heartbeat = PythonRuntimeFixtures.heartbeat
        assertEquals(
            heartbeat,
            PythonRuntimeCodec.decodeExecutionHeartbeat(
                PythonRuntimeCodec.encodeExecutionHeartbeat(heartbeat),
            ),
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
        assertEquals(
            PythonRuntimeFixtures.structuredResult,
            PythonRuntimeCodec.decodeExecutionResult(
                PythonRuntimeCodec.encodeExecutionResult(PythonRuntimeFixtures.structuredResult),
            ),
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

        val prompt = PythonRuntimeFixtures.inputPrompt
        assertEquals(
            prompt,
            PythonRuntimeCodec.decodeInputPrompt(PythonRuntimeCodec.encodeInputPrompt(prompt)),
        )

        listOf(PythonRuntimeFixtures.inputValueReply, PythonRuntimeFixtures.inputEofReply).forEach { reply ->
            assertEquals(
                reply,
                PythonRuntimeCodec.decodeInputReply(PythonRuntimeCodec.encodeInputReply(reply)),
            )
        }
    }

    @Test
    fun optionalMinorVersionFieldsRoundTripWhileLegacyDefaultsStayDisabled() {
        val legacyCapabilities = PythonRuntimeCodec.decodeCapabilities(
            PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.capabilities),
        )
        assertFalse(legacyCapabilities.supportsHostCapabilitySnapshot)
        assertFalse(legacyCapabilities.supportsModuleEntry)
        assertFalse(legacyCapabilities.supportsInteractiveInput)
        assertFalse(legacyCapabilities.supportsStructuredJsonResult)
        assertFalse(legacyCapabilities.supportsOutputArtifacts)
        assertFalse(legacyCapabilities.supportsLongRunningExecution)
        assertEquals(0L, legacyCapabilities.limits.maxHostCapabilitySnapshotBytes)
        assertEquals(0, legacyCapabilities.limits.maxInputPromptBytes)
        assertEquals(0, legacyCapabilities.limits.maxInputReplyBytes)
        assertEquals(0, legacyCapabilities.limits.maxInputPrompts)
        assertEquals(0L, legacyCapabilities.limits.maxInputWaitMillis)
        assertEquals(0, legacyCapabilities.limits.maxStructuredJsonBytes)
        assertEquals(0, legacyCapabilities.limits.maxOutputArtifacts)
        assertEquals(0, legacyCapabilities.limits.maxOutputArtifactPathBytes)
        assertEquals(0L, legacyCapabilities.limits.maxOutputArtifactBytes)
        assertEquals(0L, legacyCapabilities.limits.maxTotalOutputArtifactBytes)
        assertEquals(
            PythonEntryMode.FILE,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.request),
            ).entryMode,
        )
        assertEquals(
            PythonExecutionMode.BOUNDED,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.request),
            ).executionMode,
        )

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
        assertEquals(
            PythonRuntimeFixtures.r2Capabilities,
            PythonRuntimeCodec.decodeCapabilities(
                PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r2Capabilities),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.r2ModuleRequest,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r2ModuleRequest),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.r3Capabilities,
            PythonRuntimeCodec.decodeCapabilities(
                PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r3Capabilities),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.r3InteractiveRequest,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r3InteractiveRequest),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.r4Capabilities,
            PythonRuntimeCodec.decodeCapabilities(
                PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r4Capabilities),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.r4ResultRequest,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r4ResultRequest),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.longRunningCapabilities,
            PythonRuntimeCodec.decodeCapabilities(
                PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.longRunningCapabilities),
            ),
        )
        assertEquals(
            PythonRuntimeFixtures.longRunningRequest,
            PythonRuntimeCodec.decodeExecutionRequest(
                PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.longRunningRequest),
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
