package org.autojs.autojs.core.plugin.gallery

import org.autojs.plugin.gallery.api.GalleryContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GallerySessionBridgeTest {

    private class RecordingSink : GallerySessionSink {
        val events = ArrayList<GalleryEvent>()
        val dropped = ArrayList<Triple<String, Long, Long>>()

        override fun onEvent(event: GalleryEvent) {
            events += event
        }

        override fun onDropped(reason: String, generation: Long, seq: Long) {
            dropped += Triple(reason, generation, seq)
        }
    }

    @Test
    fun locksOntoTheFirstGenerationAndAcceptsAStrictlyIncreasingSequence() {
        val sink = RecordingSink()
        val bridge = GallerySessionBridge(sink)
        assertNull(bridge.generation)
        assertTrue(bridge.accept(3L, 1L, GalleryEvent(GalleryContract.EVENT_OPEN, kind = GalleryContract.KIND_VIEWER)))
        assertTrue(bridge.accept(3L, 2L, GalleryEvent(GalleryContract.EVENT_PAGE, index = 0)))
        assertTrue(bridge.accept(3L, 5L, GalleryEvent(GalleryContract.EVENT_PAGE, index = 1)))
        assertEquals(3L, bridge.generation)
        assertEquals(5L, bridge.lastSeq)
        assertEquals(listOf(null, 0, 1), sink.events.map { it.index })
    }

    @Test
    fun dropsStaleGenerationsReorderedAndMalformedEvents() {
        val sink = RecordingSink()
        val bridge = GallerySessionBridge(sink)
        assertTrue(bridge.accept(3L, 2L, GalleryEvent(GalleryContract.EVENT_OPEN)))
        assertFalse(bridge.accept(4L, 3L, GalleryEvent(GalleryContract.EVENT_PAGE, index = 1)))
        assertFalse(bridge.accept(3L, 2L, GalleryEvent(GalleryContract.EVENT_PAGE, index = 1)))
        assertFalse(bridge.accept(3L, 3L, GalleryEvent("progress")))
        assertFalse(bridge.accept(3L, 4L, null))
        assertEquals(
            listOf(
                GallerySessionBridge.REASON_STALE_GENERATION,
                GallerySessionBridge.REASON_OUT_OF_ORDER,
                GallerySessionBridge.REASON_MALFORMED,
                GallerySessionBridge.REASON_MALFORMED,
            ),
            sink.dropped.map { it.first },
        )
        assertEquals(1L, bridge.deliveredCount)
        assertEquals(1L, bridge.staleCount)
        assertEquals(1L, bridge.reorderedCount)
        assertEquals(2L, bridge.malformedCount)
    }

    @Test
    fun theCloseEventEndsTheSessionAndALocalCloseIsDeliveredOnce() {
        val sink = RecordingSink()
        val bridge = GallerySessionBridge(sink)
        assertTrue(bridge.accept(1L, 1L, GalleryEvent(GalleryContract.EVENT_RESULT, itemsJson = "[]")))
        assertTrue(bridge.accept(1L, 2L, GalleryEvent(GalleryContract.EVENT_CLOSE, reason = GalleryContract.REASON_DONE)))
        assertTrue(bridge.isClosed)
        assertFalse(bridge.accept(1L, 3L, GalleryEvent(GalleryContract.EVENT_PAGE, index = 2)))
        bridge.closeLocally(GalleryContract.REASON_HOST)
        assertEquals(listOf(GalleryContract.EVENT_RESULT, GalleryContract.EVENT_CLOSE), sink.events.map { it.type })

        val other = RecordingSink()
        val local = GallerySessionBridge(other)
        local.closeLocally(GalleryContract.REASON_ERROR)
        local.closeLocally(GalleryContract.REASON_HOST)
        assertEquals(listOf(GalleryContract.REASON_ERROR), other.events.map { it.reason })
        assertFalse(local.accept(1L, 1L, GalleryEvent(GalleryContract.EVENT_OPEN)))
    }
}
