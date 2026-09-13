package org.autojs.autojs.core.plugin.mcp

import org.autojs.plugin.mcp.server.api.McpServerContract

/**
 * Listener configuration the host hands to `IMcpServerPlugin.openServer` (contract keys
 * `KEY_SERVER_CONFIG_*`). Pure Kotlin; the Bundle codec lives in `McpServerBundles.kt`.
 */
data class McpServerConfig(
    val port: Int = McpServerContract.DEFAULT_PORT,
    val bindScope: String = McpServerContract.BIND_SCOPE_LOOPBACK,
    val protocolMode: String = McpServerContract.PROTOCOL_MODE_STATEFUL,
    val hostLabel: String? = null,
    val useSavedSettings: Boolean = false,
) {

    /** `null` when the configuration can be sent; otherwise the `ERROR_*` code plus a detail. */
    fun validate(): ValidationError? = when {
        !McpServerContract.isValidPort(port) -> ValidationError(
            McpServerContract.ERROR_INVALID_CONFIG,
            "port must be within ${McpServerContract.MIN_PORT}..${McpServerContract.MAX_PORT}: $port",
        )
        bindScope !in McpServerContract.BIND_SCOPES -> ValidationError(
            McpServerContract.ERROR_INVALID_CONFIG,
            "unknown bind scope: $bindScope",
        )
        protocolMode !in McpServerContract.PROTOCOL_MODES -> ValidationError(
            McpServerContract.ERROR_INVALID_CONFIG,
            "unknown protocol mode: $protocolMode",
        )
        protocolMode == McpServerContract.PROTOCOL_MODE_DUAL -> ValidationError(
            McpServerContract.ERROR_INVALID_CONFIG,
            "the dual protocol mode is reserved until the SDK supports it (roadmap D9)",
        )
        hostLabel != null && hostLabel.length > MAX_HOST_LABEL_LENGTH -> ValidationError(
            McpServerContract.ERROR_INVALID_CONFIG,
            "host label exceeds $MAX_HOST_LABEL_LENGTH characters",
        )
        else -> null
    }

    /** The loopback URL the listener exposes for `adb forward`, regardless of the bind scope. */
    val loopbackEndpoint: String
        get() = "http://${McpServerContract.LOOPBACK_HOST}:$port${McpServerContract.ENDPOINT_PATH}"

    data class ValidationError(val code: String, val message: String)

    companion object {
        const val MAX_HOST_LABEL_LENGTH = 64
    }
}

/** Snapshot of `KEY_STATUS_*` as reported by the plugin, or synthesized by the host on failures. */
data class McpServerStatus(
    val state: String = McpServerContract.STATE_STOPPED,
    val endpoints: List<String> = emptyList(),
    val port: Int = 0,
    val bindScope: String = McpServerContract.BIND_SCOPE_LOOPBACK,
    val protocolMode: String = McpServerContract.PROTOCOL_MODE_STATEFUL,
    val pairedClientCount: Int = 0,
    val activeSessionCount: Int = 0,
    val startedAt: Long = 0L,
    val hostAvailable: Boolean = true,
    val lastErrorCode: String? = null,
    val lastError: String? = null,
) {

    val isActive: Boolean
        get() = McpServerContract.isActiveState(state)

    val isRunning: Boolean
        get() = state == McpServerContract.STATE_RUNNING

    companion object {

        fun starting(config: McpServerConfig, lastErrorCode: String? = null, lastError: String? = null) = McpServerStatus(
            state = McpServerContract.STATE_STARTING,
            port = config.port,
            bindScope = config.bindScope,
            protocolMode = config.protocolMode,
            lastErrorCode = lastErrorCode,
            lastError = lastError,
        )

        fun stopped(reasonCode: String? = null, message: String? = null) = McpServerStatus(
            state = McpServerContract.STATE_STOPPED,
            lastErrorCode = reasonCode,
            lastError = message,
        )

        fun failed(code: String, message: String?) = McpServerStatus(
            state = McpServerContract.STATE_FAILED,
            lastErrorCode = code,
            lastError = message,
        )
    }
}

/** One `KEY_EVENT_*` notification from the plugin. */
data class McpServerEvent(
    val type: String,
    val text: String? = null,
    val timestamp: Long = 0L,
    val clientName: String? = null,
    val toolName: String? = null,
)

/**
 * Bounded rebind schedule applied when the plugin process dies while a server is open
 * (roadmap P1.2): three attempts after 1 s, 3 s, and 9 s, then `STOPPED(reason = plugin_died)`.
 */
object McpRebindPolicy {

    const val MAX_ATTEMPTS = 3

    @JvmField
    val DELAYS_MS = longArrayOf(1_000L, 3_000L, 9_000L)

    /** Delay before the 1-based [attempt], or `null` once the schedule is exhausted. */
    fun delayForAttempt(attempt: Int): Long? = DELAYS_MS.getOrNull(attempt - 1)

    fun isExhausted(attempt: Int): Boolean = attempt > MAX_ATTEMPTS
}
