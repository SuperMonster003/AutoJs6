package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderCodecTest {
    @Test
    fun capabilitiesAndTargetPagesRoundTrip() {
        val capabilities = capabilities(persistent = true)
        val page = AiTargetPage(
            catalogGeneration = "generation-1",
            targets = listOf(
                localTarget(
                    targetId = "fake.local:echo",
                    modelId = "fake.echo",
                    displayName = "Fake echo",
                    capabilityIds = listOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE),
                    maximumContextBytes = 1024,
                    maximumOutputBytes = 2048,
                    backendProfiles = listOf(
                        AiBackendProfileInfo(
                            AiProviderBackendProfile.CPU,
                            AiProviderBackendAvailability.AVAILABLE,
                        ),
                        AiBackendProfileInfo(
                            AiProviderBackendProfile.GPU,
                            AiProviderBackendAvailability.AVAILABLE,
                        ),
                        AiBackendProfileInfo(
                            AiProviderBackendProfile.NPU,
                            AiProviderBackendAvailability.UNAVAILABLE,
                            AiProviderBackendUnavailableReason.NPU_RUNTIME_NOT_PACKAGED,
                        ),
                    ),
                ),
            ),
            nextPageToken = "page-token-2",
        )

        assertEquals(capabilities, AiProviderCodec.decodeCapabilities(AiProviderCodec.encodeCapabilities(capabilities)))
        assertEquals(page, AiProviderCodec.decodeTargetPage(AiProviderCodec.encodeTargetPage(page)))
    }

    @Test
    fun publicTargetsSnapshotAndFreezeEveryCallerOwnedList() {
        val textMimeTypes = mutableListOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON)
        val schemaMimeTypes = mutableListOf(AiProviderMimeType.JSON)
        val negotiatedCapabilities = capabilities().copy(
            acceptedTextMimeTypes = textMimeTypes,
            acceptedSchemaMimeTypes = schemaMimeTypes,
        )
        val capabilityIds = mutableListOf(AiProviderCapabilityId.STREAMING)
        val backendProfiles = mutableListOf(
            AiBackendProfileInfo(
                AiProviderBackendProfile.CPU,
                AiProviderBackendAvailability.AVAILABLE,
            ),
        )
        val target = localTarget(
            targetId = "fake.local:echo",
            modelId = "fake.echo",
            displayName = "Fake echo",
            capabilityIds = capabilityIds,
            maximumContextBytes = 1024,
            maximumOutputBytes = 2048,
            backendProfiles = backendProfiles,
        )
        val targets = mutableListOf(target)
        val page = AiTargetPage("generation-1", targets)
        val part = AiContentPart(textPayload("Hello"))
        val parts = mutableListOf(part)
        val message = AiMessage(AiMessageRole.USER, parts)
        val requiredCapabilityIds = mutableListOf(AiProviderCapabilityId.STREAMING)
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
        val request = AiProviderRequest(
            requestId = "request-1",
            protocolVersion = AiProtocolVersion(2, 0),
            providerId = "fake.local",
            targetId = "fake.local:echo",
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
        backendProfiles.clear()
        targets.clear()
        parts.clear()
        requiredCapabilityIds.clear()
        messages.clear()
        tools.clear()
        calls.clear()
        results.clear()

        assertEquals(listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON), negotiatedCapabilities.acceptedTextMimeTypes)
        assertEquals(listOf(AiProviderMimeType.JSON), negotiatedCapabilities.acceptedSchemaMimeTypes)
        assertEquals(listOf(AiProviderCapabilityId.STREAMING), target.capabilityIds)
        assertEquals(listOf(AiProviderBackendProfile.CPU), target.backendProfiles.map { it.profileId })
        assertEquals(listOf(target), page.targets)
        assertEquals(listOf(part), message.parts)
        assertEquals(listOf(AiProviderCapabilityId.STREAMING), options.requiredCapabilityIds)
        assertEquals(listOf(message), request.messages)
        assertEquals(listOf(tool), request.tools)
        assertEquals(listOf(call), callBatch.calls)
        assertEquals(listOf(result), resultBatch.results)

        assertEquals(negotiatedCapabilities.acceptedTextMimeTypes, negotiatedCapabilities.component15())
        val (_, componentTargets) = page
        val (_, componentParts) = message
        val (_, _, _, _, _, _, _, _, _, _, componentRequiredCapabilityIds) = options
        val (_, _, _, _, componentMessages, _, componentTools) = request
        val (componentCalls) = callBatch
        val (componentResults) = resultBatch
        assertEquals(page.targets, componentTargets)
        assertEquals(message.parts, componentParts)
        assertEquals(options.requiredCapabilityIds, componentRequiredCapabilityIds)
        assertEquals(request.messages, componentMessages)
        assertEquals(request.tools, componentTools)
        assertEquals(callBatch.calls, componentCalls)
        assertEquals(resultBatch.results, componentResults)

        assertFrozen(negotiatedCapabilities.acceptedTextMimeTypes, AiProviderMimeType.PLAIN)
        assertFrozen(negotiatedCapabilities.acceptedSchemaMimeTypes, AiProviderMimeType.JSON)
        assertFrozen(target.capabilityIds, AiProviderCapabilityId.USAGE)
        assertFrozen(
            target.backendProfiles,
            AiBackendProfileInfo(
                AiProviderBackendProfile.GPU,
                AiProviderBackendAvailability.AVAILABLE,
            ),
        )
        assertFrozen(page.targets, target)
        assertFrozen(message.parts, part)
        assertFrozen(options.requiredCapabilityIds, AiProviderCapabilityId.USAGE)
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
    fun targetPageEnvelopeLimitIsSymmetricAtTheEncodeAndDecodeBoundary() {
        assertEquals(
            AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES,
            AiProviderCodec.requireTargetPageEnvelopeSize(
                ByteArray(AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES),
            ).size,
        )
        assertThrows(IllegalArgumentException::class.java) {
            AiProviderCodec.requireTargetPageEnvelopeSize(
                ByteArray(AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES + 1),
            )
        }

        val maximumLegalPage = AiTargetPage(
            catalogGeneration = "g".repeat(256),
            targets = (0 until AiProviderLimits.MAX_TARGETS_PER_PAGE).map { index ->
                localTarget(
                    targetId = "fake.local:model_$index",
                    modelId = "fake.model_$index",
                    displayName = "d".repeat(256),
                    capabilityIds = AiProviderCapabilityId.VALUES.toList(),
                    maximumContextBytes = AiProviderLimits.MAX_TOTAL_INPUT_BYTES,
                    maximumOutputBytes = AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES,
                )
            },
            nextPageToken = "t".repeat(AiProviderLimits.MAX_TARGET_PAGE_TOKEN_BYTES),
        )
        val encoded = AiProviderCodec.encodeTargetPage(maximumLegalPage)
        assertTrue(encoded.size <= AiProviderLimits.MAX_TARGET_PAGE_ENVELOPE_BYTES)
        assertEquals(maximumLegalPage, AiProviderCodec.decodeTargetPage(encoded))
    }

    @Test
    fun textRequestWithToolsAndDescriptorReferencesRoundTrips() {
        val request = request(
            messagePayload = descriptorPayload(AiProviderMimeType.PLAIN, 128, 0, charset = "utf-8"),
            schemaPayload = descriptorPayload(AiProviderMimeType.JSON, 256, 1),
            responseSchema = descriptorPayload(AiProviderMimeType.JSON, 512, 2),
        )

        assertEquals(request, AiProviderCodec.decodeTextRequest(AiProviderCodec.encodeTextRequest(request)))
    }

    @Test
    fun generationSamplingOptionsRoundTripAndRejectInvalidRanges() {
        val options = AiGenerationOptions(
            stream = true,
            includeReasoning = false,
            structuredJson = false,
            reportUsage = false,
            maximumOutputBytes = 4_096,
            maximumOutputTokens = 512,
            timeoutMillis = 30_000,
            temperature = 0.75,
            topK = 32,
            topP = 0.9,
            persistentSession = true,
            backendProfile = AiProviderBackendProfile.GPU,
        )

        assertEquals(options, AiProviderCodec.decodeGenerationOptions(AiProviderCodec.encodeGenerationOptions(options)))
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                options.copy(temperature = invalid)
            }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                options.copy(topP = invalid)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { options.copy(topK = 0) }
        assertThrows(IllegalArgumentException::class.java) { options.copy(maximumOutputTokens = 0) }
        assertThrows(IllegalArgumentException::class.java) {
            options.copy(maximumOutputTokens = AiProviderLimits.MAX_OUTPUT_TOKENS + 1L)
        }
        assertThrows(IllegalArgumentException::class.java) { options.copy(backendProfile = "unknown") }
        assertThrows(IllegalArgumentException::class.java) {
            AiBackendProfileInfo(
                AiProviderBackendProfile.GPU,
                AiProviderBackendAvailability.AVAILABLE,
                AiProviderBackendUnavailableReason.OPENCL_LIBRARY_UNAVAILABLE,
            )
        }
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

        assertEquals(calls, AiProviderCodec.decodeToolCallBatch(AiProviderCodec.encodeToolCallBatch(calls)))
        assertEquals(results, AiProviderCodec.decodeToolResultBatch(AiProviderCodec.encodeToolResultBatch(results)))
    }

    @Test
    fun sessionChunksAndCompletionRoundTripWithoutNativeRawState() {
        val started = AiSessionStarted(
            requestId = "request-1",
            sessionId = "session-1",
            protocolVersion = AiProtocolVersion(2, 0),
            providerId = "fake.local",
            targetId = "fake.local:echo",
            firstChunkSequence = 5,
            effectiveMaximumOutputBytes = 4096,
            effectiveMaximumToolRounds = 2,
        )
        val chunk = AiProviderChunk(5, textDelta = "Hello", reasoningDelta = "Check")
        val completed = AiCompletionResult(
            output = textPayload("Hello world"),
            finishReason = AiFinishReason.STOP,
            usage = AiUsage(4, 2, 6, 1),
            reasoning = AiReasoningMetadata(textPayload("Short summary"), 1, redacted = false),
        )

        assertEquals(started, AiProviderCodec.decodeSessionStarted(AiProviderCodec.encodeSessionStarted(started)))
        assertEquals(chunk, AiProviderCodec.decodeTextChunk(AiProviderCodec.encodeTextChunk(chunk)))
        assertEquals(completed, AiProviderCodec.decodeCompletionResult(AiProviderCodec.encodeCompletionResult(completed)))
    }

    @Test
    fun futureOptionalRequestFieldIsIgnoredAndFutureRequiredFieldIsRejected() {
        fun encoded(required: Boolean) = TaggedWireWriter(AiProviderProtocol.SCHEMA_TARGET_LIST_REQUEST, 1, 1)
            .int32(1, 2, true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .string(99, "future", required)
            .encode()

        assertEquals(10, AiProviderCodec.decodeTargetListRequest(encoded(false)).pageSize)
        val error = assertThrows(TaggedWireException::class.java) {
            AiProviderCodec.decodeTargetListRequest(encoded(true))
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun incompatibleSchemaMajorIsRejected() {
        val encoded = TaggedWireWriter(AiProviderProtocol.SCHEMA_TARGET_LIST_REQUEST, 2, 0)
            .int32(1, 2, true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .encode()

        val error = assertThrows(TaggedWireException::class.java) {
            AiProviderCodec.decodeTargetListRequest(encoded)
        }
        assertEquals(TaggedWireError.SCHEMA_MISMATCH, error.error)
    }

    @Test
    fun malformedEnvelopeAndKnownFieldTypeMismatchAreRejected() {
        val valid = AiProviderCodec.encodeTargetListRequest(
            AiTargetListRequest(AiProtocolVersion(2, 0), pageSize = 10),
        )
        val malformed = assertThrows(TaggedWireException::class.java) {
            AiProviderCodec.decodeTargetListRequest(valid.copyOf(valid.size - 1))
        }
        assertEquals(TaggedWireError.MALFORMED_HEADER, malformed.error)

        val wrongType = TaggedWireWriter(AiProviderProtocol.SCHEMA_TARGET_LIST_REQUEST, 1, 0)
            .string(1, "one", true)
            .int32(2, 0, true)
            .int32(3, 10, true)
            .encode()
        val typeFailure = assertThrows(TaggedWireException::class.java) {
            AiProviderCodec.decodeTargetListRequest(wrongType)
        }
        assertEquals(TaggedWireError.TYPE_MISMATCH, typeFailure.error)
    }

    private fun request(
        messagePayload: AiPayloadReference = textPayload("Hello"),
        schemaPayload: AiPayloadReference = jsonPayload("{\"type\":\"object\"}"),
        responseSchema: AiPayloadReference? = null,
    ) = AiProviderRequest(
        requestId = "request-1",
        protocolVersion = AiProviderProtocol.PROTOCOL_V2,
        providerId = "fake.local",
        targetId = "fake.local:tools",
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
            responseMimeType = if (responseSchema == null) AiProviderMimeType.PLAIN else AiProviderMimeType.JSON,
            responseSchema = responseSchema,
            requiredCapabilityIds = listOf(AiProviderCapabilityId.STREAMING),
            temperature = 0.75,
            topK = 32,
            topP = 0.9,
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

internal fun localTarget(
    targetId: String = "fake.local:echo",
    modelId: String = "fake.echo",
    displayName: String = "Fake echo",
    capabilityIds: List<String> = emptyList(),
    maximumContextBytes: Long? = 1024,
    maximumOutputBytes: Long? = 1024,
    backendProfiles: List<AiBackendProfileInfo> = emptyList(),
) = AiTargetInfo(
    targetId = targetId,
    providerId = "fake.local",
    profileId = null,
    modelId = modelId,
    displayName = displayName,
    capabilityIds = capabilityIds,
    locality = AiDataLocality.ON_DEVICE,
    credentialMode = AiCredentialMode.NONE,
    availability = AiTargetAvailability.AVAILABLE,
    configured = true,
    maximumContextBytes = maximumContextBytes,
    maximumOutputBytes = maximumOutputBytes,
    supportedControls = if (backendProfiles.isEmpty()) emptyList() else listOf(AiTargetControlId.BACKEND_PROFILE),
    backendProfiles = backendProfiles,
)

internal fun capabilities(
    streaming: Boolean = true,
    reasoning: Boolean = true,
    tools: Boolean = true,
    structuredJson: Boolean = true,
    usage: Boolean = true,
    persistent: Boolean = false,
) = AiProviderCapabilities(
    supportsStreaming = streaming,
    supportsReasoning = reasoning,
    supportsTools = tools,
    supportsStructuredJson = structuredJson,
    supportsUsage = usage,
    supportsPersistentSessions = persistent,
    maximumMessages = AiProviderLimits.MAX_MESSAGES,
    maximumContentParts = AiProviderLimits.MAX_CONTENT_PARTS,
    maximumToolDefinitions = if (tools) AiProviderLimits.MAX_TOOL_DEFINITIONS else 0,
    maximumContextBytes = AiProviderLimits.MAX_TOTAL_INPUT_BYTES,
    maximumOutputBytes = AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES,
    maximumToolRounds = if (tools) AiProviderLimits.MAX_TOOL_ROUNDS else 0,
    maximumOutstandingToolCalls = if (tools) AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS else 0,
    maximumRequestDescriptors = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST,
    maximumSessionDescriptors = org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
    acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
    acceptedSchemaMimeTypes = if (tools || structuredJson) listOf(AiProviderMimeType.JSON) else emptyList(),
)

internal fun textPayload(text: String): AiPayloadReference {
    val bytes = text.toByteArray(Charsets.UTF_8)
    return AiPayloadReference(
        mimeType = AiProviderMimeType.PLAIN,
        declaredLengthBytes = bytes.size.toLong(),
        inlineBytes = bytes,
        charset = "utf-8",
    )
}

internal fun jsonPayload(json: String): AiPayloadReference {
    val bytes = json.toByteArray(Charsets.UTF_8)
    return AiPayloadReference(
        mimeType = AiProviderMimeType.JSON,
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
