package org.autojs.autojs.runtime.api.tts

import org.autojs.autojs.lang.ThreadCompat
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal fun interface TtsBlockingCallRunner {

    /**
     * Runs [action] away from the TTS state actor.
     *
     * Returns false instead of creating an unbounded queue when every native-call slot is held
     * by a broken engine. A slot is released only when the real call returns.
     */
    fun execute(action: () -> Unit): Boolean

    /**
     * Queues bounded lifecycle cleanup when all native-call slots are occupied.
     *
     * Custom runners may use the same rejection policy as [execute]. The Android runner gives
     * cleanup priority as soon as any real call returns.
     */
    fun executeCleanup(action: () -> Unit): Boolean = execute(action)
}

/**
 * Process-wide bound for calls into TextToSpeech, PackageManager, and their Binder services.
 *
 * A stuck Binder call cannot be interrupted safely. Keeping the bound process-wide prevents
 * repeatedly created script runtimes from leaking an unbounded number of blocked threads.
 */
internal object AndroidTtsBlockingCallRunner : TtsBlockingCallRunner {

    private val lock = Any()
    private val cleanupQueue = ArrayDeque<() -> Unit>()
    private var activeWorkers = 0

    override fun execute(action: () -> Unit): Boolean {
        synchronized(lock) {
            if (activeWorkers >= MAX_IN_FLIGHT_CALLS) return false
            activeWorkers += 1
        }
        return startWorker(action)
    }

    override fun executeCleanup(action: () -> Unit): Boolean {
        val startImmediately = synchronized(lock) {
            if (activeWorkers < MAX_IN_FLIGHT_CALLS) {
                activeWorkers += 1
                true
            } else {
                if (cleanupQueue.size >= MAX_PENDING_CLEANUPS) return false
                cleanupQueue.addLast(action)
                false
            }
        }
        return !startImmediately || startWorker(action)
    }

    private fun startWorker(initialAction: () -> Unit): Boolean {
        val thread = try {
            ThreadCompat(
                {
                    var action: (() -> Unit)? = initialAction
                    while (true) {
                        val current = action ?: break
                        runCatching(current)
                        action = synchronized(lock) {
                            if (cleanupQueue.isEmpty()) {
                                activeWorkers -= 1
                                null
                            } else {
                                cleanupQueue.removeFirst()
                            }
                        }
                    }
                },
                "AutoJs6-TTS-Native-${nextThreadId.incrementAndGet()}",
            ).apply { isDaemon = true }
        } catch (_: Throwable) {
            synchronized(lock) { activeWorkers -= 1 }
            return false
        }
        return try {
            thread.start()
            true
        } catch (_: Throwable) {
            synchronized(lock) { activeWorkers -= 1 }
            false
        }
    }

    internal const val MAX_IN_FLIGHT_CALLS = 4
    internal const val MAX_PENDING_CLEANUPS = 64
    private val nextThreadId = AtomicInteger()
}

internal interface TtsDeadlineScheduler {

    fun schedule(delayMillis: Long, action: () -> Unit): TtsScheduledTask

    fun shutdown()
}

/**
 * A timer lane that never enters TextToSpeech itself. Its callbacks only inspect bounded local
 * state and hand state transitions back to the actor.
 */
internal class DefaultTtsDeadlineScheduler : TtsDeadlineScheduler {

    private val closed = AtomicBoolean(false)
    private val executor = ScheduledThreadPoolExecutor(1) { action ->
        ThreadCompat(
            action,
            "AutoJs6-TTS-Deadline-${nextThreadId.incrementAndGet()}",
        ).apply { isDaemon = true }
    }.apply {
        removeOnCancelPolicy = true
        executeExistingDelayedTasksAfterShutdownPolicy = false
        continueExistingPeriodicTasksAfterShutdownPolicy = false
    }

    override fun schedule(delayMillis: Long, action: () -> Unit): TtsScheduledTask {
        check(!closed.get()) { "TTS deadline scheduler is closed" }
        val future = executor.schedule(
            Runnable(action),
            delayMillis.coerceAtLeast(0L),
            TimeUnit.MILLISECONDS,
        )
        return TtsScheduledTask { future.cancel(false) }
    }

    override fun shutdown() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow()
    }

    private companion object {

        val nextThreadId = AtomicInteger()
    }
}
