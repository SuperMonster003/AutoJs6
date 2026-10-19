package org.autojs.autojs.core.plugin.bun

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BunQueuedBinderCallLaneTest {
    private fun fixture(action:(BunQueuedBinderCallLane,java.util.concurrent.ExecutorService,CountDownLatch)->Unit) {
        val lane=BunQueuedBinderCallLane("queued-test");val pool=Executors.newFixedThreadPool(2);val release=CountDownLatch(1)
        try{action(lane,pool,release)}finally{release.countDown();pool.shutdownNow();pool.awaitTermination(2,TimeUnit.SECONDS)}
    }
    @Test fun laterCallsWaitForTheActiveCallAndAreDispatchedExactlyOnce()=fixture {lane,pool,release->
        val started=CountDownLatch(1);val first=pool.submit<Int>{lane.call("run",3000){started.countDown();release.await();1}}
        assertTrue(started.await(1,TimeUnit.SECONDS))
        val second=pool.submit<Int>{lane.call("select",3000){2}};Thread.sleep(50);assertFalse(second.isDone)
        release.countDown();assertEquals(1,first.get(1,TimeUnit.SECONDS));assertEquals(2,second.get(1,TimeUnit.SECONDS))
    }
    @Test fun queueTimeoutDoesNotDispatchAbandonOrPoisonTheLane()=fixture {lane,pool,release->
        val started=CountDownLatch(1);val first=pool.submit<Int>{lane.call("run",3000){started.countDown();release.await();1}}
        assertTrue(started.await(1,TimeUnit.SECONDS));val invoked=AtomicBoolean(false);val abandoned=AtomicBoolean(false)
        val error=assertThrows(BunBinderCallException::class.java){lane.call("queued",25,onAbandoned={abandoned.set(true);lane.poison()}){invoked.set(true);2}}
        assertEquals(BunBinderCallFailure.TIMEOUT,error.failure);assertFalse(invoked.get());assertFalse(abandoned.get())
        release.countDown();first.get(1,TimeUnit.SECONDS);assertEquals(3,lane.call("next",1000){3})
    }
    @Test fun cancellationWhileQueuedLeavesTheCurrentScriptRunning()=fixture {lane,pool,release->
        val started=CountDownLatch(1);val first=pool.submit<Int>{lane.call("run",3000){started.countDown();release.await();1}}
        assertTrue(started.await(1,TimeUnit.SECONDS));val cancelled=AtomicBoolean(false);val invoked=AtomicBoolean(false)
        val waiting=pool.submit<Boolean>{try{lane.call("queued",3000,isCancelled={cancelled.get()}){invoked.set(true)};false}catch(_:InterruptedException){true}}
        Thread.sleep(50);cancelled.set(true);assertTrue(waiting.get(1,TimeUnit.SECONDS));assertFalse(first.isDone);assertFalse(invoked.get())
        release.countDown();assertEquals(1,first.get(1,TimeUnit.SECONDS))
    }
}
