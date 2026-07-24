package org.autojs.autojs.core.ai.transport

import okio.Buffer
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

data class SseLimits(
    val maxEventBytes: Long = DEFAULT_MAX_EVENT_BYTES,
    val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
) {

    init {
        require(maxEventBytes > 0L) { "SSE maxEventBytes must be positive" }
        require(maxTotalBytes > 0L) { "SSE maxTotalBytes must be positive" }
        require(maxEventBytes <= maxTotalBytes) {
            "SSE maxEventBytes must not exceed maxTotalBytes"
        }
    }

    companion object {
        const val DEFAULT_MAX_EVENT_BYTES = 1024L * 1024L
        const val DEFAULT_MAX_TOTAL_BYTES = 32L * 1024L * 1024L
    }

}

data class SseEvent(
    val id: String?,
    val event: String?,
    val data: String,
    val retryMillis: Long?,
    val isDone: Boolean,
)

class SseLimitExceededException(
    val limitName: String,
    val limitBytes: Long,
) : IOException("SSE $limitName limit of $limitBytes bytes exceeded")

/**
 * A bounded UTF-8 Server-Sent Events reader.
 *
 * It accepts LF, CRLF, and lone CR line endings; ignores comments and unknown fields; joins
 * multiple data fields with LF; dispatches the final unterminated event at EOF; and recognizes the
 * de-facto AI stream terminator `data: [DONE]`.
 */
class SseReader(
    private val source: BufferedSource,
    private val limits: SseLimits = SseLimits(),
) : Closeable {

    constructor(
        inputStream: InputStream,
        limits: SseLimits = SseLimits(),
    ) : this(inputStream.source().buffer(), limits)

    private var closed = false
    private var ended = false
    private var firstLine = true
    private var totalBytes = 0L
    private var eventBytes = 0L

    private var dataSeen = false
    private val data = StringBuilder()
    private var eventType: String? = null
    private var lastEventId: String? = null
    private var retryMillis: Long? = null

    fun readEvent(): SseEvent? {
        check(!closed) { "SSE reader is closed" }
        if (ended) return null

        while (true) {
            val rawLine = readLine()
            if (rawLine == null) {
                ended = true
                return dispatchEvent()
            }
            val line = if (firstLine) {
                firstLine = false
                rawLine.removePrefix("\uFEFF")
            } else {
                rawLine
            }

            if (line.isEmpty()) {
                val event = dispatchEvent()
                resetEvent()
                if (event != null) {
                    if (event.isDone) ended = true
                    return event
                }
                continue
            }
            if (line.startsWith(':')) {
                continue
            }

            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            val value = if (colon < 0) {
                ""
            } else {
                line.substring(colon + 1).removePrefix(" ")
            }
            when (field) {
                "data" -> {
                    dataSeen = true
                    data.append(value).append('\n')
                }
                "event" -> eventType = value
                "id" -> if ('\u0000' !in value) lastEventId = value
                "retry" -> value.toLongOrNull()
                    ?.takeIf { it >= 0L }
                    ?.let { retryMillis = it }
            }
        }
    }

    fun readAll(onEvent: (SseEvent) -> Unit) {
        while (true) {
            val event = readEvent() ?: return
            onEvent(event)
            if (event.isDone) return
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        ended = true
        source.close()
    }

    private fun readLine(): String? {
        if (!source.request(1L)) return null
        val line = Buffer()
        while (true) {
            if (!source.request(1L)) {
                return line.readUtf8()
            }
            val byte = source.readByte()
            accountByte()
            when (byte.toInt() and 0xFF) {
                LF -> return line.readUtf8()
                CR -> {
                    if (source.request(1L) && (source.buffer[0L].toInt() and 0xFF) == LF) {
                        source.skip(1L)
                        accountByte()
                    }
                    return line.readUtf8()
                }
                else -> line.writeByte(byte.toInt())
            }
        }
    }

    private fun accountByte() {
        totalBytes += 1L
        eventBytes += 1L
        if (totalBytes > limits.maxTotalBytes) {
            throw SseLimitExceededException("total stream", limits.maxTotalBytes)
        }
        if (eventBytes > limits.maxEventBytes) {
            throw SseLimitExceededException("single event", limits.maxEventBytes)
        }
    }

    private fun dispatchEvent(): SseEvent? {
        if (!dataSeen) return null
        if (data.isNotEmpty()) {
            data.setLength(data.length - 1)
        }
        val payload = data.toString()
        return SseEvent(
            id = lastEventId,
            event = eventType?.takeIf(String::isNotEmpty),
            data = payload,
            retryMillis = retryMillis,
            isDone = payload.trim() == DONE_SENTINEL,
        )
    }

    private fun resetEvent() {
        dataSeen = false
        data.setLength(0)
        eventType = null
        eventBytes = 0L
    }

    companion object {
        private const val LF = 0x0A
        private const val CR = 0x0D
        private const val DONE_SENTINEL = "[DONE]"
    }

}
