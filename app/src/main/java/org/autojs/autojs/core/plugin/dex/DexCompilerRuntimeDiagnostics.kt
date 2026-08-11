package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnosticSeverity
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import java.util.concurrent.atomic.AtomicReference

/**
 * Redacted, process-local diagnostics for the most recent real provider failure.
 *
 * Raw provider messages can contain private paths or source details. They remain available to the
 * bounded protocol outcome, but this summary deliberately retains only validated enum values,
 * counts, and a few bounded diagnostic codes. One shared producer records one snapshot; individual
 * single-flight waiters never overwrite it.
 */
internal data class DexCompilerDiagnosticSummary(
    val infoCount: Int,
    val warningCount: Int,
    val errorCount: Int,
    val codes: List<String>,
    val additionalCodeCount: Int,
    val truncated: Boolean,
) {
    override fun toString(): String =
        "DiagnosticSummary(info=$infoCount, warning=$warningCount, error=$errorCount, " +
            "codes=$codes, additionalCodes=$additionalCodeCount, truncated=$truncated)"

    fun preferenceSuffix(
        failure: DexCompilerExperimentalFailure,
        code: DexCompilerErrorCode,
        phase: DexCompilerFailurePhase,
        retryable: Boolean,
    ): String = buildString {
        append("R2 LAST_PROVIDER_FALLBACK/PROCESS_ONLY · ")
        append(failure.name)
        append('/')
        append(code.name)
        append('/')
        append(phase.name)
        if (errorCount + warningCount + infoCount > 0) {
            append(" · E")
            append(errorCount)
            append(" W")
            append(warningCount)
            append(" I")
            append(infoCount)
        }
        if (codes.isNotEmpty()) {
            append(" · ")
            append(codes.joinToString(","))
            if (additionalCodeCount > 0) {
                append(" +")
                append(additionalCodeCount)
            }
        }
        if (truncated) append(" · TRUNCATED")
        if (retryable) append(" · RETRYABLE")
    }.take(MAX_PREFERENCE_SUFFIX_CHARS)

    companion object {
        fun from(diagnostics: Collection<DexCompilerDiagnostic>): DexCompilerDiagnosticSummary {
            var infoCount = 0
            var warningCount = 0
            var errorCount = 0
            var truncated = false
            val allCodes = LinkedHashSet<String>()
            diagnostics.forEach { diagnostic ->
                when (diagnostic.severity) {
                    DexCompilerDiagnosticSeverity.INFO -> infoCount++
                    DexCompilerDiagnosticSeverity.WARNING -> warningCount++
                    DexCompilerDiagnosticSeverity.ERROR -> errorCount++
                }
                if (diagnostic.code == TRUNCATED_DIAGNOSTIC_CODE) truncated = true
                allCodes += diagnostic.code.take(MAX_VISIBLE_CODE_CHARS)
            }
            val visibleCodes = allCodes.take(MAX_VISIBLE_CODES)
            return DexCompilerDiagnosticSummary(
                infoCount = infoCount,
                warningCount = warningCount,
                errorCount = errorCount,
                codes = visibleCodes,
                additionalCodeCount = (allCodes.size - visibleCodes.size).coerceAtLeast(0),
                truncated = truncated,
            )
        }

        private const val MAX_VISIBLE_CODES = 3
        private const val MAX_VISIBLE_CODE_CHARS = 32
        private const val MAX_PREFERENCE_SUFFIX_CHARS = 192
        private const val TRUNCATED_DIAGNOSTIC_CODE = "D8.DIAGNOSTICS_TRUNCATED"
    }
}

internal data class DexCompilerLastProviderFailure(
    val failure: DexCompilerExperimentalFailure,
    val code: DexCompilerErrorCode,
    val phase: DexCompilerFailurePhase,
    val retryable: Boolean,
    val diagnostics: DexCompilerDiagnosticSummary,
) {
    fun preferenceSuffix(): String = diagnostics.preferenceSuffix(failure, code, phase, retryable)
}

internal object DexCompilerRuntimeDiagnostics {
    private val lastProviderFailure = AtomicReference<DexCompilerLastProviderFailure?>()

    fun record(outcome: DexCompilerExperimentalOutcome.UseLocalCompiler) {
        val remote = outcome.remoteFailure ?: return
        lastProviderFailure.set(
            DexCompilerLastProviderFailure(
                failure = outcome.failure,
                code = remote.code,
                phase = remote.phase,
                retryable = remote.retryable,
                diagnostics = DexCompilerDiagnosticSummary.from(remote.diagnostics),
            ),
        )
    }

    fun latestSummary(): String? = lastProviderFailure.get()?.preferenceSuffix()

    internal fun latestForTest(): DexCompilerLastProviderFailure? = lastProviderFailure.get()

    internal fun resetForTest() {
        lastProviderFailure.set(null)
    }
}
