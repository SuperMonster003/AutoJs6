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

        val moduleCapabilitiesBytes = PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r2Capabilities)
        assertEquals(
            PythonRuntimeFixtures.r2Capabilities,
            PythonRuntimeCodec.decodeCapabilities(moduleCapabilitiesBytes),
        )
        val moduleRequestBytes = PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r2ModuleRequest)
        assertEquals(
            PythonRuntimeFixtures.r2ModuleRequest,
            PythonRuntimeCodec.decodeExecutionRequest(moduleRequestBytes),
        )
        val interactiveCapabilitiesBytes =
            PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r3Capabilities)
        assertEquals(
            PythonRuntimeFixtures.r3Capabilities,
            PythonRuntimeCodec.decodeCapabilities(interactiveCapabilitiesBytes),
        )
        val interactiveRequestBytes =
            PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r3InteractiveRequest)
        assertEquals(
            PythonRuntimeFixtures.r3InteractiveRequest,
            PythonRuntimeCodec.decodeExecutionRequest(interactiveRequestBytes),
        )
        val resultCapabilitiesBytes =
            PythonRuntimeCodec.encodeCapabilities(PythonRuntimeFixtures.r4Capabilities)
        assertEquals(
            PythonRuntimeFixtures.r4Capabilities,
            PythonRuntimeCodec.decodeCapabilities(resultCapabilitiesBytes),
        )
        val resultRequestBytes =
            PythonRuntimeCodec.encodeExecutionRequest(PythonRuntimeFixtures.r4ResultRequest)
        assertEquals(
            PythonRuntimeFixtures.r4ResultRequest,
            PythonRuntimeCodec.decodeExecutionRequest(resultRequestBytes),
        )
        val inputPromptBytes = PythonRuntimeCodec.encodeInputPrompt(PythonRuntimeFixtures.inputPrompt)
        assertEquals(
            PythonRuntimeFixtures.inputPrompt,
            PythonRuntimeCodec.decodeInputPrompt(inputPromptBytes),
        )
        val inputValueReplyBytes = PythonRuntimeCodec.encodeInputReply(PythonRuntimeFixtures.inputValueReply)
        assertEquals(
            PythonRuntimeFixtures.inputValueReply,
            PythonRuntimeCodec.decodeInputReply(inputValueReplyBytes),
        )
        val inputEofReplyBytes = PythonRuntimeCodec.encodeInputReply(PythonRuntimeFixtures.inputEofReply)
        assertEquals(
            PythonRuntimeFixtures.inputEofReply,
            PythonRuntimeCodec.decodeInputReply(inputEofReplyBytes),
        )

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

        val structuredResultBytes =
            PythonRuntimeCodec.encodeExecutionResult(PythonRuntimeFixtures.structuredResult)
        assertEquals(
            PythonRuntimeFixtures.structuredResult,
            PythonRuntimeCodec.decodeExecutionResult(structuredResultBytes),
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
        val errorBytes = PythonRuntimeCodec.encodeExecutionError(error)
        assertEquals(error, PythonRuntimeCodec.decodeExecutionError(errorBytes))

        val cancellation = canonicalCancellation()
        val cancellationBytes = PythonRuntimeCodec.encodeCancellation(cancellation)
        assertEquals(cancellation, PythonRuntimeCodec.decodeCancellation(cancellationBytes))

        val actual = linkedMapOf(
            "runtimeInfo" to runtimeInfoBytes.sha256(),
            "capabilitiesWithLimits" to capabilitiesBytes.sha256(),
            "requestWithPayloads" to requestBytes.sha256(),
            "moduleCapabilities" to moduleCapabilitiesBytes.sha256(),
            "moduleRequest" to moduleRequestBytes.sha256(),
            "interactiveCapabilities" to interactiveCapabilitiesBytes.sha256(),
            "interactiveRequest" to interactiveRequestBytes.sha256(),
            "resultCapabilities" to resultCapabilitiesBytes.sha256(),
            "resultRequest" to resultRequestBytes.sha256(),
            "inputPrompt" to inputPromptBytes.sha256(),
            "inputValueReply" to inputValueReplyBytes.sha256(),
            "inputEofReply" to inputEofReplyBytes.sha256(),
            "sessionStarted" to startedBytes.sha256(),
            "outputChunk" to outputBytes.sha256(),
            "executionResult" to resultBytes.sha256(),
            "structuredExecutionResult" to structuredResultBytes.sha256(),
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
            "moduleCapabilities" to "55c94c4dc9e1337a775e781884eda7578e718990032df5412a2a58ce58fdaca4",
            "moduleRequest" to "7d491120ea66ce96e76c2e06c29ed9f4b2e624d6d5e9657d1ef6fb03a46c31c1",
            "interactiveCapabilities" to "ec1b44378d2070c257938aa31e93304ff5b8c1ba46f1d42b40d3752b136a9bb1",
            "interactiveRequest" to "c316cac81b3404c9abbd917f7fd7e118a043bfdfa2c65dcb2344086dc664115a",
            "resultCapabilities" to "4f666874922fed148874ea335aa7b4999039840aaed08af191ae038d7cc66714",
            "resultRequest" to "b15e404ae9622b57bf9e28d3fc8a5f44f0595267707db51fbd492ff86c2f1fd0",
            "inputPrompt" to "094e1ff42f09ab33d08874cc9f0b3aeb3e8be4da8e3393084f0cc2f7049284d8",
            "inputValueReply" to "46ff07c631603bac67000f44c339c9fc892a95461ceee08b2623abce3677e191",
            "inputEofReply" to "122f8c9f9ec990f419f8d15b164bd9224679770578cccd9017504f869ad8dbd6",
            "sessionStarted" to "18f7bcdd5c964fc6791b58d0b68009987e55e92dc55484d84efd04cc77e1c2fa",
            "outputChunk" to "9319ae7787325e1656194e35696023e97c06b9bb423f5838d8b7f5db5325eed9",
            "executionResult" to "c20af7d65a7e1ceb0a70808b475dff2c98333c5e9f1e59af29817341345d9514",
            "structuredExecutionResult" to "b3adee28ba0f639ebd3675da30df85f7c9f6736bb88f6b4d6493a4bf645e9d43",
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
