package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginMessage
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.plugin.ai.agent.api.AiAgentContract
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiJsonSyntaxValidator
import org.autojs.plugin.ai.provider.api.AiProviderLimits
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
) {
    val inputBytes: Long = messages.sumOf { it.contentBytes.toLong() } +
        (responseSchemaJson?.toByteArray(Charsets.UTF_8)?.size ?: 0)

    override fun toString() = "AiAgentModelRequest(messageCount=${messages.size}, inputBytes=$inputBytes)"
}

internal object AiAgentModelRequests {
    const val DEFAULT_TIMEOUT_MS = 30_000L
    private val REQUEST_ID = Regex("^[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}$")
    private val FIELDS = setOf(
        "requestId", "targetId", "messages", "structuredJson", "responseSchema",
        "maximumOutputTokens", "temperature", "stream", "timeoutMs", "tools", "imageRefs",
    )

    fun decode(json: String, maximumInputBytes: Int): AiAgentModelRequest = sanitized {
        val value = parseObject(json)
        require(value.keySet().all { it in FIELDS })
        if (listOf("tools", "imageRefs").any { value.has(it) && !value[it].isJsonNull }) {
            throw AiAgentModelException(AiAgentContract.ERROR_TARGET_UNSUPPORTED)
        }
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
            value.boolean("stream", false), timeout).also {
            if (it.inputBytes > maximumInputBytes) throw AiAgentModelException(AiAgentContract.ERROR_QUOTA_EXCEEDED)
        }
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
    fun reserve(id: String, inputBytes: Long, maximumOutputTokens: Long?): Boolean {
        val now = clock()
        while (calls.isNotEmpty() && now - calls.first >= 60_000) calls.removeFirst()
        val estimate = saturatingAdd(estimateTokens(inputBytes), maximumOutputTokens
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
        if (reservations.remove(id) == null) return true
        val charged = usage?.totalTokens ?: if (usage?.inputTokens != null && usage.outputTokens != null) {
            saturatingAdd(usage.inputTokens, usage.outputTokens)
        } else {
            saturatingAdd(usage?.inputTokens ?: estimateTokens(inputBytes),
                usage?.outputTokens ?: estimateTokens(outputBytes))
        }
        consumed = saturatingAdd(consumed, charged)
        return consumed <= maximumTokens
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
