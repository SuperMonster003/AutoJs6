package org.autojs.plugin.ai.provider.api

import org.autojs.plugin.ai.common.api.AiCommonProtocol
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiProtocolNegotiator
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import java.util.Collections

object AiProviderProtocol {
    const val ACTION_PROVIDER = "org.autojs.plugin.AI_PROVIDER"
    const val PLUGIN_PERMISSION = AiCommonProtocol.PLUGIN_PERMISSION

    /** Protocol 2.0 exposes the unified local and remote target catalog. */
    val PROTOCOL_V2 = AiProtocolVersion(AiCommonProtocol.MAJOR_VERSION, 0)

    val HOST_PROTOCOL_RANGE = AiProtocolRange(
        PROTOCOL_V2,
        PROTOCOL_V2,
    )

    const val SCHEMA_CAPABILITIES = 0x4150_0001
    const val SCHEMA_CONTENT_PART = 0x4150_0005
    const val SCHEMA_MESSAGE = 0x4150_0006
    const val SCHEMA_GENERATION_OPTIONS = 0x4150_0007
    const val SCHEMA_TOOL_DEFINITION = 0x4150_0008
    const val SCHEMA_TOOL_CALL = 0x4150_0009
    const val SCHEMA_TOOL_CALL_BATCH = 0x4150_000A
    const val SCHEMA_TOOL_RESULT = 0x4150_000B
    const val SCHEMA_TOOL_RESULT_BATCH = 0x4150_000C
    const val SCHEMA_TEXT_REQUEST = 0x4150_000D
    const val SCHEMA_SESSION_STARTED = 0x4150_000E
    const val SCHEMA_TEXT_CHUNK = 0x4150_000F
    const val SCHEMA_REASONING_METADATA = 0x4150_0010
    const val SCHEMA_COMPLETION_RESULT = 0x4150_0011
    const val SCHEMA_BACKEND_PROFILE_INFO = 0x4150_0012
    const val SCHEMA_TARGET_LIST_REQUEST = 0x4150_0013
    const val SCHEMA_TARGET_INFO = 0x4150_0014
    const val SCHEMA_TARGET_PAGE = 0x4150_0015
}

object AiProviderBackendProfile {
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val NPU = "npu"

    val VALUES: Set<String> = immutableSetOf(CPU, GPU, NPU)
}

object AiProviderBackendAvailability {
    const val AVAILABLE = "available"
    const val UNAVAILABLE = "unavailable"

    val VALUES: Set<String> = immutableSetOf(AVAILABLE, UNAVAILABLE)
}

object AiProviderBackendUnavailableReason {
    const val ABI_UNSUPPORTED = "abi-unsupported"
    const val OPENCL_LIBRARY_UNAVAILABLE = "opencl-library-unavailable"
    const val NPU_RUNTIME_NOT_PACKAGED = "npu-runtime-not-packaged"

    val VALUES: Set<String> = immutableSetOf(
        ABI_UNSUPPORTED,
        OPENCL_LIBRARY_UNAVAILABLE,
        NPU_RUNTIME_NOT_PACKAGED,
    )
}

object AiTargetAvailability {
    const val AVAILABLE = "available"
    const val UNAVAILABLE = "unavailable"

    val VALUES: Set<String> = immutableSetOf(AVAILABLE, UNAVAILABLE)
}

object AiTargetControlId {
    const val MAXIMUM_OUTPUT_TOKENS = "maximum-output-tokens"
    const val TEMPERATURE = "temperature"
    const val TOP_K = "top-k"
    const val TOP_P = "top-p"
    const val RESPONSE_JSON_SCHEMA = "response-json-schema"
    const val BACKEND_PROFILE = "backend-profile"

    val VALUES: Set<String> = immutableSetOf(
        MAXIMUM_OUTPUT_TOKENS,
        TEMPERATURE,
        TOP_K,
        TOP_P,
        RESPONSE_JSON_SCHEMA,
        BACKEND_PROFILE,
    )
}

object AiProviderCapabilityId {
    const val STREAMING = "streaming"
    const val REASONING = "reasoning"
    const val TOOLS = "tools"
    const val STRUCTURED_JSON = "structured-json"
    const val USAGE = "usage"
    const val PERSISTENT_SESSION = "persistent-session"

    val VALUES: Set<String> = immutableSetOf(
        STREAMING,
        REASONING,
        TOOLS,
        STRUCTURED_JSON,
        USAGE,
        PERSISTENT_SESSION,
    )
}

