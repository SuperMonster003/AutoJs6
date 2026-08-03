package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

/**
 * The host never accepts credential bytes, headers, tokens, or arbitrary provider configuration.
 * This enum is the complete credential surface of a session plan.
 */
internal enum class AiTextCredentialDisposition {
    NONE,
    PROVIDER_PROCESS_ONLY,
}

internal object AiTextCredentialIsolationPolicy {
    fun disposition(pinned: AiTextProviderPinnedIdentity): AiTextCredentialDisposition =
        when (pinned.credentialMode) {
            AiCredentialMode.NONE -> {
                check(pinned.locality == AiDataLocality.ON_DEVICE) {
                    "A credential-free provider must be on-device in protocol V1"
                }
                AiTextCredentialDisposition.NONE
            }
            AiCredentialMode.PLUGIN_MANAGED -> {
                check(pinned.locality != AiDataLocality.ON_DEVICE) {
                    "An on-device provider cannot request plugin-managed remote credentials"
                }
                AiTextCredentialDisposition.PROVIDER_PROCESS_ONLY
            }
            else -> error("Unsupported credential mode")
        }
}

/**
 * Public, credential-free plan for a future transport adapter.
 *
 * Deliberately absent: request bodies, page tokens, tool arguments, arbitrary HTTP headers,
 * provider-native configuration, filesystem paths, and credential material.
 */
internal class AiTextHostSessionPlan(
    val pinnedProvider: AiTextProviderPinnedIdentity,
    val listingGeneration: String,
    val modelId: String,
    modelCapabilityIds: Collection<String>,
    val effectiveMaximumContextBytes: Long,
    val effectiveMaximumOutputBytes: Long,
    val credentialDisposition: AiTextCredentialDisposition,
) {
    val modelCapabilityIds: Set<String> = Collections.unmodifiableSet(modelCapabilityIds.toSortedSet())

    init {
        AiValidation.requireUtf8(listingGeneration, 1, 256, "Model listing generation")
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
        require(this.modelCapabilityIds.all { it in AiTextCapabilityId.VALUES }) {
            "Unknown model capability"
        }
        require(pinnedProvider.negotiatedCapabilities.supportedCapabilityIds().containsAll(this.modelCapabilityIds)) {
            "Model capability exceeds the negotiated provider capabilities"
        }
        require(effectiveMaximumContextBytes > 0L)
        require(effectiveMaximumOutputBytes > 0L)
        require(effectiveMaximumContextBytes <= pinnedProvider.negotiatedCapabilities.maximumContextBytes)
        require(effectiveMaximumOutputBytes <= pinnedProvider.negotiatedCapabilities.maximumOutputBytes)
        require(credentialDisposition == AiTextCredentialIsolationPolicy.disposition(pinnedProvider)) {
            "Session credential disposition does not match the pinned provider"
        }
    }

    override fun toString(): String =
        "AiTextHostSessionPlan(providerId=${pinnedProvider.providerId}, " +
            "component=${pinnedProvider.component.packageName}/${pinnedProvider.component.className}, " +
            "modelId=$modelId, protocolVersion=${pinnedProvider.protocolVersion}, " +
            "credentialDisposition=$credentialDisposition)"
}

internal sealed class AiTextSessionPlanningResult {
    data class Planned(val plan: AiTextHostSessionPlan) : AiTextSessionPlanningResult()
    object ModelCapabilityMismatch : AiTextSessionPlanningResult()
}

internal object AiTextHostSessionPlanner {
    fun plan(
        selected: AiTextProviderSelectionResult.Selected,
        listingGeneration: String,
        model: AiModelInfo,
        requirement: AiTextCapabilityRequirement,
    ): AiTextSessionPlanningResult {
        val pinned = selected.pinned
        if (!requirement.isSatisfiedBy(model, pinned.negotiatedCapabilities)) {
            return AiTextSessionPlanningResult.ModelCapabilityMismatch
        }
        val supportedCapabilities = pinned.negotiatedCapabilities.supportedCapabilityIds()
        if (!supportedCapabilities.containsAll(model.capabilityIds)) {
            return AiTextSessionPlanningResult.ModelCapabilityMismatch
        }
        val modelCapabilities = model.capabilityIds.toSortedSet()
        return AiTextSessionPlanningResult.Planned(
            AiTextHostSessionPlan(
                pinnedProvider = pinned,
                listingGeneration = listingGeneration,
                modelId = model.modelId,
                modelCapabilityIds = modelCapabilities,
                effectiveMaximumContextBytes = minOf(
                    model.maximumContextBytes,
                    pinned.negotiatedCapabilities.maximumContextBytes,
                ),
                effectiveMaximumOutputBytes = minOf(
                    model.maximumOutputBytes,
                    pinned.negotiatedCapabilities.maximumOutputBytes,
                ),
                credentialDisposition = AiTextCredentialIsolationPolicy.disposition(pinned),
            ),
        )
    }
}

