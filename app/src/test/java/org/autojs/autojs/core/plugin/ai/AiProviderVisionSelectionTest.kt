package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import org.autojs.plugin.ai.provider.api.AiVisionCapabilities
import org.junit.Assert.*
import org.junit.Test

class AiProviderVisionSelectionTest {
    @Test fun legacyProtocolMasksVisionAndNewProtocolStillRequiresAnExactVisionTarget() {
        val caps = AiProviderHostTestFixtures.capabilities().copy(vision = AiVisionCapabilities())
        fun select(modern: Boolean) = AiProviderSelectionPolicy.select(
            candidates = listOf(AiProviderHostTestFixtures.candidate(capabilities = caps,
                providerInfo = if (modern) AiProviderHostTestFixtures.providerInfo(protocolRange = AiProviderProtocol.HOST_PROTOCOL_RANGE)
                    else AiProviderHostTestFixtures.providerInfo())),
            environment = AiProviderHostTestFixtures.environment(), hostCapabilities = caps,
            requirement = AiProviderCapabilityRequirement(), options = AiProviderSelectionOptions(enabled = true)) as AiProviderSelectionResult.Selected
        val legacy = select(false)
        assertNull(legacy.pinned.negotiatedCapabilities.vision)
        val modern = select(true)
        assertNotNull(modern.pinned.negotiatedCapabilities.vision)
        val needsVision = AiProviderCapabilityRequirement(requiredCapabilityIds = setOf(AiProviderCapabilityId.VISION))
        assertFalse(needsVision.isSatisfiedBy(legacy.pinned.negotiatedCapabilities))
        assertFalse(needsVision.isSatisfiedBy(AiProviderHostTestFixtures.target(), modern.pinned.negotiatedCapabilities))
        assertTrue(needsVision.isSatisfiedBy(AiProviderHostTestFixtures.target(capabilityIds = listOf(AiProviderCapabilityId.VISION)),
            modern.pinned.negotiatedCapabilities))
    }
}
