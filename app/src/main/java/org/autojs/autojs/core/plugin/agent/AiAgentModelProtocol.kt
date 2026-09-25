package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.autojs.core.plugin.ai.AiPluginToolResult
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiJsonSyntaxValidator
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import org.autojs.plugin.ai.provider.api.AiToolDefinition
import java.util.ArrayDeque

/** Only stable host-owned codes cross the Agent boundary; parser/provider text never does. */
internal class AiAgentModelException(val code: String) : IllegalArgumentException(code)

internal class AiAgentModelRequest(
    val requestId: String,
    val targetId: String,
    val messages: List<AiPluginMessage>,
    val responseSchemaJson: String?,
    val maximumOutputTokens: Long?,
    val temperature: Double?,
    val stream: Boolean,
    val timeoutMs: Long,
    val tools: List<AiToolDefinition> = emptyList(),
    val maximumToolRounds: Int = 0,
    val imageRefs: List<AiAgentModelImageRef> = emptyList(),
    val vision: Boolean = false,
) {
    val imageTokens: Long = imageRefs.sumOf { it.estimatedTokens }
    val inputBytes: Long = messages.sumOf { it.contentBytes.toLong() } +
        (responseSchemaJson?.toByteArray(Charsets.UTF_8)?.size ?: 0) +
        tools.sumOf { it.inputSchema.declaredLengthBytes + AiValidation.utf8Size(it.description) + AiValidation.utf8Size(it.name) }

    override fun toString() = "AiAgentModelRequest(messageCount=${messages.size}, inputBytes=$inputBytes)"
}

internal class AiAgentModelToolResults(val requestId: String, val round: Int, val results: List<AiPluginToolResult>,
    val imagesByCallId: Map<String, List<AiAgentModelImageRef>> = emptyMap()) {
    val imageRefs = imagesByCallId.values.flatten()
    val imageTokens = imageRefs.sumOf { it.estimatedTokens }
    val inputBytes = results.sumOf { AiValidation.utf8Size(it.output).toLong() }
    override fun toString() = "AiAgentModelToolResults(round=$round, count=${results.size}, inputBytes=$inputBytes)"
}

internal object AiAgentModelRequests {
    const val DEFAULT_TIMEOUT_MS = 30_000L
    private val REQUEST_ID = Regex("^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$")
    private val FIELDS = setOf(
        "requestId", "targetId", "messages", "structuredJson", "responseSchema",
        "maximumOutputTokens", "temperature", "stream", "timeoutMs", "tools", "maximumToolRounds", "imageRefs", "vision",
    )

    fun decode(json: String, maximumInputBytes: Int): AiAgentModelRequest = sanitized {
        val value = parseObject(json)
        require(value.keySet().all { it in FIELDS })
        val imageRefs = AiAgentModelImages.parse(value["imageRefs"], withMessageIndex = true)
        AiAgentModelImages.validate(imageRefs, imageRefs.size)
        val vision = value.boolean("vision", false) || imageRefs.isNotEmpty()
        val tools = value["tools"]?.takeUnless { it.isJsonNull }?.let { entries ->
            require(entries.isJsonArray && entries.asJsonArray.size() in 1..AiProviderLimits.MAX_TOOL_DEFINITIONS)
            entries.asJsonArray.map { entry ->
                require(entry.isJsonObject)
                val tool = entry.asJsonObject
                require(tool.keySet() == setOf("name", "description", "inputSchema"))
                require(tool["inputSchema"].isJsonObject)
                val schema = tool["inputSchema"].toString().toByteArray(Charsets.UTF_8)
                AiToolDefinition(tool.string("name"), tool.string("description"), AiPayloadReference(
                    AiProviderMimeType.JSON, schema.size.toLong(), inlineBytes = schema, charset = "utf-8"))
            }.also { require(it.map { tool -> tool.name }.distinct().size == it.size) }
        } ?: emptyList()
        val toolRounds = value.optionalLong("maximumToolRounds") ?: 0
        require(toolRounds in 0..AiAgentContract.MAX_MODEL_TOOL_ROUNDS)
        require((tools.isEmpty() && toolRounds == 0L) || (tools.isNotEmpty() && toolRounds > 0))
        val id = requestId(value)
        val target = value.string("targetId")
        require(AiValidation.TARGET_ID.matches(target))
        val array = value["messages"]?.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException()
        require(array.size() in 1..AiProviderLimits.MAX_MESSAGES)
        val messages = array.map { entry ->
            require(entry.isJsonObject)
            val message = entry.asJsonObject
            require(message.keySet() == setOf("role", "content"))
            val role = when (message.string("role")) {
                "system" -> AiPluginMessageRole.SYSTEM
                "user" -> AiPluginMessageRole.USER
                "assistant" -> AiPluginMessageRole.ASSISTANT
                else -> throw IllegalArgumentException()
            }
            AiPluginMessage(role, message.string("content"))
        }
        require(messages.last().role == AiPluginMessageRole.USER)
        require(imageRefs.all { ref -> ref.messageIndex in messages.indices &&
            messages[checkNotNull(ref.messageIndex)].role == AiPluginMessageRole.USER })
        val structured = value.boolean("structuredJson", false)
        val schema = value["responseSchema"]?.takeUnless { it.isJsonNull }?.let {
            require(structured && it.isJsonObject)
            it.toString().also { encoded ->
                if (encoded.toByteArray(Charsets.UTF_8).size > AiAgentContract.MAX_RESPONSE_SCHEMA_BYTES) {
                    throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
                }
            }
        } ?: if (structured) "{\"type\":\"object\"}" else null
        val outputTokens = value.optionalLong("maximumOutputTokens")?.also {
            require(it in 1..AiProviderLimits.MAX_OUTPUT_TOKENS)
        }
        val temperature = value["temperature"]?.takeUnless { it.isJsonNull }?.let {
            require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
            it.asDouble.also { number -> require(number.isFinite() && number >= 0.0) }
        }
        val timeout = value.optionalLong("timeoutMs") ?: DEFAULT_TIMEOUT_MS
        require(timeout in AiProviderLimits.MIN_TIMEOUT_MILLIS..AiProviderLimits.MAX_TIMEOUT_MILLIS)
        AiAgentModelRequest(id, target, messages, schema, outputTokens, temperature,
            value.boolean("stream", false), timeout, tools, toolRounds.toInt(), imageRefs, vision).also {
            if (it.inputBytes > maximumInputBytes) throw AiAgentModelException(AiAgentContract.ERROR_QUOTA_EXCEEDED)
        }
    }

