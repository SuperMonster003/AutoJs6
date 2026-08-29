package org.autojs.autojs.core.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

class AccessibilityServiceAutoStartCoordinatorTest {

    @Test
    fun adjacentRequestsShareOneInFlightTask() {
        val executor = QueuedExecutor()
        val coordinator = AccessibilityServiceAutoStartCoordinator(executor)
        var runs = 0

        assertTrue(coordinator.submit { runs += 1 })
        assertFalse(coordinator.submit { runs += 10 })
        assertEquals(1, executor.pendingTaskCount)

        executor.runNext()

        assertEquals(1, runs)
        assertTrue(coordinator.submit { runs += 1 })
        executor.runNext()
        assertEquals(2, runs)
    }

    @Test
    fun taskFailureStillReleasesTheSingleFlightGuard() {
        val executor = QueuedExecutor()
        val coordinator = AccessibilityServiceAutoStartCoordinator(executor)

        assertTrue(coordinator.submit { error("boom") })
        assertThrows(IllegalStateException::class.java, executor::runNext)

        assertTrue(coordinator.submit { })
    }

    @Test
    fun rejectedExecutionStillReleasesTheSingleFlightGuard() {
        val executor = RejectOnceExecutor()
        val coordinator = AccessibilityServiceAutoStartCoordinator(executor)

        assertThrows(RejectedExecutionException::class.java) {
            coordinator.submit { }
        }

        assertTrue(coordinator.submit { })
        assertEquals(1, executor.acceptedTaskCount)
    }

    private class QueuedExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val pendingTaskCount get() = tasks.size

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runNext() {
            tasks.removeFirst().run()
        }
    }

    private class RejectOnceExecutor : Executor {
        private var shouldReject = true
        var acceptedTaskCount = 0
            private set

        override fun execute(command: Runnable) {
            if (shouldReject) {
                shouldReject = false
                throw RejectedExecutionException("test rejection")
            }
            acceptedTaskCount += 1
        }
    }
}
