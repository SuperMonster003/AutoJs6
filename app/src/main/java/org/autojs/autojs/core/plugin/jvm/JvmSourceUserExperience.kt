package org.autojs.autojs.core.plugin.jvm

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmSourceDiagnostic
import org.autojs.plugin.jvmsource.api.JvmSourceError
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage

/** Stable, non-sensitive failure categories exposed by the explicit JVM-source entry point. */
enum class JvmSourceUserFailureCode(val stableCode: String) {
    EXPERIMENT_DISABLED("JVM_SOURCE_EXPERIMENT_DISABLED"),
    PROVIDER_NOT_SELECTED("JVM_SOURCE_PROVIDER_NOT_SELECTED"),
    PROVIDER_MISSING("JVM_SOURCE_PROVIDER_MISSING"),
    PROVIDER_DISABLED("JVM_SOURCE_PROVIDER_DISABLED"),
    PROVIDER_UNTRUSTED("JVM_SOURCE_PROVIDER_UNTRUSTED"),
    PROVIDER_INCOMPATIBLE("JVM_SOURCE_PROVIDER_INCOMPATIBLE"),
    PROVIDER_BUSY("JVM_SOURCE_PROVIDER_BUSY"),
    PROVIDER_UPDATE_DRIFT("JVM_SOURCE_PROVIDER_UPDATE_DRIFT"),
    PROVIDER_ROLLBACK_REQUIRED("JVM_SOURCE_PROVIDER_ROLLBACK_REQUIRED"),
    PROVIDER_QUARANTINED("JVM_SOURCE_PROVIDER_QUARANTINED"),
    COMPILE_FAILED("JVM_SOURCE_COMPILE_FAILED"),
    RUNTIME_FAILED("JVM_SOURCE_RUNTIME_FAILED"),
    CANCELLED("JVM_SOURCE_CANCELLED"),
    TIMEOUT("JVM_SOURCE_TIMEOUT"),
    HOST_UNAVAILABLE("JVM_SOURCE_HOST_UNAVAILABLE"),
}

enum class JvmSourceRecoveryAction {
    ENABLE_EXPERIMENT,
    SELECT_PROVIDER,
    INSTALL_PROVIDER,
    ENABLE_PROVIDER,
    RESTORE_TRUSTED_PROVIDER,
    UPDATE_HOST_OR_PROVIDER,
    RETRY_LATER,
    FIX_SOURCE,
    REVIEW_RUNTIME_FAILURE,
    NONE,
}

/**
 * Containment state is deliberately coarse. It never exposes a component, signer, Binder token,
 * UID, PID, process name, path, or provider-supplied text.
 */
enum class JvmSourceContainment {
    NOT_DISPATCHED,
    RETIRED,
    QUARANTINED,
}

data class JvmSourceUserFailure(
    val code: JvmSourceUserFailureCode,
    val message: String,
    val recoveryAction: JvmSourceRecoveryAction,
    val retryable: Boolean,
    val providerErrorCode: String? = null,
    val containment: JvmSourceContainment = JvmSourceContainment.NOT_DISPATCHED,
) {
    /** JVM source must never fall through to Rhino, Node, or another script engine. */
    val scriptEngineFallbackAllowed: Boolean = false

    init {
        require(message.isNotBlank())
        require(providerErrorCode == null || SAFE_CODE.matches(providerErrorCode))
    }

    fun withContainment(value: JvmSourceContainment): JvmSourceUserFailure = copy(containment = value)

    private companion object {
        val SAFE_CODE = Regex("[A-Z][A-Z0-9_]{0,63}")
    }
}

/** Exception safe to surface through ScriptEngineService and user-visible diagnostics. */
open class JvmSourceUserFacingException(
    val failure: JvmSourceUserFailure,
) : RuntimeException("[${failure.code.stableCode}] ${failure.message}")

data class JvmSourcePublicDiagnostic(
    val severity: JvmDiagnosticSeverity,
    val code: String,
    val sourceFileName: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
)

/**
 * Projects provider diagnostics onto the user-visible shape. The compiler's own wording is kept so
 * a compilation error is actionable; the provider redacts its private paths, digests, and process
 * identity before the text crosses Protocol V1, and this projection re-checks the residual shape:
 * absolute paths, digest-like runs, and UID/PID tokens are dropped even if a provider emits them.
 * zh-CN: 保留编译器原始措辞使报错可操作; provider 已脱敏, 此处再做一次残余形状校验.
 */
