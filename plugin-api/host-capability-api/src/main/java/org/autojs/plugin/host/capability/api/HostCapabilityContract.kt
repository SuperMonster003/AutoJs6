package org.autojs.plugin.host.capability.api

/**
 * Shared capability data plane, version 1. Keys preserve the released MCP/Node bridge wire names.
 *
 * Bundles and JSON envelopes are non-null, immutable snapshots. Missing optional keys use their
 * documented defaults; unknown keys are ignored. All byte ceilings count UTF-8, not characters.
 * Calls may arrive concurrently on Binder threads: synchronous entry points validate/enqueue
 * within 200 ms and expensive work completes through the oneway callback exactly once.
 *
 * ParcelFileDescriptors are created by the sender. The receiver owns its incoming descriptor
 * and closes it after reading; rejected/unread descriptors are closed before returning.
 */
object HostCapabilityContract {
    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = CONTRACT_VERSION
    const val HOST_CAPABILITY_BROKER_CONTRACT_VERSION = CONTRACT_VERSION
    const val MIN_HOST_CAPABILITY_BROKER_CONTRACT_VERSION = MIN_CONTRACT_VERSION
    const val MAX_HOST_CAPABILITY_BROKER_CONTRACT_VERSION = MAX_CONTRACT_VERSION

    @JvmStatic
    fun supportsContractVersion(version: Int): Boolean =
        version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    @JvmStatic
    fun supportsHostCapabilityBrokerContractVersion(version: Int): Boolean =
        supportsContractVersion(version)

    /** Required Int: version of this shared contract, independent of the caller's control plane. */
    const val KEY_CONTRACT_VERSION = "contractVersion"
    /** Required String: unique broker instance id, scoped to one session or link. */
    const val KEY_HOST_CAPABILITY_BROKER_ID = "hostCapabilityBrokerId"
    /** Required Int: data-plane version implemented by the host. */
    const val KEY_HOST_CAPABILITY_BROKER_VERSION = "hostCapabilityBrokerVersion"
    /** Required String array: bridge modules admitted by this broker's grant. */
    const val KEY_HOST_CAPABILITY_MODULES = "hostCapabilityModules"
    /** Optional String array: currently available plugin-dependent methods (currently accessibility.readScreenText).
     * Missing means none advertised. This is a discovery snapshot, not a grant; every call still checks permissions.
     * Hosts discover without binding plugins or capturing the screen. Clients refresh at task start. */
    const val KEY_AVAILABLE_OPTIONAL_METHODS = "availableOptionalMethods"
    /** Required String: bridge engine info JSON, including engine name and host version. */
    const val KEY_BRIDGE_ENGINE_INFO = "bridgeEngineInfo"

    /** Required String array: granted module.method pairs; omitted methods are denied. */
    const val KEY_GRANT_METHODS = "grantMethods"
    /** Required String array: permitted request permission tokens. */
    const val KEY_GRANT_PERMISSIONS = "grantPermissions"
    /** Required Int: request JSON byte ceiling, at most MAX_BRIDGE_INLINE_JSON_BYTES. */
    const val KEY_GRANT_MAX_REQUEST_BYTES = "grantMaxRequestBytes"
    /** Required Int: in-flight dispatch ceiling, at most MAX_CONCURRENT_TOOL_CALLS. */
    const val KEY_GRANT_MAX_CONCURRENT_CALLS = "grantMaxConcurrentCalls"
    /** Required Int: maximum accessibility queries per second. */
    const val KEY_GRANT_ACCESSIBILITY_QUERIES_PER_SECOND = "grantAccessibilityQueriesPerSecond"
    /** Required Long: default request timeout, at most DEFAULT_TOOL_TIMEOUT_MS. */
    const val KEY_GRANT_DEFAULT_TIMEOUT_MS = "grantDefaultTimeoutMs"
    /** Required Long: request timeout ceiling, at most MAX_TOOL_TIMEOUT_MS. */
    const val KEY_GRANT_MAX_TIMEOUT_MS = "grantMaxTimeoutMs"
    /** Optional String: bounded JSON summary of methods, groups, permissions and model quotas. */
    const val KEY_GRANT_JSON = "grantJson"
    /** Optional String: reason JSON {code, message?}; message is bounded by MAX_ERROR_MESSAGE_BYTES. */
    const val KEY_REASON_JSON = "reasonJson"

