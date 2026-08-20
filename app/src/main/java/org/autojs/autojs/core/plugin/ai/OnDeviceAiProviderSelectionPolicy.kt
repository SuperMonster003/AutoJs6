package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiProtocolNegotiator
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.autojs.plugin.ai.common.api.AiProviderInfo
import org.autojs.plugin.ondeviceai.api.AiModelInfo
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilities
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityId
import org.autojs.plugin.ondeviceai.api.OnDeviceAiCapabilityNegotiator
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.autojs.plugin.ondeviceai.api.OnDeviceAiProtocol
import java.util.Collections

/**
 * Pure policy input produced by a future Android discovery adapter.
 *
 * This layer intentionally does not query PackageManager or bind a service. Every security-relevant
 * package value is copied into the candidate and later pinned before any future Binder dispatch.
 */
internal data class OnDeviceAiProviderComponent(
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

internal enum class OnDeviceAiProviderTrustLevel {
    OFFICIAL,
    USER_APPROVED,
    DEBUG_ONLY,
    UNTRUSTED,
}

internal class OnDeviceAiProviderCandidate(
    val component: OnDeviceAiProviderComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val trustLevel: OnDeviceAiProviderTrustLevel,
    val sameSignerAsHost: Boolean,
    val providerInfo: AiProviderInfo,
    val capabilities: OnDeviceAiCapabilities,
) {
    val signerSha256: List<String> = immutableSnapshot(signerSha256)

    fun copy(
        component: OnDeviceAiProviderComponent = this.component,
        declaredAction: String = this.declaredAction,
        exported: Boolean = this.exported,
        applicationEnabled: Boolean = this.applicationEnabled,
        serviceEnabled: Boolean = this.serviceEnabled,
        requiredPermission: String? = this.requiredPermission,
        packageUid: Int = this.packageUid,
        versionCode: Long = this.versionCode,
        lastUpdateTime: Long = this.lastUpdateTime,
        signerSha256: Collection<String> = this.signerSha256,
        trustLevel: OnDeviceAiProviderTrustLevel = this.trustLevel,
        sameSignerAsHost: Boolean = this.sameSignerAsHost,
        providerInfo: AiProviderInfo = this.providerInfo,
        capabilities: OnDeviceAiCapabilities = this.capabilities,
    ) = OnDeviceAiProviderCandidate(
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

internal data class OnDeviceAiProviderEnvironment(
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
internal data class OnDeviceAiProviderSelectionOptions(
    val enabled: Boolean = false,
    val preferredComponent: OnDeviceAiProviderComponent? = null,
)

internal class OnDeviceAiCapabilityRequirement(
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
        require(this.requiredCapabilityIds.all { it in OnDeviceAiCapabilityId.VALUES }) {
            "Unknown required on-device AI capability"
        }
        require(this.requiredTextMimeTypes.all { it in OnDeviceAiMimeType.INPUT_VALUES }) {
            "Unknown required on-device AI MIME type"
        }
        require(this.requiredSchemaMimeTypes.all { it in OnDeviceAiMimeType.SCHEMA_VALUES }) {
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

    fun isSatisfiedBy(capabilities: OnDeviceAiCapabilities): Boolean =
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

    fun isSatisfiedBy(model: AiModelInfo, capabilities: OnDeviceAiCapabilities): Boolean =
        isSatisfiedBy(capabilities) &&
            model.capabilityIds.containsAll(requiredCapabilityIds) &&
            model.maximumContextBytes >= minimumContextBytes &&
            model.maximumOutputBytes >= minimumOutputBytes
}

internal enum class OnDeviceAiProviderCandidateRejection {
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

internal data class OnDeviceAiProviderCandidateEvaluation(
    val component: OnDeviceAiProviderComponent,
    val providerId: String,
    val rejection: OnDeviceAiProviderCandidateRejection,
)

/** Exact package and protocol snapshot that must be revalidated immediately before dispatch. */
internal class OnDeviceAiProviderPinnedIdentity(
    val component: OnDeviceAiProviderComponent,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    providerInfo: AiProviderInfo,
    val protocolVersion: AiProtocolVersion,
    val negotiatedCapabilities: OnDeviceAiCapabilities,
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

    override fun equals(other: Any?): Boolean = this === other || other is OnDeviceAiProviderPinnedIdentity &&
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
        "OnDeviceAiProviderPinnedIdentity(component=$component, packageUid=$packageUid, " +
            "versionCode=$versionCode, lastUpdateTime=$lastUpdateTime, " +
            "providerId=$providerId, protocolVersion=$protocolVersion)"
}

internal sealed class OnDeviceAiProviderSelectionResult {
    object Disabled : OnDeviceAiProviderSelectionResult()

    class NoMatch(evaluations: List<OnDeviceAiProviderCandidateEvaluation>) : OnDeviceAiProviderSelectionResult() {
        val evaluations: List<OnDeviceAiProviderCandidateEvaluation> = immutableSnapshot(evaluations)
    }

    class Ambiguous(candidates: List<OnDeviceAiProviderPinnedIdentity>) : OnDeviceAiProviderSelectionResult() {
        val candidates: List<OnDeviceAiProviderPinnedIdentity> = immutableSnapshot(candidates)
    }

    data class Selected(val pinned: OnDeviceAiProviderPinnedIdentity) : OnDeviceAiProviderSelectionResult()
}

internal object OnDeviceAiProviderSelectionPolicy {

    fun select(
        candidates: Collection<OnDeviceAiProviderCandidate>,
        environment: OnDeviceAiProviderEnvironment,
        hostCapabilities: OnDeviceAiCapabilities,
        requirement: OnDeviceAiCapabilityRequirement,
        options: OnDeviceAiProviderSelectionOptions = OnDeviceAiProviderSelectionOptions(),
    ): OnDeviceAiProviderSelectionResult {
        if (!options.enabled) return OnDeviceAiProviderSelectionResult.Disabled

        val scoped = candidates.asSequence()
            .filter { options.preferredComponent == null || it.component == options.preferredComponent }
            .sortedWith(CANDIDATE_ORDER)
            .toList()
        val rejected = ArrayList<OnDeviceAiProviderCandidateEvaluation>()
        val eligible = ArrayList<OnDeviceAiProviderPinnedIdentity>()

        scoped.forEach { candidate ->
            val result = evaluate(candidate, environment, hostCapabilities, requirement)
            if (result.rejection == null) {
                eligible += checkNotNull(result.pinned)
            } else {
                rejected += OnDeviceAiProviderCandidateEvaluation(
                    component = candidate.component,
                    providerId = candidate.providerInfo.providerId,
                    rejection = result.rejection,
                )
            }
        }

        return when (eligible.size) {
            0 -> OnDeviceAiProviderSelectionResult.NoMatch(rejected)
            1 -> OnDeviceAiProviderSelectionResult.Selected(eligible.single())
            else -> OnDeviceAiProviderSelectionResult.Ambiguous(eligible)
        }
    }

    fun revalidate(
        pinned: OnDeviceAiProviderPinnedIdentity,
        current: OnDeviceAiProviderCandidate?,
        environment: OnDeviceAiProviderEnvironment,
        hostCapabilities: OnDeviceAiCapabilities,
        requirement: OnDeviceAiCapabilityRequirement,
    ): Boolean {
        if (current == null || current.component != pinned.component) return false
        val evaluated = evaluate(current, environment, hostCapabilities, requirement)
        return evaluated.rejection == null && evaluated.pinned == pinned
    }

    private fun evaluate(
        candidate: OnDeviceAiProviderCandidate,
        environment: OnDeviceAiProviderEnvironment,
        hostCapabilities: OnDeviceAiCapabilities,
        requirement: OnDeviceAiCapabilityRequirement,
    ): EvaluationResult {
        structuralRejection(candidate, environment)?.let { return EvaluationResult(it, null) }

        val protocol = AiProtocolNegotiator.negotiate(
            OnDeviceAiProtocol.HOST_PROTOCOL_RANGE,
            candidate.providerInfo.protocolRange,
        ) ?: return EvaluationResult(OnDeviceAiProviderCandidateRejection.PROTOCOL_UNSUPPORTED, null)

        val negotiatedCapabilities = runCatching {
            OnDeviceAiCapabilityNegotiator.intersect(hostCapabilities, candidate.capabilities)
        }.getOrNull() ?: return EvaluationResult(OnDeviceAiProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        if (!requirement.isSatisfiedBy(negotiatedCapabilities)) {
            return EvaluationResult(OnDeviceAiProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        }

        return EvaluationResult(
            rejection = null,
            pinned = OnDeviceAiProviderPinnedIdentity(
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
        candidate: OnDeviceAiProviderCandidate,
        environment: OnDeviceAiProviderEnvironment,
    ): OnDeviceAiProviderCandidateRejection? {
        val trustRejection = when {
            candidate.declaredAction != OnDeviceAiProtocol.ACTION_PROVIDER ->
                OnDeviceAiProviderCandidateRejection.WRONG_ACTION
            !candidate.exported -> OnDeviceAiProviderCandidateRejection.NOT_EXPORTED
            !candidate.applicationEnabled || !candidate.serviceEnabled -> OnDeviceAiProviderCandidateRejection.DISABLED
            candidate.requiredPermission != OnDeviceAiProtocol.PLUGIN_PERMISSION ->
                OnDeviceAiProviderCandidateRejection.WRONG_PERMISSION
            !hasValidPackageIdentity(candidate) -> OnDeviceAiProviderCandidateRejection.INVALID_PACKAGE_IDENTITY
            candidate.sameSignerAsHost || candidate.trustLevel == OnDeviceAiProviderTrustLevel.OFFICIAL -> null
            candidate.trustLevel == OnDeviceAiProviderTrustLevel.USER_APPROVED &&
                !environment.allowUserApprovedProviders -> OnDeviceAiProviderCandidateRejection.USER_APPROVAL_REQUIRED
            candidate.trustLevel == OnDeviceAiProviderTrustLevel.USER_APPROVED -> null
            candidate.trustLevel == OnDeviceAiProviderTrustLevel.DEBUG_ONLY && environment.releaseBuild ->
                OnDeviceAiProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN
            candidate.trustLevel == OnDeviceAiProviderTrustLevel.DEBUG_ONLY -> null
            else -> OnDeviceAiProviderCandidateRejection.TRUST_REQUIRED
        }
        if (trustRejection != null) return trustRejection
        return compatibilityRejection(candidate, environment)
    }

    private fun compatibilityRejection(
        candidate: OnDeviceAiProviderCandidate,
        environment: OnDeviceAiProviderEnvironment,
    ): OnDeviceAiProviderCandidateRejection? {
        val info = candidate.providerInfo
        if (environment.hostVersionCode < info.minimumHostVersionCode ||
            info.maximumHostVersionCode?.let { environment.hostVersionCode > it } == true
        ) {
            return OnDeviceAiProviderCandidateRejection.HOST_VERSION_UNSUPPORTED
        }
        if (info.supportedAbis.isNotEmpty() &&
            (environment.currentAbi == null || environment.currentAbi !in info.supportedAbis)
        ) {
            return OnDeviceAiProviderCandidateRejection.ABI_UNSUPPORTED
        }
        return null
    }

    private fun hasValidPackageIdentity(candidate: OnDeviceAiProviderCandidate): Boolean {
        if (candidate.packageUid < 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime < 0L) return false
        if (candidate.signerSha256.isEmpty()) return false
        val normalized = candidate.signerSha256.map { value -> value.lowercase() }
        return normalized.distinct().size == normalized.size && normalized.all(::isSha256)
    }

    private data class EvaluationResult(
        val rejection: OnDeviceAiProviderCandidateRejection?,
        val pinned: OnDeviceAiProviderPinnedIdentity?,
    )

    private val CANDIDATE_ORDER = compareBy<OnDeviceAiProviderCandidate>(
        { it.component.packageName },
        { it.component.className },
        { it.providerInfo.providerId },
    ).thenByDescending { it.versionCode }
        .thenByDescending { it.lastUpdateTime }
        .thenBy { it.signerSha256.map { value -> value.lowercase() }.sorted().joinToString(separator = ",") }
}

internal object OnDeviceAiProviderCallbackCallerPolicy {
    fun isExpectedUid(pinned: OnDeviceAiProviderPinnedIdentity, callingUid: Int): Boolean =
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