/**
 * Key for caching an authenticated provider binding decision, never a generated response.
 * Only stable package identity, protocol, model, and negotiated capability values are accepted.
 */
internal object AiTextProviderBindingCacheKey {
    fun create(plan: AiTextHostSessionPlan): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val pinned = plan.pinnedProvider
        digest.put("ai-text-provider-binding-v1")
        digest.put(pinned.component.packageName)
        digest.put(pinned.component.className)
        digest.put(pinned.packageUid)
        digest.put(pinned.versionCode)
        digest.put(pinned.lastUpdateTime)
        digest.putStrings("provider-signers", pinned.signerSha256)
        digest.put(pinned.providerId)
        digest.put(pinned.implementationVersionName)
        digest.put(pinned.implementationVersionCode)
        digest.put(pinned.locality)
        digest.put(pinned.credentialMode)
        digest.put(pinned.providerProtocolRange.minimum.major)
        digest.put(pinned.providerProtocolRange.minimum.minor)
        digest.put(pinned.providerProtocolRange.maximum.major)
        digest.put(pinned.providerProtocolRange.maximum.minor)
        digest.putStrings("provider-origins", pinned.declaredHttpsOrigins.sorted())
        digest.putStrings("provider-abis", pinned.supportedAbis.sorted())
        digest.put(pinned.minimumHostVersionCode)
        digest.putNullable(pinned.maximumHostVersionCode)
        digest.put(pinned.protocolVersion.major)
        digest.put(pinned.protocolVersion.minor)
        digest.put(plan.listingGeneration)
        digest.put(plan.modelId)
        digest.putStrings("model-capabilities", plan.modelCapabilityIds.sorted())
        digest.put(plan.effectiveMaximumContextBytes)
        digest.put(plan.effectiveMaximumOutputBytes)
        digest.put(plan.credentialDisposition.name)
        digest.putCapabilities(pinned.negotiatedCapabilities)
        return "ai-text-provider-v1-${digest.digest().toHex()}"
    }
}

internal enum class AiTextHostDiagnosticEvent {
    SELECTED,
    DISPATCH_STARTED,
    CANCELLATION_REQUESTED,
    COMPLETED,
    FAILED,
    CANCELLED,
    CLOSED,
}

/**
 * Bounded diagnostic formatter. It cannot receive a request, payload, page token, provider error
 * message, Throwable message, or credential value, so those values cannot be logged accidentally.
 */
internal object AiTextSafeDiagnosticFormatter {
    fun format(event: AiTextHostDiagnosticEvent, plan: AiTextHostSessionPlan): String =
        "ai-text event=${event.name.lowercase()} " +
            "provider=${plan.pinnedProvider.providerId} " +
            "component=${plan.pinnedProvider.component.packageName}/${plan.pinnedProvider.component.className}"
}

private fun MessageDigest.putCapabilities(value: AiTextCapabilities) {
    put(value.supportsStreaming)
    put(value.supportsReasoning)
    put(value.supportsTools)
    put(value.supportsStructuredJson)
    put(value.supportsUsage)
    put(value.maximumMessages)
    put(value.maximumContentParts)
    put(value.maximumToolDefinitions)
    put(value.maximumContextBytes)
    put(value.maximumOutputBytes)
    put(value.maximumToolRounds)
    put(value.maximumOutstandingToolCalls)
    put(value.maximumRequestDescriptors)
    put(value.maximumSessionDescriptors)
    putStrings("accepted-text-mime-types", value.acceptedTextMimeTypes.sorted())
    putStrings("accepted-schema-mime-types", value.acceptedSchemaMimeTypes.sorted())
}

private fun MessageDigest.putStrings(domain: String, values: Collection<String>) {
    put(domain)
    put(values.size)
    values.forEach(::put)
}

private fun MessageDigest.put(value: Boolean) = update((if (value) 1 else 0).toByte())

private fun MessageDigest.put(value: Int) = update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())

private fun MessageDigest.put(value: Long) = update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())

private fun MessageDigest.putNullable(value: Long?) {
    put(value != null)
    if (value != null) put(value)
}

private fun MessageDigest.put(value: String) {
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    put(bytes.size)
    update(bytes)
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
