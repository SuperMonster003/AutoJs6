package org.autojs.autojs.core.ai.transport

import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import kotlin.math.min

class SseReaderTest {

    @Test
    fun readsCommentsCrLfLoneCrMultilineDataAndDoneSentinel() {
        val input = "\uFEFF: initial comment\r\n" +
            "id: 42\r\n" +
            "event: message\r\n" +
            "retry: 2500\r\n" +
            "data: first\r\n" +
            "data: second\r\n" +
            "\r\n" +
            ": keepalive\r" +
            "data: [DONE]\r" +
            "\r" +
            "data: ignored\n\n"

        SseReader(input.byteInputStream()).use { reader ->
            val first = requireNotNull(reader.readEvent())
            assertEquals("42", first.id)
            assertEquals("message", first.event)
            assertEquals("first\nsecond", first.data)
            assertEquals(2500L, first.retryMillis)
            assertFalse(first.isDone)

            val done = requireNotNull(reader.readEvent())
            assertEquals("42", done.id)
            assertNull(done.event)
            assertEquals("[DONE]", done.data)
            assertEquals(2500L, done.retryMillis)
            assertTrue(done.isDone)
            assertNull(reader.readEvent())
        }
    }

    @Test
    fun dispatchesFinalUnterminatedUtf8EventAcrossOneByteReads() {
        val payload = "data: {\"text\":\"é你🙂\"}"
        val bytes = payload.toByteArray()
        val oneByteAtATime = object : ByteArrayInputStream(bytes) {
            override fun read(target: ByteArray, offset: Int, length: Int): Int {
                return super.read(target, offset, min(1, length))
            }
        }

        SseReader(oneByteAtATime.source().buffer()).use { reader ->
            assertEquals("{\"text\":\"é你🙂\"}", requireNotNull(reader.readEvent()).data)
            assertNull(reader.readEvent())
        }
    }

    @Test
    fun ignoresFieldsWithoutDataButDispatchesAnExplicitEmptyDataField() {
        val input = "event: ignored\nid: one\n\nunknown: value\n\ndata\n\n"

        SseReader(input.byteInputStream()).use { reader ->
            val event = requireNotNull(reader.readEvent())
            assertEquals("one", event.id)
            assertEquals("", event.data)
            assertNull(reader.readEvent())
        }
    }

    @Test
    fun enforcesSingleEventByteLimit() {
        val reader = SseReader(
            "data: 12345\n\n".byteInputStream(),
            SseLimits(maxEventBytes = 10, maxTotalBytes = 100),
        )

        reader.use {
            val exception = assertThrows(SseLimitExceededException::class.java) {
                it.readEvent()
            }
            assertEquals("single event", exception.limitName)
            assertEquals(10L, exception.limitBytes)
        }
    }

    @Test
    fun enforcesCumulativeStreamByteLimitAcrossEvents() {
        val reader = SseReader(
            "data:x\n\ndata:y\n\n".byteInputStream(),
            SseLimits(maxEventBytes = 8, maxTotalBytes = 12),
        )

        reader.use {
            assertEquals("x", requireNotNull(it.readEvent()).data)
            val exception = assertThrows(SseLimitExceededException::class.java) {
                it.readEvent()
            }
            assertEquals("total stream", exception.limitName)
            assertEquals(12L, exception.limitBytes)
        }
    }

}
