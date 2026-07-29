package org.autojs.autojs.core.looper

import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ServantLooperHolderTest {

    @Test
    fun interruptedFirstGetThrowsScriptInterruptedExceptionAndAnotherCallerReceivesValue() {
        val sentinel = Any()
        val starts = AtomicInteger()
        val releasePublisher = CountDownLatch(1)
        val publisher = AtomicReference<Thread>()
        val holder = ServantLooperHolder<Any>(
            starter = { publish, fail ->
                starts.incrementAndGet()
                publisher.set(Thread {
                    try {
                        releasePublisher.await()
                        publish(sentinel)
                    } catch (t: Throwable) {
                        fail(t)
                    }
                }.apply { start() })
            },
            disposer = {},
        )
        val followerResult = AtomicReference<Any>()
        val followerFailure = AtomicReference<Throwable>()
        var follower: Thread? = null

        try {
            Thread.currentThread().interrupt()
            try {
                holder.get()
                fail("Expected ScriptInterruptedException")
            } catch (e: ScriptInterruptedException) {
                assertTrue(e.cause is InterruptedException)
            } finally {
                Thread.interrupted()
            }

            val followerThread = Thread {
                try {
                    followerResult.set(holder.get())
                } catch (t: Throwable) {
                    followerFailure.set(t)
                }
            }.apply { start() }
            follower = followerThread
            releasePublisher.countDown()
            followerThread.join(5_000)

            assertFalse(followerThread.isAlive)
            assertTrue(followerFailure.get()?.toString(), followerFailure.get() == null)
            assertSame(sentinel, followerResult.get())
            assertEquals(1, starts.get())
        } finally {
            Thread.interrupted()
            releasePublisher.countDown()
            holder.close()
            follower?.interrupt()
            publisher.get()?.interrupt()
            follower?.join(5_000)
            publisher.get()?.join(5_000)
        }
    }

    @Test
    fun concurrentFirstGetsStartOnceAndShareValue() {
        val callerCount = 16
        val sentinel = Any()
        val starts = AtomicInteger()
        val callersReady = CountDownLatch(callerCount)
        val begin = CountDownLatch(1)
        val starterEntered = CountDownLatch(1)
        val releasePublisher = CountDownLatch(1)
        val callersDone = CountDownLatch(callerCount)
        val results = ConcurrentLinkedQueue<Any>()
        val failures = ConcurrentLinkedQueue<Throwable>()
        val publisher = AtomicReference<Thread>()
        val holder = ServantLooperHolder<Any>(
            starter = { publish, fail ->
                starts.incrementAndGet()
                starterEntered.countDown()
                publisher.set(Thread {
                    try {
                        releasePublisher.await()
                        publish(sentinel)
                    } catch (t: Throwable) {
                        fail(t)
                    }
                }.apply { start() })
            },
            disposer = {},
        )
        val callers = List(callerCount) {
            Thread {
                callersReady.countDown()
                try {
                    begin.await()
                    results.add(holder.get())
                } catch (t: Throwable) {
                    failures.add(t)
                } finally {
                    callersDone.countDown()
                }
            }.apply { start() }
        }

        try {
            assertTrue(callersReady.await(5, TimeUnit.SECONDS))
            begin.countDown()
            assertTrue(starterEntered.await(5, TimeUnit.SECONDS))
            releasePublisher.countDown()
            assertTrue(callersDone.await(5, TimeUnit.SECONDS))
        } finally {
            begin.countDown()
            releasePublisher.countDown()
            holder.close()
            callers.forEach { it.interrupt() }
            publisher.get()?.interrupt()
            callers.forEach { it.join(5_000) }
            publisher.get()?.join(5_000)
        }

        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(1, starts.get())
        assertEquals(callerCount, results.size)
        results.forEach { assertSame(sentinel, it) }
    }

    @Test
    fun closeBeforePublishDisposesLateValue() {
        val sentinel = Any()
        val starterEntered = CountDownLatch(1)
        val publish = AtomicReference<(Any) -> Unit>()
        val getterFailure = AtomicReference<Throwable>()
        val disposed = ConcurrentLinkedQueue<Any>()
        val holder = ServantLooperHolder<Any>(
            starter = { complete, _ ->
                publish.set(complete)
                starterEntered.countDown()
            },
            disposer = disposed::add,
        )
        val getter = Thread {
            try {
                holder.get()
            } catch (t: Throwable) {
                getterFailure.set(t)
            }
        }.apply { start() }

        try {
            assertTrue(starterEntered.await(5, TimeUnit.SECONDS))
            holder.close()
            getter.join(5_000)

            assertFalse(getter.isAlive)
            assertTrue(getterFailure.get() is IllegalStateException)
            publish.get().invoke(sentinel)
            assertEquals(1, disposed.size)
            assertSame(sentinel, disposed.single())
        } finally {
            holder.close()
            publish.get()?.invoke(sentinel)
            getter.interrupt()
            getter.join(5_000)
        }
    }

}
