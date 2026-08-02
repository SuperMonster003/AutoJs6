package org.autojs.autojs.core.plugin.dex

/** Coordinates a single plugin attempt and at most one local compiler fallback. */
internal class DexCompilerFallbackController {

    data class LocalFallbackDirective(
        /** A dispatched plugin may have written partial output, so rollback precedes fallback. */
        val requiresPluginOutputRollback: Boolean,
    )

    data class Snapshot(
        val pluginDispatched: Boolean,
        val localFallbackClaimed: Boolean,
        val cancelled: Boolean,
        val finished: Boolean,
    )

    private var pluginDispatched = false
    private var localFallbackClaimed = false
    private var cancelled = false
    private var finished = false

    /**
     * Claims the only plugin dispatch. A second claim is a forbidden Binder retry, including
     * after an ambiguous dispatch failure.
     */
    @Synchronized
    fun tryBeginPluginDispatch(): Boolean {
        if (pluginDispatched || localFallbackClaimed || cancelled || finished) return false
        pluginDispatched = true
        return true
    }

    /**
     * Claims the local fallback exactly once. Cancellation never degrades into compilation by a
     * different engine.
     */
    @Synchronized
    fun tryClaimLocalFallback(): LocalFallbackDirective? {
        if (localFallbackClaimed || cancelled || finished) return null
        localFallbackClaimed = true
        return LocalFallbackDirective(requiresPluginOutputRollback = pluginDispatched)
    }

    /** Returns true only when cancellation work must be performed. */
    @Synchronized
    fun cancel(): Boolean {
        if (cancelled || finished) return false
        cancelled = true
        return true
    }

    /** Seals a successful plugin or local result. */
    @Synchronized
    fun finish(): Boolean {
        if (cancelled || finished) return false
        finished = true
        return true
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(
        pluginDispatched = pluginDispatched,
        localFallbackClaimed = localFallbackClaimed,
        cancelled = cancelled,
        finished = finished,
    )
}
