package org.autojs.autojs.core.plugin.hostbroker

import org.autojs.autojs.engine.NodeBridgeCapabilityProvider
import org.autojs.autojs.engine.NodeBridgeError
import org.autojs.autojs.engine.NodeBridgeErrorCategory
import org.autojs.autojs.engine.NodeBridgeRequest
import org.autojs.autojs.engine.NodeBridgeResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

class HostCapabilityDispatchQueueTest {
    @Test fun concurrencyIsBoundedAndDuplicateRepliesAreIgnored() {
        val accepted = CountDownLatch(1)
        lateinit var reply: (NodeBridgeResponse) -> Unit
        val provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
                reply = callback
                accepted.countDown()
            }
        }
        val queue = HostCapabilityDispatchQueue(provider, 1)
        val responses = CopyOnWriteArrayList<NodeBridgeResponse>()
        try {
            queue.dispatch(request("first"), responses::add)
            assertTrue(accepted.await(2, TimeUnit.SECONDS))
            queue.dispatch(request("second"), responses::add)
            assertEquals(NodeBridgeErrorCategory.RESOURCE_LIMIT, responses.single().error?.category)
            reply(NodeBridgeResponse.success("first", "true"))
            reply(NodeBridgeResponse.success("first", "false"))
            assertEquals(2, responses.size)
            assertTrue(responses.last().ok)
        } finally {
            queue.destroy(NodeBridgeError.processDead())
        }
    }

    @Test fun timeoutCompletesOnceAndDestroyRejectsNewRequests() {
        val delivered = CountDownLatch(1)
        val responses = CopyOnWriteArrayList<NodeBridgeResponse>()
        val provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) = Unit
        }
        val queue = HostCapabilityDispatchQueue(provider, 1)
        try {
            queue.dispatch(request("timeout").copy(timeoutMs = 25)) { responses += it; delivered.countDown() }
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            assertEquals(NodeBridgeErrorCategory.TIMEOUT, responses.single().error?.category)
            queue.destroy(NodeBridgeError.processDead())
            queue.dispatch(request("closed"), responses::add)
            assertEquals(NodeBridgeErrorCategory.PROCESS_DEAD, responses.last().error?.category)
            assertEquals(2, responses.size)
        } finally {
            queue.destroy(NodeBridgeError.processDead())
        }
    }

    @Test fun destroyCompletesAllPendingCallsAndCleansUpProviderOnce() {
        val provider = object : NodeBridgeCapabilityProvider {
            var destroys = 0
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) = Unit
            override fun destroy(reason: NodeBridgeError) { destroys++ }
        }
        val queue = HostCapabilityDispatchQueue(provider, 2)
        val responses = CopyOnWriteArrayList<NodeBridgeResponse>()
        queue.dispatch(request("one"), responses::add)
        queue.dispatch(request("two"), responses::add)
        queue.destroy(NodeBridgeError.processDead())
        queue.destroy(NodeBridgeError.processDead())
        assertEquals(2, responses.size)
        assertTrue(responses.all { it.error?.category == NodeBridgeErrorCategory.PROCESS_DEAD })
        assertEquals(1, provider.destroys)
    }

    @Test fun lateProviderResultsAreHandedBackForPayloadCleanup() {
        val accepted = CountDownLatch(1)
        lateinit var reply: (NodeBridgeResponse) -> Unit
        val provider = object : NodeBridgeCapabilityProvider {
            override fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
                reply = callback
                accepted.countDown()
            }
        }
        val discarded = CopyOnWriteArrayList<NodeBridgeResponse>()
        val queue = HostCapabilityDispatchQueue(provider, 1, discarded::add)
        val responses = CopyOnWriteArrayList<NodeBridgeResponse>()
        queue.dispatch(request("late"), responses::add)
        assertTrue(accepted.await(2, TimeUnit.SECONDS))
        queue.destroy(NodeBridgeError.processDead())
        reply(NodeBridgeResponse.success("late", "true"))
        assertEquals(1, responses.size)
        assertEquals(NodeBridgeErrorCategory.PROCESS_DEAD, responses.single().error?.category)
        assertEquals(1, discarded.size)
        assertTrue(discarded.single().ok)
    }

    private fun request(id: String) = NodeBridgeRequest(id, "device", "info", "[]", 5_000L)
}
