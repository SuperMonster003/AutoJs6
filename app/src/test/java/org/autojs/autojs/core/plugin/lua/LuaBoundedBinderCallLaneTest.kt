package org.autojs.autojs.core.plugin.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class LuaBoundedBinderCallLaneTest {

    @Test
    fun successfulCallsUseOneDaemonWorkerAndTheLaneRecoversFromFailure() {
        val lane = LuaBoundedBinderCallLane("lua-lane-test")
        try {
            val workerName = lane.call("success", TEST_TIMEOUT_MILLIS) {
                assertTrue(Thread.currentThread().isDaemon)
                Thread.currentThread().name
            }
            assertTrue(workerName.startsWith("lua-lane-test-"))
            assertEquals(workerName, lane.call("same-worker", TEST_TIMEOUT_MILLIS) { Thread.currentThread().name })

            val original = IllegalStateException("provider failure")
            val failure = assertThrows(IllegalStateException::class.java) {
                lane.call("failure", TEST_TIMEOUT_MILLIS) { throw original }
            }
            assertSame(original, failure)
            assertEquals("recovered", lane.call("recovery", TEST_TIMEOUT_MILLIS) { "recovered" })
        } finally {
            lane.close()
        }
    }

    @Test
    fun concurrentCallFailsBusyWithoutStartingOrQueuingASecondOperation() {
        val lane = LuaBoundedBinderCallLane("lua-lane-busy")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstResult = AtomicReference<String?>()
        val firstFailure = AtomicReference<Throwable?>()
        val secondStarts = AtomicInteger()
        val caller = thread(name = "lua-lane-first-caller") {
            try {
                firstResult.set(
                    lane.call("first", TEST_TIMEOUT_MILLIS) {
                        entered.countDown()
                        check(release.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                        "first-result"
                    },
                )
            } catch (failure: Throwable) {
                firstFailure.set(failure)
            }
        }
        try {
            assertTrue(entered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            val busy = assertThrows(LuaBinderCallException::class.java) {
                lane.call("second", TEST_TIMEOUT_MILLIS) {
                    secondStarts.incrementAndGet()
                    error("A saturated lane started a second operation")
                }
            }
            assertEquals(LuaBinderCallFailure.BUSY, busy.failure)
            assertEquals(0, secondStarts.get())
        } finally {
            release.countDown()
            caller.join(TEST_TIMEOUT_MILLIS)
            lane.close()
        }
        assertFalse(caller.isAlive)
        assertEquals(null, firstFailure.get())
        assertEquals("first-result", firstResult.get())
    }

    @Test
    fun reentrantWorkerCallFailsFastInsteadOfDeadlockingOrQueuing() {
        val lane = LuaBoundedBinderCallLane("lua-lane-reentrant")
        try {
            val nestedFailure = AtomicReference<LuaBinderCallException?>()
            val result = lane.call("outer", TEST_TIMEOUT_MILLIS) {
                nestedFailure.set(
                    assertThrows(LuaBinderCallException::class.java) {
                        lane.call("nested", TEST_TIMEOUT_MILLIS) {
                            error("A reentrant operation entered the Lua Binder lane")
                        }
                    },
                )
                "outer-result"
            }

            assertEquals("outer-result", result)
            assertEquals(LuaBinderCallFailure.BUSY, nestedFailure.get()?.failure)
        } finally {
            lane.close()
        }
    }

    @Test
    fun timeoutFusesTheLaneUntilTheOriginalCallReturnsAndDisposesItsLateResultOnce() {
        val lane = LuaBoundedBinderCallLane("lua-lane-timeout")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        val disposeCalls = AtomicInteger()
        val abandonedCalls = AtomicInteger()
        val callerFailure = AtomicReference<Throwable?>()
        val caller = thread(name = "lua-lane-timeout-caller") {
            try {
                lane.call(
                    operationName = "wedged-call",
                    timeoutMillis = RUNNING_TIMEOUT_MILLIS,
                    onAbandoned = { abandonedCalls.incrementAndGet() },
                    lateResultDisposer = { _: String ->
                        disposeCalls.incrementAndGet()
                        disposed.countDown()
                    },
                ) {
                    entered.countDown()
                    check(release.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    "late-result"
                }
            } catch (failure: Throwable) {
                callerFailure.set(failure)
            }
        }
        try {
            assertTrue(entered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            caller.join(TEST_TIMEOUT_MILLIS)
            assertFalse(caller.isAlive)
            val timeout = callerFailure.get() as LuaBinderCallException
            assertEquals(LuaBinderCallFailure.TIMEOUT, timeout.failure)
            assertEquals(1, abandonedCalls.get())

            val stalled = assertThrows(LuaBinderCallException::class.java) {
                lane.call("while-stalled", TEST_TIMEOUT_MILLIS) {
                    error("A stalled lane started another operation")
                }
            }
            assertEquals(LuaBinderCallFailure.STALLED, stalled.failure)

            release.countDown()
            assertTrue(disposed.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertEquals(1, disposeCalls.get())
            assertEquals(1, abandonedCalls.get())
            assertEquals("recovered", lane.call("after-drain", TEST_TIMEOUT_MILLIS) { "recovered" })
            assertEquals(1, disposeCalls.get())
        } finally {
            release.countDown()
            caller.join(TEST_TIMEOUT_MILLIS)
            lane.close()
        }
    }

    @Test
    fun interruptReturnsToTheCallerButKeepsTheWorkerFusedUntilLateCleanup() {
        val lane = LuaBoundedBinderCallLane("lua-lane-interrupt")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        val disposeCalls = AtomicInteger()
        val callerFailure = AtomicReference<Throwable?>()
        val interruptRestored = AtomicBoolean(false)
        val caller = thread(name = "lua-lane-interrupted-caller") {
            try {
                lane.call(
                    operationName = "interruptible-wait",
                    timeoutMillis = TEST_TIMEOUT_MILLIS,
                    lateResultDisposer = { _: String ->
                        disposeCalls.incrementAndGet()
                        disposed.countDown()
                    },
                ) {
                    entered.countDown()
                    check(release.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    "late-after-interrupt"
                }
            } catch (failure: Throwable) {
                callerFailure.set(failure)
                interruptRestored.set(Thread.currentThread().isInterrupted)
            }
        }
        try {
            assertTrue(entered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            caller.interrupt()
            caller.join(TEST_TIMEOUT_MILLIS)
            assertFalse(caller.isAlive)
            assertTrue(callerFailure.get() is InterruptedException)
            assertTrue(interruptRestored.get())

            val stalled = assertThrows(LuaBinderCallException::class.java) {
                lane.call("interrupt-stalled", TEST_TIMEOUT_MILLIS) {
                    error("An interrupted stalled lane started another operation")
                }
            }
            assertEquals(LuaBinderCallFailure.STALLED, stalled.failure)

            release.countDown()
            assertTrue(disposed.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            assertEquals(1, disposeCalls.get())
        } finally {
            release.countDown()
            caller.join(TEST_TIMEOUT_MILLIS)
            lane.close()
        }
    }

    @Test
    fun failureAfterAbandonmentDrainsWithoutCallingTheLateResultDisposer() {
        val lane = LuaBoundedBinderCallLane("lua-lane-late-failure")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val disposeCalls = AtomicInteger()
        val callerFailure = AtomicReference<Throwable?>()
        val caller = thread(name = "lua-lane-late-failure-caller") {
            try {
                lane.call(
                    operationName = "late-failure",
                    timeoutMillis = RUNNING_TIMEOUT_MILLIS,
                    lateResultDisposer = { _: String -> disposeCalls.incrementAndGet() },
                ) {
                    entered.countDown()
                    check(release.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    throw IllegalStateException("late provider failure")
                }
            } catch (failure: Throwable) {
                callerFailure.set(failure)
            }
        }
        try {
            assertTrue(entered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            caller.join(TEST_TIMEOUT_MILLIS)
            assertFalse(caller.isAlive)
            val timeout = callerFailure.get() as LuaBinderCallException
            assertEquals(LuaBinderCallFailure.TIMEOUT, timeout.failure)
            release.countDown()

            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TEST_TIMEOUT_MILLIS)
            while (true) {
                try {
                    assertEquals("drained", lane.call("after-late-failure", 100L) { "drained" })
                    break
                } catch (failure: LuaBinderCallException) {
                    if (
                        failure.failure !in setOf(LuaBinderCallFailure.STALLED, LuaBinderCallFailure.BUSY) ||
                        System.nanoTime() >= deadline
                    ) {
                        throw failure
                    }
                    Thread.yield()
                }
            }
            assertEquals(0, disposeCalls.get())
        } finally {
            release.countDown()
            caller.join(TEST_TIMEOUT_MILLIS)
            lane.close()
        }
    }

    @Test
    fun closedLaneFailsWithoutStartingWork() {
        val lane = LuaBoundedBinderCallLane("lua-lane-closed")
        lane.close()
        val starts = AtomicInteger()

        val closed = assertThrows(LuaBinderCallException::class.java) {
            lane.call("closed", TEST_TIMEOUT_MILLIS) {
                starts.incrementAndGet()
            }
        }

        assertEquals(LuaBinderCallFailure.CLOSED, closed.failure)
        assertEquals(0, starts.get())
    }

    @Test
    fun poisonedLaneNeverAcceptsAnotherCallAfterTheAbandonedCallDrains() {
        val lane = LuaBoundedBinderCallLane("lua-lane-poisoned")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val disposed = CountDownLatch(1)
        val callerFailure = AtomicReference<Throwable?>()
        val caller = thread(name = "lua-lane-poison-caller") {
            try {
                lane.call(
                    operationName = "uncertain-dispatch",
                    timeoutMillis = RUNNING_TIMEOUT_MILLIS,
                    onAbandoned = lane::poison,
                    lateResultDisposer = { _: String -> disposed.countDown() },
                ) {
                    entered.countDown()
                    check(release.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
                    "late-session"
                }
            } catch (failure: Throwable) {
                callerFailure.set(failure)
            }
        }
        try {
            assertTrue(entered.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
            caller.join(TEST_TIMEOUT_MILLIS)
            assertFalse(caller.isAlive)
            val timeout = callerFailure.get() as LuaBinderCallException
            assertEquals(LuaBinderCallFailure.TIMEOUT, timeout.failure)
            release.countDown()
            assertTrue(disposed.await(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))

            val poisoned = assertThrows(LuaBinderCallException::class.java) {
                lane.call("after-poison", TEST_TIMEOUT_MILLIS) {
                    error("A poisoned lane dispatched another operation")
                }
            }
            assertEquals(LuaBinderCallFailure.POISONED, poisoned.failure)
            assertTrue(poisoned.message.orEmpty().contains("host process restarts"))
        } finally {
            release.countDown()
            caller.join(TEST_TIMEOUT_MILLIS)
            lane.close()
        }
    }

    private companion object {
        const val RUNNING_TIMEOUT_MILLIS = 500L
        const val TEST_TIMEOUT_MILLIS = 5_000L
    }
}