    fun toolResults(json: String): AiAgentModelToolResults = sanitized {
        if (AiValidation.utf8Size(json) > AiAgentContract.MAX_MODEL_TOOL_RESULTS_BYTES) {
            throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
        }
        val value = parseObject(json)
        require(value.keySet() == setOf("requestId", "round", "results"))
        val round = requireNotNull(value.optionalLong("round"))
        require(round in 1..AiAgentContract.MAX_MODEL_TOOL_ROUNDS)
        val entries = value["results"].also { require(it.isJsonArray) }.asJsonArray
        require(entries.size() in 1..AiProviderLimits.MAX_OUTSTANDING_TOOL_CALLS)
        val imagesByCallId = linkedMapOf<String, List<AiAgentModelImageRef>>()
        val results = entries.map { entry ->
            require(entry.isJsonObject)
            val result = entry.asJsonObject
            require(result.keySet().all { it in setOf("callId", "output", "isError", "imageRefs") })
            val output = result.string("output")
            if (AiValidation.utf8Size(output) > AiAgentContract.MAX_MODEL_TOOL_RESULT_BYTES) {
                throw AiAgentModelException(AiAgentContract.ERROR_LIMIT_EXCEEDED)
            }
            val callId = result.string("callId")
            imagesByCallId[callId] = AiAgentModelImages.parse(result["imageRefs"], withMessageIndex = false)
            AiPluginToolResult(callId, output, result.boolean("isError", false)).also { it.toProtocol() }
        }
        require(results.map { it.callId }.distinct().size == results.size)
        AiAgentModelImages.validate(imagesByCallId.values.flatten(), imagesByCallId.values.sumOf { it.size })
        AiAgentModelToolResults(requestId(value), round.toInt(), results, imagesByCallId)
    }

    fun catalog(json: String): Pair<String, Long> = sanitized {
        val value = parseObject(json)
        require(value.keySet().all { it in setOf("requestId", "timeoutMs") })
        val timeout = value.optionalLong("timeoutMs") ?: DEFAULT_TIMEOUT_MS
        require(timeout in AiProviderLimits.MIN_TIMEOUT_MILLIS..AiProviderLimits.MAX_TIMEOUT_MILLIS)
        requestId(value) to timeout
    }

    fun reference(json: String): String = sanitized {
        val value = parseObject(json)
        require(value.keySet().all { it in setOf("requestId", "reason") })
        requestId(value)
    }

    fun recoverId(json: String): String = runCatching { requestId(parseObject(json)) }.getOrDefault("")

    private fun parseObject(json: String): JsonObject {
        AiJsonSyntaxValidator.requireValid(json)
        val parsed = JsonParser.parseString(json)
        require(parsed.isJsonObject)
        return parsed.asJsonObject
    }

    private fun requestId(value: JsonObject) = value.string("requestId").also { require(REQUEST_ID.matches(it)) }

    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: throw IllegalArgumentException()
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }

    private fun JsonObject.boolean(key: String, default: Boolean): Boolean {
        val value = get(key) ?: return default
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }

    private fun JsonObject.optionalLong(key: String): Long? {
        val value = get(key)?.takeUnless { it.isJsonNull } ?: return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        return value.asBigDecimal.longValueExact()
    }

    private inline fun <T> sanitized(block: () -> T): T = try {
        block()
    } catch (failure: AiAgentModelException) {
        throw failure
    } catch (_: Exception) {
        throw AiAgentModelException(AiAgentContract.ERROR_INVALID_REQUEST)
    }
}