internal object JvmSourcePublicDiagnosticPolicy {
    private val SAFE_CODES = setOf(
        "ECJ_ERROR",
        "ECJ_WARNING",
        "ECJ_INFO",
        "JAVA_RUNTIME_EXCEPTION",
        "KOTLIN_ERROR",
        "KOTLIN_WARNING",
        "KOTLIN_INFO",
        "KOTLIN_RUNTIME_EXCEPTION",
    )
    private val SAFE_SOURCE_FILE = Regex("[A-Za-z0-9_.$-]{1,255}")
    private val UNSAFE_TEXT = Regex(
        "[A-Za-z]:[\\\\/]|(?:/[\\w.@$+~%-]+){2,}|\\b[0-9a-fA-F]{32,}\\b|" +
            "\\b(?:uid|pid|component|signer|classpath)\\s*[=:]|\\bBinder@[0-9a-fA-F]+",
        RegexOption.IGNORE_CASE,
    )

    fun project(value: JvmSourceDiagnostic): JvmSourcePublicDiagnostic {
        val safeCode = value.code.takeIf(SAFE_CODES::contains) ?: "COMPILER_DIAGNOSTIC"
        val safeLocation = value.sourceFileName?.let(SAFE_SOURCE_FILE::matches) == true &&
            value.line in 1..MAX_SOURCE_POSITION && value.column in 1..MAX_SOURCE_POSITION
        return JvmSourcePublicDiagnostic(
            severity = value.severity,
            code = safeCode,
            sourceFileName = value.sourceFileName.takeIf { safeLocation },
            line = value.line.takeIf { safeLocation },
            column = value.column.takeIf { safeLocation },
            message = projectMessage(value),
        )
    }

    private fun projectMessage(value: JvmSourceDiagnostic): String {
        val language = if (value.code.startsWith("KOTLIN_")) "Kotlin" else "Java"
        val fallback = when (value.severity) {
            JvmDiagnosticSeverity.INFO -> "$language compiler diagnostic"
            JvmDiagnosticSeverity.WARNING -> "$language compiler warning"
            JvmDiagnosticSeverity.ERROR -> "$language compilation error"
        }
        val text = value.message.trim()
        if (text.isEmpty() || UNSAFE_TEXT.containsMatchIn(text)) return fallback
        val bounded = if (text.codePointCount(0, text.length) <= MAX_MESSAGE_CODE_POINTS) {
            text
        } else {
            text.substring(0, text.offsetByCodePoints(0, MAX_MESSAGE_CODE_POINTS - 1)) + "~"
        }
        return bounded
    }

    /** Keeps the Protocol V1 container while removing every provider-controlled text/location. */
    fun sanitize(value: JvmSourceDiagnostic): JvmSourceDiagnostic = project(value).let { projected ->
        JvmSourceDiagnostic(
            requestId = value.requestId,
            severity = projected.severity,
            code = projected.code,
            message = projected.message,
            sourceFileName = projected.sourceFileName,
            line = projected.line,
            column = projected.column,
        )
    }

    private const val MAX_SOURCE_POSITION = 1_000_000
    private const val MAX_MESSAGE_CODE_POINTS = 4_096
}

/** Product policy shared by discovery, lifecycle revalidation, and provider terminal mapping. */
internal object JvmSourceUserExperiencePolicy {
    fun experimentDisabled(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.EXPERIMENT_DISABLED,
        "JVM source execution is disabled",
        JvmSourceRecoveryAction.ENABLE_EXPERIMENT,
    )

    fun providerNotSelected(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_NOT_SELECTED,
        "Select a JVM source provider before running this file",
        JvmSourceRecoveryAction.SELECT_PROVIDER,
    )

    fun providerAdmission(
        candidates: Collection<JvmSourceServiceCandidate>,
        selected: JvmSourceComponent,
    ): JvmSourceUserFailure? {
        val exact = candidates.filter { it.component == selected }
        if (exact.isEmpty()) return providerMissing()
        if (exact.size != 1) return providerIncompatible()
        return when (JvmSourceProviderPolicy.rejection(exact.single(), selected)) {
            null -> null
            JvmSourceCandidateRejection.DISABLED -> providerDisabled()
            JvmSourceCandidateRejection.UNAUTHORIZED,
            JvmSourceCandidateRejection.WRONG_PERMISSION,
            JvmSourceCandidateRejection.NOT_EXPORTED,
            -> providerUntrusted()
            JvmSourceCandidateRejection.NOT_EXACT_SELECTION,
            JvmSourceCandidateRejection.WRONG_ACTION,
            JvmSourceCandidateRejection.WRONG_COMPILER_PROCESS,
            JvmSourceCandidateRejection.INVALID_IDENTITY,
            -> providerIncompatible()
        }
    }

