package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import java.io.IOException

/**
 * Immutable provider configuration used by the transport layer.
 *
 * This intentionally isn't a data class: a generated data-class [toString] would expose [apiKey].
 */
class AiProviderConfig(
    val providerId: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String?,
) {

    override fun toString(): String {
        val secret = apiKey?.takeIf(String::isNotBlank)
        fun String.redacted(): String {
            return secret?.let { replace(it, "<redacted>") } ?: this
        }
        return "AiProviderConfig(" +
            "providerId=${providerId.redacted()}, " +
            "baseUrl=${baseUrl.redacted()}, " +
            "model=${model.redacted()}, " +
            "apiKey=<redacted>)"
    }

}

enum class AiProtocol {
    OPENAI_COMPATIBLE,
    ANTHROPIC_MESSAGES,
    GEMINI_GENERATE_CONTENT,
}

object AiProviderIds {

    const val OPENAI = "openai"
    const val DEEPSEEK = "deepseek"
    const val OPENROUTER = "openrouter"
    const val OPENAI_COMPATIBLE = "openai-compatible"
    const val ANTHROPIC = "anthropic"
    const val GEMINI = "gemini"

    @JvmStatic
    fun normalize(providerId: String): String {
        return providerId.trim().lowercase()
    }

    @JvmStatic
    fun protocolOf(providerId: String): AiProtocol {
        return when (val normalized = normalize(providerId)) {
            OPENAI,
            DEEPSEEK,
            OPENROUTER,
            OPENAI_COMPATIBLE,
            -> AiProtocol.OPENAI_COMPATIBLE

            ANTHROPIC -> AiProtocol.ANTHROPIC_MESSAGES
            GEMINI -> AiProtocol.GEMINI_GENERATE_CONTENT
            else -> throw IllegalArgumentException("Unsupported AI provider id \"$normalized\"")
        }
    }

}

data class AiToolCall(
    val index: Int? = null,
    val id: String? = null,
    val name: String? = null,
    /**
     * Complete JSON for a non-streaming response, or an incremental JSON fragment for a stream.
     */
    val arguments: String? = null,
    val argumentsAreDelta: Boolean = false,
    val raw: JsonElement = JsonNull.INSTANCE,
)

data class AiUsage(
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val totalTokens: Long? = null,
    val reasoningTokens: Long? = null,
    val cachedInputTokens: Long? = null,
    val raw: JsonObject? = null,
)

data class AiProviderError(
    val type: String? = null,
    val code: String? = null,
    val message: String? = null,
)

/**
 * Provider-neutral result for a completed JSON response.
 */
data class AiResponse(
    val text: String = "",
    val reasoning: String = "",
    val toolCalls: List<AiToolCall> = emptyList(),
    val usage: AiUsage? = null,
    /**
     * Provider-native assistant message that can be appended to a subsequent request for the
     * same provider without reconstructing tool calls or opaque reasoning signatures.
     */
    val message: JsonObject? = null,
    val raw: JsonElement = JsonNull.INSTANCE,
    val finishReason: String? = null,
    val error: AiProviderError? = null,
)

/**
 * Provider-neutral incremental result for one SSE event.
 */
data class AiStreamChunk(
    val text: String = "",
    val reasoning: String = "",
    val toolCalls: List<AiToolCall> = emptyList(),
    val usage: AiUsage? = null,
    val raw: JsonElement = JsonNull.INSTANCE,
    val finishReason: String? = null,
    val done: Boolean = false,
    val error: AiProviderError? = null,
)

class AiStreamProtocolException(
    providerId: String,
    detail: String,
) : IOException("Invalid $providerId AI stream: $detail")

class AiResponseLimitExceededException(
    val limitBytes: Long,
) : IOException("AI JSON response limit of $limitBytes bytes exceeded")

class AiResponseProtocolException(
    detail: String,
) : IOException("Invalid AI JSON response: $detail")

class AiHttpException internal constructor(
    val providerId: String,
    val statusCode: Int,
    val errorType: String?,
    val errorCode: String?,
    val requestId: String?,
    val responsePreview: String,
    val responseTruncated: Boolean,
    message: String,
) : IOException(message)
