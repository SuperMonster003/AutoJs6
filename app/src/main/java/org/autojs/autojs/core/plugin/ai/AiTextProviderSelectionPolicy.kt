package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiProtocolNegotiator
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.text.api.AiModelInfo
import org.autojs.plugin.ai.text.api.AiTextCapabilities
import org.autojs.plugin.ai.text.api.AiTextCapabilityId
import org.autojs.plugin.ai.text.api.AiTextCapabilityNegotiator
import org.autojs.plugin.ai.text.api.AiTextMimeType
import org.autojs.plugin.ai.text.api.AiTextProtocol
import java.util.Collections

/**
 * Pure policy input produced by a future Android discovery adapter.
 *
 * This layer intentionally does not query PackageManager or bind a service. Every security-relevant
 * package value is copied into the candidate and later pinned before any future Binder dispatch.
 */
internal data class AiTextProviderComponent(
    val packageName: String,
    val className: String,
) {
    init {
        require(packageName.isNotBlank()) { "Provider package name must not be blank" }
        require(className.isNotBlank()) { "Provider service class name must not be blank" }
        require(packageName.none(Character::isISOControl)) { "Provider package name contains control characters" }
        require(className.none(Character::isISOControl)) { "Provider service class name contains control characters" }
    }
}

internal enum class AiTextProviderTrustLevel {
    OFFICIAL,
    USER_APPROVED,
    DEBUG_ONLY,
    UNTRUSTED,
}

internal class AiTextProviderCandidate(
    val component: AiTextProviderComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val trustLevel: AiTextProviderTrustLevel,
    val sameSignerAsHost: Boolean,
    val providerInfo: AiProviderInfo,
    val capabilities: AiTextCapabilities,
) {
    val signerSha256: List<String> = immutableSnapshot(signerSha256)

    fun copy(
        component: AiTextProviderComponent = this.component,
        declaredAction: String = this.declaredAction,
        exported: Boolean = this.exported,
        applicationEnabled: Boolean = this.applicationEnabled,
        serviceEnabled: Boolean = this.serviceEnabled,
        requiredPermission: String? = this.requiredPermission,
        packageUid: Int = this.packageUid,
        versionCode: Long = this.versionCode,
        lastUpdateTime: Long = this.lastUpdateTime,
        signerSha256: Collection<String> = this.signerSha256,
        trustLevel: AiTextProviderTrustLevel = this.trustLevel,
        sameSignerAsHost: Boolean = this.sameSignerAsHost,
        providerInfo: AiProviderInfo = this.providerInfo,
        capabilities: AiTextCapabilities = this.capabilities,
    ) = AiTextProviderCandidate(
        component = component,
        declaredAction = declaredAction,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = requiredPermission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signerSha256,
        trustLevel = trustLevel,
        sameSignerAsHost = sameSignerAsHost,
        providerInfo = providerInfo,
        capabilities = capabilities,
    )
}

internal data class AiTextProviderEnvironment(
    val hostVersionCode: Long,
    val currentAbi: String?,
    val releaseBuild: Boolean,
    val allowUserApprovedProviders: Boolean = false,
) {
    init {
        require(hostVersionCode >= 0L) { "Host version must not be negative" }
        currentAbi?.let { require(it.isNotBlank()) { "Current ABI must not be blank" } }
    }
}

/** The default constructor is the production-safe, disabled state. */
internal data class AiTextProviderSelectionOptions(
    val enabled: Boolean = false,
    val preferredComponent: AiTextProviderComponent? = null,
)

