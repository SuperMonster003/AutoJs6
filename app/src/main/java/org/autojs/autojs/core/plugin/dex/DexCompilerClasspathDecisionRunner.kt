package org.autojs.autojs.core.plugin.dex

import org.autojs.autojs.runtime.exception.ScriptInterruptedException

/**
 * Consumes one dormant ordered-classpath decision while preserving its one retained-input owner.
 * Cancellation is terminal and can never enter [loadLocal].
 */
internal object DexCompilerClasspathDecisionRunner {

    fun <T> consume(
        decision: DexCompilerRuntimeDecision,
        loadPublished: (DexCompilerVerifiedArtifact) -> T,
        loadLocal: (DexCompilerRetainedInput) -> T,
        checkInterrupted: (Throwable?) -> Unit,
        onUseLocal: (DexCompilerRuntimeDecision.UseLocal) -> Unit = {},
        onPublishedLoadFailure: (Exception) -> Unit = {},
        cancelled: () -> T,
    ): T = when (decision) {
        is DexCompilerRuntimeDecision.Published -> useOwned(decision.fallbackInput) { retained ->
            checkInterrupted(null)
            try {
                loadPublished(decision.artifact)
            } catch (fatal: VirtualMachineError) {
                throw fatal
            } catch (loadFailure: Exception) {
                checkInterrupted(loadFailure)
                try {
                    decision.artifact.invalidate()
                } catch (fatal: VirtualMachineError) {
                    throw fatal
                } catch (_: Exception) {
                    // Best effort: the exact generation handle remains isolated.
                }
                onPublishedLoadFailure(loadFailure)
                checkInterrupted(null)
                loadLocal(retained)
            }
        }
        is DexCompilerRuntimeDecision.UseLocal -> {
            val retained = checkNotNull(decision.fallbackInput) {
                "Classpath fallback has no retained input set"
            }
            useOwned(retained) { input ->
                checkInterrupted(null)
                onUseLocal(decision)
                checkInterrupted(null)
                loadLocal(input)
            }
        }
        DexCompilerRuntimeDecision.Cancelled -> cancelled()
    }

    private inline fun <T> useOwned(
        retained: DexCompilerRetainedInput,
        block: (DexCompilerRetainedInput) -> T,
    ): T {
        var primaryFailure: Throwable? = null
        try {
            return block(retained)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                retained.close()
            } catch (closeFailure: Throwable) {
                val primary = primaryFailure
                val closeWasInterrupted = closeFailure is InterruptedException ||
                    ScriptInterruptedException.causedByInterrupt(closeFailure)
                if (closeWasInterrupted) Thread.currentThread().interrupt()
                if (primary == null) throw closeFailure
                if (closeFailure === primary) {
                    // The same terminal is already escaping.
                } else if (closeFailure is VirtualMachineError && primary !is VirtualMachineError) {
                    addSuppressedWithoutThrowing(closeFailure, primary)
                    throw closeFailure
                } else if (closeWasInterrupted && primary !is VirtualMachineError) {
                    addSuppressedWithoutThrowing(closeFailure, primary)
                    throw closeFailure
                } else {
                    addSuppressedWithoutThrowing(primary, closeFailure)
                }
            }
        }
    }

    private fun addSuppressedWithoutThrowing(primary: Throwable, suppressed: Throwable) {
        if (primary === suppressed) return
        try {
            primary.addSuppressed(suppressed)
        } catch (_: Throwable) {
            // Preserve the selected terminal and ownership outcome.
        }
    }
}
