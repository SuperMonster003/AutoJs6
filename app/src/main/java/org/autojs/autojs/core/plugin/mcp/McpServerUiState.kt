package org.autojs.autojs.core.plugin.mcp

import org.autojs.plugin.mcp.server.api.McpServerContract

/** User-facing state; discovery failures never masquerade as an uninstalled package. */
enum class McpServerUiState {
    NOT_INSTALLED, APPLICATION_DISABLED, ACTIVATION_REQUIRED, PLUGIN_DISABLED,
    AUTHORIZATION_REQUIRED, TRUST_CONFIRMATION_REQUIRED, INCOMPATIBLE, AVAILABLE,
    CHECKING, STARTING, RUNNING, HOST_UNAVAILABLE, STOPPING, STOPPED, FAILED,
}

data class McpServerPluginFacts(
    val installed: Boolean,
    val applicationEnabled: Boolean = true,
    val stopped: Boolean = false,
    val pluginEnabled: Boolean = false,
    val authorized: Boolean = false,
    val trustedSigner: Boolean = false,
    val signerConfirmed: Boolean = false,
    val compatible: Boolean = true,
)

object McpServerUiPolicy {
    fun availability(facts: McpServerPluginFacts): McpServerUiState = when {
        !facts.installed -> McpServerUiState.NOT_INSTALLED
        !facts.applicationEnabled -> McpServerUiState.APPLICATION_DISABLED
        !facts.compatible -> McpServerUiState.INCOMPATIBLE
        !facts.authorized -> McpServerUiState.AUTHORIZATION_REQUIRED
        !facts.trustedSigner && !facts.signerConfirmed -> McpServerUiState.TRUST_CONFIRMATION_REQUIRED
        facts.stopped -> McpServerUiState.ACTIVATION_REQUIRED
        !facts.pluginEnabled -> McpServerUiState.PLUGIN_DISABLED
        else -> McpServerUiState.AVAILABLE
    }

    fun state(status: McpServerStatus): McpServerUiState = when (status.state) {
        McpServerContract.STATE_STARTING -> McpServerUiState.STARTING
        McpServerContract.STATE_RUNNING -> if (status.hostAvailable) McpServerUiState.RUNNING else McpServerUiState.HOST_UNAVAILABLE
        McpServerContract.STATE_STOPPING -> McpServerUiState.STOPPING
        McpServerContract.STATE_FAILED -> McpServerUiState.FAILED
        else -> McpServerUiState.STOPPED
    }

    fun checked(state: McpServerUiState): Boolean = state in setOf(
        McpServerUiState.CHECKING, McpServerUiState.STARTING, McpServerUiState.RUNNING, McpServerUiState.HOST_UNAVAILABLE,
    )

    fun progress(state: McpServerUiState): Boolean = state in setOf(
        McpServerUiState.CHECKING, McpServerUiState.STARTING, McpServerUiState.STOPPING,
    )

    /** Guidance left by a refused switch attempt; it goes stale when the plugin package or its plugin state changes. */
    fun guidance(state: McpServerUiState): Boolean = state in setOf(
        McpServerUiState.NOT_INSTALLED, McpServerUiState.APPLICATION_DISABLED, McpServerUiState.ACTIVATION_REQUIRED,
        McpServerUiState.PLUGIN_DISABLED, McpServerUiState.AUTHORIZATION_REQUIRED, McpServerUiState.TRUST_CONFIRMATION_REQUIRED,
        McpServerUiState.INCOMPATIBLE,
    )

    /** State to show after the plugin changed while the switch is off; null keeps the current state. */
    fun refreshedGuidance(current: McpServerUiState, inspected: McpServerUiState): McpServerUiState? = when {
        !guidance(current) -> null
        inspected == McpServerUiState.AVAILABLE -> McpServerUiState.STOPPED
        inspected == current || !guidance(inspected) -> null
        else -> inspected
    }
}
