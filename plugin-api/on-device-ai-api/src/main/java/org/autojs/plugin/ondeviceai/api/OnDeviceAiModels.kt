package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.common.api.AiCommonLimits
import java.util.Collections

/**
 * Negotiated provider ceilings. [maximumRequestDescriptors] applies to one request-opening event;
 * [maximumSessionDescriptors] applies to the concurrently open descriptors in one later session
 * method or callback event, not to cumulative descriptor consumption across the session lifetime.
 */
class OnDeviceAiCapabilities(
    val supportsStreaming: Boolean,
    val supportsReasoning: Boolean,
    val supportsTools: Boolean,
    val supportsStructuredJson: Boolean,
    val supportsUsage: Boolean,
    val maximumMessages: Int,
    val maximumContentParts: Int,
    val maximumToolDefinitions: Int,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
    val maximumToolRounds: Int,
    val maximumOutstandingToolCalls: Int,
    val maximumRequestDescriptors: Int,
    val maximumSessionDescriptors: Int,
    acceptedTextMimeTypes: List<String>,
    acceptedSchemaMimeTypes: List<String>,
) {
    val acceptedTextMimeTypes: List<String> = immutableListSnapshot(acceptedTextMimeTypes)
    val acceptedSchemaMimeTypes: List<String> = immutableListSnapshot(acceptedSchemaMimeTypes)

    init {
        require(maximumMessages in 1..OnDeviceAiLimits.MAX_MESSAGES)
        require(maximumContentParts in 1..OnDeviceAiLimits.MAX_CONTENT_PARTS)
        require(maximumToolDefinitions in 0..OnDeviceAiLimits.MAX_TOOL_DEFINITIONS)
        require(maximumContextBytes in 1L..OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES)
        require(maximumOutputBytes in 1L..OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES)
        require(maximumToolRounds in 0..OnDeviceAiLimits.MAX_TOOL_ROUNDS)
        require(maximumOutstandingToolCalls in 0..OnDeviceAiLimits.MAX_OUTSTANDING_TOOL_CALLS)
        require(maximumRequestDescriptors in 0..AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST)
        require(
            maximumSessionDescriptors in maximumRequestDescriptors..AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
        )
        require(acceptedTextMimeTypes.isNotEmpty())
        require(acceptedTextMimeTypes.distinct().size == acceptedTextMimeTypes.size)
        require(acceptedTextMimeTypes.all { it in OnDeviceAiMimeType.INPUT_VALUES })
        require(acceptedSchemaMimeTypes.distinct().size == acceptedSchemaMimeTypes.size)
        require(acceptedSchemaMimeTypes.all { it in OnDeviceAiMimeType.SCHEMA_VALUES })
        if (supportsTools) {
            require(maximumToolDefinitions > 0 && maximumToolRounds > 0 && maximumOutstandingToolCalls > 0)
            require(OnDeviceAiMimeType.JSON in acceptedSchemaMimeTypes)
        }
    }

    operator fun component1() = supportsStreaming
    operator fun component2() = supportsReasoning
    operator fun component3() = supportsTools
    operator fun component4() = supportsStructuredJson
    operator fun component5() = supportsUsage
    operator fun component6() = maximumMessages
    operator fun component7() = maximumContentParts
    operator fun component8() = maximumToolDefinitions
    operator fun component9() = maximumContextBytes
    operator fun component10() = maximumOutputBytes
    operator fun component11() = maximumToolRounds
    operator fun component12() = maximumOutstandingToolCalls
    operator fun component13() = maximumRequestDescriptors
    operator fun component14() = maximumSessionDescriptors
    operator fun component15() = acceptedTextMimeTypes
    operator fun component16() = acceptedSchemaMimeTypes

    fun supportedCapabilityIds(): Set<String> = buildSet {
        if (supportsStreaming) add(OnDeviceAiCapabilityId.STREAMING)
        if (supportsReasoning) add(OnDeviceAiCapabilityId.REASONING)
        if (supportsTools) add(OnDeviceAiCapabilityId.TOOLS)
        if (supportsStructuredJson) add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
        if (supportsUsage) add(OnDeviceAiCapabilityId.USAGE)
    }

    fun copy(
        supportsStreaming: Boolean = this.supportsStreaming,
        supportsReasoning: Boolean = this.supportsReasoning,
        supportsTools: Boolean = this.supportsTools,
        supportsStructuredJson: Boolean = this.supportsStructuredJson,
        supportsUsage: Boolean = this.supportsUsage,
        maximumMessages: Int = this.maximumMessages,
        maximumContentParts: Int = this.maximumContentParts,
        maximumToolDefinitions: Int = this.maximumToolDefinitions,
        maximumContextBytes: Long = this.maximumContextBytes,
        maximumOutputBytes: Long = this.maximumOutputBytes,
        maximumToolRounds: Int = this.maximumToolRounds,
        maximumOutstandingToolCalls: Int = this.maximumOutstandingToolCalls,
        maximumRequestDescriptors: Int = this.maximumRequestDescriptors,
        maximumSessionDescriptors: Int = this.maximumSessionDescriptors,
        acceptedTextMimeTypes: List<String> = this.acceptedTextMimeTypes,
        acceptedSchemaMimeTypes: List<String> = this.acceptedSchemaMimeTypes,
    ) = OnDeviceAiCapabilities(
        supportsStreaming = supportsStreaming,
        supportsReasoning = supportsReasoning,
        supportsTools = supportsTools,
        supportsStructuredJson = supportsStructuredJson,
        supportsUsage = supportsUsage,
        maximumMessages = maximumMessages,
        maximumContentParts = maximumContentParts,
        maximumToolDefinitions = maximumToolDefinitions,
        maximumContextBytes = maximumContextBytes,
        maximumOutputBytes = maximumOutputBytes,
        maximumToolRounds = maximumToolRounds,
        maximumOutstandingToolCalls = maximumOutstandingToolCalls,
        maximumRequestDescriptors = maximumRequestDescriptors,
        maximumSessionDescriptors = maximumSessionDescriptors,
        acceptedTextMimeTypes = acceptedTextMimeTypes,
        acceptedSchemaMimeTypes = acceptedSchemaMimeTypes,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is OnDeviceAiCapabilities &&
            supportsStreaming == other.supportsStreaming &&
            supportsReasoning == other.supportsReasoning &&
            supportsTools == other.supportsTools &&
            supportsStructuredJson == other.supportsStructuredJson &&
            supportsUsage == other.supportsUsage &&
            maximumMessages == other.maximumMessages &&
            maximumContentParts == other.maximumContentParts &&
            maximumToolDefinitions == other.maximumToolDefinitions &&
            maximumContextBytes == other.maximumContextBytes &&
            maximumOutputBytes == other.maximumOutputBytes &&
            maximumToolRounds == other.maximumToolRounds &&
            maximumOutstandingToolCalls == other.maximumOutstandingToolCalls &&
            maximumRequestDescriptors == other.maximumRequestDescriptors &&
            maximumSessionDescriptors == other.maximumSessionDescriptors &&
            acceptedTextMimeTypes == other.acceptedTextMimeTypes &&
            acceptedSchemaMimeTypes == other.acceptedSchemaMimeTypes

    override fun hashCode(): Int = dataClassHashCode(
        supportsStreaming,
        supportsReasoning,
        supportsTools,
        supportsStructuredJson,
        supportsUsage,
        maximumMessages,
        maximumContentParts,
        maximumToolDefinitions,
        maximumContextBytes,
        maximumOutputBytes,
        maximumToolRounds,
        maximumOutstandingToolCalls,
        maximumRequestDescriptors,
        maximumSessionDescriptors,
        acceptedTextMimeTypes,
        acceptedSchemaMimeTypes,
    )

    override fun toString(): String =
        "OnDeviceAiCapabilities(supportsStreaming=$supportsStreaming, supportsReasoning=$supportsReasoning, " +
            "supportsTools=$supportsTools, supportsStructuredJson=$supportsStructuredJson, " +
            "supportsUsage=$supportsUsage, maximumMessages=$maximumMessages, " +
            "maximumContentParts=$maximumContentParts, maximumToolDefinitions=$maximumToolDefinitions, " +
            "maximumContextBytes=$maximumContextBytes, maximumOutputBytes=$maximumOutputBytes, " +
            "maximumToolRounds=$maximumToolRounds, maximumOutstandingToolCalls=$maximumOutstandingToolCalls, " +
            "maximumRequestDescriptors=$maximumRequestDescriptors, " +
            "maximumSessionDescriptors=$maximumSessionDescriptors, " +
            "acceptedTextMimeTypes=$acceptedTextMimeTypes, " +
            "acceptedSchemaMimeTypes=$acceptedSchemaMimeTypes)"
}

data class AiModelListRequest(
    val protocolVersion: AiProtocolVersion,
    val pageSize: Int,
    val pageToken: String? = null,
) {
    init {
        require(pageSize in 1..OnDeviceAiLimits.MAX_MODELS_PER_PAGE)
        pageToken?.let {
            AiValidation.requireUtf8(it, 1, OnDeviceAiLimits.MAX_MODEL_PAGE_TOKEN_BYTES, "Model page token")
        }
    }

    override fun toString(): String =
        "AiModelListRequest(protocolVersion=$protocolVersion, pageSize=$pageSize, " +
            "pageTokenPresent=${pageToken != null})"
}

class AiModelInfo(
    val modelId: String,
    val displayName: String,
    capabilityIds: List<String>,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
) {
    val capabilityIds: List<String> = immutableListSnapshot(capabilityIds)

    init {
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        AiValidation.requireUtf8(displayName, 1, 256, "Model display name")
        require(capabilityIds.distinct().size == capabilityIds.size) { "Duplicate model capability" }
        require(capabilityIds.all { it in OnDeviceAiCapabilityId.VALUES }) { "Unknown model capability" }
        require(maximumContextBytes in 1L..OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES)
        require(maximumOutputBytes in 1L..OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES)
    }

    operator fun component1() = modelId
    operator fun component2() = displayName
    operator fun component3() = capabilityIds
    operator fun component4() = maximumContextBytes
    operator fun component5() = maximumOutputBytes

    fun copy(
        modelId: String = this.modelId,
        displayName: String = this.displayName,
        capabilityIds: List<String> = this.capabilityIds,
        maximumContextBytes: Long = this.maximumContextBytes,
        maximumOutputBytes: Long = this.maximumOutputBytes,
    ) = AiModelInfo(modelId, displayName, capabilityIds, maximumContextBytes, maximumOutputBytes)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiModelInfo &&
            modelId == other.modelId &&
            displayName == other.displayName &&
            capabilityIds == other.capabilityIds &&
            maximumContextBytes == other.maximumContextBytes &&
            maximumOutputBytes == other.maximumOutputBytes

    override fun hashCode(): Int =
        dataClassHashCode(modelId, displayName, capabilityIds, maximumContextBytes, maximumOutputBytes)

    override fun toString(): String =
        "AiModelInfo(modelId=$modelId, displayName=$displayName, capabilityIds=$capabilityIds, " +
            "maximumContextBytes=$maximumContextBytes, maximumOutputBytes=$maximumOutputBytes)"
}

class AiModelPage(
    val listingGeneration: String,
    models: List<AiModelInfo>,
    val nextPageToken: String? = null,
) {
    val models: List<AiModelInfo> = immutableListSnapshot(models)

    init {
        AiValidation.requireUtf8(listingGeneration, 1, 256, "Model listing generation")
        require(models.size <= OnDeviceAiLimits.MAX_MODELS_PER_PAGE)
        require(models.map { it.modelId }.distinct().size == models.size) { "Duplicate model ID in page" }
        nextPageToken?.let {
            AiValidation.requireUtf8(it, 1, OnDeviceAiLimits.MAX_MODEL_PAGE_TOKEN_BYTES, "Model page token")
        }
    }

    operator fun component1() = listingGeneration
    operator fun component2() = models
    operator fun component3() = nextPageToken

    fun copy(
        listingGeneration: String = this.listingGeneration,
        models: List<AiModelInfo> = this.models,
        nextPageToken: String? = this.nextPageToken,
    ) = AiModelPage(listingGeneration, models, nextPageToken)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiModelPage &&
            listingGeneration == other.listingGeneration &&
            models == other.models &&
            nextPageToken == other.nextPageToken

    override fun hashCode(): Int = dataClassHashCode(listingGeneration, models, nextPageToken)

    override fun toString(): String =
        "AiModelPage(modelCount=${models.size}, nextPageTokenPresent=${nextPageToken != null})"
}

data class AiContentPart(
    val payload: AiPayloadReference,
) {
    init {
        require(payload.mimeType in OnDeviceAiMimeType.INPUT_VALUES) { "Unsupported text content MIME type" }
        requireInlineTextPayload(payload)
    }
}

class AiMessage(
    val role: Int,
    parts: List<AiContentPart>,
    val name: String? = null,
) {
    val parts: List<AiContentPart> = immutableListSnapshot(parts)

    init {
        require(role in AiMessageRole.VALUES) { "Unknown message role" }
        require(parts.isNotEmpty()) { "A message must contain at least one content part" }
        name?.let {
            require(AiValidation.STABLE_ID.matches(it)) { "Invalid message name" }
        }
    }

    operator fun component1() = role
    operator fun component2() = parts
    operator fun component3() = name

    fun copy(
        role: Int = this.role,
        parts: List<AiContentPart> = this.parts,
        name: String? = this.name,
    ) = AiMessage(role, parts, name)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiMessage &&
            role == other.role && parts == other.parts && name == other.name

    override fun hashCode(): Int = dataClassHashCode(role, parts, name)

    override fun toString(): String = "AiMessage(role=$role, parts=$parts, name=$name)"
}

class AiGenerationOptions(
    val stream: Boolean,
    val includeReasoning: Boolean,
    val structuredJson: Boolean,
    val reportUsage: Boolean,
    val maximumOutputBytes: Long,
    val maximumOutputTokens: Long? = null,
    val maximumToolRounds: Int = 0,
    val timeoutMillis: Long,
    val responseMimeType: String = OnDeviceAiMimeType.PLAIN,
    val responseSchema: AiPayloadReference? = null,
    requiredCapabilityIds: List<String> = emptyList(),
) {
    val requiredCapabilityIds: List<String> = immutableListSnapshot(requiredCapabilityIds)

    init {
        require(maximumOutputBytes in 1L..OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES)
        maximumOutputTokens?.let { require(it > 0L) }
        require(maximumToolRounds in 0..OnDeviceAiLimits.MAX_TOOL_ROUNDS)
        require(timeoutMillis in OnDeviceAiLimits.MIN_TIMEOUT_MILLIS..OnDeviceAiLimits.MAX_TIMEOUT_MILLIS)
        require(responseMimeType in OnDeviceAiMimeType.INPUT_VALUES)
        require(requiredCapabilityIds.distinct().size == requiredCapabilityIds.size)
        require(requiredCapabilityIds.all { it in OnDeviceAiCapabilityId.VALUES }) { "Unknown required capability" }
        if (structuredJson) {
            require(responseMimeType == OnDeviceAiMimeType.JSON)
        } else {
            require(responseSchema == null) { "A response schema requires structured JSON" }
        }
        responseSchema?.let {
            require(it.mimeType == OnDeviceAiMimeType.JSON)
            requireInlineTextPayload(it)
            require(it.declaredLengthBytes <= OnDeviceAiLimits.MAX_ONE_SCHEMA_BYTES)
        }
    }

    operator fun component1() = stream
    operator fun component2() = includeReasoning
    operator fun component3() = structuredJson
    operator fun component4() = reportUsage
    operator fun component5() = maximumOutputBytes
    operator fun component6() = maximumOutputTokens
    operator fun component7() = maximumToolRounds
    operator fun component8() = timeoutMillis
    operator fun component9() = responseMimeType
    operator fun component10() = responseSchema
    operator fun component11() = requiredCapabilityIds

    fun copy(
        stream: Boolean = this.stream,
        includeReasoning: Boolean = this.includeReasoning,
        structuredJson: Boolean = this.structuredJson,
        reportUsage: Boolean = this.reportUsage,
        maximumOutputBytes: Long = this.maximumOutputBytes,
        maximumOutputTokens: Long? = this.maximumOutputTokens,
        maximumToolRounds: Int = this.maximumToolRounds,
        timeoutMillis: Long = this.timeoutMillis,
        responseMimeType: String = this.responseMimeType,
        responseSchema: AiPayloadReference? = this.responseSchema,
        requiredCapabilityIds: List<String> = this.requiredCapabilityIds,
    ) = AiGenerationOptions(
        stream = stream,
        includeReasoning = includeReasoning,
        structuredJson = structuredJson,
        reportUsage = reportUsage,
        maximumOutputBytes = maximumOutputBytes,
        maximumOutputTokens = maximumOutputTokens,
        maximumToolRounds = maximumToolRounds,
        timeoutMillis = timeoutMillis,
        responseMimeType = responseMimeType,
        responseSchema = responseSchema,
        requiredCapabilityIds = requiredCapabilityIds,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is AiGenerationOptions &&
            stream == other.stream &&
            includeReasoning == other.includeReasoning &&
            structuredJson == other.structuredJson &&
            reportUsage == other.reportUsage &&
            maximumOutputBytes == other.maximumOutputBytes &&
            maximumOutputTokens == other.maximumOutputTokens &&
            maximumToolRounds == other.maximumToolRounds &&
            timeoutMillis == other.timeoutMillis &&
            responseMimeType == other.responseMimeType &&
            responseSchema == other.responseSchema &&
            requiredCapabilityIds == other.requiredCapabilityIds

    override fun hashCode(): Int = dataClassHashCode(
        stream,
        includeReasoning,
        structuredJson,
        reportUsage,
        maximumOutputBytes,
        maximumOutputTokens,
        maximumToolRounds,
        timeoutMillis,
        responseMimeType,
        responseSchema,
        requiredCapabilityIds,
    )

    override fun toString(): String =
        "AiGenerationOptions(stream=$stream, includeReasoning=$includeReasoning, " +
            "structuredJson=$structuredJson, reportUsage=$reportUsage, " +
            "maximumOutputBytes=$maximumOutputBytes, maximumOutputTokens=$maximumOutputTokens, " +
            "maximumToolRounds=$maximumToolRounds, timeoutMillis=$timeoutMillis, " +
            "responseMimeType=$responseMimeType, responseSchema=$responseSchema, " +
            "requiredCapabilityIds=$requiredCapabilityIds)"
}

class OnDeviceAiRequest(
    val requestId: String,
    val protocolVersion: AiProtocolVersion,
    val providerId: String,
    val modelId: String,
    messages: List<AiMessage>,
    val options: AiGenerationOptions,
    tools: List<AiToolDefinition> = emptyList(),
) {
    val messages: List<AiMessage> = immutableListSnapshot(messages)
    val tools: List<AiToolDefinition> = immutableListSnapshot(tools)

    init {
        require(AiValidation.STABLE_ID.matches(requestId)) { "Invalid request ID" }
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        require(messages.isNotEmpty()) { "AI messages must not be empty" }
    }

    operator fun component1() = requestId
    operator fun component2() = protocolVersion
    operator fun component3() = providerId
    operator fun component4() = modelId
    operator fun component5() = messages
    operator fun component6() = options
    operator fun component7() = tools

    fun copy(
        requestId: String = this.requestId,
        protocolVersion: AiProtocolVersion = this.protocolVersion,
        providerId: String = this.providerId,
        modelId: String = this.modelId,
        messages: List<AiMessage> = this.messages,
        options: AiGenerationOptions = this.options,
        tools: List<AiToolDefinition> = this.tools,
    ) = OnDeviceAiRequest(requestId, protocolVersion, providerId, modelId, messages, options, tools)

    override fun equals(other: Any?): Boolean =
        this === other || other is OnDeviceAiRequest &&
            requestId == other.requestId &&
            protocolVersion == other.protocolVersion &&
            providerId == other.providerId &&
            modelId == other.modelId &&
            messages == other.messages &&
            options == other.options &&
            tools == other.tools

    override fun hashCode(): Int =
        dataClassHashCode(requestId, protocolVersion, providerId, modelId, messages, options, tools)

    override fun toString(): String =
        "OnDeviceAiRequest(requestId=$requestId, protocolVersion=$protocolVersion, providerId=$providerId, " +
            "modelId=$modelId, messageCount=${messages.size}, " +
            "contentPartCount=${messages.sumOf { it.parts.size }}, toolCount=${tools.size}, " +
            "stream=${options.stream}, responseMimeType=${options.responseMimeType})"
}

data class AiSessionStarted(
    val requestId: String,
    val sessionId: String,
    val protocolVersion: AiProtocolVersion,
    val providerId: String,
    val modelId: String,
    val firstChunkSequence: Long = 0L,
    val effectiveMaximumOutputBytes: Long,
    val effectiveMaximumToolRounds: Int,
) {
    init {
        listOf(requestId, sessionId, providerId, modelId).forEach {
            require(AiValidation.STABLE_ID.matches(it)) { "Invalid session metadata ID" }
        }
        require(firstChunkSequence >= 0L)
        require(effectiveMaximumOutputBytes in 1L..OnDeviceAiLimits.MAX_STREAMING_OUTPUT_BYTES)
        require(effectiveMaximumToolRounds in 0..OnDeviceAiLimits.MAX_TOOL_ROUNDS)
    }
}

data class OnDeviceAiChunk(
    val sequence: Long,
    val textDelta: String = "",
    val reasoningDelta: String = "",
) {
    init {
        require(sequence >= 0L)
        val total = AiValidation.utf8Size(textDelta).toLong() + AiValidation.utf8Size(reasoningDelta).toLong()
        require(total <= OnDeviceAiLimits.MAX_CHUNK_TEXT_BYTES) { "Chunk text exceeds the protocol limit" }
        require(textDelta.isNotEmpty() || reasoningDelta.isNotEmpty()) { "An empty chunk is not allowed" }
    }

    override fun toString(): String =
        "OnDeviceAiChunk(sequence=$sequence, textDeltaBytes=${AiValidation.utf8Size(textDelta)}, " +
            "reasoningDeltaBytes=${AiValidation.utf8Size(reasoningDelta)})"
}

data class AiReasoningMetadata(
    val summary: AiPayloadReference? = null,
    val reasoningTokens: Long? = null,
    val redacted: Boolean = false,
) {
    init {
        summary?.let {
            require(it.mimeType == OnDeviceAiMimeType.PLAIN)
            requireInlineTextPayload(it)
        }
        reasoningTokens?.let { require(it >= 0L) }
    }
}

data class AiCompletionResult(
    val output: AiPayloadReference,
    val finishReason: Int,
    val usage: AiUsage? = null,
    val reasoning: AiReasoningMetadata? = null,
) {
    init {
        require(output.mimeType in OnDeviceAiMimeType.INPUT_VALUES)
        requireInlineTextPayload(output)
        require(finishReason in AiFinishReason.VALUES)
    }
}

internal fun requireTextEncoding(payload: AiPayloadReference) {
    when (payload.mimeType) {
        OnDeviceAiMimeType.PLAIN -> require(payload.charset == "utf-8") {
            "text/plain payloads must explicitly declare UTF-8"
        }
        OnDeviceAiMimeType.JSON -> require(payload.charset == null || payload.charset == "utf-8") {
            "JSON payload charset must be UTF-8 when declared"
        }
    }
}

private fun <T> immutableListSnapshot(values: List<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun dataClassHashCode(first: Any?, vararg remaining: Any?): Int {
    var result = first?.hashCode() ?: 0
    remaining.forEach { value -> result = 31 * result + (value?.hashCode() ?: 0) }
    return result
}
