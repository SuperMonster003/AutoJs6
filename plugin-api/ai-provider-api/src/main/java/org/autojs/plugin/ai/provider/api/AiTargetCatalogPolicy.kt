package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiProviderInfo

object AiTargetCatalogPolicy {
    fun requireCompatible(
        providerInfo: AiProviderInfo,
        providerCapabilities: AiProviderCapabilities,
        target: AiTargetInfo,
    ) {
        require(localityIsCovered(providerInfo.locality, target.locality)) {
            "Target locality exceeds its provider descriptor"
        }
        if (target.locality == AiDataLocality.ON_DEVICE) {
            require(target.credentialMode == AiCredentialMode.NONE) {
                "An on-device target cannot inherit remote credentials"
            }
        } else {
            require(providerInfo.credentialMode == AiCredentialMode.PLUGIN_MANAGED) {
                "Remote target credentials must remain plugin-managed"
            }
            require(target.declaredHttpsOrigins.all { it in providerInfo.declaredHttpsOrigins }) {
                "Target origin is outside its provider descriptor"
            }
        }
        val providerCapabilityIds = providerCapabilities.capabilityIds()
        require(target.capabilityIds.all { it in providerCapabilityIds }) {
            "Target capability exceeds its provider declaration"
        }
        target.maximumContextBytes?.let {
            require(it <= providerCapabilities.maximumContextBytes) {
                "Target context limit exceeds its provider declaration"
            }
        }
        target.maximumOutputBytes?.let {
            require(it <= providerCapabilities.maximumOutputBytes) {
                "Target output limit exceeds its provider declaration"
            }
        }
    }

    private fun localityIsCovered(providerLocality: Int, targetLocality: Int): Boolean = when (providerLocality) {
        AiDataLocality.ON_DEVICE -> targetLocality == AiDataLocality.ON_DEVICE
        AiDataLocality.REMOTE -> targetLocality == AiDataLocality.REMOTE
        AiDataLocality.HYBRID -> targetLocality in AiDataLocality.VALUES
        else -> false
    }

    private fun AiProviderCapabilities.capabilityIds(): Set<String> = buildSet {
        if (supportsStreaming) add(AiProviderCapabilityId.STREAMING)
        if (supportsReasoning) add(AiProviderCapabilityId.REASONING)
        if (supportsTools) add(AiProviderCapabilityId.TOOLS)
        if (supportsStructuredJson) add(AiProviderCapabilityId.STRUCTURED_JSON)
        if (supportsUsage) add(AiProviderCapabilityId.USAGE)
        if (supportsPersistentSessions) add(AiProviderCapabilityId.PERSISTENT_SESSION)
    }
}
