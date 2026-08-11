package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ai.text.api.AiTextLimits
import java.io.Closeable

internal class AiTextPluginAskRequest(
    val component: AiTextProviderComponent,
    val providerId: String,
    val modelId: String,
    val prompt: String,
    val timeoutMillis: Long,
    val maximumOutputBytes: Long = DEFAULT_MAXIMUM_OUTPUT_BYTES,
) {
    init {
        require(AiValidation.STABLE_ID.matches(providerId)) { "Invalid provider ID" }
        require(AiValidation.STABLE_ID.matches(modelId)) { "Invalid model ID" }
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
    PROVIDER_REJECTED,
    BUSY,
    FUSED,
    CANCELLED,
    TIMED_OUT,
    BINDER_DIED,
    MODEL_NOT_FOUND,
    MODEL_CAPABILITY_MISMATCH,
    SESSION_REJECTED,
    PROVIDER_FAILED,
    INTERNAL_FAILURE,
}

internal sealed class AiTextPluginAskOutcome {
    class Completed(val text: String) : AiTextPluginAskOutcome() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)})"
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
