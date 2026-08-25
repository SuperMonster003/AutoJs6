package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
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

data class AiTargetListRequest(
    val protocolVersion: AiProtocolVersion,
    val pageSize: Int,
    val pageToken: String? = null,
) {
    init {
        require(pageSize in 1..AiProviderLimits.MAX_TARGETS_PER_PAGE)
        pageToken?.let {
            AiValidation.requireUtf8(it, 1, AiProviderLimits.MAX_TARGET_PAGE_TOKEN_BYTES, "Target page token")
        }
    }

    override fun toString(): String =
        "AiTargetListRequest(protocolVersion=$protocolVersion, pageSize=$pageSize, " +
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

class AiTargetInfo(
    val targetId: String,
    val providerId: String,
    val profileId: String?,
    val modelId: String,
    val displayName: String,
    capabilityIds: List<String>,
    val locality: Int,
    val credentialMode: Int,
    val availability: String,
    val configured: Boolean,
    val maximumContextBytes: Long?,
    val maximumOutputBytes: Long?,
    supportedControls: List<String> = emptyList(),
    declaredHttpsOrigins: List<String> = emptyList(),
    backendProfiles: List<AiBackendProfileInfo> = emptyList(),
) {
    val capabilityIds: List<String> = immutableListSnapshot(capabilityIds)
    val supportedControls: List<String> = immutableListSnapshot(supportedControls)
    val declaredHttpsOrigins: List<String> = immutableListSnapshot(declaredHttpsOrigins)
    val backendProfiles: List<AiBackendProfileInfo> = immutableListSnapshot(backendProfiles)
    val available: Boolean
        get() = availability == AiTargetAvailability.AVAILABLE

    init {
        require(AiValidation.TARGET_ID.matches(targetId)) { "Invalid target ID" }
        AiValidation.requireUtf8(targetId, 3, AiCommonLimits.MAX_TARGET_ID_BYTES, "Target ID")
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid target provider ID" }
        profileId?.let { require(AiValidation.STABLE_ID.matches(it)) { "Invalid target profile ID" } }
        AiValidation.requireSafeText(modelId, 1, AiCommonLimits.MAX_MODEL_ID_BYTES, "Target model ID")
        AiValidation.requireSafeText(displayName, 1, AiCommonLimits.MAX_DISPLAY_NAME_BYTES, "Target display name")
        require(locality in AiDataLocality.VALUES) { "Unknown target locality" }
        require(credentialMode in AiCredentialMode.VALUES) { "Unknown target credential mode" }
        require(capabilityIds.distinct().size == capabilityIds.size) { "Duplicate target capability" }
        require(capabilityIds.all { it in AiProviderCapabilityId.VALUES }) { "Unknown target capability" }
        require(availability in AiTargetAvailability.VALUES) { "Unknown target availability" }
        require(configured || !available) { "An unconfigured target cannot be available" }
        maximumContextBytes?.let { require(it in 1L..AiProviderLimits.MAX_TOTAL_INPUT_BYTES) }
        maximumOutputBytes?.let { require(it in 1L..AiProviderLimits.MAX_STREAMING_OUTPUT_BYTES) }
        require(supportedControls.distinct().size == supportedControls.size) { "Duplicate target control" }
        require(supportedControls.all { it in AiTargetControlId.VALUES }) { "Unknown target control" }
        require(declaredHttpsOrigins.size <= AiCommonLimits.MAX_ORIGINS) { "Too many target origins" }
        require(declaredHttpsOrigins.distinct().size == declaredHttpsOrigins.size) { "Duplicate target origin" }
        declaredHttpsOrigins.forEach(AiValidation::requireHttpsOrigin)
        when (locality) {
            AiDataLocality.ON_DEVICE -> {
                require(profileId == null) { "An on-device target cannot have a remote profile" }
                require(credentialMode == AiCredentialMode.NONE) {
                    "An on-device target cannot require remote credentials"
                }
                require(declaredHttpsOrigins.isEmpty()) { "An on-device target cannot declare remote origins" }
            }
            AiDataLocality.REMOTE,
            AiDataLocality.HYBRID,
            -> {
                require(profileId != null) { "A remote target must reference its plugin-managed profile" }
                require(credentialMode == AiCredentialMode.PLUGIN_MANAGED) {
                    "Remote target credentials must remain plugin-managed"
                }
                require(declaredHttpsOrigins.isNotEmpty()) { "A remote target must declare an HTTPS origin" }
            }
        }
        require(backendProfiles.map { it.profileId }.distinct().size == backendProfiles.size) {
            "Duplicate target backend profile"
        }
        require((AiTargetControlId.BACKEND_PROFILE in supportedControls) == backendProfiles.isNotEmpty()) {
            "Backend-profile controls and declarations must agree"
        }
        if (AiTargetControlId.RESPONSE_JSON_SCHEMA in supportedControls) {
            require(AiProviderCapabilityId.STRUCTURED_JSON in capabilityIds) {
                "JSON Schema control requires structured JSON capability"
            }
        }
    }

    fun copy(
        targetId: String = this.targetId,
        providerId: String = this.providerId,
        profileId: String? = this.profileId,
        modelId: String = this.modelId,
        displayName: String = this.displayName,
        capabilityIds: List<String> = this.capabilityIds,
        locality: Int = this.locality,
        credentialMode: Int = this.credentialMode,
        availability: String = this.availability,
        configured: Boolean = this.configured,
        maximumContextBytes: Long? = this.maximumContextBytes,
        maximumOutputBytes: Long? = this.maximumOutputBytes,
        supportedControls: List<String> = this.supportedControls,
        declaredHttpsOrigins: List<String> = this.declaredHttpsOrigins,
        backendProfiles: List<AiBackendProfileInfo> = this.backendProfiles,
    ) = AiTargetInfo(
        targetId = targetId,
        providerId = providerId,
        profileId = profileId,
        modelId = modelId,
        displayName = displayName,
        capabilityIds = capabilityIds,
        locality = locality,
        credentialMode = credentialMode,
        availability = availability,
        configured = configured,
        maximumContextBytes = maximumContextBytes,
        maximumOutputBytes = maximumOutputBytes,
        supportedControls = supportedControls,
        declaredHttpsOrigins = declaredHttpsOrigins,
        backendProfiles = backendProfiles,
    )

    override fun equals(other: Any?): Boolean =
        this === other || other is AiTargetInfo &&
            targetId == other.targetId &&
            providerId == other.providerId &&
            profileId == other.profileId &&
            modelId == other.modelId &&
            displayName == other.displayName &&
            capabilityIds == other.capabilityIds &&
            locality == other.locality &&
            credentialMode == other.credentialMode &&
            availability == other.availability &&
            configured == other.configured &&
            maximumContextBytes == other.maximumContextBytes &&
            maximumOutputBytes == other.maximumOutputBytes &&
            supportedControls == other.supportedControls &&
            declaredHttpsOrigins == other.declaredHttpsOrigins &&
            backendProfiles == other.backendProfiles

    override fun hashCode(): Int = dataClassHashCode(
        targetId,
        providerId,
        profileId,
        modelId,
        displayName,
        capabilityIds,
        locality,
        credentialMode,
        availability,
        configured,
        maximumContextBytes,
        maximumOutputBytes,
        supportedControls,
        declaredHttpsOrigins,
        backendProfiles,
    )

    override fun toString(): String =
        "AiTargetInfo(targetId=$targetId, providerId=$providerId, profileId=$profileId, " +
            "modelId=$modelId, displayName=$displayName, locality=$locality, " +
            "credentialMode=$credentialMode, capabilityIds=$capabilityIds, availability=$availability, " +
            "configured=$configured, maximumContextBytes=$maximumContextBytes, " +
            "maximumOutputBytes=$maximumOutputBytes, supportedControls=$supportedControls, " +
            "declaredHttpsOrigins=$declaredHttpsOrigins, backendProfiles=$backendProfiles)"
}

class AiTargetPage(
    val catalogGeneration: String,
    targets: List<AiTargetInfo>,
    val nextPageToken: String? = null,
) {
    val targets: List<AiTargetInfo> = immutableListSnapshot(targets)

    init {
        AiValidation.requireUtf8(catalogGeneration, 1, 256, "Target catalog generation")
        require(targets.size <= AiProviderLimits.MAX_TARGETS_PER_PAGE)
        require(targets.map { it.targetId }.distinct().size == targets.size) { "Duplicate target ID in page" }
        nextPageToken?.let {
            AiValidation.requireUtf8(it, 1, AiProviderLimits.MAX_TARGET_PAGE_TOKEN_BYTES, "Target page token")
        }
    }

    operator fun component1() = catalogGeneration
    operator fun component2() = targets
    operator fun component3() = nextPageToken

    fun copy(
        catalogGeneration: String = this.catalogGeneration,
        targets: List<AiTargetInfo> = this.targets,
        nextPageToken: String? = this.nextPageToken,
    ) = AiTargetPage(catalogGeneration, targets, nextPageToken)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiTargetPage &&
            catalogGeneration == other.catalogGeneration &&
            targets == other.targets &&
            nextPageToken == other.nextPageToken

    override fun hashCode(): Int = dataClassHashCode(catalogGeneration, targets, nextPageToken)

    override fun toString(): String =
        "AiTargetPage(targetCount=${targets.size}, nextPageTokenPresent=${nextPageToken != null})"
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
    val backendProfile: String? = null,
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
        backendProfile?.let {
            require(it in AiProviderBackendProfile.VALUES) { "Unknown backend profile" }
        }
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
        backendProfile: String? = this.backendProfile,
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
    val targetId: String,
    messages: List<AiMessage>,
    val options: AiGenerationOptions,
    tools: List<AiToolDefinition> = emptyList(),
) {
    val messages: List<AiMessage> = immutableListSnapshot(messages)
    val tools: List<AiToolDefinition> = immutableListSnapshot(tools)

    init {
        require(AiValidation.STABLE_ID.matches(requestId)) { "Invalid request ID" }
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        require(AiValidation.TARGET_ID.matches(targetId)) { "Invalid target ID" }
        require(messages.isNotEmpty()) { "AI messages must not be empty" }
    }

    operator fun component1() = requestId
    operator fun component2() = protocolVersion
    operator fun component3() = providerId
    operator fun component4() = targetId
    operator fun component5() = messages
    operator fun component6() = options
    operator fun component7() = tools

    fun copy(
        requestId: String = this.requestId,
        protocolVersion: AiProtocolVersion = this.protocolVersion,
        providerId: String = this.providerId,
        targetId: String = this.targetId,
        messages: List<AiMessage> = this.messages,
        options: AiGenerationOptions = this.options,
        tools: List<AiToolDefinition> = this.tools,
    ) = AiProviderRequest(requestId, protocolVersion, providerId, targetId, messages, options, tools)

    override fun equals(other: Any?): Boolean =
        this === other || other is AiProviderRequest &&
            requestId == other.requestId &&
            protocolVersion == other.protocolVersion &&
            providerId == other.providerId &&
            targetId == other.targetId &&
            messages == other.messages &&
            options == other.options &&
            tools == other.tools

    override fun hashCode(): Int =
        dataClassHashCode(requestId, protocolVersion, providerId, targetId, messages, options, tools)

    override fun toString(): String =
        "AiProviderRequest(requestId=$requestId, protocolVersion=$protocolVersion, providerId=$providerId, " +
            "targetId=$targetId, messageCount=${messages.size}, " +
            "contentPartCount=${messages.sumOf { it.parts.size }}, toolCount=${tools.size}, " +
            "stream=${options.stream}, responseMimeType=${options.responseMimeType})"
}

data class AiSessionStarted(
    val requestId: String,
    val sessionId: String,
    val protocolVersion: AiProtocolVersion,
    val providerId: String,
    val targetId: String,
    val firstChunkSequence: Long = 0L,
    val effectiveMaximumOutputBytes: Long,
    val effectiveMaximumToolRounds: Int,
) {
    init {
        listOf(requestId, sessionId, providerId).forEach {
            require(AiValidation.STABLE_ID.matches(it)) { "Invalid session metadata ID" }
        }
        require(AiValidation.TARGET_ID.matches(targetId)) { "Invalid session target ID" }
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
