package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ondeviceai.api.AiModelInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import org.autojs.plugin.ondeviceai.api.OnDeviceAiRequest
import org.autojs.plugin.ondeviceai.api.OnDeviceAiVersionPolicy

object FakeOnDeviceAiFixture {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val PROVIDER_ID = "fake.local"
    const val PROVIDER_VERSION_NAME = "1.0.0"
    const val PROVIDER_VERSION_CODE = 1L
    const val LISTING_GENERATION = "fake-models-v1"

    const val MODEL_ECHO = "fake.echo"
    const val MODEL_STREAM = "fake.stream"
    const val MODEL_REASONING = "fake.reasoning"
    const val MODEL_TOOLS = "fake.tools"

    val protocolVersion = OnDeviceAiProtocol.PROTOCOL_V1_1

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

    val capabilities = OnDeviceAiCapabilities(
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
        acceptedTextMimeTypes = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
        acceptedSchemaMimeTypes = listOf(OnDeviceAiMimeType.JSON),
    )

    val publicModels = listOf(
        model(
            id = MODEL_ECHO,
            name = "Fake echo",
            capabilities = listOf(OnDeviceAiCapabilityId.STRUCTURED_JSON, OnDeviceAiCapabilityId.USAGE),
        ),
        model(
            id = MODEL_STREAM,
            name = "Fake stream",
            capabilities = listOf(
                OnDeviceAiCapabilityId.STREAMING,
                OnDeviceAiCapabilityId.STRUCTURED_JSON,
                OnDeviceAiCapabilityId.USAGE,
            ),
        ),
        model(
            id = MODEL_REASONING,
            name = "Fake reasoning",
            capabilities = listOf(
                OnDeviceAiCapabilityId.STREAMING,
                OnDeviceAiCapabilityId.REASONING,
                OnDeviceAiCapabilityId.USAGE,
            ),
        ),
        model(
            id = MODEL_TOOLS,
            name = "Fake tools",
            capabilities = listOf(
                OnDeviceAiCapabilityId.STREAMING,
                OnDeviceAiCapabilityId.TOOLS,
                OnDeviceAiCapabilityId.USAGE,
            ),
        ),
    )

    fun requireSupportedRequest(request: OnDeviceAiRequest) {
        require(request.providerId == PROVIDER_ID) { "Request targets a different provider" }
        OnDeviceAiVersionPolicy.requireSelected(request.protocolVersion, protocolVersion)
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

    private fun requiredCapabilities(request: OnDeviceAiRequest): Set<String> = buildSet {
        addAll(request.options.requiredCapabilityIds)
        if (request.options.stream) add(OnDeviceAiCapabilityId.STREAMING)
        if (request.options.includeReasoning) add(OnDeviceAiCapabilityId.REASONING)
        if (request.options.structuredJson || request.options.responseMimeType == OnDeviceAiMimeType.JSON) {
            add(OnDeviceAiCapabilityId.STRUCTURED_JSON)
        }
        if (request.options.reportUsage) add(OnDeviceAiCapabilityId.USAGE)
        if (request.tools.isNotEmpty() || request.options.maximumToolRounds > 0) add(OnDeviceAiCapabilityId.TOOLS)
    }

    private fun model(id: String, name: String, capabilities: List<String>) = AiModelInfo(
        modelId = id,
        displayName = name,
        capabilityIds = capabilities,
        maximumContextBytes = this.capabilities.maximumContextBytes,
        maximumOutputBytes = this.capabilities.maximumOutputBytes,
    )
}
