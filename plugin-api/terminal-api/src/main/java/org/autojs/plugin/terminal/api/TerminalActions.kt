package org.autojs.plugin.terminal.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery and launch contract of the terminal plugin family. The literals are frozen: the plugin
 * manifest, the host `<queries>` block, the plugin center registration and the host
 * `TerminalLauncher` all repeat them.
 */
object TerminalActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [ITerminalPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SERVICE_ACTION = "org.autojs.plugin.TERMINAL"

    /** Intent category of the terminal service, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "terminal"

    /** Signature permission every Binder entry point and the two Activity entries of the plugin require. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /**
     * Action of the plugin's terminal entry Activity. The host starts it with an explicit component
     * and the [TerminalContract.EXTRA_DIRECTORY], [TerminalContract.EXTRA_SESSION_ID],
     * [TerminalContract.EXTRA_NEW_SESSION], [TerminalContract.EXTRA_COMMAND] and
     * [TerminalContract.EXTRA_MANAGER] extras (plugin roadmap D19); never through a PendingIntent.
     */
    const val OPEN_TERMINAL = "org.autojs.plugin.TERMINAL_OPEN"

    /** Parameterless settings entry of the plugin, exported only behind [PLUGIN_PERMISSION]. */
    const val OPEN_SETTINGS = "org.autojs.plugin.TERMINAL_SETTINGS"
}
