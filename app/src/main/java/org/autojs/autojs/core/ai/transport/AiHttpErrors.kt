package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import okhttp3.Response
import okio.Buffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

object AiHttpErrors {

    const val DEFAULT_MAX_ERROR_BYTES = 64 * 1024

    /**
     * Consumes and closes the error response body.
     */
    @JvmStatic
    @JvmOverloads
    fun fromResponse(
        response: Response,
        config: AiProviderConfig,
        maxErrorBytes: Int = DEFAULT_MAX_ERROR_BYTES,
    ): AiHttpException {
        require(maxErrorBytes > 0) { "maxErrorBytes must be positive" }
        val (rawPreview, truncated) = response.body?.use { body ->
            val source = body.source()
            val buffer = Buffer()
            var remaining = maxErrorBytes.toLong() + 1L
            while (remaining > 0L) {
                val read = source.read(buffer, min(remaining, READ_CHUNK_BYTES))
                if (read == -1L) break
                remaining -= read
            }
            val bytes = buffer.readByteArray()
            val wasTruncated = bytes.size > maxErrorBytes
            val visibleBytes = if (wasTruncated) bytes.copyOf(maxErrorBytes) else bytes
            String(visibleBytes, StandardCharsets.UTF_8) to wasTruncated
        } ?: ("" to false)

        val preview = redact(rawPreview, config.apiKey)
        val parsed = try {
            JsonParser.parseString(preview)
        } catch (_: RuntimeException) {
            null
        }
        val providerError = parsed
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.let(AiProtocolJson::errorOf)
        val fallbackMessage = parsed
            ?.takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.stringOrNull("message")
        val safeErrorMessage = sanitizeDetail(
            providerError?.message ?: fallbackMessage,
            config.apiKey,
        )
        val errorType = sanitizeDetail(providerError?.type, config.apiKey)
        val errorCode = sanitizeDetail(providerError?.code, config.apiKey)
        val requestId = listOf(
            "x-request-id",
            "request-id",
            "x-goog-request-id",
            "cf-ray",
        ).firstNotNullOfOrNull(response::header)
            ?.let { sanitizeDetail(it, config.apiKey) }
        val safeProviderId = sanitizeDetail(
            AiProviderIds.normalize(config.providerId),
            config.apiKey,
        ) ?: "<unknown>"

        val message = buildString {
            append("AI provider \"")
            append(safeProviderId)
            append("\" returned HTTP ")
            append(response.code)
            if (errorType != null || errorCode != null) {
                append(" (")
                append(listOfNotNull(errorType, errorCode).joinToString("/"))
                append(')')
            }
            if (safeErrorMessage != null) {
                append(": ")
                append(safeErrorMessage)
            }
        }
        return AiHttpException(
            providerId = safeProviderId,
            statusCode = response.code,
            errorType = errorType,
            errorCode = errorCode,
            requestId = requestId,
            responsePreview = preview,
            responseTruncated = truncated,
            message = message,
        )
    }

    private fun redact(value: String, apiKey: String?): String {
        val key = apiKey?.trim().orEmpty()
        if (value.isEmpty() || key.isEmpty()) return value
        val jsonEscaped = JsonPrimitive(key).toString().removeSurrounding("\"")
        return sequenceOf(key, jsonEscaped)
            .filter(String::isNotEmpty)
            .distinct()
            .sortedByDescending(String::length)
            .fold(value) { text, secret -> text.replace(secret, REDACTED) }
    }

    private fun sanitizeDetail(value: String?, apiKey: String?): String? {
        value ?: return null
        val redacted = redact(value, apiKey)
        val collapsed = buildString(redacted.length) {
            var previousWasControl = false
            redacted.forEach { character ->
                if (Character.isISOControl(character)) {
                    if (!previousWasControl) append(' ')
                    previousWasControl = true
                } else {
                    append(character)
                    previousWasControl = false
                }
            }
        }
        return collapsed
            .trim()
            .take(MAX_MESSAGE_DETAIL_CHARS)
            .takeIf(String::isNotEmpty)
    }

    private const val READ_CHUNK_BYTES = 8192L
    private const val MAX_MESSAGE_DETAIL_CHARS = 512
    private const val REDACTED = "<redacted>"

}
