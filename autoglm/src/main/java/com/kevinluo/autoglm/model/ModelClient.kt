package com.kevinluo.autoglm.model

import com.kevinluo.autoglm.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Configuration for the model client.
 *
 * Contains all settings required to connect to and communicate with the AutoGLM API.
 *
 * @property baseUrl API base URL for the model service
 * @property apiKey API key for authentication
 * @property modelName Name of the model to use for requests
 * @property maxTokens Maximum number of tokens in the response
 * @property temperature Sampling temperature for response generation
 * @property topP Top-p (nucleus) sampling parameter
 * @property frequencyPenalty Frequency penalty for response generation
 * @property timeoutSeconds Request timeout in seconds
 *
 */
data class ModelConfig(
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String = "EMPTY",
    val modelName: String = "autoglm-phone",
    val maxTokens: Int = 3000,
    val temperature: Float = 0.0f,
    val topP: Float = 0.85f,
    val frequencyPenalty: Float = 0.2f,
    val timeoutSeconds: Long = 120,
)

/**
 * Response from the model containing thinking and action.
 *
 * Represents the parsed response from the AutoGLM model, separating the thinking
 * process from the action to be executed.
 *
 * @property thinking The model's reasoning/thinking text
 * @property action The action command to execute (e.g., "do(...)" or "finish(...)")
 * @property rawContent The raw unparsed response content
 * @property timeToFirstToken Time in milliseconds until the first token was received
 * @property totalTime Total time in milliseconds for the complete response
 *
 */
data class ModelResponse(
    val thinking: String,
    val action: String,
    val rawContent: String,
    val timeToFirstToken: Long?,
    val totalTime: Long?,
)

/**
 * Sealed class representing different types of chat messages.
 *
 * Provides a type-safe way to represent system, user, and assistant messages
 * in a conversation with the model.
 *
 */
sealed class ChatMessage {
    /**
     * System message providing context or instructions to the model.
     *
     * @property content The system message content
     */
    data class System(val content: String) : ChatMessage()

    /**
     * User message containing text content.
     *
     * @property text The text content of the user message
     */
    data class User(val text: String) : ChatMessage()

    /**
     * Assistant message representing the model's response.
     *
     * @property content The assistant's response content
     */
    data class Assistant(val content: String) : ChatMessage()
}

/**
 * DTO for serializing messages to the API.
 *
 * Converts [ChatMessage] instances to the JSON format expected by the API.
 *
 * @property role The role of the message sender ("system", "user", or "assistant")
 * @property content The message content as a JSON element (string or array for multi-modal)
 *
 */
@Serializable
data class MessageDto(val role: String, val content: JsonElement) {
    companion object {
        /**
         * Creates a MessageDto from a ChatMessage.
         *
         * Handles conversion of different message types including multi-modal
         * user messages with images.
         *
         * @param message The ChatMessage to convert
         * @param imageBase64 Optional base64-encoded image to attach (only for User messages)
         * @return A MessageDto suitable for API serialization
         */
        fun fromChatMessage(message: ChatMessage, imageBase64: String? = null): MessageDto = when (message) {
            is ChatMessage.System -> {
                MessageDto(
                    role = "system",
                    content = JsonPrimitive(message.content),
                )
            }

            is ChatMessage.Assistant -> {
                MessageDto(
                    role = "assistant",
                    content = JsonPrimitive(message.content),
                )
            }

            is ChatMessage.User -> {
                if (imageBase64 != null) {
                    // Multi-modal content with text and image
                    // Detect image format from base64 header or default to PNG
                    val mimeType = detectImageMimeType(imageBase64)
                    val contentParts =
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", message.text)
                                },
                            )
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:$mimeType;base64,$imageBase64")
                                    }
                                },
                            )
                        }
                    MessageDto(role = "user", content = contentParts)
                } else {
                    // Text-only content
                    MessageDto(role = "user", content = JsonPrimitive(message.text))
                }
            }
        }

        /**
         * Detects the MIME type of an image from its base64-encoded data.
         *
         * Checks the first few bytes (magic numbers) to determine the format.
         *
         * @param base64Data The base64-encoded image data
         * @return The detected MIME type, defaults to "image/png" if unknown
         */
        private fun detectImageMimeType(base64Data: String): String {
            // Base64 magic number prefixes for common image formats
            // PNG: iVBORw0KGgo (starts with 0x89 0x50 0x4E 0x47)
            // JPEG: /9j/ (starts with 0xFF 0xD8 0xFF)
            // GIF: R0lGOD (starts with GIF87a or GIF89a)
            // WebP: UklGR (starts with RIFF....WEBP)
            return when {
                base64Data.startsWith("/9j/") -> "image/jpeg"
                base64Data.startsWith("iVBORw") -> "image/png"
                base64Data.startsWith("R0lGOD") -> "image/gif"
                base64Data.startsWith("UklGR") -> "image/webp"
                else -> "image/png" // Default to PNG
            }
        }
    }
}

