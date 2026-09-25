package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiProviderVersionPolicy
import org.autojs.plugin.ai.provider.api.AiTargetAvailability
import org.autojs.plugin.ai.provider.api.AiTargetControlId
import org.autojs.plugin.ai.provider.api.AiTargetInfo

object FakeAiProviderFixture {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val PROVIDER_ID = "fake.local"
    const val PROVIDER_VERSION_NAME = "1.0.0"
    const val PROVIDER_VERSION_CODE = 1L
    const val CATALOG_GENERATION = "fake-targets-v2"
    const val REMOTE_ORIGIN = "https://fake.example.test"
    const val REMOTE_PROFILE_ID = "remote-fixture"
    const val REMOTE_PROVIDER_ID = "fake-remote-service"

    const val MODEL_ECHO = "fake.echo"
    const val MODEL_STREAM = "fake.stream"
    const val MODEL_REASONING = "fake.reasoning"
    const val MODEL_TOOLS = "fake.tools"
    const val MODEL_REMOTE = "fake.remote"
    const val MODEL_HYBRID = "fake.hybrid"
    const val MODEL_UNCONFIGURED = "fake.unconfigured"

    const val TARGET_ECHO = "local:echo"
    const val TARGET_STREAM = "local:stream"
    const val TARGET_REASONING = "local:reasoning"
    const val TARGET_TOOLS = "local:tools"
    const val TARGET_AGENT_TOOLS = "profile:agent-tools"
    const val TARGET_REMOTE = "profile:remote"
    const val TARGET_HYBRID = "profile:hybrid"
    const val TARGET_UNCONFIGURED = "profile:unconfigured"

    val protocolVersion = AiProviderProtocol.PROTOCOL_V2

    val providerInfo = AiProviderInfo(
        providerId = PROVIDER_ID,
        displayName = "AutoJs6 fake target provider",
        implementationVersionName = PROVIDER_VERSION_NAME,
        implementationVersionCode = PROVIDER_VERSION_CODE,
        protocolRange = AiProtocolRange(protocolVersion, protocolVersion),
        locality = AiDataLocality.HYBRID,
        credentialMode = AiCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf(REMOTE_ORIGIN),
        supportedAbis = emptyList(),
        minimumHostVersionCode = 0L,
    )

    val capabilities = AiProviderCapabilities(
        supportsStreaming = true,
        supportsReasoning = true,
        supportsTools = true,
        supportsStructuredJson = true,
        supportsUsage = true,
        maximumMessages = 32,
        maximumContentParts = 64,
        maximumToolDefinitions = 16,
        maximumContextBytes = 256L * 1024L,
        maximumOutputBytes = 48L * 1024L,
        maximumToolRounds = 16,
        maximumOutstandingToolCalls = 8,
        maximumRequestDescriptors = 16,
        maximumSessionDescriptors = 32,
        acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
    )

