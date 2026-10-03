package org.autojs.autojs.runtime.api.terminal

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/**
 * Turns the byte stream of a plugin output subscription into `output` lines (plugin roadmap
 * P4.2): UTF-8 is decoded incrementally (a character split across two reads survives), ANSI
 * escape sequences (CSI, OSC, DCS and the other `ESC` forms) and C0 controls other than the tab
 * are dropped unless [stripAnsi] is off, `\r` goes with them, lines are cut at `\n` and at
 * [maxLineBytes] (64 KiB of UTF-8 by default; the cut piece is a line of its own and the `\n` that follows
 * a full piece does not add an empty line). Overflow reports of the plugin are passed through in
 * stream order. [finish] flushes the pending line and ends the stream; later input is ignored.
 * Pure JVM; the owner serializes the calls (every entry point is synchronized).
 * zh-CN: 把插件输出订阅的字节流变为 `output` 行 (插件路线图 P4.2): UTF-8 增量解码 (跨两次读取的字符不丢), 除非关闭 [stripAnsi], 否则丢弃 ANSI 转义序列
 * (CSI, OSC, DCS 及其它 `ESC` 形式) 与制表符以外的 C0 控制字符, `\r` 一并丢弃, 行在 `\n` 与 [maxLineBytes] (默认 UTF-8 64 KiB; 截下的片段自成一行, 整片后的
 * `\n` 不再添加空行) 处切分. 插件的溢出报告按流序透传. [finish] 刷出未完成的行并结束流; 之后的输入被忽略. 纯 JVM; 属主串行调用 (每个入口都同步).
 */
