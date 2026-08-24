package org.autojs.plugin.ai.provider.api

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
class AiProviderCapabilities(
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
    val supportsPersistentSessions: Boolean = false,
) {
    val acceptedTextMimeTypes: List<String> = immutableListSnapshot(acceptedTextMimeTypes)
    val acceptedSchemaMimeTypes: List<String> = immutableListSnapshot(acceptedSchemaMimeTypes)

    init {
        require(maximumMessages in 1..AiProviderLimits.MAX_MESSAGES)
        require(maximumContentParts in 1..AiProviderLimits.MAX_CONTENT_PARTS)
        require(maximumToolDefinitions in 0..AiProviderLimits.MAX_TOOL_DEFINITIONS)
        require(maximumContextBytes in 1L..AiProviderLimits.MAX_TOTAL_INPUT_BYTES)
        require(maximumOutputBytes in 1L..AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES)
        require(maximumToolRounds in 0..AiProviderLimits.MAX_TOOL_ROUNDS)
        require(maximumOutstandingToolCalls in 0..AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS)
        require(maximumRequestDescriptors in 0..AiCommonLimits.MAX_DESCRIPTORS_PER_REQUEST)
        require(
            maximumSessionDescriptors in maximumRequestDescriptors..AiCommonLimits.MAX_DESCRIPTORS_PER_SESSION,
        )
        require(acceptedTextMimeTypes.isNotEmpty())
        require(acceptedTextMimeTypes.distinct().size == acceptedTextMimeTypes.size)
        require(acceptedTextMimeTypes.all { it in AiProviderMimeType.INPUT_VALUES })
        require(acceptedSchemaMimeTypes.distinct().size == acceptedSchemaMimeTypes.size)
        require(acceptedSchemaMimeTypes.all { it in AiProviderMimeType.SCHEMA_VALUES })
        if (supportsTools) {
            require(maximumToolDefinitions > 0 && maximumToolRounds > 0 && maximumOutstandingToolCalls > 0)
            require(AiProviderMimeType.JSON in acceptedSchemaMimeTypes)
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
    operator fun component17() = supportsPersistentSessions

    fun supportedCapabilityIds(): Set<String> = buildSet {
        if (supportsStreaming) add(AiProviderCapabilityId.STREAMING)
        if (supportsReasoning) add(AiProviderCapabilityId.REASONING)
        if (supportsTools) add(AiProviderCapabilityId.TOOLS)
        if (supportsStructuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
        if (supportsUsage) add(AiProviderCapabilityId.USAGE)
        if (supportsPersistentSessions) add(AiProviderCapabilityId.PERSISTENT_SESSION)
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
        supportsPersistentSessions: Boolean = this.supportsPersistentSessions,
    ) = AiProviderCapabilities(
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
        supportsPersistentSessions = supportsPersistentSessions,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is AiProviderCapabilities &&
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
            acceptedSchemaMimeTypes == other.acceptedSchemaMimeTypes &&
            supportsPersistentSessions == other.supportsPersistentSessions

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
        supportsPersistentSessions,
    )

    override fun toString(): String =
        "AiProviderCapabilities(supportsStreaming=$supportsStreaming, supportsReasoning=$supportsReasoning, " +
            "supportsTools=$supportsTools, supportsStructuredJson=$supportsStructuredJson, " +
            "supportsUsage=$supportsUsage, maximumMessages=$maximumMessages, " +
            "maximumContentParts=$maximumContentParts, maximumToolDefinitions=$maximumToolDefinitions, " +
            "maximumContextBytes=$maximumContextBytes, maximumOutputBytes=$maximumOutputBytes, " +
            "maximumToolRounds=$maximumToolRounds, maximumOutstandingToolCalls=$maximumOutstandingToolCalls, " +
            "maximumRequestDescriptors=$maximumRequestDescriptors, " +
            "maximumSessionDescriptors=$maximumSessionDescriptors, " +
            "acceptedTextMimeTypes=$acceptedTextMimeTypes, " +
            "acceptedSchemaMimeTypes=$acceptedSchemaMimeTypes, " +
            "supportsPersistentSessions=$supportsPersistentSessions)"
}

data class AiModelListRequest(
    val protocolVersion: AiProtocolVersion,
    val pageSize: Int,
    val pageToken: String? = null,
) {
    init {
        require(pageSize in 1..AiProviderLimits.MAX_MODELS_PER_PAGE)
        pageToken?.let {
            AiValidation.requireUtf8(it, 1, AiProviderLimits.MAX_MODEL_PAGE_TOKEN_BYTES, "Model page token")
        }
    }

    override fun toString(): String =
        "AiModelListRequest(protocolVersion=$protocolVersion, pageSize=$pageSize, " +
        "pageTokenPresent=${pageToken != null})"
}

data class AiBackendProfileInfo(
    val profileId: String,
    val availability: String,
    val unavailableReason: String? = null,
) {
    init {
        require(profileId in AiProviderBackendProfile.VALUES) { "Unknown backend profile" }
        require(availability in AiProviderBackendAvailability.VALUES) { "Unknown backend availability" }
        if (availability == AiProviderBackendAvailability.AVAILABLE) {
            require(unavailableReason == null) { "An available backend cannot have an unavailable reason" }
        } else {
            require(unavailableReason in AiProviderBackendUnavailableReason.VALUES) {
                "An unavailable backend requires a known reason"
            }
        }
    }
}

class AiModelInfo(
    val modelId: String,
    val displayName: String,
    capabilityIds: List<String>,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
    backendProfiles: List<AiBackendProfileInfo> = listOf(
        AiBackendProfileInfo(
            profileId = AiProviderBackendProfile.CPU,
            availability = AiProviderBackendAvailability.AVAILABLE,
        ),
    ),
) {
    val capabilityIds: List<String> = immutableListSnapshot(capabilityIds)
    val backendProfiles: List<AiBackendProfileInfo> = immutableListSnapshot(backendProfiles)

    init {
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        AiValidation.requireUtf8(displayName, 1, 256, "Model display name")
        require(capabilityIds.distinct().size == capabilityIds.size) { "Duplicate model capability" }
        require(capabilityIds.all { it in AiProviderCapabilityId.VALUES }) { "Unknown model capability" }
        require(maximumContextBytes in 1L..AiProviderLimits.MAX_TOTAL_INPUT_BYTES)
        require(maximumOutputBytes in 1L..AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES)
        require(backendProfiles.isNotEmpty()) { "A model must declare at least one backend profile" }
        require(backendProfiles.map { it.profileId }.distinct().size == backendProfiles.size) {
            "Duplicate model backend profile"
        }
    }

    operator fun component1() = modelId
    operator fun component2() = displayName
    operator fun component3() = capabilityIds
    operator fun component4() = maximumContextBytes
    operator fun component5() = maximumOutputBytes
    operator fun component6() = backendProfiles

    fun copy(
        modelId: String = this.modelId,
        displayName: String = this.displayName,
        capabilityIds: List<String> = this.capabilityIds,
        maximumContextBytes: Long = this.maximumContextBytes,
        maximumOutputBytes: Long = this.maximumOutputBytes,
        backendProfiles: List<AiBackendProfileInfo> = this.backendProfiles,
    ) = AiModelInfo(
        modelId,
        displayName,
        capabilityIds,
        maximumContextBytes,
        maximumOutputBytes,
        backendProfiles,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is AiModelInfo &&
            modelId == other.modelId &&
            displayName == other.displayName &&
            capabilityIds == other.capabilityIds &&
            maximumContextBytes == other.maximumContextBytes &&
            maximumOutputBytes == other.maximumOutputBytes &&
            backendProfiles == other.backendProfiles

    override fun hashCode(): Int =
        dataClassHashCode(
            modelId,
            displayName,
            capabilityIds,
            maximumContextBytes,
            maximumOutputBytes,
            backendProfiles,
        )

    override fun toString(): String =
        "AiModelInfo(modelId=$modelId, displayName=$displayName, capabilityIds=$capabilityIds, " +
            "maximumContextBytes=$maximumContextBytes, maximumOutputBytes=$maximumOutputBytes, " +
            "backendProfiles=$backendProfiles)"
}

class AiModelPage(
    val listingGeneration: String,
    models: List<AiModelInfo>,
    val nextPageToken: String? = null,
) {
    val models: List<AiModelInfo> = immutableListSnapshot(models)

    init {
        AiValidation.requireUtf8(listingGeneration, 1, 256, "Model listing generation")
        require(models.size <= AiProviderLimits.MAX_MODELS_PER_PAGE)
        require(models.map { it.modelId }.distinct().size == models.size) { "Duplicate model ID in page" }
        nextPageToken?.let {
            AiValidation.requireUtf8(it, 1, AiProviderLimits.MAX_MODEL_PAGE_TOKEN_BYTES, "Model page token")
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
        require(payload.mimeType in AiProviderMimeType.INPUT_VALUES) { "Unsupported text content MIME type" }
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
    val responseMimeType: String = AiProviderMimeType.PLAIN,
    val responseSchema: AiPayloadReference? = null,
    requiredCapabilityIds: List<String> = emptyList(),
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val persistentSession: Boolean = false,
    val backendProfile: String = AiProviderBackendProfile.CPU,
) {
    val requiredCapabilityIds: List<String> = immutableListSnapshot(requiredCapabilityIds)

    init {
        require(maximumOutputBytes in 1L..AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES)
        maximumOutputTokens?.let {
            require(it in 1L..AiProviderLimits.MAX_OUTPUT_TOKENS) {
                "Maximum output tokens are outside the supported range"
            }
        }
        temperature?.let {
            require(it.isFinite() && it >= 0.0) { "Temperature must be finite and non-negative" }
        }
        topK?.let { require(it > 0) { "Top K must be positive" } }
        topP?.let {
            require(it.isFinite() && it in 0.0..1.0) { "Top P must be finite and between 0 and 1" }
        }
        require(maximumToolRounds in 0..AiProviderLimits.MAX_TOOL_ROUNDS)
        require(timeoutMillis in AiProviderLimits.MIN_TIMEOUT_MILLIS..AiProviderLimits.MAX_TIMEOUT_MILLIS)
        require(responseMimeType in AiProviderMimeType.INPUT_VALUES)
        require(requiredCapabilityIds.distinct().size == requiredCapabilityIds.size)
        require(requiredCapabilityIds.all { it in AiProviderCapabilityId.VALUES }) { "Unknown required capability" }
        require(backendProfile in AiProviderBackendProfile.VALUES) { "Unknown backend profile" }
        if (structuredJson) {
            require(responseMimeType == AiProviderMimeType.JSON)
        } else {
            require(responseSchema == null) { "A response schema requires structured JSON" }
        }
        responseSchema?.let {
            require(it.mimeType == AiProviderMimeType.JSON)
            requireInlineTextPayload(it)
            require(it.declaredLengthBytes <= AiProviderLimits.MAX_ONE_SCHEMA_BYTES)
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
    operator fun component12() = temperature
    operator fun component13() = topK
    operator fun component14() = topP
    operator fun component15() = persistentSession
    operator fun component16() = backendProfile

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
        temperature: Double? = this.temperature,
        topK: Int? = this.topK,
        topP: Double? = this.topP,
        persistentSession: Boolean = this.persistentSession,
        backendProfile: String = this.backendProfile,
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
        temperature = temperature,
        topK = topK,
        topP = topP,
        persistentSession = persistentSession,
        backendProfile = backendProfile,
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
            requiredCapabilityIds == other.requiredCapabilityIds &&
            temperature == other.temperature &&
            topK == other.topK &&
            topP == other.topP &&
            persistentSession == other.persistentSession &&
            backendProfile == other.backendProfile

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
        temperature,
        topK,
        topP,
        persistentSession,
        backendProfile,
    )

    override fun toString(): String =
        "AiGenerationOptions(stream=$stream, includeReasoning=$includeReasoning, " +
            "structuredJson=$structuredJson, reportUsage=$reportUsage, " +
            "maximumOutputBytes=$maximumOutputBytes, maximumOutputTokens=$maximumOutputTokens, " +
            "temperature=$temperature, topK=$topK, topP=$topP, " +
            "persistentSession=$persistentSession, backendProfile=$backendProfile, " +
            "maximumToolRounds=$maximumToolRounds, timeoutMillis=$timeoutMillis, " +
            "responseMimeType=$responseMimeType, responseSchema=$responseSchema, " +
            "requiredCapabilityIds=$requiredCapabilityIds)"
}

class AiProviderRequest(
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
    ) = AiProviderRequest(requestId, protocolVersion, providerId, modelId, messages, options, tools)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiProviderRequest &&
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
        "AiProviderRequest(requestId=$requestId, protocolVersion=$protocolVersion, providerId=$providerId, " +
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
        require(effectiveMaximumOutputBytes in 1L..AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES)
        require(effectiveMaximumToolRounds in 0..AiProviderLimits.MAX_TOOL_ROUNDS)
    }
}

data class AiProviderChunk(
    val sequence: Long,
    val textDelta: String = "",
    val reasoningDelta: String = "",
) {
    init {
        require(sequence >= 0L)
        val total = AiValidation.utf8Size(textDelta).toLong() + AiValidation.utf8Size(reasoningDelta).toLong()
        require(total <= AiProviderLimits.MAX_CHUNK_TEXT_BYTES) { "Chunk text exceeds the protocol limit" }
        require(textDelta.isNotEmpty() || reasoningDelta.isNotEmpty()) { "An empty chunk is not allowed" }
    }

    override fun toString(): String =
        "AiProviderChunk(sequence=$sequence, textDeltaBytes=${AiValidation.utf8Size(textDelta)}, " +
            "reasoningDeltaBytes=${AiValidation.utf8Size(reasoningDelta)})"
}

data class AiReasoningMetadata(
    val summary: AiPayloadReference? = null,
    val reasoningTokens: Long? = null,
    val redacted: Boolean = false,
) {
    init {
        summary?.let {
            require(it.mimeType == AiProviderMimeType.PLAIN)
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
        require(output.mimeType in AiProviderMimeType.INPUT_VALUES)
        requireInlineTextPayload(output)
        require(finishReason in AiFinishReason.VALUES)
    }
}

internal fun requireTextEncoding(payload: AiPayloadReference) {
    when (payload.mimeType) {
        AiProviderMimeType.PLAIN -> require(payload.charset == "utf-8") {
            "text/plain payloads must explicitly declare UTF-8"
        }
        AiProviderMimeType.JSON -> require(payload.charset == null || payload.charset == "utf-8") {
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
