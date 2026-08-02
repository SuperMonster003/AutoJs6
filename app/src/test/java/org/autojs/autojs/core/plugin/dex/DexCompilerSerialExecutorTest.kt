package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DexCompilerSerialExecutorTest {

    @Test
    fun reentrantSubmissionRunsAfterTheCurrentTask() {
        val events = mutableListOf<Int>()
        lateinit var serial: DexCompilerSerialExecutor
        serial = DexCompilerSerialExecutor(Executor(Runnable::run))

        serial.execute {
            events += 1
            serial.execute { events += 3 }
            events += 2
        }

        assertEquals(listOf(1, 2, 3), events)
    }

    @Test
    fun concurrentSubmissionsNeitherOverlapNorBecomeStranded() {
        val delegate = Executors.newFixedThreadPool(4)
        val taskCount = 2_000
        val serial = DexCompilerSerialExecutor(delegate, normalCapacity = taskCount)
        val done = CountDownLatch(taskCount)
        val active = AtomicInteger()
        val maximumActive = AtomicInteger()
        val executed = Collections.synchronizedSet(mutableSetOf<Int>())
        val submitters = Executors.newFixedThreadPool(8)

        repeat(taskCount) { index ->
            submitters.execute {
                serial.execute {
                    val now = active.incrementAndGet()
                    maximumActive.accumulateAndGet(now, ::maxOf)
                    executed += index
                    active.decrementAndGet()
                    done.countDown()
                }
            }
        }
        submitters.shutdown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        delegate.shutdownNow()

        assertEquals(taskCount, executed.size)
        assertEquals(1, maximumActive.get())
    }

    @Test
    fun delegateRejectionDropsQueuedWorkAndNotifiesExactlyOnce() {
        var acceptedRunnable: Runnable? = null
        var first = true
        val rejectionCount = AtomicInteger()
        val serial = DexCompilerSerialExecutor(
            delegate = Executor { runnable ->
                if (first) {
                    first = false
                    acceptedRunnable = runnable
                } else {
                    throw RejectedExecutionException("closed")
                }
            },
            onRejected = { rejectionCount.incrementAndGet() },
        )
        val events = mutableListOf<Int>()

        assertTrue(serial.tryExecute(Runnable { events += 1 }))
        assertTrue(serial.tryExecute(Runnable { events += 2 }))
        acceptedRunnable!!.run()

        assertEquals(listOf(1), events)
        assertEquals(1, rejectionCount.get())
        assertFalse(serial.tryExecute(Runnable { events += 3 }))
        assertEquals(1, rejectionCount.get())
        assertEquals(listOf(1), events)
    }

    @Test
    fun urgentControlPrecedesNormalControlAndSingleDataSlot() {
        val delegate = ManualExecutor()
        val serial = DexCompilerSerialExecutor(delegate, urgentCapacity = 2, normalCapacity = 2)
        val events = mutableListOf<String>()

        assertTrue(serial.tryExecute(Runnable { events += "active" }))
        assertTrue(serial.tryExecuteData(Runnable { events += "progress" }))
        assertFalse(serial.tryExecuteData(Runnable { events += "dropped" }))
        assertTrue(serial.tryExecute(Runnable { events += "normal" }))
        assertTrue(serial.tryExecuteUrgent(Runnable { events += "cancel" }))
        assertEquals(3, serial.queuedTaskCount())

        delegate.runAll()

        assertEquals(listOf("active", "cancel", "normal", "progress"), events)
        assertEquals(0, serial.queuedTaskCount())
    }

    @Test
    fun eachQueueRejectsBeyondItsConfiguredBound() {
        val delegate = ManualExecutor()
        val serial = DexCompilerSerialExecutor(delegate, urgentCapacity = 1, normalCapacity = 1)

        assertTrue(serial.tryExecute(Runnable {}))
        assertTrue(serial.tryExecute(Runnable {}))
        assertFalse(serial.tryExecute(Runnable {}))
        assertTrue(serial.tryExecuteUrgent(Runnable {}))
        assertFalse(serial.tryExecuteUrgent(Runnable {}))
        assertTrue(serial.tryExecuteData(Runnable {}))
        assertFalse(serial.tryExecuteData(Runnable {}))
        assertEquals(3, serial.queuedTaskCount())
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
