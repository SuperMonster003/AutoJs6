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
import org.autojs.plugin.ai.provider.api.AiToolDefinition
import java.io.Closeable

internal enum class AiPluginMessageRole(val protocolValue: Int) {
    SYSTEM(AiMessageRole.SYSTEM),
    USER(AiMessageRole.USER),
    ASSISTANT(AiMessageRole.ASSISTANT),
}

internal class AiPluginMessage(
    val role: AiPluginMessageRole,
    val content: String,
    images: List<AiPluginImage> = emptyList(),
) {
    val images = images.toList()
    val contentBytes: Int = AiValidation.utf8Size(content)

    init {
        require(this.images.isEmpty() || role == AiPluginMessageRole.USER)
        AiValidation.requireUtf8(
            content,
            1,
            AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES,
            "AI provider plugin message content",
        )
    }

    override fun toString(): String =
        "AiPluginMessage(role=$role, contentBytes=$contentBytes, imageCount=${images.size})"
}

internal data class AiPluginUsage(
    val inputTokens: Long?,
    val outputTokens: Long?,
    val totalTokens: Long?,
    val durationMillis: Long?,
    val reasoningTokens: Long? = null,
    val cachedInputTokens: Long? = null,
) {
    init {
        require(listOf(inputTokens, outputTokens, totalTokens, reasoningTokens, cachedInputTokens).any { it != null }) {
            "AI plugin usage must contain a token count"
        }
        require(
            listOf(
                inputTokens,
                outputTokens,
                totalTokens,
                reasoningTokens,
                cachedInputTokens,
                durationMillis,
            ).filterNotNull().all { it >= 0L },
        ) {
            "AI plugin usage values must not be negative"
        }
    }
}

internal fun AiUsage.toPluginUsage() = AiPluginUsage(
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
    reasoningTokens = reasoningTokens,
    cachedInputTokens = cachedInputTokens,
    durationMillis = durationMillis,
)

internal class AiPluginAskRequest(
    val component: AiProviderComponent,
    val providerId: String,
    /** Null selects the provider-declared default target. */
    val targetId: String?,
    messages: List<AiPluginMessage>,
    val timeoutMillis: Long,
    val maximumOutputBytes: Long = DEFAULT_MAXIMUM_OUTPUT_BYTES,
    val maximumOutputTokens: Long? = null,
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val responseSchemaJson: String? = null,
    val backendProfile: String? = null,
    val includeReasoning: Boolean = false,
    tools: List<AiToolDefinition> = emptyList(),
    val maximumToolRounds: Int = 0,
    val vision: Boolean = false,
) {
    val tools = tools.toList()
    val messages: List<AiPluginMessage> = messages.toList()
    val inputBytes: Long = this.messages.sumOf { it.contentBytes.toLong() } +
        this.tools.sumOf { it.inputSchema.declaredLengthBytes + AiValidation.utf8Size(it.description) + AiValidation.utf8Size(it.name) }
    val responseSchemaBytes: Int? = responseSchemaJson?.let(AiValidation::utf8Size)
    val structuredJson: Boolean = responseSchemaJson != null

    val images = this.messages.flatMap { it.images }
    init {
        require(vision || images.isEmpty())
        org.autojs.plugin.ai.provider.api.AiProviderImagePolicy.validate(
            images.mapIndexed { index, image -> image.contentPart(index) }, org.autojs.plugin.ai.provider.api.AiVisionCapabilities())
        require(this.tools.size <= AiProviderLimits.MAX_TOOL_DEFINITIONS)
        require(this.tools.map { it.name }.distinct().size == this.tools.size)
        require(maximumToolRounds in 0..AiProviderLimits.MAX_TOOL_ROUNDS)
        require((this.tools.isEmpty() && maximumToolRounds == 0) || (this.tools.isNotEmpty() && maximumToolRounds > 0))
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        targetId?.let { require(AiValidation.TARGET_ID.matches(it)) { "Invalid target ID" } }
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
                "AI plugin response schema",
            )
            AiJsonSyntaxValidator.requireValid(schema)
            require(schema.trimStart().startsWith('{')) {
                "The AI plugin response schema must be a JSON object"
            }
        }
    }

    override fun toString(): String =
        "AiPluginAskRequest(component=$component, providerId=$providerId, targetId=$targetId, " +
            "messageCount=${messages.size}, messageRoles=${messages.map { it.role }}, " +
            "inputBytes=$inputBytes, timeoutMillis=$timeoutMillis, " +
            "maximumOutputBytes=$maximumOutputBytes, maximumOutputTokens=$maximumOutputTokens, " +
            "temperature=$temperature, topK=$topK, topP=$topP, " +
            "backendProfile=$backendProfile, includeReasoning=$includeReasoning, " +
            "structuredJson=$structuredJson, responseSchemaBytes=$responseSchemaBytes, " +
            "toolCount=${tools.size}, maximumToolRounds=$maximumToolRounds)"

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
    includeReasoning = includeReasoning,
    structuredJson = structuredJson,
    reportUsage = true,
    maximumOutputBytes = maximumOutputBytes,
    maximumOutputTokens = maximumOutputTokens,
    maximumToolRounds = maximumToolRounds,
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
        if (includeReasoning && AiProviderCapabilityId.REASONING !in this) {
            add(AiProviderCapabilityId.REASONING)
        }
        if (tools.isNotEmpty() && AiProviderCapabilityId.TOOLS !in this) add(AiProviderCapabilityId.TOOLS)
        if (vision && AiProviderCapabilityId.VISION !in this) add(AiProviderCapabilityId.VISION)
    },
    temperature = temperature,
    topK = topK,
    topP = topP,
    persistentSession = persistentSession,
    backendProfile = backendProfile,
)