    /**
     * Classifies the fresh-install case where auto-selection could not produce a component. A
     * single discovered service still carries enough coarse admission state to distinguish a
     * disabled or incorrectly signed provider from a provider that is not installed at all.
     */
    fun providerUnavailableWithoutSelection(
        candidates: Collection<JvmSourceServiceCandidate>,
    ): JvmSourceUserFailure {
        if (candidates.isEmpty()) return providerMissing()
        val single = candidates.singleOrNull() ?: return providerNotSelected()
        return when (JvmSourceProviderPolicy.rejection(single, single.component)) {
            null -> providerNotSelected()
            JvmSourceCandidateRejection.DISABLED -> providerDisabled()
            JvmSourceCandidateRejection.UNAUTHORIZED,
            JvmSourceCandidateRejection.WRONG_PERMISSION,
            JvmSourceCandidateRejection.NOT_EXPORTED,
            -> providerUntrusted()
            JvmSourceCandidateRejection.NOT_EXACT_SELECTION,
            JvmSourceCandidateRejection.WRONG_ACTION,
            JvmSourceCandidateRejection.WRONG_COMPILER_PROCESS,
            JvmSourceCandidateRejection.INVALID_IDENTITY,
            -> providerIncompatible()
        }
    }

    fun lifecycleLoss(loss: JvmSourceProviderLifecyclePolicy.Loss): JvmSourceUserFailure = when (loss) {
        JvmSourceProviderLifecyclePolicy.Loss.UNINSTALLED -> providerMissing()
        JvmSourceProviderLifecyclePolicy.Loss.DISABLED -> providerDisabled()
        JvmSourceProviderLifecyclePolicy.Loss.UPDATED -> failure(
            JvmSourceUserFailureCode.PROVIDER_UPDATE_DRIFT,
            "The selected JVM source provider changed; retry after verification",
            JvmSourceRecoveryAction.RETRY_LATER,
            retryable = true,
        )
        JvmSourceProviderLifecyclePolicy.Loss.ROLLED_BACK -> failure(
            JvmSourceUserFailureCode.PROVIDER_ROLLBACK_REQUIRED,
            "Restore an approved JVM source provider version before retrying",
            JvmSourceRecoveryAction.RESTORE_TRUSTED_PROVIDER,
        )
        JvmSourceProviderLifecyclePolicy.Loss.IDENTITY_CHANGED -> providerUntrusted()
    }

    fun providerError(
        error: JvmSourceError,
        language: JvmSourceLanguage = JvmSourceLanguage.JAVA,
    ): JvmSourceUserFailure = when (error.code) {
        JvmSourceErrorCode.BUSY -> providerBusy()
        JvmSourceErrorCode.TIMEOUT -> timeout()
        JvmSourceErrorCode.COMPILATION_FAILED,
        JvmSourceErrorCode.DEXING_FAILED,
        JvmSourceErrorCode.SOURCE_TOO_LARGE,
        JvmSourceErrorCode.ENTRY_POINT_MISSING,
        JvmSourceErrorCode.ENTRY_POINT_AMBIGUOUS,
        JvmSourceErrorCode.ENTRY_POINT_ABI_INCOMPATIBLE,
        -> failure(
            JvmSourceUserFailureCode.COMPILE_FAILED,
            "${sourceName(language)} source compilation failed",
            JvmSourceRecoveryAction.FIX_SOURCE,
            providerErrorCode = error.code.name,
        )
        JvmSourceErrorCode.UNSUPPORTED_PROTOCOL,
        JvmSourceErrorCode.UNSUPPORTED_LANGUAGE,
        -> providerIncompatible(error.code.name)
        JvmSourceErrorCode.EXECUTION_FAILED,
        JvmSourceErrorCode.OUTPUT_LIMIT_EXCEEDED,
        JvmSourceErrorCode.WORKER_DIED,
        JvmSourceErrorCode.HOST_CALL_REJECTED,
        JvmSourceErrorCode.CLASS_LOADING_FAILED,
        JvmSourceErrorCode.INTERNAL,
        -> failure(
            JvmSourceUserFailureCode.RUNTIME_FAILED,
            "${sourceName(language)} source execution failed",
            JvmSourceRecoveryAction.REVIEW_RUNTIME_FAILURE,
            retryable = error.retryable,
            providerErrorCode = error.code.name,
        )
        JvmSourceErrorCode.ARTIFACT_INVALID -> artifactIntegrityFailure(error.phase, language)
        JvmSourceErrorCode.INVALID_REQUEST -> invalidRequest(error.phase, language)
    }

