package org.autojs.autojs.core.looper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LoopersWaitTokenTest {

    @Test
    fun releaseFromAnotherThreadRemovesWaitIdAndWakesOwnerOnce() {
        val waitIds = ConcurrentHashMap.newKeySet<Int>()
        val waitId = 7
        val wakeUps = AtomicInteger()
        assertTrue(waitIds.add(waitId))
        val token = Loopers.WaitToken(waitId, WakeUpOnRemoveSet(waitIds) {
            wakeUps.incrementAndGet()
            true
        })
        val released = CountDownLatch(1)

        val dismissThread = Thread {
            token.release()
            released.countDown()
        }.apply { start() }

        try {
            assertTrue(released.await(5, TimeUnit.SECONDS))
            assertFalse(waitIds.contains(waitId))

            token.release()
            assertFalse(waitIds.contains(waitId))
            assertEquals(1, wakeUps.get())
        } finally {
            dismissThread.interrupt()
            dismissThread.join(5_000)
        }
    }

    @Test
    fun releaseStillCompletesWhenOwnerLooperRejectsWakeUp() {
        val waitIds = ConcurrentHashMap.newKeySet<Int>().apply { add(9) }
        val token = Loopers.WaitToken(9, WakeUpOnRemoveSet(waitIds) { false })

        token.release()

        assertFalse(waitIds.contains(9))
    }

    @Test
    fun releaseStillCompletesWhenWakeUpThrows() {
        val waitIds = ConcurrentHashMap.newKeySet<Int>().apply { add(11) }
        val token = Loopers.WaitToken(11, WakeUpOnRemoveSet(waitIds) {
            throw IllegalStateException("owner looper unavailable")
        })

        token.release()

        assertFalse(waitIds.contains(11))
    }

}
