package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.AiGenerationOptions
import org.autojs.plugin.ondeviceai.api.AiMessageRole
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import java.io.Closeable

internal enum class OnDeviceAiPluginMessageRole(val protocolValue: Int) {
    SYSTEM(AiMessageRole.SYSTEM),
    USER(AiMessageRole.USER),
    ASSISTANT(AiMessageRole.ASSISTANT),
}

internal class OnDeviceAiPluginMessage(
    val role: OnDeviceAiPluginMessageRole,
    val content: String,
) {
    val contentBytes: Int = AiValidation.utf8Size(content)

    init {
        AiValidation.requireUtf8(
            content,
            1,
            AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES,
            "on-device AI plugin message content",
        )
    }

    override fun toString(): String =
        "OnDeviceAiPluginMessage(role=$role, contentBytes=$contentBytes)"
}

internal data class OnDeviceAiPluginUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val durationMillis: Long?,
) {
    init {
        require(listOf(inputTokens, outputTokens, totalTokens).any { it != null }) {
            "On-device AI plugin usage must contain a token count"
        }
        require(listOf(inputTokens, outputTokens, totalTokens, durationMillis).filterNotNull().all { it >= 0L }) {
            "On-device AI plugin usage values must not be negative"
        }
    }
}

internal fun AiUsage.toPluginUsage() = OnDeviceAiPluginUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
    durationMillis = durationMillis,
)

internal class OnDeviceAiPluginAskRequest(
    val component: OnDeviceAiProviderComponent,
    val providerId: String,
    /** Null selects the provider's single installed model; ambiguity fails the request. */
    val modelId: String?,
    messages: List<OnDeviceAiPluginMessage>,
    val timeoutMillis: Long,
    val maximumOutputBytes: Long = DEFAULT_MAXIMUM_OUTPUT_BYTES,
    val maximumOutputTokens: Long? = null,
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
) {
    val messages: List<OnDeviceAiPluginMessage> = messages.toList()
    val inputBytes: Long = this.messages.sumOf { it.contentBytes.toLong() }

    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        modelId?.let { require(AiValidation.STABLE_ID.matches(it)) { "Invalid model ID" } }
        require(this.messages.size in 1..OnDeviceAiLimits.MAX_MESSAGES) {
            "Invalid on-device AI plugin message count"
        }
        require(this.messages.last().role == OnDeviceAiPluginMessageRole.USER) {
            "The final on-device AI plugin message must have the user role"
        }
        require(inputBytes in 1L..OnDeviceAiLimits.MAX_TOTAL_INPUT_BYTES) {
            "Invalid on-device AI plugin input size"
        }
        require(timeoutMillis in OnDeviceAiLimits.MIN_TIMEOUT_MILLIS..OnDeviceAiLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid on-device AI plugin timeout"
        }
        require(maximumOutputBytes in 1L..OnDeviceAiLimits.MAX_OUTPUT_BYTES) {
            "Invalid on-device AI plugin output limit"
        }
        maximumOutputTokens?.let {
            require(it in 1L..OnDeviceAiLimits.MAX_OUTPUT_TOKENS) {
                "Invalid on-device AI plugin token limit"
            }
        }
        temperature?.let {
            require(it.isFinite() && it >= 0.0) { "Invalid on-device AI plugin temperature" }
        }
        topK?.let { require(it > 0) { "Invalid on-device AI plugin topK" } }
        topP?.let {
            require(it.isFinite() && it in 0.0..1.0) { "Invalid on-device AI plugin topP" }
        }
    }

    override fun toString(): String =
        "OnDeviceAiPluginAskRequest(component=$component, providerId=$providerId, modelId=$modelId, " +
            "messageCount=${messages.size}, messageRoles=${messages.map { it.role }}, " +
            "inputBytes=$inputBytes, timeoutMillis=$timeoutMillis, " +
            "maximumOutputBytes=$maximumOutputBytes, maximumOutputTokens=$maximumOutputTokens, " +
            "temperature=$temperature, topK=$topK, topP=$topP)"

    internal companion object {
        const val DEFAULT_MAXIMUM_OUTPUT_BYTES = 64L * 1024L
    }
}

internal fun OnDeviceAiPluginAskRequest.toGenerationOptions(
    stream: Boolean,
    maximumOutputBytes: Long,
    timeoutMillis: Long,
    requiredCapabilityIds: List<String>,
    persistentSession: Boolean = false,
) = AiGenerationOptions(
    stream = stream,
    includeReasoning = false,
    structuredJson = false,
    reportUsage = true,
    maximumOutputBytes = maximumOutputBytes,
    maximumOutputTokens = maximumOutputTokens,
    maximumToolRounds = 0,
    timeoutMillis = timeoutMillis,
    responseMimeType = OnDeviceAiMimeType.PLAIN,
    requiredCapabilityIds = requiredCapabilityIds,
    temperature = temperature,
    topK = topK,
    topP = topP,
    persistentSession = persistentSession,
)