internal enum class AiPluginAskFailure {
    INVALID_REQUEST,
    AI_PROVIDER_UNAVAILABLE,
    AI_PROVIDER_DISABLED,
    AI_PROVIDER_REJECTED,
    BUSY,
    FUSED,
    CANCELLED,
    TIMED_OUT,
    BINDER_DIED,
    TARGET_NOT_FOUND,
    TARGET_NOT_CONFIGURED,
    TARGET_UNAVAILABLE,
    TARGET_CAPABILITY_MISMATCH,
    BACKEND_UNAVAILABLE,
    SESSION_CLOSED,
    SESSION_REJECTED,
    PROVIDER_FAILED,
    INTERNAL_FAILURE,
}

internal sealed class AiPluginAskOutcome {
    class Completed(
        val text: String,
        val reasoning: String,
        val usage: AiPluginUsage,
        val finishReason: Int,
        val target: AiPluginTargetSummary,
    ) : AiPluginAskOutcome() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, " +
                "reasoningBytes=${AiValidation.utf8Size(reasoning)}, targetId=${target.targetId}, " +
                "finishReason=$finishReason, usage=$usage)"
    }

    data class Failed(
        val failure: AiPluginAskFailure,
        val providerReason: AiPluginProviderFailureReason? = null,
    ) : AiPluginAskOutcome()
}

internal interface AiPluginAskHandle : Closeable {
    fun cancel(): Boolean
    /** Returns promptly; results are validated and submitted on the session control lane. */
    fun submitToolResults(results: List<AiPluginToolResult>): Boolean = false
}

internal fun interface AiPluginAskRunner {
    fun start(
        request: AiPluginAskRequest,
        onOutcome: (AiPluginAskOutcome) -> Unit,
    ): AiPluginAskHandle
}

/**
 * Provider-neutral events for the text-message plugin stream route.
 *
 * Binder/session identities and provider-native payloads deliberately remain below this boundary.
 */
internal sealed class AiPluginStreamEvent {
    data class Opened(val target: AiPluginTargetSummary) : AiPluginStreamEvent()

