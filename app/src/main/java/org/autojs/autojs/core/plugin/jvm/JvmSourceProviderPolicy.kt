package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmSourceContract

internal data class JvmSourceComponent(
    val packageName: String,
    val className: String,
) {
    init {
        require(packageName.isNotBlank())
        require(className.isNotBlank())
    }

    val flattened: String
        get() = "$packageName/$className"

    companion object {
        fun parseExact(value: String?): JvmSourceComponent? {
            if (value.isNullOrBlank() || value != value.trim()) return null
            val separator = value.indexOf('/')
            if (separator <= 0 || separator != value.lastIndexOf('/') || separator == value.lastIndex) return null
            val packageName = value.substring(0, separator)
            val className = value.substring(separator + 1)
            if (!PACKAGE_NAME.matches(packageName) || !CLASS_NAME.matches(className)) return null
            return JvmSourceComponent(packageName, className).takeIf { it.flattened == value }
        }

        private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
        private val CLASS_NAME = Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)+")
    }
}

internal data class JvmSourceServiceCandidate(
    val component: JvmSourceComponent,
    val declaredAction: String,
    val exported: Boolean,
    val applicationEnabled: Boolean,
    val serviceEnabled: Boolean,
    val pluginEnabled: Boolean,
    val providerAuthorized: Boolean,
    val requiredPermission: String?,
    val compilerProcessName: String,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val signerSha256: Set<String>,
) {
    fun pin(): JvmSourcePinnedIdentity = JvmSourcePinnedIdentity(
        component = component,
        compilerProcessName = compilerProcessName,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signerSha256.map(String::lowercase).distinct().sorted(),
    )
}

internal data class JvmSourcePinnedIdentity(
    val component: JvmSourceComponent,
    val compilerProcessName: String,
    val packageUid: Int,
    val versionCode: Long,
    val lastUpdateTime: Long,
    val signerSha256: List<String>,
)

internal enum class JvmSourceCandidateRejection {
    NOT_EXACT_SELECTION,
    WRONG_ACTION,
    NOT_EXPORTED,
    DISABLED,
    UNAUTHORIZED,
    WRONG_PERMISSION,
    WRONG_COMPILER_PROCESS,
    INVALID_IDENTITY,
}

/** Admission remains exact-component-only after the single admissible provider is auto-selected. */
internal object JvmSourceProviderPolicy {

    fun rejection(
        candidate: JvmSourceServiceCandidate,
        selected: JvmSourceComponent,
    ): JvmSourceCandidateRejection? = when {
        candidate.component != selected -> JvmSourceCandidateRejection.NOT_EXACT_SELECTION
        candidate.declaredAction != JvmSourceContract.SERVICE_ACTION -> JvmSourceCandidateRejection.WRONG_ACTION
        !candidate.exported -> JvmSourceCandidateRejection.NOT_EXPORTED
        !candidate.applicationEnabled || !candidate.serviceEnabled || !candidate.pluginEnabled ->
            JvmSourceCandidateRejection.DISABLED
        !candidate.providerAuthorized -> JvmSourceCandidateRejection.UNAUTHORIZED
        candidate.requiredPermission != JvmSourceContract.PLUGIN_PERMISSION ->
            JvmSourceCandidateRejection.WRONG_PERMISSION
        candidate.compilerProcessName != "${candidate.component.packageName}:compiler" ->
            JvmSourceCandidateRejection.WRONG_COMPILER_PROCESS
        candidate.packageUid <= 0 || candidate.versionCode <= 0L || candidate.lastUpdateTime <= 0L ||
            candidate.signerSha256.isEmpty() || candidate.signerSha256.any { !SHA256.matches(it) } ->
            JvmSourceCandidateRejection.INVALID_IDENTITY
        else -> null
    }

    fun selectAndPin(
        candidates: Collection<JvmSourceServiceCandidate>,
        selected: JvmSourceComponent,
    ): JvmSourcePinnedIdentity? = candidates
        .filter { it.component == selected }
        .singleOrNull()
        ?.takeIf { rejection(it, selected) == null }
        ?.pin()

    fun revalidate(
        pinned: JvmSourcePinnedIdentity,
        current: JvmSourceServiceCandidate?,
    ): Boolean = current != null &&
        rejection(current, pinned.component) == null &&
        current.pin() == pinned

    private val SHA256 = Regex("[0-9a-fA-F]{64}")
}

/** Ordinary plugin-center grants never authorize compiler/executor code in R1. */
internal object JvmSourceCompilerTrustPolicy {
    fun isAuthorized(sameSignerAsHost: Boolean): Boolean = sameSignerAsHost
}
