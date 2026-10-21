package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BunHostCallExecutorTest {
    @Test fun blockedUiReadersLeaveTheFourthSlotAvailableForFlowCallbacks() {
        BunHostCallExecutor().use { executor ->
            val entered = CountDownLatch(3)
            val release = CountDownLatch(1)
            val finished = CountDownLatch(3)
            repeat(3) { assertTrue(executor.submit { entered.countDown(); try { release.await() } finally { finished.countDown() } }) }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val callback = CountDownLatch(1)
            assertTrue(executor.submit { callback.countDown() })
            assertTrue("A long UI read must not serialize the next callback", callback.await(2, TimeUnit.SECONDS))
            release.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
        }
    }
    @Test fun closeInterruptsRunningCallsAndDiscardsQueuedCalls() {
        val executor = BunHostCallExecutor()
        val entered = CountDownLatch(4)
        val interrupted = CountDownLatch(4)
        val queuedRan = AtomicBoolean()
        repeat(4) {
            assertTrue(executor.submit {
                entered.countDown()
                try { CountDownLatch(1).await() } catch (_: InterruptedException) { interrupted.countDown() }
            })
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        repeat(4) { assertTrue(executor.submit { queuedRan.set(true) }) }
        assertFalse(executor.submit { queuedRan.set(true) })
        executor.close()
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        assertFalse(queuedRan.get())
        assertFalse(executor.submit {})
    }
}
