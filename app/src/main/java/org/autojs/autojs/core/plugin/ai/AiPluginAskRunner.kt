package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiUsage
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.provider.api.AiBackendProfileInfo
import org.autojs.plugin.ai.provider.api.AiGenerationOptions
import org.autojs.plugin.ai.provider.api.AiJsonSyntaxValidator
import org.autojs.plugin.ai.provider.api.AiMessageRole
import org.autojs.plugin.ai.provider.api.AiProviderBackendProfile
import org.autojs.plugin.ai.provider.api.AiProviderCapabilityId
import org.autojs.plugin.ai.provider.api.AiProviderLimits
import org.autojs.plugin.ai.provider.api.AiProviderMimeType
import java.io.Closeable

internal enum class AiPluginMessageRole(val protocolValue: Int) {
    SYSTEM(AiMessageRole.SYSTEM),
    USER(AiMessageRole.USER),
    ASSISTANT(AiMessageRole.ASSISTANT),
}

internal class AiPluginMessage(
    val role: AiPluginMessageRole,
    val content: String,
) {
    val contentBytes: Int = AiValidation.utf8Size(content)

    init {
        AiValidation.requireUtf8(
            content,
            1,
            AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES,
            "AI provider plugin message content",
        )
    }

    override fun toString(): String =
        "AiPluginMessage(role=$role, contentBytes=$contentBytes)"
}

internal data class AiPluginUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val durationMillis: Long?,
) {
    init {
        require(listOf(inputTokens, outputTokens, totalTokens).any { it != null }) {
            "Local AI plugin usage must contain a token count"
        }
        require(listOf(inputTokens, outputTokens, totalTokens, durationMillis).filterNotNull().all { it >= 0L }) {
            "Local AI plugin usage values must not be negative"
        }
    }
}

internal fun AiUsage.toPluginUsage() = AiPluginUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
    durationMillis = durationMillis,
)

internal class AiPluginAskRequest(
    val component: AiProviderComponent,
    val providerId: String,
    /** Null selects the provider's single installed target; ambiguity fails the request. */
    val modelId: String?,
    messages: List<AiPluginMessage>,
    val timeoutMillis: Long,
    val maximumOutputBytes: Long = DEFAULT_MAXIMUM_OUTPUT_BYTES,
    val maximumOutputTokens: Long? = null,
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val responseSchemaJson: String? = null,
    val backendProfile: String? = null,
) {
    val messages: List<AiPluginMessage> = messages.toList()
    val inputBytes: Long = this.messages.sumOf { it.contentBytes.toLong() }
    val responseSchemaBytes: Int? = responseSchemaJson?.let(AiValidation::utf8Size)
    val structuredJson: Boolean = responseSchemaJson != null

    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        modelId?.let { require(AiValidation.STABLE_ID.matches(it)) { "Invalid model ID" } }
        require(this.messages.size in 1..AiProviderLimits.MAX_MESSAGES) {
            "Invalid AI provider plugin message count"
        }
        require(this.messages.last().role == AiPluginMessageRole.USER) {
            "The final AI provider plugin message must have the user role"
        }
        require(inputBytes in 1L..AiProviderLimits.MAX_TOTAL_INPUT_BYTES) {
            "Invalid AI provider plugin input size"
        }
        require(timeoutMillis in AiProviderLimits.MIN_TIMEOUT_MILLIS..AiProviderLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid AI provider plugin timeout"
        }
        require(maximumOutputBytes in 1L..AiProviderLimits.MAX_OUTPUT_BYTES) {
            "Invalid AI provider plugin output limit"
        }
        maximumOutputTokens?.let {
            require(it in 1L..AiProviderLimits.MAX_OUTPUT_TOKENS) {
                "Invalid AI provider plugin token limit"
            }
        }
        temperature?.let {
            require(it.isFinite() && it >= 0.0) { "Invalid AI provider plugin temperature" }
        }
        topK?.let { require(it > 0) { "Invalid AI provider plugin topK" } }
        topP?.let {
            require(it.isFinite() && it in 0.0..1.0) { "Invalid AI provider plugin topP" }
        }
        backendProfile?.let {
            require(it in AiProviderBackendProfile.VALUES) {
                "Invalid AI provider plugin backend profile"
            }
        }
        responseSchemaJson?.let { schema ->
            AiValidation.requireUtf8(
                schema,
                1,
                AiProviderLimits.MAX_ONE_SCHEMA_BYTES.toInt(),
                "local AI response schema",
            )
            AiJsonSyntaxValidator.requireValid(schema)
            require(schema.trimStart().startsWith('{')) {
                "The local AI response schema must be a JSON object"
            }
        }
    }

    override fun toString(): String =
        "AiPluginAskRequest(component=$component, providerId=$providerId, modelId=$modelId, " +
            "messageCount=${messages.size}, messageRoles=${messages.map { it.role }}, " +
            "inputBytes=$inputBytes, timeoutMillis=$timeoutMillis, " +
            "maximumOutputBytes=$maximumOutputBytes, maximumOutputTokens=$maximumOutputTokens, " +
            "temperature=$temperature, topK=$topK, topP=$topP, " +
            "backendProfile=$backendProfile, " +
            "structuredJson=$structuredJson, responseSchemaBytes=$responseSchemaBytes)"

    internal companion object {
        const val DEFAULT_MAXIMUM_OUTPUT_BYTES = 64L * 1024L
    }
}

