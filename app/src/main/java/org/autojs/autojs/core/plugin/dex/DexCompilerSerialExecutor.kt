package org.autojs.autojs.core.plugin.dex

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

/**
 * A bounded serial lane with control priority.
 *
 * Urgent and normal control work have independent bounded queues. Best-effort data work has a
 * single slot, so an untrusted stream of progress callbacks cannot retain an unbounded number of
 * payloads or delay cancellation and terminal control work. Delegate rejection permanently closes
 * the lane, discards queued work, and invokes [onRejected] exactly once.
 */
internal class DexCompilerSerialExecutor(
    private val delegate: Executor,
    private val onRejected: (Throwable) -> Unit = {},
    private val urgentCapacity: Int = DEFAULT_URGENT_CAPACITY,
    private val normalCapacity: Int = DEFAULT_NORMAL_CAPACITY,
) : Executor {

    private val lock = Any()
    private val urgentTasks = ArrayDeque<Runnable>()
    private val normalTasks = ArrayDeque<Runnable>()
    private var dataTask: Runnable? = null
    private var active = false
    private var rejection: Throwable? = null

    init {
        require(urgentCapacity > 0) { "Urgent queue capacity must be positive" }
        require(normalCapacity > 0) { "Normal queue capacity must be positive" }
    }

    override fun execute(command: Runnable) {
        if (!tryExecute(command)) throw RejectedExecutionException("Serial executor is unavailable or full", rejection)
    }

    fun tryExecute(command: Runnable): Boolean = submit(command, urgent = false)

    fun tryExecuteUrgent(command: Runnable): Boolean = submit(command, urgent = true)

    /** Returns false when the one best-effort data slot is occupied or the lane is unavailable. */
    fun tryExecuteData(command: Runnable): Boolean {
        val shouldSchedule = synchronized(lock) {
            if (rejection != null || dataTask != null) return false
            dataTask = command
            activateIfNeeded()
        }
        if (shouldSchedule) scheduleNext()
        return synchronized(lock) { rejection == null }
    }

    internal fun queuedTaskCount(): Int = synchronized(lock) {
        urgentTasks.size + normalTasks.size + if (dataTask == null) 0 else 1
    }

    private fun submit(command: Runnable, urgent: Boolean): Boolean {
        val shouldSchedule = synchronized(lock) {
            if (rejection != null) return false
            val queue = if (urgent) urgentTasks else normalTasks
            val capacity = if (urgent) urgentCapacity else normalCapacity
            if (queue.size >= capacity) return false
            queue.addLast(command)
            activateIfNeeded()
        }
        if (shouldSchedule) scheduleNext()
        return synchronized(lock) { rejection == null }
    }

    /** Must be called while holding [lock]. */
    private fun activateIfNeeded(): Boolean = if (active) {
        false
    } else {
        active = true
        true
    }

    private fun scheduleNext() {
        val task = synchronized(lock) {
            val next = urgentTasks.pollFirst() ?: normalTasks.pollFirst() ?: dataTask?.also { dataTask = null }
            if (next == null) active = false
            next
        } ?: return
        try {
            delegate.execute {
                try {
                    task.run()
                } finally {
                    scheduleNext()
                }
            }
        } catch (error: RuntimeException) {
            val notify = synchronized(lock) {
                urgentTasks.clear()
                normalTasks.clear()
                dataTask = null
                active = false
                if (rejection == null) {
                    rejection = error
                    true
                } else {
                    false
                }
            }
            if (notify) onRejected(error)
        }
    }

    private companion object {
        const val DEFAULT_URGENT_CAPACITY = 16
        const val DEFAULT_NORMAL_CAPACITY = 64
    }
}
