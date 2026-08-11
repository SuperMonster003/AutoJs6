package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerContract
import java.util.Collections

internal enum class DexCompilerTrustLevel {
    OFFICIAL,
    TRUSTED,
    USER_GRANTED,
    REQUIRED,
}

internal data class DexCompilerComponent(
    val packageName: String,
    val className: String,
) {
    init {
        require(packageName.isNotBlank())
        require(className.isNotBlank())
    }
}

internal class DexCompilerPinnedIdentity(
    val component: DexCompilerComponent,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    signerSha256: Collection<String>,
) {
    val signerSha256: List<String> = Collections.unmodifiableList(
        signerSha256.map(::normalizeSha256).distinct().sorted(),
    )

    init {
        require(packageUid >= 0)
        require(versionCode > 0L)
        require(lastUpdateTime >= 0L)
        require(this.signerSha256.isNotEmpty())
    }

    override fun equals(other: Any?): Boolean = other is DexCompilerPinnedIdentity &&
        component == other.component && versionCode == other.versionCode &&
        packageUid == other.packageUid && lastUpdateTime == other.lastUpdateTime && signerSha256 == other.signerSha256

    override fun hashCode(): Int {
        var result = component.hashCode()
        result = 31 * result + packageUid
        result = 31 * result + versionCode.hashCode()
        result = 31 * result + lastUpdateTime.hashCode()
        result = 31 * result + signerSha256.hashCode()
        return result
    }

    override fun toString(): String =
        "${component.packageName}/${component.className}#$packageUid@$versionCode:$lastUpdateTime:${signerSha256.joinToString()}"
}

internal data class DexCompilerServiceCandidate(
    val component: DexCompilerComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val signerSha256: Set<String>,
    val trustLevel: DexCompilerTrustLevel,
    val sameSignerAsHost: Boolean,
) {
    fun pin(): DexCompilerPinnedIdentity = DexCompilerPinnedIdentity(
        component = component,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signerSha256,
    )
}

internal enum class DexCompilerCandidateRejection {
    WRONG_ACTION,
    NOT_EXPORTED,
    DISABLED,
    WRONG_PERMISSION,
    RELEASE_TRUST_FORBIDDEN,
    TRUST_REQUIRED,
    INVALID_IDENTITY,
}

internal object DexCompilerExperimentalPolicy {

    fun rejection(
        candidate: DexCompilerServiceCandidate,
        releaseBuild: Boolean,
    ): DexCompilerCandidateRejection? = when {
        candidate.declaredAction != DexCompilerContract.SERVICE_ACTION ->
            DexCompilerCandidateRejection.WRONG_ACTION
        !candidate.exported -> DexCompilerCandidateRejection.NOT_EXPORTED
        !candidate.applicationEnabled || !candidate.serviceEnabled -> DexCompilerCandidateRejection.DISABLED
        candidate.requiredPermission != DexCompilerContract.PLUGIN_PERMISSION ->
            DexCompilerCandidateRejection.WRONG_PERMISSION
        candidate.packageUid < 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime < 0L ||
            candidate.signerSha256.isEmpty() ||
            candidate.signerSha256.any { !isSha256(it) } -> DexCompilerCandidateRejection.INVALID_IDENTITY
        candidate.sameSignerAsHost || candidate.trustLevel == DexCompilerTrustLevel.OFFICIAL -> null
        releaseBuild && (candidate.trustLevel == DexCompilerTrustLevel.TRUSTED ||
            candidate.trustLevel == DexCompilerTrustLevel.USER_GRANTED) ->
            DexCompilerCandidateRejection.RELEASE_TRUST_FORBIDDEN
        else -> DexCompilerCandidateRejection.TRUST_REQUIRED
    }

    fun selectAndPin(
        candidates: Collection<DexCompilerServiceCandidate>,
        releaseBuild: Boolean,
        preferredComponent: DexCompilerComponent? = null,
    ): DexCompilerPinnedIdentity? = candidates.asSequence()
        .filter { preferredComponent == null || it.component == preferredComponent }
        .filter { rejection(it, releaseBuild) == null }
        .sortedWith(
            compareByDescending<DexCompilerServiceCandidate> { it.versionCode }
                .thenByDescending { it.lastUpdateTime }
                .thenBy { it.component.packageName }
                .thenBy { it.component.className },
        )
        .firstOrNull()
        ?.pin()

    fun revalidate(
        pinned: DexCompilerPinnedIdentity,
        current: DexCompilerServiceCandidate?,
        releaseBuild: Boolean,
    ): Boolean = current != null && rejection(current, releaseBuild) == null && current.pin() == pinned
}

internal enum class DexCompilerInvocationKind {
    ASYNCHRONOUS,
    SYNCHRONOUS,
}

internal enum class DexCompilerEntryDecision {
    RUN_EXPERIMENT,
    FAIL_OPEN_TO_LOCAL,
}

internal object DexCompilerExperimentalEntryGate {
    fun decide(invocationKind: DexCompilerInvocationKind, isMainThread: Boolean): DexCompilerEntryDecision =
        if (invocationKind == DexCompilerInvocationKind.SYNCHRONOUS && isMainThread) {
            DexCompilerEntryDecision.FAIL_OPEN_TO_LOCAL
        } else {
            DexCompilerEntryDecision.RUN_EXPERIMENT
        }
}

/** Only crash leftovers are recoverable; published cache entries are never startup-cleaned. */
internal object DexCompilerArtifactRecoveryPolicy {
    private val TRANSIENT = Regex(
        "(?:input-[0-9a-f-]{36}\\.(?:jar|bundle)|(?:output|verified)-[0-9a-f-]{36}\\.partial)",
    )

    fun isTransientArtifact(fileName: String): Boolean = TRANSIENT.matches(fileName)
}

internal object DexCompilerTransportCallerPolicy {
    fun isExpectedUid(pinnedPackageUid: Int, callingUid: Int): Boolean =
        pinnedPackageUid >= 0 && callingUid == pinnedPackageUid
}

/** Conservative ceiling while validation and publication still materialize the whole artifact. */
internal object DexCompilerExperimentalHostLimits {
    const val MAX_IN_MEMORY_OUTPUT_BYTES = 16L * 1024L * 1024L
}

private fun normalizeSha256(value: String): String {
    require(isSha256(value)) { "Signer digest must be SHA-256" }
    return value.lowercase()
}

private fun isSha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