internal enum class OnDeviceAiPluginAskFailure {
    INVALID_REQUEST,
    PROVIDER_NOT_FOUND,
    PROVIDER_DISABLED,
    PROVIDER_REJECTED,
    BUSY,
    FUSED,
    CANCELLED,
    TIMED_OUT,
    BINDER_DIED,
    MODEL_NOT_FOUND,
    MODEL_AMBIGUOUS,
    MODEL_CAPABILITY_MISMATCH,
    SESSION_CLOSED,
    SESSION_REJECTED,
    PROVIDER_FAILED,
    INTERNAL_FAILURE,
}

internal sealed class OnDeviceAiPluginAskOutcome {
    class Completed(
        val text: String,
        val usage: OnDeviceAiPluginUsage,
        /** The model that actually served the request, once resolved from the catalog. */
        val modelId: String? = null,
    ) : OnDeviceAiPluginAskOutcome() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, modelId=$modelId, usage=$usage)"
    }

    data class Failed(val failure: OnDeviceAiPluginAskFailure) : OnDeviceAiPluginAskOutcome()
}

internal interface OnDeviceAiPluginAskHandle : Closeable {
    fun cancel(): Boolean
}

internal fun interface OnDeviceAiPluginAskRunner {
    fun start(
        request: OnDeviceAiPluginAskRequest,
        onOutcome: (OnDeviceAiPluginAskOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle
}

/**
 * Provider-neutral events for the local text-message plugin stream route.
 *
 * Binder/session identities and provider-native payloads deliberately remain below this boundary.
 */
internal sealed class OnDeviceAiPluginStreamEvent {
    object Opened : OnDeviceAiPluginStreamEvent()

    class Chunk(val text: String) : OnDeviceAiPluginStreamEvent() {
        init {
            AiValidation.requireUtf8(
                text,
                1,
                OnDeviceAiLimits.MAX_CHUNK_TEXT_BYTES,
                "on-device AI plugin stream chunk",
            )
        }

        override fun toString(): String =
            "Chunk(textBytes=${AiValidation.utf8Size(text)})"
    }

    data class Usage(val usage: OnDeviceAiPluginUsage) : OnDeviceAiPluginStreamEvent()

    class Completed(
        val text: String,
        val usage: OnDeviceAiPluginUsage,
    ) : OnDeviceAiPluginStreamEvent() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, usage=$usage)"
    }

    data class Failed(val failure: OnDeviceAiPluginAskFailure) : OnDeviceAiPluginStreamEvent()
}

internal fun interface OnDeviceAiPluginStreamRunner {
    fun startStream(
        request: OnDeviceAiPluginAskRequest,
        onEvent: (OnDeviceAiPluginStreamEvent) -> Unit,
    ): OnDeviceAiPluginAskHandle
}

/** Read-only model catalog listing against an explicitly selected plugin component. */
internal class OnDeviceAiPluginModelListRequest(
    val component: OnDeviceAiProviderComponent,
    val providerId: String,
    val timeoutMillis: Long,
) {
    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        require(timeoutMillis in OnDeviceAiLimits.MIN_TIMEOUT_MILLIS..OnDeviceAiLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid on-device AI plugin timeout"
        }
    }
}

internal class OnDeviceAiPluginModelSummary(
    val modelId: String,
    val displayName: String,
    val capabilityIds: List<String>,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
)

internal sealed class OnDeviceAiPluginModelListOutcome {
    class Completed(models: List<OnDeviceAiPluginModelSummary>) : OnDeviceAiPluginModelListOutcome() {
        val models: List<OnDeviceAiPluginModelSummary> = models.toList()
    }

    data class Failed(val failure: OnDeviceAiPluginAskFailure) : OnDeviceAiPluginModelListOutcome()
}

internal fun interface OnDeviceAiPluginModelListRunner {
    fun listModels(
        request: OnDeviceAiPluginModelListRequest,
        onOutcome: (OnDeviceAiPluginModelListOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle
}

/** A planned local plugin Conversation whose model and sampling controls are fixed at creation. */
internal interface OnDeviceAiPluginSession : Closeable {
    val providerId: String
    val modelId: String
    val isClosed: Boolean

    fun startTurn(
        message: OnDeviceAiPluginMessage,
        streaming: Boolean,
        onOutcome: ((OnDeviceAiPluginAskOutcome) -> Unit)?,
        onStreamEvent: ((OnDeviceAiPluginStreamEvent) -> Unit)?,
    ): OnDeviceAiPluginAskHandle
}

internal sealed class OnDeviceAiPluginSessionOpenOutcome {
    data class Completed(val session: OnDeviceAiPluginSession) : OnDeviceAiPluginSessionOpenOutcome()
    data class Failed(val failure: OnDeviceAiPluginAskFailure) : OnDeviceAiPluginSessionOpenOutcome()
}

internal fun interface OnDeviceAiPluginSessionRunner {
    fun openSession(
        request: OnDeviceAiPluginAskRequest,
        onOutcome: (OnDeviceAiPluginSessionOpenOutcome) -> Unit,
    ): OnDeviceAiPluginAskHandle
}
