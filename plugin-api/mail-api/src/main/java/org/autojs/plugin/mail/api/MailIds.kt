package org.autojs.plugin.mail.api

/**
 * Identity of the official mail plugin (3-Stamp Mail) as reported by `PluginInfo`. The engine id
 * `mail` is shared by every implementation of this contract; the plugin id names the official one.
 */
object MailIds {

    const val PLUGIN_ID = "three-stamp-mail"

    const val ENGINE = "mail"

    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.three.stamp.mail"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the first 6.8.0 host build with the mail
     * contract. Keep this protocol floor stable for other mail-engine implementations;
     * 3-Stamp Mail itself requires build 5316 for its renamed settings and installation entry.
     */
    const val REQUIRED_HOST_VERSION_CODE = 5282L
}
