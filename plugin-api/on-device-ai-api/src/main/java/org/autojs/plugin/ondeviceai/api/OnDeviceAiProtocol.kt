package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiCommonProtocol
import org.autojs.plugin.ai.common.api.AiProtocolRange
import org.autojs.plugin.ai.common.api.AiProtocolNegotiator
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import java.util.Collections

object OnDeviceAiProtocol {
    const val ACTION_PROVIDER = "org.autojs.plugin.ON_DEVICE_AI"
    const val PLUGIN_PERMISSION = AiCommonProtocol.PLUGIN_PERMISSION

    /** Protocol 1.2 adds an explicitly negotiated persistent Conversation lifecycle. */
    val PROTOCOL_V1_2 = AiProtocolVersion(AiCommonProtocol.MAJOR_VERSION, 2)

    /** Protocol 1.3 adds explicit inference backend profiles and device availability reporting. */
    val PROTOCOL_V1_3 = AiProtocolVersion(AiCommonProtocol.MAJOR_VERSION, 3)

    val HOST_PROTOCOL_RANGE = AiProtocolRange(
        PROTOCOL_V1_2,
        PROTOCOL_V1_3,
    )

    const val SCHEMA_CAPABILITIES = 0x4F44_0001
    const val SCHEMA_MODEL_LIST_REQUEST = 0x4F44_0002
    const val SCHEMA_MODEL_INFO = 0x4F44_0003
    const val SCHEMA_MODEL_PAGE = 0x4F44_0004
    const val SCHEMA_CONTENT_PART = 0x4F44_0005
    const val SCHEMA_MESSAGE = 0x4F44_0006
    const val SCHEMA_GENERATION_OPTIONS = 0x4F44_0007
    const val SCHEMA_TOOL_DEFINITION = 0x4F44_0008
    const val SCHEMA_TOOL_CALL = 0x4F44_0009
    const val SCHEMA_TOOL_CALL_BATCH = 0x4F44_000A
    const val SCHEMA_TOOL_RESULT = 0x4F44_000B
    const val SCHEMA_TOOL_RESULT_BATCH = 0x4F44_000C
    const val SCHEMA_TEXT_REQUEST = 0x4F44_000D
    const val SCHEMA_SESSION_STARTED = 0x4F44_000E
    const val SCHEMA_TEXT_CHUNK = 0x4F44_000F
    const val SCHEMA_REASONING_METADATA = 0x4F44_0010
    const val SCHEMA_COMPLETION_RESULT = 0x4F44_0011
    const val SCHEMA_BACKEND_PROFILE_INFO = 0x4F44_0012
}

object OnDeviceAiBackendProfile {
    const val CPU = "cpu"
    const val GPU = "gpu"
    const val NPU = "npu"

    val VALUES: Set<String> = immutableSetOf(CPU, GPU, NPU)
}

object OnDeviceAiBackendAvailability {
    const val AVAILABLE = "available"
    const val UNAVAILABLE = "unavailable"

    val VALUES: Set<String> = immutableSetOf(AVAILABLE, UNAVAILABLE)
}

object OnDeviceAiBackendUnavailableReason {
    const val ABI_UNSUPPORTED = "abi-unsupported"
    const val OPENCL_LIBRARY_UNAVAILABLE = "opencl-library-unavailable"
    const val NPU_RUNTIME_NOT_PACKAGED = "npu-runtime-not-packaged"

    val VALUES: Set<String> = immutableSetOf(
        ABI_UNSUPPORTED,
        OPENCL_LIBRARY_UNAVAILABLE,
        NPU_RUNTIME_NOT_PACKAGED,
    )
}

object OnDeviceAiCapabilityId {
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

object OnDeviceAiMimeType {
    const val PLAIN = "text/plain"
    const val JSON = "application/json"

    val INPUT_VALUES: Set<String> = immutableSetOf(PLAIN, JSON)
    val SCHEMA_VALUES: Set<String> = immutableSetOf(JSON)
}

private fun <T> immutableSetOf(vararg values: T): Set<T> =
    Collections.unmodifiableSet(linkedSetOf(*values))

object OnDeviceAiLimits {
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
    const val MAX_MODELS_PER_PAGE = 100
    const val MAX_MODEL_PAGES = 100
    const val MAX_MODEL_PAGE_TOKEN_BYTES = 4 * 1024
    const val MAX_MODEL_PAGE_ENVELOPE_BYTES = 128 * 1024
    const val MAX_MODEL_PAGE_TOKEN_LEDGER_ENTRIES = 4 * 1024
    const val MAX_OUTPUT_TOKENS = 2_147_483_647L
    const val MIN_TIMEOUT_MILLIS = 1_000L
    const val MAX_TIMEOUT_MILLIS = 10L * 60L * 1_000L
}

/**
 * LiteRT-LM requires a complete sampler triple whenever any sampler override is present. These
 * values mirror its stable engine baseline. Leaving every sampler option unset still delegates to
 * the model/engine defaults instead of installing this triple.
 */
object OnDeviceAiSamplingDefaults {
    const val TOP_K = 1
    const val TOP_P = 0.95
    const val TEMPERATURE = 1.0
}

object OnDeviceAiVersionPolicy {
    fun negotiate(providerRange: AiProtocolRange): AiProtocolVersion =
        AiProtocolNegotiator.requireNegotiated(OnDeviceAiProtocol.HOST_PROTOCOL_RANGE, providerRange)

    fun requireSelected(requested: AiProtocolVersion, negotiated: AiProtocolVersion) {
        require(requested == negotiated) { "Request protocol version does not match the negotiated version" }
    }
}
