package org.autojs.plugin.ai.text.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AiTextLifecyclePolicyTest {
    @Test
    fun creditsBoundChunksAndSequenceMustIncreaseByOne() {
        val policy = startedPolicy(firstSequence = 10)
        policy.grantCredits(2)

        policy.onChunk(AiTextChunk(10, textDelta = "a"))
        policy.onChunk(AiTextChunk(11, textDelta = "b"))

        assertEquals(0, policy.outstandingCredits)
        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(12, textDelta = "no-credit"))
        }
    }

    @Test
    fun reorderedOrDuplicateChunkSequenceIsRejectedWithoutSpendingCredit() {
        val policy = startedPolicy(firstSequence = 2)
        policy.grantCredits(2)

        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(3, textDelta = "reordered"))
        }
        assertEquals(2, policy.outstandingCredits)
        policy.onChunk(AiTextChunk(2, textDelta = "accepted"))
        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(2, textDelta = "duplicate"))
        }
    }

    @Test
    fun creditWindowRejectsZeroNegativeAndOverflowGrants() {
        val credits = AiCreditWindow()
        assertThrows(IllegalArgumentException::class.java) { credits.grant(0) }
        assertThrows(IllegalArgumentException::class.java) { credits.grant(-1) }
        credits.grant(AiTextLimits.MAX_OUTSTANDING_CHUNK_CREDITS)
        assertThrows(IllegalArgumentException::class.java) { credits.grant(1) }
    }

    @Test
    fun exactlyOneTerminalIsAcceptedAndCompletionRequiresStart() {
        val notStarted = AiTextSessionPolicy(maximumToolRounds = 0)
        assertThrows(IllegalStateException::class.java) { notStarted.onCompleted() }
        notStarted.onFailed()
        assertEquals(AiTerminalGate.Terminal.FAILED, notStarted.terminal)
        assertThrows(IllegalStateException::class.java) { notStarted.onCancelled() }

        val started = startedPolicy()
        started.onCompleted()
        assertEquals(AiTerminalGate.Terminal.COMPLETED, started.terminal)
        assertThrows(IllegalStateException::class.java) { started.onFailed() }
    }

    @Test
    fun cancelAndCloseRequestsAreIdempotentAcrossTerminalPaths() {
        val policy = startedPolicy()
        assertTrue(policy.requestCancel())
        assertFalse(policy.requestCancel())
        policy.onCancelled()
        assertFalse(policy.requestCancel())
        assertTrue(policy.close())
        assertFalse(policy.close())
    }

    @Test
    fun cancelRequestRejectsLaterDataAndSuccessButAllowsOneFailureTerminal() {
        val policy = startedPolicy()
        policy.grantCredits(1)
        assertTrue(policy.requestCancel())

        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(0, textDelta = "late"))
        }
        assertThrows(IllegalStateException::class.java) {
            policy.onUsage(AiUsage(outputTokens = 1))
        }
        assertThrows(IllegalStateException::class.java) { policy.onCompleted() }
        policy.onFailed()
        assertEquals(AiTerminalGate.Terminal.FAILED, policy.terminal)
        assertThrows(IllegalStateException::class.java) { policy.onCancelled() }

        val cancelledBeforeStart = AiTextSessionPolicy(maximumToolRounds = 0)
        assertTrue(cancelledBeforeStart.requestCancel())
        assertThrows(IllegalStateException::class.java) {
            cancelledBeforeStart.onStarted(startedMetadata())
        }
        cancelledBeforeStart.onCancelled()
        assertEquals(AiTerminalGate.Terminal.CANCELLED, cancelledBeforeStart.terminal)
    }

    @Test
    fun oneToolTurnMustReceiveTheExactResultSetBeforeCompletion() {
        val policy = startedPolicy(maximumToolRounds = 2)
        val calls = calls("call-a", "call-b")
        policy.onToolCalls(calls)

        assertThrows(IllegalStateException::class.java) { policy.onCompleted() }
        assertThrows(IllegalArgumentException::class.java) {
            policy.submitToolResults(results("call-a"))
        }
        policy.submitToolResults(results("call-b", "call-a"))
        assertEquals(1, policy.completedToolRounds)
        policy.onCompleted()
    }

    @Test
    fun toolCallIdsCannotBeReplayedInLaterRounds() {
        val policy = startedPolicy(maximumToolRounds = 2)
        policy.onToolCalls(calls("call-a"))
        policy.submitToolResults(results("call-a"))

        assertThrows(IllegalArgumentException::class.java) {
            policy.onToolCalls(calls("call-a"))
        }
    }

    @Test
    fun chunksPauseDuringToolTurnAndUsageMustBeMonotonic() {
        val policy = startedPolicy(maximumToolRounds = 1)
        policy.grantCredits(1)
        policy.onUsage(AiUsage(inputTokens = 5, outputTokens = 1, totalTokens = 10))
        policy.onUsage(AiUsage(outputTokens = 2))
        policy.onToolCalls(calls("call-a"))

        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(0, textDelta = "paused"))
        }
        policy.submitToolResults(results("call-a"))
        assertThrows(IllegalStateException::class.java) {
            policy.onUsage(AiUsage(inputTokens = 4, outputTokens = 2, totalTokens = 10))
        }
        policy.onChunk(AiTextChunk(0, textDelta = "resumed"))
    }

    @Test
    fun streamingOutputQuotaRejectsBeforeSpendingSequenceOrCredit() {
        val policy = AiTextSessionPolicy(maximumToolRounds = 0, maximumOutputBytes = 3).apply {
            onStarted(startedMetadata(effectiveMaximumOutputBytes = 3))
            grantCredits(1)
        }

        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(0, textDelta = "four"))
        }
        assertEquals(1, policy.outstandingCredits)
        policy.onChunk(AiTextChunk(0, textDelta = "ok"))
    }

    @Test
    fun toolTrafficQuotaAccumulatesAcrossArgumentsAndResults() {
        val calls = AiToolCallBatch(
            (0 until AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS).map { index ->
                AiToolCall(
                    callId = "call-$index",
                    name = "lookup",
                    arguments = AiPayloadReference(
                        mimeType = AiTextMimeType.JSON,
                        declaredLengthBytes = AiTextLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES,
                        descriptorIndex = index,
                    ),
                )
            },
        )
        val policy = startedPolicy(maximumToolRounds = 1)
        policy.onToolCalls(calls)

        assertThrows(IllegalArgumentException::class.java) {
            policy.submitToolResults(
                AiToolResultBatch(
                    calls.calls.map { call -> AiToolResult(call.callId, jsonPayload("{}")) },
                ),
            )
        }
    }

    @Test
    fun providerStartMetadataCannotWidenNegotiatedLimits() {
        val outputPolicy = AiTextSessionPolicy(maximumToolRounds = 1, maximumOutputBytes = 1024)
        assertThrows(IllegalArgumentException::class.java) {
            outputPolicy.onStarted(
                startedMetadata(maximumToolRounds = 1, effectiveMaximumOutputBytes = 1025),
            )
        }

        val toolPolicy = AiTextSessionPolicy(maximumToolRounds = 1)
        assertThrows(IllegalArgumentException::class.java) {
            toolPolicy.onStarted(startedMetadata(maximumToolRounds = 2))
        }
    }

    @Test
    fun narrowerProviderStartLimitsAreEnforcedForTheSession() {
        val policy = AiTextSessionPolicy(maximumToolRounds = 2, maximumOutputBytes = 16).apply {
            onStarted(startedMetadata(maximumToolRounds = 1, effectiveMaximumOutputBytes = 3))
            grantCredits(1)
        }

        assertThrows(IllegalStateException::class.java) {
            policy.onChunk(AiTextChunk(0, textDelta = "four"))
        }
        policy.onChunk(AiTextChunk(0, textDelta = "ok"))
        policy.onToolCalls(calls("call-a"))
        policy.submitToolResults(results("call-a"))
        assertThrows(IllegalStateException::class.java) {
            policy.onToolCalls(calls("call-b"))
        }
    }

    @Test
    fun requestAwareSessionRejectsMismatchedIdentityAndValidatesTerminalPayload() {
        val request = AiTextRequest(
            requestId = "request-life",
            protocolVersion = AiProtocolVersion(1, 0),
            providerId = "fake.local",
            modelId = "fake.echo",
            messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(textPayload("hello"))))),
            options = AiGenerationOptions(
                stream = false,
                includeReasoning = false,
                structuredJson = false,
                reportUsage = true,
                maximumOutputBytes = 16,
                timeoutMillis = 30_000,
            ),
        )
        val mismatched = AiTextSessionPolicy(
            maximumToolRounds = 0,
            maximumOutputBytes = 16,
            expectedRequest = request,
        )
        assertThrows(IllegalArgumentException::class.java) {
            mismatched.onStarted(startedMetadata().copy(modelId = "fake.other"))
        }

        val policy = AiTextSessionPolicy(
            maximumToolRounds = 0,
            maximumOutputBytes = 16,
            expectedRequest = request,
        )
        policy.onStarted(startedMetadata(effectiveMaximumOutputBytes = 16))
        policy.onUsage(AiUsage(inputTokens = 2))
        val snapshot = policy.onCompleted(
            AiCompletionResult(
                output = textPayload("ok"),
                finishReason = AiFinishReason.STOP,
                usage = AiUsage(outputTokens = 1),
            ),
            descriptorCount = 0,
        )
        assertEquals(2L, snapshot.finalUsage?.inputTokens)
        assertEquals(AiTerminalGate.Terminal.COMPLETED, policy.terminal)
    }

    @Test
    fun requestAwareSessionMaterializesDescriptorBackedCompletionThroughItsReader() {
        val request = AiTextRequest(
            requestId = "request-life",
            protocolVersion = AiProtocolVersion(1, 0),
            providerId = "fake.local",
            modelId = "fake.echo",
            messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(textPayload("hello"))))),
            options = AiGenerationOptions(
                stream = false,
                includeReasoning = false,
                structuredJson = false,
                reportUsage = false,
                maximumOutputBytes = 16,
                timeoutMillis = 30_000,
            ),
        )
        val reads = AtomicInteger(0)
        val policy = AiTextSessionPolicy(
            maximumToolRounds = 0,
            maximumOutputBytes = 16,
            expectedRequest = request,
            maximumSessionDescriptors = 1,
        )
        policy.onStarted(startedMetadata(effectiveMaximumOutputBytes = 16))
        val snapshot = policy.onCompleted(
            result = AiCompletionResult(
                output = descriptorPayload(AiTextMimeType.PLAIN, 2, 0, charset = "utf-8"),
                finishReason = AiFinishReason.STOP,
            ),
            descriptorCount = 1,
            readDescriptor = { index, declaredLengthBytes ->
                assertEquals(0, index)
                assertEquals(2L, declaredLengthBytes)
                reads.incrementAndGet()
                "ok".toByteArray()
            },
        )

        assertEquals("ok", snapshot.outputText)
        assertEquals(1, reads.get())
        assertEquals(AiTerminalGate.Terminal.COMPLETED, policy.terminal)
    }

    private fun startedPolicy(
        firstSequence: Long = 0,
        maximumToolRounds: Int = 0,
    ): AiTextSessionPolicy = AiTextSessionPolicy(maximumToolRounds).apply {
        onStarted(startedMetadata(firstSequence, maximumToolRounds))
    }

    private fun startedMetadata(
        firstSequence: Long = 0,
        maximumToolRounds: Int = 0,
        effectiveMaximumOutputBytes: Long = 4096,
    ) = AiSessionStarted(
        requestId = "request-life",
        sessionId = "session-life",
        protocolVersion = AiProtocolVersion(1, 0),
        providerId = "fake.local",
        modelId = "fake.echo",
        firstChunkSequence = firstSequence,
        effectiveMaximumOutputBytes = effectiveMaximumOutputBytes,
        effectiveMaximumToolRounds = maximumToolRounds,
    )

    private fun calls(vararg ids: String) = AiToolCallBatch(
        ids.map { id -> AiToolCall(id, "lookup", jsonPayload("{}")) },
    )

    private fun results(vararg ids: String) = AiToolResultBatch(
        ids.map { id -> AiToolResult(id, jsonPayload("{}")) },
    )
}