    fun sourcePolicyViolation(
        language: JvmSourceLanguage,
    ): JvmSourceUserFailure = invalidRequest(JvmSourceFailurePhase.INPUT, language)

    private fun invalidRequest(
        phase: JvmSourceFailurePhase,
        language: JvmSourceLanguage,
    ): JvmSourceUserFailure = when (phase) {
        JvmSourceFailurePhase.INPUT,
        JvmSourceFailurePhase.COMPILATION,
        -> failure(
            JvmSourceUserFailureCode.COMPILE_FAILED,
            "${sourceName(language)} source does not satisfy the supported source profile",
            JvmSourceRecoveryAction.FIX_SOURCE,
            providerErrorCode = JvmSourceErrorCode.INVALID_REQUEST.name,
        )
        else -> providerIncompatible(JvmSourceErrorCode.INVALID_REQUEST.name)
    }

    /**
     * Artifact integrity failures are never attributed to editable source. Before worker dispatch
     * they represent unavailable provider infrastructure; after dispatch they are a retryable
     * runtime isolation failure. Actual quarantine remains a separate host containment decision.
     */
    private fun artifactIntegrityFailure(
        phase: JvmSourceFailurePhase,
        language: JvmSourceLanguage,
    ): JvmSourceUserFailure = when (phase) {
        JvmSourceFailurePhase.WORKER_START,
        JvmSourceFailurePhase.EXECUTION,
        JvmSourceFailurePhase.HOST_BRIDGE,
        -> failure(
            JvmSourceUserFailureCode.RUNTIME_FAILED,
            "${sourceName(language)} runtime artifact validation failed",
            JvmSourceRecoveryAction.RETRY_LATER,
            retryable = true,
            providerErrorCode = JvmSourceErrorCode.ARTIFACT_INVALID.name,
        )
        else -> failure(
            JvmSourceUserFailureCode.HOST_UNAVAILABLE,
            "JVM source artifact validation failed",
            JvmSourceRecoveryAction.RETRY_LATER,
            retryable = true,
            providerErrorCode = JvmSourceErrorCode.ARTIFACT_INVALID.name,
        )
    }

    fun providerBusy(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_BUSY,
        "The JVM source provider is busy",
        JvmSourceRecoveryAction.RETRY_LATER,
        retryable = true,
    )

    fun cancelled(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.CANCELLED,
        "JVM source execution was cancelled",
        JvmSourceRecoveryAction.NONE,
    )

    fun timeout(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.TIMEOUT,
        "JVM source execution timed out",
        JvmSourceRecoveryAction.RETRY_LATER,
        retryable = true,
        providerErrorCode = JvmSourceErrorCode.TIMEOUT.name,
    )

    fun hostUnavailable(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.HOST_UNAVAILABLE,
        "The JVM source host is unavailable",
        JvmSourceRecoveryAction.RETRY_LATER,
        retryable = true,
    )

    fun providerIsolationViolation(): JvmSourceUserFailure = providerUntrusted()

    fun providerQuarantined(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_QUARANTINED,
        "The JVM source provider was quarantined after a protocol failure",
        JvmSourceRecoveryAction.RETRY_LATER,
        retryable = true,
    ).withContainment(JvmSourceContainment.QUARANTINED)

    private fun providerMissing(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_MISSING,
        "Install the JVM source provider before running this file",
        JvmSourceRecoveryAction.INSTALL_PROVIDER,
    )

    private fun providerDisabled(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_DISABLED,
        "Enable the JVM source provider before running this file",
        JvmSourceRecoveryAction.ENABLE_PROVIDER,
    )

    private fun providerUntrusted(): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_UNTRUSTED,
        "The JVM source provider is not trusted; install one signed for this AutoJs6 build",
        JvmSourceRecoveryAction.RESTORE_TRUSTED_PROVIDER,
    )

    private fun providerIncompatible(providerErrorCode: String? = null): JvmSourceUserFailure = failure(
        JvmSourceUserFailureCode.PROVIDER_INCOMPATIBLE,
        "The selected JVM source provider is incompatible with this host",
        JvmSourceRecoveryAction.UPDATE_HOST_OR_PROVIDER,
        providerErrorCode = providerErrorCode,
    )

    private fun failure(
        code: JvmSourceUserFailureCode,
        message: String,
        action: JvmSourceRecoveryAction,
        retryable: Boolean = false,
        providerErrorCode: String? = null,
    ) = JvmSourceUserFailure(code, message, action, retryable, providerErrorCode)

    private fun sourceName(language: JvmSourceLanguage): String = when (language) {
        JvmSourceLanguage.JAVA -> "Java"
        JvmSourceLanguage.KOTLIN -> "Kotlin"
    }
}
