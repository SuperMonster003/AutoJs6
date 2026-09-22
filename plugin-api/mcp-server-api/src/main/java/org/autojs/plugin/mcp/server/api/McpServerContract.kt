package org.autojs.plugin.mcp.server.api

import org.autojs.plugin.host.capability.api.HostCapabilityContract

/**
 * Bundle keys, enumerations, and hard ceilings of the MCP Server plugin contract (version 2; version 1 remains supported).
 *
 * Conventions:
 * - Every Bundle is a snapshot owned by the receiver; senders never mutate a Bundle after handing
 *   it over, and receivers never hold a reference across transactions.
 * - Keys that mirror the Node.js plugin family (`bridge*`, `hostCapabilityBroker*`, `error*`)
 *   keep the same literals and meanings, so the host reuses its Node bridge dispatch path.
 * - All Binder methods may be invoked from binder threads; implementations must be thread-safe
 *   and must not block on the peer process.
 * - Unknown keys are ignored; missing optional keys take the documented default.
 */
object McpServerContract {

    // ------------------------------------------------------------------ versions

    /** Version of the control plane ([IMcpServerPlugin], [IMcpServerSession], [IMcpServerCallback]). */
    const val CONTRACT_VERSION = 2
    const val MIN_SUPPORTED_CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = MIN_SUPPORTED_CONTRACT_VERSION
    const val MAX_CONTRACT_VERSION = CONTRACT_VERSION

    /** Version of the data plane ([IMcpHostCapabilityBroker], [IMcpHostCapabilityCallback]). */
    const val HOST_CAPABILITY_BROKER_CONTRACT_VERSION = HostCapabilityContract.HOST_CAPABILITY_BROKER_CONTRACT_VERSION
    const val MIN_HOST_CAPABILITY_BROKER_CONTRACT_VERSION = HostCapabilityContract.MIN_HOST_CAPABILITY_BROKER_CONTRACT_VERSION
    const val MAX_HOST_CAPABILITY_BROKER_CONTRACT_VERSION = HostCapabilityContract.MAX_HOST_CAPABILITY_BROKER_CONTRACT_VERSION

    @JvmStatic
    fun supportsContractVersion(version: Int): Boolean =
        version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION

    @JvmStatic
    fun supportsHostCapabilityBrokerContractVersion(version: Int): Boolean =
        version in MIN_HOST_CAPABILITY_BROKER_CONTRACT_VERSION..MAX_HOST_CAPABILITY_BROKER_CONTRACT_VERSION

    /** Int: contract version of the sender, present in config, status, and broker-info Bundles. */
    const val KEY_CONTRACT_VERSION = HostCapabilityContract.KEY_CONTRACT_VERSION

    // ------------------------------------------------------------------ endpoint defaults

    const val DEFAULT_PORT = 9637
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535
    const val ENDPOINT_PATH = "/mcp"
    const val LOOPBACK_HOST = "127.0.0.1"

    @JvmStatic
    fun isValidPort(port: Int): Boolean = port in MIN_PORT..MAX_PORT

    // ------------------------------------------------------------------ server config (openServer / updateConfig)

    /** Int in [MIN_PORT]..[MAX_PORT]; default [DEFAULT_PORT]. */
    const val KEY_SERVER_CONFIG_PORT = "serverConfigPort"

    /** String: [BIND_SCOPE_LOOPBACK] (default) or [BIND_SCOPE_LAN]. */
    const val KEY_SERVER_CONFIG_BIND_SCOPE = "serverConfigBindScope"

    /** String: [PROTOCOL_MODE_STATEFUL] (default) or [PROTOCOL_MODE_DUAL]. */
    const val KEY_SERVER_CONFIG_PROTOCOL_MODE = "serverConfigProtocolMode"

    /** String: label shown to users in pairing prompts, for example the host app name; optional. */
    const val KEY_SERVER_CONFIG_HOST_LABEL = "serverConfigHostLabel"

    /** Boolean, default false: use the plugin's saved listener settings; requires SETTINGS_VERSION >= 1. */
    const val KEY_SERVER_CONFIG_USE_SAVED_SETTINGS = "serverConfigUseSavedSettings"

    const val SETTINGS_VERSION = 1

    const val BIND_SCOPE_LOOPBACK = "loopback"
    const val BIND_SCOPE_LAN = "lan"

    /** Only the session-based Streamable HTTP transport is mounted on [ENDPOINT_PATH] (decision D9). */
    const val PROTOCOL_MODE_STATEFUL = "stateful"