/**
 * Content part for multi-modal messages.
 *
 * Represents a single part of a multi-modal message, which can be either
 * text or an image URL.
 *
 * @property type The type of content ("text" or "image_url")
 * @property text The text content, if type is "text"
 * @property imageUrl The image URL wrapper, if type is "image_url"
 *
 */
@Serializable
data class ContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ImageUrl? = null,
)

/**
 * Image URL wrapper for API.
 *
 * Wraps an image URL for inclusion in multi-modal messages.
 *
 * @property url The image URL (can be a data URL with base64 content)
 *
 */
@Serializable
data class ImageUrl(val url: String)

/**
 * Chat completion request body.
 *
 * Represents the request payload sent to the chat completions API endpoint.
 *
 * @property model The model name to use for completion
 * @property messages List of messages in the conversation
 * @property maxTokens Maximum number of tokens to generate
 * @property temperature Sampling temperature (0.0 to 2.0)
 * @property topP Top-p (nucleus) sampling parameter
 * @property frequencyPenalty Frequency penalty (-2.0 to 2.0)
 * @property stream Whether to stream the response
 *
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageDto>,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val temperature: Float,
    @SerialName("top_p")
    val topP: Float,
    @SerialName("frequency_penalty")
    val frequencyPenalty: Float,
    val stream: Boolean = true,
)

/**
 * Streaming response chunk.
 *
 * Represents a single chunk in a streaming chat completion response.
 *
 * @property choices List of completion choices in this chunk
 *
 */
@Serializable
data class ChatCompletionChunk(val choices: List<ChunkChoice> = emptyList())

/**
 * Choice in a streaming chunk.
 *
 * Represents a single choice within a streaming response chunk.
 *
 * @property delta The delta content for this choice
 *
 */
@Serializable
data class ChunkChoice(val delta: Delta = Delta())

/**
 * Delta content in streaming response.
 *
 * Contains the incremental content added in a streaming chunk.
 *
 * @property content The content string added in this delta, or null if none
 *
 */
@Serializable
data class Delta(val content: String? = null)

/**
 * Sealed class representing network errors.
 *
 * Provides type-safe error handling for various network failure scenarios.
 *
 */
sealed class NetworkError : Exception() {
    /**
     * Error indicating a connection failure.
     *
     * @property message Description of the connection failure
     */
    data class ConnectionFailed(override val message: String) : NetworkError()

    /**
     * Error indicating a request timeout.
     *
     * @property timeoutMs The timeout duration in milliseconds
     */
    data class Timeout(val timeoutMs: Long) : NetworkError() {
        override val message: String = "Request timed out after ${timeoutMs}ms"
    }

    /**
     * Error indicating a server-side error.
     *
     * @property statusCode The HTTP status code returned by the server
     * @property message Description of the server error
     */
    data class ServerError(val statusCode: Int, override val message: String) : NetworkError()

    /**
     * Error indicating a response parsing failure.
     *
     * @property rawResponse The raw response that failed to parse
     */
    data class ParseError(val rawResponse: String) : NetworkError() {
        override val message: String = "Failed to parse response: $rawResponse"
    }
}

