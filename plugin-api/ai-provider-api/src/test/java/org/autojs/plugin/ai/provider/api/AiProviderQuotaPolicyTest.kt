package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

class AiProviderQuotaPolicyTest {
    @Test
    fun validRequestProducesEffectiveIntersectionAndExactTotals() {
        val request = request(message = textPayload("Hello"))

        val snapshot = AiProviderQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 0)

        assertEquals(1, snapshot.messageCount)
        assertEquals(1, snapshot.contentPartCount)
        assertEquals(5L, snapshot.inputBytes)
        assertEquals(0, snapshot.descriptorCount)
        assertEquals(AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES, snapshot.effectiveMaximumOutputBytes)
    }

    @Test
    fun requestedFeatureMustBeDeclaredByProvider() {
        val request = request(message = textPayload("Hello"))

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(
                request,
                capabilities(streaming = false),
                descriptorCount = 0,
            )
        }
    }

    @Test
    fun persistentSessionMustBeDeclaredByBothRequestAndProvider() {
        val persistentRequest = request(
            message = textPayload("Hello"),
            persistentSession = true,
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(
                persistentRequest,
                capabilities(persistent = false),
                descriptorCount = 0,
            )
        }
        val snapshot = AiProviderQuotaPolicy.validateRequest(
            persistentRequest,
            capabilities(persistent = true),
            descriptorCount = 0,
        )
        assertEquals(5L, snapshot.inputBytes)
    }

    @Test
    fun descriptorReferencesMustExactlyMatchBinderArray() {
        val request = request(
            message = descriptorPayload(AiProviderMimeType.PLAIN, 5, 0, charset = "utf-8"),
        )

        AiProviderQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 1)
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 2)
        }
    }

    @Test
    fun nonStreamingRequestUsesSmallerHostOutputCeiling() {
        val request = request(
            message = textPayload("Hello"),
            stream = false,
            maximumOutputBytes = AiProviderLimits.MAX_OUTPUT_BYTES + 1,
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(request, capabilities(), descriptorCount = 0)
        }
    }

    @Test
    fun duplicateToolNamesAndSchemaTotalsAreRejected() {
        val schema = descriptorPayload(
            AiProviderMimeType.JSON,
            AiProviderLimits.MAX_ONE_SCHEMA_BYTES,
            descriptorIndex = 0,
        )
        val duplicate = request(
            message = textPayload("Hello"),
            tools = listOf(
                AiToolDefinition("lookup", "First", schema),
                AiToolDefinition("lookup", "Second", descriptorPayload(
                    AiProviderMimeType.JSON,
                    AiProviderLimits.MAX_ONE_SCHEMA_BYTES,
                    descriptorIndex = 1,
                )),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(duplicate, capabilities(), descriptorCount = 2)
        }
    }

    @Test
    fun aggregateToolSchemaQuotaIsIndependentFromPerSchemaQuota() {
        val schemaBytes = ("{\"x\":\"" +
            " ".repeat(AiProviderLimits.MAX_ONE_SCHEMA_BYTES.toInt() - 8) +
            "\"}").toByteArray()
        val tools = (1..17).map { index ->
            AiToolDefinition(
                name = "tool-$index",
                description = "Tool $index",
                inputSchema = org.autojs.plugin.ai.common.api.AiPayloadReference(
                    mimeType = AiProviderMimeType.JSON,
                    declaredLengthBytes = schemaBytes.size.toLong(),
                    inlineBytes = schemaBytes,
                ),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateRequest(
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
            acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN),
        )

        val effective = AiProviderCapabilityNegotiator.intersect(host, provider)

        assertEquals(8, effective.maximumMessages)
        assertEquals(4096L, effective.maximumOutputBytes)
        assertEquals(listOf(AiProviderMimeType.PLAIN), effective.acceptedTextMimeTypes)
    }

    @Test
    fun capabilityIntersectionNormalizesDisabledToolLimits() {
        val provider = capabilities(tools = false).copy(
            maximumToolDefinitions = 8,
            maximumToolRounds = 2,
            maximumOutstandingToolCalls = 4,
        )

        val effective = AiProviderCapabilityNegotiator.intersect(capabilities(), provider)

        assertEquals(false, effective.supportsTools)
        assertEquals(0, effective.maximumToolDefinitions)
        assertEquals(0, effective.maximumToolRounds)
        assertEquals(0, effective.maximumOutstandingToolCalls)
    }

    @Test
    fun capabilityIntersectionRejectsNoCommonTextMimeType() {
        val host = capabilities().copy(acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN))
        val provider = capabilities().copy(acceptedTextMimeTypes = listOf(AiProviderMimeType.JSON))

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderCapabilityNegotiator.intersect(host, provider)
        }
    }

    @Test
    fun toolResultDescriptorsMustBeUniqueExhaustiveAndWithinLimit() {
        val results = AiToolResultBatch(
            listOf(
                AiToolResult("call-a", descriptorPayload(AiProviderMimeType.JSON, 2, 0)),
                AiToolResult("call-b", descriptorPayload(AiProviderMimeType.JSON, 2, 1)),
            ),
        )

        assertEquals(4L, AiProviderQuotaPolicy.validateToolResults(results, 2, 2))
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateToolResults(results, 1, 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateToolResults(results, 2, 1)
        }
    }

    @Test
    fun requestAwareToolValidationRejectsUndeclaredNamesBeforeReadingDescriptors() {
        val request = request(
            message = textPayload("Hello"),
            tools = listOf(AiToolDefinition("lookup", "Lookup", jsonPayload("{}"))),
        )
        val reads = AtomicInteger(0)
        val batch = AiToolCallBatch(
            listOf(
                AiToolCall(
                    callId = "call-undeclared",
                    name = "delete-all",
                    arguments = descriptorPayload(AiProviderMimeType.JSON, 2, 0),
                ),
            ),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateToolCalls(
                request = request,
                batch = batch,
                descriptorCount = 1,
                maximumDescriptors = 1,
                readDescriptor = { _, _ ->
                    reads.incrementAndGet()
                    "{}".toByteArray()
                },
            )
        }
        assertEquals(0, reads.get())
    }

    @Test
    fun requestAwareToolValidationReturnsVerifiedDescriptorJsonSnapshot() {
        val request = request(
            message = textPayload("Hello"),
            tools = listOf(AiToolDefinition("lookup", "Lookup", jsonPayload("{}"))),
        )
        val bytes = "{\"query\":\"safe\"}".toByteArray()
        val batch = toolCallsWithDescriptor(
            AiPayloadReference(
                mimeType = AiProviderMimeType.JSON,
                declaredLengthBytes = bytes.size.toLong(),
                sha256 = sha256(bytes),
                descriptorIndex = 0,
            ),
        )

        val snapshot = AiProviderQuotaPolicy.validateToolCalls(
            request = request,
            batch = batch,
            descriptorCount = 1,
            maximumDescriptors = 1,
            readDescriptor = { _, _ -> bytes },
        )

        assertEquals(bytes.size.toLong(), snapshot.totalBytes)
        assertEquals(1, snapshot.descriptorCount)
        assertEquals("{\"query\":\"safe\"}", snapshot.argumentsJsonByCallId["call-1"])
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (snapshot.argumentsJsonByCallId as MutableMap<String, String>)["call-2"] = "{}"
        }
    }

    @Test
    fun requestAwareToolValidationRejectsMalformedLengthDigestUtf8AndOversizePayloads() {
        val request = request(
            message = textPayload("Hello"),
            tools = listOf(AiToolDefinition("lookup", "Lookup", jsonPayload("{}"))),
        )

        fun reject(payload: AiPayloadReference, bytes: ByteArray) {
            assertThrows(IllegalArgumentException::class.java) {
                AiProviderQuotaPolicy.validateToolCalls(
                    request = request,
                    batch = toolCallsWithDescriptor(payload),
                    descriptorCount = 1,
                    maximumDescriptors = 1,
                    readDescriptor = { _, _ -> bytes },
                )
            }
        }

        val malformed = "{]".toByteArray()
        reject(descriptorPayload(AiProviderMimeType.JSON, malformed.size.toLong(), 0), malformed)

        reject(descriptorPayload(AiProviderMimeType.JSON, 3, 0), "{}".toByteArray())

        val valid = "{}".toByteArray()
        reject(
            AiPayloadReference(
                mimeType = AiProviderMimeType.JSON,
                declaredLengthBytes = valid.size.toLong(),
                sha256 = "0".repeat(64),
                descriptorIndex = 0,
            ),
            valid,
        )

        val invalidUtf8 = byteArrayOf(0xC3.toByte(), 0x28)
        reject(descriptorPayload(AiProviderMimeType.JSON, invalidUtf8.size.toLong(), 0), invalidUtf8)

        val maximum = AiProviderLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES.toInt()
        reject(
            descriptorPayload(AiProviderMimeType.JSON, AiProviderLimits.MAX_TOOL_ARGUMENT_OR_RESULT_BYTES, 0),
            ByteArray(maximum + 1),
        )
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
            output = descriptorPayload(AiProviderMimeType.PLAIN, 5, 0, charset = "utf-8"),
            finishReason = AiFinishReason.STOP,
            usage = AiUsage(outputTokens = 3, reasoningTokens = 2),
            reasoning = AiReasoningMetadata(
                summary = descriptorPayload(AiProviderMimeType.PLAIN, 3, 1, charset = "utf-8"),
                reasoningTokens = 2,
            ),
        )

        val snapshot = AiProviderQuotaPolicy.validateCompletion(
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
            AiProviderQuotaPolicy.validateCompletion(
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
            AiProviderQuotaPolicy.validateCompletion(
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
            AiProviderQuotaPolicy.validateCompletion(
                plainRequest,
                AiCompletionResult(
                    descriptorPayload(AiProviderMimeType.PLAIN, 4, 0, charset = "utf-8"),
                    AiFinishReason.STOP,
                ),
                descriptorCount = 0,
                maximumDescriptors = 2,
            )
        }
    }

    @Test
    fun terminalTokenLimitIsProviderEnforcedAndDoesNotRequireUsageReporting() {
        val request = request(
            message = textPayload("Hello"),
            maximumOutputTokens = 2,
        )
        val snapshot = AiProviderQuotaPolicy.validateCompletion(
            request,
            AiCompletionResult(textPayload("ok"), AiFinishReason.STOP),
            descriptorCount = 0,
            maximumDescriptors = 0,
        )

        assertEquals("ok", snapshot.outputText)
        assertEquals(null, snapshot.finalUsage)
    }

    @Test
    fun tokenLimitDoesNotRequireProviderUsageCapability() {
        val request = request(
            message = textPayload("Hello"),
            maximumOutputTokens = 64,
        )

        val snapshot = AiProviderQuotaPolicy.validateRequest(
            request = request,
            provider = capabilities(usage = false),
            descriptorCount = 0,
        )

        assertEquals(5L, snapshot.inputBytes)
    }

    @Test
    fun toolDescriptorValidationAlwaysClampsToTheProtocolCeiling() {
        val results = AiToolResultBatch(
            (0 until org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION).map { index ->
                AiToolResult(
                    callId = "call-$index",
                    output = descriptorPayload(AiProviderMimeType.JSON, 2, index),
                )
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderQuotaPolicy.validateToolResults(
                results,
                descriptorCount = results.results.size + 1,
                maximumDescriptors = Int.MAX_VALUE,
            )
        }
    }

    @Test
    fun textVersionMustBeNegotiatedAndThenMatchedExactly() {
        val selected = AiProviderVersionPolicy.negotiate(
            AiProtocolRange(AiProtocolVersion(1, 0), AiProtocolVersion(1, 2)),
        )
        assertEquals(AiProviderProtocol.PROTOCOL_V1_2, selected)
        AiProviderVersionPolicy.requireSelected(AiProviderProtocol.PROTOCOL_V1_2, selected)
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderVersionPolicy.requireSelected(AiProtocolVersion(1, 0), selected)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderVersionPolicy.negotiate(
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
        persistentSession: Boolean = false,
    ) = AiProviderRequest(
        requestId = "request-quota",
        protocolVersion = AiProviderProtocol.PROTOCOL_V1_2,
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
            requiredCapabilityIds = if (persistentSession) {
                listOf(AiProviderCapabilityId.PERSISTENT_SESSION)
            } else {
                emptyList()
            },
            persistentSession = persistentSession,
        ),
        tools = tools,
    )

    private fun toolCallsWithDescriptor(payload: AiPayloadReference) = AiToolCallBatch(
        listOf(AiToolCall("call-1", "lookup", payload)),
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
}
