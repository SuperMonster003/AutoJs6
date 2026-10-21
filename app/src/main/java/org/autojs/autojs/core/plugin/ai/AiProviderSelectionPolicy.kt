package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiProtocolNegotiator
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ai.provider.api.AiTargetInfo
import org.autojs.plugin.ai.provider.api.AiProviderCapabilities
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityNegotiator
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiProviderProtocol
import java.util.Collections

/**
 * Pure policy input produced by a future Android discovery adapter.
 *
 * This layer intentionally does not query PackageManager or bind a service. Every security-relevant
 * package value is copied into the candidate and later pinned before any future Binder dispatch.
 */
internal data class AiProviderComponent(
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

internal enum class AiProviderTrustLevel {
    OFFICIAL,
    USER_APPROVED,
    DEBUG_ONLY,
    UNTRUSTED,
}

internal class AiProviderCandidate(
    val component: AiProviderComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val trustLevel: AiProviderTrustLevel,
    val sameSignerAsHost: Boolean,
    val providerInfo: AiProviderInfo,
    val capabilities: AiProviderCapabilities,
) {
    val signerSha256: List<String> = immutableSnapshot(signerSha256)

    fun copy(
        component: AiProviderComponent = this.component,
        declaredAction: String = this.declaredAction,
        exported: Boolean = this.exported,
        applicationEnabled: Boolean = this.applicationEnabled,
        serviceEnabled: Boolean = this.serviceEnabled,
        requiredPermission: String? = this.requiredPermission,
        packageUid: Int = this.packageUid,
        versionCode: Long = this.versionCode,
        lastUpdateTime: Long = this.lastUpdateTime,
        signerSha256: Collection<String> = this.signerSha256,
        trustLevel: AiProviderTrustLevel = this.trustLevel,
        sameSignerAsHost: Boolean = this.sameSignerAsHost,
        providerInfo: AiProviderInfo = this.providerInfo,
        capabilities: AiProviderCapabilities = this.capabilities,
    ) = AiProviderCandidate(
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

internal data class AiProviderEnvironment(
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
internal data class AiProviderSelectionOptions(
    val enabled: Boolean = false,
    val preferredComponent: AiProviderComponent? = null,
)

internal class AiProviderCapabilityRequirement(
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
        require(this.requiredCapabilityIds.all { it in AiProviderCapabilityId.VALUES }) {
            "Unknown required local AI capability"
        }
        require(this.requiredTextMimeTypes.all { it in AiProviderMimeType.INPUT_VALUES }) {
            "Unknown required local AI MIME type"
        }
        require(this.requiredSchemaMimeTypes.all { it in AiProviderMimeType.SCHEMA_VALUES }) {
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

    fun isSatisfiedBy(capabilities: AiProviderCapabilities): Boolean =
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

    fun isSatisfiedBy(target: AiTargetInfo, capabilities: AiProviderCapabilities): Boolean =
        isSatisfiedBy(capabilities) &&
            target.capabilityIds.containsAll(requiredCapabilityIds) &&
            target.maximumContextBytes?.let { it >= minimumContextBytes } != false &&
            target.maximumOutputBytes?.let { it >= minimumOutputBytes } != false
}

internal enum class AiProviderCandidateRejection {
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

internal data class AiProviderCandidateEvaluation(
    val component: AiProviderComponent,
    val providerId: String,
    val rejection: AiProviderCandidateRejection,
)

/** Exact package and protocol snapshot that must be revalidated immediately before dispatch. */
internal class AiProviderPinnedIdentity(
    val component: AiProviderComponent,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    providerInfo: AiProviderInfo,
    val protocolVersion: AiProtocolVersion,
    val negotiatedCapabilities: AiProviderCapabilities,
) {
    val signerSha256: List<String> = immutableSnapshot(signerSha256.map(::normalizeSha256).distinct().sorted())
    private val providerInfoSnapshot = providerInfo

    val providerId: String
        get() = providerInfoSnapshot.providerId

    val providerInfo: AiProviderInfo
        get() = providerInfoSnapshot

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

    override fun equals(other: Any?): Boolean = this === other || other is AiProviderPinnedIdentity &&
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
        "AiProviderPinnedIdentity(component=$component, packageUid=$packageUid, " +
            "versionCode=$versionCode, lastUpdateTime=$lastUpdateTime, " +
            "providerId=$providerId, protocolVersion=$protocolVersion)"
}

internal sealed class AiProviderSelectionResult {
    object Disabled : AiProviderSelectionResult()

    class NoMatch(evaluations: List<AiProviderCandidateEvaluation>) : AiProviderSelectionResult() {
        val evaluations: List<AiProviderCandidateEvaluation> = immutableSnapshot(evaluations)
    }

    class Ambiguous(candidates: List<AiProviderPinnedIdentity>) : AiProviderSelectionResult() {
        val candidates: List<AiProviderPinnedIdentity> = immutableSnapshot(candidates)
    }

    data class Selected(val pinned: AiProviderPinnedIdentity) : AiProviderSelectionResult()
}

internal object AiProviderSelectionPolicy {

    fun select(
        candidates: Collection<AiProviderCandidate>,
        environment: AiProviderEnvironment,
        hostCapabilities: AiProviderCapabilities,
        requirement: AiProviderCapabilityRequirement,
        options: AiProviderSelectionOptions = AiProviderSelectionOptions(),
    ): AiProviderSelectionResult {
        if (!options.enabled) return AiProviderSelectionResult.Disabled

        val scoped = candidates.asSequence()
            .filter { options.preferredComponent == null || it.component == options.preferredComponent }
            .sortedWith(CANDIDATE_ORDER)
            .toList()
        val rejected = ArrayList<AiProviderCandidateEvaluation>()
        val eligible = ArrayList<AiProviderPinnedIdentity>()

        scoped.forEach { candidate ->
            val result = evaluate(candidate, environment, hostCapabilities, requirement)
            if (result.rejection == null) {
                eligible += checkNotNull(result.pinned)
            } else {
                rejected += AiProviderCandidateEvaluation(
                    component = candidate.component,
                    providerId = candidate.providerInfo.providerId,
                    rejection = result.rejection,
                )
            }
        }

        return when (eligible.size) {
            0 -> AiProviderSelectionResult.NoMatch(rejected)
            1 -> AiProviderSelectionResult.Selected(eligible.single())
            else -> AiProviderSelectionResult.Ambiguous(eligible)
        }
    }

    fun revalidate(
        pinned: AiProviderPinnedIdentity,
        current: AiProviderCandidate?,
        environment: AiProviderEnvironment,
        hostCapabilities: AiProviderCapabilities,
        requirement: AiProviderCapabilityRequirement,
    ): Boolean {
        if (current == null || current.component != pinned.component) return false
        val evaluated = evaluate(current, environment, hostCapabilities, requirement)
        return evaluated.rejection == null && evaluated.pinned == pinned
    }

    private fun evaluate(
        candidate: AiProviderCandidate,
        environment: AiProviderEnvironment,
        hostCapabilities: AiProviderCapabilities,
        requirement: AiProviderCapabilityRequirement,
    ): EvaluationResult {
        structuralRejection(candidate, environment)?.let { return EvaluationResult(it, null) }

        val protocol = AiProtocolNegotiator.negotiate(
            AiProviderProtocol.HOST_PROTOCOL_RANGE,
            candidate.providerInfo.protocolRange,
        ) ?: return EvaluationResult(AiProviderCandidateRejection.PROTOCOL_UNSUPPORTED, null)

        val negotiatedCapabilities = runCatching {
            AiProviderCapabilityNegotiator.intersect(hostCapabilities, candidate.capabilities).let { capabilities ->
                if (protocol < AiProviderProtocol.PROTOCOL_V2_1) capabilities.copy(vision = null) else capabilities
            }
        }.getOrNull() ?: return EvaluationResult(AiProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        if (!requirement.isSatisfiedBy(negotiatedCapabilities)) {
            return EvaluationResult(AiProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        }

        return EvaluationResult(
            rejection = null,
            pinned = AiProviderPinnedIdentity(
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
        candidate: AiProviderCandidate,
        environment: AiProviderEnvironment,
    ): AiProviderCandidateRejection? {
        val trustRejection = when {
            candidate.declaredAction != AiProviderProtocol.ACTION_PROVIDER ->
                AiProviderCandidateRejection.WRONG_ACTION
            !candidate.exported -> AiProviderCandidateRejection.NOT_EXPORTED
            !candidate.applicationEnabled || !candidate.serviceEnabled -> AiProviderCandidateRejection.DISABLED
            !ThreeStoneAiOfficialPlugin.acceptsPermission(candidate.component, candidate.requiredPermission) ->
                AiProviderCandidateRejection.WRONG_PERMISSION
            !hasValidPackageIdentity(candidate) -> AiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY
            candidate.sameSignerAsHost || candidate.trustLevel == AiProviderTrustLevel.OFFICIAL -> null
            candidate.trustLevel == AiProviderTrustLevel.USER_APPROVED &&
                !environment.allowUserApprovedProviders -> AiProviderCandidateRejection.USER_APPROVAL_REQUIRED
            candidate.trustLevel == AiProviderTrustLevel.USER_APPROVED -> null
            candidate.trustLevel == AiProviderTrustLevel.DEBUG_ONLY && environment.releaseBuild ->
                AiProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN
            candidate.trustLevel == AiProviderTrustLevel.DEBUG_ONLY -> null
            else -> AiProviderCandidateRejection.TRUST_REQUIRED
        }
        if (trustRejection != null) return trustRejection
        return compatibilityRejection(candidate, environment)
    }

    private fun compatibilityRejection(
        candidate: AiProviderCandidate,
        environment: AiProviderEnvironment,
    ): AiProviderCandidateRejection? {
        val info = candidate.providerInfo
        if (environment.hostVersionCode < info.minimumHostVersionCode ||
            info.maximumHostVersionCode?.let { environment.hostVersionCode > it } == true
        ) {
            return AiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED
        }
        if (info.supportedAbis.isNotEmpty() &&
            (environment.currentAbi == null || environment.currentAbi !in info.supportedAbis)
        ) {
            return AiProviderCandidateRejection.ABI_UNSUPPORTED
        }
        return null
    }

    private fun hasValidPackageIdentity(candidate: AiProviderCandidate): Boolean {
        if (candidate.packageUid < 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime < 0L) return false
        if (candidate.signerSha256.isEmpty()) return false
        val normalized = candidate.signerSha256.map { value -> value.lowercase() }
        return normalized.distinct().size == normalized.size && normalized.all(::isSha256)
    }

    private data class EvaluationResult(
        val rejection: AiProviderCandidateRejection?,
        val pinned: AiProviderPinnedIdentity?,
    )

    private val CANDIDATE_ORDER = compareBy<AiProviderCandidate>(
        { it.component.packageName },
        { it.component.className },
        { it.providerInfo.providerId },
    ).thenByDescending { it.versionCode }
        .thenByDescending { it.lastUpdateTime }
        .thenBy { it.signerSha256.map { value -> value.lowercase() }.sorted().joinToString(separator = ",") }
}

internal object AiProviderCallbackCallerPolicy {
    fun isExpectedUid(pinned: AiProviderPinnedIdentity, callingUid: Int): Boolean =
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
