package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol

internal object OnDeviceAiHostTestFixtures {
    const val SIGNER_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val SIGNER_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    fun providerInfo(
        providerId: String = "fixture.provider",
        displayName: String = "Fixture Provider",
        implementationVersionCode: Long = 7L,
        protocolRange: AiProtocolRange = OnDeviceAiProtocol.HOST_PROTOCOL_RANGE,
        locality: Int = AiDataLocality.ON_DEVICE,
        credentialMode: Int = if (locality == AiDataLocality.ON_DEVICE) {
            AiCredentialMode.NONE
        } else {
            AiCredentialMode.PLUGIN_MANAGED
        },
        declaredHttpsOrigins: List<String> = if (locality == AiDataLocality.ON_DEVICE) {
            emptyList()
        } else {
            listOf("https://provider.example")
        },
        supportedAbis: List<String> = emptyList(),
        minimumHostVersionCode: Long = 0L,
        maximumHostVersionCode: Long? = null,
    ) = AiProviderInfo(
        providerId = providerId,
        displayName = displayName,
        implementationVersionName = "1.0.0",
        implementationVersionCode = implementationVersionCode,
        protocolRange = protocolRange,
        locality = locality,
        credentialMode = credentialMode,
        declaredHttpsOrigins = declaredHttpsOrigins,
        supportedAbis = supportedAbis,
        minimumHostVersionCode = minimumHostVersionCode,
        maximumHostVersionCode = maximumHostVersionCode,
    )

    fun capabilities(
        supportsStreaming: Boolean = true,
        supportsReasoning: Boolean = true,
        supportsTools: Boolean = true,
        supportsStructuredJson: Boolean = true,
        supportsUsage: Boolean = true,
        maximumMessages: Int = 64,
        maximumContentParts: Int = 128,
        maximumToolDefinitions: Int = if (supportsTools) 16 else 0,
        maximumContextBytes: Long = 1024L * 1024L,
        maximumOutputBytes: Long = 4L * 1024L * 1024L,
        maximumToolRounds: Int = if (supportsTools) 4 else 0,
        maximumOutstandingToolCalls: Int = if (supportsTools) 8 else 0,
        maximumRequestDescriptors: Int = 8,
        maximumSessionDescriptors: Int = 16,
        acceptedTextMimeTypes: List<String> = listOf(OnDeviceAiMimeType.PLAIN, OnDeviceAiMimeType.JSON),
        acceptedSchemaMimeTypes: List<String> = listOf(OnDeviceAiMimeType.JSON),
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

    fun candidate(
        packageName: String = "example.ai.provider",
        className: String = "OnDeviceAiService",
        action: String = OnDeviceAiProtocol.ACTION_PROVIDER,
        exported: Boolean = true,
        applicationEnabled: Boolean = true,
        serviceEnabled: Boolean = true,
        permission: String? = OnDeviceAiProtocol.PLUGIN_PERMISSION,
        packageUid: Int = 10_001,
        versionCode: Long = 7L,
        lastUpdateTime: Long = 123L,
        signers: Collection<String> = listOf(SIGNER_A),
        trustLevel: OnDeviceAiProviderTrustLevel = OnDeviceAiProviderTrustLevel.OFFICIAL,
        sameSignerAsHost: Boolean = false,
        providerInfo: AiProviderInfo = providerInfo(),
        capabilities: OnDeviceAiCapabilities = capabilities(),
    ) = OnDeviceAiProviderCandidate(
        component = OnDeviceAiProviderComponent(packageName, className),
        declaredAction = action,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = permission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signers,
        trustLevel = trustLevel,
        sameSignerAsHost = sameSignerAsHost,
        providerInfo = providerInfo,
        capabilities = capabilities,
    )

    fun environment(
        hostVersionCode: Long = 6_800L,
        currentAbi: String? = "arm64-v8a",
        releaseBuild: Boolean = true,
        allowUserApprovedProviders: Boolean = false,
    ) = OnDeviceAiProviderEnvironment(
        hostVersionCode = hostVersionCode,
        currentAbi = currentAbi,
        releaseBuild = releaseBuild,
        allowUserApprovedProviders = allowUserApprovedProviders,
    )

    fun unsupportedProtocolRange() = AiProtocolRange(
        minimum = AiProtocolVersion(2, 0),
        maximum = AiProtocolVersion(2, 0),
    )
}
