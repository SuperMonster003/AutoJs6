package org.autojs.autojs.core.plugin.ai

import org.autojs.plugin.ai.common.api.AiCommonLimits
import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.OnDeviceAiLimits
import java.io.Closeable

internal class OnDeviceAiPluginAskRequest(
    val component: OnDeviceAiProviderComponent,
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
            "on-device AI plugin prompt",
        )
        require(timeoutMillis in OnDeviceAiLimits.MIN_TIMEOUT_MILLIS..OnDeviceAiLimits.MAX_TIMEOUT_MILLIS) {
            "Invalid on-device AI plugin timeout"
        }
        require(maximumOutputBytes in 1L..OnDeviceAiLimits.MAX_OUTPUT_BYTES) {
            "Invalid on-device AI plugin output limit"
        }
    }

    override fun toString(): String =
        "OnDeviceAiPluginAskRequest(component=$component, providerId=$providerId, modelId=$modelId, " +
            "promptBytes=${AiValidation.utf8Size(prompt)}, timeoutMillis=$timeoutMillis, " +
            "maximumOutputBytes=$maximumOutputBytes)"

    internal companion object {
        const val DEFAULT_MAXIMUM_OUTPUT_BYTES = 64L * 1024L
    }
}

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
    SESSION_REJECTED,
    PROVIDER_FAILED,
    INTERNAL_FAILURE,
}

internal sealed class OnDeviceAiPluginAskOutcome {
    class Completed(
        val text: String,
        /** The model that actually served the request, once resolved from the catalog. */
        val modelId: String? = null,
    ) : OnDeviceAiPluginAskOutcome() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)}, modelId=$modelId)"
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
 * Provider-neutral events for the narrow prompt-only plugin stream route.
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

    class Completed(val text: String) : OnDeviceAiPluginStreamEvent() {
        override fun toString(): String =
            "Completed(textBytes=${AiValidation.utf8Size(text)})"
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
