package org.autojs.autojs.ipc

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppEventBusTest {
    @Test fun newReceiversDoNotReplayAnOldBroadcast() = runBlocking {
        val action="test.broadcast.${System.nanoTime()}"
        val stream=InAppEventBus.streamOf(action)
        assertTrue(InAppEventBus.tryEmit(action));assertTrue(stream.replayCache.isEmpty())
        val next=async(start=CoroutineStart.UNDISPATCHED){withTimeout(1000){stream.first()}}
        assertTrue(InAppEventBus.tryEmit(action));next.await();assertTrue(stream.replayCache.isEmpty())
    }
}
