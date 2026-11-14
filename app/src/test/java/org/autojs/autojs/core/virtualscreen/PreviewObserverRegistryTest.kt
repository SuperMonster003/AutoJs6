package org.autojs.autojs.core.virtualscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PreviewObserverRegistryTest {

    @Test
    fun dispatchReachesEveryLiveSubscriber() {
        val registry = PreviewObserverRegistry()
        var first = 0
        var second = 0
        val tokenA = registry.add { first++ }
        val tokenB = registry.add { second++ }
        assertEquals(2, registry.size())

        registry.dispatch()

        assertEquals(1, first)
        assertEquals(1, second)
        assertTrue(registry.remove(tokenA))
        registry.dispatch()
        assertEquals(1, first)
        assertEquals(2, second)
    }

    @Test
    fun removingTheLastSubscriberIsReported() {
        val registry = PreviewObserverRegistry()
        val token = registry.add { error("must not be called after removal") }
        assertTrue(registry.remove(token))
        assertFalse(registry.remove(token))
        assertEquals(0, registry.size())
        registry.dispatch()
    }

    @Test
    fun aFailingSubscriberDoesNotBlockTheOthers() {
        val registry = PreviewObserverRegistry()
        var reached = false
        registry.add { throw IllegalStateException("bad observer") }
        registry.add { reached = true }
        registry.dispatch()
        assertTrue(reached)
    }

    @Test
    fun clearDropsAllSubscribers() {
        val registry = PreviewObserverRegistry()
        repeat(5) { registry.add { } }
        registry.clear()
        assertEquals(0, registry.size())
        assertFalse(registry.remove(Any()))
    }

    @Test
    fun concurrentAddsAndDispatchAreSafe() {
        val registry = PreviewObserverRegistry()
        val pool = Executors.newFixedThreadPool(4)
        val done = CountDownLatch(4)
        val start = CountDownLatch(1)
        try {
            repeat(4) {
                pool.execute {
                    start.await()
                    repeat(50) { registry.add { } }
                    done.countDown()
                }
            }
            start.countDown()
            repeat(50) { registry.dispatch() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
        } finally {
            pool.shutdownNow()
        }
        assertEquals(200, registry.size())
        registry.clear()
        assertEquals(0, registry.size())
    }

}
