package org.autojs.autojs.core.ai.transport

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink

/**
 * Builds credential-bearing requests without exposing credentials in URLs or JSON bodies.
 *
 * Redirect policy deliberately belongs to the immutable client supplied by the upper transport
 * layer. A credential-bearing client should disable both HTTP and HTTPS redirects.
 */
object AiRequestFactory {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private const val DEFAULT_ANTHROPIC_VERSION = "2023-06-01"
    private const val DEFAULT_ANTHROPIC_MAX_TOKENS = 1024

    @JvmStatic
    fun build(
        config: AiProviderConfig,
        messages: JsonArray,
        options: JsonObject = JsonObject(),
        stream: Boolean = false,
    ): Request {
        val normalizedProviderId = AiProviderIds.normalize(config.providerId)
        val protocol = try {
            AiProviderIds.protocolOf(normalizedProviderId)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Unsupported AI provider id")
        }
        val model = validateModel(config.model)
        val baseUrl = parseBaseUrl(config.baseUrl)
        val apiKey = validateApiKey(
            config.apiKey,
            mayBeMissing = normalizedProviderId == AiProviderIds.OPENAI_COMPATIBLE &&
                baseUrl.isStrictLoopback(),
        )

        val providerOptions = AiOptionsMapper.map(protocol, config.providerId, options)
        val body = when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> buildOpenAiBody(
                messages,
                providerOptions,
                config.providerId,
                model,
                stream,
            )
            AiProtocol.ANTHROPIC_MESSAGES -> buildAnthropicBody(messages, providerOptions, model, stream)
            AiProtocol.GEMINI_GENERATE_CONTENT -> buildGeminiBody(messages, providerOptions)
        }
        val url = when (protocol) {
            AiProtocol.OPENAI_COMPATIBLE -> appendEndpoint(baseUrl, listOf("chat", "completions"))
            AiProtocol.ANTHROPIC_MESSAGES -> appendEndpoint(baseUrl, listOf("messages"))
            AiProtocol.GEMINI_GENERATE_CONTENT -> geminiEndpoint(baseUrl, model, stream)
        }

