package org.autojs.autojs.core.plugin.python

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeProcessExitSignalTest {
    @Test
    fun exitCompletesTheBarrierAndTimeoutRemainsBounded() {
        val pending = PythonRuntimeProcessExitSignal()
        assertFalse(pending.awaitUninterruptibly(1))

        pending.markExited()
        assertTrue(pending.awaitUninterruptibly(1))
        assertThrows(IllegalArgumentException::class.java) {
            pending.awaitUninterruptibly(0)
        }
    }

    @Test
    fun cleanupWaitPreservesCallerInterruption() {
        val signal = PythonRuntimeProcessExitSignal()
        val completed = AtomicBoolean(false)
        val interruptedAfterWait = AtomicBoolean(false)
        val waiter = Thread({
            Thread.currentThread().interrupt()
            completed.set(signal.awaitUninterruptibly(5_000))
            interruptedAfterWait.set(Thread.currentThread().isInterrupted)
        }, "python-process-exit-test")

        waiter.start()
        signal.markExited()
        waiter.join(5_000)

        assertFalse(waiter.isAlive)
        assertTrue(completed.get())
        assertTrue(interruptedAfterWait.get())
    }
}
