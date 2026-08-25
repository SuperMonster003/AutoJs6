package org.autojs.autojs.core.plugin.r8

import java.util.concurrent.atomic.AtomicReference

internal enum class R8CompilerAttemptPhase {
    SELECTED,
    NEGOTIATED,
    DISPATCHED,
    COMPLETED,
    FAILED,
}

internal data class R8CompilerFailureDirective(
    val phase: R8CompilerAttemptPhase,
    val propagateAsR8Failure: Boolean = true,
    val semanticFallbackAllowed: Boolean = false,
)

/**
 * One explicit R8 attempt can only complete as R8 or fail as R8. There is no directive that can
 * enter the legacy local compiler or the remote DEX provider, including before dispatch.
 */
internal class R8CompilerDispatchFirewall {
    private val phase = AtomicReference(R8CompilerAttemptPhase.SELECTED)

    val currentPhase: R8CompilerAttemptPhase
        get() = phase.get()

    fun negotiated() {
        transition(R8CompilerAttemptPhase.SELECTED, R8CompilerAttemptPhase.NEGOTIATED)
    }

    fun dispatched() {
        transition(R8CompilerAttemptPhase.NEGOTIATED, R8CompilerAttemptPhase.DISPATCHED)
    }

    fun completed() {
        transition(R8CompilerAttemptPhase.DISPATCHED, R8CompilerAttemptPhase.COMPLETED)
    }

    /** A verified R8 cache generation completes without reopening a provider session. */
    fun completedFromCache() {
        transition(R8CompilerAttemptPhase.NEGOTIATED, R8CompilerAttemptPhase.COMPLETED)
    }

    fun failed(): R8CompilerFailureDirective {
        while (true) {
            val observed = phase.get()
            require(observed != R8CompilerAttemptPhase.COMPLETED) { "Completed R8 attempt cannot fail" }
            if (observed == R8CompilerAttemptPhase.FAILED ||
                phase.compareAndSet(observed, R8CompilerAttemptPhase.FAILED)
            ) {
                return R8CompilerFailureDirective(observed)
            }
        }
    }

    private fun transition(expected: R8CompilerAttemptPhase, next: R8CompilerAttemptPhase) {
        check(phase.compareAndSet(expected, next)) {
            "Illegal R8 attempt transition: ${phase.get()} -> $next"
        }
    }
}