internal class AiTextCapabilityRequirement(
    requiredCapabilityIds: Collection<String> = emptySet(),
    val minimumMessages: Int = 1,
    val minimumContentParts: Int = 1,
    val minimumToolDefinitions: Int = 0,
    val minimumContextBytes: Long = 1L,
    val minimumOutputBytes: Long = 1L,
    val minimumToolRounds: Int = 0,
    val minimumOutstandingToolCalls: Int = 0,
    val minimumRequestDescriptors: Int = 0,
    val minimumSessionDescriptors: Int = 0,
    requiredTextMimeTypes: Collection<String> = emptySet(),
    requiredSchemaMimeTypes: Collection<String> = emptySet(),
) {
    val requiredCapabilityIds: Set<String> = immutableSortedSet(requiredCapabilityIds)
    val requiredTextMimeTypes: Set<String> = immutableSortedSet(requiredTextMimeTypes)
    val requiredSchemaMimeTypes: Set<String> = immutableSortedSet(requiredSchemaMimeTypes)

    init {
        require(this.requiredCapabilityIds.all { it in AiTextCapabilityId.VALUES }) {
            "Unknown required AI text capability"
        }
        require(this.requiredTextMimeTypes.all { it in AiTextMimeType.INPUT_VALUES }) {
            "Unknown required AI text MIME type"
        }
        require(this.requiredSchemaMimeTypes.all { it in AiTextMimeType.SCHEMA_VALUES }) {
            "Unknown required AI schema MIME type"
        }
        require(minimumMessages >= 1)
        require(minimumContentParts >= 1)
        require(minimumToolDefinitions >= 0)
        require(minimumContextBytes >= 1L)
        require(minimumOutputBytes >= 1L)
        require(minimumToolRounds >= 0)
        require(minimumOutstandingToolCalls >= 0)
        require(minimumRequestDescriptors >= 0)
        require(minimumSessionDescriptors >= minimumRequestDescriptors)
    }

    fun isSatisfiedBy(capabilities: AiTextCapabilities): Boolean =
        requiredCapabilityIds.all { it in capabilities.supportedCapabilityIds() } &&
            capabilities.maximumMessages >= minimumMessages &&
            capabilities.maximumContentParts >= minimumContentParts &&
            capabilities.maximumToolDefinitions >= minimumToolDefinitions &&
            capabilities.maximumContextBytes >= minimumContextBytes &&
            capabilities.maximumOutputBytes >= minimumOutputBytes &&
            capabilities.maximumToolRounds >= minimumToolRounds &&
            capabilities.maximumOutstandingToolCalls >= minimumOutstandingToolCalls &&
            capabilities.maximumRequestDescriptors >= minimumRequestDescriptors &&
            capabilities.maximumSessionDescriptors >= minimumSessionDescriptors &&
            capabilities.acceptedTextMimeTypes.containsAll(requiredTextMimeTypes) &&
            capabilities.acceptedSchemaMimeTypes.containsAll(requiredSchemaMimeTypes)

    fun isSatisfiedBy(model: AiModelInfo, capabilities: AiTextCapabilities): Boolean =
        isSatisfiedBy(capabilities) &&
            model.capabilityIds.containsAll(requiredCapabilityIds) &&
            model.maximumContextBytes >= minimumContextBytes &&
            model.maximumOutputBytes >= minimumOutputBytes
}

internal enum class AiTextProviderCandidateRejection {
    WRONG_ACTION,
    NOT_EXPORTED,
    DISABLED,
    WRONG_PERMISSION,
    INVALID_PACKAGE_IDENTITY,
    TRUST_REQUIRED,
    USER_APPROVAL_REQUIRED,
    DEBUG_TRUST_FORBIDDEN,
    HOST_VERSION_UNSUPPORTED,
    ABI_UNSUPPORTED,
    PROTOCOL_UNSUPPORTED,
    CAPABILITY_MISMATCH,
}

internal data class AiTextProviderCandidateEvaluation(
    val component: AiTextProviderComponent,
    val providerId: String,
    val rejection: AiTextProviderCandidateRejection,
)

/** Exact package and protocol snapshot that must be revalidated immediately before dispatch. */
internal class AiTextProviderPinnedIdentity(
    val component: AiTextProviderComponent,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    providerInfo: AiProviderInfo,
    val protocolVersion: AiProtocolVersion,
    val negotiatedCapabilities: AiTextCapabilities,
) {
    val signerSha256: List<String> = immutableSnapshot(signerSha256.map(::normalizeSha256).distinct().sorted())
    private val providerInfoSnapshot = providerInfo

    val providerId: String
        get() = providerInfoSnapshot.providerId

    val implementationVersionCode: Long
        get() = providerInfoSnapshot.implementationVersionCode

    val implementationVersionName: String
        get() = providerInfoSnapshot.implementationVersionName

    val locality: Int
        get() = providerInfoSnapshot.locality

    val credentialMode: Int
        get() = providerInfoSnapshot.credentialMode

    val providerProtocolRange: AiProtocolRange
        get() = providerInfoSnapshot.protocolRange

    val declaredHttpsOrigins: List<String>
        get() = providerInfoSnapshot.declaredHttpsOrigins

    val supportedAbis: List<String>
        get() = providerInfoSnapshot.supportedAbis

    val minimumHostVersionCode: Long
        get() = providerInfoSnapshot.minimumHostVersionCode

    val maximumHostVersionCode: Long?
        get() = providerInfoSnapshot.maximumHostVersionCode

    init {
        require(packageUid >= 0)
        require(versionCode > 0L)
        require(lastUpdateTime >= 0L)
        require(this.signerSha256.isNotEmpty())
        require(protocolVersion in providerInfoSnapshot.protocolRange)
    }

    fun matchesProviderInfo(value: AiProviderInfo): Boolean = providerInfoSnapshot == value

    override fun equals(other: Any?): Boolean = this === other || other is AiTextProviderPinnedIdentity &&
        component == other.component &&
        packageUid == other.packageUid &&
        versionCode == other.versionCode &&
        lastUpdateTime == other.lastUpdateTime &&
        signerSha256 == other.signerSha256 &&
        providerInfoSnapshot == other.providerInfoSnapshot &&
        protocolVersion == other.protocolVersion &&
        negotiatedCapabilities == other.negotiatedCapabilities

    override fun hashCode(): Int {
        var result = component.hashCode()
        result = 31 * result + packageUid
        result = 31 * result + versionCode.hashCode()
        result = 31 * result + lastUpdateTime.hashCode()
        result = 31 * result + signerSha256.hashCode()
        result = 31 * result + providerInfoSnapshot.hashCode()
        result = 31 * result + protocolVersion.hashCode()
        result = 31 * result + negotiatedCapabilities.hashCode()
        return result
    }

    override fun toString(): String =
        "AiTextProviderPinnedIdentity(component=$component, packageUid=$packageUid, " +
            "versionCode=$versionCode, lastUpdateTime=$lastUpdateTime, " +
            "providerId=$providerId, protocolVersion=$protocolVersion)"
}