object AiMessageRole {
    const val SYSTEM = 1
    const val USER = 2
    const val ASSISTANT = 3
    const val TOOL = 4

    val VALUES: Set<Int> = immutableSetOf(SYSTEM, USER, ASSISTANT, TOOL)
}

object AiFinishReason {
    const val STOP = 1
    const val LENGTH = 2
    const val TOOL_CALLS = 3
    const val CONTENT_FILTER = 4
    const val ERROR = 5
    const val OTHER = 6

    val VALUES: Set<Int> = immutableSetOf(STOP, LENGTH, TOOL_CALLS, CONTENT_FILTER, ERROR, OTHER)
}

object AiProviderMimeType {
    const val PLAIN = "text/plain"
    const val JSON = "application/json"

    val INPUT_VALUES: Set<String> = immutableSetOf(PLAIN, JSON)
    val SCHEMA_VALUES: Set<String> = immutableSetOf(JSON)
}

private fun <T> immutableSetOf(vararg values: T): Set<T> =
    Collections.unmodifiableSet(linkedSetOf(*values))

object AiProviderLimits {
    const val MAX_MESSAGES = 256
    const val MAX_CONTENT_PARTS = 512
    const val MAX_TOOL_DEFINITIONS = 128
    const val MAX_TOOL_ROUNDS = 16
    const val MAX_TOTAL_INPUT_BYTES = 2L * 1024L * 1024L
    const val MAX_OUTPUT_BYTES = 8L * 1024L * 1024L
    const val MAX_STREAMING_OUTPUT_BYTES = 32L * 1024L * 1024L
    const val MAX_ONE_SCHEMA_BYTES = 64L * 1024L
    const val MAX_ALL_TOOL_SCHEMA_BYTES = 1024L * 1024L
    const val MAX_CHUNK_TEXT_BYTES = 48 * 1024
    const val MAX_CHUNK_ENVELOPE_BYTES = 64 * 1024
    const val INITIAL_CHUNK_CREDITS = 8
    const val MAX_OUTSTANDING_CHUNK_CREDITS = 16
    const val MAX_OUTSTANDING_TOOL_CALLS = 32
    const val MAX_TOOL_ARGUMENT_OR_RESULT_BYTES = 256L * 1024L
    /** Cumulative declared tool-call and tool-result bytes across the complete session. */
    const val MAX_TOTAL_TOOL_DATA_BYTES = 8L * 1024L * 1024L
    const val MAX_TOOL_NAME_BYTES = 128
    const val MAX_TOOL_CALL_ID_BYTES = 256
    const val MAX_TOOL_DESCRIPTION_BYTES = 4 * 1024
    const val MAX_JSON_NESTING_DEPTH = 32
    const val MAX_JSON_NODES = 10_000
    const val MAX_JSON_STRING_BYTES = 64 * 1024
    const val MAX_TARGETS_PER_PAGE = 100
    const val MAX_TARGET_PAGES = 100
    const val MAX_TARGET_PAGE_TOKEN_BYTES = 4 * 1024
    const val MAX_TARGET_PAGE_ENVELOPE_BYTES = 128 * 1024
    const val MAX_TARGET_PAGE_TOKEN_LEDGER_ENTRIES = 4 * 1024
    const val MAX_OUTPUT_TOKENS = 2_147_483_647L
    const val MIN_TIMEOUT_MILLIS = 1_000L
    const val MAX_TIMEOUT_MILLIS = 10L * 60L * 1_000L
}

/**
 * LiteRT-LM requires a complete sampler triple whenever any sampler override is present. These
 * values mirror its stable engine baseline. Leaving every sampler option unset still delegates to
 * the model/engine defaults instead of installing this triple.
 */
object AiProviderSamplingDefaults {
    const val TOP_K = 1
    const val TOP_P = 0.95
    const val TEMPERATURE = 1.0
}

object AiProviderVersionPolicy {
    fun negotiate(providerRange: AiProtocolRange): AiProtocolVersion =
        AiProtocolNegotiator.requireNegotiated(AiProviderProtocol.HOST_PROTOCOL_RANGE, providerRange)

    fun requireSelected(requested: AiProtocolVersion, negotiated: AiProtocolVersion) {
        require(requested == negotiated) { "Request protocol version does not match the negotiated version" }
    }
}