/**
 * Result wrapper for model requests.
 *
 * Provides a type-safe way to represent success or failure of model requests.
 *
 */
sealed class ModelResult {
    /**
     * Successful model response.
     *
     * @property response The parsed model response
     */
    data class Success(val response: ModelResponse) : ModelResult()

    /**
     * Failed model request.
     *
     * @property error The network error that occurred
     */
    data class Error(val error: NetworkError) : ModelResult()
}

/**
 * Client for communicating with the AutoGLM API.
 *
 * Handles request/response serialization and streaming for chat completions.
 * Uses Server-Sent Events (SSE) for streaming responses.
 *
 * @property config Configuration for the model client
 *
 */
class ModelClient(private val config: ModelConfig) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    // Track current event source for cancellation
    @Volatile
    private var currentEventSource: EventSource? = null

    /**
     * Cancels the current ongoing request if any.
     *
     * Safe to call even if no request is in progress.
     */
    fun cancelCurrentRequest() {
        currentEventSource?.let { eventSource ->
            Logger.d(TAG, "Cancelling current request")
            eventSource.cancel()
            currentEventSource = null
        }
    }

    /**
     * Sends a request to the model and returns the response.
     *
     * Uses Server-Sent Events (SSE) for streaming responses. The response
     * is accumulated and parsed to extract thinking and action components.
     *
     * @param messages List of chat messages to send to the model
     * @param currentScreenshot Optional base64-encoded screenshot to attach to the last user message
     * @return ModelResult containing either the parsed response or an error
     */
    suspend fun request(messages: List<ChatMessage>, currentScreenshot: String? = null): ModelResult =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var timeToFirstToken: Long? = null

            val url = "${config.baseUrl}/chat/completions"
            Logger.logNetworkRequest("POST", url)

            try {
                // Convert messages to DTOs, attaching screenshot to the last user message
                val messageDtos =
                    messages.mapIndexed { index, message ->
                        val isLastUserMessage = index == messages.indexOfLast { it is ChatMessage.User }
                        if (isLastUserMessage && message is ChatMessage.User) {
                            MessageDto.fromChatMessage(message, currentScreenshot)
                        } else {
                            MessageDto.fromChatMessage(message)
                        }
                    }
                Logger.d(TAG, "Preparing request with ${messageDtos.size} messages")

                val requestBody =
                    ChatCompletionRequest(
                        model = config.modelName,
                        messages = messageDtos,
                        maxTokens = config.maxTokens,
                        temperature = config.temperature,
                        topP = config.topP,
                        frequencyPenalty = config.frequencyPenalty,
                        stream = true,
                    )

                val requestJson = json.encodeToString(requestBody)

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer ${config.apiKey}")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "text/event-stream")
                        .post(requestJson.toRequestBody("application/json".toMediaType()))
                        .build()

                val contentBuilder = StringBuilder()

                suspendCancellableCoroutine<ModelResult> { continuation ->
                    val eventSourceFactory = EventSources.createFactory(client)

                    val eventSourceListener =
                        object : EventSourceListener() {
                            override fun onOpen(eventSource: EventSource, response: Response) {
                                // Connection opened
                            }

                            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                                if (data == "[DONE]") {
                                    val totalTime = System.currentTimeMillis() - startTime
                                    val rawContent = contentBuilder.toString()
                                    val (thinking, action) = ModelResponseParser.parseThinkingAndAction(rawContent)

                                    Logger.logNetworkResponse(HTTP_STATUS_OK, totalTime)
                                    Logger.d(
                                        TAG,
                                        "Response complete: ${rawContent.length} chars, TTFT=${timeToFirstToken}ms",
                                    )

                                    val response =
                                        ModelResponse(
                                            thinking = thinking,
                                            action = action,
                                            rawContent = rawContent,
                                            timeToFirstToken = timeToFirstToken,
                                            totalTime = totalTime,
                                        )

                                    if (continuation.isActive) {
                                        continuation.resume(ModelResult.Success(response))
                                    }
                                    return
                                }

                                try {
                                    val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                                    val content =
                                        chunk.choices
                                            .firstOrNull()
                                            ?.delta
                                            ?.content

                                    if (content != null) {
                                        if (timeToFirstToken == null) {
                                            timeToFirstToken = System.currentTimeMillis() - startTime
                                            Logger.d(TAG, "First token received after ${timeToFirstToken}ms")
                                        }
                                        contentBuilder.append(content)
                                    }
                                } catch (e: Exception) {
                                    // Ignore parse errors for individual chunks
                                    Logger.v(TAG, "Chunk parse error (ignored): ${e.message}")
                                }
                            }

                            override fun onClosed(eventSource: EventSource) {
                                // If we haven't resumed yet, do so with what we have
                                if (continuation.isActive) {
                                    val totalTime = System.currentTimeMillis() - startTime
                                    val rawContent = contentBuilder.toString()

                                    if (rawContent.isNotEmpty()) {
                                        val (thinking, action) = ModelResponseParser.parseThinkingAndAction(rawContent)
                                        val response =
                                            ModelResponse(
                                                thinking = thinking,
                                                action = action,
                                                rawContent = rawContent,
                                                timeToFirstToken = timeToFirstToken,
                                                totalTime = totalTime,
                                            )
                                        continuation.resume(ModelResult.Success(response))
                                    } else {
                                        Logger.logNetworkError("Empty response received")
                                        continuation.resume(
                                            ModelResult.Error(NetworkError.ParseError("Empty response")),
                                        )
                                    }
                                }
                            }

                            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                                if (continuation.isActive) {
                                    val error =
                                        when {
                                            t is java.net.SocketTimeoutException -> {
                                                Logger.logNetworkError("Request timeout", t)
                                                NetworkError.Timeout(config.timeoutSeconds * MILLIS_PER_SECOND)
                                            }

                                            t is IOException -> {
                                                Logger.logNetworkError("Connection failed: ${t.message}", t)
                                                NetworkError.ConnectionFailed(t.message ?: "Connection failed")
                                            }

                                            response != null && !response.isSuccessful -> {
                                                Logger.logNetworkError(
                                                    "Server error ${response.code}: ${response.message}",
                                                )
                                                NetworkError.ServerError(
                                                    response.code,
                                                    response.message.ifEmpty { "Server error" },
                                                )
                                            }

                                            else -> {
                                                Logger.logNetworkError("Unknown error: ${t?.message}", t)
                                                NetworkError.ConnectionFailed(t?.message ?: "Unknown error")
                                            }
                                        }
                                    continuation.resume(ModelResult.Error(error))
                                }
                            }
                        }

                    val eventSource = eventSourceFactory.newEventSource(request, eventSourceListener)
                    currentEventSource = eventSource

                    continuation.invokeOnCancellation {
                        Logger.d(TAG, "Request cancelled via coroutine cancellation")
                        eventSource.cancel()
                        currentEventSource = null
                    }
                }
            } catch (e: Exception) {
                currentEventSource = null
                Logger.logNetworkError("Request failed: ${e.message}", e)
                when (e) {
                    is java.net.SocketTimeoutException -> {
                        ModelResult.Error(NetworkError.Timeout(config.timeoutSeconds * MILLIS_PER_SECOND))
                    }

                    is IOException -> {
                        ModelResult.Error(NetworkError.ConnectionFailed(e.message ?: "Connection failed"))
                    }

                    else -> {
                        ModelResult.Error(NetworkError.ConnectionFailed(e.message ?: "Unknown error"))
                    }
                }
            }
        }

    /**
     * Tests the connection to the model API.
     *
     * Sends a simple request to verify API connectivity and authentication.
     *
     * @return TestResult indicating success or failure with details
     */
    suspend fun testConnection(): TestResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val url = "${config.baseUrl}/chat/completions"

        Logger.logNetworkRequest("POST", url)
        Logger.d(TAG, "Testing connection to: $url")

        try {
            // Create a minimal test request
            val testMessage =
                MessageDto(
                    role = "user",
                    content = JsonPrimitive("Hi"),
                )

            val requestBody =
                ChatCompletionRequest(
                    model = config.modelName,
                    messages = listOf(testMessage),
                    maxTokens = TEST_MAX_TOKENS,
                    temperature = 0f,
                    topP = 1f,
                    frequencyPenalty = 0f,
                    stream = false,
                )

            val requestJson = json.encodeToString(requestBody)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(requestJson.toRequestBody("application/json".toMediaType()))
                    .build()

            val response = client.newCall(request).execute()
            val latency = System.currentTimeMillis() - startTime

            response.use { resp ->
                when {
                    resp.isSuccessful -> {
                        Logger.logNetworkResponse(resp.code, latency)
                        Logger.d(TAG, "Connection test successful, latency: ${latency}ms")
                        TestResult.Success(latency)
                    }

                    resp.code == HTTP_STATUS_UNAUTHORIZED -> {
                        Logger.logNetworkError("Connection test failed: Invalid API key (${resp.code})")
                        TestResult.AuthError("API 密钥无效")
                    }

                    resp.code == HTTP_STATUS_NOT_FOUND -> {
                        Logger.logNetworkError("Connection test failed: Model not found (${resp.code})")
                        TestResult.ModelNotFound("模型 '${config.modelName}' 不存在")
                    }

                    else -> {
                        val errorBody = resp.body?.string() ?: ""
                        Logger.logNetworkError("Connection test failed: ${resp.code} - $errorBody")
                        TestResult.ServerError(resp.code, resp.message.ifEmpty { "服务器错误" })
                    }
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Logger.logNetworkError("Connection test timeout", e)
            TestResult.Timeout("连接超时")
        } catch (e: java.net.UnknownHostException) {
            Logger.logNetworkError("Connection test failed: Unknown host", e)
            TestResult.ConnectionError("无法解析服务器地址")
        } catch (e: java.net.ConnectException) {
            Logger.logNetworkError("Connection test failed: Connection refused", e)
            TestResult.ConnectionError("无法连接到服务器")
        } catch (e: IOException) {
            Logger.logNetworkError("Connection test failed: ${e.message}", e)
            TestResult.ConnectionError(e.message ?: "网络错误")
        } catch (e: Exception) {
            Logger.logNetworkError("Connection test failed: ${e.message}", e)
            TestResult.ConnectionError(e.message ?: "未知错误")
        }
    }

    /**
     * Result of a connection test.
     *
     * Sealed class representing the various outcomes of a connection test.
     *
     */
    sealed class TestResult {
        /**
         * Successful connection test.
         *
         * @property latencyMs Round-trip latency in milliseconds
         */
        data class Success(val latencyMs: Long) : TestResult()

        /**
         * Authentication error (invalid API key).
         *
         * @property message Error message describing the auth failure
         */
        data class AuthError(val message: String) : TestResult()

        /**
         * Model not found error.
         *
         * @property message Error message describing the missing model
         */
        data class ModelNotFound(val message: String) : TestResult()

        /**
         * Server-side error.
         *
         * @property code HTTP status code
         * @property message Error message from the server
         */
        data class ServerError(val code: Int, val message: String) : TestResult()

        /**
         * Connection error (network issues).
         *
         * @property message Error message describing the connection failure
         */
        data class ConnectionError(val message: String) : TestResult()

        /**
         * Request timeout.
         *
         * @property message Error message describing the timeout
         */
        data class Timeout(val message: String) : TestResult()
    }

    companion object {
        private const val TAG = "ModelClient"
        private const val HTTP_STATUS_OK = 200
        private const val HTTP_STATUS_UNAUTHORIZED = 401
        private const val HTTP_STATUS_NOT_FOUND = 404
        private const val MILLIS_PER_SECOND = 1000L
        private const val TEST_MAX_TOKENS = 10
    }
}