internal sealed class AiTextProviderSelectionResult {
    object Disabled : AiTextProviderSelectionResult()

    class NoMatch(evaluations: List<AiTextProviderCandidateEvaluation>) : AiTextProviderSelectionResult() {
        val evaluations: List<AiTextProviderCandidateEvaluation> = immutableSnapshot(evaluations)
    }

    class Ambiguous(candidates: List<AiTextProviderPinnedIdentity>) : AiTextProviderSelectionResult() {
        val candidates: List<AiTextProviderPinnedIdentity> = immutableSnapshot(candidates)
    }

    data class Selected(val pinned: AiTextProviderPinnedIdentity) : AiTextProviderSelectionResult()
}

internal object AiTextProviderSelectionPolicy {

    fun select(
        candidates: Collection<AiTextProviderCandidate>,
        environment: AiTextProviderEnvironment,
        hostCapabilities: AiTextCapabilities,
        requirement: AiTextCapabilityRequirement,
        options: AiTextProviderSelectionOptions = AiTextProviderSelectionOptions(),
    ): AiTextProviderSelectionResult {
        if (!options.enabled) return AiTextProviderSelectionResult.Disabled

        val scoped = candidates.asSequence()
            .filter { options.preferredComponent == null || it.component == options.preferredComponent }
            .sortedWith(CANDIDATE_ORDER)
            .toList()
        val rejected = ArrayList<AiTextProviderCandidateEvaluation>()
        val eligible = ArrayList<AiTextProviderPinnedIdentity>()

        scoped.forEach { candidate ->
            val result = evaluate(candidate, environment, hostCapabilities, requirement)
            if (result.rejection == null) {
                eligible += checkNotNull(result.pinned)
            } else {
                rejected += AiTextProviderCandidateEvaluation(
                    component = candidate.component,
                    providerId = candidate.providerInfo.providerId,
                    rejection = result.rejection,
                )
            }
        }

        return when (eligible.size) {
            0 -> AiTextProviderSelectionResult.NoMatch(rejected)
            1 -> AiTextProviderSelectionResult.Selected(eligible.single())
            else -> AiTextProviderSelectionResult.Ambiguous(eligible)
        }
    }

    fun revalidate(
        pinned: AiTextProviderPinnedIdentity,
        current: AiTextProviderCandidate?,
        environment: AiTextProviderEnvironment,
        hostCapabilities: AiTextCapabilities,
        requirement: AiTextCapabilityRequirement,
    ): Boolean {
        if (current == null || current.component != pinned.component) return false
        val evaluated = evaluate(current, environment, hostCapabilities, requirement)
        return evaluated.rejection == null && evaluated.pinned == pinned
    }

