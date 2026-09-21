package org.autojs.autojs.core.plugin.epub

import org.autojs.plugin.epub.api.EpubContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderBridgeTest {

    private class RecordingSink : EpubReaderSink {
        val events = ArrayList<EpubReaderEvent>()
        val dropped = ArrayList<Triple<String, Long, Long>>()

        override fun onEvent(event: EpubReaderEvent) {
            events += event
        }

        override fun onDropped(reason: String, generation: Long, seq: Long) {
            dropped += Triple(reason, generation, seq)
        }
    }

    private fun event(type: String = EpubContract.EVENT_PROGRESS, reason: String? = null) = EpubReaderEvent(type, reason = reason, progression = 0.25)

    @Test
    fun locksOntoTheFirstGenerationAndAcceptsAStrictlyIncreasingSequence() {
        val sink = RecordingSink()
        val bridge = EpubReaderBridge(sink)
        assertNull(bridge.generation)
        assertTrue(bridge.accept(7L, 1L, event(EpubContract.EVENT_OPEN)))
        assertEquals(7L, bridge.generation)
        assertTrue(bridge.accept(7L, 2L, event()))
        assertTrue(bridge.accept(7L, 10L, event(EpubContract.EVENT_BOOKMARK)))
        // Contract version 2: `highlight` is a known type, so the bridge passes it like the others.
        assertTrue(bridge.accept(7L, 11L, EpubReaderEvent(EpubContract.EVENT_HIGHLIGHT, change = EpubContract.CHANGE_ADDED, annotationsJson = "[]")))
        assertEquals(4L, bridge.deliveredCount)
        assertEquals(11L, bridge.lastSeq)
        assertEquals(listOf(EpubContract.EVENT_OPEN, EpubContract.EVENT_PROGRESS, EpubContract.EVENT_BOOKMARK, EpubContract.EVENT_HIGHLIGHT), sink.events.map { it.type })
        assertEquals("[]", sink.events.last().annotationsJson)
        assertTrue(sink.dropped.isEmpty())
        assertFalse(bridge.isClosed)
    }

    @Test
    fun dropsStaleGenerationsAndReorderedSequences() {
        val sink = RecordingSink()
        val bridge = EpubReaderBridge(sink)
        assertTrue(bridge.accept(3L, 5L, event(EpubContract.EVENT_OPEN)))
        assertFalse(bridge.accept(2L, 6L, event()))
        assertFalse(bridge.accept(4L, 6L, event()))
        assertFalse(bridge.accept(3L, 5L, event()))
        assertFalse(bridge.accept(3L, 4L, event()))
        assertTrue(bridge.accept(3L, 6L, event()))
        assertEquals(2L, bridge.staleCount)
        assertEquals(2L, bridge.reorderedCount)
        assertEquals(2L, bridge.deliveredCount)
        assertEquals(
            listOf(
                Triple(EpubReaderBridge.REASON_STALE_GENERATION, 2L, 6L),
                Triple(EpubReaderBridge.REASON_STALE_GENERATION, 4L, 6L),
                Triple(EpubReaderBridge.REASON_OUT_OF_ORDER, 3L, 5L),
                Triple(EpubReaderBridge.REASON_OUT_OF_ORDER, 3L, 4L),
            ),
            sink.dropped,
        )
    }

    @Test
    fun malformedEventsNeverReachTheSinkNorLockTheGeneration() {
        val sink = RecordingSink()
        val bridge = EpubReaderBridge(sink)
        assertFalse(bridge.accept(1L, 1L, null))
        assertFalse(bridge.accept(1L, 2L, EpubReaderEvent("teleport")))
        assertNull(bridge.generation)
        assertEquals(2L, bridge.malformedCount)
        assertEquals(0L, bridge.lastSeq)
        assertTrue(bridge.accept(9L, 1L, event(EpubContract.EVENT_OPEN)))
        assertEquals(9L, bridge.generation)
        assertEquals(listOf(EpubReaderBridge.REASON_MALFORMED, EpubReaderBridge.REASON_MALFORMED), sink.dropped.map { it.first })
    }

    @Test
    fun theCloseEventEndsTheSessionAndLaterDeliveriesAreDropped() {
        val sink = RecordingSink()
        val bridge = EpubReaderBridge(sink)
        assertTrue(bridge.accept(1L, 1L, event(EpubContract.EVENT_OPEN)))
        assertTrue(bridge.accept(1L, 2L, event(EpubContract.EVENT_CLOSE, EpubContract.REASON_USER)))
        assertTrue(bridge.isClosed)
        assertFalse(bridge.accept(1L, 3L, event()))
        assertEquals(listOf(Triple(EpubReaderBridge.REASON_CLOSED, 1L, 3L)), sink.dropped)
        assertEquals(EpubContract.REASON_USER, sink.events.last().reason)
        bridge.closeLocally(EpubContract.REASON_HOST)
        assertEquals(2, sink.events.size)
        assertEquals(2L, bridge.deliveredCount)
    }

    @Test
    fun closeLocallyDeliversOneSyntheticCloseOnly() {
        val sink = RecordingSink()
        val bridge = EpubReaderBridge(sink)
        assertTrue(bridge.accept(4L, 1L, event(EpubContract.EVENT_OPEN)))
        bridge.closeLocally(EpubContract.REASON_ERROR)
        bridge.closeLocally(EpubContract.REASON_HOST)
        assertTrue(bridge.isClosed)
        assertEquals(listOf(EpubContract.EVENT_OPEN, EpubContract.EVENT_CLOSE), sink.events.map { it.type })
        assertEquals(EpubContract.REASON_ERROR, sink.events.last().reason)
        assertFalse(bridge.accept(4L, 2L, event(EpubContract.EVENT_CLOSE, EpubContract.REASON_USER)))
        assertEquals(EpubReaderBridge.REASON_CLOSED, sink.dropped.single().first)
        assertEquals(2L, bridge.deliveredCount)
    }

    @Test
    fun closeLocallyBeforeAnyEventStillClosesWithoutAGeneration() {
        val sink = RecordingSink()
        val bridge = EpubReaderBridge(sink)
        bridge.closeLocally(EpubContract.REASON_TIMEOUT)
        assertTrue(bridge.isClosed)
        assertNull(bridge.generation)
        assertEquals(EpubContract.REASON_TIMEOUT, sink.events.single().reason)
        assertFalse(bridge.accept(1L, 1L, event(EpubContract.EVENT_OPEN)))
    }
}
