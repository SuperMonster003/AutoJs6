package org.autojs.autojs.core.plugin.python

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CoderResult
import java.nio.charset.CodingErrorAction

/** Incrementally decodes one Python output stream without splitting UTF-8 code points. */
internal class PythonUtf8OutputDecoder {

    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)
    private var finished = false

    @Synchronized
    fun decode(bytes: ByteArray): String {
        check(!finished) { "Python UTF-8 output stream is already finished" }
        if (bytes.isEmpty()) return ""
        val input = ByteBuffer.wrap(joinPending(bytes))
        return decode(input, endOfInput = false).also {
            pending = ByteArray(input.remaining()).also { remainder -> input.get(remainder) }
        }
    }

    @Synchronized
    fun finish(): String {
        if (finished) return ""
        finished = true
        val input = ByteBuffer.wrap(pending)
        pending = ByteArray(0)
        val output = StringBuilder(decode(input, endOfInput = true))
        check(!input.hasRemaining()) { "Python UTF-8 decoder left terminal bytes unread" }
        drain(output, minimumCapacity = 1, decoder::flush)
        return output.toString()
    }

    private fun joinPending(bytes: ByteArray): ByteArray {
        if (pending.isEmpty()) return bytes
        return ByteArray(pending.size + bytes.size).also { combined ->
            pending.copyInto(combined)
            bytes.copyInto(combined, pending.size)
        }
    }

    private fun decode(input: ByteBuffer, endOfInput: Boolean): String =
        StringBuilder(input.remaining()).also { output ->
            drain(output, minimumCapacity = input.remaining()) { chars ->
                decoder.decode(input, chars, endOfInput)
            }
        }.toString()

    private inline fun drain(
        output: StringBuilder,
        minimumCapacity: Int,
        operation: (CharBuffer) -> CoderResult,
    ) {
        val chars = CharBuffer.allocate(maxOf(1, minimumCapacity))
        while (true) {
            val result = operation(chars)
            chars.flip()
            output.append(chars)
            chars.clear()
            when {
                result.isOverflow -> continue
                result.isUnderflow -> return
                else -> result.throwException()
            }
        }
    }
}
