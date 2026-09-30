package org.autojs.plugin.installer.api

/**
 * Identity of the official installer plugin (3-Setup Installer) as reported by `PluginInfo`. The
 * engine id `installer` is shared by every implementation of this contract; the plugin id names
 * the official one.
 */
object InstallerIds {

    const val PLUGIN_ID = "three-setup-installer"

    const val ENGINE = "installer"

    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.setup.installer"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0 host build that ships this contract
     * module, the shared package archive parser and the host client (plugin roadmap P1).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5299L
}
