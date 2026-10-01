package org.autojs.plugin.terminal.api

/**
 * Identity of the official terminal plugin (3-Shell Terminal) as reported by `PluginInfo`. The
 * engine id `terminal` is shared by every implementation of this contract; the plugin id names
 * the official one.
 */
object TerminalIds {

    const val PLUGIN_ID = "three-shell-terminal"

    const val ENGINE = "terminal"

    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.shell.terminal"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0 host build that ships this contract
     * module (plugin roadmap P1.1; P1.4 confirms the value once the host client has landed).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5304L
}
