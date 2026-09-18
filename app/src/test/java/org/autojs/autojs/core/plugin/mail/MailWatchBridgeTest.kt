package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailWatchBridgeTest {

    private class RecordingSink : MailWatchSink {
        val events = ArrayList<MailJson.Event>()
        val dropped = ArrayList<Triple<String, Long, Long>>()

        override fun onEvent(event: MailJson.Event) {
            events += event
        }

        override fun onDropped(reason: String, generation: Long, seq: Long) {
            dropped += Triple(reason, generation, seq)
        }
    }

    private fun event(type: String = MailContract.EVENT_MESSAGE, seq: Long? = null, generation: Long? = null): String {
        return buildString {
            append("{\"type\":\"").append(type).append('"')
            seq?.let { append(",\"seq\":").append(it) }
            generation?.let { append(",\"generation\":").append(it) }
            append(",\"folder\":\"INBOX\"}")
        }
    }

    @Test
    fun acceptsAStrictlyIncreasingSequenceOfItsOwnGeneration() {
        val sink = RecordingSink()
        val bridge = MailWatchBridge(5L, sink)
        assertTrue(bridge.accept(5L, 1L, event(seq = 1L, generation = 5L)))
        assertTrue(bridge.accept(5L, 2L, event()))
        assertTrue(bridge.accept(5L, 10L, event(type = MailContract.EVENT_RESYNC)))
        assertEquals(3L, bridge.deliveredCount)
        assertEquals(10L, bridge.lastSeq)
        assertEquals(listOf("message", "message", "resync"), sink.events.map { it.type })
        assertTrue(sink.dropped.isEmpty())
        assertFalse(bridge.isClosed)
    }

    @Test
    fun dropsEventsOfOtherGenerations() {
        val sink = RecordingSink()
        val bridge = MailWatchBridge(5L, sink)
        assertFalse(bridge.accept(4L, 1L, event()))
        assertFalse(bridge.accept(6L, 1L, event()))
        assertFalse(bridge.accept(5L, 1L, event(generation = 4L)))
        assertEquals(3L, bridge.staleCount)
        assertEquals(0L, bridge.lastSeq)
        assertEquals(listOf(MailWatchBridge.REASON_STALE_GENERATION, MailWatchBridge.REASON_STALE_GENERATION, MailWatchBridge.REASON_STALE_GENERATION), sink.dropped.map { it.first })
        assertTrue(bridge.accept(5L, 1L, event()))
    }

    @Test
    fun dropsReplaysAndReorderedDeliveries() {
        val sink = RecordingSink()
        val bridge = MailWatchBridge(1L, sink)
        assertTrue(bridge.accept(1L, 2L, event()))
        assertFalse(bridge.accept(1L, 1L, event()))
        assertFalse(bridge.accept(1L, 2L, event()))
        assertFalse(bridge.accept(1L, 0L, event()))
        assertTrue(bridge.accept(1L, 3L, event()))
        assertEquals(3L, bridge.reorderedCount)
        assertEquals(2L, bridge.deliveredCount)
        assertEquals(listOf(1L, 2L, 0L), sink.dropped.map { it.third })
        assertTrue(sink.dropped.all { it.first == MailWatchBridge.REASON_OUT_OF_ORDER })
    }

    @Test
    fun dropsMalformedOrInconsistentEnvelopes() {
        val sink = RecordingSink()
        val bridge = MailWatchBridge(1L, sink)
        assertFalse(bridge.accept(1L, 1L, null))
        assertFalse(bridge.accept(1L, 1L, "{"))
        assertFalse(bridge.accept(1L, 1L, "{\"seq\":1}"))
        assertFalse(bridge.accept(1L, 1L, event(seq = 2L)))
        assertEquals(4L, bridge.malformedCount)
        assertEquals(0L, bridge.lastSeq)
        assertTrue(sink.dropped.all { it.first == MailWatchBridge.REASON_MALFORMED })
        assertTrue(bridge.accept(1L, 1L, event(seq = 1L)))
    }

    @Test
    fun closedEventEndsTheWatch() {
        val sink = RecordingSink()
        val bridge = MailWatchBridge(1L, sink)
        assertTrue(bridge.accept(1L, 1L, event(type = MailContract.EVENT_CLOSED)))
        assertTrue(bridge.isClosed)
        assertFalse(bridge.accept(1L, 2L, event()))
        assertEquals(MailWatchBridge.REASON_CLOSED, sink.dropped.single().first)
        assertEquals(1L, bridge.deliveredCount)
    }

    @Test
    fun localCloseDropsLaterEvents() {
        val sink = RecordingSink()
        val bridge = MailWatchBridge(1L, sink)
        bridge.close()
        assertFalse(bridge.accept(1L, 1L, event()))
        assertTrue(sink.events.isEmpty())
        assertEquals(1L, bridge.staleCount)
    }
}