internal fun AiPluginAskRequest.toGenerationOptions(
    stream: Boolean,
    maximumOutputBytes: Long,
    timeoutMillis: Long,
    requiredCapabilityIds: List<String>,
    persistentSession: Boolean = false,
) = AiGenerationOptions(
    stream = stream,
    includeReasoning = false,
    structuredJson = structuredJson,
    reportUsage = true,
    maximumOutputBytes = maximumOutputBytes,
    maximumOutputTokens = maximumOutputTokens,
    maximumToolRounds = 0,
    timeoutMillis = timeoutMillis,
    responseMimeType = if (structuredJson) AiProviderMimeType.JSON else AiProviderMimeType.PLAIN,
    responseSchema = responseSchemaJson?.let { schema ->
        val bytes = schema.toByteArray(Charsets.UTF_8)
        AiPayloadReference(
            mimeType = AiProviderMimeType.JSON,
            declaredLengthBytes = bytes.size.toLong(),
            inlineBytes = bytes,
            charset = "utf-8",
        )
    },
    requiredCapabilityIds = buildList {
        addAll(requiredCapabilityIds)
        if (structuredJson && AiProviderCapabilityId.STRUCTURED_JSON !in this) {
            add(AiProviderCapabilityId.STRUCTURED_JSON)
        }
    },
    temperature = temperature,
    topK = topK,
    topP = topP,
    persistentSession = persistentSession,
    backendProfile = backendProfile,
)

internal enum class AiPluginAskFailure {
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

internal sealed class AiPluginAskOutcome {
    class Completed(
        val text: String,
        val usage: AiPluginUsage,
        /** The model that actually served the request, once resolved from the catalog. */
        val modelId: String? = null,
    ) : AiPluginAskOutcome() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, modelId=$modelId, usage=$usage)"
    }

    data class Failed(val failure: AiPluginAskFailure) : AiPluginAskOutcome()
}

internal interface AiPluginAskHandle : Closeable {
    fun cancel(): Boolean
}

internal fun interface AiPluginAskRunner {
    fun start(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginAskOutcome) -> Unit,
    ): AiPluginAskHandle
}

/**
 * Provider-neutral events for the local text-message plugin stream route.
 *
 * Binder/session identities and provider-native payloads deliberately remain below this boundary.
 */
internal sealed class AiPluginStreamEvent {
    object Opened : AiPluginStreamEvent()

    class Chunk(val text: String) : AiPluginStreamEvent() {
        init {
            AiValidation.requireUtf8(
                text,
                1,
                AiProviderLimits.MAX_CHUNK_TEXT_BYTES,
                "AI provider plugin stream chunk",
            )
        }

        override fun toString(): String =
            "Chunk(textBytes=${AiValidation.utf8Size(text)})"
    }

    data class Usage(val usage: AiPluginUsage) : AiPluginStreamEvent()

    class Completed(
        val text: String,
        val usage: AiPluginUsage,
    ) : AiPluginStreamEvent() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, usage=$usage)"
    }

    data class Failed(val failure: AiPluginAskFailure) : AiPluginStreamEvent()
}

internal fun interface AiPluginStreamRunner {
    fun startStream(
        request: AiPluginAskRequest,
        onEvent: (AiPluginStreamEvent) -> Unit,
    ): AiPluginAskHandle
}

/** Read-only model catalog listing against an explicitly selected plugin component. */
internal class AiPluginModelListRequest(
    val component: AiProviderComponent,
    val providerId: String,
    val timeoutMillis: Long,
) {
    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        require(timeoutMillis in AiProviderLimits.MIN_TIMEOUT_MILLIS..AiProviderLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid AI provider plugin timeout"
        }
    }
}

internal class AiPluginModelSummary(
    val modelId: String,
    val displayName: String,
    val capabilityIds: List<String>,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
    backendProfiles: List<AiBackendProfileInfo>,
) {
    val backendProfiles: List<AiBackendProfileInfo> = backendProfiles.toList()
}

internal sealed class AiPluginModelListOutcome {
    class Completed(models: List<AiPluginModelSummary>) : AiPluginModelListOutcome() {
        val models: List<AiPluginModelSummary> = models.toList()
    }

    data class Failed(val failure: AiPluginAskFailure) : AiPluginModelListOutcome()
}

internal fun interface AiPluginModelListRunner {
    fun listModels(
        request: AiPluginModelListRequest,
        onOutcome: (AiPluginModelListOutcome) -> Unit,
    ): AiPluginAskHandle
}

/** A planned local plugin Conversation whose model and sampling controls are fixed at creation. */
internal interface AiPluginSession : Closeable {
    val providerId: String
    val modelId: String
    val isClosed: Boolean

    fun startTurn(
        message: AiPluginMessage,
        streaming: Boolean,
        onOutcome: ((AiPluginAskOutcome) -> Unit)?,
        onStreamEvent: ((AiPluginStreamEvent) -> Unit)?,
    ): AiPluginAskHandle
}

internal sealed class AiPluginSessionOpenOutcome {
    data class Completed(val session: AiPluginSession) : AiPluginSessionOpenOutcome()
    data class Failed(val failure: AiPluginAskFailure) : AiPluginSessionOpenOutcome()
}

internal fun interface AiPluginSessionRunner {
    fun openSession(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginSessionOpenOutcome) -> Unit,
    ): AiPluginAskHandle
}
