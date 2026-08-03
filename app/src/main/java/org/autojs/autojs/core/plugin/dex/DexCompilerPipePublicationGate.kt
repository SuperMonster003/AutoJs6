package org.autojs.autojs.core.plugin.dex

/**
 * Correlates the two independent success signals of a reliable-pipe transaction.
 *
 * A provider terminal callback proves only what the provider claims it wrote. EOF from the host
 * drain proves only what the host actually received. Publication may be claimed exactly once and
 * only after both summaries match. Any earlier failure claims rollback exactly once.
 */
internal class DexCompilerPipePublicationGate {

    enum class Phase {
        OPEN,
        PUBLISHING,
        PUBLISHED,
        ROLLBACK_CLAIMED,
        ROLLED_BACK,
    }

    enum class Rejection {
        DRAIN_FAILED,
        DUPLICATE_DRAIN_TERMINAL,
        DUPLICATE_PROVIDER_COMPLETION,
        HOST_ABORTED,
        SUMMARY_MISMATCH,
        PUBLICATION_FAILED,
    }

    sealed class Decision {
        object Waiting : Decision()
        data class Publish(val summary: DexCompilerStreamSummary) : Decision()
        data class Rollback(val rejection: Rejection) : Decision()
        object Ignored : Decision()
    }

    data class Snapshot(
        val phase: Phase,
        val providerSummary: DexCompilerStreamSummary?,
        val drainedSummary: DexCompilerStreamSummary?,
    )

    private var phase = Phase.OPEN
    private var providerSummary: DexCompilerStreamSummary? = null
    private var drainedSummary: DexCompilerStreamSummary? = null

    @Synchronized
    fun acceptProviderCompletion(summary: DexCompilerStreamSummary): Decision {
        if (phase != Phase.OPEN) return Decision.Ignored
        if (providerSummary != null) return reject(Rejection.DUPLICATE_PROVIDER_COMPLETION)
        providerSummary = summary
        return resolveSuccess()
    }

    @Synchronized
    fun acceptDrainSuccess(summary: DexCompilerStreamSummary): Decision {
        if (phase != Phase.OPEN) return Decision.Ignored
        if (drainedSummary != null) return reject(Rejection.DUPLICATE_DRAIN_TERMINAL)
        drainedSummary = summary
        return resolveSuccess()
    }

    @Synchronized
    fun acceptDrainFailure(): Decision {
        if (phase != Phase.OPEN) return Decision.Ignored
        if (drainedSummary != null) return reject(Rejection.DUPLICATE_DRAIN_TERMINAL)
        return reject(Rejection.DRAIN_FAILED)
    }

    @Synchronized
    fun abort(): Decision = if (phase == Phase.OPEN) {
        reject(Rejection.HOST_ABORTED)
    } else {
        Decision.Ignored
    }

    @Synchronized
    fun publicationSucceeded() {
        check(phase == Phase.PUBLISHING) { "DEX output publication was not claimed" }
        phase = Phase.PUBLISHED
    }

    @Synchronized
    fun publicationFailed(): Decision {
        if (phase != Phase.PUBLISHING) return Decision.Ignored
        return reject(Rejection.PUBLICATION_FAILED)
    }

    @Synchronized
    fun rollbackFinished(): Boolean {
        if (phase != Phase.ROLLBACK_CLAIMED) return false
        phase = Phase.ROLLED_BACK
        return true
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(phase, providerSummary, drainedSummary)

    private fun resolveSuccess(): Decision {
        val provider = providerSummary ?: return Decision.Waiting
        val drained = drainedSummary ?: return Decision.Waiting
        return if (provider == drained) {
            phase = Phase.PUBLISHING
            Decision.Publish(drained)
        } else {
            reject(Rejection.SUMMARY_MISMATCH)
        }
    }

    private fun reject(rejection: Rejection): Decision.Rollback {
        phase = Phase.ROLLBACK_CLAIMED
        return Decision.Rollback(rejection)
    }
}
