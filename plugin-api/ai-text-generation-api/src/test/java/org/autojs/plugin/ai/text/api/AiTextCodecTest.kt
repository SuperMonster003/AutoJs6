package org.autojs.plugin.ai.text.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextCodecTest {
    @Test
    fun capabilitiesAndModelPagesRoundTrip() {
        val capabilities = capabilities()
        val page = AiModelPage(
            listingGeneration = "generation-1",
            models = listOf(
                AiModelInfo(
                    modelId = "fake.echo",
                    displayName = "Fake echo",
                    capabilityIds = listOf(AiTextCapabilityId.STREAMING, AiTextCapabilityId.USAGE),
                    maximumContextBytes = 1024,
                    maximumOutputBytes = 2048,
                ),
            ),
            nextPageToken = "page-token-2",
        )

        assertEquals(capabilities, AiTextCodec.decodeCapabilities(AiTextCodec.encodeCapabilities(capabilities)))
        assertEquals(page, AiTextCodec.decodeModelPage(AiTextCodec.encodeModelPage(page)))
    }

    @Test
    fun publicModelsSnapshotAndFreezeEveryCallerOwnedList() {
        val textMimeTypes = mutableListOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON)
        val schemaMimeTypes = mutableListOf(AiTextMimeType.JSON)
        val negotiatedCapabilities = capabilities().copy(
            acceptedTextMimeTypes = textMimeTypes,
            acceptedSchemaMimeTypes = schemaMimeTypes,
        )
        val capabilityIds = mutableListOf(AiTextCapabilityId.STREAMING)
        val model = AiModelInfo("fake.echo", "Fake echo", capabilityIds, 1024, 2048)
        val models = mutableListOf(model)
        val page = AiModelPage("generation-1", models)
        val part = AiContentPart(textPayload("Hello"))
        val parts = mutableListOf(part)
        val message = AiMessage(AiMessageRole.USER, parts)
        val requiredCapabilityIds = mutableListOf(AiTextCapabilityId.STREAMING)
        val options = AiGenerationOptions(
            stream = true,
            includeReasoning = false,
            structuredJson = false,
            reportUsage = false,
            maximumOutputBytes = 1024,
            timeoutMillis = 30_000,
            requiredCapabilityIds = requiredCapabilityIds,
        )
        val messages = mutableListOf(message)
        val tool = AiToolDefinition("lookup", "Look up a value", jsonPayload("{\"type\":\"object\"}"))
        val tools = mutableListOf(tool)
        val request = AiTextRequest(
            requestId = "request-1",
            protocolVersion = AiProtocolVersion(1, 0),
            providerId = "fake.local",
            modelId = "fake.echo",
            messages = messages,
            options = options,
            tools = tools,
        )
        val call = AiToolCall("call-1", "lookup", jsonPayload("{}"))
        val calls = mutableListOf(call)
        val callBatch = AiToolCallBatch(calls)
        val result = AiToolResult("call-1", textPayload("done"))
        val results = mutableListOf(result)
        val resultBatch = AiToolResultBatch(results)

        textMimeTypes.clear()
        schemaMimeTypes.clear()
        capabilityIds.clear()
        models.clear()
        parts.clear()
        requiredCapabilityIds.clear()
        messages.clear()
        tools.clear()
        calls.clear()
        results.clear()

        assertEquals(listOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON), negotiatedCapabilities.acceptedTextMimeTypes)
        assertEquals(listOf(AiTextMimeType.JSON), negotiatedCapabilities.acceptedSchemaMimeTypes)
        assertEquals(listOf(AiTextCapabilityId.STREAMING), model.capabilityIds)
        assertEquals(listOf(model), page.models)
        assertEquals(listOf(part), message.parts)
        assertEquals(listOf(AiTextCapabilityId.STREAMING), options.requiredCapabilityIds)
        assertEquals(listOf(message), request.messages)
        assertEquals(listOf(tool), request.tools)
        assertEquals(listOf(call), callBatch.calls)
        assertEquals(listOf(result), resultBatch.results)

        assertEquals(negotiatedCapabilities.acceptedTextMimeTypes, negotiatedCapabilities.component15())
        assertEquals(model.capabilityIds, model.component3())
        val (_, componentModels) = page
        val (_, componentParts) = message
        val (_, _, _, _, _, _, _, _, _, _, componentRequiredCapabilityIds) = options
        val (_, _, _, _, componentMessages, _, componentTools) = request
        val (componentCalls) = callBatch
        val (componentResults) = resultBatch
        assertEquals(page.models, componentModels)
        assertEquals(message.parts, componentParts)
        assertEquals(options.requiredCapabilityIds, componentRequiredCapabilityIds)
        assertEquals(request.messages, componentMessages)
        assertEquals(request.tools, componentTools)
        assertEquals(callBatch.calls, componentCalls)
        assertEquals(resultBatch.results, componentResults)

        assertFrozen(negotiatedCapabilities.acceptedTextMimeTypes, AiTextMimeType.PLAIN)
        assertFrozen(negotiatedCapabilities.acceptedSchemaMimeTypes, AiTextMimeType.JSON)
        assertFrozen(model.capabilityIds, AiTextCapabilityId.USAGE)
        assertFrozen(page.models, model)
        assertFrozen(message.parts, part)
        assertFrozen(options.requiredCapabilityIds, AiTextCapabilityId.USAGE)
        assertFrozen(request.messages, message)
        assertFrozen(request.tools, tool)
        assertFrozen(callBatch.calls, call)
        assertFrozen(resultBatch.results, result)

        assertEquals(request, request.copy())
        assertEquals(request.hashCode(), request.copy().hashCode())
        assertEquals(request.toString(), request.copy().toString())
        assertEquals(callBatch, callBatch.copy())
        assertEquals(resultBatch, resultBatch.copy())
    }

    @Test
    fun modelPageEnvelopeLimitIsSymmetricAtTheEncodeAndDecodeBoundary() {
        assertEquals(
            AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES,
            AiTextCodec.requireModelPageEnvelopeSize(
                ByteArray(AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES),
            ).size,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiTextCodec.requireModelPageEnvelopeSize(
                ByteArray(AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES + 1),
            )
        }

        val maximumLegalPage = AiModelPage(
            listingGeneration = "g".repeat(256),
            models = (0 until AiTextLimits.MAX_MODELS_PER_PAGE).map { index ->
                AiModelInfo(
                    modelId = "fake.model_$index",
                    displayName = "d".repeat(256),
                    capabilityIds = AiTextCapabilityId.VALUES.toList(),
                    maximumContextBytes = AiTextLimits.MAX_TOTAL_INPUT_BYTES,
                    maximumOutputBytes = AiTextLimits.MAX_STREAMING_OUTPUT_BYTES,
                )
            },
            nextPageToken = "t".repeat(AiTextLimits.MAX_MODEL_PAGE_TOKEN_BYTES),
        )
        val encoded = AiTextCodec.encodeModelPage(maximumLegalPage)
        assertTrue(encoded.size <= AiTextLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES)
        assertEquals(maximumLegalPage, AiTextCodec.decodeModelPage(encoded))
    }

    @Test
    fun textRequestWithToolsAndDescriptorReferencesRoundTrips() {
        val request = request(
            messagePayload = descriptorPayload(AiTextMimeType.PLAIN, 128, 0, charset = "utf-8"),
            schemaPayload = descriptorPayload(AiTextMimeType.JSON, 256, 1),
            responseSchema = descriptorPayload(AiTextMimeType.JSON, 512, 2),
        )

        assertEquals(request, AiTextCodec.decodeTextRequest(AiTextCodec.encodeTextRequest(request)))
    }

    @Test
    fun toolCallAndResultBatchesRoundTrip() {
        val calls = AiToolCallBatch(
            listOf(
                AiToolCall("call-1", "lookup", jsonPayload("{\"id\":1}")),
                AiToolCall("call-2", "clock", jsonPayload("{\"zone\":\"UTC\"}")),
            ),
        )
        val results = AiToolResultBatch(
            listOf(
                AiToolResult("call-1", jsonPayload("{\"value\":7}")),
                AiToolResult("call-2", textPayload("12:00"), isError = false),
            ),
        )

        assertEquals(calls, AiTextCodec.decodeToolCallBatch(AiTextCodec.encodeToolCallBatch(calls)))
        assertEquals(results, AiTextCodec.decodeToolResultBatch(AiTextCodec.encodeToolResultBatch(results)))
    }

    @Test
    fun sessionChunksAndCompletionRoundTripWithoutNativeRawState() {
        val started = AiSessionStarted(
            requestId = "request-1",
            sessionId = "session-1",
            protocolVersion = AiProtocolVersion(1, 0),
            providerId = "fake.local",
            modelId = "fake.echo",
            firstChunkSequence = 5,
            effectiveMaximumOutputBytes = 4096,
            effectiveMaximumToolRounds = 2,
        )
        val chunk = AiTextChunk(5, textDelta = "Hello", reasoningDelta = "Check")
        val completed = AiCompletionResult(
            output = textPayload("Hello world"),
            finishReason = AiFinishReason.STOP,
            usage = AiUsage(4, 2, 6, 1),
            reasoning = AiReasoningMetadata(textPayload("Short summary"), 1, redacted = false),
        )

        assertEquals(started, AiTextCodec.decodeSessionStarted(AiTextCodec.encodeSessionStarted(started)))
        assertEquals(chunk, AiTextCodec.decodeTextChunk(AiTextCodec.encodeTextChunk(chunk)))
        assertEquals(completed, AiTextCodec.decodeCompletionResult(AiTextCodec.encodeCompletionResult(completed)))
    }

    @Test
    fun futureOptionalRequestFieldIsIgnoredAndFutureRequiredFieldIsRejected() {
        fun encoded(required: Boolean) = TaggedWireWriter(AiTextProtocol.SCHEMA_MODEL_LIST_REQUEST, 1, 1)
            .int32(1, 1, true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .string(99, "future", required)
            .encode()

        assertEquals(10, AiTextCodec.decodeModelListRequest(encoded(false)).pageSize)
        val error = assertThrows(TaggedWireException::class.java) {
            AiTextCodec.decodeModelListRequest(encoded(true))
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun incompatibleSchemaMajorIsRejected() {
        val encoded = TaggedWireWriter(AiTextProtocol.SCHEMA_MODEL_LIST_REQUEST, 2, 0)
            .int32(1, 2, true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .encode()

        val error = assertThrows(TaggedWireException::class.java) {
            AiTextCodec.decodeModelListRequest(encoded)
        }
        assertEquals(TaggedWireError.SCHEMA_MISMATCH, error.error)
    }

    @Test
    fun malformedEnvelopeAndKnownFieldTypeMismatchAreRejected() {
        val valid = AiTextCodec.encodeModelListRequest(
            AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 10),
        )
        val malformed = assertThrows(TaggedWireException::class.java) {
            AiTextCodec.decodeModelListRequest(valid.copyOf(valid.size - 1))
        }
        assertEquals(TaggedWireError.MALFORMED_HEADER, malformed.error)

        val wrongType = TaggedWireWriter(AiTextProtocol.SCHEMA_MODEL_LIST_REQUEST, 1, 0)
            .string(1, "one", true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .encode()
        val typeFailure = assertThrows(TaggedWireException::class.java) {
            AiTextCodec.decodeModelListRequest(wrongType)
        }
        assertEquals(TaggedWireError.TYPE_MISMATCH, typeFailure.error)
    }

    private fun request(
        messagePayload: AiPayloadReference = textPayload("Hello"),
        schemaPayload: AiPayloadReference = jsonPayload("{\"type\":\"object\"}"),
        responseSchema: AiPayloadReference? = null,
    ) = AiTextRequest(
        requestId = "request-1",
        protocolVersion = AiProtocolVersion(1, 0),
        providerId = "fake.local",
        modelId = "fake.tools",
        messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(messagePayload)))),
        options = AiGenerationOptions(
            stream = true,
            includeReasoning = true,
            structuredJson = responseSchema != null,
            reportUsage = true,
            maximumOutputBytes = 4096,
            maximumOutputTokens = 128,
            maximumToolRounds = 2,
            timeoutMillis = 30_000,
            responseMimeType = if (responseSchema == null) AiTextMimeType.PLAIN else AiTextMimeType.JSON,
            responseSchema = responseSchema,
            requiredCapabilityIds = listOf(AiTextCapabilityId.STREAMING),
        ),
        tools = listOf(AiToolDefinition("lookup", "Look up a value", schemaPayload)),
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> assertFrozen(values: List<T>, value: T) {
        assertThrows(UnsupportedOperationException::class.java) {
            (values as MutableList<T>).add(value)
        }
    }
}

internal fun capabilities(
    streaming: Boolean = true,
    reasoning: Boolean = true,
    tools: Boolean = true,
    structuredJson: Boolean = true,
    usage: Boolean = true,
) = AiTextCapabilities(
    supportsStreaming = streaming,
    supportsReasoning = reasoning,
    supportsTools = tools,
    supportsStructuredJson = structuredJson,
    supportsUsage = usage,
    maximumMessages = AiTextLimits.MAX_MESSAGES,
    maximumContentParts = AiTextLimits.MAX_CONTENT_PARTS,
    maximumToolDefinitions = if (tools) AiTextLimits.MAX_TOOL_DEFINITIONS else 0,
    maximumContextBytes = AiTextLimits.MAX_TOTAL_INPUT_BYTES,
    maximumOutputBytes = AiTextLimits.MAX_STREAMING_OUTPUT_BYTES,
    maximumToolRounds = if (tools) AiTextLimits.MAX_TOOL_ROUNDS else 0,
    maximumOutstandingToolCalls = if (tools) AiTextLimits.MAX_OUTSTANDING_TOOL_CALLS else 0,
    maximumRequestDescriptors = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
    maximumSessionDescriptors = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
    acceptedTextMimeTypes = listOf(AiTextMimeType.PLAIN, AiTextMimeType.JSON),
    acceptedSchemaMimeTypes = if (tools || structuredJson) listOf(AiTextMimeType.JSON) else emptyList(),
)

internal fun textPayload(text: String): AiPayloadReference {
    val bytes = text.toByteArray(Charsets.UTF_8)
    return AiPayloadReference(
        mimeType = AiTextMimeType.PLAIN,
        declaredLengthBytes = bytes.size.toLong(),
        inlineBytes = bytes,
        charset = "utf-8",
    )
}

internal fun jsonPayload(json: String): AiPayloadReference {
    val bytes = json.toByteArray(Charsets.UTF_8)
    return AiPayloadReference(
        mimeType = AiTextMimeType.JSON,
        declaredLengthBytes = bytes.size.toLong(),
        inlineBytes = bytes,
    )
}

internal fun descriptorPayload(
    mimeType: String,
    length: Long,
    descriptorIndex: Int,
    charset: String? = null,
) = AiPayloadReference(
    mimeType = mimeType,
    declaredLengthBytes = length,
    descriptorIndex = descriptorIndex,
    charset = charset,
)
