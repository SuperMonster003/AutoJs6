package org.autojs.autojs.core.plugin.typescript

import java.io.Closeable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retains at most one live binding and fences acquisitions racing with release/replacement. */
internal class TypeScriptCompilerWarmBindingRetention<T : Closeable>(
    private val isAlive: (T) -> Boolean,
) : Closeable {

    private val acquisitionMutex = Mutex()
    private val stateLock = Any()
    private var generation = 0L
    private var retained: T? = null

    fun generationToken(): Long = synchronized(stateLock) { generation }

    suspend fun retain(acquire: suspend () -> T): Result<T> =
        retain(generationToken(), acquire)

    suspend fun retain(
        requestedGeneration: Long,
        acquire: suspend () -> T,
    ): Result<T> {
        return acquisitionMutex.withLock {
            var stale: T? = null
            val current = synchronized(stateLock) {
                retained?.let { value ->
                    if (value.isLive()) {
                        value
                    } else {
                        retained = null
                        stale = value
                        null
                    }
                }
            }
            stale.closeSafely()
            if (current != null) {
                return@withLock Result(value = current, acquired = false)
            }
            if (synchronized(stateLock) { generation != requestedGeneration }) {
                return@withLock Result(value = null, acquired = false)
            }

            val candidate = acquire()
            var displaced: T? = null
            var closeCandidate = false
            var result: Result<T>? = null
            synchronized(stateLock) {
                val currentAtCompletion = retained
                val currentIsLive = currentAtCompletion?.isLive() == true
                when {
                    generation != requestedGeneration -> {
                        closeCandidate = true
                        if (currentIsLive) {
                            result = Result(value = currentAtCompletion, acquired = false)
                        } else {
                            retained = null
                            displaced = currentAtCompletion
                            result = Result(value = null, acquired = false)
                        }
                    }
                    currentIsLive -> {
                        closeCandidate = true
                        result = Result(value = currentAtCompletion, acquired = false)
                    }
                    else -> {
                        displaced = currentAtCompletion
                        retained = candidate
                        result = Result(value = candidate, acquired = true)
                    }
                }
            }
            displaced.closeSafely()
            if (closeCandidate) candidate.closeSafely()
            checkNotNull(result)
        }
    }

    /** Takes ownership of [candidate], retaining it only when no live binding already exists. */
    fun offer(candidate: T, expectedGeneration: Long? = null): Boolean {
        var displaced: T? = null
        var closeCandidate = false
        var retainedCandidate = false
        synchronized(stateLock) {
            if (expectedGeneration != null && generation != expectedGeneration) {
                closeCandidate = true
                return@synchronized
            }
            generation += 1L
            val current = retained
            when {
                current?.isLive() == true -> closeCandidate = true
                candidate.isLive() -> {
                    displaced = current
                    retained = candidate
                    retainedCandidate = true
                }
                else -> {
                    displaced = current
                    retained = null
                    closeCandidate = true
                }
            }
        }
        displaced.closeSafely()
        if (closeCandidate) candidate.closeSafely()
        return retainedCandidate
    }

    fun release() {
        val released = synchronized(stateLock) {
            generation += 1L
            retained.also { retained = null }
        }
        released.closeSafely()
    }

    override fun close() = release()

    private fun T.isLive(): Boolean = runCatching { isAlive(this) }.getOrDefault(false)

    private fun T?.closeSafely() {
        this ?: return
        runCatching { close() }
    }

    internal data class Result<T>(
        val value: T?,
        val acquired: Boolean,
    )
}