    /** Required String: NodeBridgeRequest JSON (id, module, method, args, timeoutMs, permissions). */
    const val KEY_BRIDGE_REQUEST_JSON = "bridgeRequestJson"
    /** Optional String: audit client name, at most MAX_CLIENT_NAME_BYTES. */
    const val KEY_BRIDGE_CLIENT_NAME = "bridgeClientName"
    /** Required String: NodeBridgeResponse JSON (id, ok, result or error). */
    const val KEY_BRIDGE_RESPONSE_JSON = "bridgeResponseJson"
    /** Required Boolean: mirrors the JSON response's ok field. */
    const val KEY_BRIDGE_RESPONSE_OK = "bridgeResponseOk"
    /** Optional String: failure detail, at most MAX_ERROR_MESSAGE_BYTES. */
    const val KEY_BRIDGE_ERROR_MESSAGE = "bridgeErrorMessage"
    /** Optional ParcelFileDescriptor: response payload; receiver owns and closes the read side. */
    const val KEY_BRIDGE_PAYLOAD_FD = "bridgePayloadFd"
    /** Required with a descriptor: Long payload length, at most MAX_BRIDGE_PAYLOAD_BYTES. */
    const val KEY_BRIDGE_PAYLOAD_BYTES = "bridgePayloadBytes"
    /** Required with a descriptor: String MIME type, for example application/json or image/jpeg. */
    const val KEY_BRIDGE_PAYLOAD_MIME = "bridgePayloadMime"
    /** Optional String: stable error/reason code, never a localized message. */
    const val KEY_ERROR_CODE = "errorCode"
    /** Optional String: bounded diagnostic detail; never log private payloads. */
    const val KEY_ERROR_MESSAGE = "errorMessage"

    // Categories are the Node Bridge envelope vocabulary, not the Agent control-plane codes.
    const val ERROR_UNAVAILABLE = "unavailable"
    const val ERROR_PERMISSION_DENIED = "permission-denied"
    const val ERROR_CAPABILITY_DENIED = "capability-denied"
    const val ERROR_PROVIDER_FAILED = "provider-failed"
    const val ERROR_INVALID_REQUEST = "invalid-request"
    const val ERROR_RESOURCE_LIMIT = "resource-limit"
    const val ERROR_RATE_LIMITED = "rate-limited"
    const val ERROR_TIMEOUT = "timeout"
    const val ERROR_PROCESS_DEAD = "process-dead"
    const val ERROR_RUNTIME_ERROR = "runtime-error"

    @JvmField
    val ERROR_CATEGORIES: Set<String> = linkedSetOf(
        ERROR_UNAVAILABLE, ERROR_PERMISSION_DENIED, ERROR_CAPABILITY_DENIED, ERROR_PROVIDER_FAILED,
        ERROR_INVALID_REQUEST, ERROR_RESOURCE_LIMIT, ERROR_RATE_LIMITED, ERROR_TIMEOUT,
        ERROR_PROCESS_DEAD, ERROR_RUNTIME_ERROR,
    )

    /** Shared ceiling; a family grant may narrow it (3-Stove Agent allows at most two). */
    const val MAX_CONCURRENT_TOOL_CALLS = 4
    const val DEFAULT_TOOL_TIMEOUT_MS = 30_000L
    const val MAX_TOOL_TIMEOUT_MS = 300_000L
    const val MAX_DUMP_NODES = 400
    const val MAX_DUMP_DEPTH = 32
    const val MAX_DUMP_TEXT_BYTES = 256 * 1024
    const val MAX_SCREENSHOT_ENCODED_BYTES = 4 * 1024 * 1024
    const val MAX_CONSOLE_TAIL_LINES = 500
    const val MAX_CONSOLE_TAIL_BYTES = 256 * 1024
    const val MAX_FILE_READ_BYTES = 1024 * 1024
    const val MAX_FILE_WRITE_BYTES = 1024 * 1024
    const val MAX_LIST_ENTRIES = 2000
    const val MAX_SHELL_OUTPUT_BYTES = 256 * 1024
    /** Largest inline bridge JSON; larger accepted responses use KEY_BRIDGE_PAYLOAD_FD. */
    const val MAX_BRIDGE_INLINE_JSON_BYTES = 512 * 1024
    /** Maximum descriptor payload size; writers and readers independently enforce this ceiling. */
    const val MAX_BRIDGE_PAYLOAD_BYTES = 8 * 1024 * 1024
    const val MAX_ERROR_MESSAGE_BYTES = 4 * 1024
    const val MAX_CLIENT_NAME_BYTES = 256
}
