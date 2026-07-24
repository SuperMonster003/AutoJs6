package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.ResponseBody
import okio.Buffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Reads successful JSON responses with a hard byte limit.
 *
 * The response body is always closed. Invalid JSON errors deliberately omit both the original
 * payload and the parser exception so a provider response cannot smuggle credentials into logs.
 */
object AiJsonResponseReader {

    const val DEFAULT_MAX_RESPONSE_BYTES = 8L * 1024L * 1024L

    @JvmStatic
    @JvmOverloads
    fun read(
        body: ResponseBody,
        maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    ): JsonElement {
        require(maxResponseBytes > 0L) { "maxResponseBytes must be positive" }
        require(maxResponseBytes < Long.MAX_VALUE) { "maxResponseBytes is too large" }

        val bytes = body.use {
            val source = it.source()
            val buffer = Buffer()
            var remaining = maxResponseBytes + 1L
            while (remaining > 0L) {
                val read = source.read(buffer, min(remaining, READ_CHUNK_BYTES))
                if (read == -1L) break
                remaining -= read
            }
            if (buffer.size > maxResponseBytes) {
                throw AiResponseLimitExceededException(maxResponseBytes)
            }
            buffer.readByteArray()
        }
        val json = String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF")
        return try {
            JsonParser.parseString(json)
        } catch (_: RuntimeException) {
            throw AiResponseProtocolException("body is not valid JSON")
        }
    }

    @JvmStatic
    @JvmOverloads
    fun parse(
        providerId: String,
        body: ResponseBody,
        maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    ): AiResponse {
        val raw = read(body, maxResponseBytes)
        return try {
            AiResponseParser.parse(providerId, raw)
        } catch (exception: RuntimeException) {
            if (exception is IllegalArgumentException &&
                exception.message?.startsWith("Unsupported AI provider") == true
            ) {
                throw exception
            }
            throw AiResponseProtocolException("body has an unsupported structure")
        }
    }

    private const val READ_CHUNK_BYTES = 8192L

}
