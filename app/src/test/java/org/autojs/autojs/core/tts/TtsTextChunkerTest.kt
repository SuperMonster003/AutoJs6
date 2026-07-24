package org.autojs.autojs.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextChunkerTest {

    @Test
    fun splitsAsciiAtSentenceWhitespaceAndHardBoundaries() {
        assertEquals(
            listOf("Hello.", " Next ", "part"),
            TtsTextChunker.chunk("Hello. Next part", maxChunkLength = 6).map { it.text },
        )
        assertEquals(
            listOf("alpha ", "beta"),
            TtsTextChunker.chunk("alpha beta", maxChunkLength = 6).map { it.text },
        )
        assertEquals(
            listOf("abcd", "efgh", "ij"),
            TtsTextChunker.chunk("abcdefghij", maxChunkLength = 4).map { it.text },
        )
    }

    @Test
    fun recognizesChineseSentencePunctuation() {
        val text = "第一句。第二句！第三句？"

        assertEquals(
            listOf("第一句。", "第二句！", "第三句？"),
            TtsTextChunker.chunk(text, maxChunkLength = 6).map { it.text },
        )
    }

    @Test
    fun prefersSentenceThenLineThenWhitespaceBoundaries() {
        assertEquals(
            listOf("one.", "\ntwo "),
            TtsTextChunker.chunk("one.\ntwo ", maxChunkLength = 8).map { it.text },
        )
        assertEquals(
            listOf("alpha\n", "beta"),
            TtsTextChunker.chunk("alpha\nbeta", maxChunkLength = 7).map { it.text },
        )
        assertEquals(
            listOf("alpha ", "beta"),
            TtsTextChunker.chunk("alpha beta", maxChunkLength = 7).map { it.text },
        )
    }

    @Test
    fun neverSplitsUtf16SurrogatePairAtHardBoundary() {
        val text = "abc🙂def"
        val chunks = TtsTextChunker.chunk(text, maxChunkLength = 4)

        assertEquals(listOf("abc", "🙂de", "f"), chunks.map { it.text })
        chunks.forEach { chunk ->
            assertFalse(chunk.text.first().isLowSurrogate())
            assertFalse(chunk.text.last().isHighSurrogate())
        }
        assertEquals(text, chunks.joinToString(separator = "") { it.text })
    }

    @Test
    fun neverSplitsCrLfAtHardBoundary() {
        val text = "abc\r\ndef"
        val chunks = TtsTextChunker.chunk(text, maxChunkLength = 4)

        assertEquals(text, chunks.joinToString(separator = "") { it.text })
        assertFalse(chunks.zipWithNext().any { (left, right) ->
            left.text.endsWith('\r') && right.text.startsWith('\n')
        })
    }

    @Test
    fun acceptsExactlyDefaultMaximumAsOneChunk() {
        val text = "a".repeat(TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH)

        assertEquals(
            listOf(TtsTextChunk(text, 0, TtsTextChunker.DEFAULT_MAX_CHUNK_LENGTH)),
            TtsTextChunker.chunk(text),
        )
    }

    @Test
    fun rejectsBlankOversizedAndInvalidLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            TtsTextChunker.chunk(" \n\t")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsTextChunker.chunk("a".repeat(TtsTextChunker.MAX_TEXT_LENGTH + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsTextChunker.chunk("text", maxChunkLength = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TtsTextChunker.chunk("🙂", maxChunkLength = 1)
        }
    }

    @Test
    fun preservesEveryOriginalCodeUnitAcrossMixedContent() {
        val text = buildString {
            repeat(40) { index ->
                append("第")
                append(index)
                append("段🙂 has words.\r\n")
            }
        }
        val chunks = TtsTextChunker.chunk(text, maxChunkLength = 17)

        assertEquals(text, chunks.joinToString(separator = "") { it.text })
        assertTrue(chunks.all { it.length in 1..17 })
    }

    @Test
    fun reportsContiguousUtf16OffsetsIntoOriginalText() {
        val text = "甲乙。abc 🙂 xyz\n尾声"
        val chunks = TtsTextChunker.chunk(text, maxChunkLength = 6)

        var expectedStart = 0
        chunks.forEach { chunk ->
            assertEquals(expectedStart, chunk.start)
            assertEquals(text.substring(chunk.start, chunk.end), chunk.text)
            assertEquals(chunk.text.length, chunk.length)
            expectedStart = chunk.end
        }
        assertEquals(text.length, expectedStart)
    }

    private fun Char.isHighSurrogate(): Boolean {
        return this in '\uD800'..'\uDBFF'
    }

    private fun Char.isLowSurrogate(): Boolean {
        return this in '\uDC00'..'\uDFFF'
    }

}
