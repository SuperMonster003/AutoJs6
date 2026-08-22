package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class JvmSourceExactBindingLinkStateTest {
    @Test
    fun closeBetweenLinkAndPublishRejectsLateConnectionAndRequiresExactUnlink() {
        val state = JvmSourceExactBindingLinkState<String, String>()
        val linkCompleted = CountDownLatch(1)
        val allowPublish = CountDownLatch(1)
        val delivered = AtomicBoolean(false)
        val exactUnlinks = AtomicInteger(0)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val publication = executor.submit<Boolean> {
                // Models a successful binder.linkToDeath before the state pair is published.
                linkCompleted.countDown()
                assertTrue(allowPublish.await(1, TimeUnit.SECONDS))
                val published = state.publish("binder", "recipient")
                if (published) delivered.set(true)
                if (!published) exactUnlinks.incrementAndGet()
                published
            }
            assertTrue(linkCompleted.await(1, TimeUnit.SECONDS))

            val close = state.close()
            assertTrue(close.newlyClosed)
            assertNull(close.publishedLink)
            allowPublish.countDown()

            assertFalse(publication.get(1, TimeUnit.SECONDS))
            assertFalse(delivered.get())
            assertEquals(1, exactUnlinks.get())
        } finally {
            allowPublish.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun closeAfterPublicationReturnsPublishedPairExactlyOnce() {
        val state = JvmSourceExactBindingLinkState<String, String>()
        var delivered = false
        assertTrue(state.publish("binder", "recipient"))
        delivered = true
        assertTrue(delivered)

        val first = state.close()
        assertTrue(first.newlyClosed)
        assertEquals("binder", first.publishedLink?.binder)
        assertEquals("recipient", first.publishedLink?.recipient)

        val second = state.close()
        assertFalse(second.newlyClosed)
        assertNull(second.publishedLink)
    }

    @Test
    fun blockingConnectedCallbackDoesNotBlockClose() {
        val state = JvmSourceExactBindingLinkState<String, String>()
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val delivery = executor.submit {
                if (state.publish("binder", "recipient")) {
                    callbackStarted.countDown()
                    releaseCallback.await(1, TimeUnit.SECONDS)
                }
            }
            assertTrue(callbackStarted.await(1, TimeUnit.SECONDS))

            val close = state.close()
            assertTrue(close.newlyClosed)
            assertEquals("binder", close.publishedLink?.binder)
            assertFalse(delivery.isDone)

            releaseCallback.countDown()
            delivery.get(1, TimeUnit.SECONDS)
        } finally {
            releaseCallback.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun blockingDeathCallbackDoesNotBlockClose() {
        val state = JvmSourceExactBindingLinkState<String, String>()
        assertTrue(state.publish("binder", "recipient"))
        val callbackStarted = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val delivery = executor.submit {
                if (state.reserveDeliveryIfOpen()) {
                    callbackStarted.countDown()
                    releaseCallback.await(1, TimeUnit.SECONDS)
                }
            }
            assertTrue(callbackStarted.await(1, TimeUnit.SECONDS))

            val close = state.close()
            assertTrue(close.newlyClosed)
            assertEquals("recipient", close.publishedLink?.recipient)
            assertFalse(delivery.isDone)

            releaseCallback.countDown()
            delivery.get(1, TimeUnit.SECONDS)
        } finally {
            releaseCallback.countDown()
            executor.shutdownNow()
        }
    }
}
