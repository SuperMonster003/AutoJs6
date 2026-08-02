package org.autojs.plugin.ai.text.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiTextQuotaPolicyTest {
    @Test
    fun validRequestProducesEffectiveIntersectionAndExactTotals() {
        val request = request(message = textPayload("Hello"))

        val snapshot = AiTextQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 0)

        assertEquals(1, snapshot.messageCount)
        assertEquals(1, snapshot.contentPartCount)
        assertEquals(5L, snapshot.inputBytes)
        assertEquals(0, snapshot.descriptorCount)
        assertEquals(AiTextLimits.MAX_STREAMING_OUTPUT_BYTES, snapshot.effectiveMaximumOutputBytes)
    }

    @Test
    fun requestedFeatureMustBeDeclaredByProvider() {
        val request = request(message = textPayload("Hello"))

        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateRequest(
                request,
                capabilities(streaming = false),
                descriptorCount = 0,
            )
        }
    }

    @Test
    fun descriptorReferencesMustExactlyMatchBinderArray() {
        val request = request(
            message = descriptorPayload(AiTextMimeType.PLAIN, 5, 0, charset = "utf-8"),
        )

        AiTextQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 1)
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 2)
        }
    }

    @Test
    fun nonStreamingRequestUsesSmallerHostOutputCeiling() {
        val request = request(
            message = textPayload("Hello"),
            stream = false,
            maximumOutputBytes = AiTextLimits.MAX_OUTPUT_BYTES + 1,
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 0)
        }
    }

    @Test
    fun duplicateToolNamesAndSchemaTotalsAreRejected() {
        val schema = descriptorPayload(
            AiTextMimeType.JSON,
            AiTextLimits.MAX_ONE_SCHEMA_BYTES,
            descriptorIndex = 0,
        )
        val duplicate = request(
            message = textPayload("Hello"),
            tools = listOf(
                AiToolDefinition("lookup", "First", schema),
                AiToolDefinition("lookup", "Second", descriptorPayload(
                    AiTextMimeType.JSON,
                    AiTextLimits.MAX_ONE_SCHEMA_BYTES,
                    descriptorIndex = 1,
                )),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateRequest(duplicate, capabilities(), descriptorCount = 2)
        }
    }

    @Test
    fun aggregateToolSchemaQuotaIsIndependentFromPerSchemaQuota() {
        val schemaBytes = ("{\"x\":\"" +
            " ".repeat(AiTextLimits.MAX_ONE_SCHEMA_BYTES.toInt() - 8) +
            "\"}").toByteArray()
        val tools = (1..17).map { index ->
            AiToolDefinition(
                name = "tool-$index",
                description = "Tool $index",
                inputSchema = org.autojs.plugin.ai.common.api.AiPayloadReference(
                    mimeType = AiTextMimeType.JSON,
                    declaredLengthBytes = schemaBytes.size.toLong(),
                    inlineBytes = schemaBytes,
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateRequest(
                request(message = textPayload("Hello"), tools = tools),
                capabilities(),
                descriptorCount = 0,
            )
        }
    }

    @Test
    fun capabilityIntersectionUsesTheSmallestLimitsAndCommonMimeTypes() {
        val host = capabilities()
        val provider = capabilities().copy(
            maximumMessages = 8,
            maximumOutputBytes = 4096,
            acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN),
        )

        val effective = AiTextCapabilityNegotiator.intersect(host, provider)

        assertEquals(8, effective.maximumMessages)
        assertEquals(4096L, effective.maximumOutputBytes)
        assertEquals(listOf(AiTextMimeType.PLAIN), effective.acceptedTextMimeTypes)
    }

    @Test
    fun capabilityIntersectionNormalizesDisabledToolLimits() {
        val provider = capabilities(tools = false).copy(
            maximumToolDefinitions = 8,
            maximumToolRounds = 2,
            maximumOutstandingToolCalls = 4,
        )

        val effective = AiTextCapabilityNegotiator.intersect(capabilities(), provider)

        assertEquals(false, effective.supportsTools)
        assertEquals(0, effective.maximumToolDefinitions)
        assertEquals(0, effective.maximumToolRounds)
        assertEquals(0, effective.maximumOutstandingToolCalls)
    }

    @Test
    fun capabilityIntersectionRejectsNoCommonTextMimeType() {
        val host = capabilities().copy(acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN))
        val provider = capabilities().copy(acceptedTextMimeTypes = listOf(AiTextMimeType.JSON))

        assertThrows(IllegalArgumentException::class.java) {
            AiTextCapabilityNegotiator.intersect(host, provider)
        }
    }

    @Test
    fun toolResultDescriptorsMustBeUniqueExhaustiveAndWithinLimit() {
        val results = AiToolResultBatch(
            listOf(
                AiToolResult("call-a", descriptorPayload(AiTextMimeType.JSON, 2, 0)),
                AiToolResult("call-b", descriptorPayload(AiTextMimeType.JSON, 2, 1)),
            ),
        )

        assertEquals(4L, AiTextQuotaPolicy.validateToolResults(results, 2, 2))
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateToolResults(results, 1, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateToolResults(results, 2, 1)
        }
    }

    @Test
    fun terminalValidationBindsMimeBytesReasoningDescriptorsAndUsageToRequest() {
        val request = request(
            message = textPayload("Hello"),
            includeReasoning = true,
            reportUsage = true,
            maximumOutputBytes = 12,
            maximumOutputTokens = 4,
        )
        val result = AiCompletionResult(
            output = descriptorPayload(AiTextMimeType.PLAIN, 5, 0, charset = "utf-8"),
            finishReason = AiFinishReason.STOP,
            usage = AiUsage(outputTokens = 3, reasoningTokens = 2),
            reasoning = AiReasoningMetadata(
                summary = descriptorPayload(AiTextMimeType.PLAIN, 3, 1, charset = "utf-8"),
                reasoningTokens = 2,
            ),
        )

        val snapshot = AiTextQuotaPolicy.validateCompletion(
            request = request,
            result = result,
            descriptorCount = 2,
            maximumDescriptors = 2,
            observedUsage = AiUsage(inputTokens = 7),
            readDescriptor = { index, _ ->
                when (index) {
                    0 -> "hello".toByteArray()
                    1 -> "why".toByteArray()
                    else -> error("Unexpected descriptor index")
                }
            },
        )

        assertEquals(8L, snapshot.totalGeneratedBytes)
        assertEquals(7L, snapshot.finalUsage?.inputTokens)
        assertEquals(3L, snapshot.finalUsage?.outputTokens)
    }

    @Test
    fun terminalValidationRejectsWrongMimeQuotaAndActualDescriptorCount() {
        val plainRequest = request(message = textPayload("Hello"), maximumOutputBytes = 4)
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateCompletion(
                plainRequest,
                AiCompletionResult(jsonPayload("{}"), AiFinishReason.STOP),
                descriptorCount = 0,
                maximumDescriptors = 2,
            )
        }

        val reasoningRequest = request(
            message = textPayload("Hello"),
            includeReasoning = true,
            maximumOutputBytes = 4,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateCompletion(
                reasoningRequest,
                AiCompletionResult(
                    output = textPayload("abc"),
                    finishReason = AiFinishReason.STOP,
                    reasoning = AiReasoningMetadata(textPayload("xy")),
                ),
                descriptorCount = 0,
                maximumDescriptors = 2,
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateCompletion(
                plainRequest,
                AiCompletionResult(
                    descriptorPayload(AiTextMimeType.PLAIN, 4, 0, charset = "utf-8"),
                    AiFinishReason.STOP,
                ),
                descriptorCount = 0,
                maximumDescriptors = 2,
            )
        }
    }

    @Test
    fun terminalTokenLimitRequiresVerifiableOutputUsage() {
        val request = request(
            message = textPayload("Hello"),
            maximumOutputTokens = 2,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateCompletion(
                request,
                AiCompletionResult(textPayload("ok"), AiFinishReason.STOP),
                descriptorCount = 0,
                maximumDescriptors = 0,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateCompletion(
                request,
                AiCompletionResult(
                    textPayload("ok"),
                    AiFinishReason.STOP,
                    usage = AiUsage(outputTokens = 3),
                ),
                descriptorCount = 0,
                maximumDescriptors = 0,
            )
        }
    }

    @Test
    fun toolDescriptorValidationAlwaysClampsToTheProtocolCeiling() {
        val results = AiToolResultBatch(
            (0 until org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION).map { index ->
                AiToolResult(
                    callId = "call-$index",
                    output = descriptorPayload(AiTextMimeType.JSON, 2, index),
                )
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiTextQuotaPolicy.validateToolResults(
                results,
                descriptorCount = results.results.size + 1,
                maximumDescriptors = Int.MAX_VALUE,
            )
        }
    }

    @Test
    fun textVersionMustBeNegotiatedAndThenMatchedExactly() {
        val selected = AiTextVersionPolicy.negotiate(
            AiProtocolRange(AiProtocolVersion(1, 0), AiProtocolVersion(1, 1)),
        )
        assertEquals(AiProtocolVersion(1, 0), selected)
        AiTextVersionPolicy.requireSelected(AiProtocolVersion(1, 0), selected)
        assertThrows(IllegalArgumentException::class.java) {
            AiTextVersionPolicy.requireSelected(AiProtocolVersion(1, 1), selected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTextVersionPolicy.negotiate(
                AiProtocolRange(AiProtocolVersion(2, 0), AiProtocolVersion(2, 0)),
            )
        }
    }

    private fun request(
        message: org.autojs.plugin.ai.common.api.AiPayloadReference,
        stream: Boolean = true,
        includeReasoning: Boolean = false,
        reportUsage: Boolean = false,
        maximumOutputBytes: Long = 4096,
        maximumOutputTokens: Long? = null,
        tools: List<AiToolDefinition> = emptyList(),
    ) = AiTextRequest(
        requestId = "request-quota",
        protocolVersion = AiProtocolVersion(1, 0),
        providerId = "fake.local",
        modelId = "fake.echo",
        messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(message)))),
        options = AiGenerationOptions(
            stream = stream,
            includeReasoning = includeReasoning,
            structuredJson = false,
            reportUsage = reportUsage,
            maximumOutputBytes = maximumOutputBytes,
            maximumOutputTokens = maximumOutputTokens,
            maximumToolRounds = if (tools.isEmpty()) 0 else 1,
            timeoutMillis = 30_000,
        ),
        tools = tools,
    )
}
