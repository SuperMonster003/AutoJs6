package org.autojs.plugin.ai.provider.fake

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.provider.api.AiModelInfo
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiProviderRequest
import org.autojs.plugin.ai.provider.api.AiProviderVersionPolicy

object FakeAiProviderFixture {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val PROVIDER_ID = "fake.local"
    const val PROVIDER_VERSION_NAME = "1.0.0"
    const val PROVIDER_VERSION_CODE = 1L
    const val LISTING_GENERATION = "fake-models-v1"

    const val MODEL_ECHO = "fake.echo"
    const val MODEL_STREAM = "fake.stream"
    const val MODEL_REASONING = "fake.reasoning"
    const val MODEL_TOOLS = "fake.tools"

    val protocolVersion = AiProviderProtocol.PROTOCOL_V1_2

    val providerInfo = AiProviderInfo(
        providerId = PROVIDER_ID,
        displayName = "AutoJs6 fake text provider",
        implementationVersionName = PROVIDER_VERSION_NAME,
        implementationVersionCode = PROVIDER_VERSION_CODE,
        protocolRange = AiProtocolRange(protocolVersion, protocolVersion),
        locality = AiDataLocality.ON_DEVICE,
        credentialMode = AiCredentialMode.NONE,
        declaredHttpsOrigins = emptyList(),
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
        maximumToolRounds = 4,
        maximumOutstandingToolCalls = 8,
        maximumRequestDescriptors = 16,
        maximumSessionDescriptors = 32,
        acceptedTextMimeTypes = listOf(AiProviderMimeType.PLAIN, AiProviderMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(AiProviderMimeType.JSON),
    )

    val publicModels = listOf(
        model(
            id = MODEL_ECHO,
            name = "Fake echo",
            capabilities = listOf(AiProviderCapabilityId.STRUCTURED_JSON, AiProviderCapabilityId.USAGE),
        ),
        model(
            id = MODEL_STREAM,
            name = "Fake stream",
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.STRUCTURED_JSON,
                AiProviderCapabilityId.USAGE,
            ),
        ),
        model(
            id = MODEL_REASONING,
            name = "Fake reasoning",
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.REASONING,
                AiProviderCapabilityId.USAGE,
            ),
        ),
        model(
            id = MODEL_TOOLS,
            name = "Fake tools",
            capabilities = listOf(
                AiProviderCapabilityId.STREAMING,
                AiProviderCapabilityId.TOOLS,
                AiProviderCapabilityId.USAGE,
            ),
        ),
    )

    fun requireSupportedRequest(request: AiProviderRequest) {
        require(request.providerId == PROVIDER_ID) { "Request targets a different provider" }
        AiProviderVersionPolicy.requireSelected(request.protocolVersion, protocolVersion)
        if (FakeAiHostileScenario.fromModelId(request.modelId) != null) {
            return
        }
        val model = publicModels.firstOrNull { it.modelId == request.modelId }
            ?: throw IllegalArgumentException("Unknown fake model")
        val required = requiredCapabilities(request)
        require(required.all { it in model.capabilityIds }) { "Fake model does not support the request" }
        if (request.modelId == MODEL_TOOLS) {
            require(request.tools.isNotEmpty()) { "Fake tools model requires a tool definition" }
            require(request.options.maximumToolRounds > 0) { "Fake tools model requires a tool round" }
        } else {
            require(request.tools.isEmpty()) { "Only the fake tools model accepts tools" }
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

    private fun model(id: String, name: String, capabilities: List<String>) = AiModelInfo(
        modelId = id,
        displayName = name,
        capabilityIds = capabilities,
        maximumContextBytes = this.capabilities.maximumContextBytes,
        maximumOutputBytes = this.capabilities.maximumOutputBytes,
    )
}