    /** Reserved: additionally mounts the session-less transport on a second path; rejected until implemented. */
    const val PROTOCOL_MODE_DUAL = "dual"

    @JvmField
    val BIND_SCOPES: Set<String> = linkedSetOf(BIND_SCOPE_LOOPBACK, BIND_SCOPE_LAN)

    @JvmField
    val PROTOCOL_MODES: Set<String> = linkedSetOf(PROTOCOL_MODE_STATEFUL, PROTOCOL_MODE_DUAL)

    // ------------------------------------------------------------------ status (getStatus / onStatus)

    /** String: one of [STATES]. */
    const val KEY_STATUS_STATE = "statusState"

    /** String array: every URL the listener currently accepts, for example `http://127.0.0.1:9637/mcp`. */
    const val KEY_STATUS_ENDPOINTS = "statusEndpoints"

    /** Int: bound port, or 0 while stopped. */
    const val KEY_STATUS_PORT = "statusPort"

    /** String: effective bind scope, one of [BIND_SCOPES]. */
    const val KEY_STATUS_BIND_SCOPE = "statusBindScope"

    /** String: effective protocol mode, one of [PROTOCOL_MODES]. */
    const val KEY_STATUS_PROTOCOL_MODE = "statusProtocolMode"

    /** Int: clients that completed pairing, bounded by [MAX_PAIRED_CLIENTS]. */
    const val KEY_STATUS_PAIRED_CLIENT_COUNT = "statusPairedClientCount"

    /** Int: MCP sessions currently open on the listener. */
    const val KEY_STATUS_ACTIVE_SESSION_COUNT = "statusActiveSessionCount"

    /** Long: epoch milliseconds of the last successful start, or 0 while stopped. */
    const val KEY_STATUS_STARTED_AT = "statusStartedAt"

    /** Boolean: whether the host broker still answers; false turns every tool into `HOST_UNAVAILABLE`. */
    const val KEY_STATUS_HOST_AVAILABLE = "statusHostAvailable"

    /** String: one of the `ERROR_*` codes of the last failure, or absent. */
    const val KEY_STATUS_LAST_ERROR_CODE = "statusLastErrorCode"

    /** String: human-readable detail of the last failure, at most [MAX_ERROR_MESSAGE_BYTES]; optional. */
    const val KEY_STATUS_LAST_ERROR = "statusLastError"

    const val STATE_STOPPED = "stopped"
    const val STATE_STARTING = "starting"
    const val STATE_RUNNING = "running"
    const val STATE_STOPPING = "stopping"
    const val STATE_FAILED = "failed"

    @JvmField
    val STATES: Set<String> = linkedSetOf(STATE_STOPPED, STATE_STARTING, STATE_RUNNING, STATE_STOPPING, STATE_FAILED)

    @JvmStatic
    fun isKnownState(state: String?): Boolean = state != null && state in STATES

    /** True while the listener holds or is acquiring the port. */
    @JvmStatic
    fun isActiveState(state: String?): Boolean =
        state == STATE_STARTING || state == STATE_RUNNING || state == STATE_STOPPING

    // ------------------------------------------------------------------ errors and stop / destroy reasons

    /** String: an `ERROR_*` or `REASON_*` code. */
    const val KEY_ERROR_CODE = HostCapabilityContract.KEY_ERROR_CODE

    /** String: detail for logs and the drawer, at most [MAX_ERROR_MESSAGE_BYTES]. */
    const val KEY_ERROR_MESSAGE = HostCapabilityContract.KEY_ERROR_MESSAGE

    const val ERROR_UNSUPPORTED_CONTRACT = "unsupported_contract"
    const val ERROR_INVALID_CONFIG = "invalid_config"
    const val ERROR_PORT_IN_USE = "port_in_use"
    const val ERROR_BIND_FAILED = "bind_failed"
    const val ERROR_ALREADY_OPEN = "already_open"
    const val ERROR_INTERNAL = "internal"

    const val REASON_USER_REQUEST = "user_request"
    const val REASON_HOST_SHUTDOWN = "host_shutdown"
    const val REASON_HOST_DIED = "host_died"
    const val REASON_PLUGIN_DIED = "plugin_died"
    const val REASON_CONFIG_CHANGED = "config_changed"

    // ------------------------------------------------------------------ events (onEvent)

    /** String: one of the `EVENT_*` types. */
    const val KEY_EVENT_TYPE = "eventType"

    /** String: short description, at most [MAX_EVENT_TEXT_BYTES]; optional. */
    const val KEY_EVENT_TEXT = "eventText"

    /** Long: epoch milliseconds when the event happened in the plugin process. */
    const val KEY_EVENT_TIMESTAMP = "eventTimestamp"

