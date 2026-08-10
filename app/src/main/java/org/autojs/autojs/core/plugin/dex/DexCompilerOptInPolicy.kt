package org.autojs.autojs.core.plugin.dex

/** Persisted R1 state is safe by default and never implies automatic provider selection. */
internal data class DexCompilerOptInOptions(
    val enabled: Boolean = false,
    val selectedComponent: DexCompilerComponent? = null,
)

internal enum class DexCompilerOptInUnavailableReason {
    NO_EXACT_MATCH,
    DISCOVERY_FAILED,
}

internal enum class DexCompilerOptInRejectionReason {
    CANDIDATE_POLICY,
    HOST_SIGNER_MISMATCH,
    MULTIPLE_EXACT_MATCHES,
}

internal sealed class DexCompilerOptInDecision {
    object Disabled : DexCompilerOptInDecision()

    object NoSelection : DexCompilerOptInDecision()

    data class Unavailable(
        val component: DexCompilerComponent,
        val reason: DexCompilerOptInUnavailableReason,
    ) : DexCompilerOptInDecision()

    data class Rejected(
        val component: DexCompilerComponent,
        val reason: DexCompilerOptInRejectionReason,
        val candidateRejection: DexCompilerCandidateRejection? = null,
    ) : DexCompilerOptInDecision()

    data class Selected(
        val identity: DexCompilerPinnedIdentity,
    ) : DexCompilerOptInDecision()
}

/**
 * Pure R1 opt-in gate. Discovery is lazy so the hard-off and no-selection states cannot query or
 * bind a provider accidentally. An enabled decision considers only the exact persisted component.
 */
internal object DexCompilerOptInPolicy {

    fun decide(
        options: DexCompilerOptInOptions = DexCompilerOptInOptions(),
        releaseBuild: Boolean,
        discoverCandidates: () -> Collection<DexCompilerServiceCandidate>,
    ): DexCompilerOptInDecision {
        if (!options.enabled) return DexCompilerOptInDecision.Disabled
        val selected = options.selectedComponent ?: return DexCompilerOptInDecision.NoSelection
        val candidates = try {
            discoverCandidates()
        } catch (_: Exception) {
            return DexCompilerOptInDecision.Unavailable(
                selected,
                DexCompilerOptInUnavailableReason.DISCOVERY_FAILED,
            )
        }
        val exactMatches = candidates.filter { it.component == selected }
        if (exactMatches.isEmpty()) {
            return DexCompilerOptInDecision.Unavailable(
                selected,
                DexCompilerOptInUnavailableReason.NO_EXACT_MATCH,
            )
        }
        if (exactMatches.size != 1) {
            return DexCompilerOptInDecision.Rejected(
                selected,
                DexCompilerOptInRejectionReason.MULTIPLE_EXACT_MATCHES,
            )
        }

        val candidate = exactMatches.single()
        val rejection = DexCompilerExperimentalPolicy.rejection(candidate, releaseBuild)
        if (rejection != null) {
            return DexCompilerOptInDecision.Rejected(
                selected,
                DexCompilerOptInRejectionReason.CANDIDATE_POLICY,
                rejection,
            )
        }
        if (!candidate.sameSignerAsHost) {
            return DexCompilerOptInDecision.Rejected(
                selected,
                DexCompilerOptInRejectionReason.HOST_SIGNER_MISMATCH,
            )
        }
        return DexCompilerOptInDecision.Selected(candidate.pin())
    }
}

/** Versioned, delimiter-safe persistence codec with no Android framework dependency. */
internal object DexCompilerComponentCodec {

    private const val FORMAT = "v1"
    private const val MAX_NAME_CHARS = 4_096

    fun encode(component: DexCompilerComponent): String {
        requireValidName(component.packageName, "package")
        requireValidName(component.className, "class")
        return "$FORMAT:${component.packageName.length}:${component.className.length}:" +
            component.packageName + component.className
    }

    fun decodeOrNull(encoded: String?): DexCompilerComponent? {
        if (encoded == null || encoded.length > MAX_ENCODED_CHARS) return null
        val fields = encoded.split(':', limit = 4)
        if (fields.size != 4 || fields[0] != FORMAT) return null
        val packageLength = canonicalLength(fields[1]) ?: return null
        val classLength = canonicalLength(fields[2]) ?: return null
        val payload = fields[3]
        if (packageLength.toLong() + classLength.toLong() != payload.length.toLong()) return null
        val packageName = payload.substring(0, packageLength)
        val className = payload.substring(packageLength)
        if (!isValidName(packageName) || !isValidName(className)) return null
        return runCatching { DexCompilerComponent(packageName, className) }.getOrNull()
    }

    private fun canonicalLength(value: String): Int? {
        if (value.isEmpty() || value.length > MAX_LENGTH_DIGITS) return null
        if (value.length > 1 && value[0] == '0') return null
        return value.toIntOrNull()?.takeIf { it in 1..MAX_NAME_CHARS }
    }

    private fun requireValidName(value: String, label: String) {
        require(isValidName(value)) { "DEX compiler component $label name is invalid" }
    }

    private fun isValidName(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_NAME_CHARS &&
            value == value.trim() &&
            value.none(Character::isISOControl)

    private const val MAX_LENGTH_DIGITS = 4
    private const val MAX_ENCODED_CHARS = MAX_NAME_CHARS * 2 + 16
}
