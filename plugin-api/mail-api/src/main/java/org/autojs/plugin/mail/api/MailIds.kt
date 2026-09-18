package org.autojs.plugin.mail.api

/**
 * Identity of the official mail plugin (Angus Mail) as reported by `PluginInfo`. The engine id
 * `mail` is shared by every implementation of this contract; the plugin id names the official one.
 */
object MailIds {

    const val PLUGIN_ID = "angus-mail"

    const val ENGINE = "mail"

    const val VARIANT_DEFAULT = "default"

    const val DEFAULT_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.angus.mail"

    /**
     * Smallest host `versionCode` the plugin may declare through
     * `PluginCapabilityKeys.REQUIRES_HOST_VERSION`: the 6.8.0 host build that ships the mail
     * contract module and the host client (roadmap P1.4).
     */
    const val REQUIRED_HOST_VERSION_CODE = 5282L
}
