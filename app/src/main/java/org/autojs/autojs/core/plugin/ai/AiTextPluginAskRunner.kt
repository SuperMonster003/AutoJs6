package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.text.api.AiTextLimits
import java.io.Closeable

internal class AiTextPluginAskRequest(
    val component: AiTextProviderComponent,
    val providerId: String,
    /** Null selects the provider's single installed model; ambiguity fails the request. */
    val modelId: String?,
    val prompt: String,
    val timeoutMillis: Long,
    val maximumOutputBytes: Long = DEFAULT_MAXIMUM_OUTPUT_BYTES,
) {
    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        modelId?.let { require(AiValidation.STABLE_ID.matches(it)) { "Invalid model ID" } }
        AiValidation.requireUtf8(
            prompt,
            1,
            AiCommonLimits.MAX_INLINE_PAYLOAD_BYTES,
            "AI text plugin prompt",
        )
        require(timeoutMillis in AiTextLimits.MIN_TIMEOUT_MILLIS..AiTextLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid AI text plugin timeout"
        }
        require(maximumOutputBytes in 1L..AiTextLimits.MAX_OUTPUT_BYTES) {
            "Invalid AI text plugin output limit"
        }
    }

    override fun toString(): String =
        "AiTextPluginAskRequest(component=$component, providerId=$providerId, modelId=$modelId, " +
            "promptBytes=${AiValidation.utf8Size(prompt)}, timeoutMillis=$timeoutMillis, " +
            "maximumOutputBytes=$maximumOutputBytes)"

    internal companion object {
        const val DEFAULT_MAXIMUM_OUTPUT_BYTES = 64L * 1024L
    }
}

internal enum class AiTextPluginAskFailure {
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
    SESSION_REJECTED,
    PROVIDER_FAILED,
    INTERNAL_FAILURE,
}

internal sealed class AiTextPluginAskOutcome {
    class Completed(
        val text: String,
        /** The model that actually served the request, once resolved from the catalog. */
        val modelId: String? = null,
    ) : AiTextPluginAskOutcome() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, modelId=$modelId)"
    }

    data class Failed(val failure: AiTextPluginAskFailure) : AiTextPluginAskOutcome()
}

internal interface AiTextPluginAskHandle : Closeable {
    fun cancel(): Boolean
}

internal fun interface AiTextPluginAskRunner {
    fun start(
        request: AiTextPluginAskRequest,
        onOutcome: (AiTextPluginAskOutcome) -> Unit,
    ): AiTextPluginAskHandle
}

/**
 * Provider-neutral events for the narrow prompt-only plugin stream route.
 *
 * Binder/session identities and provider-native payloads deliberately remain below this boundary.
 */
internal sealed class AiTextPluginStreamEvent {
    object Opened : AiTextPluginStreamEvent()

    class Chunk(val text: String) : AiTextPluginStreamEvent() {
        init {
            AiValidation.requireUtf8(
                text,
                1,
                AiTextLimits.MAX_CHUNK_TEXT_BYTES,
                "AI text plugin stream chunk",
            )
        }

        override fun toString(): String =
            "Chunk(textBytes=${AiValidation.utf8Size(text)})"
    }

    class Completed(val text: String) : AiTextPluginStreamEvent() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)})"
    }

    data class Failed(val failure: AiTextPluginAskFailure) : AiTextPluginStreamEvent()
}

internal fun interface AiTextPluginStreamRunner {
    fun startStream(
        request: AiTextPluginAskRequest,
        onEvent: (AiTextPluginStreamEvent) -> Unit,
    ): AiTextPluginAskHandle
}

/** Read-only model catalog listing against an explicitly selected plugin component. */
internal class AiTextPluginModelListRequest(
    val component: AiTextProviderComponent,
    val providerId: String,
    val timeoutMillis: Long,
) {
    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        require(timeoutMillis in AiTextLimits.MIN_TIMEOUT_MILLIS..AiTextLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid AI text plugin timeout"
        }
    }
}

internal class AiTextPluginModelSummary(
    val modelId: String,
    val displayName: String,
    val capabilityIds: List<String>,
    val maximumContextBytes: Long,
    val maximumOutputBytes: Long,
)

internal sealed class AiTextPluginModelListOutcome {
    class Completed(models: List<AiTextPluginModelSummary>) : AiTextPluginModelListOutcome() {
        val models: List<AiTextPluginModelSummary> = models.toList()
    }

    data class Failed(val failure: AiTextPluginAskFailure) : AiTextPluginModelListOutcome()
}

internal fun interface AiTextPluginModelListRunner {
    fun listModels(
        request: AiTextPluginModelListRequest,
        onOutcome: (AiTextPluginModelListOutcome) -> Unit,
    ): AiTextPluginAskHandle
}
