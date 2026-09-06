package org.autojs.autojs.core.automator.event

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

/**
 * [EventGate]: a remembered signal, a timed-out wait, a wake-up from another thread, clearing,
 * and interruption. Real time with generous margins.
 * zh-CN: [EventGate]: 记住的信号, 超时的等待, 来自另一线程的唤醒, 清除, 以及中断. 真实时间, 留有充分余量.
 */
class EventGateTest {

    @Test
    fun aPendingSignalReturnsAtOnceAndIsConsumed() {
        val gate = EventGate()
        assertFalse(gate.isPending)
        gate.signal()
        gate.signal()
        assertTrue(gate.isPending)
        assertTrue(gate.await(0))
        assertFalse(gate.isPending)
        assertFalse(gate.await(0))
    }

    @Test
    fun awaitTimesOutWithoutASignal() {
        val gate = EventGate()
        val start = System.nanoTime()
        assertFalse(gate.await(60))
        assertTrue((System.nanoTime() - start) / 1_000_000 >= 50)
    }

    @Test
    fun aSignalFromAnotherThreadWakesTheWait() {
        val gate = EventGate()
        val start = System.nanoTime()
        thread {
            Thread.sleep(40)
            gate.signal()
        }
        assertTrue(gate.await(5_000))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 4_000)
        assertFalse(gate.isPending)
    }

    @Test
    fun clearDropsAPendingSignal() {
        val gate = EventGate()
        gate.signal()
        gate.clear()
        assertFalse(gate.isPending)
        assertFalse(gate.await(0))
    }

    @Test
    fun awaitIsInterruptible() {
        val gate = EventGate()
        val waiting = Thread.currentThread()
        thread {
            Thread.sleep(30)
            waiting.interrupt()
        }
        assertThrows(InterruptedException::class.java) { gate.await(5_000) }
        assertFalse(Thread.interrupted())
    }

}
