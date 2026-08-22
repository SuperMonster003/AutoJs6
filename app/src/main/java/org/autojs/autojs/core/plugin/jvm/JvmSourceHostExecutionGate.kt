package org.autojs.autojs.core.plugin.jvm

/**
 * Serializes host shutdown with execution admission.
 *
 * There is deliberately no queue: one control owns the slot, every concurrent caller is
 * rejected, and closing the host atomically prevents any later admission.
 */
internal class JvmSourceHostExecutionGate<T : Any> {
    enum class Admission {
        ACQUIRED,
        BUSY,
        CLOSED,
    }

    data class Snapshot(
        val closed: Boolean,
        val active: Boolean,
    )

    private var closed = false
    private var active: T? = null

    @Synchronized
    fun tryAcquire(owner: T): Admission = when {
        closed -> Admission.CLOSED
        active != null -> Admission.BUSY
        else -> {
            active = owner
            Admission.ACQUIRED
        }
    }

    @Synchronized
    fun release(owner: T): Boolean {
        if (active !== owner) return false
        active = null
        return true
    }

    /** Marks the gate closed and returns the active owner that must be cancelled, if any. */
    @Synchronized
    fun close(): T? {
        if (closed) return null
        closed = true
        return active
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(closed = closed, active = active != null)
}
