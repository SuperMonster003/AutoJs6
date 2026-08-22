package org.autojs.autojs.core.plugin.jvm

import java.nio.charset.CharacterCodingException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceStreamingOutputObserverTest {
    @Test
    fun splitUtf8AndLineEndingsPublishCompleteLinesBeforeFinalResidual() {
        val lines = mutableListOf<String>()
        val observer = JvmSourceStreamingOutputObserver(lines::add)
        val bytes = "第一行\r\nsecond\n\n尾巴".toByteArray(Charsets.UTF_8)

        assertFalse(observer.hasObservedBytes)
        bytes.forEach { value -> observer.onBytes(byteArrayOf(value), 0, 1) }

        assertTrue(observer.hasObservedBytes)
        assertEquals(listOf("第一行", "second", ""), lines)

        observer.onComplete()
        observer.onComplete()

        assertEquals(listOf("第一行", "second", "", "尾巴"), lines)
    }

    @Test
    fun malformedUtf8FailsClosed() {
        val observer = JvmSourceStreamingOutputObserver { }

        assertThrows(CharacterCodingException::class.java) {
            observer.onBytes(byteArrayOf(0xc3.toByte(), 0x28), 0, 2)
        }
    }

    @Test
    fun consoleSinkFailureDoesNotStopFollowingLines() {
        val lines = mutableListOf<String>()
        val observer = JvmSourceStreamingOutputObserver { value ->
            if (value == "first") error("synthetic console failure")
            lines += value
        }
        val bytes = "first\nsecond\n".toByteArray()

        observer.onBytes(bytes, 0, bytes.size)
        observer.onComplete()

        assertEquals(listOf("second"), lines)
    }
}
