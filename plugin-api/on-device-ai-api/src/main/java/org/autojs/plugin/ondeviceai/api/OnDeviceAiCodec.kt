package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiCommonCodec
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object OnDeviceAiCodec {
    fun encodeCapabilities(value: OnDeviceAiCapabilities): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_CAPABILITIES, 1, 0)
            .boolean(1, value.supportsStreaming, true)
            .boolean(2, value.supportsReasoning, true)
            .boolean(3, value.supportsTools, true)
            .boolean(4, value.supportsStructuredJson, true)
            .boolean(5, value.supportsUsage, true)
            .int32(6, value.maximumMessages, true)
            .int32(7, value.maximumContentParts, true)
            .int32(8, value.maximumToolDefinitions, true)
            .int64(9, value.maximumContextBytes, true)
            .int64(10, value.maximumOutputBytes, true)
            .int32(11, value.maximumToolRounds, true)
            .int32(12, value.maximumOutstandingToolCalls, true)
            .int32(13, value.maximumRequestDescriptors, true)
            .int32(14, value.maximumSessionDescriptors, true)
            .strings(15, value.acceptedTextMimeTypes)
            .strings(16, value.acceptedSchemaMimeTypes)
            .encode()

    fun decodeCapabilities(bytes: ByteArray): OnDeviceAiCapabilities {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_CAPABILITIES, (1..16).toSet(), setOf(15, 16))
        return OnDeviceAiCapabilities(
            supportsStreaming = document.requireBoolean(1),
            supportsReasoning = document.requireBoolean(2),
            supportsTools = document.requireBoolean(3),
            supportsStructuredJson = document.requireBoolean(4),
            supportsUsage = document.requireBoolean(5),
            maximumMessages = document.requireInt32(6),
            maximumContentParts = document.requireInt32(7),
            maximumToolDefinitions = document.requireInt32(8),
            maximumContextBytes = document.requireInt64(9),
            maximumOutputBytes = document.requireInt64(10),
            maximumToolRounds = document.requireInt32(11),
            maximumOutstandingToolCalls = document.requireInt32(12),
            maximumRequestDescriptors = document.requireInt32(13),
            maximumSessionDescriptors = document.requireInt32(14),
            acceptedTextMimeTypes = document.strings(15),
            acceptedSchemaMimeTypes = document.strings(16),
        )
    }

    fun encodeModelListRequest(value: AiModelListRequest): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MODEL_LIST_REQUEST, 1, 0)
            .int32(1, value.protocolVersion.major, true)
            .int32(2, value.protocolVersion.minor, true)
            .int32(3, value.pageSize, true)
            .apply { value.pageToken?.let { string(4, it) } }
            .encode()

    fun decodeModelListRequest(bytes: ByteArray): AiModelListRequest {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_MODEL_LIST_REQUEST, (1..4).toSet())
        return AiModelListRequest(
            protocolVersion = AiProtocolVersion(document.requireInt32(1), document.requireInt32(2)),
            pageSize = document.requireInt32(3),
            pageToken = document.optionalString(4),
        )
    }

    fun encodeModelInfo(value: AiModelInfo): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MODEL_INFO, 1, 0)
            .string(1, value.modelId, true)
            .string(2, value.displayName, true)
            .strings(3, value.capabilityIds)
            .int64(4, value.maximumContextBytes, true)
            .int64(5, value.maximumOutputBytes, true)
            .encode()

    fun decodeModelInfo(bytes: ByteArray): AiModelInfo {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_MODEL_INFO, (1..5).toSet(), setOf(3))
        return AiModelInfo(
            modelId = document.requireString(1),
            displayName = document.requireString(2),
            capabilityIds = document.strings(3),
            maximumContextBytes = document.requireInt64(4),
            maximumOutputBytes = document.requireInt64(5),
        )
    }

    fun encodeModelPage(value: AiModelPage): ByteArray = requireModelPageEnvelopeSize(
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MODEL_PAGE, 1, 0)
            .string(1, value.listingGeneration, true)
            .apply { value.models.forEach { document(2, encodeModelInfo(it)) } }
            .apply { value.nextPageToken?.let { string(3, it) } }
            .encode(),
    )

    fun decodeModelPage(bytes: ByteArray): AiModelPage {
        requireModelPageEnvelopeSize(bytes)
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_MODEL_PAGE, setOf(1, 2, 3), setOf(2))
        return AiModelPage(
            listingGeneration = document.requireString(1),
            models = document.documents(2).map(::decodeModelInfo),
            nextPageToken = document.optionalString(3),
        )
    }

    internal fun requireModelPageEnvelopeSize(bytes: ByteArray): ByteArray = bytes.also {
        require(it.size <= OnDeviceAiLimits.MAX_MODEL_PAGE_ENVELOPE_BYTES) {
            "Model page envelope is too large"
        }
    }

    fun encodeContentPart(value: AiContentPart): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_CONTENT_PART, 1, 0)
            .document(1, AiCommonCodec.encodePayloadReference(value.payload), true)
            .encode()

    fun decodeContentPart(bytes: ByteArray): AiContentPart {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_CONTENT_PART, setOf(1))
        return AiContentPart(AiCommonCodec.decodePayloadReference(document.requireDocument(1)))
    }

    fun encodeMessage(value: AiMessage): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_MESSAGE, 1, 0)
            .int32(1, value.role, true)
            .apply { value.parts.forEach { document(2, encodeContentPart(it), true) } }
            .apply { value.name?.let { string(3, it) } }
            .encode()

    fun decodeMessage(bytes: ByteArray): AiMessage {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_MESSAGE, setOf(1, 2, 3), setOf(2))
        return AiMessage(
            role = document.requireInt32(1),
            parts = document.documents(2).map(::decodeContentPart),
            name = document.optionalString(3),
        )
    }

    fun encodeGenerationOptions(value: AiGenerationOptions): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_GENERATION_OPTIONS, 1, 0)
            .boolean(1, value.stream, true)
            .boolean(2, value.includeReasoning, true)
            .boolean(3, value.structuredJson, true)
            .boolean(4, value.reportUsage, true)
            .int64(5, value.maximumOutputBytes, true)
            .apply { value.maximumOutputTokens?.let { int64(6, it) } }
            .int32(7, value.maximumToolRounds, true)
            .int64(8, value.timeoutMillis, true)
            .string(9, value.responseMimeType, true)
            .apply { value.responseSchema?.let { document(10, AiCommonCodec.encodePayloadReference(it)) } }
            .strings(11, value.requiredCapabilityIds)
            .encode()

    fun decodeGenerationOptions(bytes: ByteArray): AiGenerationOptions {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_GENERATION_OPTIONS, (1..11).toSet(), setOf(11))
        return AiGenerationOptions(
            stream = document.requireBoolean(1),
            includeReasoning = document.requireBoolean(2),
            structuredJson = document.requireBoolean(3),
            reportUsage = document.requireBoolean(4),
            maximumOutputBytes = document.requireInt64(5),
            maximumOutputTokens = document.optionalInt64(6),
            maximumToolRounds = document.requireInt32(7),
            timeoutMillis = document.requireInt64(8),
            responseMimeType = document.requireString(9),
            responseSchema = document.optionalDocument(10)?.let(AiCommonCodec::decodePayloadReference),
            requiredCapabilityIds = document.strings(11),
        )
    }

    fun encodeToolDefinition(value: AiToolDefinition): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TOOL_DEFINITION, 1, 0)
            .string(1, value.name, true)
            .string(2, value.description, true)
            .document(3, AiCommonCodec.encodePayloadReference(value.inputSchema), true)
            .encode()

    fun decodeToolDefinition(bytes: ByteArray): AiToolDefinition {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TOOL_DEFINITION, setOf(1, 2, 3))
        return AiToolDefinition(
            name = document.requireString(1),
            description = document.requireString(2),
            inputSchema = AiCommonCodec.decodePayloadReference(document.requireDocument(3)),
        )
    }

    fun encodeToolCall(value: AiToolCall): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TOOL_CALL, 1, 0)
            .string(1, value.callId, true)
            .string(2, value.name, true)
            .document(3, AiCommonCodec.encodePayloadReference(value.arguments), true)
            .encode()

    fun decodeToolCall(bytes: ByteArray): AiToolCall {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TOOL_CALL, setOf(1, 2, 3))
        return AiToolCall(
            callId = document.requireString(1),
            name = document.requireString(2),
            arguments = AiCommonCodec.decodePayloadReference(document.requireDocument(3)),
        )
    }

    fun encodeToolCallBatch(value: AiToolCallBatch): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TOOL_CALL_BATCH, 1, 0)
            .apply { value.calls.forEach { document(1, encodeToolCall(it), true) } }
            .encode()

    fun decodeToolCallBatch(bytes: ByteArray): AiToolCallBatch {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TOOL_CALL_BATCH, setOf(1), setOf(1))
        return AiToolCallBatch(document.documents(1).map(::decodeToolCall))
    }

    fun encodeToolResult(value: AiToolResult): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TOOL_RESULT, 1, 0)
            .string(1, value.callId, true)
            .document(2, AiCommonCodec.encodePayloadReference(value.output), true)
            .boolean(3, value.isError, true)
            .encode()

    fun decodeToolResult(bytes: ByteArray): AiToolResult {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TOOL_RESULT, setOf(1, 2, 3))
        return AiToolResult(
            callId = document.requireString(1),
            output = AiCommonCodec.decodePayloadReference(document.requireDocument(2)),
            isError = document.requireBoolean(3),
        )
    }

    fun encodeToolResultBatch(value: AiToolResultBatch): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TOOL_RESULT_BATCH, 1, 0)
            .apply { value.results.forEach { document(1, encodeToolResult(it), true) } }
            .encode()

    fun decodeToolResultBatch(bytes: ByteArray): AiToolResultBatch {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TOOL_RESULT_BATCH, setOf(1), setOf(1))
        return AiToolResultBatch(document.documents(1).map(::decodeToolResult))
    }

    fun encodeTextRequest(value: OnDeviceAiRequest): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TEXT_REQUEST, 1, 0)
            .string(1, value.requestId, true)
            .int32(2, value.protocolVersion.major, true)
            .int32(3, value.protocolVersion.minor, true)
            .string(4, value.providerId, true)
            .string(5, value.modelId, true)
            .apply { value.messages.forEach { document(6, encodeMessage(it), true) } }
            .document(7, encodeGenerationOptions(value.options), true)
            .apply { value.tools.forEach { document(8, encodeToolDefinition(it)) } }
            .encode()

    fun decodeTextRequest(bytes: ByteArray): OnDeviceAiRequest {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TEXT_REQUEST, (1..8).toSet(), setOf(6, 8))
        return OnDeviceAiRequest(
            requestId = document.requireString(1),
            protocolVersion = AiProtocolVersion(document.requireInt32(2), document.requireInt32(3)),
            providerId = document.requireString(4),
            modelId = document.requireString(5),
            messages = document.documents(6).map(::decodeMessage),
            options = decodeGenerationOptions(document.requireDocument(7)),
            tools = document.documents(8).map(::decodeToolDefinition),
        )
    }

    fun encodeSessionStarted(value: AiSessionStarted): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_SESSION_STARTED, 1, 0)
            .string(1, value.requestId, true)
            .string(2, value.sessionId, true)
            .int32(3, value.protocolVersion.major, true)
            .int32(4, value.protocolVersion.minor, true)
            .string(5, value.providerId, true)
            .string(6, value.modelId, true)
            .int64(7, value.firstChunkSequence, true)
            .int64(8, value.effectiveMaximumOutputBytes, true)
            .int32(9, value.effectiveMaximumToolRounds, true)
            .encode()

    fun decodeSessionStarted(bytes: ByteArray): AiSessionStarted {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_SESSION_STARTED, (1..9).toSet())
        return AiSessionStarted(
            requestId = document.requireString(1),
            sessionId = document.requireString(2),
            protocolVersion = AiProtocolVersion(document.requireInt32(3), document.requireInt32(4)),
            providerId = document.requireString(5),
            modelId = document.requireString(6),
            firstChunkSequence = document.requireInt64(7),
            effectiveMaximumOutputBytes = document.requireInt64(8),
            effectiveMaximumToolRounds = document.requireInt32(9),
        )
    }

    fun encodeTextChunk(value: OnDeviceAiChunk): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_TEXT_CHUNK, 1, 0)
            .int64(1, value.sequence, true)
            .apply { if (value.textDelta.isNotEmpty()) string(2, value.textDelta) }
            .apply { if (value.reasoningDelta.isNotEmpty()) string(3, value.reasoningDelta) }
            .encode()
            .also { require(it.size <= OnDeviceAiLimits.MAX_CHUNK_ENVELOPE_BYTES) { "Chunk envelope is too large" } }

    fun decodeTextChunk(bytes: ByteArray): OnDeviceAiChunk {
        require(bytes.size <= OnDeviceAiLimits.MAX_CHUNK_ENVELOPE_BYTES) { "Chunk envelope is too large" }
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_TEXT_CHUNK, setOf(1, 2, 3))
        return OnDeviceAiChunk(
            sequence = document.requireInt64(1),
            textDelta = document.optionalString(2).orEmpty(),
            reasoningDelta = document.optionalString(3).orEmpty(),
        )
    }

    fun encodeReasoningMetadata(value: AiReasoningMetadata): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_REASONING_METADATA, 1, 0)
            .apply { value.summary?.let { document(1, AiCommonCodec.encodePayloadReference(it)) } }
            .apply { value.reasoningTokens?.let { int64(2, it) } }
            .boolean(3, value.redacted, true)
            .encode()

    fun decodeReasoningMetadata(bytes: ByteArray): AiReasoningMetadata {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_REASONING_METADATA, setOf(1, 2, 3))
        return AiReasoningMetadata(
            summary = document.optionalDocument(1)?.let(AiCommonCodec::decodePayloadReference),
            reasoningTokens = document.optionalInt64(2),
            redacted = document.requireBoolean(3),
        )
    }

    fun encodeCompletionResult(value: AiCompletionResult): ByteArray =
        TaggedWireWriter(OnDeviceAiProtocol.SCHEMA_COMPLETION_RESULT, 1, 0)
            .document(1, AiCommonCodec.encodePayloadReference(value.output), true)
            .int32(2, value.finishReason, true)
            .apply { value.usage?.let { document(3, AiCommonCodec.encodeUsage(it)) } }
            .apply { value.reasoning?.let { document(4, encodeReasoningMetadata(it)) } }
            .encode()

    fun decodeCompletionResult(bytes: ByteArray): AiCompletionResult {
        val document = document(bytes, OnDeviceAiProtocol.SCHEMA_COMPLETION_RESULT, setOf(1, 2, 3, 4))
        return AiCompletionResult(
            output = AiCommonCodec.decodePayloadReference(document.requireDocument(1)),
            finishReason = document.requireInt32(2),
            usage = document.optionalDocument(3)?.let(AiCommonCodec::decodeUsage),
            reasoning = document.optionalDocument(4)?.let(::decodeReasoningMetadata),
        )
    }

    private fun document(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes)
        .requireSchema(schemaId, 1)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)
}
