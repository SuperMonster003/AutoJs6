package org.autojs.plugin.installer.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery contract of the installer plugin family. The literals are frozen: the plugin manifest,
 * the host `<queries>` block and the plugin center registration all repeat them.
 */
object InstallerActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [IInstallerPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SERVICE_ACTION = "org.autojs.plugin.INSTALLER"

    /** Intent category of the installer service, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "installer"

    /** Signature permission every Binder entry point of the plugin requires from its caller. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /** Parameterless settings entry of the plugin, exported only behind [PLUGIN_PERMISSION]. */
    const val OPEN_SETTINGS = "org.autojs.plugin.INSTALLER_SETTINGS"
}
