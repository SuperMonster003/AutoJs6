package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.junit.Assert.assertThrows
import org.junit.Test

class AiTargetCatalogPolicyTest {
    private val hybridProvider = AiProviderInfo(
        providerId = "fake.local",
        displayName = "Fake provider",
        implementationVersionName = "2.0.0",
        implementationVersionCode = 2,
        protocolRange = AiProtocolRange(AiProviderProtocol.PROTOCOL_V2, AiProviderProtocol.PROTOCOL_V2),
        locality = AiDataLocality.HYBRID,
        credentialMode = AiCredentialMode.PLUGIN_MANAGED,
        declaredHttpsOrigins = listOf("https://allowed.example"),
        supportedAbis = emptyList(),
        minimumHostVersionCode = 0,
    )

    @Test
    fun hybridDescriptorCoversLocalAndDeclaredRemoteTargets() {
        AiTargetCatalogPolicy.requireCompatible(hybridProvider, capabilities(), localTarget())
        AiTargetCatalogPolicy.requireCompatible(hybridProvider, capabilities(), remoteTarget())
    }

    @Test
    fun descriptorAndCapabilityEscalationFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            AiTargetCatalogPolicy.requireCompatible(
                hybridProvider,
                capabilities(),
                remoteTarget().copy(declaredHttpsOrigins = listOf("https://undeclared.example")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTargetCatalogPolicy.requireCompatible(
                hybridProvider,
                capabilities(reasoning = false),
                remoteTarget().copy(capabilityIds = listOf(AiProviderCapabilityId.REASONING)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTargetCatalogPolicy.requireCompatible(
                hybridProvider.copy(locality = AiDataLocality.REMOTE),
                capabilities(),
                localTarget(),
            )
        }
    }

    private fun remoteTarget() = AiTargetInfo(
        targetId = "profile:remote-profile",
        providerId = "openai-compatible",
        profileId = "remote-profile",
        modelId = "remote/model",
        displayName = "Remote target",
        capabilityIds = listOf(AiProviderCapabilityId.STREAMING),
        locality = AiDataLocality.REMOTE,
        credentialMode = AiCredentialMode.PLUGIN_MANAGED,
        availability = AiTargetAvailability.AVAILABLE,
        configured = true,
        maximumContextBytes = 1024,
        maximumOutputBytes = 1024,
        declaredHttpsOrigins = listOf("https://allowed.example"),
    )
}