    val publicTargets = listOf(
        localTarget(
            targetId = TARGET_ECHO,
            modelId = MODEL_ECHO,
            name = "Fake echo",
            capabilities = listOf(AiProviderCapabilityId.STRUCTURED_JSON, AiProviderCapabilityId.USAGE),
        ),
        localTarget(
            targetId = TARGET_STREAM,
            modelId = MODEL_STREAM,
            name = "Fake stream",
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.STRUCTURED_JSON,
                AiProviderCapabilityId.USAGE,
            ),
        ),
        localTarget(
            targetId = TARGET_REASONING,
            modelId = MODEL_REASONING,
            name = "Fake reasoning",
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.REASONING,
                AiProviderCapabilityId.USAGE,
            ),
        ),
        localTarget(
            targetId = TARGET_TOOLS,
            modelId = MODEL_TOOLS,
            name = "Fake tools",
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.TOOLS,
                AiProviderCapabilityId.USAGE,
            ),
        ),
        remoteTarget(
            targetId = TARGET_REMOTE,
            modelId = MODEL_REMOTE,
            name = "Fake remote",
            locality = AiDataLocality.REMOTE,
            capabilities = listOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.USAGE),
        ),
        remoteTarget(
            targetId = TARGET_HYBRID,
            modelId = MODEL_HYBRID,
            name = "Fake hybrid",
            locality = AiDataLocality.HYBRID,
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.REASONING,
                AiProviderCapabilityId.USAGE,
            ),
        ),
        remoteTarget(
            targetId = TARGET_UNCONFIGURED,
            modelId = MODEL_UNCONFIGURED,
            name = "Fake unconfigured remote",
            locality = AiDataLocality.REMOTE,
            capabilities = listOf(AiProviderCapabilityId.STREAMING),
            configured = false,
        ),
        remoteTarget(
            targetId = TARGET_AGENT_TOOLS,
            modelId = MODEL_TOOLS,
            name = "Fake Agent tools (no network)",
            locality = AiDataLocality.REMOTE,
            capabilities = listOf(AiProviderCapabilityId.STREAMING, AiProviderCapabilityId.TOOLS, AiProviderCapabilityId.USAGE),
        ),
    )

    fun requireSupportedRequest(request: AiProviderRequest) {
        require(request.providerId == PROVIDER_ID) { "Request targets a different provider" }
        AiProviderVersionPolicy.requireSelected(request.protocolVersion, protocolVersion)
        if (FakeAiHostileScenario.fromTargetId(request.targetId) != null) {
            return
        }
        val target = publicTargets.firstOrNull { it.targetId == request.targetId }
            ?: throw IllegalArgumentException("Unknown fake target")
        require(target.configured && target.available) { "Fake target is not available" }
        val required = requiredCapabilities(request)
        require(required.all { it in target.capabilityIds }) { "Fake target does not support the request" }
        if (request.targetId in setOf(TARGET_TOOLS, TARGET_AGENT_TOOLS)) {
            require(request.tools.isNotEmpty()) { "Fake tools target requires a tool definition" }
            require(request.options.maximumToolRounds > 0) { "Fake tools target requires a tool round" }
        } else {
            require(request.tools.isEmpty()) { "Only the fake tools target accepts tools" }
        }
    }

    private fun requiredCapabilities(request: AiProviderRequest): Set<String> = buildSet {
        addAll(request.options.requiredCapabilityIds)
        if (request.options.stream) add(AiProviderCapabilityId.STREAMING)
        if (request.options.includeReasoning) add(AiProviderCapabilityId.REASONING)
        if (request.options.structuredJson || request.options.responseMimeType == AiProviderMimeType.JSON) {
            add(AiProviderCapabilityId.STRUCTURED_JSON)
        }
        if (request.options.reportUsage) add(AiProviderCapabilityId.USAGE)
        if (request.tools.isNotEmpty() || request.options.maximumToolRounds > 0) add(AiProviderCapabilityId.TOOLS)
    }

    private fun localTarget(
        targetId: String,
        modelId: String,
        name: String,
        capabilities: List<String>,
    ) = target(
        targetId = targetId,
        providerId = PROVIDER_ID,
        profileId = null,
        modelId = modelId,
        name = name,
        capabilities = capabilities,
        locality = AiDataLocality.ON_DEVICE,
        credentialMode = AiCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
    )

    private fun remoteTarget(
        targetId: String,
        modelId: String,
        name: String,
        locality: Int,
        capabilities: List<String>,
        configured: Boolean = true,
    ) = target(
        targetId = targetId,
        providerId = REMOTE_PROVIDER_ID,
        profileId = REMOTE_PROFILE_ID,
        modelId = modelId,
        name = name,
        capabilities = capabilities,
        locality = locality,
        credentialMode = AiCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf(REMOTE_ORIGIN),
        configured = configured,
    )

    private fun target(
        targetId: String,
        providerId: String,
        profileId: String?,
        modelId: String,
        name: String,
        capabilities: List<String>,
        locality: Int,
        credentialMode: Int,
        declaredHttpsOrigins: List<String>,
        configured: Boolean = true,
    ) = AiTargetInfo(
        targetId = targetId,
        providerId = providerId,
        profileId = profileId,
        modelId = modelId,
        displayName = name,
        capabilityIds = capabilities,
        locality = locality,
        credentialMode = credentialMode,
        availability = if (configured) AiTargetAvailability.AVAILABLE else AiTargetAvailability.UNAVAILABLE,
        configured = configured,
        maximumContextBytes = this.capabilities.maximumContextBytes,
        maximumOutputBytes = this.capabilities.maximumOutputBytes,
        supportedControls = buildList {
            add(AiTargetControlId.MAXIMUM_OUTPUT_TOKENS)
            add(AiTargetControlId.TEMPERATURE)
            add(AiTargetControlId.TOP_K)
            add(AiTargetControlId.TOP_P)
            if (AiProviderCapabilityId.STRUCTURED_JSON in capabilities) {
                add(AiTargetControlId.RESPONSE_JSON_SCHEMA)
            }
        },
        declaredHttpsOrigins = declaredHttpsOrigins,
    )
}
