package org.autojs.autojs.ui.edit.editor.encoding

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class EditorEncodingStreamDecodeResult(
    val completed: Boolean,
    val hadDecodingErrors: Boolean,
)

/** Incrementally decodes a stream while reporting every replacement instead of hiding data loss. */
object EditorEncodingStreamDecoder {

    fun decode(
        input: InputStream,
        charset: Charset,
        isCancelled: () -> Boolean = { false },
        onDecodedText: (String) -> Unit,
    ): EditorEncodingStreamDecodeResult {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val byteBuffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE)
        val charBuffer = CharBuffer.allocate(CHAR_BUFFER_SIZE)
        var endOfInput = false
        var hadDecodingErrors = false

        while (!isCancelled() && !endOfInput) {
            // A conforming decoder consumes an incomplete sequence long before this buffer fills.
            // Keep a malicious or broken decoder from causing a zero-length read/buffer overflow.
            if (!byteBuffer.hasRemaining()) {
                hadDecodingErrors = true
                onDecodedText(decoder.replacement())
                byteBuffer.flip()
                byteBuffer.get()
                byteBuffer.compact()
            }
            val offset = byteBuffer.position()
            val read = input.read(byteBuffer.array(), offset, byteBuffer.remaining())
            when {
                read < 0 -> endOfInput = true
                read == 0 -> {
                    val oneByte = input.read()
                    if (oneByte < 0) {
                        endOfInput = true
                    } else {
                        byteBuffer.put(oneByte.toByte())
                    }
                }
                else -> byteBuffer.position(offset + read)
            }

            byteBuffer.flip()
            while (!isCancelled()) {
                val result = decoder.decode(byteBuffer, charBuffer, endOfInput)
                when {
                    result.isOverflow -> emitDecodedChars(charBuffer, onDecodedText)
                    result.isError -> {
                        hadDecodingErrors = true
                        val replacement = decoder.replacement()
                        if (charBuffer.remaining() < replacement.length) {
                            emitDecodedChars(charBuffer, onDecodedText)
                        }
                        charBuffer.put(replacement)
                        byteBuffer.position(
                            (byteBuffer.position() + result.length()).coerceAtMost(byteBuffer.limit()),
                        )
                    }
                    else -> break
                }
            }
            emitDecodedChars(charBuffer, onDecodedText)
            byteBuffer.compact()
        }

        if (isCancelled()) {
            return EditorEncodingStreamDecodeResult(
                completed = false,
                hadDecodingErrors = hadDecodingErrors,
            )
        }

        while (true) {
            val result = decoder.flush(charBuffer)
            if (result.isOverflow) {
                emitDecodedChars(charBuffer, onDecodedText)
            } else {
                break
            }
        }
        emitDecodedChars(charBuffer, onDecodedText)
        return EditorEncodingStreamDecodeResult(
            completed = true,
            hadDecodingErrors = hadDecodingErrors,
        )
    }

    private fun emitDecodedChars(buffer: CharBuffer, onDecodedText: (String) -> Unit) {
        buffer.flip()
        if (buffer.hasRemaining()) {
            onDecodedText(buffer.toString())
        }
        buffer.clear()
    }

    private const val BYTE_BUFFER_SIZE = 32 * 1024
    private const val CHAR_BUFFER_SIZE = 32 * 1024
}
