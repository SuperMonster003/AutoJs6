package org.autojs.plugin.terminal.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [ITerminalPlugin.getCapabilities]. Every
 * value is readable without opening a session, so the plugin center can show compatibility up
 * front and the host can choose its code path before the first request.
 */
object TerminalCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /** Int: [TerminalContract.CONTRACT_VERSION] implemented by the plugin. */
    const val CONTRACT_VERSION = "terminalContractVersion"

    /** String array: optional features, a subset of [FEATURES]. */
    const val FEATURES_KEY = "terminalFeatures"

    /** Int: largest number of concurrent sessions the plugin accepts (at most [TerminalContract.MAX_SESSIONS]). */
    const val MAX_SESSIONS = "terminalMaxSessions"

    /** Int: largest number of output subscriptions per session (at most [TerminalContract.MAX_SUBSCRIPTIONS_PER_SESSION]). */
    const val MAX_SUBSCRIPTIONS = "terminalMaxSubscriptions"

    /** String: Node.js toolchain state, one of [TerminalContract.NODE_CLI_STATES]. */
    const val NODE_CLI = "terminalNodeCli"

    /** `node` / `npm` / `npx` / `corepack` / `yarn` / `pnpm` through the Node.js Runtime plugin. */
    const val FEATURE_NODE_CLI = "node-cli"

    /** [ITerminalPlugin.subscribeOutput] pipes and [ITerminalCallback.onOutputOverflow]. */
    const val FEATURE_OUTPUT_SUBSCRIPTION = "output-subscription"

    /** [ITerminalPlugin.readTranscript]. */
    const val FEATURE_TRANSCRIPT = "transcript"

    /** [TerminalActions.OPEN_SETTINGS] entry. */
    const val FEATURE_SETTINGS = "settings"

    /** Every optional feature of contract version 1. */
    val FEATURES: List<String> = listOf(
        FEATURE_NODE_CLI,
        FEATURE_OUTPUT_SUBSCRIPTION,
        FEATURE_TRANSCRIPT,
        FEATURE_SETTINGS,
    )
}
