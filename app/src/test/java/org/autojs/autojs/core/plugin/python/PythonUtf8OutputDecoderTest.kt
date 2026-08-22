package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PythonUtf8OutputDecoderTest {

    @Test
    fun emitsCompleteTextImmediatelyAndRetainsOnlySplitCodePoints() {
        val decoder = PythonUtf8OutputDecoder()
        val expected = "prefix-中文-🙂-suffix"
        val bytes = expected.toByteArray(Charsets.UTF_8)
        val actual = buildString {
            bytes.forEach { byte -> append(decoder.decode(byteArrayOf(byte))) }
            append(decoder.finish())
        }

        assertEquals(expected, actual)
    }

    @Test
    fun keepsIndependentCallsLiveInsteadOfWaitingForFinish() {
        val decoder = PythonUtf8OutputDecoder()

        assertEquals("first\n", decoder.decode("first\n".toByteArray()))
        assertEquals("second\n", decoder.decode("second\n".toByteArray()))
        assertEquals("", decoder.finish())
        assertEquals("", decoder.finish())
    }

    @Test
    fun replacesMalformedAndTerminallyIncompleteUtf8LikeThePreviousDecoder() {
        val decoder = PythonUtf8OutputDecoder()

        assertEquals("�", decoder.decode(byteArrayOf(0x80.toByte())))
        assertEquals("", decoder.decode(byteArrayOf(0xE2.toByte(), 0x82.toByte())))
        assertEquals("�", decoder.finish())
    }

    @Test
    fun rejectsOutputAfterTheStreamIsFinished() {
        val decoder = PythonUtf8OutputDecoder()
        decoder.finish()

        assertThrows(IllegalStateException::class.java) {
            decoder.decode(byteArrayOf(1))
        }
    }
}