internal class TerminalOutputReader(
    private val sink: Sink,
    private val stripAnsi: Boolean = true,
    private val maxLineBytes: Int = MAX_LINE_BYTES,
) {

    init { require(maxLineBytes >= 4) }

    interface Sink {
        fun onLine(line: String)
        fun onOverflow(droppedBytes: Long)
        fun onEnd()
    }

    private enum class Escape { NONE, ESC, CSI, OSC, OSC_ESC, STRING, STRING_ESC, CHARSET }

    private val lock = Any()
    private val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private val chars: CharBuffer = CharBuffer.allocate(CHAR_BUFFER_SIZE)
    private val line = StringBuilder()
    private var pending = ByteArray(0)
    private var escape = Escape.NONE
    private var capped = false
    private var ended = false
    private var lineBytes = 0
    private var highSurrogate: Char? = null

    /** Lines handed to the sink so far. zh-CN: 至今交给接收方的行数. */
    var lineCount: Long = 0L
        private set

    val isEnded: Boolean
        get() = synchronized(lock) { ended }

    /** Consumes [count] bytes of [data] from [offset]. zh-CN: 消费 [data] 自 [offset] 起的 [count] 字节. */
    fun feed(data: ByteArray, offset: Int = 0, count: Int = data.size) {
        synchronized(lock) {
            if (ended || count <= 0) return
            val input = if (pending.isEmpty()) {
                ByteBuffer.wrap(data, offset, count)
            } else {
                ByteBuffer.allocate(pending.size + count).also { buffer ->
                    buffer.put(pending)
                    buffer.put(data, offset, count)
                    buffer.flip()
                }
            }
            decode(input, endOfInput = false)
            pending = if (input.hasRemaining()) ByteArray(input.remaining()).also { input.get(it) } else EMPTY
        }
    }

    /** The plugin dropped [droppedBytes] of this subscription; reported in stream order. zh-CN: 插件丢弃了本订阅的 [droppedBytes] 字节; 按流序报告. */
    fun noteOverflow(droppedBytes: Long) {
        synchronized(lock) {
            if (ended || droppedBytes <= 0L) return
            sink.onOverflow(droppedBytes)
        }
    }

    /** End of stream: decodes what is left, flushes the pending line, tells the sink. zh-CN: 流结束: 解码剩余字节, 刷出未完成的行, 通知接收方. */
    fun finish() {
        synchronized(lock) {
            if (ended) return
            ended = true
            decode(ByteBuffer.wrap(pending), endOfInput = true)
            pending = EMPTY
            if (line.isNotEmpty()) emitLine()
            sink.onEnd()
        }
    }

    /** An idle pipe can contain a prompt without a newline. Deliver that bounded fragment too. */
    fun flushPartial() = synchronized(lock) {
        if (!ended && line.isNotEmpty()) {
            emitLine()
            capped = true
        }
    }

    private fun decode(input: ByteBuffer, endOfInput: Boolean) {
        while (true) {
            chars.clear()
            val result = decoder.decode(input, chars, endOfInput)
            chars.flip()
            consume(chars)
            if (result.isUnderflow) break
        }
        if (endOfInput) {
            chars.clear()
            decoder.flush(chars)
            chars.flip()
            consume(chars)
            decoder.reset()
        }
    }

    private fun consume(buffer: CharBuffer) {
        while (buffer.hasRemaining()) {
            val c = buffer.get()
            if (stripAnsi && escape != Escape.NONE) {
                escape = step(escape, c)
                continue
            }
            when {
                c == '\n' -> {
                    if (capped) capped = false else emitLine()
                }
                !stripAnsi -> append(c)
                c == '\u001B' -> escape = Escape.ESC
                c == '\r' || c == '\u007F' || (c < ' ' && c != '\t') -> Unit
                else -> append(c)
            }
        }
    }

    private fun append(c: Char) {
        if (c.isHighSurrogate()) {
            highSurrogate = c
            return
        }
        val high = highSurrogate
        highSurrogate = null
        val bytes = when {
            high != null -> 4
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            else -> 3
        }
        if (lineBytes + bytes > maxLineBytes) emitLine()
        capped = false
        if (high != null) line.append(high)
        line.append(c)
        lineBytes += bytes
        if (lineBytes >= maxLineBytes) {
            emitLine()
            capped = true
        }
    }

    private fun emitLine() {
        val text = line.toString()
        line.setLength(0)
        lineBytes = 0
        lineCount++
        sink.onLine(text)
    }

    /** The next escape state after [c] (ECMA-48 shapes; a stray `ESC` restarts the sequence). zh-CN: [c] 之后的下一个转义状态 (ECMA-48 形态; 游离的 `ESC` 重新开始序列). */
    private fun step(state: Escape, c: Char): Escape = when (state) {
        Escape.NONE -> Escape.NONE
        Escape.ESC -> when (c) {
            '[' -> Escape.CSI
            ']' -> Escape.OSC
            'P', 'X', '^', '_' -> Escape.STRING
            '(', ')', '*', '+', '-', '.', '/', '#', ' ' -> Escape.CHARSET
            '\u001B' -> Escape.ESC
            else -> Escape.NONE
        }
        Escape.CSI -> when {
            c == '\u001B' -> Escape.ESC
            c in '@'..'~' -> Escape.NONE
            else -> Escape.CSI
        }
        Escape.OSC -> when (c) {
            '\u0007' -> Escape.NONE
            '\u001B' -> Escape.OSC_ESC
            else -> Escape.OSC
        }
        Escape.OSC_ESC -> if (c == '\\') Escape.NONE else step(Escape.ESC, c)
        Escape.STRING -> when (c) {
            '\u0007' -> Escape.NONE
            '\u001B' -> Escape.STRING_ESC
            else -> Escape.STRING
        }
        Escape.STRING_ESC -> if (c == '\\') Escape.NONE else step(Escape.ESC, c)
        Escape.CHARSET -> Escape.NONE
    }

    companion object {
        /** Appendix P4.2: a line is cut at 64 KiB. zh-CN: P4.2: 行在 64 KiB 处截断. */
        const val MAX_LINE_BYTES = 65_536
        private const val CHAR_BUFFER_SIZE = 8_192
        private val EMPTY = ByteArray(0)
    }
}
