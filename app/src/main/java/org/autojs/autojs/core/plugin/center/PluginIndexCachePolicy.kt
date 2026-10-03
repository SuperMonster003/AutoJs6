package org.autojs.autojs.core.plugin.center

/** A successful source response is a complete snapshot, including an empty one. */
internal object PluginIndexCachePolicy {
    const val MAX_AGE_MS = 60 * 60 * 1000L

    fun isFresh(fetchedAtMs: Long?, nowMs: Long): Boolean =
        fetchedAtMs != null && fetchedAtMs > 0 && nowMs >= fetchedAtMs && nowMs - fetchedAtMs < MAX_AGE_MS

    fun wasAttemptRecent(attemptedAtMs: Long, nowMs: Long, intervalMs: Long): Boolean =
        attemptedAtMs > 0 && nowMs >= attemptedAtMs && nowMs - attemptedAtMs < intervalMs

    fun <T, S> replaceSuccessfulSources(
        cached: List<T>,
        successful: Map<S, List<T>>,
        sourceOf: (T) -> S,
    ): List<T> = successful.values.flatten() + cached.filterNot { sourceOf(it) in successful }
}