    private fun evaluate(
        candidate: AiTextProviderCandidate,
        environment: AiTextProviderEnvironment,
        hostCapabilities: AiTextCapabilities,
        requirement: AiTextCapabilityRequirement,
    ): EvaluationResult {
        structuralRejection(candidate, environment)?.let { return EvaluationResult(it, null) }

        val protocol = AiProtocolNegotiator.negotiate(
            AiTextProtocol.HOST_PROTOCOL_RANGE,
            candidate.providerInfo.protocolRange,
        ) ?: return EvaluationResult(AiTextProviderCandidateRejection.PROTOCOL_UNSUPPORTED, null)

        val negotiatedCapabilities = runCatching {
            AiTextCapabilityNegotiator.intersect(hostCapabilities, candidate.capabilities)
        }.getOrNull() ?: return EvaluationResult(AiTextProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        if (!requirement.isSatisfiedBy(negotiatedCapabilities)) {
            return EvaluationResult(AiTextProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        }

        return EvaluationResult(
            rejection = null,
            pinned = AiTextProviderPinnedIdentity(
                component = candidate.component,
                packageUid = candidate.packageUid,
                versionCode = candidate.versionCode,
                lastUpdateTime = candidate.lastUpdateTime,
                signerSha256 = candidate.signerSha256,
                providerInfo = candidate.providerInfo,
                protocolVersion = protocol,
                negotiatedCapabilities = negotiatedCapabilities,
            ),
        )
    }

    private fun structuralRejection(
        candidate: AiTextProviderCandidate,
        environment: AiTextProviderEnvironment,
    ): AiTextProviderCandidateRejection? {
        val trustRejection = when {
            candidate.declaredAction != AiTextProtocol.ACTION_TEXT_PROVIDER ->
                AiTextProviderCandidateRejection.WRONG_ACTION
            !candidate.exported -> AiTextProviderCandidateRejection.NOT_EXPORTED
            !candidate.applicationEnabled || !candidate.serviceEnabled -> AiTextProviderCandidateRejection.DISABLED
            candidate.requiredPermission != AiTextProtocol.PLUGIN_PERMISSION ->
                AiTextProviderCandidateRejection.WRONG_PERMISSION
            !hasValidPackageIdentity(candidate) -> AiTextProviderCandidateRejection.INVALID_PACKAGE_IDENTITY
            candidate.sameSignerAsHost || candidate.trustLevel == AiTextProviderTrustLevel.OFFICIAL -> null
            candidate.trustLevel == AiTextProviderTrustLevel.USER_APPROVED &&
                !environment.allowUserApprovedProviders -> AiTextProviderCandidateRejection.USER_APPROVAL_REQUIRED
            candidate.trustLevel == AiTextProviderTrustLevel.USER_APPROVED -> null
            candidate.trustLevel == AiTextProviderTrustLevel.DEBUG_ONLY && environment.releaseBuild ->
                AiTextProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN
            candidate.trustLevel == AiTextProviderTrustLevel.DEBUG_ONLY -> null
            else -> AiTextProviderCandidateRejection.TRUST_REQUIRED
        }
        if (trustRejection != null) return trustRejection
        return compatibilityRejection(candidate, environment)
    }

    private fun compatibilityRejection(
        candidate: AiTextProviderCandidate,
        environment: AiTextProviderEnvironment,
    ): AiTextProviderCandidateRejection? {
        val info = candidate.providerInfo
        if (environment.hostVersionCode < info.minimumHostVersionCode ||
            info.maximumHostVersionCode?.let { environment.hostVersionCode > it } == true
        ) {
            return AiTextProviderCandidateRejection.HOST_VERSION_UNSUPPORTED
        }
        if (info.supportedAbis.isNotEmpty() &&
            (environment.currentAbi == null || environment.currentAbi !in info.supportedAbis)
        ) {
            return AiTextProviderCandidateRejection.ABI_UNSUPPORTED
        }
        return null
    }

    private fun hasValidPackageIdentity(candidate: AiTextProviderCandidate): Boolean {
        if (candidate.packageUid < 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime < 0L) return false
        if (candidate.signerSha256.isEmpty()) return false
        val normalized = candidate.signerSha256.map { value -> value.lowercase() }
        return normalized.distinct().size == normalized.size && normalized.all(::isSha256)
    }

    private data class EvaluationResult(
        val rejection: AiTextProviderCandidateRejection?,
        val pinned: AiTextProviderPinnedIdentity?,
    )

    private val CANDIDATE_ORDER = compareBy<AiTextProviderCandidate>(
        { it.component.packageName },
        { it.component.className },
        { it.providerInfo.providerId },
    ).thenByDescending { it.versionCode }
        .thenByDescending { it.lastUpdateTime }
        .thenBy { it.signerSha256.map { value -> value.lowercase() }.sorted().joinToString(separator = ",") }
}

internal object AiTextProviderCallbackCallerPolicy {
    fun isExpectedUid(pinned: AiTextProviderPinnedIdentity, callingUid: Int): Boolean =
        callingUid >= 0 && callingUid == pinned.packageUid
}

private fun normalizeSha256(value: String): String {
    require(isSha256(value)) { "Signer digest must be SHA-256" }
    return value.lowercase()
}

private fun isSha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun <T> immutableSnapshot(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))

private fun immutableSortedSet(values: Collection<String>): Set<String> =
    Collections.unmodifiableSet(values.toSortedSet())
