package org.autojs.autojs.core.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * docs/shizuku-oom/ROADMAP.md T7: the user service ends what it started before it exits, and exits on its own once its
 * hosts are gone.
 * zh-CN: docs/shizuku-oom/ROADMAP.md T7: 用户服务先结束它启动的内容再退出, 宿主都消失后自行退出.
 */
class UserServiceLifecycleTest {

    private val events = mutableListOf<String>()
    private val scheduler = FakeScheduler()
    private val lifecycle = UserServiceLifecycle(scheduler, { events += "exit:$it" }, attachTimeoutMillis = 30_000L)

    @Test
    fun hostDeathTerminatesChildrenBeforeExit() {
        lifecycle.start()
        val host = Any()
        assertTrue(lifecycle.attachHost(host))
        assertTrue(lifecycle.register(child("shell")))
        assertTrue(lifecycle.register(child("exec")))

        lifecycle.onHostDied(host)

        assertEquals(listOf("terminate:shell", "terminate:exec", "exit:host-died"), events)
        assertTrue(lifecycle.isShuttingDown)
    }

    @Test
    fun exitsWhenNoHostAttachesWithinTimeout() {
        lifecycle.start()
        assertTrue(lifecycle.register(child("exec")))
        assertEquals(30_000L, scheduler.pending.single().delayMillis)

        scheduler.runPending()

        assertEquals(listOf("terminate:exec", "exit:no-host-attached"), events)
    }

    @Test
    fun attachingCancelsWatchdog() {
        lifecycle.start()
        val task = scheduler.pending.single()

        assertTrue(lifecycle.attachHost(Any()))

        assertTrue(task.cancelled)
        // A callback already on its way when the attach cancelled it changes nothing.
        // zh-CN: 附着取消时已在途的回调不改变任何结果.
        task.action.run()
        assertEquals(emptyList<String>(), events)
        assertFalse(lifecycle.isShuttingDown)
    }

    @Test
    fun reentrantShutdownExitsOnce() {
        lifecycle.start()
        val host = Any()
        lifecycle.attachHost(host)
        // destroy() from Shizuku while a child's terminate() lets the host die: the nested triggers do nothing.
        // zh-CN: Shizuku 的 destroy() 期间某个子进程的 terminate() 导致宿主死亡: 嵌套的触发不做任何事.
        lifecycle.register {
            events += "terminate:shell"
            lifecycle.onHostDied(host)
            lifecycle.shutdown(UserServiceLifecycle.REASON_DESTROY)
        }

        lifecycle.shutdown(UserServiceLifecycle.REASON_DESTROY)
        lifecycle.shutdown(UserServiceLifecycle.REASON_EXIT)
        scheduler.runPending()

        assertEquals(listOf("terminate:shell", "exit:destroy"), events)
    }

    @Test
    fun childRegisteredDuringShutdownIsTerminatedAtOnce() {
        lifecycle.shutdown(UserServiceLifecycle.REASON_EXIT)

        assertFalse(lifecycle.register(child("late")))

        assertEquals(listOf("exit:exit", "terminate:late"), events)
    }

    @Test
    fun unregisteredChildIsNotTerminated() {
        val finished = child("finished")
        lifecycle.register(finished)
        lifecycle.register(child("running"))
        lifecycle.unregister(finished)

        lifecycle.shutdown(UserServiceLifecycle.REASON_DESTROY)

        assertEquals(listOf("terminate:running", "exit:destroy"), events)
    }

    @Test
    fun staysWhileAnotherHostIsAttached() {
        lifecycle.start()
        val first = Any()
        val second = Any()
        assertTrue(lifecycle.attachHost(first))
        assertTrue(lifecycle.attachHost(second))
        assertFalse(lifecycle.attachHost(second))

        lifecycle.onHostDied(first)
        assertFalse(lifecycle.isShuttingDown)

        lifecycle.onHostDied(second)
        assertEquals(listOf("exit:host-died"), events)
    }

    @Test
    fun failingChildDoesNotStopTheRest() {
        lifecycle.register { throw IllegalStateException("already gone") }
        lifecycle.register(child("next"))

        lifecycle.shutdown(UserServiceLifecycle.REASON_DESTROY)

        assertEquals(listOf("terminate:next", "exit:destroy"), events)
    }

    @Test
    fun shutdownWaitsForAChildBeingEndedOnAnotherThread() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ending = UserServiceLifecycle.Once {
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            events += "terminate:shell"
        }
        lifecycle.register { ending.run() }
        // The host's client token died first and is ending the shell. zh-CN: 宿主的 client 令牌先死亡, 正在结束该 shell.
        val clientDeath = Thread { ending.run() }.apply { start() }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val shutdown = Thread { lifecycle.shutdown(UserServiceLifecycle.REASON_HOST_DIED) }.apply { start() }
        shutdown.join(200)
        assertTrue(shutdown.isAlive)
        assertEquals(emptyList<String>(), events)

        release.countDown()
        shutdown.join(5_000)
        clientDeath.join(5_000)
        assertEquals(listOf("terminate:shell", "exit:host-died"), events)
    }

    private fun child(name: String) = UserServiceLifecycle.Child { events += "terminate:$name" }

    private class FakeScheduler : UserServiceLifecycle.Scheduler {

        class Task(val delayMillis: Long, val action: Runnable) {
            var cancelled = false
        }

        val pending = mutableListOf<Task>()

        override fun schedule(delayMillis: Long, action: Runnable): UserServiceLifecycle.Cancellable {
            val task = Task(delayMillis, action).also { pending += it }
            return UserServiceLifecycle.Cancellable { task.cancelled = true }
        }

        fun runPending() {
            pending.filterNot { it.cancelled }.forEach { it.action.run() }
        }

    }

}
