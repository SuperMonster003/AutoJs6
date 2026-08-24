package org.autojs.autojs.core.plugin.python

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** One-shot process-generation exit signal with bounded, interruption-preserving cleanup waits. */
internal class PythonRuntimeProcessExitSignal {
    private val exited = CountDownLatch(1)

    fun markExited() {
        exited.countDown()
    }

    fun awaitUninterruptibly(timeoutMillis: Long): Boolean {
        require(timeoutMillis > 0L) { "Python process exit timeout must be positive" }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        var interrupted = false
        try {
            while (true) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return exited.count == 0L
                try {
                    if (exited.await(remaining, TimeUnit.NANOSECONDS)) return true
                } catch (_: InterruptedException) {
                    interrupted = true
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