    /** String: MCP client name as declared in `initialize`; optional. */
    const val KEY_EVENT_CLIENT_NAME = "eventClientName"

    /** String: tool name for [EVENT_TOOL_CALL]; optional otherwise. */
    const val KEY_EVENT_TOOL_NAME = "eventToolName"

    const val EVENT_PAIRING_REQUESTED = "pairing_requested"
    const val EVENT_CLIENT_PAIRED = "client_paired"
    const val EVENT_CLIENT_REVOKED = "client_revoked"
    const val EVENT_TOOL_CALL = "tool_call"
    const val EVENT_WARNING = "warning"

    @JvmField
    val EVENT_TYPES: Set<String> = linkedSetOf(
        EVENT_PAIRING_REQUESTED,
        EVENT_CLIENT_PAIRED,
        EVENT_CLIENT_REVOKED,
        EVENT_TOOL_CALL,
        EVENT_WARNING,
    )

    // ------------------------------------------------------------------ broker info (getBrokerInfo)

    /** String: unique id of the broker instance, one per opened server. */
    const val KEY_HOST_CAPABILITY_BROKER_ID = HostCapabilityContract.KEY_HOST_CAPABILITY_BROKER_ID

    /** Int: [HOST_CAPABILITY_BROKER_CONTRACT_VERSION] of the host. */
    const val KEY_HOST_CAPABILITY_BROKER_VERSION = HostCapabilityContract.KEY_HOST_CAPABILITY_BROKER_VERSION

    /** String array: bridge modules the host can dispatch at all (before the grant). */
    const val KEY_HOST_CAPABILITY_MODULES = HostCapabilityContract.KEY_HOST_CAPABILITY_MODULES

    /** String: JSON of the host bridge engine info (`engineName = mcp`, host package and version). */
    const val KEY_BRIDGE_ENGINE_INFO = HostCapabilityContract.KEY_BRIDGE_ENGINE_INFO

    // ------------------------------------------------------------------ grant (part of the broker info; decision D17)

    /** String array of `module.method` entries the session may call; anything else is `capability-denied`. */
    const val KEY_GRANT_METHODS = HostCapabilityContract.KEY_GRANT_METHODS

    /** String array of permission tokens; a request's `permissions` must be a subset. */
    const val KEY_GRANT_PERMISSIONS = HostCapabilityContract.KEY_GRANT_PERMISSIONS

    /** Int: largest [KEY_BRIDGE_REQUEST_JSON] the host accepts, at most [MAX_BRIDGE_INLINE_JSON_BYTES]. */
    const val KEY_GRANT_MAX_REQUEST_BYTES = HostCapabilityContract.KEY_GRANT_MAX_REQUEST_BYTES

    /** Int: dispatches allowed in flight, at most [MAX_CONCURRENT_TOOL_CALLS]. */
    const val KEY_GRANT_MAX_CONCURRENT_CALLS = HostCapabilityContract.KEY_GRANT_MAX_CONCURRENT_CALLS

    /** Int: accessibility queries per second before `rate-limited`. */
    const val KEY_GRANT_ACCESSIBILITY_QUERIES_PER_SECOND = HostCapabilityContract.KEY_GRANT_ACCESSIBILITY_QUERIES_PER_SECOND

    /** Long: timeout applied when a request omits `timeoutMs`; at most [DEFAULT_TOOL_TIMEOUT_MS]. */
    const val KEY_GRANT_DEFAULT_TIMEOUT_MS = HostCapabilityContract.KEY_GRANT_DEFAULT_TIMEOUT_MS

    /** Long: ceiling for a request's `timeoutMs`; at most [MAX_TOOL_TIMEOUT_MS]. */
    const val KEY_GRANT_MAX_TIMEOUT_MS = HostCapabilityContract.KEY_GRANT_MAX_TIMEOUT_MS

    // ------------------------------------------------------------------ bridge envelope (dispatch / onResponse)

    /** String: `NodeBridgeRequest` JSON (`id`, `module`, `method`, `args`, `timeoutMs`, `permissions`). */
    const val KEY_BRIDGE_REQUEST_JSON = HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON

    /** String: MCP client name attached for audit logs; optional, at most [MAX_CLIENT_NAME_BYTES]. */
    const val KEY_BRIDGE_CLIENT_NAME = HostCapabilityContract.KEY_BRIDGE_CLIENT_NAME

    /** String: `NodeBridgeResponse` JSON (`id`, `ok`, `result` or `error`). */
    const val KEY_BRIDGE_RESPONSE_JSON = HostCapabilityContract.KEY_BRIDGE_RESPONSE_JSON

