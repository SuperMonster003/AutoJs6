package org.autojs.autojs.core.tts

/**
 * A contiguous slice of the original TTS text.
 *
 * [start] is inclusive and [end] is exclusive. Both offsets use UTF-16 code units, matching
 * [String] indices and Android TTS range callbacks.
 */
data class TtsTextChunk(
    val text: String,
    val start: Int,
    val end: Int,
) {

    init {
        require(start >= 0) { "TTS chunk start must not be negative" }
        require(end >= start) { "TTS chunk end must not precede start" }
        require(text.length == end - start) {
            "TTS chunk text length must match its UTF-16 offset range"
        }
    }

    val length: Int
        get() = end - start

}

/**
 * Splits text into bounded, lossless chunks suitable for Android text-to-speech engines.
 *
 * Natural boundaries are preferred in this order: sentence-ending punctuation, line breaks, and
 * other whitespace. If none is available, the chunk ends at the hard size limit. A boundary is
 * never placed between the high and low surrogate of a valid UTF-16 surrogate pair.
 */
object TtsTextChunker {

    const val DEFAULT_MAX_CHUNK_LENGTH = 3_999
    const val MAX_TEXT_LENGTH = 1_000_000

    @JvmStatic
    @JvmOverloads
    fun chunk(
        text: String,
        maxChunkLength: Int = DEFAULT_MAX_CHUNK_LENGTH,
    ): List<TtsTextChunk> {
        require(text.isNotBlank()) { "TTS text must not be blank" }
        require(text.length <= MAX_TEXT_LENGTH) {
            "TTS text must not exceed $MAX_TEXT_LENGTH UTF-16 code units"
        }
        require(maxChunkLength > 0) { "TTS chunk length must be positive" }
        require(maxChunkLength <= MAX_TEXT_LENGTH) {
            "TTS chunk length must not exceed $MAX_TEXT_LENGTH UTF-16 code units"
        }

        if (text.length <= maxChunkLength) {
            return listOf(TtsTextChunk(text, 0, text.length))
        }

        val chunks = ArrayList<TtsTextChunk>(
            (text.length + maxChunkLength - 1) / maxChunkLength,
        )
        var start = 0
        while (start < text.length) {
            var hardEnd = minOf(start + maxChunkLength, text.length)
            if (hardEnd < text.length) {
                hardEnd = moveBeforeSplitSurrogatePair(text, start, hardEnd)
                hardEnd = moveBeforeSplitCrLf(text, start, hardEnd)
                require(hardEnd > start) {
                    "TTS chunk length $maxChunkLength is too small for an indivisible text pair"
                }
            }

            val end = if (hardEnd == text.length) {
                hardEnd
            } else {
                findSentenceBoundary(text, start, hardEnd)
                    ?: findLineBoundary(text, start, hardEnd)
                    ?: findWhitespaceBoundary(text, start, hardEnd)
                    ?: hardEnd
            }
            chunks += TtsTextChunk(
                text = text.substring(start, end),
                start = start,
                end = end,
            )
            start = end
        }
        return chunks
    }

    private fun moveBeforeSplitSurrogatePair(
        text: String,
        start: Int,
        end: Int,
    ): Int {
        if (
            end > start &&
            text[end - 1].isHighSurrogate() &&
            text[end].isLowSurrogate()
        ) {
            return end - 1
        }
        return end
    }

    private fun moveBeforeSplitCrLf(
        text: String,
        start: Int,
        end: Int,
    ): Int {
        if (
            end > start &&
            text[end - 1] == '\r' &&
            text[end] == '\n'
        ) {
            return end - 1
        }
        return end
    }

    private fun findSentenceBoundary(
        text: String,
        start: Int,
        hardEnd: Int,
    ): Int? {
        for (index in hardEnd - 1 downTo start) {
            if (!text[index].isSentenceTerminator(text, index)) continue

            var end = index + 1
            while (end < hardEnd && text[end].isSentenceCloser()) {
                end += 1
            }
            return end
        }
        return null
    }

    private fun findLineBoundary(
        text: String,
        start: Int,
        hardEnd: Int,
    ): Int? {
        for (index in hardEnd - 1 downTo start) {
            when (text[index]) {
                '\n', '\u0085', '\u2028', '\u2029' -> return index + 1
                '\r' -> {
                    if (index + 1 < text.length && text[index + 1] == '\n') {
                        if (index + 2 <= hardEnd) return index + 2
                        continue
                    }
                    return index + 1
                }
            }
        }
        return null
    }

    private fun findWhitespaceBoundary(
        text: String,
        start: Int,
        hardEnd: Int,
    ): Int? {
        for (index in hardEnd - 1 downTo start) {
            if (text[index].isWhitespace() && !text[index].isLineBreak()) {
                return index + 1
            }
        }
        return null
    }

    private fun Char.isSentenceTerminator(text: String, index: Int): Boolean {
        return when (this) {
            '。', '！', '？', '；', '!', '?', ';', '\u2026' -> true
            '.' -> {
                val previousIsDigit = index > 0 && text[index - 1].isDigit()
                val nextIsDigit = index + 1 < text.length && text[index + 1].isDigit()
                !(previousIsDigit && nextIsDigit)
            }
            else -> false
        }
    }

    private fun Char.isSentenceCloser(): Boolean {
        return this in SENTENCE_CLOSERS
    }

    private fun Char.isLineBreak(): Boolean {
        return this == '\r' ||
            this == '\n' ||
            this == '\u0085' ||
            this == '\u2028' ||
            this == '\u2029'
    }

    private fun Char.isHighSurrogate(): Boolean {
        return this in '\uD800'..'\uDBFF'
    }

    private fun Char.isLowSurrogate(): Boolean {
        return this in '\uDC00'..'\uDFFF'
    }

    private val SENTENCE_CLOSERS = setOf(
        '"',
        '\'',
        ')',
        ']',
        '}',
        '\u00BB',
        '\u2019',
        '\u201D',
        '\u3009',
        '\u300B',
        '\u300D',
        '\u300F',
        '\u3011',
        '\u3015',
        '\u3017',
        '\u3019',
        '\u301B',
    )

}