        return Request.Builder()
            .url(url)
            .post(OneShotJsonRequestBody(body.toString().toByteArray(Charsets.UTF_8)))
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("Cache-Control", "no-store")
            .header("Content-Type", JSON.toString())
            .apply {
                when (protocol) {
                    AiProtocol.OPENAI_COMPATIBLE -> apiKey?.let {
                        header("Authorization", "Bearer $it")
                    }
                    AiProtocol.ANTHROPIC_MESSAGES -> {
                        header("x-api-key", requireNotNull(apiKey))
                        header("anthropic-version", DEFAULT_ANTHROPIC_VERSION)
                    }
                    AiProtocol.GEMINI_GENERATE_CONTENT ->
                        header("x-goog-api-key", requireNotNull(apiKey))
                }
            }
            .build()
    }

    /**
     * Prevents OkHttp response follow-ups (for example 503 + Retry-After: 0 or HTTP/2 421) from
     * replaying credential-bearing prompts even when connection retries and redirects are off.
     */
    private class OneShotJsonRequestBody(
        private val content: ByteArray,
    ) : RequestBody() {

        override fun contentType(): MediaType = JSON

        override fun contentLength(): Long = content.size.toLong()

        override fun writeTo(sink: BufferedSink) {
            sink.write(content)
        }

        override fun isOneShot(): Boolean = true
    }

    private fun buildOpenAiBody(
        messages: JsonArray,
        options: JsonObject,
        providerId: String,
        model: String,
        stream: Boolean,
    ): JsonObject {
        val system = options.remove("system")
        val normalizedMessages = normalizeOpenAiMessages(messages, providerId)
        val providerMessages = JsonArray()
        if (system != null) {
            providerMessages.add(openAiSystemMessage(system))
        }
        normalizedMessages.forEach { providerMessages.add(it) }
        return options.apply {
            addProperty("model", model)
            add("messages", providerMessages)
            addProperty("stream", stream)
        }
    }

    private fun openAiSystemMessage(system: JsonElement): JsonObject {
        val rawContent = system
            .takeIf(JsonElement::isJsonObject)
            ?.asJsonObject
            ?.get("content")
            ?: system
        val content = when {
            rawContent.isJsonNull -> JsonPrimitive("")
            rawContent.isJsonPrimitive && rawContent.asJsonPrimitive.isString -> rawContent.safeDeepCopy()
            rawContent.isJsonArray -> rawContent.deepCopy()
            else -> JsonPrimitive(rawContent.toString())
        }
        return JsonObject().apply {
            addProperty("role", "system")
            add("content", content)
        }
    }

    private fun normalizeOpenAiMessages(
        messages: JsonArray,
        providerId: String,
    ): JsonArray {
        val normalizedProviderId = AiProviderIds.normalize(providerId)
        return JsonArray().apply {
            messages.forEachIndexed { index, element ->
                val message = requireMessageObject(element, index).deepCopy()
                val role = message.stringOrNull("role")?.trim()?.lowercase()
                    ?: throw IllegalArgumentException("AI message at index $index must have a string role")
                message.addProperty("role", role)
                when (role) {
                    "assistant" -> normalizeOpenAiAssistantMessage(message)
                    "tool" -> normalizeOpenAiToolResultMessage(message, normalizedProviderId, index)
                }
                add(message)
            }
        }
    }

    private fun normalizeOpenAiAssistantMessage(message: JsonObject) {
        val publicCalls = message.arrayOrNull("toolCalls")
        val calls = if (publicCalls == null) emptyList() else AiProviderMessages.toolCallsOf(message)
        message.remove("toolCalls")
        if (!message.has("tool_calls") && calls.isNotEmpty()) {
            message.add("tool_calls", JsonArray().apply {
                calls.forEachIndexed { index, call ->
                    add(AiProviderMessages.openAiToolCall(call, index))
                }
            })
        }
        val reasoning = message.remove("reasoning")
        if (reasoning != null && !message.has("reasoning_content")) {
            message.add("reasoning_content", reasoning)
        }
        message.remove("raw")
    }

    private fun normalizeOpenAiToolResultMessage(
        message: JsonObject,
        providerId: String,
        index: Int,
    ) {
        val toolCallId = AiProviderMessages.toolResultId(message)
            ?: throw IllegalArgumentException("OpenAI-compatible tool message at index $index must include toolCallId")
        message.addProperty("tool_call_id", toolCallId)
        listOf("toolCallId", "toolUseId", "tool_use_id", "id").forEach(message::remove)
        val content = message.get("content")
        when {
            content == null || content.isJsonNull -> message.addProperty("content", "")
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> Unit
            content.isJsonArray && providerId != AiProviderIds.DEEPSEEK -> Unit
            else -> message.addProperty("content", content.toString())
        }
        message.remove("raw")
    }

    private fun buildAnthropicBody(
        messages: JsonArray,
        options: JsonObject,
        model: String,
        stream: Boolean,
    ): JsonObject {
        val body = options
        val providerMessages = JsonArray()
        val systemBlocks = JsonArray()

        body.remove("system")?.let { appendAnthropicSystemContent(systemBlocks, it) }
        var index = 0
        while (index < messages.size()) {
            val element = messages[index]
            val message = requireMessageObject(element, index)
            val role = message.stringOrNull("role")?.trim()?.lowercase()
                ?: throw IllegalArgumentException("AI message at index $index must have a string role")
            val content = message.get("content")
            when (role) {
                "system", "developer" -> appendAnthropicSystemContent(systemBlocks, content)
                "user", "assistant" -> providerMessages.add(JsonObject().apply {
                    addProperty("role", role)
                    add(
                        "content",
                        if (role == "assistant") {
                            anthropicAssistantContent(message)
                        } else {
                            normalizeAnthropicMessageContent(content)
                        },
                    )
                })
                "tool" -> {
                    val toolResults = JsonArray()
                    var toolIndex = index
                    while (toolIndex < messages.size()) {
                        val toolMessage = requireMessageObject(messages[toolIndex], toolIndex)
                        if (toolMessage.stringOrNull("role")?.trim()?.lowercase() != "tool") break
                        toolResults.add(anthropicToolResultBlock(toolMessage, toolIndex))
                        toolIndex += 1
                    }
                    providerMessages.add(JsonObject().apply {
                        addProperty("role", "user")
                        add("content", toolResults)
                    })
                    index = toolIndex
                    continue
                }
                else -> throw IllegalArgumentException("Unsupported Anthropic message role \"$role\" at index $index")
            }
            index += 1
        }

        body.addProperty("model", model)
        body.add("messages", providerMessages)
        body.addProperty("stream", stream)
        if (!body.has("max_tokens")) {
            body.addProperty("max_tokens", DEFAULT_ANTHROPIC_MAX_TOKENS)
        }
        if (systemBlocks.size() > 0) {
            body.add("system", systemBlocks)
        }
        return body
    }

    private fun anthropicAssistantContent(message: JsonObject): JsonElement {
        val calls = AiProviderMessages.toolCallsOf(message)
        if (calls.isEmpty()) {
            return normalizeAnthropicMessageContent(message.get("content"))
        }
        return JsonArray().apply {
            appendAnthropicContentBlocks(this, message.get("content"))
            calls.forEachIndexed { index, call ->
                add(AiProviderMessages.anthropicToolUse(call, index))
            }
        }
    }

    private fun normalizeAnthropicMessageContent(content: JsonElement?): JsonElement {
        return when {
            content == null || content.isJsonNull -> JsonPrimitive("")
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> content.safeDeepCopy()
            content.isJsonArray -> content.deepCopy()
            content.isJsonObject && content.asJsonObject.stringOrNull("type") != null -> {
                JsonArray().apply { add(content.deepCopy()) }
            }
            else -> JsonPrimitive(content.toString())
        }
    }

    private fun appendAnthropicContentBlocks(target: JsonArray, content: JsonElement?) {
        when {
            content == null || content.isJsonNull -> Unit
            content.isJsonArray -> content.asJsonArray.forEach { block ->
                when {
                    block.isJsonObject && block.asJsonObject.stringOrNull("type") != null -> {
                        target.add(block.deepCopy())
                    }
                    block.isJsonPrimitive && block.asJsonPrimitive.isString -> {
                        target.add(textBlock(block.asString))
                    }
                    else -> target.add(textBlock(block.toString()))
                }
            }
            content.isJsonObject && content.asJsonObject.stringOrNull("type") != null -> {
                target.add(content.deepCopy())
            }
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> {
                if (content.asString.isNotEmpty()) target.add(textBlock(content.asString))
            }
            else -> target.add(textBlock(content.toString()))
        }
    }

    private fun anthropicToolResultBlock(message: JsonObject, index: Int): JsonObject {
        val toolUseId = AiProviderMessages.toolResultId(message)
            ?: throw IllegalArgumentException("Anthropic tool message at index $index must include toolCallId")
        return JsonObject().apply {
            addProperty("type", "tool_result")
            addProperty("tool_use_id", toolUseId)
            add("content", anthropicToolResultContent(message.get("content")))
            val isError = message.booleanOrNull("isError") ?: message.booleanOrNull("is_error")
            if (isError != null) addProperty("is_error", isError)
        }
    }

    private fun anthropicToolResultContent(content: JsonElement?): JsonElement {
        return when {
            content == null || content.isJsonNull -> JsonPrimitive("")
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> content.safeDeepCopy()
            content.isJsonArray -> JsonArray().apply {
                content.asJsonArray.forEach { block ->
                    when {
                        block.isJsonObject && block.asJsonObject.stringOrNull("type") != null -> {
                            add(block.deepCopy())
                        }
                        block.isJsonPrimitive && block.asJsonPrimitive.isString -> {
                            add(textBlock(block.asString))
                        }
                        else -> add(textBlock(block.toString()))
                    }
                }
            }
            else -> JsonPrimitive(content.toString())
        }
    }

    private fun appendAnthropicSystemContent(target: JsonArray, content: JsonElement?) {
        when {
            content == null || content.isJsonNull -> Unit
            content.isJsonArray -> content.asJsonArray.forEach { block ->
                when {
                    block.isJsonPrimitive && block.asJsonPrimitive.isString -> target.add(textBlock(block.asString))
                    else -> target.add(block.safeDeepCopy())
                }
            }
            content.isJsonPrimitive && content.asJsonPrimitive.isString -> target.add(textBlock(content.asString))
            else -> target.add(content.safeDeepCopy())
        }
    }

    private fun buildGeminiBody(
        messages: JsonArray,
        options: JsonObject,
    ): JsonObject {
        val body = options
        val contents = JsonArray()
        val systemParts = JsonArray()
        val functionNamesById = mutableMapOf<String, String>()

        body.remove("systemInstruction")?.let { appendGeminiSystemContent(systemParts, it) }
        body.remove("system_instruction")?.let { appendGeminiSystemContent(systemParts, it) }
        body.remove("system")?.let { appendGeminiSystemContent(systemParts, it) }
        var index = 0
        while (index < messages.size()) {
            val element = messages[index]
            val message = requireMessageObject(element, index)
            val role = message.stringOrNull("role")?.trim()?.lowercase()
                ?: throw IllegalArgumentException("AI message at index $index must have a string role")
            when (role) {
                "system", "developer" -> appendGeminiMessageParts(systemParts, message)
                "user", "assistant", "model", "function" -> {
                    val parts = geminiMessageParts(
                        message,
                        includeToolCalls = role in setOf("assistant", "model"),
                    )
                    rememberGeminiFunctionNames(parts, functionNamesById)
                    contents.add(JsonObject().apply {
                        addProperty(
                            "role",
                            when (role) {
                                "assistant" -> "model"
                                "function" -> "user"
                                else -> role
                            },
                        )
                        add("parts", parts)
                    })
                }
                "tool" -> {
                    val responseParts = JsonArray()
                    var toolIndex = index
                    while (toolIndex < messages.size()) {
                        val toolMessage = requireMessageObject(messages[toolIndex], toolIndex)
                        if (toolMessage.stringOrNull("role")?.trim()?.lowercase() != "tool") break
                        responseParts.add(
                            geminiFunctionResponsePart(
                                toolMessage,
                                toolIndex,
                                functionNamesById,
                            ),
                        )
                        toolIndex += 1
                    }
                    contents.add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", responseParts)
                    })
                    index = toolIndex
                    continue
                }
                else -> throw IllegalArgumentException("Unsupported Gemini message role \"$role\" at index $index")
            }
            index += 1
        }

        body.add("contents", contents)
        if (systemParts.size() > 0) {
            body.add("systemInstruction", JsonObject().apply {
                add("parts", systemParts)
            })
        }
        return body
    }

    private fun geminiMessageParts(
        message: JsonObject,
        includeToolCalls: Boolean,
    ): JsonArray {
        val calls = if (includeToolCalls) AiProviderMessages.toolCallsOf(message) else emptyList()
        return JsonArray().apply {
            val content = message.get("content")
            val hasOnlyEmptyText = message.arrayOrNull("parts") == null &&
                content?.isJsonPrimitive == true &&
                content.asJsonPrimitive.isString &&
                content.asString.isEmpty()
            if (!hasOnlyEmptyText || calls.isEmpty()) {
                appendGeminiMessageParts(this, message)
            }
            calls.forEachIndexed { index, call ->
                add(AiProviderMessages.geminiFunctionCallPart(call, index))
            }
        }
    }

    private fun appendGeminiMessageParts(target: JsonArray, message: JsonObject) {
        val nativeParts = message.arrayOrNull("parts")
        if (nativeParts != null) {
            nativeParts.forEach { target.add(it.safeDeepCopy()) }
            return
        }
        if (message.has("content")) {
            appendGeminiParts(target, message.get("content"))
        }
    }

    private fun appendGeminiSystemContent(target: JsonArray, content: JsonElement) {
        if (content.isJsonObject) {
            val parts = content.asJsonObject.arrayOrNull("parts")
            if (parts != null) {
                parts.forEach { target.add(it.safeDeepCopy()) }
                return
            }
        }
        appendGeminiParts(target, content)
    }

    private fun appendGeminiParts(target: JsonArray, content: JsonElement?) {
        when {
            content == null || content.isJsonNull -> target.add(textPart(""))
            content.isJsonPrimitive -> target.add(textPart(content.asString))
            content.isJsonArray -> content.asJsonArray.forEach { block ->
                when {
                    block.isJsonPrimitive -> target.add(textPart(block.asString))
                    block.isJsonObject -> target.add(normalizeGeminiPart(block.asJsonObject))
                    else -> target.add(block.safeDeepCopy())
                }
            }
            content.isJsonObject -> target.add(normalizeGeminiPart(content.asJsonObject))
            else -> target.add(textPart(content.toString()))
        }
    }

    private fun normalizeGeminiPart(block: JsonObject): JsonElement {
        val type = block.stringOrNull("type")
        val text = block.stringOrNull("text")
        return when {
            text != null && type in setOf(null, "text", "input_text", "output_text") -> textPart(text)
            block.has("inline_data") -> JsonObject().apply {
                add("inline_data", block.get("inline_data").safeDeepCopy())
            }
            block.has("inlineData") -> JsonObject().apply {
                add("inlineData", block.get("inlineData").safeDeepCopy())
            }
            block.has("file_data") -> JsonObject().apply {
                add("file_data", block.get("file_data").safeDeepCopy())
            }
            block.has("fileData") -> JsonObject().apply {
                add("fileData", block.get("fileData").safeDeepCopy())
            }
            block.has("functionCall") || block.has("functionResponse") -> block.deepCopy()
            else -> block.deepCopy()
        }
    }

    private fun rememberGeminiFunctionNames(
        parts: JsonArray,
        functionNamesById: MutableMap<String, String>,
    ) {
        parts.forEach { partElement ->
            val functionCall = partElement
                .takeIf(JsonElement::isJsonObject)
                ?.asJsonObject
                ?.objectOrNull("functionCall")
                ?: return@forEach
            val id = functionCall.stringOrNull("id") ?: return@forEach
            val name = functionCall.stringOrNull("name") ?: return@forEach
            functionNamesById[id] = name
        }
    }

    private fun geminiFunctionResponsePart(
        message: JsonObject,
        index: Int,
        functionNamesById: Map<String, String>,
    ): JsonObject {
        val toolCallId = AiProviderMessages.toolResultId(message)
        val name = AiProviderMessages.toolResultName(message)
            ?: toolCallId?.let(functionNamesById::get)
            ?: throw IllegalArgumentException(
                "Gemini tool message at index $index must include a name " +
                    "or reference a preceding function call by toolCallId",
            )
        val content = message.get("content")
        val response = when {
            content == null || content.isJsonNull -> JsonObject()
            content.isJsonObject -> content.asJsonObject.deepCopy()
            else -> JsonObject().apply { add("result", content.safeDeepCopy()) }
        }
        return JsonObject().apply {
            add("functionResponse", JsonObject().apply {
                toolCallId?.let { addProperty("id", it) }
                addProperty("name", name)
                add("response", response)
            })
        }
    }

    private fun requireMessageObject(element: JsonElement, index: Int): JsonObject {
        require(element.isJsonObject) { "AI message at index $index must be a JSON object" }
        return element.asJsonObject
    }

    private fun textBlock(text: String): JsonObject {
        return JsonObject().apply {
            addProperty("type", "text")
            addProperty("text", text)
        }
    }

    private fun textPart(text: String): JsonObject {
        return JsonObject().apply { addProperty("text", text) }
    }

    private fun parseBaseUrl(rawBaseUrl: String): HttpUrl {
        val baseUrl = rawBaseUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("AI provider base URL must be a valid HTTP or HTTPS URL")
        require(baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) {
            "AI provider base URL must not contain user information"
        }
        return baseUrl
    }

    private fun appendEndpoint(baseUrl: HttpUrl, endpointSegments: List<String>): HttpUrl {
        val existingSegments = baseUrl.pathSegments.filter(String::isNotEmpty)
        if (existingSegments.takeLast(endpointSegments.size) == endpointSegments) {
            return baseUrl
        }
        return baseUrl.newBuilder().apply {
            endpointSegments.forEach(::addPathSegment)
        }.build()
    }

    private fun geminiEndpoint(baseUrl: HttpUrl, model: String, stream: Boolean): HttpUrl {
        val method = if (stream) "streamGenerateContent" else "generateContent"
        val normalizedModel = model.removePrefix("models/")
        val existingSegments = baseUrl.pathSegments.filter(String::isNotEmpty)
        val endpointSegment = "$normalizedModel:$method"
        val builder = baseUrl.newBuilder()

        val lastSegment = existingSegments.lastOrNull()
        if (lastSegment?.contains(":generateContent") == true || lastSegment?.contains(":streamGenerateContent") == true) {
            val actualLastIndex = baseUrl.pathSegments.indexOfLast(String::isNotEmpty)
            builder.setPathSegment(actualLastIndex, endpointSegment)
        } else {
            if (lastSegment != "models") {
                builder.addPathSegment("models")
            }
            builder.addPathSegment(endpointSegment)
        }
        if (stream) {
            builder.setQueryParameter("alt", "sse")
        } else if (baseUrl.queryParameter("alt") == "sse") {
            builder.removeAllQueryParameters("alt")
        }
        return builder.build()
    }

    private fun validateApiKey(rawApiKey: String?, mayBeMissing: Boolean): String? {
        val apiKey = rawApiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            require(mayBeMissing) { "AI provider API key must not be blank" }
            return null
        }
        require(apiKey.all { it.code in 0x21..0x7E }) {
            "AI provider API key must contain only visible ASCII characters"
        }
        return apiKey
    }

    private fun HttpUrl.isStrictLoopback(): Boolean {
        if (query != null || fragment != null || username.isNotEmpty() || password.isNotEmpty()) {
            return false
        }
        val normalizedHost = host.trimEnd('.').lowercase()
        if (
            normalizedHost == "localhost" ||
            normalizedHost == "::1" ||
            normalizedHost == "0:0:0:0:0:0:0:1"
        ) {
            return true
        }
        val parts = normalizedHost.split('.')
        return parts.size == 4 &&
            parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true } &&
            parts.first() == "127"
    }

    private fun validateModel(rawModel: String): String {
        val model = rawModel.trim()
        require(model.isNotEmpty()) { "AI provider model must not be blank" }
        require(model.none { it.code < 0x20 || it.code == 0x7F }) {
            "AI provider model contains forbidden control characters"
        }
        return model
    }

}
