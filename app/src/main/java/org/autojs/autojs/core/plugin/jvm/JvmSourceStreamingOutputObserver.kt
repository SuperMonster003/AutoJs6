package org.autojs.autojs.core.plugin.jvm

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/** Incrementally validates UTF-8 and publishes complete console lines while a worker is running. */
internal class JvmSourceStreamingOutputObserver(
    private val publishLine: (String) -> Unit,
) : JvmSourceOutputObserver {
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    private val line = StringBuilder()
    private var pending = ByteArray(0)
    private val observed = AtomicBoolean(false)
    private val completed = AtomicBoolean(false)

    val hasObservedBytes: Boolean
        get() = observed.get()

    @Synchronized
    override fun onBytes(bytes: ByteArray, offset: Int, count: Int) {
        check(!completed.get()) { "JVM source output arrived after stream completion" }
        require(offset >= 0 && count >= 0 && offset <= bytes.size - count)
        if (count == 0) return
        observed.set(true)
        val inputBytes = ByteArray(pending.size + count)
        pending.copyInto(inputBytes)
        bytes.copyInto(inputBytes, pending.size, offset, offset + count)
        decode(ByteBuffer.wrap(inputBytes), endOfInput = false)
    }

    @Synchronized
    override fun onComplete() {
        if (!completed.compareAndSet(false, true)) return
        decode(ByteBuffer.wrap(pending), endOfInput = true)
        val flushed = CharBuffer.allocate(8)
        while (true) {
            val result = decoder.flush(flushed)
            flushed.flip()
            append(flushed)
            flushed.clear()
            if (result.isUnderflow) break
            result.throwException()
        }
        if (line.isNotEmpty()) publish(line.toString())
    }

    private fun decode(input: ByteBuffer, endOfInput: Boolean) {
        val output = CharBuffer.allocate((input.remaining() * decoder.maxCharsPerByte()).toInt() + 1)
        while (true) {
            val result = decoder.decode(input, output, endOfInput)
            output.flip()
            append(output)
            output.clear()
            if (result.isUnderflow) break
            result.throwException()
        }
        pending = ByteArray(input.remaining()).also(input::get)
        if (endOfInput) check(pending.isEmpty()) { "Incomplete UTF-8 at end of JVM source output" }
    }

    private fun append(chars: CharBuffer) {
        while (chars.hasRemaining()) {
            when (val value = chars.get()) {
                '\n' -> {
                    if (line.endsWith("\r")) line.setLength(line.length - 1)
                    publish(line.toString())
                }
                else -> line.append(value)
            }
        }
    }

    private fun publish(value: String) {
        line.setLength(0)
        runCatching { publishLine(value) }
    }
}