    /** Boolean: mirrors the `ok` field of [KEY_BRIDGE_RESPONSE_JSON]. */
    const val KEY_BRIDGE_RESPONSE_OK = HostCapabilityContract.KEY_BRIDGE_RESPONSE_OK

    /** String: the error message when [KEY_BRIDGE_RESPONSE_OK] is false; optional. */
    const val KEY_BRIDGE_ERROR_MESSAGE = HostCapabilityContract.KEY_BRIDGE_ERROR_MESSAGE

    /**
     * ParcelFileDescriptor: read side of a payload larger than [MAX_BRIDGE_INLINE_JSON_BYTES]
     * (screenshots, file contents). The receiver owns it and must close it; the JSON result then
     * carries only metadata.
     */
    const val KEY_BRIDGE_PAYLOAD_FD = HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD

    /** Long: byte length of [KEY_BRIDGE_PAYLOAD_FD], at most [MAX_BRIDGE_PAYLOAD_BYTES]. */
    const val KEY_BRIDGE_PAYLOAD_BYTES = HostCapabilityContract.KEY_BRIDGE_PAYLOAD_BYTES

    /** String: MIME type of [KEY_BRIDGE_PAYLOAD_FD], for example `image/jpeg`. */
    const val KEY_BRIDGE_PAYLOAD_MIME = HostCapabilityContract.KEY_BRIDGE_PAYLOAD_MIME

    // ------------------------------------------------------------------ hard ceilings (roadmap appendix A.7)

    /** Largest HTTP request body the listener reads; larger bodies are rejected before parsing. */
    const val MAX_REQUEST_BODY_BYTES = 1024 * 1024

    /** Tool calls executed concurrently per server; further calls wait or fail with `LIMIT_EXCEEDED`. */
    const val MAX_CONCURRENT_TOOL_CALLS = HostCapabilityContract.MAX_CONCURRENT_TOOL_CALLS

    const val DEFAULT_TOOL_TIMEOUT_MS = HostCapabilityContract.DEFAULT_TOOL_TIMEOUT_MS
    const val MAX_TOOL_TIMEOUT_MS = HostCapabilityContract.MAX_TOOL_TIMEOUT_MS

    const val MAX_DUMP_NODES = HostCapabilityContract.MAX_DUMP_NODES
    const val MAX_DUMP_DEPTH = HostCapabilityContract.MAX_DUMP_DEPTH
    const val MAX_DUMP_TEXT_BYTES = HostCapabilityContract.MAX_DUMP_TEXT_BYTES

    const val MAX_SCREENSHOT_ENCODED_BYTES = HostCapabilityContract.MAX_SCREENSHOT_ENCODED_BYTES

    const val MAX_CONSOLE_TAIL_LINES = HostCapabilityContract.MAX_CONSOLE_TAIL_LINES
    const val MAX_CONSOLE_TAIL_BYTES = HostCapabilityContract.MAX_CONSOLE_TAIL_BYTES

    const val MAX_FILE_READ_BYTES = HostCapabilityContract.MAX_FILE_READ_BYTES

    /** Bounded by [MAX_REQUEST_BODY_BYTES]: file contents arrive inside the tool-call body. */
    const val MAX_FILE_WRITE_BYTES = HostCapabilityContract.MAX_FILE_WRITE_BYTES

    const val MAX_LIST_ENTRIES = HostCapabilityContract.MAX_LIST_ENTRIES

    const val MAX_SHELL_OUTPUT_BYTES = HostCapabilityContract.MAX_SHELL_OUTPUT_BYTES

    const val MAX_PAIRED_CLIENTS = 32
    const val PAIRING_WINDOW_MS = 60_000L
    const val PAIRING_COOLDOWN_MS = 30_000L

    /** Largest JSON string carried inline in a Binder Bundle in either direction; see [KEY_BRIDGE_PAYLOAD_FD]. */
    const val MAX_BRIDGE_INLINE_JSON_BYTES = HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES

    /** Largest payload streamed through [KEY_BRIDGE_PAYLOAD_FD]. */
    const val MAX_BRIDGE_PAYLOAD_BYTES = HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES

    const val MAX_ERROR_MESSAGE_BYTES = HostCapabilityContract.MAX_ERROR_MESSAGE_BYTES
    const val MAX_EVENT_TEXT_BYTES = 2 * 1024
    const val MAX_CLIENT_NAME_BYTES = HostCapabilityContract.MAX_CLIENT_NAME_BYTES
}