/** Reservations bound concurrent admission; unavailable usage is charged at 0.4 token/byte. */
internal class AiAgentModelQuota(
    private val callsPerMinute: Int,
    private val maximumTokens: Long,
    private val clock: () -> Long,
) {
    private val calls = ArrayDeque<Long>()
    private val reservations = mutableMapOf<String, Long>()
    private var consumed = 0L

    init { require(callsPerMinute > 0 && maximumTokens > 0) }

    @Synchronized
    fun reserve(id: String, inputBytes: Long, maximumOutputTokens: Long?, additionalInputTokens: Long = 0): Boolean {
        require(additionalInputTokens >= 0)
        val now = clock()
        while (calls.isNotEmpty() && now - calls.first >= 60_000) calls.removeFirst()
        val estimate = saturatingAdd(saturatingAdd(estimateTokens(inputBytes), additionalInputTokens), maximumOutputTokens
            ?: estimateTokens(AiAgentContract.MAX_MODEL_OUTPUT_BYTES.toLong()))
        if (id in reservations || calls.size >= callsPerMinute || consumed >= maximumTokens) return false
        val remaining = maximumTokens - consumed
        val held = reservations.values.fold(0L, ::saturatingAdd)
        if (held >= remaining || estimate > remaining - held) return false
        reservations[id] = estimate
        calls.addLast(now)
        return true
    }

    /** Returns false if reported provider usage exceeded the remaining link budget. */
    @Synchronized
    fun settle(id: String, usage: AiPluginUsage?, inputBytes: Long, outputBytes: Long): Boolean {
        return settleCumulative(id, usage, inputBytes, outputBytes, 0).first
    }

    /** Native tool continuations report cumulative usage; replace the previous charge, never add it twice. */
    @Synchronized
    fun settleCumulative(id: String, usage: AiPluginUsage?, inputBytes: Long, outputBytes: Long,
                         previouslyCharged: Long, releaseReservation: Boolean = true, estimatedImageTokens: Long = 0): Pair<Boolean, Long> {
        require(estimatedImageTokens >= 0)
        if (releaseReservation && reservations.remove(id) == null) return true to previouslyCharged
        val charged = usage?.totalTokens ?: if (usage?.inputTokens != null && usage.outputTokens != null) {
            saturatingAdd(usage.inputTokens, usage.outputTokens)
        } else {
            saturatingAdd(usage?.inputTokens ?: saturatingAdd(estimateTokens(inputBytes), estimatedImageTokens),
                usage?.outputTokens ?: estimateTokens(outputBytes))
        }
        require(previouslyCharged in 0..consumed)
        consumed = saturatingAdd(consumed - previouslyCharged, charged)
        return (consumed <= maximumTokens) to charged
    }

    @Synchronized fun consumedTokens(): Long = consumed

    companion object {
        fun estimateTokens(bytes: Long): Long = (bytes / 5) * 2 + ((bytes % 5) * 2 + 4) / 5
        private fun saturatingAdd(a: Long, b: Long): Long = if (b > Long.MAX_VALUE - a) Long.MAX_VALUE else a + b
    }
}

/** Emits started first, monotonically numbered events, and exactly one terminal. */
internal class AiAgentModelEventSequence {
    private var sequence = 0L
    private var terminal = false

    @Synchronized
    fun next(type: String): Long? {
        if (terminal || (sequence == 0L && type != AiAgentContract.MODEL_EVENT_STARTED) ||
            (sequence != 0L && type == AiAgentContract.MODEL_EVENT_STARTED)) return null
        if (type in setOf(AiAgentContract.MODEL_EVENT_COMPLETED, AiAgentContract.MODEL_EVENT_FAILED,
                AiAgentContract.MODEL_EVENT_CANCELLED)) terminal = true
        return ++sequence
    }
}

internal fun AiPluginAskFailure.agentModelError(): String = when (this) {
    AiPluginAskFailure.CANCELLED -> AiAgentContract.ERROR_CANCELLED
    AiPluginAskFailure.TIMED_OUT -> AiAgentContract.ERROR_MODEL_TIMEOUT
    AiPluginAskFailure.TARGET_CAPABILITY_MISMATCH -> AiAgentContract.ERROR_TARGET_UNSUPPORTED
    AiPluginAskFailure.TARGET_NOT_FOUND, AiPluginAskFailure.TARGET_NOT_CONFIGURED,
    AiPluginAskFailure.TARGET_UNAVAILABLE, AiPluginAskFailure.BACKEND_UNAVAILABLE,
    AiPluginAskFailure.AI_PROVIDER_UNAVAILABLE, AiPluginAskFailure.AI_PROVIDER_DISABLED,
    AiPluginAskFailure.AI_PROVIDER_REJECTED -> AiAgentContract.ERROR_TARGET_UNAVAILABLE
    AiPluginAskFailure.BUSY, AiPluginAskFailure.FUSED -> AiAgentContract.ERROR_RATE_LIMITED
    AiPluginAskFailure.INVALID_REQUEST -> AiAgentContract.ERROR_INVALID_REQUEST
    else -> AiAgentContract.ERROR_MODEL_FAILED
}
