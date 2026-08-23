package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractException
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractViolation
import org.autojs.plugin.python.runtime.api.PythonRuntimeInfo
import org.autojs.plugin.python.runtime.api.PythonRuntimeNegotiation
import org.autojs.plugin.python.runtime.api.PythonRuntimeValidation
import java.util.Collections

/** Pure PackageManager snapshot. Android discovery remains outside this policy. */
internal data class PythonRuntimeProviderComponent(
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

internal enum class PythonRuntimeProviderTrustLevel {
    OFFICIAL,
    USER_APPROVED,
    DEBUG_ONLY,
    UNTRUSTED,
}

internal class PythonRuntimeProviderCandidate(
    val component: PythonRuntimeProviderComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val trustLevel: PythonRuntimeProviderTrustLevel,
    val sameSignerAsHost: Boolean,
    val runtimeInfo: PythonRuntimeInfo,
    val capabilities: PythonRuntimeCapabilities,
) {
    val signerSha256: List<String> = immutableList(signerSha256)

    fun copy(
        component: PythonRuntimeProviderComponent = this.component,
        declaredAction: String = this.declaredAction,
        exported: Boolean = this.exported,
        applicationEnabled: Boolean = this.applicationEnabled,
        serviceEnabled: Boolean = this.serviceEnabled,
        requiredPermission: String? = this.requiredPermission,
        packageUid: Int = this.packageUid,
        versionCode: Long = this.versionCode,
        lastUpdateTime: Long = this.lastUpdateTime,
        signerSha256: Collection<String> = this.signerSha256,
        trustLevel: PythonRuntimeProviderTrustLevel = this.trustLevel,
        sameSignerAsHost: Boolean = this.sameSignerAsHost,
        runtimeInfo: PythonRuntimeInfo = this.runtimeInfo,
        capabilities: PythonRuntimeCapabilities = this.capabilities,
    ) = PythonRuntimeProviderCandidate(
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
        runtimeInfo = runtimeInfo,
        capabilities = capabilities,
    )
}

internal data class PythonRuntimeProviderEnvironment(
    val hostVersionCode: Long,
    val currentAbi: String,
    val releaseBuild: Boolean,
    val allowUserApprovedProviders: Boolean = false,
) {
    init {
        require(hostVersionCode > 0L) { "Host version code must be positive" }
        require(currentAbi.isNotBlank()) { "Current ABI must not be blank" }
    }
}

/** The pure evaluator defaults off; the admitted production Host path explicitly enables it. */
internal data class PythonRuntimeProviderSelectionOptions(
    val enabled: Boolean = false,
    val preferredComponent: PythonRuntimeProviderComponent? = null,
)

internal data class PythonRuntimeCapabilityRequirement(
    val workspaceArchive: Boolean = false,
    val moduleEntry: Boolean = false,
    val stdinSnapshot: Boolean = false,
    val hostCapabilitySnapshot: Boolean = false,
    val interactiveInput: Boolean = false,
    val structuredJsonResult: Boolean = false,
    val outputArtifacts: Boolean = false,
    val cooperativeCancellation: Boolean = false,
    val minimumSourceBytes: Long = 1L,
    val minimumStdinBytes: Long = 0L,
    val minimumHostCapabilitySnapshotBytes: Long = 0L,
    val minimumInputPromptBytes: Int = 0,
    val minimumInputReplyBytes: Int = 0,
    val minimumInputPrompts: Int = 0,
    val minimumInputWaitMillis: Long = 0L,
    val minimumStructuredJsonBytes: Int = 0,
    val minimumOutputArtifacts: Int = 0,
    val minimumOutputArtifactPathBytes: Int = 0,
    val minimumOutputArtifactBytes: Long = 0L,
    val minimumTotalOutputArtifactBytes: Long = 0L,
    val minimumOutputBytes: Long = 1L,
    val minimumOutputChunkBytes: Int = 1,
    val minimumOutputChunks: Long = 1L,
    val minimumOutstandingOutputCredits: Int = 1,
    val minimumTimeoutMillis: Long = 1L,
) {
    init {
        require(minimumSourceBytes > 0L)
        require(minimumStdinBytes >= 0L)
        require(minimumHostCapabilitySnapshotBytes >= 0L)
        require(minimumInputPromptBytes >= 0)
        require(minimumInputReplyBytes >= 0)
        require(minimumInputPrompts >= 0)
        require(minimumInputWaitMillis >= 0L)
        require(minimumStructuredJsonBytes >= 0)
        require(minimumOutputArtifacts >= 0)
        require(minimumOutputArtifactPathBytes >= 0)
        require(minimumOutputArtifactBytes >= 0L)
        require(minimumTotalOutputArtifactBytes >= 0L)
        require(minimumOutputBytes > 0L)
        require(minimumOutputChunkBytes > 0)
        require(minimumOutputChunks > 0L)
        require(minimumOutstandingOutputCredits > 0)
        require(minimumTimeoutMillis > 0L)
    }

    fun isSatisfiedBy(value: PythonRuntimeCapabilities): Boolean =
        (!workspaceArchive || value.supportsWorkspaceArchive) &&
            (!moduleEntry || value.supportsModuleEntry) &&
            (
                !stdinSnapshot ||
                    value.supportsStdinSnapshot && value.limits.maxStdinBytes >= minimumStdinBytes
                ) &&
            (!hostCapabilitySnapshot || value.supportsHostCapabilitySnapshot) &&
            (
                !interactiveInput ||
                    value.supportsInteractiveInput &&
                    value.limits.maxInputPromptBytes >= minimumInputPromptBytes &&
                    value.limits.maxInputReplyBytes >= minimumInputReplyBytes &&
                    value.limits.maxInputPrompts >= minimumInputPrompts &&
                    value.limits.maxInputWaitMillis >= minimumInputWaitMillis
                ) &&
            (!cooperativeCancellation || value.supportsCooperativeCancellation) &&
            (
                !structuredJsonResult ||
                    value.supportsStructuredJsonResult &&
                    value.limits.maxStructuredJsonBytes >= minimumStructuredJsonBytes
                ) &&
            (
                !outputArtifacts ||
                    value.supportsOutputArtifacts &&
                    value.limits.maxOutputArtifacts >= minimumOutputArtifacts &&
                    value.limits.maxOutputArtifactPathBytes >= minimumOutputArtifactPathBytes &&
                    value.limits.maxOutputArtifactBytes >= minimumOutputArtifactBytes &&
                    value.limits.maxTotalOutputArtifactBytes >= minimumTotalOutputArtifactBytes
                ) &&
            value.limits.maxSourceBytes >= minimumSourceBytes &&
            value.limits.maxHostCapabilitySnapshotBytes >= minimumHostCapabilitySnapshotBytes &&
            value.limits.maxOutputBytes >= minimumOutputBytes &&
            value.limits.maxOutputChunkBytes >= minimumOutputChunkBytes &&
            value.limits.maxOutputChunks >= minimumOutputChunks &&
            value.limits.maxOutstandingOutputCredits >= minimumOutstandingOutputCredits &&
            value.limits.maxTimeoutMillis >= minimumTimeoutMillis
}

internal enum class PythonRuntimeProviderCandidateRejection {
    WRONG_ACTION,
    NOT_EXPORTED,
    DISABLED,
    WRONG_PERMISSION,
    INVALID_PACKAGE_IDENTITY,
    TRUST_REQUIRED,
    USER_APPROVAL_REQUIRED,
    DEBUG_TRUST_FORBIDDEN,
    INVALID_RUNTIME_METADATA,
    HOST_VERSION_UNSUPPORTED,
    ABI_UNSUPPORTED,
    PROTOCOL_UNSUPPORTED,
    CAPABILITY_MISMATCH,
}

internal data class PythonRuntimeProviderCandidateEvaluation(
    val component: PythonRuntimeProviderComponent,
    val rejection: PythonRuntimeProviderCandidateRejection,
)

/** Exact identity and negotiated contract which must be revalidated before dispatch. */
internal class PythonRuntimeProviderPinnedIdentity(
    val component: PythonRuntimeProviderComponent,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
    val runtimeInfo: PythonRuntimeInfo,
    val capabilities: PythonRuntimeCapabilities,
    val negotiatedProtocolVersion: PythonProtocolVersion,
    val selectedAbi: String,
) {
    val signerSha256: List<String> = immutableList(signerSha256.map(::normalizeSha256).distinct().sorted())

    init {
        require(packageUid >= 0)
        require(versionCode > 0L)
        require(lastUpdateTime > 0L)
        require(this.signerSha256.isNotEmpty())
        require(selectedAbi.isNotBlank())
    }

    override fun equals(other: Any?): Boolean = this === other ||
        other is PythonRuntimeProviderPinnedIdentity &&
        component == other.component &&
        packageUid == other.packageUid &&
        versionCode == other.versionCode &&
        lastUpdateTime == other.lastUpdateTime &&
        signerSha256 == other.signerSha256 &&
        runtimeInfo == other.runtimeInfo &&
        capabilities == other.capabilities &&
        negotiatedProtocolVersion == other.negotiatedProtocolVersion &&
        selectedAbi == other.selectedAbi

    override fun hashCode(): Int = listOf(
        component,
        packageUid,
        versionCode,
        lastUpdateTime,
        signerSha256,
        runtimeInfo,
        capabilities,
        negotiatedProtocolVersion,
        selectedAbi,
    ).hashCode()

    override fun toString(): String =
        "PythonRuntimeProviderPinnedIdentity(component=$component, packageUid=$packageUid, " +
            "versionCode=$versionCode, lastUpdateTime=$lastUpdateTime, " +
            "providerId=${runtimeInfo.providerId}, protocol=$negotiatedProtocolVersion, abi=$selectedAbi)"
}

internal sealed class PythonRuntimeProviderSelectionResult {
    data object Disabled : PythonRuntimeProviderSelectionResult()

    class NoMatch(evaluations: Collection<PythonRuntimeProviderCandidateEvaluation>) :
        PythonRuntimeProviderSelectionResult() {
        val evaluations: List<PythonRuntimeProviderCandidateEvaluation> = immutableList(evaluations)
    }

    class Ambiguous(candidates: Collection<PythonRuntimeProviderPinnedIdentity>) :
        PythonRuntimeProviderSelectionResult() {
        val candidates: List<PythonRuntimeProviderPinnedIdentity> = immutableList(candidates)
    }

    data class Selected(val pinned: PythonRuntimeProviderPinnedIdentity) :
        PythonRuntimeProviderSelectionResult()
}

internal object PythonRuntimeProviderSelectionPolicy {

    fun select(
        candidates: Collection<PythonRuntimeProviderCandidate>,
        environment: PythonRuntimeProviderEnvironment,
        requirement: PythonRuntimeCapabilityRequirement = PythonRuntimeCapabilityRequirement(),
        options: PythonRuntimeProviderSelectionOptions = PythonRuntimeProviderSelectionOptions(),
    ): PythonRuntimeProviderSelectionResult {
        if (!options.enabled) return PythonRuntimeProviderSelectionResult.Disabled

        val eligible = ArrayList<PythonRuntimeProviderPinnedIdentity>()
        val rejected = ArrayList<PythonRuntimeProviderCandidateEvaluation>()
        candidates.asSequence()
            .filter { options.preferredComponent == null || it.component == options.preferredComponent }
            .sortedWith(CANDIDATE_ORDER)
            .forEach { candidate ->
                val evaluation = evaluate(candidate, environment, requirement)
                if (evaluation.pinned != null) {
                    eligible += evaluation.pinned
                } else {
                    rejected += PythonRuntimeProviderCandidateEvaluation(
                        candidate.component,
                        checkNotNull(evaluation.rejection),
                    )
                }
            }

        return when (eligible.size) {
            0 -> PythonRuntimeProviderSelectionResult.NoMatch(rejected)
            1 -> PythonRuntimeProviderSelectionResult.Selected(eligible.single())
            else -> PythonRuntimeProviderSelectionResult.Ambiguous(eligible)
        }
    }

    fun revalidate(
        pinned: PythonRuntimeProviderPinnedIdentity,
        current: PythonRuntimeProviderCandidate?,
        environment: PythonRuntimeProviderEnvironment,
        requirement: PythonRuntimeCapabilityRequirement = PythonRuntimeCapabilityRequirement(),
    ): Boolean {
        if (current == null || current.component != pinned.component) return false
        val evaluated = evaluate(current, environment, requirement)
        return evaluated.rejection == null && evaluated.pinned == pinned
    }

    private fun evaluate(
        candidate: PythonRuntimeProviderCandidate,
        environment: PythonRuntimeProviderEnvironment,
        requirement: PythonRuntimeCapabilityRequirement,
    ): Evaluation {
        structuralRejection(candidate, environment)?.let { return Evaluation(it, null) }
        try {
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(candidate.capabilities, candidate.runtimeInfo)
        } catch (error: PythonRuntimeContractException) {
            val rejection = if (error.violation == PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE) {
                PythonRuntimeProviderCandidateRejection.CAPABILITY_MISMATCH
            } else {
                PythonRuntimeProviderCandidateRejection.INVALID_RUNTIME_METADATA
            }
            return Evaluation(rejection, null)
        }
        if (candidate.runtimeInfo.minHostVersionCode?.let { environment.hostVersionCode < it } == true ||
            candidate.runtimeInfo.maxHostVersionCode?.let { environment.hostVersionCode > it } == true
        ) {
            return Evaluation(PythonRuntimeProviderCandidateRejection.HOST_VERSION_UNSUPPORTED, null)
        }
        if (environment.currentAbi !in candidate.capabilities.supportedAbis) {
            return Evaluation(PythonRuntimeProviderCandidateRejection.ABI_UNSUPPORTED, null)
        }
        val protocol = PythonRuntimeNegotiation.negotiate(candidate.runtimeInfo)
            ?: return Evaluation(PythonRuntimeProviderCandidateRejection.PROTOCOL_UNSUPPORTED, null)
        if (!requirement.isSatisfiedBy(candidate.capabilities)) {
            return Evaluation(PythonRuntimeProviderCandidateRejection.CAPABILITY_MISMATCH, null)
        }
        return Evaluation(
            rejection = null,
            pinned = PythonRuntimeProviderPinnedIdentity(
                component = candidate.component,
                packageUid = candidate.packageUid,
                versionCode = candidate.versionCode,
                lastUpdateTime = candidate.lastUpdateTime,
                signerSha256 = candidate.signerSha256,
                runtimeInfo = candidate.runtimeInfo,
                capabilities = candidate.capabilities,
                negotiatedProtocolVersion = protocol,
                selectedAbi = environment.currentAbi,
            ),
        )
    }

    private fun structuralRejection(
        candidate: PythonRuntimeProviderCandidate,
        environment: PythonRuntimeProviderEnvironment,
    ): PythonRuntimeProviderCandidateRejection? = when {
        candidate.declaredAction != PythonRuntimeContract.RUNTIME_ACTION ->
            PythonRuntimeProviderCandidateRejection.WRONG_ACTION
        !candidate.exported -> PythonRuntimeProviderCandidateRejection.NOT_EXPORTED
        !candidate.applicationEnabled || !candidate.serviceEnabled ->
            PythonRuntimeProviderCandidateRejection.DISABLED
        candidate.requiredPermission != PythonRuntimeContract.PLUGIN_PERMISSION ->
            PythonRuntimeProviderCandidateRejection.WRONG_PERMISSION
        !hasValidPackageIdentity(candidate) ->
            PythonRuntimeProviderCandidateRejection.INVALID_PACKAGE_IDENTITY
        candidate.sameSignerAsHost || candidate.trustLevel == PythonRuntimeProviderTrustLevel.OFFICIAL -> null
        candidate.trustLevel == PythonRuntimeProviderTrustLevel.USER_APPROVED &&
            !environment.allowUserApprovedProviders ->
            PythonRuntimeProviderCandidateRejection.USER_APPROVAL_REQUIRED
        candidate.trustLevel == PythonRuntimeProviderTrustLevel.USER_APPROVED -> null
        candidate.trustLevel == PythonRuntimeProviderTrustLevel.DEBUG_ONLY && environment.releaseBuild ->
            PythonRuntimeProviderCandidateRejection.DEBUG_TRUST_FORBIDDEN
        candidate.trustLevel == PythonRuntimeProviderTrustLevel.DEBUG_ONLY -> null
        else -> PythonRuntimeProviderCandidateRejection.TRUST_REQUIRED
    }

    private fun hasValidPackageIdentity(candidate: PythonRuntimeProviderCandidate): Boolean {
        if (candidate.packageUid < 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime <= 0L) return false
        if (candidate.signerSha256.isEmpty()) return false
        val normalized = candidate.signerSha256.map { it.lowercase() }
        return normalized.distinct().size == normalized.size && normalized.all(::isSha256)
    }

    private data class Evaluation(
        val rejection: PythonRuntimeProviderCandidateRejection?,
        val pinned: PythonRuntimeProviderPinnedIdentity?,
    )

    private val CANDIDATE_ORDER = compareBy<PythonRuntimeProviderCandidate>(
        { it.component.packageName },
        { it.component.className },
        { it.runtimeInfo.providerId },
    ).thenByDescending { it.versionCode }
        .thenByDescending { it.lastUpdateTime }
        .thenBy { it.signerSha256.map { signer -> signer.lowercase() }.sorted().joinToString(",") }
}

internal object PythonRuntimeProviderCallbackCallerPolicy {
    fun isExpectedUid(pinned: PythonRuntimeProviderPinnedIdentity, callingUid: Int): Boolean =
        callingUid >= 0 && callingUid == pinned.packageUid
}

private fun normalizeSha256(value: String): String {
    require(isSha256(value)) { "Signer digest must be SHA-256" }
    return value.lowercase()
}

private fun isSha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
