package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class PythonRuntimeGoldenWireTest {
    @Test
    fun allProtocolV1DocumentsMatchCanonicalSha256Goldens() {
        val runtimeInfo = PythonRuntimeFixtures.runtimeInfo.copy(maxHostVersionCode = 9_999)
        val runtimeInfoBytes = PythonRuntimeCodec.encodeRuntimeInfo(runtimeInfo)
        assertEquals(runtimeInfo, PythonRuntimeCodec.decodeRuntimeInfo(runtimeInfoBytes))

        val capabilities = PythonRuntimeFixtures.capabilities
        val capabilitiesBytes = PythonRuntimeCodec.encodeCapabilities(capabilities)
        assertEquals(capabilities, PythonRuntimeCodec.decodeCapabilities(capabilitiesBytes))

        val request = PythonRuntimeFixtures.request
        val requestBytes = PythonRuntimeCodec.encodeExecutionRequest(request)
        assertEquals(request, PythonRuntimeCodec.decodeExecutionRequest(requestBytes))

        val started = PythonRuntimeFixtures.started
        val startedBytes = PythonRuntimeCodec.encodeSessionStarted(started)
        assertEquals(started, PythonRuntimeCodec.decodeSessionStarted(startedBytes))

        val output = PythonOutputChunk(
            requestId = PythonRuntimeFixtures.requestId,
            sequence = 7,
            stream = PythonOutputStream.STDOUT,
            data = "hello".toByteArray(),
        )
        val outputBytes = PythonRuntimeCodec.encodeOutputChunk(output)
        assertEquals(output, PythonRuntimeCodec.decodeOutputChunk(outputBytes))

        val result = PythonExecutionResult(
            requestId = PythonRuntimeFixtures.requestId,
            exitCode = 0,
            elapsedMillis = 25,
            stdoutBytes = 5,
            stderrBytes = 0,
            stdoutChunks = 1,
            stderrChunks = 0,
        )
        val resultBytes = PythonRuntimeCodec.encodeExecutionResult(result)
        assertEquals(result, PythonRuntimeCodec.decodeExecutionResult(resultBytes))

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
        val errorBytes = PythonRuntimeCodec.encodeExecutionError(error)
        assertEquals(error, PythonRuntimeCodec.decodeExecutionError(errorBytes))

        val cancellation = canonicalCancellation()
        val cancellationBytes = PythonRuntimeCodec.encodeCancellation(cancellation)
        assertEquals(cancellation, PythonRuntimeCodec.decodeCancellation(cancellationBytes))

        val actual = linkedMapOf(
            "runtimeInfo" to runtimeInfoBytes.sha256(),
            "capabilitiesWithLimits" to capabilitiesBytes.sha256(),
            "requestWithPayloads" to requestBytes.sha256(),
            "sessionStarted" to startedBytes.sha256(),
            "outputChunk" to outputBytes.sha256(),
            "executionResult" to resultBytes.sha256(),
            "executionErrorWithTraceback" to errorBytes.sha256(),
            "cancellation" to cancellationBytes.sha256(),
        )
        assertEquals(GOLDEN_SHA256, actual)
    }

    @Test
    fun cancellationAlsoMatchesCompleteProtocolV1GoldenDocument() {
        val cancellation = canonicalCancellation()
        val encoded = PythonRuntimeCodec.encodeCancellation(cancellation)

        assertEquals(CANCELLATION_GOLDEN_HEX, encoded.toHexString())
        assertEquals(
            cancellation,
            PythonRuntimeCodec.decodeCancellation(CANCELLATION_GOLDEN_HEX.hexToByteArray()),
        )
    }

    private fun canonicalCancellation() = PythonExecutionCancellation(
        requestId = PythonRuntimeFixtures.requestId,
        reason = PythonCancellationReason.REQUESTED,
        phase = PythonFailurePhase.CANCELLATION,
        elapsedMillis = 10,
    )

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .toHexString()

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        HEX[(byte.toInt() ushr 4) and 0x0f].toString() + HEX[byte.toInt() and 0x0f]
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private companion object {
        const val HEX = "0123456789abcdef"

        val GOLDEN_SHA256 = linkedMapOf(
            "runtimeInfo" to "5572d6b4fcfeaa967898393296e82cc0ea7f2c3a1db0d795151fc73065ecc10d",
            "capabilitiesWithLimits" to "eab82e4c0ce5357ea7484b7f63b1b11444056349f4d05727237f12c98d6996a6",
            "requestWithPayloads" to "844409a29526e3b21a0faaee0ddc8cc82822b86b1c4cba16e2cfb59fff538c66",
            "sessionStarted" to "18f7bcdd5c964fc6791b58d0b68009987e55e92dc55484d84efd04cc77e1c2fa",
            "outputChunk" to "9319ae7787325e1656194e35696023e97c06b9bb423f5838d8b7f5db5325eed9",
            "executionResult" to "c20af7d65a7e1ceb0a70808b475dff2c98333c5e9f1e59af29817341345d9514",
            "executionErrorWithTraceback" to "5cadadceaa094d80f7d9b38e59e51f2fc038d4e73175f4baf7d745e5af19e2d6",
            "cancellation" to "6f28ed31c4236d0c6f9f4e9c2e11fba4a125d6986b25de4fcbd3460fb365b7b0",
        )

        const val CANCELLATION_GOLDEN_HEX =
            "414a3657000000010000007c50590016000000010000000000000004" +
                "0000000100000005000000000000001012345678123456789abcdef012345678" +
                "0000000200000001000000000000000400000001000000030000000100000000" +
                "000000040000000700000004000000020000000000000008000000000000000a"
    }
}
