package org.autojs.autojs.core.plugin.dex

import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnostic
import org.autojs.plugin.dexcompiler.api.DexCompilerDiagnosticSeverity
import org.autojs.plugin.dexcompiler.api.DexCompilerErrorCode
import org.autojs.plugin.dexcompiler.api.DexCompilerFailurePhase
import java.util.concurrent.atomic.AtomicReference

/** Redacted aggregate for diagnostics returned by a provider. Raw messages are never retained here. */
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
        private const val TRUNCATED_DIAGNOSTIC_CODE = "D8.DIAGNOSTICS_TRUNCATED"
    }
}

internal enum class DexCompilerRuntimePath {
    PLUGIN,
    BUILTIN,
    CACHE_HIT,
}

internal enum class DexCompilerRuntimeHint {
    BUSY,
    TIMEOUT,
    FALLBACK,
}

/** Process-local description of the most recent successfully adopted raw-JAR load route. */
internal data class DexCompilerLastRoute(
    val path: DexCompilerRuntimePath,
    val hint: DexCompilerRuntimeHint? = null,
)

/**
 * Redacted, process-local snapshot of the most recent real provider failure.
 *
 * This preserves the earlier R2 observability contract independently of the R5 route snapshot.
 * It intentionally contains no request id, path, input/output digest, provider message, timestamp,
 * or persistent key. Developer options wrap only the bounded enum/count/code tokens returned by
 * [failureDetailTokens] in localized text.
 */
internal data class DexCompilerLastProviderFailure(
    val failure: DexCompilerExperimentalFailure,
    val code: DexCompilerErrorCode,
    val phase: DexCompilerFailurePhase,
    val retryable: Boolean,
    val diagnostics: DexCompilerDiagnosticSummary,
) {
    /** Locale-neutral, bounded tokens for the pre-existing R2 developer failure summary. */
    fun failureDetailTokens(): String =
        buildList {
            add(
                listOf(failure.name, code.name, phase.name)
                    .joinToString("/"),
            )
            if (diagnostics.infoCount + diagnostics.warningCount + diagnostics.errorCount > 0) {
                add("E${diagnostics.errorCount} W${diagnostics.warningCount} I${diagnostics.infoCount}")
            }
            if (diagnostics.codes.isNotEmpty()) {
                add(
                    buildString {
                        append(diagnostics.codes.joinToString(","))
                        if (diagnostics.additionalCodeCount > 0) {
                            append(" +")
                            append(diagnostics.additionalCodeCount)
                        }
                    },
                )
            }
            if (diagnostics.truncated) add("TRUNCATED")
            if (retryable) add("RETRYABLE")
        }.joinToString(" | ").take(MAX_FAILURE_DETAIL_CHARS)

    private companion object {
        const val MAX_FAILURE_DETAIL_CHARS = 192
    }
}

internal object DexCompilerRuntimeDiagnostics {
    private val lastRoute = AtomicReference<DexCompilerLastRoute?>()
    private val lastProviderFailure = AtomicReference<DexCompilerLastProviderFailure?>()

    /** Records one shared producer failure; individual single-flight waiters never overwrite it. */
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

    fun recordPublished(cacheHit: Boolean) {
        lastRoute.set(
            DexCompilerLastRoute(
                path = if (cacheHit) DexCompilerRuntimePath.CACHE_HIT else DexCompilerRuntimePath.PLUGIN,
            ),
        )
    }

    fun recordBuiltIn() {
        lastRoute.set(DexCompilerLastRoute(path = DexCompilerRuntimePath.BUILTIN))
    }

    fun recordFallback(decision: DexCompilerRuntimeDecision.UseLocal) {
        val remote = decision.remoteFailure
        val failure = decision.experimentalFailure
        val hint = when {
            failure == DexCompilerExperimentalFailure.BUSY || remote?.code == DexCompilerErrorCode.BUSY ->
                DexCompilerRuntimeHint.BUSY
            failure == DexCompilerExperimentalFailure.TIMEOUT -> DexCompilerRuntimeHint.TIMEOUT
            else -> DexCompilerRuntimeHint.FALLBACK
        }
        lastRoute.set(
            DexCompilerLastRoute(
                path = DexCompilerRuntimePath.BUILTIN,
                hint = hint,
            ),
        )
    }

    fun recordPublishedLoadFallback() {
        lastRoute.set(
            DexCompilerLastRoute(
                path = DexCompilerRuntimePath.BUILTIN,
                hint = DexCompilerRuntimeHint.FALLBACK,
            ),
        )
    }

    fun latest(): DexCompilerLastRoute? = lastRoute.get()

    fun latestFailureDetails(): String? = lastProviderFailure.get()?.failureDetailTokens()

    internal fun latestFailureForTest(): DexCompilerLastProviderFailure? = lastProviderFailure.get()

    internal fun resetForTest() {
        lastRoute.set(null)
        lastProviderFailure.set(null)
    }
}
