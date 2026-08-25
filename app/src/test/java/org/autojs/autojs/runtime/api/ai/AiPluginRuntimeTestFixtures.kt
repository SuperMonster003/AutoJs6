package org.autojs.autojs.runtime.api.ai

import org.autojs.autojs.core.plugin.ai.AiPluginAskOutcome
import org.autojs.autojs.core.plugin.ai.AiPluginStreamEvent
import org.autojs.autojs.core.plugin.ai.AiPluginTargetSummary
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.provider.api.AiFinishReason
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiTargetAvailability

internal fun testPluginTarget(
    targetId: String = "local:example-model",
    providerId: String = "example.provider",
    profileId: String? = null,
    modelId: String = "example-model",
) = AiPluginTargetSummary(
    targetId = targetId,
    providerId = providerId,
    profileId = profileId,
    modelId = modelId,
    displayName = "Example model",
    capabilityIds = listOf(
        AiProviderCapabilityId.STREAMING,
        AiProviderCapabilityId.USAGE,
        AiProviderCapabilityId.PERSISTENT_SESSION,
    ),
    locality = AiDataLocality.ON_DEVICE,
    credentialMode = AiCredentialMode.NONE,
    availability = AiTargetAvailability.AVAILABLE,
    configured = true,
    isDefault = true,
    maximumContextBytes = 262_144,
    maximumOutputBytes = 65_536,
    supportedControls = emptyList(),
    declaredHttpsOrigins = emptyList(),
    backendProfiles = emptyList(),
)

internal fun testPluginCompletion(
    text: String,
    usage: AiPluginUsage,
    reasoning: String = "",
    target: AiPluginTargetSummary = testPluginTarget(),
) = AiPluginAskOutcome.Completed(
    text = text,
    reasoning = reasoning,
    usage = usage,
    finishReason = AiFinishReason.STOP,
    target = target,
)

internal fun testPluginStreamCompletion(
    text: String,
    usage: AiPluginUsage,
    reasoning: String = "",
    target: AiPluginTargetSummary = testPluginTarget(),
) = AiPluginStreamEvent.Completed(
    text = text,
    reasoning = reasoning,
    usage = usage,
    finishReason = AiFinishReason.STOP,
    target = target,
)
