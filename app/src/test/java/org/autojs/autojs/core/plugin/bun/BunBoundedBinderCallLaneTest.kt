package org.autojs.autojs.core.plugin.bun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BunBoundedBinderCallLaneTest {

    @Test
    fun timeoutDoesNotCreateReplacementWorkersAndCanPoisonTheLane() {
        val lane = BunBoundedBinderCallLane("bun-lane-test")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        val timeout = assertThrows(BunBinderCallException::class.java) {
            lane.call<Unit>(
                operationName = "run-script",
                timeoutMillis = 100L,
                onAbandoned = lane::poison,
            ) {
                entered.countDown()
                release.await()
            }
        }
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertEquals(BunBinderCallFailure.TIMEOUT, timeout.failure)
        release.countDown()

        val poisoned = assertThrows(BunBinderCallException::class.java) {
            lane.call<Unit>("after-timeout", 100L) {}
        }
        assertEquals(BunBinderCallFailure.POISONED, poisoned.failure)
    }

    @Test
    fun detachedCleanupDispatchHasOneWorkerAndNoBacklog() {
        val lane = BunBoundedBinderCallLane("bun-cleanup-lane-test")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val completed = CountDownLatch(1)

        assertTrue(lane.tryDispatch("cancel-script") {
            entered.countDown()
            release.await()
            completed.countDown()
        })
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        assertFalse(lane.tryDispatch("cancel-script") {})
        release.countDown()
        assertTrue(completed.await(1, TimeUnit.SECONDS))

        var accepted = false
        for (attempt in 0 until 100) {
            if (lane.tryDispatch("cancel-script") {}) {
                accepted = true
                break
            }
            Thread.sleep(10L)
        }
        assertTrue(accepted)
    }
}
