package org.autojs.autojs.core.plugin.docs

import java.util.concurrent.atomic.AtomicLong

/**
 * Prevents a verification that overlapped package invalidation from republishing stale content.
 *
 * Invalidation deliberately does not take the caller's verification lock: package broadcasts can
 * therefore invalidate in constant time without waiting for a full content hash on the main
 * thread. Entries carry their generation so that even a stale write racing after [invalidate]
 * cannot be consumed by the next inspection.
 */
internal class OfflineDocsVerificationCache<T> {

    private val generation = AtomicLong(0L)

    @Volatile
    private var entry: Entry<T>? = null

    fun snapshotGeneration(): Long = generation.get()

    fun isCurrent(expectedGeneration: Long): Boolean =
        generation.get() == expectedGeneration

    fun get(expectedGeneration: Long, identity: String): T? {
        if (!isCurrent(expectedGeneration)) return null
        return entry
            ?.takeIf { cached ->
                cached.generation == expectedGeneration && cached.identity == identity
            }
            ?.value
    }

    fun putIfCurrent(expectedGeneration: Long, identity: String, value: T): Boolean {
        if (!isCurrent(expectedGeneration)) return false
        entry = Entry(expectedGeneration, identity, value)
        return isCurrent(expectedGeneration)
    }

    fun invalidate() {
        generation.incrementAndGet()
        entry = null
    }

    private data class Entry<T>(
        val generation: Long,
        val identity: String,
        val value: T,
    )
}
