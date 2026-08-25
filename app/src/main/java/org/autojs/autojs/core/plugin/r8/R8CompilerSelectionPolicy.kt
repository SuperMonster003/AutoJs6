package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerContract
import java.util.Collections

/** A fully qualified service component; no package-only or implicit selection is representable. */
internal data class R8CompilerComponent(
    val packageName: String,
    val className: String,
) {
    init {
        require(isValidComponentPart(packageName)) { "R8 compiler package name is invalid" }
        require(isValidComponentPart(className)) { "R8 compiler class name is invalid" }
    }
}

internal class R8CompilerPinnedIdentity(
    val component: R8CompilerComponent,
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

    override fun equals(other: Any?): Boolean = other is R8CompilerPinnedIdentity &&
        component == other.component && packageUid == other.packageUid &&
        versionCode == other.versionCode && lastUpdateTime == other.lastUpdateTime &&
        signerSha256 == other.signerSha256

    override fun hashCode(): Int {
        var result = component.hashCode()
        result = 31 * result + packageUid
        result = 31 * result + versionCode.hashCode()
        result = 31 * result + lastUpdateTime.hashCode()
        result = 31 * result + signerSha256.hashCode()
        return result
    }
}

internal data class R8CompilerServiceCandidate(
    val component: R8CompilerComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val requiredPermission: String?,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val signerSha256: Set<String>,
    val sameSignerAsHost: Boolean,
) {
    fun pin(): R8CompilerPinnedIdentity = R8CompilerPinnedIdentity(
        component,
        packageUid,
        versionCode,
        lastUpdateTime,
        signerSha256,
    )
}

internal data class R8CompilerSelectionOptions(
    val enabled: Boolean = false,
    val selectedComponent: R8CompilerComponent? = null,
)

internal enum class R8CompilerCandidateRejection {
    WRONG_ACTION,
    NOT_EXPORTED,
    DISABLED,
    WRONG_PERMISSION,
    HOST_SIGNER_MISMATCH,
    INVALID_IDENTITY,
}

internal enum class R8CompilerUnavailableReason {
    NO_EXACT_MATCH,
    DISCOVERY_FAILED,
}

internal sealed class R8CompilerSelectionDecision {
    object Disabled : R8CompilerSelectionDecision()
    object NoSelection : R8CompilerSelectionDecision()

    data class Unavailable(
        val component: R8CompilerComponent,
        val reason: R8CompilerUnavailableReason,
    ) : R8CompilerSelectionDecision()

    data class Rejected(
        val component: R8CompilerComponent,
        val reason: R8CompilerCandidateRejection?,
    ) : R8CompilerSelectionDecision()

    data class Selected(val identity: R8CompilerPinnedIdentity) : R8CompilerSelectionDecision()
}

/**
 * Default-off R8 selection. Discovery is lazy, and an enabled request can select only the exact
 * persisted component. Unlike the DEX experiment, R8 accepts no trust tier below same-signature.
 */
internal object R8CompilerSelectionPolicy {
    fun decide(
        options: R8CompilerSelectionOptions = R8CompilerSelectionOptions(),
        discoverCandidates: () -> Collection<R8CompilerServiceCandidate>,
    ): R8CompilerSelectionDecision {
        if (!options.enabled) return R8CompilerSelectionDecision.Disabled
        val selected = options.selectedComponent ?: return R8CompilerSelectionDecision.NoSelection
        val candidates = try {
            discoverCandidates()
        } catch (_: Exception) {
            return R8CompilerSelectionDecision.Unavailable(
                selected,
                R8CompilerUnavailableReason.DISCOVERY_FAILED,
            )
        }
        val exact = candidates.filter { it.component == selected }
        if (exact.isEmpty()) {
            return R8CompilerSelectionDecision.Unavailable(
                selected,
                R8CompilerUnavailableReason.NO_EXACT_MATCH,
            )
        }
        if (exact.size != 1) return R8CompilerSelectionDecision.Rejected(selected, null)
        val candidate = exact.single()
        val rejection = rejection(candidate)
        return if (rejection == null) {
            R8CompilerSelectionDecision.Selected(candidate.pin())
        } else {
            R8CompilerSelectionDecision.Rejected(selected, rejection)
        }
    }

    fun revalidate(identity: R8CompilerPinnedIdentity, candidate: R8CompilerServiceCandidate?): Boolean =
        candidate != null && rejection(candidate) == null && candidate.pin() == identity

    fun rejection(candidate: R8CompilerServiceCandidate): R8CompilerCandidateRejection? = when {
        candidate.declaredAction != R8CompilerContract.SERVICE_ACTION ->
            R8CompilerCandidateRejection.WRONG_ACTION
        !candidate.exported -> R8CompilerCandidateRejection.NOT_EXPORTED
        !candidate.applicationEnabled || !candidate.serviceEnabled ->
            R8CompilerCandidateRejection.DISABLED
        candidate.requiredPermission != R8CompilerContract.PLUGIN_PERMISSION ->
            R8CompilerCandidateRejection.WRONG_PERMISSION
        candidate.packageUid < 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime < 0L ||
            candidate.signerSha256.isEmpty() || candidate.signerSha256.any { !isSha256(it) } ->
            R8CompilerCandidateRejection.INVALID_IDENTITY
        !candidate.sameSignerAsHost -> R8CompilerCandidateRejection.HOST_SIGNER_MISMATCH
        else -> null
    }
}

/** Delimiter-safe persistence codec in a namespace distinct from the DEX selection codec. */
internal object R8CompilerComponentCodec {
    private const val FORMAT = "r8-v1"
    private const val MAX_NAME_CHARS = 4_096
    private const val MAX_LENGTH_DIGITS = 4
    private const val MAX_ENCODED_CHARS = MAX_NAME_CHARS * 2 + 24

    fun encode(component: R8CompilerComponent): String =
        "$FORMAT:${component.packageName.length}:${component.className.length}:" +
            component.packageName + component.className

    fun decodeOrNull(encoded: String?): R8CompilerComponent? {
        if (encoded == null || encoded.length > MAX_ENCODED_CHARS) return null
        val fields = encoded.split(':', limit = 4)
        if (fields.size != 4 || fields[0] != FORMAT) return null
        val packageLength = canonicalLength(fields[1]) ?: return null
        val classLength = canonicalLength(fields[2]) ?: return null
        val payload = fields[3]
        if (packageLength.toLong() + classLength.toLong() != payload.length.toLong()) return null
        return runCatching {
            R8CompilerComponent(
                payload.substring(0, packageLength),
                payload.substring(packageLength),
            )
        }.getOrNull()
    }

    private fun canonicalLength(value: String): Int? {
        if (value.isEmpty() || value.length > MAX_LENGTH_DIGITS) return null
        if (value.length > 1 && value.startsWith('0')) return null
        return value.toIntOrNull()?.takeIf { it in 1..MAX_NAME_CHARS }
    }
}

private fun isValidComponentPart(value: String): Boolean = value.isNotBlank() &&
    value.length <= 4_096 && value == value.trim() && value.none(Character::isISOControl)

private fun normalizeSha256(value: String): String {
    require(isSha256(value)) { "R8 compiler signer digest must be SHA-256" }
    return value.lowercase()
}

private fun isSha256(value: String): Boolean = value.length == 64 &&
    value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
