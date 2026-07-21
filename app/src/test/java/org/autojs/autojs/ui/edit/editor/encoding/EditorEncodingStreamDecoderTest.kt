package org.autojs.autojs.ui.edit.editor.encoding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class EditorEncodingStreamDecoderTest {

    @Test
    fun utf8CharactersMayCrossEveryInputReadBoundary() {
        val text = "prefix中🙂suffix"
        val decoded = StringBuilder()

        val result = EditorEncodingStreamDecoder.decode(
            input = ChunkedInputStream(text.toByteArray(StandardCharsets.UTF_8), chunkSize = 1),
            charset = StandardCharsets.UTF_8,
            onDecodedText = decoded::append,
        )

        assertTrue(result.completed)
        assertFalse(result.hadDecodingErrors)
        assertEquals(text, decoded.toString())
    }

    @Test
    fun utf8FourByteCharacterMayCrossInternalBufferBoundary() {
        val text = "a".repeat(32 * 1024 - 1) + "🙂tail"

        assertDecodesWithoutErrors(
            text = text,
            charset = StandardCharsets.UTF_8,
            chunkSize = Int.MAX_VALUE,
        )
    }

    @Test
    fun malformedBytesAreReplacedAndReported() {
        val bytes = byteArrayOf('A'.code.toByte(), 0xC3.toByte(), 0x28, 'B'.code.toByte())
        val decoded = StringBuilder()

        val result = EditorEncodingStreamDecoder.decode(
            input = ChunkedInputStream(bytes, chunkSize = 2),
            charset = StandardCharsets.UTF_8,
            onDecodedText = decoded::append,
        )

        assertTrue(result.completed)
        assertTrue(result.hadDecodingErrors)
        assertEquals("A�(B", decoded.toString())
    }

    @Test
    fun consecutiveMalformedUtf8BytesAreReplacedAndValidTextAfterThemIsPreserved() {
        val bytes = byteArrayOf(
            'A'.code.toByte(),
            0xFF.toByte(),
            0xFE.toByte(),
            0x80.toByte(),
            0xE4.toByte(),
            0xB8.toByte(),
            0xAD.toByte(),
            0xE2.toByte(),
            0x28,
            0xA1.toByte(),
            'Z'.code.toByte(),
        )
        val decoded = StringBuilder()

        val result = EditorEncodingStreamDecoder.decode(
            input = ChunkedInputStream(bytes, chunkSize = 1),
            charset = StandardCharsets.UTF_8,
            onDecodedText = decoded::append,
        )

        assertTrue(result.completed)
        assertTrue(result.hadDecodingErrors)
        assertEquals("A���中�(�Z", decoded.toString())
    }

    @Test
    fun truncatedTrailingSequenceIsReplacedAndReportedAtEndOfInput() {
        val bytes = byteArrayOf('A'.code.toByte(), 0xE2.toByte(), 0x82.toByte())
        val decoded = StringBuilder()

        val result = EditorEncodingStreamDecoder.decode(
            input = ChunkedInputStream(bytes, chunkSize = 1),
            charset = StandardCharsets.UTF_8,
            onDecodedText = decoded::append,
        )

        assertTrue(result.completed)
        assertTrue(result.hadDecodingErrors)
        assertEquals("A�", decoded.toString())
    }

    @Test
    fun statefulIso2022JpSurvivesSingleByteReads() {
        assumeTrue("Host JVM does not support ISO-2022-JP", Charset.isSupported("ISO-2022-JP"))
        val charset = Charset.forName("ISO-2022-JP")
        val text = "ASCII-日本語-kana-かな-END-日本"

        assertDecodesWithoutErrors(
            text = text,
            charset = charset,
            chunkSize = 1,
        )
    }

    @Test
    fun utf16LittleEndianSurvivesSingleByteReadsAndSurrogateBoundaries() {
        assertDecodesWithoutErrors(
            text = "LE-中🙂文-tail",
            charset = StandardCharsets.UTF_16LE,
            chunkSize = 1,
        )
    }

    @Test
    fun utf16BigEndianSurvivesSingleByteReadsAndSurrogateBoundaries() {
        assertDecodesWithoutErrors(
            text = "BE-中🙂文-tail",
            charset = StandardCharsets.UTF_16BE,
            chunkSize = 1,
        )
    }

    @Test
    fun malformedUtf16CodeUnitIsReportedAndFollowingTextIsRecoveredInBothByteOrders() {
        val cases = listOf(
            StandardCharsets.UTF_16LE to byteArrayOf(0x00, 0xDC.toByte(), 0x41, 0x00),
            StandardCharsets.UTF_16BE to byteArrayOf(0xDC.toByte(), 0x00, 0x00, 0x41),
        )

        cases.forEach { (charset, bytes) ->
            val decoded = StringBuilder()

            val result = EditorEncodingStreamDecoder.decode(
                input = ChunkedInputStream(bytes, chunkSize = 1),
                charset = charset,
                onDecodedText = decoded::append,
            )

            assertTrue(charset.name(), result.completed)
            assertTrue(charset.name(), result.hadDecodingErrors)
            assertEquals(charset.name(), "�A", decoded.toString())
        }
    }

    @Test
    fun gb18030TwoAndFourByteCharactersSurviveSingleByteReads() {
        assumeTrue("Host JVM does not support GB18030", Charset.isSupported("GB18030"))
        val charset = Charset.forName("GB18030")
        val supplementaryCharacter = "𠀀"
        val text = "ASCII-中文-$supplementaryCharacter-tail"
        val supplementaryBytes = supplementaryCharacter.toByteArray(charset)

        assertEquals(4, supplementaryBytes.size)
        assertEquals(supplementaryCharacter, String(supplementaryBytes, charset))
        assertDecodesWithoutErrors(text, charset, chunkSize = 1)
    }

    @Test
    fun big5CharactersSurviveSingleByteReads() {
        assumeTrue("Host JVM does not support Big5", Charset.isSupported("Big5"))
        val charset = Charset.forName("Big5")

        assertDecodesWithoutErrors(
            text = "ASCII-中文繁體測試-尾",
            charset = charset,
            chunkSize = 1,
        )
    }

    @Test
    fun zeroLengthBulkReadsFallBackToSingleByteReadsWithoutLosingData() {
        val text = "zero-零🙂\u0000-tail"
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val input = ZeroBulkReadInputStream(bytes)
        val decoded = StringBuilder()

        val result = EditorEncodingStreamDecoder.decode(
            input = input,
            charset = StandardCharsets.UTF_8,
            onDecodedText = decoded::append,
        )

        assertTrue(result.completed)
        assertFalse(result.hadDecodingErrors)
        assertEquals(text, decoded.toString())
        assertEquals(bytes.size + 1, input.singleByteReadCount)
    }

    private fun assertDecodesWithoutErrors(
        text: String,
        charset: Charset,
        chunkSize: Int,
    ) {
        val bytes = text.toByteArray(charset)
        assertEquals(
            "Test fixture must be exactly representable in ${charset.name()}",
            text,
            String(bytes, charset),
        )
        val decoded = StringBuilder()

        val result = EditorEncodingStreamDecoder.decode(
            input = ChunkedInputStream(bytes, chunkSize),
            charset = charset,
            onDecodedText = decoded::append,
        )

        assertTrue(result.completed)
        assertFalse(result.hadDecodingErrors)
        assertEquals(text, decoded.toString())
    }

    private class ChunkedInputStream(
        private val bytes: ByteArray,
        private val chunkSize: Int,
    ) : InputStream() {

        private var position = 0

        override fun read(): Int {
            if (position >= bytes.size) return -1
            return bytes[position++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(chunkSize, length, bytes.size - position)
            bytes.copyInto(buffer, offset, position, position + count)
            position += count
            return count
        }
    }

    private class ZeroBulkReadInputStream(
        private val bytes: ByteArray,
    ) : InputStream() {

        private var position = 0
        var singleByteReadCount = 0
            private set

        override fun read(): Int {
            singleByteReadCount++
            if (position >= bytes.size) return -1
            return bytes[position++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
    }
}
