package org.autojs.plugin.ondeviceai.api

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

class OnDeviceAiCodecTest {
    @Test
    fun capabilitiesAndModelPagesRoundTrip() {
        val capabilities = capabilities()
        val page = AiModelPage(
            listingGeneration = "generation-1",
            models = listOf(
                AiModelInfo(
                    modelId = "fake.echo",
                    displayName = "Fake echo",
                    capabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING, OnDeviceAiCapabilityId.USAGE),
                    maximumContextBytes = 1024,
                    maximumOutputBytes = 2048,
                ),
            ),
            nextPageToken = "page-token-2",
        )

        assertEquals(capabilities, OnDeviceAiCodec.decodeCapabilities(OnDeviceAiCodec.encodeCapabilities(capabilities)))
        assertEquals(page, OnDeviceAiCodec.decodeModelPage(OnDeviceAiCodec.encodeModelPage(page)))
    }

    @Test
    fun publicModelsSnapshotAndFreezeEveryCallerOwnedList() {
        val textMimeTypes = mutableListOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON)
        val schemaMimeTypes = mutableListOf(OnDeviceAiMimeType.JSON)
        val negotiatedCapabilities = capabilities().copy(
            acceptedTextMimeTypes = textMimeTypes,
            acceptedSchemaMimeTypes = schemaMimeTypes,
        )
        val capabilityIds = mutableListOf(OnDeviceAiCapabilityId.STREAMING)
        val model = AiModelInfo("fake.echo", "Fake echo", capabilityIds, 1024, 2048)
        val models = mutableListOf(model)
        val page = AiModelPage("generation-1", models)
        val part = AiContentPart(textPayload("Hello"))
        val parts = mutableListOf(part)
        val message = AiMessage(AiMessageRole.USER, parts)
        val requiredCapabilityIds = mutableListOf(OnDeviceAiCapabilityId.STREAMING)
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
        val request = OnDeviceAiRequest(
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

        assertEquals(listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON), negotiatedCapabilities.acceptedTextMimeTypes)
        assertEquals(listOf(OnDeviceAiMimeType.JSON), negotiatedCapabilities.acceptedSchemaMimeTypes)
        assertEquals(listOf(OnDeviceAiCapabilityId.STREAMING), model.capabilityIds)
        assertEquals(listOf(model), page.models)
        assertEquals(listOf(part), message.parts)
        assertEquals(listOf(OnDeviceAiCapabilityId.STREAMING), options.requiredCapabilityIds)
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

        assertFrozen(negotiatedCapabilities.acceptedTextMimeTypes, OnDeviceAiMimeType.PLAIN)
        assertFrozen(negotiatedCapabilities.acceptedSchemaMimeTypes, OnDeviceAiMimeType.JSON)
        assertFrozen(model.capabilityIds, OnDeviceAiCapabilityId.USAGE)
        assertFrozen(page.models, model)
        assertFrozen(message.parts, part)
        assertFrozen(options.requiredCapabilityIds, OnDeviceAiCapabilityId.USAGE)
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
            OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES,
            OnDeviceAiCodec.requireModelPageEnvelopeSize(
                ByteArray(OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES),
            ).size,
        )
        assertThrows(IllegalArgumentException::class.java) {
            OnDeviceAiCodec.requireModelPageEnvelopeSize(
                ByteArray(OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES + 1),
            )
        }

        val maximumLegalPage = AiModelPage(
            listingGeneration = "g".repeat(256),
            models = (0 until OnDeviceAiLimits.MAX_MODELS_PER_PAGE).map { index ->
                AiModelInfo(
                    modelId = "fake.model_$index",
                    displayName = "d".repeat(256),
                    capabilityIds = OnDeviceAiCapabilityId.VALUES.toList(),
                    maximumContextBytes = OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES,
                    maximumOutputBytes = OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES,
                )
            },
            nextPageToken = "t".repeat(OnDeviceAiLimits.MAX_MODEL_PAGE_TOKEN_BYTES),
        )
        val encoded = OnDeviceAiCodec.encodeModelPage(maximumLegalPage)
        assertTrue(encoded.size <= OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES)
        assertEquals(maximumLegalPage, OnDeviceAiCodec.decodeModelPage(encoded))
    }

    @Test
    fun textRequestWithToolsAndDescriptorReferencesRoundTrips() {
        val request = request(
            messagePayload = descriptorPayload(OnDeviceAiMimeType.PLAIN, 128, 0, charset = "utf-8"),
            schemaPayload = descriptorPayload(OnDeviceAiMimeType.JSON, 256, 1),
            responseSchema = descriptorPayload(OnDeviceAiMimeType.JSON, 512, 2),
        )

        assertEquals(request, OnDeviceAiCodec.decodeTextRequest(OnDeviceAiCodec.encodeTextRequest(request)))
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

        assertEquals(calls, OnDeviceAiCodec.decodeToolCallBatch(OnDeviceAiCodec.encodeToolCallBatch(calls)))
        assertEquals(results, OnDeviceAiCodec.decodeToolResultBatch(OnDeviceAiCodec.encodeToolResultBatch(results)))
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
        val chunk = OnDeviceAiChunk(5, textDelta = "Hello", reasoningDelta = "Check")
        val completed = AiCompletionResult(
            output = textPayload("Hello world"),
            finishReason = AiFinishReason.STOP,
            usage = AiUsage(4, 2, 6, 1),
            reasoning = AiReasoningMetadata(textPayload("Short summary"), 1, redacted = false),
        )

        assertEquals(started, OnDeviceAiCodec.decodeSessionStarted(OnDeviceAiCodec.encodeSessionStarted(started)))
        assertEquals(chunk, OnDeviceAiCodec.decodeTextChunk(OnDeviceAiCodec.encodeTextChunk(chunk)))
        assertEquals(completed, OnDeviceAiCodec.decodeCompletionResult(OnDeviceAiCodec.encodeCompletionResult(completed)))
    }

    @Test
    fun futureOptionalRequestFieldIsIgnoredAndFutureRequiredFieldIsRejected() {
        fun encoded(required: Boolean) = TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MODEL_LIST_REQUEST, 1, 1)
            .int32(1, 1, true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .string(99, "future", required)
            .encode()

        assertEquals(10, OnDeviceAiCodec.decodeModelListRequest(encoded(false)).pageSize)
        val error = assertThrows(TaggedWireException::class.java) {
            OnDeviceAiCodec.decodeModelListRequest(encoded(true))
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun incompatibleSchemaMajorIsRejected() {
        val encoded = TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MODEL_LIST_REQUEST, 2, 0)
            .int32(1, 2, true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .encode()

        val error = assertThrows(TaggedWireException::class.java) {
            OnDeviceAiCodec.decodeModelListRequest(encoded)
        }
        assertEquals(TaggedWireError.SCHEMA_MISMATCH, error.error)
    }

    @Test
    fun malformedEnvelopeAndKnownFieldTypeMismatchAreRejected() {
        val valid = OnDeviceAiCodec.encodeModelListRequest(
            AiModelListRequest(AiProtocolVersion(1, 0), pageSize = 10),
        )
        val malformed = assertThrows(TaggedWireException::class.java) {
            OnDeviceAiCodec.decodeModelListRequest(valid.copyOf(valid.size - 1))
        }
        assertEquals(TaggedWireError.MALFORMED_HEADER, malformed.error)

        val wrongType = TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MODEL_LIST_REQUEST, 1, 0)
            .string(1, "one", true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .encode()
        val typeFailure = assertThrows(TaggedWireException::class.java) {
            OnDeviceAiCodec.decodeModelListRequest(wrongType)
        }
        assertEquals(TaggedWireError.TYPE_MISMATCH, typeFailure.error)
    }

    private fun request(
        messagePayload: AiPayloadReference = textPayload("Hello"),
        schemaPayload: AiPayloadReference = jsonPayload("{\"type\":\"object\"}"),
        responseSchema: AiPayloadReference? = null,
    ) = OnDeviceAiRequest(
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
            responseMimeType = if (responseSchema == null) OnDeviceAiMimeType.PLAIN else OnDeviceAiMimeType.JSON,
            responseSchema = responseSchema,
            requiredCapabilityIds = listOf(OnDeviceAiCapabilityId.STREAMING),
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
) = OnDeviceAiCapabilities(
    supportsStreaming = streaming,
    supportsReasoning = reasoning,
    supportsTools = tools,
    supportsStructuredJson = structuredJson,
    supportsUsage = usage,
    maximumMessages = OnDeviceAiLimits.MAX_MESSAGES,
    maximumContentParts = OnDeviceAiLimits.MAX_CONTENT_PARTS,
    maximumToolDefinitions = if (tools) OnDeviceAiLimits.MAX_TOOL_DEFINITIONS else 0,
    maximumContextBytes = OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES,
    maximumOutputBytes = OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES,
    maximumToolRounds = if (tools) OnDeviceAiLimits.MAX_TOOL_ROUNDS else 0,
    maximumOutstandingToolCalls = if (tools) OnDeviceAiLimits.MAX_OUTSTANDING_TOOL_CALLS else 0,
    maximumRequestDescriptors = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
    maximumSessionDescriptors = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
    acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
    acceptedSchemaMimeTypes = if (tools || structuredJson) listOf(OnDeviceAiMimeType.JSON) else emptyList(),
)

internal fun textPayload(text: String): AiPayloadReference {
    val bytes = text.toByteArray(Charsets.UTF_8)
    return AiPayloadReference(
        mimeType = OnDeviceAiMimeType.PLAIN,
        declaredLengthBytes = bytes.size.toLong(),
        inlineBytes = bytes,
        charset = "utf-8",
    )
}

internal fun jsonPayload(json: String): AiPayloadReference {
    val bytes = json.toByteArray(Charsets.UTF_8)
    return AiPayloadReference(
        mimeType = OnDeviceAiMimeType.JSON,
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