    class Chunk(val text: String, val reasoning: String = "") : AiPluginStreamEvent() {
        init {
            require(text.isNotEmpty() || reasoning.isNotEmpty()) { "AI plugin stream chunk is empty" }
            require(
                AiValidation.utf8Size(text).toLong() + AiValidation.utf8Size(reasoning).toLong() <=
                    AiProviderLimits.MAX_CHUNK_TEXT_BYTES,
            ) { "AI plugin stream chunk exceeds the protocol limit" }
        }

        override fun toString(): String =
            "Chunk(textBytes=${AiValidation.utf8Size(text)}, " +
                "reasoningBytes=${AiValidation.utf8Size(reasoning)})"
    }

    data class Usage(val usage: AiPluginUsage) : AiPluginStreamEvent()

    class ToolCalls(calls: List<AiPluginToolCall>) : AiPluginStreamEvent() {
        val calls = calls.toList()
        override fun toString() = "ToolCalls(callCount=${calls.size})"
    }

    class Completed(
        val text: String,
        val reasoning: String,
        val usage: AiPluginUsage,
        val finishReason: Int,
        val target: AiPluginTargetSummary,
    ) : AiPluginStreamEvent() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, " +
                "reasoningBytes=${AiValidation.utf8Size(reasoning)}, targetId=${target.targetId}, " +
                "finishReason=$finishReason, usage=$usage)"
    }

    data class Failed(
        val failure: AiPluginAskFailure,
        val providerReason: AiPluginProviderFailureReason? = null,
    ) : AiPluginStreamEvent()
}

internal fun interface AiPluginStreamRunner {
    fun startStream(
        request: AiPluginAskRequest,
        onEvent: (AiPluginStreamEvent) -> Unit,
    ): AiPluginAskHandle
}

/** Read-only target catalog listing against an explicitly selected plugin component. */
internal class AiPluginCatalogRequest(
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

internal class AiPluginTargetSummary(
    val targetId: String,
    val providerId: String,
    val profileId: String?,
    val modelId: String,
    val displayName: String,
    capabilityIds: List<String>,
    val locality: Int,
    val credentialMode: Int,
    val availability: String,
    val configured: Boolean,
    val isDefault: Boolean,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
    supportedControls: List<String>,
    declaredHttpsOrigins: List<String>,
    backendProfiles: List<AiBackendProfileInfo>,
) {
    val capabilityIds: List<String> = capabilityIds.toList()
    val supportedControls: List<String> = supportedControls.toList()
    val declaredHttpsOrigins: List<String> = declaredHttpsOrigins.toList()
    val backendProfiles: List<AiBackendProfileInfo> = backendProfiles.toList()
}

internal class AiPluginCatalog(
    val component: AiProviderComponent,
    val providerId: String,
    val generation: String,
    val defaultTargetId: String?,
    targets: List<AiPluginTargetSummary>,
) {
    val targets: List<AiPluginTargetSummary> = targets.toList()

    init {
        require(this.targets.map { it.targetId }.distinct().size == this.targets.size) {
            "AI plugin catalog contains duplicate target IDs"
        }
        val declaredDefault = this.targets.singleOrNull { it.isDefault }?.targetId
        require(defaultTargetId == declaredDefault) {
            "AI plugin catalog default target is inconsistent"
        }
    }
}

internal sealed class AiPluginCatalogOutcome {
    data class Completed(val catalog: AiPluginCatalog) : AiPluginCatalogOutcome()
    data class Failed(val failure: AiPluginAskFailure) : AiPluginCatalogOutcome()
}

internal fun interface AiPluginCatalogRunner {
    fun listCatalog(
        request: AiPluginCatalogRequest,
        onOutcome: (AiPluginCatalogOutcome) -> Unit,
    ): AiPluginAskHandle
}

/** A planned plugin Conversation whose exact target and sampling controls are fixed at creation. */
internal interface AiPluginSession : Closeable {
    val providerId: String
    val target: AiPluginTargetSummary
    val backendProfile: String?
    val modelId: String
        get() = target.modelId
    val targetId: String
        get() = target.targetId
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
