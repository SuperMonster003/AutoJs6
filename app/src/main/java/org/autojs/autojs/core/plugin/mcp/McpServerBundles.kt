package org.autojs.autojs.core.plugin.mcp

import android.os.Bundle
import org.autojs.plugin.mcp.server.api.McpServerContract

/** Bundle codecs for the pure models in `McpServerConfig.kt`; keys are the contract literals. */
internal object McpServerBundles {

    fun configToBundle(config: McpServerConfig): Bundle = Bundle().apply {
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
        putInt(McpServerContract.KEY_SERVER_CONFIG_PORT, config.port)
        putString(McpServerContract.KEY_SERVER_CONFIG_BIND_SCOPE, config.bindScope)
        putString(McpServerContract.KEY_SERVER_CONFIG_PROTOCOL_MODE, config.protocolMode)
        config.hostLabel?.takeIf { it.isNotBlank() }?.let { putString(McpServerContract.KEY_SERVER_CONFIG_HOST_LABEL, it) }
    }

    fun reasonBundle(code: String, message: String? = null): Bundle = Bundle().apply {
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.CONTRACT_VERSION)
        putString(McpServerContract.KEY_ERROR_CODE, code)
        message?.take(McpServerContract.MAX_ERROR_MESSAGE_BYTES)?.let { putString(McpServerContract.KEY_ERROR_MESSAGE, it) }
    }

    fun statusFromBundle(bundle: Bundle?): McpServerStatus {
        if (bundle == null) {
            return McpServerStatus.stopped()
        }
        val state = bundle.getString(McpServerContract.KEY_STATUS_STATE)
            ?.takeIf(McpServerContract::isKnownState)
            ?: McpServerContract.STATE_STOPPED
        return McpServerStatus(
            state = state,
            endpoints = bundle.getStringArray(McpServerContract.KEY_STATUS_ENDPOINTS)?.filterNotNull().orEmpty(),
            port = bundle.getInt(McpServerContract.KEY_STATUS_PORT, 0),
            bindScope = bundle.getString(McpServerContract.KEY_STATUS_BIND_SCOPE) ?: McpServerContract.BIND_SCOPE_LOOPBACK,
            protocolMode = bundle.getString(McpServerContract.KEY_STATUS_PROTOCOL_MODE) ?: McpServerContract.PROTOCOL_MODE_STATEFUL,
            pairedClientCount = bundle.getInt(McpServerContract.KEY_STATUS_PAIRED_CLIENT_COUNT, 0),
            activeSessionCount = bundle.getInt(McpServerContract.KEY_STATUS_ACTIVE_SESSION_COUNT, 0),
            startedAt = bundle.getLong(McpServerContract.KEY_STATUS_STARTED_AT, 0L),
            hostAvailable = bundle.getBoolean(McpServerContract.KEY_STATUS_HOST_AVAILABLE, true),
            lastErrorCode = bundle.getString(McpServerContract.KEY_STATUS_LAST_ERROR_CODE),
            lastError = bundle.getString(McpServerContract.KEY_STATUS_LAST_ERROR)?.take(McpServerContract.MAX_ERROR_MESSAGE_BYTES),
        )
    }

    fun eventFromBundle(bundle: Bundle?): McpServerEvent? {
        val type = bundle?.getString(McpServerContract.KEY_EVENT_TYPE)?.takeIf { it.isNotBlank() } ?: return null
        return McpServerEvent(
            type = type,
            text = bundle.getString(McpServerContract.KEY_EVENT_TEXT)?.take(McpServerContract.MAX_EVENT_TEXT_BYTES),
            timestamp = bundle.getLong(McpServerContract.KEY_EVENT_TIMESTAMP, 0L),
            clientName = bundle.getString(McpServerContract.KEY_EVENT_CLIENT_NAME)?.take(McpServerContract.MAX_CLIENT_NAME_BYTES),
            toolName = bundle.getString(McpServerContract.KEY_EVENT_TOOL_NAME),
        )
    }
}
