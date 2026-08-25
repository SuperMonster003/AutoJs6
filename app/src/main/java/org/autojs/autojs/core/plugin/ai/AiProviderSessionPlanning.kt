package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCredentialMode
import org.autojs.plugin.ai.common.api.AiDataLocality
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiTargetInfo
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Collections

/**
 * The host never accepts credential bytes, headers, tokens, or arbitrary provider configuration.
 * This enum is the complete credential surface of a session plan.
 */
internal enum class AiProviderCredentialDisposition {
    NONE,
    PROVIDER_PROCESS_ONLY,
}

internal object AiProviderCredentialIsolationPolicy {
    fun disposition(target: AiTargetInfo): AiProviderCredentialDisposition =
        when (target.credentialMode) {
            AiCredentialMode.NONE -> {
                check(target.locality == AiDataLocality.ON_DEVICE) {
                    "A credential-free target must be on-device"
                }
                AiProviderCredentialDisposition.NONE
            }
            AiCredentialMode.PLUGIN_MANAGED -> {
                check(target.locality != AiDataLocality.ON_DEVICE) {
                    "An on-device target cannot request plugin-managed remote credentials"
                }
                AiProviderCredentialDisposition.PROVIDER_PROCESS_ONLY
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
internal class AiProviderHostSessionPlan(
    val pinnedProvider: AiProviderPinnedIdentity,
    val catalogGeneration: String,
    val targetId: String,
    val profileId: String?,
    val modelId: String,
    val targetLocality: Int,
    val targetCredentialMode: Int,
    targetCapabilityIds: Collection<String>,
    targetDeclaredHttpsOrigins: Collection<String>,
    val effectiveMaximumContextBytes: Long,
    val effectiveMaximumOutputBytes: Long,
    val credentialDisposition: AiProviderCredentialDisposition,
) {
    val targetCapabilityIds: Set<String> = Collections.unmodifiableSet(targetCapabilityIds.toSortedSet())
    val targetDeclaredHttpsOrigins: List<String> = Collections.unmodifiableList(
        targetDeclaredHttpsOrigins.distinct().sorted(),
    )

    init {
        AiValidation.requireUtf8(catalogGeneration, 1, 256, "Target catalog generation")
        require(AiValidation.TARGET_ID.matches(targetId)) { "Invalid target ID" }
        profileId?.let { require(AiValidation.STABLE_ID.matches(it)) { "Invalid target profile ID" } }
        AiValidation.requireSafeText(
            modelId,
            1,
            org.autojs.plugin.ai.common.api.AiCommonLimits.MAX_MODEL_ID_BYTES,
            "Target model ID",
        )
        require(targetLocality in AiDataLocality.VALUES) { "Invalid target locality" }
        require(targetCredentialMode in AiCredentialMode.VALUES) { "Invalid target credential mode" }
        require(this.targetCapabilityIds.all { it in AiProviderCapabilityId.VALUES }) {
            "Unknown target capability"
        }
        require(pinnedProvider.negotiatedCapabilities.supportedCapabilityIds().containsAll(this.targetCapabilityIds)) {
            "Target capability exceeds the negotiated provider capabilities"
        }
        require(this.targetDeclaredHttpsOrigins.all { it in pinnedProvider.declaredHttpsOrigins }) {
            "Target origin exceeds the pinned provider descriptor"
        }
        require(effectiveMaximumContextBytes > 0L)
        require(effectiveMaximumOutputBytes > 0L)
        require(effectiveMaximumContextBytes <= pinnedProvider.negotiatedCapabilities.maximumContextBytes)
        require(effectiveMaximumOutputBytes <= pinnedProvider.negotiatedCapabilities.maximumOutputBytes)
        val expectedDisposition = when (targetCredentialMode) {
            AiCredentialMode.NONE -> AiProviderCredentialDisposition.NONE
            AiCredentialMode.PLUGIN_MANAGED -> AiProviderCredentialDisposition.PROVIDER_PROCESS_ONLY
            else -> error("Unsupported target credential mode")
        }
        require(credentialDisposition == expectedDisposition) {
            "Session credential disposition does not match the selected target"
        }
    }

    override fun toString(): String =
        "AiProviderHostSessionPlan(providerId=${pinnedProvider.providerId}, " +
            "component=${pinnedProvider.component.packageName}/${pinnedProvider.component.className}, " +
            "targetId=$targetId, protocolVersion=${pinnedProvider.protocolVersion}, " +
            "credentialDisposition=$credentialDisposition)"
}

internal sealed class AiProviderSessionPlanningResult {
    data class Planned(val plan: AiProviderHostSessionPlan) : AiProviderSessionPlanningResult()
    object TargetCapabilityMismatch : AiProviderSessionPlanningResult()
}

internal object AiProviderHostSessionPlanner {
    fun plan(
        selected: AiProviderSelectionResult.Selected,
        catalogGeneration: String,
        target: AiTargetInfo,
        requirement: AiProviderCapabilityRequirement,
    ): AiProviderSessionPlanningResult {
        val pinned = selected.pinned
        if (!requirement.isSatisfiedBy(target, pinned.negotiatedCapabilities)) {
            return AiProviderSessionPlanningResult.TargetCapabilityMismatch
        }
        val supportedCapabilities = pinned.negotiatedCapabilities.supportedCapabilityIds()
        if (!supportedCapabilities.containsAll(target.capabilityIds)) {
            return AiProviderSessionPlanningResult.TargetCapabilityMismatch
        }
        val targetCapabilities = target.capabilityIds.toSortedSet()
        return AiProviderSessionPlanningResult.Planned(
            AiProviderHostSessionPlan(
                pinnedProvider = pinned,
                catalogGeneration = catalogGeneration,
                targetId = target.targetId,
                profileId = target.profileId,
                modelId = target.modelId,
                targetLocality = target.locality,
                targetCredentialMode = target.credentialMode,
                targetCapabilityIds = targetCapabilities,
                targetDeclaredHttpsOrigins = target.declaredHttpsOrigins,
                effectiveMaximumContextBytes = minOf(
                    target.maximumContextBytes ?: pinned.negotiatedCapabilities.maximumContextBytes,
                    pinned.negotiatedCapabilities.maximumContextBytes,
                ),
                effectiveMaximumOutputBytes = minOf(
                    target.maximumOutputBytes ?: pinned.negotiatedCapabilities.maximumOutputBytes,
                    pinned.negotiatedCapabilities.maximumOutputBytes,
                ),
                credentialDisposition = AiProviderCredentialIsolationPolicy.disposition(target),
            ),
        )
    }
}

/**
 * Key for caching an authenticated provider binding decision, never a generated response.
 * Only stable package identity, protocol, target, and negotiated capability values are accepted.
 */
internal object AiProviderBindingCacheKey {
    fun create(plan: AiProviderHostSessionPlan): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val pinned = plan.pinnedProvider
        digest.put("ai-provider-binding-v2")
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
        digest.put(plan.catalogGeneration)
        digest.put(plan.targetId)
        digest.putNullable(plan.profileId)
        digest.put(plan.modelId)
        digest.put(plan.targetLocality)
        digest.put(plan.targetCredentialMode)
        digest.putStrings("target-capabilities", plan.targetCapabilityIds.sorted())
        digest.putStrings("target-origins", plan.targetDeclaredHttpsOrigins)
        digest.put(plan.effectiveMaximumContextBytes)
        digest.put(plan.effectiveMaximumOutputBytes)
        digest.put(plan.credentialDisposition.name)
        digest.putCapabilities(pinned.negotiatedCapabilities)
        return "ai-provider-v2-${digest.digest().toHex()}"
    }
}

internal enum class AiProviderHostDiagnosticEvent {
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
internal object AiProviderSafeDiagnosticFormatter {
    fun format(event: AiProviderHostDiagnosticEvent, plan: AiProviderHostSessionPlan): String =
        "ai-provider event=${event.name.lowercase()} " +
            "provider=${plan.pinnedProvider.providerId} " +
            "component=${plan.pinnedProvider.component.packageName}/${plan.pinnedProvider.component.className}"
}

private fun MessageDigest.putCapabilities(value: AiProviderCapabilities) {
    put(value.supportsStreaming)
    put(value.supportsReasoning)
    put(value.supportsTools)
    put(value.supportsStructuredJson)
    put(value.supportsUsage)
    put(value.supportsPersistentSessions)
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

private fun MessageDigest.putNullable(value: String?) {
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
